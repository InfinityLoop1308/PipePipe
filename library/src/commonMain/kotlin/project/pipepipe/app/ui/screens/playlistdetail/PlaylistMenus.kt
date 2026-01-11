package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.PlaylistSortMode
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.shared.infoitem.PlaylistInfo

@Composable
fun SortMenuButton(
    currentSortMode: PlaylistSortMode,
    onSortModeChange: (PlaylistSortMode) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showSortMenu = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.Sort,
                contentDescription = stringResource(MR.strings.sort)
            )
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_origin)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.ORIGIN)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.ORIGIN) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_origin_reverse)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.ORIGIN_REVERSE)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.ORIGIN_REVERSE) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_upload_time_ascending)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.UPLOAD_TIME_ASCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.UPLOAD_TIME_ASCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_upload_time_descending)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.UPLOAD_TIME_DESCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.UPLOAD_TIME_DESCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_duration_ascending)) },
                onClick = {
                    onSortModeChange(PlaylistSortMode.DURATION_ASCENDING)
                    showSortMenu = false
                },
                leadingIcon = if (currentSortMode == PlaylistSortMode.DURATION_ASCENDING) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.sort_duration_descending)) },
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
    onAddToPlaylistClick: (() -> Unit)? = null,
    onRemoveDuplicatesClick: (() -> Unit)? = null,
    onRemoveWatchedClick: (() -> Unit)? = null,
    onShareUrlListClick: (() -> Unit)? = null,
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMoreMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(MR.strings.playlist_action_more)
            )
        }
        DropdownMenu(
            expanded = showMoreMenu,
            onDismissRequest = { showMoreMenu = false }
        ) {
            when (playlistType) {
                PlaylistType.LOCAL -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.playlist_menu_rename)) },
                        onClick = {
                            showMoreMenu = false
                            onRenameClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.playlist_menu_remove_duplicates)) },
                        onClick = {
                            showMoreMenu = false
                            onRemoveDuplicatesClick?.invoke()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentPasteOff, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.playlist_menu_remove_watched)) },
                        onClick = {
                            showMoreMenu = false
                            onRemoveWatchedClick?.invoke()
                        },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_add_to)) },
                        onClick = {
                            showMoreMenu = false
                            onAddToPlaylistClick?.invoke()
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.playlist_menu_share_url_list)) },
                        onClick = {
                            showMoreMenu = false
                            onShareUrlListClick?.invoke()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.playlist_menu_delete)) },
                        onClick = {
                            showMoreMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                    SharedContext.platformMenuItems.localPlaylistMenuItems()
                }

                PlaylistType.FEED -> {
                    val feedId = playlistInfo?.url?.substringAfterLast("/")?.substringBefore("?")?.toLongOrNull()

                    if (feedId != null && feedId != -1L) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.playlist_menu_rename)) },
                            onClick = {
                                showMoreMenu = false
                                onRenameClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.playlist_menu_delete)) },
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
                                    stringResource(MR.strings.playlist_menu_unbookmark)
                                } else {
                                    stringResource(MR.strings.playlist_menu_bookmark)
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
                        text = { Text(stringResource(MR.strings.action_add_to)) },
                        onClick = {
                            showMoreMenu = false
                            onAddToPlaylistClick?.invoke()
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.share)) },
                        onClick = {
                            showMoreMenu = false
                            SharedContext.platformActions.share(
                                playlistInfo?.url ?: "",
                                playlistInfo?.name
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                }

                else -> {
                    when (playlistType) {
                        PlaylistType.HISTORY -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.clear_history_desc)) },
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
