package project.pipepipe.app.global

import android.content.Context
import androidx.media3.session.MediaController
import project.pipepipe.app.MainActivity
import project.pipepipe.app.service.playFromStreamInfo
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.shared.PlaybackMode
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.uistate.VideoDetailPageState

object PipHelper {
    fun enterPipMode(mediaController: MediaController, streamInfo: StreamInfo, context: Context) {
        SharedContext.enterPipmode()
        mediaController.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
        mediaController.playFromStreamInfo(streamInfo)
        SharedContext.sharedVideoDetailViewModel.setPageState(VideoDetailPageState.FULLSCREEN_PLAYER)
        (context as MainActivity).enterPipMode(streamInfo.isPortrait)
    }
}