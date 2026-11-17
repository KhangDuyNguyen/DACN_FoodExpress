package vn.hutech.foodexpress.driver.data.order

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header

interface DriverOrderApi {
    // Preserve presence: missing currentOrder is not a confirmed null.
    @GET("driver/orders/current")
    suspend fun current(@Header("Authorization") authorization: String): Response<DriverCurrentOrderDto>
}

// A missing field keeps the invalid empty object; an explicit JSON null becomes null.
data class DriverCurrentOrderDto(
    val currentOrder: DriverOrderDto? = DriverOrderDto(),
    val serverTime: String? = null,
)

data class DriverOrderDto(
    val orderId: Long? = null, val orderCode: String? = null, val status: String? = null,
    val createdAt: String? = null, val updatedAt: String? = null,
    val restaurantId: Long? = null, val restaurantName: String? = null, val restaurantAddress: String? = null,
    val restaurantPhone: String? = MISSING_NULLABLE_FIELD,
    val pickupLat: Double? = null, val pickupLng: Double? = null,
    val receiverName: String? = null, val receiverPhone: String? = null, val deliveryAddress: String? = null,
    val deliveryLat: Double? = null, val deliveryLng: Double? = null,
    val note: String? = MISSING_NULLABLE_FIELD,
)

internal const val MISSING_NULLABLE_FIELD = "\u0000missing-json-field"

@Module
@InstallIn(SingletonComponent::class)
object DriverOrderModule {
    @Provides @Singleton
    fun api(retrofit: Retrofit): DriverOrderApi = retrofit.create(DriverOrderApi::class.java)
    @Provides @Singleton
    fun source(repository: DriverOrderRepository): DriverCurrentOrderSource = repository
}
