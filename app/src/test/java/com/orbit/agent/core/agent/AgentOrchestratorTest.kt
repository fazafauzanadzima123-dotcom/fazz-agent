package com.orbit.agent.core.agent

import com.orbit.agent.core.ai.AiError
import com.orbit.agent.core.ai.AiException
import com.orbit.agent.core.ai.AiService
import com.orbit.agent.core.ai.AiStreamEvent
import com.orbit.agent.core.ai.ChatRequest
import com.orbit.agent.core.ai.ModelList
import com.orbit.agent.core.ai.WebSearchResult
import com.orbit.agent.core.common.jsonOf
import com.orbit.agent.core.tools.AgentTool
import com.orbit.agent.core.tools.ConfirmationRequest
import com.orbit.agent.core.tools.PermissionChecker
import com.orbit.agent.core.tools.ToolExecutor
import com.orbit.agent.core.tools.ToolRegistry
import com.orbit.agent.core.tools.ToolResult
import com.orbit.agent.core.tools.UserInteractionGateway
import com.orbit.agent.core.tools.schema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentOrchestratorTest {

    /** Scripted model: each call to streamChat returns the next scripted turn. */
    private class ScriptedAi(private val turns: List<List<AiStreamEvent>>) : AiService {
        val requests = mutableListOf<ChatRequest>()
        override fun streamChat(request: ChatRequest): Flow<AiStreamEvent> = flow {
            requests += request
            val turn = turns.getOrNull(requests.size - 1) ?: throw AiException(AiError.Server("test", "no more turns"))
            turn.forEach { emit(it) }
        }
        override suspend fun webSearch(query: String, model: String?) = WebSearchResult("", emptyList(), false)
        override suspend fun listModels() = ModelList("m", emptyList())
    }

    private class TimeTool : AgentTool {
        var calls = 0
        override val name = "get_time"
        override val description = "time"
        override val activityLabel = "Checking time"
        override val parameters = schema()
        override suspend fun execute(args: JsonObject): ToolResult { calls++; return ToolResult.ok("12:00", jsonOf("time" to "12:00")) }
    }

    private val allowAll = object : UserInteractionGateway {
        override suspend fun requestPermissions(permissions: List<String>, rationale: String) = true
        override suspend fun confirm(request: ConfirmationRequest) = true
    }

    private fun text(t: String) = AiStreamEvent.TextDelta(t, jsonOf("text" to t))
    private fun call(name: String, sig: String? = null): AiStreamEvent.FunctionCall {
        val part = if (sig == null) jsonOf("functionCall" to mapOf("name" to name, "args" to emptyMap<String, Any>()))
        else jsonOf("functionCall" to mapOf("name" to name, "args" to emptyMap<String, Any>()), "thoughtSignature" to sig)
        return AiStreamEvent.FunctionCall(null, name, JsonObject(emptyMap()), part)
    }

    private fun orchestrator(ai: AiService, tool: TimeTool = TimeTool(), maxSteps: Int = 8): AgentOrchestrator {
        val reg = ToolRegistry(listOf(tool))
        return AgentOrchestrator(ai, reg, ToolExecutor(reg, PermissionChecker { true }, allowAll), maxSteps)
    }

    @Test fun `plain answer completes in one step`() = runTest {
        val ai = ScriptedAi(listOf(listOf(text("Hi "), text("there"), AiStreamEvent.Finished("STOP"))))
        val events = orchestrator(ai).run(emptyList(), "hello", "sys", "m").toList()
        val done = events.last() as AgentEvent.Completed
        assertEquals("Hi there", done.finalText)
        assertEquals(2, done.contents.size) // user + model
        // Adjacent text parts are merged in history.
        val modelParts = done.contents[1]["parts"] as JsonArray
        assertEquals(1, modelParts.size)
    }

    @Test fun `tool call is executed and result sent back`() = runTest {
        val tool = TimeTool()
        val ai = ScriptedAi(listOf(
            listOf(call("get_time", sig = "SIG"), AiStreamEvent.Finished("STOP")),
            listOf(text("It is 12:00."), AiStreamEvent.Finished("STOP")),
        ))
        val events = orchestrator(ai, tool).run(emptyList(), "what time?", "sys", "m").toList()
        assertEquals(1, tool.calls)
        assertTrue(events.any { it is AgentEvent.ToolStarted })
        assertTrue(events.any { it is AgentEvent.ToolFinished && it.result.success })
        val done = events.last() as AgentEvent.Completed
        assertEquals("It is 12:00.", done.finalText)
        // Second request contains: user, model(functionCall w/ signature), user(functionResponse)
        val second = ai.requests[1].contents
        assertEquals(3, second.size)
        val modelPart = (second[1]["parts"] as JsonArray)[0] as JsonObject
        assertEquals(JsonPrimitive("SIG"), modelPart["thoughtSignature"])
        val respPart = (second[2]["parts"] as JsonArray)[0] as JsonObject
        assertTrue(respPart.containsKey("functionResponse"))
        // Tool declarations are sent with every request.
        assertEquals("get_time", ai.requests[0].tools.single().name)
    }

    @Test fun `unknown tool returns error to model instead of crashing`() = runTest {
        val ai = ScriptedAi(listOf(
            listOf(call("delete_everything")),
            listOf(text("I can't do that."), AiStreamEvent.Finished("STOP")),
        ))
        val events = orchestrator(ai).run(emptyList(), "x", "sys", "m").toList()
        val finished = events.filterIsInstance<AgentEvent.ToolFinished>().single()
        assertEquals("unknown_tool", finished.result.errorCode)
        assertTrue(events.last() is AgentEvent.Completed)
    }

    @Test fun `stops after max steps`() = runTest {
        val loop = List(5) { listOf(call("get_time")) }
        val events = orchestrator(ScriptedAi(loop), maxSteps = 3).run(emptyList(), "x", "sys", "m").toList()
        val done = events.last() as AgentEvent.Completed
        assertTrue(done.finalText.contains("stopped after 3 steps"))
    }

    @Test fun `failure returns sanitized history`() = runTest {
        val history = listOf(AgentOrchestrator.userContent("earlier"), jsonOf("role" to "model", "parts" to listOf(mapOf("text" to "ok"))))
        val ai = ScriptedAi(listOf(listOf(call("get_time")))) // second turn throws
        val events = orchestrator(ai).run(history, "now", "sys", "m").toList()
        val failed = events.last() as AgentEvent.Failed
        assertEquals(history, failed.contents)
    }

    @Test fun `empty response is an error`() = runTest {
        val events = orchestrator(ScriptedAi(listOf(listOf(AiStreamEvent.Finished("STOP"))))).run(emptyList(), "x", "s", "m").toList()
        assertTrue(events.last() is AgentEvent.Failed)
    }

    @Test fun `safety block is reported`() = runTest {
        val events = orchestrator(ScriptedAi(listOf(listOf(AiStreamEvent.Finished("SAFETY"))))).run(emptyList(), "x", "s", "m").toList()
        assertEquals(AiError.Blocked("SAFETY"), (events.last() as AgentEvent.Failed).error)
    }

    @Test fun `history trimmer never splits tool exchange`() {
        val contents = mutableListOf<JsonObject>()
        repeat(30) { i ->
            contents += AgentOrchestrator.userContent("q$i")
            contents += jsonOf("role" to "model", "parts" to listOf(mapOf("functionCall" to mapOf("name" to "get_time"))))
            contents += jsonOf("role" to "user", "parts" to listOf(mapOf("functionResponse" to mapOf("name" to "get_time"))))
            contents += jsonOf("role" to "model", "parts" to listOf(mapOf("text" to "a$i")))
        }
        val trimmed = HistoryTrimmer.trim(contents)
        assertTrue(trimmed.size <= HistoryTrimmer.MAX_CONTENTS)
        val first = trimmed.first()
        assertEquals("user", (first["role"] as JsonPrimitive).content)
        assertTrue(((first["parts"] as JsonArray)[0] as JsonObject).containsKey("text"))
    }
}
