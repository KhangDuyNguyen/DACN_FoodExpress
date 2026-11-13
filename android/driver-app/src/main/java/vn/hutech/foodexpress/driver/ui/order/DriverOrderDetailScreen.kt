package vn.hutech.foodexpress.driver.ui.order

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

// Filtering at render/action boundaries also protects the frame before session-clear effects run.
fun DriverCurrentOrderUiState.forDriver(authenticatedUserId: Long?): DriverCurrentOrderUiState =
    if (authenticatedUserId != null && authenticatedUserId > 0 && ownerId == authenticatedUserId && !sessionExpired) this
    else DriverCurrentOrderUiState()

private val DetailRed = Color(0xFFE7192D)
private val DetailInk = Color(0xFF18202B)
private val DetailMuted = Color(0xFF505968)
private val DetailPink = Color(0xFFFFF0F2)

// Dashboard entry back into an in-flight/uncertain command, even when currentOrder is unverified or null.
@Composable
fun DriverDeliveryRecoveryAction(state: DriverCurrentOrderUiState, authenticatedUserId: Long?, onOpen: () -> Unit) {
    val visible = state.forDriver(authenticatedUserId)
    if (visible.deliveryPending != null || visible.completion != null) {
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(if (visible.completion != null) "Xem xác nhận giao hàng" else "Kiểm tra thao tác giao hàng", color = DetailRed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrderDetailScreen(state: DriverCurrentOrderUiState, authenticatedUserId: Long?,
    onBack: () -> Unit, onRetry: () -> Unit, onDirections: (OrderDirectionsTarget) -> MapsOpenResult,
    onDelivery: ((String) -> Unit)? = null, onRetryDelivery: (() -> Unit)? = null) {
    DetailStatusBar()
    val visible = state.forDriver(authenticatedUserId)
    // A cached, unverified order is not a fresh authorization to display recipient details or navigate.
    val order = visible.order.takeIf { visible.verified && visible.error == null }
    val completion = visible.completion
    val target = order?.let { nextDeliveryStatus(it.status) }
    var confirm by remember(authenticatedUserId, order?.orderId) { mutableStateOf<String?>(null) }
    LaunchedEffect(order?.status, confirm) {
        if (confirm != null && order?.let { nextDeliveryStatus(it.status) } != confirm) confirm = null
    }
    var mapsMessage by remember(authenticatedUserId, order?.orderId, visible.verified) { mutableStateOf<String?>(null) }
    val snackbar = remember(authenticatedUserId, order?.orderId) { SnackbarHostState() }
    LaunchedEffect(mapsMessage, authenticatedUserId, order?.orderId) {
        mapsMessage?.let { snackbar.showSnackbar(it); mapsMessage = null }
    }
    val scroll = rememberScrollState()
    Scaffold(containerColor = Color(0xFFFFFBFC), snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (completion == null && (target != null || visible.deliveryPending != null)) {
            Surface(color = Color.White, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.deliverySending) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = DetailRed)
                        Text("Đang xác nhận với hệ thống…", color = DetailMuted)
                    }
                    val pending = visible.deliveryPending
                    if (pending != null && !visible.deliverySending) {
                        Text("Chưa rõ kết quả thao tác. Gửi lại chỉ xác minh hoặc thực hiện đúng thao tác đã chọn, không bỏ qua bước.", color = DetailMuted)
                        OutlinedButton(onClick = { onRetryDelivery?.invoke() }, modifier = Modifier.fillMaxWidth(),
                            enabled = onRetryDelivery != null && !visible.loading) { Text("Gửi lại thao tác đã chọn") }
                    } else if (target != null) {
                        Button(onClick = {
                            if (target == "DELIVERING") onDelivery?.invoke(target) else confirm = target
                        }, modifier = Modifier.fillMaxWidth(), enabled = !visible.deliverySending && onDelivery != null,
                            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = DetailRed)) {
                            Text(deliveryActionLabel(target), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }, topBar = {
        TopAppBar(title = { Text("Chi tiết đơn hàng", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Quay lại Trang chủ" }) {
                Text("‹", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DetailRed, titleContentColor = Color.White))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DetailRed)
            visible.deliveryMessage?.let { Text(it, color = DetailMuted) }
            when {
                completion != null -> {
                    DetailCard {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Canvas(Modifier.size(88.dp).semantics { contentDescription = "Đã hoàn thành giao hàng" }) {
                                drawCircle(Color(0xFF14853D))
                                drawLine(Color.White, Offset(size.width * .25f, size.height * .50f),
                                    Offset(size.width * .43f, size.height * .68f), 9.dp.toPx())
                                drawLine(Color.White, Offset(size.width * .43f, size.height * .68f),
                                    Offset(size.width * .76f, size.height * .32f), 9.dp.toPx())
                            }
                            Text("Giao hàng thành công!", color = DetailInk,
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Đơn ${completion.orderCode}", color = DetailInk, fontWeight = FontWeight.Bold)
                            Text("Đã được hệ thống xác nhận.", color = DetailMuted)
                            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = DetailRed)) { Text("Quay về Trang chủ") }
                        }
                    }
                    DeliveryProgress("DELIVERED")
                }
                order != null -> {
                    DetailCard {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(order.orderCode, color = DetailInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Surface(color = deliveryBadgeBackground(order.status), shape = RoundedCornerShape(20.dp)) {
                                Text(deliveryStatusLabel(order.status), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = deliveryStatusColor(order.status), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    DeliveryProgress(order.status)
                    if (order.status == "PLACED") DetailCard {
                        Text("Đơn chưa ở bước có thể xác nhận lấy món. Vui lòng cập nhật lại trạng thái.", color = DetailMuted)
                        OutlinedButton(onClick = onRetry, enabled = !visible.loading) { Text("Cập nhật lại đơn") }
                    }
                    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailIcon(DetailSymbol.TIME, Modifier.padding(top = 2.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Thông tin đơn hàng", color = DetailInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("Thời gian đặt: ${order.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}",
                                    color = DetailMuted, style = MaterialTheme.typography.bodyMedium)
                                Text("Cập nhật: ${order.updatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}",
                                    color = DetailMuted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    val pickup: @Composable () -> Unit = { DetailCard {
                        DetailTitle("Điểm lấy hàng (Nhà hàng)", DetailSymbol.RESTAURANT)
                        Text(order.restaurantName, color = DetailInk, fontWeight = FontWeight.Bold)
                        DetailInfo(DetailSymbol.LOCATION, order.restaurantAddress)
                        order.restaurantPhone?.takeIf { it.isNotBlank() }?.let { DetailInfo(DetailSymbol.PHONE, "Điện thoại quán: $it") }
                        DirectionButton("Chỉ đường đến nhà hàng", !visible.loading && !visible.deliverySending) {
                            mapsMessage = onDirections(OrderDirectionsTarget.RESTAURANT).message
                        }
                    } }
                    val destination: @Composable () -> Unit = { DetailCard {
                        DetailTitle("Điểm giao hàng (Khách hàng)", DetailSymbol.PERSON)
                        DetailText("Người nhận", order.receiverName)
                        DetailInfo(DetailSymbol.PHONE, "Điện thoại: ${order.receiverPhone}")
                        DetailInfo(DetailSymbol.LOCATION, order.deliveryAddress)
                        order.note?.takeIf { it.isNotBlank() }?.let { DetailText("Ghi chú", it) }
                        DirectionButton("Chỉ đường đến khách hàng", !visible.loading && !visible.deliverySending) {
                            mapsMessage = onDirections(OrderDirectionsTarget.CUSTOMER).message
                        }
                    } }
                    if (order.status in setOf("PICKED_UP", "DELIVERING")) { destination(); pickup() }
                    else { pickup(); destination() }
                    DetailCard {
                        DetailTitle("Thanh toán đơn hàng")
                        Text("Thông tin thanh toán chưa khả dụng.", color = DetailMuted)
                    }
                    DetailCard {
                        DetailTitle("Danh sách món ăn")
                        Text("Thông tin món ăn chưa khả dụng.", color = DetailMuted)
                    }
                    Text("Thông tin đơn hàng được đồng bộ từ hệ thống Food Express.", color = DetailMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
                visible.verified && visible.error == null -> DetailCard {
                    DetailTitle("Không có đơn đang thực hiện")
                    Text("Vui lòng trở về Trang chủ để xem trạng thái hiện tại.", color = DetailMuted)
                }
                else -> DetailCard {
                    DetailTitle("Chưa xác minh được đơn hiện tại")
                    Text(visible.error ?: "Vui lòng chờ đồng bộ hoặc thử lại trước khi xem chi tiết và chỉ đường.", color = DetailMuted)
                    if (!visible.loading) Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = DetailRed)) {
                        Text("Thử đồng bộ lại")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    confirm?.let { action ->
        AlertDialog(onDismissRequest = { confirm = null }, title = {
            DetailStatusBar()
            Text(if (action == "PICKED_UP") "Bạn đã thực sự nhận món từ nhà hàng chưa?" else "Xác nhận giao hàng thành công?")
        }, text = {
            Text(if (action == "PICKED_UP") "Chỉ xác nhận sau khi bạn đã nhận món thực tế từ nhà hàng."
                else "Chỉ xác nhận sau khi bạn đã bàn giao đơn hàng cho người nhận.")
        }, confirmButton = {
            // Fixed, shared action order at every font scale; no automatic row/column reversal.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    confirm = null
                    if (!visible.deliverySending && order != null && nextDeliveryStatus(order.status) == action) onDelivery?.invoke(action)
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = DetailRed)) {
                    Text(if (action == "PICKED_UP") "Xác nhận đã lấy món" else "Xác nhận giao thành công")
                }
                OutlinedButton(onClick = { confirm = null }, modifier = Modifier.fillMaxWidth()) { Text("Hủy", color = DetailRed) }
            }
        })
    }
}

private fun deliveryActionLabel(target: String) = when (target) {
    "PICKED_UP" -> "Đã lấy món"
    "DELIVERING" -> "Bắt đầu giao hàng"
    else -> "Xác nhận đã giao"
}

internal fun deliveryStatusLabel(status: String) = when (status) {
    "PREPARING" -> "Nhà hàng đang chuẩn bị món"
    "READY_FOR_PICKUP" -> "Món đã sẵn sàng"
    "PICKED_UP" -> "Đã lấy món"
    "DELIVERING" -> "Đang giao hàng"
    else -> status
}

private fun deliveryStatusColor(status: String) = when (status) {
    "PREPARING" -> Color(0xFF8A5700)
    "READY_FOR_PICKUP" -> Color(0xFF087D32)
    "PICKED_UP" -> Color(0xFF175DA8)
    "DELIVERING" -> Color(0xFF7130A3)
    else -> DetailMuted
}
private fun deliveryBadgeBackground(status: String) = when (status) {
    "PREPARING" -> Color(0xFFFFF1D6)
    "READY_FOR_PICKUP" -> Color(0xFFE9F8ED)
    "PICKED_UP" -> Color(0xFFE9F2FF)
    "DELIVERING" -> Color(0xFFF3EAFE)
    else -> DetailPink
}

@Composable
private fun DeliveryProgress(status: String) {
    val step = when (status) { "PREPARING", "READY_FOR_PICKUP" -> 0; "PICKED_UP" -> 1; "DELIVERING" -> 2; "DELIVERED" -> 3; else -> -1 }
    DetailCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Chuẩn bị", "Đã lấy", "Đang giao", "Hoàn thành").forEachIndexed { index, label ->
                val done = index < step || (status == "READY_FOR_PICKUP" && index == 0) || status == "DELIVERED"
                val color = if (done) Color(0xFF14853D) else if (index == step) deliveryStatusColor(status) else Color(0xFF697586)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(color)
                            if (done) {
                                drawLine(Color.White, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .43f, size.height * .68f), 2.dp.toPx())
                                drawLine(Color.White, Offset(size.width * .43f, size.height * .68f), Offset(size.width * .76f, size.height * .32f), 2.dp.toPx())
                            }
                        }
                        if (!done) Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DetailTitle(title: String, symbol: DetailSymbol? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (symbol != null) Surface(color = DetailPink, shape = RoundedCornerShape(10.dp)) {
            DetailIcon(symbol, Modifier.padding(7.dp))
        }
        Text(title, color = DetailInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailText(label: String, value: String) {
    Text("$label: $value", color = DetailInk)
}

@Composable
private fun DirectionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DetailPink, contentColor = DetailRed)) {
        DetailIcon(DetailSymbol.DIRECTIONS)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailInfo(symbol: DetailSymbol, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailIcon(symbol, Modifier.padding(top = 2.dp))
        Text(text, color = DetailInk, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailIcon(symbol: DetailSymbol, modifier: Modifier = Modifier) {
    Icon(symbol.vector, contentDescription = null, modifier = modifier.size(22.dp), tint = DetailRed)
}

// Local vector paths: no raster assets, emoji, icon font or additional dependency.
private enum class DetailSymbol(path: String) {
    RESTAURANT("M4,10 L4,21 L20,21 L20,10 M3,10 L5,3 L19,3 L21,10 M3,10 Q5,14 7,10 Q9,14 12,10 Q15,14 17,10 Q19,14 21,10 M9,21 L9,15 L15,15 L15,21"),
    LOCATION("M12,22 C9,18 4,14 4,9 C4,-1 20,-1 20,9 C20,14 15,18 12,22 M15,9 C15,13 9,13 9,9 C9,5 15,5 15,9 Z"),
    PERSON("M16,7 C16,13 8,13 8,7 C8,1 16,1 16,7 Z M4,22 L4,19 C4,11 20,11 20,19 L20,22 Z"),
    TIME("M22,12 C22,25 2,25 2,12 C2,-1 22,-1 22,12 Z M12,6 L12,12 L16,14"),
    PHONE("M7,3 L4,3 Q2,3 3,8 Q6,19 17,21 Q21,22 21,18 L21,16 L16,14 L14,17 Q9,15 7,10 L10,8 Z"),
    DIRECTIONS("M3,10 L21,3 L14,21 L11,13 Z M11,13 L16,8");

    val vector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}

@Suppress("DEPRECATION") // Scoped to this screen/dialog; do not change the shared theme or other destinations.
@Composable
private fun DetailStatusBar() {
    val view = LocalView.current
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        val previousColor = window?.statusBarColor
        controller?.isAppearanceLightStatusBars = false
        window?.statusBarColor = android.graphics.Color.rgb(231, 25, 45)
        onDispose {
            if (wasLight != null) controller?.isAppearanceLightStatusBars = wasLight
            if (previousColor != null) window?.statusBarColor = previousColor
        }
    }
}
