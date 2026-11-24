package vn.hutech.foodexpress.driver.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import vn.hutech.foodexpress.driver.data.location.DriverGpsStatus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.data.profile.DriverProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val DashboardRed = Color(0xFFE7192D)
private val DashboardPink = Color(0xFFFFF0F2)
private val DashboardGreen = Color(0xFF087D32)
private val DashboardWarning = Color(0xFF8A5700)
private val DashboardInk = Color(0xFF18202B)
private val DashboardMuted = Color(0xFF505968)

@Composable
fun DriverDashboardScreen(
    state: DriverDashboardUiState,
    onRetry: () -> Unit,
    onReceivingOrders: (Boolean) -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    onGpsRetry: () -> Unit,
    offerContent: @Composable () -> Unit = {},
    currentOrderContent: @Composable () -> Unit = {},
    orderRefreshing: Boolean = false,
    onOrders: (() -> Unit)? = null,
    onEarnings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onGpsRetry() }
    val gpsAction: () -> Unit = {
        when (state.gps.status) {
            DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE -> {
                val activity = context as? Activity
                if (permissionRequested && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else {
                    permissionRequested = true
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            DriverGpsStatus.SERVICES_OFF -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> onGpsRetry()
        }
    }
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    val refreshThreshold = 100.dp
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val latestOrderRefreshing by rememberUpdatedState(orderRefreshing)
    var gestureStartedAtTop by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val showRefreshIndicator = refreshing || state.loading || orderRefreshing
    var refreshSnapshot by remember { mutableStateOf<DriverProfile?>(null) }
    // Keep the last rendered data only while a user-requested GET is in progress.
    // Errors and expired sessions always use the actual ViewModel state.
    val profile = state.profile ?: refreshSnapshot.takeIf { state.loading && !state.sessionExpired }
    val refresh = {
        if (!refreshing && !state.loading && !orderRefreshing && !state.saving && !state.sessionExpired) {
            refreshSnapshot = state.profile
            refreshing = true
            onRetry()
            scope.launch {
                // This minimum is visual only: GET starts above and data is never delayed.
                delay(450)
                snapshotFlow { latestState.loading || latestOrderRefreshing }.first { !it }
                refreshing = false
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = Color(0xFFF7F7F9),
        topBar = {
            Row(Modifier.fillMaxWidth().background(DashboardRed).padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Food Express", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Text("Tài xế", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = DashboardRed,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        bottomBar = { DriverDashboardNavigation(onProfile, enabled = !state.saving, onOrders = onOrders, onEarnings = onEarnings) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).pointerInput(scrollState) {
            // Observe the start; Material3 still owns drag distance, threshold and release.
            // A drag that scrolls back from the middle must not become a refresh halfway.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                gestureStartedAtTop = scrollState.value == 0
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (scrollState.value > 0) gestureStartedAtTop = false
                } while (event.changes.any { it.pressed })
            }
        }.pullToRefresh(
            isRefreshing = showRefreshIndicator, state = pullState,
            threshold = refreshThreshold,
            enabled = gestureStartedAtTop && !refreshing && !state.loading && !orderRefreshing && !state.saving && !state.sessionExpired,
            onRefresh = { if (gestureStartedAtTop && scrollState.value == 0) refresh() },
        ), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Xin chào,", color = DashboardMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(profile?.fullName ?: "Tài xế Food Express", color = DashboardInk,
                            fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp)
                        Text("Tài xế Food Express", color = DashboardMuted, fontSize = 13.sp, lineHeight = 19.sp)
                        if (profile?.status == "APPROVED") Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DashboardIcon(DashboardIconType.Check, DashboardGreen, Modifier.size(16.dp))
                            Text("Hồ sơ đã duyệt", color = DashboardGreen, fontSize = 13.sp,
                                lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Image(painterResource(R.drawable.driver_profile_illustration), contentDescription = "Minh họa tài xế mặc định",
                        modifier = Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
                when {
                    state.sessionExpired || (profile == null && (state.loading || state.message == null)) -> {
                        if (!showRefreshIndicator) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        Text("Đang tải trạng thái tài xế…", color = DashboardMuted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    profile == null -> DashboardCard(DashboardPink) {
                        Text("Chưa xác nhận trạng thái nhận đơn", color = DashboardRed, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                        DashboardMessage(state.message.orEmpty(), false)
                        Button(onClick = refresh, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DashboardRed)) { Text("Thử lại") }
                    }
                    else -> {
                        if (profile.status != "APPROVED") ApprovalSummary(profile)
                        ReceivingOrdersCard(state.copy(profile = profile), onReceivingOrders)
                    }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PositionCard(state, gpsAction, Modifier.weight(1f))
                    ConnectionCard(state, showRefreshIndicator, refresh, Modifier.weight(1f))
                }
                // This policy reminder is not a failure. Keep all real errors and unverified states visible.
                val hideRoutineActiveNotice = state.messageFromDeliveryPolicy &&
                    state.orderKnowledge == DriverOrderKnowledge.ACTIVE && !state.acceptPending &&
                    state.gps.status == DriverGpsStatus.READY && profile?.status == "APPROVED" &&
                    !state.statusUnknown && !state.sessionExpired && !state.offlineConflict &&
                    !state.loading && !state.saving && !state.enablingOnline
                if (profile != null && !hideRoutineActiveNotice) state.message?.let { message ->
                    val displayMessage = if (state.messageFromDeliveryPolicy) when {
                        state.acceptPending -> "Yêu cầu nhận đơn đang được xác minh; giữ nguyên trạng thái nhận đơn."
                        state.orderKnowledge == DriverOrderKnowledge.ACTIVE -> "Bạn đang thực hiện đơn. Hãy kiểm tra vị trí và kết nối khi cần."
                        state.orderKnowledge == DriverOrderKnowledge.UNKNOWN -> "Chưa xác minh được đơn hàng; giữ nguyên trạng thái nhận đơn."
                        else -> message
                    } else message
                    DashboardCard(if (state.messageIsError) DashboardPink else Color(0xFFF2F6FA)) {
                        DashboardMessage(displayMessage,
                            state.offlineConfirmed || (profile.isOnline == true && !state.statusUnknown && !state.saving), state.messageIsError)
                    }
                }
                offerContent()
                currentOrderContent()
            }
            PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = showRefreshIndicator,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f).semantics {
                    if (showRefreshIndicator) contentDescription = "Đang làm mới Dashboard"
                }, maxDistance = refreshThreshold,
                containerColor = Color.White, color = DashboardRed)
        }
    }
}

@Composable
private fun ApprovalSummary(profile: DriverProfile) {
    val title: String
    val body: String
    val background: Color
    val accent: Color
    when (profile.status) {
        "PENDING" -> {
            title = "Chưa thể nhận đơn"; body = "Hồ sơ đang chờ xét duyệt. Vui lòng đợi hồ sơ được duyệt."
            background = Color(0xFFFFF3D9); accent = Color(0xFF865000)
        }
        "APPROVED" -> {
            title = "Đã được duyệt"; body = "Chỉ nhận đơn khi có vị trí hợp lệ và máy chủ xác nhận."
            background = Color(0xFFE5F7EB); accent = DashboardGreen
        }
        "REJECTED" -> {
            title = "Hồ sơ bị từ chối"; body = "Bạn chưa thể bật nhận đơn. Vui lòng liên hệ hỗ trợ để được hướng dẫn."
            background = DashboardPink; accent = DashboardRed
        }
        else -> {
            title = "Hồ sơ tạm ngưng"; body = "Bạn chưa thể bật nhận đơn. Vui lòng liên hệ hỗ trợ."
            background = Color(0xFFF3EBF8); accent = Color(0xFF624773)
        }
    }
    DashboardCard(background) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardIcon(DashboardIconType.Clock, accent, Modifier.size(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                Text(body, color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ReceivingOrdersCard(state: DriverDashboardUiState, onChange: (Boolean) -> Unit) {
    val online = state.profile?.isOnline == true
    val approved = state.profile?.status == "APPROVED"
    val activeOrder = state.orderKnowledge == DriverOrderKnowledge.ACTIVE
    val deliveryVerifiedIdle = state.orderKnowledge == DriverOrderKnowledge.IDLE && !state.acceptPending
    val canRequest = approved && !state.statusUnknown && !state.loading && state.gps.status in setOf(
        DriverGpsStatus.READY, DriverGpsStatus.WAITING, DriverGpsStatus.STALE, DriverGpsStatus.INACCURATE)
    val title = when {
        state.acceptPending -> "Đang xác nhận nhận đơn"
        activeOrder -> "Đang thực hiện đơn"
        state.statusUnknown -> "Chưa xác nhận trạng thái"
        !deliveryVerifiedIdle -> "Chưa xác minh được đơn hàng"
        state.saving -> "Đang cập nhật"
        !approved -> "Chưa thể nhận đơn"
        online && state.gps.status == DriverGpsStatus.READY -> "Sẵn sàng nhận đơn"
        online -> "Vị trí chưa sẵn sàng"
        else -> "Tạm ngưng nhận đơn"
    }
    DashboardCard(Color.White) {
        Text("Trạng thái nhận đơn", color = DashboardInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = DashboardRed, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
                Text(if (state.acceptPending) "Chờ xác nhận kết quả nhận đơn trước khi thay đổi trạng thái."
                    else if (activeOrder) "Chưa thể tắt nhận đơn khi đang thực hiện đơn hàng."
                    else if (state.statusUnknown) "Công tắc thể hiện lần xác nhận trước. Vui lòng cập nhật lại."
                    else if (!deliveryVerifiedIdle) "Chưa thể thay đổi trạng thái nhận đơn trước khi xác minh."
                    else if (online && state.gps.status == DriverGpsStatus.READY) "Bạn sẽ nhận được lời mời khi có đơn phù hợp."
                    else if (online) "Kiểm tra vị trí trước khi tiếp tục nhận đơn."
                    else "Bạn đang tạm ngưng nhận đơn mới.", color = DashboardMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            if (!deliveryVerifiedIdle) DashboardIcon(DashboardIconType.Lock, DashboardMuted, Modifier.size(18.dp))
            if (state.saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Switch(checked = online, onCheckedChange = onChange, enabled = deliveryVerifiedIdle && !state.saving && (online || canRequest),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DashboardRed),
                modifier = Modifier.semantics { contentDescription = "Bật hoặc tắt nhận đơn" })
        }
        if (state.enablingOnline) TextButton(onClick = { onChange(false) }) { Text("Hủy yêu cầu bật nhận đơn") }
    }
}

@Composable
private fun PositionCard(state: DriverDashboardUiState, onGpsAction: () -> Unit, modifier: Modifier) {
    val ready = state.gps.status == DriverGpsStatus.READY
    val accent = if (ready) DashboardGreen else DashboardRed
    Surface(modifier.fillMaxHeight(), color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E8EB))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusIcon(DashboardIconType.Pin, accent, if (ready) Color(0xFFE5F8E9) else DashboardPink)
                Text("Vị trí", color = DashboardInk, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(when (state.gps.status) {
                DriverGpsStatus.READY -> "Đã xác định"
                DriverGpsStatus.WAITING -> "Đang lấy vị trí"
                DriverGpsStatus.STALE -> "Vị trí đã cũ"
                DriverGpsStatus.INACCURATE -> "Chưa đủ chính xác"
                else -> "Chưa sẵn sàng"
            }, color = accent, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Text(when (state.gps.status) {
                DriverGpsStatus.READY -> "Vị trí mới, đủ chính xác."
                DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE -> "Cho phép sử dụng vị trí chính xác."
                DriverGpsStatus.SERVICES_OFF -> "Bật vị trí trên thiết bị."
                DriverGpsStatus.STALE, DriverGpsStatus.INACCURATE, DriverGpsStatus.WAITING -> "Vui lòng chờ vị trí phù hợp."
                else -> "Vị trí chỉ cập nhật khi mở ứng dụng."
            }, color = DashboardMuted, fontSize = 13.sp, lineHeight = 18.sp)
            if (state.profile?.status == "APPROVED" && !state.saving && !ready) TextButton(onClick = onGpsAction,
                contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(when (state.gps.status) {
                    DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE -> "Cấp quyền vị trí"
                    DriverGpsStatus.SERVICES_OFF -> "Bật vị trí"
                    else -> "Thử lại vị trí"
                }, color = DashboardRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: DriverDashboardUiState, updating: Boolean, onRetry: () -> Unit, modifier: Modifier) {
    // These are request results, not an Internet/presence detector.
    val failed = state.statusUnknown || (state.messageIsError && !state.messageFromDeliveryPolicy)
    val title = when {
        updating -> "Đang cập nhật"
        failed -> "Không thể cập nhật"
        state.profile == null || state.sessionExpired || state.orderKnowledge == DriverOrderKnowledge.UNKNOWN -> "Chưa xác nhận"
        else -> "Đã cập nhật"
    }
    val confirmed = !updating && !failed && state.profile != null && !state.sessionExpired &&
        state.orderKnowledge != DriverOrderKnowledge.UNKNOWN
    val accent = when { failed -> DashboardRed; confirmed -> DashboardGreen; else -> DashboardWarning }
    Surface(modifier.fillMaxHeight(), color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E8EB))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusIcon(DashboardIconType.Connection, accent, when {
                    failed -> DashboardPink; confirmed -> Color(0xFFE5F8E9); else -> Color(0xFFFFF4D6) })
                Text("Kết nối", color = DashboardInk, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = accent, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Text(if (failed) "Kiểm tra kết nối và thử lại." else "Lần tải dữ liệu gần nhất.",
                color = DashboardMuted, fontSize = 13.sp, lineHeight = 18.sp)
            if (state.statusUnknown && !state.saving) TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Thử lại", color = DashboardRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusIcon(type: DashboardIconType, accent: Color, background: Color) {
    Box(Modifier.size(40.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        DashboardIcon(type, accent, Modifier.size(28.dp))
    }
}

@Composable
private fun DashboardCard(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E8EB))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun DashboardMessage(message: String, success: Boolean, error: Boolean = true) {
    Text(message, color = if (error) MaterialTheme.colorScheme.error else if (success) DashboardGreen else DashboardMuted, fontSize = 14.sp,
        lineHeight = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
fun DriverDashboardNavigation(onProfile: () -> Unit, enabled: Boolean = true, selectedTab: Int = 0,
    onHome: () -> Unit = {}, onOrders: (() -> Unit)? = null, onEarnings: (() -> Unit)? = null) {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        NavigationBar(containerColor = Color.White, windowInsets = WindowInsets(0, 0, 0, 0)) {
            listOf("Trang chủ", "Đơn hàng", "Thu nhập", "Hồ sơ").forEachIndexed { index, title ->
                NavigationBarItem(
                    selected = index == selectedTab, enabled = enabled && (index == 0 || index == 3 || index == 1 && onOrders != null || index == 2 && onEarnings != null), alwaysShowLabel = true,
                    onClick = { when (index) { 0 -> onHome(); 1 -> onOrders?.invoke(); 2 -> onEarnings?.invoke(); 3 -> onProfile() } },
                    icon = { DashboardIcon(DashboardIconType.entries[index], if (index == selectedTab) DashboardRed else DashboardMuted, Modifier.size(24.dp)) },
                    label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (index == selectedTab) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                        if (index == 1 && onOrders == null || index == 2 && onEarnings == null) Text("Tuần sau", fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                    } },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = DashboardRed,
                        selectedTextColor = DashboardRed, indicatorColor = DashboardPink,
                        unselectedTextColor = DashboardInk, disabledTextColor = DashboardMuted,
                        disabledIconColor = DashboardMuted),
                )
            }
        }
    }
}

private enum class DashboardIconType { Home, Receipt, Wallet, Person, Clock, Pin, Star, Flag, Bell, Box, Lock, Connection, Check }

@Composable
private fun DashboardIcon(type: DashboardIconType, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx())
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w*x1,h*y1), Offset(w*x2,h*y2), stroke.width)
        when (type) {
            DashboardIconType.Check -> {
                drawCircle(color, w*.43f, center)
                drawPath(Path().apply { moveTo(w*.28f,h*.5f); lineTo(w*.44f,h*.66f); lineTo(w*.74f,h*.35f) },
                    Color.White, style = Stroke(1.7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            DashboardIconType.Lock -> {
                drawArc(color, 180f, 180f, false, Offset(w*.28f,h*.08f), Size(w*.44f,h*.5f), style=stroke)
                drawRoundRect(color, Offset(w*.18f,h*.43f), Size(w*.64f,h*.48f), androidx.compose.ui.geometry.CornerRadius(w*.08f), style=stroke)
                drawCircle(color,w*.055f,Offset(w*.5f,h*.65f))
            }
            DashboardIconType.Connection -> {
                drawPath(Path().apply {
                    moveTo(w*.3f,h*.73f); lineTo(w*.25f,h*.73f)
                    cubicTo(w*.02f,h*.73f,w*.04f,h*.4f,w*.25f,h*.39f)
                    cubicTo(w*.27f,h*.08f,w*.71f,h*.07f,w*.75f,h*.4f)
                    cubicTo(w*.99f,h*.4f,w*.99f,h*.73f,w*.76f,h*.73f)
                    lineTo(w*.7f,h*.73f)
                }, color, style = stroke)
                line(.5f,.88f,.5f,.5f); line(.35f,.65f,.5f,.5f); line(.5f,.5f,.65f,.65f)
            }
            DashboardIconType.Home -> {
                val p=Path().apply { moveTo(w*.12f,h*.45f); lineTo(w*.5f,h*.12f); lineTo(w*.88f,h*.45f); lineTo(w*.8f,h*.45f); lineTo(w*.8f,h*.88f); lineTo(w*.58f,h*.88f); lineTo(w*.58f,h*.61f); lineTo(w*.42f,h*.61f); lineTo(w*.42f,h*.88f); lineTo(w*.2f,h*.88f); lineTo(w*.2f,h*.45f); close() }
                drawPath(p,color)
            }
            DashboardIconType.Person -> {
                drawCircle(color,w*.16f,Offset(w*.5f,h*.27f),style=stroke)
                drawArc(color,180f,180f,false,Offset(w*.15f,h*.53f),Size(w*.7f,h*.55f),style=stroke)
                line(.15f,.8f,.85f,.8f)
            }
            DashboardIconType.Clock -> {
                drawCircle(color,w*.38f,center,style=stroke); line(.5f,.26f,.5f,.5f); line(.5f,.5f,.68f,.64f)
            }
            DashboardIconType.Pin -> {
                drawPath(Path().apply {
                    moveTo(w*.5f,h*.91f)
                    cubicTo(w*.33f,h*.7f,w*.17f,h*.5f,w*.2f,h*.32f)
                    cubicTo(w*.25f,h*.03f,w*.75f,h*.03f,w*.8f,h*.32f)
                    cubicTo(w*.83f,h*.5f,w*.67f,h*.7f,w*.5f,h*.91f)
                    close()
                }, color, style = stroke)
                drawCircle(color,w*.1f,Offset(w*.5f,h*.35f),style=stroke)
            }
            DashboardIconType.Star -> {
                val p=Path()
                for (i in 0..9) {
                    val a=Math.PI*(-.5+i*.2); val r=w*(if(i%2==0) .43f else .2f)
                    val x=w/2+(kotlin.math.cos(a)*r).toFloat(); val y=h/2+(kotlin.math.sin(a)*r).toFloat()
                    if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
                }
                p.close(); drawPath(p,color)
            }
            DashboardIconType.Flag -> {
                line(.22f,.12f,.22f,.9f)
                val p=Path().apply {moveTo(w*.25f,h*.15f);lineTo(w*.83f,h*.22f);lineTo(w*.75f,h*.57f);lineTo(w*.25f,h*.5f);close()};drawPath(p,color)
            }
            DashboardIconType.Bell -> {
                drawArc(color,180f,180f,false,Offset(w*.25f,h*.15f),Size(w*.5f,h*.5f),style=stroke)
                line(.25f,.4f,.2f,.75f);line(.75f,.4f,.8f,.75f);line(.2f,.75f,.8f,.75f);drawCircle(color,w*.06f,Offset(w*.5f,h*.9f))
            }
            else -> {
                drawRoundRect(color,Offset(w*.18f,h*.18f),Size(w*.65f,h*.65f),androidx.compose.ui.geometry.CornerRadius(w*.08f),style=stroke)
                if(type==DashboardIconType.Wallet) {line(.6f,.4f,.86f,.4f);line(.6f,.6f,.86f,.6f)}
                else {line(.32f,.38f,.68f,.38f);line(.32f,.55f,.62f,.55f)}
            }
        }
    }
}
