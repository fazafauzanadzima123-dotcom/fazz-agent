package com.fazz.agent

import com.fazz.agent.core.agent.SystemPrompt
import com.fazz.agent.domain.models.ResponseStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class ResponseStyleTest {
    private fun prompt(style: ResponseStyle) = SystemPrompt.build(ZonedDateTime.now(), emptyList(), "Test", style = style)

    @Test fun unknownIdFallsBackToNormal() {
        assertEquals(ResponseStyle.NORMAL, ResponseStyle.from("???"))
        assertEquals(ResponseStyle.CASUAL, ResponseStyle.from("santai"))
    }

    @Test fun styleHintIsInPromptOnlyWhenChosen() {
        assertTrue(prompt(ResponseStyle.CONCISE).contains("very concise"))
        assertFalse(prompt(ResponseStyle.NORMAL).contains("Style:"))
    }
}
