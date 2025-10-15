package project.pipepipe.app.service

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
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
            val subscriptions = if (group == -1L){
                DatabaseOperations.getAllSubscriptions()
            } else {
                DatabaseOperations.getSubscriptionsByFeedGroup(group)
            }

            val total = subscriptions.size
            var completed = 0
            var failedCount = 0

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

            Result.success(workDataOf(
                "completed" to completed,
                "failed" to failedCount,
                "total" to total
            ))
        } catch (e: Exception) {
            Result.failure(workDataOf(
                "error" to e.message
            ))
        }
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

        return ForegroundInfo(FEED_NOTIFICATION_ID, notification)
    }

    companion object {
        const val FEED_NOTIFICATION_ID = 1001
    }
}
