package com.example.journal.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Journal text is plain text. Formatting was removed deliberately, so the only
 * styling applied here is the search highlight, which is what makes matching
 * feel alive while you scroll.
 */
object TextRender {

    fun render(
        source: String,
        highlight: String? = null,
        highlightStyle: SpanStyle? = null,
    ): AnnotatedString = buildAnnotatedString {
        if (highlight.isNullOrBlank() || highlightStyle == null) {
            append(source)
            return@buildAnnotatedString
        }

        var from = 0
        while (from < source.length) {
            val at = source.indexOf(highlight, from, ignoreCase = true)
            if (at < 0) {
                append(source.substring(from))
                return@buildAnnotatedString
            }
            if (at > from) append(source.substring(from, at))
            val end = (at + highlight.length).coerceAtMost(source.length)
            withStyle(highlightStyle) { append(source.substring(at, end)) }
            from = end
        }
    }
}
