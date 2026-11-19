package vn.hutech.foodexpress.customer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun OrderTrackingMap(
    order: OrderTrackingContent.Order,
    height: Dp,
    status: TrackingMapStatus,
    onRetryMap: (() -> Unit)?,
    mapContent: (@Composable (TrackingMapLayers) -> Unit)?,
) {
    val layers = order.mapLayers
    val canShowMap = mapContent != null && status == TrackingMapStatus.Ready
    val needsMap = mapContent == null || status == TrackingMapStatus.Unavailable
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE3DE)),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(height).background(TrackingColors.Muted)) {
                if (!needsMap) {
                    Box(Modifier.fillMaxSize()) { mapContent(layers) }
                }
                when {
                    needsMap -> MapMessage(
                        title = "Chưa tải được bản đồ",
                        description = "Thông tin đơn vẫn xem được bên dưới.",
                        onRetry = onRetryMap,
                    )
                    status == TrackingMapStatus.Loading -> MapMessage(
                        title = "Đang tải bản đồ…",
                        isLoading = true,
                    )
                    else -> Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        when {
                            order.isDisconnected -> TrackingPill(
                                "Mất kết nối", TrackingColors.Secondary, TrackingColors.Muted,
                            )
                            layers.driverPoint == null -> TrackingPill(
                                "Đang chờ GPS", TrackingColors.Gold, TrackingColors.GoldSurface,
                            )
                            else -> TrackingPill(
                                "Đang cập nhật vị trí", TrackingColors.Green, TrackingColors.GreenSurface,
                            )
                        }
                    }
                }
            }
            val note = when {
                layers.isDriverPointStale && canShowMap -> "Đang hiển thị vị trí cập nhật lần cuối"
                order.isDisconnected && layers.isDriverPointStale -> "Mất kết nối. Đã giữ vị trí cập nhật lần cuối."
                order.isDisconnected -> "Mất kết nối. Chưa có vị trí của tài xế."
                layers.driverPoint == null -> "Chưa có vị trí của tài xế"
                order.isRouteUpdating -> "Đang cập nhật tuyến đường…"
                canShowMap && layers.routePoints.isNotEmpty() -> "Tuyến dự kiến còn lại"
                canShowMap && layers.deliveryPoint == null -> "Chưa có tọa độ điểm giao"
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = TrackingColors.Secondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
internal fun TrackingMapPlaceholder(height: Dp, title: String, isLoading: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFDCE3DE))) {
        Box(Modifier.fillMaxWidth().height(height).background(TrackingColors.Muted)) {
            MapMessage(title, isLoading = isLoading)
        }
    }
}

@Composable
private fun MapMessage(
    title: String,
    description: String? = null,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(TrackingColors.Muted).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = TrackingColors.Accent)
        } else {
            TrackingIconTile(
                Icons.Outlined.Map,
                tint = TrackingColors.Secondary,
                background = Color(0xFFE2E7EB),
                modifier = Modifier.size(52.dp),
            )
        }
        Text(
            title,
            color = TrackingColors.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (description != null) {
            Text(description, color = TrackingColors.Secondary, textAlign = TextAlign.Center)
        }
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackingColors.OrangeSurface,
                    contentColor = TrackingColors.OrangeText,
                ),
            ) { Text("Thử lại", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun TrackingDriverMarker(stale: Boolean, modifier: Modifier = Modifier) {
    val ink = if (stale) TrackingColors.Secondary else TrackingColors.OrangeText
    TrackingMarker(
        modifier = modifier,
        label = if (stale) "Vị trí cuối" else "Tài xế",
        fill = if (stale) TrackingColors.Muted else TrackingColors.OrangeSurface,
        border = if (stale) ink else TrackingColors.Accent,
        ink = ink,
        circular = true,
    ) { Icon(Icons.Outlined.TwoWheeler, contentDescription = null, tint = ink) }
}

@Composable
internal fun TrackingDeliveryMarker(modifier: Modifier = Modifier) {
    TrackingMarker(
        modifier = modifier,
        label = "Điểm giao",
        fill = TrackingColors.BlueSurface,
        border = TrackingColors.Blue,
        ink = TrackingColors.Blue,
    ) { Icon(Icons.Outlined.Home, contentDescription = null, tint = TrackingColors.Blue) }
}

@Composable
private fun TrackingMarker(
    modifier: Modifier,
    label: String,
    fill: Color,
    border: Color,
    ink: Color,
    circular: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(12.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(shape = shape, color = Color.White, shadowElevation = 2.dp) {
            Surface(
                modifier = Modifier.padding(3.dp).size(38.dp),
                shape = shape,
                color = fill,
                border = BorderStroke(1.5.dp, border),
            ) { Box(contentAlignment = Alignment.Center) { icon() } }
        }
        Surface(shape = RoundedCornerShape(6.dp), color = Color.White) {
            Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
                Text(label, color = ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
