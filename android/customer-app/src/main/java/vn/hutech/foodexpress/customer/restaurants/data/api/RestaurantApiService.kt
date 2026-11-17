package vn.hutech.foodexpress.customer.restaurants.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import vn.hutech.foodexpress.customer.restaurants.data.model.CuisineDto
import vn.hutech.foodexpress.customer.restaurants.data.model.MenuDto
import vn.hutech.foodexpress.customer.restaurants.data.model.RestaurantPageResponse
import vn.hutech.foodexpress.customer.restaurants.data.model.RestaurantDetailDto

interface RestaurantApiService {

    @GET("cuisines")
    suspend fun getCuisines(): List<CuisineDto>

    @GET("restaurants")
    suspend fun searchRestaurants(
        @Query("keyword")   keyword: String?,
        @Query("cuisineId") cuisineId: Int?,
        @Query("page")      page: Int,
        @Query("size")      size: Int,
    ): RestaurantPageResponse

    @GET("restaurants/{id}")
    suspend fun getRestaurantDetail(@Path("id") id: Long): RestaurantDetailDto

    @GET("restaurants/{id}/menu")
    suspend fun getRestaurantMenu(@Path("id") id: Long): MenuDto
}
