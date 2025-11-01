package project.pipepipe.app.mediasource

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyHeaders
import kotlinx.coroutines.*
import java.io.IOException
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.StreamType
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeJobFlow
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@UnstableApi
class CustomMediaSourceFactory() : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        return this
    }

    override fun getSupportedTypes(): IntArray {
        return intArrayOf(
            C.CONTENT_TYPE_DASH,
            C.CONTENT_TYPE_HLS,
            C.CONTENT_TYPE_OTHER
        )
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val extras = mediaItem.mediaMetadata.extras!!

        val dashManifestString = extras.getString("KEY_DASH_MANIFEST")
        val dashUrl = extras.getString("KEY_DASH_URL")
        val hlsUrl = extras.getString("KEY_HLS_URL")
        val headers = extras.getSerializable("KEY_HEADER_MAP") as? MutableMap<String, String> ?: mutableMapOf()
        val sponsorblockUrl = extras.getString("KEY_SPONSORBLOCK_URL")

        if (dashManifestString == null && dashUrl == null && hlsUrl == null) {
            return LazyUrlMediaSource(
                mediaItem = mediaItem,
                mediaSourceFactory = this,
            )
        }
        return createActualMediaSource(mediaItem, dashManifestString, dashUrl, hlsUrl, headers, sponsorblockUrl)
    }


    internal fun createActualMediaSource(
        mediaItem: MediaItem,
        dashManifest: String?,
        dashUrl: String?,
        hlsUrl: String?,
        headers: Map<String, String>,
        sponsorblockUrl: String?
    ): MediaSource {

        val requestHeaders = headers.toMutableMap().apply {
            putIfAbsent("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
        }

        val baseHttpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(requestHeaders)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)

        val upstreamFactory = ResolvingDataSource.Factory(baseHttpFactory) { dataSpec ->
            val filtered = dataSpec.httpRequestHeaders.filterKeys {
                it != IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME
            }
            dataSpec.withRequestHeaders(filtered)
        }

        val useCache = mediaItem.mediaMetadata.extras?.getBoolean("KEY_USE_CACHE", true) ?: true

        val dataSourceFactory: DataSource.Factory = if (useCache) {
            CacheDataSource.Factory()
                .setCache(MediaCacheProvider.get())
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheReadDataSourceFactory(FileDataSource.Factory())
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                            CacheDataSource.FLAG_IGNORE_CACHE_FOR_UNSET_LENGTH_REQUESTS
                )
        } else {
            upstreamFactory
        }

        return when {
            dashManifest != null -> {
                val dashMediaSourceFactory = DashMediaSource.Factory(dataSourceFactory)
                val manifest = DashManifestParser().parse(
                    Uri.parse("https://example.com/invalid.mpd"),
                    ByteArrayInputStream(dashManifest.toByteArray(StandardCharsets.UTF_8))
                )
                val dashMediaItem = mediaItem.copyWithStreamInfo(Uri.EMPTY, MimeTypes.APPLICATION_MPD, sponsorblockUrl)
                dashMediaSourceFactory.createMediaSource(manifest, dashMediaItem)
            }
            dashUrl != null -> {
                val dashMediaSourceFactory = DashMediaSource.Factory(dataSourceFactory)
                val dashMediaItem = mediaItem.copyWithStreamInfo(dashUrl.toUri(), MimeTypes.APPLICATION_MPD, sponsorblockUrl)
                dashMediaSourceFactory.createMediaSource(dashMediaItem)
            }
            hlsUrl != null -> {
                val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
                val hlsMediaItem = mediaItem.copyWithStreamInfo(hlsUrl.toUri(), MimeTypes.APPLICATION_M3U8, sponsorblockUrl)
                hlsMediaSourceFactory.createMediaSource(hlsMediaItem)
            }
            else -> error("Either dashManifest, dashUrl, or hlsUrl must be provided")
        }
    }

    private fun MediaItem.copyWithStreamInfo(uri: Uri, mimeType: String, sponsorblockUrl: String?): MediaItem {
        val extras = Bundle().apply {
            putString("KEY_SERVICE_ID", mediaMetadata.extras!!.getString("KEY_SERVICE_ID"))
            putString("KEY_SPONSORBLOCK_URL", sponsorblockUrl)
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaId(this.mediaId)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(this.mediaMetadata.title)
                .setArtist(this.mediaMetadata.artist)
                .setArtworkUri(this.mediaMetadata.artworkUri)
                .setDurationMs(this.mediaMetadata.durationMs)
                .setExtras(extras)
                .build())
            .build()
    }
}


@UnstableApi
class LazyUrlMediaSource(
    private val mediaItem: MediaItem,
    private val mediaSourceFactory: CustomMediaSourceFactory,
) : MediaSource {

    private var actualMediaSource: MediaSource? = null
    private var prepareJob: Job? = null
    private val eventListeners = mutableMapOf<MediaSourceEventListener, Handler>()
    private var mediaSourceCaller: MediaSource.MediaSourceCaller? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var prepareError: Exception? = null

    override fun addEventListener(
        handler: Handler,
        eventListener: MediaSourceEventListener
    ) {
        eventListeners[eventListener] = handler
        actualMediaSource?.addEventListener(handler, eventListener)
    }

    override fun removeEventListener(eventListener: MediaSourceEventListener) {
        eventListeners.remove(eventListener)
        actualMediaSource?.removeEventListener(eventListener)
    }

    override fun addDrmEventListener(
        handler: Handler,
        eventListener: DrmSessionEventListener
    ) {
        actualMediaSource?.addDrmEventListener(handler, eventListener)
    }

    override fun removeDrmEventListener(eventListener: DrmSessionEventListener) {
        actualMediaSource?.removeDrmEventListener(eventListener)
    }

    override fun prepareSource(
        caller: MediaSource.MediaSourceCaller,
        mediaTransferListener: TransferListener?,
        playerId: PlayerId
    ) {
        mediaSourceCaller = caller
        prepareJob = coroutineScope.launch {
            try {
                val serviceId = mediaItem.mediaMetadata.extras!!.getString("KEY_SERVICE_ID")
                val streamInfo = withContext(Dispatchers.IO) {
                    executeJobFlow(
                        SupportedJobType.FETCH_INFO, mediaItem.mediaId,
                        serviceId
                    ).info as StreamInfo
                }
                DatabaseOperations.updateOrInsertStreamHistory(streamInfo)
                actualMediaSource = mediaSourceFactory.createActualMediaSource(
                    mediaItem,
                    streamInfo.dashManifest,
                    streamInfo.dashUrl,
                    streamInfo.hlsUrl,
                    streamInfo.headers,
                    streamInfo.sponsorblockUrl
                )
                withContext(Dispatchers.Main) {
                    eventListeners.forEach { (listener, handler) ->
                        actualMediaSource?.addEventListener(handler, listener)
                    }
                    actualMediaSource?.prepareSource(
                        { _, timeline ->
                            caller.onSourceInfoRefreshed(this@LazyUrlMediaSource, timeline)
                        },
                        mediaTransferListener,
                        playerId
                    )
                }
            } catch (e: Exception) { // can be cancelled if service get destroyed
                e.printStackTrace()
                // Wrap with IOException to include mediaId, which ExoPlayer can handle
                prepareError = java.io.IOException("Failed to prepare media: ${mediaItem.mediaId}", e)
            }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        prepareError?.let { throw it }
        // Only delegate to actual media source if it's been initialized
        // If it's still null, the prepare job is still running and there's no error yet
        val source = actualMediaSource
        if (source != null) {
            source.maybeThrowSourceInfoRefreshError()
        }
    }

    override fun getMediaItem(): MediaItem = mediaItem

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod {
        return actualMediaSource?.createPeriod(id, allocator, startPositionUs)
            ?: throw IOException("Media source not prepared")
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        actualMediaSource?.releasePeriod(mediaPeriod)
        // Note: Don't cancel coroutineScope here - it should only be cancelled in releaseSource
        // because releasePeriod might be called multiple times
    }

    override fun releaseSource(caller: MediaSource.MediaSourceCaller) {
        prepareJob?.cancel()
        actualMediaSource?.releaseSource(caller)
        actualMediaSource = null
        mediaSourceCaller = null
        eventListeners.clear()
        coroutineScope.cancel()
    }

    override fun enable(caller: MediaSource.MediaSourceCaller) {
        actualMediaSource?.enable(caller)
    }

    override fun disable(caller: MediaSource.MediaSourceCaller) {
        actualMediaSource?.disable(caller)
    }

    override fun getInitialTimeline(): Timeline? {
        return actualMediaSource?.initialTimeline
    }

    override fun isSingleWindow(): Boolean {
        return actualMediaSource?.isSingleWindow ?: true
    }
}



fun StreamInfo.toMediaItem(): MediaItem {
    val duration = (this.duration?:0) * 1000
    val extras = Bundle().apply {
        putString("KEY_DASH_MANIFEST", dashManifest)
        putString("KEY_DASH_URL", dashUrl)
        putString("KEY_HLS_URL", hlsUrl)
        putString("KEY_SERVICE_ID", serviceId)
        putSerializable("KEY_HEADER_MAP", headers)
        putBoolean("KEY_USE_CACHE", streamType != StreamType.LIVE_STREAM)
        putString("KEY_SPONSORBLOCK_URL", sponsorblockUrl)
    }
    return MediaItem.Builder()
        .setUri("placeholder://stream")
        .setMediaId(this.url)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(this.name)
            .setArtist(this.uploaderName)
            .setArtworkUri(this.thumbnailUrl?.toUri())
            .setDurationMs(duration)
            .setExtras(extras)
            .build())
        .build()
}
