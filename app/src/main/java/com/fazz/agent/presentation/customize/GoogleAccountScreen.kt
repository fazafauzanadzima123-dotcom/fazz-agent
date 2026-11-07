package com.fazz.agent.presentation.customize

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.core.google.GoogleProfile
import com.fazz.agent.core.google.GoogleService
import com.fazz.agent.core.google.SignInScopes
import com.fazz.agent.presentation.settings.SettingsViewModel
import kotlinx.coroutines.launch

private sealed interface Pending {
    data object SignIn : Pending
    data class Connect(val service: GoogleService) : Pending
}

/**
 * Sign in with Google, then connect Calendar / Gmail / Drive one by one. Google shows its own consent screen;
 * Fazz never sees the password and does not store access tokens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAccountScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val google = vm.google

    suspend fun finish(p: Pending, token: String?) {
        if (token == null) { error = "Google tidak memberi izin. Coba lagi."; return }
        when (p) {
            Pending.SignIn -> google.profile(token)?.let { vm.saveGoogleAccount(it) } ?: run { error = "Gagal membaca profil Google." }
            is Pending.Connect -> vm.setGoogleService(p.service.id, true)
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val p = pending
        pending = null
        if (p == null) { busy = false; return@rememberLauncherForActivityResult }
        scope.launch {
            if (res.resultCode == Activity.RESULT_OK) {
                runCatching { google.grantFromIntent(res.data).accessToken }.onSuccess { finish(p, it) }.onFailure { error = "Gagal: ${it.message}" }
            } else error = "Dibatalkan."
            busy = false
        }
    }

    fun start(p: Pending, scopes: List<String>, email: String?) {
        busy = true; error = null
        scope.launch {
            runCatching { google.authorize(scopes, email) }
                .onSuccess { g ->
                    val pi = g.pendingIntent
                    if (pi != null) { pending = p; consent.launch(IntentSenderRequest.Builder(pi.intentSender).build()) }
                    else { finish(p, g.accessToken); busy = false }
                }
                .onFailure { busy = false; error = "Google Play Services tidak merespons: ${it.message}" }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Akun Google", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (s == null) return@Column
            if (s.googleEmail.isBlank()) {
                Text("Masuk dengan Google agar Fazz bisa memakai Kalender, Gmail, dan Docs-mu. Google yang menampilkan layar izin; Fazz tidak melihat kata sandimu dan tidak menyimpan token akses.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { start(Pending.SignIn, SignInScopes, null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Menunggu Google…" else "Masuk dengan Google") }
            } else {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.googleName.ifBlank { "Akun Google" }, style = MaterialTheme.typography.titleMedium)
                        Text(s.googleEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Hubungkan layanan yang boleh dipakai Fazz:", style = MaterialTheme.typography.titleSmall)
                GoogleService.entries.forEach { svc ->
                    val on = svc.id in s.googleServices
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(svc.label, style = MaterialTheme.typography.bodyLarge)
                                Text(svc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = on, enabled = !busy,
                                onCheckedChange = { want -> if (want) start(Pending.Connect(svc), svc.scopes, s.googleEmail) else vm.setGoogleService(svc.id, false) },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary),
                            )
                        }
                    }
                }
                Text("Kirim email, buat acara, dan simpan ke Docs selalu meminta persetujuanmu dulu. Untuk mencabut izin sepenuhnya, buka myaccount.google.com/permissions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.signOutGoogle() }, modifier = Modifier.fillMaxWidth()) { Text("Keluar dari akun Google") }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            Text(
                "Catatan: selama aplikasi Fazz berstatus pengujian di Google, hanya akun yang didaftarkan sebagai penguji yang bisa masuk. Google akan menampilkan peringatan \"aplikasi belum diverifikasi\"; pilih Lanjutkan.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
