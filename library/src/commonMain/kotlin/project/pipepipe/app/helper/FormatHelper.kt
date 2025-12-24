package project.pipepipe.app.helper

import project.pipepipe.app.SharedContext

object FormatHelper {
    private val defaultAdvancedFormats = setOf("VP9", "HEVC")

    /**
     * Check if a codec is allowed based on advanced_formats_key settings.
     */
    fun isCodecAllowed(codecs: String?): Boolean {
        if (codecs == null) return true

        val enabledFormats = SharedContext.settingsManager.getStringSet(
            "advanced_formats_key",
            defaultAdvancedFormats
        )

        val codecName = parseCodecName(codecs)

        // Map codec names to setting values
        val settingKey = when (codecName) {
            "VP9" -> "VP9"
            "AV1" -> "AV01"
            "HEVC" -> "HEVC"
            "EC-3" -> "EC-3"
            else -> return true // Not an advanced format
        }

        return enabledFormats.contains(settingKey)
    }
    /**
     * Parse codec string and return standardized codec name
     * Used by both player and download format selection
     */
    fun parseCodecName(codecs: String?): String {
        return when {
            codecs == null -> ""
            codecs.contains("av01", ignoreCase = true) -> "AV1"
            codecs.contains("vp9", ignoreCase = true) -> "VP9"
            codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true) -> "H264"
            codecs.contains("hevc", ignoreCase = true)
                    || codecs.contains("h265", ignoreCase = true)
                    || codecs.contains("hev1", ignoreCase = true)
                    || codecs.contains("hvc1", ignoreCase = true) -> "HEVC"
            codecs.contains("mp4a", ignoreCase = true) -> "M4A"
            codecs.contains("ec-3", ignoreCase = true) || codecs.contains("ec3", ignoreCase = true) -> "EC-3"
            else -> codecs.substringBefore(".").uppercase()
        }
    }

    /**
     * Format video display label with codec, resolution, and optional frame rate
     */
    fun formatVideoLabel(codecName: String, resolution: String, frameRate: Float? = null): String {
        return if (frameRate != null && frameRate.toInt() > 30) {
            "$codecName $resolution${frameRate.toInt()}"
        } else {
            "$codecName $resolution"
        }
    }

    /**
     * Get codec priority for sorting
     * Higher priority means better codec
     */
    fun getCodecPriority(codecs: String?): Int {
        return when {
            codecs == null -> 0
            codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true) -> 3
            codecs.contains("av01", ignoreCase = true) -> 4
            codecs.contains("hevc", ignoreCase = true) || codecs.contains("h265", ignoreCase = true) -> 2
            codecs.contains("vp9", ignoreCase = true) -> 1
            codecs.contains("mp4a", ignoreCase = true) -> -1
            codecs.contains("opus", ignoreCase = true) -> -2
            else -> 0
        }
    }

    /**
     * Get file extension based on codec
     */
    fun getFileExtension(codecs: String?, isSubtitle: Boolean = false): String {
        if (isSubtitle) return "srt"

        return when {
            codecs == null -> "mp4"
            // Video codecs
            codecs.contains("av01", ignoreCase = true) -> "mp4"
            codecs.contains("vp9", ignoreCase = true) -> "webm"
            codecs.contains("avc", ignoreCase = true) || codecs.contains("h264", ignoreCase = true) -> "mp4"
            codecs.contains("hevc", ignoreCase = true)
                || codecs.contains("h265", ignoreCase = true)
                || codecs.contains("hev1", ignoreCase = true)
                || codecs.contains("hvc1", ignoreCase = true) -> "mp4"
            // Audio codecs
            codecs.contains("mp4a", ignoreCase = true) -> "m4a"
            codecs.contains("opus", ignoreCase = true) -> "opus"
            else -> "mp4"
        }
    }
    /**
     * Get MIME type based on codec
     */
    fun getMimeType(codecs: String?): String {
        if (codecs == null) return "video/mp4" // Default fallback

        return when {
            // Audio Codecs
            codecs.contains("opus", ignoreCase = true) -> "audio/webm" // Opus usually comes in WebM container
            codecs.contains("mp4a", ignoreCase = true) -> "audio/mp4"
            codecs.contains("vorbis", ignoreCase = true) -> "audio/webm"

            // Video Codecs (WebM)
            codecs.contains("vp9", ignoreCase = true) ||
                    codecs.contains("vp8", ignoreCase = true) -> "video/webm"

            // Video Codecs (MP4)
            codecs.contains("av01", ignoreCase = true) -> "video/mp4" // AV1 usually in MP4 for mobile compatibility
            codecs.contains("avc", ignoreCase = true) ||
                    codecs.contains("h264", ignoreCase = true) -> "video/mp4"
            codecs.contains("hevc", ignoreCase = true) ||
                    codecs.contains("h265", ignoreCase = true) ||
                    codecs.contains("hev1", ignoreCase = true) ||
                    codecs.contains("hvc1", ignoreCase = true) -> "video/mp4"

            else -> "video/mp4"
        }
    }
}