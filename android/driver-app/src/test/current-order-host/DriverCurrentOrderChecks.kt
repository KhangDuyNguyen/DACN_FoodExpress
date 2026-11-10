import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.finishTestScopes
import androidx.lifecycle.testDispatcher
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.hutech.foodexpress.driver.data.auth.DriverAuthRepository
import vn.hutech.foodexpress.driver.data.offer.*
import vn.hutech.foodexpress.driver.data.order.*
import vn.hutech.foodexpress.driver.ui.offer.*
import vn.hutech.foodexpress.driver.ui.order.*

private val now = LocalDateTime.of(2026, 11, 6, 12, 0)
private fun order(id: Long = 301) = DriverCurrentOrder(id, "FAKE-$id", "PLACED", now, now,
    901, "FAKE Restaurant", "FAKE Pickup", null, 10.0, 106.0, "FAKE Receiver", "FAKE Contact",
    "FAKE Delivery", 11.0, 107.0, null)
private fun loaded(value: DriverCurrentOrder?) = CurrentOrderResult.Loaded(value, now)

private class Orders : DriverCurrentOrderSource {
    var result: CurrentOrderResult = loaded(null)
    var nextDelay = 0L
    var nonCooperative = false
    var count = 0; var active = 0; var maxActive = 0
    override suspend fun load(): CurrentOrderResult {
        count++; active++; maxActive = maxOf(maxActive, active)
        val snapshot = result
        val wait = nextDelay; nextDelay = 0
        try {
            if (nonCooperative) withContext(NonCancellable) { delay(wait) } else delay(wait)
            return snapshot
        } finally { active-- }
    }
}
private class Offers(val clock: VirtualClock, val orders: Orders) : DriverOffersSource {
    var count = 0; var actions = 0
    var decision: OfferActionResult? = null
    var decisionDelay = 0L
    override suspend fun load(): OffersResult {
        count++
        return OffersResult.Loaded(listOf(DriverOffer(991, 301, "FAKE-301", "FAKE Restaurant", "FAKE Pickup", 10.0, 106.0,
            "FAKE Delivery", 1.2, now, now.plusSeconds(30))), now, clock.nowMillis())
    }
    override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult {
        actions++
        delay(decisionDelay)
        val result = decision ?: OfferActionResult.Confirmed(offerId, orderId, action.confirmedStatus)
        if (result is OfferActionResult.Confirmed && action == OfferAction.ACCEPT) orders.result = loaded(order())
        return result
    }
}
private class Rig(val saved: SavedStateHandle = SavedStateHandle()) : AutoCloseable {
    val clock = VirtualClock().also { testDispatcher = it }
    val orders = Orders()
    val offerSource = Offers(clock, orders)
    var offers = DriverOffersViewModel(offerSource, clock, saved)
    var current = DriverCurrentOrderViewModel(orders)
    fun start(id: Long = 7, online: Boolean = true) {
        current.connectOffers(offers)
        offers.activateSession(id); offers.setOnline(online); offers.setForeground(true)
        current.setForeground(true); current.activateSession(id); clock.advance(0)
    }
    fun recreate() {
        current.clear(); offers.clear(); finishTestScopes()
        offers = DriverOffersViewModel(offerSource, clock, saved)
        current = DriverCurrentOrderViewModel(orders)
        start()
    }
    override fun close() { current.clear(); offers.clear(); finishTestScopes(); clock.advance(0) }
}
private var pass = 0
private var fail = 0
private fun case(name: String, block: () -> Unit) {
    try { block(); pass++; println("PASS $name") }
    catch (e: Throwable) { fail++; println("FAIL $name: $e"); e.printStackTrace() }
    finally { finishTestScopes() }
}

fun main() {
    case("Initial verification blocks offers until successful explicit null") {
        Rig().use { r ->
            r.orders.nextDelay = 500; r.start()
            check(r.current.state.value.loading && !r.current.state.value.verified && r.offerSource.count == 0)
            r.clock.advance(500)
            check(r.current.state.value.verified && r.current.state.value.order == null && r.offerSource.count == 1)
        }
    }
    case("Assigned PLACED is visible and blocks new offers") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.clock.advance(30_000)
            check(r.current.state.value.order?.status == "PLACED" && r.offerSource.count == 0) }
    }
    case("Current order loads while Offline without GPS dependency") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(online = false)
            check(r.current.state.value.order?.orderId == 301L && r.orders.count == 1 && r.offerSource.count == 0) }
    }
    case("Backend terminal transition represented by null removes card and unlocks offers") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.orders.result = loaded(null)
            r.current.refresh(); r.clock.advance(0)
            check(r.current.state.value.verified && r.current.state.value.order == null && r.offerSource.count == 1)
            check(r.offers.state.value.acceptedOrderId == null) }
    }
    case("Network failure retains labelled old order and blocks offers until recovery") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.orders.result = CurrentOrderResult.Failed("FAKE network")
            r.current.refresh(); r.clock.advance(0)
            check(!r.current.state.value.verified && r.current.state.value.order?.orderId == 301L && r.current.state.value.error != null)
            r.orders.result = loaded(null); r.clock.advance(15_000)
            check(r.current.state.value.verified && r.offerSource.count > 0) }
    }
    case("Initial network error is not confirmed idle") {
        Rig().use { r -> r.orders.result = CurrentOrderResult.Failed("FAKE offline"); r.start()
            check(!r.current.state.value.verified && r.offerSource.count == 0) }
    }
    case("401 requests Auth verification and keeps offers stopped") {
        Rig().use { r -> r.orders.result = CurrentOrderResult.SessionExpired; r.start()
            check(r.current.state.value.sessionExpired && r.offerSource.count == 0) }
    }
    case("Logout clears private data; same-account login restores from Backend") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.current.clear(); r.offers.clear()
            check(r.current.state.value.order == null)
            r.start(); check(r.current.state.value.order?.orderId == 301L && r.orders.count == 2) }
    }
    case("New ViewModels restore order from Backend, not accepted message") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.recreate()
            check(r.current.state.value.order?.orderId == 301L && r.offerSource.count == 0) }
    }
    case("Accept confirmation triggers current-order GET and replaces operation marker") {
        Rig().use { r -> r.start(); r.offers.accept(); r.clock.advance(0)
            check(r.current.state.value.order?.orderId == 301L && r.offers.state.value.acceptedOrderId == null)
            check(r.offers.state.value.phase != DriverOfferPhase.ACCEPTED)
            val count = r.offerSource.count; r.clock.advance(30_000); check(r.offerSource.count == count) }
    }
    case("Ambiguous Accept is never cleared by null GET or network failure") {
        Rig().use { r -> r.start(); r.offerSource.decision = OfferActionResult.Unknown("FAKE lost reply")
            r.offers.accept(); r.clock.advance(0); val count = r.offerSource.count
            r.current.refresh(); r.clock.advance(0)
            check(r.current.state.value.verified && r.offers.state.value.pending?.action == OfferAction.ACCEPT)
            r.orders.result = CurrentOrderResult.Failed("FAKE network"); r.current.refresh(); r.clock.advance(0)
            check(r.offers.state.value.pending != null && r.offerSource.count == count)
            r.offerSource.decision = null; r.offers.retryPending(); r.clock.advance(0)
            check(r.current.state.value.order?.orderId == 301L && r.offers.state.value.pending == null) }
    }
    case("Saved ambiguous Accept survives VM recreation and null GET") {
        Rig().use { r -> r.start(); r.offerSource.decision = OfferActionResult.Unknown("FAKE lost reply")
            r.offers.accept(); r.clock.advance(0); r.recreate()
            check(r.offers.state.value.pending?.action == OfferAction.ACCEPT && r.offers.state.value.offer == null) }
    }
    case("Pre-Accept late null GET cannot overwrite post-Accept order") {
        Rig().use { r -> r.start(); r.orders.nonCooperative = true; r.orders.nextDelay = 3_000
            r.current.refresh(); r.clock.advance(0); r.offers.accept(); r.clock.advance(0)
            check(r.current.state.value.order?.orderId == 301L)
            r.clock.advance(3_000); check(r.current.state.value.order?.orderId == 301L && r.offerSource.actions == 1) }
    }
    case("GET completing during Accept is re-read using the new decision revision") {
        Rig().use { r -> r.start(); r.orders.result = loaded(order(999)); r.orders.nextDelay = 100
            r.current.refresh(); r.clock.advance(0)
            r.orders.result = loaded(null); r.offerSource.decisionDelay = 500
            r.offers.accept(); r.clock.advance(100)
            check(r.orders.count == 3 && r.current.state.value.order == null)
            check(r.offers.state.value.pending?.action == OfferAction.ACCEPT)
            r.clock.advance(400); check(r.current.state.value.order?.orderId == 301L) }
    }
    case("Account switch ignores non-cooperative previous-account response") {
        Rig().use { r -> r.orders.result = loaded(order()); r.orders.nextDelay = 3_000; r.orders.nonCooperative = true; r.start()
            r.current.pauseSessionVerification(); r.offers.clear()
            check(r.current.state.value.order == null)
            r.orders.result = loaded(order(302)); r.start(id = 8); r.clock.advance(3_000)
            check(r.current.state.value.order?.orderId == 302L && r.current.state.value.ownerId == 8L) }
    }
    case("Repeated refresh joins in-flight request, indicator ends, no concurrent loop") {
        Rig().use { r -> r.start(); r.orders.nextDelay = 500; r.current.refresh(); r.clock.advance(0)
            repeat(20) { r.current.refresh(); r.current.activateSession(7); r.current.setForeground(true) }
            check(r.current.state.value.loading); r.clock.advance(500)
            check(!r.current.state.value.loading && r.orders.count == 2 && r.orders.maxActive == 1)
            r.clock.advance(15_000); check(r.orders.count == 3 && r.orders.maxActive == 1) }
    }
    case("Foreground polls at 15s; background stops and return verifies again") {
        Rig().use { r -> r.orders.result = loaded(order()); r.start(); r.clock.advance(30_000); check(r.orders.count == 3)
            r.current.setForeground(false); r.clock.advance(60_000); check(r.orders.count == 3)
            r.current.setForeground(true); r.clock.advance(0); check(r.orders.count == 4) }
    }
    case("Reject retains idle verification and continues original offer polling") {
        Rig().use { r -> r.start(); r.offers.reject(); r.clock.advance(0)
            check(r.offers.state.value.phase == DriverOfferPhase.REJECTED && r.offers.state.value.pending == null)
            val count = r.offerSource.count; r.clock.advance(4_000); check(r.offerSource.count > count) }
    }
    case("Unverified Auth pause clears order and ignores earlier request") {
        Rig().use { r -> r.orders.result = loaded(order()); r.orders.nextDelay = 1_000; r.start()
            r.current.pauseSessionVerification(); r.clock.advance(1_000)
            check(r.current.state.value.order == null && !r.current.state.value.verified) }
    }
    httpChecks()
    println("RESULT: PASS=$pass FAIL=$fail; fake sessions/Backend, real Kotlin VM/Retrofit/Repository")
    check(fail == 0)
}

private fun httpChecks() {
    val fixture = """{"orderId":301,"orderCode":"FAKE-301","status":"PLACED","createdAt":"2026-11-06T12:00:00","updatedAt":"2026-11-06T12:00:00","restaurantId":901,"restaurantName":"FAKE Restaurant","restaurantAddress":"FAKE Pickup","restaurantPhone":null,"pickupLat":10.0,"pickupLng":106.0,"receiverName":"FAKE Receiver","receiverPhone":"FAKE Contact","deliveryAddress":"FAKE Delivery","deliveryLat":11.0,"deliveryLng":107.0,"note":null}"""
    fun body(order: String = fixture) = """{"currentOrder":$order,"serverTime":"2026-11-06T12:00:00"}"""
    var response = body(); var status = 200; var dropped = false
    val requests = mutableListOf<String>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val executor = Executors.newSingleThreadExecutor()
    server.executor = executor
    server.createContext("/api/driver/orders/current") { exchange ->
        requests.add(exchange.requestMethod + " " + exchange.requestURI.toString())
        if (dropped) exchange.close() else {
            check(exchange.requestHeaders.getFirst("Authorization")?.startsWith("Bearer ") == true)
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
    server.start()
    val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
    val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/api/")
        .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(DriverOrderApi::class.java)
    val auth = DriverAuthRepository()
    val repository = DriverOrderRepository(api, auth)
    fun load() = runBlocking { repository.load() }
    try {
        case("Real Retrofit uses GET current endpoint without driverId") {
            val result = load() as CurrentOrderResult.Loaded
            check(result.order == order() && requests.last() == "GET /api/driver/orders/current")
        }
        case("Real Retrofit explicit null confirms idle") { response = body("null"); check((load() as CurrentOrderResult.Loaded).order == null) }
        for (state in listOf("PENDING_PAYMENT", "PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "DELIVERING")) {
            case("Contract active status $state") {
                response = body(fixture.replace("PLACED", state)); check((load() as CurrentOrderResult.Loaded).order!!.status == state)
            }
        }
        val invalid = linkedMapOf(
            "missing currentOrder" to """{"serverTime":"2026-11-06T12:00:00"}""",
            "missing serverTime" to """{"currentOrder":null}""",
            "malformed JSON" to "not-json", "empty object" to "{}",
            "missing required order data" to body("{}"),
            "missing nullable field" to body(fixture.replace(",\"note\":null", "")),
            "out of range pickup" to body(fixture.replace("\"pickupLat\":10.0", "\"pickupLat\":91.0")),
            "invalid delivery coordinate" to body(fixture.replace("\"deliveryLng\":107.0", "\"deliveryLng\":181.0")),
            "infinite coordinate" to body(fixture.replace("\"pickupLat\":10.0", "\"pickupLat\":1e309")),
            "null coordinate" to body(fixture.replace("\"deliveryLat\":11.0", "\"deliveryLat\":null")),
            "bad timestamp" to body(fixture.replace("2026-11-06T12:00:00", "yesterday")),
            "zero id" to body(fixture.replace("\"orderId\":301", "\"orderId\":0")),
            "array instead of order" to body("[]"),
            "terminal object DELIVERED" to body(fixture.replace("PLACED", "DELIVERED")),
            "terminal object CANCELLED" to body(fixture.replace("PLACED", "CANCELLED")),
            "terminal object REJECTED" to body(fixture.replace("PLACED", "REJECTED")),
        )
        for ((name, json) in invalid) case("Invalid response: $name stays unverified") {
            response = json; check(load() is CurrentOrderResult.Failed)
        }
        for (code in listOf(403, 404, 409, 500, 503)) case("HTTP $code is error, never idle") {
            status = code; response = "{}"; check(load() is CurrentOrderResult.Failed)
        }
        case("401 uses existing Auth expiry contract") { status = 401; check(load() == CurrentOrderResult.SessionExpired) }
        case("Missing session sends no request") { auth.token = null; val count = requests.size; check(load() == CurrentOrderResult.SessionExpired && requests.size == count) }
        case("Lost real HTTP connection never becomes idle") {
            auth.token = "fixture-token"; status = 200; dropped = true; check(load() is CurrentOrderResult.Failed)
        }
    } finally {
        client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll(); server.stop(0); executor.shutdownNow()
    }
}
