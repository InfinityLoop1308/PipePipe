@file:Suppress("UnusedPrivateMember", "MemberVisibilityCanBePrivate")

package project.pipepipe.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.russhwolf.settings.SettingsListener
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.*
import project.pipepipe.app.MR
import project.pipepipe.app.mediasource.CustomMediaSourceFactory
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.SharedContext.playbackMode
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.ui.component.player.SponsorBlockHelper
import project.pipepipe.app.uistate.VideoDetailPageState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.media3.ui.R as Media3UiR
import com.google.android.material.R as MaterialR
import project.pipepipe.app.R as AppR

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private lateinit var player: Player
    private var session: MediaLibrarySession? = null

    private var stopPlaybackReceiver: BroadcastReceiver? = null

    private lateinit var sessionCallbackExecutor: ExecutorService
    private var playbackButtonState = PlaybackButtonState.ALL_OFF

    // SponsorBlock related fields
    private val sponsorBlockCache = mutableMapOf<String, List<SponsorBlockSegmentInfo>>()
    private val skippedSegments = mutableMapOf<String, MutableSet<String>>()
    private var sponsorBlockCheckJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Skip silence setting listener
    private var skipSilenceListener: SettingsListener? = null

    private enum class PlaybackButtonState(
        val repeatMode: Int,
        val shuffleEnabled: Boolean,
        val displayName: String,
        val iconResId: Int
    ) {
        ALL_OFF(
            Player.REPEAT_MODE_OFF,
            false,
            "Repeat Off",
            Media3UiR.drawable.exo_icon_repeat_off
        ),
        SHUFFLE_ON(
            Player.REPEAT_MODE_OFF,
            true,
            "Shuffle On",
            Media3UiR.drawable.exo_icon_shuffle_on
        ),
        REPEAT_ONE(
            Player.REPEAT_MODE_ONE,
            false,
            "Repeat One",
            Media3UiR.drawable.exo_icon_repeat_one
        ),
        REPEAT_ALL(
            Player.REPEAT_MODE_ALL,
            false,
            "Repeat All",
            Media3UiR.drawable.exo_icon_repeat_all
        );

        fun next(): PlaybackButtonState = when (this) {
            ALL_OFF -> SHUFFLE_ON
            SHUFFLE_ON -> REPEAT_ONE
            REPEAT_ONE -> REPEAT_ALL
            REPEAT_ALL -> ALL_OFF
        }

        companion object {
            fun fromPlayer(repeatMode: Int, shuffleEnabled: Boolean): PlaybackButtonState {
                return if (shuffleEnabled) {
                    SHUFFLE_ON
                } else {
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> REPEAT_ONE
                        Player.REPEAT_MODE_ALL -> REPEAT_ALL
                        else -> ALL_OFF
                    }
                }
            }
        }
    }

    companion object {
        private const val SPONSOR_BLOCK_CHECK_INTERVAL_MS = 500L
    }

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

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build().apply {
                setSmallIcon(AppR.drawable.ic_pipepipe)
            }

        setMediaNotificationProvider(notificationProvider)

        stopPlaybackReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "project.pipepipe.app.action.STOP_PLAYBACK") {
                    saveCurrentProgress()
                    player.stop()
                    player.clearMediaItems()
                    stopSelf()
                }
            }
        }

        val filter = IntentFilter("project.pipepipe.app.action.STOP_PLAYBACK")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopPlaybackReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopPlaybackReceiver, filter)
        }

        sessionCallbackExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "PlaybackService-SessionCallback").apply { isDaemon = true }
        }

        val mediaSourceFactory = CustomMediaSourceFactory()

        val actualPlayer = ExoPlayer.Builder(this)
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
                skipSilenceEnabled = SharedContext.settingsManager.getBoolean("playback_skip_silence_key", false)
                addListener(createPlayerListener())
            }

        player = object : ForwardingPlayer(actualPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return Player.Commands.Builder()
                    .addAll(super.getAvailableCommands())
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT -> true
                    else -> super.isCommandAvailable(command)
                }
            }
        }

        playbackButtonState = PlaybackButtonState.fromPlayer(player.repeatMode, player.shuffleModeEnabled)

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
                .setIconResId(playbackButtonState.iconResId)
                .build(),
            CommandButton.Builder()
                .setDisplayName("Stop")
                .setSessionCommand(CustomCommands.STOP_SERVICE_COMMAND)
                .setIconResId(MaterialR.drawable.material_ic_clear_black_24dp)
                .build()
        )

        session = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(sessionActivity)
            .setId("playback_session")
            .setMediaButtonPreferences(mediaButtonPreferences)
            .build()

        applyPlaybackMode(playbackMode.value)

        // Monitor playbackMode changes
        serviceScope.launch {
            playbackMode.collect { mode ->
                applyPlaybackMode(mode)
            }
        }

        // Monitor skip silence setting changes
        skipSilenceListener = SharedContext.settingsManager.addBooleanListener(
            "playback_skip_silence_key",
            false
        ) { enabled ->
            (player as? ForwardingPlayer)?.let { forwardingPlayer ->
                (forwardingPlayer.wrappedPlayer as? ExoPlayer)?.skipSilenceEnabled = enabled
            }
        }
    }

    private fun getRepeatModeDisplayName(): String {
        return playbackButtonState.displayName
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
        stopPlaybackReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
            }
            stopPlaybackReceiver = null
        }

        // Clean up SponsorBlock resources
        stopSponsorBlockCheck()
        serviceScope.cancel()
        sponsorBlockCache.clear()
        skippedSegments.clear()

        // Clean up skip silence listener
        skipSilenceListener?.deactivate()
        skipSilenceListener = null

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
                    // applyPlaybackMode will be called automatically by the Flow collector
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
        val nextState = playbackButtonState.next()
        playbackButtonState = nextState
        applyPlaybackButtonState(nextState)
    }

    private fun applyPlaybackButtonState(state: PlaybackButtonState) {
        player.repeatMode = state.repeatMode
        player.shuffleModeEnabled = state.shuffleEnabled
    }

    private fun updateMediaButtonPreferences() {
        val updatedMediaButtonPreferences = listOf(
            CommandButton.Builder()
                .setDisplayName(getRepeatModeDisplayName())
                .setSessionCommand(CustomCommands.CHANGE_REPEAT_MODE_COMMAND)
                .setIconResId(playbackButtonState.iconResId)
                .build(),
            CommandButton.Builder()
                .setDisplayName("Stop")
                .setSessionCommand(CustomCommands.STOP_SERVICE_COMMAND)
                .setIconResId(MaterialR.drawable.material_ic_clear_black_24dp)
                .build()
        )
        session?.setMediaButtonPreferences(updatedMediaButtonPreferences)
    }

    private fun syncPlaybackButtonStateWithPlayer() {
        val newState = PlaybackButtonState.fromPlayer(player.repeatMode, player.shuffleModeEnabled)
        if (newState != playbackButtonState) {
            playbackButtonState = newState
            updateMediaButtonPreferences()
        }
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

    // SponsorBlock related methods
    private fun loadSponsorBlockForMedia(mediaItem: MediaItem) {
        val mediaId = mediaItem.mediaId
        val sponsorBlockUrl = mediaItem.mediaMetadata.extras?.getString("KEY_SPONSORBLOCK_URL")

        if (sponsorBlockUrl == null || sponsorBlockCache.containsKey(mediaId)) {
            return
        }

        serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_SPONSORBLOCK_SEGMENT_LIST,
                        sponsorBlockUrl,
                        null
                    )
                }

                val segments = result.pagedData?.itemList as? List<SponsorBlockSegmentInfo> ?: emptyList()
                sponsorBlockCache[mediaId] = segments
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startSponsorBlockCheck() {
        sponsorBlockCheckJob?.cancel()
        sponsorBlockCheckJob = serviceScope.launch {
            while (isActive) {
                checkAndSkipSponsorBlock()
                delay(SPONSOR_BLOCK_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun stopSponsorBlockCheck() {
        sponsorBlockCheckJob?.cancel()
    }

    private fun checkAndSkipSponsorBlock() {
        if (!SponsorBlockHelper.isEnabled()) return

        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val segments = sponsorBlockCache[mediaId] ?: return
        val position = player.currentPosition
        val alreadySkipped = skippedSegments.getOrPut(mediaId) { mutableSetOf() }

        val currentSegment = segments.firstOrNull { segment ->
            position.toDouble() in segment.startTime..segment.endTime
        }

        currentSegment?.let { segment ->
            if (!alreadySkipped.contains(segment.uuid) &&
                SponsorBlockHelper.shouldSkipSegment(segment)) {

                player.seekTo(segment.endTime.toLong())
                alreadySkipped.add(segment.uuid)

                if (SponsorBlockHelper.isNotificationsEnabled()) {
                    MainScope().launch {
                        val categoryName = project.pipepipe.app.ui.component.player.SponsorBlockUtils.getCategoryName(segment.category, this@PlaybackService)
                        val message = MR.strings.player_skipped_category.desc()
                            .toString(this@PlaybackService)
                            .format(categoryName)
                        ToastManager.show(message)
                    }
                }
            }
        }
    }
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    saveCurrentProgress()
                }
                mediaItem?.let {
                    skippedSegments[it.mediaId] = mutableSetOf()
                    loadSponsorBlockForMedia(it)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                syncPlaybackButtonStateWithPlayer()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncPlaybackButtonStateWithPlayer()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveCurrentProgress()
                }
                when (playbackState) {
                    Player.STATE_READY -> {
                        player.currentMediaItem?.let { loadSponsorBlockForMedia(it) }
                        startSponsorBlockCheck()
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        stopSponsorBlockCheck()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    saveCurrentProgress()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                MainScope().launch {
                    DatabaseOperations.insertErrorLog(
                        stacktrace = error.stackTraceToString(),
                        request = player.currentMediaItem?.mediaId,
                        task = "PLAY_STREAM",
                        errorCode = "PLAY_000"
                    )
                }
                ToastManager.show(MR.strings.playback_error.desc().toString(this@PlaybackService))

                // Remove failed item and try to play next
                val currentIndex = player.currentMediaItemIndex
                val hasNext = player.hasNextMediaItem()

                if (currentIndex >= 0 && currentIndex < player.mediaItemCount) {
                    player.removeMediaItem(currentIndex)

                    if (hasNext && player.mediaItemCount > 0) {
                        // If there was a next item, it's now at the current index
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
    }
    private fun saveCurrentProgress() {
        val currentMediaItem = player.currentMediaItem
        val currentPosition = player.currentPosition

        if (currentMediaItem != null && currentPosition > 0) {
            MainScope().launch {
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
