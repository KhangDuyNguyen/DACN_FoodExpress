package vn.hutech.foodexpress.customer.tracking

import java.math.BigDecimal

/** Details already authorized for the same order, supplied by the screen's caller. */
internal data class TrackingOrderDetails(
    val orderId: Long,
    val driverName: String? = null,
    val licensePlate: String? = null,
    val deliveryAddress: String? = null,
    val totalAmount: BigDecimal? = null,
)

internal sealed interface TrackingRouteUpdate {
    val orderId: Long

    data class Updating(override val orderId: Long) : TrackingRouteUpdate
    data class Ready(override val orderId: Long, val points: List<TrackingMapPoint>) : TrackingRouteUpdate
}

internal enum class TrackingMapStatus { Loading, Ready, Unavailable }

/** GPS, destination and route geometry can change independently of the map style. */
internal data class TrackingMapLayers(
    val driverPoint: TrackingMapPoint?,
    val deliveryPoint: TrackingMapPoint?,
    val routePoints: List<TrackingMapPoint>,
    val isDriverPointStale: Boolean,
)

internal sealed interface OrderTrackingContent {
    data object Loading : OrderTrackingContent
    data object SessionExpired : OrderTrackingContent
    data object OrderDenied : OrderTrackingContent
    data object LoadFailed : OrderTrackingContent

    data class Order(
        val orderCode: String,
        val status: String?,
        val isDisconnected: Boolean,
        val isRouteUpdating: Boolean,
        val details: TrackingOrderDetails?,
        val mapLayers: TrackingMapLayers,
    ) : OrderTrackingContent
}

internal fun OrderTrackingState.toContent(
    details: TrackingOrderDetails?,
    route: TrackingRouteUpdate?,
): OrderTrackingContent {
    when (problem) {
        TrackingProblem.SessionExpired -> return OrderTrackingContent.SessionExpired
        TrackingProblem.AccessDenied, TrackingProblem.OrderNotFound -> return OrderTrackingContent.OrderDenied
        else -> Unit
    }
    val code = orderCode
    if (orderId == null || code.isNullOrBlank()) {
        return if (problem == TrackingProblem.Unavailable) OrderTrackingContent.LoadFailed else OrderTrackingContent.Loading
    }

    val ownDetails = details?.takeIf { it.orderId == orderId }
    val ownRoute = route?.takeIf { it.orderId == orderId }
    val driverPoint = latestLocation?.let { TrackingMapPoint(it.lat, it.lng) }?.takeIf { it.isValid }
    val deliveryPoint = this.deliveryPoint?.takeIf { it.isValid }
    val routePoints = (ownRoute as? TrackingRouteUpdate.Ready)?.points.orEmpty()
    val isDisconnected = connection == TrackingConnection.Disconnected
    val showRoute = !isDisconnected && driverPoint != null && deliveryPoint != null && routePoints.size >= 2 &&
        routePoints.all { it.isValid } && routePoints.any { it != routePoints.first() }
    return OrderTrackingContent.Order(
        orderCode = code,
        status = status,
        isDisconnected = isDisconnected,
        isRouteUpdating = ownRoute is TrackingRouteUpdate.Updating,
        details = ownDetails,
        mapLayers = TrackingMapLayers(
            driverPoint = driverPoint,
            deliveryPoint = deliveryPoint,
            routePoints = if (showRoute) routePoints else emptyList(),
            isDriverPointStale = driverPoint != null && isDisconnected,
        ),
    )
}
