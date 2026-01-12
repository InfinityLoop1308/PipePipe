package project.pipepipe.app.ui.item

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.screens.settings.buildLogJson
import project.pipepipe.app.uistate.DownloadItemState
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.uistate.DownloadType
import project.pipepipe.app.utils.formatAbsoluteTime
import project.pipepipe.app.utils.toDurationString
import kotlin.math.abs

private fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return "-"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return "${String.format(java.util.Locale.getDefault(), "%.1f", size)} ${units[unitIndex]}"
}

private fun calculateProgress(downloaded: Long?, total: Long?): Float {
    if (total == null || total <= 0 || downloaded == null) return 0f
    return minOf(abs(downloaded.toFloat() / total.toFloat()), 1.0f)
}

@Composable
fun DownloadItem(
    state: DownloadItemState,
    modifier: Modifier = Modifier,
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onOpenClick: (String) -> Unit = {}
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThumbnailSection(state)

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TitleRow(
                    context = context,
                    state = state,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onCancelClick = onCancelClick,
                    onDeleteClick = onDeleteClick,
                    onOpenClick = onOpenClick
                )

                TagsRow(state)

                BottomStatusSection(state)
            }
        }
    }
}

@Composable
private fun ThumbnailSection(state: DownloadItemState) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = state.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f))
        )

        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(bottomStart = 6.dp),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = when (state.downloadType) {
                    DownloadType.VIDEO -> Icons.Default.Movie
                    DownloadType.AUDIO -> Icons.Default.Audiotrack
                    DownloadType.SUBTITLE -> Icons.Default.Subtitles
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(4.dp)
                    .size(14.dp)
            )
        }
        if (state.duration > 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 6.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = state.duration.toLong().toDurationString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TitleRow(
    context: Context,
    state: DownloadItemState,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenClick: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 4.dp)
                    .offset(y = (-4).dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(MR.strings.download_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                when (state.status) {
                    DownloadStatus.COMPLETED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.open)) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                expanded = false
                                state.filePath?.let { onOpenClick(it) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onDeleteClick()
                            }
                        )
                    }

                    DownloadStatus.PAUSED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.start)) },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onResumeClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.cancel)) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onCancelClick()
                            }
                        )
                    }

                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.FETCHING_INFO, DownloadStatus.PREPROCESSING, DownloadStatus.POSTPROCESSING -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.pause)) },
                            leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onPauseClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.cancel)) },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onCancelClick()
                            }
                        )
                    }

                    DownloadStatus.FAILED -> {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.retry)) },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onResumeClick()
                            }
                        )
                        if (state.status == DownloadStatus.FAILED && state.errorLogId != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.copy)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    GlobalScope.launch {
                                        DatabaseOperations.getErrorLogById(state.errorLogId!!)?.let {
                                            SharedContext.platformActions.copyToClipboard(buildLogJson(it))
                                        }
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                expanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagsRow(state: DownloadItemState) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TagBadge(
            text = if (state.quality == "auto") stringResource(MR.strings.auto)else state.quality,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )

        // Status badge
        if (state.status == DownloadStatus.FAILED) {
            TagBadge(
                text = stringResource(MR.strings.download_failed),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun TagBadge(text: String, color: Color, contentColor: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomStatusSection(state: DownloadItemState) {
    if (state.status == DownloadStatus.COMPLETED) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBytes(state.totalBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatAbsoluteTime(state.finishedTimestamp!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (state.status) {
                        DownloadStatus.PAUSED -> stringResource(MR.strings.download_paused_status)
                        DownloadStatus.QUEUED -> stringResource(MR.strings.download_status_queued)
                        DownloadStatus.FETCHING_INFO -> stringResource(MR.strings.download_status_fetching_info)
                        DownloadStatus.PREPROCESSING -> stringResource(MR.strings.download_pre_processing)
                        DownloadStatus.POSTPROCESSING -> stringResource(MR.strings.download_post_processing)
                        DownloadStatus.FAILED -> "Failed: ${state.errorMessage?.take(30) ?: stringResource(MR.strings.download_unknown_error)}"
                        DownloadStatus.DOWNLOADING -> state.downloadSpeed
                            ?: stringResource(MR.strings.download_pre_processing)

                        else -> stringResource(MR.strings.download_status_downloading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state.status) {
                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.status != DownloadStatus.FAILED) {
                    Text(
                        text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            LinearProgressIndicator(
                progress = { calculateProgress(state.downloadedBytes, state.totalBytes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = when (state.status) {
                    DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
