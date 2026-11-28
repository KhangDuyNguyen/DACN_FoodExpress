package vn.hutech.foodexpress.customer

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import vn.hutech.foodexpress.customer.navigation.CustomerAppNavigation
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import kotlinx.coroutines.flow.MutableStateFlow
import vn.hutech.foodexpress.core.notification.InboxSystemNotifications

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val inboxClick = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotification(intent)
        setContent {
            FoodExpressTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Pass the authenticated app-session source when the Customer auth flow is available.
                    CustomerAppNavigation(inboxClick = inboxClick, onConsumeClick = { inboxClick.value = null })
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
