package com.fazz.agent

import com.fazz.agent.core.tools.impl.CreateArtifactTool
import com.fazz.agent.domain.models.ArtifactType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateArtifactToolTest {
    private val tool = CreateArtifactTool()
    private fun args(vararg p: Pair<String, String>) = JsonObject(p.associate { it.first to JsonPrimitive(it.second) })

    @Test fun createsHtmlArtifactWithoutEchoingContentToModel() = runBlocking {
        val r = tool.execute(args("title" to "Kalkulator", "type" to "html", "content" to "<h1>Hai</h1>"))
        assertTrue(r.success)
        assertEquals(ArtifactType.HTML, r.artifact!!.type)
        assertFalse(r.toModelResponse().toString().contains("<h1>"))
    }

    @Test fun rejectsUnknownTypeAndEmptyContent() = runBlocking {
        assertFalse(tool.execute(args("title" to "x", "type" to "exe", "content" to "y")).success)
        assertNotNull(tool.validate(args("title" to "x", "type" to "html", "content" to " ")))
        assertNull(tool.validate(args("title" to "x", "type" to "html", "content" to "ok")))
    }
}
