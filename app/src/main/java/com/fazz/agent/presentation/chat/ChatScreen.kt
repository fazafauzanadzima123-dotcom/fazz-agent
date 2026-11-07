package com.fazz.agent.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.core.tools.PendingInteraction
import com.fazz.agent.core.voice.VoiceInputState
import com.fazz.agent.domain.models.MessageRole
import com.fazz.agent.presentation.components.AgentActivityIndicator
import com.fazz.agent.presentation.components.AssistantMessage
import com.fazz.agent.presentation.components.Greeting
import com.fazz.agent.presentation.components.InputBar
import com.fazz.agent.presentation.components.ListeningPanel
import com.fazz.agent.presentation.components.SmallOrb
import com.fazz.agent.presentation.components.Suggestion
import com.fazz.agent.presentation.components.UserBubble
import com.fazz.agent.presentation.components.toOrbMode
import java.time.LocalTime

private val Suggestions = listOf(
    Suggestion("Jadwal hari ini", "Cek kalender kamu", "Apa saja jadwalku hari ini?", Icons.Rounded.CalendarMonth),
    Suggestion("Buka aplikasi", "Contoh: YouTube", "Buka YouTube", Icons.Rounded.Apps),
    Suggestion("Status HP", "Baterai & jaringan", "Berapa persen baterai HP-ku?", Icons.Rounded.BatteryChargingFull),
    Suggestion("Pasang timer", "5 menit", "Pasang timer 5 menit", Icons.Rounded.Timer),
    Suggestion("Cari di web", "Berita teknologi terbaru", "Cari berita teknologi terbaru hari ini", Icons.Rounded.Language),
    Suggestion("Ingat sesuatu", "Preferensi kamu", "Ingat bahwa saya suka jawaban yang singkat", Icons.Rounded.EditNote),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val agentState by vm.displayState.collectAsStateWithLifecycle()
    val pending by vm.pendingInteraction.collectAsStateWithLifecycle()
    val voice by vm.voiceState.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoice()
    }
    val toolPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.resolveInteraction(result.values.all { it })
    }

    LaunchedEffect(voice) {
        val v = voice
        if (v is VoiceInputState.Error) { snackbar.showSnackbar(v.message); vm.clearVoiceError() }
    }
    val lastText = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastText) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    fun onMic() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.startVoice() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallOrb(agentState.toOrbMode())
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Fazz Agent", style = MaterialTheme.typography.titleMedium)
                                AgentActivityIndicator(agentState)
                            }
                        }
                    },
                    actions = {
                        if (speaking) IconButton(onClick = vm::stopSpeaking) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Hentikan suara") }
                        if (state.messages.isNotEmpty()) TextButton(onClick = vm::newChat) { Text("Chat baru") }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Tune, "Pengaturan") }
                    },
                )
                if (state.messages.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loaded && state.messages.isEmpty()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(28.dp))
                        Greeting(greetingFor(LocalTime.now()), Suggestions, onSuggestion = { vm.send(it) })
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.messages, key = { it.id }) { m ->
                            when (m.role) {
                                MessageRole.USER -> UserBubble(m)
                                else -> AssistantMessage(m, onRetry = vm::retryLast)
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).navigationBarsPadding()) {
                when (val v = voice) {
                    is VoiceInputState.Listening -> ListeningPanel(v.level, v.partial, false, vm::finishVoice, vm::cancelVoice)
                    VoiceInputState.Processing -> ListeningPanel(0f, "", true, vm::finishVoice, vm::cancelVoice)
                    else -> InputBar(state.input, vm::onInputChange, state.isBusy, { vm.send() }, vm::stop, ::onMic)
                }
            }
        }
    }

    when (val p = pending) {
        is PendingInteraction.Confirmation -> AlertDialog(
            onDismissRequest = { vm.resolveInteraction(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(p.request.title, style = MaterialTheme.typography.titleLarge) },
            text = { Text(p.request.message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { vm.resolveInteraction(true) }) { Text(p.request.confirmLabel) } },
            dismissButton = { TextButton(onClick = { vm.resolveInteraction(false) }) { Text(p.request.denyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
        is PendingInteraction.Permission -> AlertDialog(
            onDismissRequest = { vm.resolveInteraction(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Butuh izin", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    p.rationale.ifBlank { "Fazz membutuhkan izin untuk melakukan ini." } +
                        "\n\nAndroid akan menanyakan izinnya setelah ini. Kamu bisa mengubahnya kapan saja di Pengaturan HP.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { toolPermissions.launch(p.permissions.toTypedArray()) }) { Text("Lanjut") } },
            dismissButton = { TextButton(onClick = { vm.resolveInteraction(false) }) { Text("Nanti saja", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
        null -> Unit
    }
}

private fun greetingFor(t: LocalTime): String = when (t.hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
