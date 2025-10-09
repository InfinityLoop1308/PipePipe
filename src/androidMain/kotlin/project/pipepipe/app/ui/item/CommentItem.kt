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
import coil.compose.AsyncImage
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.shared.formatRelativeTime
import project.pipepipe.app.ui.theme.supportingTextColor

@Composable
fun CommentItem(
    commentInfo: CommentInfo,
    onItemClick: () -> Unit = {},
    onPictureButtonClick: () -> Unit = {},
    onReplyButtonClick: () -> Unit = {},
    modifier: Modifier = Modifier
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
                    .clip(CircleShape),
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
                        Spacer(modifier = Modifier.weight(1f))
                        val imageCount = commentInfo.images?.size ?: 0
                        if (imageCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.clickable { onPictureButtonClick() }
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

//@Preview(showBackground = true)
//@Composable
//fun CommentItemPreview() {
//    MaterialTheme {
//        CommentItem(
//            commentInfo = CommentInfo(
//                authorName = "未来龙皇冼衣龙女",
//                content = "整部剧对于五个人羁绊的塑造不如一句技能描述[笑哭]",
//                likeCount = 907,
//                replyCount = 13,
//                isPinned = null,
//                isHeartedByUploader = false,
//                authorAvatarUrl = "https://i2.hdslb.com/bfs/face/3f6fa50c25c4fb5d6ec83329a1c6db974300259e.jpg",
//                url = "https://api.bilibili.com/x/v2/reply/reply?type=1&ps=10&pn=1&web_location=333.788&oid=115132453683540&root=274856640352",
//                authorUrl = "https://space.bilibili.com/3546697194015143",
//                authorVerified = null,
//                uploadDate = 1756783356000,
//                replyEndPoint = EndPoint("https://api.bilibili.com/x/v2/reply/reply?type=1&ps=10&pn=1&web_location=333.788&oid=115132453683540&root=274856640352"),
//                images = listOf("http://i0.hdslb.com/bfs/new_dyn/49099d776eb9e40ce6992de46d17b7e53546697194015143.jpg"),
//                serviceId = null
//            )
//        )
//    }
//}
