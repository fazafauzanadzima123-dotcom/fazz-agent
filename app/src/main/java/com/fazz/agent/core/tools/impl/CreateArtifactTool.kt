package com.fazz.agent.core.tools.impl

import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import com.fazz.agent.domain.models.Artifact
import com.fazz.agent.domain.models.ArtifactType
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Creates a document, page, SVG or code file that appears as a card the user can open, copy and share. */
class CreateArtifactTool : AgentTool {
    override val name = "create_artifact"
    override val description = "Create a standalone piece of content for the user: a complete web page/app (html, a single self-contained file with inline CSS/JS), " +
        "an svg image, a markdown document, or a code file. The user sees it as a card and can preview, copy or share it. " +
        "Put the FULL content here; in your chat reply only add a short summary."
    override val activityLabel = "Membuat artefak"
    override val parameters = schema {
        string("title", "Short title, e.g. \"Kalkulator cicilan\".", required = true, maxLength = 80)
        string("type", "Kind of content.", required = true, enum = listOf("html", "svg", "markdown", "code"))
        string("content", "The complete content.", required = true, maxLength = 100_000)
        string("language", "Programming language when type is code (e.g. python, kotlin).", maxLength = 30)
    }

    override fun validate(args: JsonObject): String? {
        if (args.string("title").isNullOrBlank()) return "title is required"
        if (args.string("content").isNullOrBlank()) return "content is required"
        return null
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        val type = when (args.string("type")) {
            "html" -> ArtifactType.HTML
            "svg" -> ArtifactType.SVG
            "markdown" -> ArtifactType.MARKDOWN
            "code" -> ArtifactType.CODE
            else -> return ToolResult.error(ErrorCodes.INVALID_ARGUMENTS, "type harus html, svg, markdown, atau code")
        }
        val a = Artifact(UUID.randomUUID().toString(), args.string("title")!!.trim(), type, args.string("content")!!, args.string("language").orEmpty())
        return ToolResult.ok("Artefak \"${a.title}\" dibuat", jsonOf("artifact_id" to a.id, "note" to "Shown to the user as a card.")).copy(artifact = a)
    }
}
