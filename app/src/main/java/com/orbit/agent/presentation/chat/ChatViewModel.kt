package com.orbit.agent.presentation.chat

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.agent.AppContainer
import com.orbit.agent.core.agent.AgentEvent
import com.orbit.agent.core.agent.HistoryTrimmer
import com.orbit.agent.core.agent.SystemPrompt
import com.orbit.agent.core.tools.ErrorCodes
import com.orbit.agent.core.tools.PendingInteraction
import com.orbit.agent.core.voice.SpeechInputController
import com.orbit.agent.core.voice.TextToSpeechController
import com.orbit.agent.core.voice.VoiceInputState
import com.orbit.agent.data.local.SavedConversation
import com.orbit.agent.domain.models.AgentState
import com.orbit.agent.domain.models.ChatMessage
import com.orbit.agent.domain.models.MessageRole
import com.orbit.agent.domain.models.ToolActivity
import com.orbit.agent.domain.models.ToolStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.time.ZonedDateTime
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val agentState: AgentState = AgentState.Idle,
    val isBusy: Boolean = false,
    val loaded: Boolean = false,
)

class ChatViewModel(private val c: AppContainer, app: Application) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    val pendingInteraction: StateFlow<PendingInteraction?> = c.interaction.pending
    val speech = SpeechInputController(app)
    val tts = TextToSpeechController(app)
    val voiceState: StateFlow<VoiceInputState> = speech.state
    val speaking: StateFlow<Boolean> = tts.speaking

    /** Agent state shown in the indicator, including "waiting for you" while a dialog is open. */
    val displayState: StateFlow<AgentState> = combine(_state, pendingInteraction) { s, p ->
        if (p != null) AgentState.WaitingForUser else s.agentState
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AgentState.Idle)

    private var contents: List<JsonObject> = emptyList()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            c.memory.load()
            val saved = c.conversations.load()
            contents = saved.contents
            _state.update { it.copy(messages = saved.messages, loaded = true) }
        }
    }

    fun onInputChange(text: String) = _state.update { it.copy(input = text.take(4000)) }

    fun send(text: String = _state.value.input, viaVoice: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty() || _state.value.isBusy) return
        tts.stop()
        val now = System.currentTimeMillis()
        val user = ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, clean, now, viaVoice = viaVoice)
        val assistantId = UUID.randomUUID().toString()
        val assistant = ChatMessage(assistantId, MessageRole.ASSISTANT, "", now, isStreaming = true)
        _state.update { it.copy(messages = it.messages + user + assistant, input = "", isBusy = true, agentState = AgentState.Thinking) }

        job = viewModelScope.launch {
            var finalText = ""
            try {
                val settings = c.settings.current()
                val system = SystemPrompt.build(ZonedDateTime.now(), c.memory.items.value, "${Build.MANUFACTURER} ${Build.MODEL}")
                c.orchestrator.run(contents, clean, system, settings.modelId).collect { ev -> handle(assistantId, ev) { finalText = it } }
                if (finalText.isNotBlank() && (viaVoice || settings.speakReplies)) tts.speak(finalText)
            } finally {
                updateMessage(assistantId) { m ->
                    if (m.isStreaming && m.text.isEmpty() && m.error == null) m.copy(isStreaming = false, error = "Stopped.")
                    else m.copy(isStreaming = false)
                }
                _state.update { it.copy(isBusy = false, agentState = AgentState.Idle) }
                persist()
            }
        }
    }

    private fun handle(id: String, ev: AgentEvent, onFinal: (String) -> Unit) {
        when (ev) {
            is AgentEvent.TextDelta -> {
                updateMessage(id) { it.copy(text = it.text + ev.text) }
                _state.update { it.copy(agentState = AgentState.Responding) }
            }
            is AgentEvent.ToolStarted -> {
                updateMessage(id) { it.copy(tools = it.tools + ToolActivity(ev.callId, ev.toolName, ev.label, ToolStatus.RUNNING)) }
                _state.update { it.copy(agentState = AgentState.UsingTool(ev.label)) }
            }
            is AgentEvent.ToolFinished -> {
                val status = when {
                    ev.result.success -> ToolStatus.SUCCESS
                    ev.result.errorCode == ErrorCodes.USER_DECLINED || ev.result.errorCode == ErrorCodes.PERMISSION_DENIED -> ToolStatus.DENIED
                    else -> ToolStatus.FAILED
                }
                updateMessage(id) { m ->
                    m.copy(tools = m.tools.map { t -> if (t.callId == ev.callId) t.copy(status = status, detail = ev.result.summary) else t })
                }
                _state.update { it.copy(agentState = AgentState.Thinking) }
            }
            is AgentEvent.Usage -> updateMessage(id) { it.copy(usage = ev.usage) }
            is AgentEvent.Completed -> {
                contents = ev.contents
                onFinal(ev.finalText)
                updateMessage(id) { it.copy(isStreaming = false) }
            }
            is AgentEvent.Failed -> {
                contents = ev.contents
                updateMessage(id) { it.copy(isStreaming = false, error = ev.error.userMessage) }
            }
        }
    }

    /** Re-send the last user message after an error. */
    fun retryLast() {
        val msgs = _state.value.messages
        val lastUser = msgs.lastOrNull { it.role == MessageRole.USER } ?: return
        val idx = msgs.indexOf(lastUser)
        _state.update { it.copy(messages = msgs.subList(0, idx)) }
        send(lastUser.text, lastUser.viaVoice)
    }

    fun stop() {
        job?.cancel()
        c.interaction.resolve(false)
        tts.stop()
    }

    fun newChat() {
        stop()
        contents = emptyList()
        _state.update { it.copy(messages = emptyList(), input = "") }
        viewModelScope.launch { c.conversations.clear() }
    }

    fun resolveInteraction(answer: Boolean) = c.interaction.resolve(answer)

    // Voice -------------------------------------------------------------------------------
    fun startVoice() {
        tts.stop()
        speech.start { heard ->
            viewModelScope.launch {
                if (c.settings.current().autoSendVoice) send(heard, viaVoice = true)
                else onInputChange(heard)
            }
        }
    }

    fun finishVoice() = speech.finish()
    fun cancelVoice() = speech.stop()
    fun clearVoiceError() = speech.clearError()
    fun stopSpeaking() = tts.stop()

    // -------------------------------------------------------------------------------------
    private fun updateMessage(id: String, f: (ChatMessage) -> ChatMessage) =
        _state.update { s -> s.copy(messages = s.messages.map { if (it.id == id) f(it) else it }) }

    private fun persist() {
        val snapshot = SavedConversation(_state.value.messages.takeLast(200), HistoryTrimmer.trim(contents))
        viewModelScope.launch { c.conversations.save(snapshot) }
    }

    override fun onCleared() {
        speech.stop()
        tts.shutdown()
        super.onCleared()
    }
}
