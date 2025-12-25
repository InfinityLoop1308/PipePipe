package project.pipepipe.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import project.pipepipe.database.AppDatabase
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.SettingsManager
import project.pipepipe.app.helper.MainScreenTabDefaults
import project.pipepipe.app.serialize.SavedTabsPayload
import project.pipepipe.app.serialize.SavedTabPayload
import project.pipepipe.app.PipePipeApplication
import project.pipepipe.database.Error_log
import project.pipepipe.database.Remote_playlists
import project.pipepipe.database.Streams
import project.pipepipe.database.Subscriptions
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import java.net.URLEncoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.HashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream



class DatabaseImporter(
    private val context: Context,
    private val settingsManager: SettingsManager = SharedContext.settingsManager
) {

    companion object {
        private const val DATABASE_NAME = "pipepipe.db"

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
    }

    fun exportBackup(
        uri: Uri,
        includeDatabase: Boolean = true,
        includeSettings: Boolean = true
    ) {
        if (!includeDatabase && !includeSettings) {
            ToastManager.show(MR.strings.backup_nothing_selected_export.desc().toString(context = context))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val tempSettingsFile = File(context.cacheDir, PRIMARY_SETTINGS_ZIP_ENTRY)

            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
                        if (includeDatabase && databaseFile.exists()) {
                            addFileToZip(zip, databaseFile, PRIMARY_DATABASE_ZIP_ENTRY)
                        }

                        if (includeSettings) {
                            serializeSettingsSnapshot(tempSettingsFile)
                            if (tempSettingsFile.exists()) {
                                addFileToZip(zip, tempSettingsFile, PRIMARY_SETTINGS_ZIP_ENTRY)
                            }
                        }
                    }
                } ?: throw IllegalStateException("Unable to open export destination")

                withContext(Dispatchers.Main) {
                    ToastManager.show(MR.strings.backup_export_success.desc().toString(context = context))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ToastManager.show(MR.strings.backup_export_failed.desc().toString(context = context).format(e.message ?: ""))
                }
            } finally {
                if (tempSettingsFile.exists()) {
                    tempSettingsFile.delete()
                }
            }
        }
    }

    fun checkBackupVersion(uri: Uri): Int? {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName in DATABASE_ZIP_ENTRY_ALIASES) {
                            val tempFile = File(context.cacheDir, "temp_check_${System.currentTimeMillis()}.db")
                            try {
                                FileOutputStream(tempFile).use { output ->
                                    zip.copyTo(output)
                                }
                                val db = SQLiteDatabase.openDatabase(
                                    tempFile.path,
                                    null,
                                    SQLiteDatabase.OPEN_READONLY
                                )
                                val version = db.version
                                db.close()
                                return version
                            } finally {
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun importBackup(
        uri: Uri,
        importDatabase: Boolean,
        importSettings: Boolean,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!importDatabase && !importSettings) {
            ToastManager.show(MR.strings.backup_nothing_selected_import.desc().toString(context = context))
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val databaseDir = File(context.filesDir.parent, "databases")
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
            }
            val dbFile = File(databaseDir, DATABASE_NAME)
            val tempSettingsFile = File(context.cacheDir, "$PRIMARY_SETTINGS_ZIP_ENTRY.tmp")

            var databaseImported = false
            var settingsImported = false
            var dbBackupFile: File? = null

            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            when {
                                importDatabase && !databaseImported && entryName in DATABASE_ZIP_ENTRY_ALIASES -> {
                                    val result = writeDatabaseEntry(zip, dbFile)
                                    databaseImported = result.first
                                    dbBackupFile = result.second
                                }
                                importSettings && !settingsImported && entryName in SETTINGS_ZIP_ENTRY_ALIASES -> {
                                    settingsImported = writeSettingsEntry(zip, tempSettingsFile)
                                }
                            }

                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("Unable to open import source")

                if (importDatabase) {
                    if (databaseImported) {
                        try {
                            fixImportedDatabaseVersion(dbFile)
                            deleteCompanionWalFiles(dbFile)
                            verifyImportedDatabase()
                            // Verification successful, delete backup
                            dbBackupFile?.delete()
                        } catch (e: Exception) {
                            // Verification failed, restore backup
                            restoreDatabaseBackup(dbFile, dbBackupFile)
                            throw e
                        }
                    } else {
                        throw IllegalStateException(MR.strings.backup_database_not_found.desc().toString(context = context))
                    }
                }

                if (importSettings) {
                    if (settingsImported && tempSettingsFile.exists()) {
                        val snapshot = deserializeSettingsSnapshot(tempSettingsFile)

                        // Save current supported_services before restore, as it's needed for tab conversion
                        val currentSupportedServices = settingsManager.getString("supported_services")

                        settingsManager.restoreFrom(snapshot)

                        // Restore supported_services immediately to ensure tab conversion works correctly
                        if (currentSupportedServices.isNotBlank()) {
                            settingsManager.putString("supported_services", currentSupportedServices)
                        }

                        // Convert old list_view_mode to new grid layout settings
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
                            // For "auto", "list" or any other values, do nothing
                        }

                        val savedTabsJson = extractSavedTabsJson(snapshot)
                            ?: settingsManager.getString(SAVED_TABS_KEY).takeIf { it.isNotBlank() }

                        if (!savedTabsJson.isNullOrBlank()) {
                            convertSavedTabsToNewConfig(savedTabsJson)
                        }

                        // Convert old enable_watch_history (boolean) to new watch_history_mode (string)
                        val enableWatchHistory = snapshot["enable_watch_history"] as? Boolean
                        if (enableWatchHistory != null) {
                            val watchHistoryMode = if (enableWatchHistory) "on_play" else "disabled"
                            settingsManager.putString("watch_history_mode", watchHistoryMode)
                        }

                        // Re-initialize supported services to prevent being overwritten by old backup
                        PipePipeApplication.initializeSupportedServices()
                    } else {
                        throw IllegalStateException(MR.strings.backup_settings_not_found.desc().toString(context = context))
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = when {
                        importDatabase && importSettings -> MR.strings.backup_import_both_success.desc().toString(context = context)
                        importDatabase -> MR.strings.backup_import_database_success.desc().toString(context = context)
                        importSettings -> MR.strings.backup_import_settings_success.desc().toString(context = context)
                        else -> MR.strings.backup_import_completed.desc().toString(context = context)
                    }
                    ToastManager.show(message)
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ToastManager.show(MR.strings.backup_import_failed.desc().toString(context = context).format(e.message ?: ""))
                }
            } finally {
                if (tempSettingsFile.exists()) {
                    tempSettingsFile.delete()
                }
            }
        }
    }

    private fun extractSavedTabsJson(snapshot: Map<String, Any?>): String? {
        val value = snapshot[SAVED_TABS_KEY]
        return value as? String
    }

    private suspend fun convertSavedTabsToNewConfig(rawJson: String) {
        val payload = savedTabsJsonParser.decodeFromString<SavedTabsPayload>(rawJson)

        val routes = mutableSetOf<String>()
        val newTabs = mutableListOf<String>()

        // Convert each old tab to new route format
        payload.tabs.forEach { tab ->
            val route = convertTabToRoute(tab)
            // Skip duplicates and null routes
            if (route != null && !routes.contains(route)) {
                routes.add(route)
                newTabs.add(route)
            }
        }

        // Save to settings
        val jsonString = Json.encodeToString(newTabs)
        settingsManager.putString(CUSTOM_TABS_CONFIG_KEY, jsonString)
    }

    private suspend fun convertTabToRoute(tab: SavedTabPayload): String? {
        return when (tab.tabId) {
            0 -> "blank"
            1 -> MainScreenTabDefaults.SUBSCRIPTIONS_ROUTE
            2 -> "feed/-1"
            3 -> MainScreenTabDefaults.BOOKMARKED_PLAYLISTS_ROUTE
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
            val jsonString = settingsManager.getString("supported_services")
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

        // Query database for channel info
        val subscription = DatabaseOperations.getSubscriptionByUrl(url) ?: return null

        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val encodedName = URLEncoder.encode(subscription.name ?: "Unknown", "UTF-8")

        return "channel?url=$encodedUrl&serviceId=${subscription.service_id}&name=$encodedName"
    }

    private suspend fun convertPlaylistTab(tab: SavedTabPayload): String? {
        // Local playlist
        if (tab.playlistId != null && tab.playlistId != -1L) {
            val playlist = DatabaseOperations.getPlaylistInfoById(tab.playlistId.toString())
                ?: return null

            val encodedUrl = URLEncoder.encode(playlist.url, "UTF-8")
            val encodedName = URLEncoder.encode(playlist.name, "UTF-8")
            // Local playlists don't have serviceId, use a placeholder
            return "playlist?url=$encodedUrl&name=$encodedName"
        }

        // Remote playlist - query database using URL
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

    fun importDatabase(uri: Uri) {
        importBackup(uri, importDatabase = true, importSettings = false)
    }

    private fun writeDatabaseEntry(zip: ZipInputStream, target: File): Pair<Boolean, File?> {
        DataBaseDriverManager.reset(context)

        var backupFile: File? = null
        if (target.exists()) {
            backupFile = File(
                target.parentFile,
                "pipepipe_backup_${System.currentTimeMillis()}.db"
            )
            target.copyTo(backupFile, overwrite = true)
        } else {
            target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        }

        FileOutputStream(target).use { output ->
            zip.copyTo(output)
        }
        return Pair(true, backupFile)
    }

    private fun writeSettingsEntry(zip: ZipInputStream, tempFile: File): Boolean {
        tempFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
        FileOutputStream(tempFile).use { output ->
            zip.copyTo(output)
        }
        return true
    }

    private fun serializeSettingsSnapshot(target: File) {
        val snapshot = HashMap(settingsManager.snapshot())
        target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        ObjectOutputStream(FileOutputStream(target)).use { stream ->
            stream.writeObject(snapshot)
            stream.flush()
        }
    }

    private fun deserializeSettingsSnapshot(file: File): Map<String, Any?> {
        if (!file.exists()) return emptyMap()
        return ObjectInputStream(FileInputStream(file)).use { stream ->
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

    private fun addFileToZip(zip: ZipOutputStream, source: File, entryName: String) {
        if (!source.exists()) return
        FileInputStream(source).use { input ->
            BufferedInputStream(input).use { buffered ->
                zip.putNextEntry(ZipEntry(entryName))
                buffered.copyTo(zip)
                zip.closeEntry()
            }
        }
    }

    private fun deleteCompanionWalFiles(databaseFile: File) {
        listOf("-wal", "-journal", "-shm").forEach { suffix ->
            val file = File(databaseFile.path + suffix)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun restoreDatabaseBackup(currentFile: File, backupFile: File?) {
        if (backupFile != null && backupFile.exists()) {
            // Delete failed import
            if (currentFile.exists()) {
                currentFile.delete()
            }
            deleteCompanionWalFiles(currentFile)

            // Restore backup
            backupFile.copyTo(currentFile, overwrite = true)
            backupFile.delete()

            // Reinitialize database driver with restored database
            DataBaseDriverManager.reset(context)
        }
    }

    private fun fixImportedDatabaseVersion(databaseFile: File) {
        val appSchemaVersion = AppDatabase.Schema.version.toInt()
        val db = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )

        val importedVersion = db.version

        // Handle special migrations for old versions before changing version number
        when (importedVersion) {
            6 -> {
                // Migration from version 6: fix bilibili URLs
                db.execSQL("UPDATE streams" +
                        " SET url = REPLACE(url, 'https://bilibili.com', 'https://www.bilibili.com/video')" +
                        " WHERE url LIKE 'https://bilibili.com/%'")
            }
            7, 8, 9 -> {
                // Migration from versions 7, 8, 9: convert thumbnail_stream_id to thumbnail_url
                db.execSQL("CREATE TABLE IF NOT EXISTS `playlists_new`" +
                        "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT, " +
                        "display_index INTEGER NOT NULL DEFAULT 0, " +
                        "thumbnail_url TEXT)")

                db.execSQL("INSERT INTO playlists_new" +
                        " SELECT p.uid, p.name, p.display_index, s.thumbnail_url " +
                        " FROM playlists p " +
                        " LEFT JOIN streams s ON p.thumbnail_stream_id = s.uid")

                db.execSQL("DROP TABLE playlists")
                db.execSQL("ALTER TABLE playlists_new RENAME TO playlists")
                db.execSQL("CREATE INDEX IF NOT EXISTS " +
                        "`index_playlists_name` ON `playlists` (`name`)")

                // Handle remote_playlists table
                db.execSQL("CREATE TABLE `remote_playlists_tmp` " +
                        "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, " +
                        "`thumbnail_url` TEXT, `uploader` TEXT, " +
                        "`display_index` INTEGER NOT NULL DEFAULT 0," +
                        "`stream_count` INTEGER)")
                db.execSQL("INSERT INTO `remote_playlists_tmp` (`uid`, `service_id`, " +
                        "`name`, `url`, `thumbnail_url`, `uploader`, `stream_count`)" +
                        "SELECT `uid`, `service_id`, `name`, `url`, `thumbnail_url`, `uploader`, " +
                        "`stream_count` FROM `remote_playlists`")

                db.execSQL("DROP TABLE `remote_playlists`")
                db.execSQL("ALTER TABLE `remote_playlists_tmp` RENAME TO `remote_playlists`")
                db.execSQL("CREATE INDEX `index_remote_playlists_name` " +
                        "ON `remote_playlists` (`name`)")
                db.execSQL("CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` " +
                        "ON `remote_playlists` (`service_id`, `url`)")
            }
        }

        if (importedVersion > appSchemaVersion) {
            db.version = appSchemaVersion
        }
        db.close()
    }

    private fun verifyImportedDatabase() {
        try {
            val driver = AndroidSqliteDriver(AppDatabase.Schema, context, DATABASE_NAME)
            val database = AppDatabase(
                driver = DataBaseDriverManager.driver,
                error_logAdapter = Error_log.Adapter(
                    service_idAdapter = IntColumnAdapter
                ),
                remote_playlistsAdapter = Remote_playlists.Adapter(
                    service_idAdapter = IntColumnAdapter
                ),
                streamsAdapter = Streams.Adapter(
                    service_idAdapter = IntColumnAdapter
                ),
                subscriptionsAdapter = Subscriptions.Adapter(
                    service_idAdapter = IntColumnAdapter
                ),
            )
            database.appDatabaseQueries.selectStreamsCount().executeAsOne()
            driver.close()
        } catch (e: Exception) {
            throw Exception("Imported database verification failed: ${e.message}", e)
        }
    }
}