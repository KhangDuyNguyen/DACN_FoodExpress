package vn.hutech.foodexpress.driver.data.auth

import android.util.Base64
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.hutech.foodexpress.driver.data.session.DriverSessionStore

sealed interface DriverAuthResult {
    data object NoSession : DriverAuthResult
    data class Authenticated(val user: DriverAuthUser, val expiresAtSeconds: Long) : DriverAuthResult
    data class Rejected(val message: String) : DriverAuthResult
    data class Retry(val message: String) : DriverAuthResult
}

@Singleton
class DriverAuthRepository @Inject constructor(
    private val api: DriverAuthApi,
    private val sessions: DriverSessionStore,
) {
    private var memoryToken: String? = null

    suspend fun register(request: DriverRegisterRequest): DriverRegisterResult {
        val response = try {
            api.register(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return DriverRegisterResult.Failed(
                "Chưa nhận được xác nhận đăng ký. Vui lòng kiểm tra Internet và thử lại. Nếu tài khoản đã được tạo, hãy đăng nhập.",
            )
        } catch (_: Exception) {
            return DriverRegisterResult.Failed("Không thể xác nhận kết quả đăng ký. Vui lòng thử đăng nhập hoặc thử lại sau.")
        }
        if (response.code() == 201) {
            val user = response.body()?.user
            return if (user != null && user.id > 0 && user.role == "DRIVER" && user.status == "ACTIVE") {
                // No JWT is deserialized, stored or assigned to memoryToken in this path.
                DriverRegisterResult.Success
            } else {
                DriverRegisterResult.Failed("Máy chủ đã nhận đăng ký nhưng phản hồi không hợp lệ. Vui lòng thử đăng nhập trước khi gửi lại.")
            }
        }
        val error = response.errorBody()?.use { body ->
            try { JSONObject(body.string()) } catch (_: Exception) { null }
        }
        val fields = mutableMapOf<String, String>()
        val serverFields = error?.optJSONObject("fieldErrors")
        val names = mapOf(
            "fullName" to "fullName", "phone" to "phone", "email" to "email", "password" to "password",
            "vehicleType" to "vehicleType", "licensePlate" to "licensePlate",
            "driverVehicleTypeValid" to "vehicleType", "driverLicensePlateValid" to "licensePlate",
        )
        names.forEach { (server, form) ->
            serverFields?.optString(server)?.takeIf { it.isNotBlank() }?.let { fields[form] = it.take(300) }
        }
        return when (response.code()) {
            400 -> DriverRegisterResult.Failed("Vui lòng kiểm tra thông tin đăng ký.", fields)
            409 -> {
                val message = error?.optString("message")?.takeIf { it.isNotBlank() }?.take(300)
                    ?: "Số điện thoại hoặc email đã được đăng ký."
                if (message.contains("điện thoại", ignoreCase = true)) fields["phone"] = message
                if (message.contains("email", ignoreCase = true)) fields["email"] = message
                DriverRegisterResult.Failed(message, fields)
            }
            else -> DriverRegisterResult.Failed("Máy chủ tạm thời chưa xử lý được đăng ký. Vui lòng thử lại sau.")
        }
    }

    suspend fun login(identifier: String, password: String, rememberLogin: Boolean): DriverAuthResult {
        val response = try {
            api.login(DriverLoginRequest(identifier.trim(), password))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return DriverAuthResult.Retry(NETWORK_ERROR)
        } catch (_: Exception) {
            return DriverAuthResult.Retry(INVALID_RESPONSE)
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return when (response.code()) {
                400 -> DriverAuthResult.Rejected("Thông tin đăng nhập không hợp lệ. Vui lòng kiểm tra lại.")
                401 -> DriverAuthResult.Rejected("Sai tài khoản hoặc mật khẩu.")
                403 -> DriverAuthResult.Rejected("Tài khoản đã bị khóa hoặc không được phép đăng nhập.")
                else -> DriverAuthResult.Retry(SERVER_ERROR)
            }
        }
        val body = response.body()
        val user = body?.user
        val rejection = rejectUser(user)
        if (rejection != null) return DriverAuthResult.Rejected(rejection)
        val token = body?.token ?: return DriverAuthResult.Retry(INVALID_RESPONSE)
        val expiry = tokenExpiry(token) ?: return DriverAuthResult.Retry(INVALID_RESPONSE)
        if (expiry <= nowSeconds()) return DriverAuthResult.Rejected(SESSION_EXPIRED)

        return try {
            withContext(Dispatchers.IO) {
                if (rememberLogin) sessions.saveToken(token) else sessions.clear()
            }
            memoryToken = token
            DriverAuthResult.Authenticated(requireNotNull(user), expiry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DriverAuthResult.Retry("Không thể lưu phiên đăng nhập trên thiết bị. Vui lòng thử lại.")
        }
    }

    suspend fun restoreSession(): DriverAuthResult {
        val token = try {
            memoryToken ?: withContext(Dispatchers.IO) { sessions.readToken() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return DriverAuthResult.Retry("Không thể đọc phiên đã lưu. Vui lòng thử lại.")
        } ?: return DriverAuthResult.NoSession

        val expiry = tokenExpiry(token)
        if (expiry == null || expiry <= nowSeconds()) return discardSession(SESSION_EXPIRED)
        val response = try {
            api.currentUser("Bearer $token")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // Keep the saved token, but never grant access without server verification.
            return DriverAuthResult.Retry("Chưa thể xác minh phiên đăng nhập. $NETWORK_ERROR")
        } catch (_: Exception) {
            return DriverAuthResult.Retry(INVALID_RESPONSE)
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return when (response.code()) {
                401 -> discardSession(SESSION_EXPIRED)
                403 -> discardSession("Tài khoản không còn quyền truy cập Driver App.")
                else -> DriverAuthResult.Retry(SERVER_ERROR)
            }
        }
        val user = response.body()
        if (user == null || user.id <= 0 || user.role == null || user.status == null) {
            return DriverAuthResult.Retry(INVALID_RESPONSE)
        }
        val rejection = rejectUser(user)
        if (rejection != null) return discardSession(rejection)
        memoryToken = token
        return DriverAuthResult.Authenticated(user, expiry)
    }

    suspend fun logout(): DriverAuthResult = discardSession(null)

    // Profile shares Login's session and expiry rule, including sessions without Remember Login.
    internal fun currentAccessToken(): String? = memoryToken?.takeIf { token ->
        tokenExpiry(token)?.let { it > nowSeconds() } == true
    }

    private suspend fun discardSession(message: String?): DriverAuthResult {
        memoryToken = null
        return try {
            withContext(Dispatchers.IO) { sessions.clear() }
            if (message == null) DriverAuthResult.NoSession else DriverAuthResult.Rejected(message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DriverAuthResult.Retry("Không thể xóa phiên trên thiết bị. Vui lòng thử lại.")
        }
    }

    private fun rejectUser(user: DriverAuthUser?): String? = when {
        user == null || user.id <= 0 || user.role == null || user.status == null -> INVALID_RESPONSE
        user.role != "DRIVER" -> "Tài khoản này không phải tài xế. Vui lòng dùng tài khoản DRIVER."
        user.status != "ACTIVE" -> "Tài khoản không còn hoạt động hoặc đã bị khóa."
        else -> null
    }

    // Decoding exp only schedules expiry; role/signature/account validity are verified by Backend.
    private fun tokenExpiry(token: String): Long? = try {
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { !it.matches(Regex("[A-Za-z0-9_-]+")) }) {
            null
        } else {
            JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
                .getLong("exp").takeIf { it > 0 }
        }
    } catch (_: Exception) {
        null
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1_000

    private companion object {
        const val NETWORK_ERROR = "Không thể kết nối máy chủ. Vui lòng kiểm tra Internet và thử lại."
        const val SERVER_ERROR = "Máy chủ tạm thời chưa xử lý được yêu cầu. Vui lòng thử lại."
        const val INVALID_RESPONSE = "Phản hồi máy chủ không hợp lệ. Vui lòng thử lại."
        const val SESSION_EXPIRED = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."
    }
}
