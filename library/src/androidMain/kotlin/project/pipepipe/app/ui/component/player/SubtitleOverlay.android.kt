
package project.pipepipe.app.ui.component.player

import android.content.Context
import android.util.TypedValue
import android.view.accessibility.CaptioningManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import project.pipepipe.app.platform.SubtitleCue
import kotlin.math.min

/**
 * Android-specific subtitle overlay component using native SubtitleView.
 * This provides better subtitle rendering than the Compose-based implementation.
 */
@Composable
@OptIn(UnstableApi::class)
actual fun SubtitleOverlay(
    subtitles: List<SubtitleCue>,
    modifier: Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    textColor: androidx.compose.ui.graphics.Color,
    backgroundColor: androidx.compose.ui.graphics.Color,
) {
    if (subtitles.isEmpty()) return

    AndroidView(
        factory = { context ->
            val metrics = context.getResources().getDisplayMetrics()
            val minimumLength = min(metrics.heightPixels, metrics.widthPixels)
            val captionRatioInverse: Float = 20f + 4f * (1.0f - getCaptionScale(context))
            val style = getCaptionStyle(context)
            SubtitleView(context).apply {
                setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_PX, minimumLength / captionRatioInverse)
                setApplyEmbeddedStyles(style == CaptionStyleCompat.DEFAULT)
                setStyle(style)
            }
        },
        update = { subtitleView ->
            val currentCues = subtitles.map { cue ->
                Cue.Builder()
                    .setText(cue.text)
                    .build()
            }
            subtitleView.setCues(currentCues)
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
fun getCaptionStyle(context: Context): CaptionStyleCompat {
    val captioningManager = ContextCompat.getSystemService<CaptioningManager?>(
        context,
        CaptioningManager::class.java
    )
    if (captioningManager == null || !captioningManager.isEnabled()) {
        return CaptionStyleCompat.DEFAULT
    }

    return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle())
}

/**
 * Get scaling for captions based on system font scaling.
 *
 * Options:
 *
 *  * Very small: 0.25f
 *  * Small: 0.5f
 *  * Normal: 1.0f
 *  * Large: 1.5f
 *  * Very large: 2.0f
 *
 *
 * @param context Android app context
 * @return caption scaling
 */
fun getCaptionScale(context: Context): Float {
    val captioningManager = ContextCompat.getSystemService<CaptioningManager?>(
        context,
        CaptioningManager::class.java
    )
    if (captioningManager == null || !captioningManager.isEnabled()) {
        return 1.0f
    }

    return captioningManager.getFontScale()
}