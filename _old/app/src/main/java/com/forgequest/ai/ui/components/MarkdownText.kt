package com.forgequest.ai.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Removes fenced JSON code blocks (` ```json ... ``` `) from AI Coach responses
 * so that proposed workout plans are rendered exclusively via interactive cards.
 */
fun stripFencedJsonBlocks(raw: String): String {
    val fencedRegex = Regex("```(?:json)?[\\s\\S]*?```", RegexOption.IGNORE_CASE)
    return raw.replace(fencedRegex, "").trim()
}

/**
 * Parses Markdown into a clean [AnnotatedString] with zero raw syntax (`**`, `###`, `` ` ``)
 * exposed to the user.
 */
fun parseMarkdownToAnnotatedString(
    markdown: String,
    primaryColor: Color = Color(0xFFE3E3E3),
    accentColor: Color = Color(0xFF5CD6C0)
): AnnotatedString {
    val cleaned = stripFencedJsonBlocks(markdown)
    val lines = cleaned.lines()

    return buildAnnotatedString {
        lines.forEachIndexed { lineIndex, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (lineIndex > 0 && lineIndex < lines.lastIndex) {
                    append("\n")
                }
                return@forEachIndexed
            }

            when {
                trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                    val headingText = trimmed.replaceFirst(Regex("^#+\\s*"), "")
                        .replace("**", "")
                        .replace("`", "")
                    withStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    ) {
                        append(headingText)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val itemText = trimmed.substring(2).trim()
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                        append("•  ")
                    }
                    appendInlineFormattedLine(itemText, primaryColor, accentColor)
                }
                else -> {
                    appendInlineFormattedLine(trimmed, primaryColor, accentColor)
                }
            }

            if (lineIndex < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormattedLine(
    text: String,
    primaryColor: Color,
    accentColor: Color
) {
    val tokenRegex = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var lastIndex = 0
    for (match in tokenRegex.findAll(text)) {
        if (match.range.first > lastIndex) {
            val plainSegment = text.substring(lastIndex, match.range.first)
                .replace("**", "")
                .replace("`", "")
            withStyle(SpanStyle(color = primaryColor)) {
                append(plainSegment)
            }
        }
        val boldContent = match.groups[1]?.value
        val codeContent = match.groups[2]?.value
        when {
            boldContent != null -> {
                withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)) {
                    append(boldContent)
                }
            }
            codeContent != null -> {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Medium)) {
                    append(codeContent)
                }
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
            .replace("**", "")
            .replace("`", "")
        withStyle(SpanStyle(color = primaryColor)) {
            append(remaining)
        }
    }
}

@Composable
fun FormattedMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE3E3E3),
    accentColor: Color = Color(0xFF5CD6C0)
) {
    val annotated = remember(markdown, color, accentColor) {
        parseMarkdownToAnnotatedString(
            markdown = markdown,
            primaryColor = color,
            accentColor = accentColor
        )
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = color,
            lineHeight = 21.sp
        ),
        modifier = modifier
    )
}
