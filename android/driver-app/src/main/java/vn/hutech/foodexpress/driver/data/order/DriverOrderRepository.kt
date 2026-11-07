package vn.hutech.foodexpress.driver.data.order

import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

data class DriverCurrentOrder(
    val orderId: Long, val orderCode: String, val status: String,
    val createdAt: LocalDateTime, val updatedAt: LocalDateTime,
    val restaurantId: Long, val restaurantName: String, val restaurantAddress: String,
    val restaurantPhone: String?, val pickupLat: Double, val pickupLng: Double,
    val receiverName: String, val receiverPhone: String, val deliveryAddress: String,
    val deliveryLat: Double, val deliveryLng: Double, val note: String?,
)

sealed interface CurrentOrderResult {
    data class Loaded(val order: DriverCurrentOrder?, val serverTime: LocalDateTime) : CurrentOrderResult
    data object SessionExpired : CurrentOrderResult
    data class Failed(val message: String) : CurrentOrderResult
}
fun interface DriverCurrentOrderSource { suspend fun load(): CurrentOrderResult }

class DriverOrderRepository @Inject constructor(
    private val api: DriverOrderApi,
    private val auth: DriverAuthRepository,
) : DriverCurrentOrderSource {
    override suspend fun load(): CurrentOrderResult {
        val token = auth.currentAccessToken() ?: return CurrentOrderResult.SessionExpired
        return withTimeoutOrNull(12_000) {
            try {
                val response = api.current("Bearer $token")
                if (response.code() != 200) {
                    response.errorBody()?.close()
                    return@withTimeoutOrNull when (response.code()) {
                        401 -> CurrentOrderResult.SessionExpired
                        404 -> CurrentOrderResult.Failed("Không tìm thấy hồ sơ tài xế. Chưa xác minh được đơn hiện tại.")
                        409 -> CurrentOrderResult.Failed("Máy chủ phát hiện nhiều đơn hoạt động. Vui lòng liên hệ hỗ trợ.")
                        else -> CurrentOrderResult.Failed("Chưa đồng bộ được đơn hiện tại. Vui lòng thử lại.")
                    }
                }
                val body = requireNotNull(response.body())
                val now = LocalDateTime.parse(requireNotNull(body.serverTime))
                CurrentOrderResult.Loaded(body.currentOrder?.validated(), now)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CurrentOrderResult.Failed("Không thể đồng bộ đơn hiện tại: mất kết nối hoặc dữ liệu phản hồi không hợp lệ.")
            }
        } ?: CurrentOrderResult.Failed("Đồng bộ đơn hiện tại quá thời gian chờ. Vui lòng thử lại.")
    }

    private fun DriverOrderDto.validated(): DriverCurrentOrder {
        fun text(value: String?) = requireNotNull(value).also { require(it.isNotBlank()) }
        fun id(value: Long?) = requireNotNull(value).also { require(it > 0) }
        fun coordinate(value: Double?, latitude: Boolean) = requireNotNull(value).also {
            require(it.isFinite() && it in (if (latitude) -90.0..90.0 else -180.0..180.0))
        }
        val activeStatus = text(status)
        require(activeStatus in setOf("PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERING"))
        require(restaurantPhone != MISSING_NULLABLE_FIELD && note != MISSING_NULLABLE_FIELD)
        return DriverCurrentOrder(id(orderId), text(orderCode), activeStatus,
            LocalDateTime.parse(text(createdAt)), LocalDateTime.parse(text(updatedAt)),
            id(restaurantId), text(restaurantName), text(restaurantAddress), restaurantPhone,
            coordinate(pickupLat, true), coordinate(pickupLng, false), text(receiverName), text(receiverPhone),
            text(deliveryAddress), coordinate(deliveryLat, true), coordinate(deliveryLng, false), note)
    }
}
