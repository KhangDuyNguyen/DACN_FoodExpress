package vn.hutech.foodexpress.customer.notification

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.core.notification.*
import vn.hutech.foodexpress.customer.MainActivity

@Singleton
internal class CustomerNotificationRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transport: RetrofitInbox,
    registration: CustomerDeviceRegistration,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val access = MutableStateFlow<String?>(null)
    val arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val alerted = linkedSetOf<Long>()
    val configured get() = FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

    init { scope.launch { registration.syncWhileAuthenticated(access) } }

    fun token() = access.value

    fun bind(token: String?) {
        if (access.value == token) return
        access.value = token
        synchronized(alerted) { alerted.clear() }
        InboxSystemNotifications.clear(context)
    }

    suspend fun receive(data: Map<String, String>) {
        val signal = InboxSignal.parse(data) ?: return
        val (token, item) = transport.verifyForCurrentSession(signal, ::token) ?: return
        arrivals.tryEmit(Unit)
        if (!InboxSystemNotifications.canPost(context)) return
        val first = synchronized(alerted) {
            if (alerted.size >= 200) alerted.remove(alerted.first())
            alerted.add(item.id)
        }
        if (first) InboxSystemNotifications.show(context, MainActivity::class.java, item,
            "customer_order_inbox", "Thông báo đơn hàng", offer = false, onlyWhileCurrent = { access.value == token })
    }
}

@HiltViewModel
internal class CustomerNotificationViewModel @Inject constructor(
    private val runtime: CustomerNotificationRuntime, private val transport: RetrofitInbox,
) : ViewModel() {
    private var session: Job? = null
    private var tapJob: Job? = null
    val inbox = InboxController(transport, viewModelScope) { runtime.token() == it }
    val pushConfigured get() = runtime.configured

    init { viewModelScope.launch { runtime.arrivals.collect { inbox.refresh() } } }

    fun connect(accessTokens: StateFlow<String?>?) {
        session?.cancel()
        update(null)
        if (accessTokens != null) session = viewModelScope.launch { accessTokens.collect(::update) }
    }

    private fun update(token: String?) {
        tapJob?.cancel()
        runtime.bind(token)
        inbox.bind(token)
    }

    fun tap(id: Long, onOrder: (Long) -> Unit) {
        val token = runtime.token() ?: return
        tapJob?.cancel()
        tapJob = viewModelScope.launch {
            try {
                val item = transport.findItem(token, id) ?: return@launch
                if (runtime.token() != token) return@launch
                transport.markRead(token, id)
                if (runtime.token() != token) return@launch
                inbox.refresh()
                customerOrderId(item)?.let(onOrder)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (runtime.token() == token) inbox.refresh()
            }
        }
    }

    override fun onCleared() { runtime.bind(null) }
}

internal fun customerOrderId(row: InboxItem): Long? =
    row.data.orderId?.takeIf { row.type == "ORDER" && it > 0 }
