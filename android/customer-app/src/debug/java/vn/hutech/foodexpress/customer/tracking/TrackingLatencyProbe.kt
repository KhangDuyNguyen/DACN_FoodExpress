package vn.hutech.foodexpress.customer.tracking

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

internal class TrackingLatencyFailure(message: String) : IllegalStateException(message)

internal class TrackingLatencyProbe(
    private val window: Window,
    private val evidence: File,
    baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val orderId: Long,
    private val driverToken: String,
    private val trackingReads: AtomicInteger,
    private val trackingState: () -> OrderTrackingState?,
) {
    private val endpoint = requireNotNull(baseUrl.resolve("driver/location"))
    private val marker = MutableStateFlow<PlacedPin?>(null)
    private val handler = Handler(Looper.getMainLooper())

    fun onDriverMarkerPosition(point: TrackingMapPoint, bounds: Rect) {
        marker.value = PlacedPin(point, bounds)
    }

    suspend fun run() {
        val initialReads = trackingReads.get()
        val initialState = connectedOrder(initialReads)
        var previousBounds = marker.value?.bounds ?: throw TrackingLatencyFailure("Missing initial driver marker")
        val samples = JSONArray()
        withContext(Dispatchers.IO) { evidence.mkdirs() }
        Log.i(LogTag, "Started order=$orderId trackingReads=$initialReads")

        for ((index, point) in SamplePoints.withIndex()) {
            val (receipt, visible) = withTimeout(12_000) {
                coroutineScope {
                    val rendered = async { waitForRenderedPin(point, previousBounds) }
                    val response = sendLocation(point)
                    response to rendered.await()
                }
            }
            val state = connectedOrder(initialReads)
            val location = state.latestLocation
            if (location == null || location.lat != point.lat || location.lng != point.lng ||
                location.recordedAt != receipt.recordedAt || visible.observedNs < receipt.startedNs
            ) throw TrackingLatencyFailure("Rendered marker did not match the acknowledged fix")

            val name = "fix-${index + 1}.png"
            saveWindow(name)
            previousBounds = visible.bounds
            val latencyMs = (visible.observedNs - receipt.startedNs) / 1_000_000.0
            samples.put(JSONObject()
                .put("sequence", index + 1).put("lat", point.lat).put("lng", point.lng)
                .put("status", receipt.status).put("recordedAt", receipt.recordedAt.toString())
                .put("requestStartedNs", receipt.startedNs).put("httpCompletedNs", receipt.completedNs)
                .put("pinObservedNs", visible.observedNs).put("markerBounds", visible.bounds.flattenToString())
                .put("latencyMs", latencyMs).put("screenshot", name))
            val report = JSONObject().put("orderId", orderId).put("orderCode", initialState.orderCode)
                .put("clock", "Android SystemClock.elapsedRealtimeNanos")
                .put("endpoint", "POST /api/driver/location")
                .put("pinDetection", "Window PixelCopy with confirmed driver marker colors at the new coordinates")
                .put("trackingGetsBefore", initialReads).put("trackingGetsAfter", trackingReads.get())
                .put("samples", samples)
            withContext(Dispatchers.IO) { File(evidence, "latency-results.json").writeText(report.toString(2)) }
            Log.i(LogTag, "Fix ${index + 1} HTTP ${receipt.status}, marker visible after $latencyMs ms")
        }
        connectedOrder(initialReads)
        Log.i(LogTag, "Completed ${samples.length()} fixes for order=$orderId")
    }

    private fun connectedOrder(initialReads: Int): OrderTrackingState {
        val state = trackingState()
        if (state?.orderId != orderId || state.connection != TrackingConnection.Connected ||
            state.status != "DELIVERING" || trackingReads.get() != initialReads
        ) throw TrackingLatencyFailure("Order disconnected or a tracking REST refresh occurred during measurement")
        return state
    }

    private suspend fun sendLocation(point: TrackingMapPoint): HttpFix = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("lat", point.lat).put("lng", point.lng)
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $driverToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val call = client.newCall(request)
        call.timeout().timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val sent = SystemClock.elapsedRealtimeNanos()
        call.execute().use { response ->
            if (response.code != 201) throw TrackingLatencyFailure("GPS request returned HTTP ${response.code}")
            val body = JSONObject(requireNotNull(response.body).string())
            if (body.getDouble("lat") != point.lat || body.getDouble("lng") != point.lng) {
                throw TrackingLatencyFailure("GPS response coordinates do not match the request")
            }
            HttpFix(sent, SystemClock.elapsedRealtimeNanos(), response.code, LocalDateTime.parse(body.getString("recordedAt")))
        }
    }

    private suspend fun waitForRenderedPin(point: TrackingMapPoint, previous: Rect): RenderedPin {
        val placed = marker.filterNotNull().first { it.point == point }
        if (Rect.intersects(previous, placed.bounds)) {
            throw TrackingLatencyFailure("New marker overlaps the previous sample area")
        }
        while (true) {
            val frame = copyWindow(placed.bounds)
            if (frame != null) {
                try {
                    if (hasDriverColors(frame.bitmap)) return RenderedPin(placed.bounds, frame.observedNs)
                } finally { frame.bitmap.recycle() }
            }
            delay(16)
        }
    }

    private fun hasDriverColors(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var outline = 0
        var icon = 0
        for (pixel in pixels) {
            when (pixel and 0xFFFFFF) {
                0xFF6B35 -> outline++
                0xA73510 -> icon++
            }
        }
        return outline >= 20 && icon >= 20
    }

    private suspend fun copyWindow(bounds: Rect?): CopiedFrame? = suspendCancellableCoroutine { pending ->
        val bitmap = Bitmap.createBitmap(
            bounds?.width() ?: window.decorView.width,
            bounds?.height() ?: window.decorView.height,
            Bitmap.Config.ARGB_8888,
        )
        PixelCopy.request(window, bounds, bitmap, { status ->
            val observed = SystemClock.elapsedRealtimeNanos()
            if (status == PixelCopy.SUCCESS && pending.isActive) {
                pending.resume(CopiedFrame(bitmap, observed))
            } else {
                bitmap.recycle()
                if (pending.isActive) pending.resume(null)
            }
        }, handler)
    }

    private suspend fun saveWindow(name: String) {
        val frame = copyWindow(null) ?: throw TrackingLatencyFailure("Could not save the verification frame")
        try {
            withContext(Dispatchers.IO) {
                File(evidence, name).outputStream().use { frame.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally { frame.bitmap.recycle() }
    }

    private data class PlacedPin(val point: TrackingMapPoint, val bounds: Rect)
    private data class RenderedPin(val bounds: Rect, val observedNs: Long)
    private data class CopiedFrame(val bitmap: Bitmap, val observedNs: Long)
    private data class HttpFix(val startedNs: Long, val completedNs: Long, val status: Int, val recordedAt: LocalDateTime)

    companion object {
        const val LogTag = "TrackingLatency"
        private val SamplePoints = listOf(
            TrackingMapPoint(10.77525, 106.70215),
            TrackingMapPoint(10.77615, 106.70293),
            TrackingMapPoint(10.77542, 106.70395),
        )
    }
}
