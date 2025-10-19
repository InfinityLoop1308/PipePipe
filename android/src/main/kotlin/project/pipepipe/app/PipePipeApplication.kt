package project.pipepipe.app

import android.app.Application
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import project.pipepipe.app.database.DataBaseDriverManager
import project.pipepipe.extractor.Router
import project.pipepipe.app.global.CookieManager
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

class PipePipeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        suspend fun initializeSupportedServices() {
            try {
                withContext(Dispatchers.IO) {
                    val result = executeJobFlow(
                        SupportedJobType.GET_SUPPORTED_SERVICES,
                        null,
                        null
                    ).pagedData!!.itemList as List<SupportedServiceInfo>
                    val jsonString = Json.encodeToString(result)
                    SharedContext.settingsManager.putString("supported_services", jsonString)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
        SharedContext.downloader = Downloader(HttpClient(OkHttp))
        SharedContext.settingsManager = SettingsManager()
        SharedContext.cookieManager = CookieManager()
        SharedContext.sessionManager = Cache4kSessionManager()
        SharedContext.sharedVideoDetailViewModel = VideoDetailViewModel()
        SharedContext.serverRequestHandler = Router::execute

        // Initialize Media and Notifications
        MediaCacheProvider.init(this)
        NotificationHelper.initNotificationChannels(this)

        // Initialize Database
        DataBaseDriverManager.initialize(this)

        // Schedule streams notification periodic work
        StreamsNotificationManager.schedulePeriodicWork(this)

        // Async initialization
        applicationScope.launch {
            initializeSupportedServices()
        }
    }
}
