package project.pipepipe.app.ui.component.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SpatialAudioOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import project.pipepipe.app.ui.component.player.PlayerHelper.ResolutionInfo
import project.pipepipe.app.ui.component.player.PlayerHelper.SubtitleInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.utils.toDurationString

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
    val sponsorBlockSegments: List<SponsorBlockSegmentInfo>,
    val currentSegmentToSkip: SponsorBlockSegmentInfo?,
    val lastSkippedSegment: SponsorBlockSegmentInfo?,
    val showSkipButton: Boolean,
    val showUnskipButton: Boolean
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
    val onToggleDanmaku: () -> Unit,
    val onSkipSegment: () -> Unit,
    val onUnskipSegment: () -> Unit
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
    modifier: Modifier = Modifier,
    playPauseFocusRequester: FocusRequester? = null
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Semi-transparent background when controls are visible
        if (controlsTransition.targetState) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        // SponsorBlock Skip button (right side)
        AnimatedVisibility(
            visible = state.showSkipButton && state.currentSegmentToSkip != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = callbacks.onSkipSegment,
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
        AnimatedVisibility(
            visible = state.showUnskipButton && state.lastSkippedSegment != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            FloatingActionButton(
                onClick = callbacks.onUnskipSegment,
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

                    VideoProgressBar(
                        currentPosition = state.currentPosition,
                        duration = state.duration,
                        bufferedPosition = state.bufferedPosition,
                        onSeek = callbacks.onSeek,
                        sponsorBlockSegments = state.sponsorBlockSegments,
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

@Composable
private fun ResolutionMenu(
    availableResolutions: List<ResolutionInfo>,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onResolutionSelected: (ResolutionInfo) -> Unit,
    onResolutionAuto: () -> Unit
) {
    fun hasVideoOverride(): Boolean = availableResolutions.count { it.isSelected } == 1

    Box {
        TextButton(onClick = { onMenuChange(true) }) {
            Text(
                text = if (hasVideoOverride()) availableResolutions.first { it.isSelected }.displayLabel else stringResource(
                    MR.strings.auto
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onMenuChange(false) }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(MR.strings.auto),
                        color = if (!hasVideoOverride())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onResolutionAuto()
                    onMenuChange(false)
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
                        onResolutionSelected(resolution)
                        onMenuChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(
    streamInfo: StreamInfo,
    danmakuEnabled: Boolean,
    availableSubtitles: List<SubtitleInfo>,
    availableLanguages: Set<Pair<String, Boolean>>,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    showAudioLanguageMenu: Boolean,
    onAudioLanguageMenuChange: (Boolean) -> Unit,
    showSubtitleMenu: Boolean,
    onSubtitleMenuChange: (Boolean) -> Unit,
    currentLanguage: String,
    onToggleDanmaku: () -> Unit,
    onAudioLanguageSelected: (String) -> Unit,
    onSubtitleSelected: (SubtitleInfo) -> Unit,
    onSubtitleDisabled: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onPipClick: () -> Unit
) {
    Box {
        TextButton(onClick = { onMenuChange(true) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { onMenuChange(false) }
        ) {
            // Picture-in-Picture
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(MR.strings.pip))
                    }
                },
                onClick = {
                    onPipClick()
                    onMenuChange(false)
                }
            )

            // Play Queue
            DropdownMenuItem(
                modifier = Modifier.height(44.dp),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(MR.strings.play_queue))
                    }
                },
                onClick = {
                    SharedContext.toggleShowPlayQueueVisibility()
                    onMenuChange(false)
                }
            )

            // Danmaku toggle
            streamInfo.danmakuUrl?.let {
                DropdownMenuItem(
                    modifier = Modifier.height(44.dp),
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (!danmakuEnabled) {
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
                        onMenuChange(false)
                    }
                )
            }

            // Subtitles
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
                            Text(stringResource(MR.strings.caption_setting_title))
                        }
                    },
                    onClick = {
                        onMenuChange(false)
                        onSubtitleMenuChange(true)
                    }
                )
            }

            // Audio language
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
                        onMenuChange(false)
                        onAudioLanguageMenuChange(true)
                    }
                )
            }

            // Sleep timer
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
                onClick = onSleepTimerClick
            )
        }

        // Audio Language Menu
        DropdownMenu(
            expanded = showAudioLanguageMenu,
            onDismissRequest = { onAudioLanguageMenuChange(false) }
        ) {
            val originText = stringResource(MR.strings.original)
            availableLanguages.forEach { language ->
                // Get localized language name using Locale
                val languageCode = language.first
                val locale = java.util.Locale.forLanguageTag(languageCode)
                val localizedName = locale.getDisplayLanguage(java.util.Locale.getDefault())
                val displayText = if (localizedName.isNotBlank()) {
                    if (language.second) {
                        "$localizedName ($originText)"
                    } else {
                        localizedName
                    }
                } else {
                    if (language.second) {
                        "$languageCode ($originText)"
                    } else {
                        languageCode
                    }
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayText,
                            color = if (currentLanguage == languageCode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onAudioLanguageSelected(languageCode)
                        onAudioLanguageMenuChange(false)
                    }
                )
            }
        }

        // Subtitle Menu
        DropdownMenu(
            expanded = showSubtitleMenu,
            onDismissRequest = { onSubtitleMenuChange(false) }
        ) {
            // Disable option
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
                    onSubtitleDisabled()
                    onSubtitleMenuChange(false)
                }
            )

            // Available subtitles
            val autoGeneratedText = stringResource(MR.strings.player_subtitle_auto_generated)
            availableSubtitles.forEach { subtitle ->
                // Get localized language name using Locale
                val locale = java.util.Locale.forLanguageTag(subtitle.language)
                val localizedName = locale.getDisplayLanguage(java.util.Locale.getDefault())
                val displayText = if (localizedName.isNotBlank()) {
                    if (subtitle.isAutoGenerated) {
                        "$localizedName ($autoGeneratedText)"
                    } else {
                        localizedName
                    }
                } else {
                    if (subtitle.isAutoGenerated) {
                        "${subtitle.language} ($autoGeneratedText)"
                    } else {
                        subtitle.language
                    }
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayText,
                            color = if (subtitle.isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onSubtitleSelected(subtitle)
                        onSubtitleMenuChange(false)
                    }
                )
            }
        }
    }
}
