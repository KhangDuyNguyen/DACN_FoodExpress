package vn.hutech.foodexpress.driver.ui.order

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val DetailMuted = Color(0xFF667085)
private val DetailPink = Color(0xFFFFF0F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverOrderDetailScreen(state: DriverCurrentOrderUiState, authenticatedUserId: Long?,
    onBack: () -> Unit, onRetry: () -> Unit, onDirections: (OrderDirectionsTarget) -> MapsOpenResult) {
    val visible = state.forDriver(authenticatedUserId)
    // A cached, unverified order is not a fresh authorization to display recipient details or navigate.
    val order = visible.order.takeIf { visible.verified && visible.error == null }
    var mapsMessage by remember(authenticatedUserId, order?.orderId, visible.verified) { mutableStateOf<String?>(null) }
    val snackbar = remember(authenticatedUserId, order?.orderId) { SnackbarHostState() }
    LaunchedEffect(mapsMessage, authenticatedUserId, order?.orderId) {
        mapsMessage?.let { snackbar.showSnackbar(it); mapsMessage = null }
    }
    val scroll = rememberScrollState()
    Scaffold(containerColor = Color(0xFFFFFBFC), snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text("Chi tiết đơn hàng", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Quay lại Trang chủ" }) {
                Text("‹", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = DetailRed, titleContentColor = Color.White))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (visible.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DetailRed)
            when {
                order != null -> {
                    DetailCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(order.orderCode, color = DetailInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Đơn hàng hiện tại", color = DetailMuted)
                            }
                            Surface(color = DetailPink, shape = RoundedCornerShape(20.dp)) {
                                Text(order.status, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = DetailRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    DetailCard {
                        DetailTitle("Thông tin đơn hàng")
                        DetailText("Thời gian đặt", order.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                        DetailText("Cập nhật", order.updatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    }
                    DetailCard {
                        DetailTitle("Điểm lấy hàng (Nhà hàng)")
                        Text(order.restaurantName, color = DetailInk, fontWeight = FontWeight.Bold)
                        Text(order.restaurantAddress, color = DetailMuted)
                        order.restaurantPhone?.takeIf { it.isNotBlank() }?.let { DetailText("Điện thoại quán", it) }
                        DirectionButton("Chỉ đường đến nhà hàng", !visible.loading) {
                            mapsMessage = onDirections(OrderDirectionsTarget.RESTAURANT).message
                        }
                    }
                    DetailCard {
                        DetailTitle("Điểm giao hàng (Khách hàng)")
                        DetailText("Người nhận", order.receiverName)
                        DetailText("Điện thoại", order.receiverPhone)
                        Text(order.deliveryAddress, color = DetailMuted)
                        order.note?.takeIf { it.isNotBlank() }?.let { DetailText("Ghi chú", it) }
                        DirectionButton("Chỉ đường đến khách hàng", !visible.loading) {
                            mapsMessage = onDirections(OrderDirectionsTarget.CUSTOMER).message
                        }
                    }
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
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DetailTitle(title: String) = Text(title, color = DetailInk,
    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun DetailText(label: String, value: String) {
    Text("$label: $value", color = DetailInk)
}

@Composable
private fun DirectionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DetailPink, contentColor = DetailRed)) {
        Text("↗  $label", fontWeight = FontWeight.Bold)
    }
}
