package project.pipepipe.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR

object NotificationHelper {
    const val SLEEP_TIMER_CHANNEL_ID = "sleep_timer_channel"
    const val FEED_NOTIFICATION_ID = 1001
    const val FEED_FAILURE_NOTIFICATION_ID = 1002
    const val STREAMS_NOTIFICATION_BASE_ID = 2001
    const val STREAMS_NOTIFICATION_SUMMARY_ID = 2000
    const val STREAMS_NOTIFICATION_FAILURE_ID = 2100

    fun initNotificationChannels(context: Context) {
        createFeedNotificationChannel(context)
        createSleepTimerNotificationChannel(context)
        createStreamsNotificationChannel(context)
        createAppUpdateNotificationChannel(context)
    }

    private fun createFeedNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            "feed_update",
            MR.strings.feed_notification_loading.desc().toString(context = context),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createSleepTimerNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            SLEEP_TIMER_CHANNEL_ID,
            MR.strings.sleep_timer_title.desc().toString(context = context),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = MR.strings.sleep_timer_notification_channel_description.desc().toString(context = context)
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createStreamsNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            "streams_notifications",
            MR.strings.enable_streams_notifications_title.desc().toString(context = context),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = MR.strings.enable_streams_notifications_summary.desc().toString(context = context)
            setShowBadge(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createAppUpdateNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            "app_update",
            MR.strings.settings_section_update.desc().toString(context = context),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = MR.strings.update_app_enabled_summary.desc().toString(context = context)
            setShowBadge(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

