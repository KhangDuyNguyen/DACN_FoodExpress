package vn.hutech.foodexpress.driver.ui.order

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DriverCurrentOrderCard(state: DriverCurrentOrderUiState, onRetry: () -> Unit, onOpenDetails: (() -> Unit)? = null,
    receivingOrdersOfflineConfirmed: Boolean = false) {
    val red = Color(0xFFE7192D)
    Surface(Modifier.fillMaxWidth().clickable(
        enabled = state.verified && !state.sessionExpired && state.error == null && state.order != null && onOpenDetails != null,
        onClickLabel = "Xem chi tiết đơn hàng", onClick = { onOpenDetails?.invoke() }),
        color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE8E8EC))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val order = state.order
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Đơn hàng hiện tại", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp,
                    color = Color(0xFF18202B))
                Surface(color = if (order != null && state.verified) Color(0xFFE5F7EB) else Color(0xFFF2F2F4),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(when {
                        order != null && state.verified -> "Đã nhận đơn"
                        order != null -> "Chưa cập nhật"
                        state.verified -> "Chưa có đơn"
                        else -> "Chưa xác minh"
                    }, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (order != null && state.verified) Color(0xFF087D32) else Color(0xFF505968))
                }
            }
            when {
                order != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Đơn ${order.orderCode}", color = Color(0xFF18202B), fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, lineHeight = 24.sp)
                        // Current Order only contains orders assigned to the authenticated driver.
                        // PLACED after Accept confirms ownership, not pickup or delivery progress.
                        val statusLabel = when (order.status) {
                            "PLACED" -> "Đã nhận đơn"
                            "PENDING_PAYMENT" -> "Chờ thanh toán"
                            "DELIVERED" -> "Giao hàng thành công"
                            "CANCELLED" -> "Đơn đã hủy"
                            "REJECTED" -> "Đơn đã bị từ chối"
                            else -> deliveryStatusLabel(order.status)
                        }
                        Text("Trạng thái: $statusLabel", fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF505968))
                    }
                    OrderSummaryAddress(true, "Điểm lấy hàng", order.restaurantName)
                    OrderSummaryAddress(false, "Điểm giao hàng", order.deliveryAddress)
                    if (!state.verified) Text(
                        listOfNotNull("Thông tin đơn từ lần cập nhật trước.", state.error).joinToString(" "),
                        color = Color(0xFF505968), fontSize = 13.sp, lineHeight = 18.sp)
                    if (state.verified && !state.sessionExpired && state.error == null && onOpenDetails != null)
                        FilledTonalButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFFF0F2), contentColor = red)) {
                            Text("Xem chi tiết đơn hàng", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                        }
                }
                state.verified -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmptyOrderIllustration()
                    Text("Chưa có đơn hàng", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (receivingOrdersOfflineConfirmed)
                        "Bạn đang tạm ngưng nhận đơn. Bật lại khi muốn nhận đơn mới."
                        else "Lời mời mới sẽ hiển thị khi bạn đủ điều kiện nhận đơn.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF505968), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> {
                    Text("Chưa xác minh được đơn hàng", fontWeight = FontWeight.SemiBold, color = red)
                    Text(state.error ?: "Cần cập nhật trước khi xác nhận bạn có đơn hay không.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFF505968))
                }
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = red)
                Text("Đang cập nhật đơn hàng…", fontSize = 12.sp)
            }
            // Unverified branches already explain the failure once, without classifying every error as Internet loss.
            if (state.verified) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.loading && !state.verified) TextButton(onClick = onRetry) { Text("Thử cập nhật lại", color = red) }
        }
    }
}

@Composable
private fun OrderSummaryAddress(pickup: Boolean, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(color = Color(0xFFFFF0F2), shape = RoundedCornerShape(10.dp)) {
            Canvas(Modifier.padding(6.dp).size(24.dp)) {
                val w = size.width; val h = size.height
                val red = Color(0xFFE7192D)
                val stroke = Stroke(1.8.dp.toPx())
                if (pickup) {
                    drawPath(Path().apply {
                        moveTo(w*.12f,h*.39f); lineTo(w*.22f,h*.15f); lineTo(w*.78f,h*.15f)
                        lineTo(w*.88f,h*.39f); close()
                    }, red, style = stroke)
                    drawRect(red, Offset(w*.22f,h*.4f), Size(w*.56f,h*.45f), style = stroke)
                    drawRect(red, Offset(w*.43f,h*.56f), Size(w*.18f,h*.29f), style = stroke)
                } else {
                    drawPath(Path().apply {
                        moveTo(w*.5f,h*.9f)
                        cubicTo(w*.08f,h*.45f,w*.16f,h*.1f,w*.5f,h*.1f)
                        cubicTo(w*.84f,h*.1f,w*.92f,h*.45f,w*.5f,h*.9f); close()
                    }, red, style = stroke)
                    drawCircle(red, w*.1f, Offset(w*.5f,h*.36f), style = stroke)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, color = Color(0xFF505968), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Color(0xFF18202B), fontSize = 14.sp, lineHeight = 20.sp,
                fontWeight = if (pickup) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun EmptyOrderIllustration() {
    Canvas(Modifier.size(56.dp)) {
        val w = size.width
        val h = size.height
        drawPath(Path().apply {
            moveTo(w*.25f,h*.42f); lineTo(w*.5f,h*.55f); lineTo(w*.75f,h*.42f)
            lineTo(w*.75f,h*.76f); lineTo(w*.5f,h*.91f); lineTo(w*.25f,h*.76f); close()
        }, Color(0xFFB8C2D2))
        drawPath(Path().apply {
            moveTo(w*.5f,h*.25f); lineTo(w*.25f,h*.4f); lineTo(w*.08f,h*.28f)
            lineTo(w*.34f,h*.12f); close()
        }, Color(0xFFD5DBE5))
        drawPath(Path().apply {
            moveTo(w*.5f,h*.25f); lineTo(w*.75f,h*.4f); lineTo(w*.92f,h*.28f)
            lineTo(w*.66f,h*.12f); close()
        }, Color(0xFFE5E9F0))
        drawLine(Color(0xFF96A4BA), Offset(w*.5f,h*.55f), Offset(w*.5f,h*.88f), 1.dp.toPx())
    }
}
