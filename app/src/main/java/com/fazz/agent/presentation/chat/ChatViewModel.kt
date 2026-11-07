package com.fazz.agent.presentation.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fazz.agent.AppContainer
import com.fazz.agent.core.agent.SessionEvent
import com.fazz.agent.core.agent.SessionState
import com.fazz.agent.core.screen.FazzAccessibilityService
import com.fazz.agent.core.tools.PendingInteraction
import com.fazz.agent.core.voice.VoiceInputState
import com.fazz.agent.data.local.ConversationMeta
import com.fazz.agent.domain.models.AgentState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ComposerState(val text: String = "", val images: List<String> = emptyList(), val attaching: Boolean = false)

/** Thin UI adapter: the agent itself lives in [com.fazz.agent.core.agent.AgentSession] (app-scoped). */
class ChatViewModel(private val c: AppContainer, private val app: Application) : ViewModel() {
    val session: StateFlow<SessionState> = c.session.state
    val conversations: StateFlow<List<ConversationMeta>> = c.session.conversations
    val pendingInteraction: StateFlow<PendingInteraction?> = c.interaction.pending
    val voiceState: StateFlow<VoiceInputState> = c.speech.state
    val speaking: StateFlow<Boolean> = c.tts.speaking
    val screenControlOn: StateFlow<Boolean> = FazzAccessibilityService.connected
    val disclosureAccepted: StateFlow<Boolean> = c.settings.settings.map { it.screenDisclosureAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun acceptDisclosure() { viewModelScope.launch { c.settings.acceptScreenDisclosure() } }
    fun openScreenSetup() = com.fazz.agent.core.screen.ScreenSetup.open(app)

    private val _composer = MutableStateFlow(ComposerState())
    val composer: StateFlow<ComposerState> = _composer.asStateFlow()

    /** Full-screen voice conversation (listen → answer aloud → listen again). */
    private val _voiceMode = MutableStateFlow(false)
    val voiceMode: StateFlow<Boolean> = _voiceMode.asStateFlow()

    val displayState: StateFlow<AgentState> = combine(session, pendingInteraction) { s, p ->
        if (p != null) AgentState.WaitingForUser else s.agentState
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AgentState.Idle)

    init {
        viewModelScope.launch {
            c.session.events.collect { ev ->
                if (ev is SessionEvent.ReplyFinished && _voiceMode.value) {
                    // Give TTS a moment to start, wait until the spoken answer ends, then listen again.
                    delay(700)
                    c.tts.speaking.first { !it }
                    if (_voiceMode.value && !session.value.isBusy) startVoice()
                }
            }
        }
    }

    fun onTextChange(t: String) = _composer.update { it.copy(text = t.take(6000)) }

    fun send(text: String = _composer.value.text, viaVoice: Boolean = false) {
        val imgs = _composer.value.images
        if (text.isBlank() && imgs.isEmpty()) return
        c.session.send(text, imgs, viaVoice)
        _composer.value = ComposerState()
    }

    fun addImageUri(uri: Uri) {
        _composer.update { it.copy(attaching = true) }
        viewModelScope.launch {
            val path = Attachments.importUri(app, uri)
            _composer.update { s -> s.copy(attaching = false, images = if (path != null) (s.images + path).takeLast(4) else s.images) }
        }
    }

    fun addCameraFile(file: File) {
        viewModelScope.launch {
            val path = Attachments.normalizeFile(app, file) ?: return@launch
            _composer.update { s -> s.copy(images = (s.images + path).takeLast(4)) }
        }
    }

    fun removeImage(path: String) = _composer.update { it.copy(images = it.images - path) }

    fun stop() { c.session.stop(); c.interaction.resolve(false) }
    fun retryLast() = c.session.retryLast()
    fun newChat() { c.session.newChat(); _composer.value = ComposerState() }
    fun openConversation(id: String) = c.session.open(id)
    fun deleteConversation(id: String) = c.session.delete(id)
    fun resolveInteraction(answer: Boolean) = c.interaction.resolve(answer)
    fun speak(text: String) = c.tts.speak(text)
    fun stopSpeaking() = c.tts.stop()

    // Voice -------------------------------------------------------------------------------
    fun startVoice() {
        c.tts.stop()
        c.speech.start { heard ->
            viewModelScope.launch {
                if (_voiceMode.value || c.settings.current().autoSendVoice) send(heard, viaVoice = true)
                else onTextChange(heard)
            }
        }
    }

    fun enterVoiceMode() { _voiceMode.value = true; startVoice() }
    fun exitVoiceMode() { _voiceMode.value = false; c.speech.stop(); c.tts.stop() }
    fun finishVoice() = c.speech.finish()
    fun cancelVoice() = c.speech.stop()
    fun clearVoiceError() = c.speech.clearError()

    override fun onCleared() {
        c.speech.stop()
        super.onCleared()
    }
}
