package project.pipepipe.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import project.pipepipe.app.R
import project.pipepipe.app.database.DatabaseOperations

/**
 * Foreground service for managing downloads
 * Keeps the app alive while downloads are in progress
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Start as foreground service
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service is controlled by DownloadManager, just maintain foreground state
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Download progress notifications"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        // Create channel for completion notifications
        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            "Download Completion",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for completed downloads"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(completionChannel)
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloads")
            .setContentText("Download in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val COMPLETION_CHANNEL_ID = "download_completion_channel"
        private const val NOTIFICATION_ID = 3001
        private const val PROGRESS_NOTIFICATION_BASE_ID = 3100

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }

        fun updateNotification(context: Context, downloadId: Long, progress: Float, totalBytes: Long?) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val download = DatabaseOperations.getDownloadById(downloadId) ?: return@launch

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(download.title)
                    .setContentText("${(progress * 100).toInt()}% - ${download.quality}")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(100, (progress * 100).toInt(), false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                notificationManager.notify(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt(), notification)
            }
        }

        fun showCompletionNotification(context: Context, downloadId: Long) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val download = DatabaseOperations.getDownloadById(downloadId) ?: return@launch

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Cancel progress notification
                notificationManager.cancel(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt())

                // Show completion notification
                val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
                    .setContentTitle("Download Complete")
                    .setContentText(download.title)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt(), notification)
            }
        }

        fun showErrorNotification(context: Context, downloadId: Long, error: String?) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val download = DatabaseOperations.getDownloadById(downloadId) ?: return@launch

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Cancel progress notification
                notificationManager.cancel(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt())

                // Show error notification
                val notification = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
                    .setContentTitle("Download Failed")
                    .setContentText(download.title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(error ?: "Unknown error"))
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt(), notification)
            }
        }

        fun cancelProgressNotification(context: Context, downloadId: Long) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(PROGRESS_NOTIFICATION_BASE_ID + downloadId.toInt())
        }
    }
}
