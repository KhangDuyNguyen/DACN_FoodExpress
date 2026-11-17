package vn.hutech.foodexpress.driver.ui.order

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class OrderDirectionsTarget { RESTAURANT, CUSTOMER }

enum class MapsOpenResult(val message: String?) {
    OPENED(null),
    UNVERIFIED("Vui lòng xác minh lại đơn hiện tại trước khi mở chỉ đường."),
    INVALID_DESTINATION("Tọa độ điểm đến không hợp lệ. Vui lòng đồng bộ lại đơn hàng."),
    UNAVAILABLE("Không thể mở bản đồ. Hãy kiểm tra ứng dụng Google Maps hoặc trình duyệt trên thiết bị."),
}

object GoogleMapsDirections {
    fun destinationUri(latitude: Double?, longitude: Double?): Uri? {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite()
            || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", "$latitude,$longitude")
            .appendQueryParameter("travelmode", "driving")
            .appendQueryParameter("dir_action", "navigate").build()
    }

    fun open(context: Context, latitude: Double?, longitude: Double?): MapsOpenResult {
        val uri = destinationUri(latitude, longitude) ?: return MapsOpenResult.INVALID_DESTINATION
        // Starting directly works with package visibility restrictions; no package query permission needed.
        for (app in listOf("com.google.android.apps.maps", null)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(app)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return MapsOpenResult.OPENED
            } catch (_: Exception) {
                // Includes ActivityNotFoundException/SecurityException. Try the ordinary HTTPS handler once.
                // Do not log Intent contents or exception messages containing order data.
            }
        }
        return MapsOpenResult.UNAVAILABLE
    }

    fun openCurrentOrder(context: Context, state: DriverCurrentOrderUiState, authenticatedUserId: Long?,
        target: OrderDirectionsTarget): MapsOpenResult {
        val visible = state.forDriver(authenticatedUserId)
        val order = visible.order
        if (!visible.verified || visible.loading || visible.error != null || order == null)
            return MapsOpenResult.UNVERIFIED
        return when (target) {
            OrderDirectionsTarget.RESTAURANT -> open(context, order.pickupLat, order.pickupLng)
            OrderDirectionsTarget.CUSTOMER -> open(context, order.deliveryLat, order.deliveryLng)
        }
    }
}
