package vn.hutech.foodexpress.driver.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// A transparent integration destination for this Login step, not a Driver Dashboard.
@Composable
fun DriverSessionScreen(state: DriverAuthUiState, onRetry: () -> Unit, onLogout: () -> Unit, onProfile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.phase) {
            DriverAuthPhase.AUTHENTICATED -> {
                Text("Đăng nhập thành công!", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text("Xin chào ${state.user?.fullName.orEmpty()}", textAlign = TextAlign.Center)
                Text("Điểm đến tạm sau đăng nhập. Dashboard chưa được triển khai trong bước này.", textAlign = TextAlign.Center)
                Button(onClick = onProfile) { Text("Mở Hồ sơ tài xế") }
                TextButton(onClick = onLogout) { Text("Đăng xuất") }
            }
            DriverAuthPhase.RETRY_SESSION -> {
                Text(state.message.orEmpty(), textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Thử xác minh lại") }
                TextButton(onClick = onLogout) { Text("Đăng nhập tài khoản khác") }
            }
            else -> {
                CircularProgressIndicator()
                Text("Đang xác minh phiên đăng nhập…", textAlign = TextAlign.Center)
            }
        }
    }
}
