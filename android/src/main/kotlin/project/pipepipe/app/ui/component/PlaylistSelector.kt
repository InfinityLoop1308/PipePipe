package project.pipepipe.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.database.DatabaseOperations.getAllLocalPlaylists
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.ui.item.MediaListItem

@Composable
fun PlaylistSelectorPopup(
    streamInfo: StreamInfo,
    onDismiss: () -> Unit,
    onPlaylistSelected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        playlists = getAllLocalPlaylists()
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMessage != null -> {
                        Text(
                            text = stringResource(MR.strings.general_error) + ": $errorMessage",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f, false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showNewPlaylistDialog = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(MR.strings.create_playlist),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (playlists.isNotEmpty()) {
                                items(playlists) { playlist ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    addStreamToPlaylist(streamInfo, playlist)
                                                }
                                                onPlaylistSelected()
                                            },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        MediaListItem(
                                            item = playlist,
                                            onClick = {
                                                scope.launch {
                                                    addStreamToPlaylist(streamInfo, playlist)
                                                }
                                                onPlaylistSelected()
                                            }
                                        )
                                    }
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(MR.strings.playlist_selector_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onConfirm = { playlistName ->
                scope.launch  {
                    createNewPlaylistAndAddStream(playlistName, streamInfo)
                }
                showNewPlaylistDialog = false
                onPlaylistSelected()
            }
        )
    }
}

@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.create_playlist)) },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text(stringResource(MR.strings.playlist_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (playlistName.isNotBlank()) {
                        onConfirm(playlistName)
                    }
                },
                enabled = playlistName.isNotBlank()
            ) {
                Text(stringResource(MR.strings.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}

private suspend fun addStreamToPlaylist(
    streamInfo: StreamInfo,
    playlist: PlaylistInfo
) {
    try {
        var stream = DatabaseOperations.getStreamByUrl(streamInfo.url)
        if (stream == null) {
            DatabaseOperations.insertOrUpdateStream(streamInfo )
            stream = DatabaseOperations.getStreamByUrl(streamInfo.url)
        }

        if (stream != null) {
            val playlistId = playlist.url.substringAfterLast("/").toLong()
            val existingStreams = DatabaseOperations.loadPlaylistsItemsFromDatabase(playlistId.toString())
            val nextIndex = existingStreams.size
            DatabaseOperations.addStreamToPlaylist(playlistId, stream.uid)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private suspend fun createNewPlaylistAndAddStream(
    playlistName: String,
    streamInfo: StreamInfo
) {
    val playlistId = DatabaseOperations.insertPlaylistAtTop(
        name = playlistName,
        thumbnailUrl = streamInfo.thumbnailUrl,
    )

    var stream = DatabaseOperations.getStreamByUrl(streamInfo.url)
    if (stream == null) {
        DatabaseOperations.insertOrUpdateStream(streamInfo)
        stream = DatabaseOperations.getStreamByUrl(streamInfo.url)
    }

    if (stream != null) {
        DatabaseOperations.addStreamToPlaylist(playlistId, stream.uid)
    }
}