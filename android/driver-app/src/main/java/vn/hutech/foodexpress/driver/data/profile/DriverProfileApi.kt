package vn.hutech.foodexpress.driver.data.profile

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT

data class DriverProfile(
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val vehicleType: String? = null,
    val licensePlate: String? = null,
    val status: String? = null,
    val isOnline: Boolean? = null,
)

data class DriverProfileUpdate(val vehicleType: String, val licensePlate: String?)

interface DriverProfileApi {
    @GET("driver/profile")
    suspend fun getProfile(@Header("Authorization") authorization: String): Response<DriverProfile>

    @PUT("driver/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body request: DriverProfileUpdate,
    ): Response<DriverProfile>
}

@Module
@InstallIn(SingletonComponent::class)
object DriverProfileModule {
    @Provides
    @Singleton
    fun provideDriverProfileApi(retrofit: Retrofit): DriverProfileApi = retrofit.create(DriverProfileApi::class.java)
}
