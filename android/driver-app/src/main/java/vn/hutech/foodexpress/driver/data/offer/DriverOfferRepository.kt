package vn.hutech.foodexpress.driver.data.offer

import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

data class DriverOffer(
    val offerId: Long, val orderId: Long, val orderCode: String,
    val restaurantName: String, val restaurantAddress: String,
    val pickupLat: Double, val pickupLng: Double, val deliveryAddress: String,
    val distanceKm: Double, val offeredAt: LocalDateTime, val expiresAt: LocalDateTime,
)

enum class OfferAction(val confirmedStatus: String) { ACCEPT("ACCEPTED"), REJECT("REJECTED") }

sealed interface OffersResult {
    data class Loaded(val offers: List<DriverOffer>, val serverTime: LocalDateTime, val requestStartedMillis: Long) : OffersResult
    data object SessionExpired : OffersResult
    data class Failed(val message: String) : OffersResult
}

sealed interface OfferActionResult {
    data class Confirmed(val offerId: Long, val orderId: Long, val status: String) : OfferActionResult
    data object SessionExpired : OfferActionResult
    data object Unavailable : OfferActionResult
    // Neither transport errors nor a 503 establish whether a POST committed.
    data class Unknown(val message: String) : OfferActionResult
}

interface DriverOffersSource {
    suspend fun load(): OffersResult
    suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult
}

class DriverOfferRepository @Inject constructor(
    private val api: DriverOfferApi,
    private val auth: DriverAuthRepository,
    private val clock: OfferMonotonicClock,
) : DriverOffersSource {
    override suspend fun load(): OffersResult {
        val token = auth.currentAccessToken() ?: return OffersResult.SessionExpired
        val started = clock.nowMillis()
        return withTimeoutOrNull(HTTP_TIMEOUT_MS) {
            try {
                val response = api.getOffers("Bearer $token")
                if (response.code() == 401) {
                    response.errorBody()?.close()
                    return@withTimeoutOrNull OffersResult.SessionExpired
                }
                if (response.code() != 200) {
                    response.errorBody()?.close()
                    return@withTimeoutOrNull OffersResult.Failed("Chưa tải được lời mời. Vui lòng thử lại.")
                }
                val body = requireNotNull(response.body())
                val now = LocalDateTime.parse(requireNotNull(body.serverTime))
                val offers = requireNotNull(body.offers).map { requireNotNull(it).validated(now) }
                require(offers.map { it.offerId }.distinct().size == offers.size)
                OffersResult.Loaded(offers, now, started)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OffersResult.Failed("Không thể tải lời mời. Kiểm tra kết nối mạng và thử lại.")
            }
        } ?: OffersResult.Failed("Tải lời mời quá thời gian chờ. Kiểm tra kết nối và thử lại.")
    }

    override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult {
        val token = auth.currentAccessToken() ?: return OfferActionResult.SessionExpired
        return withTimeoutOrNull(HTTP_TIMEOUT_MS) {
            try {
                val response = when (action) {
                    OfferAction.ACCEPT -> api.accept("Bearer $token", offerId)
                    OfferAction.REJECT -> api.reject("Bearer $token", offerId)
                }
                if (response.code() == 200) {
                    val body = requireNotNull(response.body())
                    require(body.offerId == offerId && body.orderId == orderId && body.status == action.confirmedStatus)
                    LocalDateTime.parse(requireNotNull(body.respondedAt))
                    return@withTimeoutOrNull OfferActionResult.Confirmed(offerId, orderId, action.confirmedStatus)
                }
                response.errorBody()?.close()
                when (response.code()) {
                    401 -> OfferActionResult.SessionExpired
                    400, 403, 404, 409 -> OfferActionResult.Unavailable
                    503 -> OfferActionResult.Unknown(if (action == OfferAction.REJECT)
                        "Có thể đã từ chối thành công nhưng bước tìm tài xế tiếp theo gặp lỗi. Gửi lại đúng yêu cầu để xác minh."
                        else "Máy chủ chưa xác nhận kết quả nhận đơn. Gửi lại đúng yêu cầu để xác minh.")
                    else -> OfferActionResult.Unknown("Chưa xác định được kết quả. Vui lòng xác minh lại đúng thao tác.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OfferActionResult.Unknown("Mất kết nối hoặc phản hồi không hợp lệ. Máy chủ có thể đã xử lý; hãy xác minh lại.")
            }
        } ?: OfferActionResult.Unknown("Hết thời gian chờ phản hồi. Máy chủ có thể đã xử lý; hãy xác minh lại.")
    }

    private fun DriverOfferDto.validated(now: LocalDateTime): DriverOffer {
        val id = requireNotNull(offerId); val order = requireNotNull(orderId)
        val lat = requireNotNull(pickupLat); val lng = requireNotNull(pickupLng)
        val distance = requireNotNull(distanceKm)
        val offered = LocalDateTime.parse(requireNotNull(offeredAt))
        val expires = LocalDateTime.parse(requireNotNull(expiresAt))
        require(id > 0 && order > 0 && lat.isFinite() && lat in -90.0..90.0 && lng.isFinite() && lng in -180.0..180.0)
        require(distance.isFinite() && distance >= 0 && offered <= now && expires > now && expires > offered)
        fun text(value: String?): String = requireNotNull(value).also { require(it.isNotBlank()) }
        return DriverOffer(id, order, text(orderCode), text(restaurantName), text(restaurantAddress), lat, lng,
            text(deliveryAddress), distance, offered, expires)
    }

    private companion object { const val HTTP_TIMEOUT_MS = 12_000L }
}
