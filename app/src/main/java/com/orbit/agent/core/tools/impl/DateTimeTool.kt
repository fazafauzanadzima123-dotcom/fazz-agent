package com.orbit.agent.core.tools.impl

import com.orbit.agent.core.common.jsonOf
import com.orbit.agent.core.tools.AgentTool
import com.orbit.agent.core.tools.ToolResult
import com.orbit.agent.core.tools.schema
import kotlinx.serialization.json.JsonObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** TOOL 2: the device's current date, time and time zone. No permission needed. */
class DateTimeTool(private val clock: () -> ZonedDateTime = { ZonedDateTime.now() }) : AgentTool {
    override val name = "get_current_datetime"
    override val description = "Get the phone's current local date, time, day of week and time zone."
    override val activityLabel = "Checking the time"
    override val parameters = schema()

    override suspend fun execute(args: JsonObject): ToolResult {
        val now = clock()
        val locale = Locale.getDefault()
        val readable = now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withLocale(locale))
        return ToolResult.ok(
            readable,
            jsonOf(
                "iso_datetime" to now.toOffsetDateTime().toString(),
                "date" to now.toLocalDate().toString(),
                "time" to now.toLocalTime().withNano(0).toString(),
                "day_of_week" to now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                "time_zone" to now.zone.id,
                "utc_offset" to now.offset.id,
                "readable" to readable,
            ),
        )
    }
}
