package com.fazz.agent.core.tools.impl

import android.util.Base64
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.long
import com.fazz.agent.core.common.string
import com.fazz.agent.core.google.GoogleApi
import com.fazz.agent.core.google.GoogleService
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.time.ZoneId

/** Supplies a short-lived access token for a connected Google service, or null if the user has not connected it. */
fun interface GoogleTokens { suspend fun token(service: GoogleService): String? }

private const val NOT_CONNECTED = "Akun Google belum terhubung untuk layanan ini. Minta pengguna membuka Sesuaikan → Akun Google, lalu menghubungkannya."
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private suspend inline fun googleCall(tokens: GoogleTokens, service: GoogleService, block: (String) -> ToolResult): ToolResult {
    val token = tokens.token(service) ?: return ToolResult.error("google_not_connected", NOT_CONNECTED)
    return try {
        block(token)
    } catch (e: GoogleApi.ApiError) {
        when (e.code) {
            401, 403 -> ToolResult.error("google_not_connected", "Izin Google ditolak atau kedaluwarsa. Hubungkan ulang di Sesuaikan → Akun Google. (${e.message})")
            else -> ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Google: ${e.message}")
        }
    }
}

class GoogleCalendarListTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "google_calendar_events"
    override val description = "List upcoming events from the user's Google Calendar (not the phone's local calendar)."
    override val activityLabel = "Membaca Google Kalender"
    override val timeoutMs = 25_000L
    override val parameters = schema {
        integer("days_ahead", "How many days ahead to look (default 7).", min = 1, max = 60)
        integer("max_results", "Maximum events (default 10).", min = 1, max = 25)
    }

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.CALENDAR) { token ->
        val days = args.long("days_ahead") ?: 7
        val max = args.long("max_results") ?: 10
        val now = java.time.Instant.now()
        val url = "https://www.googleapis.com/calendar/v3/calendars/primary/events?singleEvents=true&orderBy=startTime" +
            "&maxResults=$max&timeMin=${enc(now.toString())}&timeMax=${enc(now.plusSeconds(days * 86400).toString())}"
        val items = GoogleApi.get(url, token)?.get("items")?.jsonArray ?: JsonArray(emptyList())
        val events = items.map {
            val o = it.jsonObject
            val start = o["start"]?.jsonObject
            jsonOf("id" to o.string("id"), "title" to o.string("summary"), "start" to (start?.string("dateTime") ?: start?.string("date")), "location" to o.string("location"))
        }
        ToolResult.ok("${events.size} acara", jsonOf("events" to events))
    }
}

class GoogleCalendarCreateTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "google_calendar_create_event"
    override val description = "Create an event in the user's Google Calendar. The user must approve first."
    override val activityLabel = "Membuat acara Google Kalender"
    override val timeoutMs = 25_000L
    override val parameters = schema {
        string("title", "Event title.", required = true, maxLength = 200)
        string("start", "Start, ISO-8601 with offset, e.g. 2026-11-09T15:00:00+07:00.", required = true, maxLength = 40)
        string("end", "End, ISO-8601 with offset.", required = true, maxLength = 40)
        string("description", "Optional notes.", maxLength = 1000)
    }

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Buat acara di Google Kalender?",
        message = "\"${args.string("title")}\"\n${args.string("start")} sampai ${args.string("end")}",
        confirmLabel = "Buat", denyLabel = "Batal",
    )

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.CALENDAR) { token ->
        val zone = ZoneId.systemDefault().id
        val body = buildJsonObject {
            put("summary", args.string("title").orEmpty())
            args.string("description")?.let { put("description", it) }
            put("start", buildJsonObject { put("dateTime", args.string("start").orEmpty()); put("timeZone", zone) })
            put("end", buildJsonObject { put("dateTime", args.string("end").orEmpty()); put("timeZone", zone) })
        }
        val res = GoogleApi.post("https://www.googleapis.com/calendar/v3/calendars/primary/events", token, body)
        ToolResult.ok("Acara dibuat", jsonOf("id" to res?.string("id"), "link" to res?.string("htmlLink")))
    }
}

class GmailSearchTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "gmail_search"
    override val description = "Search the user's Gmail (Gmail search syntax, e.g. 'from:bank newer_than:7d') and return sender, subject, date and a snippet. Read-only."
    override val activityLabel = "Mencari di Gmail"
    override val timeoutMs = 30_000L
    override val parameters = schema {
        string("query", "Gmail search query.", required = true, maxLength = 300)
        integer("max_results", "Maximum emails (default 5).", min = 1, max = 10)
    }

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.GMAIL) { token ->
        val max = args.long("max_results") ?: 5
        val list = GoogleApi.get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=$max&q=${enc(args.string("query").orEmpty())}", token)
        val ids = list?.get("messages")?.jsonArray?.mapNotNull { it.jsonObject.string("id") } ?: emptyList()
        val out = ids.map { id ->
            val m = GoogleApi.get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date", token)
            val headers = m?.get("payload")?.jsonObject?.get("headers")?.jsonArray?.associate { it.jsonObject.string("name").orEmpty() to it.jsonObject.string("value").orEmpty() } ?: emptyMap()
            jsonOf("id" to id, "from" to headers["From"], "subject" to headers["Subject"], "date" to headers["Date"], "snippet" to m?.string("snippet"))
        }
        ToolResult.ok("${out.size} email", jsonOf("emails" to out))
    }
}

class GmailReadTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "gmail_read"
    override val description = "Read the text of one Gmail message by id (from gmail_search). Read-only; long bodies are truncated."
    override val activityLabel = "Membaca email"
    override val timeoutMs = 25_000L
    override val parameters = schema { string("id", "Message id from gmail_search.", required = true, maxLength = 64) }

    private fun decode(data: String?): String = runCatching { String(Base64.decode(data.orEmpty(), Base64.URL_SAFE), Charsets.UTF_8) }.getOrDefault("")

    private fun findText(part: JsonObject): String? {
        if (part.string("mimeType") == "text/plain") part["body"]?.jsonObject?.string("data")?.let { return decode(it) }
        part["parts"]?.jsonArray?.forEach { findText(it.jsonObject)?.let { t -> return t } }
        return null
    }

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.GMAIL) { token ->
        val id = args.string("id").orEmpty().filter { it.isLetterOrDigit() }
        val m = GoogleApi.get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=full", token)
        val payload = m?.get("payload")?.jsonObject
        val body = (payload?.let { findText(it) } ?: m?.string("snippet")).orEmpty().take(4000)
        ToolResult.ok("Email dibaca", jsonOf("text" to body))
    }
}

private fun rfc822(to: String, subject: String, body: String): String {
    fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    val raw = "To: $to\r\nSubject: =?UTF-8?B?${b64(subject)}?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n${b64(body)}"
    return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
}

class GmailDraftTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "gmail_create_draft"
    override val description = "Save a draft email in the user's Gmail. Nothing is sent."
    override val activityLabel = "Membuat draf Gmail"
    override val timeoutMs = 25_000L
    override val parameters = schema {
        string("to", "Recipient email address.", required = true, maxLength = 200)
        string("subject", "Subject.", required = true, maxLength = 200)
        string("body", "Plain-text body.", required = true, maxLength = 8000)
    }

    override fun validate(args: JsonObject): String? =
        if (args.string("to").orEmpty().contains(Regex("[\\r\\n,;]")) || !args.string("to").orEmpty().contains("@")) "alamat penerima tidak valid" else null

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.GMAIL) { token ->
        val raw = rfc822(args.string("to").orEmpty(), args.string("subject").orEmpty(), args.string("body").orEmpty())
        val res = GoogleApi.post("https://gmail.googleapis.com/gmail/v1/users/me/drafts", token, buildJsonObject { put("message", buildJsonObject { put("raw", raw) }) })
        ToolResult.ok("Draf tersimpan di Gmail", jsonOf("draft_id" to res?.string("id")))
    }
}

class GmailSendTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "gmail_send"
    override val description = "Send an email from the user's Gmail. Irreversible: the user must approve the exact message first."
    override val activityLabel = "Mengirim email"
    override val timeoutMs = 25_000L
    override val parameters = schema {
        string("to", "Recipient email address.", required = true, maxLength = 200)
        string("subject", "Subject.", required = true, maxLength = 200)
        string("body", "Plain-text body.", required = true, maxLength = 8000)
    }

    override fun validate(args: JsonObject): String? =
        if (args.string("to").orEmpty().contains(Regex("[\\r\\n,;]")) || !args.string("to").orEmpty().contains("@")) "alamat penerima tidak valid" else null

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Kirim email ke ${args.string("to")}?",
        message = "Subjek: ${args.string("subject")}\n\n${args.string("body").orEmpty().take(500)}",
        confirmLabel = "Kirim", denyLabel = "Batal",
    )

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.GMAIL) { token ->
        val raw = rfc822(args.string("to").orEmpty(), args.string("subject").orEmpty(), args.string("body").orEmpty())
        val res = GoogleApi.post("https://gmail.googleapis.com/gmail/v1/users/me/messages/send", token, buildJsonObject { put("raw", raw) })
        ToolResult.ok("Email terkirim", jsonOf("message_id" to res?.string("id")))
    }
}

class DriveSaveDocTool(private val tokens: GoogleTokens) : AgentTool {
    override val name = "drive_save_document"
    override val description = "Save text as a new Google Doc in the user's Drive (a file Fazz creates; Fazz cannot open other Drive files). The user must approve."
    override val activityLabel = "Menyimpan ke Google Docs"
    override val timeoutMs = 40_000L
    override val parameters = schema {
        string("title", "Document title.", required = true, maxLength = 120)
        string("content", "Document text (plain text; blank lines separate paragraphs).", required = true, maxLength = 100_000)
    }

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Simpan ke Google Docs?", message = "Dokumen baru: \"${args.string("title")}\"", confirmLabel = "Simpan", denyLabel = "Batal",
    )

    private fun html(text: String) = "<html><body>" + text.split(Regex("\n{2,}")).joinToString("") { p ->
        "<p>" + p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
    } + "</body></html>"

    override suspend fun execute(args: JsonObject): ToolResult = googleCall(tokens, GoogleService.DRIVE) { token ->
        val meta = buildJsonObject { put("name", args.string("title").orEmpty()); put("mimeType", "application/vnd.google-apps.document") }
        val res = GoogleApi.multipart("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink", token, meta, "text/html", html(args.string("content").orEmpty()))
        ToolResult.ok("Disimpan ke Google Docs", jsonOf("id" to res?.string("id"), "link" to res?.string("webViewLink")))
    }
}
