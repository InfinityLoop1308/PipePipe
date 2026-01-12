package project.pipepipe.app.ui.screens

import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext.navController
import project.pipepipe.app.download.DownloadManagerHolder
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.item.DownloadItem
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.viewmodel.DownloadViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    useAsTab: Boolean = false
) {
    val viewModel: DownloadViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showCancelAllDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(MR.strings.download_tab_downloading) to DownloadStatus.DOWNLOADING,
        stringResource(MR.strings.download_tab_completed) to DownloadStatus.COMPLETED,
        stringResource(MR.strings.download_tab_failed) to DownloadStatus.FAILED
    )
// 监听 selectedTab 的变化
    LaunchedEffect(Unit) {
        snapshotFlow { selectedTab }
            .collectLatest { tab ->
                // collectLatest 特性：当 selectedTab 变化时，取消上一个 block 的协程
                if (tab == 0) {
                    while (isActive) { // 使用 isActive 检查协程状态
                        val activeDownloads = viewModel.getActiveDownloads()

                        // 只有当真的有东西在下载时，才频繁刷新
                        if (activeDownloads.isNotEmpty()) {
                            viewModel.refreshDownloads()
                            delay(1000) // 1秒刷新一次
                        } else {
                            if (uiState.activeDownloadCount != 0) {
                                viewModel.refreshDownloads()
                            }
                            // 关键修改：如果为空，不要 break！而是进入"低功耗"轮询
                            // 或者更好的方式是：在这里挂起，直到 ViewModel 发送"有新任务"的事件
                            delay(3000) // 列表为空时，3秒检查一次，节省性能
                        }
                    }
                } else {
                    // 刚切到其他 Tab 时刷一次即可
                    viewModel.refreshDownloads()
                }
            }
    }


    Column(modifier = Modifier.fillMaxSize()) {
        if (!useAsTab) {
            CustomTopBar(
                defaultTitleText = stringResource(MR.strings.downloads),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(MR.strings.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            GlobalScope.launch(Dispatchers.IO) {
                                viewModel.getDownloadsByStatus(listOf(DownloadStatus.PAUSED)).forEach { download ->
                                    DownloadManagerHolder.instance.resumeDownload(download.id)
                                }
                                viewModel.refreshDownloads()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(MR.strings.start_all)
                        )
                    }
                    IconButton(
                        onClick = {
                            GlobalScope.launch(Dispatchers.IO) {
                                viewModel.getActiveDownloads()
                                    .forEach { download ->
                                        DownloadManagerHolder.instance.pauseDownload(download.id)
                                    }
                                viewModel.refreshDownloads()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(MR.strings.pause_all)
                        )
                    }
                    IconButton(
                        onClick = { showCancelAllDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(MR.strings.cancel_all)
                        )
                    }
                }
            )
        }

        // Tab row for filtering
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, (label, _) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = label,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // Filter downloads based on selected tab
        val filteredDownloads = remember(uiState.downloads, selectedTab) {
            val (_, status) = tabs[selectedTab]
            if (status == null) {
                uiState.downloads
            } else {
                uiState.downloads.filter {
                    when (status) {
                        DownloadStatus.DOWNLOADING -> it.status.isActive() || it.status == DownloadStatus.PAUSED
                        else -> it.status == status
                    }
                }
            }
        }

        when {
            uiState.common.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            filteredDownloads.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (selectedTab) {
                                0 -> stringResource(MR.strings.download_empty_active)
                                1 -> stringResource(MR.strings.download_empty_completed)
                                2 -> stringResource(MR.strings.download_empty_failed)
                                else -> stringResource(MR.strings.download_empty)
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedTab == 0) {
                            Text(
                                text = stringResource(MR.strings.download_hint_appear),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(
                        items = filteredDownloads,
                        key = { it.id }
                    ) { download ->
                        DownloadItem(
                            state = download,
                            modifier = Modifier.fillMaxWidth(),
                            onPauseClick = {
                                GlobalScope.launch(Dispatchers.IO) {
                                    DownloadManagerHolder.instance.pauseDownload(download.id)
                                    viewModel.refreshDownloads()
                                }
                            },
                            onResumeClick = {
                                GlobalScope.launch(Dispatchers.IO) {
                                    DownloadManagerHolder.instance.resumeDownload(download.id)
                                    viewModel.refreshDownloads()
                                }
                            },
                            onCancelClick = {
                                GlobalScope.launch(Dispatchers.IO) {
                                    DownloadManagerHolder.instance.cancelDownload(download.id)
                                    viewModel.refreshDownloads()
                                }
                            },
                            onDeleteClick = {
                                GlobalScope.launch(Dispatchers.IO) {
                                    DownloadManagerHolder.instance.deleteDownload(download.id)
                                    viewModel.refreshDownloads()
                                }
                            },
                            onOpenClick = { filePath ->
                                try {
                                    val file = File(filePath)
                                    if (!file.exists()) {
                                        ToastManager.show(MR.strings.download_file_not_found.desc().toString(context))
                                        return@DownloadItem
                                    }

                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )

                                    val extension = file.extension.lowercase()
                                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                        ?: when (extension) {
                                            "mp4", "mkv", "avi", "mov", "flv", "webm" -> "video/*"
                                            "mp3", "m4a", "aac", "flac", "wav", "ogg" -> "audio/*"
                                            else -> "*/*"
                                        }

                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    try {
                                        context.startActivity(Intent.createChooser(intent, MR.strings.download_open_with.desc().toString(context)))
                                    } catch (e: Exception) {
                                        ToastManager.show(MR.strings.download_no_app_found.desc().toString(context))
                                    }
                                } catch (e: Exception) {
                                    ToastManager.show("Failed to open file: ${e.message}")
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showCancelAllDialog) {
            AlertDialog(
                onDismissRequest = { showCancelAllDialog = false },
                title = {
                    Text(stringResource(MR.strings.cancel_all))
                },
                text = {
                    Text(stringResource(MR.strings.cancel_all_confirmation))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCancelAllDialog = false
                            GlobalScope.launch(Dispatchers.IO) {
                                viewModel.getDownloadsByStatus(
                                    listOf(
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.FETCHING_INFO,
                                        DownloadStatus.PREPROCESSING,
                                        DownloadStatus.DOWNLOADING,
                                        DownloadStatus.POSTPROCESSING,
                                        DownloadStatus.PAUSED,
                                    )
                                )
                                    .forEach { download ->
                                        DownloadManagerHolder.instance.cancelDownload(download.id)
                                    }
                                viewModel.refreshDownloads()
                            }
                        }
                    ) {
                        Text(stringResource(MR.strings.confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCancelAllDialog = false }
                    ) {
                        Text(stringResource(MR.strings.cancel))
                    }
                }
            )
        }
    }
}
