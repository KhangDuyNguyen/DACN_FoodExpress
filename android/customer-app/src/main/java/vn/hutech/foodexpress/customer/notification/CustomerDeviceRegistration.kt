package vn.hutech.foodexpress.customer.notification

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
internal class CustomerDeviceRegistration @Inject constructor(
    @ApplicationContext private val context: Context,
    retrofit: Retrofit,
) {
    private val api = retrofit.create(DeviceTokenApi::class.java)
    private val deviceToken = MutableStateFlow<String?>(null)

    /** Collect once from the app's customer-session owner, including while the app is in the background. */
    suspend fun syncWhileAuthenticated(accessTokens: StateFlow<String?>) {
        if (FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) return
        val messaging = FirebaseMessaging.getInstance()
        var registered: Binding? = null
        combine(accessTokens, deviceToken, ::Pair).collect { (access, device) ->
            val previous = registered
            if (access.isNullOrBlank()) {
                if (previous != null) {
                    remove(previous)
                    // Invalidate locally as well, including sign-out after the JWT has expired.
                    withTimeoutOrNull(8_000) { messaging.deleteToken().settled() }
                    registered = null
                    deviceToken.value = null
                }
            } else {
                val token = device ?: withTimeoutOrNull(8_000) { messaging.token.settled() }
                if (token.isNullOrBlank() || accessTokens.value != access) return@collect
                val next = Binding(access, token)
                if (previous == next) return@collect
                if (previous != null) remove(previous)
                registered = if (call { api.register("Bearer $access", DeviceToken(token)) }) next else null
            }
        }
    }

    internal fun onTokenRefreshed(token: String) {
        deviceToken.value = token
    }

    private suspend fun remove(binding: Binding) {
        call { api.remove("Bearer ${binding.access}", DeviceToken(binding.device)) }
    }

    private suspend fun call(request: suspend () -> Response<Void>): Boolean = withTimeoutOrNull(8_000) {
        try {
            val response = request()
            response.errorBody()?.close()
            response.code() == 204
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    } ?: false

    private data class Binding(val access: String, val device: String)
}

@AndroidEntryPoint
internal class CustomerMessagingService : FirebaseMessagingService() {
    @Inject lateinit var registration: CustomerDeviceRegistration

    override fun onNewToken(token: String) {
        registration.onTokenRefreshed(token)
    }
}

private suspend fun <T> Task<T>.settled(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
    }
}

private data class DeviceToken(val token: String)

private interface DeviceTokenApi {
    @POST("devices/token")
    suspend fun register(@Header("Authorization") access: String, @Body device: DeviceToken): Response<Void>

    @HTTP(method = "DELETE", path = "devices/token", hasBody = true)
    suspend fun remove(@Header("Authorization") access: String, @Body device: DeviceToken): Response<Void>
}
