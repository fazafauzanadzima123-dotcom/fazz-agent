package com.orbit.agent.core.tools.impl

import com.orbit.agent.core.common.jsonOf
import com.orbit.agent.core.common.string
import com.orbit.agent.core.tools.AgentTool
import com.orbit.agent.core.tools.ConfirmationRequest
import com.orbit.agent.core.tools.ToolResult
import com.orbit.agent.core.tools.UserInteractionGateway
import com.orbit.agent.core.tools.schema
import kotlinx.serialization.json.JsonObject

/** TOOL 7: lets the model explicitly ask the user a yes/no approval question in a dialog. */
class AskConfirmationTool(private val gateway: UserInteractionGateway) : AgentTool {
    override val name = "ask_user_confirmation"
    override val description = "Show the user a Yes/No approval dialog before doing something with consequences, or when the plan is ambiguous. " +
        "Returns approved=true/false."
    override val activityLabel = "Waiting for your approval"
    override val timeoutMs = 5 * 60_000L
    override val parameters = schema {
        string("question", "The yes/no question to show the user.", required = true, maxLength = 300)
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val question = args.string("question")!!
        val approved = gateway.confirm(ConfirmationRequest("Orbit needs your approval", question, "Yes", "No"))
        return ToolResult.ok(if (approved) "You approved" else "You declined", jsonOf("approved" to approved))
    }
}
