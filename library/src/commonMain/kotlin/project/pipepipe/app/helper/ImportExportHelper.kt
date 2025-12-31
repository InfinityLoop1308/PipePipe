package project.pipepipe.app.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.serialize.SavedTabPayload
import project.pipepipe.app.serialize.SavedTabsPayload
import project.pipepipe.app.serialize.SubscriptionExportData
import project.pipepipe.app.serialize.SubscriptionItemJson
import project.pipepipe.shared.infoitem.ChannelInfo
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ImportExportHelper {
    private const val PRIMARY_DATABASE_ZIP_ENTRY = "pipepipe.db"
    private const val PRIMARY_SETTINGS_ZIP_ENTRY = "pipepipe.settings"

    private val DATABASE_ZIP_ENTRY_ALIASES = setOf("pipepipe.db", "newpipe.db")
    private val SETTINGS_ZIP_ENTRY_ALIASES = setOf("pipepipe.settings", "newpipe.settings")

    private const val SAVED_TABS_KEY = "saved_tabs_key"
    private const val CUSTOM_TABS_CONFIG_KEY = "custom_tabs_config_key"

    private val savedTabsJsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val subscriptionJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun createBackupBytes(
        includeDatabase: Boolean,
        includeSettings: Boolean
    ): ByteArray = withContext(Dispatchers.IO) {
        ByteArrayOutputStream().use { byteStream ->
            ZipOutputStream(BufferedOutputStream(byteStream)).use { zip ->
                if (includeDatabase) {
                    val dbBytes = SharedContext.platformDatabaseActions.getDatabaseBytes()
                    if (dbBytes != null) {
                        addBytesToZip(zip, dbBytes, PRIMARY_DATABASE_ZIP_ENTRY)
                    }
                }

                if (includeSettings) {
                    val settingsBytes = serializeSettingsToBytes()
                    addBytesToZip(zip, settingsBytes, PRIMARY_SETTINGS_ZIP_ENTRY)
                }
            }
            byteStream.toByteArray()
        }
    }

    data class ImportMessages(
        val nothingSelected: String,
        val databaseNotFound: String,
        val settingsNotFound: String,
        val successBoth: String,
        val successDatabase: String,
        val successSettings: String,
        val successCompleted: String,
        val failed: String
    )

    suspend fun importBackupFromBytes(
        bytes: ByteArray,
        importDatabase: Boolean,
        importSettings: Boolean,
        messages: ImportMessages,
        onSuccess: () -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!importDatabase && !importSettings) {
            ToastManager.show(messages.nothingSelected)
            return@withContext
        }

        // Extract entries from ZIP
        var dbBytes: ByteArray? = null
        var settingsBytes: ByteArray? = null

        ByteArrayInputStream(bytes).use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    when {
                        importDatabase && dbBytes == null && entryName in DATABASE_ZIP_ENTRY_ALIASES -> {
                            dbBytes = zip.readBytes()
                        }
                        importSettings && settingsBytes == null && entryName in SETTINGS_ZIP_ENTRY_ALIASES -> {
                            settingsBytes = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        // Backup current database before importing
        var dbBackupBytes: ByteArray? = null

        try {
            if (importDatabase) {
                if (dbBytes == null) {
                    throw IllegalStateException(messages.databaseNotFound)
                }

                // Backup current database
                dbBackupBytes = SharedContext.platformDatabaseActions.getDatabaseBytes()

                // Reset database connection before writing
                SharedContext.platformDatabaseActions.resetDatabase()

                // Write new database
                SharedContext.platformDatabaseActions.writeDatabaseBytes(dbBytes)

                // Run migrations
                val importedVersion = getDatabaseVersion(dbBytes)
                if (importedVersion != null) {
                    SharedContext.platformDatabaseActions.runDatabaseMigration(importedVersion)
                }

                // Verify the imported database
                try {
                    SharedContext.platformDatabaseActions.verifyDatabase()
                } catch (e: Exception) {
                    // Restore backup on failure
                    if (dbBackupBytes != null) {
                        SharedContext.platformDatabaseActions.writeDatabaseBytes(dbBackupBytes)
                        SharedContext.platformDatabaseActions.resetDatabase()
                    }
                    throw e
                }
            }

            if (importSettings) {
                if (settingsBytes == null) {
                    throw IllegalStateException(messages.settingsNotFound)
                }

                val snapshot = deserializeSettingsFromBytes(settingsBytes!!)
                val settingsManager = SharedContext.settingsManager

                val currentSupportedServices = settingsManager.getString("supported_services")

                settingsManager.restoreFrom(snapshot)

                if (currentSupportedServices.isNotBlank()) {
                    settingsManager.putString("supported_services", currentSupportedServices)
                }

                val listViewMode = snapshot["list_view_mode_key"] as? String
                when (listViewMode) {
                    "grid", "large_grid", "auto" -> {
                        settingsManager.putBoolean("grid_layout_enabled_key", true)
                        settingsManager.putString("grid_columns_key", "4")
                    }
                    "card" -> {
                        settingsManager.putBoolean("grid_layout_enabled_key", true)
                        settingsManager.putString("grid_columns_key", "1")
                    }
                    "list" -> {
                        settingsManager.putBoolean("grid_layout_enabled_key", false)
                    }
                }

                val savedTabsJson = extractSavedTabsJson(snapshot)
                    ?: settingsManager.getString(SAVED_TABS_KEY).takeIf { it.isNotBlank() }

                if (!savedTabsJson.isNullOrBlank()) {
                    convertSavedTabsToNewConfig(savedTabsJson)
                }

                val enableWatchHistory = snapshot["enable_watch_history"] as? Boolean
                if (enableWatchHistory != null) {
                    val watchHistoryMode = if (enableWatchHistory) "on_play" else "disabled"
                    settingsManager.putString("watch_history_mode", watchHistoryMode)
                }

                SharedContext.initializeSupportedServices()
            }

            val message = when {
                importDatabase && importSettings -> messages.successBoth
                importDatabase -> messages.successDatabase
                importSettings -> messages.successSettings
                else -> messages.successCompleted
            }
            ToastManager.show(message)
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            ToastManager.show(messages.failed.format(e.message ?: ""))
        }
    }

    suspend fun checkBackupVersion(bytes: ByteArray): Int? = withContext(Dispatchers.IO) {
        try {
            ByteArrayInputStream(bytes).use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName in DATABASE_ZIP_ENTRY_ALIASES) {
                            val dbBytes = zip.readBytes()
                            return@withContext getDatabaseVersion(dbBytes)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Read SQLite user_version from database bytes.
     * SQLite stores user_version at offset 60, 4 bytes big-endian.
     */
    private fun getDatabaseVersion(dbBytes: ByteArray): Int? {
        if (dbBytes.size < 64) return null
        return ((dbBytes[60].toInt() and 0xFF) shl 24) or
               ((dbBytes[61].toInt() and 0xFF) shl 16) or
               ((dbBytes[62].toInt() and 0xFF) shl 8) or
               (dbBytes[63].toInt() and 0xFF)
    }

    suspend fun exportSubscriptionsToJson(): ByteArray = withContext(Dispatchers.IO) {
        val subscriptions = DatabaseOperations.getAllSubscriptions()

        val exportData = SubscriptionExportData(
            subscriptions = subscriptions.map { subscription ->
                SubscriptionItemJson(
                    serviceId = subscription.service_id,
                    url = subscription.url!!,
                    name = subscription.name!!
                )
            }
        )

        val jsonString = subscriptionJson.encodeToString(exportData)
        jsonString.toByteArray(Charsets.UTF_8)
    }

    suspend fun importSubscriptionsFromJson(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val jsonString = bytes.toString(Charsets.UTF_8)
        val jsonElement = Json.parseToJsonElement(jsonString)

        if (jsonElement !is JsonObject) {
            throw IllegalArgumentException("Invalid JSON format: root must be an object")
        }

        val subscriptionsArray = jsonElement["subscriptions"]?.jsonArray
            ?: throw IllegalArgumentException("Missing 'subscriptions' array in JSON")

        var importCount = 0

        for (item in subscriptionsArray) {
            if (item !is JsonObject) continue

            try {
                val url = item["url"]?.jsonPrimitive?.content ?: continue
                val name = item["name"]?.jsonPrimitive?.content ?: continue
                val serviceId = item["service_id"]?.jsonPrimitive?.int ?: continue

                if (url.isNotEmpty() && name.isNotEmpty()) {
                    val channelInfo = ChannelInfo(
                        serviceId = serviceId,
                        url = url,
                        name = name,
                        thumbnailUrl = null,
                        subscriberCount = null,
                        description = null
                    )

                    DatabaseOperations.insertOrUpdateSubscription(channelInfo)
                    importCount++
                }
            } catch (e: Exception) {
                GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
            }
        }

        importCount
    }

    // ========== Backup Helper Methods ==========

    private fun extractSavedTabsJson(snapshot: Map<String, Any?>): String? {
        return snapshot[SAVED_TABS_KEY] as? String
    }

    private suspend fun convertSavedTabsToNewConfig(rawJson: String) {
        val payload = savedTabsJsonParser.decodeFromString<SavedTabsPayload>(rawJson)
        val settingsManager = SharedContext.settingsManager

        val routes = mutableSetOf<String>()
        val newTabs = mutableListOf<String>()

        payload.tabs.forEach { tab ->
            val route = convertTabToRoute(tab)
            if (route != null && !routes.contains(route)) {
                routes.add(route)
                newTabs.add(route)
            }
        }

        val jsonString = Json.encodeToString(newTabs)
        settingsManager.putString(CUSTOM_TABS_CONFIG_KEY, jsonString)
    }

    private suspend fun convertTabToRoute(tab: SavedTabPayload): String? {
        return when (tab.tabId) {
            0 -> "blank"
            1 -> "subscriptions"
            2 -> "feed/-1"
            3 -> "bookmarked_playlists"
            4 -> "history"
            5 -> convertKioskTab(tab)
            6 -> convertChannelTab(tab)
            7 -> convertDefaultKioskTab()
            8 -> convertPlaylistTab(tab)
            9 -> convertChannelGroupTab(tab)
            else -> null
        }
    }

    private fun getSupportedServices(): List<SupportedServiceInfo> {
        return try {
            val jsonString = SharedContext.settingsManager.getString("supported_services")
            if (jsonString.isNotEmpty()) {
                Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun convertLegacyKioskId(kioskId: String?): String {
        return when (kioskId) {
            "Trending" -> "trending"
            "Recommended Lives" -> "recommended_lives"
            else -> "trending"
        }
    }

    private fun convertDefaultKioskTab(): String? {
        val services = getSupportedServices()
        val firstTrending = services.firstOrNull()?.trendingList?.firstOrNull() ?: return null

        val encodedUrl = URLEncoder.encode(firstTrending.url, "UTF-8")
        val encodedName = URLEncoder.encode(firstTrending.name, "UTF-8")
        return "playlist?url=$encodedUrl&name=$encodedName&serviceId=${firstTrending.serviceId}"
    }

    private fun convertKioskTab(tab: SavedTabPayload): String? {
        val serviceId = tab.serviceId ?: return null
        val targetKioskName = convertLegacyKioskId(tab.kioskId)

        val services = getSupportedServices()
        val service = services.find { it.serviceId == serviceId } ?: return null

        val trendingInfo = service.trendingList.find { it.name == targetKioskName }
            ?: service.trendingList.firstOrNull()
            ?: return null

        val encodedUrl = URLEncoder.encode(trendingInfo.url, "UTF-8")
        val encodedName = URLEncoder.encode(trendingInfo.name, "UTF-8")
        return "playlist?url=$encodedUrl&name=$encodedName&serviceId=${trendingInfo.serviceId}"
    }

    private suspend fun convertChannelTab(tab: SavedTabPayload): String? {
        val url = tab.channelUrl ?: return null
        val subscription = DatabaseOperations.getSubscriptionByUrl(url) ?: return null

        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedName = URLEncoder.encode(subscription.name ?: "Unknown", "UTF-8")

        return "channel?url=$encodedUrl&serviceId=${subscription.service_id}&name=$encodedName"
    }

    private suspend fun convertPlaylistTab(tab: SavedTabPayload): String? {
        if (tab.playlistId != null && tab.playlistId != -1L) {
            val playlist = DatabaseOperations.getPlaylistInfoById(tab.playlistId.toString())
                ?: return null

            val encodedUrl = URLEncoder.encode(playlist.url, "UTF-8")
            val encodedName = URLEncoder.encode(playlist.name, "UTF-8")
            return "playlist?url=$encodedUrl&name=$encodedName"
        }

        if (tab.playlistUrl != null) {
            val remotePlaylist = DatabaseOperations.getRemotePlaylistByUrl(tab.playlistUrl!!)
                ?: return null

            val encodedUrl = URLEncoder.encode(tab.playlistUrl!!, "UTF-8")
            val encodedName = URLEncoder.encode(remotePlaylist.name ?: "Unknown", "UTF-8")

            return "playlist?url=$encodedUrl&name=$encodedName&serviceId=${remotePlaylist.service_id}"
        }

        return null
    }

    private suspend fun convertChannelGroupTab(tab: SavedTabPayload): String? {
        val groupId = tab.groupId ?: return null
        val groupName = tab.groupName ?: "Unknown"
        val feedGroup = DatabaseOperations.getFeedGroupById(groupId)
        val iconId = feedGroup?.icon_id?.toInt() ?: 0
        val encodedName = URLEncoder.encode(groupName, "UTF-8")
        return "feed/$groupId?name=$encodedName&iconId=$iconId"
    }

    private fun addBytesToZip(zip: ZipOutputStream, bytes: ByteArray, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun serializeSettingsToBytes(): ByteArray {
        val snapshot = HashMap(SharedContext.settingsManager.snapshot())
        return ByteArrayOutputStream().use { byteStream ->
            ObjectOutputStream(byteStream).use { stream ->
                stream.writeObject(snapshot)
                stream.flush()
            }
            byteStream.toByteArray()
        }
    }

    private fun deserializeSettingsFromBytes(bytes: ByteArray): Map<String, Any?> {
        return ByteArrayInputStream(bytes).use { byteStream ->
            ObjectInputStream(byteStream).use { stream ->
                val raw = stream.readObject()
                if (raw is Map<*, *>) {
                    val result = HashMap<String, Any?>()
                    raw.forEach { (key, value) ->
                        if (key is String) {
                            result[key] = value
                        }
                    }
                    result
                } else {
                    emptyMap()
                }
            }
        }
    }
}