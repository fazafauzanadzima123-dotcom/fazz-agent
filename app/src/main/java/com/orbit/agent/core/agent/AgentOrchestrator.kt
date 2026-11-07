package com.orbit.agent.core.agent

import com.orbit.agent.core.ai.AiError
import com.orbit.agent.core.ai.AiException
import com.orbit.agent.core.ai.AiService
import com.orbit.agent.core.ai.AiStreamEvent
import com.orbit.agent.core.ai.ChatRequest
import com.orbit.agent.core.common.jsonOf
import com.orbit.agent.core.tools.ToolExecutor
import com.orbit.agent.core.tools.ToolRegistry
import com.orbit.agent.core.tools.ToolResult
import com.orbit.agent.domain.models.TokenUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolStarted(val callId: String, val toolName: String, val label: String) : AgentEvent
    data class ToolFinished(val callId: String, val toolName: String, val result: ToolResult) : AgentEvent
    data class Usage(val usage: TokenUsage) : AgentEvent
    /** Final state of the model conversation, to be persisted. */
    data class Completed(val contents: List<JsonObject>, val finalText: String) : AgentEvent
    data class Failed(val error: AiError, val contents: List<JsonObject>) : AgentEvent
}

/**
 * The agent loop:
 *  1. send conversation + tool declarations to the model
 *  2. stream text to the UI
 *  3. if the model requested tools, run each one through [ToolExecutor] (validation, permissions,
 *     confirmation, timeout) and send the results back
 *  4. repeat until the model answers without tool calls, or [maxSteps] is reached.
 */
class AgentOrchestrator(
    private val ai: AiService,
    private val registry: ToolRegistry,
    private val executor: ToolExecutor,
    private val maxSteps: Int = 8,
) {
    fun run(
        history: List<JsonObject>,
        userText: String,
        systemInstruction: String,
        model: String,
    ): Flow<AgentEvent> = flow {
        val contents = history.toMutableList()
        contents += userContent(userText)
        val usageTotal = intArrayOf(0, 0, 0)
        val finalText = StringBuilder()

        try {
            for (step in 1..maxSteps) {
                val modelParts = mutableListOf<JsonObject>()
                val calls = mutableListOf<AiStreamEvent.FunctionCall>()
                var stepUsage: AiStreamEvent.Usage? = null
                var finishReason: String? = null

                val request = ChatRequest(model, systemInstruction, HistoryTrimmer.trim(contents.toList()).toList(), registry.declarations())
                ai.streamChat(request).collect { ev ->
                    when (ev) {
                        is AiStreamEvent.TextDelta -> {
                            modelParts += ev.part
                            finalText.append(ev.text)
                            emit(AgentEvent.TextDelta(ev.text))
                        }
                        is AiStreamEvent.FunctionCall -> { modelParts += ev.part; calls += ev }
                        is AiStreamEvent.OtherPart -> modelParts += ev.part
                        is AiStreamEvent.Usage -> stepUsage = ev // cumulative within a stream; keep last
                        is AiStreamEvent.Finished -> finishReason = ev.reason
                    }
                }
                stepUsage?.let {
                    usageTotal[0] += it.promptTokens; usageTotal[1] += it.outputTokens; usageTotal[2] += it.totalTokens
                    emit(AgentEvent.Usage(TokenUsage(usageTotal[0], usageTotal[1], usageTotal[2])))
                }

                if (modelParts.isEmpty()) {
                    val reason = finishReason ?: "EMPTY"
                    if (reason == "SAFETY" || reason == "PROHIBITED_CONTENT" || reason == "BLOCKLIST") throw AiException(AiError.Blocked(reason))
                    throw AiException(AiError.Server("empty_response", "The model returned no content ($reason)."))
                }
                contents += JsonObject(mapOf("role" to JsonPrimitive("model"), "parts" to JsonArray(mergeTextParts(modelParts))))

                if (calls.isEmpty()) {
                    emit(AgentEvent.Completed(contents.toList(), finalText.toString()))
                    return@flow
                }

                // Text emitted alongside tool calls is a preamble; the next turn continues it.
                if (finalText.isNotEmpty()) { finalText.append("\n\n"); emit(AgentEvent.TextDelta("\n\n")) }

                val responseParts = mutableListOf<JsonObject>()
                for (call in calls) {
                    val callId = call.id ?: UUID.randomUUID().toString()
                    val label = registry.get(call.name)?.activityLabel ?: "Using ${call.name}"
                    emit(AgentEvent.ToolStarted(callId, call.name, label))
                    val result = executor.execute(call.name, call.args)
                    emit(AgentEvent.ToolFinished(callId, call.name, result))
                    responseParts += functionResponsePart(call, result)
                }
                contents += JsonObject(mapOf("role" to JsonPrimitive("user"), "parts" to JsonArray(responseParts)))
            }
            // Step budget exhausted: stop safely instead of looping forever.
            val note = "I stopped after $maxSteps steps to avoid looping. Tell me how you'd like to continue."
            emit(AgentEvent.TextDelta(note))
            contents += JsonObject(mapOf("role" to JsonPrimitive("model"), "parts" to JsonArray(listOf(jsonOf("text" to note)))))
            emit(AgentEvent.Completed(contents.toList(), finalText.append(note).toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            emit(AgentEvent.Failed(e.error, sanitizeAfterFailure(contents)))
        } catch (e: Exception) {
            emit(AgentEvent.Failed(AiError.Server("client", e.message ?: e.javaClass.simpleName), sanitizeAfterFailure(contents)))
        }
    }

    companion object {
        fun userContent(text: String): JsonObject = JsonObject(
            mapOf("role" to JsonPrimitive("user"), "parts" to JsonArray(listOf(jsonOf("text" to text)))),
        )

        fun functionResponsePart(call: AiStreamEvent.FunctionCall, result: ToolResult): JsonObject {
            val fr = linkedMapOf<String, JsonElement>(
                "name" to JsonPrimitive(call.name),
                "response" to result.toModelResponse(),
            )
            call.id?.let { fr["id"] = JsonPrimitive(it) }
            return JsonObject(mapOf("functionResponse" to JsonObject(fr)))
        }

        /**
         * Streaming splits text into many small parts. Merge adjacent plain-text parts to keep history
         * compact, but never touch parts carrying extra fields (e.g. thoughtSignature), which must be
         * echoed back to Gemini exactly.
         */
        fun mergeTextParts(parts: List<JsonObject>): List<JsonObject> {
            val out = mutableListOf<JsonObject>()
            for (p in parts) {
                val isPlainText = p.keys == setOf("text")
                val last = out.lastOrNull()
                if (isPlainText && last != null && last.keys == setOf("text")) {
                    val merged = (last["text"] as JsonPrimitive).content + (p["text"] as JsonPrimitive).content
                    out[out.lastIndex] = jsonOf("text" to merged)
                } else out += p
            }
            return out
        }

        /**
         * After a failure, drop a trailing model turn with unanswered function calls (or a trailing
         * function-response turn), so the next request is a valid conversation.
         */
        fun sanitizeAfterFailure(contents: List<JsonObject>): List<JsonObject> {
            val list = contents.toMutableList()
            while (list.isNotEmpty()) {
                val last = list.last()
                val role = (last["role"] as? JsonPrimitive)?.content
                val parts = (last["parts"] as? JsonArray).orEmpty()
                val hasCall = parts.any { (it as? JsonObject)?.containsKey("functionCall") == true }
                val hasResponse = parts.any { (it as? JsonObject)?.containsKey("functionResponse") == true }
                if ((role == "model" && hasCall) || (role == "user" && hasResponse)) list.removeAt(list.lastIndex) else break
            }
            // Also drop the dangling user message so history ends on a completed exchange.
            if ((list.lastOrNull()?.get("role") as? JsonPrimitive)?.content == "user") list.removeAt(list.lastIndex)
            return list
        }
    }
}
