package project.pipepipe.app.ui.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.ui.theme.AppDimens
import java.text.NumberFormat
import java.util.*
import project.pipepipe.shared.formatCount
import project.pipepipe.shared.infoitem.StreamType

@Composable
fun VideoTitleSection(name: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {  }
            .padding(start = 16.dp, end = 4.dp)
    ) {
        Text(
            text = name ?: "Loading title...",
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp, top = 10.dp)
                .padding(end = 32.dp),
        )

        Icon(
            imageVector = Icons.Default.CopyAll,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 10.dp)
                .size(16.dp)
        )
    }
}

@Composable
fun VideoDetailSection(streamInfo: StreamInfo) {
    val isLive = streamInfo.streamType == StreamType.LIVE_STREAM
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = streamInfo.uploaderAvatarUrl, // 假设这是你的图片URL字段
                contentDescription = "Uploader Avatar", // 用于无障碍功能
                modifier = Modifier
                    .size(AppDimens.video_item_detail_uploader_image_size)
                    .clip(CircleShape), // 使用 clip 将图片裁剪成圆形
                contentScale = ContentScale.Crop // 确保图片填满圆形区域，不会变形
            )

            Spacer(modifier = Modifier.width(AppDimens.margin_small))

            Column(
                modifier = Modifier.fillMaxHeight().padding(top = 2.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = streamInfo.uploaderName ?: "Channel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
                streamInfo.uploaderSubscriberCount?.let {
                    Text(
                        text = formatCount(streamInfo.uploaderSubscriberCount) + " Subscribers",
                        fontSize = 12.sp,
                        maxLines = 1,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${NumberFormat.getNumberInstance(Locale.US).format(streamInfo.viewCount ?: 0)} ${if (!isLive)"views" else "watching"}",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )

            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp, start = 1.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Likes",
                    modifier =  Modifier.size(16.dp)
                )
                Text(
                    text = formatCount(streamInfo.likeCount),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ),
                )

                streamInfo.dislikeCount?.let {
                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = "Dislikes",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatCount(streamInfo.dislikeCount),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp),
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        ),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    MaterialTheme {
        VideoDetailSection(StreamInfo(
            duration = 320,
            url = "abc.xyz",
            serviceId = "BILIBILI",
            name = "自动分配间距自动分配间距自动分配间距自动分配间距",
            uploadDate = 1721913917,
            viewCount = 72817,
            uploaderName = "SUCCESSFUL",
            uploaderSubscriberCount = 21391
        ))
    }
}