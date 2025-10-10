package project.pipepipe.app.ui.component.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.service.playFromStreamInfo
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.service.stopService
import project.pipepipe.shared.PlaybackMode
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.helper.ToastManager
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.toText
import project.pipepipe.shared.uistate.VideoDetailPageState
import project.pipepipe.app.ui.component.player.PlayerHelper.ResolutionInfo
import project.pipepipe.app.ui.component.player.PlayerHelper.SubtitleInfo
import project.pipepipe.app.ui.component.player.PlayerHelper.applyDefaultResolution
import project.pipepipe.app.ui.component.player.PlayerHelper.forEachIndexed
import project.pipepipe.app.ui.component.rememberDanmakuState
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
    var isControlsVisible by remember { mutableStateOf(true) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioLanguageMenu by remember { mutableStateOf(false) }

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
        SharedContext.settingsManager.getString("default_resolution_key", "auto")
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
                deltaMs.absoluteValue.toText(true)
        val positionLabel = targetMs.toText(true)
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
        swipeSeekState = SwipeSeekUiState("+0", swipeSeekStartPosition.toText(true))
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
        val deltaLabel = (if (diff >= 0) "+" else "-") + diff.absoluteValue.toText(true)
        swipeSeekState = SwipeSeekUiState(deltaLabel, swipeSeekTargetPosition.toText(true))
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
            if (SponsorBlockHelper.shouldSkipSegment(currentSegment)) {
                val skipToMs = (currentSegment.endTime).toLong()
                mediaController.seekTo(skipToMs)
                skippedSegments = skippedSegments + currentSegment.uuid
                lastSkippedSegment = currentSegment

                // 不显示 unskip 按钮（因为是自动跳过）

                // Show notification
                if (SponsorBlockHelper.isNotificationsEnabled()) {
                    val categoryName = segmentDisplayNames[currentSegment.uuid] ?: ""
                    ToastManager.show(playerSkippedText.replace("%s", categoryName))
                }
            }
            // Show manual skip button
            else if (SponsorBlockHelper.shouldShowSkipButton(currentSegment)) {
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
                val languageName = format.language ?: audioLanguageDefault
                languages.add(Pair(languageName, languageName.contains("Original")))
                if (audioGroup.isTrackSelected(index)) {
                    currentLanguage = languageName
                }
            }
        }
        availableLanguages = languages

        val subtitles = mutableListOf<SubtitleInfo>()
        currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { textGroup ->
            textGroup?.forEachIndexed { index, format ->
                val language = format.language ?: subtitleLanguageUnknown
                subtitles.add(
                    SubtitleInfo(
                        language = language,
                        trackGroup = textGroup.mediaTrackGroup,
                        trackIndex = index,
                        isSelected = textGroup.isTrackSelected(index)
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

        onDispose {
            mediaController.removeListener(listener)
            unskipButtonJob?.cancel()
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
    val isDarkTheme = isSystemInDarkTheme()
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
                controller.isAppearanceLightStatusBars = !isDarkTheme
                controller.isAppearanceLightNavigationBars = !isDarkTheme

                controller.show(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            }
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
                VideoSurface(
                    modifier = Modifier.fillMaxSize(),
                    mediaController = mediaController,
                    danmakuPool = danmakuPool,
                    danmakuState = danmakuState,
                    danmakuEnabled = danmakuEnabled
                )
                if (isControlsVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }

                // SponsorBlock Skip button (right side)
                AnimatedVisibility(
                    visible = showSkipButton && currentSegmentToSkip != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { skipCurrentSegment() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(MR.strings.player_skip),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(MR.strings.player_skip_segment)
                            )
                        }
                    }
                }

                // SponsorBlock Unskip button (left side)
                AnimatedVisibility(
                    visible = showUnskipButton && lastSkippedSegment != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { unskipLastSegment() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = stringResource(MR.strings.player_unskip_segment)
                            )
                            Text(
                                text = stringResource(MR.strings.player_unskip),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Player Controls
                AnimatedVisibility(
                    visibleState = controlsTransition,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.systemBarsIgnoringVisibility
                                    .only(WindowInsetsSides.Horizontal)
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        // top
                        Column(
                            modifier = modifier.then(
                                if (isFullscreenMode) {
                                    Modifier.statusBarsPadding()
                                } else {
                                    Modifier
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (!isFullscreenMode) {
                                        IconButton(onClick = {
                                            mediaController.stopService()
                                            SharedContext.sharedVideoDetailViewModel.hide()
                                        }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(MR.strings.player_close),
                                                tint = Color.White
                                            )
                                        }
                                    }

                                    if (isFullscreenMode) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = streamInfo.name ?: "",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.basicMarquee(),
                                                style = TextStyle(
                                                    platformStyle = PlatformTextStyle(
                                                        includeFontPadding = false
                                                    )
                                                )
                                            )
                                            streamInfo.uploaderName?.let {
                                                Text(
                                                    text = it,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = TextStyle(
                                                        platformStyle = PlatformTextStyle(
                                                            includeFontPadding = false
                                                        )
                                                    ),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                                ) {
                                    // Resolution button
                                    if (availableResolutions.isNotEmpty()) {
                                        Box {
                                            TextButton(onClick = { showResolutionMenu = true }) {
                                                Text(
                                                    text = if (hasVideoOverride()) availableResolutions.first { it.isSelected }.displayLabel else stringResource(MR.strings.player_resolution_auto),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showResolutionMenu,
                                                onDismissRequest = { showResolutionMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = stringResource(MR.strings.player_resolution_auto),
                                                            color = if (!hasVideoOverride())
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
                                                        val params = mediaController.trackSelectionParameters
                                                            .buildUpon()
                                                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                            .build()
                                                        mediaController.trackSelectionParameters = params
                                                        showResolutionMenu = false
                                                    }
                                                )
                                                availableResolutions.forEach { resolution ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = resolution.displayLabel,
                                                                color = if (resolution.isSelected && hasVideoOverride())
                                                                    MaterialTheme.colorScheme.primary
                                                                else
                                                                    MaterialTheme.colorScheme.onSurface
                                                            )
                                                        },
                                                        onClick = {
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
                                                            showResolutionMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Speed button
                                    TextButton(
                                        onClick = { showSpeedPitchDialog = true }
                                    ) {
                                        Text(
                                            text = if (currentSpeed == 1f) "1x" else String.format(
                                                "%.1fx",
                                                currentSpeed
                                            ),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // More button - structured exactly like the resolution button
                                    Box {
                                        TextButton(onClick = { showMoreMenu = true }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "More options",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMoreMenu,
                                            onDismissRequest = { showMoreMenu = false }
                                        ) {
                                            streamInfo.danmakuUrl?.let{
                                                DropdownMenuItem(
                                                    modifier = Modifier.height(44.dp),
                                                    text = {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (danmakuEnabled) {
                                                                    Icons.Default.Visibility
                                                                } else {
                                                                    Icons.Default.VisibilityOff
                                                                },
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.size(20.dp)
                                                            )

                                                            Text(
                                                                text = if (danmakuEnabled) {
                                                                    stringResource(MR.strings.player_disable_danmaku)
                                                                } else {
                                                                    stringResource(MR.strings.player_enable_danmaku)
                                                                }
                                                            )

                                                        }
                                                    },
                                                    onClick = {
                                                        onToggleDanmaku()
                                                        if (!danmakuEnabled) {
                                                            danmakuState.clear()
                                                        }
                                                        showMoreMenu = false
                                                    }

                                                )
                                            }

                                            if (availableSubtitles.isNotEmpty()) {
                                                DropdownMenuItem(
                                                    modifier = Modifier.height(44.dp),
                                                    text = {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ClosedCaption,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Text(stringResource(MR.strings.player_captions))
                                                        }
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        showSubtitleMenu = true
                                                    }
                                                )
                                            }


                                            if (availableLanguages.size > 1) {
                                                DropdownMenuItem(
                                                    modifier = Modifier.height(44.dp),
                                                    text = {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.SpatialAudioOff,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Text(stringResource(MR.strings.player_audio_language))
                                                        }
                                                    },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        showAudioLanguageMenu = true
                                                    }
                                                )
                                            }


                                            DropdownMenuItem(
                                                modifier = Modifier.height(44.dp),
                                                text = {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Timer,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Text(stringResource(MR.strings.player_sleep_timer))
                                                    }
                                                },
                                                onClick = {
                                                    showMoreMenu = false
                                                }
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAudioLanguageMenu,
                                            onDismissRequest = { showAudioLanguageMenu = false }
                                        ) {
                                            availableLanguages.forEach { language ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = language.first + if (language.second) " (${stringResource(MR.strings.player_original_audio)})" else "",
                                                            color = if (currentLanguage == language.first)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
                                                        val params = mediaController.trackSelectionParameters
                                                            .buildUpon()
                                                            .setPreferredAudioLanguage(language.first)
                                                            .build()
                                                        mediaController.trackSelectionParameters = params
                                                        showAudioLanguageMenu = false
                                                    }
                                                )
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showSubtitleMenu,
                                            onDismissRequest = { showSubtitleMenu = false }
                                        ) {
                                            // Add "Disable" option first
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(MR.strings.player_disable_subtitle),
                                                        color = if (availableSubtitles.find { it.isSelected } == null)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    val params = mediaController.trackSelectionParameters
                                                        .buildUpon()
                                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                        .build()
                                                    mediaController.trackSelectionParameters = params
                                                    showSubtitleMenu = false
                                                }
                                            )

                                            // Add available subtitle languages
                                            availableSubtitles.forEach { subtitle ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = subtitle.language,
                                                            color = if (subtitle.isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.onSurface
                                                        )
                                                    },
                                                    onClick = {
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
                                                        showSubtitleMenu = false
                                                    }
                                                )
                                            }
                                        }

                                    }
                                }
                            }
                        }

                        // Center Play Controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (timelineSize > 1 && currentTimelineIndex < timelineSize - 1) {
                                IconButton(
                                    onClick = {
                                        mediaController.seekToPrevious()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SkipPrevious,
                                        contentDescription = stringResource(MR.strings.player_previous),
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        mediaController.pause()
                                    } else {
                                        mediaController.play()
                                    }
                                },
                                modifier = Modifier.size(60.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(MR.strings.player_pause) else stringResource(MR.strings.player_play),
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            if (timelineSize > 1 && currentTimelineIndex < timelineSize - 1) {
                                IconButton(
                                    onClick = {
                                        mediaController.seekToNext()
                                        SharedContext.sharedVideoDetailViewModel.loadVideoDetails(mediaController.currentMediaItem!!.mediaId)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = stringResource(MR.strings.player_next),
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        // bottom
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentPosition.toText(true),
                                color = Color.White,
                                fontSize = 14.sp
                            )

                            VideoProgressBar(
                                currentPosition = currentPosition,
                                duration = duration,
                                bufferedPosition = bufferedPosition,
                                onSeek = { position ->
                                    mediaController.seekTo(position)
                                },
                                sponsorBlockSegments = sponsorBlockSegments,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = duration.toText(true),
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = onFullScreenClicked) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = stringResource(MR.strings.player_rotate_screen),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                if (isLongPressing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = stringResource(MR.strings.player_fast_forward),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "${String.format("%.1f", originalSpeed * speedingPlaybackMultiplier)}x",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                PlayerOverlays(
                    isLoading = isLoading,
                    showVolumeOverlay = showVolumeOverlay,
                    volumeProgress = volumeOverlayProgress,
                    showBrightnessOverlay = showBrightnessOverlay,
                    brightnessProgress = brightnessOverlayProgress,
                    doubleTapOverlayState = doubleTapOverlayState,
                    swipeSeekState = swipeSeekState,
                    modifier = Modifier.fillMaxSize()
                )

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
    }
}

