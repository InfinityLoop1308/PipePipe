package project.pipepipe.app.ui.screens

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.BottomSheetMenu
import project.pipepipe.app.ui.component.PlaylistSelectorPopup
import project.pipepipe.app.ui.component.player.SpeedPitchDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.navigation.compose.rememberNavController
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import androidx.media3.common.PlaybackParameters
import project.pipepipe.app.service.SleepTimerService
import project.pipepipe.app.ui.component.player.SleepTimerDialog
import project.pipepipe.app.ui.theme.onCustomTopBarColor

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayQueueScreen() {
	val context = LocalContext.current
	var _mediaController by remember { mutableStateOf<MediaController?>(null) }
	var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

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
	var currentSpeed by remember { mutableFloatStateOf(mediaController.playbackParameters.speed) }
	var currentPitch by remember { mutableFloatStateOf(mediaController.playbackParameters.pitch) }
	var showPlaylistSelector by remember { mutableStateOf(false) }
	var showSpeedPitchDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
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
			// 同步更新 currentMediaItemIndex，因为拖拽重排后 index 可能变化
			// 但 onMediaItemTransition 不会被触发（播放的还是同一个 media item）
			currentMediaItemIndex = mediaController.currentMediaItemIndex
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

		override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
			currentSpeed = playbackParameters.speed
			currentPitch = playbackParameters.pitch
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
            defaultNavigationOnClick = { SharedContext.toggleShowPlayQueueVisibility() },

            actions = {
                IconButton(
                    onClick = {
                        showPlaylistSelector = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(MR.strings.add_to_playlist)
                    )
                }
                TextButton(
                    onClick = { showSpeedPitchDialog = true },
                    modifier = Modifier.width(44.dp)
                ) {
                    Text(
                        text = if (currentSpeed == 1f) "1x" else String.format("%.1fx", currentSpeed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onCustomTopBarColor()
                    )
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(MR.strings.playlist_action_more),
                            tint = onCustomTopBarColor()
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
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
                                showSleepTimerDialog = true
                            }
                        )
                    }
                }
            }
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
                key = { index, item -> "${item.uid}_${item.mediaItem.mediaId}"}
            ) { index, window ->
                ReorderableItem(reorderableLazyListState, key = "${window.uid}_${window.mediaItem.mediaId}") { isDragging ->
                    val interactionSource = remember { MutableInteractionSource() }
                    PlayQueueItem(
                        mediaItem = window.mediaItem,
                        isCurrentItem = index == currentMediaItemIndex,
                        onItemClick = {
                            mediaController.seekToDefaultPosition(index)
                            mediaController.play()
                        },
                        onItemLongClick = {
                            val streamInfo = StreamInfo(
                                serviceId = window.mediaItem.mediaMetadata.extras!!.getInt("KEY_SERVICE_ID")!!,
                                url = window.mediaItem.mediaId,
                                name = window.mediaItem.mediaMetadata.title?.toString() ?: "",
                                thumbnailUrl = window.mediaItem.mediaMetadata.artworkUri?.toString(),
                                uploaderName = window.mediaItem.mediaMetadata.artist?.toString(),
                                duration = window.mediaItem.mediaMetadata.durationMs ?: 0
                            )
                            SharedContext.bottomSheetMenuViewModel.show(
                                StreamInfoWithCallback(streamInfo,
                                    onNavigateTo = null,
                                    onDelete = null,
                                    disablePlayOperations = true,
                                    showProvideDetailButton = true
                                )
                            )
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
			onPrevious = { mediaController.seekToPrevious() },
			onNext = { mediaController.seekToNext() },
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

		// Playlist Selector Dialog
		if (showPlaylistSelector) {
			val queueStreams = (0 until timeline.windowCount).mapNotNull { index ->
				val window = timeline.getWindow(index, androidx.media3.common.Timeline.Window())
				try {
					StreamInfo(
						serviceId = window.mediaItem.mediaMetadata.extras!!.getInt("KEY_SERVICE_ID")!!,
						url = window.mediaItem.mediaId,
						name = window.mediaItem.mediaMetadata.title?.toString() ?: "",
						thumbnailUrl = window.mediaItem.mediaMetadata.artworkUri?.toString(),
						uploaderName = window.mediaItem.mediaMetadata.artist?.toString(),
						duration = window.mediaItem.mediaMetadata.durationMs ?: 0
					)
				} catch (e: Exception) {
					null
				}
			}

			PlaylistSelectorPopup(
				streamInfoList = queueStreams,
				onDismiss = { showPlaylistSelector = false },
				onPlaylistSelected = { showPlaylistSelector = false }
			)
		}

		// Speed Pitch Dialog
		if (showSpeedPitchDialog) {
			SpeedPitchDialog(
				currentSpeed = currentSpeed,
				currentPitch = currentPitch,
				onDismiss = { showSpeedPitchDialog = false },
				onApply = { speed, pitch ->
					val params = PlaybackParameters(speed, pitch)
					mediaController.playbackParameters = params

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
                    SleepTimerService.startTimer(context, minutes)
                }
            )
        }
	}
}

@Composable
fun PlayQueueItem(
    mediaItem: MediaItem,
    isCurrentItem: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier
) {
	Card(
		modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onItemClick() },
                onLongClick = { onItemLongClick() }
            ),
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
                        text = mediaItem.mediaMetadata.durationMs!!.toDurationString(true),
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
                    text = currentPosition.toDurationString(true),
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
                        text = duration.toDurationString(true),
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
