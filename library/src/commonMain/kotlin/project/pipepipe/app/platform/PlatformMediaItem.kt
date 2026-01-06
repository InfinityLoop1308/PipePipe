package project.pipepipe.app.platform

import project.pipepipe.shared.infoitem.StreamInfo
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Platform-independent media item representation.
 * Used for playback queue and current playing item.
 */
data class PlatformMediaItem @OptIn(ExperimentalUuidApi::class) constructor(
    val mediaId: String,
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val serviceId: Int?,
    val extras: Map<String, Any?>? = null,
    val uuid: String = Uuid.random().toString(),
)

/**
 * Convert StreamInfo to PlatformMediaItem.
 */
fun StreamInfo.toPlatformMediaItem(uuid: String? = null): PlatformMediaItem {
    return PlatformMediaItem(
        mediaId = this.url,
        title = this.name,
        artist = this.uploaderName,
        artworkUrl = this.thumbnailUrl,
        durationMs = this.duration?.let { it * 1000 },
        serviceId = this.serviceId,
        extras = buildMap<String, Any?> {
            uuid?.let { put("KEY_UUID", it) }
            dashUrl?.let { put("KEY_DASH_URL", it) }
            hlsUrl?.let { put("KEY_HLS_URL", it) }
            headers.takeIf { it.isNotEmpty() }?.let { put("KEY_HEADER_MAP", it) }
            sponsorblockUrl?.let { put("KEY_SPONSORBLOCK_URL", it) }
            relatedItemUrl?.let { put("KEY_RELATED_ITEM_URL", it) }
            if (!isLive) put("KEY_USE_CACHE", true)
        }.takeIf { it.isNotEmpty() }
    )
}
