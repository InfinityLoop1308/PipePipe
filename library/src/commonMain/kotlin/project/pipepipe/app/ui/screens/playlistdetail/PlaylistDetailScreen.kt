package project.pipepipe.app.ui.screens.playlistdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.StringResourceHelper
import project.pipepipe.app.platform.FeedWorkState
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.onCustomTopBarColor
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.viewmodel.PlaylistDetailViewModel
import project.pipepipe.extractor.Router.getType
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    url: String,
    useAsTab: Boolean = false,
    serviceId: Int? = null
) {
    val focusManager = LocalFocusManager.current
    val viewModel: PlaylistDetailViewModel = viewModel(key = url)
    val uiState by viewModel.uiState.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDuplicatesDialog by remember { mutableStateOf(false) }
    var showRemoveWatchedDialog by remember { mutableStateOf(false) }

    val titleTextRaw =
        if (url.getType() == "trending") StringResourceHelper.getTranslatedTrendingName(getQueryValue(url, "name")!!)
        else uiState.playlistInfo?.name ?: stringResource(MR.strings.playlist_title_default)


    val titleText = remember(url, uiState.playlistInfo?.name) {titleTextRaw}

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.reorderItems(from.index - 1, to.index - 1)
    }

    val gridState = rememberLazyGridState()

    val reorderableLazyGridState = rememberReorderableLazyGridState(gridState) { from, to ->
        viewModel.reorderItems(from.index - 1, to.index - 1)
    }

    // Track first visible item key before refresh to restore scroll position
    var firstVisibleItemKey by remember { mutableStateOf<String?>(null) }
    var firstVisibleItemOffset by remember { mutableIntStateOf(0) }

    val feedWorkState by SharedContext.platformActions.feedWorkState.collectAsState()

    val shouldShowMoreMenuButton = !((uiState.playlistType == PlaylistType.FEED
            && url.substringAfterLast("/").substringBefore("?").toLongOrNull() == -1L)
            || uiState.playlistType == PlaylistType.TRENDING)

    LaunchedEffect(url) {
        if (uiState.playlistInfo?.url != url) {
            viewModel.loadPlaylist(url, serviceId)
        }
    }

    LaunchedEffect(listState, gridState, uiState.displayItems.size) {
        val isGridEnabled = SharedContext.settingsManager.getBoolean("grid_layout_enabled_key", false)

        val lastVisibleIndexFlow = if (isGridEnabled) {
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            }
        } else {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            }
        }

        lastVisibleIndexFlow.collect { lastVisibleIndex ->
            if (lastVisibleIndex != null &&
                uiState.playlistType in listOf(PlaylistType.REMOTE, PlaylistType.TRENDING) &&
                !uiState.common.isLoading &&
                uiState.list.nextPageUrl != null) {

                val totalItems = uiState.displayItems.size
                if (lastVisibleIndex >= totalItems - 5) {
                    viewModel.loadRemotePlaylistMoreItems(serviceId!!)
                }
            }
        }
    }

    LaunchedEffect(feedWorkState) {
        when (feedWorkState) {
            is FeedWorkState.Running -> {
                viewModel.setRefreshing(true)
            }
            is FeedWorkState.Success, is FeedWorkState.Failed -> {
                if (uiState.isRefreshing && uiState.playlistType == PlaylistType.FEED) {
                    // Save current first visible item before refresh
                    val isGridEnabled = SharedContext.settingsManager.getBoolean("grid_layout_enabled_key", false)
                    val firstVisibleItemIndex = if (isGridEnabled) {
                        gridState.firstVisibleItemIndex
                    } else {
                        listState.firstVisibleItemIndex
                    }

                    // Account for header items - find the first actual stream item
                    val headerOffset = 2 // FeedRefreshHeader + PlaylistHeaderSection
                    val actualItemIndex = (firstVisibleItemIndex - headerOffset).coerceAtLeast(0)

                    if (actualItemIndex < uiState.displayItems.size) {
                        val firstVisibleItem = uiState.displayItems[actualItemIndex]
                        firstVisibleItemKey = firstVisibleItem.url
                        firstVisibleItemOffset = if (isGridEnabled) {
                            gridState.firstVisibleItemScrollOffset
                        } else {
                            listState.firstVisibleItemScrollOffset
                        }
                    }

                    viewModel.loadPlaylist(url, serviceId)
                    val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                    viewModel.updateFeedLastUpdated(feedId)
                }
                viewModel.setRefreshing(false)
                SharedContext.platformActions.resetFeedState()
            }
            is FeedWorkState.Idle -> {
                viewModel.setRefreshing(false)
            }
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(listState.isScrollInProgress, gridState.isScrollInProgress) {
        val isGridEnabled = SharedContext.settingsManager.getBoolean("grid_layout_enabled_key", false)
        val isScrolling = if (isGridEnabled) gridState.isScrollInProgress else listState.isScrollInProgress
        if (isSearchActive && isScrolling) {
            focusManager.clearFocus()
        }
    }

    // Restore scroll position after feed refresh
    LaunchedEffect(uiState.displayItems.size, firstVisibleItemKey) {
        if (firstVisibleItemKey != null && uiState.playlistType == PlaylistType.FEED) {
            val newIndex = uiState.displayItems.indexOfFirst {
                it.url == firstVisibleItemKey
            }
            if (newIndex >= 0) {
                val isGridEnabled = SharedContext.settingsManager.getBoolean("grid_layout_enabled_key", false)
                val headerOffset = 2 // FeedRefreshHeader + PlaylistHeaderSection
                val targetIndex = newIndex + headerOffset

                if (isGridEnabled) {
                    gridState.scrollToItem(targetIndex, firstVisibleItemOffset)
                } else {
                    listState.scrollToItem(targetIndex, firstVisibleItemOffset)
                }
                // Clear the saved key after restoring
                firstVisibleItemKey = null
            }
        }
    }

    fun startPlayAll(index: Int = 0, shuffle: Boolean = false) {
        SharedContext.platformMediaController!!.playAll(viewModel.sortedItems, index, shuffle)
    }

    // Dialogs
    if (showRenameDialog) {
        RenamePlaylistDialog(
            playlistType = uiState.playlistType,
            playlistUid = uiState.playlistInfo?.uid,
            currentName = uiState.playlistInfo?.name ?: "",
            url = url,
            onDismiss = { showRenameDialog = false },
            onRenamed = { newName ->
                viewModel.updatePlaylistName(newName)
                showRenameDialog = false
            },
            scope = scope
        )
    }

    if (showDeleteDialog) {
        DeletePlaylistDialog(
            playlistType = uiState.playlistType,
            playlistUid = uiState.playlistInfo?.uid,
            playlistName = uiState.playlistInfo?.name ?: "",
            url = url,
            onDismiss = { showDeleteDialog = false },
            onDeleted = {
                showDeleteDialog = false
                navController.popBackStack()
            },
            scope = scope
        )
    }

    if (showClearHistoryDialog) {
        ClearHistoryDialog(
            onDismiss = { showClearHistoryDialog = false },
            onConfirmClear = { viewModel.clearHistory() }
        )
    }

    if (showAddToPlaylistDialog) {
        project.pipepipe.app.ui.component.PlaylistSelectorPopup(
            streamInfoList = uiState.displayItems,
            onDismiss = { showAddToPlaylistDialog = false },
            onPlaylistSelected = {
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showRemoveDuplicatesDialog) {
        val msg = stringResource(MR.strings.done)
        RemoveDuplicatesDialog(
            onDismiss = { showRemoveDuplicatesDialog = false },
            onConfirm = { viewModel.removeDuplicates(msg) }
        )
    }

    if (showRemoveWatchedDialog) {
        val msg = stringResource(MR.strings.done)
        RemoveWatchedDialog(
            onDismiss = { showRemoveWatchedDialog = false },
            onConfirmPartiallyWatched = { viewModel.removePartiallyWatched(msg, "partially") },
            onConfirmFullyWatched = { viewModel.removePartiallyWatched(msg, "full") }
        )
    }

    SharedContext.platformMenuItems.localPlaylistDialogs(uiState.playlistInfo?.url?.substringAfterLast("/")?.toLongOrNull())

    if (isSearchActive) {
        BackHandler {
            focusManager.clearFocus()
            isSearchActive = false
            viewModel.updateSearchQuery("")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSearchActive) {
                if (isSearchActive) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                }
            }
    ) {
        if (!useAsTab) {
            CustomTopBar(
                title = if (isSearchActive) {
                    {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    text = stringResource(MR.strings.search),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    ),
                                    fontSize = 16.sp,
                                    color = onCustomTopBarColor()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = TextStyle(fontSize = 16.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { /* Search is live, no action needed */ })
                        )
                    }
                } else {
                    {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = onCustomTopBarColor()
                        )
                    }
                },
                titlePadding = 0.dp,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                if (uiState.searchQuery.isEmpty()) {
                                    isSearchActive = false
                                } else {
                                    viewModel.updateSearchQuery("")
                                }
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(MR.strings.clear)
                                )
                            }
                        }
                        if (uiState.playlistType == PlaylistType.LOCAL ||
                            (uiState.playlistType == PlaylistType.REMOTE && url.getType() != "trending")) {
                            SortMenuButton(
                                currentSortMode = uiState.sortMode,
                                onSortModeChange = { viewModel.updateSortMode(it) }
                            )
                        }
                        if (!isSearchActive && uiState.playlistType !in listOf(PlaylistType.REMOTE, PlaylistType.TRENDING)) {
                            IconButton(
                                onClick = {
                                    isSearchActive = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(MR.strings.search)
                                )
                            }
                        }
                        if (shouldShowMoreMenuButton) {
                            PlaylistMoreMenu(
                                playlistType = uiState.playlistType,
                                playlistInfo = uiState.playlistInfo,
                                onRenameClick = { showRenameDialog = true },
                                onDeleteClick = { showDeleteDialog = true },
                                onReloadPlaylist = { viewModel.loadPlaylist(url, serviceId) },
                                onClearHistoryClick = { showClearHistoryDialog = true },
                                onAddToPlaylistClick = {
                                    showAddToPlaylistDialog = true
                                },
                                onRemoveDuplicatesClick = {
                                    showRemoveDuplicatesDialog = true
                                },
                                onRemoveWatchedClick = {
                                    showRemoveWatchedDialog = true
                                },
                                onShareUrlListClick = {
                                    val urlList = uiState.displayItems.joinToString("\n") { it.url }
                                    SharedContext.platformActions.share(
                                        urlList,
                                        titleText
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
        if (uiState.playlistType == PlaylistType.FEED) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    if (!uiState.isRefreshing) {
                        val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                        SharedContext.platformActions.startFeedUpdate(feedId)
                    }
                }
            ) {
                PlaylistContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    listState = listState,
                    reorderableLazyListState = reorderableLazyListState,
                    gridState = gridState,
                    reorderableLazyGridState = reorderableLazyGridState,
                    isSearchActive = isSearchActive,
                    url = url,
                    serviceId = serviceId,
                    scope = scope,
                    onStartPlayAll = { index, shuffle -> startPlayAll(index, shuffle) },
                    onClearSearchFocus = {
                        isSearchActive = false
                        viewModel.updateSearchQuery("")
                    }
                )
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            PlaylistContent(
                uiState = uiState,
                viewModel = viewModel,
                listState = listState,
                reorderableLazyListState = reorderableLazyListState,
                gridState = gridState,
                reorderableLazyGridState = reorderableLazyGridState,
                isSearchActive = isSearchActive,
                url = url,
                serviceId = serviceId,
                scope = scope,
                onStartPlayAll = { index, shuffle -> startPlayAll(index, shuffle) },
                onClearSearchFocus = {
                    isSearchActive = false
                    viewModel.updateSearchQuery("")
                }
            )
        }
    }
}
