package com.orbit.agent.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.agent.domain.models.AgentState
import com.orbit.agent.domain.models.ChatMessage
import com.orbit.agent.domain.models.ToolActivity
import com.orbit.agent.domain.models.ToolStatus
import com.orbit.agent.presentation.theme.OrbitColors

fun AgentState.toOrbMode(): OrbMode = when (this) {
    AgentState.Idle -> OrbMode.IDLE
    AgentState.Thinking, AgentState.Responding -> OrbMode.THINKING
    is AgentState.UsingTool -> OrbMode.TOOL
    AgentState.WaitingForUser -> OrbMode.TOOL
}

fun AgentState.statusText(): String = when (this) {
    AgentState.Idle -> "Ready"
    AgentState.Thinking -> "Thinking"
    AgentState.Responding -> "Responding"
    is AgentState.UsingTool -> label
    AgentState.WaitingForUser -> "Waiting for you"
}

/** Status line under the title: animated dots while the agent is working. */
@Composable
fun AgentActivityIndicator(state: AgentState, modifier: Modifier = Modifier) {
    val busy = state != AgentState.Idle
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200)), label = "phase")
    val dots = if (busy) ".".repeat(phase.toInt() + 1).padEnd(3, ' ') else ""
    Row(modifier.semantics { contentDescription = "Agent status: ${state.statusText()}" }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(
                when (state) {
                    AgentState.Idle -> OrbitColors.Success
                    is AgentState.UsingTool, AgentState.WaitingForUser -> OrbitColors.Orchid
                    else -> OrbitColors.Teal
                },
            ),
        )
        Spacer(Modifier.width(6.dp))
        Text(state.statusText() + dots, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun UserBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (message.viaVoice) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                        Spacer(Modifier.width(4.dp))
                        Text("voice", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantMessage(message: ChatMessage, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(end = 24.dp).animateContentSize()) {
        SmallOrb(if (message.isStreaming) OrbMode.THINKING else OrbMode.IDLE, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            message.tools.forEach { ToolChip(it) ; Spacer(Modifier.height(6.dp)) }
            if (message.text.isNotEmpty()) {
                Text(
                    renderMarkdown(message.text + if (message.isStreaming) " ▍" else "", colors.secondary, colors.surfaceVariant),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
            } else if (message.isStreaming && message.tools.isEmpty()) {
                TypingDots()
            }
            message.error?.let { ErrorCard(it, onRetry) }
            message.usage?.takeIf { !message.isStreaming && it.totalTokens > 0 }?.let {
                Spacer(Modifier.height(4.dp))
                Text("${it.totalTokens} tokens", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = a)))
        }
    }
}

@Composable
fun ToolChip(tool: ToolActivity) {
    val (icon, tint) = when (tool.status) {
        ToolStatus.RUNNING -> null to OrbitColors.Orchid
        ToolStatus.SUCCESS -> Icons.Rounded.CheckCircle to OrbitColors.Success
        ToolStatus.FAILED -> Icons.Rounded.ErrorOutline to OrbitColors.Danger
        ToolStatus.DENIED -> Icons.Rounded.Block to OrbitColors.Warning
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Tool ${tool.label}: ${tool.status.name.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) CircularProgressIndicator(Modifier.size(13.dp), color = tint, strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(tool.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            tool.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OrbitColors.Danger.copy(alpha = 0.12f))
            .border(1.dp, OrbitColors.Danger.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = OrbitColors.Danger)
        Spacer(Modifier.width(8.dp))
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Retry") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Greeting(greeting: String, suggestions: List<String>, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitOrb(OrbMode.IDLE, 148.dp)
        Spacer(Modifier.height(20.dp))
        Text(greeting, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ask me anything, or tell me what to do on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s ->
                Surface(
                    onClick = { onSuggestion(s) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { Text(s, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).widthIn(min = 1.dp),
                placeholder = { Text("Message Orbit") },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(onClick = onMic, enabled = !busy, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = if (busy) onStop else onSend,
                enabled = busy || text.isNotBlank(),
                modifier = Modifier.padding(bottom = 4.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (busy) OrbitColors.Danger else MaterialTheme.colorScheme.primary),
            ) {
                if (busy) Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

/** Shown instead of the input bar while the microphone is open — a clear privacy indicator. */
@Composable
fun ListeningPanel(level: Float, partial: String, processing: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, OrbitColors.Danger.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "rec")
                val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recA")
                Box(Modifier.size(10.dp).clip(CircleShape).background(OrbitColors.Danger.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
                Text(if (processing) "Processing…" else "Listening · microphone on", style = MaterialTheme.typography.labelLarge, color = OrbitColors.Danger)
            }
            Spacer(Modifier.height(12.dp))
            OrbitOrb(OrbMode.LISTENING, 84.dp, level = level)
            AnimatedVisibility(partial.isNotBlank(), enter = fadeIn() + slideInVertically()) {
                Text(partial, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onDone, enabled = !processing) { Text("Done") }
            }
        }
    }
}
