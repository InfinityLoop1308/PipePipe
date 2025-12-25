package project.pipepipe.app.platform

/**
 * Platform-independent media item representation.
 * Used for playback queue and current playing item.
 */
data class PlatformMediaItem(
    val mediaId: String,
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val serviceId: Int?,
    val extras: Map<String, Any?>? = null
)
