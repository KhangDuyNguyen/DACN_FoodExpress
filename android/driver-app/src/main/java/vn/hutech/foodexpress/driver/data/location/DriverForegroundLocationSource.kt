package vn.hutech.foodexpress.driver.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

object DriverLocationPolicy {
    const val MAX_FIX_AGE_MS = 15_000L
    const val MAX_ACCURACY_METERS = 50f
    const val ACQUIRE_TIMEOUT_MS = 20_000L
    const val FIX_INTERVAL_MS = 5_000L
    const val UPLOAD_INTERVAL_MS = 20_000L
    const val LOST_FIX_MS = 45_000L
    const val HTTP_TIMEOUT_MS = 12_000L
    const val OFF_RETRY_MS = 20_000L

    fun problem(fix: DriverLocationFix, nowNanos: Long): DriverGpsStatus? = when {
        !fix.lat.isFinite() || !fix.lng.isFinite() || fix.lat !in -90.0..90.0 || fix.lng !in -180.0..180.0 -> DriverGpsStatus.ERROR
        fix.timeMillis <= 0 || fix.elapsedNanos <= 0 || fix.elapsedNanos > nowNanos -> DriverGpsStatus.STALE
        nowNanos - fix.elapsedNanos > MAX_FIX_AGE_MS * 1_000_000 -> DriverGpsStatus.STALE
        fix.accuracyMeters == null || !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0 || fix.accuracyMeters > MAX_ACCURACY_METERS -> DriverGpsStatus.INACCURATE
        else -> null
    }
}

data class DriverLocationFix(
    val lat: Double, val lng: Double, val elapsedNanos: Long,
    val timeMillis: Long, val accuracyMeters: Float?,
)

enum class DriverGpsStatus { STOPPED, NO_PERMISSION, APPROXIMATE, SERVICES_OFF, WAITING, READY, STALE, INACCURATE, ERROR }

data class DriverGpsSnapshot(val status: DriverGpsStatus = DriverGpsStatus.STOPPED, val fix: DriverLocationFix? = null) {
    val message: String get() = when (status) {
        DriverGpsStatus.STOPPED -> "GPS chỉ hoạt động khi ứng dụng ở phía trước."
        DriverGpsStatus.NO_PERMISSION -> "Chưa cấp quyền vị trí. Bạn vẫn có thể xem hồ sơ."
        DriverGpsStatus.APPROXIMATE -> "Hiện chỉ có vị trí gần đúng. Cần cấp vị trí chính xác để nhận đơn."
        DriverGpsStatus.SERVICES_OFF -> "Dịch vụ vị trí đang tắt. Hãy bật vị trí trên thiết bị."
        DriverGpsStatus.WAITING -> "Đang chờ vị trí mới từ thiết bị…"
        DriverGpsStatus.READY -> "Vị trí hiện tại đủ mới và chính xác."
        DriverGpsStatus.STALE -> "Vị trí đã cũ. Đang chờ fix mới; chưa thể xác nhận GPS sẵn sàng."
        DriverGpsStatus.INACCURATE -> "Vị trí chưa đạt độ chính xác ${DriverLocationPolicy.MAX_ACCURACY_METERS.toInt()} m. Hãy thử tại nơi thoáng hơn."
        DriverGpsStatus.ERROR -> "Chưa lấy được vị trí hợp lệ. Vui lòng thử lại GPS."
    }
}

// Owned by the Activity ViewModel, not an individual navigation destination.
class DriverForegroundLocationSource @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var requested = false
    private var listening = false
    private var registeredProviders = emptySet<String>()
    private var latest: DriverLocationFix? = null
    @Suppress("OVERRIDE_DEPRECATION")
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!requested) return
            latest = DriverLocationFix(location.latitude, location.longitude, location.elapsedRealtimeNanos,
                location.time, location.accuracy.takeIf { location.hasAccuracy() })
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Required on API 26")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()

    fun start(): DriverGpsSnapshot {
        requested = true
        return refresh()
    }

    fun stop() {
        requested = false
        removeListeners()
        latest = null
    }

    fun refresh(): DriverGpsSnapshot {
        if (!requested) return DriverGpsSnapshot()
        val precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!precise) {
            removeListeners(); latest = null
            return DriverGpsSnapshot(if (coarse) DriverGpsStatus.APPROXIMATE else DriverGpsStatus.NO_PERMISSION)
        }
        return try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
            val enabled = if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else providers.isNotEmpty()
            if (!enabled || providers.isEmpty()) {
                removeListeners(); latest = null
                DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)
            } else {
                if (!listening || providers.toSet() != registeredProviders) {
                    removeListeners()
                    providers.forEach { manager.requestLocationUpdates(it, DriverLocationPolicy.FIX_INTERVAL_MS, 0f, listener, Looper.getMainLooper()) }
                    listening = true
                    registeredProviders = providers.toSet()
                }
                val fix = latest
                if (fix == null) DriverGpsSnapshot(DriverGpsStatus.WAITING)
                else DriverGpsSnapshot(DriverLocationPolicy.problem(fix, nowNanos()) ?: DriverGpsStatus.READY, fix)
            }
        } catch (_: SecurityException) {
            removeListeners(); latest = null
            DriverGpsSnapshot(DriverGpsStatus.NO_PERMISSION)
        } catch (_: Exception) {
            removeListeners()
            DriverGpsSnapshot(DriverGpsStatus.ERROR)
        }
    }

    private fun removeListeners() {
        try { manager.removeUpdates(listener) } catch (_: Exception) { /* Permission may have been revoked. */ }
        listening = false
        registeredProviders = emptySet()
    }
}
