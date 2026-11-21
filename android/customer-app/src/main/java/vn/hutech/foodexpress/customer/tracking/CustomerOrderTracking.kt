package vn.hutech.foodexpress.customer.tracking

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/** Destination for the selected order. The app's customer session owns [accessTokens]. */
@Composable
internal fun CustomerOrderTracking(
    orderId: Long,
    accessTokens: StateFlow<String?>,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    details: TrackingOrderDetails? = null,
    route: TrackingRouteUpdate? = null,
    onDriverMarkerPosition: ((TrackingMapPoint, Rect) -> Unit)? = null,
    viewModel: OrderTrackingViewModel = hiltViewModel(),
) {
    val token by accessTokens.collectAsStateWithLifecycle()
    val configured = VietmapTrackingResources.isConfigured
    var mapStatus by remember(orderId, token) {
        mutableStateOf(if (configured) TrackingMapStatus.Loading else TrackingMapStatus.Unavailable)
    }
    OrderTrackingRoute(
        orderId = orderId,
        accessTokens = accessTokens,
        onBack = onBack,
        onSignIn = onSignIn,
        details = details,
        route = route,
        mapStatus = mapStatus,
        onRetryMap = if (configured) ({ mapStatus = TrackingMapStatus.Loading }) else null,
        mapContent = if (configured) ({ layers ->
            VietmapTrackingMap(layers, { mapStatus = it }, onDriverMarkerPosition)
        }) else null,
        viewModel = viewModel,
    )
}
