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
import com.fazz.agent.domain.models.TokenUsage
import com.fazz.agent.domain.models.ToolActivity
import com.fazz.agent.domain.models.ToolStatus
import com.fazz.agent.presentation.chat.ChatScreen
import com.fazz.agent.presentation.chat.ChatViewModel
import com.fazz.agent.presentation.components.AssistantMessage
import com.fazz.agent.presentation.components.InputBar
import com.fazz.agent.presentation.components.ListeningPanel
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

    @Test fun chatHome() = shot("01_chat_home") { ChatScreen(ChatViewModel(app.container, app)) {} }

    @Test fun chatHomeDark() = shot("02_chat_home_dark", dark = true) { ChatScreen(ChatViewModel(app.container, app)) {} }

    @Test fun conversation() = shot("03_conversation") { SampleConversation() }

    @Test fun conversationDark() = shot("04_conversation_dark", dark = true) { SampleConversation() }

    @Test fun settings() = shot("05_settings") { SettingsScreen(SettingsViewModel(app.container), {}, {}) }

    @Test fun memory() = shot("06_memory") { MemoryScreen(SettingsViewModel(app.container)) {} }

    @Test fun listening() = shot("07_listening") {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.Bottom) {
            ListeningPanel(level = 0.6f, partial = "Apa jadwalku besok pagi?", processing = false, onDone = {}, onCancel = {})
        }
    }
}

@Composable
private fun SampleConversation() {
    val now = 0L
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            UserBubble(ChatMessage("1", MessageRole.USER, "Berapa persen baterai HP-ku? Lalu buka YouTube ya", now, viaVoice = true))
            AssistantMessage(
                ChatMessage(
                    "2", MessageRole.ASSISTANT,
                    "Baterai kamu **73%** dan sedang tidak dicas. YouTube sudah saya buka — selamat menonton!",
                    now,
                    tools = listOf(
                        ToolActivity("a", "get_device_info", "Mengecek status HP", ToolStatus.SUCCESS, "Pixel 8, baterai 73%"),
                        ToolActivity("b", "open_app", "Membuka aplikasi", ToolStatus.SUCCESS, "YouTube dibuka"),
                    ),
                    usage = TokenUsage(120, 40, 160),
                ),
                onRetry = {},
            )
            UserBubble(ChatMessage("3", MessageRole.USER, "Cek jadwalku besok", now))
            AssistantMessage(
                ChatMessage(
                    "4", MessageRole.ASSISTANT, "", now, isStreaming = true,
                    tools = listOf(ToolActivity("c", "read_calendar_events", "Mengecek kalender", ToolStatus.RUNNING)),
                ),
                onRetry = {},
            )
        }
        Spacer(Modifier.height(12.dp))
        InputBar("", {}, busy = true, onSend = {}, onStop = {}, onMic = {}, modifier = Modifier.fillMaxWidth())
    }
}
