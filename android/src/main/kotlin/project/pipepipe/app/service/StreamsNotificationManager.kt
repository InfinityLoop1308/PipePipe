package project.pipepipe.app.service

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.runBlocking
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import java.util.concurrent.TimeUnit

object StreamsNotificationManager {
    private const val WORK_NAME = "streams_notification_work"

    /**
     * Schedule or cancel periodic work based on settings
     */
    fun schedulePeriodicWork(context: Context) {
        val enableNotifications = SharedContext.settingsManager.getBoolean("enable_streams_notifications", false)

        // Check if there are any subscriptions with notification_mode = 1
        val hasNotificationSubscriptions = runBlocking {
            DatabaseOperations.getSubscriptionsByNotificationMode(1L).isNotEmpty()
        }

        if (enableNotifications && hasNotificationSubscriptions) {
            // Get interval from settings (in seconds)
            val intervalSeconds = SharedContext.settingsManager.getString("streams_notifications_interval_key", "14400").toLongOrNull() ?: 14400L

            // Convert to minutes (WorkManager requires minutes, minimum 15)
            val intervalMinutes = maxOf(15L, intervalSeconds / 60)

            val workRequest = PeriodicWorkRequestBuilder<StreamsNotificationWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            // Cancel the work if notifications are disabled or no subscriptions
            cancelPeriodicWork(context)
        }
    }

    /**
     * Cancel the periodic work
     */
    fun cancelPeriodicWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Trigger an immediate check (useful for testing or manual refresh)
     */
    fun triggerImmediateCheck(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<StreamsNotificationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
