package com.github.libretube.ui.views

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.github.libretube.R
import kotlin.math.roundToInt

class SpeedOverlayView(context: Context) : FrameLayout(context) {
    private var speedTextView: TextView? = null
    private val hideRunnable = Runnable { hideOverlay() }
    private val hideDelayMillis = 800L

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun showSpeed(speed: Float) {
        val textView = ensureSpeedTextView()
        textView.text = formatSpeed(speed)
        removeCallbacks(hideRunnable)
        if (textView.alpha < 0.5f) {
            textView.animate().cancel()
            textView.animate().alpha(1f).setDuration(100).start()
        }
        postDelayed(hideRunnable, hideDelayMillis)
    }

    fun updateSpeed(speed: Float) {
        speedTextView?.text = formatSpeed(speed)
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, hideDelayMillis)
    }

    fun hideOverlay() {
        speedTextView?.animate()?.alpha(0f)?.setDuration(150)?.start()
    }

    private fun ensureSpeedTextView(): TextView {
        return speedTextView ?: TextView(context).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setTextAppearance(context, android.R.style.TextAppearance_Large)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundResource(R.drawable.bg_speed_overlay)
            alpha = 0f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(48) }
            addView(this)
            speedTextView = this
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun formatSpeed(speed: Float): String {
        val rounded = (speed * 4).roundToInt() / 4f
        return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
    }
}
