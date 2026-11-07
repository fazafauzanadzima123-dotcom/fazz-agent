package com.fazz.agent.presentation.chat

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What Fazz will do with screen control. Shown once before the user is sent to Android's switch. */
const val ScreenDisclosureText =
    "Dengan kontrol layar, Fazz bisa membaca teks & tombol di layar, mengetuk, mengetik, menggulir, dan mengambil screenshot di aplikasi lain — " +
        "hanya saat menjalankan tugas yang kamu minta.\n\n" +
        "• Isi layar dikirim ke penyedia AI selama tugas berjalan.\n" +
        "• Fazz selalu minta persetujuan sebelum mengirim, membayar, memposting, menelepon, atau menghapus.\n" +
        "• Fazz tidak mengetik password, PIN, atau OTP.\n" +
        "• Tombol STOP selalu muncul saat Fazz bekerja."

/**
 * One-tap activation. Android only lets the user flip an accessibility switch, so we make that the single step:
 * the button opens Fazz's own switch and Fazz comes back on its own once it is on.
 */
@Composable
fun ScreenSetupCard(disclosureAccepted: Boolean, onAccept: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var showDisclosure by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Biarkan Fazz mengerjakan tugas di aplikasi lain", style = MaterialTheme.typography.titleSmall)
            Text(
                if (Build.VERSION.SDK_INT >= 33) "Cukup sekali: nyalakan tombol Fazz Agent, lalu Fazz kembali ke sini sendiri."
                else "Cukup sekali: pilih Fazz Agent di daftar, nyalakan, lalu Fazz kembali ke sini sendiri.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (disclosureAccepted) onOpen() else showDisclosure = true }) { Text("Aktifkan kontrol layar") }
            }
        }
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Izinkan Fazz mengontrol layar?") },
            text = { Text(ScreenDisclosureText) },
            confirmButton = { TextButton(onClick = { showDisclosure = false; onAccept(); onOpen() }) { Text("Setuju & aktifkan") } },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Nanti saja") } },
        )
    }
}
