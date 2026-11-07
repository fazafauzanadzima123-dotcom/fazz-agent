package com.fazz.agent.core.tools.impl

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.long
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** TOOL 3a: read upcoming calendar events. Read-only; requires READ_CALENDAR + user confirmation. */
class ReadCalendarTool(private val context: Context) : AgentTool {
    override val name = "read_calendar_events"
    override val description = "Read the user's upcoming calendar events from the phone's calendars (read-only). " +
        "Use for questions like 'what's on my schedule today/tomorrow/this week'."
    override val activityLabel = "Mengecek kalender"
    override val requiredPermissions = listOf(Manifest.permission.READ_CALENDAR)
    override val permissionRationale = "Fazz butuh akses kalender untuk membaca jadwalmu. Fazz tidak pernah mengubah acara sendiri."
    override val parameters = schema {
        integer("days_ahead", "How many days from the start of today to include (1 = today only).", min = 1, max = 31)
        integer("max_events", "Maximum number of events to return.", min = 1, max = 25)
    }

    override fun confirmation(args: JsonObject): ConfirmationRequest {
        val days = (args.long("days_ahead") ?: 1L)
        return ConfirmationRequest(
            title = "Bagikan kalender ke AI Fazz?",
            message = "Fazz akan membaca acara $days hari ke depan dan mengirim judul, waktu, dan lokasinya ke penyedia AI Fazz untuk menjawabmu.",
        )
    }

    override suspend fun execute(args: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val days = (args.long("days_ahead") ?: 1L)
        val max = (args.long("max_events") ?: 15L).toInt()
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone)
        val end = start.plusDays(days)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start.toInstant().toEpochMilli())
            ContentUris.appendId(it, end.toInstant().toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val events = mutableListOf<Map<String, Any?>>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && events.size < max) {
                val allDay = c.getInt(3) == 1
                events += mapOf(
                    "title" to (c.getString(0) ?: "(no title)"),
                    "start" to formatTime(c.getLong(1), allDay, zone),
                    "end" to formatTime(c.getLong(2), allDay, zone),
                    "all_day" to allDay,
                    "location" to c.getString(4)?.takeIf { it.isNotBlank() },
                    "calendar" to c.getString(5),
                )
            }
        } ?: return@withContext ToolResult.error(ErrorCodes.UNAVAILABLE, "Kalender tidak tersedia di HP ini.")

        ToolResult.ok(
            if (events.isEmpty()) "Tidak ada acara" else "${events.size} acara ditemukan",
            jsonOf("range_start" to start.toLocalDate().toString(), "range_end_exclusive" to end.toLocalDate().toString(), "events" to events),
        )
    }

    private fun formatTime(millis: Long, allDay: Boolean, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(millis)
        // All-day instances are stored in UTC midnight.
        return if (allDay) instant.atZone(ZoneId.of("UTC")).toLocalDate().toString()
        else instant.atZone(zone).toOffsetDateTime().toString()
    }
}

/**
 * TOOL 3b: propose a new event. It never writes to the calendar directly: it opens the user's
 * calendar app with the details pre-filled, and the user must tap Save there.
 */
class CreateCalendarEventTool(private val context: Context) : AgentTool {
    override val name = "create_calendar_event"
    override val description = "Prepare a new calendar event. Opens the phone's calendar app pre-filled; the user reviews and saves it themselves. " +
        "Times must be ISO-8601 local date-times like 2026-11-08T15:00 (use get_current_datetime first to resolve relative dates)."
    override val activityLabel = "Menyiapkan acara kalender"
    override val parameters = schema {
        string("title", "Event title.", required = true, maxLength = 200)
        string("start", "Start as ISO-8601 local date-time, e.g. 2026-11-08T15:00.", required = true, maxLength = 40)
        string("end", "End as ISO-8601 local date-time. Defaults to one hour after start.", maxLength = 40)
        string("location", "Optional location.", maxLength = 200)
        string("description", "Optional notes.", maxLength = 1000)
    }

    override fun validate(args: JsonObject): String? {
        val start = parse(args.string("start")) ?: return "start is not a valid ISO-8601 date-time"
        args.string("end")?.let { e ->
            val end = parse(e) ?: return "end is not a valid ISO-8601 date-time"
            if (!end.isAfter(start)) return "end must be after start"
        }
        return null
    }

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Buat acara kalender?",
        message = "\"${args.string("title")}\" mulai ${args.string("start")?.replace('T', ' ')}. Aplikasi kalender akan terbuka agar kamu bisa cek dan menyimpannya.",
        confirmLabel = "Buka kalender",
        denyLabel = "Batal",
    )

    override suspend fun execute(args: JsonObject): ToolResult {
        val start = parse(args.string("start"))!!
        val end = parse(args.string("end")) ?: start.plusHours(1)
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, args.string("title"))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toInstant().toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        args.string("location")?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        args.string("description")?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
        return try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            ToolResult.ok(
                "Kalender dibuka, tinggal tekan Simpan",
                jsonOf("note" to "The event is NOT saved yet. The user must tap Save in their calendar app."),
            )
        } catch (e: ActivityNotFoundException) {
            ToolResult.error(ErrorCodes.UNAVAILABLE, "Tidak ada aplikasi kalender yang bisa membuat acara.")
        }
    }

    companion object {
        fun parse(s: String?): ZonedDateTime? {
            if (s.isNullOrBlank()) return null
            val zone = ZoneId.systemDefault()
            return try { OffsetDateTime.parse(s).atZoneSameInstant(zone) } catch (_: DateTimeParseException) {
                try { LocalDateTime.parse(s).atZone(zone) } catch (_: DateTimeParseException) { null }
            }
        }
    }
}
