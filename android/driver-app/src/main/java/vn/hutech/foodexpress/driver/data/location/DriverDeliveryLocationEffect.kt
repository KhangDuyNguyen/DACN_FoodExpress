package vn.hutech.foodexpress.driver.data.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthPhase
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthUiState
import vn.hutech.foodexpress.driver.ui.order.DriverCurrentOrderUiState

@Composable
internal fun DriverDeliveryLocationEffect(
    session: DriverAuthUiState,
    current: DriverCurrentOrderUiState,
    foreground: Boolean,
) {
    val context = LocalContext.current
    val userId = session.user?.id
    val orderId = current.order?.takeIf { it.status == "DELIVERING" }?.orderId
    val verified = userId != null && current.ownerId == userId && current.verified && !current.sessionExpired &&
        current.error == null
    var resumed by remember { mutableStateOf(false) }
    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { resumed = false }
    val permitted = DriverDeliveryLocationService.canReadGps(context)
    LaunchedEffect(session.phase, userId, verified, orderId, foreground, resumed, permitted) {
        when {
            !permitted || session.phase == DriverAuthPhase.SIGNED_OUT || (verified && orderId == null) ->
                DriverDeliveryLocationService.stop(context)
            foreground && resumed && session.phase == DriverAuthPhase.AUTHENTICATED && verified && orderId != null -> {
                DriverDeliveryLocationService.start(context, orderId)
                if (Build.VERSION.SDK_INT >= 33 && !notificationAsked &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationAsked = true
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
