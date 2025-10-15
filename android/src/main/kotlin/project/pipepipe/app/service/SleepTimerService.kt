package project.pipepipe.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR
import project.pipepipe.app.utils.toDurationString
import project.pipepipe.app.R as AppR

class SleepTimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var remainingTimeMillis: Long = 0
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START_TIMER = "project.pipepipe.app.action.START_SLEEP_TIMER"
        const val ACTION_CANCEL_TIMER = "project.pipepipe.app.action.CANCEL_SLEEP_TIMER"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"

        fun startTimer(context: Context, minutes: Int) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_START_TIMER
                putExtra(EXTRA_DURATION_MINUTES, minutes)
            }
            context.startForegroundService(intent)
        }

        fun cancelTimer(context: Context) {
            val intent = Intent(context, SleepTimerService::class.java).apply {
                action = ACTION_CANCEL_TIMER
            }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 15)
                startCountdown(minutes)
            }
            ACTION_CANCEL_TIMER -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCountdown(minutes: Int) {
        // Cancel any existing timer
        countDownTimer?.cancel()

        val durationMillis = minutes * 60 * 1000L
        remainingTimeMillis = durationMillis

        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMillis = millisUntilFinished
                updateNotification(millisUntilFinished)
            }

            override fun onFinish() {
                stopPlaybackService()
                stopTimer()
            }
        }.start()

        // Start as foreground service with initial notification
        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))
    }

    private fun updateNotification(millisUntilFinished: Long) {
        val notification = buildNotification(millisUntilFinished)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(millisUntilFinished: Long): android.app.Notification {
        val timeText = millisUntilFinished.toDurationString(true)

        // Cancel button action
        val cancelIntent = Intent(this, SleepTimerService::class.java).apply {
            action = ACTION_CANCEL_TIMER
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.SLEEP_TIMER_CHANNEL_ID)
            .setSmallIcon(AppR.drawable.ic_pipepipe)
            .setContentTitle(MR.strings.sleep_timer_title.desc().toString(context = this))
            .setContentText(
                MR.strings.sleep_timer_notification_content.desc().toString(context = this).format(timeText)
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_delete,
                MR.strings.cancel.desc().toString(context = this),
                cancelPendingIntent
            )
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun stopPlaybackService() {
        val intent = Intent("project.pipepipe.app.action.STOP_PLAYBACK")
        sendBroadcast(intent)
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
