package vn.hutech.foodexpress.driver.order

import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.time.LocalDateTime
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.order.DriverCurrentOrder
import vn.hutech.foodexpress.driver.ui.order.*

/** Fake account/API/order data only. Maps URL construction and Android Intent objects are real. */
class DriverOrderDetailChecks(private val instrumentation: Instrumentation) {
    private val context get() = instrumentation.targetContext
    private class RecordingContext(base: Context, private val fail: (Intent) -> Exception? = { null }) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            intents.add(Intent(intent))
            fail(intent)?.let { throw it }
        }
    }

    fun run(main: MainActivity, test: (String, () -> Unit) -> Unit) {
        val now = LocalDateTime.of(2026, 11, 7, 9, 0)
        val order = DriverCurrentOrder(99401, "FAKE-DETAIL-99401", "PLACED", now, now, 904,
            "FAKE Quán kiểm thử", "FAKE Địa chỉ lấy hàng", null, 10.125, 106.25,
            "FAKE Người nhận", "FAKE Điện thoại", "FAKE Địa chỉ giao hàng", 11.5, 107.75, "FAKE Ghi chú đơn")
        val loaded = DriverCurrentOrderUiState(order = order, verified = true, ownerId = 999004)
        test("DETAIL Maps URI has exact pickup/delivery destinations and no private fields") {
            val recorder = RecordingContext(context)
            for ((target, expected) in listOf(OrderDirectionsTarget.RESTAURANT to "10.125,106.25", OrderDirectionsTarget.CUSTOMER to "11.5,107.75")) {
                check(GoogleMapsDirections.openCurrentOrder(recorder, loaded, 999004, target) == MapsOpenResult.OPENED)
                val intent = recorder.intents.last(); val uri = requireNotNull(intent.data)
                check(intent.action == Intent.ACTION_VIEW && intent.`package` == "com.google.android.apps.maps")
                check(uri.scheme == "https" && uri.host == "www.google.com" && uri.path == "/maps/dir/")
                check(uri.getQueryParameter("destination") == expected && uri.getQueryParameter("api") == "1")
                check(uri.getQueryParameter("travelmode") == "driving" && uri.getQueryParameter("dir_action") == "navigate")
                check(uri.queryParameterNames == setOf("api", "destination", "travelmode", "dir_action"))
                check(!uri.toString().contains("FAKE") && intent.extras == null)
            }
        }
        test("DETAIL Maps rejects null nonfinite and out-of-range coordinates without Intent") {
            val recorder = RecordingContext(context)
            val invalid = listOf(null to 106.0, 10.0 to null, Double.NaN to 106.0, 10.0 to Double.NaN,
                Double.POSITIVE_INFINITY to 106.0, 10.0 to Double.NEGATIVE_INFINITY,
                90.01 to 106.0, -90.01 to 106.0, 10.0 to 180.01, 10.0 to -180.01)
            for ((lat, lng) in invalid) check(GoogleMapsDirections.open(recorder, lat, lng) == MapsOpenResult.INVALID_DESTINATION)
            check(recorder.intents.isEmpty())
            for ((lat, lng) in listOf(90.0 to 180.0, -90.0 to -180.0, 0.0 to 0.0))
                check(GoogleMapsDirections.destinationUri(lat, lng) != null)
        }
        test("DETAIL Missing Maps falls back once to generic HTTPS Intent") {
            val recorder = RecordingContext(context) { if (it.`package` != null) ActivityNotFoundException() else null }
            check(GoogleMapsDirections.open(recorder, 10.0, 106.0) == MapsOpenResult.OPENED)
            check(recorder.intents.size == 2 && recorder.intents[1].`package` == null)
            check(recorder.intents[0].data == recorder.intents[1].data)
        }
        test("DETAIL No URL handler returns Vietnamese failure; Security/runtime errors are safe") {
            for (failure in listOf(ActivityNotFoundException(), SecurityException(), IllegalStateException())) {
                val recorder = RecordingContext(context) { failure }
                val result = GoogleMapsDirections.open(recorder, 10.0, 106.0)
                check(result == MapsOpenResult.UNAVAILABLE && !result.message.isNullOrBlank() && recorder.intents.size == 2)
            }
        }
        test("DETAIL Map actions reject UNKNOWN errors logout wrong owner and pending GET") {
            val recorder = RecordingContext(context)
            for ((state, user) in listOf(loaded.copy(verified = false) to 999004L,
                loaded.copy(error = "FAKE network") to 999004L, loaded.copy(loading = true) to 999004L,
                loaded.copy(sessionExpired = true) to 999004L, loaded.copy(order = null) to 999004L,
                loaded to null, loaded to 999005L)) {
                check(GoogleMapsDirections.openCurrentOrder(recorder, state, user, OrderDirectionsTarget.CUSTOMER) == MapsOpenResult.UNVERIFIED)
            }
            check(recorder.intents.isEmpty())
        }

        var owner by mutableStateOf<Long?>(999004)
        var state by mutableStateOf(loaded)
        val destinations = mutableListOf<OrderDirectionsTarget>()
        var retries = 0
        var openResult = MapsOpenResult.OPENED
        instrumentation.runOnMainSync {
            main.setContent {
                val nav = rememberNavController()
                FoodExpressTheme {
                    NavHost(navController = nav, startDestination = "fake-dashboard",
                        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                        composable("fake-dashboard") {
                            DriverCurrentOrderCard(state.forDriver(owner), { retries++ },
                                onOpenDetails = { nav.navigate("fake-detail") { launchSingleTop = true } })
                        }
                        composable("fake-detail") {
                            DriverOrderDetailScreen(state, owner, onBack = { nav.popBackStack() }, onRetry = { retries++ },
                                onDirections = { destinations.add(it); openResult })
                        }
                    }
                }
            }
        }
        test("DETAIL Dashboard card opens detail and Back returns; no sensitive route values") {
            await { node("Đơn ${order.orderCode}") != null }
            click("Đơn ${order.orderCode}") // Tap the card, not only its text link.
            await { node("Chi tiết đơn hàng") != null && node(order.orderCode) != null }
            check(node("FAKE Quán kiểm thử") != null)
            click("‹"); await { node("Đơn ${order.orderCode}") != null }
            click("Xem chi tiết đơn hàng"); await { node("Chi tiết đơn hàng") != null }
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "detail-fixture.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        test("DETAIL Real buttons select correct targets and display receiver/note from fake API") {
            reveal("Chỉ đường đến nhà hàng"); click("Chỉ đường đến nhà hàng")
            reveal("Chỉ đường đến khách hàng")
            check(node("Người nhận: FAKE Người nhận") != null && node("Ghi chú: FAKE Ghi chú đơn") != null)
            click("Chỉ đường đến khách hàng")
            check(destinations == listOf(OrderDirectionsTarget.RESTAURANT, OrderDirectionsTarget.CUSTOMER))
        }
        test("DETAIL Missing payment/items show availability message, never mock money or COD") {
            reveal("Thông tin món ăn chưa khả dụng.")
            check(node("Thông tin thanh toán chưa khả dụng.") != null)
            check(node("COD") == null && node("115.000đ") == null && node("Thu nhập tài xế") == null)
        }
        test("DETAIL Reveal rechecks visible end content and rejects an absent fake label") {
            reveal("Thông tin món ăn chưa khả dụng.") // Already revealed by the preceding check.
            check(node("Thông tin món ăn chưa khả dụng.")?.isVisibleToUser == true)
            check(node("Thông tin thanh toán chưa khả dụng.")?.isVisibleToUser == true)
            val missing = "FAKE-DETAIL-ABSENT-LABEL"
            val failure = runCatching { reveal(missing) }.exceptionOrNull()
            check(failure is IllegalStateException && failure.message?.startsWith("Detail label not visible: $missing;") == true)
            check(failure.message?.contains("labelState=missing") == true)
        }
        test("DETAIL Intent failure shows Vietnamese snackbar") {
            instrumentation.runOnMainSync { openResult = MapsOpenResult.UNAVAILABLE }
            reveal("Chỉ đường đến khách hàng", backwards = true); click("Chỉ đường đến khách hàng")
            await { node(MapsOpenResult.UNAVAILABLE.message!!) != null }
        }
        test("DETAIL Unverified/error hides cached recipient and supports retry") {
            instrumentation.runOnMainSync { state = loaded.copy(verified = false, error = "FAKE Mất kết nối") }
            await { node("Chưa xác minh được đơn hiện tại") != null }
            check(node("Người nhận: FAKE Người nhận") == null && node("Chỉ đường đến khách hàng") == null)
            click("Thử đồng bộ lại"); check(retries == 1)
        }
        test("DETAIL Confirmed null displays neutral empty state") {
            instrumentation.runOnMainSync { state = loaded.copy(order = null) }
            await { node("Không có đơn đang thực hiện") != null }
            check(node(order.orderCode) == null)
        }
        test("DETAIL Logout/account switch hides old recipient until new owner verifies") {
            instrumentation.runOnMainSync { state = loaded; owner = null }
            await { node("Chưa xác minh được đơn hiện tại") != null }
            check(node("Người nhận: FAKE Người nhận") == null)
            instrumentation.runOnMainSync { owner = 999005 }
            check(node(order.orderCode) == null)
            instrumentation.runOnMainSync { state = loaded.copy(ownerId = 999005, order = order.copy(orderCode = "FAKE-NEW-OWNER", receiverName = "FAKE Người nhận mới")) }
            await { node("FAKE-NEW-OWNER") != null }
            reveal("Người nhận: FAKE Người nhận mới")
            check(node("Người nhận: FAKE Người nhận") == null)
        }
        // External launch uses only synthetic coordinates, never the dev account/order.
        for ((name, lat, lng) in listOf(Triple("pickup", order.pickupLat, order.pickupLng), Triple("delivery", order.deliveryLat, order.deliveryLng)))
            test("DETAIL Native Maps/URL launch and return to Food Express: $name (FAKE destination)") {
                var result = MapsOpenResult.UNAVAILABLE
                instrumentation.runOnMainSync { result = GoogleMapsDirections.open(main, lat, lng) }
                check(result == MapsOpenResult.OPENED)
                await { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.let {
                    it == "com.google.android.apps.maps" || it == "com.android.chrome" || it == "com.android.intentresolver"
                } == true }
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                await { node("Chi tiết đơn hàng") != null }
            }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val automation = instrumentation.uiAutomation
        val cleared = Build.VERSION.SDK_INT >= 34 && automation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            // API 26-33 fallback: refresh each node before reading its children/bounds.
            if (!cleared && !node.refresh()) return
            result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        automation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun node(label: String) = nodes().firstOrNull { it.text?.toString()?.let { text -> text == label || text == "↗  $label" } == true }
    private fun click(label: String) {
        var target = requireNotNull(node(label)) { "Missing fake UI label: $label" }
        while (!target.isClickable) target = requireNotNull(target.parent)
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun reveal(label: String, backwards: Boolean = false) {
        val action = if (backwards) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val deadline = SystemClock.elapsedRealtime() + 10 * 180L // Preserve the old retry/wait budget.
        fun matches(node: AccessibilityNodeInfo) = node.text?.toString().let { it == label || it == "↗  $label" }
        fun visible(snapshot: List<AccessibilityNodeInfo>) = snapshot.any { matches(it) && it.isVisibleToUser }
        var snapshot = nodes()
        var actionResult: Boolean? = null
        var reason = "notAttempted"
        var attempts = 0
        var scrolls = 0
        while (attempts < 10 && SystemClock.elapsedRealtime() < deadline) {
            if (visible(snapshot)) return
            val scrollables = snapshot.filter { it.isVisibleToUser && it.isScrollable }
            val scroller = scrollables.firstOrNull { node -> node.actionList.any { it.id == action } }
            reason = when {
                scrollables.isEmpty() -> "noScrollableNode"
                scroller == null -> "directionUnavailableAtBoundary"
                else -> "scrollRequested"
            }
            actionResult = scroller?.performAction(action)
            if (scroller != null) scrolls++
            attempts++
            // A false action is not a verdict: reread the rendered content after the existing wait.
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining > 0) SystemClock.sleep(minOf(180L, remaining))
            snapshot = nodes()
            if (visible(snapshot)) return
        }
        snapshot = nodes() // Also verify after the last attempt/boundary/slow snapshot.
        if (visible(snapshot)) return
        val labelState = if (snapshot.any(::matches)) "presentButNotVisible" else "missing"
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, "detail-scroll-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        error("Detail label not visible: $label; labelState=$labelState; reason=$reason; attempts=$attempts; scrolls=$scrolls; scrollAction=$actionResult; scrollableNodes=${snapshot.count { it.isScrollable }}; snapshotRefreshed=true")
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; SystemClock.sleep(100) }
        error("Timed out waiting for detail UI")
    }
}
