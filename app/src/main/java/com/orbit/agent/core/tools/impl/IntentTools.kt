package com.orbit.agent.core.tools.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.orbit.agent.core.common.jsonOf
import com.orbit.agent.core.common.long
import com.orbit.agent.core.common.string
import com.orbit.agent.core.tools.AgentTool
import com.orbit.agent.core.tools.ConfirmationRequest
import com.orbit.agent.core.tools.ErrorCodes
import com.orbit.agent.core.tools.ToolResult
import com.orbit.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** Opens Android's share sheet with text; the user picks the target app and sends it themselves. */
class ShareTextTool(private val context: Context) : AgentTool {
    override val name = "share_text"
    override val description = "Open the Android share sheet with some text so the user can send it via any app (WhatsApp, email, notes...). " +
        "The user chooses the app and sends it themselves."
    override val activityLabel = "Preparing to share"
    override val parameters = schema {
        string("text", "The text to share.", required = true, maxLength = 4000)
    }

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Share this text?",
        message = args.string("text")!!.take(400),
        confirmLabel = "Choose app",
        denyLabel = "Cancel",
    )

    override suspend fun execute(args: JsonObject): ToolResult {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, args.string("text"))
        val chooser = Intent.createChooser(send, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { context.startActivity(chooser) }
        return ToolResult.ok("Share sheet opened", jsonOf("note" to "The user picks the app and sends it; nothing was sent automatically."))
    }
}

/** Starts a countdown timer in the phone's Clock app. */
class SetTimerTool(private val context: Context) : AgentTool {
    override val name = "set_timer"
    override val description = "Start a countdown timer in the phone's Clock app."
    override val activityLabel = "Setting a timer"
    override val parameters = schema {
        integer("seconds", "Timer length in seconds.", required = true, min = 1, max = 86_400)
        string("label", "Optional label.", maxLength = 60)
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val seconds = args.long("seconds")!!.toInt()
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        args.string("label")?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return try {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            ToolResult.ok("Timer for ${formatDuration(seconds)} sent to Clock", jsonOf("seconds" to seconds))
        } catch (e: ActivityNotFoundException) {
            ToolResult.error(ErrorCodes.UNAVAILABLE, "No clock app on this phone supports timers.")
        }
    }

    private fun formatDuration(s: Int): String = buildList {
        if (s >= 3600) add("${s / 3600}h")
        if (s % 3600 >= 60) add("${s % 3600 / 60}m")
        if (s % 60 > 0) add("${s % 60}s")
    }.joinToString(" ")
}
