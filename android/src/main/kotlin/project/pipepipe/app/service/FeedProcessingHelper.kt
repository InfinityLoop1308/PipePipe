package project.pipepipe.app.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import project.pipepipe.app.SharedContext
import project.pipepipe.database.Subscriptions
import project.pipepipe.shared.infoitem.SupportedServiceInfo

/**
 * Load fetch interval configurations for all services
 */
fun loadServiceFetchIntervals(): Map<Int, Int> {
    return try {
        val jsonString = SharedContext.settingsManager.getString("supported_services", "[]")
        val serviceInfoList = Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)
        serviceInfoList.associate { it.serviceId to it.feedFetchInterval }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }
}

/**
 * Process subscriptions concurrently with service-level rate limiting
 *
 * @param subscriptions List of subscriptions to process
 * @param serviceFetchIntervals Map of service_id to fetch interval in milliseconds
 * @param maxConcurrency Maximum number of concurrent requests (default: 5)
 * @param processOne Callback to process a single subscription
 */
suspend fun processSubscriptionsConcurrently(
    subscriptions: List<Subscriptions>,
    serviceFetchIntervals: Map<Int, Int>,
    maxConcurrency: Int = 5,
    processOne: suspend (Subscriptions) -> Unit
) {
    // Group subscriptions by service_id
    val subscriptionsByService = subscriptions.groupBy { it.service_id }

    // Global semaphore to limit total concurrent requests
    val globalSemaphore = Semaphore(maxConcurrency)

    coroutineScope {
        // Process each service group concurrently
        subscriptionsByService.map { (serviceId, serviceSubscriptions) ->
            async(Dispatchers.IO) {
                val fetchInterval = serviceFetchIntervals[serviceId] ?: 0

                // Within each service, process serially with delay
                serviceSubscriptions.forEach { subscription ->
                    globalSemaphore.withPermit {
                        processOne(subscription)
                    }

                    // Wait for service-specific interval before next request
                    if (fetchInterval > 0) {
                        delay(fetchInterval.toLong())
                    }
                }
            }
        }.forEach { it.await() }
    }
}
