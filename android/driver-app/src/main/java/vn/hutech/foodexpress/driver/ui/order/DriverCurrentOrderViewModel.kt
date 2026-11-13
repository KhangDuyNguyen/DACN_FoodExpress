package vn.hutech.foodexpress.driver.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.order.*
import vn.hutech.foodexpress.driver.ui.offer.DriverOffersViewModel

data class DriverCurrentOrderUiState(
    val order: DriverCurrentOrder? = null,
    val verified: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    val ownerId: Long? = null,
    val deliverySending: Boolean = false,
    val deliveryPending: PendingDelivery? = null,
    val deliveryMessage: String? = null,
    val completion: DriverDeliveryCompletion? = null,
)

data class PendingDelivery(val orderId: Long, val orderCode: String, val target: String)
// A receipt contains no recipient/address data and survives currentOrder=null only in the same session.
data class DriverDeliveryCompletion(val orderId: Long, val orderCode: String, val deliveredAt: LocalDateTime)

fun nextDeliveryStatus(status: String): String? = when (status) {
    "PREPARING", "READY_FOR_PICKUP" -> "PICKED_UP"
    "PICKED_UP" -> "DELIVERING"
    "DELIVERING" -> "DELIVERED"
    else -> null
}

private fun deliveryStep(status: String) = when (status) {
    "PREPARING", "READY_FOR_PICKUP" -> 0
    "PICKED_UP" -> 1
    "DELIVERING" -> 2
    "DELIVERED" -> 3
    else -> -1
}

@HiltViewModel
class DriverCurrentOrderViewModel @Inject constructor(private val source: DriverCurrentOrderSource) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverCurrentOrderUiState())
    val state = mutableState.asStateFlow()
    private var owner: Long? = null
    private var authenticated = false
    private var foreground = false
    private var generation = 0L
    private var loop: Job? = null
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    private var offers: DriverOffersViewModel? = null
    private var decisions: Job? = null
    private var deliveryJob: Job? = null
    private var deliveryEpoch = 0L
    private var deliveryFloor: DriverDeliveryStatus? = null

    // Activity-owned bridge: apply the busy gate before publishing GET results.
    fun connectOffers(model: DriverOffersViewModel) {
        if (offers === model) return
        offers = model
        model.requireCurrentOrderVerification()
        decisions?.cancel()
        decisions = viewModelScope.launch {
            var previous: Long? = null
            model.state.collect { offer ->
                if (offer.acceptedOrderId != null && offer.acceptedOrderId != previous) restart()
                previous = offer.acceptedOrderId
            }
        }
    }

    fun activateSession(id: Long) {
        if (owner != id) { clear(); owner = id }
        mutableState.value = state.value.copy(ownerId = id)
        if (!authenticated) { authenticated = true; offers?.invalidateCurrentOrder() }
        start()
    }
    fun pauseSessionVerification() {
        authenticated = false
        stop(); offers?.invalidateCurrentOrder()
        mutableState.value = state.value.copy(ownerId = null, order = null, verified = false, loading = false, error = null)
    }
    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (active) restart() else stop()
    }
    fun clear() {
        deliveryEpoch++; deliveryJob?.cancel(); deliveryJob = null; deliveryFloor = null
        stop(); owner = null; authenticated = false
        offers?.invalidateCurrentOrder()
        mutableState.value = DriverCurrentOrderUiState()
    }
    fun refresh() {
        if (!canLoad() || state.value.loading || state.value.deliverySending) return
        mutableState.value = state.value.copy(loading = true)
        refreshes.trySend(Unit)
        start()
    }
    private fun canLoad() = owner != null && authenticated && foreground

    fun changeDeliveryStatus(target: String) {
        val current = state.value
        val order = current.order ?: return
        if (!canLoad() || current.ownerId != owner || !current.verified || current.error != null ||
            current.deliverySending || current.deliveryPending != null || current.sessionExpired || order.orderId <= 0 ||
            nextDeliveryStatus(order.status) != target) return
        sendDelivery(PendingDelivery(order.orderId, order.orderCode, target))
    }

    // Explicit user retry only; never advance to a different target or retry from polling/timers.
    fun retryPendingDelivery() {
        val current = state.value
        val pending = current.deliveryPending ?: return
        if (!canLoad() || current.ownerId != owner || current.deliverySending || current.loading || current.sessionExpired) return
        sendDelivery(pending)
    }

    fun dismissCompletion() { mutableState.value = state.value.copy(completion = null) }

    private fun sendDelivery(pending: PendingDelivery) {
        stop(); offers?.invalidateCurrentOrder()
        val epoch = deliveryEpoch
        val account = owner
        mutableState.value = state.value.copy(deliverySending = true, deliveryPending = pending, deliveryMessage = null)
        deliveryJob = viewModelScope.launch {
            val result = try {
                withTimeoutOrNull(13_000) { source.changeStatus(pending.orderId, pending.target) }
                    ?: DriverDeliveryResult.Uncertain("Chưa nhận được kết quả. Vui lòng kiểm tra lại thao tác.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DriverDeliveryResult.Uncertain("Kết nối gián đoạn. Chưa xác định được kết quả cập nhật.") }
            ensureActive()
            if (epoch != deliveryEpoch || owner != account) return@launch
            when (result) {
                is DriverDeliveryResult.Updated -> {
                    val value = result.value
                    if (value.orderId != pending.orderId || value.status != pending.target ||
                        ((value.status == "DELIVERED") != (value.deliveredAt != null))) {
                        mutableState.value = state.value.copy(verified = false, deliverySending = false,
                            deliveryMessage = "Phản hồi chưa đủ để xác nhận kết quả. Vui lòng kiểm tra lại.")
                    } else if (value.status == "DELIVERED") {
                        deliveryFloor = null
                        mutableState.value = state.value.copy(order = null, verified = false, error = null,
                            deliverySending = false, deliveryPending = null, deliveryMessage = null,
                            completion = DriverDeliveryCompletion(value.orderId, pending.orderCode, value.deliveredAt!!))
                    } else {
                        deliveryFloor = value
                        val order = state.value.order?.takeIf { it.orderId == value.orderId }
                            ?.copy(status = value.status, updatedAt = value.updatedAt)
                        mutableState.value = state.value.copy(order = order, verified = authenticated && order != null,
                            error = null, deliverySending = false, deliveryPending = null, deliveryMessage = null)
                    }
                }
                DriverDeliveryResult.SessionExpired, DriverDeliveryResult.Forbidden -> {
                    deliveryFloor = null; authenticated = false
                    mutableState.value = DriverCurrentOrderUiState(sessionExpired = true,
                        error = "Phiên đăng nhập hoặc quyền thao tác cần được xác minh lại.")
                }
                is DriverDeliveryResult.Rejected -> mutableState.value = state.value.copy(verified = false,
                    deliverySending = false, deliveryPending = null, deliveryMessage = result.message)
                is DriverDeliveryResult.Uncertain -> mutableState.value = state.value.copy(verified = false,
                    deliverySending = false, deliveryMessage = result.message)
            }
            // Start a fresh GET after the command; generation guards reject any pre-command GET.
            start()
        }
    }

    private fun loaded(result: CurrentOrderResult.Loaded, revision: Long?) {
        var order = result.order
        val pending = state.value.deliveryPending
        if (pending != null) {
            if (order != null && order.orderId == pending.orderId && deliveryStep(order.status) >= deliveryStep(pending.target)) {
                mutableState.value = state.value.copy(deliveryPending = null, deliveryMessage = "Đã xác minh trạng thái đơn hàng.")
            } else {
                // Null alone cannot prove a lost DELIVERED response (cancellation/ownership may also change).
                offers?.invalidateCurrentOrder()
                mutableState.value = state.value.copy(loading = false, verified = false)
                return
            }
        }
        if (order != null && order.orderId == state.value.completion?.orderId) {
            mutableState.value = state.value.copy(loading = false, verified = false)
            return
        }
        val floor = deliveryFloor
        if (order != null && floor?.orderId == order.orderId && deliveryStep(order.status) < deliveryStep(floor.status)) {
            // Keep the newer server-confirmed PATCH result; never regress because a GET snapshot is stale.
            order = order.copy(status = floor.status, updatedAt = floor.updatedAt)
        }
        if (revision != null) offers?.confirmCurrentOrder(order?.orderId, revision)
        mutableState.value = state.value.copy(order = order, verified = true, loading = false, error = null,
            sessionExpired = false, ownerId = owner)
    }
    private fun stop() {
        generation++
        loop?.cancel(); loop = null
        mutableState.value = state.value.copy(loading = false)
    }
    private fun restart() {
        stop(); offers?.invalidateCurrentOrder()
        mutableState.value = state.value.copy(verified = false)
        start()
    }
    private fun start() {
        if (!canLoad() || state.value.deliverySending || loop?.isActive == true) return
        while (refreshes.tryReceive().isSuccess) { /* discard old session wake-ups */ }
        val session = generation
        loop = viewModelScope.launch {
            while (isActive && session == generation && canLoad()) {
                val revision = offers?.currentOrderRevision
                mutableState.value = state.value.copy(loading = true)
                val result = try { source.load() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { CurrentOrderResult.Failed("Không thể đồng bộ đơn hiện tại. Vui lòng thử lại.") }
                ensureActive()
                if (session != generation || !canLoad()) break
                if (revision != offers?.currentOrderRevision) {
                    // A POST began after this GET. Never publish the pre-POST snapshot.
                    mutableState.value = state.value.copy(loading = false, verified = false)
                    continue
                }
                when (result) {
                    is CurrentOrderResult.Loaded -> {
                        loaded(result, revision)
                    }
                    CurrentOrderResult.SessionExpired -> {
                        deliveryEpoch++; deliveryJob?.cancel(); deliveryJob = null; deliveryFloor = null
                        offers?.invalidateCurrentOrder(); authenticated = false
                        mutableState.value = DriverCurrentOrderUiState(sessionExpired = true, error = "Phiên đăng nhập cần xác minh lại.")
                    }
                    is CurrentOrderResult.Failed -> {
                        offers?.invalidateCurrentOrder()
                        mutableState.value = state.value.copy(verified = false, loading = false, error = result.message)
                    }
                }
                withTimeoutOrNull(15_000) { refreshes.receive() }
            }
        }
    }
}
