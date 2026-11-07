package com.fazz.agent.core.tools.impl

import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.UserInteractionGateway
import com.fazz.agent.core.tools.schema
import kotlinx.serialization.json.JsonObject

/** TOOL 7: lets the model explicitly ask the user a yes/no approval question in a dialog. */
class AskConfirmationTool(private val gateway: UserInteractionGateway) : AgentTool {
    override val name = "ask_user_confirmation"
    override val description = "Show the user a Yes/No approval dialog before doing something with consequences, or when the plan is ambiguous. " +
        "Returns approved=true/false."
    override val activityLabel = "Menunggu persetujuanmu"
    override val timeoutMs = 5 * 60_000L
    override val parameters = schema {
        string("question", "The yes/no question to show the user.", required = true, maxLength = 300)
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val question = args.string("question")!!
        val approved = gateway.confirm(ConfirmationRequest("Fazz minta persetujuan", question, "Ya", "Tidak"))
        return ToolResult.ok(if (approved) "Kamu setuju" else "Kamu menolak", jsonOf("approved" to approved))
    }
}
