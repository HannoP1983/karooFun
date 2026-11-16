package de.karoofun.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.KarooEvent
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val id = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
        trySend(event.state)
    }
    awaitClose { removeConsumer(id) }
}

/** Only the numeric value of a data type, skipping searching/idle/unavailable states. */
fun KarooSystemService.valueFlow(dataTypeId: String): Flow<Double> =
    streamDataFlow(dataTypeId).mapNotNull { (it as? StreamState.Streaming)?.dataPoint?.singleValue }

inline fun <reified T : KarooEvent> KarooSystemService.consumerFlow(): Flow<T> = callbackFlow {
    val id = addConsumer<T> { trySend(it) }
    awaitClose { removeConsumer(id) }
}

/**
 * GET through the Karoo, which uses WiFi or the phone via the companion app. Returns the body on
 * HTTP 200, null without a connection, on errors or after a minute. Responses must stay below
 * [OnHttpResponse.MAX_REQUEST_SIZE].
 */
suspend fun KarooSystemService.httpGet(url: String): ByteArray? = withTimeoutOrNull(60_000) {
    val request = OnHttpResponse.MakeHttpRequest(method = "GET", url = url, waitForConnection = false)
    val response = callbackFlow {
        val id = addConsumer<OnHttpResponse>(request, onError = { close() }, onComplete = { close() }) { trySend(it.state) }
        awaitClose { removeConsumer(id) }
    }.filterIsInstance<HttpResponseState.Complete>().firstOrNull()
    response?.takeIf { it.statusCode == 200 }?.body
}
