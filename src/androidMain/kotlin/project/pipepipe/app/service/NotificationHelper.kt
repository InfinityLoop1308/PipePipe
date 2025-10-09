package project.pipepipe.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR

object NotificationHelper {
    fun initNotificationChannels(context: Context) {
        createFeedNotificationChannel(context)
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
}

