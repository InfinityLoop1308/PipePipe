package project.pipepipe.app.platform

interface PlatformDatabaseActions {
    fun initializeDatabase()
    fun resetDatabase()
    fun verifyDatabase()
    fun getDatabaseBytes(): ByteArray?
    fun writeDatabaseBytes(bytes: ByteArray)
    fun runDatabaseMigration(importedVersion: Int)
}
