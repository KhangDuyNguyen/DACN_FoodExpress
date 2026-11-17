package vn.hutech.foodexpress.customer.orders.data.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Singleton

// ── Models ────────────────────────────────────────────────────────────────

data class OrderSummary(
    val id: Long = 0,
    val code: String = "",
    val restaurantName: String = "",
    val status: String = "",
    val totalAmount: Double = 0.0,
    val itemCount: Int = 0,
    val createdAt: String = "",
)

data class OrderPageResponse(
    val items: List<OrderSummary> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalItems: Long = 0,
    val totalPages: Int = 0,
)

data class ReviewReply(
    val id: Long = 0,
    val merchantName: String? = null,
    val content: String = "",
    val createdAt: String = "",
)

data class ReviewResponse(
    val id: Long = 0,
    val orderId: Long = 0,
    val customerName: String = "",
    val restaurantRating: Int = 0,
    val driverRating: Int? = null,
    val comment: String? = null,
    val createdAt: String = "",
    val reply: ReviewReply? = null,
)

data class CreateReviewRequest(
    val restaurantRating: Int,
    val driverRating: Int?,
    val comment: String?,
)

// ── API Interface ─────────────────────────────────────────────────────────

interface OrderApiService {

    @GET("orders")
    suspend fun getMyOrders(
        @Header("Authorization") authorization: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("status") status: String?,
    ): OrderPageResponse

    @POST("orders/{orderId}/review")
    suspend fun createReview(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: Long,
        @Body body: CreateReviewRequest,
    ): ReviewResponse

    @GET("orders/{orderId}/review")
    suspend fun getOrderReview(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: Long,
    ): ReviewResponse
}

@Module
@InstallIn(SingletonComponent::class)
object OrderModule {
    @Provides
    @Singleton
    fun provideOrderApiService(retrofit: Retrofit): OrderApiService =
        retrofit.create(OrderApiService::class.java)
}
