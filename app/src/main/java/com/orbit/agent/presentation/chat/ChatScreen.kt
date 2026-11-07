package com.orbit.agent.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.orbit.agent.core.tools.PendingInteraction
import com.orbit.agent.core.voice.VoiceInputState
import com.orbit.agent.domain.models.MessageRole
import com.orbit.agent.presentation.components.AgentActivityIndicator
import com.orbit.agent.presentation.components.AssistantMessage
import com.orbit.agent.presentation.components.Greeting
import com.orbit.agent.presentation.components.InputBar
import com.orbit.agent.presentation.components.ListeningPanel
import com.orbit.agent.presentation.components.SmallOrb
import com.orbit.agent.presentation.components.UserBubble
import com.orbit.agent.presentation.components.toOrbMode
import java.time.LocalTime

private val Suggestions = listOf(
    "What's on my calendar today?",
    "Open YouTube",
    "How much battery do I have?",
    "Search the web for today's top tech news",
    "Set a 5 minute timer",
    "Remember that I prefer short answers",
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
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = vm::newChat, enabled = state.messages.isNotEmpty()) { Icon(Icons.Rounded.AddComment, "New chat") }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            SmallOrb(agentState.toOrbMode())
                            Spacer(Modifier.width(8.dp))
                            Text("Orbit", style = MaterialTheme.typography.titleLarge)
                        }
                        AgentActivityIndicator(agentState)
                    }
                },
                actions = {
                    if (speaking) IconButton(onClick = vm::stopSpeaking) { Icon(Icons.Rounded.VolumeOff, "Stop speaking") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loaded && state.messages.isEmpty()) {
                    Greeting(greetingFor(LocalTime.now()), Suggestions, onSuggestion = { vm.send(it) }, Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()) {
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
            title = { Text(p.request.title) },
            text = { Text(p.request.message) },
            confirmButton = { TextButton(onClick = { vm.resolveInteraction(true) }) { Text(p.request.confirmLabel) } },
            dismissButton = { TextButton(onClick = { vm.resolveInteraction(false) }) { Text(p.request.denyLabel) } },
        )
        is PendingInteraction.Permission -> AlertDialog(
            onDismissRequest = { vm.resolveInteraction(false) },
            title = { Text("Permission needed") },
            text = { Text(p.rationale.ifBlank { "Orbit needs a permission to do this." } + "\n\nAndroid will ask you next. You can change this anytime in system Settings.") },
            confirmButton = { TextButton(onClick = { toolPermissions.launch(p.permissions.toTypedArray()) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { vm.resolveInteraction(false) }) { Text("Not now") } },
        )
        null -> Unit
    }
}

private fun greetingFor(t: LocalTime): String = when (t.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello, night owl"
}
