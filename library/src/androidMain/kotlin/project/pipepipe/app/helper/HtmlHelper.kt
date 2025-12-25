package project.pipepipe.app.helper

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import androidx.core.text.HtmlCompat

actual object HtmlHelper {
    actual fun parseHtml(
        html: String,
        linkColor: Color,
        textStyle: TextStyle,
        enableHashtag: Boolean,
        enableTimestamp: Boolean
    ): AnnotatedString {
        val spanned = HtmlCompat.fromHtml(
            html.replace("\n", "<br>"),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        return spannedToAnnotatedString(spanned, textStyle, linkColor, enableHashtag, enableTimestamp)
    }

    private fun spannedToAnnotatedString(
        spanned: Spanned,
        textStyle: TextStyle,
        linkColor: Color,
        enableHashtag: Boolean,
        enableTimestamp: Boolean
    ): AnnotatedString {
        return buildAnnotatedString {
            val text = spanned.toString()
            append(text)

            spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)

                when (span) {
                    is StyleSpan -> {
                        when (span.style) {
                            Typeface.BOLD -> {
                                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                            }
                            Typeface.ITALIC -> {
                                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                            }
                            Typeface.BOLD_ITALIC -> {
                                addStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic
                                    ), start, end
                                )
                            }
                        }
                    }
                    is UnderlineSpan -> {
                        addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    }
                    is StrikethroughSpan -> {
                        addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    }
                    is ForegroundColorSpan -> {
                        addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                    }
                    is RelativeSizeSpan -> {
                        if (textStyle.fontSize.isSpecified) {
                            addStyle(SpanStyle(fontSize = textStyle.fontSize * span.sizeChange), start, end)
                        }
                    }
                    is URLSpan -> {
                        addStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            ), start, end
                        )
                        addStringAnnotation(
                            tag = "URL",
                            annotation = span.url,
                            start = start,
                            end = end
                        )
                    }
                }
            }

            detectAndAnnotatePatterns(text, linkColor, enableHashtag, enableTimestamp)
        }
    }

    private fun AnnotatedString.Builder.detectAndAnnotatePatterns(
        text: String,
        linkColor: Color,
        enableHashtag: Boolean,
        enableTimestamp: Boolean
    ) {
        URL_PATTERN.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            addStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ), start, end
            )
            addStringAnnotation(
                tag = "URL",
                annotation = match.value,
                start = start,
                end = end
            )
        }

        if (enableHashtag) {
            HASHTAG_PATTERN.findAll(text).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ), start, end
                )
                addStringAnnotation(
                    tag = "HASHTAG",
                    annotation = match.groupValues[1],
                    start = start,
                    end = end
                )
            }
        }

        if (enableTimestamp) {
            TIMESTAMPS_PATTERN.findAll(text).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1

                val hours = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                val minutes = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                val seconds = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                val totalSeconds = hours * 3600 + minutes * 60 + seconds

                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ), start, end
                )
                addStringAnnotation(
                    tag = "TIMESTAMP",
                    annotation = totalSeconds.toString(),
                    start = start,
                    end = end
                )
            }
        }
    }

    private val TIMESTAMPS_PATTERN = Regex(
        "(?<=(?:^|(?!:)\\W))" +
                "(?:([0-5]?[0-9])[:：]\\s?)?" +
                "([0-5]?[0-9])[:：]\\s?" +
                "([0-5][0-9])" +
                "(?!(?:[:：]\\s?[0-5]?[0-9]))"
    )

    private val URL_PATTERN = Regex(
        """(?i)\b((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:'".,<>?«»""'']))"""
    )

    private val HASHTAG_PATTERN = Regex("(?<=^|\\s)#(\\w+)")
}
