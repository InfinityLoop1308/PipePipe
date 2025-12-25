package project.pipepipe.app.helper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

expect object HtmlHelper {
    fun parseHtml(
        html: String,
        linkColor: Color,
        textStyle: TextStyle,
        enableHashtag: Boolean,
        enableTimestamp: Boolean
    ): AnnotatedString
}
