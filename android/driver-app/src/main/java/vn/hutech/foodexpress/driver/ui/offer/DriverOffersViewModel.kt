package vn.hutech.foodexpress.driver.ui.offer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.offer.*

enum class DriverOfferPhase {
    IDLE, OFFER, SENDING_ACCEPT, SENDING_REJECT, ACCEPTED, REJECTED,
    EXPIRED, UNAVAILABLE, NETWORK_ERROR, ACCEPT_UNKNOWN, REJECT_UNKNOWN,
}

data class PendingOfferAction(val offerId: Long, val orderId: Long, val action: OfferAction)

data class DriverOffersUiState(
    val offer: DriverOffer? = null,
    val remainingSeconds: Int = 0,
    val phase: DriverOfferPhase = DriverOfferPhase.IDLE,
    val pending: PendingOfferAction? = null,
    val acceptedOrderId: Long? = null,
    val message: String? = null,
    val sessionExpired: Boolean = false,
) {
    val sending: Boolean get() = phase == DriverOfferPhase.SENDING_ACCEPT || phase == DriverOfferPhase.SENDING_REJECT
}

@HiltViewModel
class DriverOffersViewModel @Inject constructor(
    private val repository: DriverOffersSource,
    private val clock: OfferMonotonicClock,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverOffersUiState())
    val state = mutableState.asStateFlow()
    private var userId: Long? = null
    private var generation = 0L
    private var verified = false
    private var foreground = false
    private var online = false
    private var currentOrderVerificationRequired = false
    private var currentOrderVerified = false
    private var currentOrderId: Long? = null
    var currentOrderRevision: Long = 0
        private set
    private var poll: Job? = null
    private var ticker: Job? = null
    private var actionJob: Job? = null
    private var deadline: OfferDeadline? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val handled = mutableSetOf<Long>()
    private var latestServerTime: LocalDateTime? = null
    private val locallyExpired = mutableMapOf<Long, LocalDateTime>()
    private val expiryRefreshRequested = mutableSetOf<Long>()

    fun activateSession(id: Long) {
        if (userId != id) {
            clear()
            userId = id
            if (saved.get<Long>(OWNER) != id) {
                listOf(PENDING_ID, PENDING_ORDER, PENDING_ACTION, ACCEPTED_ORDER).forEach { saved.remove<Any>(it) }
            }
            saved[OWNER] = id
            val pendingId = saved.get<Long>(PENDING_ID)
            val pendingOrder = saved.get<Long>(PENDING_ORDER)
            val action = saved.get<String>(PENDING_ACTION)?.let { value -> OfferAction.entries.find { it.name == value } }
            val pending = if (pendingId != null && pendingOrder != null && action != null)
                PendingOfferAction(pendingId, pendingOrder, action) else null
            val accepted = saved.get<Long>(ACCEPTED_ORDER)
            mutableState.value = DriverOffersUiState(pending = pending, acceptedOrderId = accepted,
                phase = when {
                    pending?.action == OfferAction.ACCEPT -> DriverOfferPhase.ACCEPT_UNKNOWN
                    pending != null -> DriverOfferPhase.REJECT_UNKNOWN
                    accepted != null -> DriverOfferPhase.ACCEPTED
                    else -> DriverOfferPhase.IDLE
                }, message = if (pending != null) UNKNOWN_MESSAGE else if (accepted != null) acceptedMessage(accepted) else null)
        }
        verified = true
        mutableState.value = state.value.copy(sessionExpired = false)
        reconcilePolling()
    }

    fun pauseSessionVerification() { verified = false; stopPolling() }

    // Navigation connects current-order sync before activating the authenticated session.
    // Standalone 4A consumers retain their original behavior until connected.
    fun requireCurrentOrderVerification() {
        currentOrderVerificationRequired = true
        invalidateCurrentOrder()
    }

    fun invalidateCurrentOrder() {
        currentOrderVerified = false
        stopPolling()
        if (state.value.pending == null) {
            deadline = null
            mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                phase = if (state.value.phase == DriverOfferPhase.OFFER) DriverOfferPhase.IDLE else state.value.phase)
        }
    }

    fun confirmCurrentOrder(orderId: Long?, expectedRevision: Long) {
        if (expectedRevision != currentOrderRevision) return
        currentOrderVerified = true
        currentOrderId = orderId
        if (state.value.pending == null) {
            saved.remove<Any>(ACCEPTED_ORDER)
            val wasAccepted = state.value.phase == DriverOfferPhase.ACCEPTED
            mutableState.value = state.value.copy(acceptedOrderId = null,
                phase = if (wasAccepted) DriverOfferPhase.IDLE else state.value.phase,
                message = if (wasAccepted) null else state.value.message)
        }
        if (orderId != null && state.value.pending == null) {
            deadline = null
            mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                phase = if (state.value.phase == DriverOfferPhase.OFFER) DriverOfferPhase.IDLE else state.value.phase)
        }
        reconcilePolling()
    }

    fun setForeground(active: Boolean) {
        foreground = active
        reconcilePolling()
    }

    fun setOnline(confirmed: Boolean) {
        online = confirmed
        if (!confirmed && state.value.pending == null) {
            deadline = null
            mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                phase = if (state.value.phase == DriverOfferPhase.OFFER) DriverOfferPhase.IDLE else state.value.phase)
        }
        reconcilePolling()
    }

    fun clear() {
        generation++
        currentOrderRevision++
        currentOrderVerified = false; currentOrderId = null
        stopPolling()
        actionJob?.cancel(); actionJob = null
        userId = null; verified = false; online = false
        deadline = null; handled.clear()
        latestServerTime = null; locallyExpired.clear(); expiryRefreshRequested.clear()
        mutableState.value = DriverOffersUiState()
        // Preserve only non-sensitive pending IDs for the same account's saved-state restoration.
        // A new account cannot replay them; activateSession discards a different owner's IDs.
    }

    fun refresh() { if (canPoll()) refreshRequests.trySend(Unit) }

    fun accept() = decideNew(OfferAction.ACCEPT)
    fun reject() = decideNew(OfferAction.REJECT)

    fun retryPending() {
        val pending = state.value.pending ?: return
        if (!verified || !foreground || state.value.sending) return
        // The same POST verifies an ambiguous result even after its original countdown elapsed.
        perform(pending, verifying = true)
    }

    private fun decideNew(action: OfferAction) {
        val offer = state.value.offer ?: return
        if (!canPoll() || state.value.pending != null || state.value.sending) return
        if ((deadline?.remainingSeconds(clock.nowMillis()) ?: 0) == 0) { tick(); return }
        perform(PendingOfferAction(offer.offerId, offer.orderId, action), verifying = false)
    }

    private fun perform(pending: PendingOfferAction, verifying: Boolean) {
        stopPolling()
        currentOrderRevision++
        val session = generation
        saved[PENDING_ID] = pending.offerId
        saved[PENDING_ORDER] = pending.orderId
        saved[PENDING_ACTION] = pending.action.name
        mutableState.value = state.value.copy(pending = pending,
            phase = if (pending.action == OfferAction.ACCEPT) DriverOfferPhase.SENDING_ACCEPT else DriverOfferPhase.SENDING_REJECT,
            message = "Đang chờ Backend xác nhận…")
        startTicker()
        actionJob = viewModelScope.launch {
            val result = try {
                repository.decide(pending.action, pending.offerId, pending.orderId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OfferActionResult.Unknown(UNKNOWN_MESSAGE)
            }
            if (session != generation) return@launch
            currentOrderRevision++
            deadline = null
            when (result) {
                is OfferActionResult.Confirmed -> {
                    clearPending()
                    handled.add(pending.offerId)
                    val accepted = pending.action == OfferAction.ACCEPT
                    if (accepted) saved[ACCEPTED_ORDER] = result.orderId
                    mutableState.value = state.value.copy(offer = null, remainingSeconds = 0, pending = null,
                        phase = if (accepted) DriverOfferPhase.ACCEPTED else DriverOfferPhase.REJECTED,
                        acceptedOrderId = if (accepted) result.orderId else state.value.acceptedOrderId,
                        message = if (accepted) acceptedMessage(result.orderId) else "Đã từ chối lời mời. Trạng thái Online không bị thay đổi bởi thao tác này.")
                }
                OfferActionResult.Unavailable -> {
                    if (verifying && pending.action == OfferAction.ACCEPT) {
                        // Backend only replays ACCEPTED while Order is still PLACED. A later 409
                        // cannot disprove an earlier committed accept; GET never returns accepted offers.
                        unknown(pending, "Kết quả nhận đơn trước đó chưa xác định. Vui lòng xác minh lại hoặc liên hệ hỗ trợ để đối chiếu trạng thái đơn.")
                    } else {
                        clearPending(); handled.add(pending.offerId)
                        mutableState.value = state.value.copy(offer = null, remainingSeconds = 0, pending = null,
                            phase = DriverOfferPhase.UNAVAILABLE,
                            message = "Lời mời không còn khả dụng hoặc điều kiện thao tác đã thay đổi. Vui lòng tải lại.")
                    }
                }
                OfferActionResult.SessionExpired -> {
                    unknown(pending, "Phiên đăng nhập cần xác minh lại. Kết quả thao tác trước đó chưa xác định.")
                    verified = false
                    mutableState.value = state.value.copy(sessionExpired = true)
                }
                is OfferActionResult.Unknown -> unknown(pending, result.message)
            }
            reconcilePolling()
        }
    }

    private fun unknown(pending: PendingOfferAction, message: String) {
        mutableState.value = state.value.copy(offer = null, remainingSeconds = 0, pending = pending,
            phase = if (pending.action == OfferAction.ACCEPT) DriverOfferPhase.ACCEPT_UNKNOWN else DriverOfferPhase.REJECT_UNKNOWN,
            message = message)
    }

    private fun clearPending() {
        listOf(PENDING_ID, PENDING_ORDER, PENDING_ACTION).forEach { saved.remove<Any>(it) }
    }

    private fun canPoll() = userId != null && verified && foreground && online &&
        (!currentOrderVerificationRequired || (currentOrderVerified && currentOrderId == null)) &&
        state.value.pending == null && state.value.acceptedOrderId == null && !state.value.sessionExpired

    private fun reconcilePolling() {
        if (!canPoll()) {
            stopPolling()
            if (foreground && state.value.sending) startTicker()
            return
        }
        tick()
        if (poll?.isActive != true) {
            while (refreshRequests.tryReceive().isSuccess) { /* discard old wake-ups */ }
            val session = generation
            poll = viewModelScope.launch {
                while (isActive && session == generation && canPoll()) {
                    val result = try { repository.load() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { OffersResult.Failed("Không thể tải lời mời. Kiểm tra kết nối và thử lại.") }
                    ensureActive()
                    if (session != generation || !canPoll()) break
                    applyOffers(result)
                    withTimeoutOrNull(POLL_INTERVAL_MS) { refreshRequests.receive() }
                }
            }
        }
        startTicker()
    }

    private fun startTicker() {
        if (foreground && ticker?.isActive != true) ticker = viewModelScope.launch {
            while (isActive && foreground && userId != null && (canPoll() || state.value.sending)) {
                tick(); delay(100)
            }
        }
    }

    private fun applyOffers(result: OffersResult) {
        when (result) {
            is OffersResult.Loaded -> {
                if (latestServerTime?.let { result.serverTime < it } == true) return
                latestServerTime = result.serverTime
                val offer = result.offers.filter { it.offerId !in handled && it.expiresAt > result.serverTime
                    // Local expiry may be corrected only by a newer Backend snapshot, never a cached replay.
                    && locallyExpired[it.offerId]?.let { expiredAt -> result.serverTime > expiredAt } != false }
                    .sortedWith(compareBy<DriverOffer> { it.expiresAt }.thenBy { it.offerId }).firstOrNull()
                if (offer == null) {
                    if (state.value.offer != null) {
                        deadline = null
                        mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                            phase = DriverOfferPhase.UNAVAILABLE, message = "Lời mời không còn hiệu lực trên máy chủ.")
                    } else if (state.value.phase == DriverOfferPhase.NETWORK_ERROR) {
                        mutableState.value = state.value.copy(phase = DriverOfferPhase.IDLE, message = null)
                    }
                } else {
                    val next = OfferDeadline.from(result.serverTime, offer.expiresAt, result.requestStartedMillis)
                    deadline = if (state.value.offer?.offerId == offer.offerId && deadline != null)
                        OfferDeadline(minOf(deadline!!.elapsedDeadlineMillis, next.elapsedDeadlineMillis)) else next
                    mutableState.value = state.value.copy(offer = offer, phase = DriverOfferPhase.OFFER, message = null)
                    tick()
                }
            }
            OffersResult.SessionExpired -> {
                verified = false
                mutableState.value = state.value.copy(offer = null, remainingSeconds = 0, sessionExpired = true)
                stopPolling()
            }
            is OffersResult.Failed -> mutableState.value = state.value.copy(
                phase = DriverOfferPhase.NETWORK_ERROR, message = result.message)
        }
    }

    private fun tick() {
        val offer = state.value.offer ?: return
        val remaining = deadline?.remainingSeconds(clock.nowMillis()) ?: 0
        if (remaining == 0) {
            if (state.value.pending != null) {
                deadline = null
                mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                    message = "Đã qua thời hạn hiển thị; vẫn chờ Backend xác nhận thao tác đã gửi.")
                return
            }
            // A conservative local deadline is not a terminal Backend decision.
            // Keep confirmed Accept/Reject/Unavailable IDs handled, but allow a fresh GET to revalidate this ID.
            latestServerTime?.let { locallyExpired[offer.offerId] = it }
            deadline = null
            mutableState.value = state.value.copy(offer = null, remainingSeconds = 0,
                phase = DriverOfferPhase.EXPIRED, message = "Đã hết thời gian nhận lời mời ${offer.orderCode}. Backend quyết định hiệu lực cuối cùng.")
            // One immediate verification until a positive countdown returns; otherwise retain the 4s cadence.
            if (expiryRefreshRequested.add(offer.offerId)) refreshRequests.trySend(Unit)
        } else {
            expiryRefreshRequested.remove(offer.offerId)
            if (remaining != state.value.remainingSeconds)
                mutableState.value = state.value.copy(remainingSeconds = remaining)
        }
    }

    private fun stopPolling() { poll?.cancel(); poll = null; ticker?.cancel(); ticker = null }

    private companion object {
        const val POLL_INTERVAL_MS = 4_000L
        const val OWNER = "offers.owner"
        const val PENDING_ID = "offers.pending.id"
        const val PENDING_ORDER = "offers.pending.order"
        const val PENDING_ACTION = "offers.pending.action"
        const val ACCEPTED_ORDER = "offers.accepted.order"
        const val UNKNOWN_MESSAGE = "Chưa xác định được kết quả. Gửi lại đúng thao tác để Backend xác minh; không coi lời mời biến mất là thao tác thất bại."
        fun acceptedMessage(orderId: Long) = "Đã nhận đơn thành công. orderId: $orderId."
    }
}
