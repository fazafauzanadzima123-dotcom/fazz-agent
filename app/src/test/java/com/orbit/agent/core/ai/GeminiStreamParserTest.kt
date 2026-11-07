package com.orbit.agent.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiStreamParserTest {

    private fun feed(vararg lines: String): List<AiStreamEvent> {
        val p = GeminiStreamParser()
        return lines.flatMap { p.acceptLine(it) } + p.finish()
    }

    @Test fun `parses text deltas, usage and finish reason`() {
        val events = feed(
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hel"}]}}]}""", "",
            """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"lo"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":2,"totalTokenCount":12}}""", "",
        )
        val text = events.filterIsInstance<AiStreamEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Hello", text)
        assertEquals(AiStreamEvent.Finished("STOP"), events.last())
        val usage = events.filterIsInstance<AiStreamEvent.Usage>().single()
        assertEquals(12, usage.totalTokens)
    }

    @Test fun `function call keeps full part including thought signature`() {
        val events = feed(
            """data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"open_app","args":{"app_name":"YouTube"}},"thoughtSignature":"abc123"}]}}]}""", "",
        )
        val call = events.filterIsInstance<AiStreamEvent.FunctionCall>().single()
        assertEquals("open_app", call.name)
        assertEquals("\"YouTube\"", call.args["app_name"].toString())
        assertTrue(call.part.containsKey("thoughtSignature"))
    }

    @Test fun `handles CRLF and trailing event without blank line`() {
        val events = feed("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\"}]}}]}\r")
        assertEquals("Hi", (events.single() as AiStreamEvent.TextDelta).text)
    }

    @Test fun `ignores comments and malformed payloads`() {
        val events = feed(": keep-alive", "", "data: not json", "")
        assertTrue(events.isEmpty())
    }

    @Test fun `empty trailing text part is dropped (real Gemini output)`() {
        val events = feed("""data: {"candidates": [{"content": {"parts": [{"text": ""}],"role": "model"},"finishReason": "STOP","index": 0}]}""", "")
        assertEquals(listOf<AiStreamEvent>(AiStreamEvent.Finished("STOP")), events)
    }

    @Test fun `real Gemini function call with id is parsed`() {
        val events = feed("""data: {"candidates": [{"content": {"parts": [{"functionCall": {"name": "get_device_info","args": {},"id": "call_58"},"thoughtSignature": "EmAK"}],"role": "model"},"index": 0}]}""", "")
        val call = events.single() as AiStreamEvent.FunctionCall
        assertEquals("call_58", call.id)
        assertEquals("get_device_info", call.name)
    }

    @Test fun `thought parts are not shown as text`() {
        val events = feed("""data: {"candidates":[{"content":{"parts":[{"text":"secret reasoning","thought":true}]}}]}""", "")
        assertTrue(events.single() is AiStreamEvent.OtherPart)
    }

    @Test fun `error payload throws AiException`() {
        try {
            feed("""data: {"error":{"code":500,"message":"boom"}}""", "")
            fail("expected exception")
        } catch (e: AiException) {
            assertTrue(e.error is AiError.Server)
        }
    }

    @Test fun `blocked prompt throws Blocked`() {
        try {
            feed("""data: {"promptFeedback":{"blockReason":"SAFETY"}}""", "")
            fail("expected exception")
        } catch (e: AiException) {
            assertEquals(AiError.Blocked("SAFETY"), e.error)
        }
    }
}
