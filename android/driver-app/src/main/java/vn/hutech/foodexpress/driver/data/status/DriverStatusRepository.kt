package vn.hutech.foodexpress.driver.data.status

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.location.DriverLocationPolicy
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

sealed interface DriverStatusResult {
    data object OnlineConfirmed : DriverStatusResult
    data object OfflineConfirmed : DriverStatusResult
    data object SessionExpired : DriverStatusResult
    data object OfflineConflict : DriverStatusResult
    data class Failed(val message: String) : DriverStatusResult
}

class DriverStatusRepository @Inject constructor(
    private val api: DriverStatusApi,
    private val auth: DriverAuthRepository,
) {
    suspend fun goOnline(): DriverStatusResult = update(true, auth.currentAccessToken())
    suspend fun goOffline(): DriverStatusResult = update(false, auth.currentAccessToken())

    // Bind commands to the verified owner, including queued work during account changes.
    fun onlineForCurrentSession(): suspend () -> DriverStatusResult {
        val token = auth.currentAccessToken()
        return { update(true, token) }
    }

    fun offlineForCurrentSession(): suspend () -> DriverStatusResult {
        val token = auth.currentAccessToken()
        return { update(false, token) }
    }

    private suspend fun update(online: Boolean, token: String?): DriverStatusResult {
        if (token == null) return DriverStatusResult.SessionExpired
        return withTimeoutOrNull(DriverLocationPolicy.HTTP_TIMEOUT_MS) { send(online, token) }
            ?: DriverStatusResult.Failed("Yêu cầu nhận đơn quá thời gian chờ. Trạng thái chưa xác nhận; vui lòng tải lại.")
    }

    private suspend fun send(online: Boolean, token: String): DriverStatusResult {
        val response = try {
            api.updateStatus("Bearer $token", DriverStatusRequest(isOnline = online))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return DriverStatusResult.Failed("Chưa xác nhận được yêu cầu nhận đơn. Kiểm tra Internet và tải lại; máy chủ có thể đã nhận yêu cầu.")
        } catch (_: Exception) {
            return DriverStatusResult.Failed("Chưa xác nhận được trạng thái nhận đơn. Vui lòng tải lại trước khi thao tác tiếp.")
        }
        if (response.code() == 200) {
            return if (response.body()?.isOnline == online) {
                if (online) DriverStatusResult.OnlineConfirmed else DriverStatusResult.OfflineConfirmed
            } else DriverStatusResult.Failed("Phản hồi trạng thái không hợp lệ. Chưa thể xác nhận; vui lòng tải lại.")
        }
        response.errorBody()?.close()
        return when (response.code()) {
            401 -> DriverStatusResult.SessionExpired
            403 -> DriverStatusResult.Failed("Máy chủ từ chối thay đổi trạng thái nhận đơn. Vui lòng tải lại hồ sơ để kiểm tra quyền truy cập.")
            409 -> if (!online) DriverStatusResult.OfflineConflict else
                DriverStatusResult.Failed("Trạng thái hoặc điều kiện nhận đơn đã thay đổi. Vui lòng tải lại trạng thái trước khi thử tiếp.")
            400 -> DriverStatusResult.Failed("Máy chủ không chấp nhận yêu cầu trạng thái. Vui lòng tải lại hoặc thử sau.")
            404 -> DriverStatusResult.Failed("Không tìm thấy hồ sơ tài xế. Vui lòng tải lại hoặc liên hệ hỗ trợ.")
            else -> DriverStatusResult.Failed("Máy chủ chưa xác nhận yêu cầu nhận đơn. Vui lòng tải lại trạng thái và thử sau.")
        }
    }
}
