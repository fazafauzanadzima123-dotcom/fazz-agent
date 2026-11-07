package com.fazz.agent.data.local

import com.fazz.agent.core.agent.HistoryTrimmer
import com.fazz.agent.core.common.JsonUtil
import com.fazz.agent.domain.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID

@Serializable
data class ConversationMeta(val id: String, val title: String, val updatedAt: Long)

@Serializable
data class StoredConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<ChatMessage> = emptyList(),
    val contents: List<JsonObject> = emptyList(),
)

/** Many conversations, stored in app-private storage (never uploaded). Images are not kept in model context on disk. */
class ConversationStore(private val dir: File) {
    private val mutex = Mutex()
    private val indexFile get() = File(dir, "index.json")
    private val indexSerializer = ListSerializer(ConversationMeta.serializer())
    private val _index = MutableStateFlow<List<ConversationMeta>>(emptyList())
    val index: StateFlow<List<ConversationMeta>> = _index.asStateFlow()

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            migrateLegacy()
            _index.value = if (indexFile.exists()) {
                runCatching { JsonUtil.json.decodeFromString(indexSerializer, indexFile.readText()) }.getOrDefault(emptyList())
            } else emptyList()
        }
    }

    suspend fun get(id: String): StoredConversation? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) null else runCatching { JsonUtil.json.decodeFromString(StoredConversation.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun save(c: StoredConversation) = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val clean = c.copy(
                messages = c.messages.takeLast(300).map { it.copy(isStreaming = false) },
                contents = HistoryTrimmer.stripImages(HistoryTrimmer.trim(c.contents)),
            )
            atomicWrite(File(dir, "${c.id}.json"), JsonUtil.json.encodeToString(StoredConversation.serializer(), clean))
            val meta = ConversationMeta(c.id, c.title, c.updatedAt)
            val list = (listOf(meta) + _index.value.filterNot { it.id == c.id }).sortedByDescending { it.updatedAt }
            atomicWrite(indexFile, JsonUtil.json.encodeToString(indexSerializer, list))
            _index.value = list
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            File(dir, "$id.json").delete()
            val list = _index.value.filterNot { it.id == id }
            atomicWrite(indexFile, JsonUtil.json.encodeToString(indexSerializer, list))
            _index.value = list
        }
    }

    suspend fun deleteAll() = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            _index.value = emptyList()
        }
    }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    /** v0.2 kept a single conversation in conversations/current.json; turn it into a normal entry. */
    private fun migrateLegacy() {
        val legacy = File(dir, "current.json")
        if (!legacy.exists()) return
        runCatching {
            val old = JsonUtil.json.decodeFromString(SavedConversation.serializer(), legacy.readText())
            if (old.messages.isNotEmpty()) {
                val id = UUID.randomUUID().toString()
                val title = titleFrom(old.messages.firstOrNull()?.text.orEmpty())
                val c = StoredConversation(id, title, old.messages.last().timestamp, old.messages, old.contents)
                File(dir, "$id.json").writeText(JsonUtil.json.encodeToString(StoredConversation.serializer(), c))
                val list = listOf(ConversationMeta(id, title, c.updatedAt))
                indexFile.writeText(JsonUtil.json.encodeToString(indexSerializer, list))
            }
        }
        legacy.delete()
    }

    companion object {
        fun titleFrom(firstMessage: String): String =
            firstMessage.trim().replace(Regex("\\s+"), " ").take(48).ifBlank { "Percakapan baru" }
    }
}
