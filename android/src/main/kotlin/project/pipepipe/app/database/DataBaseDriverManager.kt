package project.pipepipe.app.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import project.pipepipe.app.SharedContext
import project.pipepipe.database.*

object DataBaseDriverManager {
    lateinit var driver: AndroidSqliteDriver

    fun initialize(context: Context) {
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
        SharedContext.database = AppDatabase(
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

    fun reset(context: Context) {
        driver.close()
        initialize(context)
    }
}