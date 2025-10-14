package project.pipepipe.app.ui.item
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.app.utils.formatRelativeTime
import project.pipepipe.app.ui.theme.supportingTextColor
import project.pipepipe.shared.SharedContext

@Composable
fun CommentItem(
    commentInfo: CommentInfo,
    onItemClick: () -> Unit = {},
    onReplyButtonClick: () -> Unit = {},
    onChannelAvatarClick: () -> Unit?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() },
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
                contentDescription = "User avatar",
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable{ onChannelAvatarClick() },
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
                            contentDescription = "Pinned comment",
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
                    Text(
                        text = commentInfo.content!!,
                        fontSize = 12.sp,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
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
                            contentDescription = "Likes",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = commentInfo.likeCount.toString(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val isHearted = commentInfo.isHeartedByUploader?: false
                    if (isHearted) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Creator heart",
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
                                text = "$replyCount replies",
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
                                    text = "View pictures ($imageCount)",
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