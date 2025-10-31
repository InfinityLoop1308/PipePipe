package project.pipepipe.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.database.Subscriptions
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import project.pipepipe.app.R as AppR

data class ChannelStreamsData(
    val subscription: Subscriptions,
    val streams: List<StreamInfo>
)

class StreamsNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {


    override suspend fun doWork(): Result {
        // Check if notifications are enabled
        val enableNotifications = SharedContext.settingsManager.getBoolean("enable_streams_notifications", false)
        if (!enableNotifications) {
            return Result.success()
        }

        return try {
            // Get subscriptions with notification_mode = 1
            val subscriptions = DatabaseOperations.getSubscriptionsByNotificationMode(1L)

            if (subscriptions.isEmpty()) {
                return Result.success()
            }

            // Load service fetch intervals
            val serviceFetchIntervals = loadServiceFetchIntervals()

            // Thread-safe data structures
            val newStreamsMap = ConcurrentHashMap<String, ChannelStreamsData>()
            val failedChannels = Collections.synchronizedList(mutableListOf<String>())

            // Process subscriptions concurrently with service-level rate limiting
            processSubscriptionsConcurrently(
                subscriptions = subscriptions,
                serviceFetchIntervals = serviceFetchIntervals,
                maxConcurrency = 6
            ) { subscription ->
                try {
                    val result = withContext(Dispatchers.IO) {
                        executeJobFlow(
                            SupportedJobType.FETCH_INFO,
                            subscription.url,
                            subscription.service_id
                        )
                    }

                    if (result.pagedData != null) {
                        val newStreams = result.pagedData!!.itemList as List<StreamInfo>

                        // Get the last updated time for this subscription
                        val lastUpdated = DatabaseOperations.getFeedLastUpdated(subscription.uid)

                        // Filter for streams published after last_updated
                        val actualNewStreams = newStreams.filter { stream ->
                            val uploadDate = stream.uploadDate
                            uploadDate != null && (lastUpdated == null || uploadDate > lastUpdated)
                        }

                        if (actualNewStreams.isNotEmpty()) {
                            newStreamsMap[subscription.name ?: "Unknown"] = ChannelStreamsData(
                                subscription = subscription,
                                streams = actualNewStreams
                            )
                        }

                        // Update the feed
                        DatabaseOperations.updateSubscriptionFeed(
                            subscription.url!!,
                            newStreams
                        )
                    } else {
                        subscription.name?.let { failedChannels.add(it) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    subscription.name?.let { failedChannels.add(it) }
                }
            }

            // Show notifications for new streams
            if (newStreamsMap.isNotEmpty()) {
                showNewStreamsNotification(newStreamsMap)
            }

            // Show failure notification if any channels failed
            if (failedChannels.isNotEmpty()) {
                showFailureNotification(failedChannels)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun loadChannelAvatar(avatarUrl: String?): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                if (avatarUrl.isNullOrEmpty()) return@withContext null

                val imageLoader = ImageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(avatarUrl)
                    .size(Size(256, 256))
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                (result.image!!.asDrawable(applicationContext.resources) as BitmapDrawable).bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun showNewStreamsNotification(newStreamsMap: Map<String, ChannelStreamsData>) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (newStreamsMap.size == 1) {
            // Single channel notification
            val (channelName, data) = newStreamsMap.entries.first()
            val streams = data.streams
            val subscription = data.subscription

            // Load avatar
            val largeIcon = loadChannelAvatar(subscription.avatar_url)

            // Build notification content based on stream count
            val (title, contentText, style) = when {
                streams.size == 1 -> {
                    // Single stream: show stream title directly
                    Triple(
                        channelName,
                        streams[0].name ?: MR.strings.unknown_title.desc().toString(applicationContext),
                        null
                    )
                }
                streams.size in 2..3 -> {
                    // 2-3 streams: use InboxStyle
                    val newText = MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size)
                    Triple(
                        "$channelName ($newText)",
                        MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size),
                        NotificationCompat.InboxStyle().also { style ->
                            streams.forEach { stream ->
                                style.addLine(stream.name ?: MR.strings.unknown_title.desc().toString(applicationContext))
                            }
                        }
                    )
                }
                else -> {
                    // 4+ streams: just show count
                    val newText = MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size)
                    Triple(
                        "$channelName ($newText)",
                        MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size),
                        null
                    )
                }
            }

            val builder = NotificationCompat.Builder(applicationContext, "streams_notifications")
                .setContentTitle(title)
                .setContentText(contentText)
                .setSmallIcon(AppR.drawable.ic_pipepipe)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            largeIcon?.let { builder.setLargeIcon(it) }
            style?.let { builder.setStyle(it) }

            // Add PendingIntent to open channel when notification is clicked
            val intent = Intent("project.pipepipe.app.OPEN_CHANNEL").apply {
                putExtra("channel_url", subscription.url)
                putExtra("service_id", subscription.service_id.toString())
                putExtra("notification_id", NotificationHelper.STREAMS_NOTIFICATION_BASE_ID)
                setPackage(applicationContext.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                NotificationHelper.STREAMS_NOTIFICATION_BASE_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)

            notificationManager.notify(NotificationHelper.STREAMS_NOTIFICATION_BASE_ID, builder.build())
        } else {
            // Multiple channels - show summary notification
            val totalStreams = newStreamsMap.values.sumOf { it.streams.size }
            val summaryNotification = NotificationCompat.Builder(applicationContext, "streams_notifications")
                .setContentTitle(MR.strings.streams_notification_multiple_channels.desc().toString(applicationContext))
                .setContentText(MR.strings.streams_notification_multiple_content.desc().toString(applicationContext).format(newStreamsMap.size, totalStreams))
                .setSmallIcon(AppR.drawable.ic_pipepipe)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("streams_notifications_group")
                .setGroupSummary(true)
                .build()

            notificationManager.notify(NotificationHelper.STREAMS_NOTIFICATION_SUMMARY_ID, summaryNotification)

            // Show individual notifications for each channel
            newStreamsMap.entries.forEachIndexed { index, (channelName, data) ->
                val streams = data.streams
                val subscription = data.subscription

                // Load avatar
                val largeIcon = loadChannelAvatar(subscription.avatar_url)

                // Build notification content
                val (title, contentText, style) = when {
                    streams.size == 1 -> {
                        Triple(
                            channelName,
                            streams[0].name ?: MR.strings.unknown_title.desc().toString(applicationContext),
                            null
                        )
                    }
                    streams.size in 2..3 -> {
                        val newText = MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size)
                        Triple(
                            "$channelName ($newText)",
                            MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size),
                            NotificationCompat.InboxStyle().also { style ->
                                streams.forEach { stream ->
                                    style.addLine(stream.name ?: MR.strings.unknown_title.desc().toString(applicationContext))
                                }
                            }
                        )
                    }
                    else -> {
                        val newText = MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size)
                        Triple(
                            "$channelName ($newText)",
                            MR.strings.streams_notification_new_content.desc().toString(applicationContext).format(streams.size),
                            null
                        )
                    }
                }

                val builder = NotificationCompat.Builder(applicationContext, "streams_notifications")
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setSmallIcon(AppR.drawable.ic_pipepipe)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setGroup("streams_notifications_group")

                largeIcon?.let { builder.setLargeIcon(it) }
                style?.let { builder.setStyle(it) }

                // Add PendingIntent to open channel when notification is clicked
                val notificationId = NotificationHelper.STREAMS_NOTIFICATION_BASE_ID + index + 1
                val intent = Intent("project.pipepipe.app.OPEN_CHANNEL").apply {
                    putExtra("channel_url", subscription.url)
                    putExtra("service_id", subscription.service_id.toString())
                    putExtra("notification_id", notificationId)
                    setPackage(applicationContext.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)

                notificationManager.notify(notificationId, builder.build())
            }
        }
    }

    private fun showFailureNotification(failedChannels: List<String>) {
        val intent = Intent("project.pipepipe.app.SHOW_STREAMS_NOTIFICATION_FAILURES").apply {
            putStringArrayListExtra("failed_channels", ArrayList(failedChannels))
            setPackage(applicationContext.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "streams_notifications")
            .setContentTitle(MR.strings.streams_notification_error.desc().toString(applicationContext))
            .setContentText(MR.strings.stream_processing_failed_count.desc().toString(applicationContext).format(failedChannels.size))
            .setSmallIcon(AppR.drawable.ic_pipepipe)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.STREAMS_NOTIFICATION_FAILURE_ID, notification)
    }
}
