package vn.hutech.foodexpress.driver.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.data.profile.DriverProfile
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressInk
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressMuted

private val ProfilePink = Color(0xFFFFF0F2)
private val ProfileRed = Color(0xFFE7192D)

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
    Box(
        Modifier.fillMaxSize().background(ProfilePink).windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileHeader(onBack = if (state.editing) onCancel else onHome, enabled = !state.saving)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    state.loading || state.sessionExpired -> {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        Text(if (state.saving) "Đang xác nhận hồ sơ sau cập nhật…" else "Đang tải hồ sơ…",
                            modifier = Modifier.align(Alignment.CenterHorizontally), color = FoodExpressMuted)
                    }
                    state.profile == null -> {
                        ProfileMessage(state.message ?: "Chưa tải được hồ sơ.", success = false)
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Thử tải lại hồ sơ") }
                    }
                    else -> {
                        val profile = state.profile
                        Text(profile.fullName.orEmpty(), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            color = FoodExpressInk, modifier = Modifier.align(Alignment.CenterHorizontally))
                        ProfileDetail("Số điện thoại", profile.phone.orEmpty())
                        ProfileDetail("Email", profile.email?.takeIf { it.isNotBlank() } ?: "Chưa cung cấp")
                        ApprovalCard(profile.status.orEmpty())
                        state.message?.let { ProfileMessage(it, state.saved) }
                        Surface(
                            shape = RoundedCornerShape(16.dp), color = Color(0xFFFFFBFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0E6E8)),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (state.editing) "Chỉnh sửa phương tiện" else "Thông tin phương tiện",
                                        Modifier.weight(1f), color = FoodExpressInk, fontWeight = FontWeight.Bold)
                                    if (profile.status == "PENDING" && !state.editing) {
                                        TextButton(onClick = onEdit) { Text("Chỉnh sửa", color = ProfileRed) }
                                    }
                                }
                                if (state.editing) {
                                    VehicleForm(state, onVehicle, onPlate, save, onCancel)
                                } else {
                                    ProfileDetail("Loại xe", vehicleLabel(profile.vehicleType.orEmpty()))
                                    ProfileDetail("Biển số xe", profile.licensePlate?.takeIf { it.isNotBlank() } ?: "Chưa cung cấp")
                                    VehiclePolicy(profile)
                                }
                            }
                        }
                        if (!state.editing) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Tải lại hồ sơ")
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFF2E8EA))
                Button(onClick = onHome, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Về Trang chủ") }
                Text("Trang chủ hiện là màn Session tạm sau đăng nhập.", color = FoodExpressMuted, fontSize = 12.sp)
                OutlinedButton(
                    onClick = onLogout, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ProfileRed),
                ) { Text("Đăng xuất", color = ProfileRed) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth().background(ProfilePink)) {
        Canvas(Modifier.matchParentSize()) {
            // Decorative skyline echoes the original PDF; approval is always rendered from API data below.
            for (i in 0..11) {
                val width = size.width / 12
                val height = size.height * (0.2f + (i % 4) * 0.09f)
                drawRect(Color(0xFFFFDDE2), Offset(i * width, size.height - height), Size(width * 0.7f, height))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Quay lại" }) {
                    Text("‹", fontSize = 32.sp, color = FoodExpressInk)
                }
                Text("Hồ sơ tài xế", Modifier.weight(1f), color = FoodExpressInk, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("FOOD EXPRESS", color = ProfileRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Image(painterResource(R.drawable.driver_profile_illustration), contentDescription = "Minh họa tài xế mặc định",
                modifier = Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Text("Hình minh họa mặc định", color = FoodExpressMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ProfileDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, color = FoodExpressMuted)
        Text(value, color = FoodExpressInk, fontSize = 16.sp)
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold)
            Text(description, color = FoodExpressInk, fontSize = 14.sp)
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
    Text(message, fontSize = 13.sp, color = FoodExpressMuted)
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
                Text(vehicleLabel(type), color = FoodExpressInk, modifier = Modifier.padding(start = 8.dp))
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
        fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private fun vehicleLabel(type: String): String = when (type) {
    "MOTORBIKE" -> "Xe máy"
    "BICYCLE" -> "Xe đạp"
    "CAR" -> "Ô tô"
    else -> "Chưa xác định"
}
