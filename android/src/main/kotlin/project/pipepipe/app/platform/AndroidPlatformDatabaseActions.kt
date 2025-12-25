package project.pipepipe.app.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import project.pipepipe.app.SharedContext
import project.pipepipe.database.AppDatabase
import project.pipepipe.database.Error_log
import project.pipepipe.database.Remote_playlists
import project.pipepipe.database.Streams
import project.pipepipe.database.Subscriptions

class AndroidPlatformDatabaseActions(private val context: Context): PlatformDatabaseActions {
    private lateinit var driver: AndroidSqliteDriver
    private fun createAppDatabase(driver: app.cash.sqldelight.db.SqlDriver): AppDatabase {
        return AppDatabase(
            driver = driver,
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
    }

    override fun initializeDatabase() {
        driver = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "pipepipe.db",
            callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
        SharedContext.database = createAppDatabase(driver)
    }

    override fun resetDatabase() {
        driver.close()
        initializeDatabase()
    }

    override fun verifyDatabase() {
        try {
            val verifyDriver = AndroidSqliteDriver(AppDatabase.Schema, context, "pipepipe.db")
            val database = createAppDatabase(verifyDriver)
            database.appDatabaseQueries.selectStreamsCount().executeAsOne()
            verifyDriver.close()
        } catch (e: Exception) {
            throw Exception("Imported database verification failed: ${e.message}", e)
        }
    }

    override fun getDatabaseBytes(): ByteArray? {
        val dbFile = context.getDatabasePath("pipepipe.db")
        return if (dbFile.exists()) dbFile.readBytes() else null
    }

    override fun writeDatabaseBytes(bytes: ByteArray) {
        val dbFile = context.getDatabasePath("pipepipe.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(bytes)
    }

    override fun runDatabaseMigration(importedVersion: Int) {
        val dbFile = context.getDatabasePath("pipepipe.db")
        val appSchemaVersion = AppDatabase.Schema.version.toInt()

        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        )

        when (importedVersion) {
            6 -> {
                db.execSQL(
                    "UPDATE streams" +
                            " SET url = REPLACE(url, 'https://bilibili.com', 'https://www.bilibili.com/video')" +
                            " WHERE url LIKE 'https://bilibili.com/%'"
                )
            }
            7, 8, 9 -> {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlists_new`" +
                            "(uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT, " +
                            "display_index INTEGER NOT NULL DEFAULT 0, " +
                            "thumbnail_url TEXT)"
                )

                db.execSQL(
                    "INSERT INTO playlists_new" +
                            " SELECT p.uid, p.name, p.display_index, s.thumbnail_url " +
                            " FROM playlists p " +
                            " LEFT JOIN streams s ON p.thumbnail_stream_id = s.uid"
                )

                db.execSQL("DROP TABLE playlists")
                db.execSQL("ALTER TABLE playlists_new RENAME TO playlists")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                            "`index_playlists_name` ON `playlists` (`name`)"
                )

                db.execSQL(
                    "CREATE TABLE `remote_playlists_tmp` " +
                            "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`service_id` INTEGER NOT NULL, `name` TEXT, `url` TEXT, " +
                            "`thumbnail_url` TEXT, `uploader` TEXT, " +
                            "`display_index` INTEGER NOT NULL DEFAULT 0," +
                            "`stream_count` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `remote_playlists_tmp` (`uid`, `service_id`, " +
                            "`name`, `url`, `thumbnail_url`, `uploader`, `stream_count`)" +
                            "SELECT `uid`, `service_id`, `name`, `url`, `thumbnail_url`, `uploader`, " +
                            "`stream_count` FROM `remote_playlists`"
                )

                db.execSQL("DROP TABLE `remote_playlists`")
                db.execSQL("ALTER TABLE `remote_playlists_tmp` RENAME TO `remote_playlists`")
                db.execSQL(
                    "CREATE INDEX `index_remote_playlists_name` " +
                            "ON `remote_playlists` (`name`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_remote_playlists_service_id_url` " +
                            "ON `remote_playlists` (`service_id`, `url`)"
                )
            }
        }

        if (importedVersion > appSchemaVersion) {
            db.version = appSchemaVersion
        }
        db.close()
    }

}