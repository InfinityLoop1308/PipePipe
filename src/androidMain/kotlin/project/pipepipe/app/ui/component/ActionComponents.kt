package project.pipepipe.app.ui.component

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.shared.infoitem.StreamInfo


@Composable
fun ActionButtons(
    onPlayAudioClick: () -> Unit = {},
    onAddToPlaylistClick: () -> Unit = {},
    streamInfo: StreamInfo,
) {
    val context = LocalContext.current
    var showDownloadDialog by remember { mutableStateOf(false) }

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
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, streamInfo.url)
                    putExtra(Intent.EXTRA_TITLE, streamInfo.name)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            },
            modifier = Modifier.weight(1f)
        )
        ActionControlButton(
            icon = Icons.Default.Download,
            text = stringResource(MR.strings.download),
            onClick = {
                handleDownload(
                    context = context,
                    url = streamInfo.url,
                    onShowDialog = { show ->
                        showDownloadDialog = show
                    }
                )
            },
            modifier = Modifier.weight(1f)
        )
    }

    if (showDownloadDialog) {
        DownloadInfoDialog(
            url = streamInfo.url,
            onDismiss = { showDownloadDialog = false }
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

