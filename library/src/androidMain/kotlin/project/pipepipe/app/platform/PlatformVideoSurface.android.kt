package project.pipepipe.app.platform

import android.view.View.GONE
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
actual fun PlatformVideoSurface(
    controller: PlatformMediaController,
    resizeMode: ResizeMode,
    modifier: Modifier
) {
    val player = controller.nativePlayer as Player
    val media3ResizeMode = when (resizeMode) {
        ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
                subtitleView?.visibility = GONE
                this.resizeMode = media3ResizeMode
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.resizeMode = media3ResizeMode
        },
        onRelease = { playerView ->
            playerView.player = null
        },
        modifier = modifier
    )
}
