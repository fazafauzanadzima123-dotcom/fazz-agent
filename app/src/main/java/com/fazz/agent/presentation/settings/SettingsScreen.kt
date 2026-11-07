package com.fazz.agent.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenMemory: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var modelText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings?.modelId) { settings?.modelId?.let { if (modelText.isEmpty()) modelText = it } }

    var permTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permTick++ }
    fun granted(p: String): Boolean { permTick; return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }

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
            Section("Model AI") {
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it.trim().lowercase() },
                    label = { Text("ID model Gemini") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Default: ${BuildConfig.DEFAULT_MODEL} (punya kuota gratis). Tekan Tes koneksi untuk melihat model lain.") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setModel(modelText) }, enabled = modelText.isNotBlank() && modelText != settings?.modelId) { Text("Simpan model") }
                    OutlinedButton(onClick = vm::checkConnection, enabled = check !is ConnectionCheck.Running) { Text("Tes koneksi") }
                }
                when (val c = check) {
                    ConnectionCheck.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Menghubungi server…")
                    }
                    is ConnectionCheck.Ok -> {
                        Text("✓ Terhubung. Default server: ${c.defaultModel}. Ketuk model untuk memakainya:", color = FazzColors.Success, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.models.take(24).forEach { m ->
                                FilterChip(selected = m == settings?.modelId, onClick = { modelText = m; vm.setModel(m) }, label = { Text(m, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                    is ConnectionCheck.Failed -> Text(c.message, color = FazzColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    ConnectionCheck.Idle -> Unit
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

            Section("Izin") {
                PermissionRow("Mikrofon", "Input suara", granted(Manifest.permission.RECORD_AUDIO))
                PermissionRow("Kalender", "Membaca jadwal", granted(Manifest.permission.READ_CALENDAR))
                PermissionRow("Kontak", "Mencari orang berdasarkan nama", granted(Manifest.permission.READ_CONTACTS))
                Text("Izin hanya diminta saat fitur pertama kali membutuhkannya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text("Buka pengaturan aplikasi Android") }
            }

            Section("Privasi & keamanan") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "API key Gemini hanya ada di server (secret Supabase). Aplikasi ini tidak menyimpan kunci AI apa pun. " +
                            "Pesan dikirim lewat HTTPS ke backend lalu ke Google Gemini. Data kalender dan kontak hanya dikirim setelah kamu menyetujui setiap permintaan. " +
                            "Chat dan memori hanya tersimpan di perangkat ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Backend", style = MaterialTheme.typography.labelMedium)
                Text(vm.backendUrl, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Install ID (hanya untuk batas pemakaian)", style = MaterialTheme.typography.labelMedium)
                Text(settings?.installId?.ifBlank { "…" } ?: "…", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Fazz Agent v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

@Composable
private fun PermissionRow(name: String, use: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(18.dp),
            tint = if (granted) FazzColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(use, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (granted) "Diizinkan" else "Belum", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

