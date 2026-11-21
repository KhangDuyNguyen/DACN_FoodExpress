package vn.hutech.foodexpress.driver.data.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.order.CurrentOrderResult
import vn.hutech.foodexpress.driver.data.order.DriverCurrentOrderSource
import javax.inject.Inject

@AndroidEntryPoint
class DriverDeliveryLocationService : Service() {
    @Inject lateinit var auth: DriverAuthRepository
    @Inject lateinit var orders: DriverCurrentOrderSource
    @Inject lateinit var gps: DriverForegroundLocationSource
    @Inject lateinit var locations: DriverLocationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tracking: Job? = null
    private var target: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orderId = intent?.getLongExtra(OrderId, 0L) ?: 0L
        val session = auth.currentAccessToken()
        if (orderId <= 0 || session == null || !canReadGps(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (target == orderId && tracking?.isActive == true) return START_NOT_STICKY
        tracking?.cancel()
        target = orderId
        try {
            ServiceCompat.startForeground(this, NotificationId, trackingNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }
        isTracking = true
        gps.stop()
        val startedAt = gps.nowNanos()
        gps.start(DeliveryFixIntervalMillis)
        tracking = scope.launch {
            try {
                track(orderId, session, startedAt)
            } finally {
                // Another command can replace this job before its cancellation is processed.
                if (isActive) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun track(orderId: Long, session: String, startedAt: Long) {
        var lastFix = startedAt
        var nextCheck = 0L
        while (auth.currentAccessToken() == session && canReadGps(this)) {
            val snapshot = gps.refresh()
            if (snapshot.status == DriverGpsStatus.NO_PERMISSION || snapshot.status == DriverGpsStatus.APPROXIMATE ||
                snapshot.status == DriverGpsStatus.SERVICES_OFF
            ) return
            val fix = snapshot.fix?.takeIf { snapshot.status == DriverGpsStatus.READY && it.elapsedNanos > lastFix }
            val now = gps.nowNanos()
            if (now >= nextCheck || fix != null) {
                val current = orders.load()
                if (auth.currentAccessToken() != session || !canReadGps(this)) return
                nextCheck = gps.nowNanos() + OrderPollNanos
                when (current) {
                    CurrentOrderResult.SessionExpired -> return
                    is CurrentOrderResult.Loaded -> {
                        if (current.order?.orderId != orderId || current.order.status != "DELIVERING") return
                        if (fix != null) {
                            lastFix = fix.elapsedNanos
                            if (locations.record(fix, gps.nowNanos(), session) == DriverLocationResult.SessionExpired) return
                        }
                    }
                    is CurrentOrderResult.Failed -> {
                        // Skip unverified fixes. A newer fix or the next poll can retry verification.
                        if (fix != null) lastFix = fix.elapsedNanos
                    }
                }
            }
            delay(FixCheckMillis)
        }
    }

    override fun onDestroy() {
        isTracking = false
        scope.cancel()
        gps.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun trackingNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Channel, "Theo dõi giao hàng", NotificationManager.IMPORTANCE_LOW))
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, Channel)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Đang theo dõi giao hàng")
            .setContentText("Vị trí được cập nhật trong khi bạn đang giao đơn.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val Channel = "driver-delivery-location"
        private const val OrderId = "orderId"
        private const val NotificationId = 61
        private const val OrderPollNanos = 2_000_000_000L
        private const val DeliveryFixIntervalMillis = 2_000L
        private const val FixCheckMillis = 200L

        internal var isTracking = false
            private set

        internal fun canReadGps(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        internal fun start(context: Context, orderId: Long) {
            if (!canReadGps(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, DriverDeliveryLocationService::class.java)
                    .putExtra(OrderId, orderId))
            } catch (_: IllegalStateException) {
                Log.w("DriverDeliveryLocation", "Tracking can only start while the driver screen is visible")
            } catch (_: SecurityException) {
                Log.w("DriverDeliveryLocation", "Location permission was unavailable at service start")
            }
        }

        internal fun stop(context: Context) {
            context.stopService(Intent(context, DriverDeliveryLocationService::class.java))
        }
    }
}
