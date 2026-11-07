package com.orbit.agent.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Provider-neutral AI abstraction. The agent only talks to this interface, so a different
 * provider (or a different backend) can be plugged in without touching the agent or the UI.
 *
 * Conversation "contents" use the Gemini wire shape ({role, parts[]}) as the neutral format;
 * another provider implementation would translate to/from it.
 */
interface AiService {
    /** Streams one model turn. Throws [AiException] (inside the flow) on failure. */
    fun streamChat(request: ChatRequest): Flow<AiStreamEvent>

    /** Real web search with sources (never fabricated): grounded by the provider's search tool. */
    suspend fun webSearch(query: String, model: String?): WebSearchResult

    /** Lists models available to the server's credentials. */
    suspend fun listModels(): ModelList
}

data class ToolDeclaration(val name: String, val description: String, val parameters: JsonObject)

data class ChatRequest(
    val model: String,
    val systemInstruction: String,
    val contents: List<JsonObject>,
    val tools: List<ToolDeclaration>,
)

sealed interface AiStreamEvent {
    data class TextDelta(val text: String, val part: JsonObject) : AiStreamEvent
    data class FunctionCall(val id: String?, val name: String, val args: JsonObject, val part: JsonObject) : AiStreamEvent
    /** Any other part (e.g. thought signatures) that must be echoed back verbatim in history. */
    data class OtherPart(val part: JsonObject) : AiStreamEvent
    data class Usage(val promptTokens: Int, val outputTokens: Int, val totalTokens: Int) : AiStreamEvent
    data class Finished(val reason: String) : AiStreamEvent
}

data class WebSearchSource(val title: String, val url: String)

data class WebSearchResult(
    val answer: String,
    val sources: List<WebSearchSource>,
    val grounded: Boolean,
)

data class ModelList(val defaultModel: String, val models: List<String>)

sealed class AiError(val userMessage: String) {
    data object NotConfigured : AiError("The app is missing its backend configuration (SUPABASE_URL / key).")
    data object BackendAuth : AiError("The backend rejected the app's credentials. Check SUPABASE_ANON_KEY.")
    data object MissingServerKey : AiError("The AI server has no Gemini API key yet. Add GEMINI_API_KEY in Supabase → Edge Functions → Secrets.")
    data class InvalidServerKey(val detail: String) : AiError("The server's Gemini API key was rejected. Please update GEMINI_API_KEY. ($detail)")
    data class ModelNotFound(val detail: String) : AiError("That model isn't available. Choose another in Settings. ($detail)")
    data object RateLimited : AiError("Daily request limit reached for this device. Try again tomorrow.")
    data object QuotaExceeded : AiError("Gemini free-tier quota reached. Wait a minute and try again.")
    data class Network(val detail: String) : AiError("Can't reach the server. Check your internet connection. ($detail)")
    data object Timeout : AiError("The server took too long to respond. Please try again.")
    data class Blocked(val reason: String) : AiError("The response was blocked by Gemini's safety filter ($reason).")
    data class Server(val code: String, val detail: String) : AiError("Server error ($code): $detail")
}

class AiException(val error: AiError) : Exception(error.userMessage)
