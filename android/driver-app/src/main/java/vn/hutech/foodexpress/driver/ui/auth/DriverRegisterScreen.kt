package vn.hutech.foodexpress.driver.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressInk
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressMuted

@Composable
fun DriverRegisterScreen(
    step: Int,
    state: DriverRegisterUiState,
    onChange: (String, String) -> Unit,
    onVehicle: (DriverVehicleType) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
) {
    BackHandler { if (!state.loading) onBack() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val advance = {
        focus.clearFocus(); keyboard?.hide()
        if (step == 1) onNext() else onSubmit()
    }
    RegisterPage(title = "Đăng ký tài xế", onBack = onBack, loading = state.loading) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RegisterSteps(step)
            Text(if (step == 1) "Thông tin tài khoản" else "Thông tin phương tiện",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FoodExpressInk)
            Text(if (step == 1) "Nhập thông tin để tạo tài khoản." else "Cung cấp thông tin phương tiện của bạn.",
                color = FoodExpressMuted, fontSize = 14.sp)
            if (step == 1) {
                RegisterField("fullName", "Họ và tên *", state.form.fullName, state, onChange)
                RegisterField("phone", "Số điện thoại *", state.form.phone, state, onChange, keyboardType = KeyboardType.Phone)
                RegisterField("email", "Email (không bắt buộc)", state.form.email, state, onChange, keyboardType = KeyboardType.Email)
                RegisterField("password", "Mật khẩu *", state.form.password, state, onChange, password = true)
                Text("Mật khẩu từ 8 đến 72 ký tự.", fontSize = 12.sp, color = FoodExpressMuted)
                RegisterField("confirmPassword", "Xác nhận mật khẩu *", state.form.confirmPassword,
                    state, onChange, password = true, last = true, onDone = advance)
            } else {
                Text("Loại phương tiện *", color = FoodExpressInk, fontSize = 14.sp)
                DriverVehicleType.entries.forEach { type ->
                    VehicleOption(type, selected = state.form.vehicleType == type, enabled = !state.loading,
                        onClick = { onVehicle(type) })
                }
                RegisterError(state.fieldErrors["vehicleType"])
                RegisterField("licensePlate", if (state.form.vehicleType == DriverVehicleType.BICYCLE) "Biển số (không bắt buộc)" else "Biển số xe *",
                    state.form.licensePlate, state, onChange, last = true, onDone = advance)
                Text(if (state.form.vehicleType == DriverVehicleType.BICYCLE) "Xe đạp được để trống biển số. Nếu nhập, tối đa 20 ký tự."
                    else "Biển số bắt buộc, tối đa 20 ký tự.", fontSize = 12.sp, color = FoodExpressMuted)
                RegisterInfoRow("!", "Thông tin xét duyệt", "Thông tin phương tiện được sử dụng để xác minh và xét duyệt tài xế.")
            }
            RegisterError(state.message)
            if (step == 1) {
                RegisterPrimaryButton("Tiếp theo  →", onClick = advance, loading = state.loading)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, enabled = !state.loading,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Quay lại") }
                    RegisterPrimaryButton("Gửi đăng ký", onClick = advance, loading = state.loading, modifier = Modifier.weight(1.5f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// Shared only by the four Register screens; existing Login/Onboarding layouts stay unchanged.
@Composable
internal fun RegisterPage(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    loading: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (onBack != null || title != null) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    if (onBack != null) IconButton(onClick = onBack, enabled = !loading,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                            .semantics { contentDescription = "Quay lại" }) { Text("←", fontSize = 26.sp, color = FoodExpressInk) }
                    if (title != null) Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = FoodExpressInk, modifier = Modifier.align(Alignment.Center))
                }
            }
            content()
        }
    }
}

@Composable
internal fun RegisterPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    Button(onClick = onClick, enabled = !loading, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (loading) "Đang gửi…" else text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun RegisterInfoRow(symbol: String, title: String, body: String, pending: Boolean = false, framed: Boolean = true) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = if (pending) Color(0xFFFFF2DA) else Color.White,
        border = if (framed) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1E8E7)) else null) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFFFFE7E5), CircleShape), contentAlignment = Alignment.Center) {
                Text(symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = if (pending) MaterialTheme.colorScheme.primary else FoodExpressInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(body, color = FoodExpressMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun RegisterError(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun RegisterSteps(step: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        listOf("Thông tin tài khoản", "Thông tin phương tiện").forEachIndexed { index, title ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val active = index + 1 <= step
                Box(Modifier.size(28.dp).background(if (active) MaterialTheme.colorScheme.primary else Color(0xFFE2E5E9), CircleShape),
                    contentAlignment = Alignment.Center) { Text(if (index == 0 && step == 2) "✓" else "${index + 1}", color = if (active) Color.White else FoodExpressInk) }
                Text(title, fontSize = 12.sp, color = FoodExpressMuted)
            }
        }
    }
}

@Composable
private fun RegisterField(
    field: String, label: String, value: String, state: DriverRegisterUiState, onChange: (String, String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false,
    last: Boolean = false, onDone: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val error = state.fieldErrors[field]
    OutlinedTextField(
        value = value, onValueChange = { onChange(field, it) }, modifier = Modifier.fillMaxWidth(),
        enabled = !state.loading, singleLine = true, label = { Text(label, fontSize = 14.sp) },
        leadingIcon = { RegisterFieldIcon(field) },
        trailingIcon = if (password) {
            { IconButton(onClick = { visible = !visible }, enabled = !state.loading,
                modifier = Modifier.semantics { contentDescription = if (visible) "Ẩn $label" else "Hiện $label" }) { RegisterEye(visible) } }
        } else null,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { onDone() }),
        isError = error != null, supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FoodExpressInk, unfocusedTextColor = FoodExpressInk,
            unfocusedBorderColor = Color(0xFFDDE2E6), focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = FoodExpressMuted,
        ),
    )
}

@Composable
private fun VehicleOption(type: DriverVehicleType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFDDE2E6)
    Row(Modifier.fillMaxWidth().border(1.dp, color, RoundedCornerShape(12.dp))
        .background(Color.White, RoundedCornerShape(12.dp))
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Canvas(Modifier.size(28.dp)) {
            val ink = FoodExpressInk; val stroke = Stroke(1.5.dp.toPx())
            drawCircle(ink, size.width * .13f, Offset(size.width * .20f, size.height * .75f), style = stroke)
            drawCircle(ink, size.width * .13f, Offset(size.width * .80f, size.height * .75f), style = stroke)
            if (type == DriverVehicleType.CAR) {
                drawRoundRect(ink, Offset(size.width * .08f, size.height * .30f), Size(size.width * .84f, size.height * .38f), CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(ink, Offset(size.width * .25f, size.height * .30f), Offset(size.width * .35f, size.height * .12f), strokeWidth = stroke.width)
                drawLine(ink, Offset(size.width * .35f, size.height * .12f), Offset(size.width * .65f, size.height * .12f), strokeWidth = stroke.width)
            } else {
                val path = Path().apply {
                    moveTo(size.width * .20f, size.height * .75f); lineTo(size.width * .40f, size.height * .30f)
                    lineTo(size.width * .60f, size.height * .75f); close()
                    moveTo(size.width * .40f, size.height * .30f); lineTo(size.width * .70f, size.height * .30f)
                    lineTo(size.width * .80f, size.height * .75f)
                }
                drawPath(path, ink, style = stroke)
                if (type == DriverVehicleType.MOTORBIKE) drawRoundRect(ink, Offset(size.width * .30f, size.height * .42f), Size(size.width * .33f, size.height * .18f), CornerRadius(2.dp.toPx()))
            }
        }
        Text(type.label, color = FoodExpressInk, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RegisterEye(visible: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.5.dp.toPx())
        val path = Path().apply {
            moveTo(0f, size.height * .5f); quadraticTo(size.width * .5f, 0f, size.width, size.height * .5f)
            quadraticTo(size.width * .5f, size.height, 0f, size.height * .5f)
        }
        drawPath(path, FoodExpressInk, style = stroke)
        drawCircle(FoodExpressInk, size.width * .12f, style = stroke)
        if (!visible) drawLine(FoodExpressInk, Offset.Zero, Offset(size.width, size.height), strokeWidth = stroke.width)
    }
}

@Composable
private fun RegisterFieldIcon(field: String) {
    Canvas(Modifier.size(20.dp)) {
        val ink = FoodExpressInk; val stroke = Stroke(1.5.dp.toPx())
        when (field) {
            "fullName" -> {
                drawCircle(ink, size.width * .18f, Offset(size.width * .5f, size.height * .25f), style = stroke)
                drawArc(ink, 180f, 180f, false, Offset(size.width * .1f, size.height * .52f), Size(size.width * .8f, size.height * .8f), style = stroke)
            }
            "email" -> {
                drawRect(ink, Offset(0f, size.height * .15f), Size(size.width, size.height * .7f), style = stroke)
                val path = Path().apply { moveTo(0f, size.height * .15f); lineTo(size.width * .5f, size.height * .55f); lineTo(size.width, size.height * .15f) }
                drawPath(path, ink, style = stroke)
            }
            "password", "confirmPassword" -> {
                drawRoundRect(ink, Offset(size.width * .15f, size.height * .4f), Size(size.width * .7f, size.height * .55f), CornerRadius(2.dp.toPx()), style = stroke)
                drawArc(ink, 180f, 180f, false, Offset(size.width * .3f, 0f), Size(size.width * .4f, size.height * .75f), style = stroke)
            }
            else -> {
                drawRoundRect(ink, Offset(size.width * .1f, size.height * .1f), Size(size.width * .8f, size.height * .8f), CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(ink, Offset(size.width * .35f, size.height * .72f), Offset(size.width * .65f, size.height * .72f), strokeWidth = stroke.width)
            }
        }
    }
}
