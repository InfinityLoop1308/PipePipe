package project.pipepipe.app.mediasource

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCacheProvider {
    @Volatile
    private var simpleCache: Cache? = null

    fun init(context: Context): Boolean {
        if (simpleCache != null) {
            return false
        }

        synchronized(this) {
            if (simpleCache != null) {
                return false
            }

            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, "media_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024)
            val databaseProvider = StandaloneDatabaseProvider(appContext)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            return true
        }
    }

    fun getOrNull(): Cache? = simpleCache
    fun get(): Cache = simpleCache
        ?: throw IllegalStateException("MediaCache not initialized")

    fun isInitialized(): Boolean = simpleCache != null

    fun release() {
        synchronized(this) {
            simpleCache?.release()
            simpleCache = null
        }
    }
}
