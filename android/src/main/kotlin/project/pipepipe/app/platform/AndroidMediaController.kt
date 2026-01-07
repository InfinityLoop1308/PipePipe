package project.pipepipe.app.platform

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.text.CueGroup
import androidx.media3.common.C
import androidx.media3.common.C.TRACK_TYPE_TEXT
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.global.MediaControllerHolder
import project.pipepipe.app.helper.FormatHelper.isCodecAllowed
import project.pipepipe.app.helper.FormatHelper.isHDRFromCodec
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.stopService
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.shared.infoitem.StreamInfo

/**
 * Android implementation of PlatformMediaController.
 * Wraps Media3 MediaController and exposes state as StateFlows.
 */
@OptIn(UnstableApi::class)
class AndroidMediaController(
    private val mediaController: MediaController
) : PlatformMediaController {

    override val scope = CoroutineScope(Dispatchers.Main + Job())

    // ===== State Flows =====
    private val _isPlaying = MutableStateFlow(mediaController.isPlaying)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(mediaController.currentPosition)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    override fun getCurrentPositionRealtime(): Long = mediaController.currentPosition

    private val _duration = MutableStateFlow(mediaController.duration)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(mapPlaybackState(mediaController.playbackState))
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMediaItem = MutableStateFlow(mediaController.currentMediaItem?.toPlatformMediaItem())
    override val currentMediaItem: StateFlow<PlatformMediaItem?> = _currentMediaItem.asStateFlow()

    private val _repeatMode = MutableStateFlow(mapRepeatMode(mediaController.repeatMode))
    override val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(mediaController.shuffleModeEnabled)
    override val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(mediaController.playbackParameters.speed)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(mediaController.playbackParameters.pitch)
    override val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _currentSubtitles = MutableStateFlow<List<SubtitleCue>>(emptyList())
    override val currentSubtitles: StateFlow<List<SubtitleCue>> = _currentSubtitles.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(mediaController.bufferedPosition)
    override val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _availableResolutions = MutableStateFlow<List<ResolutionInfo>>(emptyList())
    override val availableResolutions: StateFlow<List<ResolutionInfo>> = _availableResolutions.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<SubtitleInfo>>(emptyList())
    override val availableSubtitles: StateFlow<List<SubtitleInfo>> = _availableSubtitles.asStateFlow()

    private val _availableAudioLanguages = MutableStateFlow<List<AudioLanguageInfo>>(emptyList())
    override val availableAudioLanguages: StateFlow<List<AudioLanguageInfo>> = _availableAudioLanguages.asStateFlow()

    private val _currentAudioLanguage = MutableStateFlow("Default")
    override val currentAudioLanguage: StateFlow<String> = _currentAudioLanguage.asStateFlow()

    private val playbackEventCallbacks = mutableListOf<PlaybackEventCallback>()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = mapPlaybackState(state)
            _duration.value = mediaController.duration
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Media3 timeline changes are ignored - QueueManager is single source of truth
            // Media3 only plays the current item, doesn't manage queue
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem?.toPlatformMediaItem()
            // currentIndex comes from QueueManager, not Media3
            playbackEventCallbacks.forEach { it.onMediaItemTransition() }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = mapRepeatMode(repeatMode)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleModeEnabled.value = shuffleModeEnabled
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackSpeed.value = playbackParameters.speed
            _playbackPitch.value = playbackParameters.pitch
        }

        override fun onCues(cueGroup: CueGroup) {
            _currentSubtitles.value = cueGroup.cues.mapNotNull { cue ->
                cue.text?.toString()?.let { text ->
                    SubtitleCue(
                        text = text,
                        line = if (cue.line != androidx.media3.common.text.Cue.DIMEN_UNSET) cue.line else null,
                        position = if (cue.position != androidx.media3.common.text.Cue.DIMEN_UNSET) cue.position else null
                    )
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableTracks(tracks)
            playbackEventCallbacks.forEach { it.onTracksChanged() }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                oldPosition.mediaItem?.let {
                    GlobalScope.launch {
                        DatabaseOperations.updateStreamProgress(it.mediaId, oldPosition.positionMs)
                    }
                }
            }
            when (reason) {
                Player.DISCONTINUITY_REASON_SEEK,
                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> {
                    playbackEventCallbacks.forEach { it.onSeek() }
                }
                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                    playbackEventCallbacks.forEach { it.onAutoTransition() }
                }
            }
        }
    }

    init {
        mediaController.addListener(listener)
        // Initialize tracks
        updateAvailableTracks(mediaController.currentTracks)

        // Don't initialize QueueManager here - it's managed by higher-level code
        // Just update currentMediaItem from Media3 if needed
        val currentItem = mediaController.currentMediaItem?.toPlatformMediaItem()
        _currentMediaItem.value = currentItem

        // Start position update loop
        scope.launch {
            while (isActive) {
                _currentPosition.value = mediaController.currentPosition
                _duration.value = mediaController.duration
                _bufferedPosition.value = mediaController.bufferedPosition
                delay(500) // Update more frequently for smoother UI
            }
        }
    }

    // ===== Playback Controls =====

    override fun play() {
        mediaController.play()
    }

    override fun pause() {
        mediaController.pause()
    }

    override fun stop() {
        mediaController.stopService()
    }

    override fun seekTo(positionMs: Long) {
        mediaController.seekTo(positionMs)
    }

    override fun seekToPrevious() {
        mediaController.seekToPrevious()
    }

    override fun seekToNext() {
        mediaController.seekToNext()
    }

    // ===== Queue Navigation (Platform-specific implementations) =====

    override fun loadCurrentItem(startPositionMs: Long?, shouldKeepPosition: Boolean) {
        val item = SharedContext.queueManager.getCurrentItem()
        if (item != null) {
            loadMediaItem(item, startPositionMs, shouldKeepPosition = shouldKeepPosition, shouldPrepare = false)
        }
    }

    override fun clearPlayer() {
        mediaController.clearMediaItems()
        _currentMediaItem.value = null
    }

    private fun indexOfMediaItem(mediaId: String): Int {
        for (i in 0 until mediaController.mediaItemCount) {
            if (mediaController.getMediaItemAt(i).mediaId == mediaId) {
                return i
            }
        }
        return C.INDEX_UNSET
    }
    override fun loadMediaItem(
        item: PlatformMediaItem,
        startPositionMs: Long?,
        shouldPrepare: Boolean,
        shouldKeepPosition: Boolean
    ) {
        // Find the item's index in the queue
        val currentQueue = SharedContext.queueManager.getCurrentQueue()
        val index = currentQueue.indexOfFirst { it.uuid == item.uuid }
        if (index < 0) return
        SharedContext.queueManager.setIndex(index)
        val itemsToLoad = SharedContext.queueManager.getCurrentThreeElementQueue().map {
            // Find existing MediaItem or create new one from PlatformMediaItem
            val existingIndex = indexOfMediaItem(it.mediaId)
            if (existingIndex != C.INDEX_UNSET) {
                mediaController.getMediaItemAt(existingIndex)
            } else {
                it.toMedia3MediaItem()
            }
        }
        val currentMedia3Index = itemsToLoad.indexOfFirst { it.uuid == item.uuid }

        if (itemsToLoad.isNotEmpty()) {
            val startPositionMs = startPositionMs ?: runBlocking {
                val progress = DatabaseOperations.getStreamProgress(item.mediaId)
                if (progress != null && item.durationMs != null &&
                    item.durationMs!! - progress > 5000
                ) {
                    progress
                } else {
                    0L
                }
            }

            if (shouldKeepPosition && mediaController.currentMediaItem?.mediaId == item.mediaId) {
                repeat(mediaController.currentMediaItemIndex) { mediaController.removeMediaItem(0) }
                while (mediaController.mediaItemCount > 1) { mediaController.removeMediaItem(1) }
                if (currentMedia3Index == 1) {
                    mediaController.addMediaItem(0, itemsToLoad[0])
                }
                if (currentMedia3Index != itemsToLoad.lastIndex) {
                    mediaController.addMediaItem(currentMedia3Index + 1, itemsToLoad.last())
                }
            } else {
                mediaController.setMediaItems(itemsToLoad, currentMedia3Index.coerceAtLeast(0), startPositionMs)
            }
            if (shouldPrepare){ mediaController.prepare() }
        }
        _currentMediaItem.value = item
    }

    // ===== Settings Controls =====

    override fun setRepeatMode(mode: RepeatMode) {
        mediaController.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun setShuffleModeEnabled(enabled: Boolean) {
        if (enabled && !shuffleModeEnabled.value) {
            SharedContext.queueManager.shuffle()
            SharedContext.platformMediaController?.loadCurrentItem(shouldKeepPosition = true)
        } else if (!enabled && shuffleModeEnabled.value){
            SharedContext.queueManager.unshuffle()
            SharedContext.platformMediaController?.loadCurrentItem(shouldKeepPosition = true)
        }
        _shuffleModeEnabled.value = enabled

    }
    // ===== Lifecycle =====

    override val nativePlayer: Any get() = mediaController

    override fun prepare() {
        mediaController.prepare()
    }

    override fun release() {
        mediaController.removeListener(listener)
    }

    // ===== Business Operations =====

    override fun setPlaybackMode(mode: PlaybackMode) {
        mediaController.sendCustomCommand(
            PlaybackService.CustomCommands.buildSetPlaybackModeCommand(mode),
            Bundle.EMPTY
        )
    }

    override fun setPlaybackParameters(speed: Float, pitch: Float) {
        mediaController.playbackParameters = PlaybackParameters(speed, pitch)
    }

    override fun playFromStreamInfo(streamInfo: StreamInfo) {
        MainScope().launch{
            val item = streamInfo.toPlatformMediaItem()
            val currentQueue = SharedContext.queueManager.getCurrentQueue()
            val queueIndex = currentQueue.indexOfFirst { it.mediaId == streamInfo.url }

            if (mediaController.currentMediaItem?.mediaId == streamInfo.url) {
                // already current, pass
            } else if (queueIndex >= 0) {
                // Item is in queue, just navigate to it
                SharedContext.queueManager.setIndex(queueIndex)
                loadMediaItem(item, shouldPrepare = false, shouldKeepPosition = false)
            } else {
                // Item is not in queue, set it as single item
                super.setMediaItem(item, null)
            }
            mediaController.prepare()
            mediaController.play()

            if (SharedContext.settingsManager.getString("watch_history_mode", "on_play") == "on_play") {
                DatabaseOperations.updateOrInsertStreamHistory(streamInfo)
            }
        }

        if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
            SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
        }
    }

    override fun setStreamInfoAsOnlyMediaItem(streamInfo: StreamInfo) {
        val item = streamInfo.toPlatformMediaItem()
        SharedContext.queueManager.setMediaItem(item)
        loadMediaItem(item)
    }

    override fun backgroundPlay(streamInfo: StreamInfo) {
        setPlaybackMode(PlaybackMode.AUDIO_ONLY)
        playFromStreamInfo(streamInfo)
    }

    override fun enqueue(streamInfo: StreamInfo) {
        MainScope().launch {
            val item = streamInfo.toPlatformMediaItem()
            super.addMediaItem(item)
            // Play if queue was empty
            if (SharedContext.queueManager.getCurrentQueue().size == 1) {
                mediaController.play()
            }
        }
    }

    override fun playAll(items: List<StreamInfo>, startIndex: Int, shuffle: Boolean) {
        MainScope().launch {
            setPlaybackMode(PlaybackMode.AUDIO_ONLY)
            // Save items to database
            GlobalScope.launch {
                items.forEach { item ->
                    DatabaseOperations.insertOrUpdateStream(item)
                }
            }
            val platformMediaItems = items.map { it.toPlatformMediaItem() }
            setQueue(platformMediaItems, startIndex, null)
            if (shuffle) {
                setShuffleModeEnabled(true)
            }
            prepare()
            play()

            GlobalScope.launch {
                if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
                    kotlinx.coroutines.delay(500)
                    SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
                }
            }
        }
    }

    // ===== Track Selection =====

    @OptIn(UnstableApi::class)
    override fun getAvailableResolutions(): List<ResolutionInfo> {
        val tracks = mediaController.currentTracks
        val resolutions = mutableListOf<ResolutionInfo>()

        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                trackGroup.forEachIndexed { trackIndex, format ->
                    if (!isCodecAllowed(format.codecs)) {
                        return@forEachIndexed
                    }
                    val isSelected = trackGroup.isTrackSelected(trackIndex)
                    val isHDR = isHDRFromCodec(format.codecs)

                    resolutions.add(
                        ResolutionInfo(
                            height = format.height,
                            width = format.width,
                            codec = format.codecs,
                            frameRate = format.frameRate,
                            isSelected = isSelected,
                            isHDR = isHDR
                        )
                    )
                }
            }
        }

        // Sort by codec priority descending, then by height descending
        return resolutions.sortedWith(
            compareByDescending<ResolutionInfo> { it.codecPriority }
                .thenByDescending { it.height }
        )
    }

    @OptIn(UnstableApi::class)
    override fun getAvailableSubtitles(): List<SubtitleInfo> {
        val tracks = mediaController.currentTracks
        val subtitles = mutableListOf<SubtitleInfo>()

        for (trackGroup in tracks.groups) {
            if (trackGroup.type == TRACK_TYPE_TEXT) {
                trackGroup.forEachIndexed { trackIndex, format ->
                    val isSelected = trackGroup.isTrackSelected(trackIndex)
                    val isAutoGenerated = format.label?.contains("auto-generated", ignoreCase = true) == true

                    subtitles.add(
                        SubtitleInfo(
                            language = format.language ?: "Unknown",
                            isSelected = isSelected,
                            isAutoGenerated = isAutoGenerated
                        )
                    )
                }
            }
        }

        return subtitles
    }

    @OptIn(UnstableApi::class)
    override fun selectResolution(resolution: ResolutionInfo) {
        val tracks = mediaController.currentTracks

        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                trackGroup.find { format ->
                    format.height == resolution.height &&
                        format.width == resolution.width &&
                        format.codecs == resolution.codec &&
                        format.frameRate == resolution.frameRate
                }?.let { format ->
                    val trackIndex = trackGroup.indexOfFirst { it == format }
                    if (trackIndex >= 0) {
                        val params = mediaController.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(trackGroup.mediaTrackGroup, trackIndex)
                            )
                            .build()
                        mediaController.trackSelectionParameters = params
                    }
                }
                break
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun selectSubtitle(subtitle: SubtitleInfo) {
        val tracks = mediaController.currentTracks

        for (trackGroup in tracks.groups) {
            trackGroup.find { format ->
                format.language == subtitle.language && trackGroup.type == C.TRACK_TYPE_TEXT
            }?.let { format ->
                val trackIndex = trackGroup.indexOfFirst { it == format }
                if (trackIndex >= 0) {
                    val params = mediaController.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(trackGroup.mediaTrackGroup, trackIndex)
                        )
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                    mediaController.trackSelectionParameters = params
                }
                break
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun applyDefaultResolution(defaultResolution: String) {
        val resolutions = _availableResolutions.value

        val targetResolution = when (defaultResolution) {
            "best" -> resolutions.firstOrNull()
            "lowest" -> resolutions.lastOrNull()
            "1080p" -> resolutions.find { it.resolutionPixel == "1080p" }
            "720p" -> resolutions.find { it.resolutionPixel == "720p" }
            "480p" -> resolutions.find { it.resolutionPixel == "480p" }
            "360p" -> resolutions.find { it.resolutionPixel == "360p" }
            else -> null
        }

        targetResolution?.let { selectResolution(it) }
    }

    @OptIn(UnstableApi::class)
    override fun clearResolutionOverride() {
        val params = mediaController.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
        mediaController.trackSelectionParameters = params
    }

    @OptIn(UnstableApi::class)
    override fun selectAudioLanguage(language: String) {
        val params = mediaController.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(language)
            .build()
        mediaController.trackSelectionParameters = params
    }

    @OptIn(UnstableApi::class)
    override fun disableSubtitles() {
        val params = mediaController.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        mediaController.trackSelectionParameters = params
    }

    override fun stopService() {
        mediaController.stopService()
    }

    override fun addPlaybackEventCallback(callback: PlaybackEventCallback) {
        playbackEventCallbacks.add(callback)
    }

    override fun removePlaybackEventCallback(callback: PlaybackEventCallback) {
        playbackEventCallbacks.remove(callback)
    }

    // ===== Helper Methods =====

    @OptIn(UnstableApi::class)
    private fun updateAvailableTracks(tracks: Tracks) {
        // Update resolutions
        val resolutions = mutableListOf<ResolutionInfo>()
        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                trackGroup.forEachIndexed { trackIndex, format ->
                    if (!isCodecAllowed(format.codecs)) {
                        return@forEachIndexed
                    }
                    val isSelected = trackGroup.isTrackSelected(trackIndex)
                    val isHDR = isHDRFromCodec(format.codecs)

                    resolutions.add(
                        ResolutionInfo(
                            height = format.height,
                            width = format.width,
                            codec = format.codecs,
                            frameRate = format.frameRate,
                            isSelected = isSelected,
                            isHDR = isHDR
                        )
                    )
                }
            }
        }
        _availableResolutions.value = resolutions
            .distinctBy { "${it.codec}_${it.height}_${it.frameRate}_${it.isHDR}" }
            .sortedWith(
                compareByDescending<ResolutionInfo> { it.height }
                    .thenByDescending { it.frameRate }
                    .thenByDescending { it.isHDR }
                    .thenByDescending { it.codecPriority }
            )

        // Update audio languages
        val languages = mutableListOf<AudioLanguageInfo>()
        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                trackGroup.forEachIndexed { trackIndex, format ->
                    val rawLanguage = format.language ?: "Default"
                    val languageCode = rawLanguage.substringBefore(".")
                    val hasMainRole = (format.roleFlags and C.ROLE_FLAG_MAIN) != 0
                    val isDefaultSelection = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
                    val isSelected = trackGroup.isTrackSelected(trackIndex)

                    languages.add(
                        AudioLanguageInfo(
                            language = languageCode,
                            isDefault = hasMainRole || isDefaultSelection,
                            isSelected = isSelected
                        )
                    )
                    if (isSelected) {
                        _currentAudioLanguage.value = languageCode
                    }
                }
            }
        }
        _availableAudioLanguages.value = languages.distinctBy { it.language }

        // Update subtitles
        val subtitles = mutableListOf<SubtitleInfo>()
        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                trackGroup.forEachIndexed { trackIndex, format ->
                    val isSelected = trackGroup.isTrackSelected(trackIndex)
                    val isAutoGenerated = format.id?.startsWith("a.") == true

                    subtitles.add(
                        SubtitleInfo(
                            language = format.language ?: "Unknown",
                            isSelected = isSelected,
                            isAutoGenerated = isAutoGenerated
                        )
                    )
                }
            }
        }
        _availableSubtitles.value = subtitles
    }

    private inline fun Tracks.Group.forEachIndexed(action: (index: Int, item: Format) -> Unit) {
        for (i in 0 until length) {
            action(i, getTrackFormat(i))
        }
    }

    private inline fun Tracks.Group.find(predicate: (Format) -> Boolean): Format? {
        for (i in 0 until length) {
            val item = getTrackFormat(i)
            if (predicate(item)) {
                return item
            }
        }
        return null
    }

    private inline fun Tracks.Group.indexOfFirst(predicate: (Format) -> Boolean): Int {
        for (i in 0 until length) {
            val item = getTrackFormat(i)
            if (predicate(item)) {
                return i
            }
        }
        return -1
    }

    private fun mapPlaybackState(state: Int): PlaybackState {
        return when (state) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> PlaybackState.READY
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }
    }

    private fun mapRepeatMode(mode: Int): RepeatMode {
        return when (mode) {
            Player.REPEAT_MODE_OFF -> RepeatMode.OFF
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
    }

    companion object {
        @Volatile
        private var instance: AndroidMediaController? = null
        private val mutex = Mutex()

        suspend fun getInstance(context: Context): AndroidMediaController {
            instance?.let { return it }
            return mutex.withLock {
                instance ?: AndroidMediaController(
                    MediaControllerHolder.getInstance(context)
                ).also { instance = it }
            }
        }
    }
}
