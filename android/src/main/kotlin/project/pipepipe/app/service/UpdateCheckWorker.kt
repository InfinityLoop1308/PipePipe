package project.pipepipe.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.icerock.moko.resources.desc.desc
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.MR
import project.pipepipe.app.R
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.UpdateHelper

class UpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            checkNewVersion()
            Result.success()
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                DatabaseOperations.insertErrorLog(
                    stacktrace = e.stackTraceToString(),
                    task = "Update Check",
                    errorCode = "UPDATE_CHECK_FAILED",
                    request = GITHUB_API_URL
                )
            }
            Result.failure()
        }
    }

    private suspend fun checkNewVersion() {
        val isManual = inputData.getBoolean(IS_MANUAL, false)

        if (!isManual) {
            val expiry = SharedContext.settingsManager.getLong(UPDATE_EXPIRY_KEY, 0L)
            if (System.currentTimeMillis() <= expiry) {
                return
            }
        }

        val response = SharedContext.downloader.get(GITHUB_API_URL)
        handleResponse(response.body(), isManual)
    }

    private suspend fun handleResponse(responseBody: String, isManual: Boolean) {
        val newExpiry = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
        SharedContext.settingsManager.putLong(UPDATE_EXPIRY_KEY, newExpiry)

        val includePreRelease = SharedContext.settingsManager.getBoolean(SHOW_PRERELEASE_KEY, false)
        val currentVersionName = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName!!

        val result = UpdateHelper.checkForUpdate(
            responseBody = responseBody,
            currentVersionName = currentVersionName,
            supportedAbis = Build.SUPPORTED_ABIS,
            includePreRelease = includePreRelease
        )

        if (!result.hasUpdate) {
            if (isManual) {
                withContext(Dispatchers.Main) {
                    ToastManager.show(MR.strings.app_update_unavailable.desc().toString(context = applicationContext))
                }
            }
            return
        }

        showUpdateNotification(result.versionName ?: return, result.downloadUrl)
    }

    private suspend fun showUpdateNotification(versionName: String, apkLocationUrl: String?) {
        val intent = Intent(Intent.ACTION_VIEW, apkLocationUrl?.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(applicationContext, "app_update")
            .setSmallIcon(R.drawable.ic_pipepipe)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setContentTitle(MR.strings.app_update_notification_title.desc().toString(context = applicationContext))
            .setContentText(MR.strings.app_update_notification_message.desc().toString(context = applicationContext).format(versionName))

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build())
    }

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/InfinityLoop1308/PipePipe/releases"
        private const val IS_MANUAL = "isManual"
        private const val UPDATE_NOTIFICATION_ID = 3000

        const val UPDATE_ENABLED_KEY = "update_app_enabled"
        const val UPDATE_EXPIRY_KEY = "update_expiry"
        const val SHOW_PRERELEASE_KEY = "show_prerelease"

        fun enqueueUpdateCheck(context: Context, isManual: Boolean) {
            val workRequest = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setInputData(workDataOf(IS_MANUAL to isManual))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
