package project.pipepipe.app.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import project.pipepipe.database.AppDatabase
import project.pipepipe.shared.SharedContext

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
        SharedContext.database = AppDatabase(driver)
    }

    fun reset(context: Context) {
        driver.close()
        initialize(context)
    }
}