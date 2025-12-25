package project.pipepipe.app.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import project.pipepipe.app.helper.HtmlHelper

/**
 * A Composable Text component that supports HTML formatting
 * @param text The HTML string to display
 * @param color Text color
 * @param modifier Modifier to be applied to the Text
 * @param style Text style to apply
 * @param fontSize Font size
 * @param onHashtagClick Callback when a hashtag is clicked
 * @param onTimestampClick Callback when a timestamp is clicked
 */
@Composable
fun HtmlText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    onHashtagClick: ((String) -> Unit)? = null,
    onTimestampClick: ((Long) -> Unit)? = null
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(
        text,
        style,
        fontSize,
        linkColor,
        onHashtagClick != null,
        onTimestampClick != null
    ) {
        HtmlHelper.parseHtml(
            html = text,
            linkColor = linkColor,
            textStyle = style,
            enableHashtag = onHashtagClick != null,
            enableTimestamp = onTimestampClick != null
        )
    }
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotatedString,
        modifier = modifier.pointerInput(annotatedString) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val layout = layoutResult.value ?: return@awaitEachGesture
                val offset = layout.getOffsetForPosition(down.position)
                val annotation = annotatedString
                    .getStringAnnotations(start = offset, end = offset)
                    .firstOrNull()

                if (annotation != null) {
                    down.consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        when (annotation.tag) {
                            "URL" -> uriHandler.openUri(annotation.item)
                            "HASHTAG" -> onHashtagClick?.invoke(annotation.item)
                            "TIMESTAMP" -> onTimestampClick?.invoke(annotation.item.toLong())
                        }
                    }
                }
            }
        },
        style = style.merge(TextStyle(color = color, fontSize = fontSize)),
        onTextLayout = { layoutResult.value = it }
    )
}
