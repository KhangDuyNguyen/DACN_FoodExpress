package vn.hutech.foodexpress.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

class DeviceBindingSync(
    private val register: suspend (String, String) -> Unit,
    private val remove: suspend (String, String) -> Unit,
) {
    suspend fun collect(access: StateFlow<String?>, devices: StateFlow<String?>, getDevice: suspend () -> String?) {
        var confirmed: Pair<String, String>? = null
        var attempted: Pair<String, String>? = null
        combine(access, devices, ::Pair).collectLatest { (token, cached) ->
            val device = if (token.isNullOrBlank()) null else cached ?: getDevice()
            currentCoroutineContext().ensureActive()
            if (access.value != token) return@collectLatest
            val next = if (token != null && !device.isNullOrBlank()) token to device else null
            val previous = listOfNotNull(attempted, confirmed).distinct()
            for (binding in previous) {
                if (binding != next && tryCall { remove(binding.first, binding.second) }) {
                    if (confirmed == binding) confirmed = null
                    if (attempted == binding) attempted = null
                }
            }
            if (next == null || next == confirmed || access.value != token) return@collectLatest
            attempted = next
            if (tryCall { register(next.first, next.second) }) {
                currentCoroutineContext().ensureActive()
                if (access.value == token) confirmed = next
            }
        }
    }

    private suspend fun tryCall(operation: suspend () -> Unit): Boolean = try {
        operation()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}

object InboxSystemNotifications {
    const val EXTRA_NOTIFICATION_ID = "notification_inbox_id"
    private const val PREFIX = "notification_inbox_"

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun show(context: Context, activity: Class<*>, item: InboxItem, channel: String, label: String, offer: Boolean,
        expiresAtElapsedMillis: Long? = null,
        onlyWhileCurrent: () -> Boolean) {
        if (!canPost(context) || !onlyWhileCurrent()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, label,
            if (offer) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(offer)
        })
        val intent = Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NOTIFICATION_ID, item.id)
        val id = item.id.hashCode()
        val click = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title).setContentText(item.body)
            .setStyle(Notification.BigTextStyle().bigText(item.body))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(click)
        if (expiresAtElapsedMillis != null) {
            val remaining = expiresAtElapsedMillis - SystemClock.elapsedRealtime()
            if (remaining <= 0) return
            builder.setTimeoutAfter(remaining)
        }
        if (onlyWhileCurrent()) manager.notify(PREFIX + (if (offer) item.data.offerId ?: item.id else item.id), id,
            builder.build())
    }

    fun clear(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
