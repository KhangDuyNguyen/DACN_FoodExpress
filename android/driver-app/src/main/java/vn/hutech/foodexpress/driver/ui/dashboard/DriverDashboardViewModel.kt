package vn.hutech.foodexpress.driver.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.location.*
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
    val gps: DriverGpsSnapshot = DriverGpsSnapshot(),
    val enablingOnline: Boolean = false,
    val statusUnknown: Boolean = false,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    private val profiles: DriverProfileRepository,
    private val statuses: DriverStatusRepository,
    private val locations: DriverLocationRepository,
    private val gps: DriverForegroundLocationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DriverDashboardUiState())
    val state = mutableState.asStateFlow()
    // Every GET/POST/PATCH is serialized. Cancelling a client job cannot undo a server write.
    private val operations = Mutex()
    private var generation = 0L
    private var userId: Long? = null
    private var verified = false
    private var foreground = false
    private var wantsOnline = false
    private var intentVersion = 0L
    private var onlineRisk = false
    private var offlineNeeded = false
    private var getJob: Job? = null
    private var onJob: Job? = null
    private var offJob: Job? = null
    private var uploadJob: Job? = null
    private var monitor: Job? = null
    private var lastGoodFix = 0L
    private var lastUploadedFix = 0L
    private var lastUploadAttempt = 0L
    private var lastOffAttempt = 0L
    private var offlineExplanation: String? = null

    fun activateSession(id: Long) {
        if (userId != id) { clear(); userId = id }
        verified = true
        load()
    }

    fun pauseSessionVerification() {
        verified = false
        stopGps()
        if (wantsOnline || onlineRisk) requestOffline("Đang xác minh phiên; yêu cầu tắt nhận đơn.")
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) {
            stopGps()
            if (wantsOnline || onlineRisk) requestOffline("Ứng dụng ra nền. Đang yêu cầu Offline; cần máy chủ xác nhận.")
        } else if (verified) {
            load()
        }
    }

    fun clear() {
        val cleanup = if (onlineRisk || wantsOnline || offlineNeeded) statuses.offlineForCurrentSession() else null
        generation++
        intentVersion++
        userId = null
        verified = false
        wantsOnline = false
        onlineRisk = false
        offlineNeeded = false
        getJob?.cancel()
        stopGps()
        lastGoodFix = 0L
        lastUploadedFix = 0L
        lastUploadAttempt = 0L
        lastOffAttempt = 0L
        offlineExplanation = null
        mutableState.value = DriverDashboardUiState()
        // The closure contains the old session only. Do not cancel an in-flight ON and race OFF.
        if (cleanup != null) viewModelScope.launch { operations.withLock { cleanup() } }
    }

    fun load() {
        if (!verified || !foreground || getJob?.isActive == true || state.value.saving) return
        val session = generation
        mutableState.value = state.value.copy(loading = true, message = null, offlineConfirmed = false, messageIsError = false)
        getJob = viewModelScope.launch {
            operations.withLock {
                if (!current(session)) return@withLock
                val result = withTimeoutOrNull(DriverLocationPolicy.HTTP_TIMEOUT_MS) { profiles.getProfile() }
                if (!current(session)) return@withLock
                when (result) {
                    is DriverProfileResult.Loaded -> {
                        onlineRisk = result.profile.isOnline == true
                        if (!onlineRisk) offlineNeeded = false
                        mutableState.value = state.value.copy(profile = result.profile, loading = false,
                            statusUnknown = false, offlineConfirmed = false)
                        refreshGps()
                        if (onlineRisk && (!wantsOnline || result.profile.status != "APPROVED"))
                            requestOffline("Đang xác minh lại điều kiện nhận đơn. Yêu cầu chuyển Offline.", force = true)
                    }
                    DriverProfileResult.SessionExpired -> expire()
                    else -> mutableState.value = state.value.copy(loading = false, statusUnknown = true, messageIsError = true,
                        message = (result as? DriverProfileResult.Failed)?.message ?: "Tải trạng thái quá thời gian chờ. Vui lòng thử lại.")
                }
            }
        }
    }

    fun refreshGps() {
        if (!verified || !foreground || state.value.profile?.status != "APPROVED") {
            stopGps(); return
        }
        mutableState.value = state.value.copy(gps = gps.start())
        if (monitor?.isActive == true) return
        val session = generation
        monitor = viewModelScope.launch {
            while (isActive && current(session) && verified && foreground) {
                val snapshot = gps.refresh()
                mutableState.value = state.value.copy(gps = snapshot)
                val now = gps.nowNanos()
                if (snapshot.status == DriverGpsStatus.READY) lastGoodFix = snapshot.fix!!.elapsedNanos
                val unavailable = snapshot.status in setOf(DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE, DriverGpsStatus.SERVICES_OFF)
                val lost = lastGoodFix > 0 && (now - lastGoodFix) / 1_000_000 >= DriverLocationPolicy.LOST_FIX_MS
                // A new ON gets its own 20s acquisition window; an old Offline fix is not an Online loss.
                if (((onlineRisk || wantsOnline) && unavailable) || (onlineRisk && lost))
                    requestOffline("GPS không còn sẵn sàng. Đang yêu cầu Offline; chưa xác nhận cho đến khi máy chủ phản hồi.")
                if (offlineNeeded && offJob?.isActive != true && now - lastOffAttempt >= DriverLocationPolicy.OFF_RETRY_MS * 1_000_000)
                    requestOffline("Đang thử xác nhận lại Offline…")
                if (wantsOnline && onlineRisk && !offlineNeeded && !state.value.saving && !state.value.statusUnknown
                    && snapshot.status == DriverGpsStatus.READY && snapshot.fix!!.elapsedNanos > lastUploadedFix
                    && now - lastUploadAttempt >= DriverLocationPolicy.UPLOAD_INTERVAL_MS * 1_000_000
                ) upload(snapshot.fix, session)
                delay(1_000)
            }
        }
    }

    fun changeReceivingOrders(online: Boolean) {
        if (!online) { requestOffline("Đang yêu cầu tắt nhận đơn…", force = true); return }
        val profile = state.value.profile ?: return
        if (!verified || !foreground || state.value.loading || state.value.saving || state.value.statusUnknown || offlineNeeded) return
        if (profile.status != "APPROVED") {
            mutableState.value = state.value.copy(message = "Chỉ hồ sơ APPROVED được bật nhận đơn.", messageIsError = true)
            return
        }
        if (onJob?.isActive == true || profile.isOnline == true) return
        wantsOnline = true
        val intent = ++intentVersion
        val session = generation
        mutableState.value = state.value.copy(saving = true, enablingOnline = true, offlineConfirmed = false, messageIsError = false,
            message = "Đang lấy vị trí hợp lệ trước khi bật nhận đơn…")
        refreshGps()
        onJob = viewModelScope.launch {
            operations.withLock {
                if (!canEnable(session, intent)) return@withLock
                val fix = withTimeoutOrNull(DriverLocationPolicy.ACQUIRE_TIMEOUT_MS) {
                    var found: DriverLocationFix? = null
                    while (canEnable(session, intent)) {
                        val snapshot = gps.refresh()
                        mutableState.value = state.value.copy(gps = snapshot)
                        if (snapshot.status == DriverGpsStatus.READY) { found = snapshot.fix; break }
                        if (snapshot.status in setOf(DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE, DriverGpsStatus.SERVICES_OFF, DriverGpsStatus.ERROR)) break
                        delay(250)
                    }
                    found
                }
                if (!canEnable(session, intent)) return@withLock
                if (fix == null) {
                    failEnable("Chưa lấy được GPS hợp lệ trong ${DriverLocationPolicy.ACQUIRE_TIMEOUT_MS / 1_000} giây. ${state.value.gps.message} Hãy thử lại.")
                    return@withLock
                }
                lastGoodFix = fix.elapsedNanos
                mutableState.value = state.value.copy(message = "Đang gửi vị trí đến máy chủ…")
                lastUploadAttempt = gps.nowNanos()
                val locationResult = locations.record(fix, gps.nowNanos())
                if (!current(session)) return@withLock
                if (locationResult == DriverLocationResult.SessionExpired) { expire(); return@withLock }
                if (locationResult is DriverLocationResult.Failed) { failEnable(locationResult.message); return@withLock }
                lastUploadedFix = fix.elapsedNanos
                if (!canEnable(session, intent)) return@withLock
                if (DriverLocationPolicy.problem(fix, gps.nowNanos()) != null || gps.refresh().status != DriverGpsStatus.READY) {
                    failEnable("Điều kiện GPS đã thay đổi trong lúc gửi vị trí. Vui lòng thử lại."); return@withLock
                }
                mutableState.value = state.value.copy(message = "Đang chờ máy chủ xác nhận Online…")
                onlineRisk = true
                val result = statuses.goOnline()
                if (!current(session)) return@withLock
                when (result) {
                    DriverStatusResult.OnlineConfirmed -> {
                        mutableState.value = state.value.copy(profile = state.value.profile?.copy(isOnline = true), statusUnknown = false)
                        if (canEnable(session, intent)) mutableState.value = state.value.copy(message = "Máy chủ xác nhận đang bật nhận đơn.", messageIsError = false)
                        else requestOffline("Yêu cầu bật đã bị hủy. Đang xác nhận Offline.", force = true)
                    }
                    DriverStatusResult.SessionExpired -> expire()
                    else -> {
                        wantsOnline = false
                        mutableState.value = state.value.copy(statusUnknown = true, message = (result as? DriverStatusResult.Failed)?.message)
                        offlineExplanation = (result as? DriverStatusResult.Failed)?.message
                        requestOffline("${offlineExplanation.orEmpty()} Đang yêu cầu Offline để bảo đảm ý định an toàn.", force = true)
                    }
                }
            }
            if (current(session)) mutableState.value = state.value.copy(enablingOnline = false, saving = offJob?.isActive == true)
        }
    }

    private fun requestOffline(message: String, force: Boolean = false) {
        wantsOnline = false
        intentVersion++
        if (userId == null) return
        offlineNeeded = true
        mutableState.value = state.value.copy(enablingOnline = false, offlineConfirmed = false)
        if (offJob?.isActive == true) return
        val now = gps.nowNanos()
        if (!force && lastOffAttempt > 0 && now - lastOffAttempt < DriverLocationPolicy.OFF_RETRY_MS * 1_000_000) return
        val session = generation
        // Bind token before any subsequent logout/account switch.
        val sendOffline = statuses.offlineForCurrentSession()
        mutableState.value = state.value.copy(saving = true, message = message)
        lastOffAttempt = now
        offJob = viewModelScope.launch {
            operations.withLock {
                if (!current(session)) return@withLock
                val result = sendOffline()
                if (!current(session)) return@withLock
                when (result) {
                    DriverStatusResult.OfflineConfirmed -> {
                        onlineRisk = false; offlineNeeded = false
                        mutableState.value = state.value.copy(profile = state.value.profile?.copy(isOnline = false), saving = false,
                            enablingOnline = false, statusUnknown = false, offlineConfirmed = true, messageIsError = offlineExplanation != null,
                            message = listOfNotNull(offlineExplanation, "Máy chủ xác nhận đã tắt nhận đơn.").joinToString(" "))
                        offlineExplanation = null
                    }
                    DriverStatusResult.SessionExpired -> expire()
                    else -> mutableState.value = state.value.copy(saving = false, statusUnknown = true, messageIsError = true,
                        message = (result as? DriverStatusResult.Failed)?.message ?: "Chưa xác nhận được Offline. Vui lòng thử lại.")
                }
            }
        }
    }

    private fun upload(fix: DriverLocationFix, session: Long) {
        if (uploadJob?.isActive == true) return
        lastUploadAttempt = gps.nowNanos()
        uploadJob = viewModelScope.launch {
            operations.withLock {
                if (!current(session) || !verified || !foreground || !wantsOnline || offlineNeeded) return@withLock
                when (val result = locations.record(fix, gps.nowNanos())) {
                    DriverLocationResult.Recorded -> if (current(session)) lastUploadedFix = fix.elapsedNanos
                    DriverLocationResult.SessionExpired -> if (current(session)) expire()
                    is DriverLocationResult.Failed -> if (current(session)) {
                        offlineExplanation = result.message
                        requestOffline("${result.message} Đang yêu cầu Offline.", force = true)
                    }
                }
            }
        }
    }

    private fun failEnable(message: String) {
        wantsOnline = false
        mutableState.value = state.value.copy(saving = false, enablingOnline = false, message = message, messageIsError = true)
    }

    private fun current(session: Long) = generation == session && userId != null
    private fun canEnable(session: Long, intent: Long) = current(session) && verified && foreground && wantsOnline && intentVersion == intent
    private fun stopGps() {
        monitor?.cancel(); monitor = null
        gps.stop()
        mutableState.value = state.value.copy(gps = DriverGpsSnapshot())
    }
    private fun expire() {
        wantsOnline = false; verified = false; intentVersion++
        stopGps()
        mutableState.value = state.value.copy(saving = false, enablingOnline = false, loading = false, statusUnknown = true, sessionExpired = true)
    }
    override fun onCleared() { gps.stop(); super.onCleared() }
}
