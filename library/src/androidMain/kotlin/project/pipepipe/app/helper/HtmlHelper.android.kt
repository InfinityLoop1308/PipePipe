package project.pipepipe.app.helper

import android.text.Spanned
import androidx.core.text.HtmlCompat

class AndroidHtmlHelper : HtmlHelper {
    override fun parseHtml(html: String): Any {
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
}

actual fun getHtmlHelper(): HtmlHelper = AndroidHtmlHelper()
