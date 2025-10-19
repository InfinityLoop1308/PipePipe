package project.pipepipe.app.ui.screens.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.list.commentListContent
import project.pipepipe.shared.infoitem.StreamInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentSection(
    streamInfo: StreamInfo,
    navController: NavHostController,
    onPlayAudioClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = SharedContext.sharedVideoDetailViewModel
    val uiState by viewModel.uiState.collectAsState()
    val commentsState = uiState.currentComments
    val coroutineScope = rememberCoroutineScope()

    val currentStreamUrl = uiState.currentStreamInfo?.url

    val commentsListState = remember(currentStreamUrl) {
        LazyListState(
            commentsState.comments.firstVisibleItemIndex,
            commentsState.comments.firstVisibleItemScrollOffset
        )
    }
    val repliesListState = remember(currentStreamUrl) {
        LazyListState(
            commentsState.replies.firstVisibleItemIndex,
            commentsState.replies.firstVisibleItemScrollOffset
        )
    }

    DisposableEffect(uiState.currentStreamInfo?.url) {
        onDispose {
            viewModel.updateCommentsScrollPosition(
                commentsListState.firstVisibleItemIndex,
                commentsListState.firstVisibleItemScrollOffset
            )
            viewModel.updateRepliesScrollPosition(
                repliesListState.firstVisibleItemIndex,
                repliesListState.firstVisibleItemScrollOffset
            )
        }
    }
    
    val serviceId = uiState.currentStreamInfo?.serviceId

    if (commentsState.common.error != null) {
        ErrorComponent(
            error = commentsState.common.error!!,
            onRetry = {
                uiState.currentStreamInfo?.let { streamInfo ->
                    coroutineScope.launch {
                        viewModel.loadComments(streamInfo)
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            shouldStartFromTop = true
        )
    } else {
        when {
            commentsState.parentComment != null -> {
                // Replies view with common header
                LaunchedEffect(repliesListState, commentsState.replies.nextPageUrl, commentsState.common.isLoading) {
                    snapshotFlow {
                        val layoutInfo = repliesListState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val totalItemsCount = layoutInfo.totalItemsCount
                        lastVisibleIndex to totalItemsCount
                    }
                        .distinctUntilChanged()
                        .collect { (lastVisibleIndex, totalItemsCount) ->
                            val isAtBottom = totalItemsCount > 0 && lastVisibleIndex == totalItemsCount - 1
                            val hasMoreReplies = commentsState.replies.nextPageUrl != null
                            if (isAtBottom && hasMoreReplies && !commentsState.common.isLoading) {
                                serviceId?.let {
                                    viewModel.loadMoreReplies(it)
                                }
                            }
                        }
                }

                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    state = repliesListState
                ) {
                    // Common header
                    videoDetailCommonHeader(
                        streamInfo = streamInfo,
                        navController = navController,
                        onPlayAudioClick = onPlayAudioClick,
                        onAddToPlaylistClick = onAddToPlaylistClick
                    )

                    // Replies content (inline from CommentList)
                    commentListContent(
                        comments = commentsState.replies.itemList,
                        isLoading = commentsState.common.isLoading,
                        hasMoreComments = commentsState.replies.nextPageUrl != null,
                        onShowReplies = { Unit },
                        onLoadMore = {
                            serviceId?.let {
                                coroutineScope.launch {
                                    viewModel.loadMoreReplies(it)
                                }
                            }
                        },
                        showStickyHeader = true,
                        onBackClick = { viewModel.backToCommentList() },
                        navController = navController,
                        onTimestampClick = onTimestampClick
                    )
                }
                BackHandler {
                    viewModel.backToCommentList()
                }
            }
            else -> {
                // Comments list with common header
                LaunchedEffect(commentsListState, commentsState.comments.nextPageUrl, commentsState.common.isLoading) {
                    snapshotFlow {
                        val layoutInfo = commentsListState.layoutInfo
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val totalItemsCount = layoutInfo.totalItemsCount
                        lastVisibleIndex to totalItemsCount
                    }
                        .distinctUntilChanged()
                        .collect { (lastVisibleIndex, totalItemsCount) ->
                            val isAtBottom = totalItemsCount > 0 && lastVisibleIndex == totalItemsCount - 1
                            val hasMoreComments = commentsState.comments.nextPageUrl != null
                            if (isAtBottom && hasMoreComments && !commentsState.common.isLoading) {
                                serviceId?.let {
                                    viewModel.loadMoreComments(it)
                                }
                            }
                        }
                }

                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                    state = commentsListState
                ) {
                    // Common header
                    videoDetailCommonHeader(
                        streamInfo = streamInfo,
                        navController = navController,
                        onPlayAudioClick = onPlayAudioClick,
                        onAddToPlaylistClick = onAddToPlaylistClick
                    )

                    // Comments content (inline from CommentList)
                    commentListContent(
                        comments = commentsState.comments.itemList,
                        isLoading = commentsState.common.isLoading,
                        hasMoreComments = commentsState.comments.nextPageUrl != null,
                        onShowReplies = { comment ->
                            serviceId?.let {
                                coroutineScope.launch {
                                    viewModel.loadReplies(comment, it)
                                }
                            }
                        },
                        onLoadMore = {
                            serviceId?.let {
                                coroutineScope.launch {
                                    viewModel.loadMoreComments(it)
                                }
                            }
                        },
                        showStickyHeader = false,
                        onBackClick = { },
                        navController = navController,
                        onTimestampClick = onTimestampClick
                    )
                }
            }
        }
    }
}
