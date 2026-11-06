package vn.hutech.foodexpress.driver.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.auth.DriverAuthResult
import vn.hutech.foodexpress.driver.data.auth.DriverAuthUser

enum class DriverAuthPhase { CHECKING, SIGNED_OUT, RETRY_SESSION, AUTHENTICATED }

data class DriverAuthUiState(
    val phase: DriverAuthPhase = DriverAuthPhase.CHECKING,
    val loading: Boolean = false,
    val message: String? = null,
    val identifierError: String? = null,
    val passwordError: String? = null,
    val user: DriverAuthUser? = null,
    val skipOnboarding: Boolean = false,
)

@HiltViewModel
class DriverAuthViewModel @Inject constructor(private val repository: DriverAuthRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverAuthUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var expiryTimer: Job? = null

    init {
        checkSession()
    }

    fun checkSession() {
        if (request?.isActive == true) return
        expiryTimer?.cancel()
        mutableState.value = mutableState.value.copy(phase = DriverAuthPhase.CHECKING, user = null)
        request = viewModelScope.launch { applySessionResult(repository.restoreSession()) }
    }

    fun onResume() {
        if (state.value.phase == DriverAuthPhase.AUTHENTICATED) checkSession()
    }

    fun login(identifier: String, password: String, rememberLogin: Boolean) {
        if (request?.isActive == true || state.value.phase != DriverAuthPhase.SIGNED_OUT) return
        val identifierError = if (identifier.isBlank()) "Vui lòng nhập số điện thoại hoặc email." else null
        val passwordError = if (password.isBlank()) "Vui lòng nhập mật khẩu." else null
        mutableState.value = state.value.copy(
            identifierError = identifierError, passwordError = passwordError, message = null,
        )
        if (identifierError != null || passwordError != null) return
        mutableState.value = state.value.copy(loading = true)
        request = viewModelScope.launch {
            when (val result = repository.login(identifier, password, rememberLogin)) {
                is DriverAuthResult.Authenticated -> applySessionResult(result)
                is DriverAuthResult.Rejected -> mutableState.value = state.value.copy(loading = false, message = result.message)
                is DriverAuthResult.Retry -> mutableState.value = state.value.copy(loading = false, message = result.message)
                DriverAuthResult.NoSession -> mutableState.value = DriverAuthUiState(phase = DriverAuthPhase.SIGNED_OUT)
            }
        }
    }

    fun clearInputErrors() {
        if (!state.value.loading) mutableState.value = state.value.copy(
            message = null, identifierError = null, passwordError = null,
        )
    }

    fun logout() {
        if (request?.isActive == true) return
        expiryTimer?.cancel()
        mutableState.value = state.value.copy(phase = DriverAuthPhase.CHECKING, user = null)
        request = viewModelScope.launch { applySessionResult(repository.logout(), skipOnboarding = true) }
    }

    private fun applySessionResult(result: DriverAuthResult, skipOnboarding: Boolean = state.value.skipOnboarding) {
        mutableState.value = when (result) {
            DriverAuthResult.NoSession -> DriverAuthUiState(phase = DriverAuthPhase.SIGNED_OUT, skipOnboarding = skipOnboarding)
            is DriverAuthResult.Rejected -> DriverAuthUiState(
                phase = DriverAuthPhase.SIGNED_OUT, message = result.message, skipOnboarding = true,
            )
            is DriverAuthResult.Retry -> DriverAuthUiState(
                phase = DriverAuthPhase.RETRY_SESSION, message = result.message, skipOnboarding = true,
            )
            is DriverAuthResult.Authenticated -> {
                expiryTimer?.cancel()
                expiryTimer = viewModelScope.launch {
                    val remaining = result.expiresAtSeconds - System.currentTimeMillis() / 1_000
                    delay(remaining.coerceAtLeast(0).coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000)
                    checkSession()
                }
                DriverAuthUiState(phase = DriverAuthPhase.AUTHENTICATED, user = result.user, skipOnboarding = true)
            }
        }
    }
}
