package vn.hutech.foodexpress.driver.report

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File
import java.math.BigDecimal
import java.time.*
import vn.hutech.foodexpress.core.ui.theme.FoodExpressTheme
import vn.hutech.foodexpress.driver.MainActivity
import vn.hutech.foodexpress.driver.data.order.DriverCurrentOrder
import vn.hutech.foodexpress.driver.data.report.*
import vn.hutech.foodexpress.driver.ui.dashboard.DriverDashboardNavigation
import vn.hutech.foodexpress.driver.ui.order.*
import vn.hutech.foodexpress.driver.ui.report.*
import vn.hutech.foodexpress.driver.ui.offer.*
import vn.hutech.foodexpress.driver.data.offer.DriverOffer

/** Production Composables/VM and one NavHost. All session/order/HTTP data is FIXTURE, not real Backend E2E. */
class DriverReportsUiInstrumentation : Instrumentation() {
    private var directory="week5d23"
    override fun onCreate(arguments: Bundle?) {
        directory=arguments?.getString("directory") ?: directory
        require(directory.matches(Regex("week5d23(?:-[a-z0-9]+)*")))
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        var passed=0; var failed=0
        val report=StringBuilder("Emulator FIXTURE; production Reports UI/VM/bottom bar and existing M06. NOT real Backend E2E.\n")
        fun test(name: String, block: () -> Unit) {
            try { block(); passed++; report.append("PASS $name\n") }
            catch (error: Throwable) { failed++; report.append("FAIL $name: $error\n${error.stackTrace.take(3).joinToString("\n")}\n") }
        }
        var model: DriverReportsViewModel?=null
        var activity: MainActivity?=null
        val fixtureScope=CoroutineScope(Dispatchers.Main.immediate+SupervisorJob())
        try {
            val main=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            activity=main
            val clock=Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"),ZoneOffset.UTC)
            val source=Source(); val vm=DriverReportsViewModel(source,clock); model=vm
            val owner=mutableStateOf<Long?>(7)
            val current=mutableStateOf(DriverCurrentOrderUiState(ownerId=7,verified=true))
            val offer=mutableStateOf(DriverOffersUiState())
            val rejectOrigin=mutableStateOf<String?>(null)
            var currentReads=0; var currentGate: CompletableDeferred<Unit>?=null; var currentFails=false
            val refreshCurrent: () -> Unit = {
                if (!current.value.loading) {
                    currentReads++; current.value=current.value.copy(loading=true)
                    fixtureScope.launch {
                        currentGate?.await(); currentGate=null
                        current.value=current.value.copy(loading=false,verified=!currentFails,
                            error=if(currentFails) "FIXTURE current GET failed" else null)
                    }
                }
            }
            var profiles=0; var patches=0
            runOnMainSync {
                vm.setForeground(true); vm.activateSession(7)
                main.setContent {
                    val state by vm.state.collectAsState()
                    val nav=rememberNavController()
                    val backStack by nav.currentBackStackEntryAsState()
                    val route=backStack?.destination?.route
                    val feedback: (@Composable () -> Unit)? = if(offer.value.message!=null && offer.value.phase!=DriverOfferPhase.REJECTED)
                        { { DriverOfferStatusCard(offer.value,{}, {}) } } else null
                    LaunchedEffect(route) { vm.show(when(route) { "earnings" -> DriverReportsPage.EARNINGS; "orders" -> DriverReportsPage.ORDERS; else -> DriverReportsPage.NONE }) }
                    val bar: @Composable (Int) -> Unit = { selected -> DriverDashboardNavigation(onProfile={ profiles++ }, selectedTab=selected,
                        onHome={ nav.navigate("home") { popUpTo("home"); launchSingleTop=true } },
                        onOrders={ nav.navigate("orders") { popUpTo("home"); launchSingleTop=true } },
                        onEarnings={ nav.navigate("earnings") { popUpTo("home"); launchSingleTop=true } }) }
                    FoodExpressTheme {
                        DriverReportsSystemBars(route in setOf("earnings","earning-detail","orders","history-detail"),backStack?.lifecycle)
                        NavHost(navController=nav,startDestination="home",enterTransition={androidx.compose.animation.EnterTransition.None},exitTransition={androidx.compose.animation.ExitTransition.None},popEnterTransition={androidx.compose.animation.EnterTransition.None},popExitTransition={androidx.compose.animation.ExitTransition.None}) {
                            composable("home") { Scaffold(bottomBar={ Column(Modifier.navigationBarsPadding()) { bar(0) } }) { padding -> Text("FIXTURE Driver session",Modifier.padding(padding)) } }
                            composable("earnings") { DriverEarningsScreen(state,owner.value,vm::selectPeriod,vm::refreshEarnings,
                                onSelect={ if(state.ownerId==owner.value && vm.selectEarning(it)) nav.navigate("earning-detail") },bottomBar={bar(2)},offerContent=feedback) }
                            composable("earning-detail") { DriverEarningDetailScreen(state,owner.value,{nav.popBackStack()},offerContent=feedback) }
                            composable("orders") { DriverOrdersScreen(state,current.value,owner.value,false,vm::selectHistory,
                                onCurrentRefresh=refreshCurrent,onHistoryRefresh=vm::refreshHistory,onMore=vm::loadMoreHistory,
                                onCurrent={ if(current.value.forDriver(owner.value).let { it.verified && it.order!=null && it.error==null }) nav.navigate("m06") },
                                onHistory={ if(state.ownerId==owner.value && vm.selectHistoryOrder(it)) nav.navigate("history-detail") },bottomBar={bar(1)},offerContent=feedback) }
                            composable("history-detail") { DriverHistoryDetailScreen(state,owner.value,{nav.popBackStack()},offerContent=feedback) }
                            composable("m06") { DriverOrderDetailScreen(current.value,owner.value,{nav.popBackStack()}, {},{ MapsOpenResult.UNVERIFIED },onDelivery={patches++}) }
                        }
                        DriverRejectedOfferNotice(offer.value,route,rejectOrigin.value,owner.value,true) {
                            if(offer.value===it) offer.value=it.copy(message=null)
                        }
                        DriverOfferDialog(offer.value,{}, {
                            rejectOrigin.value=route
                            offer.value=DriverOffersUiState(phase=DriverOfferPhase.REJECTED,message="FIXTURE confirmed Reject")
                        }, {})
                    }
                }
            }
            test("Bottom tab opens real Earnings; Today uses official total and Vietnam date") {
                click("Thu nhập"); await { vm.state.value.earnings != null && text("Tổng thu nhập")!=null }
                check(source.ranges.last() == EarningsRange(LocalDate.of(2026,11,13),LocalDate.of(2026,11,13)))
                check(text("91.000đ")!=null && text("80%") == null)
                await { whiteStatusBar(main) }
                capture("01-earnings-today.png")
            }
            test("Week and month filters issue inclusive Vietnam ranges") {
                click("Tuần này"); await { !vm.state.value.earningsLoading && vm.state.value.period==EarningsPeriod.WEEK }
                await { text("09/11/2026 — 13/11/2026")!=null }
                check(source.ranges.last().from == LocalDate.of(2026,11,9)); capture("02-earnings-week.png")
                check(text("91.000đ")!=null && vm.state.value.earnings!!.items.map { it.orderId } == listOf(88001L,88002L))
                click("Tháng này"); await { !vm.state.value.earningsLoading && vm.state.value.period==EarningsPeriod.MONTH }
                await { text("01/11/2026 — 13/11/2026")!=null }
                check(source.ranges.last().from == LocalDate.of(2026,11,1)); capture("11-earnings-month.png")
                check(text("91.000đ")!=null && vm.state.value.earnings!!.items.map { it.orderId } == listOf(88001L,88002L))
                report.append("EVIDENCE same owner=7, fixed Vietnam date=2026-11-13, same ledger IDs88001/88002: Today/Week/Month total=91000. Empty scenario is switched separately afterwards.\n")
            }
            test("Trusted earnings row opens read-only detail with exact stored amounts") {
                reveal("FIXTURE-INCOME-88001"); click("FIXTURE-INCOME-88001")
                await { text("Chi tiết thu nhập")!=null }
                check(text("Thu nhập cơ bản: 40.000đ")!=null && text("Thưởng: 1.000đ")!=null)
                check(text("80%") == null && text("Đã lấy món") == null)
                capture("04-earning-detail.png")
                reveal("Quay lại Thu nhập"); check(button("Quay lại Thu nhập")?.isEnabled==true)
                capture("16-earning-detail-bottom.png"); click("Quay lại Thu nhập")
                await { text("Tổng thu nhập")!=null }; check(vm.state.value.period==EarningsPeriod.MONTH)
            }
            test("Empty earnings is confirmed data, not a network fallback") {
                source.empty=true; runOnMainSync { vm.refreshEarnings() }
                await { text("Chưa có thu nhập")!=null }; check(text("0đ")!=null); capture("03-earnings-empty.png")
            }
            test("Earnings errors remain errors and Retry restores data") {
                source.error=true; runOnMainSync { vm.refreshEarnings() }
                await { text("Chưa thể tải thu nhập")!=null }; capture("09-earnings-error.png")
                source.error=false; source.empty=false; click("Thử lại")
                await { vm.state.value.earningsError==null && !vm.state.value.earningsLoading }
            }
            test("Loading is visible; repeated refresh does not duplicate in-flight GET") {
                source.gate=CompletableDeferred(); val before=source.ranges.size
                runOnMainSync { repeat(5) { vm.refreshEarnings() } }
                try { await { text("Đang tải dữ liệu…")!=null }; check(source.ranges.size==before+1); capture("10-earnings-loading.png") }
                finally { runOnMainSync { source.gate?.complete(Unit) } }
                await { !vm.state.value.earningsLoading }
            }
            test("Pull Earnings refreshes EACH selected period once, retains filter, and removes only its toolbar refresh") {
                for (period in EarningsPeriod.entries) {
                    if(vm.state.value.period!=period) click(period.label)
                    await { !vm.state.value.earningsLoading && vm.state.value.earnings!=null }
                    toTop(); val before=source.ranges.size; source.gate=CompletableDeferred()
                    try {
                        pullDown(); await { vm.state.value.earningsLoading && source.ranges.size==before+1 && text("Đang tải dữ liệu…")!=null }
                        check(button("Làm mới")==null && vm.state.value.period==period)
                        capture("refresh-earnings-${period.name.lowercase()}.png")
                        pullDown(); check(source.ranges.size==before+1)
                        check(source.ranges.last()==EarningsRange.forPeriod(period,clock))
                    } finally { runOnMainSync { source.gate?.complete(Unit) } }
                    await { !vm.state.value.earningsLoading }; check(vm.state.value.period==period)
                }
            }
            test("Pull Earnings works on empty and failed data; Retry and chosen period survive") {
                source.empty=true; runOnMainSync { vm.refreshEarnings() }; await { text("Chưa có thu nhập")!=null }
                toTop(); val before=source.ranges.size; source.gate=CompletableDeferred()
                try { pullDown(); await { source.ranges.size==before+1 && vm.state.value.earningsLoading }; capture("refresh-earnings-empty.png") }
                finally { runOnMainSync { source.gate?.complete(Unit) } }
                await { !vm.state.value.earningsLoading }
                source.error=true; pullDown(); await { text("Chưa thể tải thu nhập")!=null && button("Thử lại")!=null }
                check(vm.state.value.period==EarningsPeriod.MONTH)
                source.error=false; source.empty=false; click("Thử lại"); await { !vm.state.value.earningsLoading && vm.state.value.earningsError==null }
            }
            test("Reject confirmation is local to its route; tab/detail navigation and a NEW offer do not replay it") {
                val now=LocalDateTime.of(2026,11,13,10,0)
                val fresh=DriverOffer(89901,89902,"FIXTURE-NEW-OFFER","FIXTURE Restaurant","FIXTURE Pickup",10.0,106.0,"FIXTURE Delivery",1.0,now,now.plusSeconds(30))
                runOnMainSync { offer.value=DriverOffersUiState(offer=fresh,remainingSeconds=24,phase=DriverOfferPhase.OFFER) }
                await { text("Đơn FIXTURE-NEW-OFFER")!=null }; click("Từ chối"); await { text("Đã từ chối lời mời")!=null }
                capture("reject-local-confirmation.png")
                click("Đơn hàng"); await { text("Đã từ chối lời mời")==null && offer.value.message==null }
                click("Thu nhập"); await { text("Tổng thu nhập")!=null }; reveal("FIXTURE-INCOME-88001"); click("FIXTURE-INCOME-88001")
                await { text("Chi tiết thu nhập")!=null }; check(text("Đã từ chối lời mời")==null)
                reveal("Quay lại Thu nhập"); click("Quay lại Thu nhập"); click("Đơn hàng"); click("Lịch sử đã giao")
                await { vm.state.value.historyPage==0 }; reveal("FIXTURE-HISTORY-88100"); click("FIXTURE-HISTORY-88100")
                await { text("Chi tiết đơn đã giao")!=null }; check(text("Đã từ chối lời mời")==null)
                runOnMainSync { offer.value=DriverOffersUiState(offer=fresh.copy(offerId=89903),remainingSeconds=23,phase=DriverOfferPhase.OFFER) }
                await { text("Đơn FIXTURE-NEW-OFFER")!=null && text("00:23")!=null }
                capture("new-offer-on-history-detail.png")
                runOnMainSync { offer.value=DriverOffersUiState() }
                reveal("Quay lại Lịch sử"); click("Quay lại Lịch sử"); click("Trang chủ"); click("Thu nhập")
                await { text("Tổng thu nhập")!=null }; check(text("Đã từ chối lời mời")==null)
                runOnMainSync { vm.selectHistory(false) }
                report.append("EVIDENCE route-scoped Reject consumed; Orders/Earnings/both details/Home clear; NEW offer/countdown still displayed\n")
            }
            test("Orders default Active uses confirmed empty current, with no intermediate screen") {
                click("Đơn hàng"); await { text("Bạn chưa có đơn đang thực hiện.")!=null }
                check(!vm.state.value.historySelected && text("Xem chi tiết đơn hàng")==null); capture("05-orders-empty.png")
            }
            test("Pull Active works with empty/short current; held response prevents duplicate reads and keeps branch") {
                for (active in listOf(false,true)) {
                    runOnMainSync { current.value=DriverCurrentOrderUiState(ownerId=7,verified=true,order=if(active) activeOrder() else null) }
                    toTop(); val before=currentReads; currentGate=CompletableDeferred()
                    try {
                        pullDown(); await { current.value.loading && currentReads==before+1 }
                        check(!vm.state.value.historySelected && button("Làm mới")==null)
                        capture(if(active) "refresh-orders-active.png" else "refresh-orders-empty.png")
                        pullDown(); check(currentReads==before+1)
                    } finally { runOnMainSync { currentGate?.complete(Unit) } }
                    await { !current.value.loading }
                }
                runOnMainSync { current.value=DriverCurrentOrderUiState(ownerId=7,verified=true); currentFails=true }
                toTop(); pullDown(); await { text("FIXTURE current GET failed")!=null && button("Thử cập nhật lại")!=null }
                currentFails=false; click("Thử cập nhật lại"); await { current.value.verified && current.value.error==null }
            }
            test("Unknown current order never becomes confirmed empty") {
                runOnMainSync { current.value=current.value.copy(verified=false,error="FIXTURE chưa xác minh đơn") }
                await { text("Bạn chưa có đơn đang thực hiện.")==null && text("FIXTURE chưa xác minh đơn")!=null }
                capture("12-orders-unknown.png")
            }
            test("Active card opens existing M06 directly; no Delivery buttons in list") {
                runOnMainSync { current.value=DriverCurrentOrderUiState(order=activeOrder(),verified=true,ownerId=7) }
                await { text("Đơn FIXTURE-CURRENT-88090")!=null }
                await { text("Trạng thái: Nhà hàng đang chuẩn bị món")!=null && text("Đã nhận đơn")!=null }
                check(text("Trạng thái: PREPARING")==null && text("Đã nhận")==null)
                await { whiteStatusBar(main) }
                check(text("Đã lấy món")==null && patches==0); capture("06-orders-active.png")
                click("Đơn FIXTURE-CURRENT-88090"); await { text("Chi tiết đơn hàng")!=null && text("Nhà hàng đang chuẩn bị món")!=null }
                await { whiteStatusBar(main) }
                capture("15-m06-from-orders.png")
                click("Quay lại Trang chủ"); await { text("Đang thực hiện")!=null && text("Lịch sử đã giao")!=null }
                await { whiteStatusBar(main) }
            }
            test("History branch loads delivered-only summary independently") {
                click("Lịch sử đã giao"); await { vm.state.value.historyPage==0 && !vm.state.value.historyLoading }
                // Do not scroll the previous Active frame while StateFlow is ahead of Compose.
                await { text("Các đơn đã hoàn tất của bạn")!=null && text("FIXTURE-HISTORY-88100")?.isVisibleToUser==true }
                check(text("Đã giao")!=null)
                report.append("EVIDENCE History entry shows first order before any test scroll.\n")
                capture("07-history.png")
            }
            test("Active status labels are presentation only and never change the API enum") {
                click("Đang thực hiện")
                for ((code,label) in listOf("READY_FOR_PICKUP" to "Món đã sẵn sàng", "PICKED_UP" to "Đã lấy món",
                    "DELIVERING" to "Đang giao hàng", "PLACED" to "Đã nhận đơn", "PENDING_PAYMENT" to "Chờ thanh toán",
                    "DELIVERED" to "Giao hàng thành công", "CANCELLED" to "Đơn đã hủy", "REJECTED" to "Đơn đã bị từ chối")) {
                    runOnMainSync { current.value=current.value.copy(order=current.value.order!!.copy(status=code)) }
                    await { text("Trạng thái: $label")!=null }
                    check(current.value.order!!.status==code && patches==0)
                    check(text("Trạng thái: $code")==null && text("Đã nhận đơn")!=null)
                }
                runOnMainSync { current.value=current.value.copy(order=current.value.order!!.copy(status="PREPARING")) }
                click("Lịch sử đã giao"); await { text("FIXTURE-HISTORY-88100")?.isVisibleToUser==true }
            }
            test("History row opens read-only detail without current-order lookup or fake finance") {
                click("FIXTURE-HISTORY-88100"); await { text("Chi tiết đơn đã giao")!=null }
                check(text("FIXTURE Restaurant")!=null && text("Đã giao thành công")!=null)
                check(text("Hoàn thành lúc: 13/11/2026 09:00")!=null)
                check(text("Đã lấy món")==null && text("Xác nhận đã giao")==null && text("40.000đ")==null)
                capture("08-history-detail.png"); click("Quay lại")
                await { text("Các đơn đã hoàn tất của bạn")!=null }; check(vm.state.value.historySelected)
            }
            test("Missing deliveredAt stays missing and never falls back to creation/current time") {
                source.nullCompletionTime=true; runOnMainSync { vm.refreshHistory() }
                await { text("Chưa có thời điểm hoàn thành")!=null && !vm.state.value.historyLoading }
                click("FIXTURE-HISTORY-88100"); await { text("Chi tiết đơn đã giao")!=null }
                check(text("Chưa có thời điểm hoàn thành")!=null && text("Hoàn thành lúc: 13/11/2026 09:00")==null)
                capture("18-history-missing-time.png"); click("Quay lại")
                source.nullCompletionTime=false; runOnMainSync { vm.refreshHistory() }
                await { !vm.state.value.historyLoading && vm.state.value.history.firstOrNull()?.deliveredAt!=null }
            }
            test("Load more has one request and no repeated order IDs") {
                reveal("Tải thêm"); source.historyGate=CompletableDeferred()
                val before=source.pages.size; click("Tải thêm")
                try {
                    await { vm.state.value.historyLoading }; runOnMainSync { repeat(5) { vm.loadMoreHistory() } }
                    check(source.pages.size==before+1)
                } finally { runOnMainSync { source.historyGate?.complete(Unit) } }
                await { !vm.state.value.historyLoading && vm.state.value.historyPage==1 }
                check(vm.state.value.history.map { it.orderId }.distinct().size == vm.state.value.history.size)
            }
            test("History empty/error/retry remain distinct") {
                source.emptyHistory=true; runOnMainSync { vm.refreshHistory() }
                await { text("Chưa có đơn đã giao")!=null }; capture("13-history-empty.png")
                source.errorHistory=true; runOnMainSync { vm.refreshHistory() }
                await { text("Chưa thể tải lịch sử")!=null }; check(text("Chưa có đơn đã giao")==null); capture("14-history-error.png")
                source.emptyHistory=false; source.errorHistory=false; click("Thử lại")
                await { vm.state.value.historyError==null && vm.state.value.history.isNotEmpty() }
            }
            test("Pull History restarts page ZERO, retains branch, handles empty/error and never duplicates rows/requests") {
                for (empty in listOf(false,true)) {
                    source.emptyHistory=empty
                    toTop(); val before=source.pages.size; source.historyGate=CompletableDeferred()
                    try {
                        pullDown(); await { vm.state.value.historyLoading && source.pages.size==before+1 }
                        check(vm.state.value.historySelected && source.pages.last()==0 && vm.state.value.historyPage == -1 && button("Làm mới")==null)
                        capture(if(empty) "refresh-history-empty.png" else "refresh-history.png")
                        pullDown(); check(source.pages.size==before+1)
                    } finally { runOnMainSync { source.historyGate?.complete(Unit) } }
                    await { !vm.state.value.historyLoading && vm.state.value.historyPage==0 }
                    check(vm.state.value.history.map { it.orderId }.distinct().size==vm.state.value.history.size)
                }
                source.errorHistory=true; toTop(); pullDown(); await { text("Chưa thể tải lịch sử")!=null && button("Thử lại")!=null }
                source.emptyHistory=false; source.errorHistory=false; click("Thử lại"); await { vm.state.value.historyError==null && vm.state.value.history.size==10 }
            }
            test("Owner change immediately hides history and detail; logout clears all report snapshots") {
                runOnMainSync { owner.value=8 }
                await { text("FIXTURE-HISTORY-88100")==null && text("Đang xác minh phiên")!=null }
                val previousReads=source.pages.size; toTop(); pullDown(); check(source.pages.size==previousReads)
                runOnMainSync { vm.clear() }
                check(vm.state.value.earnings==null && vm.state.value.history.isEmpty() && vm.state.value.selectedHistory==null)
                runOnMainSync { owner.value=7; vm.activateSession(7) }
                await { vm.state.value.ownerId==7L }
            }
            test("History 403 stays on History, hides data, and never retries automatically") {
                runOnMainSync { vm.selectHistory(true) }
                await { !vm.state.value.historyLoading && vm.state.value.historyPage==0 }
                source.forbiddenHistory=true; runOnMainSync { vm.refreshHistory() }
                await { text("Không có quyền xem lịch sử")!=null }
                check(vm.state.value.historySelected && vm.state.value.history.isEmpty() && text("FIXTURE-HISTORY-88100")==null)
                val count=source.pages.size; runOnMainSync { vm.refreshHistory(); vm.loadMoreHistory() }; check(source.pages.size==count)
                capture("17-history-forbidden.png")
            }
            test("The four shared tabs still navigate Home and Profile") {
                click("Hồ sơ"); check(profiles==1)
                click("Trang chủ"); await { text("FIXTURE Driver session")!=null }
                val ancestry=nodes().filter { matches(it,"Trang chủ") }.map { node -> generateSequence(node) { it.parent }.take(5).map { listOf(it.className,it.isEnabled,it.isSelected,it.isClickable) }.toList() }
                report.append("EVIDENCE Home accessibility [class,enabled,selected,clickable]=$ancestry\n")
                await { listOf("Trang chủ","Đơn hàng","Thu nhập","Hồ sơ").all { button(it)?.isEnabled==true } }
                for (label in listOf("Trang chủ","Đơn hàng","Thu nhập","Hồ sơ")) check(button(label)?.isEnabled==true)
                report.append("EVIDENCE Home tab selected=${button("Trang chủ")?.isSelected}, clickable=${button("Trang chủ")?.isClickable}, enabled=${button("Trang chủ")?.isEnabled}, actions=${button("Trang chủ")?.actionList?.map { it.id }}\n")
            }
            report.append("FONT_SCALE=${targetContext.resources.configuration.fontScale}; PATCH_COUNT=$patches\n")
        } catch (error: Throwable) { failed++; report.append("ERROR runner: $error\n") }
        finally {
            runOnMainSync { fixtureScope.cancel(); model?.clear(); activity?.finish() }
            report.append("RESULT: PASS=$passed FAIL=$failed\n")
            finish(if(failed==0) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,Bundle().apply { putString("stream",report.toString()) })
        }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        if(android.os.Build.VERSION.SDK_INT>=34) uiAutomation.clearCache()
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) { result.add(node); for(i in 0 until node.childCount) node.getChild(i)?.let(::visit) }
        uiAutomation.rootInActiveWindow?.let(::visit); return result
    }
    private fun whiteStatusBar(main: MainActivity): Boolean {
        var white=false
        runOnMainSync { white=!WindowCompat.getInsetsController(main.window,main.window.decorView).isAppearanceLightStatusBars }
        return white
    }
    private fun matches(node: AccessibilityNodeInfo,value: String) = node.text?.toString()?.split('\n')?.contains(value)==true || node.contentDescription?.toString()==value
    private fun text(value: String) = nodes().firstOrNull { matches(it,value) }
    private fun button(value: String): AccessibilityNodeInfo? {
        for(node in nodes().filter { matches(it,value) && it.isVisibleToUser }) {
            var next: AccessibilityNodeInfo?=node
            // Selected tabs are enabled selections even when Android intentionally removes click actions.
            repeat(7) { if(next?.isSelected==true || next?.isClickable==true || next?.actionList?.any { it.id==AccessibilityNodeInfo.ACTION_CLICK }==true) return next; next=next?.parent }
        }; return null
    }
    private fun click(value: String) {
        await { button(value)?.let { it.isEnabled && it.isVisibleToUser }==true }
        check(button(value)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true) { "Cannot click $value" }
    }
    private fun reveal(value: String) {
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<deadline) {
            val snapshot=nodes(); if(snapshot.any { matches(it,value) && it.isVisibleToUser }) return
            snapshot.firstOrNull { it.isScrollable && it.isVisibleToUser && it.actionList.any { a->a.id==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            try { uiAutomation.waitForIdle(100,1000) } catch (_: Exception) { }
        }; error("Content not reachable: $value")
    }
    private fun await(condition: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<deadline) { if(condition()) return; SystemClock.sleep(100) }
        error("UI condition timed out after15s")
    }
    private fun toTop() {
        repeat(20) {
            val list=nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }
            if (list==null || list.actionList.none { a->a.id==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD }) return
            if(!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return
            try { uiAutomation.waitForIdle(100,1000) } catch (_: Exception) { }
        }
        error("List did not reach top")
    }
    private fun pullDown() {
        val display=uiAutomation.takeScreenshot()!!
        val x=display.width/2; val from=display.height*28/100; val to=display.height*72/100
        display.recycle()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("input swipe $x $from $x $to 420")).use { it.readBytes() }
    }
    private fun capture(name: String) {
        try { uiAutomation.waitForIdle(100,2000) } catch (_: Exception) { }
        val folder=File(targetContext.getExternalFilesDir(null),directory).apply { mkdirs() }
        val bitmap=checkNotNull(uiAutomation.takeScreenshot())
        File(folder,name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }; bitmap.recycle()
    }
    private fun activeOrder()=DriverCurrentOrder(88090,"FIXTURE-CURRENT-88090","PREPARING",LocalDateTime.of(2026,11,13,10,0),LocalDateTime.of(2026,11,13,10,1),88900,"FIXTURE Restaurant","FIXTURE Pickup address",null,10.0,106.0,"FIXTURE Recipient","FIXTURE phone","FIXTURE Delivery address",10.1,106.1,null)
    private class Source : DriverReportsSource {
        val ranges=mutableListOf<EarningsRange>(); val pages=mutableListOf<Int>()
        var empty=false; var error=false; var emptyHistory=false; var errorHistory=false; var forbiddenHistory=false; var nullCompletionTime=false
        var gate: CompletableDeferred<Unit>?=null; var historyGate: CompletableDeferred<Unit>?=null
        // A fixed fake-server ledger, independent of the requested filter. Empty is a separate scenario.
        private val ledger=listOf(
            DriverEarning(88001,"FIXTURE-INCOME-88001",BigDecimal("40000"),BigDecimal("1000"),BigDecimal("41000"),LocalDateTime.of(2026,11,13,10,0)),
            DriverEarning(88002,"FIXTURE-INCOME-88002",BigDecimal("50000"),BigDecimal.ZERO,BigDecimal("50000"),LocalDateTime.of(2026,11,13,9,0)))
        override suspend fun earnings(range: EarningsRange): ReportsResult<DriverEarnings> {
            ranges.add(range); gate?.await(); gate=null
            if(error) return ReportsResult.Failed("FIXTURE không tải được dữ liệu, hãy thử lại.")
            val rows=if(empty) emptyList() else ledger.filter { it.createdAt.toLocalDate() in range.from..range.to }
            val base=rows.fold(BigDecimal.ZERO) { total,row -> total.add(row.amount) }
            val bonus=rows.fold(BigDecimal.ZERO) { total,row -> total.add(row.bonus) }
            return ReportsResult.Loaded(DriverEarnings(range,base,bonus,base.add(bonus),rows))
        }
        override suspend fun history(page: Int,size: Int): ReportsResult<DriverHistoryPage> {
            pages.add(page); historyGate?.await(); historyGate=null
            if(forbiddenHistory) return ReportsResult.Forbidden
            if(errorHistory) return ReportsResult.Failed("FIXTURE không tải được lịch sử.")
            val ids=if(emptyHistory) emptyList() else if(page==0) (88100L..88109L).toList() else listOf(88109L,88110L)
            return ReportsResult.Loaded(DriverHistoryPage(ids.map { DriverHistoryOrder(it,"FIXTURE-HISTORY-$it",88900,"FIXTURE Restaurant",if(nullCompletionTime) null else LocalDateTime.of(2026,11,13,9,0)) },page,size,if(emptyHistory) 0L else 12L,if(emptyHistory) 0 else 2))
        }
    }
}
