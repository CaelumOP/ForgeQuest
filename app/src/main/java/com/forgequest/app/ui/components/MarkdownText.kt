package com.forgequest.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Renders the small Markdown subset the coach uses (headings, bullets, numbered lists, **bold**, *italic*)
 * so no raw symbols ever reach the screen.
 */
@Composable
fun MarkdownText(text: String, color: Color, modifier: Modifier = Modifier) {
    val rtl = isRtlText(text)
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MarkdownBody(text, color, modifier)
    }
}

/** True when the first strong letter is Arabic or Hebrew, so bullets and alignment mirror for that message. */
fun isRtlText(text: String): Boolean {
    val first = text.firstOrNull { it.isLetter() } ?: return false
    return first in '\u0590'..'\u08FF' || first in '\uFB1D'..'\uFDFF' || first in '\uFE70'..'\uFEFF'
}

@Composable
private fun MarkdownBody(text: String, color: Color, modifier: Modifier) {
    val lines = text.replace("\r", "").lines()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((index, raw) in lines.withIndex()) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                // Paragraph break: a little air, but never at the very top.
                if (index > 0) Spacer(Modifier.height(4.dp))
                continue
            }
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> Text(
                    inline(trimmed.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp),
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> Bullet("•", trimmed.drop(2), color, indent = line.length - trimmed.length > 1)
                numbered.containsMatchIn(trimmed) -> {
                    val num = trimmed.substringBefore(' ')
                    Bullet(num, trimmed.substringAfter(' '), color, indent = false)
                }
                trimmed == "---" || trimmed == "***" -> Unit
                else -> Text(inline(trimmed), style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** "1." or "١." or "3)" followed by a space. */
private val numbered = Regex("^\\p{Nd}+[.)] ")

@Composable
private fun Bullet(marker: String, body: String, color: Color, indent: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = if (indent) 16.dp else 0.dp)) {
        Text(marker, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.width(if (marker.length > 1) 22.dp else 14.dp))
        Text(inline(body), style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
    }
}

/** Handles **bold**, __bold__, *italic*, _italic_ and `code` (shown plain). */
internal fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val bold = when {
            text.startsWith("**", i) -> "**"
            text.startsWith("__", i) -> "__"
            else -> null
        }
        if (bold != null) {
            val end = text.indexOf(bold, i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }
        val c = text[i]
        if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] != ' ') {
            val end = text.indexOf(c, i + 1)
            if (end > i + 1 && (c == '*' || end + 1 >= text.length || !text[end + 1].isLetterOrDigit())) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                append(text.substring(i + 1, end))
                i = end + 1
                continue
            }
        }
        append(c)
        i++
    }
}
