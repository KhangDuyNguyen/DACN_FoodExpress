package vn.hutech.foodexpress.customer.tracking

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class RetrofitTrackingTransport @Inject constructor(retrofit: Retrofit) : TrackingTransport {
    private val api = retrofit.create(TrackingApi::class.java)
    private val endpoint = trackingSocketEndpoint(retrofit.baseUrl())
    private val client = (retrofit.callFactory() as OkHttpClient).newBuilder()
        .followRedirects(false)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    override suspend fun snapshot(orderId: Long, token: String): TrackingSnapshot {
        val result = api.snapshot(orderId, "Bearer $token")
        require(result.orderId == orderId)
        return TrackingSnapshot(
            orderId,
            requireNotNull(result.orderCode),
            requireNotNull(result.status),
            result.latestLocation?.let { requireNotNull(it.toLocation()) },
            result.deliveryPoint?.toPoint(),
        )
    }

    override fun positions(orderId: Long, token: String): Flow<TrackingSocketEvent> = callbackFlow {
        val topic = "/topic/orders/$orderId/location"
        val subscribed = CompletableDeferred<Unit>()
        val request = Request.Builder().url(endpoint)
            .header("Sec-WebSocket-Protocol", "v12.stomp")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            private val pending = StringBuilder()

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(
                        "CONNECT\naccept-version:1.2\nhost:${endpoint.host}\nheart-beat:0,0\n" +
                            "Authorization:Bearer $token\n\n\u0000",
                    )
                ) {
                    close()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                pending.append(text)
                if (pending.length > 65_536) {
                    close()
                    return
                }

                var end = pending.indexOf("\u0000")
                while (end >= 0) {
                    val frame = pending.substring(0, end).replace("\r\n", "\n").trimStart('\n')
                    pending.delete(0, end + 1)
                    val headers = frame.substringBefore("\n\n").lineSequence()
                    when (headers.first()) {
                        "CONNECTED" -> if (!subscribed.isCompleted) {
                            if (!webSocket.send("SUBSCRIBE\nid:tracking\nack:auto\ndestination:$topic\n\n\u0000")) {
                                close()
                                return
                            }
                            subscribed.complete(Unit)
                            if (trySend(TrackingSocketEvent.Connected).isFailure) {
                                close()
                                return
                            }
                        }
                        "MESSAGE" -> {
                            val metadata = headers.drop(1).toList()
                            if (subscribed.isCompleted && "destination:$topic" in metadata &&
                                "subscription:tracking" in metadata
                            ) {
                                val location = try {
                                    gson.fromJson(frame.substringAfter("\n\n", ""), LocationWire::class.java)
                                        ?.toLocation()
                                } catch (_: JsonParseException) {
                                    null
                                }
                                if (location != null && trySend(TrackingSocketEvent.Position(location)).isFailure) {
                                    close()
                                    return
                                }
                            }
                        }
                        "ERROR" -> {
                            close()
                            return
                        }
                    }
                    end = pending.indexOf("\u0000")
                }
                if (pending.isNotEmpty() && pending.all { it == '\r' || it == '\n' }) pending.clear()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket, bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close()
            }
        })

        val connectTimeout = launch {
            if (withTimeoutOrNull(10_000) { subscribed.await() } == null) close()
        }
        awaitClose {
            connectTimeout.cancel()
            socket.cancel()
        }
    }
}

internal fun trackingSocketEndpoint(baseUrl: HttpUrl): HttpUrl = requireNotNull(baseUrl.resolve("/ws"))

private interface TrackingApi {
    @GET("orders/{orderId}/tracking")
    suspend fun snapshot(
        @Path("orderId") orderId: Long,
        @Header("Authorization") authorization: String,
    ): SnapshotWire
}

private data class SnapshotWire(
    val orderId: Long?,
    val orderCode: String?,
    val status: String?,
    val latestLocation: LocationWire?,
    val deliveryPoint: DeliveryPointWire?,
)

private data class DeliveryPointWire(val lat: Double?, val lng: Double?) {
    fun toPoint(): TrackingMapPoint? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        return TrackingMapPoint(latitude, longitude).takeIf { it.isValid }
    }
}

private data class LocationWire(val lat: Double?, val lng: Double?, val recordedAt: String?) {
    fun toLocation(): TrackingLocation? {
        val latitude = lat?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = lng?.takeIf { it in -180.0..180.0 } ?: return null
        val timestamp = recordedAt ?: return null
        return try {
            TrackingLocation(latitude, longitude, LocalDateTime.parse(timestamp))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
