package project.pipepipe.app.download

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.CookieManager
import project.pipepipe.app.helper.FormatHelper
import project.pipepipe.app.helper.isLoggedInCookie
import project.pipepipe.app.ui.component.Format
import project.pipepipe.shared.utils.json.requireString
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.min

/**
 * Helper class to fetch video formats using yt-dlp's --dump-json command
 * This provides comprehensive format information for all platforms supported by yt-dlp
 */
object YtDlpFormatHelper {
    private val TAG = "YtDlpFormatHelper"
    private val objectMapper = SharedContext.objectMapper

    /**
     * Video format information from yt-dlp
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class YtDlpFormat(
        @JsonProperty("format_id") val formatId: String = "",
        @JsonProperty("ext") val ext: String = "",
        @JsonProperty("vcodec") val vcodec: String? = null,
        @JsonProperty("acodec") val acodec: String? = null,
        @JsonProperty("width") val width: Int? = null,
        @JsonProperty("height") val height: Int? = null,
        @JsonProperty("fps") val fps: Float? = null,
        @JsonProperty("tbr") val tbr: Float? = null,  // Total bitrate
        @JsonProperty("vbr") val vbr: Float? = null,  // Video bitrate
        @JsonProperty("abr") val abr: Float? = null,  // Audio bitrate
        @JsonProperty("filesize") val filesize: Long? = null,
        @JsonProperty("filesize_approx") val filesizeApprox: Long? = null,
        @JsonProperty("format") val format: String = "",
        @JsonProperty("format_note") val formatNote: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("url") val url: String? = null
    ) {
        fun isVideoOnly(): Boolean = vcodec != null && vcodec != "none" && (acodec == null || acodec == "none")
        fun isAudioOnly(): Boolean = acodec != null && acodec != "none" && (vcodec == null || vcodec == "none")
        fun hasVideo(): Boolean = vcodec != null && vcodec != "none"
        fun hasAudio(): Boolean = acodec != null && acodec != "none"
        fun isHDR(): Boolean = formatNote?.contains("HDR", ignoreCase = true) == true

        fun getDisplayLabel(): String {
            val hdrLabel = if (isHDR()) " HDR" else ""

            return when {
                hasVideo() && hasAudio() -> {
                    // Combined format (video + audio)
                    val resolution = runCatching{ "${min(height!!, width!!)}p"}.getOrDefault("Unknown")
                    val codec = FormatHelper.parseCodecName(vcodec)
                    val audioCodec = FormatHelper.parseCodecName(acodec)
                    val fpsLabel = fps?.let { if (it > 30) " ${it.toInt()}fps" else "" } ?: ""
                    "$codec + $audioCodec - $resolution$fpsLabel$hdrLabel"
                }
                isVideoOnly() -> {
                    // Video only
                    val resolution = runCatching{ "${min(height!!, width!!)}p"}.getOrDefault("Unknown")
                    val codec = FormatHelper.parseCodecName(vcodec)
                    val fpsLabel = fps?.let { if (it > 30) " ${it.toInt()}fps" else "" } ?: ""
                    val baseLabel = FormatHelper.formatVideoLabel(codec, resolution, fps ?: 0f)
                    "$baseLabel$hdrLabel"
                }
                isAudioOnly() -> {
                    // Audio only
                    val codec = FormatHelper.parseCodecName(acodec)
                    val bitrateLabel = abr?.let { " ${it.toInt()}kbps" } ?: tbr?.let { " ${it.toInt()}kbps" } ?: ""
                    val langLabel = language?.let { " - $it" } ?: ""
                    "$codec$bitrateLabel$langLabel"
                }
                else -> format
            }
        }

        fun getFileSize(): Long? = filesize ?: filesizeApprox
    }

    data class FormatsResult(
        val videoFormats: List<YtDlpFormat>,
        val audioFormats: List<YtDlpFormat>,
        val combinedFormats: List<YtDlpFormat>,
        val subtitles: List<String>,
        val autoCaption: String? = null  // Preferred language that only exists in auto_captions
    )

    /**
     * Fetch all available formats for a given URL using yt-dlp
     */
    suspend fun fetchFormats(url: String): Result<FormatsResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching formats for URL: $url")

            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--skip-download")
                addOption("--quiet")
                addOption("--no-warnings")
                addOption("--socket-timeout", "10")
            }

            val response = YoutubeDL.getInstance().execute(request, null, null)

            if (response.exitCode != 0) {
                Log.e(TAG, "yt-dlp error: ${response.err}")
                return@withContext Result.failure(Exception("Failed to fetch formats: ${response.err}"))
            }

            // Parse JSON once
            val jsonNode = objectMapper.readTree(response.out)

            // Get formats list
            val formatsNode = jsonNode.get("formats")
            val allFormats = if (formatsNode != null && formatsNode.isArray) {
                objectMapper.convertValue(formatsNode, object : TypeReference<List<YtDlpFormat>>() {})
            } else {
                emptyList()
            }

            Log.d(TAG, "Found ${allFormats.size} total formats")

            // Get preferred subtitle language
            val preferredLang = SharedContext.settingsManager.getString("preferred_subtitle_language_key", "en")

            // Get subtitles keys (only read field names, not full content)
            val subtitlesKeys = jsonNode.get("subtitles")
                ?.fieldNames()
                ?.asSequence()
                ?.toList() ?: emptyList()

//            // Determine autoCaption based on preference
//            var autoCaption = if (preferredLang !in subtitlesKeys) {
//                // Preferred language not in manual subtitles, check auto captions
//                jsonNode.get("automatic_captions")
//                    ?.fieldNames()
//                    ?.asSequence()
//                    ?.toList()
//                    ?.find { it == preferredLang }
//            } else {
//                // Preferred language exists in manual subtitles, no need for auto caption
//                null
//            }
//
//            if (jsonNode.requireString("webpage_url_domain").contains("youtube", ignoreCase = true) &&
//                CookieManager.getCookie(0)?.isLoggedInCookie() == null) {
//                autoCaption = null
//            }
//
//            Log.d(TAG, "Preferred subtitle: $preferredLang, autoCaption: $autoCaption")

            // Separate formats by type
            val videoOnly = allFormats.filter { it.isVideoOnly() }
                .sortedWith(
                    compareByDescending<YtDlpFormat> { it.height ?: 0 }
                        .thenByDescending { it.fps ?: 0f }
                        .thenByDescending { it.isHDR() }
                        .thenByDescending { FormatHelper.getCodecPriority(it.vcodec) }
                )
                .distinctBy { "${it.height}_${it.fps}_${it.isHDR()}_${it.vcodec}" }

            val audioOnly = allFormats.filter { it.isAudioOnly() }
                .sortedWith(
                    compareByDescending<YtDlpFormat> { it.abr ?: it.tbr ?: 0f }
                        .thenByDescending { FormatHelper.getCodecPriority(it.acodec) }
                )
                .distinctBy { "${it.acodec}_${it.abr ?: it.tbr}" }

            val combined = allFormats.filter { it.hasVideo() && it.hasAudio() }
                .sortedWith(
                    compareByDescending<YtDlpFormat> { it.height ?: 0 }
                        .thenByDescending { it.fps ?: 0f }
                        .thenByDescending { it.isHDR() }
                        .thenByDescending { it.tbr ?: 0f }
                )
                .distinctBy { "${it.height}_${it.fps}_${it.isHDR()}_${it.vcodec}_${it.acodec}" }

            Log.d(TAG, "Categorized: ${videoOnly.size} video, ${audioOnly.size} audio, ${combined.size} combined")

            Result.success(
                FormatsResult(
                    videoFormats = videoOnly,
                    audioFormats = audioOnly,
                    combinedFormats = combined,
                    subtitles = subtitlesKeys,
                    autoCaption = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching formats", e)
            Result.failure(e)
        }
    }

    /**
     * Parse formats from DASH manifest XML
     * Returns: Pair of (videoFormats, audioFormats, subtitleIds)
     */
    fun parseFormatsFromDashManifest(dashManifest: String, url: String): Triple<List<Format>, List<Format>, List<String>> {
        val videoFormats = mutableListOf<Format>()
        val audioFormats = mutableListOf<Format>()
        val subtitleIds = mutableListOf<String>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(dashManifest.toByteArray(Charsets.UTF_8))
            val document = builder.parse(inputStream)

            val adaptationSets = document.getElementsByTagName("AdaptationSet")
            for (i in 0 until adaptationSets.length) {
                val adaptationSet = adaptationSets.item(i) as Element
                val contentType = adaptationSet.getAttribute("contentType")

                val representations = adaptationSet.getElementsByTagName("Representation")
                for (j in 0 until representations.length) {
                    val rep = representations.item(j) as Element
                    val repId = rep.getAttribute("id")
                    val codecs = rep.getAttribute("codecs")

                    when (contentType) {
                        "video" -> {
                            val height = rep.getAttribute("height").toIntOrNull() ?: 0
                            val frameRate = rep.getAttribute("frameRate").toFloatOrNull() ?: 0f
                            val codecName = FormatHelper.parseCodecName(codecs)
                            val isHDR = codecs.contains("vp9.2", ignoreCase = true) ||
                                    Regex("av01\\.0\\.(09|1[0-3])M", RegexOption.IGNORE_CASE).containsMatchIn(codecs)
                            val displayLabel = FormatHelper.formatVideoLabel(codecName, "${height}p", frameRate) + if (isHDR) " HDR" else ""

                            videoFormats.add(
                                Format(
                                    id = repId,
                                    url = url,
                                    displayLabel = displayLabel,
                                    height = height,
                                    frameRate = frameRate,
                                    codec = codecs,
                                    bitrate = rep.getAttribute("bandwidth").toIntOrNull() ?: 0,
                                    isVideoOnly = true
                                )
                            )
                        }
                        "audio" -> {
                            val codecName = FormatHelper.parseCodecName(codecs)
                            val bitrate = rep.getAttribute("bandwidth").toIntOrNull() ?: 0
                            val bitrateKbps = bitrate / 1000
                            val displayLabel = "$codecName ${bitrateKbps}kbps"

                            audioFormats.add(
                                Format(
                                    id = repId,
                                    url = url,
                                    displayLabel = displayLabel,
                                    height = 0,
                                    frameRate = 0f,
                                    codec = codecs,
                                    bitrate = bitrate
                                )
                            )
                        }
                        "text" -> {
                            // Extract subtitle id
                            if (repId.isNotEmpty()) {
                                subtitleIds.add(repId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DASH manifest", e)
        }

        // Sort and deduplicate like fetchFormats
        val sortedVideos = videoFormats
            .sortedWith(
                compareByDescending<Format> { it.height }
                    .thenByDescending { it.frameRate }
                    .thenByDescending { it.displayLabel.contains("HDR") }
                    .thenByDescending { FormatHelper.getCodecPriority(it.codec) }
            )
            .distinctBy { "${it.height}_${it.frameRate}_${it.displayLabel.contains("HDR")}_${it.codec}" }

        val sortedAudios = audioFormats
            .sortedWith(
                compareByDescending<Format> { it.bitrate }
                    .thenByDescending { FormatHelper.getCodecPriority(it.codec) }
            )
            .distinctBy { "${it.codec}_${it.bitrate}" }

        return Triple(sortedVideos, sortedAudios, subtitleIds)
    }
    private const val DEFAULT_RESOLUTION_KEY = "default_resolution_key"

    fun getFormatIdForVideo(): String {
        val defaultResolution = SharedContext.settingsManager.getString(DEFAULT_RESOLUTION_KEY, "auto")

        return when (defaultResolution) {
            "best" -> "bv+ba"        // Best video + best audio
            "worst", "lowest" -> "wv+ba"  // Worst video + best audio
            "auto" -> "res:720"       // 720p as default
            else -> {
                // Parse resolution like "1080p", "720p" etc.
                if (defaultResolution.endsWith("p")) {
                    "res:${defaultResolution.replace("p","")}"
                } else {
                    "res:720"  // Fallback to 720p
                }
            }
        }
    }

    fun getFormatIdForAudio(): String {
        return "ba[ext=m4a]/ba"
    }
}