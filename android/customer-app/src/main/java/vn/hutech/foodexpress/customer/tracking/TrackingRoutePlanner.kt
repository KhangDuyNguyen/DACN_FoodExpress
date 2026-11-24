package vn.hutech.foodexpress.customer.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.hypot

/** Owns the route only. GPS markers still read the original tracking state. */
internal class TrackingRoutePlanner(private val nowMillis: () -> Long) {
    data class Ticket(val id: Long, val startAt: Long)

    data class Request(
        val ticket: Ticket,
        val from: TrackingMapPoint,
        val to: TrackingMapPoint,
    )

    data class Display(
        val route: TrackingRouteUpdate? = null,
        val ticket: Ticket? = null,
        val failed: Boolean = false,
    )

    private data class Trip(val orderId: Long, val destination: TrackingMapPoint)

    private val _display = MutableStateFlow(Display())
    val display: StateFlow<Display> = _display.asStateFlow()
    private var trip: Trip? = null
    private var latestFix: TrackingLocation? = null
    private val recentPoints = ArrayDeque<TrackingMapPoint>()
    private var remaining = emptyList<TrackingMapPoint>()
    private var misses = 0
    private var nextTicketId = 0L
    private var inFlight: Request? = null
    private var lastStartedAt: Long? = null

    fun clear() = update(OrderTrackingState())

    fun update(state: OrderTrackingState) {
        val destination = state.deliveryPoint?.takeIf { it.isValid }
        val fix = state.latestLocation?.takeIf { it.point().isValid }
        val activeTrip = if (state.orderId != null && destination != null && fix != null &&
            state.problem == null && state.connection == TrackingConnection.Connected && state.status == "DELIVERING"
        ) Trip(state.orderId, destination) else null
        if (activeTrip != trip) {
            trip = activeTrip
            latestFix = null
            recentPoints.clear()
            remaining = emptyList()
            misses = 0
            inFlight = null
            _display.value = Display()
        }
        if (activeTrip == null || fix == null || fix == latestFix) return

        latestFix = fix
        val point = fix.point()
        if (recentPoints.size == OffRouteFixes) recentPoints.removeFirst()
        recentPoints.addLast(point)
        if (_display.value.ticket != null) return

        if (remaining.isEmpty()) {
            schedule(activeTrip)
            return
        }
        val match = nearestOnRoute(point, remaining)
        if (match.distanceMeters > OffRouteMeters) {
            misses++
            if (misses >= OffRouteFixes) schedule(activeTrip)
        } else {
            misses = 0
            if (match.distanceMeters <= TrimMeters) {
                remaining = remaining.after(match)
                _display.value = Display(route = TrackingRouteUpdate.Ready(activeTrip.orderId, remaining))
            }
        }
    }

    fun begin(ticket: Ticket): Request? {
        val activeTrip = trip ?: return null
        val origin = latestFix?.point() ?: return null
        if (_display.value.ticket != ticket || inFlight != null || nowMillis() < ticket.startAt) return null
        val request = Request(ticket, origin, activeTrip.destination)
        inFlight = request
        lastStartedAt = nowMillis()
        return request
    }

    fun finish(request: Request, points: List<TrackingMapPoint>?) {
        val activeTrip = trip ?: return
        if (request != inFlight || _display.value.ticket != request.ticket) return
        inFlight = null
        if (points == null || points.size < 2 || points.any { !it.isValid } ||
            points.all { it == points.first() } || distanceMeters(points.first(), request.from) > EndpointMeters ||
            distanceMeters(points.last(), request.to) > EndpointMeters
        ) {
            _display.value = Display(failed = true)
            return
        }

        // A response may take long enough for several new fixes to arrive. Check those fixes
        // against its own geometry before ever showing it.
        val current = requireNotNull(latestFix).point()
        val recentMatches = recentPoints.map { nearestOnRoute(it, points) }
        val latestMatch = recentMatches.last()
        misses = recentMatches.takeLastWhile { it.distanceMeters > OffRouteMeters }.size
        if (current != request.from && misses >= OffRouteFixes) {
            schedule(activeTrip)
        } else {
            remaining = if (latestMatch.distanceMeters <= TrimMeters) points.after(latestMatch) else points
            _display.value = Display(route = TrackingRouteUpdate.Ready(activeTrip.orderId, remaining))
        }
    }

    private fun schedule(activeTrip: Trip) {
        remaining = emptyList()
        misses = 0
        val startAt = maxOf(nowMillis(), (lastStartedAt ?: -RequestIntervalMs) + RequestIntervalMs)
        _display.value = Display(
            route = TrackingRouteUpdate.Updating(activeTrip.orderId),
            ticket = Ticket(++nextTicketId, startAt),
        )
    }
}

private const val OffRouteFixes = 3
private const val OffRouteMeters = 45.0
private const val TrimMeters = 30.0
private const val EndpointMeters = 100.0
private const val RequestIntervalMs = 8_000L
private const val MetersPerDegree = 111_195.0

private fun TrackingLocation.point() = TrackingMapPoint(lat, lng)

private data class RouteMatch(val nextIndex: Int, val point: TrackingMapPoint, val distanceMeters: Double)

private fun nearestOnRoute(fix: TrackingMapPoint, points: List<TrackingMapPoint>): RouteMatch {
    val longitudeScale = MetersPerDegree * cos(Math.toRadians(fix.lat))
    return points.zipWithNext().mapIndexed { index, (start, end) ->
        val x = (end.lng - start.lng) * longitudeScale
        val y = (end.lat - start.lat) * MetersPerDegree
        val lengthSquared = x * x + y * y
        val fraction = if (lengthSquared == 0.0) 0.0 else
            (((fix.lng - start.lng) * longitudeScale * x + (fix.lat - start.lat) * MetersPerDegree * y) /
                lengthSquared).coerceIn(0.0, 1.0)
        val projected = TrackingMapPoint(
            start.lat + (end.lat - start.lat) * fraction,
            start.lng + (end.lng - start.lng) * fraction,
        )
        RouteMatch(index + 1, projected, distanceMeters(projected, fix))
    }.minBy { it.distanceMeters }
}

private fun List<TrackingMapPoint>.after(match: RouteMatch): List<TrackingMapPoint> {
    val tail = drop(match.nextIndex)
    return if (match.point == tail.first() && tail.size > 1) tail else listOf(match.point) + tail
}

private fun distanceMeters(first: TrackingMapPoint, second: TrackingMapPoint): Double = hypot(
    (first.lat - second.lat) * MetersPerDegree,
    (first.lng - second.lng) * MetersPerDegree * cos(Math.toRadians(second.lat)),
)
