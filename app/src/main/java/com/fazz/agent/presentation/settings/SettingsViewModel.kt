package com.fazz.agent.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fazz.agent.AppContainer
import com.fazz.agent.core.ai.AiException
import com.fazz.agent.core.screen.FazzAccessibilityService
import com.fazz.agent.core.security.SensitiveDataDetector
import com.fazz.agent.domain.models.AppSettings
import com.fazz.agent.domain.models.MemoryCategory
import com.fazz.agent.domain.models.MemoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck
    data object Running : ConnectionCheck
    data object Ok : ConnectionCheck
    data class Failed(val message: String) : ConnectionCheck
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val memories: StateFlow<List<MemoryItem>> = c.memory.items
    val screenControlOn: StateFlow<Boolean> = FazzAccessibilityService.connected

    private val _check = MutableStateFlow<ConnectionCheck>(ConnectionCheck.Idle)
    val check: StateFlow<ConnectionCheck> = _check.asStateFlow()

    init { viewModelScope.launch { c.memory.load(); c.settings.installId() } }

    fun notificationAccessOn(): Boolean = c.notifications.listenerEnabled()

    fun setSpeak(v: Boolean) = viewModelScope.launch { c.settings.setSpeakReplies(v) }
    fun setAutoSend(v: Boolean) = viewModelScope.launch { c.settings.setAutoSendVoice(v) }
    fun savePersonal(nickname: String, about: String, instructions: String) = viewModelScope.launch { c.settings.setPersonal(nickname.trim(), about.trim(), instructions.trim()) }
    fun setTone(id: String) = viewModelScope.launch { c.settings.setTone(id) }
    fun setAnswerLength(id: String) = viewModelScope.launch { c.settings.setAnswerLength(id) }
    fun setEmoji(id: String) = viewModelScope.launch { c.settings.setEmoji(id) }
    fun setLocationAllowed(v: Boolean) = viewModelScope.launch { c.settings.setLocationAllowed(v) }
    fun setPluginEnabled(id: String, enabled: Boolean) = viewModelScope.launch { c.settings.setPluginEnabled(id, enabled) }
    fun markSetupSeen() = viewModelScope.launch { c.settings.markSetupSeen() }
    fun setStyle(id: String) = viewModelScope.launch { c.settings.setResponseStyle(id) }
    fun acceptScreenDisclosure() = viewModelScope.launch { c.settings.acceptScreenDisclosure() }

    fun setBriefing(enabled: Boolean, hour: Int) = viewModelScope.launch {
        c.settings.setBriefing(enabled, hour)
        if (enabled) c.briefing.schedule(hour) else c.briefing.cancel()
    }

    fun runBriefingNow() = c.briefing.runNow()

    fun checkConnection() {
        _check.value = ConnectionCheck.Running
        viewModelScope.launch {
            _check.value = try {
                c.aiService.listModels()
                ConnectionCheck.Ok
            } catch (e: AiException) {
                ConnectionCheck.Failed(e.error.userMessage)
            }
        }
    }

    /** Manual add from the Memory screen; still blocked if it looks like a secret. */
    fun addMemory(text: String, category: MemoryCategory): String? {
        val t = text.trim()
        if (t.length < 3) return "Terlalu pendek."
        SensitiveDataDetector.findReason(t)?.let { return "Tidak disimpan: $it." }
        viewModelScope.launch { c.memory.add(t, category) }
        return null
    }

    fun deleteMemory(id: String) = viewModelScope.launch { c.memory.delete(id) }
    fun clearMemories() = viewModelScope.launch { c.memory.clear() }
}
