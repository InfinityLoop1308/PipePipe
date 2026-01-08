@file:OptIn(ExperimentalMaterial3Api::class)

package project.pipepipe.app.ui.screens.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ArtTrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.NetworkStateHelper
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

        SharedContext.queueManager.currentItem
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
                kotlinx.coroutines.delay(500) // don't use a small value, this will interfere the animation
                controller?.let {
                    controller.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
                    if (SharedContext.queueManager.currentItem.value?.mediaId == streamInfo.url && controller.isPlaying.value) {
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
                        if (SharedContext.queueManager.currentItem.value?.mediaId != streamInfo.url) {
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
                            if (controller != null && SharedContext.queueManager.currentItem.value?.mediaId == streamInfo.url) {
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
                            if (SharedContext.queueManager.currentItem.value?.mediaId == streamInfo!!.url) {
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
                            if (SharedContext.queueManager.currentItem.value?.mediaId == streamInfo.url) {
                                controller?.seekTo(timestamp * 1000)
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
                    danmakuEnabled = uiState.danmakuEnabled,
                    onToggleDanmaku = { viewModel.toggleDanmaku() },
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
            BoxWithConstraints(
                modifier = Modifier
                .systemBarsPadding()
                .clipToBounds()
            ) {
                val containerHeight = maxHeight

                val transition = updateTransition(
                    targetState = uiState.pageState,
                    label = "pageTransition"
                )

                val detailPageOffsetY by transition.animateDp(
                    transitionSpec = { tween(250) },
                    label = "detailOffset"
                ) { state ->
                    when (state) {
                        VideoDetailPageState.DETAIL_PAGE -> 0.dp
                        VideoDetailPageState.BOTTOM_PLAYER -> containerHeight - 64.dp
                        else -> 0.dp
                    }
                }

                val bottomPlayerAlpha by transition.animateFloat(
                    transitionSpec = { tween(250) },
                    label = "bottomPlayerAlpha"
                ) { state ->
                    when (state) {
                        VideoDetailPageState.BOTTOM_PLAYER -> 1f
                        else -> 0f
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = detailPageOffsetY)
                ) {
                    Column(
                        modifier = modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(1 - bottomPlayerAlpha)
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
                                        danmakuEnabled = uiState.danmakuEnabled,
                                        onToggleDanmaku = { viewModel.toggleDanmaku() },
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
                                            if (SharedContext.queueManager.currentItem.value?.mediaId != streamInfo.url) {
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
                    if (bottomPlayerAlpha > 0){
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .alpha(bottomPlayerAlpha)
                        ) {
                            VideoDetailScreenBottomSheetContent()
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
