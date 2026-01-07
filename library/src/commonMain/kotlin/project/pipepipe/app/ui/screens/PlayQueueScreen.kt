package project.pipepipe.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.onCustomTopBarColor
import project.pipepipe.app.platform.PlatformMediaController
import project.pipepipe.app.platform.PlatformMediaItem
import project.pipepipe.app.platform.RepeatMode
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.PlaylistSelectorPopup
import project.pipepipe.app.ui.component.player.SleepTimerDialog
import project.pipepipe.app.ui.component.player.SpeedPitchDialog
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlayQueueScreen() {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var _mediaController by remember { mutableStateOf<PlatformMediaController?>(null) }
    LaunchedEffect(Unit) {
        _mediaController = SharedContext.platformMediaController
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

    val playQueue by SharedContext.queueManager.queue.collectAsState()
    val currentMediaItemIndex by SharedContext.queueManager.currentIndex.collectAsState()
    val currentMediaItem by mediaController.currentMediaItem.collectAsState()
    val isPlaying by mediaController.isPlaying.collectAsState()
    val currentPosition by mediaController.currentPosition.collectAsState()
    val duration by mediaController.duration.collectAsState()
    val repeatMode by mediaController.repeatMode.collectAsState()
    val shuffleModeEnabled by mediaController.shuffleModeEnabled.collectAsState()
    val currentSpeed by mediaController.playbackSpeed.collectAsState()
    val currentPitch by mediaController.playbackPitch.collectAsState()
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var showSpeedPitchDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        SharedContext.queueManager.moveItem(from.index, to.index)
    }

    LaunchedEffect(shuffleModeEnabled) {
        if (currentMediaItemIndex >= 0 && currentMediaItemIndex < playQueue.size) {
            listState.scrollToItem(currentMediaItemIndex)
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
                items = playQueue,
                key = { index, item -> item.uuid}
            ) { index, platformMediaItem ->
                ReorderableItem(reorderableLazyListState, key = platformMediaItem.uuid) { isDragging ->
                    val interactionSource = remember { MutableInteractionSource() }
                    PlayQueueItem(
                        mediaItem = platformMediaItem,
                        isCurrentItem = index == currentMediaItemIndex,
                        onItemClick = {
                            mediaController.seekToItem(index)
                            mediaController.play()
                        },
                        onItemLongClick = {
                            val streamInfo = StreamInfo(
                                serviceId = platformMediaItem.serviceId ?: 0,
                                url = platformMediaItem.mediaId,
                                name = platformMediaItem.title ?: "",
                                thumbnailUrl = platformMediaItem.artworkUrl,
                                uploaderName = platformMediaItem.artist,
                                duration = platformMediaItem.durationMs ?: 0
                            )
                            SharedContext.bottomSheetMenuViewModel.show(
                                StreamInfoWithCallback(streamInfo,
                                    onNavigateTo = null,
                                    onDelete = {
                                        SharedContext.queueManager.removeItemByUuid(platformMediaItem.uuid)
                                    },
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
			currentMediaItem = currentMediaItem,
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
					RepeatMode.OFF -> RepeatMode.ALL
					RepeatMode.ALL -> RepeatMode.ONE
					RepeatMode.ONE -> RepeatMode.OFF
					else -> RepeatMode.OFF
				}
				mediaController.setRepeatMode(newMode)
			},
			onShuffle = {
				mediaController.setShuffleModeEnabled(!shuffleModeEnabled)
			}
		)

		// Playlist Selector Dialog
		if (showPlaylistSelector) {
			val queueStreams = playQueue.mapNotNull { platformMediaItem ->
				try {
					StreamInfo(
						serviceId = platformMediaItem.serviceId ?: 0,
						url = platformMediaItem.mediaId,
						name = platformMediaItem.title ?: "",
						thumbnailUrl = platformMediaItem.artworkUrl,
						uploaderName = platformMediaItem.artist,
						duration = platformMediaItem.durationMs ?: 0
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
                    SharedContext.platformActions.startSleepTimer(minutes)
                }
            )
        }
	}
}

@Composable
fun PlayQueueItem(
    mediaItem: PlatformMediaItem,
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
                    model = mediaItem.artworkUrl,
                    contentDescription = stringResource(MR.strings.thumbnail),
                    modifier = Modifier
                        .height(45.dp)
                        .width(80.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                if (mediaItem.durationMs != null) {
                    Text(
                        text = mediaItem.durationMs.toDurationString(true),
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
					text = mediaItem.title ?: stringResource(MR.strings.unknown_title),
					style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
					maxLines = 1,
                    overflow = TextOverflow.Ellipsis
				)

				mediaItem.artist?.let { artist ->
					Text(
						text = artist,
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
    repeatMode: RepeatMode,
    shuffleModeEnabled: Boolean,
    currentMediaItem: PlatformMediaItem?,
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
                            text = mediaItem.title ?: stringResource(MR.strings.unknown_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )

                        mediaItem.artist?.let { artist ->
                            Text(
                                text = artist,
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
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            RepeatMode.ALL -> Icons.Default.Repeat
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = stringResource(MR.strings.repeat_mode),
                        tint = when (repeatMode) {
                            RepeatMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
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
