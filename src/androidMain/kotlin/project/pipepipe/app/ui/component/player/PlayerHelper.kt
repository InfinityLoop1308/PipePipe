package project.pipepipe.app.ui.component.player

import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import kotlin.math.min

object PlayerHelper {
    data class ResolutionInfo(
        val height: Int,
        val width: Int,
        val codecs: String?,
        val frameRate: Float,
        val trackGroup: TrackGroup,
        val trackIndex: Int,
        val isSelected: Boolean
    ) {
        val resolutionPixel: String get() = "${min(height, width)}p"
        val displayLabel: String
            get() {
                val codecName = when {
                    codecs == null -> ""
                    codecs.contains("av01", ignoreCase = true) -> "AV1"
                    codecs.contains("vp9", ignoreCase = true) -> "VP9"
                    codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true) -> "H264"
                    codecs.contains("hevc", ignoreCase = true)
                            || codecs.contains("h265", ignoreCase = true)
                            || codecs.contains("hev1", ignoreCase = true)
                            || codecs.contains("hvc1", ignoreCase = true) -> "HEVC"

                    else -> codecs.uppercase()
                }

                return if (frameRate > 30f) {
                    "$codecName ${resolutionPixel}${frameRate.toInt()}"
                } else {
                    "$codecName $resolutionPixel"
                }
            }
        val codecPriority: Int
            get() = when {
                codecs == null -> 0
                codecs.contains("av01", ignoreCase = true) -> 4
                codecs.contains("hevc", ignoreCase = true) || codecs.contains("h265", ignoreCase = true) -> 3
                codecs.contains("vp9", ignoreCase = true) -> 2
                codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true) -> 1
                else -> 0
            }
    }

    data class SubtitleInfo(
        val language: String,
        val trackGroup: TrackGroup,
        val trackIndex: Int,
        val isSelected: Boolean
    )

    inline fun Tracks.Group.forEachIndexed(action: (index: Int, item: Format) -> Unit) {
        for (i in 0 until length) {
            action(i, getTrackFormat(i))
        }
    }

    inline fun Tracks.Group.find(predicate: (Format) -> Boolean): Format? {
        for (i in 0 until length) {
            val item = getTrackFormat(i)
            if (predicate(item)) {
                return item
            }
        }
        return null
    }

    inline fun Tracks.Group.indexOfFirst(predicate: (Format) -> Boolean): Int {
        for (i in 0 until length) {
            val item = getTrackFormat(i)
            if (predicate(item)) {
                return i
            }
        }
        return -1
    }

    @UnstableApi
    fun applyDefaultResolution(
        defaultResolution: String,
        availableResolutions: List<ResolutionInfo>,
        mediaController: MediaController
    ) {
        val targetResolution = when (defaultResolution) {
            "best" -> availableResolutions.firstOrNull()
            "lowest" -> availableResolutions.lastOrNull()
            "1080p" -> availableResolutions.find { it.resolutionPixel == "1080p" }
            "720p" -> availableResolutions.find { it.resolutionPixel == "720p" }
            "480p" -> availableResolutions.find { it.resolutionPixel == "480p" }
            "360p" -> availableResolutions.find { it.resolutionPixel == "360p" }
            else -> null
        }

        targetResolution?.let { resolution ->
            val params = mediaController.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(
                        resolution.trackGroup,
                        resolution.trackIndex
                    )
                )
                .build()
            mediaController.trackSelectionParameters = params
        }
    }
}