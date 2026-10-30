package vn.hutech.foodexpress.driver.ui.profile

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

data class DriverProfileUiState(
    val profile: DriverProfile? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val editing: Boolean = false,
    val vehicleType: String = "",
    val licensePlate: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class DriverProfileViewModel @Inject constructor(private val repository: DriverProfileRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverProfileUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun clear() {
        request?.cancel()
        request = null
        mutableState.value = DriverProfileUiState()
    }

    fun load() {
        if (request?.isActive == true) return
        mutableState.value = DriverProfileUiState(loading = true)
        request = viewModelScope.launch { applyLoad(repository.getProfile()) }
    }

    fun startEditing() {
        val current = state.value
        if (current.loading || current.saving || current.profile?.status != "PENDING") return
        mutableState.value = current.copy(
            editing = true, vehicleType = current.profile.vehicleType.orEmpty(),
            licensePlate = current.profile.licensePlate.orEmpty(), fieldErrors = emptyMap(), message = null, saved = false,
        )
    }

    fun cancelEditing() {
        if (!state.value.saving) mutableState.value = state.value.copy(editing = false, fieldErrors = emptyMap(), message = null)
    }

    fun selectVehicle(type: String) {
        if (state.value.editing && !state.value.saving) mutableState.value = state.value.copy(
            vehicleType = type, fieldErrors = emptyMap(), message = null,
        )
    }

    fun updatePlate(plate: String) {
        if (state.value.editing && !state.value.saving) mutableState.value = state.value.copy(
            licensePlate = plate, fieldErrors = state.value.fieldErrors - "licensePlate", message = null,
        )
    }

    fun save() {
        val current = state.value
        if (request?.isActive == true || !current.editing || current.profile?.status != "PENDING") return
        val plate = current.licensePlate.trim().takeIf { it.isNotEmpty() }
        val errors = buildMap {
            if (current.vehicleType !in setOf("MOTORBIKE", "BICYCLE", "CAR")) put("vehicleType", "Vui lòng chọn loại xe.")
            if (plate != null && plate.length > 20) put("licensePlate", "Biển số tối đa 20 ký tự.")
            if (current.vehicleType in setOf("MOTORBIKE", "CAR") && plate == null) put("licensePlate", "Xe máy và ô tô bắt buộc có biển số.")
        }
        mutableState.value = current.copy(fieldErrors = errors, message = null, saved = false)
        if (errors.isNotEmpty()) return
        mutableState.value = state.value.copy(saving = true)
        request = viewModelScope.launch {
            when (val result = repository.updateVehicle(current.vehicleType, plate)) {
                is DriverProfileResult.Loaded -> {
                    // PUT acknowledgement is followed by a fresh GET; the UI never assumes local edits were saved.
                    mutableState.value = DriverProfileUiState(loading = true, saving = true)
                    applyLoad(repository.getProfile(), afterUpdate = true)
                }
                DriverProfileResult.SessionExpired -> mutableState.value = DriverProfileUiState(sessionExpired = true)
                is DriverProfileResult.Failed -> {
                    mutableState.value = if (result.reloadRequired) DriverProfileUiState(message = result.message)
                    else state.value.copy(saving = false, message = result.message, fieldErrors = result.fieldErrors)
                }
            }
        }
    }

    private fun applyLoad(result: DriverProfileResult, afterUpdate: Boolean = false) {
        mutableState.value = when (result) {
            is DriverProfileResult.Loaded -> DriverProfileUiState(
                profile = result.profile, saved = afterUpdate,
                message = if (afterUpdate) "Đã cập nhật phương tiện và tải lại hồ sơ." else null,
            )
            is DriverProfileResult.Failed -> DriverProfileUiState(message =
                if (afterUpdate) "Máy chủ đã nhận cập nhật nhưng chưa tải lại được hồ sơ. ${result.message}" else result.message)
            DriverProfileResult.SessionExpired -> DriverProfileUiState(sessionExpired = true)
        }
    }
}
