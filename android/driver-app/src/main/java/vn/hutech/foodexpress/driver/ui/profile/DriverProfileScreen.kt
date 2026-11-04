package vn.hutech.foodexpress.driver.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.data.profile.DriverProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val ProfilePink = Color(0xFFFFF0F2)
private val ProfileRed = Color(0xFFE7192D)
private val ProfileInk = Color(0xFF173B70)
private val ProfileMuted = Color(0xFF596B82)
private val ProfileBlue = Color(0xFF315D9B)

@Composable
fun DriverProfileScreen(
    state: DriverProfileUiState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onVehicle: (String) -> Unit,
    onPlate: (String) -> Unit,
    onSave: () -> Unit,
    onHome: () -> Unit,
    onLogout: () -> Unit,
) {
    BackHandler(enabled = state.editing || state.saving) { if (!state.saving) onCancel() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val save = { focus.clearFocus(); keyboard?.hide(); onSave() }
    val pullState = rememberPullToRefreshState()
    val scrollState = rememberScrollState()
    val refreshThreshold = 100.dp
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    var gestureStartedAtTop by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val showRefreshIndicator = refreshing || (state.loading && !state.saving)
    var showSavedMessage by remember(state.message, state.saved) { mutableStateOf(state.saved) }
    LaunchedEffect(state.message, state.saved) {
        if (state.saved && state.message != null) {
            delay(3_500)
            showSavedMessage = false
        }
    }
    var refreshSnapshot by remember { mutableStateOf<DriverProfile?>(null) }
    val profile = state.profile ?: refreshSnapshot.takeIf { state.loading && !state.saving && !state.sessionExpired }
    val refresh = {
        // A refresh must never replace the vehicle form while the driver is editing it.
        if (!refreshing && !state.editing && !state.loading && !state.saving && !state.sessionExpired) {
            refreshSnapshot = state.profile
            refreshing = true
            onRetry()
            scope.launch {
                // Keep fast GETs perceptible without delaying the API or the new data.
                delay(450)
                snapshotFlow { latestState.loading }.first { !it }
                refreshing = false
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(ProfilePink).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(
            Modifier.widthIn(max = 520.dp).fillMaxSize().background(Color.White).pointerInput(scrollState) {
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
                enabled = gestureStartedAtTop && !refreshing && !state.editing && !state.loading && !state.saving && !state.sessionExpired,
                onRefresh = { if (gestureStartedAtTop && scrollState.value == 0) refresh() },
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState).heightIn(min = maxHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileHeader(profile?.fullName, onBack = if (state.editing) onCancel else onHome, enabled = !state.saving)
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when {
                            state.sessionExpired || (state.loading && profile == null) -> {
                                if (!showRefreshIndicator) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                                Text(if (state.saving) "Đang xác nhận hồ sơ sau cập nhật…" else "Đang tải hồ sơ…",
                                    modifier = Modifier.align(Alignment.CenterHorizontally), color = ProfileMuted,
                                    fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            // Empty is the pre-load/route-exit state, not a failed API request.
                            // Only explicit loading and error states show indicators or Retry.
                            profile == null && state.message == null -> Spacer(Modifier.height(8.dp))
                            profile == null -> {
                                ProfileMessage(state.message.orEmpty(), success = false)
                                Button(onClick = refresh, modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ProfileRed)) { Text("Thử tải lại hồ sơ") }
                            }
                            else -> {
                                ProfileContact(ProfileIconType.Phone, "Số điện thoại", profile.phone.orEmpty())
                                ProfileContact(ProfileIconType.Email, "Email", profile.email?.takeIf { it.isNotBlank() } ?: "Chưa cung cấp")
                                ApprovalCard(profile.status.orEmpty())
                                state.message?.takeIf { !state.saved || showSavedMessage }?.let { ProfileMessage(it, state.saved) }
                                Surface(
                                    shape = RoundedCornerShape(12.dp), color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E6E8)),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (state.editing) "Chỉnh sửa phương tiện" else "Thông tin phương tiện",
                                                Modifier.weight(1f), color = ProfileInk, fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp, lineHeight = 20.sp)
                                            if (profile.status == "PENDING" && !state.editing) {
                                                TextButton(onClick = onEdit, enabled = !state.loading) { Text("Chỉnh sửa", color = ProfileRed) }
                                            }
                                        }
                                        if (state.editing) {
                                            VehicleForm(state, onVehicle, onPlate, save, onCancel)
                                        } else {
                                            ProfileVehicleDetail(vehicleIcon(profile.vehicleType.orEmpty()), "Loại xe", vehicleLabel(profile.vehicleType.orEmpty()))
                                            HorizontalDivider(color = Color(0xFFF1F3F7))
                                            ProfileVehicleDetail(ProfileIconType.Plate, "Biển số xe", profile.licensePlate?.takeIf { it.isNotBlank() } ?: "Chưa cung cấp")
                                            VehiclePolicy(profile)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onLogout, enabled = !state.saving,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProfileRed),
                ) {
                    ProfileIcon(ProfileIconType.Logout, ProfileRed, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đăng xuất", color = ProfileRed, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = showRefreshIndicator,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f), maxDistance = refreshThreshold,
                containerColor = Color.White, color = ProfileRed)
        }
    }
}

@Composable
private fun ProfileHeader(fullName: String?, onBack: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth().background(ProfilePink)) {
        Canvas(Modifier.matchParentSize()) {
            // Decorative skyline echoes the original PDF; approval is always rendered from API data below.
            for (i in 0..11) {
                val width = size.width / 12
                val height = size.height * (0.2f + (i % 4) * 0.09f)
                drawRect(Color(0xFFFFDDE2), Offset(i * width, size.height - height), Size(width * 0.7f, height))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("Hồ sơ tài xế", Modifier.fillMaxWidth().padding(horizontal = 48.dp), color = ProfileInk,
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
                IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.align(Alignment.CenterStart).semantics { contentDescription = "Quay lại" }) {
                    Text("‹", fontSize = 32.sp, lineHeight = 36.sp, color = ProfileInk)
                }
            }
            Image(painterResource(R.drawable.driver_profile_illustration), contentDescription = "Minh họa tài xế mặc định",
                modifier = Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            fullName?.let {
                Text(it, color = ProfileInk, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            }
        }
    }
}

@Composable
private fun ProfileContact(icon: ProfileIconType, label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ProfileIcon(icon, ProfileBlue, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, Modifier.weight(1f, fill = false), color = ProfileBlue, fontSize = 14.sp,
            lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileVehicleDetail(icon: ProfileIconType, label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileIcon(icon, ProfileBlue, Modifier.size(22.dp))
        Text(label, Modifier.weight(0.42f), fontSize = 12.sp, lineHeight = 16.sp, color = ProfileMuted)
        Text(value, Modifier.weight(0.58f), fontSize = 14.sp, lineHeight = 20.sp, color = ProfileBlue)
    }
}

@Composable
private fun ApprovalCard(status: String) {
    val title: String
    val description: String
    val color: Color
    val background: Color
    when (status) {
        "PENDING" -> {
            title = "Đang chờ xét duyệt"; description = "Hồ sơ đang chờ xét duyệt. Bạn chưa thể nhận đơn."
            color = Color(0xFF865000); background = Color(0xFFFFF3D9)
        }
        "APPROVED" -> {
            title = "Đã được duyệt"; description = "Hồ sơ tài xế của bạn đã được duyệt."
            color = Color(0xFF087D3C); background = Color(0xFFE5F7EB)
        }
        "REJECTED" -> {
            title = "Bị từ chối"; description = "Hồ sơ chưa được chấp nhận. Vui lòng liên hệ hỗ trợ để được hướng dẫn."
            color = Color(0xFFB32334); background = ProfilePink
        }
        else -> {
            title = "Tạm ngưng"; description = "Hồ sơ tài xế đang tạm ngưng. Bạn chưa thể nhận đơn. Vui lòng liên hệ hỗ trợ."
            color = Color(0xFF624773); background = Color(0xFFF3EBF8)
        }
    }
    Surface(color = background, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileIcon(if (status == "APPROVED") ProfileIconType.Check else ProfileIconType.Clock, color, Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                Text(description, color = ProfileMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun VehiclePolicy(profile: DriverProfile) {
    val message = when (profile.status) {
        "APPROVED" -> "Đổi phương tiện đã được duyệt cần xét duyệt lại. Chức năng gửi yêu cầu đổi phương tiện chưa được triển khai."
        "REJECTED", "SUSPENDED" -> "Trạng thái hồ sơ hiện không cho phép sửa phương tiện. Vui lòng liên hệ hỗ trợ."
        else -> "Bạn có thể cập nhật phương tiện trong khi hồ sơ đang chờ xét duyệt."
    }
    Text(message, fontSize = 12.sp, lineHeight = 16.sp, color = ProfileMuted)
}

@Composable
private fun VehicleForm(
    state: DriverProfileUiState,
    onVehicle: (String) -> Unit,
    onPlate: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        listOf("MOTORBIKE", "BICYCLE", "CAR").forEach { type ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = state.vehicleType == type, enabled = !state.saving, role = Role.RadioButton,
                        onClick = { onVehicle(type) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = state.vehicleType == type, onClick = null, enabled = !state.saving)
                Text(vehicleLabel(type), color = ProfileInk, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    state.fieldErrors["vehicleType"]?.let { ProfileMessage(it, false) }
    OutlinedTextField(
        value = state.licensePlate, onValueChange = onPlate, modifier = Modifier.fillMaxWidth(), enabled = !state.saving,
        label = { Text(if (state.vehicleType == "BICYCLE") "Biển số (không bắt buộc)" else "Biển số xe *") },
        singleLine = true, isError = state.fieldErrors.containsKey("licensePlate"),
        supportingText = { Text(state.fieldErrors["licensePlate"] ?: "Tối đa 20 ký tự. Xe đạp có thể để trống.") },
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!state.saving) onSave() }),
    )
    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
        if (state.saving) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (state.saving) "Đang lưu…" else "Lưu phương tiện")
    }
    TextButton(onClick = onCancel, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Hủy chỉnh sửa") }
}

@Composable
private fun ProfileMessage(message: String, success: Boolean) {
    Text(message, color = if (success) Color(0xFF087D3C) else MaterialTheme.colorScheme.error,
        fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private enum class ProfileIconType { Phone, Email, Motorbike, Bicycle, Car, Plate, Clock, Check, Logout }

private fun vehicleIcon(type: String): ProfileIconType = when (type) {
    "CAR" -> ProfileIconType.Car
    "MOTORBIKE" -> ProfileIconType.Motorbike
    "BICYCLE" -> ProfileIconType.Bicycle
    else -> ProfileIconType.Plate
}

@Composable
private fun ProfileIcon(type: ProfileIconType, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = Stroke(1.7.dp.toPx())
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w*x1,h*y1), Offset(w*x2,h*y2), stroke.width)
        when (type) {
            ProfileIconType.Logout -> {
                val path = Path().apply {
                    moveTo(w*.5f,h*.16f); lineTo(w*.18f,h*.16f); lineTo(w*.18f,h*.84f); lineTo(w*.5f,h*.84f)
                }
                drawPath(path, color, style = stroke)
                line(.43f,.5f,.88f,.5f); line(.7f,.32f,.88f,.5f); line(.7f,.68f,.88f,.5f)
            }
            ProfileIconType.Phone -> {
                val path = Path().apply {
                    moveTo(w*.22f,h*.12f); lineTo(w*.4f,h*.3f); lineTo(w*.3f,h*.43f)
                    quadraticTo(w*.4f,h*.64f,w*.61f,h*.73f); lineTo(w*.74f,h*.62f)
                    lineTo(w*.9f,h*.8f); quadraticTo(w*.68f,h*1.02f,w*.33f,h*.68f)
                    quadraticTo(w*.02f,h*.36f,w*.22f,h*.12f)
                }
                drawPath(path,color,style=stroke)
            }
            ProfileIconType.Email, ProfileIconType.Plate -> {
                drawRoundRect(color,Offset(w*.1f,h*.22f),Size(w*.8f,h*.58f),androidx.compose.ui.geometry.CornerRadius(w*.06f),style=stroke)
                if (type == ProfileIconType.Email) { line(.12f,.25f,.5f,.53f); line(.5f,.53f,.88f,.25f) }
                else for (i in 0..3) line(.25f+i*.16f,.38f,.25f+i*.16f,.63f)
            }
            ProfileIconType.Car, ProfileIconType.Motorbike, ProfileIconType.Bicycle -> {
                // Reuse the vehicle shapes already used by Register A2; no icon dependency.
                drawCircle(color,w*.13f,Offset(w*.2f,h*.75f),style=stroke)
                drawCircle(color,w*.13f,Offset(w*.8f,h*.75f),style=stroke)
                if (type == ProfileIconType.Car) {
                    drawRoundRect(color,Offset(w*.08f,h*.3f),Size(w*.84f,h*.38f),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),style=stroke)
                    line(.25f,.3f,.35f,.12f); line(.35f,.12f,.65f,.12f); line(.65f,.12f,.75f,.3f)
                } else {
                    val path = Path().apply {
                        moveTo(w*.2f,h*.75f);lineTo(w*.4f,h*.3f);lineTo(w*.6f,h*.75f);close()
                        moveTo(w*.4f,h*.3f);lineTo(w*.7f,h*.3f);lineTo(w*.8f,h*.75f)
                    }
                    drawPath(path,color,style=stroke)
                    if (type == ProfileIconType.Motorbike) {
                        drawRoundRect(color,Offset(w*.3f,h*.42f),Size(w*.33f,h*.18f),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                    }
                }
            }
            ProfileIconType.Clock -> {
                drawCircle(color,w*.39f,center,style=stroke);line(.5f,.25f,.5f,.5f);line(.5f,.5f,.68f,.65f)
            }
            ProfileIconType.Check -> {
                drawCircle(color,w*.4f,center);drawLine(Color.White,Offset(w*.28f,h*.51f),Offset(w*.44f,h*.67f),stroke.width)
                drawLine(Color.White,Offset(w*.44f,h*.67f),Offset(w*.73f,h*.34f),stroke.width)
            }
        }
    }
}

private fun vehicleLabel(type: String): String = when (type) {
    "MOTORBIKE" -> "Xe máy"
    "BICYCLE" -> "Xe đạp"
    "CAR" -> "Ô tô"
    else -> "Chưa xác định"
}
