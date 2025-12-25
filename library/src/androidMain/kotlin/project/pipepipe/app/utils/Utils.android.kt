package project.pipepipe.app.utils

import android.graphics.Bitmap
import android.icu.text.CompactDecimalFormat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale

actual fun formatCount(count: Long?): String {
    if (count == null) return "..."

    val locale = Locale.getDefault()
    val formatter = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)

    return formatter.format(count)
}

actual fun Bitmap.toComposeImageBitmap(): ImageBitmap = asImageBitmap()
