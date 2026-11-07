package com.fazz.agent.core.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

/** Lifecycle of a single tool call, for UI status reporting. */
enum class ExecutionStage { VALIDATING, AWAITING_PERMISSION, AWAITING_CONFIRMATION, RUNNING, DONE }

/**
 * Runs a tool call through a fixed safety pipeline:
 * unknown-tool check → schema + semantic validation → permission check/request →
 * user confirmation (sensitive tools) → execution with timeout → structured result.
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val permissions: PermissionChecker,
    private val interaction: UserInteractionGateway,
) {
    suspend fun execute(
        name: String,
        args: JsonObject,
        onStage: (ExecutionStage) -> Unit = {},
    ): ToolResult {
        val tool = registry.get(name)
            ?: return ToolResult.error(ErrorCodes.UNKNOWN_TOOL, "Alat '$name' tidak ada.")

        onStage(ExecutionStage.VALIDATING)
        val invalid = SchemaValidator.validate(tool.parameters, args) ?: tool.validate(args)
        if (invalid != null) return ToolResult.error(ErrorCodes.INVALID_ARGUMENTS, "Argumen tidak valid: $invalid")

        val missing = tool.requiredPermissions.filterNot { permissions.isGranted(it) }
        if (missing.isNotEmpty()) {
            onStage(ExecutionStage.AWAITING_PERMISSION)
            interaction.requestPermissions(missing, tool.permissionRationale)
            // Re-check the real system state rather than trusting the dialog callback.
            val stillMissing = tool.requiredPermissions.filterNot { permissions.isGranted(it) }
            if (stillMissing.isNotEmpty()) {
                return ToolResult.error(
                    ErrorCodes.PERMISSION_DENIED,
                    "Izin belum diberikan (${stillMissing.joinToString { it.substringAfterLast('.') }}). [Model: do not retry; explain how to enable it in Settings.]",
                )
            }
        }

        tool.confirmation(args)?.let { request ->
            onStage(ExecutionStage.AWAITING_CONFIRMATION)
            if (!interaction.confirm(request)) {
                return ToolResult.error(ErrorCodes.USER_DECLINED, "Kamu menolak tindakan ini. [Model: do not retry unless the user asks again.]")
            }
        }

        onStage(ExecutionStage.RUNNING)
        return try {
            withTimeout(tool.timeoutMs) { tool.execute(args) }
        } catch (e: TimeoutCancellationException) {
            ToolResult.error(ErrorCodes.TIMEOUT, "Alat '$name' melewati batas waktu ${tool.timeoutMs / 1000} detik.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            ToolResult.error(ErrorCodes.PERMISSION_DENIED, "Android memblokir tindakan ini: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Alat gagal: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            onStage(ExecutionStage.DONE)
        }
    }
}
