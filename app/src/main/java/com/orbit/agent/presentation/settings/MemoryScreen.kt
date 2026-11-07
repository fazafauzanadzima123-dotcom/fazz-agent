package com.orbit.agent.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.orbit.agent.domain.models.MemoryCategory
import com.orbit.agent.presentation.theme.OrbitColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(MemoryCategory.PREFERENCE) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmClear = true }, enabled = memories.isNotEmpty()) { Icon(Icons.Rounded.DeleteSweep, "Delete all memories") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Orbit only remembers what you approve. Memories stay on this phone and are included in Orbit's instructions so it can personalize answers. Passwords, PINs and card numbers are always refused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(draft, { draft = it.take(300); error = null }, Modifier.fillMaxWidth(), label = { Text("Add a memory") }, isError = error != null,
                            supportingText = { error?.let { Text(it, color = OrbitColors.Danger) } })
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MemoryCategory.entries.forEach { c ->
                                FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Button(onClick = { error = vm.addMemory(draft, category); if (error == null) draft = "" }, enabled = draft.isNotBlank()) { Text("Save") }
                    }
                }
            }
            if (memories.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No memories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(memories.reversed(), key = { it.id }) { m ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                m.category.name.lowercase() + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.deleteMemory(m.id) }) { Icon(Icons.Rounded.DeleteOutline, "Delete memory") }
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete all memories?") },
        text = { Text("This permanently removes everything Orbit remembers about you.") },
        confirmButton = { TextButton(onClick = { vm.clearMemories(); confirmClear = false }) { Text("Delete all", color = OrbitColors.Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}
