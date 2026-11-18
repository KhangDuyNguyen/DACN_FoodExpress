package vn.hutech.foodexpress.customer.restaurants.data.model

import com.google.gson.annotations.SerializedName

// ── Cuisine ───────────────────────────────────────────────────────────────────

data class CuisineDto(
    val id: Int = 0,
    val name: String = "",
    @field:SerializedName("iconUrl") val iconUrl: String? = null,
)

// ── Restaurant List ───────────────────────────────────────────────────────────

data class RestaurantSummaryDto(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    @field:SerializedName("logoUrl")         val logoUrl: String? = null,
    @field:SerializedName("avgRating")       val avgRating: Double = 0.0,
    @field:SerializedName("ratingCount")     val ratingCount: Int = 0,
    @field:SerializedName("minOrderAmount")  val minOrderAmount: Long = 0,
    @field:SerializedName("prepTimeMinutes") val prepTimeMinutes: Int = 0,
    @field:SerializedName("openStatus")      val openStatus: String = "CLOSED",
    @field:SerializedName("cuisineName")     val cuisineName: String? = null,
)

data class PageResponse<T>(
    val data: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val total: Long = 0,
    @field:SerializedName("totalPages") val totalPages: Int = 0,
)


// Concrete pagination response cho danh sách quán (tránh KAPT issue với generics)
data class RestaurantPageResponse(
    val data: List<RestaurantSummaryDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val total: Long = 0,
    @field:SerializedName("totalPages") val totalPages: Int = 0,
)

// ── Restaurant Detail ─────────────────────────────────────────────────────────

data class RestaurantDetailDto(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val phone: String? = null,
    val address: String = "",
    @field:SerializedName("logoUrl")         val logoUrl: String? = null,
    @field:SerializedName("coverUrl")        val coverUrl: String? = null,
    @field:SerializedName("avgRating")       val avgRating: Double = 0.0,
    @field:SerializedName("ratingCount")     val ratingCount: Int = 0,
    @field:SerializedName("minOrderAmount")  val minOrderAmount: Long = 0,
    @field:SerializedName("prepTimeMinutes") val prepTimeMinutes: Int = 0,
    @field:SerializedName("openStatus")      val openStatus: String = "CLOSED",
    val cuisines: List<CuisineDto> = emptyList(),
    val hours: List<RestaurantHoursDto> = emptyList(),
)

data class RestaurantHoursDto(
    @field:SerializedName("dayOfWeek") val dayOfWeek: Int = 1,
    @field:SerializedName("openTime")  val openTime: String = "",
    @field:SerializedName("closeTime") val closeTime: String = "",
)

// ── Menu ─────────────────────────────────────────────────────────────────────

data class MenuDto(
    @field:SerializedName("restaurantId") val restaurantId: Long = 0,
    val categories: List<MenuCategoryDto> = emptyList(),
)

data class MenuCategoryDto(
    val id: Long = 0,
    val name: String = "",
    @field:SerializedName("sortOrder") val sortOrder: Int = 0,
    val items: List<MenuItemDto> = emptyList(),
)

data class MenuItemDto(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val price: Long = 0,
    @field:SerializedName("imageUrl")     val imageUrl: String? = null,
    @field:SerializedName("isAvailable")  val isAvailable: Boolean = true,
    @field:SerializedName("sortOrder")    val sortOrder: Int = 0,
    @field:SerializedName("optionGroups") val optionGroups: List<OptionGroupDto> = emptyList(),
)

data class OptionGroupDto(
    val id: Long = 0,
    val name: String = "",
    @field:SerializedName("isRequired") val isRequired: Boolean = false,
    @field:SerializedName("minSelect")  val minSelect: Int = 0,
    @field:SerializedName("maxSelect")  val maxSelect: Int = 1,
    val options: List<OptionDto> = emptyList(),
)

data class OptionDto(
    val id: Long = 0,
    val name: String = "",
    @field:SerializedName("extraPrice")  val extraPrice: Long = 0,
    @field:SerializedName("isAvailable") val isAvailable: Boolean = true,
)
