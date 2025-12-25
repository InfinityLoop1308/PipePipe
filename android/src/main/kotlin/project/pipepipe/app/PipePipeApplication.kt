package project.pipepipe.app

import android.app.Application
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import project.pipepipe.extractor.Router
import project.pipepipe.app.mediasource.MediaCacheProvider
import project.pipepipe.app.service.NotificationHelper
import project.pipepipe.app.service.StreamsNotificationManager
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.shared.downloader.Downloader
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.helper.SettingsManager
import project.pipepipe.shared.state.Cache4kSessionManager
import project.pipepipe.app.viewmodel.VideoDetailViewModel
import project.pipepipe.app.download.DownloadManager
import project.pipepipe.app.download.DownloadManagerHolder
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import android.util.Log
import android.content.pm.PackageManager
import android.os.Build
import project.pipepipe.app.platform.AndroidPlatformDatabaseActions

class PipePipeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val handler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runBlocking {
                runCatching {
                    DatabaseOperations.insertErrorLog(throwable.stackTraceToString(), "UNKNOWN", "UNKNOWN_999")
                }
            }
            handler?.uncaughtException(thread, throwable)
        }

        // Initialize SharedContext
        SharedContext.androidVersion = Build.VERSION.SDK_INT
        SharedContext.isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        SharedContext.downloader = Downloader(HttpClient(OkHttp))
        SharedContext.settingsManager = SettingsManager()
        SharedContext.sessionManager = Cache4kSessionManager()
        SharedContext.sharedVideoDetailViewModel = VideoDetailViewModel()
        SharedContext.serverRequestHandler = Router::execute

        // Initialize Media and Notifications
        MediaCacheProvider.init(this)
        NotificationHelper.initNotificationChannels(this)
        SharedContext.platformDatabaseActions = AndroidPlatformDatabaseActions(this)
        SharedContext.platformDatabaseActions.initializeDatabase()

        // Initialize youtubedl-android and FFmpeg
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d("PipePipeApp", "YoutubeDL and FFmpeg initialized successfully")
            GlobalScope.launch {
                runCatching{ YoutubeDL.updateYoutubeDL(this@PipePipeApplication) }
            }
        } catch (e: Exception) {
            Log.e("PipePipeApp", "Failed to initialize YoutubeDL/FFmpeg", e)
        }

        // Initialize DownloadManager
        val downloadManager = DownloadManager(this)
        DownloadManagerHolder.initialize(downloadManager)
        Log.d("PipePipeApp", "DownloadManager initialized")

        // Schedule streams notification periodic work
        StreamsNotificationManager.schedulePeriodicWork(this)

        // Async initialization
        GlobalScope.launch {
            SharedContext.initializeSupportedServices()
        }
    }
}
