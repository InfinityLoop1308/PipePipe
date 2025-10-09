@file:Suppress("UnusedPrivateMember", "MemberVisibilityCanBePrivate")

package project.pipepipe.app.service

import android.R
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import project.pipepipe.app.mediasource.CustomMediaSourceFactory
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.shared.PlaybackMode
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.SharedContext.playbackMode
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.helper.ToastManager
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.uistate.VideoDetailPageState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null

    private lateinit var sessionCallbackExecutor: ExecutorService
    private var repeatMode: Int = Player.REPEAT_MODE_OFF

    object CustomCommands {
        const val ACTION_SET_PLAYBACK_MODE = "project.pipepipe.app.action.SET_PLAYBACK_MODE"
        const val ARG_MODE = "mode"

        const val ACTION_STOP_SERVICE = "project.pipepipe.app.action.STOP_SERVICE"
        val STOP_SERVICE_COMMAND = SessionCommand(ACTION_STOP_SERVICE, Bundle.EMPTY)
        const val ACTION_CHANGE_REPEAT_MODE = "project.pipepipe.app.action.CHANGE_REPEAT_MODE"
        val CHANGE_REPEAT_MODE_COMMAND = SessionCommand(ACTION_CHANGE_REPEAT_MODE, Bundle.EMPTY)

        fun buildSetPlaybackModeCommand(mode: PlaybackMode): SessionCommand {
            val args = Bundle().apply { putString(ARG_MODE, mode.name) }
            return SessionCommand(ACTION_SET_PLAYBACK_MODE, args)
        }
    }

    override fun onCreate() {
        super.onCreate()

        sessionCallbackExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "PlaybackService-SessionCallback").apply { isDaemon = true }
        }

        val mediaSourceFactory = CustomMediaSourceFactory()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                repeatMode = Player.REPEAT_MODE_OFF
                shuffleModeEnabled = false
                addListener(createPlayerListener())
            }

        val sessionActivity = buildSessionActivity()

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CustomCommands.ACTION_SET_PLAYBACK_MODE, Bundle.EMPTY))
                    .add(CustomCommands.STOP_SERVICE_COMMAND)
                    .add(CustomCommands.CHANGE_REPEAT_MODE_COMMAND)
                    .build()
                val availablePlayerCommands = Player.Commands.Builder()
                    .addAll(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()

                return MediaSession.ConnectionResult.accept(
                    availableSessionCommands,
                    availablePlayerCommands
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return Futures.immediateFuture(
                    handleCustomCommand(customCommand, args)
                )
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                return Futures.immediateFuture(mediaItems.toList())
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val result = MediaSession.MediaItemsWithStartPosition(
                    mediaItems.toList(),
                    startIndex,
                    startPositionMs
                )
                return Futures.immediateFuture(result)
            }
        }
        val mediaButtonPreferences = listOf(
            CommandButton.Builder()
                .setDisplayName(getRepeatModeDisplayName())
                .setSessionCommand(CustomCommands.CHANGE_REPEAT_MODE_COMMAND)
                .setIconResId(R.drawable.ic_menu_rotate)
                .build(),

            CommandButton.Builder()
                .setDisplayName("Stop")
                .setSessionCommand(CustomCommands.STOP_SERVICE_COMMAND)
                .setIconResId(R.drawable.ic_menu_close_clear_cancel)
                .build()
        )

        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity)
            .setId("playback_session")
            .setMediaButtonPreferences(mediaButtonPreferences)
            .build()

        applyPlaybackMode(playbackMode.value)
    }

    private fun getRepeatModeDisplayName(): String {
        return when (repeatMode) {
            Player.REPEAT_MODE_OFF -> "Repeat None"
            Player.REPEAT_MODE_ONE -> "Repeat One"
            Player.REPEAT_MODE_ALL -> "Repeat All"
            else -> "Repeat"
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrentProgress()
        super.onDestroy()
        session?.release()
        player.release()
        sessionCallbackExecutor.shutdownNow()
    }

    private fun handleCustomCommand(
        command: SessionCommand,
        args: Bundle
    ): SessionResult {
        return when (command.customAction) {
            CustomCommands.ACTION_SET_PLAYBACK_MODE -> {
                val modeName = command.customExtras.getString(CustomCommands.ARG_MODE)
                val mode = runCatching { PlaybackMode.valueOf(modeName ?: "") }.getOrNull()
                if (mode != null) {
                    SharedContext.updatePlaybackMode(mode)
                    applyPlaybackMode(mode)
                    SessionResult(SessionResult.RESULT_SUCCESS)
                } else {
                    SessionResult(SessionError.ERROR_BAD_VALUE)
                }
            }
            CustomCommands.ACTION_STOP_SERVICE -> {
                saveCurrentProgress()
                player.stop()
                player.clearMediaItems()
                stopSelf()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            CustomCommands.ACTION_CHANGE_REPEAT_MODE -> {
                changeRepeatMode()
                updateMediaButtonPreferences()
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
            else -> SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        }
    }

    private fun changeRepeatMode() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = repeatMode
    }

    private fun updateMediaButtonPreferences() {
        val updatedMediaButtonPreferences = listOf(
            CommandButton.Builder()
                .setDisplayName(getRepeatModeDisplayName())
                .setSessionCommand(CustomCommands.CHANGE_REPEAT_MODE_COMMAND)
                .setIconResId(R.drawable.ic_menu_rotate)
                .build(),

            CommandButton.Builder()
                .setDisplayName("Stop")
                .setSessionCommand(CustomCommands.STOP_SERVICE_COMMAND)
                .setIconResId(R.drawable.ic_menu_close_clear_cancel)
                .build()
        )
        session?.setMediaButtonPreferences(updatedMediaButtonPreferences)
    }

    private fun applyPlaybackMode(mode: PlaybackMode) {
        val disableVideo = (mode == PlaybackMode.AUDIO_ONLY)
        val params = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableVideo)
            .build()
        player.trackSelectionParameters = params
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, Class.forName("project.pipepipe.app.MainActivity"))
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent.putExtra("open_play_queue", true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    saveCurrentProgress()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        saveCurrentProgress()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                MainScope().launch{
                    DatabaseOperations.insertErrorLog(
                        stacktrace = error.stackTraceToString(),
                        request = player.currentMediaItem?.mediaId,
                        task = "PLAY_STREAM",
                        errorCode = "PLAY_000"
                    )
                }
                ToastManager.show("Playback error")
            }
        }
    }
    private fun saveCurrentProgress() {
        val currentMediaItem = player.currentMediaItem
        val currentPosition = player.currentPosition

        if (currentMediaItem != null && currentPosition > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                DatabaseOperations.updateStreamProgress(currentMediaItem.mediaId, currentPosition)
            }
        }
    }
}
@OptIn(UnstableApi::class)
fun MediaController.setPlaybackMode(mode: PlaybackMode) {
    sendCustomCommand(PlaybackService.CustomCommands.buildSetPlaybackModeCommand(mode), android.os.Bundle.EMPTY)
}

fun MediaController.playFromStreamInfo(streamInfo: StreamInfo) {
    MainScope().launch {
        if (currentMediaItem?.mediaId != streamInfo.url) {
            val mediaItem = streamInfo.toMediaItem()
            val progress = DatabaseOperations.getStreamProgress(streamInfo.url)
            if (progress != null && streamInfo.duration != null &&
                streamInfo.duration!! * 1000 - progress > 5000) {
                setMediaItem(mediaItem, progress)
            } else {
                setMediaItem(mediaItem)
            }

        }
        prepare()
        play()
        if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
            SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
        }
    }
}

@OptIn(UnstableApi::class)
fun MediaController.stopService() {
    sendCustomCommand(
        PlaybackService.CustomCommands.STOP_SERVICE_COMMAND,
        Bundle.EMPTY
    )
}
