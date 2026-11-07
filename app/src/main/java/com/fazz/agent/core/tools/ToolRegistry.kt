package com.fazz.agent.core.tools

import com.fazz.agent.core.ai.ToolDeclaration

/** The fixed set of tools the model may use. */
class ToolRegistry(tools: List<AgentTool>) {
    private val byName: Map<String, AgentTool>

    init {
        val names = tools.map { it.name }
        require(names.size == names.toSet().size) { "duplicate tool names: $names" }
        require(names.all { NAME_RE.matches(it) }) { "invalid tool name in $names" }
        byName = tools.associateBy { it.name }
    }

    val all: List<AgentTool> get() = byName.values.toList()

    fun get(name: String): AgentTool? = byName[name]

    fun declarations(): List<ToolDeclaration> = byName.values.map {
        ToolDeclaration(it.name, it.description, it.parameters)
    }

    companion object {
        private val NAME_RE = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")
    }
}
