package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.item.CommonItem
import project.pipepipe.app.ui.item.DisplayType
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.ui.viewmodel.PlaylistDetailViewModel
import project.pipepipe.app.service.FeedUpdateManager
import project.pipepipe.app.uistate.PlaylistUiState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState

@Composable
fun PlaylistContent(
    uiState: PlaylistUiState,
    viewModel: PlaylistDetailViewModel,
    listState: LazyListState,
    reorderableLazyListState: ReorderableLazyListState,
    isSearchActive: Boolean,
    url: String,
    serviceId: String?,
    scope: CoroutineScope,
    onStartPlayAll: (index: Int, shuffle: Boolean) -> Unit,
    onClearSearchFocus: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    when {
        uiState.common.error != null -> {
            ErrorComponent(
                error = uiState.common.error!!,
                onRetry = {
                    scope.launch {
                        val errorRow = DatabaseOperations.getErrorLogById(uiState.common.error!!.errorId)
                        viewModel.loadPlaylist(errorRow!!.request!!, serviceId)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        uiState.common.isLoading && uiState.displayItems.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.displayItems.isEmpty() && uiState.playlistType != PlaylistType.FEED -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSearchActive && uiState.searchQuery.isNotEmpty()) {
                        MR.strings.playlist_empty_search_result.desc().toString(context = context)
                            .format(uiState.searchQuery)
                    } else {
                        MR.strings.playlist_empty_no_streams.desc().toString(context = context)
                    },
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp)
                    .clipToBounds()
            ) {
                if (uiState.playlistType != PlaylistType.HISTORY && uiState.playlistType != PlaylistType.TRENDING){
                    item {
                        PlaylistHeaderSection(
                            playlistType = uiState.playlistType,
                            playlistName = uiState.playlistInfo?.name ?: "",
                            thumbnailUrl = uiState.playlistInfo?.thumbnailUrl,
                            streamCount = uiState.playlistInfo?.streamCount,
                            totalDuration = uiState.list.itemList.sumOf { it.duration ?: 0L },
                            feedLastUpdated = uiState.feedLastUpdated,
                            isRefreshing = uiState.isRefreshing,
                            hasItems = uiState.list.itemList.isNotEmpty(),
                            onRefreshClick = {
                                if (!uiState.isRefreshing) {
                                    val feedId = url.substringAfterLast("/")
                                        .substringBefore("?").toLong()
                                    FeedUpdateManager.startFeedUpdate(context, feedId)
                                }
                            },
                            onPlayAllClick = { onStartPlayAll(0, false) }
                        )
                    }
                    if (uiState.displayItems.isNotEmpty()) {
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }

                itemsIndexed(
                    items = uiState.displayItems,
                    key = { _, item -> item.joinId ?: item.url }
                ) { index, streamItem ->
                    ReorderableItem(reorderableLazyListState, key = streamItem.joinId ?: streamItem.url) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }
                        CommonItem(
                            item = streamItem,
                            isDragging = isDragging,
                            showDragHandle = uiState.playlistType == PlaylistType.LOCAL && !isSearchActive,
                            onClick = {
                                focusManager.clearFocus()
                                if (uiState.playlistType in listOf(PlaylistType.LOCAL, PlaylistType.FEED)
                                    && SharedContext.settingsManager.getBoolean("auto_background_play_key")) {
                                    onStartPlayAll(
                                        viewModel.sortedItems.indexOfFirst { it.joinId == streamItem.joinId },
                                        SharedContext.settingsManager.getBoolean("random_music_play_mode_key")
                                    )
                                } else {
                                    SharedContext.sharedVideoDetailViewModel.loadVideoDetails(
                                        streamItem.url,
                                        streamItem.serviceId
                                    )
                                }
                            },
                            onNavigateTo = if (isSearchActive && uiState.searchQuery.isNotEmpty()) {
                                {
                                    scope.launch {
                                        onClearSearchFocus()
                                        val displayIndex = viewModel.uiState.value.displayItems.indexOfFirst { it.joinId == streamItem.joinId }
                                        if (uiState.list.itemList.size <= 100) {
                                            listState.animateScrollToItem(index = displayIndex)
                                        } else {
                                            listState.scrollToItem(index = displayIndex)
                                        }
                                    }
                                }
                            } else null,
                            onDelete = if(uiState.playlistType != PlaylistType.REMOTE) {
                                {
                                    scope.launch {
                                        viewModel.removeItem(streamItem)
                                    }
                                }
                            } else null,
                            showProvideDetailButton = uiState.playlistType in listOf(PlaylistType.LOCAL, PlaylistType.FEED)
                                    && SharedContext.settingsManager.getBoolean("auto_background_play_key"),
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStarted = {
                                    // Optional: Add haptic feedback
                                },
                                onDragStopped = {
                                    // Optional: Add completion feedback
                                },
                                interactionSource = interactionSource
                            ),
                            displayType = when (uiState.playlistType) {
                                PlaylistType.LOCAL -> DisplayType.NAME_ONLY
                                PlaylistType.HISTORY -> DisplayType.STREAM_HISTORY
                                PlaylistType.REMOTE, PlaylistType.FEED, PlaylistType.TRENDING  -> DisplayType.ORIGIN
                            }
                        )
                    }
                    if (index < uiState.displayItems.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (uiState.common.isLoading && uiState.list.nextPageUrl != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
