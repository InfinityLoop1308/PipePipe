package project.pipepipe.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import project.pipepipe.app.MainActivity

class FeedFailureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "project.pipepipe.app.SHOW_FEED_FAILURES") {
            val failedChannels = intent.getStringArrayListExtra("failed_channels") ?: return

            // Cancel the failure notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.FEED_FAILURE_NOTIFICATION_ID)

            // Start MainActivity to show dialog
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putStringArrayListExtra("failed_channels", ArrayList(failedChannels))
                putExtra("show_feed_failures", true)
            }
            context.startActivity(mainIntent)
        }
    }
}
