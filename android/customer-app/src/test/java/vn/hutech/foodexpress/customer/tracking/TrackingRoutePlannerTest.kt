package vn.hutech.foodexpress.customer.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TrackingRoutePlannerTest {
    private var now = 0L
    private val planner = TrackingRoutePlanner { now }
    private val start = TrackingMapPoint(10.7744, 106.7010)
    private val mid = TrackingMapPoint(10.7754, 106.7020)
    private val destination = TrackingMapPoint(10.7772, 106.7050)
    private val route = listOf(start, mid, destination)
    private val detour = (0..3).map { TrackingMapPoint(10.7744 + it * 0.0001, 106.703 + it * 0.0001) }

    @Test
    fun forwardFixTrimsExistingGeometryWithoutARequestOrBacktracking() {
        loadInitialRoute()
        planner.update(state(mid))
        val remaining = listOf(mid, destination)
        assertEquals(remaining, readyPoints())
        assertNull(planner.display.value.ticket)
        planner.update(state(TrackingMapPoint(10.77539, 106.70199)))
        assertEquals(remaining, readyPoints())
    }

    @Test
    fun onlyConsecutiveOffRouteFixesQueueARateLimitedRequest() {
        loadInitialRoute()
        planner.update(state(detour[0]))
        assertEquals(route, readyPoints())
        planner.update(state(mid))

        val first = state(detour[0])
        planner.update(first)
        planner.update(first)
        planner.update(state(detour[1]))
        assertNull(planner.display.value.ticket)

        planner.update(state(detour[2]))
        val ticket = requireNotNull(planner.display.value.ticket)
        assertTrue(planner.display.value.route is TrackingRouteUpdate.Updating)
        assertEquals(8_000L, ticket.startAt)
        assertNull(planner.begin(ticket))
        planner.update(state(detour[3]))
        now = ticket.startAt
        assertEquals(detour[3], planner.begin(ticket)?.from)
    }

    @Test
    fun disconnectDropsTheRouteAndAnOlderResponseCannotOverwriteAReplacement() {
        planner.update(state(start))
        val old = begin()
        planner.update(state(start).copy(connection = TrackingConnection.Disconnected))
        assertNull(planner.display.value.route)
        assertNull(planner.display.value.ticket)

        planner.update(state(detour[0]))
        val replacement = begin()
        val replacementPoints = listOf(detour[0], destination)
        planner.finish(replacement, replacementPoints)
        planner.finish(old, route)
        assertEquals(replacementPoints, readyPoints())
    }

    @Test
    fun threeNewOffRouteFixesDuringARequestKeepObsoleteGeometryHidden() {
        planner.update(state(start))
        val old = begin()
        detour.take(3).forEach { planner.update(state(it)) }
        planner.finish(old, route)
        assertTrue(planner.display.value.route is TrackingRouteUpdate.Updating)
        assertNotEquals(old.ticket, planner.display.value.ticket)
        assertEquals(detour[2], begin().from)
    }

    @Test
    fun missingPointsAndFailedOrInvalidResponsesDoNotShowARoute() {
        planner.update(state(start).copy(deliveryPoint = null))
        assertNull(planner.display.value.ticket)
        planner.update(state(start))
        val failing = begin()
        planner.finish(failing, null)
        assertTrue(planner.display.value.failed)
        assertNull(planner.display.value.route)

        planner.clear()
        planner.update(state(start))
        planner.finish(begin(), listOf(mid, mid))
        assertTrue(planner.display.value.failed)
        assertNull(planner.display.value.route)
    }

    private fun loadInitialRoute() {
        planner.update(state(start))
        planner.finish(begin(), route)
        assertEquals(route, readyPoints())
    }

    private fun begin(): TrackingRoutePlanner.Request {
        val ticket = requireNotNull(planner.display.value.ticket)
        now = ticket.startAt
        return requireNotNull(planner.begin(ticket))
    }

    private fun readyPoints() = (planner.display.value.route as TrackingRouteUpdate.Ready).points

    private fun state(point: TrackingMapPoint) = OrderTrackingState(
        orderId = 1,
        status = "DELIVERING",
        latestLocation = TrackingLocation(point.lat, point.lng, LocalDateTime.of(2026, 11, 19, 17, 40)),
        connection = TrackingConnection.Connected,
        deliveryPoint = destination,
    )
}
