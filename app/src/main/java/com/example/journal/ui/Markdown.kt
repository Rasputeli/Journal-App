package com.example.journal.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * A deliberately small Markdown subset, styled but not coloured so it can be
 * used from anywhere. Search matches are highlighted while rendering, which
 * is why the highlight style is a parameter rather than a constant.
 *
 *   **bold**   *italic*   `code`   ~~strike~~
 *   # / ## / ### headings      - or * bullets      > quotes
 *
 * Underscores are intentionally NOT italic markers, so snake_case stays legible.
 */
object MarkdownLite {

    private val inlineRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`([^`]+)`|~~(.+?)~~""")

    private class Highlighter(val needle: String, val style: SpanStyle)

    fun render(
        source: String,
        highlight: String? = null,
        highlightStyle: SpanStyle? = null,
    ): AnnotatedString = buildAnnotatedString {
        val highlighter = if (highlight.isNullOrBlank() || highlightStyle == null) null
        else Highlighter(highlight, highlightStyle)

        source.split("\n").forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) {
                        appendInline(trimmed.removePrefix("### "), highlighter)
                    }

                trimmed.startsWith("## ") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) {
                        appendInline(trimmed.removePrefix("## "), highlighter)
                    }

                trimmed.startsWith("# ") ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                        appendInline(trimmed.removePrefix("# "), highlighter)
                    }

                trimmed.startsWith("> ") ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append("| ")
                        appendInline(trimmed.removePrefix("> "), highlighter)
                    }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("• ")
                    appendInline(trimmed.drop(2), highlighter)
                }

                else -> appendInline(line, highlighter)
            }
        }
    }

    private fun AnnotatedString.Builder.appendInline(line: String, highlighter: Highlighter?) {
        var cursor = 0
        for (match in inlineRegex.findAll(line)) {
            if (match.range.first > cursor) {
                appendRaw(line.substring(cursor, match.range.first), highlighter)
            }
            val groups = match.groupValues
            when {
                groups[1].isNotEmpty() ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendRaw(groups[1], highlighter)
                    }

                groups[2].isNotEmpty() ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendRaw(groups[2], highlighter)
                    }

                groups[3].isNotEmpty() ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        appendRaw(groups[3], highlighter)
                    }

                groups[4].isNotEmpty() ->
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendRaw(groups[4], highlighter)
                    }
            }
            cursor = match.range.last + 1
        }
        if (cursor < line.length) appendRaw(line.substring(cursor), highlighter)
    }

    private fun AnnotatedString.Builder.appendRaw(text: String, highlighter: Highlighter?) {
        if (highlighter == null || text.isEmpty()) {
            append(text)
            return
        }
        var from = 0
        while (from < text.length) {
            val at = text.indexOf(highlighter.needle, from, ignoreCase = true)
            if (at < 0) {
                append(text.substring(from))
                return
            }
            if (at > from) append(text.substring(from, at))
            val end = (at + highlighter.needle.length).coerceAtMost(text.length)
            withStyle(highlighter.style) { append(text.substring(at, end)) }
            from = end
        }
    }
}
