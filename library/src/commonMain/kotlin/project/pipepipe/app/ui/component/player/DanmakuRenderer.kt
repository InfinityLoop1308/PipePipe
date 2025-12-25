package project.pipepipe.app.ui.component.player

import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 文本测量结果
 */
data class DanmakuTextMetrics(
    val width: Float,
    val ascent: Float,
    val descent: Float
) {
    val height: Float get() = ascent + descent
}

/**
 * 弹幕文本样式配置
 */
data class DanmakuTextStyle(
    val fontSize: Float,
    val color: Int,
    val shadowRadius: Float,
    val shadowColor: Int
)

/**
 * 平台弹幕渲染器
 *
 * Android: 使用 Paint + nativeCanvas.drawText
 * iOS: 使用 Core Text
 * Desktop: 使用 Skia
 */
expect class DanmakuRenderer(platformTypeface: Any?) {
    /**
     * 测量文本尺寸
     */
    fun measure(text: String, style: DanmakuTextStyle): DanmakuTextMetrics

    /**
     * 绘制弹幕文本
     */
    fun draw(drawScope: DrawScope, text: String, x: Float, y: Float, style: DanmakuTextStyle)
}
