package project.pipepipe.app.utils

import android.icu.text.CompactDecimalFormat
import java.util.Locale

actual fun formatCount(count: Long?): String {
    if (count == null) return "..."

    val locale = Locale.getDefault()
    val formatter = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)

    return formatter.format(count)
}
