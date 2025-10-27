package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.PlaylistType

@Composable
fun RenamePlaylistDialog(
    playlistType: PlaylistType,
    playlistUid: Long?,
    currentName: String,
    url: String,
    onDismiss: () -> Unit,
    onRenamed: (String) -> Unit,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    var renameText by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        when (playlistType) {
                            PlaylistType.LOCAL -> {
                                DatabaseOperations.renamePlaylist(playlistUid!!, renameText)
                            }
                            PlaylistType.FEED -> {
                                val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                                DatabaseOperations.renameFeedGroup(feedId, renameText)
                            }
                            else -> {}
                        }
                        onRenamed(renameText)
                    }
                },
                enabled = renameText.isNotBlank()
            ) {
                Text(MR.strings.dialog_confirm.desc().toString(context = context))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(MR.strings.cancel.desc().toString(context = context))
            }
        }
    )
}

@Composable
fun DeletePlaylistDialog(
    playlistType: PlaylistType,
    playlistUid: Long?,
    playlistName: String,
    url: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    scope: CoroutineScope
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MR.strings.playlist_delete_title.desc().toString(context = context)) },
        text = {
            Text(
                MR.strings.playlist_delete_message.desc().toString(context = context)
                    .format(playlistName)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        when (playlistType) {
                            PlaylistType.LOCAL -> {
                                DatabaseOperations.deletePlaylist(playlistUid!!)
                            }
                            PlaylistType.FEED -> {
                                val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                                DatabaseOperations.deleteFeedGroup(feedId)
                            }
                            else -> {}
                        }
                        onDeleted()
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
            TextButton(onClick = onDismiss) {
                Text(MR.strings.cancel.desc().toString(context = context))
            }
        }
    )
}
