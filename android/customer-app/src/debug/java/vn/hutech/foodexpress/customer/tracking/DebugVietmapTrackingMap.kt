package vn.hutech.foodexpress.customer.tracking

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import vn.vietmap.vietmapsdk.camera.CameraUpdateFactory
import vn.vietmap.vietmapsdk.geometry.LatLng
import vn.vietmap.vietmapsdk.geometry.LatLngBounds
import vn.vietmap.vietmapsdk.maps.MapView
import vn.vietmap.vietmapsdk.maps.Style
import vn.vietmap.vietmapsdk.maps.VietMapGL
import vn.vietmap.vietmapsdk.maps.VietMapGLOptions
import kotlin.math.roundToInt

@Composable
internal fun DebugVietmapTrackingMap(
    layers: TrackingMapLayers,
    onStatus: (TrackingMapStatus) -> Unit,
    onDriverMarkerPosition: ((TrackingMapPoint, Rect) -> Unit)? = null,
    controls: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestOnStatus by rememberUpdatedState(onStatus)
    val mapView = remember(context) {
        DebugVietmapResources.initialize(context)
        MapView(context, VietMapGLOptions.createFromAttributes(context).textureMode(true))
    }

    var map by remember(mapView) { mutableStateOf<VietMapGL?>(null) }
    var styleLoaded by remember(mapView) { mutableStateOf(false) }
    var initialCameraSet by remember(mapView) { mutableStateOf(false) }
    var ready by remember(mapView) { mutableStateOf(false) }
    var size by remember(mapView) { mutableStateOf(IntSize.Zero) }
    var cameraUpdate by remember(mapView) { mutableIntStateOf(0) }

    DisposableEffect(mapView, lifecycle) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)

        val failure = MapView.OnDidFailLoadingMapListener {
            Log.w(DebugVietmapResources.LogTag, "VIETMAP style or tiles could not load")
            latestOnStatus(TrackingMapStatus.Unavailable)
        }
        val frame = MapView.OnDidFinishRenderingFrameListener { fullyRendered ->
            if (fullyRendered && styleLoaded && initialCameraSet && !ready) {
                // Require vector features before revealing the city map and its markers.
                val vietMap = map ?: return@OnDidFinishRenderingFrameListener
                val viewport = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
                if (vietMap.queryRenderedFeatures(viewport).isNotEmpty()) {
                    val settings = vietMap.uiSettings
                    val attributionEnabled = settings.isAttributionEnabled
                    runCatching { settings.setLogoEnabled(false) }
                        .onFailure { Log.w(DebugVietmapResources.LogTag, "VIETMAP logo toggle failed") }
                    Log.i(
                        DebugVietmapResources.LogTag,
                        "VIETMAP logo enabled=${settings.isLogoEnabled}, " +
                            "attribution enabled=${settings.isAttributionEnabled} (was $attributionEnabled)",
                    )
                    ready = true
                    latestOnStatus(TrackingMapStatus.Ready)
                }
            }
        }
        val cameraMove = VietMapGL.OnCameraMoveListener { cameraUpdate++ }
        mapView.addOnDidFailLoadingMapListener(failure)
        mapView.addOnDidFinishRenderingFrameListener(frame)
        mapView.getMapAsync { vietMap ->
            map = vietMap
            vietMap.addOnCameraMoveListener(cameraMove)
            vietMap.setStyle(Style.Builder().fromUri(DebugVietmapResources.streetStyleUri())) {
                styleLoaded = true
                Log.i(DebugVietmapResources.LogTag, "VIETMAP style loaded")
            }
        }

        onDispose {
            map?.removeOnCameraMoveListener(cameraMove)
            mapView.removeOnDidFailLoadingMapListener(failure)
            mapView.removeOnDidFinishRenderingFrameListener(frame)
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    val padding = with(LocalDensity.current) { 70.dp.roundToPx() }
    LaunchedEffect(map, size, layers.driverPoint, layers.deliveryPoint) {
        val vietMap = map ?: return@LaunchedEffect
        if (initialCameraSet || size.width == 0 || size.height == 0) return@LaunchedEffect
        val points = listOfNotNull(layers.driverPoint, layers.deliveryPoint).filter { it.isValid }.distinct()
        if (points.isEmpty()) return@LaunchedEffect
        val camera = if (points.size == 2) {
            val bounds = LatLngBounds.Builder().includes(points.map { it.toLatLng() }).build()
            CameraUpdateFactory.newLatLngBounds(bounds, padding.coerceAtMost(minOf(size.width, size.height) / 4))
        } else {
            CameraUpdateFactory.newLatLngZoom(points.single().toLatLng(), 16.0)
        }
        initialCameraSet = true
        vietMap.moveCamera(camera)
        Log.i(DebugVietmapResources.LogTag, "VIETMAP initial camera positioned")
        cameraUpdate++
    }
    LaunchedEffect(mapView, ready) {
        if (!ready) {
            delay(25_000)
            Log.w(DebugVietmapResources.LogTag, "VIETMAP map rendering timed out")
            latestOnStatus(TrackingMapStatus.Unavailable)
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
        )
        if (ready) {
            val projection = map?.projection
            val routePath = remember(projection, layers.routePoints, cameraUpdate, size) {
                if (projection == null || layers.routePoints.isEmpty()) {
                    null
                } else {
                    val points = layers.routePoints.map { projection.toScreenLocation(it.toLatLng()) }
                    Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                }
            }
            if (routePath != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPath(
                        routePath,
                        Color.White,
                        style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    drawPath(
                        routePath,
                        Color(0xFF7044CE),
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            val delivery = remember(projection, layers.deliveryPoint, cameraUpdate, size) {
                layers.deliveryPoint?.let { projection?.toScreenLocation(it.toLatLng()) }
            }
            val driver = remember(projection, layers.driverPoint, cameraUpdate, size) {
                layers.driverPoint?.let { projection?.toScreenLocation(it.toLatLng()) }
            }
            if (delivery != null) MapMarkerAt(delivery) { TrackingDeliveryMarker() }
            val driverPoint = layers.driverPoint
            if (driver != null && driverPoint != null) {
                val diameter = with(LocalDensity.current) { 44.dp.roundToPx() }
                val positionModifier = if (onDriverMarkerPosition != null && !layers.isDriverPointStale) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        val left = (bounds.center.x - diameter / 2f).roundToInt()
                        val top = bounds.top.roundToInt()
                        onDriverMarkerPosition(driverPoint, Rect(left, top, left + diameter, top + diameter))
                    }
                } else {
                    Modifier
                }
                MapMarkerAt(driver) { TrackingDriverMarker(layers.isDriverPointStale, positionModifier) }
            }
            controls()
        }
    }
}

private fun TrackingMapPoint.toLatLng() = LatLng(lat, lng)

@Composable
private fun MapMarkerAt(point: PointF, marker: @Composable () -> Unit) {
    Layout(content = marker, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val content = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val iconCenter = 22.dp.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            content.place(point.x.roundToInt() - content.width / 2, point.y.roundToInt() - iconCenter)
        }
    }
}
