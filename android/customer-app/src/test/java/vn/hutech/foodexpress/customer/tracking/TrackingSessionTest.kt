package vn.hutech.foodexpress.customer.tracking

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Base64

class TrackingSessionTest {
    private val recordedAt = LocalDateTime.of(2026, 11, 19, 10, 30)
    private val firstPoint = TrackingLocation(10.77, 106.69, recordedAt)
    private val secondPoint = TrackingLocation(10.78, 106.70, recordedAt)
    private val destination = TrackingMapPoint(10.76, 106.66)

    @Test
    fun lateRestWithOlderTimestampKeepsTheLivePoint() = exercise {
        val (socket, refresh) = connect()
        socket.send(secondPoint)
        state.first { it.latestLocation == secondPoint }
        refresh.respond(firstPoint.copy(recordedAt = recordedAt.minusSeconds(1)))

        val result = state.first { it.orderCode == "ORDER-17" }
        assertEquals(secondPoint, result.latestLocation)
        assertEquals("DELIVERING", result.status)
    }

    @Test
    fun lateRestWithTheSameTimestampKeepsTheLivePoint() = exercise {
        val (socket, refresh) = connect()
        socket.send(secondPoint)
        state.first { it.latestLocation == secondPoint }
        refresh.respond(firstPoint)

        assertEquals(secondPoint, state.first { it.orderCode == "ORDER-17" }.latestLocation)
    }

    @Test
    fun twoLivePointsWithTheSameTimestampBothUpdateTheState() = exercise {
        val (socket, refresh) = connect()
        refresh.respond(null)
        state.first { it.isWaitingForLocation }
        socket.send(firstPoint)
        assertEquals(firstPoint, state.first { it.latestLocation == firstPoint }.latestLocation)
        socket.send(secondPoint)
        assertEquals(secondPoint, state.first { it.latestLocation == secondPoint }.latestLocation)
    }

    @Test
    fun orderWithoutGpsStillReceivesItsDeliveryPoint() = exercise {
        val (_, refresh) = connect()
        refresh.respond(null, destination)

        val result = state.first { it.orderCode == "ORDER-17" }
        assertEquals(17L, result.orderId)
        assertNull(result.latestLocation)
        assertEquals(destination, result.deliveryPoint)
        assertTrue(result.isWaitingForLocation)
    }

    @Test
    fun switchingOrdersAndLeavingClosesEachOrderSubscription() = exercise {
        val (firstSocket, refresh) = connect()
        refresh.respond(firstPoint, destination)
        state.first { it.deliveryPoint == destination }

        orderId.value = 42L
        firstSocket.closed.await()
        val secondSocket = transport.sockets.receive()
        assertEquals(42L, secondSocket.orderId)
        val nextOrder = state.first { it.orderId == 42L }
        assertNull(nextOrder.latestLocation)
        assertNull(nextOrder.deliveryPoint)

        orderId.value = null
        secondSocket.closed.await()
        assertEquals(OrderTrackingState(), state.first { it.orderId == null })
    }

    @Test
    fun replacingTheTokenForTheSameOrderRejectsOldStateAndLateRest() = exercise {
        val (socket, refresh) = connect()
        refresh.respond(firstPoint, destination)
        val authorized = requireNotNull(updates.first {
            it?.forSelection(17L, token.value)?.deliveryPoint == destination
        })
        socket.events.close()
        val reconnect = transport.sockets.receive()
        reconnect.events.send(TrackingSocketEvent.Connected)
        val oldRequest = transport.requests.receive()

        token.value = "replacement-token"
        assertNull(authorized.forSelection(17L, token.value))
        oldRequest.respond(secondPoint, destination)
        reconnect.closed.await()
        val newRequest = transport.requests.receive()
        assertEquals("replacement-token", newRequest.token)
        val pending = state.first { it.isLoading && it.orderId == 17L }
        assertNull(pending.deliveryPoint)
        assertNull(pending.latestLocation)

        val newDestination = TrackingMapPoint(10.75, 106.65)
        newRequest.respond(null, newDestination)
        assertEquals(newDestination, state.first { it.deliveryPoint == newDestination }.deliveryPoint)
    }

    @Test
    fun disconnectKeepsLastPointAndReconnectRefreshesFromRest() = exercise {
        val (firstSocket, refresh) = connect()
        refresh.respond(firstPoint, destination)
        state.first { it.latestLocation == firstPoint }
        firstSocket.events.close()

        val disconnected = state.first { it.connection == TrackingConnection.Disconnected }
        assertEquals(firstPoint, disconnected.latestLocation)
        assertEquals(destination, disconnected.deliveryPoint)
        val nextSocket = transport.sockets.receive()
        assertEquals(17L, nextSocket.orderId)
        nextSocket.events.send(TrackingSocketEvent.Connected)
        val reconnectRefresh = transport.requests.receive()
        reconnectRefresh.respond(secondPoint)

        assertEquals(TrackingConnection.Connected, state.first { it.latestLocation == secondPoint }.connection)
    }

    @Test
    fun endingTheSessionClosesTheSocketAndClearsThePoint() = exercise {
        val (socket, refresh) = connect()
        refresh.respond(firstPoint, destination)
        state.first { it.deliveryPoint == destination }

        token.value = null
        socket.closed.await()
        val expired = state.first { it.problem == TrackingProblem.SessionExpired }
        assertNull(expired.latestLocation)
        assertNull(expired.deliveryPoint)
        assertEquals(TrackingConnection.Idle, expired.connection)
    }

    @Test
    fun expiredJwtDoesNotOpenTheSocketOrRequestASnapshot() = exercise {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":1}".encodeToByteArray())
        token.value = "header.$payload.signature"

        state.first { it.problem == TrackingProblem.SessionExpired }
        assertTrue(transport.sockets.tryReceive().isFailure)
        assertTrue(transport.requests.tryReceive().isFailure)
    }

    @Test
    fun websocketPathStartsAtHostRoot() {
        val apiUrl = HttpUrl.Builder().scheme("https").host("example.test").port(8443)
            .addPathSegment("api").addPathSegment("").build()
        assertEquals("https://example.test:8443/ws", trackingSocketEndpoint(apiUrl).toString())
    }

    private fun exercise(test: suspend Scenario.() -> Unit) = runBlocking {
        withTimeout(5_000) {
            val scenario = Scenario(this)
            try {
                scenario.test()
            } finally {
                scenario.observation.cancel()
            }
        }
    }

    private class Scenario(scope: CoroutineScope) {
        val transport = FakeTransport()
        val orderId = MutableStateFlow<Long?>(17L)
        val token = MutableStateFlow<String?>("token")
        val state = MutableStateFlow(OrderTrackingState())
        val updates = MutableStateFlow<TrackingSessionUpdate?>(null)
        val observation = scope.launch {
            TrackingSession(transport, retryDelayMillis = 1).observe(orderId, token).collect { update ->
                update.forSelection(orderId.value, token.value)?.let {
                    updates.value = update
                    state.value = it
                }
            }
        }

        suspend fun connect(): Pair<FakeSocket, RestRequest> {
            val socket = transport.sockets.receive()
            assertEquals(17L, socket.orderId)
            assertEquals(17L, transport.requests.receive().orderId)
            socket.events.send(TrackingSocketEvent.Connected)
            return socket to transport.requests.receive()
        }
    }

    private class FakeTransport : TrackingTransport {
        val sockets = Channel<FakeSocket>(Channel.UNLIMITED)
        val requests = Channel<RestRequest>(Channel.UNLIMITED)

        override suspend fun snapshot(orderId: Long, token: String): TrackingSnapshot {
            val request = RestRequest(orderId, token)
            requests.send(request)
            return request.result.await()
        }

        override fun positions(orderId: Long, token: String): Flow<TrackingSocketEvent> = flow {
            val socket = FakeSocket(orderId)
            try {
                sockets.send(socket)
                for (event in socket.events) emit(event)
            } finally {
                socket.closed.complete(Unit)
            }
        }
    }

    private class RestRequest(val orderId: Long, val token: String) {
        val result = CompletableDeferred<TrackingSnapshot>()

        fun respond(point: TrackingLocation?, destination: TrackingMapPoint? = null) {
            result.complete(TrackingSnapshot(orderId, "ORDER-$orderId", "DELIVERING", point, destination))
        }
    }

    private class FakeSocket(val orderId: Long) {
        val events = Channel<TrackingSocketEvent>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Unit>()

        suspend fun send(location: TrackingLocation) {
            events.send(TrackingSocketEvent.Position(location))
        }
    }
}
