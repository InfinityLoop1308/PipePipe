package project.pipepipe.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.ui.item.CommonItem
import project.pipepipe.app.ui.theme.supportingTextColor

@Composable
fun PlaylistSelectorPopup(
    streamInfo: StreamInfo? = null,
    streamInfoList: List<StreamInfo>? = null,
    onDismiss: () -> Unit,
    onPlaylistSelected: () -> Unit
) {
    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // Support both single stream and multiple streams
    val streams = streamInfoList ?: listOfNotNull(streamInfo)

    LaunchedEffect(Unit) {
        playlists = DatabaseOperations.getAllLocalPlaylists(streamInfo)
        isLoading = false
    }
    val addedText = stringResource(MR.strings.added)
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
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            .compositeOver(MaterialTheme.colorScheme.primaryContainer)
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
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(MR.strings.duplicate_in_playlist),
                                    color = supportingTextColor(),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    )
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                            if (playlists.isNotEmpty()) {
                                items(playlists) { playlist ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        CommonItem(
                                            item = playlist,
                                            onClick = {
                                                GlobalScope.launch {
                                                    addStreamsToPlaylist(streams, playlist)
                                                }
                                                onPlaylistSelected()
                                                ToastManager.show(addedText)
                                            },
                                            shouldUseSecondaryColor = playlist.shouldUseSecondaryColor
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
                GlobalScope.launch  {
                    createNewPlaylistAndAddStreams(playlistName, streams)
                    ToastManager.show(addedText)
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

private suspend fun addStreamsToPlaylist(
    streams: List<StreamInfo>,
    playlist: PlaylistInfo
) {
    DatabaseOperations.addStreamsToPlaylist(playlist.uid!!, streams)
}

private suspend fun createNewPlaylistAndAddStreams(
    playlistName: String,
    streams: List<StreamInfo>
) {
    val playlistId = DatabaseOperations.insertPlaylistAtTop(
        name = playlistName,
        thumbnailUrl = streams.firstOrNull()?.thumbnailUrl,
    )

    DatabaseOperations.addStreamsToPlaylist(playlistId, streams)
}