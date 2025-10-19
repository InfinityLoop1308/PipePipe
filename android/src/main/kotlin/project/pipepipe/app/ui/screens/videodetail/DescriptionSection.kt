package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.HtmlText
import project.pipepipe.app.ui.theme.supportingTextColor
import project.pipepipe.app.utils.formatAbsoluteTime
import project.pipepipe.app.viewmodel.VideoDetailViewModel
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.app.ui.screens.Screen

@Composable
fun DescriptionSection(
    streamInfo: StreamInfo,
    navController: NavHostController,
    onTimestampClick: (Long)-> Unit
) {
    val viewModel = SharedContext.sharedVideoDetailViewModel
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {

        // Top row: Published date and thumbnail button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(MR.strings.published_on)} ${streamInfo.uploadDate?.let { formatAbsoluteTime(it) }?: stringResource(MR.strings.unknown_title)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.7f else 0.8f
                    )
                )

                // Thumbnail button
                streamInfo.thumbnailUrl?.let { thumbnailUrl ->
                    IconButton(
                        onClick = {
                            SharedContext.showImageViewer(listOf(thumbnailUrl), 0)
                        },
                        modifier = Modifier.scale(0.8f).offset(y = (-1).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(MR.strings.thumbnail),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Description content (removed Card)
        item {
            Spacer(modifier = Modifier.height(4.dp))
            streamInfo.description?.let { description ->
                HtmlText(
                    text = description.content ?: stringResource(MR.strings.no_items),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = supportingTextColor(),
                    modifier = Modifier.fillMaxWidth(),
                    onHashtagClick = {
                        viewModel.showAsBottomPlayer()
                        navController.navigate(Screen.Search.createRoute(it, streamInfo.serviceId))
                    },
                    onTimestampClick = onTimestampClick
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Tags section (centered FlowRow)
        streamInfo.tags?.let { tags ->
            if (tags.isNotEmpty()) {
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = 6.dp,
                            alignment = Alignment.CenterHorizontally
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        tags.forEach { tag ->
                            ElevatedSuggestionChip(
                                onClick = {
                                    // Minimize to bottom player
                                    viewModel.showAsBottomPlayer()

                                    // Navigate to search screen with tag as query
                                    navController.navigate(Screen.Search.createRoute(tag, streamInfo.serviceId))
                                },
                                label = {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = SuggestionChipDefaults.elevatedSuggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
