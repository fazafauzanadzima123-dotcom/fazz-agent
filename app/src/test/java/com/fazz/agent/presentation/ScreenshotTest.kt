package com.fazz.agent.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.fazz.agent.FazzApp
import com.fazz.agent.domain.models.ChatMessage
import com.fazz.agent.domain.models.MessageRole
import com.fazz.agent.domain.models.ToolActivity
import com.fazz.agent.domain.models.ToolStatus
import com.fazz.agent.presentation.chat.ChatScreen
import com.fazz.agent.presentation.chat.ChatViewModel
import com.fazz.agent.presentation.components.AssistantMessage
import com.fazz.agent.presentation.components.Composer
import com.fazz.agent.presentation.components.ListeningPanel
import com.fazz.agent.presentation.components.StepsGroup
import com.fazz.agent.presentation.components.UserBubble
import com.fazz.agent.presentation.settings.MemoryScreen
import com.fazz.agent.presentation.settings.SettingsScreen
import com.fazz.agent.presentation.settings.SettingsViewModel
import com.fazz.agent.presentation.theme.FazzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens to PNG on the JVM (no emulator needed).
 * Record: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Proborazzi.record=true
 * Output: app/build/outputs/roborazzi/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<FazzApp>()
    private fun out(name: String) = "build/outputs/roborazzi/$name.png"

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { FazzTheme(darkTheme = dark) { content() } }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage(out(name))
    }

    @Test fun chatHome() = shot("01_chat_home") { ChatScreen(ChatViewModel(app.container, app), {}, {}) }

    @Test fun chatHomeDark() = shot("02_chat_home_dark", dark = true) { ChatScreen(ChatViewModel(app.container, app), {}, {}) }

    @Test fun conversation() = shot("03_conversation") { SampleConversation(expanded = false) }

    @Test fun conversationDark() = shot("04_conversation_dark", dark = true) { SampleConversation(expanded = false) }

    @Test fun settings() = shot("05_settings") { SettingsScreen(SettingsViewModel(app.container), {}, {}) }

    @Test fun memory() = shot("06_memory") { MemoryScreen(SettingsViewModel(app.container)) {} }

    @Test fun listening() = shot("07_listening") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.Bottom) {
            ListeningPanel(level = 0.6f, partial = "Kirim WhatsApp ke Budi bilang aku telat 10 menit", processing = false, onDone = {}, onCancel = {})
        }
    }

    @Test fun agentSteps() = shot("08_agent_steps") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            StepsGroup(sampleSteps(running = false), running = false)
        }
    }
}

private fun sampleSteps(running: Boolean) = listOf(
    ToolActivity("1", "search_contacts", "Mencari kontak", ToolStatus.SUCCESS, "1 kontak ditemukan"),
    ToolActivity("2", "open_url", "Membuka tautan", ToolStatus.SUCCESS, "Tautan dibuka"),
    ToolActivity("3", "read_screen", "Membaca layar", ToolStatus.SUCCESS, "WhatsApp: 42 elemen"),
    ToolActivity("4", "tap", "Mengetuk", if (running) ToolStatus.RUNNING else ToolStatus.SUCCESS, if (running) null else "Mengetuk \"Kirim\""),
)

@Composable
private fun SampleConversation(expanded: Boolean) {
    val now = 0L
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            UserBubble(ChatMessage("1", MessageRole.USER, "Kirim WhatsApp ke Budi: aku telat 10 menit ya", now, viaVoice = true))
            AssistantMessage(
                ChatMessage("2", MessageRole.ASSISTANT,
                    "Sudah terkirim ke **Budi** lewat WhatsApp: \"Aku telat 10 menit ya\". Mau saya pasang pengingat juga?",
                    now, tools = sampleSteps(running = false)),
                isLast = false, onRetry = {}, onSpeak = {},
            )
            UserBubble(ChatMessage("3", MessageRole.USER, "Ya, ingatkan 10 menit lagi", now))
            AssistantMessage(
                ChatMessage("4", MessageRole.ASSISTANT, "", now, isStreaming = true,
                    tools = listOf(ToolActivity("5", "set_reminder", "Memasang pengingat", ToolStatus.RUNNING))),
                isLast = true, onRetry = {}, onSpeak = {},
            )
        }
        Spacer(Modifier.height(12.dp))
        Composer("", emptyList(), false, busy = true, onTextChange = {}, onRemoveImage = {}, onPlus = {}, onMic = {}, onSend = {}, onStop = {}, onVoiceMode = {},
            modifier = Modifier.fillMaxWidth())
        if (expanded) Unit
    }
}
