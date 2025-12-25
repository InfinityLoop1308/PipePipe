package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.items.CommonItem

@Composable
fun RelatedItemSection (
    modifier: Modifier = Modifier,
) {
    val viewModel = SharedContext.sharedVideoDetailViewModel
    val uiState by viewModel.uiState.collectAsState()
    val relatedState = uiState.currentRelatedItems
    val coroutineScope = rememberCoroutineScope()

    val currentStreamUrl = uiState.currentStreamInfo?.url
    val relatedItemsListState = remember(currentStreamUrl) {
        LazyListState(
            relatedState.list.firstVisibleItemIndex,
            relatedState.list.firstVisibleItemScrollOffset
        )
    }

    DisposableEffect(currentStreamUrl) {
        onDispose {
            viewModel.updateRelatedItemsScrollPosition(
                relatedItemsListState.firstVisibleItemIndex,
                relatedItemsListState.firstVisibleItemScrollOffset
            )
        }
    }

    if (relatedState.common.error != null) {
        ErrorComponent(
            error = relatedState.common.error!!,
            onRetry = {
                uiState.currentStreamInfo?.let { streamInfo ->
                    coroutineScope.launch {
                        viewModel.loadRelatedItems(streamInfo)
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            shouldStartFromTop = true
        )
    } else {
        // Autoplay next switch state
        var autoplayNextEnabled by remember {
            mutableStateOf(SharedContext.settingsManager.getBoolean("auto_queue_key", false))
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = relatedItemsListState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Autoplay next switch at the top
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(MR.strings.auto_queue_title),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = autoplayNextEnabled,
                        onCheckedChange = { enabled ->
                            autoplayNextEnabled = enabled
                            SharedContext.settingsManager.putBoolean("auto_queue_key", enabled)
                        },
                        modifier = Modifier.scale(0.6f)
                    )
                }
            }

            if (relatedState.common.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(
                    items = relatedState.list.itemList,
                    key = { it.url }
                ) { item ->
                    CommonItem(
                        item = item,
                        onClick = { viewModel.loadVideoDetails(item.url, item.serviceId) }
                    )
                }
            }
        }
    }
}