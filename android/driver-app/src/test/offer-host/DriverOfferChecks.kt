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
import vn.hutech.foodexpress.driver.ui.offer.*

private val serverNow = LocalDateTime.parse("2026-11-06T10:00:00")
private fun sample(id: Long = 1) = DriverOffer(id, 301, "FE301", "Quán thử nghiệm", "Địa chỉ quán thử",
    10.0, 106.0, "Địa chỉ giao thử", 1.23, serverNow, serverNow.plusSeconds(30))
private class TestClock(var millis: Long = 100_000) : OfferMonotonicClock { override fun nowMillis() = millis }
private class Source(private val clock: TestClock) : DriverOffersSource {
    var offers = listOf(sample())
    var serverTime = serverNow
    var loads = 0
    var activeLoads = 0
    var maxLoads = 0
    var loadGate: CompletableDeferred<Unit>? = null
    var actionGate: CompletableDeferred<Unit>? = null
    var loadResult: OffersResult? = null
    var actionResult: OfferActionResult? = null
    val actions = mutableListOf<PendingOfferAction>()
    override suspend fun load(): OffersResult {
        loads++; activeLoads++; maxLoads = maxOf(maxLoads, activeLoads)
        val start = clock.nowMillis()
        try { loadGate?.await(); return loadResult ?: OffersResult.Loaded(offers, serverTime, start) }
        finally { activeLoads-- }
    }
    override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult {
        actions.add(PendingOfferAction(offerId, orderId, action)); actionGate?.await()
        return actionResult ?: OfferActionResult.Confirmed(offerId, orderId, action.confirmedStatus)
    }
}
private data class Fixture(val vm: DriverOffersViewModel, val source: Source, val clock: TestClock, val saved: SavedStateHandle)
private fun fixture(active: Boolean = true): Fixture {
    val clock = TestClock(); val source = Source(clock); val saved = SavedStateHandle()
    val vm = DriverOffersViewModel(source, clock, saved)
    if (active) { vm.activateSession(7); vm.setOnline(true); vm.setForeground(true) }
    return Fixture(vm, source, clock, saved)
}
private suspend fun awaitCondition(predicate: () -> Boolean) { withTimeout(2_500) { while (!predicate()) delay(5) } }
private suspend fun Fixture.awaitOffer() = awaitCondition { vm.state.value.offer != null }

private var passed = 0
private var failed = 0
private suspend fun case(name: String, block: suspend () -> Unit) {
    try { block(); passed++; println("PASS $name") }
    catch (failure: Throwable) { failed++; println("FAIL $name: $failure"); failure.printStackTrace() }
    finally { finishTestScopes() }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun main() {
    Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
        testDispatcher = dispatcher
        runBlocking(dispatcher) {
            case("Countdown uses Backend delta, not device wall clock") {
                val deadline = OfferDeadline.from(serverNow, serverNow.plusSeconds(30), 100_000)
                check(deadline.remainingSeconds(101_500) == 29)
                check(deadline.remainingSeconds(129_999) == 1)
                check(deadline.remainingSeconds(130_000) == 0)
            }
            case("Countdown fractional Backend timestamp and conservative round trip") {
                val d = OfferDeadline.from(serverNow.plusNanos(250_000_000), serverNow.plusSeconds(30), 5_000)
                check(d.remainingSeconds(6_000) == 29)
                check(d.remainingSeconds(34_750) == 0)
            }
            case("No polling when signed out, Offline or in background") {
                val f = fixture(false)
                f.vm.setForeground(true); delay(30); check(f.source.loads == 0)
                f.vm.activateSession(7); delay(30); check(f.source.loads == 0)
                f.vm.setForeground(false); f.vm.setOnline(true); delay(30); check(f.source.loads == 0)
            }
            case("Only one poll loop; real 4 second cadence") {
                val f = fixture(); f.awaitOffer()
                repeat(10) { f.vm.activateSession(7); f.vm.setForeground(true); f.vm.setOnline(true) }
                delay(100); check(f.source.loads == 1)
                delay(4_050); check(f.source.loads == 2 && f.source.maxLoads == 1)
            }
            case("Background cancels GET and ignores its suspended result") {
                val f = fixture(false); f.source.loadGate = CompletableDeferred()
                f.vm.activateSession(7); f.vm.setOnline(true); f.vm.setForeground(true)
                awaitCondition { f.source.loads == 1 }; f.vm.setForeground(false)
                f.source.loadGate!!.complete(Unit); delay(50)
                check(f.vm.state.value.offer == null && f.source.activeLoads == 0)
            }
            case("Resume does not reset countdown and repeated GET cannot extend it") {
                val f = fixture(); f.awaitOffer(); check(f.vm.state.value.remainingSeconds == 30)
                f.vm.setForeground(false); f.clock.millis += 12_000
                f.vm.setForeground(true); awaitCondition { f.source.loads == 2 }; delay(30)
                check(f.vm.state.value.remainingSeconds == 18)
            }
            case("Expiry closes dialog, refreshes GET and suppresses same stale offer") {
                val f = fixture(); f.awaitOffer(); f.clock.millis += 30_000
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.EXPIRED }
                awaitCondition { f.source.loads >= 2 }
                check(f.vm.state.value.offer == null)
                f.vm.accept(); f.vm.reject(); check(f.source.actions.isEmpty())
            }
            case("GET removal uses neutral invalidation message") {
                val f = fixture(); f.awaitOffer(); f.source.offers = emptyList(); f.vm.refresh()
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.UNAVAILABLE }
                check(f.vm.state.value.offer == null && !f.vm.state.value.message!!.contains("Khách hàng đã hủy"))
            }
            case("Double Accept and opposing Reject make one POST; confirmed orderId only") {
                val f = fixture(); f.awaitOffer(); f.source.actionGate = CompletableDeferred()
                f.vm.accept(); f.vm.accept(); f.vm.reject(); f.vm.retryPending()
                awaitCondition { f.source.actions.size == 1 }
                check(f.vm.state.value.sending && f.vm.state.value.acceptedOrderId == null)
                f.source.actionGate!!.complete(Unit)
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPTED }
                check(f.vm.state.value.acceptedOrderId == 301L && f.source.actions.single().action == OfferAction.ACCEPT)
                val loaded = f.source.loads; f.vm.refresh(); delay(30); check(f.source.loads == loaded)
            }
            case("Reject confirmation resumes polling, never modifies Online") {
                val f = fixture(); f.awaitOffer(); f.source.offers = emptyList()
                f.vm.reject(); f.vm.reject()
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.REJECTED }
                awaitCondition { f.source.loads >= 2 }
                check(f.source.actions.size == 1 && f.source.actions.single().action == OfferAction.REJECT)
            }
            case("In-flight POST past deadline closes offer without claiming failure or allowing duplicates") {
                val f = fixture(); f.awaitOffer(); f.source.actionGate = CompletableDeferred()
                f.vm.accept(); awaitCondition { f.source.actions.size == 1 }
                f.clock.millis += 30_000
                awaitCondition { f.vm.state.value.offer == null }
                check(f.vm.state.value.sending && f.vm.state.value.acceptedOrderId == null)
                f.vm.retryPending(); f.vm.accept(); f.vm.reject(); delay(20)
                check(f.source.actions.size == 1)
                f.source.actionGate!!.complete(Unit)
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPTED }
            }
            case("Initial Accept 409 is unavailable and never successful") {
                val f = fixture(); f.awaitOffer(); f.source.actionResult = OfferActionResult.Unavailable
                f.vm.accept(); awaitCondition { f.vm.state.value.phase == DriverOfferPhase.UNAVAILABLE }
                check(f.vm.state.value.acceptedOrderId == null && f.vm.state.value.pending == null)
            }
            case("Lost Accept response is unknown; replay same POST after expiry and Offline") {
                val f = fixture(); f.awaitOffer(); f.source.actionResult = OfferActionResult.Unknown("Mất mạng")
                f.vm.accept(); awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPT_UNKNOWN }
                check(f.vm.state.value.acceptedOrderId == null)
                f.clock.millis += 60_000; f.vm.setOnline(false)
                f.source.actionResult = null; f.vm.retryPending()
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPTED }
                check(f.source.actions.size == 2 && f.source.actions[0] == f.source.actions[1])
            }
            case("An ambiguous Accept followed by 409 stays unknown") {
                val f = fixture(); f.awaitOffer(); f.source.actionResult = OfferActionResult.Unknown("Mất mạng")
                f.vm.accept(); awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPT_UNKNOWN }
                f.source.actionResult = OfferActionResult.Unavailable; f.vm.retryPending()
                awaitCondition { f.source.actions.size == 2 && !f.vm.state.value.sending }
                check(f.vm.state.value.phase == DriverOfferPhase.ACCEPT_UNKNOWN && f.vm.state.value.pending != null)
            }
            case("Reject 503 remains ambiguous and replays Reject only") {
                val f = fixture(); f.awaitOffer(); f.source.actionResult = OfferActionResult.Unknown("Có thể đã từ chối")
                f.vm.reject(); awaitCondition { f.vm.state.value.phase == DriverOfferPhase.REJECT_UNKNOWN }
                f.vm.accept(); check(f.source.actions.size == 1)
                f.source.offers = emptyList(); f.source.actionResult = null; f.vm.retryPending()
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.REJECTED }
                check(f.source.actions.all { it.action == OfferAction.REJECT } && f.source.actions.size == 2)
            }
            case("Pending intent saved before POST; recreated VM verifies same offer") {
                val f = fixture(); f.awaitOffer(); f.source.actionGate = CompletableDeferred()
                f.vm.accept(); awaitCondition { f.source.actions.isNotEmpty() }; f.vm.clear()
                f.source.actionGate = null; f.source.actionResult = null
                val restored = DriverOffersViewModel(f.source, f.clock, f.saved)
                restored.activateSession(7); restored.setForeground(true)
                check(restored.state.value.phase == DriverOfferPhase.ACCEPT_UNKNOWN)
                restored.retryPending(); awaitCondition { restored.state.value.phase == DriverOfferPhase.ACCEPTED }
                check(f.source.actions[0] == f.source.actions[1])
            }
            case("Different account cannot replay another driver's pending action") {
                val f = fixture(); f.awaitOffer(); f.source.actionResult = OfferActionResult.Unknown("Mất mạng")
                f.vm.accept(); awaitCondition { f.vm.state.value.pending != null && !f.vm.state.value.sending }
                f.vm.activateSession(8); f.vm.retryPending(); delay(20)
                check(f.vm.state.value.pending == null && f.source.actions.size == 1)
            }
            case("Confirmed order survives saved-state VM recreation") {
                val f = fixture(); f.awaitOffer(); f.vm.accept()
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.ACCEPTED }; f.vm.clear()
                val restored = DriverOffersViewModel(f.source, f.clock, f.saved); restored.activateSession(7)
                check(restored.state.value.acceptedOrderId == 301L && restored.state.value.phase == DriverOfferPhase.ACCEPTED)
            }
            case("Network GET failure is visible; manual retry recovers") {
                val f = fixture(false); f.source.loadResult = OffersResult.Failed("Lỗi mạng")
                f.vm.activateSession(7); f.vm.setOnline(true); f.vm.setForeground(true)
                awaitCondition { f.vm.state.value.phase == DriverOfferPhase.NETWORK_ERROR }
                f.source.loadResult = null; f.vm.refresh(); f.awaitOffer()
            }
            case("Session re-verification pauses polling and resumes without duplicates") {
                val f = fixture(); f.awaitOffer(); f.vm.pauseSessionVerification(); val calls = f.source.loads
                f.vm.refresh(); delay(30); check(f.source.loads == calls)
                f.vm.activateSession(7); awaitCondition { f.source.loads == calls + 1 }; check(f.source.maxLoads == 1)
            }
            case("401 stops polling and asks existing Auth to verify session") {
                val f = fixture(false); f.source.loadResult = OffersResult.SessionExpired
                f.vm.activateSession(7); f.vm.setOnline(true); f.vm.setForeground(true)
                awaitCondition { f.vm.state.value.sessionExpired }
                f.vm.refresh(); delay(30); check(f.source.loads == 1)
            }
            httpCases()
        }
    }
    println("RESULT: PASS=$passed FAIL=$failed")
    check(failed == 0)
}

private suspend fun httpCases() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var code = 200
    var json = ""
    val requests = mutableListOf<Triple<String, String, Int>>()
    server.createContext("/api/") { exchange ->
        val bodySize = exchange.requestBody.use { it.readBytes().size }
        synchronized(requests) {
            check(exchange.requestHeaders.getFirst("Authorization") == "Bearer fixture-token")
            requests.add(Triple(exchange.requestMethod, exchange.requestURI.path, bodySize))
        }
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    val client = OkHttpClient()
    val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/api/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(DriverOfferApi::class.java)
    val auth = DriverAuthRepository()
    val clock = TestClock()
    val repository = DriverOfferRepository(api, auth, clock)
    val offerJson = """{"offerId":1,"orderId":301,"orderCode":"FE301","restaurantName":"Quán thử","restaurantAddress":"Địa chỉ quán thử","pickupLat":10.0,"pickupLng":106.0,"deliveryAddress":"Địa chỉ giao thử","distanceKm":1.23,"offeredAt":"2026-11-06T10:00:00","expiresAt":"2026-11-06T10:00:30"}"""
    fun actionJson(status: String, id: Long = 1) = """{"offerId":$id,"orderId":301,"status":"$status","respondedAt":"2026-11-06T10:00:10"}"""
    try {
        case("Real Retrofit GET path, JWT header, JSON DTO and request clock") {
            json = """{"offers":[$offerJson],"serverTime":"2026-11-06T10:00:01.250"}"""
            val result = repository.load() as OffersResult.Loaded
            check(result.offers.single().distanceKm == 1.23 && result.serverTime.nano == 250_000_000)
            check(result.requestStartedMillis == clock.millis)
            check(requests.last() == Triple("GET", "/api/driver/offers", 0))
        }
        case("Real Retrofit Accept uses exact endpoint and no client driverId/body") {
            json = actionJson("ACCEPTED")
            check(repository.decide(OfferAction.ACCEPT, 1, 301) is OfferActionResult.Confirmed)
            check(requests.last() == Triple("POST", "/api/driver/offers/1/accept", 0))
        }
        case("Real Retrofit Reject uses exact endpoint and confirms REJECTED") {
            json = actionJson("REJECTED")
            check(repository.decide(OfferAction.REJECT, 1, 301) is OfferActionResult.Confirmed)
            check(requests.last() == Triple("POST", "/api/driver/offers/1/reject", 0))
        }
        case("HTTP 409 returns unavailable for either initial action") {
            code = 409; json = "{}"
            for (action in OfferAction.entries) check(repository.decide(action, 1, 301) == OfferActionResult.Unavailable)
        }
        case("HTTP 503 Reject never claims definitive failure or success") {
            code = 503
            val result = repository.decide(OfferAction.REJECT, 1, 301) as OfferActionResult.Unknown
            check(result.message.contains("Có thể đã từ chối"))
        }
        case("HTTP 500 Accept remains unknown") {
            code = 500
            check(repository.decide(OfferAction.ACCEPT, 1, 301) is OfferActionResult.Unknown)
        }
        case("Wrong status or mismatched IDs in HTTP 200 are not success") {
            code = 200
            for (invalid in listOf(actionJson("REJECTED"), actionJson("ACCEPTED", 2), "{}", "not-json")) {
                json = invalid
                check(repository.decide(OfferAction.ACCEPT, 1, 301) is OfferActionResult.Unknown)
            }
        }
        case("Empty GET is valid; malformed payload and invalid coordinates are rejected") {
            json = """{"offers":[],"serverTime":"2026-11-06T10:00:01"}"""
            check((repository.load() as OffersResult.Loaded).offers.isEmpty())
            for (invalid in listOf("{}", "not-json",
                """{"offers":[${offerJson.replace("10.0", "91.0")}],"serverTime":"2026-11-06T10:00:01"}""",
                """{"offers":[$offerJson],"serverTime":"2026-11-06T10:00:30"}""")) {
                json = invalid; check(repository.load() is OffersResult.Failed)
            }
        }
        case("HTTP 401 and absent session use existing Auth contract") {
            code = 401
            check(repository.load() == OffersResult.SessionExpired)
            check(repository.decide(OfferAction.ACCEPT, 1, 301) == OfferActionResult.SessionExpired)
            auth.token = null; val before = requests.size
            check(repository.load() == OffersResult.SessionExpired)
            check(repository.decide(OfferAction.REJECT, 1, 301) == OfferActionResult.SessionExpired)
            check(requests.size == before)
        }
        case("Actual lost HTTP connection preserves ambiguous POST") {
            server.stop(0); auth.token = "fixture-token"
            check(repository.load() is OffersResult.Failed)
            check(repository.decide(OfferAction.ACCEPT, 1, 301) is OfferActionResult.Unknown)
        }
    } finally {
        server.stop(0)
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}
