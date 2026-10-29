package vn.hutech.foodexpress.driver.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressInk
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressMuted

@Composable
fun DriverLoginScreen(state: DriverAuthUiState, onLogin: (String, String, Boolean) -> Unit, onInputChanged: () -> Unit, onRegister: () -> Unit) {
    var identifier by rememberSaveable { mutableStateOf("") }
    // Never put the password in SavedState, preferences or a navigation argument.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberLogin by rememberSaveable { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        if (!state.loading) {
            focus.clearFocus()
            keyboard?.hide()
            passwordVisible = false
            onLogin(identifier, password, rememberLogin)
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val headerHeight = (maxHeight * 0.50f).coerceIn(210.dp, 380.dp)
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.driver_login_header),
                contentDescription = "Food Express – Giao nhanh, trao vị ngon. Tài xế giao hàng.",
                modifier = Modifier.fillMaxWidth().height(headerHeight),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.BottomCenter,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it; onInputChanged() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        singleLine = true,
                        label = { Text("Số điện thoại hoặc email", fontSize = 14.sp) },
                        leadingIcon = { LoginIcon(LoginIconType.Phone) },
                        isError = state.identifierError != null,
                        supportingText = state.identifierError?.let { error -> { Text(error) } },
                        shape = RoundedCornerShape(12.dp),
                        colors = loginFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; onInputChanged() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.loading,
                        singleLine = true,
                        label = { Text("Mật khẩu", fontSize = 14.sp) },
                        leadingIcon = { LoginIcon(LoginIconType.Lock) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !state.loading,
                                modifier = Modifier.semantics {
                                    contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                },
                            ) { LoginIcon(if (passwordVisible) LoginIconType.EyeOpen else LoginIconType.EyeClosed) }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = state.passwordError != null,
                        supportingText = state.passwordError?.let { error -> { Text(error) } },
                        shape = RoundedCornerShape(12.dp),
                        colors = loginFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(
                            value = rememberLogin, enabled = !state.loading, role = Role.Checkbox,
                            onValueChange = { rememberLogin = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = rememberLogin, onCheckedChange = null, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp))
                        Text("Lưu đăng nhập", color = FoodExpressInk, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    state.message?.let { message ->
                        Text(
                            message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Button(
                        onClick = { submit() }, enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(if (state.loading) "Đang đăng nhập…" else "Đăng nhập", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Chưa có tài khoản?", color = FoodExpressMuted, fontSize = 14.sp)
                        TextButton(onClick = onRegister, enabled = !state.loading) { Text("Đăng ký", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FoodExpressInk, unfocusedTextColor = FoodExpressInk,
    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFFDDE2E6),
    focusedLabelColor = FoodExpressInk, unfocusedLabelColor = FoodExpressMuted,
)

private enum class LoginIconType { Phone, Lock, EyeOpen, EyeClosed }

@Composable
private fun LoginIcon(type: LoginIconType) {
    Canvas(Modifier.size(22.dp)) {
        val ink = FoodExpressInk
        val stroke = Stroke(width = 1.5.dp.toPx())
        when (type) {
            LoginIconType.Lock -> {
                drawRoundRect(ink, Offset(size.width * .20f, size.height * .42f), Size(size.width * .60f, size.height * .50f), CornerRadius(2.dp.toPx()), style = stroke)
                drawArc(ink, 180f, 180f, false, Offset(size.width * .33f, size.height * .08f), Size(size.width * .34f, size.height * .60f), style = stroke)
                drawLine(ink, Offset(size.width * .5f, size.height * .61f), Offset(size.width * .5f, size.height * .75f), strokeWidth = stroke.width)
            }
            LoginIconType.Phone -> {
                val path = Path().apply {
                    moveTo(size.width * .25f, size.height * .10f)
                    cubicTo(0f, size.height * .30f, size.width * .25f, size.height * .80f, size.width * .70f, size.height * .90f)
                    lineTo(size.width * .90f, size.height * .72f)
                    lineTo(size.width * .66f, size.height * .56f)
                    lineTo(size.width * .55f, size.height * .68f)
                    quadraticTo(size.width * .32f, size.height * .58f, size.width * .30f, size.height * .38f)
                    lineTo(size.width * .42f, size.height * .26f)
                    close()
                }
                drawPath(path, ink, style = stroke)
            }
            LoginIconType.EyeOpen, LoginIconType.EyeClosed -> {
                val path = Path().apply {
                    moveTo(0f, size.height * .50f)
                    quadraticTo(size.width * .50f, 0f, size.width, size.height * .50f)
                    quadraticTo(size.width * .50f, size.height, 0f, size.height * .50f)
                }
                drawPath(path, ink, style = stroke)
                drawCircle(ink, size.width * .12f, style = stroke)
                if (type == LoginIconType.EyeClosed) drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = stroke.width)
            }
        }
    }
}
