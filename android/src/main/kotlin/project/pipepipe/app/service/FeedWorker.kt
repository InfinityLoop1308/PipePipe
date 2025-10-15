package project.pipepipe.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.R as AppR

class FeedWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0, 0, 0)
    }

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(0))
        val group = inputData.getLong("groupId", -1)

        return try {
            // Get feed update threshold setting (in seconds)
            val thresholdSeconds = SharedContext.settingsManager.getString("feed_update_threshold_key", "300").toLongOrNull() ?: 300L

            val subscriptions = if (thresholdSeconds == 0L) {
                // Threshold is 0, always update all subscriptions
                if (group == -1L) {
                    DatabaseOperations.getAllSubscriptions()
                } else {
                    DatabaseOperations.getSubscriptionsByFeedGroup(group)
                }
            } else {
                // Use threshold to filter subscriptions
                if (group == -1L) {
                    DatabaseOperations.getAllSubscriptionsWithThreshold(thresholdSeconds)
                } else {
                    DatabaseOperations.getSubscriptionsByFeedGroupWithThreshold(group, thresholdSeconds)
                }
            }

            val total = subscriptions.size
            if (total == 0) {
                ToastManager.show(MR.strings.feed_all_subscriptions_up_to_date.desc().toString(context = applicationContext))
            }
            var completed = 0
            var failedCount = 0
            val failedChannels = mutableListOf<String>()

            subscriptions.forEach { subscription ->
                val result = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_INFO,
                        subscription.url,
                        subscription.service_id
                    )
                }

                if (result.pagedData != null) {
                    DatabaseOperations.updateSubscriptionFeed(
                        subscription.url!!,
                        result.pagedData!!.itemList as List<StreamInfo>
                    )
                } else {
                    failedCount++
                    subscription.name?.let { failedChannels.add(it) }
                }

                completed++
                val progress = (completed * 100) / total

                // 更新进度
                setProgress(workDataOf(
                    "progress" to progress,
                    "completed" to completed,
                    "total" to total,
                    "failed" to failedCount
                ))

                // 更新前台通知
                setForeground(createForegroundInfo(progress, completed, total, failedCount))
            }

            DatabaseOperations.deleteFeedStreamsOlderThan(13)

            // If there are failures, show persistent notification. Otherwise cancel it.
            if (failedCount > 0) {
                showFailureNotification(failedChannels)
            } else {
                cancelNotification()
            }

            Result.success(workDataOf(
                "completed" to completed,
                "failed" to failedCount,
                "total" to total
            ))
        } catch (e: Exception) {
            // Cancel the foreground notification even on failure
            cancelNotification()

            Result.failure(workDataOf(
                "error" to e.message
            ))
        }
    }

    private fun cancelNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.FEED_NOTIFICATION_ID)
    }

    private fun showFailureNotification(failedChannels: List<String>) {
        val intent = Intent("project.pipepipe.app.SHOW_FEED_FAILURES").apply {
            putStringArrayListExtra("failed_channels", ArrayList(failedChannels))
            setPackage(applicationContext.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "feed_update")
            .setContentTitle(MR.strings.feed_load_error.desc().toString(applicationContext))
            .setContentText(MR.strings.stream_processing_failed_count.desc().toString(applicationContext).format(failedChannels.size))
            .setSmallIcon(AppR.drawable.ic_pipepipe)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.FEED_FAILURE_NOTIFICATION_ID, notification)
    }

    private fun createForegroundInfo(
        progress: Int,
        completed: Int = 0,
        total: Int = 0,
        failed: Int = 0
    ): ForegroundInfo {
        val contentText = if (total > 0) {
            "$completed/$total ${MR.strings.stream_processing_failed_count.desc().toString(applicationContext).format(failed)}"
        } else {
            MR.strings.feed_notification_loading.desc().toString(applicationContext)
        }

        val notification = NotificationCompat.Builder(applicationContext, "feed_update")
            .setContentTitle(MR.strings.feed_notification_loading.desc().toString(applicationContext))
            .setContentText(contentText)
            .setProgress(100, progress, progress == 0)
            .setSmallIcon(AppR.drawable.ic_pipepipe)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.FEED_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.FEED_NOTIFICATION_ID, notification)
        }
    }
}
