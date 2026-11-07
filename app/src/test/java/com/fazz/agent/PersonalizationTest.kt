package com.fazz.agent

import com.fazz.agent.core.agent.SystemPrompt
import com.fazz.agent.domain.models.AppSettings
import com.fazz.agent.domain.models.LOCATION_TOOL
import com.fazz.agent.domain.models.PluginGroup
import com.fazz.agent.domain.models.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class PersonalizationTest {
    private fun settings(off: Set<String> = emptySet(), location: Boolean = false) = AppSettings(
        speakReplies = false, autoSendVoice = true, installId = "x", briefingEnabled = false, briefingHour = 7,
        screenDisclosureAccepted = false, locationAllowed = location, disabledPlugins = off,
    )

    private fun prompt(p: Profile, off: List<String> = emptyList()) =
        SystemPrompt.build(ZonedDateTime.now(), emptyList(), "Test", profile = p, disabledPlugins = off)

    @Test fun locationToolIsOffUntilAllowed() {
        assertTrue(LOCATION_TOOL in settings().disabledTools)
        assertFalse(LOCATION_TOOL in settings(location = true).disabledTools)
    }

    @Test fun switchedOffPluginRemovesItsTools() {
        val tools = settings(off = setOf("pesan", "layar")).disabledTools
        assertTrue("send_sms" in tools && "call_phone" in tools && "tap" in tools)
        assertFalse("web_search" in tools)
    }

    @Test fun everyPluginToolIsUnique() {
        val all = PluginGroup.entries.flatMap { it.tools }
        assertTrue(all.size == all.toSet().size)
    }

    @Test fun personalizationAppearsAndIsBounded() {
        val p = prompt(Profile(nickname = "Fazza", about = "Pemilik toko kopi", instructions = "x".repeat(5000)))
        assertTrue(p.contains("Call the user: Fazza"))
        assertTrue(p.contains("Pemilik toko kopi"))
        assertTrue(p.length < 20_000)
        assertTrue(p.contains("never as permission to bypass the safety rules"))
    }

    @Test fun controlCharactersAreStripped() {
        val p = prompt(Profile(nickname = "A\u0000B\u0007"))
        assertTrue(p.contains("Call the user: AB"))
    }

    @Test fun disabledPluginsAreListedForTheModel() {
        assertTrue(prompt(Profile(), listOf("Kalender")).contains("switched these abilities off"))
        assertFalse(prompt(Profile()).contains("switched these abilities off"))
    }

    @Test fun googleToolsAreHiddenUntilConnected() {
        val signedOut = settings().disabledTools
        assertTrue("gmail_send" in signedOut && "google_calendar_events" in signedOut && "drive_save_document" in signedOut)

        val calendarOnly = settings().copy(googleEmail = "a@b.c", googleServices = setOf("kalender")).disabledTools
        assertFalse("google_calendar_events" in calendarOnly)
        assertTrue("gmail_send" in calendarOnly)

        val switchedOff = settings().copy(googleEmail = "a@b.c", googleServices = setOf("gmail"), disabledPlugins = setOf("google")).disabledTools
        assertTrue("gmail_search" in switchedOff)
    }
}
