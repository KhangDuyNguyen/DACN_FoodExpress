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

enum class DriverOrderKnowledge { UNKNOWN, IDLE, ACTIVE }

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
    val orderKnowledge: DriverOrderKnowledge = DriverOrderKnowledge.UNKNOWN,
    val acceptPending: Boolean = false,
    val offlineConflict: Boolean = false,
    val messageFromDeliveryPolicy: Boolean = false,
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
    private var ownerOnline: (suspend () -> DriverStatusResult)? = null
    private var ownerOffline: (suspend () -> DriverStatusResult)? = null

    fun activateSession(id: Long) {
        if (userId != id) { clear(); userId = id }
        ownerOnline = statuses.onlineForCurrentSession()
        ownerOffline = statuses.offlineForCurrentSession()
        verified = true
        load()
    }

    fun updateDeliveryKnowledge(id: Long, knowledge: DriverOrderKnowledge, acceptPending: Boolean) {
        if (id != userId) return
        if (state.value.orderKnowledge == knowledge && state.value.acceptPending == acceptPending) return
        mutableState.value = state.value.copy(orderKnowledge = knowledge, acceptPending = acceptPending).syncDeliveryNotice()
        if (!allowsOff()) {
            offlineNeeded = false
        } else if (verified && foreground && (offlineNeeded || (onlineRisk && !wantsOnline))) {
            requestOffline("Đã xác minh không có đơn hoạt động. Đang xác nhận Offline.")
        }
    }

    private fun allowsOff() = state.value.orderKnowledge == DriverOrderKnowledge.IDLE && !state.value.acceptPending
    private fun allowsAutoOff() = allowsOff() && !state.value.offlineConflict

    private fun deliveryNotice(value: DriverDashboardUiState): String? = when {
        value.acceptPending -> "Đang xác minh Nhận đơn; không tự chuyển Offline."
        value.orderKnowledge == DriverOrderKnowledge.ACTIVE -> "Đang thực hiện đơn hàng. Không tự tắt nhận đơn; hãy kiểm tra GPS/kết nối."
        value.orderKnowledge == DriverOrderKnowledge.UNKNOWN -> "Chưa xác minh được đơn hiện tại; không tự chuyển Offline."
        else -> null
    }

    // Only replace a notice owned by delivery policy, never a GPS/network/business error.
    private fun DriverDashboardUiState.syncDeliveryNotice(): DriverDashboardUiState {
        if (!messageFromDeliveryPolicy) return this
        val notice = deliveryNotice(this)
        return copy(message = notice, messageIsError = notice != null, messageFromDeliveryPolicy = notice != null)
    }

    private fun showDeliveryNotice() {
        if (state.value.message != null && state.value.messageIsError && !state.value.messageFromDeliveryPolicy) return
        mutableState.value = state.value.copy(message = deliveryNotice(state.value),
            messageIsError = true, messageFromDeliveryPolicy = true)
    }

    private fun suspendEnableIntent() {
        intentVersion++ // An ON queued before background/reverification cannot resume as a new intent.
        if (state.value.profile?.isOnline != true) wantsOnline = false
        mutableState.value = state.value.copy(enablingOnline = false)
    }

    fun pauseSessionVerification() {
        val wasIdle = allowsOff()
        verified = false
        suspendEnableIntent()
        if (wasIdle) wantsOnline = false // Preserve idle recheck intent, defer OFF until a fresh idle confirmation.
        mutableState.value = state.value.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN).syncDeliveryNotice()
        offlineNeeded = false
        stopGps()
    }

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) {
            suspendEnableIntent()
            stopGps()
            if (wantsOnline || onlineRisk) requestOffline("Ứng dụng ra nền. Đang yêu cầu Offline; cần máy chủ xác nhận.")
        } else if (verified) {
            load()
        }
    }

    fun clear() {
        val cleanup = if (allowsAutoOff() && (onlineRisk || wantsOnline || offlineNeeded)) ownerOffline else null
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
        ownerOnline = null; ownerOffline = null
        mutableState.value = DriverDashboardUiState()
        // The closure contains the old session only. Do not cancel an in-flight ON and race OFF.
        if (cleanup != null) viewModelScope.launch { operations.withLock { cleanup() } }
    }

    fun load() {
        if (!verified || !foreground || getJob?.isActive == true || state.value.saving) return
        val session = generation
        mutableState.value = state.value.copy(loading = true, message = null, messageFromDeliveryPolicy = false,
            offlineConfirmed = false, messageIsError = false)
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
                        messageFromDeliveryPolicy = false,
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
                if ((wantsOnline || state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE) && onlineRisk
                    && !offlineNeeded && !state.value.saving && !state.value.statusUnknown
                    && snapshot.status == DriverGpsStatus.READY && snapshot.fix!!.elapsedNanos > lastUploadedFix
                    && now - lastUploadAttempt >= DriverLocationPolicy.UPLOAD_INTERVAL_MS * 1_000_000
                ) upload(session, intentVersion)
                delay(1_000)
            }
        }
    }

    fun changeReceivingOrders(online: Boolean) {
        if (!allowsOff()) {
            showDeliveryNotice()
            return
        }
        if (!online) { requestOffline("Đang yêu cầu tắt nhận đơn…", force = true, manual = true); return }
        val profile = state.value.profile ?: return
        if (!verified || !foreground || state.value.loading || state.value.saving || state.value.statusUnknown || offlineNeeded) return
        if (profile.status != "APPROVED") {
            mutableState.value = state.value.copy(message = "Chỉ hồ sơ APPROVED được bật nhận đơn.",
                messageIsError = true, messageFromDeliveryPolicy = false)
            return
        }
        if (onJob?.isActive == true || profile.isOnline == true) return
        wantsOnline = true
        val intent = ++intentVersion
        val session = generation
        mutableState.value = state.value.copy(saving = true, enablingOnline = true, offlineConfirmed = false, messageIsError = false,
            message = "Đang lấy vị trí hợp lệ trước khi bật nhận đơn…", messageFromDeliveryPolicy = false)
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
                mutableState.value = state.value.copy(message = "Đang gửi vị trí đến máy chủ…", messageFromDeliveryPolicy = false)
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
                mutableState.value = state.value.copy(message = "Đang chờ máy chủ xác nhận Online…", messageFromDeliveryPolicy = false)
                onlineRisk = true
                val result = ownerOnline?.invoke() ?: DriverStatusResult.SessionExpired
                if (!current(session)) return@withLock
                when (result) {
                    DriverStatusResult.OnlineConfirmed -> {
                        mutableState.value = state.value.copy(profile = state.value.profile?.copy(isOnline = true), statusUnknown = false)
                        if (canEnable(session, intent)) mutableState.value = state.value.copy(message = "Máy chủ xác nhận đang bật nhận đơn.",
                            messageIsError = false, messageFromDeliveryPolicy = false)
                        else requestOffline("Yêu cầu bật đã bị hủy. Đang xác nhận Offline.", force = true)
                    }
                    DriverStatusResult.SessionExpired -> expire()
                    else -> {
                        wantsOnline = false
                        mutableState.value = state.value.copy(statusUnknown = true, message = (result as? DriverStatusResult.Failed)?.message,
                            messageFromDeliveryPolicy = false)
                        offlineExplanation = (result as? DriverStatusResult.Failed)?.message
                        requestOffline("${offlineExplanation.orEmpty()} Đang yêu cầu Offline để bảo đảm ý định an toàn.",
                            force = true, failureMessage = offlineExplanation)
                    }
                }
            }
            if (current(session)) mutableState.value = state.value.copy(enablingOnline = false, saving = offJob?.isActive == true)
        }
    }

    private fun requestOffline(message: String, force: Boolean = false, manual: Boolean = false, failureMessage: String? = null) {
        if (!allowsOff() || (!manual && !allowsAutoOff())) {
            offlineNeeded = false
            when {
                failureMessage != null -> mutableState.value = state.value.copy(message = failureMessage,
                    messageIsError = true, messageFromDeliveryPolicy = false)
                state.value.offlineConflict -> {
                    if (state.value.message == null || state.value.messageFromDeliveryPolicy)
                        mutableState.value = state.value.copy(message = "Máy chủ chưa cho phép Offline. Vui lòng đồng bộ đơn và hồ sơ trước khi thử lại.",
                            messageIsError = true, messageFromDeliveryPolicy = false)
                }
                else -> showDeliveryNotice()
            }
            return
        }
        if (manual) mutableState.value = state.value.copy(offlineConflict = false)
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
        val sendOffline = ownerOffline ?: return
        mutableState.value = state.value.copy(saving = true, message = message, messageFromDeliveryPolicy = false)
        lastOffAttempt = now
        offJob = viewModelScope.launch {
            operations.withLock {
                if (!current(session)) return@withLock
                // Policy may have changed while a profile/GPS request held the mutex.
                if (!allowsOff() || (!manual && !allowsAutoOff())) {
                    offlineNeeded = false
                    mutableState.value = state.value.copy(saving = false)
                    return@withLock
                }
                val result = sendOffline()
                if (!current(session)) return@withLock
                when (result) {
                    DriverStatusResult.OfflineConfirmed -> {
                        onlineRisk = false; offlineNeeded = false
                        mutableState.value = state.value.copy(profile = state.value.profile?.copy(isOnline = false), saving = false,
                            enablingOnline = false, statusUnknown = false, offlineConfirmed = true, messageIsError = offlineExplanation != null,
                            messageFromDeliveryPolicy = false,
                            message = listOfNotNull(offlineExplanation, "Máy chủ xác nhận đã tắt nhận đơn.").joinToString(" "))
                        offlineExplanation = null
                    }
                    DriverStatusResult.SessionExpired -> expire()
                    DriverStatusResult.OfflineConflict -> {
                        offlineNeeded = false
                        mutableState.value = state.value.copy(saving = false, statusUnknown = true,
                            offlineConfirmed = false, offlineConflict = true,
                            message = "Máy chủ chưa cho phép Offline. Đang cần đồng bộ đơn và hồ sơ; không tự gửi lại yêu cầu.",
                            messageIsError = true, messageFromDeliveryPolicy = false)
                    }
                    else -> mutableState.value = state.value.copy(saving = false, statusUnknown = true, messageIsError = true,
                        messageFromDeliveryPolicy = false,
                        message = (result as? DriverStatusResult.Failed)?.message ?: "Chưa xác nhận được Offline. Vui lòng thử lại.")
                }
            }
        }
    }

    private fun upload(session: Long, intent: Long) {
        if (uploadJob?.isActive == true) return
        uploadJob = viewModelScope.launch {
            operations.withLock {
                if (!canTrack(session, intent) || !onlineRisk || offlineNeeded || state.value.saving || state.value.statusUnknown
                    || state.value.profile?.status != "APPROVED" || state.value.profile?.isOnline != true
                ) return@withLock
                // GET may have held the mutex long enough to invalidate the queued fix.
                // Re-sample here; the monitor still owns permission/lost-fix Offline policy.
                val snapshot = gps.refresh()
                mutableState.value = state.value.copy(gps = snapshot)
                val now = gps.nowNanos()
                val fix = snapshot.fix
                if (snapshot.status != DriverGpsStatus.READY || fix == null || DriverLocationPolicy.problem(fix, now) != null
                    || fix.elapsedNanos <= lastUploadedFix
                    || now - lastUploadAttempt < DriverLocationPolicy.UPLOAD_INTERVAL_MS * 1_000_000
                ) return@withLock
                // Throttle actual periodic attempts, not time spent waiting for the mutex.
                lastUploadAttempt = now
                when (val result = locations.record(fix, now)) {
                    DriverLocationResult.Recorded -> if (current(session)) lastUploadedFix = fix.elapsedNanos
                    DriverLocationResult.SessionExpired -> if (current(session)) expire()
                    is DriverLocationResult.Failed -> if (current(session)) {
                        offlineExplanation = result.message
                        requestOffline("${result.message} Đang yêu cầu Offline.", force = true, failureMessage = result.message)
                    }
                }
            }
        }
    }

    private fun failEnable(message: String) {
        wantsOnline = false
        mutableState.value = state.value.copy(saving = false, enablingOnline = false, message = message,
            messageIsError = true, messageFromDeliveryPolicy = false)
    }

    private fun current(session: Long) = generation == session && userId != null
    private fun canEnable(session: Long, intent: Long) = current(session) && verified && foreground && wantsOnline && intentVersion == intent
    private fun canTrack(session: Long, intent: Long) = current(session) && verified && foreground && intentVersion == intent &&
        (wantsOnline || state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE)
    private fun stopGps() {
        monitor?.cancel(); monitor = null
        gps.stop()
        mutableState.value = state.value.copy(gps = DriverGpsSnapshot())
    }
    private fun expire() {
        wantsOnline = false; verified = false; intentVersion++
        stopGps()
        mutableState.value = state.value.copy(saving = false, enablingOnline = false, loading = false,
            orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true, sessionExpired = true).syncDeliveryNotice()
    }
    override fun onCleared() { gps.stop(); super.onCleared() }
}
