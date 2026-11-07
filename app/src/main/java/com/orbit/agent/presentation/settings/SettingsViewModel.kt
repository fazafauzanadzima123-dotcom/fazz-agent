package com.orbit.agent.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.agent.AppContainer
import com.orbit.agent.BuildConfig
import com.orbit.agent.core.ai.AiException
import com.orbit.agent.core.security.SensitiveDataDetector
import com.orbit.agent.domain.models.AppSettings
import com.orbit.agent.domain.models.MemoryCategory
import com.orbit.agent.domain.models.MemoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck
    data object Running : ConnectionCheck
    data class Ok(val defaultModel: String, val models: List<String>) : ConnectionCheck
    data class Failed(val message: String) : ConnectionCheck
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val memories: StateFlow<List<MemoryItem>> = c.memory.items

    private val _check = MutableStateFlow<ConnectionCheck>(ConnectionCheck.Idle)
    val check: StateFlow<ConnectionCheck> = _check.asStateFlow()

    val backendUrl: String = BuildConfig.SUPABASE_URL.ifBlank { "(not configured)" }

    init { viewModelScope.launch { c.memory.load(); c.settings.installId() } }

    fun setModel(id: String) = viewModelScope.launch { c.settings.setModel(id) }
    fun setSpeak(v: Boolean) = viewModelScope.launch { c.settings.setSpeakReplies(v) }
    fun setAutoSend(v: Boolean) = viewModelScope.launch { c.settings.setAutoSendVoice(v) }

    fun checkConnection() {
        _check.value = ConnectionCheck.Running
        viewModelScope.launch {
            _check.value = try {
                val list = c.aiService.listModels()
                ConnectionCheck.Ok(list.defaultModel, list.models.filter { it.contains("flash") || it.contains("pro") }.ifEmpty { list.models })
            } catch (e: AiException) {
                ConnectionCheck.Failed(e.error.userMessage)
            }
        }
    }

    /** Manual add from the Memory screen; still blocked if it looks like a secret. */
    fun addMemory(text: String, category: MemoryCategory): String? {
        val t = text.trim()
        if (t.length < 3) return "Too short."
        SensitiveDataDetector.findReason(t)?.let { return "Not saved: $it." }
        viewModelScope.launch { c.memory.add(t, category) }
        return null
    }

    fun deleteMemory(id: String) = viewModelScope.launch { c.memory.delete(id) }
    fun clearMemories() = viewModelScope.launch { c.memory.clear() }
}
