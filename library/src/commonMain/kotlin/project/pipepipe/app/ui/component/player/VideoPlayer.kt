package project.pipepipe.app.ui.component.player

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.app.helper.SponsorBlockHelper
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.ranges.coerceAtLeast
import project.pipepipe.app.platform.*

data class DoubleTapOverlayState(
    val portion: DisplayPortion,
    val accumulatedSeekMs: Long
)


private const val SEEK_SWIPE_FACTOR = 100f
private const val SEEK_SWIPE_FAST_MULTIPLIER = 10f
private const val SEEK_SWIPE_FAST_THRESHOLD_MS = 60_000L
private const val VERTICAL_SWIPE_NORMALIZER = 600f
private const val DEFAULT_CONTROLS_HIDE_TIME = 3000L // 3 seconds

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPlayer(
    mediaController: PlatformMediaController,
    streamInfo: StreamInfo,
    onFullScreenClicked: () -> Unit,
    modifier: Modifier = Modifier,
    gestureSettings: PlayerGestureSettings = PlayerGestureSettings(),
    danmakuPool: List<DanmakuInfo>? = null,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit,
    sponsorBlockSegments: List<SponsorBlockSegmentInfo> = emptyList()
) {
    val platformActions = SharedContext.platformActions

    var isControlsVisible by remember { mutableStateOf(false) }
    var showResolutionMenu by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showAudioLanguageMenu by remember { mutableStateOf(false) }
    var isSeekBarDragging by remember { mutableStateOf(false) }

    val isInPipMode by SharedContext.isInPipMode.collectAsState()

    // Collect state from PlatformMediaController
    val isPlaying by mediaController.isPlaying.collectAsState()
    val currentPosition by mediaController.currentPosition.collectAsState()
    val duration by mediaController.duration.collectAsState()
    val bufferedPosition by mediaController.bufferedPosition.collectAsState()
    val currentItemIndex by SharedContext.queueManager.currentIndex.collectAsState()
    val mediaItemCount by SharedContext.queueManager.mediaItemCount.collectAsState()
    val playbackState by mediaController.playbackState.collectAsState()
    val playbackSpeed by mediaController.playbackSpeed.collectAsState()
    val playbackPitch by mediaController.playbackPitch.collectAsState()
    val availableResolutions by mediaController.availableResolutions.collectAsState()
    val availableAudioLanguages by mediaController.availableAudioLanguages.collectAsState()
    val currentAudioLanguage by mediaController.currentAudioLanguage.collectAsState()
    val availableSubtitles by mediaController.availableSubtitles.collectAsState()

    var showSubtitleMenu by remember { mutableStateOf(false) }
    val defaultResolution = remember {
        SharedContext.settingsManager.getString("default_resolution", "auto")
    }

    // Derived state
    val isLoading = playbackState == PlaybackState.BUFFERING
    val currentSpeed = playbackSpeed
    val currentPitch = playbackPitch
    val currentTimelineIndex = currentItemIndex
    val timelineSize = mediaItemCount

    // Convert audio languages to Set<Pair<String, Boolean>> for compatibility
    val availableLanguages = remember(availableAudioLanguages) {
        availableAudioLanguages.map { it.language to it.isDefault }.toSet()
    }

    // SponsorBlock state variables
    var currentSegmentToSkip by remember { mutableStateOf<SponsorBlockSegmentInfo?>(null) }
    var showSkipButton by remember { mutableStateOf(false) }
    var showUnskipButton by remember { mutableStateOf(false) }
    var lastSkippedSegment by remember { mutableStateOf<SponsorBlockSegmentInfo?>(null) }
    var skippedSegments by remember { mutableStateOf<Set<String>>(emptySet()) }
    var skipButtonShownTime by remember { mutableLongStateOf(0L) }
    var skipButtonJob by remember { mutableStateOf<Job?>(null) }
    var unskipButtonJob by remember { mutableStateOf<Job?>(null) }

    // Pre-compute display names for all segments to avoid calling @Composable from non-Composable context
    val segmentDisplayNames = sponsorBlockSegments.associate { segment ->
        segment.uuid to SponsorBlockHelper.getCategoryName(segment.category)
    }

    // Pre-fetch string resources for notifications and track labels
    val playerSkippedText = stringResource(MR.strings.player_skipped_category)
    val playerUnskippedText = stringResource(MR.strings.player_unskipped)

    fun hasVideoOverride(): Boolean = availableResolutions.count { it.isSelected } == 1


    val danmakuState = rememberDanmakuState()

    val controlsTransition = remember(isControlsVisible) {
        MutableTransitionState(isControlsVisible)
    }



    val density = LocalDensity.current
    val rotationThresholdPx = remember(density) { with(density) { 40.dp.toPx() } }
    val gestureScope = rememberCoroutineScope()

    // TV support
    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    val isFullscreenMode =
        SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.FULLSCREEN_PLAYER

    // Volume state from PlatformActions
    val maxSystemVolume = remember { platformActions.getMaxVolume() }
    var volumeOverlayProgress by remember {
        mutableFloatStateOf(platformActions.getCurrentVolume())
    }

    var brightnessOverlayProgress by remember {
        mutableFloatStateOf(platformActions.readScreenBrightness())
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
        val current = mediaController.currentPosition.value
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
        swipeSeekStartPosition = mediaController.currentPosition.value
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
        accumulatedSeek += deltaPx
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
        volumeOverlayProgress = platformActions.getCurrentVolume()
        showVolumeOverlay = true
        showBrightnessOverlay = false
        swipeSeekDismissJob?.cancel()
    }

    fun updateVolumeGesture(deltaY: Float) {
        if (!isChangingVolume) beginVolumeGesture()

        val deltaProgress = (-deltaY) / VERTICAL_SWIPE_NORMALIZER
        volumeOverlayProgress = (volumeOverlayProgress + deltaProgress).coerceIn(0f, 1f)
        platformActions.setVolume(volumeOverlayProgress)
    }


    fun endVolumeGesture() {
        isChangingVolume = false
        showVolumeOverlay = false
    }

    fun beginBrightnessGesture() {
        if (isChangingBrightness) return
        isChangingBrightness = true
        brightnessOverlayProgress = platformActions.readScreenBrightness()
        showBrightnessOverlay = true
        showVolumeOverlay = false
        swipeSeekDismissJob?.cancel()
    }

    fun updateBrightnessGesture(deltaY: Float) {
        if (!isChangingBrightness) beginBrightnessGesture()
        val deltaProgress = (-deltaY) / VERTICAL_SWIPE_NORMALIZER
        val newProgress = (brightnessOverlayProgress + deltaProgress).coerceIn(0f, 1f)
        brightnessOverlayProgress = newProgress
        platformActions.setScreenBrightness(newProgress)
        platformActions.saveScreenBrightness(newProgress)
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
            skipButtonJob?.cancel()
            skipButtonShownTime = 0L
            return
        }

        // Find segment at current position
        val currentSegment = sponsorBlockSegments.firstOrNull { segment ->
            position.toDouble() in segment.startTime..segment.endTime
        }
        if (currentSegment != null && !skippedSegments.contains(currentSegment.uuid)) {
            // Only show manual skip button (automatic skipping is handled by PlaybackService)
            if (SponsorBlockHelper.shouldShowSkipButton(currentSegment)) {
                // Check if 5 seconds have passed since button was first shown
                if (skipButtonShownTime == 0L || (System.currentTimeMillis() - skipButtonShownTime) < 5000) {
                    if (skipButtonShownTime == 0L) {
                        skipButtonShownTime = System.currentTimeMillis()
                    }
                    if (!showSkipButton) {
                        currentSegmentToSkip = currentSegment
                        showSkipButton = true
                    }
                } else {
                    // 5 seconds elapsed, hide and don't show again for this segment
                    showSkipButton = false
                }
            } else {
                currentSegmentToSkip = null
                showSkipButton = false
                skipButtonJob?.cancel()
            }
        } else {
            currentSegmentToSkip = null
            showSkipButton = false
            skipButtonJob?.cancel()
            skipButtonShownTime = 0L
        }
    }

    // Manual skip function
    fun skipCurrentSegment() {
        currentSegmentToSkip?.let { segment ->
            skipButtonJob?.cancel()
            skipButtonShownTime = 0L
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

    // Apply default resolution when tracks change
    LaunchedEffect(availableResolutions, defaultResolution) {
        if (!hasVideoOverride() && defaultResolution != "auto" && availableResolutions.isNotEmpty()) {
            mediaController.applyDefaultResolution(defaultResolution)
        }
    }

    val audioMode by SharedContext.playbackMode.collectAsState()

    // Register playback event callback
    DisposableEffect(mediaController) {
        val callback = object : PlaybackEventCallback {
            override fun onMediaItemTransition() {
                danmakuState.clear()
            }

            override fun onSeek() {
                danmakuState.onSeek()
                // User manual seek - clear skip button
                showSkipButton = false
                currentSegmentToSkip = null
            }

            override fun onAutoTransition() {
                danmakuState.clear()
                // Reset all SponsorBlock state on video transition
                skippedSegments = emptySet()
                lastSkippedSegment = null
                showUnskipButton = false
                showSkipButton = false
                currentSegmentToSkip = null
            }
        }

        mediaController.addPlaybackEventCallback(callback)

        // Keep screen on while playing
        if (isPlaying) {
            platformActions.setKeepScreenOn(true)
        }

        onDispose {
            mediaController.removePlaybackEventCallback(callback)
            skipButtonJob?.cancel()
            unskipButtonJob?.cancel()
            platformActions.setKeepScreenOn(false)
            // Restore system brightness when player is disposed
            platformActions.setScreenBrightness(-1f)
        }
    }

    // Keep screen on based on playing state
    LaunchedEffect(isPlaying) {
        platformActions.setKeepScreenOn(isPlaying)
    }

    LaunchedEffect(mediaController, sponsorBlockSegments) {
        while (true) {
            // Check for SponsorBlock segments
            checkCurrentSegment(currentPosition)
            delay(500)  // Check more frequently for better responsiveness
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val isSystemDark = isSystemInDarkTheme()

    LaunchedEffect(isControlsVisible, isFullscreenMode) {
        platformActions.setSystemBarsVisible(
            visible = isControlsVisible,
            isFullscreen = isFullscreenMode,
            colorScheme = colorScheme,
            isSystemDark = isSystemDark
        )
    }

    // Manage brightness based on fullscreen mode
    LaunchedEffect(isFullscreenMode, gestureSettings.brightnessGestureEnabled) {
        if (isFullscreenMode && gestureSettings.brightnessGestureEnabled) {
            // Entering fullscreen: restore saved brightness if available
            val savedBrightness = platformActions.getSavedScreenBrightness()
            if (savedBrightness >= 0f) {
                platformActions.setScreenBrightness(savedBrightness)
                brightnessOverlayProgress = savedBrightness
            }
        } else {
            // Exiting fullscreen or gesture disabled: restore system brightness
            platformActions.setScreenBrightness(-1f)
        }
    }

    // Determine if player is in "busy" state where auto-hide should be paused
    val isPlayerBusy = showResolutionMenu || showSpeedPitchDialog || showSleepTimerDialog ||
            showMoreMenu || showAudioLanguageMenu || showSubtitleMenu || isSeekBarDragging

    // Auto-hide controls after timeout when playing and not busy
    LaunchedEffect(isControlsVisible, isPlaying, isPlayerBusy) {
        if (isControlsVisible && isPlaying && !isPlayerBusy) {
            delay(DEFAULT_CONTROLS_HIDE_TIME)
            // Double check we're still not busy before hiding
            if (!showResolutionMenu && !showSpeedPitchDialog && !showSleepTimerDialog &&
                !showMoreMenu && !showAudioLanguageMenu && !showSubtitleMenu && !isSeekBarDragging) {
                isControlsVisible = false
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
                        if (SharedContext.settingsManager.getBoolean("start_main_player_fullscreen_key")) {
                            SharedContext.sharedVideoDetailViewModel.toggleFullscreenPlayer()
                        }
                        if (SharedContext.isTv) {
                            gestureScope.launch {
                                delay(100)
                                runCatching { playerFocusRequester.requestFocus() }
                            }
                        }
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
            val seekMs = remember {
                SharedContext.settingsManager.getString("seek_duration_key", "15000").toLong()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onSizeChanged { gestureContainerSize = it }
                    .then(
                        if (SharedContext.isTv) {
                            Modifier
                                .focusRequester(playerFocusRequester)
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.DirectionCenter, Key.Enter -> {
                                                if (!isControlsVisible) {
                                                    isControlsVisible = true
                                                    gestureScope.launch {
                                                        delay(100)
                                                        runCatching { playPauseFocusRequester.requestFocus() }
                                                    }
                                                } else {
                                                    // Toggle play/pause when controls visible
                                                    if (isPlaying) mediaController.pause() else mediaController.play()
                                                }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                if (!isControlsVisible) {
                                                    applySeekDelta(-seekMs)
                                                    true
                                                } else {
                                                    false // Let focus move to other controls
                                                }
                                            }
                                            Key.DirectionRight -> {
                                                if (!isControlsVisible) {
                                                    applySeekDelta(seekMs)
                                                    true
                                                } else {
                                                    false // Let focus move to other controls
                                                }
                                            }
                                            Key.MediaPlayPause -> {
                                                if (isPlaying) mediaController.pause() else mediaController.play()
                                                true
                                            }
                                            // Don't intercept up/down - let focus move to other elements
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        } else {
                            Modifier
                        }
                    )
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
                                    originalSpeed = mediaController.playbackSpeed.value
                                    val speedUpSpeed = originalSpeed * speedingPlaybackMultiplier
                                    mediaController.setPlaybackParameters(speedUpSpeed, mediaController.playbackPitch.value)
                                }
                            },
                            onPress = {
                                // 等待手指抬起
                                val released = tryAwaitRelease()
                                // 无论是否成功释放，如果正在长按加速状态，都恢复速度
                                if (isLongPressing) {
                                    isLongPressing = false
                                    mediaController.setPlaybackParameters(originalSpeed, mediaController.playbackPitch.value)
                                }
                            }
                        )
                    }

            ) {
                key(audioMode) {
                    VideoSurface(
                        modifier = Modifier.fillMaxSize(),
                        controller = mediaController,
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
                        duration = duration.coerceAtLeast(0),
                        bufferedPosition = bufferedPosition,
                        currentSpeed = currentSpeed,
                        currentPitch = currentPitch,
                        currentTimelineIndex = currentTimelineIndex,
                        timelineSize = timelineSize,
                        availableResolutions = availableResolutions,
                        availableLanguages = availableLanguages,
                        currentLanguage = currentAudioLanguage,
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
                            val currentItem = mediaController.currentMediaItem.value
                            currentItem?.let { item ->
                                SharedContext.sharedVideoDetailViewModel.loadVideoDetails(
                                    url = item.mediaId,
                                    serviceId = item.serviceId ?: 0,
                                    shouldDisableLoading = true,
                                    shouldKeepPlaybackMode = true
                                )
                            }
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
                            mediaController.selectResolution(resolution)
                        },
                        onResolutionAuto = {
                            mediaController.clearResolutionOverride()
                        },
                        onSpeedPitchClick = {
                            showSpeedPitchDialog = true
                        },
                        onAudioLanguageSelected = { language ->
                            mediaController.selectAudioLanguage(language)
                        },
                        onSubtitleSelected = { subtitle ->
                            mediaController.selectSubtitle(subtitle)
                        },
                        onSubtitleDisabled = {
                            mediaController.disableSubtitles()
                        },
                        onToggleDanmaku = onToggleDanmaku,
                        onSkipSegment = ::skipCurrentSegment,
                        onUnskipSegment = ::unskipLastSegment
                    )

                    // Use PlayerControl component
                    PlayerControl(
                        streamInfo = streamInfo,
                        state = playerControlState,
                        callbacks = playerControlCallbacks,
                        isFullscreenMode = isFullscreenMode,
                        danmakuEnabled = danmakuEnabled,
                        danmakuState = danmakuState,
                        controlsTransition = controlsTransition,
                        showResolutionMenu = showResolutionMenu,
                        onResolutionMenuChange = { showResolutionMenu = it },
                        showMoreMenu = showMoreMenu,
                        onMoreMenuChange = { showMoreMenu = it },
                        showAudioLanguageMenu = showAudioLanguageMenu,
                        onAudioLanguageMenuChange = { showAudioLanguageMenu = it },
                        showSubtitleMenu = showSubtitleMenu,
                        onSubtitleMenuChange = { showSubtitleMenu = it },
                        onSleepTimerDialogChange = { showSleepTimerDialog = it },
                        onPipClick = {
                            platformActions.enterPictureInPicture(streamInfo)
                        },
                        onSeekBarDraggingChange = { isSeekBarDragging = it },
                        playPauseFocusRequester = if (SharedContext.isTv) playPauseFocusRequester else null
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
                    // Apply speed and pitch to the media controller
                    mediaController.setPlaybackParameters(speed, pitch)

                    // Save to preferences for persistence across app restarts
                    SharedContext.settingsManager.putFloat("playback_speed_key", speed)
                    SharedContext.settingsManager.putFloat("playback_pitch_key", pitch)
                }
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepTimerDialog = false },
                onConfirm = { minutes ->
                    platformActions.startSleepTimer(minutes)
                }
            )
        }
    }
}
