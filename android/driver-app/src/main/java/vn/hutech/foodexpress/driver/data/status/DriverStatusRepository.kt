package vn.hutech.foodexpress.driver.data.status

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

sealed interface DriverStatusResult {
    data object OfflineConfirmed : DriverStatusResult
    data object SessionExpired : DriverStatusResult
    data class Failed(val message: String) : DriverStatusResult
}

class DriverStatusRepository @Inject constructor(
    private val api: DriverStatusApi,
    private val auth: DriverAuthRepository,
) {
    // Online is deliberately unavailable until foreground GPS can verify a fresh Android location.
    suspend fun goOffline(): DriverStatusResult {
        val token = auth.currentAccessToken() ?: return DriverStatusResult.SessionExpired
        val response = try {
            api.updateStatus("Bearer $token", DriverStatusRequest(isOnline = false))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return DriverStatusResult.Failed("Chưa xác nhận được yêu cầu Offline. Kiểm tra Internet và tải lại trạng thái; máy chủ có thể chưa nhận được yêu cầu.")
        } catch (_: Exception) {
            return DriverStatusResult.Failed("Chưa xác nhận được trạng thái nhận đơn. Vui lòng tải lại trước khi thao tác tiếp.")
        }
        if (response.isSuccessful) {
            return if (response.body()?.isOnline == false) DriverStatusResult.OfflineConfirmed
            else DriverStatusResult.Failed("Phản hồi trạng thái không hợp lệ. Chưa thể xác nhận Offline; vui lòng tải lại.")
        }
        response.errorBody()?.close()
        return when (response.code()) {
            401 -> DriverStatusResult.SessionExpired
            403 -> DriverStatusResult.Failed("Máy chủ từ chối thay đổi trạng thái nhận đơn. Vui lòng tải lại hồ sơ để kiểm tra quyền truy cập.")
            409 -> DriverStatusResult.Failed("Trạng thái hoặc điều kiện nhận đơn đã thay đổi. Vui lòng tải lại trạng thái trước khi thử tiếp.")
            400 -> DriverStatusResult.Failed("Máy chủ không chấp nhận yêu cầu trạng thái. Vui lòng tải lại hoặc thử sau.")
            404 -> DriverStatusResult.Failed("Không tìm thấy hồ sơ tài xế. Vui lòng tải lại hoặc liên hệ hỗ trợ.")
            else -> DriverStatusResult.Failed("Máy chủ chưa xác nhận yêu cầu Offline. Vui lòng tải lại trạng thái và thử sau.")
        }
    }
}
