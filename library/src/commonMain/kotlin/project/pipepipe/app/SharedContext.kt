package project.pipepipe.app

import androidx.navigation.NavHostController
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import project.pipepipe.database.AppDatabase
import project.pipepipe.shared.downloader.Downloader
import project.pipepipe.app.viewmodel.BottomSheetMenuViewModel
import project.pipepipe.app.viewmodel.VideoDetailViewModel
import project.pipepipe.shared.infoitem.Info
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.job.JobRequest
import project.pipepipe.shared.job.JobResponse
import project.pipepipe.app.helper.SettingsManager
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.platform.PlatformDatabaseActions
import project.pipepipe.app.platform.PlatformActions
import project.pipepipe.app.platform.PlatformMediaController
import project.pipepipe.app.platform.PlatformRouteHandler
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.shared.state.SessionManager

enum class PlaybackMode {
    VIDEO_AUDIO,
    AUDIO_ONLY
}

object SharedContext {
    var isTv: Boolean = false
    var androidVersion: Int = -1
    lateinit var downloader: Downloader
    val objectMapper = ObjectMapper()
    lateinit var sharedVideoDetailViewModel: VideoDetailViewModel
    lateinit var database: AppDatabase
    lateinit var serverRequestHandler: suspend (JobRequest) -> JobResponse<out Info, out Info>
    lateinit var settingsManager: SettingsManager
    val bottomSheetMenuViewModel = BottomSheetMenuViewModel()
    lateinit var sessionManager: SessionManager
    lateinit var systemBarColorsManager: SystemBarColorsManager
    lateinit var platformActions: PlatformActions
    lateinit var platformDatabaseActions: PlatformDatabaseActions
    var platformMediaController: PlatformMediaController? = null
    lateinit var platformRouteHandler: PlatformRouteHandler
//    Safe in single-activity architecture where Activity lifecycle matches application lifecycle
    lateinit var navController: NavHostController

    var playingVideoUrlBeforeMinimizing: String? = null

    private val _playbackMode = MutableStateFlow(PlaybackMode.AUDIO_ONLY)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    fun updatePlaybackMode(mode: PlaybackMode) {
        _playbackMode.value = mode
    }

    private val _playQueueVisibility = MutableStateFlow(false)
    val playQueueVisibility: StateFlow<Boolean> = _playQueueVisibility.asStateFlow()
    fun toggleShowPlayQueueVisibility() {
        _playQueueVisibility.value = !_playQueueVisibility.value
    }

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()
    fun enterPipmode() {
        _isInPipMode.value = true
    }
    fun exitPipMode() {
        _isInPipMode.value = false
    }

    // Image Viewer State
    data class ImageViewerState(
        val isVisible: Boolean = false,
        val urls: List<String> = emptyList(),
        val initialPage: Int = 0
    )

    private val _imageViewerState = MutableStateFlow(ImageViewerState())
    val imageViewerState: StateFlow<ImageViewerState> = _imageViewerState.asStateFlow()

    fun showImageViewer(urls: List<String>, initialPage: Int = 0) {
        _imageViewerState.value = ImageViewerState(
            isVisible = true,
            urls = urls,
            initialPage = initialPage
        )
    }

    fun hideImageViewer() {
        _imageViewerState.value = ImageViewerState()
    }

    // Download Format Dialog State
    data class DownloadFormatDialogState(
        val isVisible: Boolean = false,
        val streamInfo: StreamInfo? = null
    )

    private val _downloadFormatDialogState = MutableStateFlow(DownloadFormatDialogState())
    val downloadFormatDialogState: StateFlow<DownloadFormatDialogState> = _downloadFormatDialogState.asStateFlow()

    fun showDownloadFormatDialog(streamInfo: StreamInfo) {
        _downloadFormatDialogState.value = DownloadFormatDialogState(
            isVisible = true,
            streamInfo = streamInfo
        )
    }

    fun hideDownloadFormatDialog() {
        _downloadFormatDialogState.value = DownloadFormatDialogState()
    }

    // Playlist Change Notification
    private val _playlistChanged = MutableSharedFlow<Long>()
    val playlistChanged: SharedFlow<Long> = _playlistChanged.asSharedFlow()

    suspend fun notifyPlaylistChanged(playlistId: Long) {
        _playlistChanged.emit(playlistId)
    }

    // History Change Notification
    private val _historyChanged = MutableSharedFlow<Unit>()
    val historyChanged: SharedFlow<Unit> = _historyChanged.asSharedFlow()

    suspend fun notifyHistoryChanged() {
        _historyChanged.emit(Unit)
    }

    // Check and show dialogs (e.g., after backup import)
    private val _checkAndShowDialogs = MutableSharedFlow<Unit>()
    val checkAndShowDialogs: SharedFlow<Unit> = _checkAndShowDialogs.asSharedFlow()

    suspend fun triggerDialogCheck() {
        _checkAndShowDialogs.emit(Unit)
    }

    // Stream Info Loaded Notification (for SponsorBlock and Autoplay)
    data class StreamInfoLoadedEvent(
        val mediaId: String,
        val sponsorblockUrl: String?,
        val relatedItemUrl: String?
    )

    private val _streamInfoLoaded = MutableSharedFlow<StreamInfoLoadedEvent>()
    val streamInfoLoaded: SharedFlow<StreamInfoLoadedEvent> = _streamInfoLoaded.asSharedFlow()

    suspend fun notifyStreamInfoLoaded(mediaId: String, sponsorblockUrl: String?, relatedItemUrl: String?) {
        _streamInfoLoaded.emit(StreamInfoLoadedEvent(mediaId, sponsorblockUrl, relatedItemUrl))
    }

    // Decoder Error Event (for showing dialog in VideoDetailScreen)
    private val _decoderErrorEvent = MutableSharedFlow<Unit>()
    val decoderErrorEvent: SharedFlow<Unit> = _decoderErrorEvent.asSharedFlow()

    suspend fun notifyDecoderError() {
        _decoderErrorEvent.emit(Unit)
    }

    // =========== methods ==============

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