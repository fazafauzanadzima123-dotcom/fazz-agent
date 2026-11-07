package com.fazz.agent.core.tools.impl

import com.fazz.agent.core.ai.AiException
import com.fazz.agent.core.ai.WebSearchResult
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.serialization.json.JsonObject

/** Modular web search interface; swap the implementation without touching the tool. */
fun interface WebSearchProvider {
    suspend fun search(query: String): WebSearchResult
}

/** TOOL 6: real web search. Results come from the provider; nothing is invented. */
class WebSearchTool(private val provider: WebSearchProvider) : AgentTool {
    override val name = "web_search"
    override val description = "Search the web for current or factual information (news, prices, opening hours, facts after your training). " +
        "Returns an answer grounded in search results plus source URLs. Always cite the sources you use."
    override val activityLabel = "Mencari di web"
    override val timeoutMs = 45_000L
    override val parameters = schema {
        string("query", "The search query.", required = true, maxLength = 300)
    }

    override fun validate(args: JsonObject): String? =
        if ((args.string("query")?.trim()?.length ?: 0) < 2) "query is too short" else null

    override suspend fun execute(args: JsonObject): ToolResult {
        val query = args.string("query")!!.trim()
        val result = try {
            provider.search(query)
        } catch (e: AiException) {
            return ToolResult.error(ErrorCodes.UNAVAILABLE, "Pencarian web gagal: ${e.error.userMessage}")
        }
        if (!result.grounded || result.sources.isEmpty()) {
            return ToolResult.error(
                ErrorCodes.NOT_FOUND,
                "Tidak ada sumber yang bisa diverifikasi. [Model: tell the user no reliable results were found; do not guess.]",
                jsonOf("unverified_text" to result.answer.take(500)),
            )
        }
        return ToolResult.ok(
            "${result.sources.size} sumber ditemukan",
            jsonOf(
                "answer" to result.answer,
                "sources" to result.sources.map { mapOf("title" to it.title, "url" to it.url) },
            ),
        )
    }
}
