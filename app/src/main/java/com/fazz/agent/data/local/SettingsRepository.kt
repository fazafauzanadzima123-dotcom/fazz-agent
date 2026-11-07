package com.fazz.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.fazz.agent.domain.models.Profile
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
        val setupSeen = booleanPreferencesKey("setup_seen")
        val nickname = stringPreferencesKey("nickname")
        val about = stringPreferencesKey("about")
        val instructions = stringPreferencesKey("instructions")
        val tone = stringPreferencesKey("tone")
        val length = stringPreferencesKey("answer_length")
        val emoji = stringPreferencesKey("emoji")
        val location = booleanPreferencesKey("location_allowed")
        val disabledPlugins = stringSetPreferencesKey("disabled_plugins")
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
            setupSeen = p[Keys.setupSeen] ?: false,
            nickname = p[Keys.nickname].orEmpty(),
            about = p[Keys.about].orEmpty(),
            instructions = p[Keys.instructions].orEmpty(),
            tone = p[Keys.tone] ?: "netral",
            answerLength = p[Keys.length] ?: "otomatis",
            emoji = p[Keys.emoji] ?: "sedikit",
            locationAllowed = p[Keys.location] ?: false,
            disabledPlugins = p[Keys.disabledPlugins] ?: emptySet(),
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
    suspend fun setPersonal(nickname: String, about: String, instructions: String) = context.dataStore.edit {
        it[Keys.nickname] = nickname.take(Profile.MAX_NICKNAME)
        it[Keys.about] = about.take(Profile.MAX_ABOUT)
        it[Keys.instructions] = instructions.take(Profile.MAX_INSTRUCTIONS)
    }
    suspend fun setTone(id: String) = context.dataStore.edit { it[Keys.tone] = id }
    suspend fun setAnswerLength(id: String) = context.dataStore.edit { it[Keys.length] = id }
    suspend fun setEmoji(id: String) = context.dataStore.edit { it[Keys.emoji] = id }
    suspend fun setLocationAllowed(v: Boolean) = context.dataStore.edit { it[Keys.location] = v }
    suspend fun setPluginEnabled(id: String, enabled: Boolean) = context.dataStore.edit {
        val cur = it[Keys.disabledPlugins] ?: emptySet()
        it[Keys.disabledPlugins] = if (enabled) cur - id else cur + id
    }
    suspend fun markSetupSeen() = context.dataStore.edit { it[Keys.setupSeen] = true }
    suspend fun setResponseStyle(id: String) = context.dataStore.edit { it[Keys.style] = id }
    suspend fun acceptScreenDisclosure() = context.dataStore.edit { it[Keys.screenDisclosure] = true }
}
