package project.pipepipe.app.ui.component.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

private enum class DragGestureType { Seek, Volume, Brightness, Rotation }

enum class DisplayPortion { Left, Middle, Right }

@Immutable
data class PlayerGestureSettings(
    val swipeSeekEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val fullscreenGestureEnabled: Boolean = true
)

@Immutable
data class SwipeSeekUiState(
    val deltaLabel: String,
    val positionLabel: String
)

suspend fun PointerInputScope.detectPlayerDragGestures(
    sizeProvider: () -> IntSize,
    rotationThresholdPx: Float,
    isFullscreen: () -> Boolean,
    isSwipeSeekEnabled: () -> Boolean,
    isVolumeGestureEnabled: () -> Boolean,
    isBrightnessGestureEnabled: () -> Boolean,
    isFullscreenGestureEnabled: () -> Boolean,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onVolumeStart: () -> Unit,
    onVolume: (Float) -> Unit,
    onVolumeEnd: () -> Unit,
    onBrightnessStart: () -> Unit,
    onBrightness: (Float) -> Unit,
    onBrightnessEnd: () -> Unit,
    onRotation: (Boolean) -> Unit
) {
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var activeGesture: DragGestureType? = null
        var total = Offset.Zero
        var rotationDistance = 0f
        val pointerId = down.id

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
                ?: event.changes.first()

            if (!change.pressed) {
                break
            }

            val delta = change.positionChange()

            if (activeGesture == null) {
                total += delta
                val absX = abs(total.x)
                val absY = abs(total.y)

                if (absX > touchSlop || absY > touchSlop) {

                    if (absX > absY) {
                        if (!isFullscreen() || !isSwipeSeekEnabled()) break
                        activeGesture = DragGestureType.Seek
                        change.consume()
                        onSeekStart()
                        onSeek(delta.x)
                    } else {
                        when (sizeProvider().portionForX(down.position.x)) {
                            DisplayPortion.Left -> {
                                if (!isFullscreen() || !isBrightnessGestureEnabled()) break
                                activeGesture = DragGestureType.Brightness
                                change.consume()
                                onBrightnessStart()
                                onBrightness(delta.y)
                            }
                            DisplayPortion.Right -> {
                                if (!isFullscreen() || !isVolumeGestureEnabled()) break
                                activeGesture = DragGestureType.Volume
                                change.consume()
                                onVolumeStart()
                                onVolume(delta.y)
                            }
                            DisplayPortion.Middle -> {
                                if (!isFullscreenGestureEnabled()) break
                                activeGesture = DragGestureType.Rotation
                                change.consume()
                                rotationDistance += delta.y
                            }
                        }
                    }
                }
            } else {
                change.consume()
                when (activeGesture) {
                    DragGestureType.Seek -> onSeek(delta.x)
                    DragGestureType.Volume -> onVolume(delta.y)
                    DragGestureType.Brightness -> onBrightness(delta.y)
                    DragGestureType.Rotation -> rotationDistance += delta.y
                }
            }
        }

        when (activeGesture) {
            DragGestureType.Seek -> onSeekEnd()
            DragGestureType.Volume -> onVolumeEnd()
            DragGestureType.Brightness -> onBrightnessEnd()
            DragGestureType.Rotation -> {
                if (abs(rotationDistance) > rotationThresholdPx) {
                    onRotation(rotationDistance > 0f)
                }
            }
            null -> Unit
        }
    }
}

fun IntSize.portionForX(x: Float): DisplayPortion {
    if (width == 0) return DisplayPortion.Middle
    val third = width / 3f
    return when {
        x < third -> DisplayPortion.Left
        x > third * 2f -> DisplayPortion.Right
        else -> DisplayPortion.Middle
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun readScreenBrightness(activity: Activity): Float {
    val windowValue = activity.window.attributes.screenBrightness
    if (windowValue >= 0f) {
        return windowValue.coerceIn(0f, 1f)
    }
    val resolver = activity.contentResolver
    val system = runCatching {
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    }.getOrDefault(128)
    return (system / 255f).coerceIn(0f, 1f)
}

/**
 * Save screen brightness to preferences with timestamp.
 * The saved brightness will expire after 4 hours.
 */
fun saveScreenBrightness(brightness: Float) {
    project.pipepipe.app.SharedContext.settingsManager.putFloat("screen_brightness", brightness)
    project.pipepipe.app.SharedContext.settingsManager.putLong("screen_brightness_timestamp", System.currentTimeMillis())
}

/**
 * Get saved screen brightness from preferences.
 * Returns -1 if no saved brightness or if it has expired (after 4 hours).
 */
fun getSavedScreenBrightness(): Float {
    val timestamp = project.pipepipe.app.SharedContext.settingsManager.getLong("screen_brightness_timestamp", 0)

    // Check if saved brightness has expired (4 hours)
    val fourHoursInMillis = 4 * 60 * 60 * 1000L
    if (System.currentTimeMillis() - timestamp > fourHoursInMillis) {
        return -1f
    }

    return project.pipepipe.app.SharedContext.settingsManager.getFloat("screen_brightness", -1f)
}