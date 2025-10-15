package project.pipepipe.app.ui.component.player

import android.annotation.SuppressLint
import android.view.View.GONE
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory

@Composable
fun rememberPlaybackTimeMs(player: Player): State<Long> {
    return produceState(initialValue = player.contentPosition, key1 = player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                    events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                    value = player.contentPosition
                }
            }
        }
        player.addListener(listener)
        try {
            while (true) {
                value = player.contentPosition
                withFrameNanos { }   // 等下一帧
            }
        } finally {
            player.removeListener(listener)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    mediaController: MediaController,
    danmakuState: DanmakuState,
    danmakuPool: List<DanmakuInfo>? = null,
    danmakuEnabled: Boolean = true,
) {
    var currentCues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    val playbackTimeMs by rememberPlaybackTimeMs(mediaController)

    DisposableEffect(mediaController, danmakuEnabled) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                currentCues = cueGroup.cues
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                danmakuState.updatePlaybackSpeed(playbackParameters.speed)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                danmakuState.setPlaying(isPlaying && danmakuEnabled)
            }
        }

        mediaController.addListener(listener)
        danmakuState.updatePlaybackSpeed(mediaController.playbackParameters.speed)
        danmakuState.setPlaying(mediaController.isPlaying && danmakuEnabled)

        onDispose {
            mediaController.removeListener(listener)
            danmakuState.setPlaying(true)
        }
    }

    LaunchedEffect(danmakuEnabled, mediaController.isPlaying) {
        danmakuState.setPlaying(mediaController.isPlaying && danmakuEnabled)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = mediaController
                    subtitleView?.visibility = GONE
                }
            },
            update = { playerView ->
                playerView.player = mediaController
                playerView.keepScreenOn = mediaController.isPlaying
            },
            modifier = Modifier.fillMaxSize()
        )

        AndroidView(
            factory = { context ->
                SubtitleView(context).apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
                    setPadding(16, 0, 16, 16)
                }
            },
            update = { subtitleView ->
                val modifiedCues = currentCues.map { originalCue ->
                    originalCue.buildUpon()
                        .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
                        .build()
                }
                subtitleView.setCues(modifiedCues)
            }
        )

        if (danmakuPool != null) {
            DanmakuOverlay(
                modifier = Modifier.fillMaxSize(),
                state = danmakuState,
                danmakuPool = danmakuPool,
                playbackTimeMs = playbackTimeMs,
                enabled = danmakuEnabled
            )
        }
    }
}
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VideoProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    sponsorBlockSegments: List<SponsorBlockSegmentInfo> = emptyList()
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    val progress = if (isDragging) {
        dragPosition.toFloat() / duration.toFloat()
    } else {
        currentPosition.toFloat() / duration.toFloat()
    }.coerceIn(0f, 1f)

    val bufferedProgress = (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newPosition = (offset.x / size.width * duration).toLong()
                    onSeek(newPosition.coerceIn(0L, duration))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragPosition = (offset.x / size.width * duration).toLong()
                    },
                    onDragEnd = {
                        onSeek(dragPosition.coerceIn(0L, duration))
                        isDragging = false
                    }
                ) { _, dragAmount ->
                    val newPosition = dragPosition + (dragAmount.x / size.width * duration).toLong()
                    dragPosition = newPosition.coerceIn(0L, duration)
                }
            }
    ) {
        val boxWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)

        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
            )

            // Buffered progress
            if (bufferedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedProgress)
                        .fillMaxHeight()
                        .background(
                            Color.White.copy(alpha = 0.5f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            if (duration > 0 && sponsorBlockSegments.isNotEmpty()) {
                sponsorBlockSegments.forEach { segment ->
                    val segmentStart = (segment.startTime / duration.toFloat()).coerceIn(0.0, 1.0)
                    val segmentEnd = (segment.endTime / duration.toFloat()).coerceIn(0.0, 1.0)
                    val segmentWidth: Double = segmentEnd - segmentStart

                    if (segmentWidth > 0f) {
                        val segmentColor = getSponsorBlockColor(segment.category)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segmentWidth.toFloat())
                                .fillMaxHeight()
                                .offset(x = segmentStart * boxWidth)
                                .background(
                                    segmentColor.copy(alpha = 0.7f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }

            // Current progress
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(2.dp))
                )
            }

            // Thumb (only when dragging)
            if (isDragging && progress > 0f) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = progress * (boxWidth - 12.dp))
                        .background(Color.White, CircleShape)
                        .align(Alignment.CenterStart)
                )
            }
        }
    }
}

@Composable
private fun getSponsorBlockColor(category: SponsorBlockCategory): Color {
    return SponsorBlockUtils.getCategoryColor(category)
}
