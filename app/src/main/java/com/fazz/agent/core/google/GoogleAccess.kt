package com.fazz.agent.core.google

import android.accounts.Account
import android.content.Context
import com.fazz.agent.core.common.string
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The Google services Fazz can use, with the minimum scopes each needs. */
enum class GoogleService(val id: String, val label: String, val description: String, val scopes: List<String>) {
    CALENDAR("kalender", "Google Kalender", "Lihat jadwal dan buat acara di kalender Google-mu.", listOf("https://www.googleapis.com/auth/calendar")),
    GMAIL("gmail", "Gmail", "Cari dan ringkas email, buat draf, dan kirim setelah kamu setujui.",
        listOf("https://www.googleapis.com/auth/gmail.readonly", "https://www.googleapis.com/auth/gmail.compose")),
    DRIVE("drive", "Google Drive & Docs", "Simpan hasil kerja Fazz sebagai Google Docs (hanya berkas yang dibuat Fazz).", listOf("https://www.googleapis.com/auth/drive.file")),
    ;
    companion object { fun from(id: String) = entries.firstOrNull { it.id == id } }
}

val SignInScopes = listOf("openid", "email", "profile")

data class GoogleProfile(val name: String, val email: String, val photoUrl: String)

/** Result of asking Google for an access token. [pendingIntent] is set when the user must approve first. */
data class GoogleGrant(val accessToken: String?, val pendingIntent: android.app.PendingIntent?)

/**
 * Talks to Google Play services for OAuth access tokens. Tokens are never stored by Fazz: Play services caches
 * the user's consent and hands out short-lived tokens on request.
 */
class GoogleAccess(private val context: Context) {
    private fun request(scopes: List<String>, email: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { Scope(it) })
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()

    private fun fromResult(r: AuthorizationResult) = GoogleGrant(r.accessToken, if (r.hasResolution()) r.pendingIntent else null)

    /** Asks for a token; the result tells the caller whether a consent screen must be shown. */
    suspend fun authorize(scopes: List<String>, email: String?): GoogleGrant = suspendCancellableCoroutine { cont: CancellableContinuation<GoogleGrant> ->
        Identity.getAuthorizationClient(context).authorize(request(scopes, email))
            .addOnSuccessListener { cont.resume(fromResult(it)) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Reads the token out of the consent screen's result. */
    fun grantFromIntent(data: android.content.Intent?): GoogleGrant =
        fromResult(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))

    /** A usable token without any UI, or null when consent is missing or expired. */
    suspend fun silentToken(service: GoogleService, email: String?): String? =
        runCatching { authorize(service.scopes, email).accessToken }.getOrNull()

    suspend fun profile(accessToken: String): GoogleProfile? = GoogleApi.get("https://www.googleapis.com/oauth2/v3/userinfo", accessToken)?.let {
        GoogleProfile(it.string("name").orEmpty(), it.string("email").orEmpty(), it.string("picture").orEmpty())
    }
}

/** Minimal JSON-over-HTTPS helper for Google REST APIs. */
object GoogleApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    class ApiError(val code: Int, message: String) : Exception(message)

    private suspend fun call(req: Request): JsonObject? = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.string("message") }.getOrNull()
                throw ApiError(res.code, msg ?: "Google error ${res.code}")
            }
            if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
        }
    }

    suspend fun get(url: String, token: String): JsonObject? =
        call(Request.Builder().url(url).header("Authorization", "Bearer $token").build())

    suspend fun post(url: String, token: String, body: JsonObject): JsonObject? =
        call(Request.Builder().url(url).header("Authorization", "Bearer $token").post(body.toString().toRequestBody(JSON_TYPE)).build())

    /** Drive multipart upload: JSON metadata + raw media in one request. */
    suspend fun multipart(url: String, token: String, metadata: JsonObject, mediaType: String, media: String): JsonObject? {
        val boundary = "fazz" + System.nanoTime()
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: $mediaType; charset=UTF-8\r\n\r\n$media\r\n--$boundary--"
        return call(
            Request.Builder().url(url).header("Authorization", "Bearer $token")
                .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType())).build(),
        )
    }
}
