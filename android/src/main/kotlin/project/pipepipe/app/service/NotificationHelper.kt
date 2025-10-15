package project.pipepipe.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR

object NotificationHelper {
    const val SLEEP_TIMER_CHANNEL_ID = "sleep_timer_channel"

    fun initNotificationChannels(context: Context) {
        createFeedNotificationChannel(context)
        createSleepTimerNotificationChannel(context)
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
}

