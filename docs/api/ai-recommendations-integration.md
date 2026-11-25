# Tài liệu tích hợp AI Gợi ý món ăn cho App Khách (Android)

**Người phụ trách AI & Backend:** Sầm Xuân Điệp  
**Người phối hợp tích hợp App Khách:** Trần Xuân Dưỡng (`android/customer-app`)  
**Tuần:** Tuần 7 (23/11 – 28/11/2026)

---

## 1. Tổng quan API

### 1.1. Lấy danh sách món “Gợi ý cho bạn”
- **Endpoint:** `GET /api/ai/recommendations`
- **Authentication:** Optional Bearer JWT
  - **Không truyền token (khách chưa đăng nhập):** Backend tự động trả về danh sách món bán chạy (`strategy = "POPULARITY"`), **không trả 401**.
  - **Có truyền JWT (`Authorization: Bearer <token>`):** Backend đọc `userId` từ JWT. Nếu user có lịch sử tương tác (`VIEW`, `ADD_TO_CART`, `ORDER`, `FAVORITE`), trả về gợi ý cá nhân hóa (`strategy = "PERSONALIZED"`); nếu người dùng mới chưa có lịch sử, tự động fallback về `POPULARITY`.
- **Query Parameters:**
  - `limit` (optional, default `8`, range `1..24`): Số lượng món trả về tối đa.
  - `restaurantId` (optional): Lọc món gợi ý trong phạm vi 1 quán cụ thể (dùng khi đang xem chi tiết quán).

**Ví dụ Response (`200 OK`):**
```json
{
  "strategy": "PERSONALIZED",
  "fallbackUsed": false,
  "message": "Gợi ý cá nhân hóa dựa trên sở thích của bạn",
  "items": [
    {
      "id": 96,
      "name": "Quẩy",
      "description": "Quẩy giòn ăn kèm phở (2 cái)",
      "price": 10000,
      "imageUrl": "https://placehold.co/400x300/png?text=Quay",
      "isAvailable": true,
      "restaurantId": 1,
      "restaurantName": "Phở 24 Nguyễn Trãi",
      "restaurantAddress": "5 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM",
      "restaurantOpenStatus": "OPEN",
      "restaurantAvgRating": 4.5,
      "score": 0.9125,
      "reason": "Thường được đặt cùng với Phở bò tái"
    }
  ]
}
```

### 1.2. Cách phân biệt `PERSONALIZED` và `POPULARITY` trên UI
- `strategy == "PERSONALIZED"`: Hiển thị badge **"✨ Cá nhân hóa cho bạn"**.
- `strategy == "POPULARITY"`: Hiển thị badge **"🔥 Món bán chạy"**.
- `fallbackUsed == true`: AI Service (FastAPI) tạm thời không phản hồi, Spring Boot đã dùng danh sách món bán chạy trực tiếp từ MySQL — UI hiển thị bình thường không báo lỗi.

### 1.3. Ghi nhận hành vi người dùng (khi đã có JWT)
- **Endpoint:** `POST /api/interactions`
- **Authentication:** Bắt buộc JWT (`Authorization: Bearer <token>`). `userId` luôn lấy từ JWT.
- **Request Body:**
```json
{
  "menuItemId": 96,
  "type": "VIEW",
  "quantity": 1,
  "eventId": "app-view-96-1732530000"
}
```
  - `type`: `"VIEW"` (xem chi tiết món), `"ADD_TO_CART"` (thêm vào giỏ), `"FAVORITE"` (yêu thích), `"ORDER"` (đặt hàng thành công — Backend `OrderInteractionListener` đã tự động ghi từ `OrderPlacedEvent` nên App không bắt buộc gọi tay cho `ORDER`).
  - `eventId` (optional): Chuỗi duy nhất (tối đa 64 ký tự) giúp chống ghi nhận trùng khi mạng retry.

---

## 2. Mẫu tích hợp Kotlin / Retrofit / Jetpack Compose (`android/customer-app`)

### 2.1. Vị trí file cần tích hợp trong `android/customer-app`
1. Tạo `restaurants/data/api/AiRecommendationApiService.kt` (cùng pattern `OrderApiService.kt` / `RestaurantApiService.kt`).
2. Cập nhật `restaurants/ui/list/RestaurantListViewModel.kt` và `RestaurantListScreen.kt`:
   - Gọi `getRecommendations(authorization = tokenOrNull, limit = 8)` song song khi khởi tạo màn hình.
   - Thêm một `LazyRow` **"Gợi ý cho bạn"** phía trên danh sách quán (hoặc ngay dưới thanh chip loại món).
   - Khi người dùng bấm vào thẻ món gợi ý -> gọi `onRestaurantClick(item.restaurantId)` và gửi `trackInteraction` (`VIEW`) nếu đã có JWT.
3. Cập nhật `restaurants/ui/detail/RestaurantDetailScreen.kt`:
   - Khi hoàn thiện nút `+ Thêm` (thay thế `TODO: add to cart`), gọi thêm `trackInteraction(..., type = "ADD_TO_CART")` ở background coroutine (`runCatching { ... }`, bỏ qua lỗi để không chậm trải nghiệm đặt hàng).

### 2.2. Code mẫu Kotlin DTO & Retrofit Interface
```kotlin
package vn.hutech.foodexpress.customer.restaurants.data.api

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Singleton

data class RecommendedItemDto(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val price: Long = 0,
    val imageUrl: String? = null,
    val isAvailable: Boolean = true,
    val restaurantId: Long = 0,
    val restaurantName: String = "",
    val restaurantAddress: String = "",
    val restaurantOpenStatus: String = "OPEN",
    val restaurantAvgRating: Double = 0.0,
    val score: Double = 0.0,
    val reason: String = "",
)

data class RecommendationResponseDto(
    val strategy: String = "POPULARITY",
    val fallbackUsed: Boolean = false,
    val message: String? = null,
    val items: List<RecommendedItemDto> = emptyList(),
)

data class RecordInteractionRequest(
    val menuItemId: Long,
    val type: String, // "VIEW" | "ADD_TO_CART" | "FAVORITE"
    val quantity: Int = 1,
    val eventId: String? = null,
)

interface AiRecommendationApiService {
    @GET("ai/recommendations")
    suspend fun getRecommendations(
        @Header("Authorization") authorization: String? = null,
        @Query("limit") limit: Int = 8,
        @Query("restaurantId") restaurantId: Long? = null,
    ): RecommendationResponseDto

    @POST("interactions")
    suspend fun recordInteraction(
        @Header("Authorization") authorization: String,
        @Body body: RecordInteractionRequest,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideAiApiService(retrofit: Retrofit): AiRecommendationApiService =
        retrofit.create(AiRecommendationApiService::class.java)
}
```

### 2.3. Lưu ý phối hợp hiện trạng App Android
- Hiện tại trong `MyOrdersViewModel.kt`, Customer App chưa có luồng đăng nhập/lưu JWT (`private val token: String = ""`).
- Nhờ `GET /api/ai/recommendations` đã mở công khai (`permitAll` trong `SecurityConfig.java`), App Khách vẫn có thể gọi ngay API này khi chưa đăng nhập để hiển thị danh sách món bán chạy (`POPULARITY`), và sẽ tự động chuyển sang `PERSONALIZED` ngay khi gắn `Bearer <token>` từ luồng auth.

