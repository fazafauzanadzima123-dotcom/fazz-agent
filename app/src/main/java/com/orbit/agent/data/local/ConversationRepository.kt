package com.orbit.agent.data.local

import com.orbit.agent.core.common.JsonUtil
import com.orbit.agent.domain.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File

/** The saved chat: what the UI shows plus the raw model context needed to continue it. */
@Serializable
data class SavedConversation(
    val messages: List<ChatMessage> = emptyList(),
    val contents: List<JsonObject> = emptyList(),
)

/** Stores the current conversation in app-private storage on the device. */
class ConversationRepository(private val file: File) {
    suspend fun load(): SavedConversation = withContext(Dispatchers.IO) {
        if (!file.exists()) SavedConversation()
        else runCatching { JsonUtil.json.decodeFromString(SavedConversation.serializer(), file.readText()) }
            .getOrDefault(SavedConversation())
    }

    suspend fun save(conversation: SavedConversation) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        // Streaming placeholders are never persisted as "in progress".
        val clean = conversation.copy(messages = conversation.messages.map { it.copy(isStreaming = false) })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JsonUtil.json.encodeToString(SavedConversation.serializer(), clean))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete(); Unit }
}
