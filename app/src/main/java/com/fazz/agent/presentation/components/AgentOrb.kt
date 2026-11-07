package com.fazz.agent.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fazz.agent.presentation.theme.FazzColors
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMode { IDLE, THINKING, TOOL, LISTENING, SPEAKING }

/**
 * Fazz's minimal "presence": two hairline rings and an accent dot.
 * Idle = calm drift, thinking = arc sweeps, tool = orbiting dot, listening = core reacts to voice.
 */
@Composable
fun AgentOrb(mode: OrbMode, size: Dp, modifier: Modifier = Modifier, level: Float = 0f) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = when (mode) {
        OrbMode.LISTENING -> FazzColors.Danger
        else -> MaterialTheme.colorScheme.secondary
    }
    val t = rememberInfiniteTransition(label = "orb")
    val period = when (mode) {
        OrbMode.IDLE -> 12000
        OrbMode.THINKING -> 1400
        OrbMode.TOOL -> 2600
        OrbMode.LISTENING -> 5000
        OrbMode.SPEAKING -> 3000
    }
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "a")
    val breathe by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "b")
    val voice by animateFloatAsState(if (mode == OrbMode.LISTENING) level else 0f, label = "v")
    val sweep by animateFloatAsState(if (mode == OrbMode.THINKING || mode == OrbMode.TOOL) 110f else 0f, tween(500), label = "s")

    Canvas(modifier.size(size).semantics { contentDescription = "Status Fazz: ${mode.label()}" }) {
        val r = this.size.minDimension / 2f
        val c = center
        val hair = (r * 0.035f).coerceAtLeast(1.2f)
        // Outer and inner hairline rings
        drawCircle(ink.copy(alpha = 0.16f), radius = r * 0.92f, style = Stroke(hair))
        drawCircle(ink.copy(alpha = 0.28f), radius = r * 0.64f, style = Stroke(hair))
        // Working arc
        if (sweep > 1f) rotate(angle, c) {
            drawArc(
                color = accent, startAngle = 0f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - r * 0.92f, c.y - r * 0.92f),
                size = androidx.compose.ui.geometry.Size(r * 1.84f, r * 1.84f),
                style = Stroke(hair * 2.2f, cap = StrokeCap.Round),
            )
        }
        // Satellite on the inner ring
        val rad = Math.toRadians((angle * if (mode == OrbMode.IDLE) 1f else 1.5f).toDouble())
        drawCircle(ink, radius = r * 0.06f, center = Offset(c.x + (r * 0.64f * cos(rad)).toFloat(), c.y + (r * 0.64f * sin(rad)).toFloat()))
        // Core
        val core = r * 0.26f * breathe * (1f + voice * 0.6f)
        if (mode == OrbMode.LISTENING) drawCircle(accent.copy(alpha = 0.18f), radius = core * 1.9f)
        drawCircle(accent, radius = core)
    }
}

private fun OrbMode.label() = when (this) {
    OrbMode.IDLE -> "siap"
    OrbMode.THINKING -> "berpikir"
    OrbMode.TOOL -> "menjalankan tugas"
    OrbMode.LISTENING -> "mendengarkan"
    OrbMode.SPEAKING -> "berbicara"
}

@Composable
fun SmallOrb(mode: OrbMode, modifier: Modifier = Modifier) = AgentOrb(mode, 32.dp, modifier)
