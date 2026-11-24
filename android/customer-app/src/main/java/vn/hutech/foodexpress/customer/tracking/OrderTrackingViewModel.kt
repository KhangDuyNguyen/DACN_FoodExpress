package vn.hutech.foodexpress.customer.tracking

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OrderTrackingViewModel @Inject constructor(transport: RetrofitTrackingTransport) : ViewModel() {
    private val session = TrackingSession(transport)
    private val selectedOrder = MutableStateFlow<Long?>(null)
    private val currentState = MutableStateFlow<TrackingSessionUpdate?>(null)
    val state: StateFlow<TrackingSessionUpdate?> = currentState.asStateFlow()

    private var tokenSource: StateFlow<String?>? = null
    private var observation: Job? = null

    /** Call while the tracking screen is active. The session owner emits null on sign-out. */
    @MainThread
    fun start(orderId: Long, accessTokens: StateFlow<String?>) {
        require(orderId > 0)
        val sessionSourceChanged = tokenSource !== accessTokens
        if (sessionSourceChanged || selectedOrder.value != orderId) {
            currentState.value = null
        }
        selectedOrder.value = orderId
        if (sessionSourceChanged) {
            observation?.cancel()
            tokenSource = accessTokens
            observation = viewModelScope.launch {
                session.observe(selectedOrder, accessTokens).collect { next ->
                    if (next.forSelection(selectedOrder.value, accessTokens.value) != null) {
                        currentState.value = next
                    }
                }
            }
        }
    }

    /** Call when leaving the screen to close the current order subscription. */
    @MainThread
    fun stop() {
        observation?.cancel()
        observation = null
        selectedOrder.value = null
        tokenSource = null
        currentState.value = null
    }
}
