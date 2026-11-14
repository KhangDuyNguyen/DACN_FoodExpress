package vn.hutech.foodexpress.driver.data.report

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DriverReportsApi {
    @GET("driver/earnings")
    suspend fun earnings(@Header("Authorization") token: String, @Query("from") from: String,
        @Query("to") to: String): Response<EarningsDto>
    @GET("driver/orders/history")
    suspend fun history(@Header("Authorization") token: String, @Query("page") page: Int,
        @Query("size") size: Int): Response<HistoryDto>
}
data class EarningsDto(val from: String? = null, val to: String? = null, val timeZone: String? = null,
    val baseAmount: BigDecimal? = null, val bonus: BigDecimal? = null, val totalAmount: BigDecimal? = null,
    val items: List<EarningDto>? = null)
data class EarningDto(val orderId: Long? = null, val orderCode: String? = null, val amount: BigDecimal? = null,
    val bonus: BigDecimal? = null, val totalAmount: BigDecimal? = null, val createdAt: String? = null)
data class HistoryDto(val items: List<HistoryOrderDto>? = null, val page: Int? = null, val size: Int? = null,
    val totalItems: Long? = null, val totalPages: Int? = null)
data class HistoryOrderDto(val orderId: Long? = null, val orderCode: String? = null, val restaurantId: Long? = null,
    val restaurantName: String? = null, val status: String? = null, val deliveredAt: String? = MISSING_TIME)
internal const val MISSING_TIME = "\u0000missing-deliveredAt"

@Module @InstallIn(SingletonComponent::class)
object DriverReportsModule {
    @Provides @Singleton fun api(retrofit: Retrofit): DriverReportsApi = retrofit.create(DriverReportsApi::class.java)
    @Provides fun source(repository: DriverReportsRepository): DriverReportsSource = repository
}
