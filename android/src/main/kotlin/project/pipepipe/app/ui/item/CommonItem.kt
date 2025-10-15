package project.pipepipe.app.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import project.pipepipe.app.SharedContext
import project.pipepipe.shared.formatCount
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.StreamInfoWithCallback
import project.pipepipe.shared.toText
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.shared.infoitem.StreamType
import project.pipepipe.app.ui.theme.supportingTextColor
import project.pipepipe.app.utils.formatAbsoluteTime

enum class DisplayType{
    ORIGIN,
    NAME_ONLY,
    STREAM_HISTORY
}

@Composable
fun MediaListItem(
    item: Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    displayType: DisplayType = DisplayType.ORIGIN
) {
    when (item) {
        is ChannelInfo -> {
            ChannelListItem(
                item = item,
                modifier = modifier,
                onClick = onClick
            )
        }
        else -> {
            StreamOrPlaylistListItem(
                item = item,
                modifier = modifier,
                onClick = onClick,
                isDragging = isDragging,
                onNavigateTo = onNavigateTo,
                onDelete = onDelete,
                showDragHandle = showDragHandle,
                dragHandleModifier = dragHandleModifier,
                displayType = displayType
            )
        }
    }
}

@Composable
private fun ChannelListItem(
    item: ChannelInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val itemHeight = 70.dp
    val avatarSize = 60.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    SharedContext.bottomSheetMenuViewModel.show(item)
                }
            )
            .padding(horizontal = 2.dp, vertical = 5.dp)
    ) {
        // 圆形头像
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 频道信息
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // 频道名
            Text(
                text = item.name,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 描述
            item.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = supportingTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 订阅数
            item.subscriberCount?.let { count ->
                Text(
                    text = "${formatCount(count)} ${stringResource(MR.strings.subscribers) }",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = supportingTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StreamOrPlaylistListItem(
    item: Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    displayType: DisplayType = DisplayType.ORIGIN
) {
    var thumbnailUrl: String? = null
    var title: String
    var uploaderName: String? = null
    val thumbnailWidth: Dp = 120.dp
    val thumbnailHeight: Dp = 70.dp
    var duration: Long? = null
    var uploadTimeText: String? = null
    var views: Long? = null
    var progress: Long? = null
    var streamCount: Long? = null
    var isLive: Boolean = false
    when (item) {
        is StreamInfo -> {
            duration = item.duration
            uploadTimeText = item.uploadDate?.let { formatRelativeTime(it) }
            views = item.viewCount
            progress = item.progress
            thumbnailUrl = item.thumbnailUrl
            uploaderName = item.uploaderName
            title = item.name!!
            isLive = item.streamType == StreamType.LIVE_STREAM
        }
        is PlaylistInfo -> {
            streamCount = item.streamCount
            thumbnailUrl = item.thumbnailUrl
            uploaderName = item.uploaderName
            title = item.name
        }
        else -> throw Exception("Invalid item")
    }
    val additionalDetails = buildString {
        when (displayType) {
            DisplayType.ORIGIN -> {
                views?.let { append("${formatCount(it)} ${if (!isLive)"views" else "watching"}") }
                if (!uploadTimeText.isNullOrEmpty() && views != null) {
                    append(" • ")
                }
                uploadTimeText?.let { append(it) }
            }
            DisplayType.STREAM_HISTORY -> {
                item as StreamInfo
                item.localRepeatCount?.let { append("${formatCount(it)} views") }
                if (item.localLastViewDate != null && item.localRepeatCount != null) {
                    append(" • ")
                }
                item.localLastViewDate?.let { append(formatAbsoluteTime(it)) }
            }
            DisplayType.NAME_ONLY -> {}
        }


    }.takeIf { it.isNotEmpty() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbnailHeight)
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    when (item) {
                        is StreamInfo -> {
                            SharedContext.bottomSheetMenuViewModel.show(
                                StreamInfoWithCallback(
                                    item,
                                    onNavigateTo = onNavigateTo,
                                    onDelete = onDelete
                                )
                            )
                        }

                        is PlaylistInfo -> {
                            SharedContext.bottomSheetMenuViewModel.show(item)
                        }

                        else -> {}
                    }
                }
            )
            .padding(horizontal = 2.dp)
            .background(
                color = if (isDragging)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                else
                    Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (isDragging) {
                    Modifier.padding(4.dp)
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .width(thumbnailWidth)
                .height(thumbnailHeight)
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize(),
                contentScale = ContentScale.Crop
            )

            if (isLive) {
                Text(
                    text = stringResource(MR.strings.duration_live).uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
            } else if (duration != null) {
                Text(
                    text = duration.toText(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
            }
            if (item is StreamInfo && item.isPaid) {
                Text(
                    text = stringResource(MR.strings.paid_video).uppercase(),
                    color = Color.Black,
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(SponsorBlockUtils.parseHexColor("#FFD700"), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
            }
            if (progress != null && duration != null) {
                LinearProgressIndicator(
                    progress = { progress / duration / 1000f },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color.Red,
                    trackColor = Color(0x33FFFFFF),
                    drawStopIndicator = {}
                )
            }
            if (streamCount != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(thumbnailWidth / 4)
                        .align(Alignment.CenterEnd)
                        .background(Color(0x80000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "$streamCount",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = title,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            uploaderName?.let {
                Text(
                    text = uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = supportingTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (additionalDetails != null) {
                Text(
                    text = additionalDetails,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = supportingTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDragHandle) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragHandleModifier
                    .align(Alignment.CenterVertically)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}