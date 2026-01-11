package project.pipepipe.app.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.service.DownloadService
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.uistate.DownloadType

/**
 * Central manager for coordinating download tasks
 * Manages concurrent downloads, queue, and worker lifecycle
 */
class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeWorkers = mutableMapOf<Long, DownloadWorker>()
    private val maxConcurrent = 3  // Maximum concurrent downloads

    private val _activeDownloadIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeDownloadIds: StateFlow<Set<Long>> = _activeDownloadIds.asStateFlow()

    private val TAG = "DownloadManager"

    init {
        // Pause all active downloads on initialization
        scope.launch {
            val activeDownloads = DatabaseOperations.getActiveDownloads()
            Log.d(TAG, "Pausing ${activeDownloads.size} active downloads")

            activeDownloads.forEach { download ->
                DatabaseOperations.updateDownloadStatus(download.id, DownloadStatus.PAUSED.name, null)
            }
        }
    }

    /**
     * Add a new download to the queue
     */
    suspend fun addDownload(
        url: String,
        title: String,
        imageUrl: String?,
        duration: Int,
        downloadType: DownloadType,
        quality: String,
        codec: String,
        formatId: String
    ): Long {
        Log.d(TAG, "Adding download: $title ($quality)")

        // Insert into database
        val downloadId = DatabaseOperations.insertDownload(
            url = url,
            title = title,
            imageUrl = imageUrl,
            duration = duration,
            downloadType = downloadType.name,
            quality = quality,
            codec = codec,
            formatId = formatId
        )

        // Check if we can start this download immediately
        if (activeWorkers.size < maxConcurrent) {
            // Start download service to keep app alive
            DownloadService.start(context)
            startWorker(downloadId)
        } else {
            Log.d(TAG, "Download queued (${activeWorkers.size}/$maxConcurrent active)")
        }

        return downloadId
    }

    /**
     * Start a worker for the given download ID
     */
    private fun startWorker(downloadId: Long) {
        if (activeWorkers.containsKey(downloadId)) {
            Log.w(TAG, "Worker already running for download $downloadId")
            return
        }

        val worker = DownloadWorker(
            context = context,
            downloadId = downloadId,
            onProgressUpdate = { progress, downloaded, total, speed ->
                scope.launch {
                    DatabaseOperations.updateDownloadProgress(
                        id = downloadId,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speed = speed
                    )

                    // Update notification
                    DownloadService.updateNotification(context, downloadId, progress, total)
                }
            },
            onStateChange = { status, error ->
                scope.launch {
                    // Insert error log if status is FAILED
                    val errorLogId = if (status == DownloadStatus.FAILED && error != null) {
                        try {
                            val download = DatabaseOperations.getDownloadById(downloadId)
                            val stacktrace = error
                            val task = "Download: ${download?.title ?: "Unknown"}"
                            val errorCode = "DOWNLOAD_FAILED"
                            val request = download?.url

                            DatabaseOperations.insertErrorLog(
                                stacktrace = stacktrace,
                                task = task,
                                errorCode = errorCode,
                                request = request,
                                serviceId = null
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to insert error log", e)
                            null
                        }
                    } else {
                        null
                    }

                    DatabaseOperations.updateDownloadStatus(downloadId, status.name, error, errorLogId)

                    if (status.isTerminal()) {
                        Log.d(TAG, "Download $downloadId finished with status: $status")

                        // Remove from active workers
                        activeWorkers.remove(downloadId)
                        updateActiveDownloadIds()

                        // Start next queued download
                        startNextInQueue()

                        // Stop service if no more active downloads
                        if (activeWorkers.isEmpty()) {
                            DownloadService.stop(context)
                        }

                        // Show completion notification
                        if (status == DownloadStatus.COMPLETED) {
                            DownloadService.showCompletionNotification(context, downloadId)
                        } else if (status == DownloadStatus.FAILED) {
                            DownloadService.showErrorNotification(context, downloadId, error)
                        }
                    }
                }
            }
        )

        activeWorkers[downloadId] = worker
        updateActiveDownloadIds()

        // Start the worker
        scope.launch {
            worker.execute()
        }

        Log.d(TAG, "Worker started for download $downloadId (${activeWorkers.size}/$maxConcurrent)")
    }

    /**
     * Start the next queued download if available
     */
    private suspend fun startNextInQueue() {
        if (activeWorkers.size >= maxConcurrent) {
            return
        }

        val queuedDownloads = DatabaseOperations.getActiveDownloads()
        val nextDownload = queuedDownloads.firstOrNull { download ->
            !activeWorkers.containsKey(download.id) &&
            download.status == "QUEUED"
        }

        nextDownload?.let { download ->
            Log.d(TAG, "Starting next queued download: ${download.title}")
            startWorker(download.id)
        }
    }

    /**
     * Pause a download
     */
    fun pauseDownload(downloadId: Long) {
        Log.d(TAG, "Pausing download: $downloadId")

        activeWorkers[downloadId]?.cancel(isPause = true)
        activeWorkers.remove(downloadId)
        updateActiveDownloadIds()

        scope.launch {
            // Start next queued download
            startNextInQueue()

            // Stop service if no more active downloads
            if (activeWorkers.isEmpty()) {
                DownloadService.stop(context)
            }
        }
    }

    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: Long) {
        Log.d(TAG, "Resuming download: $downloadId")

        scope.launch {
            // Update status to queued
            DatabaseOperations.updateDownloadStatus(downloadId, DownloadStatus.QUEUED.name, null)

            // Start service
            DownloadService.start(context)

            // Start worker if slots available
            if (activeWorkers.size < maxConcurrent) {
                startWorker(downloadId)
            }
        }
    }

    /**
     * Cancel a download (delete directly)
     */
    fun cancelDownload(downloadId: Long) {
        Log.d(TAG, "Canceling download: $downloadId")

        activeWorkers[downloadId]?.cancel()
        activeWorkers.remove(downloadId)
        updateActiveDownloadIds()

        // Cancel progress notification
        DownloadService.cancelProgressNotification(context, downloadId)

        scope.launch {
            // Delete from database
            DatabaseOperations.deleteDownload(downloadId)

            // Start next queued download
            startNextInQueue()

            // Stop service if no more active downloads
            if (activeWorkers.isEmpty()) {
                DownloadService.stop(context)
            }
        }
    }

    /**
     * Delete a download and its file
     */
    fun deleteDownload(downloadId: Long) {
        Log.d(TAG, "Deleting download: $downloadId")

        // Cancel if active
        cancelDownload(downloadId)

        scope.launch {
            // Get download info to delete file
            val download = DatabaseOperations.getDownloadById(downloadId)
            download?.file_path?.let { filePath ->
                try {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "Deleted file: $filePath")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete file: $filePath", e)
                }
            }

            // Delete from database
            DatabaseOperations.deleteDownload(downloadId)
        }
    }

    /**
     * Update the set of active download IDs
     */
    private fun updateActiveDownloadIds() {
        _activeDownloadIds.value = activeWorkers.keys.toSet()
    }

    /**
     * Get current active download count
     */
    fun getActiveDownloadCount(): Int = activeWorkers.size

    /**
     * Check if a download is currently active
     */
    fun isDownloadActive(downloadId: Long): Boolean = activeWorkers.containsKey(downloadId)
}
