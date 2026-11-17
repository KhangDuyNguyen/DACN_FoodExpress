package vn.hutech.foodexpress.driver.offer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDateTime
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.offer.DriverOffer
import vn.hutech.foodexpress.driver.data.offer.OfferAction
import vn.hutech.foodexpress.driver.ui.offer.*

/** Dependency-free UI smoke checks. Run ONLY on a fresh test emulator: all offers below are FAKE. */
class OfferUiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = StringBuilder("Compose UI fixtures only; NOT a Backend integration test.\n")
        var passed = 0
        var failed = 0
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val now = LocalDateTime.parse("2026-11-06T10:00:00")
            val fakeOffer = DriverOffer(991, 99301, "TEST-FE99301", "Quán kiểm thử • DỮ LIỆU GIẢ",
                "12 Đường kiểm thử, Quận 1", 10.0, 106.0, "28 Đường giao hàng kiểm thử, Quận 1", 1.23, now, now.plusSeconds(30))
            val state = mutableStateOf(DriverOffersUiState(offer = fakeOffer, remainingSeconds = 24, phase = DriverOfferPhase.OFFER))
            var accepts = 0
            var rejects = 0
            var retries = 0
            val main = activity
            runOnMainSync {
                main.setContent {
                    FoodExpressTheme {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(24.dp)) {
                                androidx.compose.material3.Text("KIỂM THỬ UI • DỮ LIỆU GIẢ")
                                DriverOfferStatusCard(state.value, { retries++ }, {})
                            }
                            DriverOfferDialog(state.value, {
                                accepts++
                                state.value = state.value.copy(phase = DriverOfferPhase.SENDING_ACCEPT)
                            }, {
                                rejects++
                                state.value = state.value.copy(phase = DriverOfferPhase.SENDING_REJECT)
                            }, {})
                        }
                    }
                }
            }
            fun test(name: String, block: () -> Unit) {
                try { block(); passed++; report.append("PASS $name\n") }
                catch (error: Throwable) { failed++; report.append("FAIL $name: $error\n") }
            }
            fun render(value: DriverOffersUiState) { runOnMainSync { state.value = value }; waitForIdleSync() }
            test("Offer shows only API fields and Backend-based countdown") {
                waitText("Đơn TEST-FE99301"); waitText("Quán kiểm thử • DỮ LIỆU GIẢ")
                waitText("28 Đường giao hàng kiểm thử, Quận 1"); waitText("1,23 km"); waitText("00:24")
                check(findText("COD") == null && findText("Thu nhập dự kiến") == null)
                screenshot("offer-dialog.png")
            }
            test("Accept click shows loading and disables both buttons") {
                click("Nhận đơn"); waitText("Đang xử lý…")
                check(!button("Nhận đơn").isEnabled && !button("Từ chối").isEnabled)
                check(accepts == 1 && rejects == 0)
                screenshot("offer-sending.png")
            }
            test("Reject click shows loading and disables both buttons") {
                render(DriverOffersUiState(offer = fakeOffer, remainingSeconds = 20, phase = DriverOfferPhase.OFFER))
                click("Từ chối"); waitText("Đang xử lý…")
                check(!button("Nhận đơn").isEnabled && rejects == 1)
            }
            test("Confirmed accept displays actual orderId, without fabricated detail") {
                render(DriverOffersUiState(phase = DriverOfferPhase.ACCEPTED, acceptedOrderId = 99301,
                    message = "Đã nhận đơn thành công. orderId: 99301."))
                waitText("Đã nhận đơn"); waitText("Đã nhận đơn thành công. orderId: 99301.")
                screenshot("offer-accepted.png")
            }
            test("Ambiguous Accept offers a verification action") {
                render(DriverOffersUiState(phase = DriverOfferPhase.ACCEPT_UNKNOWN,
                    pending = PendingOfferAction(991, 99301, OfferAction.ACCEPT), message = "Máy chủ có thể đã xử lý; chưa xác định kết quả."))
                waitText("Chưa xác định kết quả Nhận đơn"); click("Xác minh Nhận đơn"); check(retries == 1)
                screenshot("offer-unknown.png")
            }
            test("Ambiguous Reject 503 does not claim rejection failed") {
                render(DriverOffersUiState(phase = DriverOfferPhase.REJECT_UNKNOWN,
                    pending = PendingOfferAction(991, 99301, OfferAction.REJECT), message = "Có thể đã từ chối; hãy xác minh lại."))
                waitText("Chưa xác định kết quả Từ chối"); click("Xác minh Từ chối"); check(retries == 2)
            }
            test("Expiry closes dialog and presents expiry state") {
                render(DriverOffersUiState(phase = DriverOfferPhase.EXPIRED, message = "Lời mời kiểm thử đã hết hạn."))
                waitText("Đã hết thời gian nhận"); check(findText("Nhận đơn") == null)
            }
            test("Invalidation uses a neutral message") {
                render(DriverOffersUiState(phase = DriverOfferPhase.UNAVAILABLE, message = "Lời mời không còn hiệu lực."))
                waitText("Lời mời không còn khả dụng"); check(findText("Khách hàng đã hủy đơn") == null)
            }
            test("Network error shows refresh action") {
                render(DriverOffersUiState(phase = DriverOfferPhase.NETWORK_ERROR, message = "Kiểm tra kết nối mạng và thử lại."))
                waitText("Kiểm tra kết nối mạng và thử lại."); waitText("Tải lại lời mời")
            }
            test("Confirmed rejection state is visible") {
                render(DriverOffersUiState(phase = DriverOfferPhase.REJECTED, message = "Đã từ chối lời mời kiểm thử."))
                waitText("Đã từ chối lời mời")
            }
        } catch (error: Throwable) {
            failed++; report.append("ERROR setup: $error\n")
        } finally {
            activity?.let { runOnMainSync { it.finish() } }
        }
        report.append("RESULT: PASS=$passed FAIL=$failed\n")
        val result = Bundle().apply { putString("stream", report.toString()) }
        finish(if (failed == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun findText(text: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { match -> return match } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::visit)
    }

    private fun waitText(text: String): AccessibilityNodeInfo {
        val until = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < until) {
            findText(text)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Missing UI text: $text")
    }

    private fun button(text: String): AccessibilityNodeInfo {
        var node = waitText(text)
        while (!node.isClickable && node.isEnabled) node = node.parent ?: error("Button not clickable: $text")
        return node
    }

    private fun click(text: String) { check(button(text).performAction(AccessibilityNodeInfo.ACTION_CLICK)) }

    private fun screenshot(name: String) {
        val bitmap = requireNotNull(uiAutomation.takeScreenshot())
        val output = File(requireNotNull(targetContext.getExternalFilesDir(null)), name)
        output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
}
