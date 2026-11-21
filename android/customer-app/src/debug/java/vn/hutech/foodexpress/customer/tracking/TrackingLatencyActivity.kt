package vn.hutech.foodexpress.customer.tracking

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import vn.hutech.foodexpress.customer.notification.CustomerDeviceRegistration
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
internal class TrackingLatencyActivity : ComponentActivity() {
    @Inject lateinit var retrofit: Retrofit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val fixture = readFixture()
        if (fixture == null) {
            setContent { Text("Cần fixture local với đơn và hai phiên test.") }
            return
        }
        val reads = AtomicInteger()
        val trackingPath = "/api/orders/${fixture.orderId}/tracking"
        val client = (retrofit.callFactory() as OkHttpClient).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "GET" && request.url.encodedPath == trackingPath) reads.incrementAndGet()
            chain.proceed(request).also { response ->
                if (fixture.registerDevice && request.method == "POST" &&
                    request.url.encodedPath == "/api/devices/token"
                ) Log.i("CustomerFcmProbe", "POST /api/devices/token HTTP ${response.code}")
            }
        }.build()
        val baseUrl = HttpUrl.Builder().scheme("http").host("10.0.2.2").port(fixture.port)
            .addPathSegment("api").addPathSegment("").build()
        val localRetrofit = retrofit.newBuilder().baseUrl(baseUrl).client(client).build()
        val tracking = ViewModelProvider(this, viewModelFactory {
            initializer { OrderTrackingViewModel(RetrofitTrackingTransport(localRetrofit)) }
        })[OrderTrackingViewModel::class.java]
        val tokens = MutableStateFlow<String?>(fixture.customerToken)
        val registration = if (fixture.registerDevice) CustomerDeviceRegistration(applicationContext, localRetrofit) else null
        val probe = TrackingLatencyProbe(
            window, File(filesDir, if (fixture.observeOnly) "background-gps-evidence" else "tracking-latency-evidence"), baseUrl, client,
            fixture.orderId, fixture.driverToken, reads,
        ) { tracking.state.value?.forSelection(fixture.orderId, tokens.value) }
        setContent {
            FoodExpressTheme {
                if (registration != null) {
                    LaunchedEffect(registration) { registration.syncWhileAuthenticated(tokens) }
                }
                if (fixture.observeOnly) {
                    LaunchedEffect(probe) { probe.observeBackgroundGps() }
                    CustomerOrderTracking(
                        orderId = fixture.orderId,
                        accessTokens = tokens,
                        onBack = ::finish,
                        onSignIn = ::finish,
                        onDriverMarkerPosition = probe::onDriverMarkerPosition,
                        viewModel = tracking,
                    )
                } else {
                    TrackingTestScreen(fixture, tracking, tokens, probe)
                }
            }
        }
    }

    @Composable
    private fun TrackingTestScreen(
        fixture: Fixture,
        tracking: OrderTrackingViewModel,
        tokens: MutableStateFlow<String?>,
        probe: TrackingLatencyProbe,
    ) {
        val update by tracking.state.collectAsStateWithLifecycle()
        val state = update?.forSelection(fixture.orderId, fixture.customerToken)
        var mapStatus by remember { mutableStateOf(TrackingMapStatus.Loading) }
        var result by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        OrderTrackingRoute(
            orderId = fixture.orderId,
            accessTokens = tokens,
            viewModel = tracking,
            onBack = ::finish,
            onSignIn = ::finish,
            mapStatus = mapStatus,
            onRetryMap = { mapStatus = TrackingMapStatus.Loading },
            mapContent = { layers ->
                VietmapTrackingMap(
                    layers = layers,
                    onStatus = { mapStatus = it },
                    onDriverMarkerPosition = probe::onDriverMarkerPosition,
                    hideLogo = true,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Button(
                            modifier = Modifier.padding(end = 12.dp, bottom = 24.dp),
                            enabled = result == null && state?.connection == TrackingConnection.Connected &&
                                state.status == "DELIVERING" && state.latestLocation != null,
                            onClick = {
                                result = "Đang đo…"
                                scope.launch {
                                    try {
                                        probe.run()
                                        result = "Đã đo 3 lần"
                                    } catch (timeout: TimeoutCancellationException) {
                                        Log.w(TrackingLatencyProbe.LogTag, "Timed out waiting for a visible pin")
                                        result = "Hết thời gian chờ ghim"
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        val reason = if (failure is TrackingLatencyFailure) failure.message
                                            else failure.javaClass.simpleName
                                        Log.w(TrackingLatencyProbe.LogTag, "Measurement stopped: $reason")
                                        result = "Dừng đo"
                                    }
                                }
                            },
                        ) { Text(result ?: "Bắt đầu đo GPS") }
                    }
                }
            },
        )
    }

    private fun readFixture(): Fixture? {
        val file = File(filesDir, "tracking-latency-input.json")
        return try {
            val json = JSONObject(file.readText())
            val observeOnly = json.optBoolean("observeOnly")
            Fixture(
                json.getLong("orderId").also { require(it > 0) },
                json.getInt("port").also { require(it in 1024..65535) },
                json.getString("customerToken").also { require(it.isNotBlank()) },
                if (observeOnly) "" else json.getString("driverToken").also { require(it.isNotBlank()) },
                observeOnly,
                json.optBoolean("registerDevice"),
            )
        } catch (_: Exception) {
            null
        } finally {
            file.delete()
        }
    }

    private class Fixture(
        val orderId: Long,
        val port: Int,
        val customerToken: String,
        val driverToken: String,
        val observeOnly: Boolean,
        val registerDevice: Boolean,
    )
}
