package vn.hutech.foodexpress.customer.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OrderTrackingPresentationTest {
    private val destination = TrackingMapPoint(10.7772, 106.7050)
    private val state = OrderTrackingState(
        orderId = 1082,
        orderCode = "TEST-1082",
        status = "DELIVERING",
        latestLocation = TrackingLocation(10.7744, 106.7010, LocalDateTime.of(2026, 11, 19, 17, 40)),
        connection = TrackingConnection.Connected,
        deliveryPoint = destination,
    )
    private val details = TrackingOrderDetails(orderId = 1082, driverName = "Tài xế kiểm thử")
    private val driver = TrackingMapPoint(10.7744, 106.7010)
    private val route = TrackingRouteUpdate.Ready(1082, listOf(driver, TrackingMapPoint(10.7750, 106.7030), destination))

    @Test
    fun loadingDoesNotExposeDetailsBeforeTheOrderSnapshot() {
        assertEquals(
            OrderTrackingContent.Loading,
            OrderTrackingState(orderId = 1082, isLoading = true).toContent(details, route),
        )
    }

    @Test
    fun expiredAndDeniedScreensDoNotExposeCachedOrderData() {
        assertEquals(
            OrderTrackingContent.SessionExpired,
            state.copy(problem = TrackingProblem.SessionExpired).toContent(details, route),
        )
        for (problem in listOf(TrackingProblem.AccessDenied, TrackingProblem.OrderNotFound)) {
            assertEquals(OrderTrackingContent.OrderDenied, state.copy(problem = problem).toContent(details, route))
        }
    }

    @Test
    fun waitingForGpsOnlyShowsTheDeliveryPoint() {
        val content = state.copy(latestLocation = null).toContent(details, route) as OrderTrackingContent.Order
        assertNull(content.mapLayers.driverPoint)
        assertEquals(destination, content.mapLayers.deliveryPoint)
        assertTrue(content.mapLayers.routePoints.isEmpty())
    }

    @Test
    fun disconnectedRetainsLastKnownDriverPointAndHidesReadyRoute() {
        val connected = state.toContent(details, route) as OrderTrackingContent.Order
        assertEquals(route.points, connected.mapLayers.routePoints)

        val content = state.copy(connection = TrackingConnection.Disconnected).toContent(details, route)
            as OrderTrackingContent.Order
        assertTrue(content.isDisconnected)
        assertTrue(content.mapLayers.isDriverPointStale)
        assertEquals(driver, content.mapLayers.driverPoint)
        assertEquals(destination, content.mapLayers.deliveryPoint)
        assertTrue(content.mapLayers.routePoints.isEmpty())
    }

    @Test
    fun missingOrInvalidDeliveryPointIsNotReplacedByDriverGps() {
        for (point in listOf(null, TrackingMapPoint(91.0, 106.7050))) {
            val content = state.copy(deliveryPoint = point).toContent(details, route) as OrderTrackingContent.Order
            assertEquals(driver, content.mapLayers.driverPoint)
            assertNull(content.mapLayers.deliveryPoint)
            assertTrue(content.mapLayers.routePoints.isEmpty())
        }
    }

    @Test
    fun reroutingMovesTheDriverWithoutWaitingForReplacementGeometry() {
        val moved = state.copy(latestLocation = state.latestLocation!!.copy(lat = 10.775))
        val pending = moved.toContent(details, TrackingRouteUpdate.Updating(1082)) as OrderTrackingContent.Order
        assertTrue(pending.isRouteUpdating)
        assertEquals(TrackingMapPoint(10.775, driver.lng), pending.mapLayers.driverPoint)
        assertTrue(pending.mapLayers.routePoints.isEmpty())

        val replacement = route.copy(points = listOf(TrackingMapPoint(10.775, driver.lng), route.points[1], destination))
        val ready = moved.toContent(details, replacement) as OrderTrackingContent.Order
        assertFalse(ready.isRouteUpdating)
        assertEquals(replacement.points, ready.mapLayers.routePoints)
    }

    @Test
    fun malformedOrDuplicateGeometryNeverBecomesARoute() {
        val invalidRoutes = listOf(
            listOf(driver),
            listOf(driver, driver),
            listOf(driver, TrackingMapPoint(Double.NaN, 106.7030)),
            listOf(driver, TrackingMapPoint(10.775, 181.0)),
        )
        for (points in invalidRoutes) {
            val content = state.toContent(details, route.copy(points = points)) as OrderTrackingContent.Order
            assertTrue(content.mapLayers.routePoints.isEmpty())
        }
    }

    @Test
    fun detailsAndRouteFromAnotherOrderAreIgnored() {
        val content = state.toContent(details.copy(orderId = 500), TrackingRouteUpdate.Updating(500))
            as OrderTrackingContent.Order
        assertNull(content.details)
        assertEquals(destination, content.mapLayers.deliveryPoint)
        assertTrue(content.mapLayers.routePoints.isEmpty())
        assertFalse(content.isRouteUpdating)
    }
}
