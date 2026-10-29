package vn.hutech.foodexpress.driver.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressInk
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressMuted

@Composable
fun DriverRegisterIntroScreen(onBegin: () -> Unit, onLogin: () -> Unit) {
    BackHandler(onBack = onLogin)
    RegisterPage(onBack = onLogin) {
        Image(painterResource(R.drawable.driver_register_brand), contentDescription = "Food Express – Giao nhanh, trao vị ngon",
            modifier = Modifier.fillMaxWidth().height(108.dp), contentScale = ContentScale.Fit)
        Image(painterResource(R.drawable.driver_register_intro_artwork), contentDescription = "Tài xế Food Express",
            modifier = Modifier.fillMaxWidth().aspectRatio(263f / 141f), contentScale = ContentScale.FillWidth)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Đăng ký tài xế", color = FoodExpressInk, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Trở thành đối tác tài xế của Food Express và bắt đầu hành trình cùng chúng tôi.",
                color = FoodExpressMuted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                Column {
                    RegisterInfoRow("↔", "Chủ động thời gian", "Linh hoạt theo lịch của bạn.", framed = false)
                    RegisterInfoRow("$", "Thu nhập ổn định", "Nhiều cơ hội nhận đơn.", framed = false)
                    RegisterInfoRow("✓", "Quy trình đơn giản", "Đăng ký rõ ràng, xét duyệt minh bạch.", framed = false)
                }
            }
            RegisterPrimaryButton("Bắt đầu đăng ký", onClick = onBegin)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Đã có tài khoản?", fontSize = 14.sp, color = FoodExpressMuted)
                TextButton(onClick = onLogin) { Text("Đăng nhập", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}
