package com.fazz.agent.core.tools.impl

import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.common.string
import com.fazz.agent.core.security.SensitiveDataDetector
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ConfirmationRequest
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import com.fazz.agent.data.local.MemoryRepository
import com.fazz.agent.domain.models.MemoryCategory
import kotlinx.serialization.json.JsonObject

/** Saves a fact to long-term memory, only after the user approves the exact text. */
class RememberTool(private val memory: MemoryRepository) : AgentTool {
    override val name = "save_memory"
    override val description = "Save a short, useful fact about the user to long-term memory (e.g. a preference). " +
        "Only use when the user asks you to remember something or clearly states a lasting preference. " +
        "Never save passwords, PINs, codes, financial or health details."
    override val activityLabel = "Menyimpan ke memori"
    override val parameters = schema {
        string("fact", "The fact to remember, written as a short sentence.", required = true, maxLength = 300)
        string("category", "Kind of memory.", required = true, enum = MemoryCategory.entries.map { it.name.lowercase() })
    }

    override fun validate(args: JsonObject): String? {
        val fact = args.string("fact")?.trim().orEmpty()
        if (fact.length < 3) return "fact is too short"
        SensitiveDataDetector.findReason(fact)?.let { return "menolak menyimpan karena $it" }
        return null
    }

    override fun confirmation(args: JsonObject) = ConfirmationRequest(
        title = "Ingat ini?",
        message = "Fazz ingin menyimpan ke memori:\n\n\"${args.string("fact")}\"\n\nKamu bisa melihat atau menghapusnya kapan saja di Pengaturan → Memori.",
        confirmLabel = "Simpan",
        denyLabel = "Jangan",
    )

    override suspend fun execute(args: JsonObject): ToolResult {
        val category = MemoryCategory.valueOf(args.string("category")!!.uppercase())
        val item = memory.add(args.string("fact")!!, category)
        return ToolResult.ok("Tersimpan di memori", jsonOf("memory_id" to item.id))
    }
}
