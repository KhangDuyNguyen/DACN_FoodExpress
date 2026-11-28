package vn.hutech.foodexpress.customer.notification

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import vn.hutech.foodexpress.core.notification.DeviceBindingSync
import vn.hutech.foodexpress.core.notification.RetrofitInbox
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
internal class CustomerDeviceRegistration @Inject constructor(
    @param:ApplicationContext private val context: Context,
    retrofit: Retrofit,
) {
    private val transport = RetrofitInbox(retrofit)
    private val deviceToken = MutableStateFlow<String?>(null)

    /** Collect from a verified session owner, including while the app is in the background. */
    suspend fun syncWhileAuthenticated(accessTokens: StateFlow<String?>) {
        if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) return
        val messaging = FirebaseMessaging.getInstance()
        DeviceBindingSync(transport::registerDevice, transport::removeDevice).collect(accessTokens, deviceToken) {
            withTimeoutOrNull(5000) { messaging.token.settled() }
        }
    }

    fun onTokenRefreshed(token: String) { deviceToken.value = token }
}

@AndroidEntryPoint
internal class CustomerMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registration: CustomerDeviceRegistration
    @Inject lateinit var runtime: CustomerNotificationRuntime

    override fun onNewToken(token: String) { registration.onTokenRefreshed(token) }

    override fun onMessageReceived(message: RemoteMessage) {
        // Legacy notification messages retain Firebase's existing background handling.
        if (message.data.isEmpty()) return
        runBlocking {
            try {
                withTimeoutOrNull(6000) { runtime.receive(message.data) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No verified content to display. The REST inbox remains available.
            }
        }
    }
}

private suspend fun <T> Task<T>.settled(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
    }
}
