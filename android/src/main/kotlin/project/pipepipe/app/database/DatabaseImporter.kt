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
import project.pipepipe.app.serialize.SavedTabsPayload
import project.pipepipe.app.PipePipeApplication
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
        private const val PINNED_PLAYLIST_TAB_ID = 8
        private const val PINNED_FEED_GROUP_TAB_ID = 9

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

            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(BufferedInputStream(stream)).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            val entryName = entry.name
                            when {
                                importDatabase && !databaseImported && entryName in DATABASE_ZIP_ENTRY_ALIASES -> {
                                    databaseImported = writeDatabaseEntry(zip, dbFile)
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
                        fixImportedDatabaseVersion(dbFile)
                        deleteCompanionWalFiles(dbFile)
                        verifyImportedDatabase()
                    } else {
                        throw IllegalStateException(MR.strings.backup_database_not_found.desc().toString(context = context))
                    }
                }

                if (importSettings) {
                    if (settingsImported && tempSettingsFile.exists()) {
                        val snapshot = deserializeSettingsSnapshot(tempSettingsFile)
                        settingsManager.restoreFrom(snapshot)

                        val savedTabsJson = extractSavedTabsJson(snapshot)
                            ?: settingsManager.getString(SAVED_TABS_KEY).takeIf { it.isNotBlank() }

                        if (!savedTabsJson.isNullOrBlank()) {
                            pinItemsFromSavedTabs(savedTabsJson)
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

    fun importDatabase(uri: Uri) {
        importBackup(uri, importDatabase = true, importSettings = false)
    }

    private fun writeDatabaseEntry(zip: ZipInputStream, target: File): Boolean {
        DataBaseDriverManager.reset(context)

        if (target.exists()) {
            val backup = File(
                target.parentFile,
                "pipepipe_backup_${System.currentTimeMillis()}.db"
            )
            target.copyTo(backup, overwrite = true)
        } else {
            target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        }

        FileOutputStream(target).use { output ->
            zip.copyTo(output)
        }
        return true
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

    private fun fixImportedDatabaseVersion(databaseFile: File) {
        val appSchemaVersion = AppDatabase.Schema.version.toInt()
        val db = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )

        val importedVersion = db.version
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