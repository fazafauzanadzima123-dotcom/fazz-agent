package com.fazz.agent.core.screen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.fazz.agent.AppContainer
import com.fazz.agent.MainActivity
import com.fazz.agent.core.common.AppVisibility
import com.fazz.agent.core.tools.PendingInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Floating panel shown over other apps while Fazz is working:
 *  - always: what Fazz is doing + a STOP button (the user can halt the agent at any moment)
 *  - when approval is needed: the question with Izinkan / Tolak
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which only an enabled accessibility service can draw.
 */
class ControlOverlay(
    private val service: AccessibilityService,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private lateinit var status: TextView
    private lateinit var question: TextView
    private lateinit var approvalRow: LinearLayout
    private lateinit var allow: Button
    private lateinit var deny: Button
    private var hidden = false

    private val ink = Color.parseColor("#1E1C19")
    private val paper = Color.parseColor("#FBF8F1")
    private val nila = Color.parseColor("#2E4A7D")
    private val danger = Color.parseColor("#B3261E")

    fun start() {
        scope.launch {
            combine(container.session.state, container.interaction.pending, AppVisibility.foreground) { s, p, fg -> Triple(s, p, fg) }
                .collect { (s, pending, fg) -> render(s.isBusy && !fg, s.agentStatus, if (fg) null else pending) }
        }
    }

    fun stop() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    fun setHidden(h: Boolean) {
        hidden = h
        root?.visibility = if (h) View.INVISIBLE else View.VISIBLE
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()

    private fun ensureView(): LinearLayout {
        root?.let { return it }
        val ctx = service
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { setColor(paper); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#DDD4C4")) }
            elevation = dp(8).toFloat()
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(nila) }
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(10) }
        }
        status = TextView(ctx).apply {
            setTextColor(ink); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stopBtn = pill("STOP", danger, Color.WHITE) { container.session.stop() }
        row.addView(dot); row.addView(status); row.addView(stopBtn)
        question = TextView(ctx).apply {
            setTextColor(ink); textSize = 15f; setPadding(0, dp(10), 0, dp(8)); visibility = View.GONE
        }
        approvalRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; visibility = View.GONE }
        deny = pill("Tolak", Color.parseColor("#EDE5D6"), ink) { container.interaction.resolve(false) }
        allow = pill("Izinkan", ink, paper) { container.interaction.resolve(true) }
        approvalRow.addView(deny); approvalRow.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 1) }); approvalRow.addView(allow)
        panel.addView(row); panel.addView(question); panel.addView(approvalRow)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = dp(36)
            horizontalMargin = 0.03f
        }
        wm.addView(panel, lp)
        root = panel
        return panel
    }

    private fun pill(text: String, bg: Int, fg: Int, onClick: () -> Unit) = Button(service).apply {
        this.text = text
        isAllCaps = false
        setTextColor(fg)
        textSize = 13f
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(18).toFloat() }
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun render(working: Boolean, statusText: String, pending: PendingInteraction?) {
        if (!working && pending == null) { stop(); return }
        val v = ensureView()
        v.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        status.text = if (statusText.isBlank()) "Fazz sedang bekerja…" else "Fazz: $statusText"
        when (pending) {
            is PendingInteraction.Confirmation -> {
                question.text = pending.request.title + "\n" + pending.request.message
                question.visibility = View.VISIBLE
                approvalRow.visibility = View.VISIBLE
                allow.text = pending.request.confirmLabel
                deny.text = pending.request.denyLabel
                allow.setOnClickListener { container.interaction.resolve(true) }
            }
            is PendingInteraction.Permission -> {
                question.text = "Fazz butuh izin Android. Buka Fazz untuk memberikannya."
                question.visibility = View.VISIBLE
                approvalRow.visibility = View.VISIBLE
                allow.text = "Buka Fazz"
                deny.text = "Tolak"
                allow.setOnClickListener {
                    service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
            }
            null -> { question.visibility = View.GONE; approvalRow.visibility = View.GONE }
        }
    }
}
