package project.pipepipe.app.ui.component

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.app.utils.formatCount
import project.pipepipe.shared.infoitem.StreamInfo
import java.text.NumberFormat
import java.util.*

@Composable
fun VideoTitleSection(name: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                name?.let {
                    SharedContext.platformActions.copyToClipboard(it)
                }
            }
            .padding(start = 16.dp, end = 4.dp)
    ) {
        Text(
            text = name ?: stringResource(MR.strings.video_info_loading_title),
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
fun VideoDetailSection(streamInfo: StreamInfo, navController: NavController) {
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
                .clickable {
                    streamInfo.uploaderUrl?.let {
                        navController.navigate(Screen.Channel.createRoute(it, streamInfo.serviceId))
                        SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
                    }
               },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = streamInfo.uploaderAvatarUrl,
                contentDescription = stringResource(MR.strings.detail_uploader_thumbnail_view_description),
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = streamInfo.uploaderName ?: stringResource(MR.strings.video_info_channel),
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
                        text = formatCount(streamInfo.uploaderSubscriberCount) + " " + stringResource(MR.strings.metadata_subscribers),
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
                text = "${NumberFormat.getNumberInstance(Locale.US).format(streamInfo.viewCount ?: 0)} ${if (!streamInfo.isLive) stringResource(MR.strings.video_info_views) else stringResource(MR.strings.video_info_watching)}",
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
                    contentDescription = stringResource(MR.strings.detail_likes_img_view_description),
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
            }
        }
    }
}