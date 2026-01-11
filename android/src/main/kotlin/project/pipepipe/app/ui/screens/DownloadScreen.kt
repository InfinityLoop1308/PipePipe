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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext.navController
import project.pipepipe.app.database.DatabaseOperations
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

    // Auto-refresh downloads periodically
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshDownloads()
            delay(1000) // Refresh every second
        }
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "All" to null,
        "Downloading" to DownloadStatus.DOWNLOADING,
        "Completed" to DownloadStatus.COMPLETED,
        "Failed" to DownloadStatus.FAILED
    )

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
                        DownloadStatus.DOWNLOADING -> it.status.isActive()
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
                                1 -> "No active downloads"
                                2 -> "No completed downloads"
                                3 -> "No failed downloads"
                                else -> "No downloads yet"
                            },
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (selectedTab == 0) {
                            Text(
                                text = "Downloads will appear here",
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
                                        ToastManager.show("File not found")
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
                                        context.startActivity(Intent.createChooser(intent, "Open with"))
                                    } catch (e: Exception) {
                                        ToastManager.show("No app found to open this file")
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
