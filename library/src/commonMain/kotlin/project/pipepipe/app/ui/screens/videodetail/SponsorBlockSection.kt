package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.SponsorBlockHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.component.HtmlText
import project.pipepipe.shared.infoitem.SponsorBlockSegmentInfo
import project.pipepipe.shared.infoitem.helper.SponsorBlockCategory
import project.pipepipe.app.utils.toDurationString
import java.util.UUID

@Composable
fun SponsorBlockSection(
    segments: List<SponsorBlockSegmentInfo> = emptyList(),
    modifier: Modifier = Modifier,
    onStart: () -> Long?,
    onEnd: () -> Long?,
    onTimestampClick: (Long) -> Unit = {},
) {
    val skipMarked = remember { mutableStateOf(true) }
//    val whitelistChannel = remember { mutableStateOf(false) }
    val startTime = remember { mutableStateOf<Long?>(null) }
    val endTime = remember { mutableStateOf<Long?>(null) }
    val showCategoryMenu = remember { mutableStateOf(false) }

    val viewModel = SharedContext.sharedVideoDetailViewModel

    val currentRange = remember(startTime.value, endTime.value) {
        val start = startTime.value?.toDurationString(true) ?: "00:00:00"
        val end = endTime.value?.toDurationString(true) ?: "00:00:00"
        "$start - $end"
    }
    
    val skipMarkedSegmentsLabel = stringResource(MR.strings.sponsor_block_skip_marked_segments)
    val noSponsorSegmentsText = stringResource(MR.strings.no_sponsor_segments)
    val alreadyVotedText = stringResource(MR.strings.already_voted)
    val successText = stringResource(MR.strings.success)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            ControlRow(
                currentRange = currentRange,
                onStart = {
                    startTime.value = onStart()
                    endTime.value?.let {
                        endTime.value = null
                    }
                },
                onEnd = {
                    endTime.value = onEnd()
                },
                onClear = {
                    startTime.value = null
                    endTime.value = null
                },
                onSubmit = {
                    if (startTime.value != null && endTime.value != null) {
                        showCategoryMenu.value = true
                    }
                },
                showCategoryMenu = showCategoryMenu.value,
                onDismissCategoryMenu = {
                    showCategoryMenu.value = false
                },
                onCategorySelected = { category ->
                    showCategoryMenu.value = false
                    GlobalScope.launch {
                        viewModel.submitSponsorBlockSegment(
                            url = viewModel.uiState.value.currentStreamInfo!!.sponsorblockUrl!!,
                            segment = SponsorBlockSegmentInfo(
                                uuid = UUID.randomUUID().toString(),
                                startTime = startTime.value!!.toDouble(),
                                endTime = endTime.value!!.toDouble(),
                                category = category
                            ),
                            msg = successText
                        )
                    }
                }
            )
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 10.dp))
        }

        item {
            ToggleRow(
                label = skipMarkedSegmentsLabel,
                checked = skipMarked.value,
                onCheckedChange = { skipMarked.value = it }
            )
        }

//        item {
//            ToggleRow(
//                label = stringResource(MR.strings.sponsor_block_whitelist_channel),
//                checked = whitelistChannel.value,
//                onCheckedChange = { whitelistChannel.value = it }
//            )
//        }

        item {
            Divider(modifier = Modifier.padding(vertical = 10.dp))
        }

        if (segments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = noSponsorSegmentsText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(segments) { segment ->
                SegmentRow(
                    segment = segment,
                    onTimestampClick = onTimestampClick,
                    onThumbUp = {
                        if (segment.hasVoted) {
                            ToastManager.show(alreadyVotedText)
                        } else {
                            GlobalScope.launch {
                                viewModel.voteSponsorBlockSegment(
                                    url = viewModel.uiState.value.currentStreamInfo!!.sponsorblockUrl!!,
                                    uuid = segment.uuid,
                                    voteType = 1, //upvote
                                    msg = successText
                                )
                            }
                            segment.hasVoted = true
                            ToastManager.show(successText)
                        }
                    },
                    onThumbDown = {
                        if (segment.hasVoted) {
                            ToastManager.show(alreadyVotedText)
                        } else {
                            GlobalScope.launch {
                                viewModel.voteSponsorBlockSegment(
                                    url = viewModel.uiState.value.currentStreamInfo!!.sponsorblockUrl!!,
                                    uuid = segment.uuid,
                                    msg = successText,
                                    voteType = 0 //downvote
                                )
                            }
                            segment.hasVoted = true
                            ToastManager.show(successText)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}



@Composable
private fun ControlRow(
    currentRange: String,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    showCategoryMenu: Boolean,
    onDismissCategoryMenu: () -> Unit,
    onCategorySelected: (SponsorBlockCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-8).dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onStart) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = stringResource(MR.strings.start)
                    )
                }
                Text(stringResource(MR.strings.start), style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEnd) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = stringResource(MR.strings.end)
                    )
                }
                Text(stringResource(MR.strings.end), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text = currentRange,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
        Row {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Clear"
                )
            }
            Box {
                IconButton(onClick = onSubmit) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Submit"
                    )
                }

                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = onDismissCategoryMenu
                ) {
                    SponsorBlockCategory.values().filter { it != SponsorBlockCategory.PENDING }.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(SponsorBlockHelper.getCategoryName(category)) },
                            onClick = { onCategorySelected(category) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.6f)
        )
    }
}

@Composable
private fun SegmentRow(
    segment: SponsorBlockSegmentInfo,
    onTimestampClick: (Long) -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit
) {
    val categoryColor = getCategoryColor(segment.category)
    val categoryName = SponsorBlockHelper.getCategoryName(segment.category)

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(categoryColor, shape = MaterialTheme.shapes.small)
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        HtmlText(
            text = formatSegmentRange(segment),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 12.dp),
            onTimestampClick = onTimestampClick
        )
        Row(modifier = Modifier.width(80.dp)) {
            IconButton(onClick = onThumbUp) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = "Thumb up"
                )
            }
            IconButton(onClick = onThumbDown) {
                Icon(
                    imageVector = Icons.Filled.ThumbDown,
                    contentDescription = "Thumb down"
                )
            }
        }
    }
}

@Composable
private fun getCategoryColor(category: SponsorBlockCategory): Color {
    return SponsorBlockHelper.getCategoryColor(category)
}

private fun formatSegmentRange(segment: SponsorBlockSegmentInfo): String {
    return "${segment.startTime.toLong().toDurationString(true)} - ${segment.endTime.toLong().toDurationString(true)}"
}