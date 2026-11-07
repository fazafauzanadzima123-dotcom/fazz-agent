package com.fazz.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fazz.agent.domain.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "fazz_settings")

/** Non-sensitive app settings. No passwords or tokens are stored here (none exist in the app). */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val speak = booleanPreferencesKey("speak_replies")
        val autoSend = booleanPreferencesKey("auto_send_voice")
        val installId = stringPreferencesKey("install_id")
        val briefingOn = booleanPreferencesKey("briefing_enabled")
        val briefingHour = intPreferencesKey("briefing_hour")
        val screenDisclosure = booleanPreferencesKey("screen_disclosure_accepted")
        val style = stringPreferencesKey("response_style")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            speakReplies = p[Keys.speak] ?: false,
            autoSendVoice = p[Keys.autoSend] ?: true,
            installId = p[Keys.installId].orEmpty(),
            briefingEnabled = p[Keys.briefingOn] ?: false,
            briefingHour = p[Keys.briefingHour] ?: 7,
            screenDisclosureAccepted = p[Keys.screenDisclosure] ?: false,
            responseStyle = p[Keys.style] ?: "normal",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Random per-install identifier used only for server-side rate limiting. Not tied to the user. */
    suspend fun installId(): String {
        context.dataStore.data.first()[Keys.installId]?.let { if (it.isNotBlank()) return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.installId] = id }
        return id
    }

    suspend fun setSpeakReplies(v: Boolean) = context.dataStore.edit { it[Keys.speak] = v }
    suspend fun setAutoSendVoice(v: Boolean) = context.dataStore.edit { it[Keys.autoSend] = v }
    suspend fun setBriefing(enabled: Boolean, hour: Int) = context.dataStore.edit { it[Keys.briefingOn] = enabled; it[Keys.briefingHour] = hour }
    suspend fun setResponseStyle(id: String) = context.dataStore.edit { it[Keys.style] = id }
    suspend fun acceptScreenDisclosure() = context.dataStore.edit { it[Keys.screenDisclosure] = true }
}
