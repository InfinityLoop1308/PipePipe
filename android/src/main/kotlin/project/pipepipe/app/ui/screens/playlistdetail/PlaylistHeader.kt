package project.pipepipe.app.ui.screens.playlistdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR
import project.pipepipe.app.uistate.PlaylistType
import project.pipepipe.app.utils.formatCount
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.app.utils.toDurationString

@Composable
fun PlaylistHeaderSection(
    playlistType: PlaylistType,
    playlistName: String,
    thumbnailUrl: String?,
    streamCount: Long?,
    totalDuration: Long,
    feedLastUpdated: Long?,
    isRefreshing: Boolean,
    hasItems: Boolean,
    onRefreshClick: () -> Unit,
    onPlayAllClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        if (playlistType != PlaylistType.FEED) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .height(72.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = MR.strings.playlist_info_summary.desc().toString(context = context)
                            .format(formatCount(streamCount), totalDuration.toDurationString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(MR.strings.feed_oldest_subscription_update).format(
                        feedLastUpdated?.let { formatRelativeTime(it) } ?: stringResource(MR.strings.never)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onRefreshClick,
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
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
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        if (hasItems) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalButton(
                    onClick = onPlayAllClick,
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
