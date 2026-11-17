package vn.hutech.foodexpress.customer.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import vn.hutech.foodexpress.customer.orders.data.api.OrderSummary
import vn.hutech.foodexpress.customer.orders.data.api.ReviewResponse

private val Orange = Color(0xFFFF6B35)

// ── Status label ─────────────────────────────────────────────────────────

private fun statusLabel(status: String) = when (status) {
    "PLACED" -> "Chờ xác nhận"
    "PREPARING" -> "Đang chuẩn bị"
    "READY_FOR_PICKUP" -> "Chờ lấy món"
    "DELIVERING" -> "Đang giao"
    "DELIVERED" -> "Hoàn thành"
    "CANCELLED" -> "Đã hủy"
    else -> status
}

private fun statusColor(status: String) = when (status) {
    "DELIVERED" -> Color(0xFF2E7D32) to Color(0xFFE8F5E9)
    "CANCELLED" -> Color(0xFFC62828) to Color(0xFFFFEBEE)
    "DELIVERING" -> Color(0xFF1565C0) to Color(0xFFE3F2FD)
    else -> Color(0xFFE65100) to Color(0xFFFFF3E0)
}

// ── Star Rating ───────────────────────────────────────────────────────────

@Composable
private fun StarRating(value: Int, onChange: (Int) -> Unit, label: String) {
    Column {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Row {
            (1..5).forEach { star ->
                TextButton(
                    onClick = { onChange(star) },
                    contentPadding = PaddingValues(4.dp),
                ) {
                    Text(
                        "★",
                        fontSize = 28.sp,
                        color = if (star <= value) Color(0xFFFFC107) else Color(0xFFE0E0E0),
                    )
                }
            }
        }
    }
}

// ── Review Dialog ─────────────────────────────────────────────────────────

@Composable
fun ReviewDialog(
    orderId: Long,
    restaurantName: String,
    onDismiss: () -> Unit,
    viewModel: MyOrdersViewModel,
) {
    val reviewState by viewModel.reviewState.collectAsState()
    var restaurantRating by remember { mutableIntStateOf(5) }
    var driverRating by remember { mutableIntStateOf(5) }
    var comment by remember { mutableStateOf("") }

    LaunchedEffect(orderId) { viewModel.loadReview(orderId) }

    Dialog(onDismissRequest = { viewModel.resetReviewState(); onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            when (val state = reviewState) {
                is ReviewState.Loading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Orange) }

                is ReviewState.Loaded -> ExistingReviewContent(state.review, onDismiss = {
                    viewModel.resetReviewState(); onDismiss()
                })

                is ReviewState.Success -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Cảm ơn bạn đã đánh giá!", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.resetReviewState(); onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                    ) { Text("Đóng") }
                }

                else -> ReviewFormContent(
                    restaurantName = restaurantName,
                    restaurantRating = restaurantRating,
                    driverRating = driverRating,
                    comment = comment,
                    isSubmitting = state is ReviewState.Submitting,
                    error = (state as? ReviewState.Error)?.msg,
                    onRestaurantRating = { restaurantRating = it },
                    onDriverRating = { driverRating = it },
                    onComment = { comment = it },
                    onSubmit = {
                        viewModel.submitReview(orderId, restaurantRating, driverRating, comment)
                    },
                    onCancel = { viewModel.resetReviewState(); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun ExistingReviewContent(review: ReviewResponse, onDismiss: () -> Unit) {
    Column {
        Text("Đánh giá của bạn", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row {
            Text("Quán: ", fontSize = 13.sp, color = Color.Gray)
            Text("★".repeat(review.restaurantRating), color = Color(0xFFFFC107), fontSize = 16.sp)
        }
        review.driverRating?.let {
            Row {
                Text("Tài xế: ", fontSize = 13.sp, color = Color.Gray)
                Text("★".repeat(it), color = Color(0xFFFFC107), fontSize = 16.sp)
            }
        }
        review.comment?.let {
            Spacer(Modifier.height(4.dp))
            Text("\"$it\"", fontSize = 13.sp, color = Color(0xFF555555), fontWeight = FontWeight.Normal)
        }
        review.reply?.let { reply ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Column {
                    Text("💬 Phản hồi từ quán", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Orange)
                    Text(reply.content, fontSize = 13.sp, color = Color(0xFF555555))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
        ) { Text("Đóng", color = Color.DarkGray) }
    }
}

@Composable
private fun ReviewFormContent(
    restaurantName: String,
    restaurantRating: Int,
    driverRating: Int,
    comment: String,
    isSubmitting: Boolean,
    error: String?,
    onRestaurantRating: (Int) -> Unit,
    onDriverRating: (Int) -> Unit,
    onComment: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Text("Đánh giá đơn hàng", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("🏪 $restaurantName", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        StarRating(restaurantRating, onRestaurantRating, "Chất lượng quán *")
        Spacer(Modifier.height(8.dp))
        StarRating(driverRating, onDriverRating, "Tài xế giao hàng")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = onComment,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Nhận xét (không bắt buộc)...", fontSize = 13.sp) },
            minLines = 2,
            maxLines = 4,
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, fontSize = 12.sp, color = Color.Red)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) { Text(if (isSubmitting) "Đang gửi..." else "Gửi đánh giá") }
            TextButton(onClick = onCancel) { Text("Hủy") }
        }
    }
}

// ── Main Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyOrdersScreen(
    onBack: () -> Unit,
    viewModel: MyOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var reviewingOrder by remember { mutableStateOf<OrderSummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đơn hàng của tôi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8F9FA),
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Orange) }

            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("😕", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "Lỗi kết nối", color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.loadPage(state.page) },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange),
                ) { Text("Thử lại") }
            }

            state.orders.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🧾", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("Chưa có đơn hàng nào", color = Color.Gray)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.orders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        onReviewClick = { reviewingOrder = order },
                    )
                }

                // Pagination
                if (state.totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Button(
                                onClick = { viewModel.loadPage(state.page - 1) },
                                enabled = state.page > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                            ) { Text("← Trước", color = Color.DarkGray) }
                            Text(
                                "${state.page + 1} / ${state.totalPages}",
                                modifier = Modifier.align(Alignment.CenterVertically),
                                color = Color.Gray,
                            )
                            Button(
                                onClick = { viewModel.loadPage(state.page + 1) },
                                enabled = state.page + 1 < state.totalPages,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                            ) { Text("Sau →", color = Color.DarkGray) }
                        }
                    }
                }
            }
        }
    }

    // Review dialog
    reviewingOrder?.let { order ->
        ReviewDialog(
            orderId = order.id,
            restaurantName = order.restaurantName,
            onDismiss = { reviewingOrder = null },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun OrderCard(order: OrderSummary, onReviewClick: () -> Unit) {
    val (textColor, bgColor) = statusColor(order.status)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        order.restaurantName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(order.code, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(bgColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(statusLabel(order.status), fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${order.itemCount} phần · ${String.format("%,.0f", order.totalAmount)}đ",
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
                if (order.status == "DELIVERED") {
                    Button(
                        onClick = onReviewClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("⭐ Đánh giá", fontSize = 12.sp, color = Orange)
                    }
                }
            }
        }
    }
}
