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
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardScreen
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardUiState

// The existing session destination now hosts Dashboard after authentication.
@Composable
fun DriverSessionScreen(
    state: DriverAuthUiState,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit,
    dashboardState: DriverDashboardUiState,
    onDashboardRetry: () -> Unit,
    onReceivingOrders: (Boolean) -> Unit,
) {
    if (state.phase == DriverAuthPhase.AUTHENTICATED) {
        DriverDashboardScreen(dashboardState, onDashboardRetry, onReceivingOrders, onProfile, onLogout)
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.phase) {
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
