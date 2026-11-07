package com.fazz.agent.core.screen

import com.fazz.agent.core.common.bool
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.long
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Access to the live accessibility service; null when the user hasn't enabled screen control. */
fun interface ScreenControllerProvider {
    fun get(): FazzAccessibilityService?
}

private val notEnabled = ToolResult.error(
    "accessibility_disabled",
    "Kontrol layar belum aktif. [Model: tell the user to enable it in Fazz → Pengaturan → Kontrol layar, then stop.]",
)

/** Waits for the UI to settle after an action, then returns the new screen so the model sees the effect. */
private suspend fun afterAction(svc: FazzAccessibilityService, summary: String, settleMs: Long = 900): ToolResult {
    delay(settleMs)
    val s = svc.snapshot()
    return ToolResult.ok(summary, jsonOf("screen_after" to ScreenFormatter.format(s)))
}

class ReadScreenTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "read_screen"
    override val description = "Read what is currently on the phone screen as a numbered list of elements " +
        "(buttons, text, input fields). Use the numbers with tap/type_text. Call it after opening an app and after each step to verify."
    override val activityLabel = "Membaca layar"
    override val parameters = schema()
    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val s = svc.snapshot()
        return ToolResult.ok("${s.appLabel}: ${s.elements.size} elemen", jsonOf("screen" to ScreenFormatter.format(s)))
    }
}

class TapTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "tap"
    override val description = "Tap an element from the latest read_screen by its number. Set long_press for a long press. " +
        "Set irreversible=true when the tap sends, posts, pays, buys, deletes, calls or confirms something; the user will be asked first."
    override val activityLabel = "Mengetuk"
    override val parameters = schema {
        integer("element", "Element number from read_screen.", required = true, min = 1, max = 2000)
        boolean("long_press", "Long-press instead of tap.")
        boolean("irreversible", "True if this action cannot be undone (send/pay/post/delete/call/confirm).")
    }

    override fun confirmation(args: JsonObject): ConfirmationRequest? {
        val id = args.long("element")?.toInt() ?: return null
        val snap = p.get()?.lastSnapshot
        val label = snap?.labelOf(id) ?: "elemen #$id"
        val flagged = args.bool("irreversible") == true
        val word = IrreversibleGuard.riskyWord(label)
        if (!flagged && word == null) return null
        return ConfirmationRequest(
            title = "Izinkan Fazz menekan \"${label.take(40)}\"?",
            message = "Di ${snap?.appLabel ?: "aplikasi ini"}. Tindakan ini mungkin tidak bisa dibatalkan.",
            confirmLabel = "Izinkan",
            denyLabel = "Tolak",
        )
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val id = args.long("element")!!.toInt()
        val label = svc.lastSnapshot?.labelOf(id) ?: return ToolResult.error(ErrorCodes.INVALID_ARGUMENTS, "Baca layar dulu (read_screen).")
        if (svc.lastSnapshot?.find(id) == null) return ToolResult.error(ErrorCodes.NOT_FOUND, "Elemen #$id tidak ada di layar terakhir. Panggil read_screen lagi.")
        val ok = svc.click(id, long = args.bool("long_press") == true)
        if (!ok) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Gagal mengetuk \"$label\".")
        return afterAction(svc, "Mengetuk \"${label.take(40)}\"")
    }
}

class TypeTextTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "type_text"
    override val description = "Type text into an input field from read_screen (replaces its content). " +
        "Set press_enter=true to submit with the keyboard's enter/search key (only for searches; for sending messages tap the send button instead)."
    override val activityLabel = "Mengetik"
    override val parameters = schema {
        integer("element", "Number of an editable element from read_screen.", required = true, min = 1, max = 2000)
        string("text", "Text to type.", required = true, maxLength = 2000)
        boolean("press_enter", "Press the keyboard enter/search key afterwards.")
    }

    override fun validate(args: JsonObject): String? {
        val t = args.string("text").orEmpty()
        return if (com.fazz.agent.core.security.SensitiveDataDetector.findReason(t) != null)
            "refusing to type what looks like a password, PIN, OTP or card number; ask the user to type it themselves" else null
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val id = args.long("element")!!.toInt()
        val el = svc.lastSnapshot?.find(id) ?: return ToolResult.error(ErrorCodes.NOT_FOUND, "Elemen #$id tidak ada. Panggil read_screen lagi.")
        if (!el.editable) return ToolResult.error(ErrorCodes.INVALID_ARGUMENTS, "Elemen #$id (${el.label()}) bukan kolom isian.")
        if (!svc.setText(id, args.string("text")!!)) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Gagal mengetik di \"${el.label()}\".")
        if (args.bool("press_enter") == true) svc.pressEnter()
        return afterAction(svc, "Mengetik di \"${el.label().take(30)}\"", 600)
    }
}

class ScrollTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "scroll"
    override val description = "Scroll the screen (or a specific scrollable element) up, down, left or right to reveal more content."
    override val activityLabel = "Menggulir layar"
    override val parameters = schema {
        string("direction", "Direction to move the content into view.", required = true, enum = listOf("down", "up", "left", "right"))
        integer("element", "Optional scrollable element number.", min = 1, max = 2000)
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val ok = svc.scroll(args.string("direction")!!, args.long("element")?.toInt())
        if (!ok) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Tidak bisa menggulir.")
        return afterAction(svc, "Menggulir ${args.string("direction")}", 700)
    }
}

class PressKeyTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "press_key"
    override val description = "Press a system key: back, home, recents, or notifications (opens the notification shade)."
    override val activityLabel = "Menekan tombol sistem"
    override val parameters = schema {
        string("key", "Which key.", required = true, enum = listOf("back", "home", "recents", "notifications"))
    }
    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        if (!svc.globalAction(args.string("key")!!)) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Tombol gagal ditekan.")
        return afterAction(svc, "Tombol ${args.string("key")}", 800)
    }
}

class SwipeTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "swipe"
    override val description = "Swipe or tap at exact screen coordinates (pixels). Use only when an element has no number in read_screen, " +
        "e.g. based on look_at_screen. For a tap, give the same start and end point."
    override val activityLabel = "Menggeser layar"
    override val parameters = schema {
        integer("x1", "Start x", required = true, min = 0, max = 10000)
        integer("y1", "Start y", required = true, min = 0, max = 10000)
        integer("x2", "End x", required = true, min = 0, max = 10000)
        integer("y2", "End y", required = true, min = 0, max = 10000)
        boolean("irreversible", "True if this gesture sends/pays/posts/deletes/confirms something.")
    }
    override fun confirmation(args: JsonObject) = if (args.bool("irreversible") == true)
        ConfirmationRequest("Izinkan Fazz melakukan aksi ini?", "Gerakan di layar yang mungkin tidak bisa dibatalkan.", "Izinkan", "Tolak") else null

    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val x1 = args.long("x1")!!.toFloat(); val y1 = args.long("y1")!!.toFloat()
        val x2 = args.long("x2")!!.toFloat(); val y2 = args.long("y2")!!.toFloat()
        val ok = if (x1 == x2 && y1 == y2) svc.tapAt(x1, y1) else svc.swipe(x1, y1, x2, y2)
        if (!ok) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Gerakan gagal.")
        return afterAction(svc, "Gerakan di layar", 800)
    }
}

class LookAtScreenTool(private val p: ScreenControllerProvider) : AgentTool {
    override val name = "look_at_screen"
    override val description = "Take a screenshot and look at it (vision). Use when read_screen is not enough: images, maps, games, unlabeled icons. " +
        "Coordinates in the image map to screen pixels via the given scale."
    override val activityLabel = "Melihat layar"
    override val timeoutMs = 20_000L
    override val parameters = schema()
    override suspend fun execute(args: JsonObject): ToolResult {
        val svc = p.get() ?: return notEnabled
        val jpeg = svc.screenshotJpeg() ?: return ToolResult.error(ErrorCodes.UNAVAILABLE, "Screenshot tidak tersedia (butuh Android 11+ atau aplikasi ini memblokirnya).")
        val dm = svc.resources.displayMetrics
        val longSide = maxOf(dm.widthPixels, dm.heightPixels)
        val scale = longSide / 960.0
        return ToolResult(
            success = true,
            data = jsonOf("note" to "Screenshot attached as the next image.", "screen_px" to "${dm.widthPixels}x${dm.heightPixels}", "image_to_screen_scale" to "%.3f".format(scale)),
            summary = "Screenshot diambil",
            imageJpeg = jpeg,
        )
    }
}

class WaitTool : AgentTool {
    override val name = "wait"
    override val description = "Wait a few seconds for an app or page to finish loading."
    override val activityLabel = "Menunggu"
    override val parameters = schema { integer("seconds", "1 to 8 seconds.", required = true, min = 1, max = 8) }
    override suspend fun execute(args: JsonObject): ToolResult {
        delay(args.long("seconds")!! * 1000)
        return ToolResult.ok("Menunggu ${args.long("seconds")} detik")
    }
}
