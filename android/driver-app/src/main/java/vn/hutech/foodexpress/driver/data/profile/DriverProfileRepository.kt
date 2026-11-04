package vn.hutech.foodexpress.driver.data.profile

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import retrofit2.Response
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

sealed interface DriverProfileResult {
    data class Loaded(val profile: DriverProfile) : DriverProfileResult
    data object SessionExpired : DriverProfileResult
    data class Failed(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val reloadRequired: Boolean = false,
    ) : DriverProfileResult
}

class DriverProfileRepository @Inject constructor(
    private val api: DriverProfileApi,
    private val auth: DriverAuthRepository,
) {
    suspend fun getProfile(): DriverProfileResult = request(updating = false) { api.getProfile(it) }

    suspend fun updateVehicle(vehicleType: String, licensePlate: String?): DriverProfileResult =
        request(updating = true) { api.updateProfile(it, DriverProfileUpdate(vehicleType, licensePlate)) }

    private suspend fun request(
        updating: Boolean,
        call: suspend (String) -> Response<DriverProfile>,
    ): DriverProfileResult {
        // Read the Login repository's verified in-memory session; never maintain a second token store.
        val token = auth.currentAccessToken() ?: return DriverProfileResult.SessionExpired
        val response = try {
            call("Bearer $token")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return DriverProfileResult.Failed(
                if (updating) "Chưa xác nhận được cập nhật. Vui lòng kiểm tra Internet và tải lại hồ sơ trước khi sửa tiếp."
                else "Không thể tải hồ sơ. Vui lòng kiểm tra Internet và thử lại.",
                reloadRequired = updating,
            )
        } catch (_: Exception) {
            return DriverProfileResult.Failed("Không thể xác nhận phản hồi máy chủ. Vui lòng tải lại hồ sơ.", reloadRequired = updating)
        }
        if (response.isSuccessful) {
            val profile = response.body()
            return if (profile != null && !profile.fullName.isNullOrBlank() && !profile.phone.isNullOrBlank()
                && profile.vehicleType in setOf("MOTORBIKE", "BICYCLE", "CAR")
                && profile.status in setOf("PENDING", "APPROVED", "REJECTED", "SUSPENDED")
                && profile.isOnline != null
            ) DriverProfileResult.Loaded(profile)
            else DriverProfileResult.Failed("Phản hồi hồ sơ không hợp lệ. Vui lòng tải lại.", reloadRequired = updating)
        }
        val error = response.errorBody()?.use { body ->
            try { JSONObject(body.string()) } catch (_: Exception) { null }
        }
        if (response.code() == 401 || (!updating && response.code() == 403)) return DriverProfileResult.SessionExpired
        val fields = mutableMapOf<String, String>()
        val serverFields = error?.optJSONObject("fieldErrors")
        mapOf("vehicleType" to "vehicleType", "licensePlate" to "licensePlate", "licensePlateValid" to "licensePlate")
            .forEach { (server, form) ->
                serverFields?.optString(server)?.takeIf { it.isNotBlank() }?.let { fields[form] = it.take(300) }
            }
        val message = when (response.code()) {
            400 -> "Thông tin phương tiện chưa hợp lệ. Vui lòng kiểm tra lại."
            403 -> "Hồ sơ hiện không cho phép sửa phương tiện. Vui lòng tải lại để xem trạng thái mới nhất."
            404 -> "Không tìm thấy hồ sơ tài xế. Vui lòng liên hệ hỗ trợ hoặc thử tải lại."
            409 -> "Hồ sơ đã thay đổi trong lúc cập nhật. Vui lòng tải lại trước khi sửa tiếp."
            else -> "Máy chủ chưa xử lý được yêu cầu. Vui lòng tải lại hồ sơ."
        }
        return DriverProfileResult.Failed(message, fields, reloadRequired = updating && response.code() != 400)
    }
}
