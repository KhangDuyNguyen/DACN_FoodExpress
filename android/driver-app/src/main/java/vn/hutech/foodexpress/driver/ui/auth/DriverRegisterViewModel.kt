package vn.hutech.foodexpress.driver.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.auth.DriverRegisterRequest
import vn.hutech.foodexpress.driver.data.auth.DriverRegisterResult

enum class DriverVehicleType(val label: String) {
    MOTORBIKE("Xe máy"), BICYCLE("Xe đạp"), CAR("Ô tô"),
}

data class DriverRegisterForm(
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val vehicleType: DriverVehicleType = DriverVehicleType.MOTORBIKE,
    val licensePlate: String = "",
) {
    override fun toString(): String = "DriverRegisterForm(redacted)"
}

data class DriverRegisterUiState(
    val form: DriverRegisterForm = DriverRegisterForm(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val loading: Boolean = false,
    val registered: Boolean = false,
    val errorStep: Int? = null,
)

internal fun DriverRegisterForm.accountErrors(): Map<String, String> = buildMap {
    if (fullName.isBlank()) put("fullName", "Vui lòng nhập họ tên.")
    else if (fullName.trim().length > 100) put("fullName", "Họ tên tối đa 100 ký tự.")
    if (!phone.trim().matches(Regex("0[0-9]{9}"))) put("phone", "SĐT phải gồm 10 chữ số, bắt đầu bằng 0.")
    if (email.isNotBlank() && (email.trim().length > 150 || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())) {
        put("email", "Vui lòng nhập email hợp lệ, tối đa 150 ký tự.")
    }
    if (password.isBlank() || password.length !in 8..72) put("password", "Mật khẩu phải từ 8 đến 72 ký tự.")
    if (confirmPassword != password || confirmPassword.isBlank()) put("confirmPassword", "Xác nhận mật khẩu chưa khớp.")
}

internal fun DriverRegisterForm.vehicleErrors(): Map<String, String> = buildMap {
    if (licensePlate.trim().length > 20) put("licensePlate", "Biển số tối đa 20 ký tự.")
    else if (vehicleType != DriverVehicleType.BICYCLE && licensePlate.isBlank()) {
        put("licensePlate", "Vui lòng nhập biển số xe.")
    }
}

@HiltViewModel
class DriverRegisterViewModel @Inject constructor(private val repository: DriverAuthRepository) : ViewModel() {
    // Memory only: no SavedStateHandle, rememberSaveable or disk storage for this form.
    private val mutableState = MutableStateFlow(DriverRegisterUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun update(field: String, value: String) {
        if (state.value.loading || state.value.registered) return
        val form = state.value.form
        val updated = when (field) {
            "fullName" -> form.copy(fullName = value)
            "phone" -> form.copy(phone = value)
            "email" -> form.copy(email = value)
            "password" -> form.copy(password = value)
            "confirmPassword" -> form.copy(confirmPassword = value)
            "licensePlate" -> form.copy(licensePlate = value)
            else -> return
        }
        mutableState.value = state.value.copy(form = updated, fieldErrors = state.value.fieldErrors - field,
            message = null, errorStep = null)
    }

    fun selectVehicle(type: DriverVehicleType) {
        if (!state.value.loading && !state.value.registered) mutableState.value = state.value.copy(
            form = state.value.form.copy(vehicleType = type), fieldErrors = state.value.fieldErrors - "licensePlate" - "vehicleType",
            message = null, errorStep = null,
        )
    }

    fun continueToVehicle(): Boolean {
        if (state.value.loading || state.value.registered) return false
        val errors = state.value.form.accountErrors()
        mutableState.value = state.value.copy(fieldErrors = errors, message = null, errorStep = null)
        return errors.isEmpty()
    }

    fun hasValidAccount(): Boolean = state.value.form.accountErrors().isEmpty()

    fun submit() {
        if (request?.isActive == true || state.value.loading || state.value.registered) return
        val form = state.value.form
        val account = form.accountErrors()
        val errors = account + form.vehicleErrors()
        if (errors.isNotEmpty()) {
            mutableState.value = state.value.copy(fieldErrors = errors, errorStep = if (account.isNotEmpty()) 1 else 2)
            return
        }
        mutableState.value = state.value.copy(loading = true, message = null, fieldErrors = emptyMap(), errorStep = null)
        val payload = DriverRegisterRequest(
            fullName = form.fullName.trim(), phone = form.phone.trim(),
            email = form.email.trim().takeIf { it.isNotEmpty() }, password = form.password,
            vehicleType = form.vehicleType.name, licensePlate = form.licensePlate.trim().takeIf { it.isNotEmpty() },
        )
        request = viewModelScope.launch {
            when (val result = repository.register(payload)) {
                DriverRegisterResult.Success -> mutableState.value = DriverRegisterUiState(registered = true)
                is DriverRegisterResult.Failed -> mutableState.value = state.value.copy(
                    loading = false, message = result.message, fieldErrors = result.fieldErrors,
                    errorStep = if (result.fieldErrors.keys.any { it in setOf("fullName", "phone", "email", "password") }) 1 else null,
                )
            }
        }
    }

    fun reset() {
        request?.cancel()
        request = null
        mutableState.value = DriverRegisterUiState()
    }

    override fun onCleared() {
        mutableState.value = DriverRegisterUiState()
        super.onCleared()
    }
}
