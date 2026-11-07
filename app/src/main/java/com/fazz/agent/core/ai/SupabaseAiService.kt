package com.fazz.agent.core.ai

import com.fazz.agent.core.common.JsonUtil
import com.fazz.agent.core.common.array
import com.fazz.agent.core.common.bool
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.obj
import com.fazz.agent.core.common.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the `agent-chat` Supabase Edge Function over HTTPS. Only the public Supabase anon key
 * is used here; the Gemini key never leaves the server.
 */
class SupabaseAiService(
    private val baseUrl: String,
    private val anonKey: String,
    private val installIdProvider: suspend () -> String,
    private val client: OkHttpClient = defaultClient(),
    /** Production always requires HTTPS; only unit tests against a local mock server disable it. */
    private val requireHttps: Boolean = true,
) : AiService {

    private val endpoint get() = baseUrl.trimEnd('/') + "/functions/v1/agent-chat"
    private val jsonMedia = "application/json".toMediaType()

    override fun streamChat(request: ChatRequest): Flow<AiStreamEvent> = flow {
        ensureConfigured()
        val body = jsonOf(
            "mode" to "chat",
            "installId" to installIdProvider(),
            "model" to request.model,
            "systemInstruction" to request.systemInstruction,
            "contents" to JsonArray(request.contents),
            "tools" to JsonArray(request.tools.map {
                jsonOf("name" to it.name, "description" to it.description, "parameters" to it.parameters)
            }),
        )
        val call = client.newCall(buildRequest(body))
        try {
            val response = executeOrThrow { call.execute() }
            response.use { res ->
                if (!res.isSuccessful) throw AiException(mapError(res.code, res.body.string()))
                val source = res.body.source()
                val parser = GeminiStreamParser()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: InterruptedIOException) {
                        throw AiException(AiError.Timeout)
                    } catch (e: IOException) {
                        throw AiException(AiError.Network(e.message ?: "connection lost"))
                    } ?: break
                    for (event in parser.acceptLine(line)) emit(event)
                }
                for (event in parser.finish()) emit(event)
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun webSearch(query: String, model: String?): WebSearchResult = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = jsonOf("mode" to "search", "installId" to installIdProvider(), "query" to query, "model" to model)
        val obj = postJson(body)
        WebSearchResult(
            answer = obj.string("answer").orEmpty(),
            sources = obj.array("sources")?.mapNotNull { el ->
                val s = el as? JsonObject ?: return@mapNotNull null
                val url = s.string("url") ?: return@mapNotNull null
                WebSearchSource(s.string("title").orEmpty(), url)
            }.orEmpty(),
            grounded = obj.bool("grounded") ?: false,
        )
    }

    override suspend fun listModels(): ModelList = withContext(Dispatchers.IO) {
        ensureConfigured()
        val obj = postJson(jsonOf("mode" to "models", "installId" to installIdProvider()))
        ModelList(
            defaultModel = obj.string("defaultModel").orEmpty(),
            models = obj.array("models")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        )
    }

    private fun ensureConfigured() {
        val schemeOk = baseUrl.startsWith("https://") || (!requireHttps && baseUrl.startsWith("http://"))
        if (!schemeOk || anonKey.isBlank()) throw AiException(AiError.NotConfigured)
    }

    private fun buildRequest(body: JsonObject): Request = Request.Builder()
        .url(endpoint)
        .header("Authorization", "Bearer $anonKey")
        .header("apikey", anonKey)
        .post(JsonUtil.json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMedia))
        .build()

    private fun postJson(body: JsonObject): JsonObject {
        val response = executeOrThrow { client.newCall(buildRequest(body)).execute() }
        response.use { res ->
            val text = res.body.string()
            if (!res.isSuccessful) throw AiException(mapError(res.code, text))
            return runCatching { JsonUtil.json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw AiException(AiError.Server("parse", "invalid JSON from server")) }
        }
    }

    private inline fun executeOrThrow(block: () -> Response): Response = try {
        block()
    } catch (e: InterruptedIOException) {
        throw AiException(AiError.Timeout)
    } catch (e: IOException) {
        throw AiException(AiError.Network(e.message ?: e.javaClass.simpleName))
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS) // per-read timeout while streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()

        /** Maps backend error JSON ({error:{code,message}} or gateway {code,message}) to [AiError]. */
        fun mapError(status: Int, body: String): AiError {
            val obj = runCatching { JsonUtil.json.parseToJsonElement(body).jsonObject }.getOrNull()
            val err = obj?.obj("error")
            val code = err?.string("code") ?: obj?.string("code") ?: "http_$status"
            val message = err?.string("message") ?: obj?.string("message") ?: body.take(200)
            return when (code) {
                "missing_api_key" -> AiError.MissingServerKey
                "invalid_api_key" -> AiError.InvalidServerKey(message)
                "model_not_found" -> AiError.ModelNotFound(message)
                "rate_limited" -> AiError.RateLimited
                "quota_exceeded" -> AiError.QuotaExceeded
                else -> when {
                    status == 401 || code.startsWith("UNAUTHORIZED") -> AiError.BackendAuth
                    status == 404 && code == "NOT_FOUND" -> AiError.Server(code, "agent-chat function not deployed")
                    else -> AiError.Server(code, message)
                }
            }
        }
    }
}
