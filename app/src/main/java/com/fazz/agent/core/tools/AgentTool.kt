package com.fazz.agent.core.tools

import com.fazz.agent.core.common.jsonOf
import kotlinx.serialization.json.JsonObject

/**
 * A capability the AI may request. Tools are fixed, typed and reviewed in code: the model can only
 * pick a tool by name and supply JSON arguments. It can never run arbitrary code or shell commands.
 */
interface AgentTool {
    val name: String
    /** Shown to the model. Explain when to use it. */
    val description: String
    /** OpenAPI-subset JSON schema (Gemini function declaration "parameters"). */
    val parameters: JsonObject
    /** Short human label for the activity indicator, e.g. "Checking your calendar". */
    val activityLabel: String
    /** Android runtime permissions needed. Requested from the user on first use. */
    val requiredPermissions: List<String> get() = emptyList()
    /** Why the permissions are needed, shown before the system dialog. */
    val permissionRationale: String get() = ""
    val timeoutMs: Long get() = 15_000L

    /** Extra semantic validation after schema validation. Return an error message or null. */
    fun validate(args: JsonObject): String? = null

    /** Non-null when the action is sensitive and needs explicit user approval before running. */
    fun confirmation(args: JsonObject): ConfirmationRequest? = null

    suspend fun execute(args: JsonObject): ToolResult
}

data class ConfirmationRequest(
    val title: String,
    val message: String,
    val confirmLabel: String = "Allow",
    val denyLabel: String = "Deny",
)

data class ToolResult(
    val success: Boolean,
    val data: JsonObject,
    /** Short, human-readable summary for the UI. */
    val summary: String,
    val errorCode: String? = null,
    /** Optional image (JPEG) sent to the model alongside the result, e.g. a screenshot. */
    val imageJpeg: ByteArray? = null,
) {
    /** What the model receives as functionResponse.response. */
    fun toModelResponse(): JsonObject = if (success) {
        jsonOf("status" to "success", "result" to data)
    } else {
        jsonOf("status" to "error", "error_code" to errorCode, "message" to summary, "details" to data)
    }

    companion object {
        fun ok(summary: String, data: JsonObject = JsonObject(emptyMap())) = ToolResult(true, data, summary)
        fun error(code: String, message: String, data: JsonObject = JsonObject(emptyMap())) =
            ToolResult(false, data, message, code)
    }
}

object ErrorCodes {
    const val UNKNOWN_TOOL = "unknown_tool"
    const val INVALID_ARGUMENTS = "invalid_arguments"
    const val PERMISSION_DENIED = "permission_denied"
    const val USER_DECLINED = "user_declined"
    const val TIMEOUT = "timeout"
    const val EXECUTION_FAILED = "execution_failed"
    const val NOT_FOUND = "not_found"
    const val UNAVAILABLE = "unavailable"
}
