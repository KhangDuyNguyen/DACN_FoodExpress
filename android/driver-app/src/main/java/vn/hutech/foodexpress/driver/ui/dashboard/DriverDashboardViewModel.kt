package vn.hutech.foodexpress.driver.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.driver.data.profile.DriverProfile
import vn.hutech.foodexpress.driver.data.profile.DriverProfileRepository
import vn.hutech.foodexpress.driver.data.profile.DriverProfileResult
import vn.hutech.foodexpress.driver.data.status.DriverStatusRepository
import vn.hutech.foodexpress.driver.data.status.DriverStatusResult

data class DriverDashboardUiState(
    val profile: DriverProfile? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
    val offlineConfirmed: Boolean = false,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    private val profiles: DriverProfileRepository,
    private val statuses: DriverStatusRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverDashboardUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun clear() {
        request?.cancel()
        request = null
        mutableState.value = DriverDashboardUiState()
    }

    fun load() {
        if (request?.isActive == true) return
        mutableState.value = DriverDashboardUiState(loading = true)
        request = viewModelScope.launch {
            mutableState.value = when (val result = profiles.getProfile()) {
                is DriverProfileResult.Loaded -> DriverDashboardUiState(profile = result.profile)
                is DriverProfileResult.Failed -> DriverDashboardUiState(message = result.message)
                DriverProfileResult.SessionExpired -> DriverDashboardUiState(sessionExpired = true)
            }
        }
    }

    fun changeReceivingOrders(online: Boolean) {
        if (request?.isActive == true) return
        val current = state.value
        val profile = current.profile ?: return
        if (online) {
            mutableState.value = current.copy(
                offlineConfirmed = false,
                message = if (profile.status == "APPROVED")
                    "Chưa thể bật nhận đơn: chưa xác minh được vị trí GPS Android hiện tại."
                else "Hồ sơ chưa đủ điều kiện bật nhận đơn.",
            )
            return
        }
        mutableState.value = current.copy(saving = true, message = null, offlineConfirmed = false)
        request = viewModelScope.launch {
            mutableState.value = when (val result = statuses.goOffline()) {
                DriverStatusResult.OfflineConfirmed -> current.copy(
                    profile = profile.copy(isOnline = false), saving = false, offlineConfirmed = true,
                    message = "Đã đồng bộ Offline. Máy chủ xác nhận đã tắt nhận đơn.",
                )
                // An uncertain PATCH result invalidates the displayed snapshot; Retry only performs GET.
                is DriverStatusResult.Failed -> DriverDashboardUiState(message = result.message)
                DriverStatusResult.SessionExpired -> DriverDashboardUiState(sessionExpired = true)
            }
        }
    }
}
