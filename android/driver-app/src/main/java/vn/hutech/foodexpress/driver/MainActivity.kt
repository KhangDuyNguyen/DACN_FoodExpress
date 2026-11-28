package vn.hutech.foodexpress.driver

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import vn.hutech.foodexpress.core.notification.InboxSystemNotifications
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.navigation.DriverAppNavigation

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val inboxClick = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotification(intent)
        setContent {
            FoodExpressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DriverAppNavigation(inboxClick) { inboxClick.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureNotification(intent)
    }

    private fun captureNotification(intent: Intent) {
        inboxClick.value = intent.getLongExtra(InboxSystemNotifications.EXTRA_NOTIFICATION_ID, 0).takeIf { it > 0 }
        intent.removeExtra(InboxSystemNotifications.EXTRA_NOTIFICATION_ID)
    }
}
