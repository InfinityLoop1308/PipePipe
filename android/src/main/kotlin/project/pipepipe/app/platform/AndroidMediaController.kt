package project.pipepipe.app.platform

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
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

    private val _currentItemIndex = MutableStateFlow(mediaController.currentMediaItemIndex)
    override val currentItemIndex: StateFlow<Int> = _currentItemIndex.asStateFlow()

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

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.value = mapPlaybackState(state)
            _duration.value = mediaController.duration
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem?.toPlatformMediaItem()
            _currentItemIndex.value = mediaController.currentMediaItemIndex
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
        _currentItemIndex.value = mediaController.currentMediaItemIndex

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

    override fun seekToItem(index: Int, positionMs: Long) {
        mediaController.seekTo(index, getPlaybackStartPosition(mediaController.getMediaItemAt(index).toPlatformMediaItem()))
    }

    override fun seekToPrevious() {
        mediaController.seekToPrevious()
    }

    override fun seekToNext() {
        mediaController.seekToNext()
    }

    fun syncCurrentItemIndex() {
        _currentItemIndex.value = mediaController.currentMediaItemIndex
    }

    override fun setQueue(items: List<PlatformMediaItem>, startIndex: Int) {
        val mediaItems = items.map { it.toMedia3MediaItem() }
        mediaController.setMediaItems(mediaItems, startIndex, getPlaybackStartPosition(items[0]))
    }

    override fun syncQueueShuffle() {
        val currentQueue = SharedContext.queueManager.getCurrentQueue()
        mediaController.removeMediaItems(0, mediaController.currentMediaItemIndex)
        mediaController.removeMediaItems(1, mediaController.mediaItemCount)
        val indexInQueueManger: Int = currentQueue.indexOfFirst { it.uuid == mediaController.currentMediaItem!!.uuid }
        mediaController.addMediaItems(1, currentQueue.subList(indexInQueueManger + 1, currentQueue.size).map { it.toMedia3MediaItem() })
        mediaController.addMediaItems(0, currentQueue.subList(0, indexInQueueManger).map { it.toMedia3MediaItem() })
        syncCurrentItemIndex()
    }

    override fun syncQueueClear() {
        mediaController.clearMediaItems()
    }

    override fun syncQueueRemove(index: Int) {
        mediaController.removeMediaItem(index)
        syncCurrentItemIndex()
    }

    override fun syncQueueAppend(item: PlatformMediaItem) {
        mediaController.addMediaItem(item.toMedia3MediaItem())
    }

    override fun syncQueueMove(from: Int, to: Int) {
        mediaController.moveMediaItem(from, to)
        syncCurrentItemIndex()
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
        } else if (!enabled && shuffleModeEnabled.value){
            SharedContext.queueManager.unshuffle()
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
                mediaController.seekTo(queueIndex, getPlaybackStartPosition(item))
            } else {
                // Item is not in queue, set it as single item
                SharedContext.queueManager.setQueue(listOf(item))
            }
            mediaController.prepare()
            mediaController.play()
        }

        if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
            SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
        }
    }


    // ===== Track Selection =====
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
                    break
                }
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
