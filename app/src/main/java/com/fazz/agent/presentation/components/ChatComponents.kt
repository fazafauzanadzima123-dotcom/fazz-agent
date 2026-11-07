package com.fazz.agent.presentation.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fazz.agent.domain.models.AgentState
import com.fazz.agent.domain.models.ChatMessage
import com.fazz.agent.domain.models.ToolActivity
import com.fazz.agent.domain.models.ToolStatus
import com.fazz.agent.presentation.theme.FazzColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

@Composable
fun AgentActivityIndicator(state: AgentState, modifier: Modifier = Modifier) {
    val busy = state != AgentState.Idle
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pa")
    Row(modifier.semantics { contentDescription = "Status: ${state.statusText()}" }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (busy) MaterialTheme.colorScheme.secondary.copy(alpha = a) else FazzColors.Success))
        Spacer(Modifier.width(6.dp))
        Text(
            state.statusText() + if (busy) "…" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decodes a local image file into a small thumbnail off the main thread. */
@Composable
fun rememberThumb(path: String, maxPx: Int = 512): ImageBitmap? {
    val state by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / s > maxPx) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state
}

@Composable
fun Thumb(path: String, size: Dp, modifier: Modifier = Modifier) {
    val img = rememberThumb(path)
    Box(modifier.size(size).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        img?.let { Image(it, contentDescription = "Gambar terlampir", contentScale = ContentScale.Crop, modifier = Modifier.size(size)) }
    }
}

@Composable
fun UserBubble(message: ChatMessage) {
    Column(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalAlignment = Alignment.End) {
        if (message.images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { message.images.forEach { Thumb(it, 120.dp) } }
            Spacer(Modifier.height(6.dp))
        }
        if (message.text.isNotBlank()) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (message.viaVoice) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, end = 6.dp)) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("via suara", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NoticeLine(message: ChatMessage) {
    Text(
        message.text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Fazz's answer: no bubble, collapsible agent steps, and copy / retry / read-aloud actions. */
@Composable
@Suppress("DEPRECATION")
fun AssistantMessage(message: ChatMessage, isLast: Boolean, onRetry: () -> Unit, onSpeak: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (message.tools.isNotEmpty()) {
            StepsGroup(message.tools, running = message.isStreaming)
            Spacer(Modifier.height(10.dp))
        }
        if (message.text.isNotEmpty()) {
            Text(
                renderMarkdown(message.text + if (message.isStreaming) " ▍" else "", colors.secondary, colors.surfaceVariant),
                style = MaterialTheme.typography.bodyLarge, color = colors.onBackground,
            )
        } else if (message.isStreaming && message.tools.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) { AgentOrb(OrbMode.THINKING, 22.dp); Spacer(Modifier.width(8.dp)); TypingDots() }
        }
        message.error?.let { ErrorCard(it, onRetry) }
        if (!message.isStreaming && message.text.isNotBlank()) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(Icons.Rounded.ContentCopy, "Salin") { clipboard.setText(AnnotatedString(message.text)) }
                SmallAction(Icons.AutoMirrored.Rounded.VolumeUp, "Bacakan") { onSpeak(message.text) }
                if (isLast) SmallAction(Icons.Rounded.Refresh, "Ulangi") { onRetry() }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Menjalankan 4 langkah" header that expands into the individual tool steps (like Claude's tool-use blocks). */
@Composable
fun StepsGroup(tools: List<ToolActivity>, running: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = tools.lastOrNull { it.status == ToolStatus.RUNNING }
    val failed = tools.count { it.status == ToolStatus.FAILED || it.status == ToolStatus.DENIED }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running && current != null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp, color = MaterialTheme.colorScheme.secondary)
            else Icon(if (failed > 0) Icons.Rounded.ErrorOutline else Icons.Rounded.Check, null, Modifier.size(16.dp),
                tint = if (failed > 0) FazzColors.Warning else FazzColors.Success)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    running && current != null -> current.label + "…"
                    tools.size == 1 -> tools.first().label
                    else -> "Menjalankan ${tools.size} langkah"
                },
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Tutup" else "Lihat langkah", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tools.forEach { StepRow(it) }
            }
        }
    }
}

@Composable
private fun StepRow(tool: ToolActivity) {
    val (icon, tint) = when (tool.status) {
        ToolStatus.RUNNING -> null to MaterialTheme.colorScheme.secondary
        ToolStatus.SUCCESS -> Icons.Rounded.Check to FazzColors.Success
        ToolStatus.FAILED -> Icons.Rounded.ErrorOutline to FazzColors.Danger
        ToolStatus.DENIED -> Icons.Rounded.Block to FazzColors.Warning
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(16.dp), contentAlignment = Alignment.Center) {
            if (icon == null) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = tint)
            else Icon(icon, null, Modifier.size(14.dp), tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(tool.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            tool.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = a)))
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
        TextButton(onClick = onRetry) { Text("Coba lagi") }
    }
}

/** Centered greeting, like Claude's empty state. */
@Composable
fun Greeting(greeting: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AgentOrb(OrbMode.IDLE, 52.dp)
        Spacer(Modifier.height(18.dp))
        Text(greeting, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
    }
}

/** Quick starters shown above the composer on an empty chat. */
@Composable
fun StarterChips(items: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, prompt) ->
            Surface(onClick = { onPick(prompt) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Claude-style composer: attachments row, multi-line field, then a tool row with "+" on the left and
 * mic + send / voice-mode / stop on the right.
 */
@Composable
fun Composer(
    text: String,
    images: List<String>,
    attaching: Boolean,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onPlus: () -> Unit,
    onMic: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoiceMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val canSend = text.isNotBlank() || images.isNotEmpty()
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outline), shadowElevation = 3.dp) {
        Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            if (images.isNotEmpty() || attaching) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    images.forEach { p ->
                        Box {
                            Thumb(p, 64.dp)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(Color(0xAA000000)).clickable { onRemoveImage(p) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, "Hapus gambar", Modifier.size(13.dp), tint = Color.White) }
                        }
                    }
                    if (attaching) Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
            }
            TextField(
                value = text, onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).widthIn(min = 1.dp),
                placeholder = { Text("Tulis pesan untuk Fazz", color = colors.onSurfaceVariant) },
                maxLines = 7,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = colors.secondary,
                ),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onPlus, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, colors.outline), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Tambah foto", tint = colors.onSurface) }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMic, enabled = !busy) { Icon(Icons.Rounded.Mic, "Dikte suara", tint = colors.onSurfaceVariant) }
                when {
                    busy -> FilledIconButton(onClick = onStop, colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
                        Icon(Icons.Rounded.Stop, "Hentikan")
                    }
                    canSend -> FilledIconButton(onClick = onSend, colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.secondary, contentColor = colors.onSecondary)) {
                        Icon(Icons.Rounded.ArrowUpward, "Kirim")
                    }
                    else -> FilledIconButton(onClick = onVoiceMode, colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary)) {
                        Icon(Icons.Rounded.GraphicEq, "Mode suara")
                    }
                }
            }
        }
    }
}

/** Shown instead of the composer while dictating — a clear "microphone on" indicator. */
@Composable
fun ListeningPanel(level: Float, partial: String, processing: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = colors.surface, border = BorderStroke(1.dp, FazzColors.Danger.copy(alpha = 0.4f)), shadowElevation = 3.dp) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val t = rememberInfiniteTransition(label = "rec")
                val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recA")
                Box(Modifier.size(8.dp).clip(CircleShape).background(FazzColors.Danger.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
                Text(if (processing) "Memproses…" else "Mendengarkan, mikrofon aktif", style = MaterialTheme.typography.labelLarge, color = FazzColors.Danger)
            }
            Spacer(Modifier.height(14.dp))
            AgentOrb(OrbMode.LISTENING, 72.dp, level = level)
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
