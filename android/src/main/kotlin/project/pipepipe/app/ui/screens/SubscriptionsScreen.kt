package project.pipepipe.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.database.Feed_group
import project.pipepipe.database.Subscriptions
import project.pipepipe.app.utils.formatCount
import project.pipepipe.app.ui.viewmodel.SubscriptionsViewModel
import project.pipepipe.app.uistate.VideoDetailPageState
import java.net.URLEncoder

@Composable
fun SubscriptionsScreen(navController: NavController) {
    val viewModel: SubscriptionsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init()
    }
    val videoDetailUiState by SharedContext.sharedVideoDetailViewModel.uiState.collectAsState()

    LaunchedEffect(videoDetailUiState.pageState) {
        if (videoDetailUiState.pageState == VideoDetailPageState.BOTTOM_PLAYER
            && navController.currentDestination?.route == Screen.Main.route)
            viewModel.init()
    }

    SubscriptionsContent(
        isLoading = uiState.common.isLoading,
        channelGroups = uiState.feedGroups,
        subscriptions = uiState.subscriptions,
        onChannelGroupClick = { feedGroupId, name ->
            val route = Screen.Feed.createRoute(
                id = feedGroupId ?: -1,
                name = name
            )
            navController.navigate(route)
        },
        onSearchClick = { },
        onSubscriptionClick = { subscription ->
            val url = subscription.url ?: return@SubscriptionsContent
            val encodedUrl = URLEncoder.encode(url, "UTF-8")
            navController.navigate("channel?url=$encodedUrl&serviceId=${subscription.service_id}")
        },
        onCreateFeedGroup = { name, iconId ->
            viewModel.createFeedGroup(name, iconId)
        }
    )
}

@Composable
private fun SubscriptionsContent(
    isLoading: Boolean,
    channelGroups: List<Feed_group>,
    subscriptions: List<Subscriptions>,
    onChannelGroupClick: (Long?, String?) -> Unit,
    onSearchClick: () -> Unit,
    onSubscriptionClick: (Subscriptions) -> Unit,
    onCreateFeedGroup: (String, Int) -> Unit
) {
    var showCreateFeedGroupDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreateFeedGroupDialog) {
        CreateFeedGroupDialog(
            onDismissRequest = { showCreateFeedGroupDialog = false },
            onCreate = { name, iconId ->
                onCreateFeedGroup(name, iconId)
                showCreateFeedGroupDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(MR.strings.feed_groups),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(MR.strings.create_feed_group),
                        modifier = Modifier.clickable{ showCreateFeedGroupDialog = true }
                    )

                }
                Spacer(modifier = Modifier.height(16.dp))
                ChannelGroupsRow(
                    channelGroups = channelGroups,
                    onAllGroupsClick = { onChannelGroupClick(null, null) },
                    onChannelGroupClick = { group ->
                        onChannelGroupClick(group.uid, group.name)
                    },
                    showAll = true
                )
            }
        }
        item {
            Divider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        item { SubscriptionsHeader(onSearchClick = onSearchClick) }

        when {
            isLoading && subscriptions.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            subscriptions.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(MR.strings.no_subscriptions_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                items(subscriptions, key = { it.uid }) { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        onClick = { onSubscriptionClick(subscription) }
                    )
                }
                if (isLoading) {
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

@Composable
fun ChannelGroupsRow(
    channelGroups: List<Feed_group>,
    onAllGroupsClick: () -> Unit,
    onChannelGroupClick: (Feed_group) -> Unit,
    showAll: Boolean = false
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showAll) {
            item(key = "all") {
                ChannelGroupCard(
                    title = stringResource(MR.strings.all),
                    iconId = 0,
                    onClick = onAllGroupsClick
                )
            }
        }

        items(channelGroups, key = { it.uid }) { group ->
            ChannelGroupCard(
                title = group.name,
                iconId = group.icon_id.toInt(),
                onClick = { onChannelGroupClick(group) }
            )
        }
    }
}

@Composable
private fun ChannelGroupCard(
    title: String,
    iconId: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .width(72.dp)
            .height(48.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = categoryIconFor(iconId),
                contentDescription = title,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun SubscriptionsHeader(
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(MR.strings.tab_subscriptions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(MR.strings.search_subscriptions)
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscriptions,
    onClick: () -> Unit
) {
    val subscribersLabel = subscription.subscriber_count
        ?.takeIf { it > 0 }
        ?.let { "${formatCount(it)} ${stringResource(MR.strings.subscribers_text)}" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = subscription.avatar_url,
            contentDescription = "${subscription.name ?: subscription.url ?: stringResource(MR.strings.subscription)} avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.LightGray)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.name ?: subscription.url ?: stringResource(MR.strings.subscription),
                fontSize = 15.sp,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontWeight = FontWeight.Medium
            )
            subscribersLabel?.let {
                Text(
                    text = it,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateFeedGroupDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, Int) -> Unit
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var selectedIconId by rememberSaveable { mutableStateOf(0) }
    val iconOptions = remember { (0..38).toList() }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.new_feed_group)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(MR.strings.group_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(MR.strings.icon),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5), // 每行5个图标
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(iconOptions) { iconId ->
                        val isSelected = selectedIconId == iconId
                        Surface(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                            onClick = { selectedIconId = iconId }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIconFor(iconId),
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(groupName.trim(), selectedIconId) },
                enabled = groupName.isNotBlank()
            ) {
                Text(stringResource(MR.strings.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}

fun categoryIconFor(id: Int): ImageVector =
    when (id) {
        1 -> Icons.Default.MusicNote
        2 -> Icons.Default.School
        3 -> Icons.Default.FitnessCenter
        4 -> Icons.Default.SatelliteAlt
        5 -> Icons.Default.Computer
        6 -> Icons.Default.VideogameAsset
        7 -> Icons.Default.DirectionsBike
        8 -> Icons.Default.Campaign
        9 -> Icons.Default.Favorite
        10 -> Icons.Default.DirectionsCar
        11 -> Icons.Default.Motorcycle
        12 -> Icons.Default.TrendingUp
        13 -> Icons.Default.Movie
        14 -> Icons.Default.Backup
        15 -> Icons.Default.Palette
        16 -> Icons.Default.Person
        17 -> Icons.Default.People
        18 -> Icons.Default.AttachMoney
        19 -> Icons.Default.ChildCare
        20 -> Icons.Default.Fastfood
        21 -> Icons.Default.InsertEmoticon
        22 -> Icons.Default.Explore
        23 -> Icons.Default.Restaurant
        24 -> Icons.Default.Mic
        25 -> Icons.Default.Headset
        26 -> Icons.Default.Radio
        27 -> Icons.Default.ShoppingCart
        28 -> Icons.Default.WatchLater
        29 -> Icons.Default.Work
        30 -> Icons.Default.Whatshot
        31 -> Icons.Default.Tv
        32 -> Icons.Default.Bookmark
        33 -> Icons.Default.Pets
        34 -> Icons.Default.Public
        35 -> Icons.Default.Stars
        36 -> Icons.Default.WbSunny
        37 -> Icons.Default.RssFeed
        0, 38 -> Icons.Default.Subscriptions
        else -> Icons.Default.Subscriptions
    }