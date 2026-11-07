package com.orbit.agent

import android.content.Context
import com.orbit.agent.core.agent.AgentOrchestrator
import com.orbit.agent.core.ai.AiService
import com.orbit.agent.core.ai.SupabaseAiService
import com.orbit.agent.core.security.AndroidPermissionChecker
import com.orbit.agent.core.tools.InteractionBroker
import com.orbit.agent.core.tools.ToolExecutor
import com.orbit.agent.core.tools.ToolRegistry
import com.orbit.agent.core.tools.impl.AskConfirmationTool
import com.orbit.agent.core.tools.impl.ContactsTool
import com.orbit.agent.core.tools.impl.CreateCalendarEventTool
import com.orbit.agent.core.tools.impl.DateTimeTool
import com.orbit.agent.core.tools.impl.DeviceInfoTool
import com.orbit.agent.core.tools.impl.OpenAppTool
import com.orbit.agent.core.tools.impl.ReadCalendarTool
import com.orbit.agent.core.tools.impl.RememberTool
import com.orbit.agent.core.tools.impl.SetTimerTool
import com.orbit.agent.core.tools.impl.ShareTextTool
import com.orbit.agent.core.tools.impl.WebSearchProvider
import com.orbit.agent.core.tools.impl.WebSearchTool
import com.orbit.agent.data.local.ConversationRepository
import com.orbit.agent.data.local.FileMemoryRepository
import com.orbit.agent.data.local.MemoryRepository
import com.orbit.agent.data.local.SettingsRepository
import java.io.File

/** Manual dependency injection: one place that builds and wires the app's services. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = SettingsRepository(app, BuildConfig.DEFAULT_MODEL)
    val memory: MemoryRepository = FileMemoryRepository(File(app.filesDir, "memory/memories.json"))
    val conversations = ConversationRepository(File(app.filesDir, "conversations/current.json"))

    val aiService: AiService = SupabaseAiService(
        baseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        installIdProvider = { settings.installId() },
    )

    val interaction = InteractionBroker()

    private val webSearch = WebSearchProvider { query -> aiService.webSearch(query, settings.current().modelId) }

    val toolRegistry = ToolRegistry(
        listOf(
            OpenAppTool(app),
            DateTimeTool(),
            ReadCalendarTool(app),
            CreateCalendarEventTool(app),
            ContactsTool(app),
            DeviceInfoTool(app),
            WebSearchTool(webSearch),
            AskConfirmationTool(interaction),
            RememberTool(memory),
            ShareTextTool(app),
            SetTimerTool(app),
        ),
    )

    val toolExecutor = ToolExecutor(toolRegistry, AndroidPermissionChecker(app), interaction)

    val orchestrator = AgentOrchestrator(aiService, toolRegistry, toolExecutor)
}
