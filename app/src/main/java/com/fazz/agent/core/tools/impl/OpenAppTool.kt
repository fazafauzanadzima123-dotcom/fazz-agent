package com.fazz.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** TOOL 1: open an installed app by name. Only apps with a launcher entry are visible/launchable. */
class OpenAppTool(private val context: Context) : AgentTool {
    override val name = "open_app"
    override val description = "Open an installed Android app on the user's phone by its name (e.g. 'YouTube', 'Camera', 'WhatsApp'). " +
        "Returns success, or not_found with similar installed app names."
    override val activityLabel = "Membuka aplikasi"
    override val parameters = schema {
        string("app_name", "The app's display name as the user said it.", required = true, maxLength = 80)
    }

    override fun validate(args: JsonObject): String? =
        if (args.string("app_name").isNullOrBlank()) "app_name must not be empty" else null

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args.string("app_name")!!.trim()
        val apps = withContext(Dispatchers.IO) { launchableApps() }
        val match = AppMatcher.bestMatch(query, apps)
            ?: return ToolResult.error(
                ErrorCodes.NOT_FOUND,
                "Tidak ada aplikasi bernama '$query'.",
                jsonOf("similar_apps" to AppMatcher.suggestions(query, apps).map { it.label }),
            )
        val intent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return ToolResult.error(ErrorCodes.UNAVAILABLE, "'${match.label}' terpasang tapi tidak bisa dibuka.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) { context.startActivity(intent) }
        return ToolResult.ok("${match.label} dibuka", jsonOf("opened_app" to match.label, "package" to match.packageName))
    }

    private fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(main, 0)
        }
        return infos
            .filter { it.activityInfo.packageName != context.packageName }
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
    }
}
