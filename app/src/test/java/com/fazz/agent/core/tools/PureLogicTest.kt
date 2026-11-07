package com.fazz.agent.core.tools

import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.security.SensitiveDataDetector
import com.fazz.agent.core.tools.impl.AppMatcher
import com.fazz.agent.core.tools.impl.CreateCalendarEventTool
import com.fazz.agent.core.tools.impl.DateTimeTool
import com.fazz.agent.core.tools.impl.InstalledApp
import com.fazz.agent.core.voice.TextToSpeechController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PureLogicTest {
    private val apps = listOf(
        InstalledApp("YouTube", "com.google.android.youtube"),
        InstalledApp("YouTube Music", "com.google.android.apps.youtube.music"),
        InstalledApp("WhatsApp", "com.whatsapp"),
        InstalledApp("Camera", "com.android.camera"),
        InstalledApp("Google Maps", "com.google.android.apps.maps"),
    )

    @Test fun `app matcher prefers exact label`() {
        assertEquals("YouTube", AppMatcher.bestMatch("youtube", apps)?.label)
        assertEquals("YouTube Music", AppMatcher.bestMatch("YouTube Music", apps)?.label)
        assertEquals("WhatsApp", AppMatcher.bestMatch("Whats App", apps)?.label)
        assertEquals("Google Maps", AppMatcher.bestMatch("maps", apps)?.label)
        assertEquals("Camera", AppMatcher.bestMatch("camera app", apps)?.label)
    }

    @Test fun `app matcher returns null for unknown app`() {
        assertNull(AppMatcher.bestMatch("Spotify", apps))
        assertNull(AppMatcher.bestMatch("", apps))
    }

    @Test fun `sensitive detector blocks secrets but allows preferences`() {
        assertNotNull(SensitiveDataDetector.findReason("my password is hunter2"))
        assertNotNull(SensitiveDataDetector.findReason("My PIN is 1234"))
        assertNotNull(SensitiveDataDetector.findReason("card 4111 1111 1111 1111"))
        assertNotNull(SensitiveDataDetector.findReason("key AIzaSyA1234567890abcdefghij"))
        assertNull(SensitiveDataDetector.findReason("I prefer short answers"))
        assertNull(SensitiveDataDetector.findReason("My phone is 0812 3456 7890")) // not a Luhn card
    }

    @Test fun `schema validator enforces enums and ranges`() {
        val s = schema {
            string("c", "c", required = true, enum = listOf("a", "b"))
            integer("n", "n", min = 1, max = 5)
            boolean("f", "f")
        }
        assertNull(SchemaValidator.validate(s, jsonOf("c" to "a", "n" to 3, "f" to true)))
        assertNotNull(SchemaValidator.validate(s, jsonOf("c" to "z")))
        assertNotNull(SchemaValidator.validate(s, jsonOf("c" to "a", "n" to 9)))
        assertNotNull(SchemaValidator.validate(s, jsonOf("c" to "a", "n" to 2.5)))
        assertNotNull(SchemaValidator.validate(s, jsonOf("c" to "a", "f" to "yes")))
    }

    @Test fun `calendar date parsing accepts local and offset times`() {
        assertNotNull(CreateCalendarEventTool.parse("2026-11-08T15:00"))
        assertNotNull(CreateCalendarEventTool.parse("2026-11-08T15:00:00+07:00"))
        assertNull(CreateCalendarEventTool.parse("tomorrow at 3"))
    }

    @Test fun `datetime tool reports zone and day`() = runTest {
        val fixed = ZonedDateTime.of(2026, 11, 7, 19, 30, 0, 0, ZoneId.of("Asia/Jakarta"))
        val r = DateTimeTool { fixed }.execute(jsonOf())
        assertTrue(r.success)
        assertEquals("\"Saturday\"", r.data["day_of_week"].toString())
        assertEquals("\"Asia/Jakarta\"", r.data["time_zone"].toString())
    }

    @Test fun `tts strips markdown and urls`() {
        assertEquals("Hello world see docs", TextToSpeechController.stripMarkdown("**Hello** _world_ see [docs](https://x.y) https://a.b"))
    }

    @Test fun `registry rejects duplicate names`() {
        val t = DateTimeTool()
        try { ToolRegistry(listOf(t, t)); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) {}
    }
}
