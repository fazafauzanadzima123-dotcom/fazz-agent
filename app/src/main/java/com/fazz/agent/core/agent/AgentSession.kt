package com.fazz.agent.core.agent

import android.content.Context
import android.os.Build
import com.fazz.agent.core.common.AppVisibility
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.proactive.Notifier
import com.fazz.agent.core.screen.FazzAccessibilityService
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.voice.TextToSpeechController
import com.fazz.agent.data.local.ConversationStore
import com.fazz.agent.data.local.MemoryRepository
import com.fazz.agent.data.local.SettingsRepository
import com.fazz.agent.data.local.StoredConversation
import com.fazz.agent.domain.models.AgentState
import com.fazz.agent.domain.models.ChatMessage
import com.fazz.agent.domain.models.MessageRole
import com.fazz.agent.domain.models.ToolActivity
import com.fazz.agent.domain.models.ToolStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID

data class SessionState(
    val conversationId: String? = null,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isBusy: Boolean = false,
    val agentState: AgentState = AgentState.Idle,
    /** Short status for the floating overlay and progress notification. */
    val agentStatus: String = "",
    val loaded: Boolean = false,
)

sealed interface SessionEvent {
    data class ReplyFinished(val text: String, val viaVoice: Boolean) : SessionEvent
}

data class BackgroundResult(val text: String, val error: String?, val conversationId: String)

/**
 * App-wide owner of the running agent and the open conversation. Lives outside any screen, so a task keeps
 * going while Fazz operates other apps (the UI only observes it).
 */
class AgentSession(
    private val context: Context,
    private val orchestrator: () -> AgentOrchestrator,
    private val backgroundOrchestrator: (Set<String>) -> AgentOrchestrator,
    private val settings: SettingsRepository,
    private val memory: MemoryRepository,
    private val store: ConversationStore,
    private val tts: TextToSpeechController,
    private val notifier: Notifier,
    private val notificationAccess: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events
    val conversations = store.index

    private var contents: List<JsonObject> = emptyList()
    private var job: Job? = null

    init {
        scope.launch {
            store.load()
            memory.load()
            _state.update { it.copy(loaded = true) }
        }
    }

    fun send(text: String, imagePaths: List<String> = emptyList(), viaVoice: Boolean = false) {
        val clean = text.trim()
        if ((clean.isEmpty() && imagePaths.isEmpty()) || _state.value.isBusy) return
        tts.stop()
        val now = System.currentTimeMillis()
        val convId = _state.value.conversationId ?: UUID.randomUUID().toString()
        val title = _state.value.title.ifBlank { ConversationStore.titleFrom(clean.ifBlank { "Gambar" }) }
        val user = ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, clean, now, viaVoice = viaVoice, images = imagePaths)
        val assistantId = UUID.randomUUID().toString()
        val assistant = ChatMessage(assistantId, MessageRole.ASSISTANT, "", now, isStreaming = true)
        _state.update {
            it.copy(conversationId = convId, title = title, messages = it.messages + user + assistant,
                isBusy = true, agentState = AgentState.Thinking, agentStatus = "Berpikir")
        }
        AgentForegroundService.start(context)

        job = scope.launch {
            var finalText = ""
            try {
                val parts = buildUserParts(clean, imagePaths)
                val system = systemPrompt()
                orchestrator().run(contents, parts, system, "fazz").collect { ev -> handle(assistantId, ev) { finalText = it } }
                val speak = viaVoice || settings.current().speakReplies
                if (finalText.isNotBlank() && speak) tts.speak(finalText)
                if (finalText.isNotBlank() && !AppVisibility.foreground.value) {
                    notifier.post(Notifier.ID_ALERT, notifier.base(Notifier.CH_ALERT, "Fazz selesai", finalText.take(300))
                        .setAutoCancel(true).setContentIntent(notifier.openAppIntent(convId, 3)))
                }
                _events.tryEmit(SessionEvent.ReplyFinished(finalText, viaVoice))
            } finally {
                updateMessage(assistantId) { m ->
                    if (m.isStreaming && m.text.isEmpty() && m.error == null) m.copy(isStreaming = false, error = "Dihentikan.")
                    else m.copy(isStreaming = false)
                }
                _state.update { it.copy(isBusy = false, agentState = AgentState.Idle, agentStatus = "") }
                AgentForegroundService.stop(context)
                persist()
            }
        }
    }

    private fun handle(id: String, ev: AgentEvent, onFinal: (String) -> Unit) {
        when (ev) {
            is AgentEvent.TextDelta -> {
                updateMessage(id) { it.copy(text = it.text + ev.text) }
                _state.update { it.copy(agentState = AgentState.Responding, agentStatus = "Menulis jawaban") }
            }
            is AgentEvent.ToolStarted -> {
                updateMessage(id) { it.copy(tools = it.tools + ToolActivity(ev.callId, ev.toolName, ev.label, ToolStatus.RUNNING)) }
                _state.update { it.copy(agentState = AgentState.UsingTool(ev.label), agentStatus = ev.label) }
                AgentForegroundService.update(context, ev.label)
            }
            is AgentEvent.ToolFinished -> {
                val status = when {
                    ev.result.success -> ToolStatus.SUCCESS
                    ev.result.errorCode == ErrorCodes.USER_DECLINED || ev.result.errorCode == ErrorCodes.PERMISSION_DENIED -> ToolStatus.DENIED
                    else -> ToolStatus.FAILED
                }
                updateMessage(id) { m -> m.copy(tools = m.tools.map { t -> if (t.callId == ev.callId) t.copy(status = status, detail = ev.result.summary) else t }, artifacts = ev.result.artifact?.let { a -> m.artifacts + a } ?: m.artifacts) }
                _state.update { it.copy(agentState = AgentState.Thinking, agentStatus = "Berpikir") }
            }
            is AgentEvent.Usage -> Unit // token counts are not shown to the user
            is AgentEvent.Completed -> { contents = ev.contents; onFinal(ev.finalText); updateMessage(id) { it.copy(isStreaming = false) } }
            is AgentEvent.Failed -> { contents = ev.contents; updateMessage(id) { it.copy(isStreaming = false, error = ev.error.userMessage) } }
        }
    }

    fun stop() {
        job?.cancel()
        tts.stop()
    }

    fun retryLast() {
        val msgs = _state.value.messages
        val lastUser = msgs.lastOrNull { it.role == MessageRole.USER } ?: return
        _state.update { it.copy(messages = msgs.subList(0, msgs.indexOf(lastUser))) }
        send(lastUser.text, lastUser.images, lastUser.viaVoice)
    }

    fun newChat() {
        stop()
        contents = emptyList()
        _state.update { SessionState(loaded = true) }
    }

    fun open(id: String) {
        if (_state.value.isBusy) stop()
        scope.launch {
            val c = store.get(id) ?: return@launch
            contents = c.contents
            _state.update { SessionState(c.id, c.title, c.messages, loaded = true) }
        }
    }

    fun delete(id: String) {
        scope.launch {
            store.delete(id)
            if (_state.value.conversationId == id) newChat()
        }
    }

    /** Headless run for proactive features (morning briefing). Read-only tools only. */
    suspend fun runBackgroundTask(title: String, prompt: String, allowedTools: Set<String>): BackgroundResult {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        var error: String? = null
        var finalContents: List<JsonObject> = emptyList()
        backgroundOrchestrator(allowedTools).run(emptyList(), prompt, systemPrompt(), "fazz").collect { ev ->
            when (ev) {
                is AgentEvent.Completed -> { sb.setLength(0); sb.append(ev.finalText); finalContents = ev.contents }
                is AgentEvent.Failed -> { error = ev.error.userMessage; finalContents = ev.contents }
                else -> Unit
            }
        }
        val messages = listOf(
            ChatMessage(UUID.randomUUID().toString(), MessageRole.NOTICE, title, now),
            ChatMessage(UUID.randomUUID().toString(), MessageRole.ASSISTANT, sb.toString(), now, error = error),
        )
        store.save(StoredConversation(id, title, now, messages, finalContents))
        return BackgroundResult(sb.toString(), error, id)
    }

    private suspend fun systemPrompt(): String {
        val s = settings.current()
        val off = s.disabledPlugins.mapNotNull(com.fazz.agent.domain.models.PluginGroup::from)
        return SystemPrompt.build(
            now = ZonedDateTime.now(),
            memories = memory.items.value,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            screenControl = FazzAccessibilityService.instance != null && com.fazz.agent.domain.models.PluginGroup.SCREEN !in off,
            notificationAccess = notificationAccess(),
            profile = s.profile,
            disabledPlugins = off.map { it.label },
        )
    }

    private suspend fun buildUserParts(text: String, imagePaths: List<String>): List<JsonObject> = withContext(Dispatchers.IO) {
        val parts = mutableListOf<JsonObject>()
        imagePaths.forEach { p ->
            val f = File(p)
            if (f.exists()) parts += AgentOrchestrator.imagePart(f.readBytes())
        }
        parts += jsonOf("text" to text.ifBlank { "Lihat gambar ini." })
        parts
    }

    private fun updateMessage(id: String, f: (ChatMessage) -> ChatMessage) =
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) f(it) else it }) }

    private fun persist() {
        val s = _state.value
        val id = s.conversationId ?: return
        if (s.messages.isEmpty()) return
        val snapshot = StoredConversation(id, s.title, System.currentTimeMillis(), s.messages, contents)
        scope.launch { store.save(snapshot) }
    }
}
