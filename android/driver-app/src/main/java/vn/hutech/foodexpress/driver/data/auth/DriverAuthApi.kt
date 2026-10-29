package vn.hutech.foodexpress.driver.data.auth

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
import retrofit2.http.POST

// Regular classes deliberately avoid generated toString() containing credentials/JWT.
class DriverLoginRequest(val identifier: String, val password: String)

class DriverLoginResponse(val token: String?, val user: DriverAuthUser?)

data class DriverAuthUser(
    val id: Long = 0,
    val fullName: String? = null,
    val role: String? = null,
    // users.status (ACTIVE/LOCKED/DELETED), never drivers.status (PENDING/APPROVED/...).
    val status: String? = null,
)

interface DriverAuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: DriverLoginRequest): Response<DriverLoginResponse>

    @GET("users/me")
    suspend fun currentUser(@Header("Authorization") authorization: String): Response<DriverAuthUser>
}

@Module
@InstallIn(SingletonComponent::class)
object DriverAuthModule {
    @Provides
    @Singleton
    fun provideDriverAuthApi(retrofit: Retrofit): DriverAuthApi =
        retrofit.create(DriverAuthApi::class.java)
}
