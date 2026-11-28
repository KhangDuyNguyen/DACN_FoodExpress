package vn.hutech.foodexpress.driver.notification

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.core.notification.*
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.offer.DriverOfferRepository
import vn.hutech.foodexpress.driver.data.offer.OffersResult
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.ui.offer.OfferDeadline

internal enum class OfferNotice { ACTIVE, EXPIRED, UNKNOWN }

internal fun verifyOfferNotice(item: InboxItem, result: OffersResult): OfferNotice {
    if (result !is OffersResult.Loaded) return OfferNotice.UNKNOWN
    return if (offerNotificationDeadline(item, result) != null) OfferNotice.ACTIVE else OfferNotice.EXPIRED
}

internal fun offerNotificationDeadline(item: InboxItem, result: OffersResult): OfferDeadline? {
    if (result !is OffersResult.Loaded) return null
    val offer = result.offers.firstOrNull {
        it.offerId == item.data.offerId && it.orderId == item.data.orderId && it.expiresAt > result.serverTime
    } ?: return null
    return OfferDeadline.from(result.serverTime, offer.expiresAt, result.requestStartedMillis)
}

@Singleton
internal class DriverNotificationRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: DriverAuthRepository,
    private val transport: RetrofitInbox,
    private val offers: DriverOfferRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val devices = MutableStateFlow<String?>(null)
    val arrivals = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private val alerted = linkedSetOf<Pair<Boolean, Long>>()
    @Volatile var foreground = false
    val configured get() = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

    init {
        scope.launch {
            auth.accessTokens.collect {
                synchronized(alerted) { alerted.clear() }
                InboxSystemNotifications.clear(context)
            }
        }
        if (configured) scope.launch {
            DeviceBindingSync(transport::registerDevice, transport::removeDevice).collect(auth.accessTokens, devices) {
                withTimeoutOrNull(5000) { FirebaseMessaging.getInstance().token.resultOrNull() }
            }
        }
    }

    fun tokenChanged(token: String) { devices.value = token }

    suspend fun receive(data: Map<String, String>) {
        val signal = InboxSignal.parse(data) ?: return
        val (token, item) = transport.verifyForCurrentSession(signal, auth::currentAccessToken) ?: return
        val current = { auth.currentAccessToken() == token }
        val offer = item.eventType == "OFFER_CREATED"
        val deadline = if (offer) {
            offerNotificationDeadline(item, offers.load()) ?: return
        } else null
        if (deadline != null && deadline.elapsedDeadlineMillis <= SystemClock.elapsedRealtime()) return
        if (!current()) return
        arrivals.tryEmit(offer)
        // Foreground offer UI keeps its existing polling/countdown and owns its alert.
        if (offer && foreground || !InboxSystemNotifications.canPost(context)) return
        val alertId = if (offer) item.data.offerId ?: return else item.id
        val first = synchronized(alerted) {
            if (alerted.size >= 200) alerted.remove(alerted.first())
            alerted.add(offer to alertId)
        }
        if (first) InboxSystemNotifications.show(context, MainActivity::class.java, item,
            if (offer) "driver_new_offer" else "driver_inbox",
            if (offer) "Lời mời giao đơn" else "Thông báo giao hàng", offer,
            expiresAtElapsedMillis = deadline?.elapsedDeadlineMillis,
            onlyWhileCurrent = { current() && (!offer || !foreground) })
    }
}

@AndroidEntryPoint
internal class DriverMessagingService : FirebaseMessagingService() {
    @Inject lateinit var notifications: DriverNotificationRuntime

    override fun onNewToken(token: String) { notifications.tokenChanged(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        runBlocking {
            try {
                withTimeoutOrNull(6000) { notifications.receive(message.data) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Inbox remains the source of truth when verification is unavailable.
            }
        }
    }
}

internal suspend fun <T> Task<T>.resultOrNull(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
    }
}

@HiltViewModel
internal class DriverNotificationViewModel @Inject constructor(
    private val auth: DriverAuthRepository, private val transport: RetrofitInbox,
    private val offers: DriverOfferRepository, private val runtime: DriverNotificationRuntime,
) : ViewModel() {
    private var authenticated = false
    private var tapJob: Job? = null
    val inbox = InboxController(transport, viewModelScope) { authenticated && auth.currentAccessToken() == it }
    val notice = MutableStateFlow<String?>(null)
    val pushConfigured get() = runtime.configured
    val offerSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openVerifiedOffer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch { auth.accessTokens.collect { syncSession() } }
        viewModelScope.launch { runtime.arrivals.collect { offer -> inbox.refresh(); if (offer) offerSignal.tryEmit(Unit) } }
    }

    fun authenticated(value: Boolean) {
        if (authenticated == value) return
        authenticated = value
        syncSession()
    }

    private fun syncSession() {
        tapJob?.cancel()
        notice.value = null
        inbox.bind(auth.currentAccessToken().takeIf { authenticated })
    }

    fun foreground(value: Boolean) {
        runtime.foreground = value
        if (value) inbox.refresh()
    }

    fun open(id: Long) = inbox.open(id, ::checkOffer)

    fun tap(id: Long) {
        val token = auth.currentAccessToken()?.takeIf { authenticated } ?: return
        tapJob?.cancel()
        tapJob = viewModelScope.launch {
            try {
                val item = transport.findItem(token, id) ?: return@launch
                if (!authenticated || auth.currentAccessToken() != token) return@launch
                transport.markRead(token, id)
                if (!authenticated || auth.currentAccessToken() != token) return@launch
                inbox.refresh()
                checkOffer(item, token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (authenticated && auth.currentAccessToken() == token) inbox.refresh()
            }
        }
    }

    private suspend fun checkOffer(item: InboxItem, token: String) {
        notice.value = null
        if (item.eventType != "OFFER_CREATED") return
        val result = verifyOfferNotice(item, offers.load())
        if (!authenticated || auth.currentAccessToken() != token) return
        when (result) {
            OfferNotice.ACTIVE -> {
                notice.value = "Lời mời còn hiệu lực. Xem lời mời trong màn hình nhận đơn."
                openVerifiedOffer.tryEmit(Unit)
                offerSignal.tryEmit(Unit)
            }
            OfferNotice.EXPIRED -> notice.value = "Đơn hàng đã hết hiệu lực"
            OfferNotice.UNKNOWN -> notice.value = "Chưa xác minh được lời mời. Vui lòng thử lại."
        }
    }
}
