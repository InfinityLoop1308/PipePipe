package project.pipepipe.app.serialize

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SavedTabsPayload(
    val tabs: List<SavedTabPayload> = emptyList()
)

@Serializable
data class SavedTabPayload(
    @SerialName("tab_id") val tabId: Int,
    @SerialName("playlist_id") val playlistId: Long? = null,
    @SerialName("playlist_url") val playlistUrl: String? = null,
    @SerialName("group_id") val groupId: Long? = null
)