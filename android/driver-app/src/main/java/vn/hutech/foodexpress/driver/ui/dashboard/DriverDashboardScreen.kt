package vn.hutech.foodexpress.driver.ui.dashboard

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
private val DashboardGreen = Color(0xFF087D3C)
private val DashboardInk = Color(0xFF173B70)
private val DashboardMuted = Color(0xFF596B82)

@Composable
fun DriverDashboardScreen(
    state: DriverDashboardUiState,
    onRetry: () -> Unit,
    onReceivingOrders: (Boolean) -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    val refreshThreshold = 100.dp
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    var gestureStartedAtTop by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val showRefreshIndicator = refreshing || state.loading
    var refreshSnapshot by remember { mutableStateOf<DriverProfile?>(null) }
    // Keep the last rendered data only while a user-requested GET is in progress.
    // Errors and expired sessions always use the actual ViewModel state.
    val profile = state.profile ?: refreshSnapshot.takeIf { state.loading && !state.sessionExpired }
    val refresh = {
        if (!refreshing && !state.loading && !state.saving && !state.sessionExpired) {
            refreshSnapshot = state.profile
            refreshing = true
            onRetry()
            scope.launch {
                // This minimum is visual only: GET starts above and data is never delayed.
                delay(450)
                snapshotFlow { latestState.loading }.first { !it }
                refreshing = false
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = Color(0xFFFFFBFC),
        bottomBar = { DashboardNavigation(onProfile, enabled = !state.saving) },
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
            enabled = gestureStartedAtTop && !refreshing && !state.loading && !state.saving && !state.sessionExpired,
            onRefresh = { if (gestureStartedAtTop && scrollState.value == 0) refresh() },
        ), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.driver_profile_illustration), contentDescription = "Minh họa tài xế mặc định",
                        modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text("Xin chào,", color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        Text(profile?.fullName ?: "Tài xế Food Express", color = DashboardInk,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp)
                    }
                    DashboardIcon(DashboardIconType.Bell, DashboardRed, Modifier.size(24.dp))
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
                        ApprovalSummary(profile)
                        ReceivingOrdersCard(state.copy(profile = profile), onReceivingOrders)
                        state.message?.let { DashboardMessage(it, state.offlineConfirmed) }
                    }
                }
                if (profile == null) {
                    Text("GPS chưa sẵn sàng. Vị trí hiện tại chưa được xác minh; chưa thể bật Online.",
                        color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Text("Tổng quan hôm nay", color = DashboardInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatisticCard("Đơn hôm nay", DashboardIconType.Receipt, DashboardRed, Modifier.weight(1f))
                        StatisticCard("Thu nhập", DashboardIconType.Wallet, DashboardGreen, Modifier.weight(1f))
                    }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatisticCard("Đánh giá", DashboardIconType.Star, Color(0xFFAF6800), Modifier.weight(1f))
                        StatisticCard("Tỷ lệ hoàn thành", DashboardIconType.Flag, DashboardGreen, Modifier.weight(1f))
                    }
                }
                Text("Đơn hàng hiện tại", color = DashboardInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                DashboardCard(DashboardPink) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DashboardIcon(DashboardIconType.Box, DashboardRed, Modifier.size(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Chưa tích hợp dữ liệu đơn hàng", color = DashboardRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
                            Text("Đơn hàng và nhận đơn sẽ được triển khai ở tuần sau.", color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = showRefreshIndicator,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f), maxDistance = refreshThreshold,
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
            title = "Đã được duyệt"; body = "Cần xác minh vị trí GPS hiện tại trước khi bật nhận đơn."
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
    DashboardCard(if (online) Color(0xFFE5F7EB) else Color(0xFFF2F2F4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardIcon(DashboardIconType.Pin, if (online) DashboardGreen else DashboardMuted, Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (online) "Đang bật nhận đơn trên máy chủ" else "Bật nhận đơn", color = DashboardInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
                Text(if (online) "GPS hiện tại chưa được xác minh. Hãy chuyển Offline."
                    else "Đang Offline theo phản hồi máy chủ.", color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            if (state.saving) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Switch(checked = online, onCheckedChange = onChange, enabled = online && !state.loading)
        }
        Text("GPS chưa được xác minh; chưa thể bật Online.", color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun StatisticCard(title: String, icon: DashboardIconType, color: Color, modifier: Modifier) {
    Surface(modifier.fillMaxHeight(), color = Color.White, shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E8EB))) {
        Row(Modifier.padding(10.dp).heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DashboardIcon(icon, color, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = DashboardMuted, fontSize = 12.sp, lineHeight = 16.sp)
                Text("Chưa có dữ liệu", color = DashboardInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun DashboardCard(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun DashboardMessage(message: String, success: Boolean) {
    Text(message, color = if (success) DashboardGreen else MaterialTheme.colorScheme.error, fontSize = 14.sp,
        lineHeight = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun DashboardNavigation(onProfile: () -> Unit, enabled: Boolean) {
    Surface(color = Color.White, shadowElevation = 4.dp) {
        NavigationBar(containerColor = Color.White, windowInsets = WindowInsets(0, 0, 0, 0)) {
            listOf("Trang chủ", "Đơn hàng", "Thu nhập", "Hồ sơ").forEachIndexed { index, title ->
                NavigationBarItem(
                    selected = index == 0, enabled = enabled && (index == 0 || index == 3), alwaysShowLabel = true,
                    onClick = { if (index == 3) onProfile() },
                    icon = { DashboardIcon(DashboardIconType.entries[index], if (index == 0) DashboardRed else DashboardMuted, Modifier.size(22.dp)) },
                    label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                        if (index == 1 || index == 2) Text("Tuần sau", fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
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

private enum class DashboardIconType { Home, Receipt, Wallet, Person, Clock, Pin, Star, Flag, Bell, Box }

@Composable
private fun DashboardIcon(type: DashboardIconType, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = 1.8.dp.toPx())
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w*x1,h*y1), Offset(w*x2,h*y2), stroke.width)
        when (type) {
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
                drawCircle(color,w*.28f,Offset(w*.5f,h*.38f),style=stroke)
                line(.27f,.55f,.5f,.9f); line(.73f,.55f,.5f,.9f); drawCircle(color,w*.07f,Offset(w*.5f,h*.38f))
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
