package project.pipepipe.app.ui.screens.videodetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import project.pipepipe.shared.SharedContext
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.list.CommentList

@Composable
fun CommentSection(
    modifier: Modifier = Modifier,
    navController: NavHostController,
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

    Box(modifier = modifier.fillMaxSize()) {
        if (commentsState.common.error != null) {
            ErrorComponent(
                error = uiState.common.error!!,
                onRetry = {
                    uiState.currentStreamInfo?.let { streamInfo ->
                        coroutineScope.launch {
                            viewModel.loadComments(streamInfo)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            when {
                commentsState.parentComment != null -> {
                    CommentList(
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
                        listState = repliesListState,
                        navController = navController
                    )
                    BackHandler() {
                        viewModel.backToCommentList()
                    }
                }
                else -> {
                    CommentList(
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
                        onBackClick = { },
                        listState = commentsListState,
                        navController = navController
                    )
                }
            }
        }
    }
}
