package com.fazz.agent.presentation.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** Minimal markdown: headings, **bold**, `code`, bullets and clickable links. Enough for chat. */
fun renderMarkdown(text: String, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.split('\n')
    lines.forEachIndexed { idx, raw ->
        var line = raw
        var heading = false
        val h = Regex("^#{1,6}\\s+").find(line)
        if (h != null) { line = line.substring(h.range.last + 1); heading = true }
        val bullet = Regex("^\\s*[-*]\\s+").find(line)
        if (bullet != null) { append("  •  "); line = line.substring(bullet.range.last + 1) }
        if (heading) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(line, linkColor, codeBg) }
        else inline(line, linkColor, codeBg)
        if (idx < lines.lastIndex) append('\n')
    }
}

private val token = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)|(https?://[^\\s)]+)")

private fun AnnotatedString.Builder.inline(s: String, linkColor: Color, codeBg: Color) {
    var i = 0
    for (m in token.findAll(s)) {
        append(s.substring(i, m.range.first))
        val (bold, code, linkText, linkUrl, bareUrl) = m.destructured
        val linkStyle = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(" $code ") }
            linkUrl.isNotEmpty() -> withLink(LinkAnnotation.Url(linkUrl, linkStyle)) { append(linkText) }
            bareUrl.isNotEmpty() -> withLink(LinkAnnotation.Url(bareUrl, linkStyle)) { append(bareUrl) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}
