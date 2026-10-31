package vn.hutech.foodexpress.driver.data.location

import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

sealed interface DriverLocationResult {
    data object Recorded : DriverLocationResult
    data object SessionExpired : DriverLocationResult
    data class Failed(val message: String) : DriverLocationResult
}

class DriverLocationRepository @Inject constructor(
    private val api: DriverLocationApi,
    private val auth: DriverAuthRepository,
) {
    suspend fun record(fix: DriverLocationFix, nowNanos: Long): DriverLocationResult {
        if (DriverLocationPolicy.problem(fix, nowNanos) != null) {
            return DriverLocationResult.Failed("Vị trí chưa đủ mới hoặc chính xác. Vui lòng thử lại GPS.")
        }
        val token = auth.currentAccessToken() ?: return DriverLocationResult.SessionExpired
        return withTimeoutOrNull(DriverLocationPolicy.HTTP_TIMEOUT_MS) {
            val response = try {
                api.recordLocation("Bearer $token", DriverLocationRequest(fix.lat, fix.lng))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                return@withTimeoutOrNull DriverLocationResult.Failed("Chưa xác nhận được vị trí. Kiểm tra Internet và thử lại; chưa bật nhận đơn.")
            } catch (_: Exception) {
                return@withTimeoutOrNull DriverLocationResult.Failed("Phản hồi vị trí không hợp lệ. Chưa bật nhận đơn.")
            }
            if (response.code() == 201) {
                val body = response.body()
                val lat = (body?.lat as? Number)?.toDouble()
                val lng = (body?.lng as? Number)?.toDouble()
                val timeValid = try { LocalDateTime.parse(body?.recordedAt); true } catch (_: Exception) { false }
                if (lat != null && lng != null && lat.isFinite() && lng.isFinite()
                    && abs(lat - fix.lat) <= 0.0000001 && abs(lng - fix.lng) <= 0.0000001 && timeValid
                ) DriverLocationResult.Recorded
                else DriverLocationResult.Failed("Máy chủ chưa xác nhận vị trí hợp lệ. Vui lòng thử lại.")
            } else {
                response.errorBody()?.close()
                when (response.code()) {
                    401 -> DriverLocationResult.SessionExpired
                    400 -> DriverLocationResult.Failed("Máy chủ không chấp nhận tọa độ. Vui lòng lấy lại GPS.")
                    403 -> DriverLocationResult.Failed("Không có quyền gửi vị trí. Vui lòng tải lại hồ sơ để xác minh quyền.")
                    404 -> DriverLocationResult.Failed("Không tìm thấy hồ sơ tài xế. Vui lòng tải lại hồ sơ.")
                    else -> DriverLocationResult.Failed("Máy chủ chưa xác nhận vị trí. Vui lòng thử lại khi có kết nối.")
                }
            }
        } ?: DriverLocationResult.Failed("Gửi vị trí quá thời gian chờ. Chưa bật nhận đơn; vui lòng thử lại.")
    }
}
