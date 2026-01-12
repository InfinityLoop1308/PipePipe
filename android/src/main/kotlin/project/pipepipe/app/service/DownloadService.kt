package project.pipepipe.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import dev.icerock.moko.resources.desc.desc

/**
 * Foreground service for managing downloads
 * Keeps app alive while downloads are in progress
 */
class DownloadService : Service() {

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Start foreground immediately to satisfy system requirement
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(MR.strings.downloads.desc().toString(this))
            .setContentText(MR.strings.notification_downloads_in_progress.desc().toString(this))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        isForeground = true

        // Check if there are actually active downloads, stop foreground if not
        val activeDownloads = runBlocking { DatabaseOperations.getActiveDownloads() }
        if (activeDownloads.isEmpty()) {
            stopForeground()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle notification actions
        intent?.action?.let { action ->
            if (action == ACTION_RESET_DOWNLOAD_FINISHED || action == ACTION_OPEN_DOWNLOADS_FINISHED) {
                DownloadNotificationManager.reset()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopForeground()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            MR.strings.downloads.desc().toString(this),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Download progress notifications"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val completionChannel = NotificationChannel(
            COMPLETION_CHANNEL_ID,
            MR.strings.download_completion_channel_name.desc().toString(this),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for completed downloads"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(completionChannel)
    }

    private fun updateForegroundState(hasActiveDownloads: Boolean) {
        android.util.Log.d("DownloadService", "updateForegroundState: hasActiveDownloads=$hasActiveDownloads, isForeground=$isForeground")
        if (hasActiveDownloads == isForeground) return

        if (hasActiveDownloads) {
            android.util.Log.d("DownloadService", "Starting foreground")
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(MR.strings.downloads.desc().toString(this))
                .setContentText(MR.strings.notification_downloads_in_progress.desc().toString(this))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            android.util.Log.d("DownloadService", "Stopping foreground")
            stopForeground()
        }
    }

    private fun stopForeground() {
        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val COMPLETION_CHANNEL_ID = "download_completion_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 3001
        private const val COMPLETION_NOTIFICATION_ID = 3002
        private const val ERROR_NOTIFICATION_BASE_ID = 3100

        private const val ACTION_RESET_DOWNLOAD_FINISHED = "project.pipepipe.reset_download_finished"
        private const val ACTION_OPEN_DOWNLOADS_FINISHED = "project.pipepipe.open_downloads_finished"

        private var instance: DownloadService? = null

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateForegroundState(context: Context, hasActiveDownloads: Boolean) {
            android.util.Log.d("DownloadService", "Companion updateForegroundState: hasActiveDownloads=$hasActiveDownloads, instance=$instance")
            instance?.updateForegroundState(hasActiveDownloads)
        }

        fun showCompletionNotification(context: Context, title: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val count = DownloadNotificationManager.increment()
            DownloadNotificationManager.append(title)

            val builder = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setDeleteIntent(makePendingIntent(context, ACTION_RESET_DOWNLOAD_FINISHED))
                .setContentIntent(makePendingIntent(context, ACTION_OPEN_DOWNLOADS_FINISHED))
                .setContentTitle(MR.strings.notification_download_count.desc().toString(context).format(count))
                .setContentText(DownloadNotificationManager.getList())
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(MR.strings.notification_download_count.desc().toString(context).format(count))
                        .bigText(DownloadNotificationManager.getList())
                )

            notificationManager.notify(COMPLETION_NOTIFICATION_ID, builder.build())
        }

        fun showErrorNotification(context: Context, title: String, error: String?) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val builder = NotificationCompat.Builder(context, COMPLETION_CHANNEL_ID)
                .setContentTitle(MR.strings.download_failed.desc().toString(context))
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(error ?: MR.strings.download_unknown_error.desc().toString(context)))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(ERROR_NOTIFICATION_BASE_ID + title.hashCode(), builder)
        }

        private fun makePendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getService(context, action.hashCode(), intent, flags)
        }
    }
}

object DownloadNotificationManager {
    private var count = 0
    private val list = StringBuilder()

    fun increment(): Int {
        count++
        return count
    }

    fun reset() {
        count = 0
        list.clear()
    }

    fun append(title: String) {
        if (list.isEmpty()) {
            list.append(title)
        } else {
            list.append('\n')
            list.append(title)
        }
    }

    fun getList(): String = list.toString()
}
