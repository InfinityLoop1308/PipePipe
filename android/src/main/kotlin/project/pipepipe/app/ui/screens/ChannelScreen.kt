package project.pipepipe.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import project.pipepipe.app.MR
import kotlinx.coroutines.launch
import project.pipepipe.app.global.StringResourceHelper
import project.pipepipe.app.SharedContext
import project.pipepipe.app.utils.formatCount
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.ChannelTabType
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.FeedGroupSelectionDialog
import project.pipepipe.app.ui.item.CommonItem
import project.pipepipe.app.ui.theme.supportingTextColor
import project.pipepipe.app.ui.viewmodel.ChannelViewModel
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.component.HtmlText
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelScreen(
    navController: NavController,
    channelUrl: String,
    serviceId: String
) {
    val context = LocalContext.current
    val viewModel: ChannelViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val tabs = uiState.channelInfo?.tabs.orEmpty()
    val hasDescription = !uiState.channelInfo?.description.isNullOrEmpty()
    val tabTypes = remember(tabs, hasDescription) {
        val types = tabs.map { it.type }.toMutableList()
        if (hasDescription) {
            types.add(ChannelTabType.INFO)
        }
        types
    }
    val tabTitles = remember(tabTypes) { tabTypes.map { it.name } }
    val hasTabs = tabTypes.isNotEmpty()

    val pagerState = rememberPagerState(pageCount = { if (hasTabs) tabTypes.size else 1 })
    val scope = rememberCoroutineScope()
    val videoListState = rememberLazyListState()
    val liveListState = rememberLazyListState()
    var showGroupDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var notificationMode by remember { mutableStateOf(0L) }

    val deferredTabLoaders = remember(channelUrl, serviceId, viewModel) {
        mapOf(
            ChannelTabType.LIVES to { viewModel.loadChannelLiveTab(uiState.channelInfo!!.tabs.first{it.type == ChannelTabType.LIVES}.url, serviceId) },
            ChannelTabType.PLAYLISTS to { viewModel.loadChannelPlaylistTab(uiState.channelInfo!!.tabs.first{it.type == ChannelTabType.PLAYLISTS}.url, serviceId) },
            ChannelTabType.ALBUMS to { viewModel.loadChannelAlbumTab(uiState.channelInfo!!.tabs.first{it.type == ChannelTabType.ALBUMS}.url, serviceId) },
            // 在这里继续为其它 Tab 注册加载逻辑
        )
    }
    val tabLoadRequests = remember(channelUrl) { mutableStateMapOf<ChannelTabType, Boolean>()}

    LaunchedEffect(channelUrl) {
        tabLoadRequests.clear()
        viewModel.loadChannelMainTab(channelUrl, serviceId)
        viewModel.checkSubscriptionStatus(channelUrl)

        // Load notification mode
        val subscription = DatabaseOperations.getSubscriptionByUrl(channelUrl)
        notificationMode = subscription?.notification_mode ?: 1L
    }

    LaunchedEffect(tabTypes, pagerState, deferredTabLoaders) {
        if (tabTypes.isEmpty()) return@LaunchedEffect

        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val selectedType = tabTypes.getOrNull(pageIndex) ?: return@collect
                if (tabLoadRequests[selectedType] == true) return@collect

                deferredTabLoaders[selectedType]?.let { loader ->
                    loader()
                    tabLoadRequests[selectedType] = true
                }
            }
    }



    Column(modifier = Modifier.fillMaxSize()) {
        CustomTopBar(
            defaultTitleText = uiState.channelInfo?.name ?: stringResource(MR.strings.channel),
            actions = {
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        // Notification toggle - only show when subscribed
                        if (uiState.isSubscribed) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (notificationMode == 1L) MR.strings.channel_disable_notifications
                                            else MR.strings.channel_enable_notifications
                                        )
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        val newMode = if (notificationMode == 1L) 0L else 1L
                                        DatabaseOperations.updateSubscriptionNotificationMode(
                                            channelUrl,
                                            newMode
                                        )
                                        notificationMode = newMode
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (notificationMode == 1L) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                        contentDescription = null
                                    )
                                }
                            )
                        }

                        // Share
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.share)) },
                            onClick = {
                                showMoreMenu = false
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, channelUrl)
                                    putExtra(Intent.EXTRA_TITLE, uiState.channelInfo?.name ?: "")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        )

        if (uiState.common.isLoading && uiState.channelInfo == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.channelInfo?.let { channelInfo ->
                ChannelHeader(
                    info = channelInfo,
                    isSubscribed = uiState.isSubscribed,
                    onSubscribeClick = {
                        viewModel.toggleSubscription(channelInfo)
                    },
                    onAddToGroupClick = {
                        showGroupDialog = true
                    }
                )
            }
            TabRow(selectedTabIndex = pagerState.currentPage, modifier = Modifier.height(44.dp)) {
                tabTypes.forEachIndexed { index, item ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.height(44.dp),
                        text = { Text(text = StringResourceHelper.getTranslatedTabString(item.name.lowercase()).uppercase()) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(top = 4.dp).padding(horizontal = 16.dp),
            ) { page ->
                when (tabTypes.getOrNull(page)) {
                    ChannelTabType.INFO -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            item {
                                HtmlText(
                                    text = uiState.channelInfo?.description!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = supportingTextColor()
                                )
                            }
                        }
                    }

                    ChannelTabType.LIVES -> TabContent(
                        listState = liveListState,
                        items = uiState.liveTab.itemList,
                        isLoading = uiState.common.isLoading,
                        hasMore = uiState.liveTab.nextPageUrl != null,
                        onLoadMore = { viewModel.loadLiveTabMoreItems(serviceId) },
                        getUrl = { it.url },
                        onItemClick = { item ->
                            SharedContext.sharedVideoDetailViewModel.loadVideoDetails(
                                item.url,
                                item.serviceId
                            )
                        }
                    )

                    ChannelTabType.VIDEOS -> TabContent(
                        listState = videoListState,
                        items = uiState.videoTab.itemList,
                        isLoading = uiState.common.isLoading,
                        hasMore = uiState.videoTab.nextPageUrl != null,
                        onLoadMore = { viewModel.loadMainTabMoreItems(serviceId) },
                        getUrl = { it.url },
                        onItemClick = { item ->
                            SharedContext.sharedVideoDetailViewModel.loadVideoDetails(
                                item.url,
                                item.serviceId
                            )
                        }
                    )

                    ChannelTabType.PLAYLISTS -> TabContent(
                        listState = rememberLazyListState(),
                        items = uiState.playlistTab.itemList,
                        isLoading = uiState.common.isLoading,
                        hasMore = uiState.playlistTab.nextPageUrl != null,
                        onLoadMore = { viewModel.loadPlaylistTabMoreItems(serviceId) },
                        getUrl = { it.url },
                        onItemClick = { item -> navController.navigate(
                            "playlist?url=" + URLEncoder.encode(item.url, "UTF-8")
                                    + if(item.serviceId != null) "&serviceId=${item.serviceId}" else ""
                        )}
                    )

                    ChannelTabType.ALBUMS -> TabContent(
                        listState = rememberLazyListState(),
                        items = uiState.albumTab.itemList,
                        isLoading = uiState.common.isLoading,
                        hasMore = uiState.albumTab.nextPageUrl != null,
                        onLoadMore = { viewModel.loadAlbumTabMoreItems(serviceId) },
                        getUrl = { it.url },
                        onItemClick = { item -> navController.navigate(
                            "playlist?url=" + URLEncoder.encode(item.url, "UTF-8")
                                    + if(item.serviceId != null) "&serviceId=${item.serviceId}" else ""
                        )}
                    )

                    else -> {
                        val placeholderTitle = tabTitles.getOrNull(page)
                            ?: tabTypes.getOrNull(page)?.name.orEmpty()
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = placeholderTitle)
                        }
                    }
                }
            }
        }
    }

    // Feed Group Selection Dialog
    if (showGroupDialog && uiState.channelInfo != null) {
        FeedGroupSelectionDialog(
            channelInfo = uiState.channelInfo!!,
            onDismiss = { showGroupDialog = false },
            onConfirm = { selectedGroups ->
                viewModel.updateFeedGroups(uiState.channelInfo!!, selectedGroups)
            }
        )
    }
}

@Composable
private fun ChannelHeader(
    info: ChannelInfo,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    onAddToGroupClick: () -> Unit
) {

    Box(modifier = Modifier.fillMaxWidth()) {
        // Banner Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {
            AsyncImage(
                model = info.bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Profile Image positioned absolutely
        Surface(
            modifier = Modifier
                .size(72.dp)
                .offset(y = 48.dp, x = 8.dp), // 向下移动以在 banner 下方
            shape = CircleShape,
            tonalElevation = 2.dp
        ) {
            AsyncImage(
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                model = info.thumbnailUrl
            )
        }

        // Name and Subscribe button Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 70.dp), // 添加底部间距
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.width(72.dp)) // 保留头像空间

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                info.subscriberCount?.let {
                    Text(
                        text = "${formatCount(it)} ${stringResource(MR.strings.subscribers_text)}",
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        fontSize = 11.sp,
                        color = supportingTextColor(),
                        fontWeight = Normal
                    )
                }
            }

            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAddToGroupClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(MR.strings.add_to_group),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Button(
                    onClick = onSubscribeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) Color.Gray else Color.Red.copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = (if (isSubscribed) stringResource(MR.strings.subscribed_button_title) else stringResource(MR.strings.subscribe_button_title)).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
@Composable
private fun <T: Info> TabContent(
    listState: LazyListState,
    items: List<T>,
    isLoading: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    getUrl: (T) -> String,
    onItemClick: (T) -> Unit
) {
    val uniqueItems = remember(items) { items.distinctBy { getUrl(it) } }
    val loadMoreInvoker = rememberUpdatedState(onLoadMore)

    LaunchedEffect(listState, hasMore, isLoading, uniqueItems) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = layoutInfo.totalItemsCount
            lastVisibleIndex to totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItemsCount) ->
                val isAtBottom = totalItemsCount > 0 && lastVisibleIndex == totalItemsCount - 1
                if (isAtBottom && hasMore && !isLoading) {
                    loadMoreInvoker.value.invoke()
                }
            }
    }

    if (isLoading && uniqueItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp)
        ) {
            items(
                items = uniqueItems,
                key = { getUrl(it) }
            ) { item ->
                CommonItem(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = { onItemClick(item) }
                )
            }
            if (isLoading && uniqueItems.isNotEmpty()) {
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
