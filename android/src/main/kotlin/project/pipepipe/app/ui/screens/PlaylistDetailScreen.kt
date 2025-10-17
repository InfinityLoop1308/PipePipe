package project.pipepipe.app.ui.screens

import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.service.FeedUpdateManager
import project.pipepipe.app.service.FeedWorkState
import project.pipepipe.app.service.PlaybackService

import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.utils.formatCount
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.app.uistate.PlaylistSortMode
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.item.DisplayType
import project.pipepipe.app.ui.item.CommonItem
import project.pipepipe.app.ui.viewmodel.PlaylistDetailViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    navController: NavController,
    url: String,
    serviceId: String? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val viewModel: PlaylistDetailViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }


    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.reorderItems(from.index - 1, to.index - 1)
    }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }

    val feedWorkState by FeedUpdateManager.workState.collectAsState()

    LaunchedEffect(url) {
        viewModel.loadPlaylist(url, serviceId)
    }

    LaunchedEffect(listState, uiState.displayItems.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collect { lastVisibleIndex ->
            if (lastVisibleIndex != null &&
                uiState.playlistType == PlaylistType.REMOTE &&
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
                SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
            }
        }
    }

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
                            when (uiState.playlistType) {
                                PlaylistType.LOCAL -> {
                                    DatabaseOperations.renamePlaylist(uiState.playlistInfo!!.uid!!, renameText)
                                }
                                PlaylistType.FEED -> {
                                    val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                                    DatabaseOperations.renameFeedGroup(feedId, renameText)
                                }
                                else -> {}
                            }
                            viewModel.updatePlaylistName(renameText)
                            showRenameDialog = false
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
                        .format(uiState.playlistInfo?.name ?: "")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (uiState.playlistType) {
                                PlaylistType.LOCAL -> {
                                    DatabaseOperations.deletePlaylist(uiState.playlistInfo!!.uid!!)
                                }
                                PlaylistType.FEED -> {
                                    val feedId = url.substringAfterLast("/").substringBefore("?").toLong()
                                    DatabaseOperations.deleteFeedGroup(feedId)
                                }
                                else -> {}
                            }
                            showDeleteDialog = false
                            navController.popBackStack()
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
        if (isSearchActive) {
            BackHandler {
                focusManager.clearFocus()
                isSearchActive = false
                viewModel.updateSearchQuery("")
            }
        }
        CustomTopBar(
            title = {
                if (isSearchActive) {
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
                                fontSize = 16.sp
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
                } else {
                    Text(
                        uiState.playlistInfo?.name ?: MR.strings.playlist_title_default.desc().toString(context = context),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            },
            titlePadding = 0.dp,
            defaultNavigationOnClick = { navController.popBackStack() },
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
                    if (uiState.playlistType == PlaylistType.LOCAL){
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true }
                            ) {
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
                                        viewModel.updateSortMode(PlaylistSortMode.ORIGIN)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (uiState.sortMode == PlaylistSortMode.ORIGIN) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                                DropdownMenuItem(
                                    text = { Text(MR.strings.sort_origin_reverse.desc().toString(context = context)) },
                                    onClick = {
                                        viewModel.updateSortMode(PlaylistSortMode.ORIGIN_REVERSE)
                                        showSortMenu = false
                                    },
                                    leadingIcon = if (uiState.sortMode == PlaylistSortMode.ORIGIN_REVERSE) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                    if (!isSearchActive) {
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
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = MR.strings.playlist_action_more.desc().toString(context = context)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            when (uiState.playlistType) {
                                PlaylistType.LOCAL -> {
                                    DropdownMenuItem(
                                        text = { Text(MR.strings.playlist_menu_rename.desc().toString(context = context)) },
                                        onClick = {
                                            showMoreMenu = false
                                            renameText = uiState.playlistInfo?.name ?: ""
                                            showRenameDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(MR.strings.playlist_menu_delete.desc().toString(context = context)) },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (uiState.playlistInfo?.isPinned == true) {
                                                    MR.strings.playlist_menu_unpin.desc().toString(context = context)
                                                } else {
                                                    MR.strings.playlist_menu_pin.desc().toString(context = context)
                                                }
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            scope.launch {
                                                DatabaseOperations.setPlaylistPinned(
                                                    uiState.playlistInfo!!.uid!!,
                                                    !uiState.playlistInfo!!.isPinned
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                                PlaylistType.FEED -> {
                                    val feedId = url.substringAfterLast("/").substringBefore("?").toLongOrNull()

                                    if (feedId != null && feedId != -1L) {
                                        DropdownMenuItem(
                                            text = { Text(MR.strings.playlist_menu_rename.desc().toString(context = context)) },
                                            onClick = {
                                                showMoreMenu = false
                                                renameText = uiState.playlistInfo?.name ?: ""
                                                showRenameDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(MR.strings.playlist_menu_delete.desc().toString(context = context)) },
                                            onClick = {
                                                showMoreMenu = false
                                                showDeleteDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (uiState.playlistInfo?.isPinned == true) {
                                                        MR.strings.playlist_menu_unpin.desc().toString(context = context)
                                                    } else {
                                                        MR.strings.playlist_menu_pin.desc().toString(context = context)
                                                    }
                                                )
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                scope.launch {
                                                    DatabaseOperations.setFeedGroupPinned(
                                                        feedId,
                                                        !(uiState.playlistInfo?.isPinned ?: false)
                                                    )
                                                    viewModel.loadPlaylist(url, serviceId)
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null
                                                )
                                            }
                                        )
                                    }
                                }
                                else -> {
                                    // No menu items for HISTORY and REMOTE types
                                }
                            }
                        }
                    }
                }
            }
        )

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
            uiState.displayItems.isEmpty() -> {
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
                ) {
                    if (uiState.playlistType != PlaylistType.HISTORY){
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                if ( uiState.playlistType != PlaylistType.FEED) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = uiState.playlistInfo?.thumbnailUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(120.dp)
                                                .height(72.dp)
                                                .clip(MaterialTheme.shapes.medium),
                                            contentScale = ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = uiState.playlistInfo!!.name,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            val totalDuration = uiState.list.itemList.sumOf { it.duration ?: 0L }
                                            Text(
                                                text = MR.strings.playlist_info_summary.desc().toString(context = context)
                                                    .format(formatCount(uiState.playlistInfo?.streamCount), totalDuration.toDurationString()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(MR.strings.feed_oldest_subscription_update).format(
                                                uiState.feedLastUpdated?.let { formatRelativeTime(it) } ?: stringResource(MR.strings.never)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        IconButton(
                                            onClick = {
                                                if (!uiState.isRefreshing) {
                                                    val feedId = url.substringAfterLast("/")
                                                        .substringBefore("?").toLong()
                                                    FeedUpdateManager.startFeedUpdate(context, feedId)
                                                }
                                            },
                                            enabled = !uiState.isRefreshing
                                        ) {
                                            if (uiState.isRefreshing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = stringResource(MR.strings.refresh_feed)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(
//                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }

                                if (uiState.list.itemList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        FilledTonalButton(
                                            onClick = { startPlayAll() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(MR.strings.play_all.desc().toString(context = context))
                                        }
                                    }
                                }
                            }
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
                                        startPlayAll(viewModel.sortedItems.indexOfFirst { it.joinId == streamItem.joinId },
                                            SharedContext.settingsManager.getBoolean("random_music_play_mode_key"))
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
                                            isSearchActive = false
                                            viewModel.updateSearchQuery("")
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
                                    PlaylistType.REMOTE, PlaylistType.FEED  -> DisplayType.ORIGIN
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
}
