package vn.hutech.foodexpress.driver.order

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import retrofit2.Response
import android.util.Base64
import vn.hutech.foodexpress.driver.data.auth.*
import vn.hutech.foodexpress.driver.data.location.*
import vn.hutech.foodexpress.driver.data.profile.*
import vn.hutech.foodexpress.driver.data.session.DriverSessionStore
import vn.hutech.foodexpress.driver.data.status.*
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthPhase
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthUiState
import vn.hutech.foodexpress.driver.ui.auth.DriverAuthViewModel
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardViewModel
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.order.DriverCurrentOrder
import vn.hutech.foodexpress.driver.data.profile.DriverProfile
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardScreen
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardUiState
import vn.hutech.foodexpress.driver.ui.dashboard.DriverOrderKnowledge
import vn.hutech.foodexpress.driver.data.order.CurrentOrderResult
import vn.hutech.foodexpress.driver.data.order.DriverCurrentOrderSource
import vn.hutech.foodexpress.driver.data.offer.*
import vn.hutech.foodexpress.driver.ui.offer.DriverOffersViewModel
import vn.hutech.foodexpress.driver.ui.offer.DriverOfferDialog
import vn.hutech.foodexpress.driver.ui.offer.DriverOffersUiState
import vn.hutech.foodexpress.driver.ui.offer.DriverOfferPhase
import vn.hutech.foodexpress.driver.ui.offer.PendingOfferAction
import vn.hutech.foodexpress.driver.ui.order.*
import vn.hutech.foodexpress.driver.navigation.confirmedOfflineForCurrentOrder

/** Run on an isolated AVD only. All account/order/API data in this runner is FAKE. */
class CurrentOrderUiInstrumentation : Instrumentation() {
    private var visualOnly = false
    private var policySnapshots = false
    override fun onCreate(arguments: Bundle?) {
        visualOnly = arguments?.getString("visualOnly") == "true"
        policySnapshots = arguments?.getString("policySnapshots") == "true"
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        if (visualOnly) { captureVisualReview(); return }
        val report = StringBuilder("FAKE UI/API callbacks, real Compose Card/Dashboard/Pull-to-Refresh. NOT Backend E2E.\n")
        var pass = 0; var fail = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var activity: MainActivity? = null
        fun test(name: String, block: () -> Unit) {
            try { block(); pass++; report.append("PASS $name\n") }
            catch (e: Throwable) { fail++; report.append("FAIL $name: $e\n") }
        }
        try {
            val main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity = main
            val now = LocalDateTime.of(2026, 11, 6, 12, 0)
            val order = DriverCurrentOrder(99301, "FAKE-4B2-99301", "PLACED", now, now, 901,
                "FAKE Restaurant", "FAKE Pickup", null, 10.0, 106.0, "FAKE Receiver", "FAKE Contact",
                "FAKE Delivery", 11.0, 107.0, null)
            val state = mutableStateOf(DriverCurrentOrderUiState(order = order, verified = true))
            val retries = AtomicInteger()
            runOnMainSync { main.setContent { FoodExpressTheme { Surface { DriverCurrentOrderCard(state.value, { retries.incrementAndGet() }) } } } }
            test("Card shows actual DTO fields, no invented amounts") {
                await { text("Đơn FAKE-4B2-99301") != null && text("Trạng thái: Đã nhận đơn") != null }
                check(text("FAKE Restaurant") != null && text("FAKE Delivery") != null)
                check(text("COD") == null && text("ETA") == null)
            }
            test("Sync error labels retained data and offers retry") {
                runOnMainSync { state.value = state.value.copy(verified = false, error = "FAKE sync failure") }
                await { text("Thông tin đơn từ lần cập nhật trước. FAKE sync failure") != null }
                check(nodes().count { it.text?.toString()?.contains("FAKE sync failure") == true } == 1)
                click("Thử cập nhật lại"); check(retries.get() == 1)
            }
            test("Explicit null removes previous order and shows neutral idle") {
                runOnMainSync { state.value = DriverCurrentOrderUiState(verified = true) }
                await { text("Chưa có đơn hàng") != null }
                check(text("Đơn FAKE-4B2-99301") == null && text("Khách hàng đã hủy đơn") == null)
            }
            test("Pull-to-Refresh waits for both fake APIs and blocks duplicate gestures") {
                val dash = mutableStateOf(DriverDashboardUiState(profile = DriverProfile(fullName = "FAKE Driver", status = "APPROVED", isOnline = false)))
                val orderLoading = mutableStateOf(false)
                var orderResponse = CompletableDeferred<Unit>()
                val profileCalls = AtomicInteger(); val orderCalls = AtomicInteger()
                runOnMainSync {
                    main.setContent {
                        FoodExpressTheme {
                            DriverDashboardScreen(dash.value, onRetry = {
                                profileCalls.incrementAndGet(); orderCalls.incrementAndGet()
                                dash.value = dash.value.copy(loading = true)
                                orderLoading.value = true
                                scope.launch { delay(200); dash.value = dash.value.copy(loading = false) }
                                scope.launch { orderResponse.await(); orderLoading.value = false }
                            }, onReceivingOrders = {}, onProfile = {}, onLogout = {}, onGpsRetry = {},
                                currentOrderContent = { DriverCurrentOrderCard(DriverCurrentOrderUiState(verified = true), {}) },
                                orderRefreshing = orderLoading.value)
                        }
                    }
                }
                await { text("FAKE Driver") != null }
                report.append("TRACE Dashboard ready\n")
                pull()
                report.append("TRACE after pull profileCalls=${profileCalls.get()} orderCalls=${orderCalls.get()}\n")
                await { profileCalls.get() == 1 }
                await("fake profile response") { !dash.value.loading }
                check(!dash.value.loading && orderLoading.value)
                await { refreshing() }
                report.append("TRACE profile done, order pending, refresh indicator accessible\n")
                pull(); check(profileCalls.get() == 1 && orderCalls.get() == 1)
                orderResponse.complete(Unit)
                await { !orderLoading.value && !refreshing() }
                report.append("TRACE first refresh finished\n")
                orderResponse = CompletableDeferred()
                pull(); await { profileCalls.get() == 2 }
                orderResponse.complete(Unit)
                await { !orderLoading.value && !refreshing() }
                check(orderCalls.get() == 2 && dash.value.profile?.isOnline == false)
            }

            val receiving = mutableStateOf(DriverDashboardUiState(
                profile = DriverProfile(fullName = "FAKE Active Driver", status = "APPROVED", isOnline = true),
                orderKnowledge = DriverOrderKnowledge.ACTIVE))
            val toggles = AtomicInteger()
            runOnMainSync {
                main.setContent { FoodExpressTheme {
                    DriverDashboardScreen(receiving.value, {}, { toggles.incrementAndGet() }, {}, {}, {})
                } }
            }
            test("ACTIVE switch is checked but disabled and busy reason is visible") {
                await { text("Đang thực hiện đơn") != null }
                val toggle = nodes().first { it.isCheckable }
                check(toggle.isChecked && !toggle.isEnabled && toggles.get() == 0)
                check(text("Chưa thể tắt nhận đơn khi đang thực hiện đơn hàng.") != null)
            }
            test("UNKNOWN and pending Accept do not enable a receiving switch") {
                runOnMainSync { receiving.value = receiving.value.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN) }
                await { text("Chưa xác minh được đơn hàng") != null }
                check(!nodes().first { it.isCheckable }.isEnabled)
                runOnMainSync { receiving.value = receiving.value.copy(acceptPending = true) }
                await { text("Đang xác nhận nhận đơn") != null }
                check(!nodes().first { it.isCheckable }.isEnabled && toggles.get() == 0)
            }

            // Real CurrentOrder/Offers VMs, fake Backend/session. Lifecycle owner stays above NavHost.
            val loads = AtomicInteger(); val offerLoads = AtomicInteger(); val stops = AtomicInteger(); val starts = AtomicInteger()
            val current = DriverCurrentOrderViewModel(DriverCurrentOrderSource {
                loads.incrementAndGet(); CurrentOrderResult.Loaded(order, now)
            })
            val offers = DriverOffersViewModel(object : DriverOffersSource {
                override suspend fun load(): OffersResult {
                    offerLoads.incrementAndGet(); return OffersResult.Loaded(emptyList(), now, SystemClock.elapsedRealtime())
                }
                override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult = error("No POST in fake lifecycle test")
            }, OfferMonotonicClock { SystemClock.elapsedRealtime() }, SavedStateHandle())
            try {
                runOnMainSync {
                    current.connectOffers(offers)
                    offers.activateSession(999001); offers.setOnline(true); offers.setForeground(true)
                    current.setForeground(true); current.activateSession(999001)
                    main.setContent {
                        val currentState by current.state.collectAsStateWithLifecycle()
                        val owner = LocalLifecycleOwner.current
                        val nav = rememberNavController()
                        DisposableEffect(owner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) { starts.incrementAndGet(); current.setForeground(true); offers.setForeground(true) }
                                if (event == Lifecycle.Event.ON_STOP) { stops.incrementAndGet(); current.setForeground(false); offers.setForeground(false) }
                            }
                            owner.lifecycle.addObserver(observer)
                            onDispose { owner.lifecycle.removeObserver(observer); current.setForeground(false); offers.setForeground(false) }
                        }
                        FoodExpressTheme {
                            NavHost(navController = nav, startDestination = "home") {
                                composable("home") {
                                    Column {
                                        TextButton(onClick = { nav.navigate("internal") }) { Text("Mở màn nội bộ kiểm thử") }
                                        TextButton(onClick = { current.clear(); offers.clear() }) { Text("Đăng xuất phiên giả") }
                                        TextButton(onClick = {
                                            offers.activateSession(999001); offers.setOnline(true); offers.setForeground(true)
                                            current.activateSession(999001)
                                        }) { Text("Đăng nhập lại phiên giả") }
                                        Box(Modifier.weight(1f)) { DriverCurrentOrderCard(currentState, current::refresh) }
                                    }
                                }
                                composable("internal") {
                                    Column {
                                        Text("Màn nội bộ kiểm thử")
                                        TextButton(onClick = { nav.popBackStack() }) { Text("Quay lại kiểm thử") }
                                    }
                                }
                            }
                        }
                    }
                }
                test("Internal NavHost transition keeps root lifecycle and current order") {
                    await { text("Đơn FAKE-4B2-99301") != null }
                    val before = stops.get()
                    click("Mở màn nội bộ kiểm thử"); await { text("Màn nội bộ kiểm thử") != null }
                    check(stops.get() == before && current.state.value.order?.orderId == 99301L)
                    click("Quay lại kiểm thử"); await { text("Đơn FAKE-4B2-99301") != null }
                    check(stops.get() == before && offerLoads.get() == 0)
                }
                test("Native background stops current polling and foreground re-verifies") {
                    val beforeStops = stops.get(); val beforeStarts = starts.get()
                    runOnMainSync { check(main.moveTaskToBack(true)) }
                    await { stops.get() > beforeStops }
                    val beforeLoads = loads.get(); SystemClock.sleep(800); check(loads.get() == beforeLoads)
                    targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    await { starts.get() > beforeStarts && loads.get() > beforeLoads }
                    await { text("Đơn FAKE-4B2-99301") != null }
                    check(offerLoads.get() == 0)
                }
                test("Fake logout removes receiver state; login restores by fresh Current Order GET") {
                    val beforeLoads = loads.get()
                    click("Đăng xuất phiên giả"); await { text("Chưa xác minh được đơn hàng") != null }
                    check(current.state.value.order == null && text("FAKE Delivery") == null)
                    click("Đăng nhập lại phiên giả"); await { text("Đơn FAKE-4B2-99301") != null }
                    check(loads.get() > beforeLoads && offerLoads.get() == 0)
                }
            } finally { runOnMainSync { current.clear(); offers.clear() } }
            for (profileFirst in listOf(true, false)) test("HOTFIX real VMs/Auth lifecycle recover ACTIVE without Refresh; profileFirst=$profileFirst") {
                checkDeliveryRecovery(main, order, now, profileFirst, report)
            }
            checkDashboardV2(main, order, ::test)
            DriverOrderDetailChecks(this).run(main, ::test)
        } catch (e: Throwable) { fail++; report.append("ERROR setup: $e\n") }
        finally { runOnMainSync { scope.cancel(); activity?.finish() } }
        report.append("RESULT: PASS=$pass FAIL=$fail\n")
        finish(if (fail == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
    }
    private fun captureVisualReview() {
        // Screenshots render the real production Composables. All input below is test-only.
        val report = StringBuilder("FIXTURE screenshots; no Backend, GPS upload, or database writes.\n")
        var pass = 0
        var fail = 0
        var main: MainActivity? = null
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            main = activity
            val now = LocalDateTime.of(2026, 11, 8, 10, 24)
            val gps = DriverGpsSnapshot(DriverGpsStatus.READY,
                DriverLocationFix(10.77, 106.69, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), 10f))
            val base = DriverDashboardUiState(profile = DriverProfile(fullName = "Tài xế minh họa", status = "APPROVED", isOnline = true),
                orderKnowledge = DriverOrderKnowledge.IDLE, gps = gps)
            val order = DriverCurrentOrder(990201, "UI02-TEST-01", "PLACED", now, now, 990201,
                "Quán ăn minh họa", "Điểm lấy hàng thử nghiệm", null, 10.77, 106.69,
                "Người nhận thử nghiệm", "Liên hệ thử nghiệm", "Điểm giao hàng thử nghiệm, TP. Hồ Chí Minh", 10.78, 106.70, null)
            val session = DriverAuthUiState(phase = DriverAuthPhase.AUTHENTICATED,
                user = DriverAuthUser(990200, "Tài xế minh họa", "DRIVER", "ACTIVE"))
            val idle = DriverCurrentOrderUiState(verified = true, ownerId = session.user!!.id)
            val active = DriverCurrentOrderUiState(order = order, verified = true, ownerId = session.user!!.id)
            val error = "Không thể cập nhật. Vui lòng kiểm tra kết nối và thử lại."
            val unavailable = DriverCurrentOrderUiState(error = error)
            val offer = DriverOffer(990202, 990202, "UI02-TEST-02", order.restaurantName, order.restaurantAddress,
                10.77, 106.69, order.deliveryAddress, 1.2, now, now.plusSeconds(30))
            data class Shot(val name: String, val dashboard: DriverDashboardUiState,
                val current: DriverCurrentOrderUiState, val showOffer: Boolean = false)
            val cases = if (policySnapshots) {
                val policy = base.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE,
                    message = "Đang thực hiện đơn hàng. Không tự tắt nhận đơn; hãy kiểm tra GPS/kết nối.",
                    messageIsError = true, messageFromDeliveryPolicy = true)
                listOf(
                    Shot("01-active-healthy", policy, active),
                    Shot("02-active-network-error", policy.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN,
                        statusUnknown = true), active.copy(verified = false, error = error)),
                    Shot("03-active-gps-error", policy.copy(gps = DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)), active),
                    Shot("04-active-network-gps-error", policy.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN,
                        statusUnknown = true, gps = DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)),
                        active.copy(verified = false, error = error)),
                )
            } else listOf(
                Shot("01-ready-no-order", base, idle),
                Shot("02-new-offer", base, idle, true),
                Shot("03-active-order", base.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE), active),
                Shot("04-offline-no-order", base.copy(profile = base.profile!!.copy(isOnline = false)), idle),
                Shot("05-online-network-error", base.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN,
                    statusUnknown = true, messageIsError = true), unavailable),
                Shot("06-online-gps-error", base.copy(gps = DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)), idle),
                Shot("07-offline-network-error", base.copy(profile = base.profile!!.copy(isOnline = false),
                    orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true, messageIsError = true), unavailable),
                // Production bridge uses UNKNOWN after a failed GET; retain only the same-owner cached order.
                Shot("08-active-order-network-gps-error", base.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN,
                    statusUnknown = true, messageIsError = true, gps = DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)),
                    active.copy(verified = false, error = error)),
            )
            for (shot in cases) {
                try {
                    val marker = "Visual fixture ${shot.name}"
                    runOnMainSync {
                        activity.setContent {
                            key(marker) {
                                FoodExpressTheme {
                                    Box(Modifier.fillMaxSize().semantics { contentDescription = marker }) {
                                        DriverDashboardScreen(shot.dashboard, {}, {}, {}, {}, {},
                                            currentOrderContent = { DriverCurrentOrderCard(shot.current, {}, {},
                                                receivingOrdersOfflineConfirmed = confirmedOfflineForCurrentOrder(
                                                    session, shot.dashboard, shot.current, DriverOffersUiState())) },
                                            offerContent = { if (shot.showOffer) DriverOfferDialog(
                                                DriverOffersUiState(offer = offer, remainingSeconds = 30, phase = DriverOfferPhase.OFFER), {}, {}, {}) })
                                    }
                                }
                            }
                        }
                    }
                    await(marker) {
                        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
                        if (shot.showOffer) text("Đơn ${offer.orderCode}") != null
                        else nodes().any { it.contentDescription?.toString() == marker } && text("Đơn hàng hiện tại") != null
                    }
                    waitForIdleSync()
                    val tree = nodes()
                    val toggle = tree.firstOrNull { it.isCheckable }
                    if (!shot.showOffer) {
                        check(toggle?.isChecked == (shot.dashboard.profile?.isOnline == true))
                        if (shot.dashboard.orderKnowledge != DriverOrderKnowledge.IDLE) check(toggle?.isEnabled == false)
                        if (!shot.current.verified && shot.current.order == null) check(text("Chưa có đơn hàng") == null)
                    } else check(text("Nhận đơn") != null && text("Từ chối") != null)
                    val directory = java.io.File(targetContext.cacheDir, "dashboard-v2-visual-review").apply { mkdirs() }
                    val bitmap = requireNotNull(uiAutomation.takeScreenshot())
                    java.io.File(directory, "${shot.name}.png").outputStream().use {
                        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    bitmap.recycle()
                    // Also capture the bottom of large-font layouts without replacing the required full viewport PNG.
                    val bottomLabel = when {
                        shot.showOffer -> "Nhận đơn"
                        !shot.current.verified -> "Thử cập nhật lại"
                        shot.current.order != null -> "Xem chi tiết đơn hàng"
                        confirmedOfflineForCurrentOrder(session, shot.dashboard, shot.current, DriverOffersUiState()) ->
                            "Bạn đang tạm ngưng nhận đơn. Bật lại khi muốn nhận đơn mới."
                        else -> "Lời mời mới sẽ hiển thị khi bạn đủ điều kiện nhận đơn."
                    }
                    await("visible bottom content: ${shot.name}") {
                        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
                        val fresh = nodes()
                        if (fresh.any { it.text?.toString() == bottomLabel && it.isVisibleToUser }) true
                        else {
                            fresh.firstOrNull { node -> node.isScrollable && node.actionList.any {
                                it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            false
                        }
                    }
                    // A partially visible label is not sufficient evidence of the card's bottom.
                    // Wait for accessibility scroll events to settle, rather than adding a sleep.
                    for (step in 0 until 12) {
                        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
                        val scroll = nodes().firstOrNull { node -> node.isScrollable && node.actionList.any {
                            it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                        if (scroll?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) break
                        uiAutomation.waitForIdle(120, 3_000)
                    }
                    waitForIdleSync()
                    if (!shot.showOffer && (!shot.current.verified || shot.current.order != null)) {
                        if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
                        fun actionOrSelectedTab(label: String, selectedTab: Boolean = false): AccessibilityNodeInfo {
                            var node = requireNotNull(text(label)) { "Missing action/tab label: $label" }
                            while (!node.isClickable && !(selectedTab && node.isSelected))
                                node = requireNotNull(node.parent) { "Missing actionable/selected ancestor: $label" }
                            return node
                        }
                        val buttonBounds = android.graphics.Rect()
                        val navBounds = android.graphics.Rect()
                        actionOrSelectedTab(bottomLabel).getBoundsInScreen(buttonBounds)
                        // Accessibility exposes the current tab as selected; it need not be clickable.
                        val nav = actionOrSelectedTab("Trang chủ", selectedTab = true)
                        nav.getBoundsInScreen(navBounds)
                        report.append("TRACE ${shot.name} action=$buttonBounds nav=$navBounds selected=${nav.isSelected} clickable=${nav.isClickable}\n")
                        check(buttonBounds.height() > 0 && buttonBounds.bottom <= navBounds.top) {
                            "Bottom action obscured: ${shot.name}, action=$buttonBounds nav=$navBounds"
                        }
                    }
                    val bottom = requireNotNull(uiAutomation.takeScreenshot())
                    java.io.File(directory, "${shot.name}-bottom.png").outputStream().use {
                        check(bottom.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                    }
                    bottom.recycle()
                    pass++; report.append("PASS ${shot.name} FIXTURE fontScale=${targetContext.resources.configuration.fontScale}\n")
                } catch (e: Throwable) { fail++; report.append("FAIL ${shot.name}: $e\n") }
            }
        } catch (e: Throwable) { fail++; report.append("ERROR visual setup: $e\n") }
        finally { runOnMainSync { main?.finish() } }
        report.append("RESULT: PASS=$pass FAIL=$fail\n")
        finish(if (fail == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.toString()) })
    }
    private fun checkDeliveryRecovery(main: MainActivity, order: DriverCurrentOrder, now: LocalDateTime,
        profileFirst: Boolean, report: StringBuilder) {
        // Isolated AVD only: fake session/HTTP, real Auth/Dashboard/CurrentOrder/Offers VMs.
        // Device-protected fixture store is separate from the app's real session store.
        val user = DriverAuthUser(999002, "FAKE Recovery Driver", "DRIVER", "ACTIVE")
        fun encoded(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val fakeToken = encoded("{}") + "." + encoded("{\"exp\":${System.currentTimeMillis() / 1000 + 3600}}") + ".fixture"
        // Do not let instant fake HTTP authenticate before the lifecycle/bridge is installed.
        var authGate: CompletableDeferred<Unit>? = CompletableDeferred()
        val bridgeReady = CompletableDeferred<Unit>()
        val authRepo = DriverAuthRepository(object : DriverAuthApi {
            override suspend fun register(request: DriverRegisterRequest): Response<DriverRegisterResponse> = error("No registration")
            override suspend fun login(request: DriverLoginRequest) = Response.success(DriverLoginResponse(fakeToken, user))
            override suspend fun currentUser(authorization: String): Response<DriverAuthUser> {
                authGate?.await(); return Response.success(user)
            }
        }, DriverSessionStore(targetContext.createDeviceProtectedStorageContext()))
        check(runBlocking { authRepo.login("FAKE", "fixture-only", false) } is DriverAuthResult.Authenticated)
        var profileGate: CompletableDeferred<Unit>? = null
        var orderGate: CompletableDeferred<Unit>? = null
        val profileCalls = AtomicInteger(); val orderCalls = AtomicInteger(); val offerCalls = AtomicInteger(); val patches = AtomicInteger()
        val profiles = DriverProfileRepository(object : DriverProfileApi {
            override suspend fun getProfile(authorization: String): Response<DriverProfile> {
                profileCalls.incrementAndGet(); profileGate?.await()
                return Response.success(DriverProfile(fullName = "FAKE Recovery Driver", phone = "0999999999",
                    vehicleType = "CAR", status = "APPROVED", isOnline = true))
            }
            override suspend fun updateProfile(authorization: String, request: DriverProfileUpdate): Response<DriverProfile> = error("No profile mutation")
        }, authRepo)
        val statuses = DriverStatusRepository(object : DriverStatusApi {
            override suspend fun updateStatus(authorization: String, request: DriverStatusRequest): Response<DriverStatusResponse> {
                patches.incrementAndGet(); error("Unexpected PATCH in recovery test")
            }
        }, authRepo)
        val locations = DriverLocationRepository(object : DriverLocationApi {
            override suspend fun recordLocation(authorization: String, request: DriverLocationRequest) =
                Response.success(201, DriverLocationResponse(request.lat, request.lng, now.toString()))
        }, authRepo)
        val models = ViewModelStore()
        lateinit var auth: DriverAuthViewModel
        lateinit var dashboard: DriverDashboardViewModel
        lateinit var current: DriverCurrentOrderViewModel
        lateinit var offers: DriverOffersViewModel
        val stops = AtomicInteger(); val starts = AtomicInteger(); val refreshes = AtomicInteger()
        try {
            runOnMainSync {
                auth = DriverAuthViewModel(authRepo)
                dashboard = DriverDashboardViewModel(profiles, statuses, locations, DriverForegroundLocationSource(targetContext))
                offers = DriverOffersViewModel(object : DriverOffersSource {
                    override suspend fun load(): OffersResult {
                        offerCalls.incrementAndGet(); return OffersResult.Loaded(emptyList(), now, SystemClock.elapsedRealtime())
                    }
                    override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult = error("No decisions")
                }, OfferMonotonicClock { SystemClock.elapsedRealtime() }, SavedStateHandle())
                current = DriverCurrentOrderViewModel(DriverCurrentOrderSource {
                    orderCalls.incrementAndGet(); orderGate?.await(); CurrentOrderResult.Loaded(order, now)
                })
                models.put("auth", auth); models.put("dashboard", dashboard); models.put("current", current); models.put("offers", offers)
                current.connectOffers(offers)
                fun syncKnowledge() {
                    val session = auth.state.value
                    val id = session.user?.id?.takeIf { session.phase == DriverAuthPhase.AUTHENTICATED } ?: return
                    val knowledge = current.state.value
                    dashboard.updateDeliveryKnowledge(id, when {
                        knowledge.ownerId != id || !knowledge.verified -> DriverOrderKnowledge.UNKNOWN
                        knowledge.order != null -> DriverOrderKnowledge.ACTIVE
                        else -> DriverOrderKnowledge.IDLE
                    }, offers.state.value.pending?.action == OfferAction.ACCEPT || offers.state.value.acceptedOrderId != null)
                }
                main.setContent {
                    val session by auth.state.collectAsStateWithLifecycle()
                    val dash by dashboard.state.collectAsStateWithLifecycle()
                    val orderState by current.state.collectAsStateWithLifecycle()
                    val offerState by offers.state.collectAsStateWithLifecycle()
                    // Match DriverAppNavigation's two bridges, including Auth/owner keys.
                    // A -> unverified -> equal A need not emit every intermediate value.
                    LaunchedEffect(session.phase, session.user?.id) {
                        when (session.phase) {
                            DriverAuthPhase.AUTHENTICATED -> session.user?.id?.let { id ->
                                offers.activateSession(id); current.activateSession(id); dashboard.activateSession(id); syncKnowledge()
                            }
                            DriverAuthPhase.SIGNED_OUT -> { dashboard.clear(); current.clear(); offers.clear() }
                            else -> {
                                dashboard.pauseSessionVerification(); current.pauseSessionVerification(); offers.pauseSessionVerification()
                                bridgeReady.complete(Unit)
                            }
                        }
                    }
                    LaunchedEffect(session.phase, session.user?.id, orderState.ownerId, orderState.verified,
                        orderState.order?.orderId, offerState.pending, offerState.acceptedOrderId) { syncKnowledge() }
                    val owner = LocalLifecycleOwner.current
                    // setContent reuses the ComposeView across fixtures; detach the previous fixture's observer.
                    DisposableEffect(owner, dashboard, current, offers, auth) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> {
                                    starts.incrementAndGet(); current.setForeground(true); syncKnowledge(); dashboard.setForeground(true); offers.setForeground(true)
                                }
                                Lifecycle.Event.ON_RESUME -> auth.onResume()
                                Lifecycle.Event.ON_STOP -> {
                                    syncKnowledge(); stops.incrementAndGet(); current.setForeground(false); dashboard.setForeground(false); offers.setForeground(false)
                                }
                                else -> Unit
                            }
                        }
                        owner.lifecycle.addObserver(observer)
                        onDispose { owner.lifecycle.removeObserver(observer) }
                    }
                    FoodExpressTheme { DriverDashboardScreen(dash,
                        onRetry = { refreshes.incrementAndGet(); dashboard.load(); current.refresh() },
                        onReceivingOrders = dashboard::changeReceivingOrders, onProfile = {}, onLogout = {}, onGpsRetry = {},
                        currentOrderContent = { DriverCurrentOrderCard(orderState, current::refresh) }, orderRefreshing = orderState.loading) }
                }
            }
            await("initial lifecycle and Auth bridge ready") {
                starts.get() > 0 && bridgeReady.isCompleted && auth.state.value.phase == DriverAuthPhase.CHECKING
            }
            runOnMainSync { authGate!!.complete(Unit) }
            try {
                await("initial ACTIVE, profileFirst=$profileFirst") { dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE && text("Đang thực hiện đơn") != null }
            } catch (error: Exception) {
                throw IllegalStateException("Initial recovery fixture: auth=${auth.state.value.phase}, owner=${current.state.value.ownerId}, verified=${current.state.value.verified}, knowledge=${dashboard.state.value.orderKnowledge}, profileLoaded=${dashboard.state.value.profile != null}", error)
            }
            val beforeStops = stops.get(); val beforeStarts = starts.get()
            runOnMainSync { check(main.moveTaskToBack(true)) }
            await("native ON_STOP, profileFirst=$profileFirst") { stops.get() > beforeStops }
            runOnMainSync {
                authGate = CompletableDeferred(); profileGate = CompletableDeferred(); orderGate = CompletableDeferred()
            }
            targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            await("foreground Auth CHECKING and bridge paused, profileFirst=$profileFirst") {
                starts.get() > beforeStarts && auth.state.value.phase == DriverAuthPhase.CHECKING &&
                    current.state.value.ownerId == null && dashboard.state.value.orderKnowledge == DriverOrderKnowledge.UNKNOWN
            }
            runOnMainSync { authGate!!.complete(Unit) }
            await("authenticated GETs started, profileFirst=$profileFirst") { auth.state.value.phase == DriverAuthPhase.AUTHENTICATED && current.state.value.loading && dashboard.state.value.loading }
            if (profileFirst) {
                runOnMainSync { profileGate!!.complete(Unit) }
                await("profile completed before Current Order") { !dashboard.state.value.loading }
                check(current.state.value.loading && !current.state.value.verified && !orderGate!!.isCompleted)
                // Profile recheck publishes the production notice while Current Order GET is delayed.
                await("UNKNOWN notice before delayed Current Order") { text("Chưa xác minh được đơn hàng; giữ nguyên trạng thái nhận đơn.") != null }
                runOnMainSync { orderGate!!.complete(Unit) }
            } else {
                runOnMainSync { orderGate!!.complete(Unit) }
                await("Current Order completed before profile") { dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE }
                check(dashboard.state.value.loading && current.state.value.ownerId == user.id && !profileGate!!.isCompleted)
                runOnMainSync { profileGate!!.complete(Unit) }
            }
            await("final ACTIVE and card, profileFirst=$profileFirst") { text("Đang thực hiện đơn") != null && text("Đơn ${order.orderCode}") != null && !dashboard.state.value.loading }
            check(dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE && current.state.value.verified && current.state.value.ownerId == user.id)
            check(text("Chưa xác minh được đơn hàng; giữ nguyên trạng thái nhận đơn.") == null)
            check(refreshes.get() == 0 && patches.get() == 0 && offerCalls.get() == 0)
            check(stops.get() > beforeStops && starts.get() > beforeStarts)
            report.append("TRACE HOTFIX profileFirst=$profileFirst background/foreground=verified responseOrder=verified owner=verified currentGET=${orderCalls.get()} profileGET=${profileCalls.get()} refresh=0 PATCH=0 offersGET=0\n")
        } finally {
            runOnMainSync { main.setContent { }; models.clear() }
        }
    }
    private fun checkDashboardV2(main: MainActivity, order: DriverCurrentOrder, test: (String, () -> Unit) -> Unit) {
        // Independent presentation fixtures. No Auth, GPS sensor, HTTP or business-state writes.
        val readyGps = DriverGpsSnapshot(DriverGpsStatus.READY,
            DriverLocationFix(10.0, 106.0, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), 10f))
        val base = DriverDashboardUiState(profile = DriverProfile(fullName = "FAKE V2 Driver", status = "APPROVED", isOnline = true),
            orderKnowledge = DriverOrderKnowledge.IDLE, gps = readyGps)
        val session = DriverAuthUiState(phase = DriverAuthPhase.AUTHENTICATED,
            user = DriverAuthUser(990200, "FAKE V2 Driver", "DRIVER", "ACTIVE"))
        val empty = DriverCurrentOrderUiState(verified = true, ownerId = session.user!!.id)
        var requested: Boolean? = null
        var retries = 0
        var profiles = 0
        fun snapshot(): List<AccessibilityNodeInfo> {
            if (android.os.Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
            return nodes()
        }
        fun render(name: String, value: DriverDashboardUiState, current: DriverCurrentOrderUiState = empty, updatingOrder: Boolean = false) {
            requested = null; retries = 0; profiles = 0
            val marker = "V2 fixture: $name"
            runOnMainSync {
                main.setContent {
                    key(marker) {
                        Box(Modifier.fillMaxSize().semantics { contentDescription = marker }) {
                            FoodExpressTheme {
                                DriverDashboardScreen(value, { retries++ }, { requested = it }, { profiles++ }, {}, {},
                                    currentOrderContent = { DriverCurrentOrderCard(current, { retries++ },
                                        receivingOrdersOfflineConfirmed = confirmedOfflineForCurrentOrder(session, value, current, DriverOffersUiState())) },
                                    orderRefreshing = updatingOrder)
                            }
                        }
                    }
                }
            }
            await(marker) { snapshot().any { it.contentDescription?.toString() == marker } }
        }
        fun reveal(label: String) {
            await("V2 label $label") {
                val tree = snapshot()
                if (tree.any { it.text?.toString() == label && it.isVisibleToUser }) true
                else {
                    tree.firstOrNull { it.isScrollable && it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    false
                }
            }
        }
        fun toggle() = snapshot().first { it.isCheckable }
        fun capture(name: String) {
            uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(targetContext.cacheDir, "dashboard-v2-$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        test("V2 confirmed ready shows location and cautious connection copy without fake statistics") {
            render("ready", base)
            reveal("Sẵn sàng nhận đơn")
            check(toggle().isChecked && toggle().isEnabled)
            check(text("Vị trí") != null && text("Kết nối") != null && text("Đã xác định") != null)
            check(text("Đã kết nối") == null && text("Tổng quan hôm nay") == null)
            capture("ready")
            reveal("Chưa có đơn hàng")
            check(text("Đơn ${order.orderCode}") == null && requested == null)
        }
        test("V2 confirmed OFF keeps switch OFF until caller confirms a new value") {
            render("off", base.copy(profile = base.profile!!.copy(isOnline = false)))
            reveal("Tạm ngưng nhận đơn")
            check(!toggle().isChecked && toggle().isEnabled)
            check(toggle().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { requested == true }
            check(!toggle().isChecked)
        }
        test("V2 pending Accept is locked and never shown as an active order") {
            render("accept", base.copy(acceptPending = true, orderKnowledge = DriverOrderKnowledge.UNKNOWN),
                DriverCurrentOrderUiState())
            reveal("Đang xác nhận nhận đơn")
            check(!toggle().isEnabled && requested == null && text("Đang thực hiện đơn") == null)
            reveal("Chưa xác minh được đơn hàng")
            check(text("Chưa có đơn hàng") == null)
        }
        test("V2 assigned PLACED is active without claiming delivery progress or fake amounts") {
            render("active", base.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE),
                DriverCurrentOrderUiState(order = order, verified = true))
            reveal("Đang thực hiện đơn")
            check(!toggle().isEnabled && requested == null)
            capture("active")
            reveal("Đơn ${order.orderCode}")
            check(text("Trạng thái: Đã nhận đơn") != null && text("Trạng thái: PLACED") == null && text("Đang giao") == null)
            reveal(order.restaurantName)
            reveal(order.deliveryAddress)
            check(text(order.restaurantAddress) == null) // Full pickup address remains in Details.
            check(text("COD") == null && text("ETA") == null)
        }
        test("V2 unapproved profiles remain unable to enable receiving") {
            for (approval in listOf("PENDING", "REJECTED", "SUSPENDED")) {
                render("profile-$approval", base.copy(profile = base.profile!!.copy(isOnline = false, status = approval)))
                reveal("Chưa thể nhận đơn")
                check(!toggle().isChecked && !toggle().isEnabled && requested == null)
            }
        }
        test("V2 GPS loss keeps last confirmed switch value and exposes permission guidance") {
            render("gps", base.copy(gps = DriverGpsSnapshot(DriverGpsStatus.NO_PERMISSION)))
            reveal("Vị trí chưa sẵn sàng")
            check(toggle().isChecked && text("Sẵn sàng nhận đơn") == null && requested == null)
            reveal("Cấp quyền vị trí")
        }
        test("V2 OFF with disabled location never auto-enables receiving") {
            render("off-gps", base.copy(profile = base.profile!!.copy(isOnline = false), gps = DriverGpsSnapshot(DriverGpsStatus.SERVICES_OFF)))
            reveal("Tạm ngưng nhận đơn")
            check(!toggle().isChecked && !toggle().isEnabled && requested == null)
            reveal("Bật vị trí")
        }
        test("V2 failed status update is unconfirmed rather than ready or an Internet diagnosis") {
            render("connection", base.copy(statusUnknown = true, messageIsError = true, message = "FAKE update failed"))
            reveal("Chưa xác nhận trạng thái")
            check(toggle().isChecked && text("Sẵn sàng nhận đơn") == null && requested == null)
            reveal("Không thể cập nhật")
            check(text("Không thể kết nối") == null)
            click("Thử lại"); check(retries == 1)
        }
        test("V2 unknown current order cannot display confirmed empty data") {
            render("unknown", base.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN),
                DriverCurrentOrderUiState(error = "FAKE current-order error"))
            reveal("Chưa xác minh được đơn hàng")
            check(!toggle().isEnabled && requested == null && text("Chưa có đơn hàng") == null)
            reveal("FAKE current-order error")
        }
        test("V2 retained order during update failure stays labelled stale and locked") {
            render("stale", base.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true,
                gps = DriverGpsSnapshot(DriverGpsStatus.ERROR)),
                DriverCurrentOrderUiState(order = order, verified = false, error = "FAKE retained-order error"))
            check(!toggle().isEnabled && requested == null)
            reveal("Đơn ${order.orderCode}")
            reveal("Thông tin đơn từ lần cập nhật trước. FAKE retained-order error")
            check(nodes().count { it.text?.toString()?.contains("FAKE retained-order error") == true } == 1)
            check(text("Chưa có đơn hàng") == null)
        }
        test("UI022 unknown last ON and last OFF keep switch snapshot, never claim an idle driver") {
            for (lastOnline in listOf(true, false)) {
                render("unknown-status-$lastOnline", base.copy(profile = base.profile!!.copy(isOnline = lastOnline),
                    orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true),
                    DriverCurrentOrderUiState(error = "Chưa cập nhật được đơn hiện tại."))
                reveal("Chưa xác nhận trạng thái")
                check(toggle().isChecked == lastOnline && !toggle().isEnabled && requested == null)
                reveal("Chưa cập nhật được đơn hiện tại.")
                check(text("Chưa có đơn hàng") == null && text("Tạm ngưng nhận đơn") == null && text("Sẵn sàng nhận đơn") == null)
            }
        }
        test("UI022 confirmed OFF changes only the verified empty-order description") {
            render("confirmed-off-copy", base.copy(profile = base.profile!!.copy(isOnline = false)))
            reveal("Bạn đang tạm ngưng nhận đơn. Bật lại khi muốn nhận đơn mới.")
            check(!toggle().isChecked && toggle().isEnabled && requested == null)
            check(text("Lời mời mới sẽ hiển thị khi bạn đủ điều kiện nhận đơn.") == null)
            render("confirmed-on-copy", base)
            reveal("Lời mời mới sẽ hiển thị khi bạn đủ điều kiện nhận đơn.")
            check(text("Bạn đang tạm ngưng nhận đơn. Bật lại khi muốn nhận đơn mới.") == null)
        }
        test("UI022 production OFF presentation gate rejects unverified states, pending Accept and a different owner") {
            val off = base.copy(profile = base.profile!!.copy(isOnline = false))
            val noOffer = DriverOffersUiState()
            fun confirmed(auth: DriverAuthUiState = session, dashboard: DriverDashboardUiState = off,
                current: DriverCurrentOrderUiState = empty, offer: DriverOffersUiState = noOffer) =
                confirmedOfflineForCurrentOrder(auth, dashboard, current, offer)
            check(confirmed())
            for (dashboard in listOf(off.copy(statusUnknown = true), off.copy(loading = true), off.copy(saving = true),
                off.copy(enablingOnline = true), off.copy(sessionExpired = true), off.copy(messageIsError = true),
                off.copy(offlineConflict = true), off.copy(acceptPending = true), off.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN),
                off.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE), base)) check(!confirmed(dashboard = dashboard))
            for (current in listOf(empty.copy(verified = false), empty.copy(loading = true), empty.copy(error = "FAKE timeout"),
                empty.copy(sessionExpired = true), empty.copy(order = order), empty.copy(ownerId = 990201))) check(!confirmed(current = current))
            check(!confirmed(auth = session.copy(phase = DriverAuthPhase.CHECKING)))
            check(!confirmed(auth = session.copy(phase = DriverAuthPhase.SIGNED_OUT, user = null)))
            check(!confirmed(auth = session.copy(user = session.user!!.copy(id = 990201))))
            check(!confirmed(offer = noOffer.copy(pending = PendingOfferAction(990202, order.orderId, OfferAction.ACCEPT))))
            check(!confirmed(offer = noOffer.copy(acceptedOrderId = order.orderId)))
            check(!confirmed(offer = noOffer.copy(phase = DriverOfferPhase.SENDING_ACCEPT)))
            check(!confirmed(offer = noOffer.copy(sessionExpired = true)))
            // Same owner filter as Navigation: the previous session cannot lend its verified null to the new one.
            val next = session.copy(user = session.user!!.copy(id = 990201))
            val filtered = empty.forDriver(next.user!!.id)
            check(!confirmed(auth = next, current = filtered) && !filtered.verified)
            render("owner-switched-off", off, filtered)
            reveal("Chưa xác minh được đơn hàng")
            check(text("Bạn đang tạm ngưng nhận đơn. Bật lại khi muốn nhận đơn mới.") == null)
        }
        test("UI022 current-order business errors remain visible once without an Internet diagnosis") {
            val reason = "Máy chủ phát hiện nhiều đơn hoạt động. Vui lòng liên hệ hỗ trợ."
            render("business-error", base.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true),
                DriverCurrentOrderUiState(error = reason))
            reveal(reason)
            check(snapshot().count { it.text?.toString() == reason } == 1)
            check(text("Không có kết nối Internet") == null && text("Chưa có đơn hàng") == null)
        }
        test("UI022 statuses other than PLACED retain the actual value rather than invented progress") {
            render("other-status", base.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE),
                DriverCurrentOrderUiState(order = order.copy(status = "PREPARING"), verified = true))
            reveal("Trạng thái: Nhà hàng đang chuẩn bị món")
            check(text("Trạng thái: PREPARING") == null)
            check(!toggle().isEnabled && text("Trạng thái: Đã nhận đơn") == null && text("Đang giao") == null)
        }
        val policyMessage = "Đang thực hiện đơn hàng. Không tự tắt nhận đơn; hãy kiểm tra GPS/kết nối."
        val activeNotice = "Bạn đang thực hiện đơn. Hãy kiểm tra vị trí và kết nối khi cần."
        val active = base.copy(orderKnowledge = DriverOrderKnowledge.ACTIVE, message = policyMessage,
            messageIsError = true, messageFromDeliveryPolicy = true)
        val currentActive = DriverCurrentOrderUiState(order = order, verified = true, ownerId = session.user!!.id)
        test("UI023 healthy ACTIVE hides only routine notice, keeps order and locked switch independently of Online") {
            for (online in listOf(true, false)) {
                render("healthy-policy-$online", active.copy(profile = active.profile!!.copy(isOnline = online)), currentActive)
                reveal("Đang thực hiện đơn")
                check(text(activeNotice) == null && !toggle().isEnabled && toggle().isChecked == online && requested == null)
                reveal("Đơn ${order.orderCode}")
                check(text("Trạng thái: Đã nhận đơn") != null && text(order.restaurantName) != null)
            }
        }
        test("UI023 verified ACTIVE background GET keeps indicator without flashing a routine error notice") {
            render("policy-current-refresh", active, currentActive.copy(loading = true), updatingOrder = true)
            reveal("Đang thực hiện đơn")
            check(text(activeNotice) == null && refreshing() && !toggle().isEnabled && requested == null)
        }
        test("UI023 every non-ready GPS state retains the policy notice and active-order protection") {
            for (gps in DriverGpsStatus.entries.filter { it != DriverGpsStatus.READY }) {
                render("policy-gps-$gps", active.copy(gps = DriverGpsSnapshot(gps)), currentActive)
                reveal(activeNotice)
                check(text("Đã xác định") == null && !toggle().isEnabled && requested == null)
            }
        }
        test("UI023 unconfirmed status, processing, suspension, conflict and pending keep their notices") {
            val values = listOf(active.copy(statusUnknown = true), active.copy(loading = true),
                active.copy(saving = true), active.copy(enablingOnline = true), active.copy(sessionExpired = true),
                active.copy(offlineConflict = true), active.copy(profile = active.profile!!.copy(status = "SUSPENDED")),
                active.copy(acceptPending = true))
            values.forEachIndexed { index, value ->
                render("policy-unconfirmed-$index", value, currentActive)
                reveal(if (value.acceptPending) "Yêu cầu nhận đơn đang được xác minh; giữ nguyên trạng thái nhận đơn." else activeNotice)
                // The existing sessionExpired branch shows verification instead of a status switch.
                if (value.sessionExpired) check(snapshot().none { it.isCheckable })
                else check(!toggle().isEnabled)
                check(requested == null)
            }
        }
        test("UI023 real GPS, transport and business errors remain visible even if other ACTIVE fields look healthy") {
            for ((index, reason) in listOf("Không thể gửi vị trí. Vui lòng thử lại.",
                "Không thể cập nhật dữ liệu. Vui lòng kiểm tra kết nối.",
                "Máy chủ chưa cho phép Offline. Vui lòng đồng bộ đơn và hồ sơ.").withIndex()) {
                render("policy-real-error-$index", active.copy(message = reason, messageFromDeliveryPolicy = false), currentActive)
                reveal(reason)
                check(text(activeNotice) == null && !toggle().isEnabled && requested == null)
            }
        }
        test("UI023 IDLE ON/OFF and UNKNOWN retain existing messages and correctly verified order data") {
            for (online in listOf(true, false)) {
                val info = "Đã cập nhật trạng thái nhận đơn."
                render("policy-idle-$online", base.copy(profile = base.profile!!.copy(isOnline = online), message = info), empty)
                reveal(info); reveal("Chưa có đơn hàng")
                check(toggle().isChecked == online && requested == null)
                val reason = "Chưa kiểm tra được thay đổi của đơn."
                render("policy-unknown-$online", active.copy(profile = active.profile!!.copy(isOnline = online),
                    orderKnowledge = DriverOrderKnowledge.UNKNOWN, statusUnknown = true),
                    currentActive.copy(verified = false, error = reason))
                reveal("Chưa xác minh được đơn hàng; giữ nguyên trạng thái nhận đơn.")
                check(!toggle().isEnabled && text("Chưa có đơn hàng") == null && requested == null)
                reveal("Thông tin đơn từ lần cập nhật trước. $reason")
            }
        }
        test("V2 updating preserves loading guards without claiming a successful refresh") {
            render("updating", base.copy(saving = true), updatingOrder = true)
            reveal("Đang cập nhật")
            check(!toggle().isEnabled && refreshing() && retries == 0 && requested == null)
        }
        test("V2 retains the four original tabs and the profile callback") {
            render("tabs", base)
            for (label in listOf("Trang chủ", "Đơn hàng", "Thu nhập", "Hồ sơ")) check(text(label) != null)
            for (label in listOf("Đơn hàng", "Thu nhập")) {
                var node = requireNotNull(text(label))
                while (!node.isClickable) node = requireNotNull(node.parent)
                check(!node.isEnabled)
            }
            click("Hồ sơ"); check(profiles == 1 && requested == null)
        }
    }
    private fun await(label: String = "Compose state", condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; SystemClock.sleep(100) }
        error("Timed out waiting for $label")
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) { result.add(node); for (i in 0 until node.childCount) node.getChild(i)?.let(::visit) }
        uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun text(value: String) = nodes().firstOrNull { it.text?.toString() == value }
    private fun refreshing() = nodes().any { it.contentDescription?.toString() == "Đang làm mới Dashboard" }
    private fun click(label: String) {
        var node = requireNotNull(text(label))
        while (!node.isClickable) node = requireNotNull(node.parent)
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun pull() {
        val metrics = targetContext.resources.displayMetrics
        val x = metrics.widthPixels * .5f
        val start = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val motion = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(motion, true)); motion.recycle()
        }
        val from = metrics.heightPixels * .16f
        val to = metrics.heightPixels * .72f
        event(MotionEvent.ACTION_DOWN, from)
        repeat(30) { SystemClock.sleep(20); event(MotionEvent.ACTION_MOVE, from + (to - from) * (it + 1) / 30) }
        event(MotionEvent.ACTION_UP, to)
    }
}
