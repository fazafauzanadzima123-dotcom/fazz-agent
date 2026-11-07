package com.fazz.agent.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fazz.agent.domain.models.MemoryCategory
import com.fazz.agent.presentation.theme.FazzColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun MemoryCategory.labelId(): String = when (this) {
    MemoryCategory.PREFERENCE -> "Preferensi"
    MemoryCategory.PERSONAL -> "Pribadi"
    MemoryCategory.TASK -> "Tugas"
    MemoryCategory.OTHER -> "Lainnya"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(MemoryCategory.PREFERENCE) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("id-ID"))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memori", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                actions = { IconButton(onClick = { confirmClear = true }, enabled = memories.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, "Hapus semua memori") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Fazz hanya mengingat hal yang kamu setujui. Memori tersimpan di HP ini dan dipakai agar jawaban lebih personal. Password, PIN, dan nomor kartu selalu ditolak.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            draft, { draft = it.take(300); error = null }, Modifier.fillMaxWidth(),
                            label = { Text("Tambah memori") }, isError = error != null, shape = RoundedCornerShape(14.dp),
                            supportingText = { error?.let { Text(it, color = FazzColors.Danger) } },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MemoryCategory.entries.forEach { c ->
                                FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c.labelId()) })
                            }
                        }
                        Button(onClick = { error = vm.addMemory(draft, category); if (error == null) draft = "" }, enabled = draft.isNotBlank()) { Text("Simpan") }
                    }
                }
            }
            if (memories.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada memori.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(memories.reversed(), key = { it.id }) { m ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                m.category.labelId() + ", " + dateFmt.format(Date(m.createdAt)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.deleteMemory(m.id) }) { Icon(Icons.Rounded.DeleteOutline, "Hapus memori") }
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Hapus semua memori?") },
        text = { Text("Semua hal yang diingat Fazz tentang kamu akan dihapus permanen.") },
        confirmButton = { TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Hapus semua", color = FazzColors.Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )
}
