package project.pipepipe.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import project.pipepipe.database.AppDatabase
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.helper.SettingsManager
import project.pipepipe.app.helper.MainScreenTabConfig
import project.pipepipe.app.helper.MainScreenTabConfigDefaults
import project.pipepipe.app.serialize.SavedTabsPayload
import project.pipepipe.app.serialize.SavedTabPayload
import project.pipepipe.app.PipePipeApplication
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
        private const val PINNED_PLAYLIST_TAB_ID = 8
        private const val PINNED_FEED_GROUP_TAB_ID = 9

        private val savedTabsJsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private val tabConfigJsonParser = Json {
            ignoreUnknownKeys = false
            isLenient = false
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
        importSettings: Boolean
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
                        settingsManager.restoreFrom(snapshot)

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
                            pinItemsFromSavedTabs(savedTabsJson)
                            convertSavedTabsToNewConfig(savedTabsJson)
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

    private suspend fun pinItemsFromSavedTabs(rawJson: String) {
        val payload = savedTabsJsonParser.decodeFromString<SavedTabsPayload>(rawJson)

        // Pin playlists (tab_id = 8)
        val playlistTabs = payload.tabs.filter { it.tabId == PINNED_PLAYLIST_TAB_ID }

        playlistTabs.forEach { tab ->
            when {
                // Local playlist: use playlistId
                tab.playlistId!! != -1L -> {
                    DatabaseOperations.setPlaylistPinned(tab.playlistId!!, true)
                }
                // Remote playlist: use playlistUrl
                tab.playlistUrl != null -> {
                    val remotePlaylist = DatabaseOperations.getRemotePlaylistByUrl(tab.playlistUrl!!)
                    remotePlaylist?.uid?.let { playlistId ->
                        DatabaseOperations.setRemotePlaylistPinned(playlistId, true)
                    }
                }
            }
        }

        // Pin feed groups (tab_id = 9)
        val feedGroupIdsToPin = payload.tabs
            .filter { it.tabId == PINNED_FEED_GROUP_TAB_ID }
            .mapNotNull { it.groupId }
            .distinct()

        feedGroupIdsToPin.forEach { groupId ->
            DatabaseOperations.setFeedGroupPinned(groupId, true)
        }
    }

    private suspend fun convertSavedTabsToNewConfig(rawJson: String) {
        val payload = savedTabsJsonParser.decodeFromString<SavedTabsPayload>(rawJson)

        val routes = mutableSetOf<String>()
        val newTabs = mutableListOf<MainScreenTabConfig>()

        // Convert each old tab to new route format
        payload.tabs.forEach { tab ->
            val route = convertTabToRoute(tab)
            // Skip duplicates and null routes
            if (route != null && !routes.contains(route)) {
                routes.add(route)
                val isDefault = route in listOf(
                    MainScreenTabConfigDefaults.DASHBOARD_ROUTE,
                    MainScreenTabConfigDefaults.SUBSCRIPTIONS_ROUTE,
                    MainScreenTabConfigDefaults.BOOKMARKED_PLAYLISTS_ROUTE
                )
                newTabs.add(MainScreenTabConfig(route, isDefault))
            }
        }

        // Ensure all default tabs are present
        val defaultRoutes = listOf(
            MainScreenTabConfigDefaults.DASHBOARD_ROUTE,
            MainScreenTabConfigDefaults.SUBSCRIPTIONS_ROUTE,
            MainScreenTabConfigDefaults.BOOKMARKED_PLAYLISTS_ROUTE
        )

        defaultRoutes.forEach { defaultRoute ->
            if (!routes.contains(defaultRoute)) {
                newTabs.add(MainScreenTabConfig(defaultRoute, isDefault = true))
                routes.add(defaultRoute)
            }
        }

        // Save to settings
        val jsonString = tabConfigJsonParser.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(MainScreenTabConfig.serializer()),
            newTabs
        )
        settingsManager.putString(CUSTOM_TABS_CONFIG_KEY, jsonString)
    }

    private suspend fun convertTabToRoute(tab: SavedTabPayload): String? {
        return when (tab.tabId) {
            0 -> "blank"
            1 -> MainScreenTabConfigDefaults.SUBSCRIPTIONS_ROUTE
            2 -> "feed/-1"
            3 -> MainScreenTabConfigDefaults.BOOKMARKED_PLAYLISTS_ROUTE
            4 -> "history"
            5 -> null // Skip KIOSK tabs
            6 -> convertChannelTab(tab)
            7 -> MainScreenTabConfigDefaults.DASHBOARD_ROUTE
            8 -> convertPlaylistTab(tab)
            9 -> convertChannelGroupTab(tab)
            else -> null
        }
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
            val database = AppDatabase(driver)
            database.appDatabaseQueries.selectStreamsCount().executeAsOne()
            driver.close()
        } catch (e: Exception) {
            throw Exception("Imported database verification failed: ${e.message}", e)
        }
    }
}