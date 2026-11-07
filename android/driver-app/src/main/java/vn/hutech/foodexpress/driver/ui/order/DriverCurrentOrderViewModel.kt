package vn.hutech.foodexpress.driver.ui.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
)

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
        mutableState.value = DriverCurrentOrderUiState()
    }
    fun setForeground(active: Boolean) {
        if (foreground == active) return
        foreground = active
        if (active) restart() else stop()
    }
    fun clear() {
        stop(); owner = null; authenticated = false
        offers?.invalidateCurrentOrder()
        mutableState.value = DriverCurrentOrderUiState()
    }
    fun refresh() {
        if (!canLoad() || state.value.loading) return
        mutableState.value = state.value.copy(loading = true)
        refreshes.trySend(Unit)
        start()
    }
    private fun canLoad() = owner != null && authenticated && foreground
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
        if (!canLoad() || loop?.isActive == true) return
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
                        offers?.confirmCurrentOrder(result.order?.orderId, revision!!)
                        mutableState.value = DriverCurrentOrderUiState(order = result.order, verified = true, ownerId = owner)
                    }
                    CurrentOrderResult.SessionExpired -> {
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
