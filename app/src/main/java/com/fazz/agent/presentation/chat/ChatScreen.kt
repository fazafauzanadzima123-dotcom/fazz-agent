package com.fazz.agent.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.core.tools.PendingInteraction
import com.fazz.agent.core.voice.VoiceInputState
import com.fazz.agent.data.local.ConversationMeta
import com.fazz.agent.domain.models.MessageRole
import com.fazz.agent.presentation.components.AgentActivityIndicator
import com.fazz.agent.presentation.components.AgentOrb
import com.fazz.agent.presentation.components.ArtifactViewer
import com.fazz.agent.presentation.components.AssistantMessage
import com.fazz.agent.presentation.components.Composer
import com.fazz.agent.presentation.components.Greeting
import com.fazz.agent.presentation.components.ListeningPanel
import com.fazz.agent.presentation.components.NoticeLine
import com.fazz.agent.presentation.components.OrbMode
import com.fazz.agent.presentation.components.StarterChips
import com.fazz.agent.presentation.components.UserBubble
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val Starters = listOf(
    "Jadwal hari ini" to "Apa saja jadwalku hari ini?",
    "Ringkas notifikasi" to "Ringkas notifikasi pentingku hari ini",
    "Kirim WhatsApp" to "Kirim WhatsApp ke ",
    "Pasang pengingat" to "Ingatkan aku 30 menit lagi untuk ",
    "Navigasi" to "Arahkan aku ke ",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit, onOpenMemory: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val composer by vm.composer.collectAsStateWithLifecycle()
    val agentState by vm.displayState.collectAsStateWithLifecycle()
    val pending by vm.pendingInteraction.collectAsStateWithLifecycle()
    val voice by vm.voiceState.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val voiceMode by vm.voiceMode.collectAsStateWithLifecycle()
    val screenOn by vm.screenControlOn.collectAsStateWithLifecycle()
    val disclosureAccepted by vm.disclosureAccepted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var showAttach by remember { mutableStateOf(false) }
    var openArtifact by remember { mutableStateOf<com.fazz.agent.domain.models.Artifact?>(null) }
    openArtifact?.let { ArtifactViewer(it) { openArtifact = null } }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startVoice()
    }
    val voiceModePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.enterVoiceMode()
    }
    val toolPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.resolveInteraction(result.values.all { it })
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> uri?.let(vm::addImageUri) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) vm.addCameraFile(f)
        cameraFile = null
    }

    var wasOn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(screenOn) {
        if (wasOn == false && screenOn) snackbar.showSnackbar("Kontrol layar aktif. Fazz siap bekerja di aplikasi lain.")
        wasOn = screenOn
    }
    val needsScreen = !screenOn && (session.messages.isEmpty() ||
        session.messages.takeLast(2).any { m -> m.tools.any { it.detail?.contains("Kontrol layar belum aktif") == true } })

    LaunchedEffect(voice) {
        val v = voice
        if (v is VoiceInputState.Error) { if (!voiceMode) snackbar.showSnackbar(v.message); vm.clearVoiceError() }
    }
    val lastText = session.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(session.messages.size, lastText) {
        if (session.messages.isNotEmpty()) listState.animateScrollToItem(session.messages.lastIndex)
    }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            HistoryDrawer(
                conversations = conversations,
                currentId = session.conversationId,
                onNew = { vm.newChat(); scope.launch { drawer.close() } },
                onOpen = { vm.openConversation(it); scope.launch { drawer.close() } },
                onDelete = vm::deleteConversation,
                onSettings = { scope.launch { drawer.close() }; onOpenSettings() },
                onMemory = { scope.launch { drawer.close() }; onOpenMemory() },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Riwayat chat") } },
                    title = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(session.title.ifBlank { "Fazz" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            AgentActivityIndicator(agentState)
                        }
                    },
                    actions = {
                        if (speaking && !voiceMode) IconButton(onClick = vm::stopSpeaking) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Hentikan suara") }
                        IconButton(onClick = vm::newChat) { Icon(Icons.Rounded.EditNote, "Chat baru") }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (session.loaded && session.messages.isEmpty()) {
                        Greeting(greetingFor(LocalTime.now()), Modifier.align(Alignment.Center))
                    } else {
                        val lastAssistant = session.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(session.messages, key = { _, m -> m.id }) { i, m ->
                                when (m.role) {
                                    MessageRole.USER -> UserBubble(m)
                                    MessageRole.NOTICE -> NoticeLine(m)
                                    MessageRole.ASSISTANT -> AssistantMessage(m, isLast = i == lastAssistant, onRetry = vm::retryLast, onSpeak = vm::speak, onOpenArtifact = { openArtifact = it })
                                }
                            }
                        }
                    }
                }
                if (needsScreen && composer.text.isBlank() && !session.isBusy) {
                    ScreenSetupCard(disclosureAccepted, vm::acceptDisclosure, vm::openScreenSetup, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                }
                if (session.messages.isEmpty() && composer.text.isBlank()) {
                    StarterChips(Starters) { vm.onTextChange(it); if (!it.endsWith(" ")) vm.send(it) }
                    Spacer(Modifier.height(10.dp))
                }
                Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp).navigationBarsPadding()) {
                    when (val v = voice) {
                        is VoiceInputState.Listening -> if (!voiceMode) ListeningPanel(v.level, v.partial, false, vm::finishVoice, vm::cancelVoice)
                            else ComposerFor(vm, composer, session.isBusy, ::hasMic, micPermission::launch, voiceModePermission::launch) { showAttach = true }
                        VoiceInputState.Processing -> if (!voiceMode) ListeningPanel(0f, "", true, vm::finishVoice, vm::cancelVoice)
                            else ComposerFor(vm, composer, session.isBusy, ::hasMic, micPermission::launch, voiceModePermission::launch) { showAttach = true }
                        else -> ComposerFor(vm, composer, session.isBusy, ::hasMic, micPermission::launch, voiceModePermission::launch) { showAttach = true }
                    }
                }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(onDismissRequest = { showAttach = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
                Text("Tambahkan ke chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                SheetRow(Icons.Rounded.CameraAlt, "Kamera", "Ambil foto lalu tanyakan ke Fazz") {
                    showAttach = false
                    val (f, uri) = Attachments.newCameraTarget(context)
                    cameraFile = f
                    takePicture.launch(uri)
                }
                SheetRow(Icons.Rounded.Photo, "Foto", "Pilih dari galeri") {
                    showAttach = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }

    if (voiceMode) VoiceModeScreen(vm, onClose = vm::exitVoiceMode)

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
                Text(p.rationale.ifBlank { "Fazz membutuhkan izin untuk melakukan ini." } +
                    "\n\nAndroid akan menanyakan izinnya setelah ini. Kamu bisa mengubahnya kapan saja di Pengaturan HP.", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = { TextButton(onClick = { toolPermissions.launch(p.permissions.toTypedArray()) }) { Text("Lanjut") } },
            dismissButton = { TextButton(onClick = { vm.resolveInteraction(false) }) { Text("Nanti saja", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
        null -> Unit
    }
}

@Composable
private fun ComposerFor(
    vm: ChatViewModel,
    composer: ComposerState,
    busy: Boolean,
    hasMic: () -> Boolean,
    askMic: (String) -> Unit,
    askMicForVoiceMode: (String) -> Unit,
    onPlus: () -> Unit,
) {
    Composer(
        text = composer.text, images = composer.images, attaching = composer.attaching, busy = busy,
        onTextChange = vm::onTextChange, onRemoveImage = vm::removeImage, onPlus = onPlus,
        onMic = { if (hasMic()) vm.startVoice() else askMic(Manifest.permission.RECORD_AUDIO) },
        onSend = { vm.send() }, onStop = vm::stop,
        onVoiceMode = { if (hasMic()) vm.enterVoiceMode() else askMicForVoiceMode(Manifest.permission.RECORD_AUDIO) },
    )
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryDrawer(
    conversations: List<ConversationMeta>,
    currentId: String?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    onMemory: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<ConversationMeta?>(null) }
    val filtered = conversations.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    val today = LocalDate.now()
    fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val groups = filtered.groupBy {
        val d = dayOf(it.updatedAt)
        when {
            d == today -> "Hari ini"
            d == today.minusDays(1) -> "Kemarin"
            d.isAfter(today.minusDays(7)) -> "7 hari terakhir"
            else -> "Lebih lama"
        }
    }

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background, drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                AgentOrb(OrbMode.IDLE, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text("Fazz", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Cari chat") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            )
            Spacer(Modifier.height(8.dp))
            DrawerItem(Icons.Rounded.EditNote, "Chat baru", onNew)
            DrawerItem(Icons.Rounded.Psychology, "Memori", onMemory)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
            LazyColumn(Modifier.weight(1f)) {
                if (filtered.isEmpty()) item {
                    Text(if (query.isBlank()) "Belum ada percakapan." else "Tidak ditemukan.", Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                listOf("Hari ini", "Kemarin", "7 hari terakhir", "Lebih lama").forEach { g ->
                    val list = groups[g].orEmpty()
                    if (list.isNotEmpty()) {
                        item(key = "h-$g") {
                            Text(g, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(list, key = { it.id }) { c ->
                            val selected = c.id == currentId
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
                                    .clickable { onOpen(c.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { confirmDelete = c }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, "Hapus chat", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            DrawerItem(Icons.Rounded.School, "Fazz Academy") {
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://fazz-agent.vercel.app/academy/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            DrawerItem(Icons.Rounded.Tune, "Pengaturan", onSettings)
            Spacer(Modifier.height(10.dp))
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus chat?") },
            text = { Text("\"${c.title}\" akan dihapus permanen dari HP ini.") },
            confirmButton = { TextButton(onClick = { onDelete(c.id); confirmDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Full-screen hands-free conversation: listen → Fazz answers aloud → listen again. */
@Composable
private fun VoiceModeScreen(vm: ChatViewModel, onClose: () -> Unit) {
    val voice by vm.voiceState.collectAsStateWithLifecycle()
    val speaking by vm.speaking.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val (mode, label, partial) = when {
        speaking -> Triple(OrbMode.SPEAKING, "Fazz berbicara…", "")
        session.isBusy -> Triple(OrbMode.THINKING, session.agentStatus.ifBlank { "Berpikir" } + "…", "")
        voice is VoiceInputState.Listening -> Triple(OrbMode.LISTENING, "Mendengarkan…", (voice as VoiceInputState.Listening).partial)
        voice is VoiceInputState.Processing -> Triple(OrbMode.THINKING, "Memproses…", "")
        else -> Triple(OrbMode.IDLE, "Ketuk mikrofon untuk bicara", "")
    }
    val level = (voice as? VoiceInputState.Listening)?.level ?: 0f
    val lastReply = session.messages.lastOrNull { it.role == MessageRole.ASSISTANT && !it.isStreaming }?.text.orEmpty()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode suara", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (mode == OrbMode.LISTENING) Text("● mikrofon aktif", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                AgentOrb(mode, 200.dp, level = level)
                Spacer(Modifier.height(28.dp))
                Text(label, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    partial.ifBlank { if (!session.isBusy && !speaking) lastReply.take(220) else "" },
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 6, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Rounded.Close, "Tutup mode suara", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, onClose)
                    RoundButton(
                        Icons.Rounded.Mic, if (voice is VoiceInputState.Listening) "Selesai bicara" else "Mulai bicara",
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary,
                    ) {
                        when {
                            voice is VoiceInputState.Listening -> vm.finishVoice()
                            session.isBusy -> vm.stop()
                            speaking -> { vm.stopSpeaking(); vm.startVoice() }
                            else -> vm.startVoice()
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, color = bg, modifier = Modifier.size(68.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(28.dp), tint = fg) }
    }
}

private fun greetingFor(t: LocalTime): String = when (t.hour) {
    in 4..10 -> "Selamat pagi.\nAda yang bisa dibantu?"
    in 11..14 -> "Selamat siang.\nAda yang bisa dibantu?"
    in 15..17 -> "Selamat sore.\nAda yang bisa dibantu?"
    else -> "Selamat malam.\nAda yang bisa dibantu?"
}
