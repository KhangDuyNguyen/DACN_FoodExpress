package vn.hutech.foodexpress.driver.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.hutech.foodexpress.driver.R
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressInk
import vn.hutech.foodexpress.driver.ui.onboarding.FoodExpressMuted

@Composable
fun DriverRegisterSuccessScreen(onLogin: () -> Unit) {
    BackHandler(onBack = onLogin)
    RegisterPage {
        Image(painterResource(R.drawable.driver_register_success_artwork), contentDescription = "Tài xế đã gửi hồ sơ thành công",
            modifier = Modifier.fillMaxWidth().height(232.dp), contentScale = ContentScale.Fit)
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Đăng ký thành công!", fontSize = 24.sp, color = FoodExpressInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("Hồ sơ tài xế của bạn đã được tạo và đang chờ xét duyệt.", color = FoodExpressMuted, textAlign = TextAlign.Center, fontSize = 15.sp)
            RegisterInfoRow("◷", "Đang chờ xét duyệt (PENDING)", "Bạn chưa thể bật nhận đơn khi hồ sơ chưa được duyệt.", pending = true)
            Text("Tiếp theo", color = FoodExpressInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            RegisterInfoRow("◎", "Đăng nhập", "Đăng nhập để xem hồ sơ tài xế và theo dõi trạng thái xét duyệt.")
            RegisterInfoRow("✓", "Sau khi được duyệt", "Bạn có thể bật nhận đơn khi đáp ứng các điều kiện hoạt động.")
            RegisterPrimaryButton("Về màn hình đăng nhập", onClick = onLogin)
        }
    }
}
