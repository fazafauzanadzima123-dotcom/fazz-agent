package com.fazz.agent.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole { USER, ASSISTANT, NOTICE }

@Serializable
enum class ToolStatus { RUNNING, SUCCESS, FAILED, DENIED }

/** A single tool invocation shown inside an assistant message ("Checked your calendar"). */
@Serializable
data class ToolActivity(
    val callId: String,
    val toolName: String,
    val label: String,
    val status: ToolStatus,
    val detail: String? = null,
)

@Serializable
data class TokenUsage(val promptTokens: Int = 0, val outputTokens: Int = 0, val totalTokens: Int = 0)

/** What the chat screen renders. Separate from the raw model conversation ("contents"). */
@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val tools: List<ToolActivity> = emptyList(),
    val error: String? = null,
    val usage: TokenUsage? = null,
    val viaVoice: Boolean = false,
    /** Paths of attached images (app-private files). */
    val images: List<String> = emptyList(),
    /** Documents/pages/code Fazz created for the user, opened in a side viewer. */
    val artifacts: List<Artifact> = emptyList(),
)

@Serializable
enum class ArtifactType { HTML, SVG, MARKDOWN, CODE }

@Serializable
data class Artifact(
    val id: String,
    val title: String,
    val type: ArtifactType,
    val content: String,
    val language: String = "",
)

@Serializable
enum class MemoryCategory { PREFERENCE, PERSONAL, TASK, OTHER }

/** A user-approved long-term memory. Never written without explicit confirmation. */
@Serializable
data class MemoryItem(
    val id: String,
    val text: String,
    val category: MemoryCategory,
    val createdAt: Long,
)

data class AppSettings(
    val speakReplies: Boolean,
    val autoSendVoice: Boolean,
    val installId: String,
    val briefingEnabled: Boolean,
    val briefingHour: Int,
    val screenDisclosureAccepted: Boolean,
)

/** High-level agent state for the activity indicator. */
sealed interface AgentState {
    data object Idle : AgentState
    data object Thinking : AgentState
    data object Responding : AgentState
    data class UsingTool(val label: String) : AgentState
    data object WaitingForUser : AgentState
}
