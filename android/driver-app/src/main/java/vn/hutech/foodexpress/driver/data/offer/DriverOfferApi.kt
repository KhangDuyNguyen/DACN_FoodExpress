package vn.hutech.foodexpress.driver.data.offer

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// Nullable wire fields let the repository reject incomplete JSON instead of displaying defaults.
data class DriverOffersDto(val offers: List<DriverOfferDto?>? = null, val serverTime: String? = null)

data class DriverOfferDto(
    val offerId: Long? = null,
    val orderId: Long? = null,
    val orderCode: String? = null,
    val restaurantName: String? = null,
    val restaurantAddress: String? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val deliveryAddress: String? = null,
    val distanceKm: Double? = null,
    val offeredAt: String? = null,
    val expiresAt: String? = null,
)

data class DriverOfferActionDto(
    val offerId: Long? = null,
    val orderId: Long? = null,
    val status: String? = null,
    val respondedAt: String? = null,
)

interface DriverOfferApi {
    @GET("driver/offers")
    suspend fun getOffers(@Header("Authorization") authorization: String): Response<DriverOffersDto>

    @POST("driver/offers/{id}/accept")
    suspend fun accept(@Header("Authorization") authorization: String, @Path("id") id: Long): Response<DriverOfferActionDto>

    @POST("driver/offers/{id}/reject")
    suspend fun reject(@Header("Authorization") authorization: String, @Path("id") id: Long): Response<DriverOfferActionDto>
}

fun interface OfferMonotonicClock { fun nowMillis(): Long }

@Module
@InstallIn(SingletonComponent::class)
object DriverOfferModule {
    @Provides @Singleton
    fun api(retrofit: Retrofit): DriverOfferApi = retrofit.create(DriverOfferApi::class.java)

    @Provides @Singleton
    fun source(repository: DriverOfferRepository): DriverOffersSource = repository

    @Provides @Singleton
    fun clock(): OfferMonotonicClock = OfferMonotonicClock { SystemClock.elapsedRealtime() }
}
