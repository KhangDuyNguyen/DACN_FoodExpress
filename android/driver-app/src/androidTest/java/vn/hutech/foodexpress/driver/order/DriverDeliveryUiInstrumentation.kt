package vn.hutech.foodexpress.driver.order

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.view.WindowCompat
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.order.*
import vn.hutech.foodexpress.driver.ui.order.*
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.time.LocalDateTime

/** Isolated AVD. Real production Screen/VM; all account, command and order data below is FIXTURE. */
class DriverDeliveryUiInstrumentation : Instrumentation() {
    private var directory = "week5d1"
    override fun onCreate(arguments: Bundle?) {
        directory = arguments?.getString("directory") ?: directory
        require(directory in setOf("week5d1", "week5d1-large"))
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        val report = StringBuilder("FIXTURE API/session, production Compose Screen and CurrentOrder VM. NOT real Backend E2E.\n")
        var passed = 0; var failed = 0
        var model: DriverCurrentOrderViewModel? = null
        var activity: MainActivity? = null
        fun test(name: String, block: () -> Unit) {
            try { block(); passed++; report.append("PASS $name\n") }
            catch (e: Throwable) { failed++; report.append("FAIL $name: $e\n${e.stackTrace.take(3).joinToString("\n")}\n") }
        }
        try {
            val main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = main
            var originalLightStatusBar = false
            runOnMainSync { originalLightStatusBar = WindowCompat.getInsetsController(main.window, main.window.decorView).isAppearanceLightStatusBars }
            val owner = mutableStateOf<Long?>(7)
            var source = FixtureSource("PREPARING")
            val maps = mutableListOf<OrderDirectionsTarget>()
            var home = 0
            var renderIndex = 0
            fun render(status: String) {
                val marker = "5D fixture ${++renderIndex}: $status"
                runOnMainSync {
                    model?.clear(); source = FixtureSource(status)
                    val vm = DriverCurrentOrderViewModel(source); model = vm
                    owner.value = 7; vm.setForeground(true); vm.activateSession(7)
                    main.setContent {
                        val current by vm.state.collectAsState()
                        key(marker) { FoodExpressTheme { Surface(Modifier.semantics { contentDescription = marker }) {
                            DriverOrderDetailScreen(current, owner.value,
                                onBack = { home++; vm.dismissCompletion() }, onRetry = vm::refresh,
                                onDirections = { maps.add(it); MapsOpenResult.OPENED },
                                onDelivery = vm::changeDeliveryStatus, onRetryDelivery = vm::retryPendingDelivery)
                        } } }
                    }
                }
                await { model!!.state.value.verified && nodes().any { it.contentDescription?.toString() == marker } && text("FIXTURE-5D-950501") != null }
            }
            test("PREPARING has pickup action, real fixture data and four-step progress") {
                render("PREPARING")
                check(text("Nhà hàng đang chuẩn bị món") != null)
                check(text("Chuẩn bị") != null && text("Đã lấy") != null && text("Đang giao") != null && text("Hoàn thành") != null)
                check(button("Đã lấy món")?.isEnabled == true)
                runOnMainSync { check(!WindowCompat.getInsetsController(main.window, main.window.decorView).isAppearanceLightStatusBars) }
                capture("01-preparing.png")
            }
            test("All information and both vector direction buttons remain reachable by scrolling") {
                check(text("Thời gian đặt: 12/11/2026 12:00") != null && text("Cập nhật: 12/11/2026 12:00") != null)
                reveal("Chỉ đường đến nhà hàng"); click("Chỉ đường đến nhà hàng")
                reveal("Người nhận: FIXTURE Receiver"); check(text("Người nhận: FIXTURE Receiver")?.isVisibleToUser == true)
                reveal("Ghi chú: Ghi chú thử nghiệm"); check(text("Ghi chú: Ghi chú thử nghiệm")?.isVisibleToUser == true)
                reveal("Chỉ đường đến khách hàng"); click("Chỉ đường đến khách hàng")
                check(maps == listOf(OrderDirectionsTarget.RESTAURANT, OrderDirectionsTarget.CUSTOMER))
                reveal("Thông tin món ăn chưa khả dụng.")
                check(text("Thông tin thanh toán chưa khả dụng.") != null)
                check(button("Đã lấy món")?.isVisibleToUser == true && source.commands.isEmpty())
                capture("08-detail-scrolled.png")
            }
            test("READY_FOR_PICKUP keeps pickup enabled without an invented wait") {
                render("READY_FOR_PICKUP"); check(text("Món đã sẵn sàng") != null)
                check(button("Đã lấy món")?.isEnabled == true); capture("02-ready-for-pickup.png")
            }
            test("Cancel pickup confirmation sends zero PATCH and shows only one dialog") {
                click("Đã lấy món"); await { text("Bạn đã thực sự nhận món từ nhà hàng chưa?") != null }
                check(source.commands.isEmpty()); check(text("Xác nhận giao hàng thành công?") == null)
                checkDialogActions("Xác nhận đã lấy món")
                capture("03-confirm-pickup.png"); click("Hủy")
                await { text("Bạn đã thực sự nhận món từ nhà hàng chưa?") == null }
                check(source.commands.isEmpty() && model!!.state.value.order?.status == "READY_FOR_PICKUP")
            }
            test("Confirmed pickup sends one command; busy guard prevents double tap") {
                source.gate = CompletableDeferred()
                click("Đã lấy món"); await { text("Xác nhận đã lấy món") != null }; click("Xác nhận đã lấy món")
                try {
                    await { model!!.state.value.deliverySending && button("Đã lấy món")?.isEnabled == false }
                    check(source.commands.size == 1)
                    runOnMainSync { model!!.changeDeliveryStatus("PICKED_UP") }
                    check(source.commands.size == 1)
                } finally { runOnMainSync { source.gate?.complete(Unit) } }
                await { text("Bắt đầu giao hàng") != null && model!!.state.value.verified }
                check(source.commands == listOf("PICKED_UP")); capture("04-picked-up.png")
            }
            test("PICKED_UP advances only after explicit Begin delivery") {
                check(model!!.state.value.order?.status == "PICKED_UP")
                click("Bắt đầu giao hàng"); await { text("Đang giao hàng") != null && text("Xác nhận đã giao") != null }
                check(source.commands == listOf("PICKED_UP", "DELIVERING")); capture("05-delivering.png")
            }
            test("Cancel delivered confirmation does not send DELIVERED") {
                click("Xác nhận đã giao"); await { text("Xác nhận giao hàng thành công?") != null }
                checkDialogActions("Xác nhận giao thành công")
                capture("06-confirm-delivered.png"); click("Hủy")
                await { text("Xác nhận giao hàng thành công?") == null }
                check(source.commands == listOf("PICKED_UP", "DELIVERING"))
            }
            test("Server confirms DELIVERED; null current order does not erase success or expose recipient") {
                click("Xác nhận đã giao"); await { text("Xác nhận giao thành công") != null }; click("Xác nhận giao thành công")
                await { text("Giao hàng thành công!") != null && model!!.state.value.verified }
                check(model!!.state.value.order == null && source.current == null)
                repeat(2) { runOnMainSync { model!!.refresh() }; await { !model!!.state.value.loading } }
                check(text("Giao hàng thành công!") != null && text("FIXTURE Receiver") == null)
                check(text("Thu nhập") == null && text("80%") == null && text("COD") == null)
                capture("07-delivered.png")
            }
            test("Completion survives same-owner recheck but disappears immediately for another owner") {
                runOnMainSync { model!!.setForeground(false); model!!.pauseSessionVerification(); model!!.setForeground(true); model!!.activateSession(7) }
                await { text("Giao hàng thành công!") != null && model!!.state.value.verified }
                runOnMainSync { owner.value = 8 }
                await { text("Giao hàng thành công!") == null && text("Đơn FIXTURE-5D-950501") == null }
                runOnMainSync { model!!.clear() }
                check(model!!.state.value.order == null && model!!.state.value.completion == null)
            }
            test("PLACED fallback offers synchronization and sends no illegal PATCH") {
                render("PLACED"); check(text("Cập nhật lại đơn") != null)
                check(button("Đã lấy món") == null && button("Bắt đầu giao hàng") == null)
                check(source.commands.isEmpty())
            }
            test("Unknown GET hides recipient and prevents delivery commands") {
                render("PREPARING"); source.failure = CurrentOrderResult.Failed("FIXTURE connection unavailable")
                runOnMainSync { model!!.refresh() }
                await { !model!!.state.value.verified && !model!!.state.value.loading && text("Chưa xác minh được đơn hiện tại") != null }
                check(text("Chưa xác minh được đơn hiện tại") != null && text("FIXTURE Receiver") == null)
                check(button("Đã lấy món") == null && source.commands.isEmpty())
            }
            test("Lost DELIVERED response stays uncertain until the user explicitly replays the same action") {
                render("DELIVERING"); source.loseResponse = true
                click("Xác nhận đã giao"); await { text("Xác nhận giao thành công") != null }; click("Xác nhận giao thành công")
                await { model!!.state.value.deliveryPending != null && !model!!.state.value.deliverySending && !model!!.state.value.loading }
                check(text("Giao hàng thành công!") == null && source.commands == listOf("DELIVERED"))
                click("Gửi lại thao tác đã chọn"); await { text("Giao hàng thành công!") != null }
                check(source.commands == listOf("DELIVERED", "DELIVERED"))
            }
            test("Home acknowledgement removes the completion receipt") {
                click("Quay về Trang chủ"); await { model!!.state.value.completion == null }; check(home == 1)
            }
            test("Preparation polling retains a valid pickup confirmation without sending PATCH") {
                render("PREPARING"); click("Đã lấy món")
                await { text("Bạn đã thực sự nhận món từ nhà hàng chưa?") != null }
                source.current = source.current!!.copy(status = "READY_FOR_PICKUP")
                runOnMainSync { model!!.refresh() }
                await { model!!.state.value.order?.status == "READY_FOR_PICKUP" && !model!!.state.value.loading }
                check(text("Bạn đã thực sự nhận món từ nhà hàng chưa?") != null && source.commands.isEmpty())
                click("Hủy")
            }
            test("Dashboard recovery action reopens an uncertain delivery and hides itself for a different owner") {
                render("DELIVERING"); source.loseResponse = true
                click("Xác nhận đã giao"); await { text("Xác nhận giao thành công") != null }; click("Xác nhận giao thành công")
                await { model!!.state.value.deliveryPending != null && !model!!.state.value.deliverySending && !model!!.state.value.loading }
                val showDetail = mutableStateOf(false)
                runOnMainSync { main.setContent {
                    val current by model!!.state.collectAsState()
                    FoodExpressTheme { Surface {
                        if (showDetail.value) DriverOrderDetailScreen(current, owner.value,
                            onBack = { showDetail.value = false }, onRetry = { model!!.refresh() },
                            onDirections = { MapsOpenResult.UNVERIFIED }, onDelivery = { model!!.changeDeliveryStatus(it) },
                            onRetryDelivery = { model!!.retryPendingDelivery() })
                        else DriverDeliveryRecoveryAction(current, owner.value) { showDetail.value = true }
                    } }
                } }
                await { button("Kiểm tra thao tác giao hàng") != null }
                runOnMainSync { owner.value = 8 }; await { button("Kiểm tra thao tác giao hàng") == null }
                runOnMainSync { owner.value = 7 }; await { button("Kiểm tra thao tác giao hàng") != null }
                click("Kiểm tra thao tác giao hàng"); await { button("Gửi lại thao tác đã chọn") != null }
                check(source.commands == listOf("DELIVERED") && text("FIXTURE Receiver") == null)
                click("Gửi lại thao tác đã chọn"); await { text("Giao hàng thành công!") != null }
                check(source.commands == listOf("DELIVERED", "DELIVERED"))
            }
            test("Detail status-bar appearance is local and restores after leaving the screen") {
                runOnMainSync { main.setContent { FoodExpressTheme { Surface { androidx.compose.material3.Text("FIXTURE detail closed") } } } }
                await { text("FIXTURE detail closed") != null }
                runOnMainSync { check(WindowCompat.getInsetsController(main.window, main.window.decorView).isAppearanceLightStatusBars == originalLightStatusBar) }
            }
            report.append("FONT_SCALE=${targetContext.resources.configuration.fontScale}\n")
        } catch (e: Throwable) { failed++; report.append("FAIL runner: $e\n") }
        finally {
            runOnMainSync { model?.clear(); activity?.finish() }
            report.append("RESULT: PASS=$passed FAIL=$failed\n")
            finish(if (failed == 0) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,
                Bundle().apply { putString("stream", report.toString()) })
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        val root = uiAutomation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) { result.add(node); for (i in 0 until node.childCount) node.getChild(i)?.let(::visit) }
        visit(root); return result
    }
    private fun text(value: String) = nodes().firstOrNull { it.text?.toString() == value }
    private fun button(value: String): AccessibilityNodeInfo? {
        var n = text(value)
        repeat(5) { if (n?.isClickable == true || n?.isEnabled == false) return n; n = n?.parent }
        return null
    }
    private fun click(value: String) {
        // VM publication can precede Compose's next frame; interact only with the actual enabled UI node.
        await { button(value)?.let { it.isEnabled && it.isVisibleToUser } == true }
        check(button(value)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Missing/cannot click: $value" }
    }
    private fun checkDialogActions(confirmLabel: String) {
        await { button(confirmLabel)?.isVisibleToUser == true && button("Hủy")?.isVisibleToUser == true }
        val confirm = Rect(); val cancel = Rect()
        button(confirmLabel)!!.getBoundsInScreen(confirm); button("Hủy")!!.getBoundsInScreen(cancel)
        check(confirm.left == cancel.left && confirm.right == cancel.right && confirm.bottom <= cancel.top) {
            "Both dialogs must use the same full-width Confirm then Cancel arrangement: $confirm / $cancel"
        }
    }
    private fun reveal(value: String) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            val snapshot = nodes()
            if (snapshot.any { it.text?.toString() == value && it.isVisibleToUser }) return
            snapshot.firstOrNull { it.isScrollable && it.isVisibleToUser &&
                it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            try { uiAutomation.waitForIdle(100, 1_000) } catch (_: Exception) { }
        }
        error("Detail content not reachable within 15 seconds: $value")
    }
    private fun await(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(100) }
        error("Condition timed out after 15 seconds")
    }
    private fun capture(name: String) {
        try { uiAutomation.waitForIdle(150, 2_000) } catch (_: Exception) { }
        val folder = File(targetContext.getExternalFilesDir(null), directory).apply { mkdirs() }
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(folder, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private class FixtureSource(status: String) : DriverCurrentOrderSource {
        val now = LocalDateTime.of(2026, 11, 12, 12, 0)
        var current: DriverCurrentOrder? = DriverCurrentOrder(950501, "FIXTURE-5D-950501", status, now, now,
            950901, "FIXTURE Nhà hàng", "Địa chỉ nhà hàng thử nghiệm", "0280000000", 10.77, 106.70,
            "FIXTURE Receiver", "0900000000", "Địa chỉ giao hàng thử nghiệm", 10.78, 106.71, "Ghi chú thử nghiệm")
        var failure: CurrentOrderResult? = null
        var gate: CompletableDeferred<Unit>? = null
        var loseResponse = false
        val commands = mutableListOf<String>()
        override suspend fun load(): CurrentOrderResult = failure ?: CurrentOrderResult.Loaded(current, now)
        override suspend fun changeStatus(orderId: Long, target: String): DriverDeliveryResult {
            commands.add(target); gate?.await(); gate = null
            current = if (target == "DELIVERED") null else current?.copy(status = target, updatedAt = now.plusSeconds(1))
            if (loseResponse) { loseResponse = false; return DriverDeliveryResult.Uncertain("FIXTURE mất phản hồi; chưa rõ kết quả.") }
            return DriverDeliveryResult.Updated(DriverDeliveryStatus(orderId, target, now.plusSeconds(1),
                if (target == "DELIVERED") now.plusSeconds(1) else null))
        }
    }
}
