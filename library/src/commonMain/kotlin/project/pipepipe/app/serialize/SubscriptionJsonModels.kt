package project.pipepipe.app.serialize

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for JSON import/export of subscriptions
 * Compatible with NewPipe format
 */

@Serializable
data class SubscriptionExportData(
    @SerialName("subscriptions")
    val subscriptions: List<SubscriptionItemJson>
)

@Serializable
data class SubscriptionItemJson(
    @SerialName("service_id")
    val serviceId: String,

    @SerialName("url")
    val url: String,

    @SerialName("name")
    val name: String
)
