package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.icerock.moko.resources.compose.stringResource
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
    var renameText by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.playlist_menu_rename)) },
        text = {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text(stringResource(MR.strings.playlist_rename_label)) },
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
                Text(stringResource(MR.strings.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.playlist_delete_title)) },
        text = {
            Text(
                stringResource(MR.strings.playlist_delete_message)
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
                Text(stringResource(MR.strings.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}

@Composable
fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirmClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.clear_history_desc)) },
        text = {
            Text(stringResource(MR.strings.clear_history_message))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmClear()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(MR.strings.clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}

@Composable
fun RemoveDuplicatesDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.playlist_menu_remove_duplicates)) },
        text = {
            Text(stringResource(MR.strings.playlist_remove_duplicates_message))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(MR.strings.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}
