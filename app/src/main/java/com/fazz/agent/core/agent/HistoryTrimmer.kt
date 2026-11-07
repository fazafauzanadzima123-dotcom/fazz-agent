package com.fazz.agent.core.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keeps the context window bounded. Cuts only at a user turn that contains plain text, so a
 * functionCall is never separated from its functionResponse.
 */
object HistoryTrimmer {
    const val MAX_CONTENTS = 40
    private const val MAX_CHARS = 120_000

    fun trim(contents: List<JsonObject>): List<JsonObject> {
        if (contents.size <= MAX_CONTENTS && totalChars(contents) <= MAX_CHARS) return contents
        val boundaries = contents.indices.filter { isUserText(contents[it]) }
        for (start in boundaries) {
            val slice = contents.subList(start, contents.size)
            if (slice.size <= MAX_CONTENTS && totalChars(slice) <= MAX_CHARS) return slice
        }
        // Fall back to the last user text turn onward.
        return boundaries.lastOrNull()?.let { contents.subList(it, contents.size) } ?: contents.takeLast(1)
    }

    private fun isUserText(c: JsonObject): Boolean {
        if ((c["role"] as? JsonPrimitive)?.content != "user") return false
        val parts = c["parts"] as? JsonArray ?: return false
        return parts.any { (it as? JsonObject)?.containsKey("text") == true } &&
            parts.none { (it as? JsonObject)?.containsKey("functionResponse") == true }
    }

    private fun totalChars(list: List<JsonObject>) = list.sumOf { it.toString().length }
}
