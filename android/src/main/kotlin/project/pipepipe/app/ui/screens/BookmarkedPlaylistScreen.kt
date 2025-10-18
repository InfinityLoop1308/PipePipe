package project.pipepipe.app.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.item.DisplayType
import project.pipepipe.app.ui.item.CommonItem
import project.pipepipe.app.ui.viewmodel.BookmarkedPlaylistViewModel
import project.pipepipe.app.uistate.VideoDetailPageState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.net.URLEncoder

@Composable
fun BookmarkedPlaylistScreen(navController: NavController) {
    val viewModel: BookmarkedPlaylistViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.reorderItems(from.index, to.index)
    }

    LaunchedEffect(Unit) {
        viewModel.loadPlaylists()
    }


    val videoDetailUiState by SharedContext.sharedVideoDetailViewModel.uiState.collectAsState()

    LaunchedEffect(videoDetailUiState.pageState) {
        if (videoDetailUiState.pageState == VideoDetailPageState.BOTTOM_PLAYER
            && navController.currentDestination?.route == Screen.Main.route)
        viewModel.loadPlaylists()
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        when {
            uiState.playlists.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(MR.strings.no_bookmarked_playlists),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.playlists,
                        key = { _, playlist -> playlist.url }
                    ) { index, playlist ->
                        ReorderableItem(
                            reorderableLazyListState,
                            key = playlist.url
                        ) { isDragging ->
                            val interactionSource = remember { MutableInteractionSource() }

                            CommonItem(
                                item = playlist,
                                isDragging = isDragging,
                                showDragHandle = true,
                                onClick = {
                                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.url, playlist.serviceId))
                                },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        // 可选：添加触觉反馈
                                    },
                                    onDragStopped = {
                                        // 可选：添加完成反馈
                                    },
                                    interactionSource = interactionSource
                                ),
                                displayType = DisplayType.ORIGIN
                            )
                        }
                    }
                }
            }
        }
    }
}