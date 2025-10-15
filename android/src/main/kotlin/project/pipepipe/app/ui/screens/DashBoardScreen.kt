package project.pipepipe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import project.pipepipe.database.Feed_group
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.ui.viewmodel.DashboardViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import project.pipepipe.shared.infoitem.StreamType
import project.pipepipe.shared.toText
import java.net.URLEncoder


@Composable
fun DashboardScreen(navController: NavController) {
    val viewModel: DashboardViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(MR.strings.pinned_feed_groups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChannelGroupsRow(
                channelGroups = uiState.feedGroups,
                onAllGroupsClick = { },
                onChannelGroupClick = { group ->
                    val route = Screen.Feed.createRoute(
                        id = group.uid,
                        name = group.name
                    )
                    navController.navigate(route)
                }
            )
        }


        Divider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(MR.strings.pinned_playlists),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(
                    items = uiState.playlists,
                    key = { it.url }
                ) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { navController.navigate("playlist?url=" + URLEncoder.encode(playlist.url, "UTF-8")) }
                    )
                }
            }
        }

        Divider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Screen.History.route)},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(MR.strings.title_activity_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(uiState.historyItems) { item ->
                    HistoryCard(
                        item = item,
                        onClick = { SharedContext.sharedVideoDetailViewModel.loadVideoDetails(item.url) }
                    )
                }
            }
        }

        Divider()
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 16.dp, vertical = 16.dp)
//        ) {
//            Text(
//                text = "Trendings",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.SemiBold
//            )
//            Spacer(modifier = Modifier.height(12.dp))
//            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
//                trendingItems.forEachIndexed { index, trending ->
//                    TrendingItemRow(
//                        trending = trending,
//                        showDivider = index != trendingItems.lastIndex
//                    )
//                }
//            }
//        }
    }
}


@Composable
private fun HistoryCard(
    item: StreamInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = item.streamType == StreamType.LIVE_STREAM
    val duration = item.duration
    val progressFraction = item.progress?.let { progress ->
        duration?.takeIf { it > 0 }?.let {
            (progress.toFloat() / (it * 1000f)).coerceIn(0f, 1f)
        }
    }

    Column(
        modifier = modifier
            .width(128.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    SharedContext.bottomSheetMenuViewModel.show(
                        StreamInfoWithCallback(
                            item
                        )
                    )
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            when {
                isLive -> {
                    Text(
                        text = stringResource(MR.strings.duration_live).uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                duration != null -> {
                    Text(
                        text = duration.toText(),
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            progressFraction?.let { fraction ->
                if (!fraction.isNaN() && !fraction.isInfinite()) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color.Red,
                        trackColor = Color(0x33FFFFFF)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name.orEmpty(),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(128.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    SharedContext.bottomSheetMenuViewModel.show(
                        playlist
                    )
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            playlist.streamCount.let { count ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(IntrinsicSize.Min)
                        .align(Alignment.CenterEnd)
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "$count",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

//@Composable
//private fun TrendingItemRow(
//    trending: Trending,
//    showDivider: Boolean,
//    modifier: Modifier = Modifier
//) {
//    Column(modifier = modifier.fillMaxWidth()) {
//        Text(
//            text = trending.title,
//            style = MaterialTheme.typography.titleSmall,
//            fontWeight = FontWeight.Medium
//        )
//        trending.subtitle?.takeIf { it.isNotBlank() }?.let {
//            Spacer(modifier = Modifier.height(4.dp))
//            Text(
//                text = it,
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//        if (showDivider) {
//            Spacer(modifier = Modifier.height(12.dp))
//            Divider()
//        }
//    }
//}

@Composable
private fun Thumbnail(
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}