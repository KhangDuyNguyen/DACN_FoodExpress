import androidx.lifecycle.finishTestScopes
import androidx.lifecycle.testDispatcher
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.*
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.report.*
import vn.hutech.foodexpress.driver.ui.report.*

private val fixed = Clock.fixed(Instant.parse("2026-11-12T18:30:00Z"), ZoneOffset.UTC)
private val today = EarningsRange.forPeriod(EarningsPeriod.TODAY, fixed)
private fun earning(id: Long = 700L, range: EarningsRange = today) = DriverEarning(id, "FIXTURE-$id", BigDecimal("40000"), BigDecimal("1000"), BigDecimal("41000"), range.to.atTime(10, 0))
private fun sampleEarnings(range: EarningsRange = today) = DriverEarnings(range, BigDecimal("40000"), BigDecimal("1000"), BigDecimal("41000"), listOf(earning(range = range)))
private fun past(id: Long) = DriverHistoryOrder(id, "FIXTURE-$id", 900, "FIXTURE Restaurant", today.to.atTime(9, 0))
private class Source : DriverReportsSource {
    val ranges = mutableListOf<EarningsRange>()
    val pages = mutableListOf<Int>()
    var wait = 0L
    var ignoreCancel = false
    var earningResult: ReportsResult<DriverEarnings>? = null
    var historyResult: ReportsResult<DriverHistoryPage>? = null
    override suspend fun earnings(range: EarningsRange): ReportsResult<DriverEarnings> {
        ranges.add(range); val result = earningResult ?: ReportsResult.Loaded(sampleEarnings(range))
        if (ignoreCancel) withContext(NonCancellable) { delay(wait) } else delay(wait)
        return result
    }
    override suspend fun history(page: Int, size: Int): ReportsResult<DriverHistoryPage> {
        pages.add(page)
        val result = historyResult ?: ReportsResult.Loaded(DriverHistoryPage(if (page == 0) listOf(past(1), past(2)) else listOf(past(2), past(3)), page, size, 12, 2))
        if (ignoreCancel) withContext(NonCancellable) { delay(wait) } else delay(wait)
        return result
    }
}
private class Rig(page: DriverReportsPage = DriverReportsPage.EARNINGS) : AutoCloseable {
    val scheduler = VirtualClock().also { testDispatcher = it }
    val source = Source()
    val vm = DriverReportsViewModel(source, fixed)
    init { vm.setForeground(true); vm.show(page); vm.activateSession(7); scheduler.advance(0) }
    override fun close() { vm.clear(); finishTestScopes(); scheduler.advance(30_000) }
}
private var passed = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS $name") } catch (e: Throwable) { failed++; println("FAIL $name: $e") }
}
fun main() {
    test("Vietnam calendar crosses UTC midnight correctly") { check(today.from == LocalDate.of(2026,11,13) && today.to == today.from) }
    test("Week starts Monday and ends today, inclusive") { check(EarningsRange.forPeriod(EarningsPeriod.WEEK,fixed) == EarningsRange(LocalDate.of(2026,11,9),today.to)) }
    test("Month starts first Vietnam day, inclusive") { check(EarningsRange.forPeriod(EarningsPeriod.MONTH,fixed).from == LocalDate.of(2026,11,1)) }
    test("Sunday and month/year boundary use Vietnam clock") {
        val clock = Clock.fixed(Instant.parse("2026-12-31T18:00:00Z"), ZoneOffset.UTC)
        check(EarningsRange.forPeriod(EarningsPeriod.MONTH,clock).from == LocalDate.of(2027,1,1))
        check(EarningsRange.forPeriod(EarningsPeriod.WEEK,clock).from == LocalDate.of(2026,12,28))
    }
    test("Earnings activate once and retain official server total") { Rig().use { r ->
        check(r.source.ranges.size == 1 && r.vm.state.value.earnings!!.totalAmount == BigDecimal("41000"))
        r.vm.activateSession(7); r.vm.show(DriverReportsPage.EARNINGS); r.scheduler.advance(0); check(r.source.ranges.size == 1)
    } }
    test("Three filters issue exact date ranges") { Rig().use { r ->
        r.vm.selectPeriod(EarningsPeriod.WEEK); r.scheduler.advance(0); r.vm.selectPeriod(EarningsPeriod.MONTH); r.scheduler.advance(0)
        check(r.source.ranges == EarningsPeriod.entries.map { EarningsRange.forPeriod(it,fixed) })
    } }
    test("Selected detail uses trusted orderId, never position or derived fee") { Rig().use { r ->
        check(r.vm.selectEarning(700) && r.vm.state.value.selectedEarning!!.bonus == BigDecimal("1000"))
        check(!r.vm.selectEarning(999)); r.vm.selectPeriod(EarningsPeriod.MONTH); check(r.vm.state.value.selectedEarning == null)
    } }
    test("Empty earnings is distinct from failed request") { Rig().use { r ->
        r.source.earningResult = ReportsResult.Loaded(sampleEarnings().copy(items=emptyList(),baseAmount=BigDecimal.ZERO,bonus=BigDecimal.ZERO,totalAmount=BigDecimal.ZERO))
        r.vm.refreshEarnings(); r.scheduler.advance(0); check(r.vm.state.value.earnings!!.items.isEmpty() && r.vm.state.value.earningsError == null)
        r.source.earningResult = ReportsResult.Failed("FIXTURE network"); r.vm.refreshEarnings(); r.scheduler.advance(0)
        check(r.vm.state.value.earningsError != null && r.vm.state.value.earnings != null)
    } }
    test("Rapid refresh does not create duplicate request") { Rig().use { r ->
        r.source.wait=100; repeat(10) { r.vm.refreshEarnings() }; r.scheduler.advance(0)
        check(r.source.ranges.size == 2); r.scheduler.advance(100); check(!r.vm.state.value.earningsLoading)
    } }
    test("Old filter response cannot overwrite new filter") { Rig().use { r ->
        r.source.wait=100; r.source.ignoreCancel=true; r.vm.refreshEarnings(); r.scheduler.advance(0)
        r.source.wait=0; r.vm.selectPeriod(EarningsPeriod.MONTH); r.scheduler.advance(100)
        check(r.vm.state.value.earnings!!.range.from == LocalDate.of(2026,11,1))
    } }
    test("Recheck clears private reports and retains same-owner filter") { Rig().use { r ->
        r.vm.selectPeriod(EarningsPeriod.MONTH); r.scheduler.advance(0); r.vm.selectEarning(700); r.vm.pauseSessionVerification()
        check(r.vm.state.value.ownerId == null && r.vm.state.value.earnings == null && r.vm.state.value.selectedEarning == null)
        r.vm.activateSession(7); r.scheduler.advance(0); check(r.vm.state.value.period == EarningsPeriod.MONTH && r.vm.state.value.ownerId == 7L)
    } }
    test("Different account clears prior selection, data and filter before response") { Rig().use { r ->
        r.vm.selectPeriod(EarningsPeriod.MONTH); r.scheduler.advance(0); r.vm.selectEarning(700)
        r.source.wait=100; r.vm.activateSession(8)
        check(r.vm.state.value.earnings == null && r.vm.state.value.selectedEarning == null && r.vm.state.value.period == EarningsPeriod.TODAY)
    } }
    test("Late noncooperative response cannot repopulate logged-out session") { Rig().use { r ->
        r.source.wait=100; r.source.ignoreCancel=true; r.vm.refreshEarnings(); r.scheduler.advance(0)
        r.vm.clear(); r.scheduler.advance(100); check(r.vm.state.value == DriverReportsState())
    } }
    test("Background stops reads; foreground refreshes once, no polling loop") { Rig().use { r ->
        r.vm.setForeground(false); r.vm.refreshEarnings(); r.scheduler.advance(60_000); check(r.source.ranges.size == 1)
        r.vm.setForeground(true); r.vm.setForeground(true); r.scheduler.advance(60_000); check(r.source.ranges.size == 2)
    } }
    test("History is independent and active section is default") { Rig(DriverReportsPage.ORDERS).use { r ->
        check(!r.vm.state.value.historySelected && r.source.pages.isEmpty()); r.vm.selectHistory(true); r.scheduler.advance(0); check(r.source.pages == listOf(0))
    } }
    test("Pagination deduplicates reliable order IDs and locks repeated taps") { Rig(DriverReportsPage.ORDERS).use { r ->
        r.vm.selectHistory(true); r.scheduler.advance(0); r.source.wait=100
        repeat(10) { r.vm.loadMoreHistory() }; r.scheduler.advance(0); check(r.source.pages == listOf(0,1))
        r.scheduler.advance(100); check(r.vm.state.value.history.map { it.orderId } == listOf(1L,2L,3L))
        r.vm.loadMoreHistory(); check(r.source.pages == listOf(0,1))
    } }
    test("History refresh resets pagination and read-only selection is reliable") { Rig(DriverReportsPage.ORDERS).use { r ->
        r.vm.selectHistory(true); r.scheduler.advance(0); r.vm.loadMoreHistory(); r.scheduler.advance(0)
        check(r.vm.selectHistoryOrder(3) && !r.vm.selectHistoryOrder(999))
        r.vm.refreshHistory(); r.scheduler.advance(0); check(r.source.pages == listOf(0,1,0) && r.vm.state.value.history.size == 2)
    } }
    test("Load-more error keeps prior page and retry asks same next page") { Rig(DriverReportsPage.ORDERS).use { r ->
        r.vm.selectHistory(true); r.scheduler.advance(0); r.source.historyResult=ReportsResult.Failed("FIXTURE server")
        r.vm.loadMoreHistory(); r.scheduler.advance(0); check(r.vm.state.value.history.size == 2 && r.vm.state.value.historyError != null)
        r.source.historyResult=null; r.vm.loadMoreHistory(); r.scheduler.advance(0); check(r.source.pages == listOf(0,1,1))
    } }
    test("Completed delivery invalidates earnings/history without writing orders") { Rig(DriverReportsPage.ORDERS).use { r ->
        r.vm.selectHistory(true); r.scheduler.advance(0); r.vm.invalidateAfterDelivery(); r.scheduler.advance(0)
        check(r.source.pages == listOf(0,0) && r.vm.state.value.earnings == null)
    } }
    for (history in listOf(false,true)) test("Auth failure clears both reports, history=$history") { Rig(if(history) DriverReportsPage.ORDERS else DriverReportsPage.EARNINGS).use { r ->
        if(history) { r.source.historyResult=ReportsResult.AuthRequired; r.vm.selectHistory(true) }
        else { r.source.earningResult=ReportsResult.AuthRequired; r.vm.refreshEarnings() }
        r.scheduler.advance(0); check(r.vm.state.value.sessionExpired && r.vm.state.value.ownerId == null && r.vm.state.value.earnings == null && r.vm.state.value.history.isEmpty())
    } }
    test("Presentation owner guard never exposes prior data") {
        val state=DriverReportsState(ownerId=7,earnings=sampleEarnings(),selectedEarning=earning(),history=listOf(past(1)))
        check(state.forReportOwner(8).earnings == null && state.forReportOwner(null).history.isEmpty())
    }
    test("403 clears snapshots without an automatic auth/read retry loop") { Rig().use { r ->
        r.source.earningResult=ReportsResult.Forbidden; r.vm.refreshEarnings(); r.scheduler.advance(0)
        val calls=r.source.ranges.size; r.vm.refreshEarnings(); r.scheduler.advance(60_000)
        check(r.vm.state.value.accessDenied && !r.vm.state.value.sessionExpired && r.vm.state.value.earnings==null && r.source.ranges.size==calls)
    } }
    test("History 403 retains the selected branch while clearing all data") { Rig(DriverReportsPage.ORDERS).use { r ->
        r.vm.selectHistory(true); r.scheduler.advance(0); r.source.historyResult=ReportsResult.Forbidden
        r.vm.refreshHistory(); r.scheduler.advance(0)
        val visible=r.vm.state.value.forReportOwner(7)
        check(visible.historySelected && visible.accessDenied && visible.history.isEmpty() && visible.selectedHistory==null)
    } }
    test("Denied presentation never exposes a cached financial snapshot") {
        val visible=DriverReportsState(ownerId=7,historySelected=true,accessDenied=true,earnings=sampleEarnings(),selectedEarning=earning(),history=listOf(past(1))).forReportOwner(7)
        check(visible.historySelected && visible.earnings==null && visible.selectedEarning==null && visible.history.isEmpty())
    }

    // Actual Retrofit/Gson contract calls to a loopback fixture server, never Food Express/MySQL.
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    var json=""; var status=200; var requests=0; var path=""; var query=""; var authorization=""
    server.createContext("/") { exchange ->
        requests++; path=exchange.requestURI.path; query=exchange.requestURI.rawQuery.orEmpty(); authorization=exchange.requestHeaders.getFirst("Authorization").orEmpty()
        val bytes=json.toByteArray(); exchange.responseHeaders.add("Content-Type","application/json"); exchange.sendResponseHeaders(status,bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }; server.start()
    val auth=DriverAuthRepository()
    val api=Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/api/").addConverterFactory(GsonConverterFactory.create()).build().create(DriverReportsApi::class.java)
    val repo=DriverReportsRepository(api,auth)
    val valid="""{"from":"2026-11-13","to":"2026-11-13","timeZone":"Asia/Ho_Chi_Minh","baseAmount":40000,"bonus":1000,"totalAmount":41000,"items":[{"orderId":700,"orderCode":"FIXTURE-700","amount":40000,"bonus":1000,"totalAmount":41000,"createdAt":"2026-11-13T10:00:00"}]}"""
    val historyJson="""{"items":[{"orderId":700,"orderCode":"FIXTURE-700","restaurantId":900,"restaurantName":"FIXTURE Restaurant","status":"DELIVERED","deliveredAt":null}],"page":0,"size":10,"totalItems":1,"totalPages":1}"""
    try {
        test("Real GET earnings uses exact contract, dates, JWT snapshot and decimal amount") { runBlocking {
            json=valid; val result=repo.earnings(today) as ReportsResult.Loaded
            check(result.value.totalAmount == BigDecimal("41000") && path == "/api/driver/earnings" && query == "from=2026-11-13&to=2026-11-13" && authorization == "Bearer fixture-token")
        } }
        fun fixedLedgerBody(range: EarningsRange, first: LocalDate = today.to, last: LocalDate = today.to) =
            """{"from":"${range.from}","to":"${range.to}","timeZone":"Asia/Ho_Chi_Minh","baseAmount":90000,"bonus":1000,"totalAmount":91000,"items":[{"orderId":88001,"orderCode":"FIXTURE-INCOME-88001","amount":40000,"bonus":1000,"totalAmount":41000,"createdAt":"${first}T10:00:00"},{"orderId":88002,"orderCode":"FIXTURE-INCOME-88002","amount":50000,"bonus":0,"totalAmount":50000,"createdAt":"${last}T09:00:00"}]}"""
        test("Same fixed Nov13 ledger belongs to Today, Week and Month for the same account/clock") { runBlocking {
            for (period in EarningsPeriod.entries) {
                val range=EarningsRange.forPeriod(period,fixed); json=fixedLedgerBody(range)
                val result=(repo.earnings(range) as ReportsResult.Loaded).value
                check(result.totalAmount.compareTo(BigDecimal("91000"))==0 && result.items.map { it.orderId }==listOf(88001L,88002L))
                check(result.items.all { it.createdAt.toLocalDate()==LocalDate.of(2026,11,13) })
                check(query=="from=${range.from}&to=${range.to}" && authorization=="Bearer fixture-token")
            }
        } }
        test("Earnings response accepts records exactly on both inclusive Vietnam date boundaries") { runBlocking {
            val range=EarningsRange.forPeriod(EarningsPeriod.WEEK,fixed); json=fixedLedgerBody(range,range.from,range.to)
            val result=(repo.earnings(range) as ReportsResult.Loaded).value
            check(result.items.map { it.createdAt.toLocalDate() }==listOf(range.from,range.to))
        } }
        test("History GET preserves explicit nullable completion time") { runBlocking {
            json=historyJson; val result=repo.history(0,10) as ReportsResult.Loaded
            check(result.value.items.single().deliveredAt == null && path == "/api/driver/orders/history" && query == "page=0&size=10")
        } }
        for ((label,body) in listOf("missing total" to valid.replace("\"totalAmount\":41000,\"items\"","\"items\""),
            "wrong dates" to valid.replace("2026-11-13","2026-11-14"),"wrong zone" to valid.replace("Asia/Ho_Chi_Minh","UTC"),
            "negative amount" to valid.replace("40000","-40000"),"invalid JSON" to "not-json")) {
            test("Malformed earnings is failure, not empty: $label") { runBlocking { json=body; check(repo.earnings(today) is ReportsResult.Failed) } }
        }
        for ((label,body) in listOf("active status" to historyJson.replace("DELIVERED","PREPARING"),
            "missing nullable field" to historyJson.replace(",\"deliveredAt\":null",""),
            "bad metadata" to historyJson.replace("\"page\":0","\"page\":1"))) {
            test("History rejects unsafe response: $label") { runBlocking { json=body; check(repo.history(0,10) is ReportsResult.Failed) } }
        }
        for (code in listOf(400,401,403,404,500,503)) test("HTTP $code is not successful empty data") { runBlocking {
            status=code; json="{}"; val result=repo.earnings(today)
            check(when(code) {401 -> result is ReportsResult.AuthRequired; 403 -> result is ReportsResult.Forbidden; else -> result is ReportsResult.Failed})
        } }
        test("No token makes no HTTP request") { runBlocking {
            val before=requests; auth.token=null; check(repo.earnings(today) is ReportsResult.AuthRequired && requests==before); auth.token="fixture-token"
        } }
        test("Invalid pagination/range rejected before HTTP") { runBlocking {
            val before=requests; check(repo.history(-1,10) is ReportsResult.Failed && repo.history(0,51) is ReportsResult.Failed)
            check(repo.earnings(EarningsRange(today.to.plusDays(1),today.to)) is ReportsResult.Failed && requests==before)
        } }
    } finally { server.stop(0) }
    println("RESULT: PASS=$passed FAIL=$failed; Kotlin production VM/Retrofit/Repository with fixture session/clock/HTTP, NOT real Backend E2E")
    check(failed==0)
}
