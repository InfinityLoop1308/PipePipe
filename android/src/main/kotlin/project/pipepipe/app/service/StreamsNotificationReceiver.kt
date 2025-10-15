package project.pipepipe.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import project.pipepipe.app.MainActivity
import project.pipepipe.app.ui.screens.Screen

class StreamsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "project.pipepipe.app.OPEN_CHANNEL") {
            val channelUrl = intent.getStringExtra("channel_url") ?: return
            val serviceId = intent.getStringExtra("service_id") ?: return
            val notificationId = intent.getIntExtra("notification_id", -1)

            // Cancel the notification
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }

            // Start MainActivity and navigate to channel screen
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_to_channel", true)
                putExtra("channel_url", channelUrl)
                putExtra("service_id", serviceId)
            }
            context.startActivity(mainIntent)
        }
    }
}
