package project.pipepipe.app.ui.screens.playlistdetail

import android.content.ComponentName
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.StringResourceHelper
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.service.FeedUpdateManager
import project.pipepipe.app.service.FeedWorkState
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.theme.onCustomTopBarColor
import project.pipepipe.app.ui.viewmodel.PlaylistDetailViewModel
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.extractor.Router.getType
import project.pipepipe.extractor.utils.RequestHelper.getQueryValue
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyGridState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    url: String,
    useAsTab: Boolean = false,
    serviceId: String? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val viewModel: PlaylistDetailViewModel = viewModel(key = url)
    val uiState by viewModel.uiState.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

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

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    val feedWorkState by FeedUpdateManager.workState.collectAsState()

    val shouldShowMoreMenuButton = !((uiState.playlistType == PlaylistType.FEED
            && url.substringAfterLast("/").substringBefore("?").toLongOrNull() == -1L)
            || uiState.playlistType == PlaylistType.TRENDING)

    LaunchedEffect(url) {
        if (uiState.playlistInfo?.url != url) {
            viewModel.loadPlaylist(url, serviceId)
        }
    }

    LaunchedEffect(listState, uiState.displayItems.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collect { lastVisibleIndex ->
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
                FeedUpdateManager.resetState()
            }
            is FeedWorkState.Idle -> {
                viewModel.setRefreshing(false)
            }
        }
    }

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

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (isSearchActive && listState.isScrollInProgress) {
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
        mediaController?.let { controller ->
            controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
            GlobalScope.launch{
                viewModel.sortedItems.forEach {
                    DatabaseOperations.insertOrUpdateStream(it)
                }
            }
            val mediaItems = viewModel.sortedItems.map { it.toMediaItem() }
            controller.setShuffleModeEnabled(shuffle)
            controller.setMediaItems(mediaItems, index, 0L)
            controller.prepare()
            controller.play()
            if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
                GlobalScope.launch {
                    delay(500)
                    SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
                }
            }
        }
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
                                    text = MR.strings.search.desc().toString(context = context),
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
                                    contentDescription = MR.strings.clear.desc().toString(context = context)
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
                                    contentDescription = MR.strings.search.desc().toString(context = context)
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
                        FeedUpdateManager.startFeedUpdate(context, feedId)
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
