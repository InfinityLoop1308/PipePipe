package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.PlaylistSortMode
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.shared.infoitem.PlaylistInfo

@Composable
fun SortMenuButton(
    currentSortMode: PlaylistSortMode,
    onSortModeChange: (PlaylistSortMode) -> Unit
) {
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showSortMenu = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.Sort,
                contentDescription = MR.strings.sort.desc().toString(context = context)
            )
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(MR.strings.sort_origin.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.ORIGIN)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.ORIGIN) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(MR.strings.sort_origin_reverse.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.ORIGIN_REVERSE)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.ORIGIN_REVERSE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(MR.strings.sort_upload_time_ascending.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.UPLOAD_TIME_ASCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.UPLOAD_TIME_ASCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(MR.strings.sort_upload_time_descending.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.UPLOAD_TIME_DESCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.UPLOAD_TIME_DESCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(MR.strings.sort_duration_ascending.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.DURATION_ASCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.DURATION_ASCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(MR.strings.sort_duration_descending.desc().toString(context = context)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.DURATION_DESCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.DURATION_DESCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
        }
    }
}

@Composable
fun PlaylistMoreMenu(
    playlistType: PlaylistType,
    playlistInfo: PlaylistInfo?,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReloadPlaylist: () -> Unit,
    onClearHistoryClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMoreMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = MR.strings.playlist_action_more.desc().toString(context = context)
            )
        }
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            when (playlistType) {
                PlaylistType.LOCAL -> {
                    DropdownMenuItem(
                        text = { Text(MR.strings.playlist_menu_rename.desc().toString(context = context)) },
                        onClick = {
                            showMoreMenu = false
                            onRenameClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(MR.strings.playlist_menu_delete.desc().toString(context = context)) },
                        onClick = {
                            showMoreMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }

                PlaylistType.FEED -> {
                    val feedId = playlistInfo?.url?.substringAfterLast("/")?.substringBefore("?")?.toLongOrNull()

                    if (feedId != null && feedId != -1L) {
                        DropdownMenuItem(
                            text = { Text(MR.strings.playlist_menu_rename.desc().toString(context = context)) },
                            onClick = {
                                showMoreMenu = false
                                onRenameClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(MR.strings.playlist_menu_delete.desc().toString(context = context)) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }

                PlaylistType.REMOTE -> {
                    val isBookmarked = playlistInfo?.uid != null

                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isBookmarked) {
                                    MR.strings.playlist_menu_unbookmark.desc().toString(context = context)
                                } else {
                                    MR.strings.playlist_menu_bookmark.desc().toString(context = context)
                                }
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            GlobalScope.launch {
                                if (isBookmarked) {
                                    DatabaseOperations.deleteRemotePlaylist(playlistInfo!!.uid!!)
                                    onReloadPlaylist()
                                } else if (playlistInfo != null && playlistInfo.serviceId != null) {
                                    DatabaseOperations.insertOrReplaceRemotePlaylist(
                                        serviceId = playlistInfo.serviceId!!,
                                        name = playlistInfo.name,
                                        url = playlistInfo.url,
                                        thumbnailUrl = playlistInfo.thumbnailUrl,
                                        uploader = playlistInfo.uploaderName,
                                        streamCount = playlistInfo.streamCount
                                    )
                                    onReloadPlaylist()
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd,
                                contentDescription = null
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(MR.strings.share.desc().toString(context = context)) },
                        onClick = {
                            showMoreMenu = false
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, playlistInfo?.url ?: "")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, null))
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                }

                else -> {
                    when (playlistType) {
                        PlaylistType.HISTORY -> {
                            DropdownMenuItem(
                                text = { Text(MR.strings.clear_history_desc.desc().toString(context = context)) },
                                onClick = {
                                    showMoreMenu = false
                                    onClearHistoryClick?.invoke()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                            )
                        }

                        else -> {
                            // No menu items for TRENDING types
                        }
                    }
                }
            }
        }
    }
}
