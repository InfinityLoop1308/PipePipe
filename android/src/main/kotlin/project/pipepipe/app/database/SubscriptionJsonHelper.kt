package project.pipepipe.app.database

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import project.pipepipe.app.serialize.SubscriptionExportData
import project.pipepipe.app.serialize.SubscriptionItemJson
import project.pipepipe.shared.infoitem.ChannelInfo
import java.io.InputStream
import java.io.OutputStream

/**
 * Helper class for importing and exporting subscriptions in JSON format
 * Compatible with NewPipe subscription format with backward compatibility for int service_id
 */
object SubscriptionJsonHelper {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Export subscriptions to JSON format
     * @param context Android context
     * @param uri URI to write the JSON file
     * @return Number of subscriptions exported
     */
    suspend fun exportToJson(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val subscriptions = DatabaseOperations.getAllSubscriptions()

        val exportData = SubscriptionExportData(
            subscriptions = subscriptions.map { subscription ->
                SubscriptionItemJson(
                    serviceId = subscription.service_id,
                    url = subscription.url!!,
                    name = subscription.name!!
                )
            }
        )

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            writeJson(exportData, outputStream)
        } ?: throw IllegalStateException("Unable to open output stream")

        return@withContext subscriptions.size
    }

    /**
     * Import subscriptions from JSON format
     * Supports both legacy int service_id (0->YOUTUBE, 5->BILIBILI, 6->NICONICO)
     * and new string service_id formats
     * @param context Android context
     * @param uri URI to read the JSON file
     * @return Number of subscriptions imported
     */
    suspend fun importFromJson(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            return@withContext readJson(inputStream)
        } ?: throw IllegalStateException("Unable to open input stream")
    }

    private fun writeJson(exportData: SubscriptionExportData, outputStream: OutputStream) {
        val jsonString = json.encodeToString(exportData)
        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    private suspend fun readJson(inputStream: InputStream): Int {
        val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val jsonElement = Json.parseToJsonElement(jsonString)

        if (jsonElement !is JsonObject) {
            throw IllegalArgumentException("Invalid JSON format: root must be an object")
        }

        val subscriptionsArray = jsonElement["subscriptions"]?.jsonArray
            ?: throw IllegalArgumentException("Missing 'subscriptions' array in JSON")

        var importCount = 0

        for (item in subscriptionsArray) {
            if (item !is JsonObject) continue

            try {
                val url = item["url"]?.jsonPrimitive?.content ?: continue
                val name = item["name"]?.jsonPrimitive?.content ?: continue

                val serviceId = parseServiceId(item["service_id"]) ?: continue

                if (url.isNotEmpty() && name.isNotEmpty()) {
                    val channelInfo = ChannelInfo(
                        serviceId = serviceId,
                        url = url,
                        name = name,
                        thumbnailUrl = null,
                        subscriberCount = null,
                        description = null
                    )

                    DatabaseOperations.insertOrUpdateSubscription(channelInfo)
                    importCount++
                }
            } catch (e: Exception) {
                GlobalScope.launch{ DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
            }
        }

        return importCount
    }

    /**
     * Parse service_id with backward compatibility
     * Returns null if service_id is invalid (skip this subscription)
     */
    private fun parseServiceId(serviceIdElement: JsonElement?): String? {
        if (serviceIdElement == null) {
            return null
        }

        return try {
            val intValue = serviceIdElement.jsonPrimitive.int
            convertLegacyServiceId(intValue)
        } catch (e: Exception) {
            try {
                serviceIdElement.jsonPrimitive.content
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun convertLegacyServiceId(intServiceId: Int): String? {
        return when (intServiceId) {
            0 -> "YOUTUBE"
            5 -> "BILIBILI"
            6 -> "NICONICO"
            else -> null
        }
    }
}
