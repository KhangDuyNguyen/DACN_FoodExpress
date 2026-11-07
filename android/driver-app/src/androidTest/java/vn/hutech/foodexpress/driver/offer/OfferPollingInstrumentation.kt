package vn.hutech.foodexpress.driver.offer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.offer.*
import vn.hutech.foodexpress.driver.ui.offer.DriverOfferDialog
import vn.hutech.foodexpress.driver.ui.offer.DriverOffersViewModel

/** Run only on an isolated test AVD: real VM + Compose, FAKE server, no HTTP/JWT/MySQL. */
class OfferPollingInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    private data class Reply(val started: Long, val returned: Long, val hasOffer: Boolean)
    private class FakeBackend(private val delayBefore: Long, private val delayAfter: Long) : DriverOffersSource {
        private val origin = SystemClock.elapsedRealtime()
        private val epoch = LocalDateTime.of(2026, 11, 6, 12, 0)
        fun now() = epoch.plusNanos((SystemClock.elapsedRealtime() - origin) * 1_000_000)
        val replies = CopyOnWriteArrayList<Reply>()
        val starts = CopyOnWriteArrayList<Long>()
        private val active = AtomicInteger()
        val maxActive = AtomicInteger()
        @Volatile var offer: DriverOffer? = null
        override suspend fun load(): OffersResult {
            val started = SystemClock.elapsedRealtime()
            starts.add(started)
            val count = active.incrementAndGet()
            maxActive.updateAndGet { maxOf(it, count) }
            try {
                delay(delayBefore)
                val serverTime = now()
                val body = listOfNotNull(offer).filter { it.expiresAt > serverTime }
                delay(delayAfter)
                replies.add(Reply(started, SystemClock.elapsedRealtime(), body.isNotEmpty()))
                return OffersResult.Loaded(body, serverTime, started)
            } finally { active.decrementAndGet() }
        }
        override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult =
            error("No Accept/Reject actions belong in this passive polling test")
    }

    override fun onStart() {
        val report = StringBuilder("FAKE Backend + real Android ViewModel/Compose; NOT local Backend E2E.\n")
        var passed = 0
        var failed = 0
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val main = activity
            for ((name, before, after) in listOf(Triple("TC01_TC02_no_reload", 0L, 0L), Triple("TC07_slow_fake_response", 1_500L, 500L))) {
                var vm: DriverOffersViewModel? = null
                val collector = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                try {
                    val backend = FakeBackend(before, after)
                    val observedVm = AtomicLong(-1)
                    val stopEvents = AtomicInteger()
                    val destroyEvents = AtomicInteger()
                    lateinit var model: DriverOffersViewModel
                    runOnMainSync {
                        model = DriverOffersViewModel(backend, OfferMonotonicClock { SystemClock.elapsedRealtime() }, SavedStateHandle())
                        vm = model
                        collector.launch {
                            model.state.collect { state ->
                                if (state.offer != null) observedVm.compareAndSet(-1, SystemClock.elapsedRealtime())
                            }
                        }
                        main.setContent {
                            val state by model.state.collectAsStateWithLifecycle()
                            val owner = LocalLifecycleOwner.current
                            DisposableEffect(owner, model) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_START) model.setForeground(true)
                                    if (event == Lifecycle.Event.ON_STOP) { stopEvents.incrementAndGet(); model.setForeground(false) }
                                    if (event == Lifecycle.Event.ON_DESTROY) destroyEvents.incrementAndGet()
                                }
                                owner.lifecycle.addObserver(observer)
                                onDispose { owner.lifecycle.removeObserver(observer); model.setForeground(false) }
                            }
                            FoodExpressTheme {
                                Surface {
                                    Column { Text("POLLING TEST • FAKE DATA • NO REAL ACCOUNT") }
                                    DriverOfferDialog(state, {}, {}, {})
                                }
                            }
                        }
                        model.activateSession(999001)
                        model.setOnline(true)
                        model.setForeground(true)
                    }
                    await { backend.replies.any { !it.hasOffer } }
                    SystemClock.sleep(700)
                    val created = SystemClock.elapsedRealtime()
                    val serverTime = backend.now()
                    backend.offer = DriverOffer(991, 99301, "FAKE-POLL-991", "FAKE Restaurant", "FAKE Pickup", 10.0, 106.0,
                        "FAKE Delivery", 1.23, serverTime, serverTime.plusSeconds(30))
                    // No refresh, click, restart, logout, Activity recreation or new session after creation.
                    await { backend.replies.any { it.hasOffer } && observedVm.get() > 0 }
                    await { findText("Đơn FAKE-POLL-991") != null }
                    val dialogVisible = SystemClock.elapsedRealtime()
                    val reply = backend.replies.first { it.hasOffer }
                    check(reply.started >= created && reply.returned >= reply.started)
                    check(observedVm.get() >= reply.returned && dialogVisible >= observedVm.get())
                    check(stopEvents.get() == 0 && destroyEvents.get() == 0 && backend.maxActive.get() == 1)
                    val timeline = "$name FAKE_CREATE=0ms GET_START=${reply.started - created}ms " +
                        "FAKE_RETURN=${reply.returned - created}ms VM_STATE=${observedVm.get() - created}ms " +
                        "DIALOG_ACCESSIBILITY=${dialogVisible - created}ms maxConcurrentGET=${backend.maxActive.get()} " +
                        "ON_STOP=${stopEvents.get()} ON_DESTROY=${destroyEvents.get()}"
                    Log.i("OfferPollingCheck", timeline)
                    report.append("PASS $timeline\n")
                    passed++
                } catch (error: Throwable) {
                    failed++; report.append("FAIL $name: $error\n")
                } finally {
                    runOnMainSync { vm?.clear(); collector.cancel() }
                }
            }
        } catch (error: Throwable) { failed++; report.append("ERROR setup: $error\n") }
        finally { activity?.let { runOnMainSync { it.finish() } } }
        report.append("RESULT: PASS=$passed FAIL=$failed\n")
        finish(if (failed == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()) })
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for polling/VM/Dialog stage")
    }

    private fun findText(text: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return uiAutomation.rootInActiveWindow?.let(::visit)
    }
}
