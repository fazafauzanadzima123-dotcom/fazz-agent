package com.fazz.agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fazz.agent.domain.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "fazz_settings")

/** Non-sensitive app settings. No passwords or tokens are stored here (none exist in the app). */
class SettingsRepository(private val context: Context, private val defaultModel: String) {
    private object Keys {
        val model = stringPreferencesKey("model_id")
        val speak = booleanPreferencesKey("speak_replies")
        val autoSend = booleanPreferencesKey("auto_send_voice")
        val installId = stringPreferencesKey("install_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            modelId = p[Keys.model]?.takeIf { it.isNotBlank() } ?: defaultModel,
            speakReplies = p[Keys.speak] ?: false,
            autoSendVoice = p[Keys.autoSend] ?: true,
            installId = p[Keys.installId].orEmpty(),
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

    suspend fun setModel(model: String) = context.dataStore.edit { it[Keys.model] = model.trim() }
    suspend fun setSpeakReplies(v: Boolean) = context.dataStore.edit { it[Keys.speak] = v }
    suspend fun setAutoSendVoice(v: Boolean) = context.dataStore.edit { it[Keys.autoSend] = v }
}
