package project.pipepipe.app.platform

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import project.pipepipe.shared.infoitem.StreamInfo
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
fun MediaItem.toPlatformMediaItem(): PlatformMediaItem {
    val extrasMap = mediaMetadata.extras?.let { bundle ->
        val map = mutableMapOf<String, Any?>()
        bundle.keySet().forEach { key ->
            // Only support serializable types: String, Int, Boolean, Map<String, String>
            when (val value = bundle.get(key)) {
                is String, is Int, is Boolean, is Map<*, *> -> map[key] = value
                // Skip unsupported types
            }
        }
        map.ifEmpty { null }
    }

    return PlatformMediaItem(
        mediaId = mediaId,
        title = mediaMetadata.title?.toString(),
        artist = mediaMetadata.artist?.toString(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
        durationMs = mediaMetadata.durationMs,
        serviceId = mediaMetadata.extras?.getInt("KEY_SERVICE_ID"),
        extras = extrasMap,
        uuid = uuid ?: Uuid.random().toString()
    )
}

val MediaItem.uuid: String? get() = this.mediaMetadata.extras?.getString("KEY_UUID")

fun PlatformMediaItem.toMedia3MediaItem(): MediaItem {
    val extras = Bundle().apply {
        serviceId?.let { putInt("KEY_SERVICE_ID", it) }
        putString("KEY_UUID", uuid)
        extras?.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Map<*, *> -> {
                    // Assuming header map is Map<String, String>
                    @Suppress("UNCHECKED_CAST")
                    putSerializable(key, value as? java.io.Serializable)
                }
            }
        }
    }
    return MediaItem.Builder()
        .setUri("placeholder://stream")
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUrl?.toUri())
                .setDurationMs(durationMs)
                .setExtras(extras)
                .build()
        )
        .build()
}

fun StreamInfo.toMedia3MediaItem(uuid: String?): MediaItem = this.toPlatformMediaItem(uuid).toMedia3MediaItem()