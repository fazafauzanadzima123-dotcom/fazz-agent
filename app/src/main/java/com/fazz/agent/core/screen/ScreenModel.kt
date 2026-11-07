package com.fazz.agent.core.screen

/** A simplified, platform-independent view of one on-screen element (built from AccessibilityNodeInfo). */
data class UiElement(
    val id: Int,
    val role: String,          // Button, EditText, Text, Image, Switch, ...
    val text: String,
    val description: String,
    val hint: String,
    val resourceId: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checked: Boolean?,
    val focused: Boolean,
    val centerX: Int,
    val centerY: Int,
)

data class ScreenSnapshot(
    val packageName: String,
    val appLabel: String,
    val width: Int,
    val height: Int,
    val elements: List<UiElement>,
) {
    fun find(id: Int): UiElement? = elements.firstOrNull { it.id == id }

    /** Human-readable label for an element, used in prompts and confirmations. */
    fun labelOf(id: Int): String = find(id)?.label() ?: "elemen #$id"
}

fun UiElement.label(): String = listOf(text, description, hint).firstOrNull { it.isNotBlank() }
    ?: resourceId.substringAfterLast('/').replace('_', ' ').ifBlank { role }

/**
 * Compact text rendering of the screen for the model. One line per element:
 *   [12] Button "Kirim" (click)
 * Limited in size so a whole screen fits in a few hundred tokens.
 */
object ScreenFormatter {
    const val MAX_ELEMENTS = 160
    private const val MAX_TEXT = 120

    fun format(s: ScreenSnapshot): String = buildString {
        appendLine("App: ${s.appLabel} (${s.packageName}), screen ${s.width}x${s.height}")
        if (s.elements.isEmpty()) {
            appendLine("(no readable elements — try look_at_screen for a screenshot)")
            return@buildString
        }
        s.elements.take(MAX_ELEMENTS).forEach { e ->
            append('[').append(e.id).append("] ").append(e.role)
            val label = listOf(e.text, e.description).filter { it.isNotBlank() }.distinct().joinToString(" | ")
            if (label.isNotBlank()) append(" \"").append(label.take(MAX_TEXT).replace('\n', ' ')).append('"')
            if (e.editable && e.hint.isNotBlank() && e.text.isBlank()) append(" hint=\"").append(e.hint.take(60)).append('"')
            val flags = buildList {
                if (e.clickable) add("click")
                if (e.editable) add("edit")
                if (e.scrollable) add("scroll")
                e.checked?.let { add(if (it) "on" else "off") }
                if (e.focused) add("focused")
            }
            if (flags.isNotEmpty()) append(" (").append(flags.joinToString(",")).append(')')
            appendLine()
        }
        if (s.elements.size > MAX_ELEMENTS) appendLine("… ${s.elements.size - MAX_ELEMENTS} more elements not shown; scroll or use look_at_screen.")
    }
}

/**
 * Decides whether an on-screen action is irreversible (sending, paying, posting, deleting, calling…)
 * and therefore needs the user's explicit approval before Fazz performs it.
 */
object IrreversibleGuard {
    private val risky = Regex(
        "\\b(kirim|send|bayar|pay|payment|beli|buy|checkout|check out|pesan sekarang|order|place order|buat pesanan|" +
            "posting|post|publish|terbitkan|unggah|upload|share|bagikan|tweet|hapus|delete|remove|buang|" +
            "telepon|call|panggil|video call|transfer|top ?up|tarik|withdraw|konfirmasi|confirm|submit|setuju|agree|" +
            "langganan|subscribe|berlangganan|daftar|sign up|keluar|log ?out|sign out|uninstall|copot|reset|format|blokir|block|laporkan|report)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Returns the matched risky word, or null when the label looks safe. */
    fun riskyWord(label: String): String? = risky.find(label)?.value

    fun isRisky(label: String): Boolean = riskyWord(label) != null
}
