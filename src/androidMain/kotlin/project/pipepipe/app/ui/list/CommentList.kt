package project.pipepipe.app.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.distinctUntilChanged
import project.pipepipe.shared.infoitem.CommentInfo
import project.pipepipe.app.ui.item.CommentItem
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.shared.SharedContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentList(
    comments: List<CommentInfo>,
    isLoading: Boolean,
    hasMoreComments: Boolean,
    onShowReplies: (CommentInfo) -> Unit,
    onLoadMore: () -> Unit,
    showStickyHeader: Boolean = false,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    listState: LazyListState,
    navController: NavHostController
) {
    val uniqueItems = remember(comments) { comments.distinctBy { it.url } }

    LaunchedEffect(listState, hasMoreComments, isLoading) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItemsCount = layoutInfo.totalItemsCount
            lastVisibleIndex to totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItemsCount) ->
                val isAtBottom = totalItemsCount > 0 && lastVisibleIndex == totalItemsCount - 1
                if (isAtBottom && hasMoreComments && !isLoading) {
                    onLoadMore()
                }
            }
    }

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState
        ) {
            if (isLoading && comments.isEmpty()) {
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
                if (showStickyHeader) {
                    stickyHeader {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(bottom = 16.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.clickable { onBackClick() }
                            )
                            Text(
                                text = "Replies",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                } else {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                items(
                    items = uniqueItems,
                    key = { it.url!! }
                ) { comment ->
                    CommentItem(
                        commentInfo = comment,
                        onReplyButtonClick = { onShowReplies(comment) },
                        onChannelAvatarClick = {
                            comment.authorUrl?.let {
                                navController.navigate(Screen.Channel.createRoute(it, comment.serviceId!!))
                                SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (isLoading && comments.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}