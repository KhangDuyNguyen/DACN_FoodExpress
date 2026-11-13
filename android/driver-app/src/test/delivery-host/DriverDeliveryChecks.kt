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
        return commandResult ?: DriverDeliveryResult.Updated(DriverDeliveryStatus(orderId, target, time.plusSeconds(1),
            if (target == "DELIVERED") time.plusSeconds(1) else null))
    }
}
private class Rig(status: String = "PREPARING") : AutoCloseable {
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
    for ((from, target) in listOf("PREPARING" to "PICKED_UP", "READY_FOR_PICKUP" to "PICKED_UP",
        "PICKED_UP" to "DELIVERING", "DELIVERING" to "DELIVERED")) {
        test("Server-confirmed transition $from to $target") { Rig(from).use { r ->
            r.vm.changeDeliveryStatus(target); r.clock.advance(0)
            check(r.source.commands == listOf(501L to target))
            if (target == "DELIVERED") check(r.vm.state.value.completion?.orderId == 501L && r.vm.state.value.order == null)
            else check(r.vm.state.value.order?.status == target && r.vm.state.value.verified)
        } }
    }
    for ((from, target) in listOf("PLACED" to "PICKED_UP", "PREPARING" to "DELIVERING", "PREPARING" to "DELIVERED",
        "PICKED_UP" to "DELIVERED", "DELIVERING" to "PICKED_UP")) {
        test("No skipped/backward/legacy command $from to $target") { Rig(from).use { r ->
            r.vm.changeDeliveryStatus(target); r.clock.advance(0); check(r.source.commands.isEmpty())
        } }
    }
    test("Double tap creates one PATCH and does not optimistically advance") { Rig().use { r ->
        r.source.commandDelay = 100
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        check(r.vm.state.value.deliverySending && r.vm.state.value.order?.status == "PREPARING")
        check(r.source.commands.size == 1); r.clock.advance(100)
        check(r.vm.state.value.order?.status == "PICKED_UP")
    } }
    test("Pre-PATCH noncooperative GET cannot overwrite the command result") { Rig().use { r ->
        r.source.ignoreCancellation = true; r.source.nextLoadDelay = 100
        r.vm.refresh(); r.clock.advance(0); check(r.vm.state.value.loading)
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0); r.clock.advance(100)
        check(r.vm.state.value.order?.status == "PICKED_UP" && r.source.commands.size == 1)
    } }
    test("Stale post-PATCH GET preserves newer server-confirmed status") { Rig().use { r ->
        r.source.commit = false
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        check(r.vm.state.value.order?.status == "PICKED_UP")
        r.vm.refresh(); r.clock.advance(0); check(r.vm.state.value.order?.status == "PICKED_UP")
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
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        check(r.vm.state.value.order?.status == "PICKED_UP" && r.vm.state.value.deliveryPending == null)
        check(r.source.commands.size == 1)
    } }
    test("Null GET alone never proves a lost DELIVERED response; only explicit replay confirms") { Rig("DELIVERING").use { r ->
        r.source.commandResult = DriverDeliveryResult.Uncertain("Fixture lost response")
        r.vm.changeDeliveryStatus("DELIVERED"); r.clock.advance(0); r.clock.advance(45_000)
        check(r.vm.state.value.deliveryPending?.target == "DELIVERED" && !r.vm.state.value.verified)
        check(r.vm.state.value.completion == null && r.source.commands.size == 1)
        r.source.commandResult = null; r.vm.retryPendingDelivery(); r.clock.advance(0)
        check(r.vm.state.value.completion?.orderId == 501L)
        check(r.source.commands == listOf(501L to "DELIVERED", 501L to "DELIVERED"))
    } }
    test("409 blocks more commands until fresh GET finishes") { Rig().use { r ->
        r.source.commandResult = DriverDeliveryResult.Rejected("Fixture 409"); r.source.commit = false
        r.source.nextLoadDelay = 100
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        check(!r.vm.state.value.verified && r.vm.state.value.loading)
        r.vm.changeDeliveryStatus("PICKED_UP"); check(r.source.commands.size == 1)
        r.clock.advance(100); check(r.vm.state.value.verified)
    } }
    for (failure in listOf(DriverDeliveryResult.SessionExpired, DriverDeliveryResult.Forbidden)) {
        test("$failure clears recipient, pending and receipt") { Rig().use { r ->
            r.source.commandResult = failure; r.source.commit = false
            r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
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
        r.source.commandDelay = 100; r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        r.vm.pauseSessionVerification(); check(r.vm.state.value.order == null)
        r.clock.advance(100); r.vm.activateSession(7); r.clock.advance(0)
        check(r.vm.state.value.ownerId == 7L && r.vm.state.value.order?.status == "PICKED_UP")
    } }
    test("Background stops GET polling, not a sent command; no duplicate foreground loop") { Rig().use { r ->
        r.source.commandDelay = 100; r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        r.vm.setForeground(false); val before = r.source.getCount; r.clock.advance(30_000)
        check(r.source.getCount == before && r.source.commands.size == 1)
        repeat(5) { r.vm.setForeground(true); r.vm.activateSession(7) }; r.clock.advance(0)
        check(r.source.getCount == before + 1 && r.vm.state.value.order?.status == "PICKED_UP")
    } }
    test("Network/GET failure preserves uncertainty and blocks a different action") { Rig().use { r ->
        r.source.commandResult = DriverDeliveryResult.Uncertain("Fixture offline"); r.source.commit = false
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture offline")
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        r.vm.changeDeliveryStatus("DELIVERING"); r.clock.advance(30_000)
        check(!r.vm.state.value.verified && r.vm.state.value.deliveryPending != null && r.source.commands.size == 1)
    } }
    test("Command timeout stays pending without automatic PATCH retry") { Rig().use { r ->
        r.source.commandDelay = 20_000
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(13_000)
        check(!r.vm.state.value.deliverySending && !r.vm.state.value.verified && r.vm.state.value.deliveryPending != null)
        check(r.source.commands.size == 1 && r.vm.state.value.completion == null)
    } }
    test("Wrong order/status in PATCH response does not manufacture success") { Rig().use { r ->
        r.source.commit = false
        r.source.commandResult = DriverDeliveryResult.Updated(DriverDeliveryStatus(999, "PICKED_UP", time, null))
        r.vm.changeDeliveryStatus("PICKED_UP"); r.clock.advance(0)
        check(!r.vm.state.value.verified && r.vm.state.value.deliveryPending != null)
    } }
    test("Unverified current order cannot PATCH") { Rig().use { r ->
        r.source.loadFailure = CurrentOrderResult.Failed("Fixture unavailable"); r.vm.refresh(); r.clock.advance(0)
        r.vm.changeDeliveryStatus("PICKED_UP"); check(r.source.commands.isEmpty())
    } }
    http()
    println("RESULT: PASS=$passed FAIL=$failed; real Kotlin VM/Retrofit/Repository, FAKE clock/session/local HTTP; NOT Backend E2E")
    check(failed == 0)
}

private fun http() {
    var code = 200
    var body = """{"orderId":501,"status":"PICKED_UP","updatedAt":"2026-11-12T12:00:01","deliveredAt":null}"""
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
    server.start()
    // Start with the shared transport's default retry policy; production's write adapter must disable it.
    val client = OkHttpClient.Builder().build()
    val auth = DriverAuthRepository()
    val api = DriverOrderModule.api(Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/api/")
        .client(client).addConverterFactory(GsonConverterFactory.create()).build())
    val repository = DriverOrderRepository(api, auth)
    fun send(id: Long = 501, target: String = "PICKED_UP") = runBlocking { repository.changeStatus(id, target) }
    try {
        test("Real Retrofit uses PATCH, exact id path and status-only body") {
            check(send() is DriverDeliveryResult.Updated)
            check(requests.last() == ("PATCH /api/driver/orders/501/status" to "{\"status\":\"PICKED_UP\"}"))
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
            "missing timestamp" to "{}", "wrong status" to good.replace("PICKED_UP", "DELIVERING"),
            "missing nullable field" to good.replace(",\"deliveredAt\":null", ""))) {
            test("Malformed response $name cannot confirm delivery") { code = 200; body = invalid; check(send() is DriverDeliveryResult.Uncertain) }
        }
        test("DELIVERED requires a real deliveredAt, not null") {
            body = good.replace("PICKED_UP", "DELIVERED"); check(send(target = "DELIVERED") is DriverDeliveryResult.Uncertain)
            body = body.replace("\"deliveredAt\":null", "\"deliveredAt\":\"2026-11-12T12:00:01\"")
            check(send(target = "DELIVERED") is DriverDeliveryResult.Updated)
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
                val response = async(Dispatchers.IO) { repository.changeStatus(501, "PICKED_UP") }
                check(arrived!!.await(3, TimeUnit.SECONDS)); auth.token = "fixture-next-account"
                release!!.countDown(); check(response.await() is DriverDeliveryResult.Updated)
                check(seenAuthorization == "Bearer fixture-token")
            }
            arrived = null; release = null
        }
        test("Missing JWT sends no PATCH") {
            val count = requests.size; auth.token = null; check(send() == DriverDeliveryResult.SessionExpired && requests.size == count)
        }
    } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); server.stop(0); executor.shutdownNow() }
}
