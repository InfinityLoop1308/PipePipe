package project.pipepipe.app.ui.screens

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import project.pipepipe.app.service.PlaybackService

import project.pipepipe.app.SharedContext
import project.pipepipe.shared.toText
import project.pipepipe.app.ui.component.CustomTopBar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayQueueScreen() {
	val context = LocalContext.current
	var _mediaController by remember { mutableStateOf<MediaController?>(null) }
	var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

	LaunchedEffect(Unit) {
		val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
		controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

		controllerFuture?.addListener({
			_mediaController = controllerFuture?.get()
		}, MoreExecutors.directExecutor())
	}

	DisposableEffect(Unit) {
		onDispose {
			controllerFuture?.let { future ->
				MediaController.releaseFuture(future)
			}
		}
	}

	val mediaController = _mediaController
	if (mediaController == null) {
		Box(
			modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
			contentAlignment = Alignment.Center
		) {
			CircularProgressIndicator()
		}
		return
	}

	var timeline by remember { mutableStateOf(mediaController.currentTimeline) }
	var currentMediaItemIndex by remember { mutableStateOf(mediaController.currentMediaItemIndex) }
	var isPlaying by remember { mutableStateOf(mediaController.isPlaying) }
	var currentPosition by remember { mutableStateOf(0L) }
	var duration by remember { mutableStateOf(0L) }
	var repeatMode by remember { mutableStateOf(mediaController.repeatMode) }
	var shuffleModeEnabled by remember { mutableStateOf(mediaController.shuffleModeEnabled) }

    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        if (currentMediaItemIndex == from.index) {
            currentMediaItemIndex = to.index
        }
        mediaController.moveMediaItem(from.index, to.index)
    }

    LaunchedEffect(Unit) {
        if (currentMediaItemIndex >= 0 && currentMediaItemIndex < timeline.windowCount) {
            listState.scrollToItem(currentMediaItemIndex)  // 使用 scrollToItem 立即滚动，不带动画
        }
    }


    val listener = object : Player.Listener {
		override fun onTimelineChanged(newTimeline: androidx.media3.common.Timeline, reason: Int) {
			timeline = newTimeline
		}

		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			currentMediaItemIndex = mediaController.currentMediaItemIndex
		}

		override fun onIsPlayingChanged(newIsPlaying: Boolean) {
			isPlaying = newIsPlaying
		}

		override fun onRepeatModeChanged(newRepeatMode: Int) {
			repeatMode = newRepeatMode
		}

		override fun onShuffleModeEnabledChanged(newShuffleModeEnabled: Boolean) {
			shuffleModeEnabled = newShuffleModeEnabled
		}
	}

	DisposableEffect(mediaController) {
		mediaController.addListener(listener)
		onDispose {
			mediaController.removeListener(listener)
		}
	}

	LaunchedEffect(mediaController) {
		while (true) {
			currentPosition = mediaController.currentPosition
			duration = mediaController.duration
			kotlinx.coroutines.delay(1000)
		}
	}

	BackHandler {
		SharedContext.toggleShowPlayQueueVisibility()
	}

	Column(
		modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
	) {
		@OptIn(ExperimentalMaterial3Api::class)
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.play_queue),
            defaultNavigationOnClick = { SharedContext.toggleShowPlayQueueVisibility() }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            itemsIndexed(
                items = (0 until timeline.windowCount).map { index ->
                    timeline.getWindow(index, androidx.media3.common.Timeline.Window())
                },
                key = {_,item -> item.mediaItem.mediaId }
            ) { index, window ->
                ReorderableItem(reorderableLazyListState, key = window.mediaItem.mediaId) { isDragging ->
                    val interactionSource = remember { MutableInteractionSource() }
                    PlayQueueItem(
                        mediaItem = window.mediaItem,
                        isCurrentItem = index == currentMediaItemIndex,
                        onItemClick = {
                            mediaController.play()
                        },
                        dragHandleModifier = Modifier.draggableHandle(
                            interactionSource = interactionSource
                        )
                    )
                }
            }
        }

		PlayQueueFooter(
			isPlaying = isPlaying,
			currentPosition = currentPosition,
			duration = duration,
			repeatMode = repeatMode,
			shuffleModeEnabled = shuffleModeEnabled,
			currentMediaItem = if (currentMediaItemIndex >= 0 && currentMediaItemIndex < timeline.windowCount) {
				timeline.getWindow(currentMediaItemIndex, androidx.media3.common.Timeline.Window()).mediaItem
			} else null,
			onPlayPause = {
				if (isPlaying) mediaController.pause() else mediaController.play()
			},
			onSeek = { position ->
				mediaController.seekTo(position)
			},
			onPrevious = { mediaController.seekToPreviousMediaItem() },
			onNext = { mediaController.seekToNextMediaItem() },
			onRewind = { mediaController.seekTo(maxOf(0, currentPosition - 10000)) },
			onFastForward = { mediaController.seekTo(minOf(duration, currentPosition + 10000)) },
			onRepeatMode = {
				val newMode = when (repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
					else -> Player.REPEAT_MODE_OFF
				}
				mediaController.repeatMode = newMode
			},
			onShuffle = {
				mediaController.shuffleModeEnabled = !shuffleModeEnabled
			}
		)
	}
}

@Composable
fun PlayQueueItem(
    mediaItem: MediaItem,
    isCurrentItem: Boolean,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier
) {
	Card(
		modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
		colors = CardDefaults.cardColors(
			containerColor = if (isCurrentItem)
				MaterialTheme.colorScheme.primaryContainer
			else
				MaterialTheme.colorScheme.surface
		)
	) {
		Row(
			modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
            Box() {
                AsyncImage(
                    model = mediaItem.mediaMetadata.artworkUri,
                    contentDescription = stringResource(MR.strings.thumbnail),
                    modifier = Modifier
                        .height(45.dp)
                        .width(80.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                if (mediaItem.mediaMetadata.durationMs != null) {
                    Text(
                        text = mediaItem.mediaMetadata.durationMs!!.toText(true),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0x99000000), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 0.dp)
                    )
                }
            }


			Spacer(modifier = Modifier.width(8.dp))

			Column(
				modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
			) {
				Text(
					text = mediaItem.mediaMetadata.title?.toString() ?: stringResource(MR.strings.unknown_title),
					style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
					maxLines = 1,
                    overflow = TextOverflow.Ellipsis
				)

				mediaItem.mediaMetadata.artist?.let { artist ->
					Text(
						text = artist.toString(),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}

            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(MR.strings.detail_drag_description),
                modifier = dragHandleModifier
                    .align(Alignment.CenterVertically)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
		}
	}
}

@Composable
fun PlayQueueFooter(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    currentMediaItem: MediaItem?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onRepeatMode: () -> Unit,
    onShuffle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            currentMediaItem?.let { mediaItem ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = mediaItem.mediaMetadata.title?.toString() ?: stringResource(MR.strings.unknown_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )

                        mediaItem.mediaMetadata.artist?.let { artist ->
                            Text(
                                text = artist.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            var sliderPosition by remember { mutableFloatStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }

            LaunchedEffect(currentPosition, isDragging) {
                if (!isDragging && duration > 0) {
                    sliderPosition = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
            }

            // 将时间文本和 Slider 放在同一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = currentPosition.toText(true),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Slider(
                    value = sliderPosition,
                    onValueChange = { value ->
                        isDragging = true
                        sliderPosition = value
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        if (duration > 0) {
                            onSeek((sliderPosition * duration).toLong())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                if (duration > 0) {
                    Text(
                        text = duration.toText(true),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRepeatMode) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = stringResource(MR.strings.repeat_mode),
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(MR.strings.previous),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onRewind) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = stringResource(MR.strings.rewind),
                        modifier = Modifier.size(28.dp)
                    )
                }

                FloatingActionButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(MR.strings.pause) else stringResource(MR.strings.player_play),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onFastForward) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = stringResource(MR.strings.player_fast_forward),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(MR.strings.next),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(MR.strings.notification_action_shuffle),
                        tint = if (shuffleModeEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
