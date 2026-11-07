package com.fazz.agent.core.tools.impl

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.KeyEvent
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.long
import com.fazz.agent.core.common.string
import com.fazz.agent.core.proactive.NotificationStore
import com.fazz.agent.core.proactive.ReminderScheduler
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.ZonedDateTime

/** Resolves "Budi" or "0812…" to a phone number using the user's contacts. */
internal object ContactResolver {
    data class Match(val name: String, val number: String)

    fun resolve(context: Context, who: String): List<Match> {
        val digits = who.filter { it.isDigit() || it == '+' }
        if (who.count { it.isDigit() } >= 6) return listOf(Match(who, digits))
        val out = mutableListOf<Match>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%" + who.replace("%", "").trim() + "%"),
            null,
        )?.use { c -> while (c.moveToNext() && out.size < 6) out += Match(c.getString(0) ?: who, c.getString(1) ?: continue) }
        return out.distinctBy { it.number.filter(Char::isDigit) }
    }
}

class SendSmsTool(private val context: Context) : AgentTool {
    override val name = "send_sms"
    override val description = "Send an SMS directly (no app UI). 'to' is a phone number or a contact name. The user always approves the exact text first."
    override val activityLabel = "Mengirim SMS"
    override val requiredPermissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    override val permissionRationale = "Fazz butuh izin SMS untuk mengirim pesan yang kamu setujui, dan izin kontak untuk mencari nomornya."
    override val parameters = schema {
        string("to", "Phone number or contact name.", required = true, maxLength = 80)
        string("message", "SMS text.", required = true, maxLength = 1000)
    }
    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        "Kirim SMS ke ${args.string("to")}?", "\"${args.string("message")}\"", "Kirim", "Batal",
    )
    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val to = args.string("to")!!
        val matches = ContactResolver.resolve(context, to)
        if (matches.isEmpty()) return@withContext ToolResult.error(ErrorCodes.NOT_FOUND, "Kontak \"$to\" tidak ditemukan.")
        if (matches.size > 1) return@withContext ToolResult.error(
            "ambiguous", "Ada beberapa kontak cocok. [Model: ask the user which one.]",
            jsonOf("candidates" to matches.map { mapOf("name" to it.name, "number" to it.number) }),
        )
        val m = matches.single()
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(args.string("message")!!)
        sms.sendMultipartTextMessage(m.number, null, parts, null, null)
        ToolResult.ok("SMS terkirim ke ${m.name}", jsonOf("to" to m.name, "number" to m.number))
    }
}

class CallPhoneTool(private val context: Context) : AgentTool {
    override val name = "call_phone"
    override val description = "Start a phone call to a number or contact name. The user approves first."
    override val activityLabel = "Menelepon"
    override val requiredPermissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
    override val permissionRationale = "Fazz butuh izin telepon dan kontak untuk menelepon orang yang kamu minta."
    override val parameters = schema { string("to", "Phone number or contact name.", required = true, maxLength = 80) }
    override fun confirmation(args: JsonObject) = ConfirmationRequest("Telepon ${args.string("to")}?", "Panggilan akan langsung dimulai.", "Telepon", "Batal")
    override suspend fun execute(args: JsonObject): ToolResult {
        val to = args.string("to")!!
        val matches = withContext(Dispatchers.IO) { ContactResolver.resolve(context, to) }
        if (matches.isEmpty()) return ToolResult.error(ErrorCodes.NOT_FOUND, "Kontak \"$to\" tidak ditemukan.")
        if (matches.size > 1) return ToolResult.error("ambiguous", "Ada beberapa kontak cocok. [Model: ask which one.]",
            jsonOf("candidates" to matches.map { mapOf("name" to it.name, "number" to it.number) }))
        val m = matches.single()
        withContext(Dispatchers.Main) {
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(m.number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return ToolResult.ok("Menelepon ${m.name}", jsonOf("to" to m.name))
    }
}

class ComposeEmailTool(private val context: Context) : AgentTool {
    override val name = "compose_email"
    override val description = "Open the email app with a new email pre-filled (to, subject, body). To actually send, use the screen tools to tap Send (needs approval)."
    override val activityLabel = "Menyiapkan email"
    override val parameters = schema {
        string("to", "Recipient email address(es), comma-separated.", required = true, maxLength = 300)
        string("subject", "Subject.", maxLength = 200)
        string("body", "Body text.", maxLength = 5000)
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val uri = Uri.parse("mailto:" + args.string("to")!!.replace(" ", "") +
            "?subject=" + Uri.encode(args.string("subject").orEmpty()) + "&body=" + Uri.encode(args.string("body").orEmpty()))
        return try {
            withContext(Dispatchers.Main) { context.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ToolResult.ok("Email disiapkan", jsonOf("note" to "Email app opened with a draft; not sent yet."))
        } catch (e: ActivityNotFoundException) {
            ToolResult.error(ErrorCodes.UNAVAILABLE, "Tidak ada aplikasi email.")
        }
    }
}

class NavigateTool(private val context: Context) : AgentTool {
    override val name = "navigate_to"
    override val description = "Open Google Maps with directions or a place search."
    override val activityLabel = "Membuka navigasi"
    override val parameters = schema {
        string("destination", "Place name or address.", required = true, maxLength = 200)
        string("mode", "Travel mode.", enum = listOf("driving", "walking", "two_wheeler", "transit", "search"))
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val dest = args.string("destination")!!
        val mode = args.string("mode") ?: "driving"
        val uri = when (mode) {
            "search" -> Uri.parse("geo:0,0?q=" + Uri.encode(dest))
            else -> Uri.parse("google.navigation:q=" + Uri.encode(dest) + "&mode=" + when (mode) { "walking" -> "w"; "two_wheeler" -> "l"; "transit" -> "r"; else -> "d" })
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            ToolResult.ok("Maps dibuka: $dest")
        } catch (e: ActivityNotFoundException) {
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(dest))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            ToolResult.ok("Peta dibuka di browser: $dest")
        }
    }
}

class OpenUrlTool(private val context: Context) : AgentTool {
    override val name = "open_url"
    override val description = "Open a URL or app deep link. Useful: WhatsApp chat with text pre-filled: https://wa.me/<number_with_country_code>?text=<urlencoded>; " +
        "Telegram: https://t.me/<username>; any website. Opening never sends anything by itself."
    override val activityLabel = "Membuka tautan"
    override val parameters = schema { string("url", "http(s) URL or deep link.", required = true, maxLength = 2000) }
    override fun validate(args: JsonObject): String? {
        val u = args.string("url").orEmpty().lowercase()
        return if (u.startsWith("javascript:") || u.startsWith("file:") || u.startsWith("content:") || u.startsWith("intent:")) "this URL scheme is not allowed" else null
    }
    override suspend fun execute(args: JsonObject): ToolResult = try {
        withContext(Dispatchers.Main) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(args.string("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        ToolResult.ok("Tautan dibuka")
    } catch (e: ActivityNotFoundException) {
        ToolResult.error(ErrorCodes.UNAVAILABLE, "Tidak ada aplikasi untuk membuka tautan ini.")
    }
}

class SetAlarmTool(private val context: Context) : AgentTool {
    override val name = "set_alarm"
    override val description = "Set an alarm in the Clock app at a given hour and minute (24h)."
    override val activityLabel = "Memasang alarm"
    override val parameters = schema {
        integer("hour", "0-23", required = true, min = 0, max = 23)
        integer("minute", "0-59", required = true, min = 0, max = 59)
        string("label", "Optional label.", maxLength = 60)
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val h = args.long("hour")!!.toInt(); val m = args.long("minute")!!.toInt()
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        args.string("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            ToolResult.ok("Alarm %02d.%02d dipasang".format(h, m))
        } catch (e: ActivityNotFoundException) {
            ToolResult.error(ErrorCodes.UNAVAILABLE, "Tidak ada aplikasi jam yang mendukung alarm.")
        }
    }
}

class SetReminderTool(private val scheduler: ReminderScheduler) : AgentTool {
    override val name = "set_reminder"
    override val description = "Schedule a reminder notification from Fazz. Give either in_minutes or at (ISO-8601 local date-time)."
    override val activityLabel = "Memasang pengingat"
    override val parameters = schema {
        string("text", "What to remind.", required = true, maxLength = 200)
        integer("in_minutes", "Minutes from now.", min = 1, max = 60 * 24 * 30)
        string("at", "ISO-8601 local date-time, e.g. 2026-11-08T07:30.", maxLength = 40)
    }
    override fun validate(args: JsonObject): String? {
        if (args.long("in_minutes") == null && args.string("at") == null) return "give in_minutes or at"
        args.string("at")?.let { if (CreateCalendarEventTool.parse(it) == null) return "at is not a valid date-time" }
        return null
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val delay = args.long("in_minutes")?.let { Duration.ofMinutes(it) }
            ?: Duration.between(ZonedDateTime.now(), CreateCalendarEventTool.parse(args.string("at"))!!)
        if (delay.isNegative) return ToolResult.error(ErrorCodes.INVALID_ARGUMENTS, "Waktunya sudah lewat.")
        scheduler.schedule(args.string("text")!!, delay)
        val mins = delay.toMinutes()
        return ToolResult.ok("Pengingat dalam ${if (mins >= 60) "${mins / 60} jam ${mins % 60} menit" else "$mins menit"}")
    }
}

class MediaControlTool(private val context: Context) : AgentTool {
    override val name = "media_control"
    override val description = "Control whatever music/video is playing: play, pause, next, previous."
    override val activityLabel = "Mengontrol musik"
    override val parameters = schema { string("action", "Media action.", required = true, enum = listOf("play", "pause", "next", "previous")) }
    override suspend fun execute(args: JsonObject): ToolResult {
        val code = when (args.string("action")) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val am = context.getSystemService(AudioManager::class.java)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
        return ToolResult.ok("Media: ${args.string("action")}")
    }
}

class ReadNotificationsTool(private val store: NotificationStore) : AgentTool {
    override val name = "read_notifications"
    override val description = "Read recent phone notifications (messages, emails, app alerts) to summarize or prioritize them. Optionally filter by app name."
    override val activityLabel = "Membaca notifikasi"
    override val parameters = schema {
        integer("limit", "Max notifications (newest first).", min = 1, max = 60)
        string("app", "Optional app name filter, e.g. WhatsApp.", maxLength = 40)
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        if (!store.listenerEnabled()) return ToolResult.error("notification_access_disabled",
            "Akses notifikasi belum aktif. [Model: tell the user to enable it in Fazz → Pengaturan → Notifikasi.]")
        val items = store.recent(args.long("limit")?.toInt() ?: 25, args.string("app"))
        return ToolResult.ok("${items.size} notifikasi", jsonOf("notifications" to items.map {
            mapOf("app" to it.appLabel, "title" to it.title, "text" to it.text, "time" to it.timeLabel())
        }))
    }
}
