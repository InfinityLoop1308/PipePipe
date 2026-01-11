package project.pipepipe.app.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.download.DownloadManagerHolder
import project.pipepipe.app.download.YtDlpFormatHelper
import project.pipepipe.app.uistate.DownloadType


class AndroidMenuItems : PlatformMenuItems {
    private val _showDownloadDialog = mutableStateOf(false)

    @Composable
    override fun localPlaylistMenuItems() {
        DropdownMenuItem(
            text = { Text(stringResource(MR.strings.download)) },
            onClick = {
                showDownloadDialog()
            },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
        )
    }

    @Composable
    override fun localPlaylistDialogs(playlistId: Long?) {
        if (_showDownloadDialog.value && playlistId != null) {
            DownloadTypeSelectionDialog(
                onDismiss = { hideDownloadDialog() },
                onVideoSelected = { startBatchDownload(DownloadType.VIDEO, playlistId) },
                onAudioSelected = { startBatchDownload(DownloadType.AUDIO, playlistId) }
            )
        }
    }

    fun showDownloadDialog() {
        _showDownloadDialog.value = true
    }

    fun hideDownloadDialog() {
        _showDownloadDialog.value = false
    }

    private fun startBatchDownload(type: DownloadType, playlistId: Long) {
        val formatId = if (type == DownloadType.VIDEO) {
            YtDlpFormatHelper.getFormatIdForVideo()
        } else {
            YtDlpFormatHelper.getFormatIdForAudio()
        }

        // Placeholder logic - will be modified later with playlistId
        // For now, this demonstrates the structure
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            DatabaseOperations.loadPlaylistsItemsFromDatabase(playlistId).forEach { streamInfo ->
                DownloadManagerHolder.instance.addDownload(
                    url = streamInfo.url,
                    title = streamInfo.name ?: "Unknown",
                    imageUrl = streamInfo.thumbnailUrl,
                    duration = if (type == DownloadType.SUBTITLE) 0 else streamInfo.duration?.toInt() ?: 0,
                    downloadType = type,
                    quality = "auto",
                    codec = "auto",
                    formatId = formatId
                )
            }
        }
        hideDownloadDialog()
    }

    @Composable
    private fun DownloadTypeSelectionDialog(
        onDismiss: () -> Unit,
        onVideoSelected: () -> Unit,
        onAudioSelected: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(MR.strings.download)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(MR.strings.download_dialog_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onVideoSelected) {
                    Text(stringResource(MR.strings.download_video_quality))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(MR.strings.cancel))
                    }
                    TextButton(onClick = onAudioSelected) {
                        Text(stringResource(MR.strings.download_audio_format))
                    }
                }
            }
        )
    }
}