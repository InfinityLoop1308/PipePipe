package project.pipepipe.app.ui.component

import android.text.Spanned
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import android.text.style.*
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion
import project.pipepipe.app.helper.getHtmlHelper

/**
 * A Composable Text component that supports HTML formatting
 * @param html The HTML string to display
 * @param modifier Modifier to be applied to the Text
 * @param style Text style to apply
 * @param color Text color
 * @param onUrlClick Callback when a URL is clicked
 * @param onHashtagClick Callback when a hashtag is clicked
 * @param onTimestampClick Callback when a timestamp is clicked
 */
@Composable
fun HtmlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    onHashtagClick: ((String) -> Unit)? = null,
    onTimestampClick: ((Int) -> Unit)? = null
) {
    val htmlHelper = getHtmlHelper()
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(
        text,
        style,
        fontSize,
        linkColor,
        onHashtagClick != null,
        onTimestampClick != null
    ) {
        val spanned = htmlHelper.parseHtml(text) as Spanned
        spannedToAnnotatedString(
            spanned,
            style,
            linkColor,
            onHashtagClick != null,
            onTimestampClick != null
        )
    }
    val uriHandler = LocalUriHandler.current

    // 使用 ClickableText 替代 Text
    androidx.compose.foundation.text.ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, fontSize = fontSize)),
        onClick = { offset ->
            // 检查点击位置的所有注解
            annotatedString.getStringAnnotations(start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    when (annotation.tag) {
                        "URL" -> uriHandler.openUri(annotation.item)
                        "HASHTAG" -> onHashtagClick?.invoke(annotation.item)
                        "TIMESTAMP" -> onTimestampClick?.invoke(annotation.item.toInt())
                    }
                }
        }
    )
}


/**
 * Converts Android Spanned text to Compose AnnotatedString
 */
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

        // First, apply HTML spans
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)

            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        android.graphics.Typeface.BOLD -> {
                            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        }
                        android.graphics.Typeface.ITALIC -> {
                            addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        }
                        android.graphics.Typeface.BOLD_ITALIC -> {
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
                    addStyle(SpanStyle(fontSize = textStyle.fontSize * span.sizeChange), start, end)
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

        // Then, detect and highlight patterns in plain text
        detectAndAnnotatePatterns(text, linkColor, enableHashtag, enableTimestamp)
    }
}

/**
 * Pattern for detecting timestamps in format: [HH:]MM:SS
 */
private val TIMESTAMPS_PATTERN = Regex(
    "(?<=(?:^|(?!:)\\W))" + // Positive lookbehind for the context
            "(?:([0-5]?[0-9])[:：]\\s?)?" + // Optional hours (Group 1)
            "([0-5]?[0-9])[:：]\\s?" +    // Minutes (Group 2)
            "([0-5][0-9])" +             // Seconds (Group 3)
            "(?!(?:[:：]\\s?[0-5]?[0-9]))" // Negative lookahead to prevent further timestamp parts
)

/**
 * Pattern for detecting URLs
 */
private val URL_PATTERN = Regex(
    """
    (?i)\b
    ((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)
    (?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+
    (?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:'".,<>?«»""'']))
    """.trimIndent().replace("\n", "").replace(" ", "")
)


/**
 * Pattern for detecting hashtags (# followed by word characters, must be at start or after whitespace/newline)
 */
private val HASHTAG_PATTERN = Regex("(?<=^|\\s)#(\\w+)")

/**
 * Detects and annotates URLs, hashtags, and timestamps in the text
 */
private fun AnnotatedString.Builder.detectAndAnnotatePatterns(
    text: String,
    linkColor: Color,
    enableHashtag: Boolean,
    enableTimestamp: Boolean
) {
    // Detect URLs
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

    // Detect hashtags (only if enabled)
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
                annotation = match.groupValues[1], // Just the hashtag text without #
                start = start,
                end = end
            )
        }
    }

    // Detect timestamps (only if enabled)
    if (enableTimestamp) {
        TIMESTAMPS_PATTERN.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1

            // Calculate seconds from timestamp
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
