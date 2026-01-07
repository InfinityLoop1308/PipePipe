package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext

@Composable
fun VideoDetailScreenBottomSheetContent() {
    val controller = SharedContext.platformMediaController!!
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
