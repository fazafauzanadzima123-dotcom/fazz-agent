package com.orbit.agent.core.ai

import com.orbit.agent.core.common.JsonUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Gemini `streamGenerateContent?alt=sse` output. Pure Kotlin so it is unit-testable.
 * Feed raw lines with [acceptLine]; complete events are returned as they become available.
 */
class GeminiStreamParser {
    private val dataBuffer = StringBuilder()

    fun acceptLine(line: String): List<AiStreamEvent> {
        val trimmed = line.trimEnd('\r')
        if (trimmed.isEmpty()) return flush()
        if (trimmed.startsWith("data:")) {
            if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
            dataBuffer.append(trimmed.removePrefix("data:").trimStart())
        }
        // Comments (":") and other SSE fields are ignored.
        return emptyList()
    }

    /** Call at end of stream to emit any trailing event without a blank-line terminator. */
    fun finish(): List<AiStreamEvent> = flush()

    private fun flush(): List<AiStreamEvent> {
        if (dataBuffer.isEmpty()) return emptyList()
        val payload = dataBuffer.toString()
        dataBuffer.clear()
        return parseChunk(payload)
    }

    companion object {
        fun parseChunk(payload: String): List<AiStreamEvent> {
            val root = runCatching { JsonUtil.json.parseToJsonElement(payload).jsonObject }.getOrNull()
                ?: return emptyList()
            val events = mutableListOf<AiStreamEvent>()

            root["error"]?.let { err ->
                val msg = runCatching { err.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw AiException(AiError.Server("stream", msg ?: "unknown streaming error"))
            }

            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
            parts?.forEach { el ->
                val part = el as? JsonObject ?: return@forEach
                val isThought = (part["thought"] as? JsonPrimitive)?.booleanOrNull == true
                val fc = part["functionCall"] as? JsonObject
                val text = (part["text"] as? JsonPrimitive)?.contentOrNull
                when {
                    // Gemini often ends a stream with {"text": ""}; echoing it back can be rejected.
                    text != null && text.isEmpty() && part.keys == setOf("text") -> Unit
                    fc != null -> {
                        val name = (fc["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        val args = fc["args"] as? JsonObject ?: JsonObject(emptyMap())
                        val id = (fc["id"] as? JsonPrimitive)?.contentOrNull
                        events += AiStreamEvent.FunctionCall(id, name, args, part)
                    }
                    text != null && !isThought -> events += AiStreamEvent.TextDelta(text, part)
                    else -> events += AiStreamEvent.OtherPart(part)
                }
            }

            root["usageMetadata"]?.jsonObject?.let { u ->
                fun i(k: String) = (u[k] as? JsonPrimitive)?.intOrNull ?: 0
                events += AiStreamEvent.Usage(i("promptTokenCount"), i("candidatesTokenCount"), i("totalTokenCount"))
            }

            val finish = (candidate?.get("finishReason") as? JsonPrimitive)?.contentOrNull
            if (finish != null) events += AiStreamEvent.Finished(finish)

            val blockReason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            if (blockReason != null) throw AiException(AiError.Blocked(blockReason))
            return events
        }
    }
}
