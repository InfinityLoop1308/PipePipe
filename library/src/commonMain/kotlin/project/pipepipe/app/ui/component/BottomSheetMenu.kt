package project.pipepipe.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import project.pipepipe.app.ui.screens.Screen.Channel
import kotlinx.coroutines.GlobalScope
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.app.uistate.VideoDetailPageState

@Composable
fun BottomSheetMenu(
    navController: NavController,
    content: Info,
    onDismiss: () -> Unit
) {
    val doneText = stringResource(MR.strings.done)

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (content) {
            is StreamInfoWithCallback -> {
                InfoHeader(content.streamInfo.thumbnailUrl, content.streamInfo.name, content.streamInfo.uploaderName)
                Spacer(Modifier.height(16.dp))
                StreamInfoMenuItems(
                    streamInfo = content.streamInfo,
                    onDismiss = onDismiss,
                    onNavigateTo = content.onNavigateTo,
                    onDelete = content.onDelete,
                    disablePlayOperations = content.disablePlayOperations,
                    showProvideDetailButton = content.showProvideDetailButton,
                    onOpenChannel = {
                        content.streamInfo.uploaderUrl?.let {
                            navController.navigate(
                                Channel.createRoute(
                                    content.streamInfo.uploaderUrl!!,
                                    content.streamInfo.serviceId
                                )
                            )
                        }
                        if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState != VideoDetailPageState.HIDDEN) {
                            SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
                        }
                    },
                    doneText = doneText
                )
            }
            is PlaylistInfo -> {
                InfoHeader(content.thumbnailUrl, content.name, content.uploaderName)
                Spacer(Modifier.height(16.dp))
                PlaylistInfoMenuItems(
                    playlistInfo = content,
                    onDismiss = onDismiss
                )
            }
            else -> {
            }
        }
    }
}

@Composable
private fun InfoHeader(thumbnailUrl: String?, name: String?, uploaderName: String?) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail with 16:9 aspect ratio
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(120.dp)
                    .height(67.5.dp) // 16:9 aspect ratio
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Title and uploader
            Column {
                Text(
                    text = name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                uploaderName?.let { uploader ->
                    Text(
                        text = uploader,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamInfoMenuItems(
    onDismiss: () -> Unit,
    streamInfo: StreamInfo,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    disablePlayOperations: Boolean = false,
    showProvideDetailButton: Boolean = false,
    onOpenChannel: (() -> Unit),
    doneText: String
) {
    var showPlaylistPopup by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(streamInfo.uploaderUrl) {
        streamInfo.uploaderUrl?.let { url ->
            isSubscribed = DatabaseOperations.isSubscribed(url)
        }
        streamInfo.uploaderName?.let { name ->
            val blockedChannels = SharedContext.settingsManager.getStringSet("filter_by_channel_key_set", emptySet())
            isBlocked = blockedChannels.contains(name)
        }
    }

    val mediaActions = SharedContext.platformActions

    val menuItems = buildList {
        if (!disablePlayOperations) {
            add(Triple(Icons.Default.PlayCircle, stringResource(MR.strings.bottom_sheet_background_play)) {
                GlobalScope.launch {
                    mediaActions.backgroundPlay(streamInfo)
                }
                onDismiss()
            })
            add(Triple(Icons.Default.Queue, stringResource(MR.strings.enqueue_stream)) {
                GlobalScope.launch {
                    mediaActions.enqueue(streamInfo)
                }
                onDismiss()
            })
            add(Triple(Icons.Default.PictureInPicture, stringResource(MR.strings.pip)) {
                mediaActions.enterPictureInPicture(streamInfo)
                onDismiss()
            })
        }

        if (streamInfo.duration != null && streamInfo.duration!! > 0) {
            add(Triple(Icons.Default.Visibility, stringResource(MR.strings.mark_as_watched)) {
                GlobalScope.launch {
                    DatabaseOperations.updateOrInsertStreamHistory(streamInfo)
                    DatabaseOperations.updateStreamProgress(streamInfo.url, streamInfo.duration!! * 1000)
                }
                ToastManager.show(doneText)
                onDismiss()
            })
        }
        add(Triple(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(MR.strings.add_to_playlist)) {
            showPlaylistPopup = true
        })
        add(Triple(Icons.Default.Download, stringResource(MR.strings.download)) {
            SharedContext.showDownloadFormatDialog(streamInfo)
            onDismiss()
        })
        add(Triple(Icons.Default.Share, stringResource(MR.strings.share)) {
            mediaActions.share(streamInfo.url, streamInfo.name)
            onDismiss()
        })
        if (showProvideDetailButton) {
            add(Triple(Icons.Default.Info, stringResource(MR.strings.show_details)) {
                SharedContext.sharedVideoDetailViewModel.loadVideoDetails(streamInfo.url, streamInfo.serviceId)
                onDismiss()
            })
        }
        if (streamInfo.uploaderUrl != null && streamInfo.uploaderName != null) {
            add(Triple(Icons.Default.Person, stringResource(MR.strings.show_channel_details)) {
                onOpenChannel()
                onDismiss()
            })
            if (!isSubscribed) {
                add(Triple(Icons.Default.Subscriptions, stringResource(MR.strings.bottom_sheet_subscribe_channel)) {
                    GlobalScope.launch {
                        streamInfo.uploaderUrl?.let { url ->
                            DatabaseOperations.insertOrUpdateSubscription(
                                ChannelInfo(
                                    serviceId = streamInfo.serviceId,
                                    url = url,
                                    name = streamInfo.uploaderName!!,
                                    thumbnailUrl = streamInfo.thumbnailUrl,
                                    subscriberCount = streamInfo.uploaderSubscriberCount
                                )
                            )
                        }
                        isSubscribed = true
                    }
                    ToastManager.show(doneText)
                    onDismiss()
                })
            }
            if (!isBlocked) {
                add(Triple(Icons.Default.Block, stringResource(MR.strings.bottom_sheet_block_channel)) {
                    streamInfo.uploaderName?.let { name ->
                        val currentSet =
                            SharedContext.settingsManager.getStringSet("filter_by_channel_key_set", emptySet())
                        if (!currentSet.contains(name)) {
                            SharedContext.settingsManager.putStringSet("filter_by_channel_key_set", currentSet + name)
                            isBlocked = true
                        }
                        ToastManager.show(doneText)
                    }
                    onDismiss()
                })
            }
        }

        if (onNavigateTo != null) {
            add(Triple(Icons.Default.Start, stringResource(MR.strings.navigate_to)) {
                onNavigateTo()
                onDismiss()
            })
        }
        if (onDelete != null) {
            add(Triple(Icons.Default.Delete, stringResource(MR.strings.delete)) {
                onDelete()
                onDismiss()
            })
        }
    }

    Column {
        menuItems.forEach { (icon, text, onClick) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    if (showPlaylistPopup) {
        PlaylistSelectorPopup(
            streamInfo = streamInfo,
            onDismiss = {
                showPlaylistPopup = false
            },
            onPlaylistSelected = {
                showPlaylistPopup = false
                onDismiss()
            }
        )
    }
}


@Composable
private fun PlaylistInfoMenuItems(
    playlistInfo: PlaylistInfo,
    onDismiss: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlistInfo.name) }

    val renameText_ = stringResource(MR.strings.playlist_menu_rename)
    val deleteText = stringResource(MR.strings.playlist_menu_delete)
    val renameLabelText = stringResource(MR.strings.playlist_rename_label)
    val confirmText = stringResource(MR.strings.dialog_confirm)
    val cancelText = stringResource(MR.strings.cancel)
    val deleteTitleText = stringResource(MR.strings.playlist_delete_title)
    val deleteMessageText = stringResource(MR.strings.playlist_delete_message)
    val deleteButtonText = stringResource(MR.strings.delete)

    val menuItems = listOf(
        Triple(Icons.Default.Edit, renameText_) {
            showRenameDialog = true
        },
        Triple(Icons.Default.Delete, deleteText) {
            showDeleteDialog = true
        }
    )

    Column {
        menuItems.forEach { (icon, text, onClick) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(renameText_) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(renameLabelText) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        GlobalScope.launch {
                            playlistInfo.uid?.let { uid ->
                                DatabaseOperations.renamePlaylist(uid, renameText)
                            }
                            showRenameDialog = false
                            onDismiss()
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(cancelText)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(deleteTitleText) },
            text = {
                Text(deleteMessageText.format(playlistInfo.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        GlobalScope.launch {
                            playlistInfo.uid?.let { uid ->
                                DatabaseOperations.deletePlaylist(uid)
                            }
                            showDeleteDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(deleteButtonText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(cancelText)
                }
            }
        )
    }
}