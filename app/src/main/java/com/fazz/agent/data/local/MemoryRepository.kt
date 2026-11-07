package com.fazz.agent.data.local

import com.fazz.agent.core.common.JsonUtil
import com.fazz.agent.domain.models.MemoryCategory
import com.fazz.agent.domain.models.MemoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** Persistent, user-controlled memory. Separate from the temporary conversation context. */
interface MemoryRepository {
    val items: StateFlow<List<MemoryItem>>
    suspend fun load()
    suspend fun add(text: String, category: MemoryCategory): MemoryItem
    suspend fun delete(id: String)
    suspend fun clear()
}

/** Stored on-device only (app-private storage, excluded from backups). Nothing is uploaded. */
class FileMemoryRepository(private val file: File) : MemoryRepository {
    private val mutex = Mutex()
    private val serializer = ListSerializer(MemoryItem.serializer())
    private val _items = MutableStateFlow<List<MemoryItem>>(emptyList())
    override val items: StateFlow<List<MemoryItem>> = _items.asStateFlow()

    override suspend fun load() = mutex.withLock {
        _items.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyList()
            else runCatching { JsonUtil.json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
        }
    }

    override suspend fun add(text: String, category: MemoryCategory): MemoryItem = mutex.withLock {
        val item = MemoryItem(UUID.randomUUID().toString(), text.trim().take(500), category, System.currentTimeMillis())
        write(_items.value + item)
        item
    }

    override suspend fun delete(id: String) = mutex.withLock { write(_items.value.filterNot { it.id == id }) }

    override suspend fun clear() = mutex.withLock { write(emptyList()) }

    private suspend fun write(list: List<MemoryItem>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JsonUtil.json.encodeToString(serializer, list))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        }
        _items.value = list
    }
}
