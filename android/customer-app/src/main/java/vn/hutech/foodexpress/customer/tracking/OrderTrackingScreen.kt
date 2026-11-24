package vn.hutech.foodexpress.customer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

internal object TrackingColors {
    val Background = Color(0xFFF8F9FA)
    val Ink = Color(0xFF21252A)
    val Secondary = Color(0xFF57616D)
    val Border = Color(0xFFDFE3E7)
    val Accent = Color(0xFFFF6B35)
    val OrangeText = Color(0xFFA73510)
    val OrangeSurface = Color(0xFFFFEFE5)
    val Blue = Color(0xFF135EB5)
    val BlueSurface = Color(0xFFE3F1FD)
    val Green = Color(0xFF106348)
    val GreenSurface = Color(0xFFE7F5ED)
    val Gold = Color(0xFF754800)
    val GoldSurface = Color(0xFFFFF1D9)
    val Muted = Color(0xFFEFF2F3)
}

@Composable
internal fun OrderTrackingRoute(
    orderId: Long,
    accessTokens: StateFlow<String?>,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    details: TrackingOrderDetails? = null,
    route: TrackingRouteUpdate? = null,
    mapStatus: TrackingMapStatus = TrackingMapStatus.Unavailable,
    onRetryMap: (() -> Unit)? = null,
    mapContent: (@Composable (TrackingMapLayers) -> Unit)? = null,
    viewModel: OrderTrackingViewModel = hiltViewModel(),
) {
    val update by viewModel.state.collectAsStateWithLifecycle()
    val token by accessTokens.collectAsStateWithLifecycle()
    var retry by remember(orderId, accessTokens) { mutableIntStateOf(0) }
    LifecycleStartEffect(orderId, accessTokens, retry) {
        viewModel.start(orderId, accessTokens)
        onStopOrDispose { viewModel.stop() }
    }
    val currentToken = accessTokens.value
    val visibleState = if (currentToken.isNullOrBlank()) {
        OrderTrackingState(orderId, problem = TrackingProblem.SessionExpired)
    } else {
        update?.takeIf { currentToken == token }?.forSelection(orderId, currentToken)
            ?: OrderTrackingState(orderId, isLoading = true)
    }
    OrderTrackingScreen(
        state = visibleState,
        onBack = onBack,
        onSignIn = onSignIn,
        details = details,
        route = route,
        mapStatus = mapStatus,
        onRetryMap = onRetryMap,
        onRetryOrder = { retry++ },
        mapContent = mapContent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderTrackingScreen(
    state: OrderTrackingState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    details: TrackingOrderDetails? = null,
    route: TrackingRouteUpdate? = null,
    mapStatus: TrackingMapStatus = TrackingMapStatus.Unavailable,
    onRetryMap: (() -> Unit)? = null,
    onRetryOrder: (() -> Unit)? = null,
    mapContent: (@Composable (TrackingMapLayers) -> Unit)? = null,
) {
    Scaffold(
        containerColor = TrackingColors.Background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Theo dõi đơn hàng",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TrackingColors.Ink,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            TrackingIconTile(Icons.AutoMirrored.Filled.ArrowBack, description = "Quay lại")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
                HorizontalDivider(color = TrackingColors.Border)
            }
        },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val mapHeight = (maxHeight * 0.58f).coerceIn(280.dp, 560.dp)
            val messageHeight = (maxHeight - 28.dp).coerceAtLeast(260.dp)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val content = state.toContent(details, route)) {
                    OrderTrackingContent.Loading -> {
                        OrderHeader(null)
                        TrackingMapPlaceholder(
                            height = mapHeight,
                            title = "Đang tải thông tin đơn…",
                            isLoading = true,
                        )
                        OrderDetailsCard(null, isLoading = true)
                    }
                    OrderTrackingContent.SessionExpired -> PrivateOrderMessage(
                        height = messageHeight,
                        title = "Phiên đăng nhập đã hết",
                        description = "Đăng nhập lại để tiếp tục theo dõi.",
                        action = "Đăng nhập lại",
                        onAction = onSignIn,
                    )
                    OrderTrackingContent.OrderDenied -> PrivateOrderMessage(
                        height = messageHeight,
                        title = "Không xem được đơn",
                        description = "Đơn không tồn tại hoặc không thuộc tài khoản.",
                        action = "Về danh sách đơn",
                        onAction = onBack,
                    )
                    OrderTrackingContent.LoadFailed -> PrivateOrderMessage(
                        height = messageHeight,
                        title = "Chưa tải được thông tin đơn",
                        description = "Kiểm tra kết nối rồi thử lại.",
                        action = "Thử lại",
                        onAction = onRetryOrder,
                        icon = Icons.Outlined.Description,
                    )
                    is OrderTrackingContent.Order -> {
                        OrderHeader(content)
                        OrderTrackingMap(content, mapHeight, mapStatus, onRetryMap, mapContent)
                        OrderDetailsCard(content.details)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHeader(order: OrderTrackingContent.Order?) {
    val shape = RoundedCornerShape(16.dp)
    Surface(shape = shape, color = Color.White, border = BorderStroke(1.dp, Color(0xFFF2DFD3))) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFFFFF3EB), Color.White)))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackingIconTile(Icons.Outlined.Description, background = Color(0xFFFFE4D2))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (order == null) {
                    SkeletonBar(78, 8)
                    SkeletonBar(112, 11)
                } else {
                    OrderCaption("Mã đơn hàng")
                    Text(
                        "#${order.orderCode.removePrefix("#")}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TrackingColors.Ink,
                    )
                }
            }
            if (order?.status != null) OrderStatusPill(order.status)
        }
    }
}

@Composable
private fun OrderStatusPill(status: String) {
    val label = when (status) {
        "PLACED" -> "Chờ xác nhận"
        "PREPARING" -> "Đang chuẩn bị"
        "READY_FOR_PICKUP" -> "Chờ lấy món"
        "DELIVERING" -> "Đang giao"
        "DELIVERED" -> "Đã giao"
        "CANCELLED" -> "Đã hủy"
        else -> "Đang xử lý"
    }
    val (ink, fill) = when (status) {
        "DELIVERING" -> TrackingColors.Blue to TrackingColors.BlueSurface
        "DELIVERED" -> TrackingColors.Green to TrackingColors.GreenSurface
        "CANCELLED" -> TrackingColors.OrangeText to TrackingColors.OrangeSurface
        else -> TrackingColors.Gold to TrackingColors.GoldSurface
    }
    TrackingPill(label, ink, fill)
}

@Composable
private fun OrderDetailsCard(details: TrackingOrderDetails?, isLoading: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, TrackingColors.Border),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackingIconTile(Icons.Outlined.PersonOutline, circular = true)
                Column(Modifier.weight(1f)) {
                    OrderValue("Tài xế giao hàng", details?.driverName, isLoading)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isLoading) {
                        SkeletonBar(76, 9)
                    } else {
                        OrderCaption("Biển số")
                        Surface(color = TrackingColors.Muted, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                details?.licensePlate?.takeIf { it.isNotBlank() } ?: "—",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TrackingColors.Ink,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = TrackingColors.Border)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackingIconTile(Icons.Outlined.LocationOn, TrackingColors.Blue, TrackingColors.BlueSurface)
                Column(Modifier.weight(1f)) {
                    OrderValue("Địa chỉ giao", details?.deliveryAddress, isLoading)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (isLoading) {
                        SkeletonBar(76, 9)
                    } else {
                        OrderCaption("Tổng tiền đơn")
                        Text(
                            details?.totalAmount?.let(::formatVnd) ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TrackingColors.OrangeText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderValue(label: String, value: String?, isLoading: Boolean) {
    if (isLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(68, 7)
            SkeletonBar(96, 9)
        }
    } else {
        OrderCaption(label)
        val text = value?.takeIf { it.isNotBlank() }
        Text(
            text ?: "Chưa có thông tin",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (text == null) FontWeight.Normal else FontWeight.Bold,
            color = if (text == null) TrackingColors.Secondary else TrackingColors.Ink,
        )
    }
}

@Composable
private fun OrderCaption(text: String) {
    Text(text, color = TrackingColors.Secondary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SkeletonBar(width: Int, height: Int) {
    Box(Modifier.width(width.dp).height(height.dp).background(TrackingColors.Border, CircleShape))
}

private fun formatVnd(value: BigDecimal): String {
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
    return "${number.format(value)} đ"
}

@Composable
private fun PrivateOrderMessage(
    height: Dp,
    title: String,
    description: String,
    action: String,
    onAction: (() -> Unit)?,
    icon: ImageVector = Icons.Outlined.Lock,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, TrackingColors.Border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = height).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrackingIconTile(icon, modifier = Modifier.size(74.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TrackingColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(8.dp))
            Text(description, color = TrackingColors.Secondary, textAlign = TextAlign.Center)
            if (onAction != null) {
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrackingColors.OrangeSurface,
                        contentColor = TrackingColors.OrangeText,
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFCEB9)),
                    shape = RoundedCornerShape(12.dp),
                ) { Text(action, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun TrackingIconTile(
    icon: ImageVector,
    tint: Color = TrackingColors.OrangeText,
    background: Color = TrackingColors.OrangeSurface,
    modifier: Modifier = Modifier.size(40.dp),
    description: String? = null,
    circular: Boolean = false,
) {
    Box(
        modifier.background(background, if (circular) CircleShape else RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

@Composable
internal fun TrackingPill(label: String, ink: Color, fill: Color) {
    Surface(color = fill, shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(ink, CircleShape))
            Text(label, color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
