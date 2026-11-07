package com.fazz.agent.presentation.customize

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.domain.models.AnswerLength
import com.fazz.agent.domain.models.EmojiLevel
import com.fazz.agent.domain.models.PluginGroup
import com.fazz.agent.domain.models.Profile
import com.fazz.agent.domain.models.ResponseStyle
import com.fazz.agent.domain.models.Tone
import com.fazz.agent.presentation.settings.SettingsViewModel

private fun Context.granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/** Re-reads Android state (permissions) whenever the user comes back from system settings. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

/** A rounded group of rows separated by hairlines. */
@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column { content() }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, status: String = "", highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (status.isNotBlank()) {
            Text(
                status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp, end = 4.dp).widthIn(max = 150.dp),
                color = if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The main "Sesuaikan" list. */
@Composable
fun CustomizeScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onPersonality: () -> Unit,
    onMemory: () -> Unit,
    onPlugins: () -> Unit,
    onLocation: () -> Unit,
    onPassword: () -> Unit,
    onGoogle: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    @Suppress("UNUSED_EXPRESSION") tick
    val s = settings
    val calendarOn = context.granted(Manifest.permission.READ_CALENDAR)
    val contactsOn = context.granted(Manifest.permission.READ_CONTACTS)
    val off = s?.disabledPlugins?.mapNotNull(PluginGroup::from)?.size ?: 0

    Page("Sesuaikan", onBack) {
        Group {
            NavRow(Icons.Rounded.Face, "Kepribadian", s?.nickname?.takeIf { it.isNotBlank() } ?: s?.profile?.style?.label.orEmpty(), onClick = onPersonality)
            Divider()
            NavRow(Icons.AutoMirrored.Rounded.MenuBook, "Memori", "${memories.size} item", onClick = onMemory)
            Divider()
            NavRow(Icons.Rounded.Extension, "Plugin", "${PluginGroup.entries.size - off} aktif", onClick = onPlugins)
        }
        Group {
            NavRow(Icons.Rounded.AccountCircle, "Akun Google", s?.googleEmail?.takeIf { it.isNotBlank() } ?: "Masuk", highlight = s?.googleEmail.isNullOrBlank(), onClick = onGoogle)
            Divider()
            NavRow(Icons.Rounded.CalendarMonth, "Kalender", if (calendarOn) "Terhubung" else "Siapkan", highlight = !calendarOn) {
                if (calendarOn) context.startActivity(appInfo(context)) else calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
            Divider()
            NavRow(Icons.Rounded.Person, "Kontak", if (contactsOn) "Terhubung" else "Siapkan", highlight = !contactsOn) {
                if (contactsOn) context.startActivity(appInfo(context)) else contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            Divider()
            NavRow(Icons.Rounded.Email, "Email", "Draf di aplikasi email") { }
        }
        Group {
            NavRow(Icons.Rounded.LocationOn, "Lokasi", if (s?.locationAllowed == true) "Aktif" else "Mati", highlight = s?.locationAllowed == true, onClick = onLocation)
            Divider()
            NavRow(Icons.Rounded.Lock, "Kata sandi", onClick = onPassword)
        }
        Text(
            "Pilihan di sini tersimpan di HP ini dan dikirim ke AI sebagai bagian dari instruksi saat kamu mengobrol.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

private fun appInfo(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(name(o)) }) }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, hint: String, max: Int, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(max)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = minLines,
        supportingText = { Text(if (hint.isBlank()) "${value.length}/$max" else "$hint  ${value.length}/$max") },
    )
}

@Composable
fun PersonalityScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) { Page("Kepribadian", onBack) { }; return }
    var nick by remember(s.nickname) { mutableStateOf(s.nickname) }
    var about by remember(s.about) { mutableStateOf(s.about) }
    var instr by remember(s.instructions) { mutableStateOf(s.instructions) }
    val dirty = nick != s.nickname || about != s.about || instr != s.instructions

    Page("Kepribadian", onBack) {
        Text("Atur bagaimana Fazz memanggilmu dan cara Fazz menjawab. Berlaku di semua chat.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow("Gaya jawaban", ResponseStyle.entries, s.profile.style, { it.label }) { vm.setStyle(it.id) }
        ChipRow("Nada", Tone.entries, s.profile.tone, { it.label }) { vm.setTone(it.id) }
        ChipRow("Panjang jawaban", AnswerLength.entries, s.profile.length, { it.label }) { vm.setAnswerLength(it.id) }
        ChipRow("Emoji", EmojiLevel.entries, s.profile.emoji, { it.label }) { vm.setEmoji(it.id) }
        Field(nick, { nick = it }, "Panggil aku", "Contoh: Fazza", Profile.MAX_NICKNAME)
        Field(about, { about = it }, "Tentang aku", "Pekerjaan, minat, atau hal yang perlu Fazz tahu.", Profile.MAX_ABOUT, minLines = 3)
        Field(instr, { instr = it }, "Instruksi khusus", "Contoh: selalu beri contoh singkat.", Profile.MAX_INSTRUCTIONS, minLines = 4)
        Text("Jangan tulis kata sandi, PIN, atau data kartu di sini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.savePersonal(nick, about, instr) }, enabled = dirty, modifier = Modifier.fillMaxWidth()) { Text(if (dirty) "Simpan" else "Tersimpan") }
    }
}

@Composable
fun PluginsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val off = settings?.disabledPlugins ?: emptySet()
    Page("Plugin", onBack) {
        Text("Nyalakan kemampuan yang boleh dipakai Fazz. Yang dimatikan benar-benar dilepas dari Fazz.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Group {
            PluginGroup.entries.forEachIndexed { i, g ->
                if (i > 0) Divider()
                val on = g.id !in off
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Switch) { vm.setPluginEnabled(g.id, !on) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.label, style = MaterialTheme.typography.bodyLarge)
                        Text(g.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = on, onCheckedChange = { vm.setPluginEnabled(g.id, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary),
                    )
                }
            }
        }
        Text("Fazz tetap meminta persetujuanmu sebelum tindakan yang tidak bisa dibatalkan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LocationScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    @Suppress("UNUSED_EXPRESSION") tick
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> asked = true; if (ok) vm.setLocationAllowed(true) }
    val permission = context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val allowed = settings?.locationAllowed == true

    Page("Lokasi", onBack) {
        Group {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Switch) {
                    if (allowed) vm.setLocationAllowed(false) else if (permission) vm.setLocationAllowed(true) else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Boleh pakai lokasiku", style = MaterialTheme.typography.bodyLarge)
                    Text("Untuk \"restoran dekat sini\" atau petunjuk arah dari tempatmu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = allowed, onCheckedChange = { want ->
                        if (!want) vm.setLocationAllowed(false) else if (permission) vm.setLocationAllowed(true) else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary),
                )
            }
        }
        Text(
            "Fazz hanya mengecek lokasi saat permintaanmu membutuhkannya. Lokasi tidak disimpan, dan hanya perkiraan alamat yang dibagikan ke AI untuk menjawab.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (asked && !permission) {
            Text("Izin lokasi ditolak. Aktifkan lewat Info aplikasi → Izin → Lokasi.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { context.startActivity(appInfo(context)) }) { Text("Buka Info aplikasi") }
        }
        Text(if (permission) "Izin Android: diberikan" else "Izin Android: belum diberikan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun PasswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Page("Kata sandi", onBack) {
        Text("Fazz tidak pernah menyimpan, melihat, atau mengetik kata sandi, PIN, kode OTP, atau data kartu.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Jika Fazz bertemu halaman masuk atau pembayaran saat bekerja di aplikasi lain, Fazz berhenti dan meminta kamu yang mengisinya. " +
                "Gunakan pengelola kata sandi milikmu agar isi otomatis tetap aman.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            val i = Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE").setData(Uri.parse("package:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(i) }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }) { Text("Buka pengaturan isi otomatis") }
    }
}
