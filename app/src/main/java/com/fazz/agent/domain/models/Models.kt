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
    val responseStyle: String = ResponseStyle.NORMAL.id,
    val setupSeen: Boolean = false,
    val nickname: String = "",
    val about: String = "",
    val instructions: String = "",
    val tone: String = Tone.NEUTRAL.id,
    val answerLength: String = AnswerLength.AUTO.id,
    val emoji: String = EmojiLevel.LIGHT.id,
    val locationAllowed: Boolean = false,
    val disabledPlugins: Set<String> = emptySet(),
    val googleName: String = "",
    val googleEmail: String = "",
    val googlePhoto: String = "",
    val googleServices: Set<String> = emptySet(),
) {
    val profile: Profile
        get() = Profile(nickname, about, instructions, ResponseStyle.from(responseStyle), Tone.from(tone), AnswerLength.from(answerLength), EmojiLevel.from(emoji))

    /** Tool names the user has switched off (plugins) or not allowed (location). */
    val disabledTools: Set<String>
        get() = buildSet {
            disabledPlugins.mapNotNull(PluginGroup::from).forEach { addAll(it.tools) }
            if (!locationAllowed) add(LOCATION_TOOL)
            // Google tools only exist for services the signed-in user has connected (and not switched off).
            val googleOn = googleEmail.isNotBlank() && PluginGroup.GOOGLE.id !in disabledPlugins
            GoogleToolMap.forEach { (service, tools) -> if (!googleOn || service !in googleServices) addAll(tools) }
        }
}

/** How Fazz words its answers (like Claude's response styles). */
enum class ResponseStyle(val id: String, val label: String, val hint: String) {
    NORMAL("normal", "Normal", ""),
    CONCISE("ringkas", "Ringkas", "Style: very concise. Answer in as few words as possible; no preamble, no recap."),
    FORMAL("formal", "Formal", "Style: formal and professional Indonesian (baku). No slang or emoji."),
    CASUAL("santai", "Santai", "Style: relaxed, friendly and conversational, like a helpful friend. Light humor is fine."),
    EXPLAINER("penjelas", "Penjelas", "Style: patient teacher. Explain step by step with simple examples and analogies."),
    ;
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: NORMAL }
}

/** High-level agent state for the activity indicator. */
sealed interface AgentState {
    data object Idle : AgentState
    data object Thinking : AgentState
    data object Responding : AgentState
    data class UsingTool(val label: String) : AgentState
    data object WaitingForUser : AgentState
}
