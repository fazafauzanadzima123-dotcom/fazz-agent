package com.orbit.agent.presentation.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.orbit.agent.presentation.theme.MonoStyle
import com.orbit.agent.presentation.theme.OrbitColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenMemory: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var modelText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings?.modelId) { settings?.modelId?.let { if (modelText.isEmpty()) modelText = it } }

    var permTick by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permTick++ }
    fun granted(p: String): Boolean { permTick; return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("AI model") {
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it.trim().lowercase() },
                    label = { Text("Gemini model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Default: gemini-flash-latest (Google's alias for the current Flash model).") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setModel(modelText) }, enabled = modelText.isNotBlank() && modelText != settings?.modelId) { Text("Save model") }
                    OutlinedButton(onClick = vm::checkConnection, enabled = check !is ConnectionCheck.Running) { Text("Test connection") }
                }
                when (val c = check) {
                    ConnectionCheck.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Contacting server…")
                    }
                    is ConnectionCheck.Ok -> {
                        Text("✓ Connected. Server default: ${c.defaultModel}. Tap a model to use it:", color = OrbitColors.Success, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.models.take(24).forEach { m ->
                                FilterChip(selected = m == settings?.modelId, onClick = { modelText = m; vm.setModel(m) }, label = { Text(m, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                    is ConnectionCheck.Failed -> Text(c.message, color = OrbitColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    ConnectionCheck.Idle -> Unit
                }
            }

            Section("Voice") {
                ToggleRow("Speak replies aloud", "Always read answers with text-to-speech. (Voice questions are always answered aloud.)", settings?.speakReplies == true, vm::setSpeak)
                ToggleRow("Send voice automatically", "Send what you said as soon as you stop talking.", settings?.autoSendVoice != false, vm::setAutoSend)
            }

            Section("Memory") {
                Surface(onClick = onOpenMemory, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Saved memories", style = MaterialTheme.typography.titleSmall)
                            Text("${memories.size} item(s) · stored only on this phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }

            Section("Permissions") {
                PermissionRow("Microphone", "Voice input", granted(Manifest.permission.RECORD_AUDIO))
                PermissionRow("Calendar", "Read upcoming events", granted(Manifest.permission.READ_CALENDAR))
                PermissionRow("Contacts", "Look up people by name", granted(Manifest.permission.READ_CONTACTS))
                Text("Permissions are requested only when a feature first needs them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text("Open Android app settings") }
            }

            Section("Privacy & security") {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = OrbitColors.Teal)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Your Gemini API key lives only on the server (Supabase secret). This app contains no AI keys. " +
                            "Messages go over HTTPS to your backend and then to Google Gemini. Calendar and contact data are sent only after you approve each request. " +
                            "Chats and memories are stored only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Backend", style = MaterialTheme.typography.labelMedium)
                Text(vm.backendUrl, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Install ID (for rate limiting only)", style = MaterialTheme.typography.labelMedium)
                Text(settings?.installId?.ifBlank { "…" } ?: "…", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(name: String, use: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(18.dp),
            tint = if (granted) OrbitColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(use, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (granted) "Allowed" else "Not allowed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
