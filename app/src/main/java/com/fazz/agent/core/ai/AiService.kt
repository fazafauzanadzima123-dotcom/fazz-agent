package com.fazz.agent.core.ai

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
    data object NotConfigured : AiError("Konfigurasi backend aplikasi belum lengkap (SUPABASE_URL / key).")
    data object BackendAuth : AiError("Backend menolak kredensial aplikasi. Periksa SUPABASE_ANON_KEY.")
    data object MissingServerKey : AiError("Server AI belum punya API key Gemini. Tambahkan GEMINI_API_KEY di Supabase → Edge Functions → Secrets.")
    data class InvalidServerKey(val detail: String) : AiError("API key Gemini di server ditolak. Perbarui GEMINI_API_KEY. ($detail)")
    data class ModelNotFound(val detail: String) : AiError("Model itu tidak tersedia. Pilih model lain di Pengaturan. ($detail)")
    data object RateLimited : AiError("Batas harian untuk perangkat ini sudah habis. Coba lagi besok.")
    data object QuotaExceeded : AiError("Kuota gratis Gemini sedang habis. Tunggu sebentar lalu coba lagi.")
    data class Network(val detail: String) : AiError("Tidak bisa terhubung ke server. Periksa koneksi internet. ($detail)")
    data object Timeout : AiError("Server terlalu lama merespons. Silakan coba lagi.")
    data class Blocked(val reason: String) : AiError("Jawaban diblokir oleh filter keamanan Gemini ($reason).")
    data class Server(val code: String, val detail: String) : AiError("Error server ($code): $detail")
}

class AiException(val error: AiError) : Exception(error.userMessage)
