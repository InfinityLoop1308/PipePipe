package project.pipepipe.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.infoitem.StreamInfo


@Composable
fun ActionButtons(
    onPlayAudioClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {},
    streamInfo: StreamInfo,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionControlButton(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            text = stringResource(MR.strings.action_add_to),
            onClick = onAddToPlaylistClick,
            modifier = Modifier.weight(1f)
        )
        ActionControlButton(
            icon = Icons.Default.Headset,
            text = stringResource(MR.strings.controls_background_title),
            onClick = onPlayAudioClick,
            modifier = Modifier.weight(1f)
        )
        ActionControlButton(
            icon = Icons.Default.Share,
            text = stringResource(MR.strings.share),
            onClick = {
                SharedContext.platformActions.share(streamInfo.url, streamInfo.name)
            },
            modifier = Modifier.weight(1f)
        )
        ActionControlButton(
            icon = Icons.Default.Download,
            text = stringResource(MR.strings.download),
            onClick = {
                SharedContext.showDownloadFormatDialog(streamInfo)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionControlButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onClick() }
            .padding(top = 12.dp, bottom = 4.dp)
            .wrapContentWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(22.dp)
        )
//        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

