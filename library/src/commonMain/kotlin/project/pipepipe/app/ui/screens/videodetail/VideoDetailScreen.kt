@file:OptIn(ExperimentalMaterial3Api::class)

package project.pipepipe.app.ui.screens.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.NetworkStateHelper
import project.pipepipe.app.platform.PlatformMediaController
import project.pipepipe.app.platform.ScreenOrientation
import project.pipepipe.app.ui.component.*
import project.pipepipe.app.ui.component.player.PlayerGestureSettings
import project.pipepipe.app.ui.component.player.VideoPlayer
import project.pipepipe.app.uistate.VideoDetailPageState
import kotlin.math.min

@Composable
fun VideoDetailScreen(modifier: Modifier, navController: NavHostController) {
    val viewModel = SharedContext.sharedVideoDetailViewModel
    val uiState by SharedContext.sharedVideoDetailViewModel.uiState.collectAsState()
    val streamInfo = uiState.currentStreamInfo
    val platformActions = SharedContext.platformActions
    val screenOrientation by platformActions.screenOrientation.collectAsState()
    val scope = rememberCoroutineScope()

    val controller = SharedContext.platformMediaController

    var showPlaylistPopup by remember { mutableStateOf(false) }
    var showDecoderErrorDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(streamInfo?.url) {
        listState.scrollToItem(0)
    }

    // Listen for decoder error events
    val playbackMode by SharedContext.playbackMode.collectAsState()
    LaunchedEffect(Unit) {
        SharedContext.decoderErrorEvent.collect {
            // Only show dialog when in DETAIL_PAGE and VIDEO_AUDIO mode
            if (uiState.pageState == VideoDetailPageState.DETAIL_PAGE &&
                playbackMode == PlaybackMode.VIDEO_AUDIO) {
                showDecoderErrorDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        while (SharedContext.platformMediaController == null) {
            delay(100) // safe, only delay 200-300ms at beginning once in the app lifecycle
        }

        SharedContext.platformMediaController!!.currentMediaItem
            .filterNotNull()
            .collect { item ->
                // called 2 times for unknown reason, but anyway loadVideoDetails does nothing if same url so should be safe
                if (SharedContext.playbackMode.value == PlaybackMode.VIDEO_AUDIO) {
                    SharedContext.sharedVideoDetailViewModel.loadVideoDetails(
                        url = item.mediaId,
                        serviceId = item.serviceId,
                        shouldDisableLoading = true,
                        shouldKeepPlaybackMode = true,
                        shouldNotChangePageState = true
                    )
                }
            }
    }



    val nestedScrollConnection1 = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) {
                    val consumed = min(-available.y, listState.layoutInfo.visibleItemsInfo.last().offset.toFloat())
                    scope.launch {
                        listState.scrollBy(consumed)
                    }
                    return Offset(0f, if (consumed > 0) available.y else 0f)
                } else {
                    return Offset.Zero
                }
            }
        }
    }

    // Track drag distance for swipe-down gesture
    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    // Reset drag distance when state changes
    LaunchedEffect(uiState.pageState) {
        totalDragDistance = 0f
    }

    // Auto-play logic based on settings and network state
    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(streamInfo, uiState.pageState) {
        if (streamInfo != null && uiState.pageState == VideoDetailPageState.DETAIL_PAGE && !uiState.common.isLoading
        ) {
            // Check if this video was playing before minimizing
            val wasPlayingBeforeMinimizing = SharedContext.playingVideoUrlBeforeMinimizing == streamInfo.url
            SharedContext.playingVideoUrlBeforeMinimizing = null // Clear the flag
            if (wasPlayingBeforeMinimizing) {
                // Resume video playback
                kotlinx.coroutines.delay(200)
                controller?.let {
                    controller.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
                    if (controller.currentMediaItem.value?.mediaId == streamInfo.url && controller.isPlaying.value) {
                        controller.playFromStreamInfo(streamInfo)
                    }
                }
            } else if (!hasAutoPlayed) {
                // Original autoplay logic
                val autoplaySetting = SharedContext.settingsManager.getString("autoplay_key", "autoplay_never_key")

                val shouldAutoPlay = when (autoplaySetting) {
                    "autoplay_always_key" -> true
                    "autoplay_wifi_key" -> NetworkStateHelper.isWifiConnected()
                    "autoplay_never_key" -> false
                    else -> NetworkStateHelper.isWifiConnected() // default to WiFi only
                }

                if (shouldAutoPlay) {
                    controller?.let {
                        controller.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
                        if (controller.currentMediaItem.value?.mediaId != streamInfo.url) {
                            controller.playFromStreamInfo(streamInfo)
                        } else if (!controller.isPlaying.value) {
                            controller.play()
                        }
                    }
                    hasAutoPlayed = true
                }
            }
        }
    }

    // Reset auto-play flag when video changes
    LaunchedEffect(streamInfo?.url) {
        hasAutoPlayed = false
    }

    var currentScreenOrientation by rememberSaveable { mutableStateOf(screenOrientation) }

    LaunchedEffect(screenOrientation, streamInfo?.isPortrait) {
        if (SharedContext.isInPipMode.value) {
            return@LaunchedEffect
        }
        val newOrientation = screenOrientation
        val oldOrientation = currentScreenOrientation
        currentScreenOrientation = newOrientation
        if (uiState.pageState in listOf(VideoDetailPageState.DETAIL_PAGE, VideoDetailPageState.FULLSCREEN_PLAYER)) {

            when (newOrientation) {
                ScreenOrientation.LANDSCAPE if oldOrientation == ScreenOrientation.PORTRAIT &&
                        uiState.pageState != VideoDetailPageState.FULLSCREEN_PLAYER -> {
                    viewModel.setPageState(VideoDetailPageState.FULLSCREEN_PLAYER)
                }

                ScreenOrientation.PORTRAIT if oldOrientation == ScreenOrientation.LANDSCAPE &&
                        uiState.pageState == VideoDetailPageState.FULLSCREEN_PLAYER -> {
                    viewModel.setPageState(VideoDetailPageState.DETAIL_PAGE)
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(uiState.pageState, streamInfo?.isPortrait) {
        if (streamInfo == null || SharedContext.isInPipMode.value) {
            return@LaunchedEffect
        }
        when (uiState.pageState) {
            VideoDetailPageState.FULLSCREEN_PLAYER -> {
                platformActions.enterImmersiveVideoMode(streamInfo.isPortrait)
            }
            else -> {
                platformActions.exitImmersiveVideoMode()
            }
        }
    }




    DisposableEffect(Unit) {
        onDispose {
            platformActions.exitImmersiveVideoMode()
        }
    }



    data class TabConfig(
        val tag: String,
        val title: String,
        val icon: ImageVector,
        val isAvailable: Boolean,
        val content: @Composable () -> Unit
    )

    // Monitor SponsorBlock enabled state
    var isSponsorBlockEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("sponsor_block_enable_key", true))
    }

    DisposableEffect(Unit) {
        val listener = SharedContext.settingsManager.addBooleanListener(
            "sponsor_block_enable_key",
            true
        ) { newValue ->
            isSponsorBlockEnabled = newValue
        }
        onDispose {
            listener?.deactivate()
        }
    }

    var configuredTabs by remember {
        mutableStateOf(SharedContext.settingsManager.getStringSet("video_tabs_key", setOf("comments", "related", "sponsorblock", "description")))
    }

    DisposableEffect(Unit) {
        val listener = SharedContext.settingsManager.addStringSetListener(
            "video_tabs_key",
            setOf("comments", "related", "sponsorblock", "description")
        ) { newValue ->
            configuredTabs = newValue
        }
        onDispose {
            listener?.deactivate()
        }
    }

    val allTabs = listOf(
        TabConfig(
            tag = "COMMENTS",
            title = stringResource(MR.strings.comments_tab_description),
            icon = Icons.AutoMirrored.Filled.Comment,
            isAvailable = streamInfo?.commentUrl != null && configuredTabs.contains("comments"),
            content = {
                streamInfo?.commentUrl?.let {
                    CommentSection(
                        navController = navController,
                        onTimestampClick = { timestamp ->
                            if (controller != null && controller.currentMediaItem.value?.mediaId == streamInfo.url) {
                                controller.seekTo(timestamp * 1000)
                            }
                        })
                }
            }
        ),
        TabConfig(
            tag = "NEXT VIDEO",
            title = stringResource(MR.strings.related_videos),
            icon = Icons.Default.ArtTrack,
            isAvailable = streamInfo?.relatedItemUrl != null && configuredTabs.contains("related"),
            content = { RelatedItemSection() }
        ),
        TabConfig(
            tag = "SPONSOR_BLOCK TAB",
            title = stringResource(MR.strings.sponsor_block),
            icon = Icons.Default.Shield,
            isAvailable = streamInfo?.sponsorblockUrl != null && isSponsorBlockEnabled  && configuredTabs.contains("sponsorblock"),
            content = {
                val sponsorBlockState = uiState.currentSponsorBlock
                if (sponsorBlockState.common.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    SponsorBlockSection(
                        segments = sponsorBlockState.segments,
                        modifier = Modifier.fillMaxSize(),
                        onStart = { controller?.currentPosition?.value },
                        onEnd = { controller?.currentPosition?.value },
                        onTimestampClick = { timestamp ->
                            if (controller?.currentMediaItem?.value?.mediaId == streamInfo!!.url) {
                                controller?.seekTo(timestamp * 1000)
                            }
                        }
                    )
                }
            }
        ),
        TabConfig(
            tag = "DESCRIPTION TAB",
            title = stringResource(MR.strings.description_tab),
            icon = Icons.Default.Description,
            isAvailable = streamInfo != null && configuredTabs.contains("description"),
            content = {
                streamInfo?.let {
                    DescriptionSection(
                        streamInfo = it,
                        navController = navController,
                        onTimestampClick = { timestamp ->
                            if (controller?.currentMediaItem?.value?.mediaId == streamInfo.url) {
                                controller.seekTo(timestamp * 1000)
                            }
                        }
                    )
                }
            }
        )
    )

    val availableTabs = allTabs.filter { it.isAvailable }

    // Calculate initial page index based on saved tab
    val savedTabTag = SharedContext.settingsManager.getString("stream_info_selected_tab", "")
    val initialPage = savedTabTag.let { tag ->
        availableTabs.indexOfFirst { it.tag == tag }.takeIf { it >= 0 }
    } ?: 0

    // Use key to recreate pagerState when saved tab or available tabs change
    val pagerState = key(savedTabTag, availableTabs.size) {
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { availableTabs.size }
        )
    }
    when (uiState.pageState) {
        VideoDetailPageState.HIDDEN -> {}
        VideoDetailPageState.FULLSCREEN_PLAYER -> {
            if (streamInfo != null && controller != null) {
                BackHandler {
                    viewModel.toggleFullscreenPlayer()
                }
                VideoPlayer(
                    mediaController = controller!!,
                    streamInfo = streamInfo,
                    onFullScreenClicked = { viewModel.toggleFullscreenPlayer() },
                    modifier = Modifier.fillMaxSize(),
                    danmakuPool = uiState.currentDanmaku,
                    gestureSettings = PlayerGestureSettings(
                        swipeSeekEnabled = SharedContext.settingsManager.getBoolean(
                            "swipe_seek_gesture_control_key",
                            true
                        ),
                        volumeGestureEnabled = SharedContext.settingsManager.getBoolean(
                            "volume_gesture_control_key",
                            true
                        ),
                        brightnessGestureEnabled = SharedContext.settingsManager.getBoolean(
                            "brightness_gesture_control_key",
                            true
                        ),
                        fullscreenGestureEnabled = SharedContext.settingsManager.getBoolean(
                            "fullscreen_gesture_control_key",
                            true
                        )
                    ),
                    danmakuEnabled = uiState.danmakuEnabled,
                    onToggleDanmaku = { viewModel.toggleDanmaku() },
                    sponsorBlockSegments = if (isSponsorBlockEnabled) uiState.currentSponsorBlock.segments else emptyList()
                )
            }
        }

        VideoDetailPageState.BOTTOM_PLAYER, VideoDetailPageState.DETAIL_PAGE -> {
            if (uiState.pageState == VideoDetailPageState.DETAIL_PAGE) {
                BackHandler {
                    if (!viewModel.navigateBack()) {
                        viewModel.showAsBottomPlayer()
                    }
                }
            }
            Crossfade(
                targetState = uiState.pageState,
                animationSpec = tween(durationMillis = 300),
                label = "pageStateTransition"
            ) { pageState ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    when (pageState) {
                        VideoDetailPageState.DETAIL_PAGE -> {
                            Column(
                                modifier = modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .statusBarsPadding()
                            ) {
                                if (uiState.common.error != null) {
                                    // Error state: show only ErrorComponent
                                    ErrorComponent(
                                        error = uiState.common.error!!,
                                        onRetry = {
                                            scope.launch {
                                                val errorRow =
                                                    DatabaseOperations.getErrorLogById(uiState.common.error!!.errorId)
                                                viewModel.loadVideoDetails(
                                                    errorRow!!.request!!,
                                                    serviceId = errorRow.service_id
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (streamInfo == null || uiState.common.isLoading) {
                                    // Loading state: show loading indicator
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                } else if (controller != null) {
                                    // Normal state: show full video detail UI
                                    val isPortrait = screenOrientation == ScreenOrientation.PORTRAIT

                                    // Player composable content
                                    val playerContent: @Composable () -> Unit = {
                                        Box(
                                            modifier = Modifier
                                                .then(
                                                    if (isPortrait) Modifier.aspectRatio(16f / 9f)
                                                    else Modifier
                                                        .height(200.dp)
                                                )
                                                .pointerInput(Unit) {
                                                    detectVerticalDragGestures(
                                                        onDragEnd = {
                                                            // If dragged down more than threshold, minimize to bottom player
                                                            if (totalDragDistance > 100.dp.toPx()) {
                                                                viewModel.showAsBottomPlayer()
                                                            }
                                                            if (SharedContext.playbackMode.value == PlaybackMode.AUDIO_ONLY) return@detectVerticalDragGestures
                                                            else if (totalDragDistance < -50.dp.toPx()) {
                                                                viewModel.toggleFullscreenPlayer()
                                                            }
                                                            totalDragDistance = 0f
                                                        },
                                                        onDragCancel = {
                                                            totalDragDistance = 0f
                                                        }
                                                    ) { change, dragAmount ->
                                                        change.consume()
                                                        totalDragDistance += dragAmount
                                                    }
                                                }
                                        ) {
                                            VideoPlayer(
                                                mediaController = controller!!,
                                                streamInfo = streamInfo,
                                                onFullScreenClicked = { viewModel.toggleFullscreenPlayer() },
                                                modifier = Modifier.fillMaxSize(),
                                                danmakuPool = uiState.currentDanmaku,
                                                gestureSettings = PlayerGestureSettings(
                                                    swipeSeekEnabled = SharedContext.settingsManager.getBoolean("swipe_seek_gesture_control_key"),
                                                    volumeGestureEnabled = SharedContext.settingsManager.getBoolean("volume_gesture_control_key"),
                                                    brightnessGestureEnabled = SharedContext.settingsManager.getBoolean("brightness_gesture_control_key"),
                                                    fullscreenGestureEnabled = SharedContext.settingsManager.getBoolean("fullscreen_gesture_control_key")
                                                ),
                                                danmakuEnabled = uiState.danmakuEnabled,
                                                onToggleDanmaku = { viewModel.toggleDanmaku() },
                                                sponsorBlockSegments = if (isSponsorBlockEnabled) uiState.currentSponsorBlock.segments else emptyList()
                                            )
                                        }
                                    }

                                    if (isPortrait) {
                                        // Portrait mode: Player is sticky (outside LazyColumn)
                                        playerContent()
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f),
                                        state = listState
                                    ) {
                                        if (!isPortrait) {
                                            // Landscape mode: Player participates in scrolling (inside LazyColumn)
                                            item { playerContent() }
                                        }
                                        item { VideoTitleSection(name = streamInfo.name) }
                                        item {
                                            VideoDetailSection(
                                                streamInfo = streamInfo,
                                                navController = navController
                                            )
                                        }
                                        item {
                                            ActionButtons(
                                                onPlayAudioClick = {
                                                    controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
                                                    if (controller.currentMediaItem.value?.mediaId != streamInfo.url) {
                                                        controller.playFromStreamInfo(streamInfo)
                                                    } else if (!controller.isPlaying.value) {
                                                        controller.play()
                                                    }
                                                },
                                                onAddToPlaylistClick = { showPlaylistPopup = true },
                                                streamInfo = streamInfo
                                            )
                                        }
                                        item {
                                            if (availableTabs.isNotEmpty()) {
                                                HorizontalPager(
                                                    state = pagerState,
                                                    modifier = Modifier
                                                        .fillParentMaxHeight()
                                                        .padding(horizontal = 16.dp)
                                                        .padding(top = 8.dp)
                                                        .nestedScroll(nestedScrollConnection1),
                                                    beyondViewportPageCount = 4
                                                ) { page ->
                                                    availableTabs[page].content()
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = stringResource(MR.strings.no_available_tabs),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (availableTabs.isNotEmpty()) {
                                        TabRow(
                                            selectedTabIndex = pagerState.currentPage,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                        ) {
                                            availableTabs.forEachIndexed { index, tab ->
                                                Tab(
                                                    selected = pagerState.currentPage == index,
                                                    onClick = {
                                                        scope.launch {
                                                            listState.scrollToItem(0)
                                                            pagerState.animateScrollToPage(index)
                                                            SharedContext.settingsManager.putString("stream_info_selected_tab", tab.tag)
                                                        }
                                                    },
                                                    icon = {
                                                        Icon(
                                                            imageVector = tab.icon,
                                                            contentDescription = tab.title,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        VideoDetailPageState.BOTTOM_PLAYER -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                            ) {
                                BottomSheetContent(controller!!)
                            }
                        }

                        else -> {
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistPopup && streamInfo != null) {
        PlaylistSelectorPopup(
            streamInfo = streamInfo,
            onDismiss = {
                showPlaylistPopup = false
            },
            onPlaylistSelected = {
                showPlaylistPopup = false
            }
        )
    }

    if (showDecoderErrorDialog) {
        AlertDialog(
            onDismissRequest = { showDecoderErrorDialog = false },
            title = { Text(stringResource(MR.strings.decoder_init_failed_title)) },
            text = { Text(stringResource(MR.strings.decoder_init_failed_message)) },
            confirmButton = {
                TextButton(onClick = { showDecoderErrorDialog = false }) {
                    Text(stringResource(MR.strings.ok))
                }
            }
        )
    }
}

@Composable
private fun BottomSheetContent(controller: PlatformMediaController) {
    val isPlaying by controller.isPlaying.collectAsState()
    val currentMediaItem by controller.currentMediaItem.collectAsState()
    if (currentMediaItem == null) {
        SharedContext.sharedVideoDetailViewModel.hide()
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { SharedContext.sharedVideoDetailViewModel.loadVideoDetails(currentMediaItem!!.mediaId, null) }
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = currentMediaItem?.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(width = 64.dp, height = 36.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = currentMediaItem?.title.toString(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentMediaItem?.artist.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = stringResource(MR.strings.add_to_queue),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { SharedContext.toggleShowPlayQueueVisibility() }
                .padding(8.dp)
        )

        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) stringResource(MR.strings.pause) else stringResource(MR.strings.player_play),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable {
                    if (isPlaying) {
                        controller.pause()
                    } else {
                        controller.play()
                    }
                }
                .padding(8.dp)
        )

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(MR.strings.close),
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable {
                    SharedContext.platformActions.stopPlaybackService()
                    SharedContext.sharedVideoDetailViewModel.hide()
                }
                .padding(8.dp)
        )
    }
}
