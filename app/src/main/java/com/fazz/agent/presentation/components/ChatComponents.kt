package com.fazz.agent.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fazz.agent.domain.models.AgentState
import com.fazz.agent.domain.models.ChatMessage
import com.fazz.agent.domain.models.ToolActivity
import com.fazz.agent.domain.models.ToolStatus
import com.fazz.agent.presentation.theme.FazzColors

fun AgentState.toOrbMode(): OrbMode = when (this) {
    AgentState.Idle -> OrbMode.IDLE
    AgentState.Thinking, AgentState.Responding -> OrbMode.THINKING
    is AgentState.UsingTool, AgentState.WaitingForUser -> OrbMode.TOOL
}

fun AgentState.statusText(): String = when (this) {
    AgentState.Idle -> "Siap membantu"
    AgentState.Thinking -> "Sedang berpikir"
    AgentState.Responding -> "Menulis jawaban"
    is AgentState.UsingTool -> label
    AgentState.WaitingForUser -> "Menunggu persetujuanmu"
}

/** Small status line under the title. */
@Composable
fun AgentActivityIndicator(state: AgentState, modifier: Modifier = Modifier) {
    val busy = state != AgentState.Idle
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pa")
    Row(modifier.semantics { contentDescription = "Status: ${state.statusText()}" }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(
                if (busy) MaterialTheme.colorScheme.secondary.copy(alpha = a) else FazzColors.Success,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            state.statusText() + if (busy) "…" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun UserBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp), color = MaterialTheme.colorScheme.primary) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
                if (message.viaVoice) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Rounded.GraphicEq, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text("via suara", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun AssistantMessage(message: ChatMessage, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(end = 20.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentOrb(if (message.isStreaming) OrbMode.THINKING else OrbMode.IDLE, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Fazz", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        if (message.tools.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { message.tools.forEach { ToolChip(it) } }
            Spacer(Modifier.height(10.dp))
        }
        if (message.text.isNotEmpty()) {
            Text(
                renderMarkdown(message.text + if (message.isStreaming) " ▍" else "", colors.secondary, colors.surfaceVariant),
                style = MaterialTheme.typography.bodyLarge, color = colors.onBackground,
            )
        } else if (message.isStreaming && message.tools.isEmpty()) {
            TypingDots()
        }
        message.error?.let { ErrorCard(it, onRetry) }
        message.usage?.takeIf { !message.isStreaming && it.totalTokens > 0 }?.let {
            Spacer(Modifier.height(6.dp))
            Text("${it.totalTokens} token", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = a)))
        }
    }
}

@Composable
fun ToolChip(tool: ToolActivity) {
    val (icon, tint) = when (tool.status) {
        ToolStatus.RUNNING -> null to MaterialTheme.colorScheme.secondary
        ToolStatus.SUCCESS -> Icons.Rounded.Check to FazzColors.Success
        ToolStatus.FAILED -> Icons.Rounded.ErrorOutline to FazzColors.Danger
        ToolStatus.DENIED -> Icons.Rounded.Block to FazzColors.Warning
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "Alat ${tool.label}: ${tool.status.name.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            if (icon == null) CircularProgressIndicator(Modifier.size(12.dp), color = tint, strokeWidth = 1.6.dp)
            else Icon(icon, null, Modifier.size(14.dp), tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(tool.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            tool.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FazzColors.Danger.copy(alpha = 0.07f))
            .border(1.dp, FazzColors.Danger.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = FazzColors.Danger)
        Spacer(Modifier.width(10.dp))
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Coba lagi") }
    }
}

data class Suggestion(val title: String, val subtitle: String, val prompt: String, val icon: ImageVector)

@Composable
fun Greeting(greeting: String, suggestions: List<Suggestion>, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        AgentOrb(OrbMode.IDLE, 56.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            "$greeting.\nAda yang bisa saya bantu?",
            style = MaterialTheme.typography.displaySmall, color = colors.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Ketik atau bicara. Fazz bisa membuka aplikasi, cek jadwal, dan mencari di web. Untuk hal sensitif, Fazz selalu minta izin dulu.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        suggestions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { s -> SuggestionCard(s, Modifier.weight(1f)) { onSuggestion(s.prompt) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 96.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(s.icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(10.dp))
            Text(s.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
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
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outline), shadowElevation = 2.dp) {
        Row(Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = text, onValueChange = onTextChange,
                modifier = Modifier.weight(1f).widthIn(min = 1.dp),
                placeholder = { Text("Tanya atau suruh Fazz…", color = colors.onSurfaceVariant) },
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.secondary,
                ),
            )
            IconButton(onClick = onMic, enabled = !busy, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = "Input suara", tint = colors.onSurfaceVariant)
            }
            FilledIconButton(
                onClick = if (busy) onStop else onSend,
                enabled = busy || text.isNotBlank(),
                modifier = Modifier.padding(bottom = 4.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (busy) FazzColors.Danger else colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceVariant,
                ),
            ) {
                if (busy) Icon(Icons.Rounded.Stop, contentDescription = "Hentikan")
                else Icon(Icons.Rounded.ArrowUpward, contentDescription = "Kirim")
            }
        }
    }
}

/** Shown instead of the input bar while the microphone is open — a clear privacy indicator. */
@Composable
fun ListeningPanel(level: Float, partial: String, processing: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = colors.surface, border = BorderStroke(1.dp, FazzColors.Danger.copy(alpha = 0.4f)), shadowElevation = 2.dp) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "rec")
                val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recA")
                Box(Modifier.size(8.dp).clip(CircleShape).background(FazzColors.Danger.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
                Text(if (processing) "Memproses…" else "Mendengarkan, mikrofon aktif", style = MaterialTheme.typography.labelLarge, color = FazzColors.Danger)
            }
            Spacer(Modifier.height(14.dp))
            AgentOrb(OrbMode.LISTENING, 76.dp, level = level)
            AnimatedVisibility(partial.isNotBlank(), enter = fadeIn() + slideInVertically()) {
                Text(partial, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                TextButton(onClick = onCancel) { Text("Batal", color = colors.onSurfaceVariant) }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onDone, enabled = !processing) {
                    Text("Selesai"); Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

