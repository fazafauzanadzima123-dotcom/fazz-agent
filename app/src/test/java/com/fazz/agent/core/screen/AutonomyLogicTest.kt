package com.fazz.agent.core.screen

import com.fazz.agent.core.agent.AgentOrchestrator
import com.fazz.agent.core.agent.HistoryTrimmer
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.proactive.NotificationStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyLogicTest {

    private fun el(id: Int, text: String, role: String = "Button", clickable: Boolean = true, editable: Boolean = false) =
        UiElement(id, role, text, "", "", "", clickable, editable, false, null, false, 100, 200)

    @Test fun `risky labels need approval, safe ones do not`() {
        listOf("Kirim", "Send", "Bayar sekarang", "Checkout", "Hapus chat", "Posting", "Pesan sekarang", "Konfirmasi", "Telepon", "Transfer").forEach {
            assertTrue("$it should be risky", IrreversibleGuard.isRisky(it))
        }
        listOf("Cari", "Kembali", "Buka", "Chat", "Profil", "Beranda", "Kirimi aku kabar").forEach {
            // "Kirimi" must not match the word "kirim"
            assertFalse("$it should be safe", IrreversibleGuard.isRisky(it))
        }
    }

    @Test fun `screen formatter lists numbered elements with flags`() {
        val snap = ScreenSnapshot(
            "com.whatsapp", "WhatsApp", 1080, 2400,
            listOf(el(1, "Budi"), el(2, "", role = "EditText", clickable = true, editable = true).copy(hint = "Ketik pesan"), el(3, "Kirim")),
        )
        val text = ScreenFormatter.format(snap)
        assertTrue(text.contains("App: WhatsApp (com.whatsapp)"))
        assertTrue(text.contains("[1] Button \"Budi\" (click)"))
        assertTrue(text.contains("[2] EditText hint=\"Ketik pesan\" (click,edit)"))
        assertEquals("Kirim", snap.labelOf(3))
        assertEquals("elemen #9", snap.labelOf(9))
    }

    @Test fun `empty screen suggests screenshot`() {
        val text = ScreenFormatter.format(ScreenSnapshot("x", "X", 1, 1, emptyList()))
        assertTrue(text.contains("look_at_screen"))
    }

    @Test fun `otp codes are masked before reaching the AI`() {
        assertEquals("Kode OTP kamu ••••. Jangan bagikan.", NotificationStore.mask("Kode OTP kamu 482913. Jangan bagikan."))
        assertEquals("Your verification code is ••••", NotificationStore.mask("Your verification code is 1234"))
        assertEquals("Rapat jam 1530 di kantor", NotificationStore.mask("Rapat jam 1530 di kantor"))
    }

    @Test fun `only the newest image is kept in model context`() {
        val img = AgentOrchestrator.imagePart(byteArrayOf(1, 2, 3))
        val contents = listOf(
            JsonObject(mapOf("role" to kotlinx.serialization.json.JsonPrimitive("user"), "parts" to JsonArray(listOf(img, jsonOf("text" to "a"))))),
            JsonObject(mapOf("role" to kotlinx.serialization.json.JsonPrimitive("user"), "parts" to JsonArray(listOf(jsonOf("text" to "b"), img)))),
        )
        val kept = HistoryTrimmer.keepLatestImages(contents, 1)
        val first = kept[0]["parts"] as JsonArray
        val second = kept[1]["parts"] as JsonArray
        assertEquals(2, first.size)
        assertTrue((first[0] as JsonObject).containsKey("text"))
        assertTrue((second[1] as JsonObject).containsKey("inlineData"))
        val none = HistoryTrimmer.stripImages(contents)
        assertTrue(none.all { c -> (c["parts"] as JsonArray).none { (it as JsonObject).containsKey("inlineData") } })
    }

    @Test fun `image part is base64 jpeg inline data`() {
        val p = AgentOrchestrator.imagePart(byteArrayOf(0x41, 0x42))
        val inline = p["inlineData"] as JsonObject
        assertEquals("\"image/jpeg\"", inline["mimeType"].toString())
        assertEquals("\"QUI=\"", inline["data"].toString())
        assertNull(p["text"])
    }
}
