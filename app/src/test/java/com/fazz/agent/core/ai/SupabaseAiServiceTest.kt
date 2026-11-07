package com.fazz.agent.core.ai

import com.fazz.agent.core.common.jsonOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SupabaseAiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: SupabaseAiService

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        service = SupabaseAiService(server.url("/").toString(), "anon-key", { "test-install-0001" }, requireHttps = false)
    }

    @After fun tearDown() = server.shutdown()

    private val request = ChatRequest("gemini-flash-latest", "sys", listOf(jsonOf("role" to "user", "parts" to listOf(mapOf("text" to "hi")))), emptyList())

    @Test fun `streams SSE and sends auth headers`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello\"}]},\"finishReason\":\"STOP\"}]}\n\n",
            ),
        )
        val events = service.streamChat(request).toList()
        assertEquals("Hello", (events.first() as AiStreamEvent.TextDelta).text)
        val recorded = server.takeRequest()
        assertEquals("/functions/v1/agent-chat", recorded.path)
        assertEquals("Bearer anon-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"installId\":\"test-install-0001\""))
        assertTrue("Gemini key must never be sent by the app", !body.contains("GEMINI_API_KEY"))
    }

    @Test fun `missing server key maps to MissingServerKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"missing_api_key","message":"x"}}"""))
        assertError(AiError.MissingServerKey)
    }

    @Test fun `invalid key maps to InvalidServerKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_api_key","message":"bad key"}}"""))
        assertError(AiError.InvalidServerKey("bad key"))
    }

    @Test fun `gateway 401 maps to BackendAuth`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"UNAUTHORIZED_NO_AUTH_HEADER","message":"Missing authorization header"}"""))
        assertError(AiError.BackendAuth)
    }

    @Test fun `quota maps to QuotaExceeded`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":"quota_exceeded","message":"x"}}"""))
        assertError(AiError.QuotaExceeded)
    }

    @Test fun `network failure maps to Network`() = runBlocking {
        server.shutdown()
        try {
            service.streamChat(request).toList(); fail("expected failure")
        } catch (e: AiException) {
            assertTrue("was ${e.error}", e.error is AiError.Network || e.error is AiError.Timeout)
        }
    }

    @Test fun `unconfigured app fails fast`() = runBlocking {
        val s = SupabaseAiService("", "", { "x" })
        try { s.streamChat(request).toList(); fail() } catch (e: AiException) { assertEquals(AiError.NotConfigured, e.error) }
    }

    @Test fun `plain http is rejected in production mode`() = runBlocking {
        val s = SupabaseAiService("http://example.com", "k", { "x" })
        try { s.listModels(); fail() } catch (e: AiException) { assertEquals(AiError.NotConfigured, e.error) }
    }

    @Test fun `web search parses sources`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"answer":"It is sunny.","sources":[{"title":"Weather","url":"https://w.example"}],"grounded":true}"""))
        val r = service.webSearch("weather", null)
        assertTrue(r.grounded)
        assertEquals("https://w.example", r.sources.single().url)
    }

    private suspend fun assertError(expected: AiError) {
        try {
            service.streamChat(request).toList(); fail("expected $expected")
        } catch (e: AiException) {
            assertEquals(expected, e.error)
        }
    }
}
