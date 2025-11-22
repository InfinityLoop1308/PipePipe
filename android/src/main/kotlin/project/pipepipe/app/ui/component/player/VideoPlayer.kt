package project.pipepipe.app.ui.component.player

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.global.PipHelper
import project.pipepipe.app.service.SleepTimerService
import project.pipepipe.app.service.playFromStreamInfo
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.service.stopService
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.app.ui.component.player.PlayerHelper.ResolutionInfo
import project.pipepipe.app.ui.component.player.PlayerHelper.SubtitleInfo
import project.pipepipe.app.ui.component.player.PlayerHelper.applyDefaultResolution
import project.pipepipe.app.ui.component.player.PlayerHelper.forEachIndexed
import project.pipepipe.app.ui.theme.applySystemBarColors
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

data class DoubleTapOverlayState(
    val portion: DisplayPortion,
    val accumulatedSeekMs: Long
)


private const val SEEK_SWIPE_FACTOR = 100f
private const val SEEK_SWIPE_FAST_MULTIPLIER = 10f
private const val SEEK_SWIPE_FAST_THRESHOLD_MS = 60_000L
private const val VERTICAL_SWIPE_NORMALIZER = 600f

@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable
fun VideoPlayer(
    mediaController: MediaController,
    streamInfo: StreamInfo,
    onFullScreenClicked: () -> Unit,
    modifier: Modifier = Modifier,
    gestureSettings: PlayerGestureSettings = PlayerGestureSettings(),
    danmakuPool: List<DanmakuInfo>? = null,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit,
    sponsorBlockSegments: List<SponsorBlockSegmentInfo> = emptyList()
) {
    var isControlsVisible by remember { mutableStateOf(false) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioLanguageMenu by remember { mutableStateOf(false) }

    val isInPipMode by SharedContext.isInPipMode.collectAsState()

    var isPlaying by remember { mutableStateOf(mediaController.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(mediaController.currentPosition) }
    var duration by remember { mutableLongStateOf(mediaController.duration.coerceAtLeast(0)) }
    var bufferedPosition by remember { mutableLongStateOf(mediaController.bufferedPosition) }
    var currentTimelineIndex by remember { mutableIntStateOf(mediaController.currentMediaItemIndex) }
    var timelineSize by remember { mutableIntStateOf(mediaController.mediaItemCount) }
    var isLoading by remember { mutableStateOf(mediaController.playbackState == Player.STATE_BUFFERING) }
    var currentSpeed by remember { mutableFloatStateOf(mediaController.playbackParameters.speed) }
    var currentPitch by remember { mutableFloatStateOf(mediaController.playbackParameters.pitch) }
    var availableResolutions by remember { mutableStateOf<List<ResolutionInfo>>(emptyList()) }
    var availableLanguages by remember { mutableStateOf<Set<Pair<String, Boolean>>>(emptySet()) }
    var currentLanguage by remember { mutableStateOf("Default") }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var availableSubtitles by remember { mutableStateOf<List<SubtitleInfo>>(emptyList()) }
    val defaultResolution = remember {
        SharedContext.settingsManager.getString("default_resolution", "auto")
    }

    // SponsorBlock state variables
    var currentSegmentToSkip by remember { mutableStateOf<SponsorBlockSegmentInfo?>(null) }
    var showSkipButton by remember { mutableStateOf(false) }
    var showUnskipButton by remember { mutableStateOf(false) }
    var lastSkippedSegment by remember { mutableStateOf<SponsorBlockSegmentInfo?>(null) }
    var skippedSegments by remember { mutableStateOf<Set<String>>(emptySet()) }
    var unskipButtonJob by remember { mutableStateOf<Job?>(null) }

    // Pre-compute display names for all segments to avoid calling @Composable from non-Composable context
    val segmentDisplayNames = sponsorBlockSegments.associate { segment ->
        segment.uuid to SponsorBlockUtils.getCategoryName(segment.category)
    }

    // Pre-fetch string resources for notifications and track labels
    val playerSkippedText = stringResource(MR.strings.player_skipped_category)
    val playerUnskippedText = stringResource(MR.strings.player_unskipped)
    val audioLanguageDefault = stringResource(MR.strings.player_audio_language_default)
    val subtitleLanguageUnknown = stringResource(MR.strings.player_subtitle_language_unknown)


    fun hasVideoOverride(): Boolean = availableResolutions.count { it.isSelected } == 1


    val danmakuState = rememberDanmakuState()

    val controlsTransition = remember { MutableTransitionState(false) }.apply {
        targetState = isControlsVisible
    }


    val context = LocalContext.current
    val density = LocalDensity.current
    val rotationThresholdPx = remember(density) { with(density) { 40.dp.toPx() } }
    val gestureScope = rememberCoroutineScope()

    val isFullscreenMode =
        SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.FULLSCREEN_PLAYER

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxSystemVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volumeOverlayProgress by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxSystemVolume.toFloat()
        )
    }

    val activity = remember(context) { context.findActivity() }
    val windowInsetsController = remember(activity) {
        activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
    }
    var brightnessOverlayProgress by remember {
        mutableFloatStateOf(activity?.let(::readScreenBrightness) ?: 0.5f)
    }

    var gestureContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var swipeSeekState by remember { mutableStateOf<SwipeSeekUiState?>(null) }
    var swipeSeekDismissJob by remember { mutableStateOf<Job?>(null) }
    var isSwipeSeeking by remember { mutableStateOf(false) }
    var accumulatedSeek by remember { mutableFloatStateOf(0f) }
    var swipeSeekStartPosition by remember { mutableLongStateOf(0L) }
    var swipeSeekTargetPosition by remember { mutableLongStateOf(0L) }
    var isChangingVolume by remember { mutableStateOf(false) }
    var isChangingBrightness by remember { mutableStateOf(false) }

    var doubleTapOverlayState by remember { mutableStateOf<DoubleTapOverlayState?>(null) }
    var doubleTapAccumulatedMs by remember { mutableLongStateOf(0L) }
    var doubleTapLastPortion by remember { mutableStateOf<DisplayPortion?>(null) }
    var doubleTapOverlayJob by remember { mutableStateOf<Job?>(null) }

    var isLongPressing by remember { mutableStateOf(false) }
    var originalSpeed by remember { mutableFloatStateOf(1f) }
    val speedingPlaybackMultiplier = remember {
        SharedContext.settingsManager.getString("speeding_playback_key", "3").toFloat()
    }

    fun showDoubleTapOverlay(portion: DisplayPortion, deltaMs: Long) {
        val sameSide = doubleTapLastPortion == portion
        doubleTapAccumulatedMs = if (sameSide) {
            doubleTapAccumulatedMs + deltaMs
        } else {
            deltaMs
        }

        doubleTapLastPortion = portion
        doubleTapOverlayState = DoubleTapOverlayState(portion, doubleTapAccumulatedMs)

        doubleTapOverlayJob?.cancel()
        doubleTapOverlayJob = gestureScope.launch {
            delay(1000)
            doubleTapOverlayState = null
            doubleTapAccumulatedMs = 0L
            doubleTapLastPortion = null
        }
    }


    fun showSeekOverlay(deltaMs: Long, targetMs: Long) {
        val deltaLabel = (if (deltaMs >= 0) "+" else "-") +
                deltaMs.absoluteValue.toDurationString(true)
        val positionLabel = targetMs.toDurationString(true)
        swipeSeekState = SwipeSeekUiState(deltaLabel, positionLabel)
        swipeSeekDismissJob?.cancel()
        swipeSeekDismissJob = gestureScope.launch {
            delay(600)
            if (!isSwipeSeeking) swipeSeekState = null
        }
    }

    fun applySeekDelta(deltaMs: Long, showSeekOverlay: Boolean = true) {
        val current = mediaController.currentPosition
        val clamp = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (current + deltaMs).coerceIn(0L, clamp)
        mediaController.seekTo(target)
        if (showSeekOverlay) {
            showSeekOverlay(deltaMs, target)
        }
    }


    fun beginSeekGesture() {
        if (isSwipeSeeking) return
        isSwipeSeeking = true
        accumulatedSeek = 0f
        swipeSeekStartPosition = mediaController.currentPosition
        swipeSeekTargetPosition = swipeSeekStartPosition
        swipeSeekDismissJob?.cancel()
        swipeSeekState = SwipeSeekUiState("+0", swipeSeekStartPosition.toDurationString(true))
        showVolumeOverlay = false
        showBrightnessOverlay = false
        isChangingVolume = false
        isChangingBrightness = false
    }

    fun updateSeekGesture(deltaPx: Float) {
        if (!isSwipeSeeking) beginSeekGesture()
        accumulatedSeek -= deltaPx
        val thresholdPx = SEEK_SWIPE_FAST_THRESHOLD_MS / SEEK_SWIPE_FACTOR
        val deltaMs = if (abs(accumulatedSeek) <= thresholdPx) {
            (accumulatedSeek * SEEK_SWIPE_FACTOR).toLong()
        } else {
            val beyond = abs(accumulatedSeek) - thresholdPx
            (accumulatedSeek.sign *
                    (SEEK_SWIPE_FAST_THRESHOLD_MS + beyond * SEEK_SWIPE_FACTOR * SEEK_SWIPE_FAST_MULTIPLIER)).toLong()
        }
        val clamp = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        swipeSeekTargetPosition = (swipeSeekStartPosition + deltaMs).coerceIn(0L, clamp)
        val diff = swipeSeekTargetPosition - swipeSeekStartPosition
        val deltaLabel = (if (diff >= 0) "+" else "-") + diff.absoluteValue.toDurationString(true)
        swipeSeekState = SwipeSeekUiState(deltaLabel, swipeSeekTargetPosition.toDurationString(true))
    }

    fun endSeekGesture() {
        if (!isSwipeSeeking) return
        mediaController.seekTo(swipeSeekTargetPosition)
        isSwipeSeeking = false
        accumulatedSeek = 0f
        gestureScope.launch {
            delay(200)
            if (!isSwipeSeeking) swipeSeekState = null
        }
    }

    fun beginVolumeGesture() {
        if (isChangingVolume) return
        isChangingVolume = true
        volumeOverlayProgress =
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxSystemVolume.toFloat()
        showVolumeOverlay = true
        showBrightnessOverlay = false
        swipeSeekDismissJob?.cancel()
    }

    fun updateVolumeGesture(deltaY: Float) {
        if (!isChangingVolume) beginVolumeGesture()

        val deltaProgress = (-deltaY) / VERTICAL_SWIPE_NORMALIZER
        volumeOverlayProgress = (volumeOverlayProgress + deltaProgress).coerceIn(0f, 1f)
        val newVolume = (volumeOverlayProgress * maxSystemVolume).roundToInt()
            .coerceIn(0, maxSystemVolume)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (newVolume != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        }
    }


    fun endVolumeGesture() {
        isChangingVolume = false
        showVolumeOverlay = false
    }

    fun beginBrightnessGesture() {
        if (isChangingBrightness) return
        isChangingBrightness = true
        brightnessOverlayProgress = activity?.let(::readScreenBrightness) ?: brightnessOverlayProgress
        showBrightnessOverlay = true
        showVolumeOverlay = false
        swipeSeekDismissJob?.cancel()
    }

    fun updateBrightnessGesture(deltaY: Float) {
        if (!isChangingBrightness) beginBrightnessGesture()
        val deltaProgress = (-deltaY) / VERTICAL_SWIPE_NORMALIZER
        val newProgress = (brightnessOverlayProgress + deltaProgress).coerceIn(0f, 1f)
        brightnessOverlayProgress = newProgress
        activity?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = newProgress
            window.attributes = lp
            // Save brightness to preferences
            saveScreenBrightness(newProgress)
        }
    }

    fun endBrightnessGesture() {
        isChangingBrightness = false
        showBrightnessOverlay = false
    }

    fun handleDoubleTap(portion: DisplayPortion) {
        val seekMs = SharedContext.settingsManager.getString("seek_duration_key", "15000").toLong()
        when (portion) {
            DisplayPortion.Left -> {
                applySeekDelta(-seekMs, showSeekOverlay = false)
                showDoubleTapOverlay(DisplayPortion.Left, -seekMs)
            }

            DisplayPortion.Right -> {
                applySeekDelta(seekMs, showSeekOverlay = false)
                showDoubleTapOverlay(DisplayPortion.Right, seekMs)
            }

            DisplayPortion.Middle -> {
                if (isPlaying) mediaController.pause() else mediaController.play()
            }
        }
    }

    // Check current segment for SponsorBlock

    fun checkCurrentSegment(position: Long) {
        if (!SponsorBlockHelper.isEnabled()) {
            currentSegmentToSkip = null
            showSkipButton = false
            return
        }

        // Find segment at current position
        val currentSegment = sponsorBlockSegments.firstOrNull { segment ->
            position.toDouble() in segment.startTime..segment.endTime
        }
        if (currentSegment != null && !skippedSegments.contains(currentSegment.uuid)) {
            // Only show manual skip button (automatic skipping is handled by PlaybackService)
            if (SponsorBlockHelper.shouldShowSkipButton(currentSegment)) {
                currentSegmentToSkip = currentSegment
                showSkipButton = true
            } else {
                currentSegmentToSkip = null
                showSkipButton = false
            }
        } else {
            currentSegmentToSkip = null
            showSkipButton = false
        }
    }

    // Manual skip function
    fun skipCurrentSegment() {
        currentSegmentToSkip?.let { segment ->
            val skipToMs = segment.endTime.toLong()
            mediaController.seekTo(skipToMs)
            skippedSegments = skippedSegments + segment.uuid
            lastSkippedSegment = segment
            showSkipButton = false
            currentSegmentToSkip = null

            // Show unskip button
            showUnskipButton = true
            unskipButtonJob?.cancel()
            unskipButtonJob = gestureScope.launch {
                delay(5000)
                showUnskipButton = false
            }

            // Show notification
            if (SponsorBlockHelper.isNotificationsEnabled()) {
                val categoryName = segmentDisplayNames[segment.uuid] ?: ""
                ToastManager.show(playerSkippedText.replace("%s", categoryName))
            }
        }
    }

    // Unskip function
    fun unskipLastSegment() {
        lastSkippedSegment?.let { segment ->
            val returnToMs = segment.startTime.toLong()
            mediaController.seekTo(returnToMs)
            skippedSegments = skippedSegments - segment.uuid
            lastSkippedSegment = null
            showUnskipButton = false
            unskipButtonJob?.cancel()

            // Show notification
            if (SponsorBlockHelper.isNotificationsEnabled()) {
                ToastManager.show(playerUnskippedText)
            }
        }
    }


    val updateAvailableTracks: (Tracks) -> Unit = { currentTracks ->
        val resolutions = mutableListOf<ResolutionInfo>()
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { videoGroup ->
            videoGroup?.forEachIndexed { index, format ->
                if (videoGroup.isTrackSupported(index)) {
                    resolutions.add(
                        ResolutionInfo(
                            height = format.height,
                            width = format.width,
                            codecs = format.codecs,
                            frameRate = format.frameRate,
                            trackGroup = videoGroup.mediaTrackGroup,
                            trackIndex = index,
                            isSelected = videoGroup.isTrackSelected(index)
                        )
                    )
                }
            }
        }


        availableResolutions = resolutions
            .distinctBy { "${it.codecs}_${it.height}_${it.frameRate}" }
            .sortedWith(
                compareByDescending<ResolutionInfo> { it.height }
                    .thenByDescending { it.frameRate }
                    .thenByDescending { it.codecPriority }
            )

        if (!hasVideoOverride() && defaultResolution != "auto") {
            applyDefaultResolution(defaultResolution, availableResolutions, mediaController)
        }


        val languages = mutableSetOf<Pair<String, Boolean>>()
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { audioGroup ->
            audioGroup?.forEachIndexed { index, format ->
                val rawLanguage = format.language ?: audioLanguageDefault
                // Extract pure language code by removing everything after "."
                val languageCode = rawLanguage.substringBefore(".")
                val isOriginal = rawLanguage.contains("Original")
                languages.add(Pair(languageCode, isOriginal))
                if (audioGroup.isTrackSelected(index)) {
                    currentLanguage = languageCode
                }
            }
        }
        availableLanguages = languages

        val subtitles = mutableListOf<SubtitleInfo>()
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { textGroup ->
            textGroup?.forEachIndexed { index, format ->
                val language = format.language ?: subtitleLanguageUnknown
                // Check if this is an auto-generated subtitle by checking the vssId format
                // Auto-generated subtitles have vssId starting with "a." (e.g., "a.en")
                val isAutoGenerated = format.id?.startsWith("a.") == true
                subtitles.add(
                    SubtitleInfo(
                        language = language,
                        trackGroup = textGroup.mediaTrackGroup,
                        trackIndex = index,
                        isSelected = textGroup.isTrackSelected(index),
                        isAutoGenerated = isAutoGenerated
                    )
                )
            }
        }
        availableSubtitles = subtitles
    }

    LaunchedEffect(mediaController) {
        updateAvailableTracks(mediaController.currentTracks)
    }


    val audioMode by SharedContext.playbackMode.collectAsState()

    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTimelineIndex = mediaController.currentMediaItemIndex
                timelineSize = mediaController.mediaItemCount
                danmakuState.clear()
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateAvailableTracks(tracks)
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                currentSpeed = playbackParameters.speed
                currentPitch = playbackParameters.pitch
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                currentTimelineIndex = mediaController.currentMediaItemIndex
                timelineSize = mediaController.mediaItemCount
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                when (reason) {
                    Player.DISCONTINUITY_REASON_SEEK,
                    Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> {
                        danmakuState.onSeek()
                        // User manual seek - clear skip button
                        showSkipButton = false
                        currentSegmentToSkip = null
                    }
                    Player.DISCONTINUITY_REASON_REMOVE,
                    Player.DISCONTINUITY_REASON_SKIP -> {
                        danmakuState.onSeek()
                    }

                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                        danmakuState.clear()
                        // Reset all SponsorBlock state on video transition
                        skippedSegments = emptySet()
                        lastSkippedSegment = null
                        showUnskipButton = false
                        showSkipButton = false
                        currentSegmentToSkip = null
                    }
                }
            }

        }

        mediaController.addListener(listener)

        if (mediaController.isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            mediaController.removeListener(listener)
            unskipButtonJob?.cancel()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Restore system brightness when player is disposed
            activity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = -1f  // -1 means use system default brightness
                window.attributes = lp
            }
        }
    }

    LaunchedEffect(mediaController, sponsorBlockSegments) {
        while (true) {
            currentPosition = mediaController.currentPosition
            duration = mediaController.duration.coerceAtLeast(0)
            bufferedPosition = mediaController.bufferedPosition

            // Check for SponsorBlock segments
            checkCurrentSegment(currentPosition)

            delay(500)  // Check more frequently for better responsiveness
        }
    }
    val colorScheme = MaterialTheme.colorScheme
    LaunchedEffect(isControlsVisible, isFullscreenMode) {
        windowInsetsController?.let { controller ->
            if (isFullscreenMode) {
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                if (isControlsVisible) {
                    controller.show(
                        WindowInsetsCompat.Type.statusBars() or
                                WindowInsetsCompat.Type.navigationBars()
                    )
                } else {
                    controller.hide(
                        WindowInsetsCompat.Type.statusBars() or
                                WindowInsetsCompat.Type.navigationBars()
                    )
                }
            } else {
                applySystemBarColors(controller, colorScheme)
                controller.show(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            }
        }
    }

    // Manage brightness based on fullscreen mode
    LaunchedEffect(isFullscreenMode, gestureSettings.brightnessGestureEnabled) {
        activity?.window?.let { window ->
            val lp = window.attributes
            if (isFullscreenMode && gestureSettings.brightnessGestureEnabled) {
                // Entering fullscreen: restore saved brightness if available
                val savedBrightness = getSavedScreenBrightness()
                if (savedBrightness >= 0f) {
                    lp.screenBrightness = savedBrightness
                    brightnessOverlayProgress = savedBrightness
                }
            } else {
                // Exiting fullscreen or gesture disabled: restore system brightness
                lp.screenBrightness = -1f
            }
            window.attributes = lp
        }
    }


    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (audioMode == PlaybackMode.AUDIO_ONLY) {
            AsyncImage(
                model = streamInfo.thumbnailUrl,
                contentDescription = "Video thumbnail",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        mediaController.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
                        mediaController.playFromStreamInfo(streamInfo)
                    }
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(MR.strings.player_play_video),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { gestureContainerSize = it }
                    .pointerInput(
                        isFullscreenMode,
                        gestureSettings,
                        gestureContainerSize
                    ) {
                        if (!gestureSettings.swipeSeekEnabled &&
                            !gestureSettings.volumeGestureEnabled &&
                            !gestureSettings.brightnessGestureEnabled &&
                            !gestureSettings.fullscreenGestureEnabled
                        ) return@pointerInput
                        detectPlayerDragGestures(
                            sizeProvider = { gestureContainerSize },
                            rotationThresholdPx = rotationThresholdPx,
                            isFullscreen = { isFullscreenMode },
                            isSwipeSeekEnabled = { gestureSettings.swipeSeekEnabled },
                            isVolumeGestureEnabled = { gestureSettings.volumeGestureEnabled },
                            isBrightnessGestureEnabled = { gestureSettings.brightnessGestureEnabled },
                            isFullscreenGestureEnabled = { gestureSettings.fullscreenGestureEnabled },
                            onSeekStart = ::beginSeekGesture,
                            onSeek = { updateSeekGesture(it) },
                            onSeekEnd = {
                                endSeekGesture()
                                if (isControlsVisible && isPlaying) {
                                    isControlsVisible = false
                                }
                            },
                            onVolumeStart = ::beginVolumeGesture,
                            onVolume = ::updateVolumeGesture,
                            onVolumeEnd = ::endVolumeGesture,
                            onBrightnessStart = ::beginBrightnessGesture,
                            onBrightness = ::updateBrightnessGesture,
                            onBrightnessEnd = ::endBrightnessGesture,
                            onRotation = { swipeUp ->
                                if (gestureSettings.fullscreenGestureEnabled) {
                                    if ((isFullscreenMode && swipeUp) ||
                                        (!isFullscreenMode && !swipeUp)
                                    ) {
                                        onFullScreenClicked()
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(gestureContainerSize, isPlaying, isControlsVisible) {
                        detectTapGestures(
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            },
                            onDoubleTap = { offset ->
                                val portion = gestureContainerSize.portionForX(offset.x)
                                handleDoubleTap(portion)
                            },
                            onLongPress = {
                                // 长按加速
                                if (!isLongPressing && isPlaying) {
                                    isLongPressing = true
                                    originalSpeed = mediaController.playbackParameters.speed
                                    val speedUpSpeed = originalSpeed * speedingPlaybackMultiplier
                                    mediaController.playbackParameters = PlaybackParameters(
                                        speedUpSpeed,
                                        mediaController.playbackParameters.pitch
                                    )
                                }
                            },
                            onPress = {
                                // 等待手指抬起
                                val released = tryAwaitRelease()
                                // 无论是否成功释放，如果正在长按加速状态，都恢复速度
                                if (isLongPressing) {
                                    isLongPressing = false
                                    mediaController.playbackParameters = PlaybackParameters(
                                        originalSpeed,
                                        mediaController.playbackParameters.pitch
                                    )
                                }
                            }
                        )
                    }

            ) {
                key(audioMode) {
                    VideoSurface(
                        modifier = Modifier.fillMaxSize(),
                        mediaController = mediaController,
                        danmakuPool = danmakuPool,
                        danmakuState = danmakuState,
                        danmakuEnabled = danmakuEnabled
                    )
                }
                if (!isInPipMode) {
                    // Create player control state
                    val playerControlState = PlayerControlState(
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        bufferedPosition = bufferedPosition,
                        currentSpeed = currentSpeed,
                        currentPitch = currentPitch,
                        currentTimelineIndex = currentTimelineIndex,
                        timelineSize = timelineSize,
                        availableResolutions = availableResolutions,
                        availableLanguages = availableLanguages,
                        currentLanguage = currentLanguage,
                        availableSubtitles = availableSubtitles,
                        isLongPressing = isLongPressing,
                        originalSpeed = originalSpeed,
                        speedingPlaybackMultiplier = speedingPlaybackMultiplier,
                        isLoading = isLoading,
                        showVolumeOverlay = showVolumeOverlay,
                        volumeProgress = volumeOverlayProgress,
                        showBrightnessOverlay = showBrightnessOverlay,
                        brightnessProgress = brightnessOverlayProgress,
                        doubleTapOverlayState = doubleTapOverlayState,
                        swipeSeekState = swipeSeekState,
                        sponsorBlockSegments = sponsorBlockSegments,
                        currentSegmentToSkip = currentSegmentToSkip,
                        lastSkippedSegment = lastSkippedSegment,
                        showSkipButton = showSkipButton,
                        showUnskipButton = showUnskipButton
                    )

                    // Create player control callbacks
                    val playerControlCallbacks = PlayerControlCallbacks(
                        onPlayPauseClick = {
                            if (isPlaying) {
                                mediaController.pause()
                            } else {
                                mediaController.play()
                            }
                        },
                        onSeekToPrevious = {
                            mediaController.seekToPrevious()
                        },
                        onSeekToNext = {
                            mediaController.seekToNext()
                            SharedContext.sharedVideoDetailViewModel.loadVideoDetails(mediaController.currentMediaItem!!.mediaId)
                        },
                        onSeek = { position ->
                            mediaController.seekTo(position)
                        },
                        onFullScreenClick = {
                            isControlsVisible = false
                            onFullScreenClicked()
                        },
                        onClose = {
                            mediaController.stopService()
                            SharedContext.sharedVideoDetailViewModel.hide()
                        },
                        onResolutionSelected = { resolution ->
                            val params = mediaController.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        resolution.trackGroup,
                                        resolution.trackIndex
                                    )
                                )
                                .build()
                            mediaController.trackSelectionParameters = params
                        },
                        onResolutionAuto = {
                            val params = mediaController.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .build()
                            mediaController.trackSelectionParameters = params
                        },
                        onSpeedPitchClick = {
                            showSpeedPitchDialog = true
                        },
                        onAudioLanguageSelected = { language ->
                            val params = mediaController.trackSelectionParameters
                                .buildUpon()
                                .setPreferredAudioLanguage(language)
                                .build()
                            mediaController.trackSelectionParameters = params
                        },
                        onSubtitleSelected = { subtitle ->
                            val params = mediaController.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(
                                        subtitle.trackGroup,
                                        subtitle.trackIndex
                                    )
                                )
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            mediaController.trackSelectionParameters = params
                        },
                        onSubtitleDisabled = {
                            val params = mediaController.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            mediaController.trackSelectionParameters = params
                        },
                        onToggleDanmaku = onToggleDanmaku,
                        onSkipSegment = ::skipCurrentSegment,
                        onUnskipSegment = ::unskipLastSegment
                    )

                    // Use PlayerControl component
                    PlayerControl(
                        streamInfo = streamInfo,
                        mediaController = mediaController,
                        state = playerControlState,
                        callbacks = playerControlCallbacks,
                        isFullscreenMode = isFullscreenMode,
                        danmakuEnabled = danmakuEnabled,
                        danmakuState = danmakuState,
                        controlsTransition = controlsTransition,
                        showResolutionMenu = showResolutionMenu,
                        onResolutionMenuChange = { showResolutionMenu = it },
                        showSpeedPitchDialog = showSpeedPitchDialog,
                        onSpeedPitchDialogChange = { showSpeedPitchDialog = it },
                        showMoreMenu = showMoreMenu,
                        onMoreMenuChange = { showMoreMenu = it },
                        showAudioLanguageMenu = showAudioLanguageMenu,
                        onAudioLanguageMenuChange = { showAudioLanguageMenu = it },
                        showSubtitleMenu = showSubtitleMenu,
                        onSubtitleMenuChange = { showSubtitleMenu = it },
                        showSleepTimerDialog = showSleepTimerDialog,
                        onSleepTimerDialogChange = { showSleepTimerDialog = it },
                        onPipClick = {
                            PipHelper.enterPipMode(mediaController, streamInfo, context)
                        }
                    )
                }
            }
        }
        if (showSpeedPitchDialog) {
            SpeedPitchDialog(
                currentSpeed = currentSpeed,
                currentPitch = currentPitch,
                onDismiss = { showSpeedPitchDialog = false },
                onApply = { speed, pitch ->
                    currentSpeed = speed
                    currentPitch = pitch

                    // Apply speed and pitch to the media controller
                    val params = PlaybackParameters(speed, pitch)
                    mediaController.playbackParameters = params
                }
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepTimerDialog = false },
                onConfirm = { minutes ->
                    SleepTimerService.startTimer(context, minutes)
                }
            )
        }
    }
}

