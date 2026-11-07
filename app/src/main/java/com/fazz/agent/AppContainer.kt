package com.fazz.agent

import android.content.Context
import com.fazz.agent.core.agent.AgentOrchestrator
import com.fazz.agent.core.agent.AgentSession
import com.fazz.agent.core.ai.AiService
import com.fazz.agent.core.ai.SupabaseAiService
import com.fazz.agent.core.proactive.BriefingScheduler
import com.fazz.agent.core.proactive.NotificationStore
import com.fazz.agent.core.proactive.Notifier
import com.fazz.agent.core.proactive.ReminderScheduler
import com.fazz.agent.core.screen.FazzAccessibilityService
import com.fazz.agent.core.screen.LookAtScreenTool
import com.fazz.agent.core.screen.PressKeyTool
import com.fazz.agent.core.screen.ReadScreenTool
import com.fazz.agent.core.screen.ScreenControllerProvider
import com.fazz.agent.core.screen.ScrollTool
import com.fazz.agent.core.screen.SwipeTool
import com.fazz.agent.core.screen.TapTool
import com.fazz.agent.core.screen.TypeTextTool
import com.fazz.agent.core.screen.WaitTool
import com.fazz.agent.core.security.AndroidPermissionChecker
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.InteractionBroker
import com.fazz.agent.core.tools.ToolExecutor
import com.fazz.agent.core.tools.ToolRegistry
import com.fazz.agent.core.tools.UserInteractionGateway
import com.fazz.agent.core.tools.impl.AskConfirmationTool
import com.fazz.agent.core.tools.impl.CallPhoneTool
import com.fazz.agent.core.tools.impl.ComposeEmailTool
import com.fazz.agent.core.tools.impl.ContactsTool
import com.fazz.agent.core.tools.impl.CreateCalendarEventTool
import com.fazz.agent.core.tools.impl.DateTimeTool
import com.fazz.agent.core.tools.impl.DeviceInfoTool
import com.fazz.agent.core.tools.impl.MediaControlTool
import com.fazz.agent.core.tools.impl.NavigateTool
import com.fazz.agent.core.tools.impl.OpenAppTool
import com.fazz.agent.core.tools.impl.OpenUrlTool
import com.fazz.agent.core.tools.impl.CreateArtifactTool
import com.fazz.agent.core.tools.impl.DriveSaveDocTool
import com.fazz.agent.core.tools.impl.GetLocationTool
import com.fazz.agent.core.tools.impl.GmailDraftTool
import com.fazz.agent.core.tools.impl.GmailReadTool
import com.fazz.agent.core.tools.impl.GmailSearchTool
import com.fazz.agent.core.tools.impl.GmailSendTool
import com.fazz.agent.core.tools.impl.GoogleCalendarCreateTool
import com.fazz.agent.core.tools.impl.GoogleCalendarListTool
import com.fazz.agent.core.tools.impl.GoogleTokens
import com.fazz.agent.core.tools.impl.ReadCalendarTool
import com.fazz.agent.core.tools.impl.ReadNotificationsTool
import com.fazz.agent.core.tools.impl.RememberTool
import com.fazz.agent.core.tools.impl.SendSmsTool
import com.fazz.agent.core.tools.impl.SetAlarmTool
import com.fazz.agent.core.tools.impl.SetReminderTool
import com.fazz.agent.core.tools.impl.SetTimerTool
import com.fazz.agent.core.tools.impl.ShareTextTool
import com.fazz.agent.core.tools.impl.WebSearchProvider
import com.fazz.agent.core.tools.impl.WebSearchTool
import com.fazz.agent.core.voice.SpeechInputController
import com.fazz.agent.core.voice.TextToSpeechController
import com.fazz.agent.data.local.ConversationStore
import com.fazz.agent.data.local.FileMemoryRepository
import com.fazz.agent.data.local.MemoryRepository
import com.fazz.agent.data.local.SettingsRepository
import com.fazz.agent.core.common.AppVisibility
import com.fazz.agent.core.tools.PendingInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency injection: one place that builds and wires the app's services. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsRepository(app)
    val memory: MemoryRepository = FileMemoryRepository(File(app.filesDir, "memory/memories.json"))
    val conversations = ConversationStore(File(app.filesDir, "conversations"))
    val notifier = Notifier(app).also { it.createChannels() }
    val notifications = NotificationStore(app)
    val reminders = ReminderScheduler(app)
    val briefing = BriefingScheduler(app)
    val tts = TextToSpeechController(app)
    val speech = SpeechInputController(app)

    val aiService: AiService = SupabaseAiService(
        baseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        installIdProvider = { settings.installId() },
    )

    val interaction = InteractionBroker()

    val googleAccess = com.fazz.agent.core.google.GoogleAccess(app)
    private val googleTokens = GoogleTokens { service ->
        val s = settings.current()
        if (s.googleEmail.isBlank() || service.id !in s.googleServices) null else googleAccess.silentToken(service, s.googleEmail)
    }
    private val permissions = AndroidPermissionChecker(app)
    private val screen = ScreenControllerProvider { FazzAccessibilityService.instance }
    private val webSearch = WebSearchProvider { query -> aiService.webSearch(query, null) }

    private fun allTools(gateway: UserInteractionGateway): List<AgentTool> = listOf(
        // Phone & information
        OpenAppTool(app), DateTimeTool(), DeviceInfoTool(app), WebSearchTool(webSearch),
        ReadCalendarTool(app), CreateCalendarEventTool(app), ContactsTool(app),
        // Communication & integrations
        SendSmsTool(app), CallPhoneTool(app), ComposeEmailTool(app), OpenUrlTool(app), ShareTextTool(app),
        NavigateTool(app), SetAlarmTool(app), SetTimerTool(app), SetReminderTool(reminders), MediaControlTool(app),
        ReadNotificationsTool(notifications),
        // Screen control (autonomy)
        ReadScreenTool(screen), TapTool(screen), TypeTextTool(screen), ScrollTool(screen), PressKeyTool(screen),
        SwipeTool(screen), LookAtScreenTool(screen), WaitTool(),
        // Memory & approval
        RememberTool(memory), AskConfirmationTool(gateway), CreateArtifactTool(), GetLocationTool(app),
        // Google account (Calendar, Gmail, Drive/Docs)
        GoogleCalendarListTool(googleTokens), GoogleCalendarCreateTool(googleTokens), GmailSearchTool(googleTokens), GmailReadTool(googleTokens),
        GmailDraftTool(googleTokens), GmailSendTool(googleTokens), DriveSaveDocTool(googleTokens),
    )

    val toolRegistry = ToolRegistry(allTools(interaction))
    val toolExecutor = ToolExecutor(toolRegistry, permissions, interaction)

    /** Tools the user switched off in Sesuaikan; kept current from settings. */
    @Volatile private var disabledTools: Set<String> = setOf(com.fazz.agent.domain.models.LOCATION_TOOL)

    private fun interactiveOrchestrator(): AgentOrchestrator {
        val registry = ToolRegistry(allTools(interaction).filter { it.name !in disabledTools })
        return AgentOrchestrator(aiService, registry, ToolExecutor(registry, permissions, interaction), maxSteps = 30)
    }

    /**
     * Background (no UI) runs: only the given read-only tools, and sharing data with the AI is pre-approved
     * because the user turned the feature on. Runtime permissions cannot be requested in the background.
     */
    private fun backgroundOrchestrator(allowed: Set<String>): AgentOrchestrator {
        val gateway = object : UserInteractionGateway {
            override suspend fun requestPermissions(permissions: List<String>, rationale: String) = false
            override suspend fun confirm(request: ConfirmationRequest) = true
        }
        val registry = ToolRegistry(allTools(gateway).filter { it.name in allowed })
        return AgentOrchestrator(aiService, registry, ToolExecutor(registry, permissions, gateway), maxSteps = 8)
    }

    val session = AgentSession(
        context = app,
        orchestrator = ::interactiveOrchestrator,
        backgroundOrchestrator = ::backgroundOrchestrator,
        settings = settings,
        memory = memory,
        store = conversations,
        tts = tts,
        notifier = notifier,
        notificationAccess = { notifications.listenerEnabled() },
    )

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        appScope.launch { settings.settings.collect { disabledTools = it.disabledTools } }
        // Approvals must always be reachable: in-app dialog when Fazz is visible, otherwise overlay + notification.
        appScope.launch {
            combine(interaction.pending, AppVisibility.foreground) { p, fg -> p to fg }.collect { (p, fg) ->
                if (p is PendingInteraction.Confirmation && !fg) {
                    notifier.showApproval(p.request.title, p.request.message, p.request.confirmLabel, p.request.denyLabel)
                } else {
                    notifier.cancel(Notifier.ID_APPROVAL)
                }
            }
        }
    }
}
