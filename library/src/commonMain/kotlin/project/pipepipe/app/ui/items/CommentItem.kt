package project.pipepipe.app.ui.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.component.HtmlText
import project.pipepipe.app.supportingTextColor
import project.pipepipe.app.utils.formatCount
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.shared.infoitem.CommentInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    commentInfo: CommentInfo,
    onReplyButtonClick: () -> Unit = {},
    onChannelAvatarClick: () -> Unit?,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copiedText = stringResource(MR.strings.msg_copied)
    val clipboardManager = LocalClipboardManager.current
    Surface(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = { },
            onLongClick = {
                clipboardManager.setText(AnnotatedString(commentInfo.content ?: ""))
                ToastManager.show(copiedText)
            }
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            AsyncImage(
                model = commentInfo.authorAvatarUrl,
                contentDescription = stringResource(MR.strings.comment_user_avatar),
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClick = { onChannelAvatarClick() }, enabled = !SharedContext.isTv ),
                contentScale = ContentScale.Crop,
//                placeholder = painterResource(R.drawable.buddy),
//                error = painterResource(R.drawable.buddy)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if(commentInfo.isPinned == true) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(MR.strings.comment_pinned),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = commentInfo.authorName!!,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = supportingTextColor(),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .defaultMinSize(minHeight = 25.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    HtmlText(
                        color = MaterialTheme.colorScheme.onSurface,
                        text = commentInfo.content!!,
                        fontSize = 12.sp,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        onTimestampClick = onTimestampClick
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = stringResource(MR.strings.comment_likes),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = formatCount(commentInfo.likeCount?.toLong()),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val isHearted = commentInfo.isHeartedByUploader?: false
                    if (isHearted) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = stringResource(MR.strings.comment_creator_heart),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    commentInfo.uploadDate?.let {
                        Text(
                            text = formatRelativeTime(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if ((commentInfo.replyInfo != null) || (commentInfo.images?.size ?: 0) > 0){
                    Row(
                        modifier = Modifier
                            .wrapContentHeight()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val replyCount = commentInfo.replyCount ?: 0
                        if (commentInfo.replyInfo != null) {
                            Text(
                                text = stringResource(MR.strings.comment_replies, replyCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onReplyButtonClick() }
                            )
                        }
                        if (commentInfo.replyInfo != null && (commentInfo.images?.size ?: 0) > 0) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        val imageCount = commentInfo.images?.size ?: 0
                        if (imageCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.clickable {
                                    SharedContext.showImageViewer(commentInfo.images!!, 0)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(MR.strings.comment_view_pictures, imageCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

            }
        }
    }
}