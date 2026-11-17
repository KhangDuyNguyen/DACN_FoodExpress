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
import androidx.compose.ui.Modifier
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
import vn.hutech.foodexpress.driver.ui.order.*

/** Run on an isolated AVD only. All account/order/API data in this runner is FAKE. */
class CurrentOrderUiInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
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
                await { text("Đơn FAKE-4B2-99301") != null && text("Trạng thái: PLACED") != null }
                check(text("FAKE Restaurant") != null && text("Địa chỉ giao: FAKE Delivery") != null)
                check(text("COD") == null && text("ETA") == null)
            }
            test("Sync error labels retained data and offers retry") {
                runOnMainSync { state.value = state.value.copy(verified = false, error = "FAKE sync failure") }
                await { text("FAKE sync failure") != null }
                check(text("Dữ liệu lần đồng bộ trước; đang cần xác minh lại.") != null)
                click("Thử đồng bộ lại"); check(retries.get() == 1)
            }
            test("Explicit null removes previous order and shows neutral idle") {
                runOnMainSync { state.value = DriverCurrentOrderUiState(verified = true) }
                await { text("Không có đơn đang thực hiện") != null }
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
                await { text("Đang thực hiện đơn hàng") != null }
                val toggle = nodes().first { it.isCheckable }
                check(toggle.isChecked && !toggle.isEnabled && toggles.get() == 0)
                check(text("Chưa thể tắt nhận đơn khi đang thực hiện đơn hàng.") != null)
            }
            test("UNKNOWN and pending Accept do not enable a receiving switch") {
                runOnMainSync { receiving.value = receiving.value.copy(orderKnowledge = DriverOrderKnowledge.UNKNOWN) }
                await { text("Đang xác minh đơn hiện tại") != null }
                check(!nodes().first { it.isCheckable }.isEnabled)
                runOnMainSync { receiving.value = receiving.value.copy(acceptPending = true) }
                await { text("Đang xác minh Nhận đơn") != null }
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
                    click("Đăng xuất phiên giả"); await { text("Chưa xác minh được đơn hiện tại") != null }
                    check(current.state.value.order == null && text("Địa chỉ giao: FAKE Delivery") == null)
                    click("Đăng nhập lại phiên giả"); await { text("Đơn FAKE-4B2-99301") != null }
                    check(loads.get() > beforeLoads && offerLoads.get() == 0)
                }
            } finally { runOnMainSync { current.clear(); offers.clear() } }
            for (profileFirst in listOf(true, false)) test("HOTFIX real VMs/Auth lifecycle recover ACTIVE without Refresh; profileFirst=$profileFirst") {
                checkDeliveryRecovery(main, order, now, profileFirst, report)
            }
            DriverOrderDetailChecks(this).run(main, ::test)
        } catch (e: Throwable) { fail++; report.append("ERROR setup: $e\n") }
        finally { runOnMainSync { scope.cancel(); activity?.finish() } }
        report.append("RESULT: PASS=$pass FAIL=$fail\n")
        finish(if (fail == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString()) })
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
                await("initial ACTIVE, profileFirst=$profileFirst") { dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE && text("Đang thực hiện đơn hàng") != null }
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
                await("UNKNOWN notice before delayed Current Order") { text("Chưa xác minh được đơn hiện tại; không tự chuyển Offline.") != null }
                runOnMainSync { orderGate!!.complete(Unit) }
            } else {
                runOnMainSync { orderGate!!.complete(Unit) }
                await("Current Order completed before profile") { dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE }
                check(dashboard.state.value.loading && current.state.value.ownerId == user.id && !profileGate!!.isCompleted)
                runOnMainSync { profileGate!!.complete(Unit) }
            }
            await("final ACTIVE and card, profileFirst=$profileFirst") { text("Đang thực hiện đơn hàng") != null && text("Đơn ${order.orderCode}") != null && !dashboard.state.value.loading }
            check(dashboard.state.value.orderKnowledge == DriverOrderKnowledge.ACTIVE && current.state.value.verified && current.state.value.ownerId == user.id)
            check(text("Chưa xác minh được đơn hiện tại; không tự chuyển Offline.") == null)
            check(refreshes.get() == 0 && patches.get() == 0 && offerCalls.get() == 0)
            check(stops.get() > beforeStops && starts.get() > beforeStarts)
            report.append("TRACE HOTFIX profileFirst=$profileFirst background/foreground=verified responseOrder=verified owner=verified currentGET=${orderCalls.get()} profileGET=${profileCalls.get()} refresh=0 PATCH=0 offersGET=0\n")
        } finally {
            runOnMainSync { main.setContent { }; models.clear() }
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
