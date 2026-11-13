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
data class DriverDeliveryStatus(val orderId: Long, val status: String,
    val updatedAt: LocalDateTime, val deliveredAt: LocalDateTime?)

sealed interface DriverDeliveryResult {
    data class Updated(val value: DriverDeliveryStatus) : DriverDeliveryResult
    data object SessionExpired : DriverDeliveryResult
    data object Forbidden : DriverDeliveryResult
    data class Rejected(val message: String) : DriverDeliveryResult
    data class Uncertain(val message: String) : DriverDeliveryResult
}

fun interface DriverCurrentOrderSource {
    suspend fun load(): CurrentOrderResult
    // Read-only adapters remain usable; the production Repository implements the command explicitly.
    suspend fun changeStatus(orderId: Long, target: String): DriverDeliveryResult =
        DriverDeliveryResult.Rejected("Chức năng cập nhật chưa sẵn sàng. Vui lòng thử lại.")
}

class DriverOrderRepository @Inject constructor(
    private val api: DriverOrderApi,
    private val auth: DriverAuthRepository,
) : DriverCurrentOrderSource {
    override suspend fun changeStatus(orderId: Long, target: String): DriverDeliveryResult {
        if (orderId <= 0 || target !in setOf("PICKED_UP", "DELIVERING", "DELIVERED")) {
            return DriverDeliveryResult.Rejected("Thao tác giao hàng không hợp lệ. Vui lòng cập nhật lại đơn.")
        }
        // Capture once: a delayed command must never acquire the next account's token.
        val token = auth.currentAccessToken() ?: return DriverDeliveryResult.SessionExpired
        return withTimeoutOrNull(12_000) {
            try {
                val response = api.changeStatus("Bearer $token", orderId, DriverDeliveryRequest(target))
                if (response.code() != 200) {
                    response.errorBody()?.close()
                    return@withTimeoutOrNull when (response.code()) {
                        401 -> DriverDeliveryResult.SessionExpired
                        403 -> DriverDeliveryResult.Forbidden
                        400 -> DriverDeliveryResult.Rejected("Yêu cầu chưa hợp lệ. Vui lòng kiểm tra lại trạng thái đơn hàng.")
                        404 -> DriverDeliveryResult.Rejected("Không tìm thấy đơn có thể thao tác. Vui lòng kiểm tra lại.")
                        409 -> DriverDeliveryResult.Rejected("Đơn hàng đã thay đổi. Hãy kiểm tra trạng thái mới nhất trước khi tiếp tục.")
                        else -> DriverDeliveryResult.Uncertain("Chưa xác định được kết quả cập nhật. Vui lòng kiểm tra lại.")
                    }
                }
                val body = requireNotNull(response.body())
                require(body.orderId == orderId && body.status == target)
                val updatedAt = LocalDateTime.parse(requireNotNull(body.updatedAt))
                require(body.deliveredAt != MISSING_NULLABLE_FIELD)
                val deliveredAt = body.deliveredAt?.let(LocalDateTime::parse)
                require((target == "DELIVERED") == (deliveredAt != null))
                DriverDeliveryResult.Updated(DriverDeliveryStatus(orderId, target, updatedAt, deliveredAt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DriverDeliveryResult.Uncertain("Chưa xác định được kết quả: kết nối gián đoạn hoặc phản hồi chưa hợp lệ. Vui lòng kiểm tra lại.")
            }
        } ?: DriverDeliveryResult.Uncertain("Chưa nhận được kết quả trong thời gian chờ. Đơn có thể đã được cập nhật; hãy kiểm tra lại.")
    }

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
