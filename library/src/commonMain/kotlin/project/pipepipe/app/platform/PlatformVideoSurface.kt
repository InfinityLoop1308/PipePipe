package project.pipepipe.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific video rendering surface.
 * Android: Uses PlayerView with ExoPlayer
 * iOS: Uses AVPlayerLayer
 */
@Composable
expect fun PlatformVideoSurface(
    controller: PlatformMediaController,
    resizeMode: ResizeMode,
    modifier: Modifier
)

/**
 * Cross-platform resize mode for video surface.
 */
enum class ResizeMode {
    /** Scale to fit within bounds, maintaining aspect ratio */
    FIT,
    /** Scale to fill bounds, maintaining aspect ratio (may crop) */
    FILL,
    /** Scale to fill bounds exactly (may distort) */
    ZOOM
}
