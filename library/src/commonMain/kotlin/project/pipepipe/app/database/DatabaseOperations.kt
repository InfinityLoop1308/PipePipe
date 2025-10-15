package project.pipepipe.app.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.database.Search_history
import project.pipepipe.app.SharedContext.database
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamType
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object DatabaseOperations {
    suspend fun loadPlaylistsItemsFromDatabase(playlistId: String): List<StreamInfo> = withContext(Dispatchers.IO) {
        return@withContext database.appDatabaseQueries.selectStreamsByPlaylist(playlistId.toLong()).executeAsList()
            .map { row ->
                StreamInfo(
                    url = row.url,
                    serviceId = row.service_id,
                    streamType = StreamType.valueOf(row.stream_type),
                    name = row.title,
                    uploaderUrl = row.uploader_url,
                    uploaderName = row.uploader,
                    viewCount = row.view_count,
                    duration = row.duration.takeIf { it > 0 },
                    uploadDate = row.upload_date?.takeIf { it > 0 },
                    thumbnailUrl = row.thumbnail_url,
                    joinId = row.join_id,
                    progress = row.progress_time,
                    isPaid = row.is_paid != 0L,
                    shortFormContent = row.is_short != 0L
                )
            }
    }

    suspend fun loadStreamHistoryItems(): List<StreamInfo> = withContext(Dispatchers.IO) {
        return@withContext database.appDatabaseQueries.selectStreamHistoryItems().executeAsList().map { row ->
            StreamInfo(
                url = row.url,
                serviceId = row.service_id,
                streamType = StreamType.valueOf(row.stream_type),
                name = row.title,
                uploaderName = row.uploader,
                uploaderUrl = row.uploader_url,
                viewCount = row.view_count,
                duration = row.duration.takeIf { it > 0 },
                uploadDate = row.upload_date?.takeIf { it > 0 },
                thumbnailUrl = row.thumbnail_url,
                progress = row.progress_time,
                localLastViewDate = row.last_access_date,
                localRepeatCount = row.repeat_count,
                isPaid = row.is_paid != 0L,
                shortFormContent = row.is_short != 0L
            )
        }
    }

    suspend fun getFeedStreamsByGroup(groupId: Long): List<StreamInfo> = withContext(Dispatchers.IO) {
        val rows = if (groupId == -1L) {
            database.appDatabaseQueries.selectFeedStreams().executeAsList()
        } else {
            database.appDatabaseQueries.selectFeedStreamsByGroup(groupId).executeAsList()
        }

        rows.map { row ->
            StreamInfo(
                url = row.url,
                serviceId = row.service_id,
                streamType = StreamType.valueOf(row.stream_type),
                name = row.title,
                uploaderName = row.uploader,
                uploaderUrl = row.uploader_url,
                viewCount = row.view_count,
                duration = row.duration.takeIf { it > 0 },
                uploadDate = row.upload_date?.takeIf { it > 0 },
                thumbnailUrl = row.thumbnail_url,
                progress = row.progress_time,
                isPaid = row.is_paid != 0L,
                shortFormContent = row.is_short != 0L
            )
        }
    }

    suspend fun getAllLocalPlaylists(): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        val playlists = getAllLocalPlaylistsRaw()
        return@withContext playlists.map { playlist ->
            val streamCount = loadPlaylistsItemsFromDatabase(playlist.uid.toString()).size
            PlaylistInfo(
                url = "local://playlist/${playlist.uid}",
                name = playlist.name ?: "Unnamed Playlist",
                thumbnailUrl = playlist.thumbnail_url,
                streamCount = streamCount.toLong(),
                isPinned = playlist.is_pinned != 0L,
                uid = playlist.uid
            )
        }
    }

    suspend fun getAllPlaylistsCombined(): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        return@withContext database.appDatabaseQueries.selectAllPlaylistsCombined().executeAsList().map { row ->
            val isLocal = row.playlist_type == "local"

            if (isLocal) {
                val streamCount = loadPlaylistsItemsFromDatabase(row.uid.toString()).size
                PlaylistInfo(
                    url = "local://playlist/${row.uid}",
                    name = row.name ?: "Unnamed Playlist",
                    thumbnailUrl = row.thumbnail_url,
                    streamCount = streamCount.toLong(),
                    isPinned = row.is_pinned != 0L,
                    uid = row.uid,
                    serviceId = null
                )
            } else {
                PlaylistInfo(
                    url = row.url!!,
                    name = row.name ?: "Unnamed Playlist",
                    thumbnailUrl = row.thumbnail_url,
                    uploaderName = row.uploader,
                    streamCount = row.stream_count ?: 0L,
                    uid = row.uid,
                    serviceId = row.service_id
                )
            }
        }
    }

    suspend fun insertOrUpdateStream(streamInfo: StreamInfo) = withContext(Dispatchers.IO) {
        val existingStream = getStreamByUrl(streamInfo.url)

        if (existingStream != null) {
            database.appDatabaseQueries.updateStream(
                service_id = streamInfo.serviceId,
                title = streamInfo.name ?: "Unknown",
                stream_type = streamInfo.streamType.name,
                view_count = streamInfo.viewCount,
                duration = streamInfo.duration ?: 0,
                uploader = streamInfo.uploaderName ?: "Unknown",
                uploader_url = streamInfo.uploaderUrl,
                thumbnail_url = streamInfo.thumbnailUrl,
                upload_date = streamInfo.uploadDate ?: 0,
                is_paid = if (streamInfo.isPaid) 1L else 0L,
                is_short = if (streamInfo.shortFormContent) 1L else 0L,
                url = streamInfo.url
            )
        } else {
            database.appDatabaseQueries.insertStream(
                streamInfo.serviceId,
                streamInfo.url,
                streamInfo.name ?: "Unknown",
                streamInfo.streamType.name,
                streamInfo.viewCount,
                streamInfo.duration?:0,
                streamInfo.uploaderName?: "Unknown",
                streamInfo.uploaderUrl,
                streamInfo.thumbnailUrl,
                streamInfo.uploadDate?:0,
                if (streamInfo.isPaid) 1L else 0L,
                if (streamInfo.shortFormContent) 1L else 0L
            )
        }
    }

    suspend fun getStreamByUrl(url: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectStreamByUrl(url).executeAsOneOrNull()
    }

    suspend fun insertPlaylistAtTop(name: String, thumbnailUrl: String?): Long = withContext(Dispatchers.IO) {
        var newPlaylistId: Long = -1
        database.transaction {
            database.appDatabaseQueries.incrementAllPlaylistDisplayIndexes()

            database.appDatabaseQueries.insertPlaylist(
                name = name,
                thumbnail_url = thumbnailUrl,
                display_index = 0L,
                is_pinned = 0L
            )
            newPlaylistId = database.appDatabaseQueries.lastInsertRowId().executeAsOne()
        }
        return@withContext newPlaylistId
    }

    suspend fun getAllLocalPlaylistsRaw() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectAllPlaylists().executeAsList()
    }

    suspend fun getPlaylistInfoById(playlistId: String): PlaylistInfo? = withContext(Dispatchers.IO) {
        val playlist = getAllLocalPlaylistsRaw().find { it.uid == playlistId.toLong() } ?: return@withContext null
        val streamCount = loadPlaylistsItemsFromDatabase(playlistId).size
        return@withContext PlaylistInfo(
            url = "local://playlist/$playlistId",
            name = playlist.name ?: "Unnamed Playlist",
            thumbnailUrl = playlist.thumbnail_url,
            streamCount = streamCount.toLong(),
            isPinned = playlist.is_pinned != 0L,
            uid = playlist.uid
        )
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deletePlaylist(playlistId)
    }

    suspend fun addStreamToPlaylist(playlistId: Long, streamId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.insertStreamAtEndOfPlaylist(playlistId, streamId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updatePlaylistName(newName, playlistId)
    }

    suspend fun updatePlaylistThumbnail(playlistId: Long, thumbnailUrl: String?) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updatePlaylistThumbnail(thumbnailUrl, playlistId)
    }

    suspend fun setPlaylistPinned(playlistId: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updatePlaylistPinnedState(
            if (isPinned) 1L else 0L,
            playlistId
        )
    }


    suspend fun getPinnedPlaylists(): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        return@withContext database.appDatabaseQueries.selectPinnedPlaylists().executeAsList().map { playlist ->
            val streamCount = loadPlaylistsItemsFromDatabase(playlist.uid.toString()).size
            PlaylistInfo(
                url = "local://playlist/${playlist.uid}",
                name = playlist.name ?: "Unnamed Playlist",
                thumbnailUrl = playlist.thumbnail_url,
                streamCount = streamCount.toLong(),
                isPinned = true,
                uid = playlist.uid
            )
        }
    }


    suspend fun removeStreamFromPlaylistByJoinId(joinId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteStreamFromPlaylistById(joinId)
    }

    suspend fun reorderPlaylistItem(orderedJoinIds: List<Long>) = withContext(Dispatchers.IO) {
        database.transaction {
            // First, shift all join_index values to avoid conflicts
            val offset = orderedJoinIds.size * 1000L // Use a large offset
            orderedJoinIds.forEachIndexed { index, joinId ->
                database.appDatabaseQueries.updatePlaylistStreamOrder(
                    join_index = offset + index,
                    id = joinId
                )
            }

            // Then, set the final values
            orderedJoinIds.forEachIndexed { index, joinId ->
                database.appDatabaseQueries.updatePlaylistStreamOrder(
                    join_index = index.toLong(),
                    id = joinId
                )
            }
        }
    }

    suspend fun updatePlaylistDisplayIndex(playlistId: Long, displayIndex: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updatePlaylistDisplayIndex(displayIndex, playlistId)
    }

    suspend fun updateRemotePlaylistDisplayIndex(playlistId: Long, displayIndex: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateRemotePlaylistDisplayIndex(displayIndex, playlistId)
    }

    suspend fun insertOrUpdateSearchHistory(search: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.transaction {
            val existing = database.appDatabaseQueries
                .selectSearchHistoryBySearch(search)
                .executeAsOneOrNull()

            if (existing != null) {
                database.appDatabaseQueries.updateSearchHistoryDate(now, search)
            } else {
                database.appDatabaseQueries.insertSearchHistory(now, search)
            }
        }
    }

    suspend fun getSearchHistory() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectSearchHistory().executeAsList()
    }

    suspend fun getSearchHistoryByPattern(searchPattern: String): List<Search_history> = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectSearchHistoryBySearchPattern(searchPattern).executeAsList()
    }

    suspend fun deleteSearchHistory(id: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteSearchHistory(id)
    }

    // Updated: Now updates the stream directly instead of inserting into a separate table
    suspend fun updateStreamHistory(url: String, accessDate: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateStreamHistory(accessDate, url)
    }

    suspend fun deleteStreamHistory(url: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteStreamHistory(url)
    }

    suspend fun updateStreamProgress(url: String, progressTime: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateStreamProgress(progressTime, url)
    }

    suspend fun getStreamProgress(url: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectStreamProgress(url).executeAsOneOrNull()
    }

    suspend fun getAllFeedGroups() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectAllFeedGroups().executeAsList()
    }

    suspend fun setFeedGroupPinned(groupId: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateFeedGroupPinnedState(
            if (isPinned) 1L else 0L,
            groupId
        )
    }

    suspend fun getPinnedFeedGroups() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectPinnedFeedGroups().executeAsList()
    }

    suspend fun getEarliestLastUpdatedForAll(): Long? = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectEarliestLastUpdated()
            .executeAsOneOrNull()?.MIN
    }

    suspend fun getEarliestLastUpdatedByFeedGroup(groupId: Long): Long? = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectEarliestLastUpdatedByFeedGroup(groupId)
            .executeAsOneOrNull()?.MIN
    }

    suspend fun getSubscriptionsByFeedGroup(groupId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectSubscriptionsByFeedGroup(groupId).executeAsList()
    }

    suspend fun getSubscriptionsByFeedGroupWithThreshold(groupId: Long, thresholdSeconds: Long) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        database.appDatabaseQueries.selectSubscriptionsByFeedGroupWithThreshold(groupId, currentTime, thresholdSeconds).executeAsList()
    }

    suspend fun getAllSubscriptionsWithThreshold(thresholdSeconds: Long) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        database.appDatabaseQueries.selectAllSubscriptionsWithThreshold(currentTime, thresholdSeconds).executeAsList()
    }

    suspend fun insertOrUpdateSubscription(
        channelInfo: ChannelInfo,
        updateExistedOnly: Boolean = false
    ) = withContext(Dispatchers.IO) {
        database.transaction {
            val existing = database.appDatabaseQueries
                .selectAllSubscriptions()
                .executeAsList()
                .find { it.service_id == channelInfo.serviceId && it.url == channelInfo.url }

            if (existing != null) {
                database.appDatabaseQueries.updateSubscription(
                    channelInfo.name,
                    channelInfo.thumbnailUrl,
                    channelInfo.subscriberCount,
                    existing.url
                )
            } else if (!updateExistedOnly) {
                database.appDatabaseQueries.insertSubscription(
                    channelInfo.serviceId,
                    channelInfo.url,
                    channelInfo.name,
                    channelInfo.thumbnailUrl,
                    channelInfo.subscriberCount
                )
            }
        }
    }

    suspend fun updateSubscriptionNotificationMode(
        url: String,
        notificationMode: Long
    ) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateSubscriptionNotificationMode(
            notificationMode,
            url
        )
    }

    suspend fun getAllSubscriptions() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectAllSubscriptions().executeAsList()
    }

    suspend fun getSubscriptionsByNotificationMode(notificationMode: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectSubscriptionsByNotificationMode(notificationMode).executeAsList()
    }

    suspend fun deleteSubscription(url: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteSubscription(url)
    }

    suspend fun updateSubscriptionFeed(
        subscriptionUrl: String,
        streams: List<StreamInfo>
    ) = withContext(Dispatchers.IO) {
        val subscription = database.appDatabaseQueries
            .selectAllSubscriptions()
            .executeAsList()
            .find { it.url == subscriptionUrl }
            ?: return@withContext

        val thirteenWeeksAgo = System.currentTimeMillis() - (13 * 7 * 24 * 60 * 60 * 1000L)

        val recentStreams = streams.filter { streamInfo ->
            val uploadDate = streamInfo.uploadDate
            uploadDate == null || uploadDate >= thirteenWeeksAgo
        }

        val streamIds = recentStreams.mapNotNull { streamInfo ->
            insertOrUpdateStream(streamInfo)
            getStreamByUrl(streamInfo.url)?.uid
        }

        database.transaction {
            val currentTime = System.currentTimeMillis()

            streamIds.forEach { streamId ->
                database.appDatabaseQueries.insertFeedStream(
                    stream_id = streamId,
                    subscription_id = subscription.uid
                )
            }

            database.appDatabaseQueries.insertOrUpdateFeedLastUpdated(
                subscription_id = subscription.uid,
                last_updated = currentTime
            )
        }
    }

    suspend fun deleteFeedStreamsOlderThan(weeks: Int = 13) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (weeks * 7 * 24 * 60 * 60 * 1000L)
        database.appDatabaseQueries.deleteFeedStreamsOlderThan(cutoffTime)
    }


    suspend fun updateOrInsertStreamHistory(streamInfo: StreamInfo) = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        var streamRow = getStreamByUrl(streamInfo.url)
        if (streamRow == null) {
            insertOrUpdateStream(streamInfo)
            streamRow = getStreamByUrl(streamInfo.url)!!
        }
        updateStreamHistory(streamRow.url, currentTime)
    }

    suspend fun isSubscribed(url: String): Boolean = withContext(Dispatchers.IO) {
        database.appDatabaseQueries
            .selectAllSubscriptions()
            .executeAsList()
            .any { it.url == url }
    }

    suspend fun getSubscriptionByUrl(url: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries
            .selectAllSubscriptions()
            .executeAsList()
            .find { it.url == url }
    }

    suspend fun insertFeedGroupSubscription(groupId: Long, subscriptionId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.insertFeedGroupSubscription(groupId, subscriptionId)
    }

    suspend fun deleteFeedGroupSubscription(groupId: Long, subscriptionId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteFeedGroupSubscription(groupId, subscriptionId)
    }

    suspend fun getFeedGroupsBySubscription(subscriptionId: Long): List<Long> = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectFeedGroupsBySubscription(subscriptionId)
            .executeAsList()
            .map { it.group_id }
    }

    suspend fun renameFeedGroup(groupId: Long, newName: String) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.updateFeedGroupName(newName, groupId)
    }

    suspend fun deleteFeedGroup(groupId: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteFeedGroup(groupId)
    }
    suspend fun insertFeedGroup(name: String, iconId: Long) = withContext(Dispatchers.IO) {
        database.transaction {
            val maxSortOrder = database.appDatabaseQueries
                .selectAllFeedGroups()
                .executeAsList()
                .maxOfOrNull { it.sort_order } ?: -1L

            database.appDatabaseQueries.insertFeedGroup(
                name = name,
                icon_id = iconId,
                sort_order = maxSortOrder + 1,
            )
            database.appDatabaseQueries.lastInsertRowId().executeAsOne()
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun insertErrorLog(
        stacktrace: String,
        task: String,
        errorCode: String,
        request: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.insertErrorLog(
            Clock.System.now().toEpochMilliseconds(),
            stacktrace,
            request,
            task,
            errorCode
        )
        database.appDatabaseQueries.lastInsertRowId().executeAsOne()
    }

    suspend fun deleteErrorLog(id: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.deleteErrorLog(id)
    }

    suspend fun getAllErrorLogs() = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectAllErrorLogs().executeAsList()
    }

    suspend fun getErrorLogById(id: Long) = withContext(Dispatchers.IO) {
        database.appDatabaseQueries.selectErrorLogById(id).executeAsOneOrNull()
    }
}
