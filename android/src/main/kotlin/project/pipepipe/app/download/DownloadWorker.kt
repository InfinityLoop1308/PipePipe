package project.pipepipe.app.download

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.uistate.DownloadType
import java.io.File

/**
 * Worker class responsible for executing a single download task using youtubedl-android
 */
class DownloadWorker(
    private val context: Context,
    private val downloadId: Long,
    private val onProgressUpdate: (Float, Long?, Long?, String?) -> Unit,
    private val onStateChange: (DownloadStatus, String?) -> Unit
) {
    private val processId: String = "download_$downloadId"
    private val TAG = "DownloadWorker"

    @Volatile
    private var isPausing = false

    suspend fun execute() = withContext(Dispatchers.IO) {
        try {
            // Get download record from database
            val download = DatabaseOperations.getDownloadById(downloadId)
            if (download == null) {
                onStateChange(DownloadStatus.FAILED, "Download record not found")
                return@withContext
            }

            Log.d(TAG, "Starting download: ${download.title}")

            // Phase 1: Mark as started and preprocessing
            DatabaseOperations.markDownloadStarted(downloadId)
            onStateChange(DownloadStatus.PREPROCESSING, null)

            // Phase 2: Build download request
            val request = buildDownloadRequest(download)

            // Phase 3: Execute download with progress tracking
            var hasStartedDownloading = false
            var hasStartedPostProcessing = false
            val response = YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                callback = { progress, eta, line ->
                    println(line)

                    // Detect yt-dlp post-processing phase
                    val isPostProcessing = line.contains("[Merger]", ignoreCase = true) ||
                                         line.contains("[ffmpeg]", ignoreCase = true) ||
                                         line.contains("[post]", ignoreCase = true) ||
                                         line.contains("Merging formats", ignoreCase = true) ||
                                         line.contains("Post-process", ignoreCase = true)

                    if (isPostProcessing && !hasStartedPostProcessing) {
                        hasStartedPostProcessing = true
                        onStateChange(DownloadStatus.POSTPROCESSING, null)
                    }

                    // Parse progress info from output line
                    val (speed, downloaded, total) = parseProgressLine(line)

                    // Switch to DOWNLOADING state when we first get actual download data
                    if (!hasStartedDownloading && !hasStartedPostProcessing && (speed != null || downloaded != null)) {
                        hasStartedDownloading = true
                        onStateChange(DownloadStatus.DOWNLOADING, null)
                    }

                    // Update progress
                    onProgressUpdate(progress / 100f, downloaded, total, speed)
                }
            )

            // Phase 4: Handle completion
            if (response.exitCode == 0) {
                Log.d(TAG, "Download completed successfully")

                // Post-processing: move file from cache to permanent location
                // Only set POSTPROCESSING if not already set by yt-dlp detection
                if (!hasStartedPostProcessing) {
                    onStateChange(DownloadStatus.POSTPROCESSING, null)
                }
                val finalPath = moveToPermanentLocation(download)

                if (finalPath != null) {
                    // Get actual file size
                    val fileSize = try {
                        java.io.File(finalPath).length()
                    } catch (e: Exception) {
                        null
                    }

                    DatabaseOperations.updateDownloadCompleted(downloadId, finalPath, fileSize)
                    onStateChange(DownloadStatus.COMPLETED, null)
                } else {
                    onStateChange(DownloadStatus.FAILED, "Failed to move file to final location")
                }
            } else {
                Log.e(TAG, "Download failed with exit code ${response.exitCode}")
                Log.e(TAG, "Error output: ${response.err}")
                // Include full yt-dlp error output
                val fullError = "Exit code: ${response.exitCode}\n\nError output:\n${response.err}\n\nStdout:\n${response.out}"
                onStateChange(DownloadStatus.FAILED, fullError)
            }

        } catch (e: YoutubeDL.CanceledException) {
            if (isPausing) {
                Log.d(TAG, "Download paused by user")
                onStateChange(DownloadStatus.PAUSED, null)
            } else {
                Log.d(TAG, "Download canceled by user")
                onStateChange(DownloadStatus.CANCELED, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            // Include full stack trace for exceptions
            val fullError = "${e.javaClass.simpleName}: ${e.message}\n\nStack trace:\n${e.stackTraceToString()}"
            onStateChange(DownloadStatus.FAILED, fullError)
        }
    }

    private fun buildDownloadRequest(download: project.pipepipe.database.Downloads): YoutubeDLRequest {
        // Use cache directory for temporary downloads
        val cacheDir = File(context.cacheDir, "downloads/$downloadId")
        cacheDir.mkdirs()

        val request = YoutubeDLRequest(download.url).apply {
            // Format selection
            if (download.download_type == "SUBTITLE") {
                addOption("--skip-download")
                addOption("--write-sub")
                addOption("--write-auto-sub")
                addOption("--sub-lang", download.format_id)
            } else {
                if (download.format_id.startsWith("res")) {
                    addOption("-S", download.format_id)
                } else {
                    addOption("-f", download.format_id)
                }
                addOption("--embed-thumbnail")
                if (download.download_type == "AUDIO") {
                    addOption("--remux-video", "webm>opus")
                }
            }
            // Output configuration
            addOption("-P", cacheDir.absolutePath)
            addOption("-o", "%(title).200B.%(ext)s")

            // Basic options
            addOption("--no-mtime")
            addOption("--newline")  // Output progress on new lines for better parsing

            // Network optimization
            addOption("--concurrent-fragments", "3")
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")

            // Quiet mode to reduce output noise
            addOption("--no-warnings")
        }

        Log.d(TAG, "Download request built for format: ${download.format_id}")
        return request
    }

    private fun parseProgressLine(line: String): Triple<String?, Long?, Long?> {
        var speed: String? = null
        var downloaded: Long? = null
        var total: Long? = null

        try {
            // yt-dlp output format examples:
            // "[download]  45.2% of 125.6MiB at 2.5MiB/s ETA 00:25"
            // "[download]  10.5MiB of  45.2MiB at  1.2MiB/s ETA 00:30"
            // "[download] 100% of 125.6MiB in 00:25"

            // Parse speed (e.g., "2.5MiB/s", "1.23GiB/s", "500KiB/s")
            val speedRegex = "at\\s+([\\d.]+\\s*[KMGT]?i?B/s)".toRegex(RegexOption.IGNORE_CASE)
            speedRegex.find(line)?.let {
                speed = it.groupValues[1].trim()
            }

            // Parse file sizes - try percentage format first
            // Format: "45.2% of 125.6MiB"
            val percentRegex = "([\\d.]+)%\\s+of\\s+~?\\s*([\\d.]+)\\s*([KMGT]?i?B)".toRegex(RegexOption.IGNORE_CASE)
            val percentMatch = percentRegex.find(line)

            if (percentMatch != null) {
                val (percentage, totSize, totUnit) = percentMatch.destructured
                total = parseSize(totSize.toDouble(), totUnit)
                downloaded = (total * percentage.toDouble() / 100.0).toLong()
            } else {
                // Format: "10.5MiB of 45.2MiB"
                val sizeRegex = "([\\d.]+)\\s*([KMGT]?i?B)\\s+of\\s+~?\\s*([\\d.]+)\\s*([KMGT]?i?B)".toRegex(RegexOption.IGNORE_CASE)
                val sizeMatch = sizeRegex.find(line)

                if (sizeMatch != null) {
                    val (dlSize, dlUnit, totSize, totUnit) = sizeMatch.destructured
                    downloaded = parseSize(dlSize.toDouble(), dlUnit)
                    total = parseSize(totSize.toDouble(), totUnit)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse progress line: $line", e)
        }

        return Triple(speed, downloaded, total)
    }

    private fun parseSize(size: Double, unit: String): Long {
        val upperUnit = unit.uppercase().replace("I", "i")
        return when {
            upperUnit == "B" -> size.toLong()
            upperUnit.startsWith("K") -> (size * 1024).toLong()
            upperUnit.startsWith("M") -> (size * 1024 * 1024).toLong()
            upperUnit.startsWith("G") -> (size * 1024 * 1024 * 1024).toLong()
            upperUnit.startsWith("T") -> (size * 1024 * 1024 * 1024 * 1024).toLong()
            else -> {
                Log.w(TAG, "Unknown size unit: $unit, treating as bytes")
                size.toLong()
            }
        }
    }

    private suspend fun moveToPermanentLocation(download: project.pipepipe.database.Downloads): String? {
        val cacheDir = File(context.cacheDir, "downloads/$downloadId")

        if (!cacheDir.exists() || cacheDir.listFiles()?.isEmpty() == true) {
            Log.e(TAG, "Cache directory is empty or doesn't exist")
            return null
        }

        // Find the downloaded file (excluding metadata files)
        val downloadedFile = cacheDir.listFiles()?.firstOrNull { file ->
            !file.name.endsWith(".part") &&
            !file.name.endsWith(".ytdl") &&
            !file.name.endsWith(".temp") &&
            !file.name.contains(".description") &&
            !file.name.contains(".info.json") &&
            file.isFile
        }

        if (downloadedFile == null) {
            Log.e(TAG, "No downloaded file found in cache directory")
            return null
        }

        // Determine final destination based on download type
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appSubDir = when (DownloadType.valueOf(download.download_type)) {
            DownloadType.VIDEO, DownloadType.SUBTITLE -> "PipePipe/Videos"
            DownloadType.AUDIO -> "PipePipe/Audio"
        }

        val finalDir = File(downloadsDir, appSubDir)
        finalDir.mkdirs()

        val finalFile = File(finalDir, downloadedFile.name)

        return try {
            // Copy file to final location, allowing overwrite for replacement
            downloadedFile.copyTo(finalFile, overwrite = true)

            // Delete cache directory
            cacheDir.deleteRecursively()

            // Scan the file to make it visible in gallery and file managers
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalFile.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Media scan completed for: $path (URI: $uri)")
            }

            Log.d(TAG, "File moved to: ${finalFile.absolutePath}")
            finalFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file to final location", e)
            null
        }
    }

    fun cancel(isPause: Boolean = false) {
        isPausing = isPause
        Log.d(TAG, if (isPause) "Pausing download: $downloadId" else "Canceling download: $downloadId")
        YoutubeDL.destroyProcessById(processId)
    }
}
