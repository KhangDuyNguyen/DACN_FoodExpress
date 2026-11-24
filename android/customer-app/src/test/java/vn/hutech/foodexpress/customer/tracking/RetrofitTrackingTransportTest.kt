package vn.hutech.foodexpress.customer.tracking

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitTrackingTransportTest {
    @Test
    fun readsDeliveryPointFromRestEvenWhenDriverGpsIsNull() = runBlocking {
        val snapshot = snapshot(deliveryPoint = """{"lat":10.76,"lng":106.66}""", latestLocation = "null")

        assertEquals(17L, snapshot.orderId)
        assertEquals(TrackingMapPoint(10.76, 106.66), snapshot.deliveryPoint)
        assertNull(snapshot.latestLocation)
    }

    @Test
    fun absentOrInvalidDeliveryPointIsNeverFilledWithDriverGps() = runBlocking {
        val gps = """{"lat":10.78,"lng":106.70,"recordedAt":"2026-11-19T10:30:00"}"""
        for (point in listOf("null", """{"lat":10.76}""", """{"lat":10.76,"lng":181}""")) {
            val snapshot = snapshot(deliveryPoint = point, latestLocation = gps)
            assertNull(snapshot.deliveryPoint)
            assertEquals(10.78, requireNotNull(snapshot.latestLocation).lat, 0.0)
        }
    }

    private suspend fun snapshot(deliveryPoint: String, latestLocation: String): TrackingSnapshot {
        val json = """
            {
              "orderId": 17,
              "orderCode": "ORDER-17",
              "status": "DELIVERING",
              "latestLocation": $latestLocation,
              "deliveryPoint": $deliveryPoint
            }
        """.trimIndent()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/api/orders/17/tracking", request.url.encodedPath)
            assertEquals("Bearer test-token", request.header("Authorization"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.test/api/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return RetrofitTrackingTransport(retrofit).snapshot(17L, "test-token")
    }
}
