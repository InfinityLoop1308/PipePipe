package project.pipepipe.app

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import project.pipepipe.app.database.DataBaseDriverManager
import project.pipepipe.extractor.Router
import project.pipepipe.app.global.CookieManager
import project.pipepipe.app.mediasource.MediaCacheProvider
import project.pipepipe.app.service.NotificationHelper
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.downloader.Downloader
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.shared.job.executeJobFlow
import project.pipepipe.shared.helper.SettingsManager
import project.pipepipe.shared.state.Cache4kSessionManager
import project.pipepipe.shared.viewmodel.VideoDetailViewModel
import java.util.*

class PipePipeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        SharedContext.cookieManager = CookieManager.getInstance(this)
        SharedContext.sessionManager = Cache4kSessionManager()
        SharedContext.sharedVideoDetailViewModel = VideoDetailViewModel(Router::execute)
        SharedContext.appLocale = Locale.getDefault()
        SharedContext.serverRequestHandler = Router::execute
        SharedContext.settingsManager = SettingsManager()

        // Initialize Media and Notifications
        MediaCacheProvider.init(this)
        NotificationHelper.initNotificationChannels(this)

        // Initialize Database
        DataBaseDriverManager.initialize(this)


        // Async initialization
        applicationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val result = executeJobFlow(
                        SupportedJobType.GET_SUPPORTED_SERVICES,
                        null,
                        null
                    ).pagedData!!.itemList as List<SupportedServiceInfo>
                    val jsonString = Json.encodeToString(result)
                    applicationContext.dataStore.edit { preferences ->
                        preferences[stringPreferencesKey("supported_services")] = jsonString
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
