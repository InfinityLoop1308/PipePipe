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
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.russhwolf.settings.SettingsListener
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.SharedContext.playbackMode
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.mediasource.CustomMediaSourceFactory
import project.pipepipe.app.helper.SponsorBlockHelper
import project.pipepipe.app.platform.RepeatMode
import project.pipepipe.app.platform.toMedia3MediaItem
import project.pipepipe.app.platform.toPlatformMediaItem
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.shared.infoitem.RelatedItemInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory
import project.pipepipe.shared.job.SupportedJobType
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.media3.ui.R as Media3UiR
import com.google.android.material.R as MaterialR
import project.pipepipe.app.R as AppR

@UnstableApi
class PlaybackService : MediaLibraryService() {

    private lateinit var player: Player
    private var session: MediaLibrarySession? = null

    private lateinit var sessionCallbackExecutor: ExecutorService
    private var playbackButtonState = PlaybackButtonState.ALL_OFF

    // SponsorBlock related fields
    private val sponsorBlockCache = mutableMapOf<String, List<SponsorBlockSegmentInfo>>()
    private val skippedSegments = mutableMapOf<String, MutableSet<String>>()
    private var sponsorBlockCheckJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Search result cache for Android Auto
    private var lastSearchQuery: String? = null
    private var lastSearchResults: List<MediaItem> = emptyList()

    // Autoplay next deduplication
    private var autoplayNextJob: Job? = null
    private var autoplayNextMediaId: String? = null


    // Skip silence setting listener
    private var skipSilenceListener: SettingsListener? = null

    // Retry mechanism for 403 errors
    private data class RetryState(
        var retryCount: Int = 0,
        var hasRefreshedStream: Boolean = false
    )
    private val retryStates = mutableMapOf<String, RetryState>()

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
        private const val MAX_RETRIES_BEFORE_REFRESH = 1
        private const val MAX_RETRIES_AFTER_REFRESH = 1
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

        sessionCallbackExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "PlaybackService-SessionCallback").apply { isDaemon = true }
        }

        val mediaSourceFactory = CustomMediaSourceFactory()

        // Retrieve saved playback parameters
        val savedSpeed = SharedContext.settingsManager.getFloat("playback_speed_key", 1.0f)
        val savedPitch = SharedContext.settingsManager.getFloat("playback_pitch_key", 1.0f)

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
                // Restore saved playback speed and pitch
                playbackParameters = PlaybackParameters(savedSpeed, savedPitch)
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

        playbackButtonState = PlaybackButtonState.fromPlayer(player.repeatMode, SharedContext.platformMediaController?.shuffleModeEnabled?.value?:false)

        val sessionActivity = buildSessionActivity()

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                // Use DEFAULT_SESSION_AND_LIBRARY_COMMANDS to allow library browsing (Android Auto)
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
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

            // Android Auto / Media Browser callbacks
            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaBrowserHelper.createRootMediaItem()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    val children = MediaBrowserHelper.getChildren(parentId, this@PlaybackService)
                    if (children != null) {
                        LibraryResult.ofItemList(children, params)
                    } else {
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    }
                }
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future {
                    val item = MediaBrowserHelper.getItem(mediaId)
                    if (item != null) {
                        LibraryResult.ofItem(item, null)
                    } else {
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    }
                }
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                // Load history for playback resumption
                return serviceScope.future {
                    val historyItems = MediaBrowserHelper.loadHistoryItems()
                    if (historyItems.isNotEmpty()) {
                        MediaSession.MediaItemsWithStartPosition(
                            historyItems,
                            0,
                            C.TIME_UNSET
                        )
                    } else {
                        MediaSession.MediaItemsWithStartPosition(
                            emptyList(),
                            0,
                            C.TIME_UNSET
                        )
                    }
                }
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                return serviceScope.future {
                    val results = MediaBrowserHelper.onSearch(query)
                    // Cache the results
                    lastSearchQuery = query
                    lastSearchResults = results
                    // Notify client that search results are ready
                    session.notifySearchResultChanged(browser, query, results.size, params)
                    LibraryResult.ofVoid(params)
                }
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    // Use cached results if query matches, otherwise re-fetch
                    val results = if (query == lastSearchQuery) {
                        lastSearchResults
                    } else {
                        MediaBrowserHelper.onSearch(query).also {
                            lastSearchQuery = query
                            lastSearchResults = it
                        }
                    }
                    LibraryResult.ofItemList(results, params)
                }
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

        // Monitor stream info loaded events for SponsorBlock and Autoplay
        serviceScope.launch {
            SharedContext.streamInfoLoaded.collect { event ->
                // Only process if this is the current media item
                if (player.currentMediaItem?.mediaId == event.mediaId) {
                    loadSponsorBlock(event.mediaId, event.sponsorblockUrl)
                    loadAutoplayNext(event.mediaId, event.relatedItemUrl,
                        player.currentMediaItem?.mediaMetadata?.extras?.getInt("KEY_SERVICE_ID"))
                }
            }
        }

        // Monitor history changes for Android Auto
        serviceScope.launch {
            SharedContext.historyChanged.collect {
                session?.connectedControllers?.forEach { controller ->
                    session?.notifyChildrenChanged(
                        controller,
                        MediaBrowserHelper.MEDIA_HISTORY_ID,
                        Int.MAX_VALUE,
                        null
                    )
                }
            }
        }

        // Monitor shuffleMode changes from platformMediaController
        serviceScope.launch {
            delay(1000)
            SharedContext.platformMediaController?.shuffleModeEnabled?.collect { shuffleEnabled ->
                syncPlaybackButtonStateWithPlayer()
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
        SharedContext.platformMediaController?.setShuffleModeEnabled(state.shuffleEnabled)
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
        val newState = PlaybackButtonState.fromPlayer(player.repeatMode, SharedContext.platformMediaController?.shuffleModeEnabled?.value?:false)
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
        if (!disableVideo && player.currentMediaItem != null && player.isPlaying) {
            player.setMediaItem(player.currentMediaItem!!, player.currentPosition)
            player.prepare()
        }
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
        loadSponsorBlock(mediaId, sponsorBlockUrl)
    }

    private fun loadSponsorBlock(mediaId: String, sponsorBlockUrl: String?) {
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
                        val categoryName = when (segment.category) {
                            SponsorBlockCategory.SPONSOR -> MR.strings.sponsor_block_category_sponsor
                            SponsorBlockCategory.INTRO -> MR.strings.sponsor_block_category_intro
                            SponsorBlockCategory.OUTRO -> MR.strings.sponsor_block_category_outro
                            SponsorBlockCategory.INTERACTION -> MR.strings.sponsor_block_category_interaction
                            SponsorBlockCategory.HIGHLIGHT -> MR.strings.sponsor_block_category_highlight
                            SponsorBlockCategory.SELF_PROMO -> MR.strings.sponsor_block_category_self_promo
                            SponsorBlockCategory.NON_MUSIC -> MR.strings.sponsor_block_category_non_music
                            SponsorBlockCategory.PREVIEW -> MR.strings.sponsor_block_category_preview
                            SponsorBlockCategory.FILLER -> MR.strings.sponsor_block_category_filler
                            SponsorBlockCategory.PENDING -> MR.strings.missions_header_pending
                        }.desc().toString(this@PlaybackService)
                        val message = MR.strings.player_skipped_category.desc()
                            .toString(this@PlaybackService)
                            .format(categoryName)
                        ToastManager.show(message)
                    }
                }
            }
        }
    }

    // Autoplay next related methods
    private fun loadAutoplayNextForMedia(mediaItem: MediaItem) {
        val mediaId = mediaItem.mediaId
        val relatedItemUrl = mediaItem.mediaMetadata.extras?.getString("KEY_RELATED_ITEM_URL")
        val serviceId = mediaItem.mediaMetadata.extras?.getInt("KEY_SERVICE_ID")
        loadAutoplayNext(mediaId, relatedItemUrl, serviceId)
    }

    private fun loadAutoplayNext(mediaId: String, relatedItemUrl: String?, serviceId: Int?) {
        if (!SharedContext.settingsManager.getBoolean("auto_queue_key", false)) return

        val currentIndex = player.currentMediaItemIndex
        val isLastItem = currentIndex == player.mediaItemCount - 1

        // Only load for the last item
        if (!isLastItem) return

        if (relatedItemUrl == null) return

        // Deduplicate: skip if already loading for the same mediaId
        if (autoplayNextMediaId == mediaId && autoplayNextJob?.isActive == true) return

        autoplayNextJob?.cancel()
        autoplayNextMediaId = mediaId

        autoplayNextJob = serviceScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_INFO,
                        relatedItemUrl,
                        serviceId
                    )
                }
                val relatedItemInfo = result.info as? RelatedItemInfo
                val partitions = relatedItemInfo?.partitions

                // Check if we have partitions and can find next item
                if (!partitions.isNullOrEmpty()) {
                    // Find current partition index by mediaId (url)
                    val currentIndex = partitions.indexOfFirst { it.url == mediaId }
                    if (currentIndex != -1 && currentIndex < partitions.size - 1) {
                        // Get next partition item
                        val nextItem = partitions[currentIndex + 1]

                        if (SharedContext.queueManager.isLastIndex) {
                            SharedContext.queueManager.addItem(nextItem.toPlatformMediaItem())
                        }
                        return@launch
                    }
                }

                // Fallback to relatedItems logic if no partition match
                val relatedItems = result.pagedData?.itemList as? List<StreamInfo> ?: return@launch
                if (relatedItems.isEmpty()) return@launch

                // Pick a random related item
                val randomItem = relatedItems.filter {
                    if (SharedContext.settingsManager.getBoolean("dont_auto_queue_long_key", true)) {
                        runCatching { it.duration!! <= 360 }.getOrDefault(true)
                    } else true
                }.random()
                if (SharedContext.queueManager.isLastIndex) {
                    SharedContext.queueManager.addItem(randomItem.toPlatformMediaItem())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun refreshStreamAndRetry(mediaId: String) {
        val currentIndex = player.currentMediaItemIndex
        val savedPosition = player.currentPosition
        serviceScope.launch {
            try {
                // Get service ID from current media item
                val currentItem = player.currentMediaItem ?: return@launch
                val serviceId = currentItem.mediaMetadata.extras?.getInt("KEY_SERVICE_ID")
                val uuid = currentItem.mediaMetadata.extras?.getString("KEY_UUID")

                // Fetch fresh stream info
                val streamInfo = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_INFO,
                        mediaId,
                        serviceId
                    ).info as StreamInfo
                }

                // Create new media item with fresh stream URLs
                val newMediaItem = streamInfo.toMedia3MediaItem(uuid)

                // Replace the media item at the current position
                withContext(Dispatchers.Main) {
                    player.removeMediaItem(currentIndex)
                    player.addMediaItem(currentIndex, newMediaItem)

                    // Seek to the saved position and try to play
                    player.seekTo(currentIndex, savedPosition)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If refresh fails, just skip to next
                withContext(Dispatchers.Main) {
                    player.removeMediaItem(currentIndex)
                    if (player.mediaItemCount > 0) {
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
    }
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason in listOf(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) && mediaItem != null) {
                    SharedContext.platformMediaController?.loadMediaItem(mediaItem.toPlatformMediaItem())
                }
                mediaItem?.let {
                    skippedSegments[it.mediaId] = mutableSetOf()
                    loadSponsorBlockForMedia(it)
                    loadAutoplayNextForMedia(it)
                    // Reset retry state when successfully transitioning to a new item
                    retryStates.remove(it.mediaId)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
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
                // seems not correct and not needed at all
//                if (!isPlaying) {
//                    saveCurrentProgress()
//                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }

                val mediaId = player.currentMediaItem?.mediaId ?: return
                val currentIndex = player.currentMediaItemIndex

                // Log the error
                MainScope().launch {
                    DatabaseOperations.insertErrorLog(
                        stacktrace = error.stackTraceToString(),
                        request = mediaId,
                        task = "PLAY_STREAM",
                        errorCode = "PLAY_000"
                    )
                }


                if (error.cause?.message?.contains("403") == true
                    || error.cause?.message?.contains("maybeThrowPlaylistRefreshError") == true) {
                    val retryState = retryStates.getOrPut(mediaId) { RetryState() }

                    // First phase: retry up to MAX_RETRIES_BEFORE_REFRESH times before refreshing
                    if (!retryState.hasRefreshedStream && retryState.retryCount < MAX_RETRIES_BEFORE_REFRESH) {
                        retryState.retryCount++
                        ToastManager.show("403 error, retrying (${retryState.retryCount}/$MAX_RETRIES_BEFORE_REFRESH)...")

                        // Simple retry - just prepare and play again
                        player.prepare()
                        player.play()
                        return
                    }

                    // Second phase: refresh stream if we haven't done so yet
                    if (!retryState.hasRefreshedStream) {
                        retryState.hasRefreshedStream = true
                        retryState.retryCount = 0
                        ToastManager.show("Refreshing stream after $MAX_RETRIES_BEFORE_REFRESH retries...")

                        refreshStreamAndRetry(mediaId)
                        return
                    }

                    // Third phase: retry up to MAX_RETRIES_AFTER_REFRESH more times after refresh
                    if (retryState.retryCount < MAX_RETRIES_AFTER_REFRESH) {
                        retryState.retryCount++
                        ToastManager.show("403 error after refresh, retrying (${retryState.retryCount}/$MAX_RETRIES_AFTER_REFRESH)...")

                        // Simple retry - just prepare and play again
                        player.prepare()
                        player.play()
                        return
                    }

                    // Final phase: all retries exhausted, give up and skip to next
                    ToastManager.show("Failed after all retries, skipping to next...")
                    retryStates.remove(mediaId)
                    player.removeMediaItem(currentIndex)

                    if (player.mediaItemCount > 0) {
                        player.prepare()
                        player.play()
                    }
                    return
                }

                // Handle network-related errors: just pause, don't evict
                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        player.pause()
                        return
                    }
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                        // Show decoder error dialog and pause
                        serviceScope.launch {
                            SharedContext.notifyDecoderError()
                        }
                        player.pause()
                        return
                    }
                }
//todo: 403 is still wrong

                // For non-403 errors, show error and remove current item
                ToastManager.show(MR.strings.playback_error.desc().toString(this@PlaybackService))
                retryStates.remove(mediaId)
                player.removeMediaItem(currentIndex)

                if (player.mediaItemCount > 0) {
                    player.prepare()
                    player.play()
                }
            }
        }
    }
    private fun saveCurrentProgress() {
        val currentMediaItem = player.currentMediaItem
        val currentPosition = player.currentPosition

        if (currentMediaItem != null && currentPosition > 0) {
            GlobalScope.launch {
                DatabaseOperations.updateStreamProgress(currentMediaItem.mediaId, currentPosition)
            }
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
