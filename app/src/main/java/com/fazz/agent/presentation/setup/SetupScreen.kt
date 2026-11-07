package com.fazz.agent.presentation.setup

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fazz.agent.core.screen.FazzAccessibilityService
import com.fazz.agent.core.screen.ScreenSetup

private val RuntimePermissions: List<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_CALENDAR)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun Context.missingRuntime() = RuntimePermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

/** Android 13+ blocks accessibility for sideloaded apps until "Izinkan setelan terbatas" is chosen. Best-effort check. */
private fun Context.restrictedSettingsBlocking(): Boolean {
    if (Build.VERSION.SDK_INT < 33) return false
    return runCatching {
        val ops = getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow("android:access_restricted_settings", Process.myUid(), packageName)
        mode != AppOpsManager.MODE_ALLOWED && mode != AppOpsManager.MODE_DEFAULT
    }.getOrDefault(false)
}

/**
 * First-run setup: asks for everything Fazz needs to act on its own, in one place, and says exactly what to do
 * for the parts Android only lets the user switch on.
 */
@Composable
fun SetupScreen(disclosureAccepted: Boolean, onAcceptDisclosure: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // bumped on resume to re-read every status
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick
    val missing = context.missingRuntime()
    val screenOn = FazzAccessibilityService.instance != null
    val notifAccess = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val restricted = !screenOn && context.restrictedSettingsBlocking()
    var showDisclosure by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Siapkan Fazz", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Supaya Fazz bisa bekerja sendiri sejak awal, beri izin di bawah. Izin biasa bisa diberikan sekaligus; dua izin khusus hanya bisa dinyalakan olehmu di Pengaturan Android. Fazz memandu langkahnya.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StepCard(
            title = "1. Izin dasar",
            done = missing.isEmpty(),
            body = if (missing.isEmpty()) "Kontak, kalender, SMS, telepon, mikrofon, dan notifikasi sudah diizinkan."
            else "Kontak, kalender, SMS, telepon, mikrofon, dan notifikasi. Fazz tetap bertanya sebelum mengirim atau menelepon.",
        ) {
            if (missing.isNotEmpty()) {
                Button(onClick = { permLauncher.launch(missing.toTypedArray()) }) { Text("Beri semua izin") }
                if (tick > 0) Text(
                    "Jika sebuah izin tidak muncul lagi, buka Info aplikasi → Izin, lalu pilih Izinkan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { context.startActivity(appInfo(context)) }) { Text("Buka Info aplikasi") }
            }
        }

        StepCard(
            title = "2. Kontrol layar (aksesibilitas)",
            done = screenOn,
            body = if (screenOn) "Aktif. Fazz bisa mengetuk, mengetik, dan menggulir di aplikasi lain atas permintaanmu."
            else "Agar Fazz bisa mengoperasikan aplikasi lain. Ketuk tombol, lalu nyalakan \"Fazz Agent\". Fazz kembali sendiri setelah menyala.",
        ) {
            if (!screenOn) {
                Button(onClick = { if (disclosureAccepted) ScreenSetup.open(context) else showDisclosure = true }) { Text("Aktifkan kontrol layar") }
                if (restricted) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Android memblokir tombol ini untuk aplikasi dari luar Play Store.", style = MaterialTheme.typography.titleSmall)
                            Text("Lakukan sekali: Info aplikasi Fazz Agent → ketuk menu ⋮ (kanan atas) → \"Izinkan setelan terbatas\" → kembali ke sini dan ketuk Aktifkan lagi.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { context.startActivity(appInfo(context)) }) { Text("Buka Info aplikasi") }
                        }
                    }
                }
                Text(
                    "Cara manual: Pengaturan → Aksesibilitas → Aplikasi terinstal (atau Layanan terunduh) → Fazz Agent → nyalakan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StepCard(
            title = "3. Akses notifikasi (opsional)",
            done = notifAccess,
            body = if (notifAccess) "Aktif. Fazz bisa merangkum notifikasi dan membuat briefing."
            else "Agar Fazz bisa merangkum notifikasi pentingmu. Ketuk, lalu nyalakan \"Fazz Agent\".",
        ) {
            if (!notifAccess) OutlinedButton(onClick = { runCatching { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Aktifkan akses notifikasi") }
        }

        Spacer(Modifier.height(4.dp))
        val ready = missing.isEmpty() && screenOn
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (ready) "Mulai memakai Fazz" else "Lanjut") }
        if (!ready) Text(
            "Kamu bisa melewati ini. Fazz tetap bisa mengobrol dan mencari, tapi tugas yang butuh izin yang belum diberikan akan gagal sampai kamu mengaktifkannya (Pengaturan → Kontrol layar).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDisclosure) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Izinkan Fazz mengontrol layar?") },
            text = { Text(com.fazz.agent.presentation.chat.ScreenDisclosureText) },
            confirmButton = { TextButton(onClick = { showDisclosure = false; onAcceptDisclosure(); ScreenSetup.open(context) }) { Text("Setuju & aktifkan") } },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Nanti saja") } },
        )
    }
}

private fun appInfo(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun StepCard(title: String, done: Boolean, body: String, actions: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (done) "Siap ✓" else "Perlu diaktifkan", style = MaterialTheme.typography.labelMedium, color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
