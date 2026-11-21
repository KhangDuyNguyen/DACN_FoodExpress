package vn.hutech.foodexpress.customer.tracking

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import java.math.BigDecimal
import java.time.LocalDateTime

private const val OrderId = 1082L
private val ExampleDriver = TrackingLocation(10.7744, 106.7010, LocalDateTime.of(2026, 11, 19, 17, 40))
private val ExampleDelivery = TrackingMapPoint(10.7772, 106.7050)
private val EmulatorGpsPoints = listOf(
    TrackingMapPoint(ExampleDriver.lat, ExampleDriver.lng),
    TrackingMapPoint(10.775098, 106.701724),
    TrackingMapPoint(10.775542, 106.702374),
    TrackingMapPoint(10.77465, 106.70330), // One noisy fix before returning to the route.
    TrackingMapPoint(10.776151, 106.703049),
    TrackingMapPoint(10.77625, 106.70410),
    TrackingMapPoint(10.77620, 106.70420),
    TrackingMapPoint(10.77628, 106.70428),
)
private val ExampleDetails = TrackingOrderDetails(
    orderId = OrderId,
    driverName = "Nguyễn Minh",
    licensePlate = "59-X1 123.45",
    deliveryAddress = "Nguyễn Huệ, TP.HCM",
    totalAmount = BigDecimal("125000"),
)
private val ExampleOrder = OrderTrackingState(
    orderId = OrderId,
    orderCode = "FE-1082",
    status = "DELIVERING",
    latestLocation = ExampleDriver,
    connection = TrackingConnection.Connected,
    deliveryPoint = ExampleDelivery,
)
private val ExampleRoute = TrackingRouteUpdate.Ready(
    OrderId,
    listOf(
        TrackingMapPoint(ExampleDriver.lat, ExampleDriver.lng),
        TrackingMapPoint(ExampleDriver.lat, 106.7025),
        TrackingMapPoint(10.7768, 106.7025),
        TrackingMapPoint(10.7768, ExampleDelivery.lng),
        ExampleDelivery,
    ),
)

@Preview(name = "01 · Đang tải", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingLoadingPreview() = PreviewTracking(OrderTrackingState(OrderId, isLoading = true))

@Preview(name = "02 · Chờ GPS", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingWaitingPreview() = PreviewTracking(ExampleOrder.copy(latestLocation = null))

@Preview(name = "03 · Đang giao", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingDeliveringPreview() = PreviewTracking(ExampleOrder, route = ExampleRoute)

@Preview(name = "04 · Tài xế đổi lối", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingReroutingPreview() = PreviewTracking(
    ExampleOrder.copy(latestLocation = ExampleDriver.copy(lat = 10.7748, lng = 106.7031)),
    route = TrackingRouteUpdate.Updating(OrderId),
)

@Preview(name = "05 · Mất kết nối", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingDisconnectedPreview() = PreviewTracking(
    ExampleOrder.copy(connection = TrackingConnection.Disconnected),
    route = ExampleRoute,
)

@Preview(name = "06 · Hết phiên", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingSessionExpiredPreview() = PreviewTracking(ExampleOrder.copy(problem = TrackingProblem.SessionExpired))

@Preview(name = "07 · Bản đồ chưa tải", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingMapUnavailablePreview() = PreviewTracking(ExampleOrder, mapStatus = TrackingMapStatus.Unavailable)

@Preview(name = "08 · Không xem được đơn", widthDp = 392, heightDp = 812, showBackground = true)
@Composable
internal fun TrackingAccessDeniedPreview() = PreviewTracking(ExampleOrder.copy(problem = TrackingProblem.AccessDenied))

/** Device preview with the live basemap and a reproducible pair of Ho Chi Minh City pins. */
@Composable
internal fun TrackingVietmapEmulatorPreview() {
    var gpsIndex by remember { mutableIntStateOf(0) }
    var scenario by remember { mutableStateOf(EmulatorRouteScenario.Connected) }
    val gps = EmulatorGpsPoints[gpsIndex]
    val tracking = ExampleOrder.copy(
        latestLocation = TrackingLocation(gps.lat, gps.lng, ExampleDriver.recordedAt.plusSeconds(gpsIndex.toLong())),
        connection = if (scenario == EmulatorRouteScenario.Disconnected) {
            TrackingConnection.Disconnected
        } else {
            TrackingConnection.Connected
        },
    )
    val planner = remember { TrackingRoutePlanner(SystemClock::elapsedRealtime) }
    val display by planner.display.collectAsState()
    val failure = scenario == EmulatorRouteScenario.RouteFailed
    var previousFailure by remember { mutableStateOf(false) }
    LaunchedEffect(tracking, failure) {
        if (failure != previousFailure) {
            planner.clear()
            previousFailure = failure
        }
        planner.update(tracking)
    }
    LaunchedEffect(display.ticket) {
        val ticket = display.ticket ?: return@LaunchedEffect
        val waitMillis = ticket.startAt - SystemClock.elapsedRealtime()
        if (waitMillis > 0) delay(waitMillis)
        val request = planner.begin(ticket) ?: return@LaunchedEffect
        planner.finish(request, DebugTrackingRouteClient.fetch(request, failure))
    }
    var mapStatus by remember {
        mutableStateOf(
            if (VietmapTrackingResources.isConfigured) TrackingMapStatus.Loading else TrackingMapStatus.Unavailable,
        )
    }
    val mapContent: (@Composable (TrackingMapLayers) -> Unit)? = if (VietmapTrackingResources.isConfigured) {
        { layers ->
            VietmapTrackingMap(layers, onStatus = { mapStatus = it }, hideLogo = true) {
                Box(Modifier.fillMaxSize()) {
                    if (display.route == null && scenario != EmulatorRouteScenario.Disconnected) {
                        Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 72.dp)) {
                            TrackingPill(
                                label = if (display.failed) {
                                    "Chưa tải được tuyến"
                                } else {
                                    "Chưa có tuyến dự kiến"
                                },
                                ink = TrackingColors.Secondary,
                                fill = Color.White,
                            )
                        }
                    }
                    EmulatorRouteControls(
                        scenario = scenario,
                        onScenario = { scenario = it },
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 21.dp),
                    )
                    Button(
                        onClick = { gpsIndex++ },
                        enabled = scenario != EmulatorRouteScenario.Disconnected && gpsIndex < EmulatorGpsPoints.lastIndex,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 21.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = TrackingColors.OrangeText,
                        ),
                    ) { Text("Đổi GPS mô phỏng") }
                }
            }
        }
    } else {
        null
    }
    FoodExpressTheme {
        OrderTrackingScreen(
            state = tracking,
            onBack = {},
            onSignIn = {},
            details = ExampleDetails,
            route = display.route,
            mapStatus = mapStatus,
            onRetryMap = if (mapContent != null) { { mapStatus = TrackingMapStatus.Loading } } else null,
            mapContent = mapContent,
        )
    }
}

private enum class EmulatorRouteScenario(val label: String) {
    Connected("Kết nối bình thường"),
    RouteFailed("Lỗi tải tuyến"),
    Disconnected("Mất kết nối"),
}

@Composable
private fun EmulatorRouteControls(
    scenario: EmulatorRouteScenario,
    onScenario: (EmulatorRouteScenario) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TrackingColors.OrangeText),
        ) { Text("Ca kiểm thử") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EmulatorRouteScenario.entries.filter { it != scenario }.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    expanded = false
                    onScenario(option)
                })
            }
        }
    }
}

@Composable
private fun PreviewTracking(
    state: OrderTrackingState,
    route: TrackingRouteUpdate? = null,
    mapStatus: TrackingMapStatus = TrackingMapStatus.Ready,
) {
    FoodExpressTheme {
        OrderTrackingScreen(
            state = state,
            onBack = {},
            onSignIn = {},
            details = ExampleDetails,
            route = route,
            mapStatus = mapStatus,
            onRetryMap = {},
            mapContent = { ExampleMap(it) },
        )
    }
}

private fun TrackingMapPoint.previewX() = ((lng - 106.6990) / 0.0080).toFloat()
private fun TrackingMapPoint.previewY() = ((10.7785 - lat) / 0.0059).toFloat()

@Composable
private fun ExampleMap(layers: TrackingMapLayers) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFE4EBE2))
            val xEdges = listOf(0f, 0.14f, 0.25f, 0.4375f, 0.58f, 0.75f, 0.90f, 1f)
            val yEdges = listOf(0f, 0.14f, 0.2881f, 0.46f, 0.58f, 0.6949f, 0.82f, 0.94f, 1f)
            val parkCells = setOf(1 to 1, 2 to 4, 3 to 0, 4 to 2, 5 to 5)
            for (col in 0 until xEdges.lastIndex) {
                for (row in 0 until yEdges.lastIndex) {
                    val left = (xEdges[col] + 0.013f) * size.width
                    val top = (yEdges[row] + 0.016f) * size.height
                    val blockWidth = (xEdges[col + 1] - xEdges[col] - 0.026f) * size.width
                    val blockHeight = (yEdges[row + 1] - yEdges[row] - 0.032f) * size.height
                    val park = (col to row) in parkCells
                    drawRect(
                        color = if (park) Color(0xFFC5DFC3) else Color(0xFFD5E1D6),
                        topLeft = Offset(left, top),
                        size = Size(blockWidth, blockHeight),
                    )
                    if (!park && col < 6) {
                        drawRect(
                            color = Color(0xFFC9D8CD),
                            topLeft = Offset(left + blockWidth * 0.14f, top + blockHeight * 0.16f),
                            size = Size(blockWidth * 0.59f, blockHeight * 0.56f),
                        )
                    }
                }
            }
            for (x in xEdges.drop(1).dropLast(2)) {
                drawLine(Color.White, Offset(x * size.width, 0f), Offset(x * size.width, size.height), 6.5.dp.toPx())
            }
            for (y in yEdges.drop(1).dropLast(1)) {
                drawLine(Color.White, Offset(0f, y * size.height), Offset(size.width, y * size.height), 6.5.dp.toPx())
            }
            drawPath(
                Path().apply {
                    moveTo(size.width * 0.93f, 0f)
                    cubicTo(size.width * 0.77f, size.height * 0.33f, size.width * 1.04f, size.height * 0.49f, size.width * 0.91f, size.height * 0.73f)
                    quadraticTo(size.width * 0.86f, size.height * 0.87f, size.width, size.height)
                    lineTo(size.width, 0f)
                    close()
                },
                Color(0xFFBDDEEA),
            )
            if (layers.routePoints.isNotEmpty()) {
                val path = Path()
                layers.routePoints.forEachIndexed { index, point ->
                    val x = point.previewX() * size.width
                    val y = point.previewY() * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path,
                    TrackingColors.Blue,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
        }
        layers.deliveryPoint?.let { point ->
            TrackingDeliveryMarker(
                Modifier.offset(x = mapWidth * point.previewX() - 34.dp, y = mapHeight * point.previewY() - 22.dp),
            )
        }
        layers.driverPoint?.let { point ->
            TrackingDriverMarker(
                stale = layers.isDriverPointStale,
                modifier = Modifier.offset(x = mapWidth * point.previewX() - 28.dp, y = mapHeight * point.previewY() - 22.dp),
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            shape = RoundedCornerShape(5.dp),
            color = Color.White,
        ) {
            Text(
                "Sơ đồ minh họa",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                color = TrackingColors.Secondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
