package project.pipepipe.app.ui.component.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View.GONE
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil3.ImageLoader
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.getFrameBoundsAt
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.shared.infoitem.DanmakuInfo
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory
import project.pipepipe.shared.infoitem.helper.stream.Frameset
import androidx.compose.ui.Alignment as PopupAlignment

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
                val resizeMode = SharedContext.settingsManager.getInt("last_resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT)
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    player = mediaController
                    subtitleView?.visibility = GONE
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                playerView.player = mediaController
                val resizeMode = SharedContext.settingsManager.getInt("last_resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT)
                playerView.resizeMode = resizeMode
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
    sponsorBlockSegments: List<SponsorBlockSegmentInfo> = emptyList(),
    previewFrames: List<Frameset>? = null
) {
    val context = LocalPlatformContext.current

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var frameset: Frameset? by remember { mutableStateOf(null) }

    // Cache for loaded storyboard images, recreated when previewFrames changes
    val loadedStoryboards = remember(previewFrames) { mutableStateMapOf<Int, Bitmap>() }

    // Preload all storyboard images
    LaunchedEffect(previewFrames) {
        if (previewFrames.isNullOrEmpty()) {
            frameset = null
            return@LaunchedEffect
        }
        frameset = previewFrames.maxBy { it.frameWidth * it.frameHeight }
        frameset!!.urls.forEachIndexed { index, url ->
            launch(Dispatchers.IO) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .maxBitmapSize(coil3.size.Size(8192,8192)) // must have to avoid cut
                        .build()
                    val result = imageLoader.execute(request)
                    result.image?.let { image ->
                        val bitmap = (image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                        bitmap?.let { loadedStoryboards[index] = it }
                    }
                } catch (e: Exception) {
                    // Silently ignore loading failures
                }
            }
        }
    }

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
                        dragOffsetX = offset.x
                    },
                    onDragEnd = {
                        onSeek(dragPosition.coerceIn(0L, duration))
                        isDragging = false
                    }
                ) { change, dragAmount ->
                    val newPosition = dragPosition + (dragAmount.x / size.width * duration).toLong()
                    dragPosition = newPosition.coerceIn(0L, duration)
                    dragOffsetX = change.position.x
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

        // Preview card when dragging - always show timestamp
        if (isDragging && duration > 0) {
            val bounds = frameset?.getFrameBoundsAt(dragPosition)
            val storyboardIndex = bounds?.get(0)
            val storyboardBitmap = storyboardIndex?.let { loadedStoryboards[it] }

            Popup(
                alignment = PopupAlignment.BottomStart,
                offset = IntOffset(dragOffsetX.toInt(), (-64))
            ) {
                VideoPreviewCard(
                    position = dragPosition,
                    frameset = frameset,
                    previewBitmap = storyboardBitmap,
                    cropLeft = bounds?.get(1) ?: 0,
                    cropTop = bounds?.get(2) ?: 0,
                )
            }
        }
    }
}

@Composable
private fun getSponsorBlockColor(category: SponsorBlockCategory): Color {
    return SponsorBlockUtils.getCategoryColor(category)
}

/**
 * Preview card showing thumbnail and timestamp when scrubbing the progress bar
 * Note: This is designed to be used inside a Popup for proper positioning
 */
@Composable
fun VideoPreviewCard(
    position: Long,
    previewBitmap: Bitmap?,
    frameset: Frameset?,
    cropLeft: Int,
    cropTop: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(
                if (frameset != null) {
                    val previewWidth = 120.dp
                    Modifier.width(previewWidth)
                } else {
                    Modifier.wrapContentWidth()
                }
            )
            .background(
                color = Color.Black.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Always show timestamp
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = position.toDurationString(true),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (frameset == null) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        // Only show thumbnail section if frameset exists
        frameset?.let {
            val previewWidth = 120.dp
            val previewHeight = (it.frameHeight.toFloat() / it.frameWidth.toFloat() * 120).dp

            if (previewBitmap != null) {
                val painter = remember(previewBitmap, cropLeft, cropTop, it) {
                    CroppedBitmapPainter(
                        previewBitmap,
                        cropLeft,
                        cropTop,
                        it.frameWidth,
                        it.frameHeight
                    )
                }

                Image(
                    painter = painter,
                    contentDescription = "Video preview at ${position.toDurationString(true)}",
                    modifier = Modifier
                        .width(previewWidth)
                        .height(previewHeight)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Placeholder when bitmap is not loaded yet
                Box(
                    modifier = Modifier
                        .width(previewWidth)
                        .height(previewHeight)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}


/**
 * Custom Painter that draws a cropped region from a Bitmap
 */
private class CroppedBitmapPainter(
    private val bitmap: Bitmap,
    private val cropLeft: Int,
    private val cropTop: Int,
    private val cropWidth: Int,
    private val cropHeight: Int
) : Painter() {

    private val imageBitmap = bitmap.asImageBitmap()

    override val intrinsicSize: Size
        get() = Size(cropWidth.toFloat(), cropHeight.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(cropLeft, cropTop),
            srcSize = IntSize(cropWidth, cropHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )
    }
}