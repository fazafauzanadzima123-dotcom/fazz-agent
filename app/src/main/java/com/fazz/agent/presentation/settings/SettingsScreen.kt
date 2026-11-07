package com.fazz.agent.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.BuildConfig
import com.fazz.agent.presentation.theme.FazzColors
import com.fazz.agent.presentation.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenMemory: () -> Unit, onOpenSetup: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val screenOn by vm.screenControlOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    fun granted(p: String): Boolean { tick; return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }
    val notifAccess = remember(tick) { vm.notificationAccessOn() }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    val postNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Kontrol layar") {
                OutlinedButton(onClick = onOpenSetup) { Text("Buka panduan penyiapan izin") }
                StatusRow(screenOn, if (screenOn) "Aktif. Fazz bisa mengoperasikan aplikasi lain saat kamu memintanya." else "Belum aktif. Tanpa ini Fazz hanya bisa membuka aplikasi, bukan mengetuk atau mengetik di dalamnya.")
                if (!screenOn) {
                    Button(onClick = { if (settings?.screenDisclosureAccepted == true) com.fazz.agent.core.screen.ScreenSetup.open(context) else showDisclosure = true }) { Text("Aktifkan kontrol layar") }
                    if (Build.VERSION.SDK_INT >= 33) Text(
                        "Jika tombol Fazz di Aksesibilitas tidak bisa diaktifkan (\"setelan terbatas\"): buka Info aplikasi Fazz Agent → menu ⋮ → \"Izinkan setelan terbatas\", lalu coba lagi.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) { Text("Buka Info aplikasi") }
                } else {
                    OutlinedButton(onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Kelola di Aksesibilitas") }
                }
            }

            Section("Notifikasi & proaktif") {
                StatusRow(notifAccess, if (notifAccess) "Fazz bisa membaca notifikasi untuk merangkum dan briefing." else "Akses notifikasi belum aktif.")
                OutlinedButton(onClick = { open(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) {
                    Text(if (notifAccess) "Kelola akses notifikasi" else "Aktifkan akses notifikasi")
                }
                if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    OutlinedButton(onClick = { postNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Izinkan Fazz mengirim notifikasi") }
                }
                val on = settings?.briefingEnabled == true
                val hour = settings?.briefingHour ?: 7
                ToggleRow("Briefing pagi", "Ringkasan jadwal, notifikasi penting, dan berita setiap pagi.", on) { vm.setBriefing(it, hour) }
                if (on) {
                    Text("Jam", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 6, 7, 8, 9).forEach { h ->
                            FilterChip(selected = h == hour, onClick = { vm.setBriefing(true, h) }, label = { Text("%02d.00".format(h)) })
                        }
                    }
                    TextButton(onClick = vm::runBriefingNow) { Text("Coba briefing sekarang") }
                }
            }

            Section("Gaya jawaban") {
                Text("Atur cara Fazz menjawab di semua chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.fazz.agent.domain.models.ResponseStyle.entries.forEach { st ->
                        FilterChip(selected = settings?.responseStyle.orEmpty().ifBlank { "normal" } == st.id, onClick = { vm.setStyle(st.id) }, label = { Text(st.label) })
                    }
                }
            }

            Section("Suara") {
                ToggleRow("Bacakan jawaban", "Selalu bacakan jawaban dengan suara. (Pertanyaan lewat suara selalu dijawab dengan suara.)", settings?.speakReplies == true, vm::setSpeak)
                ToggleRow("Kirim suara otomatis", "Langsung kirim begitu kamu selesai bicara.", settings?.autoSendVoice != false, vm::setAutoSend)
            }

            Section("Memori") {
                Surface(onClick = onOpenMemory, color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Memori tersimpan", style = MaterialTheme.typography.titleSmall)
                            Text("${memories.size} item, hanya tersimpan di HP ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }

            Section("Izin aplikasi") {
                PermissionRow("Mikrofon", "Input suara", granted(Manifest.permission.RECORD_AUDIO))
                PermissionRow("Kalender", "Membaca jadwal", granted(Manifest.permission.READ_CALENDAR))
                PermissionRow("Kontak", "Mencari nomor & nama", granted(Manifest.permission.READ_CONTACTS))
                PermissionRow("SMS", "Mengirim SMS yang kamu setujui", granted(Manifest.permission.SEND_SMS))
                PermissionRow("Telepon", "Menelepon setelah kamu setujui", granted(Manifest.permission.CALL_PHONE))
                Text("Izin hanya diminta saat fitur pertama kali membutuhkannya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) {
                    Text("Buka pengaturan aplikasi Android")
                }
            }

            Section("Koneksi") {
                OutlinedButton(onClick = vm::checkConnection, enabled = check !is ConnectionCheck.Running) { Text("Tes koneksi") }
                when (val c = check) {
                    ConnectionCheck.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Menghubungi server Fazz…")
                    }
                    ConnectionCheck.Ok -> Text("✓ Terhubung ke server Fazz.", color = FazzColors.Success, style = MaterialTheme.typography.bodyMedium)
                    is ConnectionCheck.Failed -> Text(c.message, color = FazzColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    ConnectionCheck.Idle -> Unit
                }
            }

            Section("Privasi & keamanan") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Kunci AI hanya ada di server Fazz, tidak di aplikasi ini. Pesanmu dikirim lewat HTTPS ke server Fazz lalu diproses oleh penyedia AI. " +
                            "Data kalender, kontak, notifikasi, dan isi layar hanya dikirim saat diperlukan untuk tugasmu. " +
                            "Chat dan memori tersimpan di HP ini.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Install ID (hanya untuk batas pemakaian)", style = MaterialTheme.typography.labelMedium)
                Text(settings?.installId?.ifBlank { "…" } ?: "…", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Fazz Agent v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDisclosure) AlertDialog(
        onDismissRequest = { showDisclosure = false },
        title = { Text("Izinkan Fazz mengontrol layar?") },
        text = {
            Text(
                "Dengan kontrol layar, Fazz bisa membaca teks & tombol di layar, mengetuk, mengetik, menggulir, dan mengambil screenshot di aplikasi lain — " +
                    "hanya saat menjalankan tugas yang kamu minta.\n\n" +
                    "• Isi layar dikirim ke penyedia AI selama tugas berjalan.\n" +
                    "• Fazz selalu minta persetujuan sebelum mengirim, membayar, memposting, menelepon, atau menghapus.\n" +
                    "• Fazz tidak mengetik password, PIN, atau OTP.\n" +
                    "• Tombol STOP selalu muncul saat Fazz bekerja.\n\n" +
                    "Setelah ini, pilih \"Fazz Agent\" di daftar Aksesibilitas lalu aktifkan.",
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.acceptScreenDisclosure(); showDisclosure = false; com.fazz.agent.core.screen.ScreenSetup.open(context) }) { Text("Setuju & buka") }
        },
        dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Batal") } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            content()
        }
    }
}

@Composable
private fun StatusRow(on: Boolean, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(18.dp),
            tint = if (on) FazzColors.Success else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary))
    }
}

@Composable
private fun PermissionRow(name: String, use: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(18.dp),
            tint = if (granted) FazzColors.Success else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(use, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (granted) "Diizinkan" else "Belum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
