package vn.hutech.foodexpress.customer.tracking

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The Android emulator reaches the dev host's loopback at 10.0.2.2. */
internal object DebugTrackingRouteClient {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(route: DebugTrackingRoutePlanner.Request): List<TrackingMapPoint>? {
        val url = HttpUrl.Builder().scheme("http").host("10.0.2.2").port(18764)
            .addPathSegment("route")
            .addQueryParameter("from", "${route.from.lat},${route.from.lng}")
            .addQueryParameter("to", "${route.to.lat},${route.to.lng}")
        if (route.simulateFailure) url.addQueryParameter("simulate_failure", "1")

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url.build()).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val points = response.use {
                        if (!continuation.isActive || !it.isSuccessful) null else readLineString(it)
                    }
                    if (continuation.isActive) continuation.resume(points)
                }
            })
        }
    }

    private fun readLineString(response: Response): List<TrackingMapPoint>? = try {
        val geometry = JSONObject(response.body?.string().orEmpty())
        if (geometry.getString("type") != "LineString") {
            null
        } else {
            val coordinates = geometry.getJSONArray("coordinates")
            List(coordinates.length()) { index ->
                val pair = coordinates.getJSONArray(index)
                TrackingMapPoint(lat = pair.getDouble(1), lng = pair.getDouble(0))
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: JSONException) {
        null
    }
}
