package project.pipepipe.app.download

/**
 * Singleton holder for DownloadManager
 * Android-specific, initialized in PipePipeApplication
 */
object DownloadManagerHolder {
    lateinit var instance: DownloadManager
        private set

    fun initialize(manager: DownloadManager) {
        instance = manager
    }

    fun isInitialized(): Boolean = ::instance.isInitialized
}
