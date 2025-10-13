package project.pipepipe.app.ui.component

import android.content.ComponentName
import android.content.Intent
import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.playFromStreamInfo
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.MR
import project.pipepipe.shared.PlaybackMode
import project.pipepipe.shared.database.DatabaseOperations
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import project.pipepipe.app.ui.screens.Screen.Channel
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.GlobalScope
import project.pipepipe.app.global.PipHelper
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.uistate.VideoDetailPageState

@OptIn(UnstableApi::class)
@Composable
fun BottomSheetMenu(
    navController: NavController,
    content: Info,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    LaunchedEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())
    }

    DisposableEffect(Unit) {
        onDispose {
            controllerFuture?.let { future ->
                MediaController.releaseFuture(future)
            }
        }
    }

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
                    mediaController = mediaController,
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
                    }
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
                Text(stringResource(MR.strings.bottom_sheet_menu_title, content.toString()), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(MR.strings.enqueue_next_stream), modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO */ onDismiss() }
                    .padding(vertical = 8.dp))
                Text(stringResource(MR.strings.enqueue_stream), modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO */ onDismiss() }
                    .padding(vertical = 8.dp))
                Text(stringResource(MR.strings.download), modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* TODO */ onDismiss() }
                    .padding(vertical = 8.dp))
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
    mediaController: MediaController?,
    streamInfo: StreamInfo,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenChannel: (() -> Unit)
) {
    var showPlaylistPopup by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val menuItems = buildList {
        add(Triple(Icons.Default.PlayCircle, stringResource(MR.strings.bottom_sheet_background_play)) {
            mediaController?.let { controller ->
                controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
                controller.playFromStreamInfo(streamInfo)
            }
            onDismiss()
        })
        add(Triple(Icons.Default.Queue, stringResource(MR.strings.enqueue_stream)) {
            mediaController?.let { controller ->
                controller.addMediaItem(streamInfo.toMediaItem())
                if (controller.mediaItemCount == 1) {
                    controller.play()
                }
            }
            onDismiss()
        })
//        add(Triple(Icons.Default.PictureInPicture, stringResource(MR.strings.pip)) {
//            mediaController?.let { PipHelper.enterPipMode(it, streamInfo, context) }
//            onDismiss()
//        })
        add(Triple(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(MR.strings.add_to_playlist)) {
            showPlaylistPopup = true
        })
        add(Triple(Icons.Default.Download, stringResource(MR.strings.download)) {
            handleDownload(context, streamInfo.url) { showDialog ->
                showDownloadDialog = showDialog
            }
        })
        add(Triple(Icons.Default.Share, stringResource(MR.strings.share)) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, streamInfo.url)
                putExtra(Intent.EXTRA_TITLE, streamInfo.name)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
            onDismiss()
        })
        if (streamInfo.uploaderUrl != null) {
            add(Triple(Icons.Default.Person, stringResource(MR.strings.show_channel_details)) {
                onOpenChannel()
                onDismiss()
            })
            add(Triple(Icons.Default.Subscriptions, stringResource(MR.strings.bottom_sheet_subscribe_channel)) {
                GlobalScope.launch {
                    streamInfo.uploaderUrl?.let { url ->
                        DatabaseOperations.insertOrUpdateSubscription(ChannelInfo(
                            serviceId = streamInfo.serviceId,
                            url = url,
                            name = streamInfo.uploaderName ?: context.getString(MR.strings.bottom_sheet_unknown_channel.resourceId),
                            thumbnailUrl = null,
                            subscriberCount = null
                        ))
                    }
                }
                onDismiss()
            })
            add(Triple(Icons.Default.Block, stringResource(MR.strings.bottom_sheet_block_channel)) { /* TODO */ onDismiss() })
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

    if (showDownloadDialog) {
        DownloadInfoDialog(
            url = streamInfo.url,
            onDismiss = {
                showDownloadDialog = false
            }
        )
    }
}


@Composable
private fun PlaylistInfoMenuItems(
    playlistInfo: PlaylistInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlistInfo.name) }

    val menuItems = listOf(
        Triple(Icons.Default.Edit, MR.strings.playlist_menu_rename.desc().toString(context = context)) {
            showRenameDialog = true
        },
        Triple(Icons.Default.PushPin,
            if (playlistInfo.isPinned) {
                MR.strings.playlist_menu_unpin.desc().toString(context = context)
            } else {
                MR.strings.playlist_menu_pin.desc().toString(context = context)
            }
        ) {
            scope.launch {
                playlistInfo.uid?.let { uid ->
                    DatabaseOperations.setPlaylistPinned(uid, !playlistInfo.isPinned)
                }
            }
            onDismiss()
        },
        Triple(Icons.Default.Delete, MR.strings.playlist_menu_delete.desc().toString(context = context)) {
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
            title = { Text(MR.strings.playlist_menu_rename.desc().toString(context = context)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(MR.strings.playlist_rename_label.desc().toString(context = context)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            playlistInfo.uid?.let { uid ->
                                DatabaseOperations.renamePlaylist(uid, renameText)
                            }
                            showRenameDialog = false
                            onDismiss()
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(MR.strings.dialog_confirm.desc().toString(context = context))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(MR.strings.cancel.desc().toString(context = context))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(MR.strings.playlist_delete_title.desc().toString(context = context)) },
            text = {
                Text(
                    MR.strings.playlist_delete_message.desc().toString(context = context)
                        .format(playlistInfo.name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
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
                    Text(MR.strings.delete.desc().toString(context = context))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(MR.strings.cancel.desc().toString(context = context))
                }
            }
        )
    }
}