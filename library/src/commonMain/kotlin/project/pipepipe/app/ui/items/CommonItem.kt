package project.pipepipe.app.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ColorHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.supportingTextColor
import project.pipepipe.app.utils.formatAbsoluteTime
import project.pipepipe.app.utils.formatCount
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.shared.infoitem.*

enum class DisplayType{
    ORIGIN,
    NAME_ONLY,
    STREAM_HISTORY
}

@Composable
fun CommonItem(
    item: Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showProvideDetailButton: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    displayType: DisplayType = DisplayType.ORIGIN,
    shouldUseSecondaryColor: Boolean = false,
    isGridLayout: Boolean = false,
    forceListLayout: Boolean = false,
    showNewItemBorder: Boolean = false,
    onSetAsCover: (() -> Unit)? = null,
) {
    when (item) {
        is ChannelInfo -> {
            if (isGridLayout && !forceListLayout) {
                ChannelGridItem(
                    item = item,
                    modifier = modifier,
                    onClick = onClick
                )
            } else {
                ChannelListItem(
                    item = item,
                    modifier = modifier,
                    onClick = onClick
                )
            }
        }
        else -> {
            if (isGridLayout) {
                StreamOrPlaylistGridItem(
                    item = item,
                    modifier = modifier.alpha(
                        alpha = if (shouldUseSecondaryColor) 0.6f else 1f
                    ),
                    onClick = onClick,
                    isDragging = isDragging,
                    onNavigateTo = onNavigateTo,
                    onDelete = onDelete,
                    showProvideDetailButton = showProvideDetailButton,
                    showDragHandle = showDragHandle,
                    dragHandleModifier = dragHandleModifier,
                    displayType = displayType,
                    showNewItemBorder = showNewItemBorder,
                    onSetAsCover = onSetAsCover
                )
            } else {
                StreamOrPlaylistListItem(
                    item = item,
                    modifier = modifier.alpha(
                        alpha = if (shouldUseSecondaryColor) 0.6f else 1f
                    ),
                    onClick = onClick,
                    isDragging = isDragging,
                    onNavigateTo = onNavigateTo,
                    onDelete = onDelete,
                    showDragHandle = showDragHandle,
                    dragHandleModifier = dragHandleModifier,
                    showProvideDetailButton = showProvideDetailButton,
                    displayType = displayType,
                    showNewItemBorder = showNewItemBorder,
                    onSetAsCover = onSetAsCover
                )
            }
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
                    text = "${formatCount(count)} ${stringResource(MR.strings.subscribers)}",
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
private fun ChannelGridItem(
    item: ChannelInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val itemWidth = 100.dp
    val avatarSize = 60.dp

    Column(
        modifier = modifier
            .width(itemWidth)
            .combinedClickable(
                onClick = { onClick() },
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 圆形头像 (较小且居中)
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // 订阅数
        item.subscriberCount?.let { count ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatCount(count)} ${stringResource(MR.strings.subscribers)}",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = supportingTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamOrPlaylistListItem(
    item: Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showProvideDetailButton: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    displayType: DisplayType = DisplayType.ORIGIN,
    showNewItemBorder: Boolean = false,
    onSetAsCover: (() -> Unit)? = null,
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
            isLive = item.isLive
        }
        is PlaylistInfo -> {
            streamCount = item.streamCount
            thumbnailUrl = item.thumbnailUrl
            uploaderName = item.uploaderName
            title = item.name
        }
        else -> throw Exception("Invalid item")
    }
    val viewsText = stringResource(MR.strings.video_info_views)
    val watchingText = stringResource(MR.strings.video_info_watching)
    val additionalDetails = buildString {
        when (displayType) {
            DisplayType.ORIGIN -> {
                views?.let { append("${formatCount(it)} ${if (!isLive) viewsText else watchingText}") }
                if (!uploadTimeText.isNullOrEmpty() && views != null) {
                    append(" • ")
                }
                uploadTimeText?.let { append(it) }
            }
            DisplayType.STREAM_HISTORY -> {
                item as StreamInfo
                item.localRepeatCount?.let { append("${formatCount(it)} $viewsText") }
                if (item.localLastViewDate != null && item.localRepeatCount != null) {
                    append(" • ")
                }
                item.localLastViewDate?.let { append(formatAbsoluteTime(it)) }
            }
            DisplayType.NAME_ONLY -> {}
        }


    }.takeIf { it.isNotEmpty() }

    val swipeEnqueueEnabled = item is StreamInfo &&
        SharedContext.settingsManager.getBoolean("swipe_enqueue_gesture_key", false)
    val msg = stringResource(MR.strings.enqueued)

    val content: @Composable () -> Unit = {
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
                                    onDelete = onDelete,
                                    showProvideDetailButton = showProvideDetailButton,
                                    onSetAsCover = onSetAsCover
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
                    text = duration.toDurationString(),
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
                        .background(ColorHelper.parseHexColor("#FFD700"), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
            }

            // NEW badge for new items (List mode)
            if (showNewItemBorder && item is StreamInfo) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }

            if (progress != null && duration != null && progress > 0) {
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
                contentDescription = stringResource(MR.strings.detail_drag_description),
                modifier = dragHandleModifier
                    .align(Alignment.CenterVertically)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }

    if (swipeEnqueueEnabled) {
        val dismissState = rememberSwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { totalDistance -> totalDistance * 0.6f },
            confirmValueChange = { newValue ->
                if (newValue == SwipeToDismissBoxValue.StartToEnd) {
                    SharedContext.platformMediaController?.enqueue(item)
                    ToastManager.show(msg)
                }
                false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = false,
            content = { content() },
            backgroundContent = {},
        )
    } else {
        content()
    }
}

@Composable
private fun StreamOrPlaylistGridItem(
    item: Info,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    onNavigateTo: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showProvideDetailButton: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    displayType: DisplayType = DisplayType.ORIGIN,
    showNewItemBorder: Boolean = false,
    onSetAsCover: (() -> Unit)? = null,
) {
    var thumbnailUrl: String? = null
    var title: String
    var uploaderName: String? = null
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
            isLive = item.isLive
        }
        is PlaylistInfo -> {
            streamCount = item.streamCount
            thumbnailUrl = item.thumbnailUrl
            uploaderName = item.uploaderName
            title = item.name
        }
        else -> throw Exception("Invalid item")
    }

    val viewsText = stringResource(MR.strings.video_info_views)
    val watchingText = stringResource(MR.strings.video_info_watching)
    val additionalDetails = buildString {
        when (displayType) {
            DisplayType.ORIGIN -> {
                views?.let { append("${formatCount(it)} ${if (!isLive) viewsText else watchingText}") }
                if (!uploadTimeText.isNullOrEmpty() && views != null) {
                    append(" • ")
                }
                uploadTimeText?.let { append(it) }
            }
            DisplayType.STREAM_HISTORY -> {
                item as StreamInfo
                item.localRepeatCount?.let { append("${formatCount(it)} $viewsText") }
                if (item.localLastViewDate != null && item.localRepeatCount != null) {
                    append(" • ")
                }
                item.localLastViewDate?.let { append(formatAbsoluteTime(it)) }
            }
            DisplayType.NAME_ONLY -> {}
        }
    }.takeIf { it.isNotEmpty() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    when (item) {
                        is StreamInfo -> {
                            SharedContext.bottomSheetMenuViewModel.show(
                                StreamInfoWithCallback(
                                    item,
                                    onNavigateTo = onNavigateTo,
                                    onDelete = onDelete,
                                    showProvideDetailButton = showProvideDetailButton,
                                    onSetAsCover = onSetAsCover
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
    ) {
        // Thumbnail with 16:9 aspect ratio
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
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
                    text = duration.toDurationString(),
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
                        .background(ColorHelper.parseHexColor("#FFD700"), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 0.dp)
                )
            }

            // NEW badge for new items (Grid mode)
            if (showNewItemBorder && item is StreamInfo) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
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
                        .fillMaxWidth(0.25f)
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

        Spacer(modifier = Modifier.height(8.dp))

        // Title with drag handle
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                ),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
            )

            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(MR.strings.detail_drag_description),
                    modifier = dragHandleModifier
                        .align(Alignment.TopEnd).scale(0.8f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,

                )
            }
        }

        // Uploader name
        uploaderName?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uploaderName,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = supportingTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
            )
        }

        // Additional details
        if (additionalDetails != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = additionalDetails,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = supportingTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
            )
        }
    }
}