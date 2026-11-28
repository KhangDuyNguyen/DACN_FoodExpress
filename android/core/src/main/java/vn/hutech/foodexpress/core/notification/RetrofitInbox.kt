package vn.hutech.foodexpress.core.notification

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private data class WireItem(
    val id: Long?, val type: String?, val eventType: String?, val title: String?, val body: String?,
    val isRead: Boolean?, val createdAt: String?, val data: InboxData?,
) {
    fun valid(): InboxItem? {
        val validId = id?.takeIf { it > 0 } ?: return null
        val time = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return InboxItem(validId, type ?: return null, eventType, title ?: return null, body ?: return null,
            isRead ?: return null, time, data ?: InboxData())
    }
}
private data class WirePage(val items: List<WireItem?>?, val nextCursor: String?, val unreadCount: Int?)
private data class DeviceToken(val token: String)

private interface NotificationApi {
    @GET("notifications")
    suspend fun page(
        @Header("Authorization") auth: String, @Query("unreadOnly") unread: Boolean,
        @Query("cursor") cursor: String?, @Query("limit") limit: Int,
    ): Response<WirePage>
    @PATCH("notifications/{id}/read")
    suspend fun markRead(@Header("Authorization") auth: String, @Path("id") id: Long): Response<Void>
    @POST("devices/token")
    suspend fun register(@Header("Authorization") auth: String, @Body token: DeviceToken): Response<Void>
    @HTTP(method = "DELETE", path = "devices/token", hasBody = true)
    suspend fun remove(@Header("Authorization") auth: String, @Body token: DeviceToken): Response<Void>
}

@Singleton
class RetrofitInbox @Inject constructor(retrofit: Retrofit) : InboxTransport {
    private val api = retrofit.newBuilder().client(OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build())
        .build().create(NotificationApi::class.java)

    override suspend fun page(token: String, unread: Boolean, cursor: String?, limit: Int): InboxPage {
        val response = api.page("Bearer $token", unread, cursor, limit)
        response.requireStatus(200)
        val wire = response.body() ?: throw InboxHttpError(502)
        val count = wire.unreadCount?.takeIf { it >= 0 } ?: throw InboxHttpError(502)
        val items = wire.items ?: throw InboxHttpError(502)
        return InboxPage(items.mapNotNull { it?.valid() }, wire.nextCursor?.takeIf { it.isNotBlank() }, count)
    }

    override suspend fun markRead(token: String, id: Long) = api.markRead("Bearer $token", id).requireStatus(204)
    suspend fun registerDevice(token: String, device: String) = api.register("Bearer $token", DeviceToken(device)).requireStatus(204)
    suspend fun removeDevice(token: String, device: String) = api.remove("Bearer $token", DeviceToken(device)).requireStatus(204)

    private fun Response<*>.requireStatus(status: Int) {
        errorBody()?.close()
        if (code() != status) throw InboxHttpError(code())
    }
}
