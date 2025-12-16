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
    @SerialName("playlist_name") val playlistName: String? = null,
    @SerialName("playlist_service_id") val playlistServiceId: Int? = null,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("icon") val icon: Int? = null,
    @SerialName("channel_url") val channelUrl: String? = null,
    @SerialName("channel_name") val channelName: String? = null,
    @SerialName("channel_service_id") val channelServiceId: Int? = null,
    @SerialName("service_id") val serviceId: Int? = null,
    @SerialName("kiosk_id") val kioskId: String? = null
)