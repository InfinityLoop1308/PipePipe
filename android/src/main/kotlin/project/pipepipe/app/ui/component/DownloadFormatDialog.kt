package project.pipepipe.app.ui.component

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.download.DownloadManagerHolder
import project.pipepipe.app.helper.FormatHelper
import project.pipepipe.app.helper.YtDlpFormatHelper
import project.pipepipe.app.uistate.DownloadType
import project.pipepipe.shared.infoitem.StreamInfo

// Helper function to format file size
private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""

    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

// Generic format data class for all stream types
data class Format(
    val id: String,
    val url: String,
    val displayLabel: String,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val codec: String? = null,
    val bitrate: Int = 0,
    val isVideoOnly: Boolean = false,  // Flag to indicate if this is a video-only format that needs audio merging
    val filesize: Long? = null  // File size in bytes
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatDialog(
    streamInfo: StreamInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // State for loading formats from yt-dlp
    var isLoadingFormats by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var videoFormats by remember { mutableStateOf<List<Format>>(emptyList()) }
    var audioFormats by remember { mutableStateOf<List<Format>>(emptyList()) }

    // State for permission handling
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }

    // State for duplicate download confirmation
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Permission launcher for Android 8-9 (API 26-28)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Permission granted, execute pending download
                pendingDownload?.invoke()
                pendingDownload = null
                onDismiss()
            } else {
                // Permission denied, show explanation
                showPermissionDialog = true
            }
        }
    )

    // Fetch formats using yt-dlp
    LaunchedEffect(streamInfo.url) {
        isLoadingFormats = true
        loadError = null

        try {
            val result = YtDlpFormatHelper.fetchFormats(streamInfo.url)

            result.onSuccess { formatsResult ->
                // Convert YtDlpFormat to our Format data class
                videoFormats = formatsResult.videoFormats.map { ytdlpFormat ->
                    Format(
                        id = ytdlpFormat.formatId,
                        url = ytdlpFormat.url ?: "",
                        displayLabel = ytdlpFormat.getDisplayLabel(),
                        height = ytdlpFormat.height ?: 0,
                        frameRate = ytdlpFormat.fps ?: 0f,
                        codec = ytdlpFormat.vcodec,
                        bitrate = 0,
                        isVideoOnly = true,  // Video-only formats need audio merging
                        filesize = ytdlpFormat.getFileSize()
                    )
                }

                audioFormats = formatsResult.audioFormats.map { ytdlpFormat ->
                    Format(
                        id = ytdlpFormat.formatId,
                        url = ytdlpFormat.url ?: "",
                        displayLabel = ytdlpFormat.getDisplayLabel(),
                        height = 0,
                        frameRate = 0f,
                        codec = ytdlpFormat.acodec,
                        bitrate = (ytdlpFormat.abr ?: ytdlpFormat.tbr ?: 0f).toInt(),
                        filesize = ytdlpFormat.getFileSize()
                    )
                }.distinctBy { it.displayLabel }

                isLoadingFormats = false
            }.onFailure { e ->
                loadError = "Failed to load formats: ${e.message}"
                isLoadingFormats = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadError = "Failed to load formats: ${e.message}"
            isLoadingFormats = false
        }
    }

    var selectedType by remember { mutableStateOf(DownloadType.VIDEO) }
    var selectedVideoFormat by remember { mutableStateOf<Format?>(null) }
    var selectedAudioFormat by remember { mutableStateOf<Format?>(null) }
    var videoDropdownExpanded by remember { mutableStateOf(false) }
    var audioDropdownExpanded by remember { mutableStateOf(false) }

    // Get best audio format for calculating total video download size
    val bestAudioFormat = remember(audioFormats) {
        audioFormats.firstOrNull() // First one is the best quality (already sorted)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.download)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Video Title
                Text(
                    text = streamInfo.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Download Type Selection (Video/Audio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == DownloadType.VIDEO,
                        onClick = { selectedType = DownloadType.VIDEO },
                        label = { Text(stringResource(MR.strings.download_video_quality)) },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoadingFormats && loadError == null && videoFormats.isNotEmpty()
                    )

                    FilterChip(
                        selected = selectedType == DownloadType.AUDIO,
                        onClick = { selectedType = DownloadType.AUDIO },
                        label = { Text(stringResource(MR.strings.download_audio_format)) },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoadingFormats && loadError == null && audioFormats.isNotEmpty()
                    )
                }

                // Content area based on state
                if (isLoadingFormats) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading formats...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (loadError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = loadError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    // Format selection dropdown
                    when (selectedType) {
                        DownloadType.VIDEO -> {
                            if (videoFormats.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = videoDropdownExpanded,
                                    onExpandedChange = { videoDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedVideoFormat?.displayLabel ?: stringResource(MR.strings.download_select_video_format),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(MR.strings.download_video_quality)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = videoDropdownExpanded,
                                        onDismissRequest = { videoDropdownExpanded = false }
                                    ) {
                                        videoFormats.forEach { format ->
                                            // Calculate total size (video + best audio)
                                            val totalSize = if (format.filesize != null && bestAudioFormat?.filesize != null) {
                                                format.filesize + bestAudioFormat.filesize
                                            } else {
                                                format.filesize
                                            }
                                            val sizeLabel = formatFileSize(totalSize)

                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(format.displayLabel)
                                                        if (sizeLabel.isNotEmpty()) {
                                                            Text(
                                                                text = sizeLabel,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedVideoFormat = format
                                                    videoDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        DownloadType.AUDIO -> {
                            if (audioFormats.isNotEmpty()) {
                                ExposedDropdownMenuBox(
                                    expanded = audioDropdownExpanded,
                                    onExpandedChange = { audioDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedAudioFormat?.displayLabel ?: stringResource(MR.strings.download_select_audio_format),
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(stringResource(MR.strings.download_audio_format)) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audioDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = audioDropdownExpanded,
                                        onDismissRequest = { audioDropdownExpanded = false }
                                    ) {
                                        audioFormats.forEach { format ->
                                            val sizeLabel = formatFileSize(format.filesize)

                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(format.displayLabel)
                                                        if (sizeLabel.isNotEmpty()) {
                                                            Text(
                                                                text = sizeLabel,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedAudioFormat = format
                                                    audioDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Get selected format
                    val selectedFormat = when (selectedType) {
                        DownloadType.VIDEO -> selectedVideoFormat
                        DownloadType.AUDIO -> selectedAudioFormat
                    }

                    selectedFormat?.let { format ->
                        // Define the download action
                        val downloadAction: () -> Unit = {
                            GlobalScope.launch(Dispatchers.IO) {
                                // For video-only formats, append +bestaudio to ensure audio is merged
                                val finalFormatId = if (selectedType == DownloadType.VIDEO && format.isVideoOnly) {
                                    "${format.id}+bestaudio"
                                } else {
                                    format.id
                                }

                                // Check for duplicate downloads (same url and download type)
                                val existingDownload = DatabaseOperations.findDownloadByUrlAndType(
                                    url = streamInfo.url,
                                    downloadType = selectedType.name
                                )

                                if (existingDownload != null) {
                                    // Show duplicate confirmation dialog on main thread
                                    withContext(Dispatchers.Main) {
                                        duplicateDownloadAction = {
                                            GlobalScope.launch(Dispatchers.IO) {
                                                DownloadManagerHolder.instance.addDownload(
                                                    url = streamInfo.url,
                                                    title = streamInfo.name ?: "Unknown",
                                                    imageUrl = streamInfo.thumbnailUrl,
                                                    duration = streamInfo.duration?.toInt() ?: 0,
                                                    downloadType = selectedType,
                                                    quality = format.displayLabel,
                                                    codec = FormatHelper.parseCodecName(format.codec),
                                                    formatId = finalFormatId
                                                )
                                            }
                                        }
                                        showDuplicateDialog = true
                                    }
                                } else {
                                    // No duplicate, proceed with download
                                    DownloadManagerHolder.instance.addDownload(
                                        url = streamInfo.url,
                                        title = streamInfo.name ?: "Unknown",
                                        imageUrl = streamInfo.thumbnailUrl,
                                        duration = streamInfo.duration?.toInt() ?: 0,
                                        downloadType = selectedType,
                                        quality = format.displayLabel,
                                        codec = FormatHelper.parseCodecName(format.codec),
                                        formatId = finalFormatId
                                    )
                                    // Close dialog on main thread
                                    withContext(Dispatchers.Main) {
                                        onDismiss()
                                    }
                                }
                            }
                            Unit
                        }

                        // Check if permission is needed (Android 8-9 only)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                                // Store download action and request permission
                                pendingDownload = downloadAction
                                permissionLauncher.launch(permission)
                                return@TextButton
                            }
                        }

                        // Permission already granted or not needed (Android 10+)
                        downloadAction()
                    }
                },
                enabled = when (selectedType) {
                    DownloadType.VIDEO -> selectedVideoFormat != null
                    DownloadType.AUDIO -> selectedAudioFormat != null
                }
            ) {
                Text(stringResource(MR.strings.download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )

    // Permission denied dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(MR.strings.permission_storage_required_title)) },
            text = {
                Text(stringResource(MR.strings.permission_storage_required_message))
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(MR.strings.permission_ok))
                }
            }
        )
    }

    // Duplicate download confirmation dialog
    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = {
                showDuplicateDialog = false
                duplicateDownloadAction = null
            },
            title = { Text(stringResource(MR.strings.download_duplicate_title)) },
            text = {
                Text(stringResource(MR.strings.download_duplicate_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        duplicateDownloadAction?.invoke()
                        showDuplicateDialog = false
                        duplicateDownloadAction = null
                        onDismiss()
                    }
                ) {
                    Text(stringResource(MR.strings.download_duplicate_replace))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        duplicateDownloadAction = null
                    }
                ) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}
