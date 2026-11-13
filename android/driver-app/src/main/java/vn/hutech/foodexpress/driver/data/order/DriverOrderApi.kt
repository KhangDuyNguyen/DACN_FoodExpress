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
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink

interface DriverOrderApi {
    // Preserve presence: missing currentOrder is not a confirmed null.
    @GET("driver/orders/current")
    suspend fun current(@Header("Authorization") authorization: String): Response<DriverCurrentOrderDto>

    @PATCH("driver/orders/{id}/status")
    suspend fun changeStatus(@Header("Authorization") authorization: String, @Path("id") orderId: Long,
        @Body request: DriverDeliveryRequest): Response<DriverDeliveryDto>
}

data class DriverDeliveryRequest(val status: String)
data class DriverDeliveryDto(val orderId: Long? = null, val status: String? = null,
    val updatedAt: String? = null, val deliveredAt: String? = MISSING_NULLABLE_FIELD)

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
    fun api(retrofit: Retrofit): DriverOrderApi {
        val reads = retrofit.create(DriverOrderApi::class.java)
        // Keep existing GET transport. Writes never follow redirects or retry a lost response automatically.
        val transport = requireNotNull(retrofit.callFactory() as? OkHttpClient)
        val writes = retrofit.newBuilder().client(transport.newBuilder()
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val body = requireNotNull(request.body)
                // Also prevent Retry-After follow-ups (408/503); explicit user replay creates a new Call/body.
                val once = object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                }
                chain.proceed(request.newBuilder().method(request.method, once).build())
            }.build())
            .build().create(DriverOrderApi::class.java)
        return object : DriverOrderApi {
            override suspend fun current(authorization: String) = reads.current(authorization)
            override suspend fun changeStatus(authorization: String, orderId: Long, request: DriverDeliveryRequest) =
                writes.changeStatus(authorization, orderId, request)
        }
    }
    @Provides @Singleton
    fun source(repository: DriverOrderRepository): DriverCurrentOrderSource = repository
}
