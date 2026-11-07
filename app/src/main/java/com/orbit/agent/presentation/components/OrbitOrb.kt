package com.orbit.agent.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.agent.presentation.theme.OrbitColors
import kotlin.math.cos
import kotlin.math.sin

enum class OrbMode { IDLE, THINKING, TOOL, LISTENING, SPEAKING }

/**
 * The assistant's "presence": a glowing core with orbiting rings. Motion encodes state:
 * slow drift when idle, fast spin while thinking, reacts to voice level while listening.
 */
@Composable
fun OrbitOrb(mode: OrbMode, size: Dp, modifier: Modifier = Modifier, level: Float = 0f) {
    val transition = rememberInfiniteTransition(label = "orb")
    val speed = when (mode) {
        OrbMode.IDLE -> 9000
        OrbMode.THINKING -> 1600
        OrbMode.TOOL -> 2400
        OrbMode.LISTENING -> 4000
        OrbMode.SPEAKING -> 3000
    }
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "angle")
    val breathe by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breathe")
    val voice by animateFloatAsState(if (mode == OrbMode.LISTENING) level else 0f, label = "voice")
    val accent = when (mode) {
        OrbMode.LISTENING -> OrbitColors.Danger
        OrbMode.TOOL -> OrbitColors.Orchid
        else -> OrbitColors.Teal
    }

    Canvas(modifier.semantics { contentDescription = "Orbit status: ${mode.name.lowercase()}" }.then(Modifier)) {
        val r = this.size.minDimension / 2f
        val c = center
        // Glow
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), center = c, radius = r),
            radius = r,
        )
        // Rings
        val ringStroke = (r * 0.06f).coerceAtLeast(1.5f)
        rotate(angle, c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(OrbitColors.Indigo, OrbitColors.Orchid.copy(alpha = 0.2f), OrbitColors.Indigo), c),
                radius = r * 0.78f,
                style = Stroke(ringStroke),
            )
        }
        rotate(-angle * 1.6f, c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Color.Transparent, accent, Color.Transparent), c),
                radius = r * 0.62f,
                style = Stroke(ringStroke * 0.7f),
            )
        }
        // Satellite
        val rad = Math.toRadians(angle.toDouble() * 1.3)
        drawCircle(OrbitColors.Orchid, radius = r * 0.07f, center = Offset(c.x + (r * 0.78f * cos(rad)).toFloat(), c.y + (r * 0.78f * sin(rad)).toFloat()))
        // Core
        val coreR = r * 0.34f * breathe * (1f + voice * 0.45f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), accent, OrbitColors.Indigo.copy(alpha = 0.6f)), center = c, radius = coreR),
            radius = coreR,
        )
    }
}

@Composable
fun SmallOrb(mode: OrbMode, modifier: Modifier = Modifier) = OrbitOrb(mode, 36.dp, modifier)
