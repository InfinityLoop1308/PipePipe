package project.pipepipe.app.ui.screens.videodetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import project.pipepipe.shared.SharedContext
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.item.MediaListItem

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
            error = uiState.common.error!!,
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
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = relatedItemsListState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    MediaListItem(
                        item = item,
                        onClick = { viewModel.loadVideoDetails(item.url, item.serviceId) }
                    )
                }
            }
        }
    }
}