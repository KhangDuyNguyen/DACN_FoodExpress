package vn.hutech.foodexpress.driver.data.location

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class DriverLocationRequest(val lat: Double, val lng: Double)
// Preserve wire types: malformed strings must not become numeric acknowledgements.
data class DriverLocationResponse(val lat: Any?, val lng: Any?, val recordedAt: String?)

interface DriverLocationApi {
    @POST("driver/location")
    suspend fun recordLocation(
        @Header("Authorization") authorization: String,
        @Body request: DriverLocationRequest,
    ): Response<DriverLocationResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object DriverLocationModule {
    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): DriverLocationApi = retrofit.create(DriverLocationApi::class.java)
}
