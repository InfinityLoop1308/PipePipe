package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.lazy.LazyListScope
import androidx.navigation.NavHostController
import project.pipepipe.app.ui.component.ActionButtons
import project.pipepipe.app.ui.component.VideoDetailSection
import project.pipepipe.app.ui.component.VideoTitleSection
import project.pipepipe.shared.infoitem.StreamInfo

/**
 * Common header items for all video detail tabs.
 * Includes: VideoTitle, VideoDetail, ActionButtons (all scrollable).
 */
fun LazyListScope.videoDetailCommonHeader(
    streamInfo: StreamInfo,
    navController: NavHostController,
    onPlayAudioClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit
) {
    // Video title
    item(key = "video_title") {
        VideoTitleSection(name = streamInfo.name)
    }

    // Video details (channel, views, etc.)
    item(key = "video_detail") {
        VideoDetailSection(streamInfo = streamInfo, navController = navController)
    }

    // Action buttons (play audio, add to playlist, etc.)
    item(key = "action_buttons") {
        ActionButtons(
            onPlayAudioClick = onPlayAudioClick,
            onAddToPlaylistClick = onAddToPlaylistClick,
            streamInfo = streamInfo
        )
    }
}
