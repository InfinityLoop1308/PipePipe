package project.pipepipe.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.platform.ResolutionInfo
import project.pipepipe.app.platform.SubtitleInfo
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import java.util.*

/**
 * Data class to hold all player control state
 */
data class PlayerControlState(
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val bufferedPosition: Long,
    val currentSpeed: Float,
    val currentPitch: Float,
    val currentTimelineIndex: Int,
    val timelineSize: Int,
    val availableResolutions: List<ResolutionInfo>,
    val availableLanguages: Set<Pair<String, Boolean>>,
    val currentLanguage: String,
    val availableSubtitles: List<SubtitleInfo>,
    val isLongPressing: Boolean,
    val originalSpeed: Float,
    val speedingPlaybackMultiplier: Float,
    val isLoading: Boolean,
    val showVolumeOverlay: Boolean,
    val volumeProgress: Float,
    val showBrightnessOverlay: Boolean,
    val brightnessProgress: Float,
    val doubleTapOverlayState: DoubleTapOverlayState?,
    val swipeSeekState: SwipeSeekUiState?,
)

/**
 * Callbacks for player control actions
 */
data class PlayerControlCallbacks(
    val onPlayPauseClick: () -> Unit,
    val onSeekToPrevious: () -> Unit,
    val onSeekToNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onFullScreenClick: () -> Unit,
    val onClose: () -> Unit,
    val onResolutionSelected: (ResolutionInfo) -> Unit,
    val onResolutionAuto: () -> Unit,
    val onSpeedPitchClick: () -> Unit,
    val onAudioLanguageSelected: (String) -> Unit,
    val onSubtitleSelected: (SubtitleInfo) -> Unit,
    val onSubtitleDisabled: () -> Unit,
    val onToggleDanmaku: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerControl(
    streamInfo: StreamInfo,
    state: PlayerControlState,
    callbacks: PlayerControlCallbacks,
    isFullscreenMode: Boolean,
    danmakuEnabled: Boolean,
    danmakuState: DanmakuState,
    controlsTransition: MutableTransitionState<Boolean>,
    showResolutionMenu: Boolean,
    onResolutionMenuChange: (Boolean) -> Unit,
    showMoreMenu: Boolean,
    onMoreMenuChange: (Boolean) -> Unit,
    showAudioLanguageMenu: Boolean,
    onAudioLanguageMenuChange: (Boolean) -> Unit,
    showSubtitleMenu: Boolean,
    onSubtitleMenuChange: (Boolean) -> Unit,
    onSleepTimerDialogChange: (Boolean) -> Unit,
    onPipClick: () -> Unit,
    onSeekBarDraggingChange: (Boolean) -> Unit = {},
    playPauseFocusRequester: FocusRequester? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (controlsTransition.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        Box(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxSize()
        ) {
            // SponsorBlock Skip button (right side)
            val showSkipButton by SharedContext.sponsorBlockManager.showSkipButton.collectAsState()
            val currentSegment by SharedContext.sponsorBlockManager.currentSegment.collectAsState()
            AnimatedVisibility(
                visible = showSkipButton && currentSegment != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { currentSegment?.let { SharedContext.sponsorBlockManager.skipSegment(it, true) } },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(MR.strings.sponsor_block_manual_skip_button),
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
            val showUnskipButton by SharedContext.sponsorBlockManager.showUnskipButton.collectAsState()
            AnimatedVisibility(
                visible = showUnskipButton,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { SharedContext.sponsorBlockManager.unskipLastSegment() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
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
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBarsIgnoringVisibility
                                .only(WindowInsetsSides.Horizontal)
                        )
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // Top controls
                    Column(
                        modifier = Modifier.then(
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
                                    IconButton(onClick = callbacks.onClose) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(MR.strings.close),
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

                            // Right side buttons
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy((-6).dp)
                            ) {
                                // Resolution button
                                if (state.availableResolutions.isNotEmpty()) {
                                    ResolutionMenu(
                                        availableResolutions = state.availableResolutions,
                                        showMenu = showResolutionMenu,
                                        onMenuChange = onResolutionMenuChange,
                                        onResolutionSelected = callbacks.onResolutionSelected,
                                        onResolutionAuto = callbacks.onResolutionAuto
                                    )
                                }

                                // Speed button
                                TextButton(onClick = callbacks.onSpeedPitchClick) {
                                    Text(
                                        text = if (state.currentSpeed == 1f) "1x" else String.format(
                                            "%.1fx",
                                            state.currentSpeed
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // More menu
                                MoreMenu(
                                    streamInfo = streamInfo,
                                    danmakuEnabled = danmakuEnabled,
                                    availableSubtitles = state.availableSubtitles,
                                    availableLanguages = state.availableLanguages,
                                    showMenu = showMoreMenu,
                                    onMenuChange = onMoreMenuChange,
                                    showAudioLanguageMenu = showAudioLanguageMenu,
                                    onAudioLanguageMenuChange = onAudioLanguageMenuChange,
                                    showSubtitleMenu = showSubtitleMenu,
                                    onSubtitleMenuChange = onSubtitleMenuChange,
                                    currentLanguage = state.currentLanguage,
                                    onToggleDanmaku = {
                                        callbacks.onToggleDanmaku()
                                        if (!danmakuEnabled) {
                                            danmakuState.clear()
                                        }
                                    },
                                    onAudioLanguageSelected = callbacks.onAudioLanguageSelected,
                                    onSubtitleSelected = callbacks.onSubtitleSelected,
                                    onSubtitleDisabled = callbacks.onSubtitleDisabled,
                                    onSleepTimerClick = {
                                        onMoreMenuChange(false)
                                        onSleepTimerDialogChange(true)
                                    },
                                    onPipClick = onPipClick
                                )
                            }
                        }
                    }

                    // Center Play Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.timelineSize > 1 && state.currentTimelineIndex < state.timelineSize - 1) {
                            IconButton(
                                onClick = callbacks.onSeekToPrevious,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(MR.strings.previous),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = callbacks.onPlayPauseClick,
                            modifier = Modifier
                                .size(60.dp)
                                .then(
                                    if (playPauseFocusRequester != null) {
                                        Modifier.focusRequester(playPauseFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) stringResource(MR.strings.pause) else stringResource(
                                    MR.strings.player_play
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        if (state.timelineSize > 1 && state.currentTimelineIndex < state.timelineSize - 1) {
                            IconButton(
                                onClick = callbacks.onSeekToNext,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = stringResource(MR.strings.next),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    // Bottom controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.currentPosition.toDurationString(true),
                            color = Color.White,
                            fontSize = 14.sp
                        )

                        val currentSegments by SharedContext.sponsorBlockManager.currentSegments.collectAsState()

                        VideoProgressBar(
                            currentPosition = state.currentPosition,
                            duration = state.duration,
                            bufferedPosition = state.bufferedPosition,
                            onSeek = callbacks.onSeek,
                            sponsorBlockSegments = currentSegments,
                            previewFrames = streamInfo.previewFrames,
                            onDraggingChange = onSeekBarDraggingChange,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = state.duration.toDurationString(true),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = callbacks.onFullScreenClick) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = stringResource(MR.strings.player_rotate_screen),
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Long press speed indicator
            if (state.isLongPressing && state.speedingPlaybackMultiplier != 1f) {
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
                            text = "${
                                String.format(
                                    "%.1f",
                                    state.originalSpeed * state.speedingPlaybackMultiplier
                                )
                            }x",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Overlays (volume, brightness, double-tap, seek)
            PlayerOverlays(
                isLoading = state.isLoading,
                showVolumeOverlay = state.showVolumeOverlay,
                volumeProgress = state.volumeProgress,
                showBrightnessOverlay = state.showBrightnessOverlay,
                brightnessProgress = state.brightnessProgress,
                doubleTapOverlayState = state.doubleTapOverlayState,
                swipeSeekState = state.swipeSeekState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

