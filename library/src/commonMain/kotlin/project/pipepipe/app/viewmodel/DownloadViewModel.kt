package project.pipepipe.app.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.DownloadItemState
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.uistate.DownloadType
import project.pipepipe.app.uistate.DownloadUiState

/**
 * Shared ViewModel for download management
 * Platform-agnostic business logic for downloads
 */
class DownloadViewModel : BaseViewModel<DownloadUiState>(DownloadUiState()) {

    init {
        observeDownloads()
    }

    /**
     * Observe downloads from database and update UI state
     */
    private fun observeDownloads() {
        viewModelScope.launch {
            try {
                // Get all downloads and observe changes
                val downloads = DatabaseOperations.getAllDownloads()
                val downloadItems = downloads.map { it.toDownloadItemState() }

                setState { state ->
                    state.copy(
                        downloads = downloadItems,
                        activeDownloadCount = downloadItems.count { it.status.isActive() }
                    )
                }
            } catch (e: Exception) {
                // Handle error silently or log
            }
        }
    }

    /**
     * Refresh downloads list
     */
    fun refreshDownloads() {
        observeDownloads()
    }

    /**
     * Get downloads filtered by status
     */
    suspend fun getDownloadsByStatus(status: DownloadStatus): List<DownloadItemState> {
        return try {
            val downloads = DatabaseOperations.getAllDownloads()
            downloads
                .filter { it.status == status.name }
                .map { it.toDownloadItemState() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get active downloads
     */
    suspend fun getActiveDownloads(): List<DownloadItemState> {
        return try {
            val downloads = DatabaseOperations.getActiveDownloads()
            downloads.map { it.toDownloadItemState() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Convert database entity to UI state
 */
fun project.pipepipe.database.Downloads.toDownloadItemState(): DownloadItemState {
    return DownloadItemState(
        id = id,
        url = url,
        title = title,
        imageUrl = image_url,
        duration = duration.toInt(),
        downloadType = DownloadType.valueOf(download_type),
        quality = quality,
        codec = codec,
        status = DownloadStatus.valueOf(status),
        progress = progress.toFloat(),
        downloadedBytes = downloaded_bytes,
        totalBytes = total_bytes,
        downloadSpeed = download_speed,
        filePath = file_path,
        createdAt = created_at,
        finishedTimestamp = finished_at,
        errorMessage = error_message,
        errorLogId = error_log_id
    )
}
