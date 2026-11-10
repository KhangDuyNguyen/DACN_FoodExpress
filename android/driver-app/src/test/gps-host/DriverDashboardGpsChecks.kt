// Standalone JVM/mock tests, executed by run_checks.py; not a JUnit/Android suite.
import androidx.lifecycle.*
import java.io.IOException
import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import vn.hutech.foodexpress.driver.data.auth.*
import vn.hutech.foodexpress.driver.data.location.*
import vn.hutech.foodexpress.driver.data.profile.*
import vn.hutech.foodexpress.driver.data.status.*
import vn.hutech.foodexpress.driver.ui.dashboard.*

// One virtual scheduler drives coroutine delays and the monotonic sensor clock.
// No wall-clock sleeps or additional test dependency.
@OptIn(InternalCoroutinesApi::class)
private class Clock : CoroutineDispatcher(), Delay {
    private data class Task(val at: Long, val order: Long, val block: Runnable, var cancelled: Boolean = false)
    private val tasks = PriorityQueue(compareBy<Task> { it.at }.thenBy { it.order })
    private var sequence = 0L
    var nowMs = 1_000_000L
        private set
    override fun dispatch(context: CoroutineContext, block: Runnable) { enqueue(0, block) }
    private fun enqueue(delayMs: Long, block: Runnable): DisposableHandle {
        val task = Task(nowMs + delayMs, sequence++, block)
        tasks.add(task)
        return DisposableHandle { task.cancelled = true }
    }
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val task = enqueue(timeMillis, Runnable { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) })
        continuation.invokeOnCancellation { task.dispose() }
    }
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext) = enqueue(timeMillis, block)
    fun runCurrent() = advanceBy(0)
    fun advanceBy(ms: Long) {
        val target = nowMs + ms
        var steps = 0
        while (tasks.peek()?.at?.let { it <= target } == true) {
            check(++steps < 100_000) { "Scheduler did not settle" }
            val task = tasks.remove()
            nowMs = task.at
            if (!task.cancelled) task.block.run()
        }
        nowMs = target
    }
}

private data class Post(val atMs: Long, val lat: Double)
private class Rig(status: String = "APPROVED") : AutoCloseable {
    val clock = Clock().also { testDispatcher = it }
    val calls = mutableListOf<String>()
    val posts = mutableListOf<Post>()
    val profile = DriverProfileRepository(DriverProfile(fullName = "FIXTURE", phone = "0900000000",
        vehicleType = "CAR", status = status, isOnline = false), calls)
    val source = DriverForegroundLocationSource { clock.nowMs * 1_000_000 }
    val auth = DriverAuthRepository()
    var postGate: CompletableDeferred<Unit>? = null
    var onGate: CompletableDeferred<Unit>? = null
    var offGate: CompletableDeferred<Unit>? = null
    var offCode = 200
    var locationCode = 201
    val sentSessions = mutableListOf<Pair<String, Boolean>>()
    var network = false
    private val locationApi = object : DriverLocationApi {
        override suspend fun recordLocation(authorization: String, request: DriverLocationRequest): Response<DriverLocationResponse> {
            calls.add("POST"); posts.add(Post(clock.nowMs, request.lat)); postGate?.await()
            if (network) throw IOException("fixture disconnected")
            if (locationCode != 201) return Response.error(locationCode, "{}".toResponseBody())
            return Response.success(201, DriverLocationResponse(request.lat, request.lng, "2026-10-31T10:00:00"))
        }
    }
    private val statusApi = object : DriverStatusApi {
        override suspend fun updateStatus(authorization: String, request: DriverStatusRequest): Response<DriverStatusResponse> {
            calls.add("PATCH:${request.isOnline}")
            sentSessions.add(authorization to request.isOnline)
            if (request.isOnline) onGate?.await()
            else offGate?.await()
            if (network) throw IOException("fixture disconnected")
            if (!request.isOnline && offCode != 200) return Response.error(offCode, "{}".toResponseBody())
            profile.profile = profile.profile.copy(isOnline = request.isOnline)
            return Response.success(DriverStatusResponse(request.isOnline))
        }
    }
    val vm = DriverDashboardViewModel(profile, DriverStatusRepository(statusApi, auth),
        DriverLocationRepository(locationApi, auth), source)
    val state get() = vm.state.value
    fun start() {
        vm.setForeground(true); vm.activateSession(1)
        // Existing Week-3 cases model a Backend-confirmed idle driver, not an unknown startup.
        vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false)
        clock.runCurrent(); check(!state.loading)
    }
    fun online() { vm.changeReceivingOrders(true); clock.runCurrent(); check(state.profile?.isOnline == true && !state.saving) }
    fun delayGetBeforeUpload(frozen: Boolean) {
        clock.advanceBy(19_000)
        if (frozen) source.fixed = DriverLocationFix(11.0, 106.0, source.nowNanos() - 10_000_000_000L, 1L, 5f)
        profile.gate = CompletableDeferred()
        vm.load(); clock.runCurrent()
        clock.advanceBy(7_000) // GET owns mutex; monitor queues the 20s upload.
        check(state.loading && posts.size == 1)
    }
    fun releaseGet() { profile.gate!!.complete(Unit); clock.runCurrent(); check(!state.loading) }
    override fun close() { vm.clear(); clock.runCurrent(); finishTestScopes(); clock.runCurrent() }
}

fun main() {
    var passed = 0
    var failed = 0
    fun test(name: String, body: () -> Unit) {
        try { body(); passed++; println("PASS $name") }
        catch (error: Throwable) { failed++; println("FAIL $name: ${error.message}"); finishTestScopes() }
    }
    test("queued stale snapshot is replaced by latest fix after slow GET") {
        Rig().use { r ->
            r.start(); r.online(); r.delayGetBeforeUpload(frozen = true)
            r.source.fixed = null
            r.releaseGet()
            check(r.state.profile?.isOnline == true && "PATCH:false" !in r.calls) { "Unexpected Offline despite fresh GPS: ${r.calls}" }
            check(r.posts.size == 2 && r.posts.last().lat == 10.0) { "Did not upload latest valid fix" }
        }
    }
    test("actual periodic POSTs stay at least 20s apart after mutex wait") {
        Rig().use { r ->
            r.start(); r.online(); r.delayGetBeforeUpload(frozen = false); r.releaseGet()
            check(r.posts.size == 2)
            r.clock.advanceBy(19_000)
            check(r.posts.size == 2) { "Early POSTs at ${r.posts.map { it.atMs }}" }
            r.clock.advanceBy(1_000)
            check(r.posts.size == 3)
            val gaps = r.posts.zipWithNext { a, b -> b.atMs - a.atMs }
            check(gaps.all { it >= DriverLocationPolicy.UPLOAD_INTERVAL_MS }) { "POST gaps: $gaps" }
            println("EVIDENCE monotonic POST gaps (ms)=$gaps")
        }
    }
    test("no replacement fix skips queued upload; real 45s loss still sends OFF") {
        Rig().use { r ->
            r.start(); r.online(); r.delayGetBeforeUpload(frozen = true); r.releaseGet()
            check(r.state.profile?.isOnline == true && "PATCH:false" !in r.calls)
            check(r.posts.size == 1)
            r.clock.advanceBy(27_000); check(r.state.profile?.isOnline == true)
            r.clock.advanceBy(1_000); check(r.state.offlineConfirmed)
            check(r.calls.last() == "PATCH:false")
        }
    }
    test("queued upload cannot survive a newer OFF intent") {
        Rig().use { r ->
            r.start(); r.online(); r.delayGetBeforeUpload(frozen = false)
            r.vm.changeReceivingOrders(false); r.releaseGet()
            check(r.state.offlineConfirmed && r.posts.size == 1)
            check(r.calls.count { it == "PATCH:false" } == 1)
        }
    }
    test("queued upload cannot survive logout or a different account") {
        Rig().use { r ->
            r.start(); r.online(); r.delayGetBeforeUpload(frozen = false)
            r.vm.clear(); r.profile.profile = r.profile.profile.copy(fullName = "OTHER", isOnline = false)
            r.profile.gate = null
            r.vm.activateSession(2); r.clock.runCurrent()
            check(r.state.profile?.fullName == "OTHER" && r.state.profile?.isOnline == false)
            check(r.posts.size == 1)
        }
    }
    test("initial ON is POST then PATCH true; not throttled by a recent OFF") {
        Rig().use { r ->
            r.start(); r.online(); check(r.calls.take(3) == listOf("GET", "POST", "PATCH:true"))
            r.vm.changeReceivingOrders(false); r.clock.runCurrent(); check(r.state.offlineConfirmed)
            r.clock.advanceBy(1_000); r.online()
            check(r.posts.size == 2 && r.posts[1].atMs - r.posts[0].atMs == 1_000L)
        }
    }
    test("OFF does not require GPS; no optimistic ACK") {
        Rig().use { r ->
            r.start(); r.online(); r.source.status = DriverGpsStatus.SERVICES_OFF
            r.vm.changeReceivingOrders(false)
            check(r.state.profile?.isOnline == true && !r.state.offlineConfirmed)
            r.clock.runCurrent(); check(r.state.offlineConfirmed && r.posts.size == 1)
        }
    }
    test("rapid ON then OFF during POST never PATCHes true") {
        Rig().use { r ->
            r.start(); r.postGate = CompletableDeferred(); r.vm.changeReceivingOrders(true); r.clock.runCurrent()
            r.vm.changeReceivingOrders(true); r.vm.changeReceivingOrders(false); r.vm.changeReceivingOrders(false)
            r.postGate!!.complete(Unit); r.clock.runCurrent()
            check("PATCH:true" !in r.calls && r.state.offlineConfirmed && r.posts.size == 1)
        }
    }
    test("OFF during PATCH true serializes compensating OFF without duplicate ON") {
        Rig().use { r ->
            r.start(); r.onGate = CompletableDeferred(); r.vm.changeReceivingOrders(true); r.clock.runCurrent()
            r.vm.changeReceivingOrders(true); r.vm.changeReceivingOrders(false); r.vm.changeReceivingOrders(false)
            r.onGate!!.complete(Unit); r.clock.runCurrent()
            check(r.calls.filter { it.startsWith("PATCH") } == listOf("PATCH:true", "PATCH:false"))
            check(r.state.offlineConfirmed)
        }
    }
    test("network failure stays unknown; recovery and retry never auto-ON") {
        Rig().use { r ->
            r.start(); r.online(); r.network = true; r.clock.advanceBy(20_000)
            check(r.state.statusUnknown && !r.state.offlineConfirmed && r.state.profile?.isOnline == true)
            r.network = false; r.vm.load(); r.clock.runCurrent(); check(r.state.offlineConfirmed)
            r.clock.advanceBy(21_000); check(r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("GPS permission/services loss still OFF; restoration never auto-ON") {
        for (status in listOf(DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE, DriverGpsStatus.SERVICES_OFF)) {
            Rig().use { r ->
                r.start(); r.online(); r.source.status = status; r.clock.advanceBy(1_000)
                check(r.state.offlineConfirmed)
                r.source.status = DriverGpsStatus.READY; r.clock.advanceBy(21_000)
                check(r.calls.count { it == "PATCH:true" } == 1)
            }
        }
    }
    test("background requests OFF and foreground GET does not auto-ON") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.setForeground(false); r.clock.runCurrent()
            check(r.state.offlineConfirmed && !r.source.started)
            r.vm.setForeground(true); r.clock.runCurrent(); check(r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("refresh GET does not send duplicate POST or change intent") {
        Rig().use { r ->
            r.start(); r.vm.load(); r.vm.load(); r.clock.runCurrent()
            check(r.calls == listOf("GET", "GET"))
            r.online(); r.vm.load(); r.vm.load(); r.clock.runCurrent()
            check(r.posts.size == 1 && r.calls.count { it == "PATCH:true" } == 1 && "PATCH:false" !in r.calls)
        }
    }
    test("non-APPROVED cannot POST or enable Online") {
        for (status in listOf("PENDING", "REJECTED", "SUSPENDED")) {
            Rig(status).use { r -> r.start(); r.vm.changeReceivingOrders(true); r.clock.runCurrent(); check(r.calls == listOf("GET")) }
        }
    }
    test("ACTIVE background stops GPS without OFF; return resumes foreground tracking without ON") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.vm.setForeground(false); r.clock.runCurrent()
            check(!r.source.started && "PATCH:false" !in r.calls && r.state.profile?.isOnline == true)
            r.vm.setForeground(true); r.clock.runCurrent(); r.clock.advanceBy(21_000)
            check(r.source.started && r.posts.size >= 2 && "PATCH:false" !in r.calls)
            check(r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("ACTIVE Auth recheck and cold profile load cannot force OFF") {
        Rig().use { r ->
            r.profile.profile = r.profile.profile.copy(isOnline = true)
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            check("PATCH:false" !in r.calls && r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.vm.pauseSessionVerification(); r.clock.runCurrent()
            check(r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN && !r.source.started)
            r.vm.activateSession(1); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.clock.runCurrent(); check("PATCH:false" !in r.calls)
        }
    }
    test("ACTIVE permission services stale and inaccurate GPS never auto-OFF") {
        for (status in listOf(DriverGpsStatus.NO_PERMISSION, DriverGpsStatus.APPROXIMATE,
            DriverGpsStatus.SERVICES_OFF, DriverGpsStatus.STALE, DriverGpsStatus.INACCURATE, DriverGpsStatus.ERROR)) {
            Rig().use { r ->
                r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
                r.source.status = status; r.clock.advanceBy(60_000)
                check("PATCH:false" !in r.calls && r.state.profile?.isOnline == true)
            }
        }
    }
    test("ACTIVE upload network loss reports a warning without OFF and can recover") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.network = true; r.clock.advanceBy(20_000)
            check("PATCH:false" !in r.calls && r.state.messageIsError && r.state.profile?.isOnline == true)
            r.network = false; r.clock.advanceBy(20_000)
            check(r.posts.size == 3 && r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("UNKNOWN current-order error blocks switches and automatic OFF") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.UNKNOWN, false)
            r.vm.changeReceivingOrders(false); r.vm.setForeground(false); r.clock.runCurrent()
            r.vm.setForeground(true); r.clock.runCurrent(); r.network = true; r.clock.advanceBy(21_000)
            check("PATCH:false" !in r.calls && r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN)
        }
    }
    test("Accept pending protects background even when last current-order GET said IDLE") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, true)
            r.vm.changeReceivingOrders(false); r.vm.setForeground(false); r.clock.runCurrent()
            check(r.state.acceptPending && "PATCH:false" !in r.calls)
        }
    }
    test("ACTIVE manual OFF is blocked and already-Offline ACTIVE never auto-ON") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.vm.changeReceivingOrders(false); r.clock.runCurrent()
            check("PATCH:false" !in r.calls && r.state.message!!.contains("thực hiện"))
        }
        Rig().use { r ->
            r.start(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.vm.changeReceivingOrders(true); r.clock.advanceBy(30_000)
            check(r.calls.none { it.startsWith("PATCH") } && r.posts.isEmpty())
        }
    }
    test("OFF queued behind GET is rechecked when Accept becomes pending") {
        Rig().use { r ->
            r.start(); r.online(); r.profile.gate = CompletableDeferred()
            r.vm.load(); r.clock.runCurrent(); r.vm.changeReceivingOrders(false)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, true)
            r.profile.gate!!.complete(Unit); r.clock.runCurrent()
            check("PATCH:false" !in r.calls && !r.state.saving && r.state.acceptPending)
        }
    }
    test("Business OFF 409 is not ACK and is never automatically retried") {
        Rig().use { r ->
            r.start(); r.online(); r.offCode = 409; r.vm.changeReceivingOrders(false); r.clock.runCurrent()
            check(r.state.offlineConflict && r.state.statusUnknown && !r.state.offlineConfirmed)
            r.clock.advanceBy(65_000); r.vm.load(); r.clock.runCurrent(); r.clock.advanceBy(25_000)
            check(r.calls.count { it == "PATCH:false" } == 1)
            r.offCode = 200; r.vm.changeReceivingOrders(false); r.clock.runCurrent()
            check(r.state.offlineConfirmed && !r.state.offlineConflict)
        }
    }
    test("Logout ACTIVE or pending clears state without OFF cleanup") {
        for (pending in listOf(false, true)) Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, pending)
            r.vm.clear(); r.clock.runCurrent()
            check(r.state.profile == null && r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN && !r.source.started)
            check("PATCH:false" !in r.calls)
        }
    }
    test("Stale IDLE logout cleanup captures old JWT and ignores 409 without retry") {
        Rig().use { r ->
            r.start(); r.online(); r.offCode = 409
            r.vm.clear(); r.auth.token = "new-fixture-session"; r.clock.runCurrent(); r.clock.advanceBy(60_000)
            check(r.calls.count { it == "PATCH:false" } == 1 && r.state.profile == null)
            check(r.sentSessions.last() == ("Bearer fixture-token" to false))
        }
    }
    test("Account change rejects old knowledge and old OFF response cannot overwrite new UI") {
        Rig().use { r ->
            r.start(); r.online(); r.offGate = CompletableDeferred()
            r.vm.changeReceivingOrders(false); r.clock.runCurrent()
            r.vm.clear(); r.auth.token = "new-fixture-session"
            r.profile.profile = r.profile.profile.copy(fullName = "OTHER", isOnline = false)
            r.vm.activateSession(2)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN)
            r.offGate!!.complete(Unit); r.clock.runCurrent()
            check(r.state.profile?.fullName == "OTHER" && !r.state.offlineConfirmed)
            r.vm.updateDeliveryKnowledge(2, DriverOrderKnowledge.IDLE, false)
            check(r.state.orderKnowledge == DriverOrderKnowledge.IDLE)
        }
    }
    test("IDLE Auth recheck defers OFF until Backend confirms idle again") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.pauseSessionVerification(); r.clock.runCurrent()
            check("PATCH:false" !in r.calls && r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN)
            r.vm.activateSession(1); r.clock.runCurrent(); check("PATCH:false" !in r.calls)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false); r.clock.runCurrent()
            check(r.state.offlineConfirmed && r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("Late ON response during pending Accept does not trigger unguarded OFF or a new ON") {
        Rig().use { r ->
            r.start(); r.onGate = CompletableDeferred(); r.vm.changeReceivingOrders(true); r.clock.runCurrent()
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, true); r.vm.setForeground(false)
            r.onGate!!.complete(Unit); r.clock.runCurrent()
            check("PATCH:false" !in r.calls && r.calls.count { it == "PATCH:true" } == 1)
            r.vm.setForeground(true); r.clock.runCurrent()
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false); r.clock.runCurrent()
            check(r.state.offlineConfirmed && r.calls.count { it == "PATCH:true" } == 1)
        }
    }
    test("Expired session stops foreground GPS and invalidates delivery knowledge without auto-OFF") {
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.locationCode = 401; r.clock.advanceBy(20_000)
            check(r.state.sessionExpired && !r.source.started && r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN)
            check("PATCH:false" !in r.calls)
            r.vm.clear(); r.clock.runCurrent(); check(r.state.profile == null)
        }
    }
    test("Direct account activation cannot capture new account JWT for old owner cleanup") {
        Rig().use { r ->
            r.start(); r.online()
            r.auth.token = "new-fixture-session"
            r.profile.profile = r.profile.profile.copy(fullName = "OTHER", isOnline = false)
            r.vm.activateSession(2); r.clock.runCurrent()
            val cleanup = r.sentSessions.filter { !it.second }
            check(cleanup == listOf("Bearer fixture-token" to false))
            check(r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN && r.state.profile?.fullName == "OTHER")
        }
    }
    test("HOTFIX T01 UNKNOWN notice expires on ACTIVE without refresh or another GET") {
        Rig().use { r ->
            r.profile.profile = r.profile.profile.copy(isOnline = true)
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            check(r.state.message!!.contains("Chưa xác minh"))
            val calls = r.calls.toList()
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.orderKnowledge == DriverOrderKnowledge.ACTIVE)
            check(r.state.message?.contains("Chưa xác minh") != true)
            check(r.calls == calls && "PATCH:false" !in r.calls)
        }
    }
    test("HOTFIX T02 UNKNOWN notice expires on confirmed IDLE without auto ON") {
        Rig().use { r ->
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            r.vm.changeReceivingOrders(false); check(r.state.message!!.contains("Chưa xác minh"))
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false); r.clock.runCurrent()
            check(r.state.message == null && !r.state.messageIsError)
            check(r.calls == listOf("GET") && r.state.profile?.isOnline == false)
        }
    }
    test("HOTFIX T03 unresolved UNKNOWN retains warning and protection") {
        Rig().use { r ->
            r.profile.profile = r.profile.profile.copy(isOnline = true)
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            val warning = r.state.message
            repeat(3) { // Current Order timeout/500/409 all supply UNKNOWN, never IDLE.
                r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.UNKNOWN, false)
                r.clock.advanceBy(20_000)
                check(r.state.orderKnowledge == DriverOrderKnowledge.UNKNOWN && r.state.message == warning)
            }
            check(r.calls.none { it.startsWith("PATCH") })
        }
    }
    test("HOTFIX pending notice follows pending and expires only after resolution") {
        Rig().use { r ->
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            r.vm.changeReceivingOrders(false)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, true)
            check(r.state.message!!.contains("Nhận đơn") && r.state.acceptPending)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, true)
            check(r.state.message!!.contains("Nhận đơn"))
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.message?.contains("Nhận đơn") != true)
            check(r.calls.none { it.startsWith("PATCH") })
        }
    }
    for (profileFirst in listOf(true, false)) test("HOTFIX T05 Auth recheck restores ACTIVE; profileFirst=$profileFirst") {
        Rig().use { r ->
            r.profile.profile = r.profile.profile.copy(isOnline = true)
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            r.vm.setForeground(false); r.vm.pauseSessionVerification(); r.clock.runCurrent()
            r.profile.gate = CompletableDeferred()
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            if (profileFirst) r.releaseGet()
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            if (!profileFirst) r.releaseGet()
            r.clock.advanceBy(60_000)
            check(r.state.orderKnowledge == DriverOrderKnowledge.ACTIVE)
            check(r.state.message?.contains("Chưa xác minh") != true)
            check(r.calls.none { it.startsWith("PATCH") })
        }
    }
    test("HOTFIX T04 GPS and network errors survive delivery knowledge changes") {
        for (network in listOf(false, true)) Rig().use { r ->
            r.vm.setForeground(true); r.vm.activateSession(1); r.clock.runCurrent()
            r.vm.changeReceivingOrders(false) // First install a policy notice.
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false)
            if (network) r.network = true else r.source.status = DriverGpsStatus.SERVICES_OFF
            r.vm.changeReceivingOrders(true); r.clock.runCurrent()
            val error = r.state.message
            check(!error.isNullOrBlank() && r.state.messageIsError)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.message == error && r.state.messageIsError)
        }
        Rig().use { r ->
            r.start(); r.online(); r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.UNKNOWN, false)
            r.network = true; r.clock.advanceBy(20_000)
            val uploadError = r.state.message
            check(!uploadError.isNullOrBlank() && r.state.messageIsError)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.message == uploadError && r.state.messageIsError)
            check("PATCH:false" !in r.calls)
        }
    }
    test("HOTFIX T04 OFF 409 survives ACTIVE and IDLE; no automatic retry") {
        Rig().use { r ->
            r.start(); r.online(); r.offCode = 409; r.vm.changeReceivingOrders(false); r.clock.runCurrent()
            val conflict = r.state.message
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.ACTIVE, false)
            check(r.state.message == conflict && r.state.offlineConflict)
            r.vm.updateDeliveryKnowledge(1, DriverOrderKnowledge.IDLE, false); r.clock.advanceBy(60_000)
            check(r.state.message == conflict && r.state.offlineConflict && !r.state.offlineConfirmed)
            check(r.calls.count { it == "PATCH:false" } == 1)
        }
    }
    println("RESULT $passed PASS / $failed FAIL; JVM/mock, production VM/repositories/policy")
    check(failed == 0) { "$failed regression check(s) failed" }
}
