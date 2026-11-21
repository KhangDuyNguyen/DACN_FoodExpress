package vn.hutech.foodexpress.customer.tracking

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDateTime
import java.util.Base64

data class TrackingLocation(val lat: Double, val lng: Double, val recordedAt: LocalDateTime)

data class TrackingMapPoint(val lat: Double, val lng: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lng in -180.0..180.0
}

enum class TrackingConnection { Idle, Connecting, Connected, Disconnected }

enum class TrackingProblem { SessionExpired, AccessDenied, OrderNotFound, Unavailable }

data class OrderTrackingState(
    val orderId: Long? = null,
    val orderCode: String? = null,
    val status: String? = null,
    val latestLocation: TrackingLocation? = null,
    val connection: TrackingConnection = TrackingConnection.Idle,
    val isLoading: Boolean = false,
    val problem: TrackingProblem? = null,
    val deliveryPoint: TrackingMapPoint? = null,
) {
    val isWaitingForLocation: Boolean get() = orderCode != null && latestLocation == null
}

internal data class TrackingSnapshot(
    val orderId: Long,
    val orderCode: String,
    val status: String,
    val latestLocation: TrackingLocation?,
    val deliveryPoint: TrackingMapPoint?,
)

// StateFlow and Compose can collect a session change separately from its first tracking update.
internal class TrackingSessionUpdate(private val token: String?, private val state: OrderTrackingState) {
    fun forSelection(orderId: Long?, token: String?): OrderTrackingState? =
        state.takeIf { it.orderId == orderId && this.token == token }
}

internal sealed interface TrackingSocketEvent {
    data object Connected : TrackingSocketEvent
    data class Position(val location: TrackingLocation) : TrackingSocketEvent
}

internal interface TrackingTransport {
    suspend fun snapshot(orderId: Long, token: String): TrackingSnapshot
    fun positions(orderId: Long, token: String): Flow<TrackingSocketEvent>
}

internal class TrackingSession(
    private val transport: TrackingTransport,
    private val retryDelayMillis: Long = 2_000,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(orderIds: Flow<Long?>, tokens: Flow<String?>): Flow<TrackingSessionUpdate> =
        combine(orderIds, tokens) { orderId, token -> orderId to token }
            .distinctUntilChanged()
            .flatMapLatest { (orderId, token) ->
                when {
                    orderId == null || orderId <= 0 -> flowOf(OrderTrackingState())
                    token.isNullOrBlank() || token.any { it.isWhitespace() || it == '\u0000' } ->
                        flowOf(stopped(orderId, TrackingProblem.SessionExpired))
                    else -> track(orderId, token)
                }.map { state -> TrackingSessionUpdate(token, state) }
            }
            .buffer(0)

    private fun track(orderId: Long, token: String): Flow<OrderTrackingState> = channelFlow {
        val expiresIn = millisUntilTokenExpiry(token)
        if (expiresIn != null && expiresIn <= 0) {
            send(stopped(orderId, TrackingProblem.SessionExpired))
            return@channelFlow
        }

        var state = OrderTrackingState(orderId, connection = TrackingConnection.Connecting, isLoading = true)
        send(state)
        val updates = Channel<Update>(Channel.BUFFERED)
        var socketRevision = 0L
        var refreshId = 0L
        var refresh: Job? = null

        fun refreshSnapshot() {
            refresh?.cancel()
            val id = ++refreshId
            val revisionAtRequest = socketRevision
            refresh = launch {
                val update = try {
                    val snapshot = transport.snapshot(orderId, token)
                    require(snapshot.orderId == orderId)
                    Update.Snapshot(id, revisionAtRequest, snapshot)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val problem = when ((failure as? HttpException)?.code()) {
                        401 -> TrackingProblem.SessionExpired
                        403 -> TrackingProblem.AccessDenied
                        404 -> TrackingProblem.OrderNotFound
                        else -> TrackingProblem.Unavailable
                    }
                    Update.RefreshFailed(id, problem)
                }
                updates.send(update)
            }
        }

        refreshSnapshot()
        val socket = launch {
            while (isActive) {
                try {
                    transport.positions(orderId, token).collect { updates.send(Update.Socket(it)) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the current point visible while reconnecting.
                }
                updates.send(Update.Disconnected)
                delay(retryDelayMillis)
            }
        }
        val expiry = expiresIn?.let { remaining ->
            launch {
                delay(remaining)
                updates.send(Update.Expired)
            }
        }

        try {
            while (true) {
                when (val update = updates.receive()) {
                    is Update.Socket -> when (val event = update.event) {
                        TrackingSocketEvent.Connected -> {
                            state = state.copy(connection = TrackingConnection.Connected)
                            // Subscribe before refreshing, including on reconnect.
                            refreshSnapshot()
                        }
                        is TrackingSocketEvent.Position -> {
                            val last = state.latestLocation
                            if (last == null || !event.location.recordedAt.isBefore(last.recordedAt)) {
                                socketRevision++
                                state = state.copy(latestLocation = event.location)
                            }
                        }
                    }
                    Update.Disconnected -> state = state.copy(connection = TrackingConnection.Disconnected)
                    is Update.Snapshot -> {
                        if (update.id != refreshId) continue
                        val snapshot = update.value
                        val last = state.latestLocation
                        val candidate = snapshot.latestLocation
                        val acceptSnapshot = candidate != null && (last == null ||
                            candidate.recordedAt.isAfter(last.recordedAt) ||
                            candidate.recordedAt == last.recordedAt && update.socketRevision == socketRevision)
                        state = state.copy(
                            orderCode = snapshot.orderCode,
                            status = snapshot.status,
                            latestLocation = if (acceptSnapshot) candidate else last,
                            deliveryPoint = snapshot.deliveryPoint,
                            isLoading = false,
                            problem = null,
                        )
                    }
                    is Update.RefreshFailed -> {
                        if (update.id != refreshId) continue
                        if (update.problem != TrackingProblem.Unavailable) {
                            send(stopped(orderId, update.problem))
                            return@channelFlow
                        }
                        state = state.copy(isLoading = false, problem = update.problem)
                    }
                    Update.Expired -> {
                        send(stopped(orderId, TrackingProblem.SessionExpired))
                        return@channelFlow
                    }
                }
                send(state)
            }
        } finally {
            socket.cancel()
            refresh?.cancel()
            expiry?.cancel()
            updates.cancel()
        }
    }

    private fun stopped(orderId: Long, problem: TrackingProblem) = OrderTrackingState(orderId, problem = problem)

    // The claim only limits the local lifetime. REST and STOMP still authenticate the token on the server.
    private fun millisUntilTokenExpiry(token: String): Long? = runCatching {
        val payload = Base64.getUrlDecoder().decode(token.split('.')[1]).decodeToString()
        val seconds = JsonParser.parseString(payload).asJsonObject.get("exp").asLong
        Math.subtractExact(Math.multiplyExact(seconds, 1_000L), System.currentTimeMillis())
    }.getOrNull()

    private sealed interface Update {
        data class Socket(val event: TrackingSocketEvent) : Update
        data class Snapshot(val id: Long, val socketRevision: Long, val value: TrackingSnapshot) : Update
        data class RefreshFailed(val id: Long, val problem: TrackingProblem) : Update
        data object Disconnected : Update
        data object Expired : Update
    }
}
