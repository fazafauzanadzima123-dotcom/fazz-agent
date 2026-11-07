package com.fazz.agent.core.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fazz.agent.FazzApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Fazz's screen control. Enabled by the user in Android Settings → Accessibility.
 * It only acts when the agent asks it to; it does not watch or record the screen on its own.
 */
class FazzAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: FazzAccessibilityService? = null
            private set
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }

    private val nodes = ConcurrentHashMap<Int, AccessibilityNodeInfo>()
    @Volatile var lastSnapshot: ScreenSnapshot? = null
        private set
    private var overlay: ControlOverlay? = null
    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        val container = (application as FazzApp).container
        overlay = ControlOverlay(this, container, s).also { it.start() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        overlay?.stop(); overlay = null
        scope?.cancel(); scope = null
        nodes.clear()
        instance = null
        _connected.value = false
    }

    // ---------------------------------------------------------------- reading

    suspend fun snapshot(): ScreenSnapshot = withContext(Dispatchers.Main) {
        nodes.clear()
        val root = rootInActiveWindow
            ?: windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }?.root
        val dm = resources.displayMetrics
        if (root == null) {
            return@withContext ScreenSnapshot("unknown", "Tidak diketahui", dm.widthPixels, dm.heightPixels, emptyList()).also { lastSnapshot = it }
        }
        val pkg = root.packageName?.toString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val out = mutableListOf<UiElement>()
        var nextId = 1
        val rect = Rect()

        fun visit(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || out.size >= 400) return
            if (n.isVisibleToUser) {
                val text = n.text?.toString().orEmpty().trim()
                val desc = n.contentDescription?.toString().orEmpty().trim()
                val hint = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty() else ""
                val interesting = text.isNotEmpty() || desc.isNotEmpty() || n.isClickable || n.isEditable ||
                    n.isScrollable || n.isCheckable
                if (interesting) {
                    n.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        val id = nextId++
                        nodes[id] = n
                        @Suppress("DEPRECATION") val isOn = n.isChecked
                        out += UiElement(
                            id = id,
                            role = roleOf(n),
                            text = text,
                            description = desc,
                            hint = hint,
                            resourceId = n.viewIdResourceName.orEmpty(),
                            clickable = n.isClickable || n.isLongClickable,
                            editable = n.isEditable,
                            scrollable = n.isScrollable,
                            checked = if (n.isCheckable) isOn else null,
                            focused = n.isFocused,
                            centerX = rect.centerX(),
                            centerY = rect.centerY(),
                        )
                    }
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it, depth + 1) }
        }
        visit(root, 0)
        ScreenSnapshot(pkg, label, dm.widthPixels, dm.heightPixels, out).also { lastSnapshot = it }
    }

    private fun roleOf(n: AccessibilityNodeInfo): String {
        val cls = n.className?.toString().orEmpty().substringAfterLast('.')
        return when {
            n.isEditable -> "EditText"
            cls.contains("Switch") -> "Switch"
            cls.contains("CheckBox") -> "CheckBox"
            cls.contains("Button") -> "Button"
            cls.contains("Image") -> if (n.isClickable) "ImageButton" else "Image"
            cls.contains("RecyclerView") || cls.contains("ListView") || cls.contains("ScrollView") -> "List"
            cls.contains("TextView") -> if (n.isClickable) "Button" else "Text"
            n.isClickable -> "Clickable"
            cls.isNotEmpty() -> cls
            else -> "View"
        }
    }

    // ---------------------------------------------------------------- acting

    suspend fun click(id: Int, long: Boolean = false): Boolean = withContext(Dispatchers.Main) {
        val node = nodes[id] ?: return@withContext false
        var n: AccessibilityNodeInfo? = node
        val wanted = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        while (n != null && !(if (long) n.isLongClickable else n.isClickable)) n = n.parent
        if (n != null && n.performAction(wanted)) return@withContext true
        val r = Rect().also { node.getBoundsInScreen(it) }
        tapAt(r.centerX().toFloat(), r.centerY().toFloat(), if (long) 650 else 60)
    }

    suspend fun setText(id: Int, text: String): Boolean = withContext(Dispatchers.Main) {
        val node = nodes[id] ?: return@withContext false
        if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Presses the keyboard's Enter/Search/Send key on the focused input (Android 11+). */
    suspend fun pressEnter(): Boolean = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < 30) return@withContext false
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return@withContext false
        focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    suspend fun scroll(direction: String, id: Int?): Boolean = withContext(Dispatchers.Main) {
        val target = id?.let { nodes[it] } ?: nodes.values.filter { it.isScrollable }.maxByOrNull {
            val r = Rect(); it.getBoundsInScreen(r); r.width() * r.height()
        }
        val forward = direction == "down" || direction == "right"
        if (target != null) {
            val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (target.performAction(action)) return@withContext true
        }
        // Fallback: swipe gesture across the middle of the screen.
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f; val cy = dm.heightPixels / 2f
        val dy = dm.heightPixels * 0.3f; val dx = dm.widthPixels * 0.3f
        when (direction) {
            "down" -> swipe(cx, cy + dy, cx, cy - dy)
            "up" -> swipe(cx, cy - dy, cx, cy + dy)
            "right" -> swipe(cx + dx, cy, cx - dx, cy)
            else -> swipe(cx - dx, cy, cx + dx, cy)
        }
    }

    fun globalAction(key: String): Boolean = performGlobalAction(
        when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return false
        },
    )

    suspend fun tapAt(x: Float, y: Float, durationMs: Long = 60): Boolean =
        gesture(Path().apply { moveTo(x, y) }, durationMs)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, durationMs)

    private suspend fun gesture(path: Path, durationMs: Long): Boolean = withContext(Dispatchers.Main) {
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    /** Screenshot as a compressed JPEG (Android 11+). The control overlay is hidden while capturing. */
    suspend fun screenshotJpeg(maxSide: Int = 960, quality: Int = 70): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        overlay?.setHidden(true)
        try {
            kotlinx.coroutines.delay(120)
            val bmp: Bitmap = suspendCancellableCoroutine { cont ->
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (cont.isActive) cont.resume(soft)
                    }
                    override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
                })
            } ?: return null
            val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        } finally {
            overlay?.setHidden(false)
        }
    }
}
