package vn.hutech.foodexpress.driver.data.status

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PATCH

data class DriverStatusRequest(val isOnline: Boolean)
// Preserve the wire type: Gson must not coerce a malformed string/number into an Offline acknowledgement.
data class DriverStatusResponse(val isOnline: Any? = null)

interface DriverStatusApi {
    @PATCH("driver/status")
    suspend fun updateStatus(
        @Header("Authorization") authorization: String,
        @Body request: DriverStatusRequest,
    ): Response<DriverStatusResponse>
}

@Module
@InstallIn(SingletonComponent::class)
object DriverStatusModule {
    @Provides
    @Singleton
    fun provideDriverStatusApi(retrofit: Retrofit): DriverStatusApi = retrofit.create(DriverStatusApi::class.java)
}
