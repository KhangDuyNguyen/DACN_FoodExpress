package vn.hutech.foodexpress.driver.data.report

import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository

val REPORT_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
enum class EarningsPeriod(val label: String) { TODAY("Hôm nay"), WEEK("Tuần này"), MONTH("Tháng này") }
data class EarningsRange(val from: LocalDate, val to: LocalDate) {
    companion object {
        fun forPeriod(period: EarningsPeriod, clock: Clock): EarningsRange {
            val today = LocalDate.now(clock.withZone(REPORT_ZONE))
            val first = when (period) {
                EarningsPeriod.TODAY -> today
                EarningsPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                EarningsPeriod.MONTH -> today.withDayOfMonth(1)
            }
            // Inclusive Vietnam calendar dates; week/month are to date.
            return EarningsRange(first, today)
        }
    }
}
data class DriverEarning(val orderId: Long, val orderCode: String, val amount: BigDecimal,
    val bonus: BigDecimal, val totalAmount: BigDecimal, val createdAt: LocalDateTime)
data class DriverEarnings(val range: EarningsRange, val baseAmount: BigDecimal, val bonus: BigDecimal,
    val totalAmount: BigDecimal, val items: List<DriverEarning>)
data class DriverHistoryOrder(val orderId: Long, val orderCode: String, val restaurantId: Long,
    val restaurantName: String, val deliveredAt: LocalDateTime?)
data class DriverHistoryPage(val items: List<DriverHistoryOrder>, val page: Int, val size: Int,
    val totalItems: Long, val totalPages: Int)
sealed interface ReportsResult<out T> {
    data class Loaded<T>(val value: T) : ReportsResult<T>
    data object AuthRequired : ReportsResult<Nothing>
    data object Forbidden : ReportsResult<Nothing>
    data class Failed(val message: String) : ReportsResult<Nothing>
}
interface DriverReportsSource {
    suspend fun earnings(range: EarningsRange): ReportsResult<DriverEarnings>
    suspend fun history(page: Int, size: Int): ReportsResult<DriverHistoryPage>
}

class DriverReportsRepository @Inject constructor(private val api: DriverReportsApi,
    private val auth: DriverAuthRepository) : DriverReportsSource {
    override suspend fun earnings(range: EarningsRange): ReportsResult<DriverEarnings> {
        if (range.from > range.to) return ReportsResult.Failed("Khoảng ngày chưa hợp lệ.")
        return read({ api.earnings(it, range.from.toString(), range.to.toString()) }) { body ->
            require(body.from == range.from.toString() && body.to == range.to.toString() && body.timeZone == REPORT_ZONE.id)
            val base = money(body.baseAmount); val bonus = money(body.bonus); val total = money(body.totalAmount)
            require(base.add(bonus).compareTo(total) == 0)
            val items = requireNotNull(body.items).map { item ->
                val amount = money(item.amount); val extra = money(item.bonus); val sum = money(item.totalAmount)
                require(amount.add(extra).compareTo(sum) == 0)
                val at = LocalDateTime.parse(requireNotNull(item.createdAt))
                require(at.toLocalDate() in range.from..range.to)
                DriverEarning(id(item.orderId), text(item.orderCode), amount, extra, sum, at)
            }
            require(items.map { it.orderId }.distinct().size == items.size)
            // Always display the server total, never calculate an 80% fee from items.
            DriverEarnings(range, base, bonus, total, items)
        }
    }
    override suspend fun history(page: Int, size: Int): ReportsResult<DriverHistoryPage> {
        if (page < 0 || size !in 1..50) return ReportsResult.Failed("Trang lịch sử chưa hợp lệ.")
        return read({ api.history(it, page, size) }) { body ->
            require(body.page == page && body.size == size)
            val count = requireNotNull(body.totalItems); val pages = requireNotNull(body.totalPages)
            require(count >= 0 && pages >= 0 && pages.toLong() == (count + size - 1) / size)
            val items = requireNotNull(body.items).map { item ->
                require(item.status == "DELIVERED" && item.deliveredAt != MISSING_TIME)
                DriverHistoryOrder(id(item.orderId), text(item.orderCode), id(item.restaurantId), text(item.restaurantName),
                    item.deliveredAt?.let(LocalDateTime::parse))
            }
            require(items.size <= size && items.size.toLong() <= count && items.map { it.orderId }.distinct().size == items.size)
            DriverHistoryPage(items, page, size, count, pages)
        }
    }
    private suspend fun <W, T> read(call: suspend (String) -> Response<W>, validate: (W) -> T): ReportsResult<T> {
        val snapshot = auth.currentAccessToken() ?: return ReportsResult.AuthRequired
        return withTimeoutOrNull(12_000) {
            try {
                val response = call("Bearer $snapshot")
                if (response.code() != 200) {
                    response.errorBody()?.close()
                    return@withTimeoutOrNull when (response.code()) {
                        401 -> ReportsResult.AuthRequired
                        403 -> ReportsResult.Forbidden
                        404 -> ReportsResult.Failed("Không tìm thấy hồ sơ tài xế. Vui lòng kiểm tra lại phiên đăng nhập.")
                        else -> ReportsResult.Failed("Chưa thể tải dữ liệu. Vui lòng thử lại.")
                    }
                }
                ReportsResult.Loaded(validate(requireNotNull(response.body())))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ReportsResult.Failed("Không thể tải dữ liệu: kết nối gián đoạn hoặc phản hồi chưa hợp lệ.") }
        } ?: ReportsResult.Failed("Chưa nhận được dữ liệu trong thời gian chờ. Vui lòng thử lại.")
    }
    private fun money(value: BigDecimal?) = requireNotNull(value).also { require(it.signum() >= 0 && it.scale() <= 2) }
    private fun id(value: Long?) = requireNotNull(value).also { require(it > 0) }
    private fun text(value: String?) = requireNotNull(value).also { require(it.isNotBlank()) }
}
