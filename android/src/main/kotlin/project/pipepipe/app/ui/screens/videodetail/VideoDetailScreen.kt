@file:OptIn(ExperimentalMaterial3Api::class)

package project.pipepipe.app.ui.screens.videodetail

import android.app.Activity
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import project.pipepipe.app.MR
import project.pipepipe.app.PlaybackMode
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.NetworkStateHelper
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.playFromStreamInfo
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.service.stopService
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.component.PlaylistSelectorPopup
import project.pipepipe.app.ui.component.player.PlayerGestureSettings
import project.pipepipe.app.ui.component.player.VideoPlayer
import project.pipepipe.app.uistate.VideoDetailPageState

data class TabConfig(
    val title: String,
    val icon: ImageVector,
    val isAvailable: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoDetailScreen(modifier: Modifier, navController: NavHostController) {
    val viewModel = SharedContext.sharedVideoDetailViewModel
    val uiState by SharedContext.sharedVideoDetailViewModel.uiState.collectAsState()
    val streamInfo = uiState.currentStreamInfo
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    val scope = rememberCoroutineScope()

    val showAsBottomTask = {
        SharedContext.updatePlaybackMode(PlaybackMode.AUDIO_ONLY)
        if (mediaController?.mediaItemCount == 0 && streamInfo != null) {
            mediaController?.setMediaItem(
                streamInfo.toMediaItem(),
                runBlocking{ DatabaseOperations.getStreamProgress(streamInfo.url) } ?:0)
        }
    }

    var showPlaylistPopup by remember { mutableStateOf(false) }

    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val bottomSheetContentHeight = 64.dp
    val bottomSheetContentHeightPx = with(density) { bottomSheetContentHeight.toPx() }
    val navBarHeightPx = WindowInsets.navigationBars.getBottom(density)

    val draggableState = remember(uiState.pageState) {
        AnchoredDraggableState(
            initialValue = uiState.pageState,
            anchors = DraggableAnchors {
                VideoDetailPageState.DETAIL_PAGE at 0f
                VideoDetailPageState.BOTTOM_PLAYER at screenHeight - bottomSheetContentHeightPx - navBarHeightPx
            }
        )
    }

    var layoutHeightPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(layoutHeightPx, draggableState) {
        if (layoutHeightPx > 0f) {
            val newAnchors = DraggableAnchors {
                VideoDetailPageState.DETAIL_PAGE at 0f
                VideoDetailPageState.BOTTOM_PLAYER at layoutHeightPx - navBarHeightPx - bottomSheetContentHeightPx
            }
            draggableState.updateAnchors(newAnchors)
        }
    }

    LaunchedEffect(draggableState.currentValue) {
        if (draggableState.currentValue != uiState.pageState &&
            uiState.pageState != VideoDetailPageState.FULLSCREEN_PLAYER) {
            viewModel.setPageState(draggableState.currentValue)
            if (draggableState.currentValue == VideoDetailPageState.BOTTOM_PLAYER) {
                showAsBottomTask()
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

    // Auto-play logic based on settings and network state
    var hasAutoPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(streamInfo, mediaController) {
        if (streamInfo != null && mediaController != null && !hasAutoPlayed &&
            uiState.pageState == VideoDetailPageState.DETAIL_PAGE) {
            val autoplaySetting = SharedContext.settingsManager.getString("autoplay_key", "autoplay_never_key")

            val shouldAutoPlay = when (autoplaySetting) {
                "autoplay_always_key" -> true
                "autoplay_wifi_key" -> NetworkStateHelper.isWifiConnected()
                "autoplay_never_key" -> false
                else -> NetworkStateHelper.isWifiConnected() // default to WiFi only
            }

            if (shouldAutoPlay) {
                mediaController?.let { controller ->
                    controller.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
                    if (controller.currentMediaItem?.mediaId != streamInfo.url) {
                        controller.playFromStreamInfo(streamInfo)
                    } else if (!controller.isPlaying) {
                        controller.play()
                    }
                }
                hasAutoPlayed = true
            }
        }
    }

    // Reset auto-play flag when video changes
    LaunchedEffect(streamInfo?.url) {
        hasAutoPlayed = false
    }

    val view = LocalView.current
    var currentScreenOrientation by rememberSaveable { mutableIntStateOf(configuration.orientation) }

    LaunchedEffect(configuration.orientation, streamInfo?.isPortrait) {
        if (SharedContext.isInPipMode.value) {
            return@LaunchedEffect
        }
        val newOrientation = configuration.orientation
        val oldOrientation = currentScreenOrientation
        currentScreenOrientation = newOrientation
        if (uiState.pageState in listOf(VideoDetailPageState.DETAIL_PAGE, VideoDetailPageState.FULLSCREEN_PLAYER)) {

            when (newOrientation) {
                Configuration.ORIENTATION_LANDSCAPE if oldOrientation == Configuration.ORIENTATION_PORTRAIT &&
                        uiState.pageState != VideoDetailPageState.FULLSCREEN_PLAYER -> {
                    viewModel.setPageState(VideoDetailPageState.FULLSCREEN_PLAYER)
                }

                Configuration.ORIENTATION_PORTRAIT if oldOrientation == Configuration.ORIENTATION_LANDSCAPE &&
                        uiState.pageState == VideoDetailPageState.FULLSCREEN_PLAYER -> {
                    viewModel.setPageState(VideoDetailPageState.DETAIL_PAGE)
                }
            }
        }
    }

    LaunchedEffect(uiState.pageState, streamInfo?.isPortrait) {
        if (streamInfo == null || SharedContext.isInPipMode.value) {
            return@LaunchedEffect
        }
        activity?.let { act ->
            val insetsController = WindowCompat.getInsetsController(act.window, view)

            when (uiState.pageState) {
                VideoDetailPageState.FULLSCREEN_PLAYER -> {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    if (act.resources.configuration.orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                        act.requestedOrientation = if(streamInfo.isPortrait) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                }

                else -> {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }




    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controllerFuture?.let { future ->
                MediaController.releaseFuture(future)
            }
        }
    }



    val allTabs = listOf(
        TabConfig(
            title = stringResource(MR.strings.comments_tab_description),
            icon = Icons.AutoMirrored.Filled.Comment,
            isAvailable = streamInfo?.commentUrl != null
        ),
        TabConfig(
            title = stringResource(MR.strings.related_videos),
            icon = Icons.Default.ArtTrack,
            isAvailable = streamInfo?.relatedItemUrl != null
        ),
        TabConfig(
            title = stringResource(MR.strings.sponsor_block),
            icon = Icons.Default.Shield,
            isAvailable = streamInfo?.sponsorblockUrl != null
        ),
        TabConfig(
            title = stringResource(MR.strings.description_tab),
            icon = Icons.Default.Description,
            isAvailable = streamInfo != null
        )
    )

    val availableTabs = allTabs.filter { it.isAvailable }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { availableTabs.size }
    )
    when (uiState.pageState) {
        VideoDetailPageState.HIDDEN -> {}
        VideoDetailPageState.FULLSCREEN_PLAYER -> {
            if (streamInfo != null && mediaController != null) {
                BackHandler {
                    viewModel.toggleFullscreenPlayer()
                }
                VideoPlayer(
                    mediaController = mediaController!!,
                    streamInfo = streamInfo,
                    onFullScreenClicked = { viewModel.toggleFullscreenPlayer() },
                    modifier = Modifier.fillMaxSize(),
                    danmakuPool = uiState.currentDanmaku,
                    gestureSettings = PlayerGestureSettings(
                        swipeSeekEnabled = false,
                        volumeGestureEnabled = SharedContext.settingsManager.getBoolean("volume_gesture_control_key", true),
                        brightnessGestureEnabled = SharedContext.settingsManager.getBoolean("brightness_gesture_control_key", true),
                        fullscreenGestureEnabled = SharedContext.settingsManager.getBoolean("fullscreen_gesture_control_key", true)
                    ),
                    danmakuEnabled = uiState.danmakuEnabled,
                    onToggleDanmaku = { viewModel.toggleDanmaku() },
                    sponsorBlockSegments = uiState.currentSponsorBlock.segments
                )
            }
        }
        VideoDetailPageState.BOTTOM_PLAYER, VideoDetailPageState.DETAIL_PAGE -> {
            if (uiState.pageState == VideoDetailPageState.DETAIL_PAGE) {
                BackHandler {
                    if (!viewModel.navigateBack()) {
                        viewModel.showAsBottomPlayer()
                        showAsBottomTask()
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        layoutHeightPx = size.height.toFloat()
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    when (draggableState.currentValue) {
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
                                                val errorRow = DatabaseOperations.getErrorLogById(uiState.common.error!!.errorId)
                                                viewModel.loadVideoDetails(errorRow!!.request!!, serviceId = errorRow.service_id)
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
                                } else if (mediaController != null) {
                                    // Normal state: show full video detail UI
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(16f / 9f)
                                            .anchoredDraggable(
                                                state = draggableState,
                                                orientation = Orientation.Vertical,
                                            )
                                            .then(
                                                if (draggableState.currentValue == VideoDetailPageState.BOTTOM_PLAYER) {
                                                    Modifier.zIndex(1f)
                                                } else Modifier
                                            )
                                    ) {
                                        VideoPlayer(
                                            mediaController = mediaController!!,
                                            streamInfo = streamInfo,
                                            onFullScreenClicked = { viewModel.toggleFullscreenPlayer() },
                                            modifier = Modifier.fillMaxSize(),
                                            danmakuPool = uiState.currentDanmaku,
                                            gestureSettings = PlayerGestureSettings(
                                                swipeSeekEnabled = false,
                                                volumeGestureEnabled = SharedContext.settingsManager.getBoolean("volume_gesture_control_key"),
                                                brightnessGestureEnabled = SharedContext.settingsManager.getBoolean("brightness_gesture_control_key"),
                                                fullscreenGestureEnabled = SharedContext.settingsManager.getBoolean("fullscreen_gesture_control_key")
                                            ),
                                            danmakuEnabled = uiState.danmakuEnabled,
                                            onToggleDanmaku = { viewModel.toggleDanmaku() },
                                            sponsorBlockSegments = uiState.currentSponsorBlock.segments
                                        )
                                    }

                                    // HorizontalPager with TabRow at bottom
                                    Column(modifier = Modifier.weight(1f)) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier
                                                .weight(1f),
                                            beyondViewportPageCount = 4
                                        ) { page ->
                                            val tabIndex = availableTabs[page]
                                            val onPlayAudioClick: () -> Unit = {
                                                mediaController?.let { controller ->
                                                    controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
                                                    if (controller.getCurrentMediaItem()?.mediaId != streamInfo.url) {
                                                        controller.playFromStreamInfo(streamInfo)
                                                    } else if (!controller.isPlaying) {
                                                        controller.play()
                                                    }
                                                }
                                            }
                                            val onAddToPlaylistClick: () -> Unit = { showPlaylistPopup = true }
                                            val onTimestampClick: (Long) -> Unit = { timestamp ->
                                                mediaController?.let {
                                                    if (it.currentMediaItem?.mediaId == streamInfo.url) {
                                                        it.seekTo(timestamp * 1000)
                                                    }
                                                }
                                            }

                                            when(page) {
                                                allTabs.indexOfFirst { it.title == stringResource(MR.strings.comments_tab_description) && it.isAvailable } -> {
                                                    CommentSection(
                                                        streamInfo = streamInfo,
                                                        navController = navController,
                                                        onPlayAudioClick = onPlayAudioClick,
                                                        onAddToPlaylistClick = onAddToPlaylistClick,
                                                        onTimestampClick = onTimestampClick
                                                    )
                                                }
                                                allTabs.indexOfFirst { it.title == stringResource(MR.strings.related_videos) && it.isAvailable } -> {
                                                    RelatedItemSection(
                                                        streamInfo = streamInfo,
                                                        navController = navController,
                                                        onPlayAudioClick = onPlayAudioClick,
                                                        onAddToPlaylistClick = onAddToPlaylistClick
                                                    )
                                                }
                                                allTabs.indexOfFirst { it.title == stringResource(MR.strings.sponsor_block) && it.isAvailable } -> {
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
                                                            streamInfo = streamInfo,
                                                            navController = navController,
                                                            onPlayAudioClick = onPlayAudioClick,
                                                            onAddToPlaylistClick = onAddToPlaylistClick,
                                                            segments = sponsorBlockState.segments,
                                                            onStart = { mediaController?.currentPosition },
                                                            onEnd = { mediaController?.currentPosition }
                                                        )
                                                    }
                                                }
                                                allTabs.indexOfFirst { it.title == stringResource(MR.strings.description_tab) && it.isAvailable } -> {
                                                    DescriptionSection(
                                                        streamInfo = streamInfo,
                                                        navController = navController,
                                                        onPlayAudioClick = onPlayAudioClick,
                                                        onAddToPlaylistClick = onAddToPlaylistClick,
                                                        onTimestampClick = onTimestampClick
                                                    )
                                                }
                                            }
                                        }

                                        if (availableTabs.isNotEmpty()) {
                                            TabRow(
                                                selectedTabIndex = pagerState.currentPage,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                availableTabs.forEachIndexed { index, tab ->
                                                    Tab(
                                                        selected = pagerState.currentPage == index,
                                                        onClick = {
                                                            scope.launch {
                                                                pagerState.animateScrollToPage(index)
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
                        }
                        VideoDetailPageState.BOTTOM_PLAYER -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()) {
                                BottomSheetContent(
                                    mediaController = mediaController
                                )
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
}

@Composable
private fun BottomSheetContent(
    mediaController: MediaController?
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentMediaItem by remember { mutableStateOf<MediaItem?>(null) }

    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // 检查播放状态变化
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    isPlaying = player.isPlaying
                }

                // 检查媒体项变化
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    currentMediaItem = player.currentMediaItem
                }
            }
        }

        mediaController?.let { controller ->
            controller.addListener(listener)
            // 初始化当前状态
            isPlaying = controller.isPlaying
            currentMediaItem = controller.currentMediaItem
        }

        onDispose {
            mediaController?.removeListener(listener)
        }
    }

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
            model = currentMediaItem?.mediaMetadata?.artworkUri,
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
                text = currentMediaItem?.mediaMetadata?.title.toString(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentMediaItem?.mediaMetadata?.artist.toString(),
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
                    mediaController?.let { controller ->
                        if (controller.isPlaying) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
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
                    mediaController?.stopService()
                    SharedContext.sharedVideoDetailViewModel.hide()
                }
                .padding(8.dp)
        )
    }
}
