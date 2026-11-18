import androidx.lifecycle.finishTestScopes
import androidx.lifecycle.testDispatcher
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.order.*
import vn.hutech.foodexpress.driver.data.report.DriverReportsModule
import vn.hutech.foodexpress.driver.data.report.DriverReportsRepository
import vn.hutech.foodexpress.driver.ui.order.*

private val time = LocalDateTime.of(2026, 11, 12, 12, 0)
private fun order(status: String = "PREPARING", id: Long = 501) = DriverCurrentOrder(id, "FIXTURE-5D-$id", status,
    time, time, 905, "FIXTURE Restaurant", "FIXTURE Pickup", null, 10.0, 106.0,
    "FIXTURE Receiver", "FIXTURE Phone", "FIXTURE Destination", 11.0, 107.0, null)

private class Source : DriverCurrentOrderSource {
    var current: DriverCurrentOrder? = order()
    var loadFailure: CurrentOrderResult? = null
    var commandResult: DriverDeliveryResult? = null
    var commandDelay = 0L
    var nextLoadDelay = 0L
    var ignoreCancellation = false
    var commit = true
    var getCount = 0
    val history = mutableMapOf<Long, LocalDateTime>()
    override suspend fun findDeliveredAt(orderId: Long) = history[orderId]
    val commands = mutableListOf<Pair<Long, String>>()
    override suspend fun load(): CurrentOrderResult {
        getCount++
        val value = loadFailure ?: CurrentOrderResult.Loaded(current, time)
        val wait = nextLoadDelay; nextLoadDelay = 0
        if (ignoreCancellation) withContext(NonCancellable) { delay(wait) } else delay(wait)
        return value
    }
    override suspend fun changeStatus(orderId: Long, target: String): DriverDeliveryResult {
        commands.add(orderId to target)
        if (ignoreCancellation) withContext(NonCancellable) { delay(commandDelay) } else delay(commandDelay)
        if (commit && current?.orderId == orderId) current = if (target == "DELIVERED") null else current!!.copy(status = target)
        return commandResult ?: DriverDeliveryResult.Updated(DriverDeliveryStatus(orderId, target,
            if (target == "DELIVERED") time.plusSeconds(1) else null))
    }
}
private class Rig(status: String = "READY_FOR_PICKUP") : AutoCloseable {
    val clock = VirtualClock().also { testDispatcher = it }
    val source = Source().also { it.current = order(status) }
    val vm = DriverCurrentOrderViewModel(source)
    init { vm.setForeground(true); vm.activateSession(7); clock.advance(0) }
    override fun close() { vm.clear(); finishTestScopes(); clock.advance(30_000) }
}
private var passed = 0
private var failed = 0
private fun test(name: String, body: () -> Unit) {
    try { body(); passed++; println("PASS $name") }
    catch (e: Throwable) { failed++; println("FAIL $name: $e") }
}

fun main() {
    for ((from, target) in listOf("READY_FOR_PICKUP" to "DELIVERING", "DELIVERING" to "DELIVERED")) {
        test("Server-confirmed transition $from to $target") { Rig(from).use { r ->
            r.vm.changeDeliveryStatus(target); r.clock.advance(0)
            check(r.source.commands == listOf(501L to target))
            if (target == "DELIVERED") check(r.vm.state.value.completion?.orderId == 501L && r.vm.state.value.order == null)
            else check(r.vm.state.value.order?.status == target && r.vm.state.value.verified)
        } }
    }
    for ((from, target) in listOf("PLACED" to "DELIVERING", "PREPARING" to "DELIVERING",
        "PREPARING" to "DELIVERED", "READY_FOR_PICKUP" to "DELIVERED", "DELIVERING" to "PICKED_UP")) {
        test("No skipped/backward/legacy command $from to $target") { Rig(from).use { r ->
            r.vm.changeDeliveryStatus(target); r.clock.advance(0); check(r.source.commands.isEmpty())
        } }
    }
    test("Double tap creates one PATCH and does not optimistically advance") { Rig().use { r ->
        r.source.commandDelay = 100
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(r.vm.state.value.deliverySending && r.vm.state.value.order?.status == "READY_FOR_PICKUP")
        check(r.source.commands.size == 1); r.clock.advance(100)
        check(r.vm.state.value.order?.status == "DELIVERING")
    } }
    test("Pre-PATCH noncooperative GET cannot overwrite the command result") { Rig().use { r ->
        r.source.ignoreCancellation = true; r.source.nextLoadDelay = 100
        r.vm.refresh(); r.clock.advance(0); check(r.vm.state.value.loading)
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0); r.clock.advance(100)
        check(r.vm.state.value.order?.status == "DELIVERING" && r.source.commands.size == 1)
    } }
    test("Stale post-PATCH GET preserves newer server-confirmed status") { Rig().use { r ->
        r.source.commit = false
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(r.vm.state.value.order?.status == "DELIVERING")
        r.vm.refresh(); r.clock.advance(0); check(r.vm.state.value.order?.status == "DELIVERING")
    } }
    test("DELIVERED receipt survives repeated null GET and foreground recheck") { Rig("DELIVERING").use { r ->
        r.vm.changeDeliveryStatus("DELIVERED"); r.clock.advance(0)
        repeat(3) { r.vm.refresh(); r.clock.advance(0) }
        r.vm.setForeground(false); r.vm.pauseSessionVerification(); r.vm.setForeground(true); r.vm.activateSession(7); r.clock.advance(0)
        check(r.vm.state.value.completion?.orderCode == "FIXTURE-5D-501" && r.vm.state.value.order == null)
        r.vm.dismissCompletion(); check(r.vm.state.value.completion == null)
    } }
    test("Lost pickup response reconciles from confirmed current status") { Rig().use { r ->
        r.source.commandResult = DriverDeliveryResult.Uncertain("Fixture lost response")
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(r.vm.state.value.order?.status == "DELIVERING" && r.vm.state.value.deliveryPending == null)
        check(r.source.commands.size == 1)
    } }
    test("Lost completion stays uncertain until history contains exactly that order") { Rig("DELIVERING").use { r ->
        r.source.commandResult = DriverDeliveryResult.Uncertain("Fixture lost response")
        r.source.history[502] = time
        r.vm.changeDeliveryStatus("DELIVERED"); r.clock.advance(0); r.clock.advance(45_000)
        check(r.vm.state.value.deliveryPending?.target == "DELIVERED" && !r.vm.state.value.verified)
        check(r.vm.state.value.completion == null && r.source.commands.size == 1)
        r.source.history[501] = time.plusSeconds(1)
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture current GET failed")
        r.vm.retryPendingDelivery(); r.clock.advance(0)
        check(r.vm.state.value.completion?.orderId == 501L && r.vm.state.value.completion?.deliveredAt == time.plusSeconds(1))
        check(r.source.commands == listOf(501L to "DELIVERED"))
    } }
    test("Successful PATCH 200 survives a failed GET without reissuing PATCH") { Rig().use { r ->
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture current GET failed")
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(r.vm.state.value.order?.status == "DELIVERING")
        check(r.vm.state.value.deliveryPending == null && r.vm.state.value.deliveryMessage != null)
        r.clock.advance(45_000)
        check(r.source.commands == listOf(501L to "DELIVERING"))
    } }
    test("409 blocks more commands until fresh GET finishes") { Rig().use { r ->
        r.source.commandResult = DriverDeliveryResult.Rejected("Fixture 409"); r.source.commit = false
        r.source.nextLoadDelay = 100
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(!r.vm.state.value.verified && r.vm.state.value.loading)
        r.vm.changeDeliveryStatus("DELIVERING"); check(r.source.commands.size == 1)
        r.clock.advance(100); check(r.vm.state.value.verified)
    } }
    for (failure in listOf(DriverDeliveryResult.SessionExpired, DriverDeliveryResult.Forbidden)) {
        test("$failure clears recipient, pending and receipt") { Rig().use { r ->
            r.source.commandResult = failure; r.source.commit = false
            r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
            check(r.vm.state.value.sessionExpired && r.vm.state.value.order == null && r.vm.state.value.deliveryPending == null && r.vm.state.value.completion == null)
        } }
    }
    test("Logout/account change rejects a late noncooperative PATCH") { Rig("DELIVERING").use { r ->
        r.source.ignoreCancellation = true; r.source.commandDelay = 100
        r.vm.changeDeliveryStatus("DELIVERED"); r.clock.advance(0)
        r.vm.clear(); r.source.current = order("PREPARING", 502); r.vm.activateSession(8); r.clock.advance(0); r.clock.advance(100)
        check(r.vm.state.value.ownerId == 8L && r.vm.state.value.order?.orderId == 502L && r.vm.state.value.completion == null)
    } }
    test("Auth CHECKING during a command resumes with the same owner and fresh GET") { Rig().use { r ->
        r.source.commandDelay = 100; r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        r.vm.pauseSessionVerification(); check(r.vm.state.value.order == null)
        r.clock.advance(100); r.vm.activateSession(7); r.clock.advance(0)
        check(r.vm.state.value.ownerId == 7L && r.vm.state.value.order?.status == "DELIVERING")
    } }
    test("Background stops GET polling, not a sent command; no duplicate foreground loop") { Rig().use { r ->
        r.source.commandDelay = 100; r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        r.vm.setForeground(false); val before = r.source.getCount; r.clock.advance(30_000)
        check(r.source.getCount == before && r.source.commands.size == 1)
        repeat(5) { r.vm.setForeground(true); r.vm.activateSession(7) }; r.clock.advance(0)
        check(r.source.getCount == before + 1 && r.vm.state.value.order?.status == "DELIVERING")
    } }
    test("Network/GET failure preserves uncertainty and blocks a different action") { Rig().use { r ->
        r.source.commandResult = DriverDeliveryResult.Uncertain("Fixture offline"); r.source.commit = false
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture offline")
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(30_000)
        check(!r.vm.state.value.verified && r.vm.state.value.deliveryPending != null && r.source.commands.size == 1)
    } }
    test("Command timeout stays pending without automatic PATCH retry") { Rig().use { r ->
        r.source.commandDelay = 20_000
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(13_000)
        check(!r.vm.state.value.deliverySending && !r.vm.state.value.verified && r.vm.state.value.deliveryPending != null)
        check(r.source.commands.size == 1 && r.vm.state.value.completion == null)
    } }
    test("Wrong order/status in PATCH response does not manufacture success") { Rig().use { r ->
        r.source.commit = false
        r.source.commandResult = DriverDeliveryResult.Updated(DriverDeliveryStatus(999, "DELIVERING", null))
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(0)
        check(!r.vm.state.value.verified && r.vm.state.value.deliveryPending != null)
    } }
    test("Unverified current order cannot PATCH") { Rig().use { r ->
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture unavailable"); r.vm.refresh(); r.clock.advance(0)
        r.vm.changeDeliveryStatus("DELIVERING"); check(r.source.commands.isEmpty())
    } }
    http()
    println("RESULT: PASS=$passed FAIL=$failed; real Kotlin VM/Retrofit/Repository, FAKE clock/session/local HTTP; NOT Backend E2E")
    check(failed == 0)
}

private fun http() {
    var code = 200
    var body = """{"id":501,"status":"DELIVERING","deliveredAt":null,"history":[{"status":"DELIVERING"}]}"""
    val requests = mutableListOf<Pair<String, String>>()
    var drop = false
    var arrived: CountDownLatch? = null
    var release: CountDownLatch? = null
    var seenAuthorization: String? = null
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val executor = Executors.newSingleThreadExecutor(); server.executor = executor
    server.createContext("/api/driver/orders/") { e ->
        requests.add((e.requestMethod + " " + e.requestURI) to e.requestBody.readAllBytes().toString(Charsets.UTF_8))
        seenAuthorization = e.requestHeaders.getFirst("Authorization")
        check(seenAuthorization?.startsWith("Bearer ") == true)
        arrived?.countDown(); check(release?.await(3, TimeUnit.SECONDS) != false)
        if (drop) { e.close(); return@createContext }
        if (code == 503) e.responseHeaders.add("Retry-After", "0")
        if (code == 307) e.responseHeaders.add("Location", "/api/driver/orders/501/status")
        val bytes = body.toByteArray(); e.sendResponseHeaders(code, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
    }
    val historyPages = mutableListOf<String>()
    server.createContext("/api/driver/orders/history") { exchange ->
        val query = exchange.requestURI.rawQuery
        historyPages.add(query)
        val page = if (query.startsWith("page=0")) 0 else 1
        val orderId = if (page == 0) 502 else 501
        val bytes = """{"page":$page,"size":50,"totalItems":51,"totalPages":2,"items":[
            {"orderId":$orderId,"orderCode":"FIXTURE-$orderId","restaurantId":905,"restaurantName":"FIXTURE",
            "status":"DELIVERED","deliveredAt":"2026-11-12T12:00:01"}]}""".toByteArray()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    // Start with the shared transport's default retry policy; production's write adapter must disable it.
    val client = OkHttpClient.Builder().build()
    val auth = DriverAuthRepository()
    val retrofit = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/api/")
        .client(client).addConverterFactory(GsonConverterFactory.create()).build()
    val api = DriverOrderModule.api(retrofit)
    val repository = DriverOrderRepository(api, auth, DriverReportsRepository(DriverReportsModule.api(retrofit), auth))
    fun send(id: Long = 501, target: String = "DELIVERING") = runBlocking { repository.changeStatus(id, target) }
    try {
        test("Real Retrofit uses PATCH, exact id path and status-only body") {
            check(send() is DriverDeliveryResult.Updated)
            check(requests.last() == ("PATCH /api/driver/orders/501/status" to "{\"status\":\"DELIVERING\"}"))
        }
        test("Invalid id or target never reaches HTTP") {
            val count = requests.size; check(send(0) is DriverDeliveryResult.Rejected); check(send(target = "PREPARING") is DriverDeliveryResult.Rejected)
            check(requests.size == count)
        }
        for (status in listOf(400, 404, 409)) test("HTTP $status is rejected, not optimistic success") {
            code = status; check(send() is DriverDeliveryResult.Rejected)
        }
        for (status in listOf(500, 503)) test("HTTP $status stays uncertain until reconciliation") {
            code = status; check(send() is DriverDeliveryResult.Uncertain)
        }
        test("HTTP 401 and 403 use private-data-clearing results") {
            code = 401; check(send() == DriverDeliveryResult.SessionExpired)
            code = 403; check(send() == DriverDeliveryResult.Forbidden)
        }
        val good = body
        for ((name, invalid) in listOf("bad JSON" to "not-json", "wrong id" to good.replace("501", "502"),
            "missing id" to "{}", "wrong status" to good.replace("DELIVERING", "DELIVERED"))) {
            test("Malformed response $name cannot confirm delivery") { code = 200; body = invalid; check(send() is DriverDeliveryResult.Uncertain) }
        }
        test("DELIVERED requires a real deliveredAt, not null") {
            body = good.replace("DELIVERING", "DELIVERED"); check(send(target = "DELIVERED") is DriverDeliveryResult.Uncertain)
            body = body.replace("\"deliveredAt\":null", "\"deliveredAt\":\"2026-11-12T12:00:01\"")
            check(send(target = "DELIVERED") is DriverDeliveryResult.Updated)
        }
        test("History recovery follows pagination and proves only the requested order") {
            check(runBlocking { repository.findDeliveredAt(501) } == time.plusSeconds(1))
            check(historyPages == listOf("page=0&size=50", "page=1&size=50"))
            check(runBlocking { repository.findDeliveredAt(999) } == null)
        }
        test("Write transport never retries 503 or follows a PATCH redirect") {
            for (status in listOf(503, 307)) {
                val count = requests.size; code = status; body = good
                check(send() is DriverDeliveryResult.Uncertain && requests.size == count + 1)
            }
        }
        test("Dropped HTTP response sends exactly one PATCH and stays uncertain") {
            code = 200; body = good; drop = true; val count = requests.size
            check(send() is DriverDeliveryResult.Uncertain && requests.size == count + 1); drop = false
        }
        test("JWT is snapshotted before a delayed request; no next-account token is read") {
            arrived = CountDownLatch(1); release = CountDownLatch(1)
            runBlocking {
                val response = async(Dispatchers.IO) { repository.changeStatus(501, "DELIVERING") }
                check(arrived!!.await(3, TimeUnit.SECONDS)); auth.token = "fixture-next-account"
                release!!.countDown(); check(response.await() is DriverDeliveryResult.Updated)
                check(seenAuthorization == "Bearer fixture-token")
            }
            arrived = null; release = null
        }
        test("Missing JWT sends no PATCH") {
            val count = requests.size; auth.token = null; check(send() == DriverDeliveryResult.SessionExpired && requests.size == count)
        }
    } finally { client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); server.stop(0); executor.shutdownNow() }
}
