package vn.hutech.foodexpress.driver.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DriverCurrentOrderCard(state: DriverCurrentOrderUiState, onRetry: () -> Unit, onOpenDetails: (() -> Unit)? = null) {
    val red = Color(0xFFE7192D)
    Surface(Modifier.fillMaxWidth().clickable(
        enabled = state.verified && !state.sessionExpired && state.error == null && state.order != null && onOpenDetails != null,
        onClickLabel = "Xem chi tiết đơn hàng", onClick = { onOpenDetails?.invoke() }),
        color = Color(0xFFFFF0F2), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val order = state.order
            when {
                order != null -> {
                    Text("Đơn ${order.orderCode}", color = red, fontWeight = FontWeight.Bold)
                    Text("Trạng thái: ${order.status}")
                    Text(order.restaurantName, fontWeight = FontWeight.SemiBold)
                    Text("Địa chỉ giao: ${order.deliveryAddress}")
                    if (!state.verified) Text("Dữ liệu lần đồng bộ trước; đang cần xác minh lại.")
                    if (state.verified && !state.sessionExpired && state.error == null && onOpenDetails != null)
                        TextButton(onClick = onOpenDetails) { Text("Xem chi tiết đơn hàng", color = red) }
                }
                state.verified -> Text("Không có đơn đang thực hiện", fontWeight = FontWeight.SemiBold)
                else -> Text("Chưa xác minh được đơn hiện tại", fontWeight = FontWeight.SemiBold)
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = red)
                Text("Đang đồng bộ đơn hiện tại…")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.loading && !state.verified) TextButton(onClick = onRetry) { Text("Thử đồng bộ lại", color = red) }
        }
    }
}
