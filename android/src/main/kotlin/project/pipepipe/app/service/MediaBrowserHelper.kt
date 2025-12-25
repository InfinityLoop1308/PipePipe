package project.pipepipe.app.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import project.pipepipe.app.MR
import project.pipepipe.app.R
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.utils.generateQueryUrl
import project.pipepipe.app.viewmodel.PlaylistDetailViewModel
import project.pipepipe.app.viewmodel.SearchViewModel
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.SupportedServiceInfo

/**
 * Helper class for Android Auto media browser functionality.
 * Provides browsable media items from history and playlists.
 */
object MediaBrowserHelper {

    // Media ID constants
    const val MEDIA_ROOT_ID = "pipepipe_root"
    const val MEDIA_HISTORY_ID = "history"
    const val MEDIA_PLAYLISTS_ID = "playlists"
    const val MEDIA_SEARCH_ID = "search"
    const val MEDIA_LOCAL_PLAYLIST_PREFIX = "local_playlist/"
    const val MEDIA_REMOTE_PLAYLIST_PREFIX = "remote_playlist/"
    const val MEDIA_SEARCH_RESULT_PREFIX = "search_result/"
    const val MEDIA_STREAM_PREFIX = "stream/"

    // Settings keys
    private const val SUPPORTED_SERVICES_KEY = "supported_services"
    private const val SELECTED_SERVICE_KEY = "selected_search_service"

    // Android Auto media ID scheme - format: auto://{serviceId}/{realUrl}?name=...&artist=...
    // All metadata is encoded in the URL since extras are lost during IPC
    const val AUTO_MEDIA_ID_SCHEME = "auto"

    // Singleton ViewModels for remote playlist and search
    private val playlistVm by lazy { PlaylistDetailViewModel() }
    private val searchVm by lazy { SearchViewModel() }

    /**
     * Creates the root MediaItem for the media browser.
     */
    fun createRootMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MEDIA_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("PipePipe")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /**
     * Creates the top-level browsable items (History, Playlists).
     */
    fun createTopLevelItems(context: Context): List<MediaItem> {
        return listOf(
            createBrowsableItem(
                mediaId = MEDIA_HISTORY_ID,
                title = MR.strings.title_activity_history.desc().toString(context),
                iconResId = R.drawable.ic_history,
                context = context
            ),
            createBrowsableItem(
                mediaId = MEDIA_PLAYLISTS_ID,
                title = MR.strings.tab_bookmarks.desc().toString(context),
                iconResId = R.drawable.ic_bookmark,
                context = context
            )
        )
    }

    /**
     * Loads history items as MediaItems.
     */
    suspend fun loadHistoryItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val historyItems = DatabaseOperations.loadStreamHistoryItems()
        historyItems.map { streamInfo ->
            createPlayableMediaItem(streamInfo)
        }
    }

    /**
     * Loads all playlists as browsable MediaItems.
     */
    suspend fun loadPlaylistItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val playlists = DatabaseOperations.getAllPlaylistsCombined()
        playlists.map { playlistInfo ->
            createBrowsablePlaylistItem(playlistInfo)
        }
    }

    /**
     * Loads streams from a local playlist.
     */
    suspend fun loadLocalPlaylistStreams(playlistId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val streams = DatabaseOperations.loadPlaylistsItemsFromDatabase(playlistId)
        streams.map { streamInfo ->
            createPlayableMediaItem(streamInfo)
        }
    }

    /**
     * Loads streams from a remote playlist using ViewModel.
     */
    suspend fun loadRemotePlaylistStreams(playlistId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        // Get remote playlist info from database
        val playlists = DatabaseOperations.getAllPlaylistsCombined()
        val playlistInfo = playlists.find { it.uid == playlistId && it.serviceId != null }
            ?: return@withContext emptyList()

        // Load remote playlist using ViewModel
        playlistVm.loadPlaylist(playlistInfo.url, playlistInfo.serviceId)
        val streams = playlistVm.uiState.value.list.itemList
        streams.map { streamInfo ->
            createPlayableMediaItem(streamInfo)
        }
    }

    /**
     * Performs search and returns results as MediaItems.
     */
    suspend fun performSearch(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        // Initialize search service if needed
        initSearchServiceIfNeeded()

        val service = searchVm.uiState.value.selectedService ?: return@withContext emptyList()
        val searchType = searchVm.uiState.value.selectedSearchType ?: return@withContext emptyList()

        val searchUrl = generateQueryUrl(query, searchType)
        searchVm.search(searchUrl, service.serviceId)

        // Return StreamInfo items only
        searchVm.uiState.value.list.itemList
            .filterIsInstance<StreamInfo>()
            .map { streamInfo -> createPlayableMediaItem(streamInfo) }
    }

    /**
     * Initializes search service from settings if not already set.
     */
    private fun initSearchServiceIfNeeded() {
        if (searchVm.uiState.value.selectedService != null) return

        val serviceInfoList = try {
            val jsonString = SharedContext.settingsManager.getString(SUPPORTED_SERVICES_KEY, "[]")
            Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }

        if (serviceInfoList.isEmpty()) return

        val savedServiceId = SharedContext.settingsManager.getInt(SELECTED_SERVICE_KEY, 0)
        val savedService = serviceInfoList.find { it.serviceId == savedServiceId }
        val serviceToUse = savedService ?: serviceInfoList.first()
        searchVm.updateSelectedService(serviceToUse)
    }

    /**
     * Parses a media ID and returns the corresponding children or null if not supported.
     */
    suspend fun getChildren(
        parentId: String,
        context: Context
    ): List<MediaItem>? {
        return when {
            parentId == MEDIA_ROOT_ID -> createTopLevelItems(context)
            parentId == MEDIA_HISTORY_ID -> loadHistoryItems()
            parentId == MEDIA_PLAYLISTS_ID -> loadPlaylistItems()
            parentId.startsWith(MEDIA_LOCAL_PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(MEDIA_LOCAL_PLAYLIST_PREFIX).toLongOrNull()
                if (playlistId != null) {
                    loadLocalPlaylistStreams(playlistId)
                } else null
            }
            parentId.startsWith(MEDIA_REMOTE_PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(MEDIA_REMOTE_PLAYLIST_PREFIX).toLongOrNull()
                if (playlistId != null) {
                    loadRemotePlaylistStreams(playlistId)
                } else null
            }
            parentId.startsWith(MEDIA_SEARCH_RESULT_PREFIX) -> {
                val query = parentId.removePrefix(MEDIA_SEARCH_RESULT_PREFIX)
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else null
            }
            else -> null
        }
    }

    /**
     * Handles search queries from Android Auto.
     */
    suspend fun onSearch(query: String): List<MediaItem> {
        return performSearch(query)
    }

    /**
     * Gets a single MediaItem by its media ID.
     */
    suspend fun getItem(mediaId: String): MediaItem? {
        return when {
            mediaId == MEDIA_ROOT_ID -> createRootMediaItem()
            mediaId == MEDIA_HISTORY_ID || mediaId == MEDIA_PLAYLISTS_ID -> {
                // These are browsable folders, return basic info
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(if (mediaId == MEDIA_HISTORY_ID) "History" else "Playlists")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
            }
            else -> null
        }
    }

    /**
     * Creates a browsable MediaItem for folders.
     */
    private fun createBrowsableItem(
        mediaId: String,
        title: String,
        @DrawableRes iconResId: Int,
        context: Context
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(getResourceUri(context, iconResId))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /**
     * Creates a browsable MediaItem for a playlist.
     */
    private fun createBrowsablePlaylistItem(playlistInfo: PlaylistInfo): MediaItem {
        val isLocal = playlistInfo.url.startsWith("local://")
        val mediaId = if (isLocal) {
            "$MEDIA_LOCAL_PLAYLIST_PREFIX${playlistInfo.uid}"
        } else {
            "$MEDIA_REMOTE_PLAYLIST_PREFIX${playlistInfo.uid}"
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlistInfo.name)
                    .setSubtitle("${playlistInfo.streamCount} streams")
                    .setArtworkUri(playlistInfo.thumbnailUrl?.let { Uri.parse(it) })
                    .setIsBrowsable(true)
                    .setIsPlayable(false) // Playlists must be browsed, not played directly
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()
    }

    /**
     * Creates a playable MediaItem from StreamInfo for Android Auto.
     * Encodes all metadata in the mediaId since extras are lost during IPC.
     * Format: auto://{serviceId}/{realUrl}?name=...&artist=...&thumb=...&duration=...
     */
    private fun createPlayableMediaItem(streamInfo: StreamInfo): MediaItem {
        val duration = (streamInfo.duration ?: 0) * 1000

        // Build URL with query parameters to preserve metadata across IPC
        val uriBuilder = Uri.Builder()
            .scheme("auto")
            .authority(streamInfo.serviceId.toString())
            .appendPath(streamInfo.url)

        streamInfo.name?.let { uriBuilder.appendQueryParameter("name", it) }
        streamInfo.uploaderName?.let { uriBuilder.appendQueryParameter("artist", it) }
        streamInfo.thumbnailUrl?.let { uriBuilder.appendQueryParameter("thumb", it) }
        if (duration > 0) uriBuilder.appendQueryParameter("duration", duration.toString())

        val autoMediaId = uriBuilder.build().toString()

        return MediaItem.Builder()
            .setUri("placeholder://stream")
            .setMediaId(autoMediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(streamInfo.name)
                    .setArtist(streamInfo.uploaderName)
                    .setArtworkUri(streamInfo.thumbnailUrl?.let { Uri.parse(it) })
                    .setDurationMs(duration)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    /**
     * Parsed Android Auto media ID containing all encoded metadata.
     */
    data class AutoMediaInfo(
        val serviceId: Int,
        val realUrl: String,
        val name: String?,
        val artist: String?,
        val thumbnailUrl: String?,
        val durationMs: Long?
    )

    /**
     * Parses an Android Auto media ID to extract serviceId, real URL, and metadata.
     * @return AutoMediaInfo or null if not an auto:// media ID
     */
    fun parseAutoMediaId(mediaId: String): AutoMediaInfo? {
        if (!mediaId.startsWith("auto://")) return null
        val uri = Uri.parse(mediaId)
        val serviceId = uri.authority?.toInt() ?: return null
        val realUrl = uri.pathSegments.firstOrNull() ?: return null

        return AutoMediaInfo(
            serviceId = serviceId,
            realUrl = realUrl,
            name = uri.getQueryParameter("name"),
            artist = uri.getQueryParameter("artist"),
            thumbnailUrl = uri.getQueryParameter("thumb"),
            durationMs = uri.getQueryParameter("duration")?.toLongOrNull()
        )
    }

    /**
     * Gets a Uri for a drawable resource.
     */
    private fun getResourceUri(context: Context, @DrawableRes resId: Int): Uri {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.resources.getResourcePackageName(resId))
            .appendPath(context.resources.getResourceTypeName(resId))
            .appendPath(context.resources.getResourceEntryName(resId))
            .build()
    }
}
