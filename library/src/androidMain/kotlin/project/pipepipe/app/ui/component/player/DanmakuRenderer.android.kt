package project.pipepipe.app.ui.component.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

actual class DanmakuRenderer actual constructor(platformTypeface: Any?) {

    private val typeface: Typeface = (platformTypeface as? Typeface) ?: Typeface.DEFAULT

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lastStyle: DanmakuTextStyle? = null

    actual fun measure(text: String, style: DanmakuTextStyle): DanmakuTextMetrics {
        updatePaint(style)
        val fm = paint.fontMetrics
        return DanmakuTextMetrics(
            width = paint.measureText(text),
            ascent = -fm.ascent,
            descent = fm.descent
        )
    }

    actual fun draw(
        drawScope: DrawScope,
        text: String,
        x: Float,
        y: Float,
        style: DanmakuTextStyle
    ) {
        updatePaint(style)
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(text, x, y, paint)
        }
    }

    private fun updatePaint(style: DanmakuTextStyle) {
        if (style == lastStyle) return
        lastStyle = style

        paint.textSize = style.fontSize
        paint.color = style.color
        paint.typeface = Typeface.create(typeface, Typeface.BOLD)

        if (style.shadowRadius > 0f) {
            paint.setShadowLayer(style.shadowRadius, 0f, 0f, style.shadowColor)
        } else {
            paint.clearShadowLayer()
        }
    }
}
