import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.finishTestScopes
import androidx.lifecycle.testDispatcher
import java.time.LocalDateTime
import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import vn.hutech.foodexpress.driver.data.offer.*
import vn.hutech.foodexpress.driver.ui.offer.*

@OptIn(InternalCoroutinesApi::class)
private class VirtualClock : CoroutineDispatcher(), Delay, OfferMonotonicClock {
    private data class Task(val at: Long, val sequence: Long, val block: Runnable, var cancelled: Boolean = false)
    private val tasks = PriorityQueue(compareBy<Task> { it.at }.thenBy { it.sequence })
    private var sequence = 0L
    var now = 100_000L
        private set
    override fun nowMillis() = now
    override fun dispatch(context: CoroutineContext, block: Runnable) { enqueue(0, block) }
    private fun enqueue(delay: Long, block: Runnable): DisposableHandle {
        val task = Task(now + delay, sequence++, block)
        tasks.add(task)
        return DisposableHandle { task.cancelled = true }
    }
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val task = enqueue(timeMillis, Runnable { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) })
        continuation.invokeOnCancellation { task.dispose() }
    }
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext) = enqueue(timeMillis, block)
    fun advance(ms: Long) {
        val end = now + ms
        var steps = 0
        while (tasks.peek()?.at?.let { it <= end } == true) {
            check(++steps < 100_000) { "Virtual tasks did not settle" }
            val next = tasks.remove(); now = next.at
            if (!next.cancelled) next.block.run()
        }
        now = end
    }
}

private class FakeBackend(val time: VirtualClock) : DriverOffersSource {
    private val epoch = LocalDateTime.of(2026, 11, 6, 12, 0)
    fun serverNow() = epoch.plusNanos((time.now - 100_000) * 1_000_000)
    var offer: DriverOffer? = null
    var beforeServerTimeMs = 0L
    var afterServerTimeMs = 0L
    var makeFollowingRequestsFast = false
    var active = 0
    var maxActive = 0
    var loadedFailure: OffersResult? = null
    var decision: OfferActionResult? = null
    var decisionGate: CompletableDeferred<Unit>? = null
    var reply: (suspend (Long) -> OffersResult)? = null
    val actions = mutableListOf<OfferAction>()
    val starts = mutableListOf<Long>()
    val events = mutableListOf<String>()
    fun create(id: Long = 991, ttl: Long = 30_000) {
        val now = serverNow()
        offer = DriverOffer(id, 99301, "FAKE-POLL-$id", "FAKE Restaurant", "FAKE Pickup", 10.0, 106.0,
            "FAKE Delivery", 1.23, now, now.plusNanos(ttl * 1_000_000))
        events.add("FAKE_CREATE t=${time.now - 100_000}ms offerId=$id expiresAt=${offer!!.expiresAt}")
    }
    override suspend fun load(): OffersResult {
        val started = time.now
        starts.add(started); active++; maxActive = maxOf(maxActive, active)
        events.add("GET_START t=${started - 100_000}ms")
        try {
            reply?.let { return it(started) }
            delay(beforeServerTimeMs)
            val now = serverNow()
            val body = listOfNotNull(offer).filter { it.expiresAt > now }
            delay(afterServerTimeMs)
            events.add("GET_RETURN t=${time.now - 100_000}ms serverTime=$now offers=${body.map { it.offerId }}")
            if (makeFollowingRequestsFast) {
                beforeServerTimeMs = 0; afterServerTimeMs = 0; makeFollowingRequestsFast = false
            }
            return loadedFailure ?: OffersResult.Loaded(body, now, started)
        } finally { active-- }
    }
    override suspend fun decide(action: OfferAction, offerId: Long, orderId: Long): OfferActionResult {
        actions.add(action)
        val result = decision ?: OfferActionResult.Confirmed(offerId, orderId, action.confirmedStatus)
        decisionGate?.await() // Delay the response, not the simulated Backend decision.
        if (result is OfferActionResult.Confirmed) offer = null
        return result
    }
}

private class Rig(val saved: SavedStateHandle = SavedStateHandle()) : AutoCloseable {
    val time = VirtualClock().also { testDispatcher = it }
    val backend = FakeBackend(time)
    var vm = DriverOffersViewModel(backend, time, saved)
    private val observers = CoroutineScope(SupervisorJob() + time)
    private var watch: Job? = null
    fun watchState() {
        watch?.cancel()
        watch = observers.launch {
            vm.state.collect { state ->
                state.offer?.let { backend.events.add("VM_OFFER t=${time.now - 100_000}ms offerId=${it.offerId} remaining=${state.remainingSeconds}") }
            }
        }
    }
    fun start() {
        watchState()
        vm.activateSession(999001); vm.setOnline(true); vm.setForeground(true); time.advance(0)
    }
    fun trace() { backend.events.forEach(::println) }
    override fun close() { vm.clear(); observers.cancel(); finishTestScopes(); time.advance(0) }
}

private var pass = 0
private var fail = 0
private fun case(name: String, block: () -> Unit) {
    try { block(); pass++; println("PASS $name") }
    catch (error: Throwable) { fail++; println("FAIL $name: ${error.message}") }
    finally { finishTestScopes() }
}

fun main() {
    case("TC01 empty GET then newly created offer is detected automatically") {
        Rig().use { r ->
            r.start(); check(r.vm.state.value.offer == null)
            r.time.advance(1_000); r.backend.create()
            r.time.advance(2_999); check(r.vm.state.value.offer == null)
            r.time.advance(1); check(r.vm.state.value.offer?.offerId == 991L)
            check(r.backend.starts == listOf(100_000L, 104_000L)); r.trace()
        }
    }
    case("TC02 detection after many empty polls needs no refresh or lifecycle calls") {
        Rig().use { r ->
            r.start(); r.time.advance(21_000); r.backend.create(992)
            r.time.advance(3_000); check(r.vm.state.value.offer?.offerId == 992L)
            check(r.backend.maxActive == 1)
        }
    }
    case("TC03 unchanged foreground keeps exactly one loop for 60 seconds") {
        Rig().use { r ->
            r.start(); repeat(10) { r.vm.setOnline(true); r.vm.setForeground(true); r.vm.activateSession(999001) }
            r.time.advance(60_000); check(r.backend.starts.size == 16 && r.backend.maxActive == 1)
        }
    }
    case("TC03 background stops polling and foreground restarts once") {
        Rig().use { r ->
            r.start(); r.vm.setForeground(false); r.time.advance(20_000)
            check(r.backend.starts.size == 1)
            r.vm.setForeground(true); r.time.advance(0); check(r.backend.starts.size == 2)
        }
    }
    case("TC04 Offline or unverified session blocks polling") {
        Rig().use { r ->
            r.vm.activateSession(999001); r.vm.setForeground(true); r.time.advance(8_000)
            check(r.backend.starts.isEmpty())
            r.vm.setOnline(true); r.time.advance(0); check(r.backend.starts.size == 1)
            r.vm.pauseSessionVerification(); r.time.advance(8_000); check(r.backend.starts.size == 1)
            r.vm.activateSession(999001); r.time.advance(0); check(r.backend.starts.size == 2)
        }
    }
    case("TC04 temporary loading/saving gate cancels GET and resumes without parallel loops") {
        Rig().use { r ->
            r.backend.beforeServerTimeMs = 2_000; r.start(); r.time.advance(1_000)
            r.vm.setOnline(false); r.time.advance(3_000); check(r.backend.active == 0)
            r.vm.setOnline(true); r.time.advance(2_000)
            check(r.backend.starts.size == 2 && r.backend.maxActive == 1)
        }
    }
    case("TC05 recreated VM reads the same offer with remaining time, not fresh 30 seconds") {
        Rig().use { r ->
            r.backend.create(); val old = r.backend.offer!!; r.start(); r.time.advance(9_000)
            r.vm.clear(); r.vm = DriverOffersViewModel(r.backend, r.time, r.saved); r.start()
            check(r.vm.state.value.offer?.offerId == old.offerId)
            check(r.vm.state.value.offer?.expiresAt == old.expiresAt && r.vm.state.value.remainingSeconds == 21)
        }
    }
    case("TC05 a different offer has its own ID and new expiry") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.time.advance(30_000)
            check(r.vm.state.value.offer == null)
            r.backend.create(992); r.time.advance(4_000)
            check(r.vm.state.value.offer?.offerId == 992L && r.vm.state.value.remainingSeconds == 26)
        }
    }
    case("TC06 Accept and saved accepted marker block later polling") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.vm.accept(); r.time.advance(0)
            val count = r.backend.starts.size; r.time.advance(20_000); check(r.backend.starts.size == count)
            r.vm.clear(); r.vm = DriverOffersViewModel(r.backend, r.time, r.saved); r.start()
            check(r.vm.state.value.acceptedOrderId == 99301L && r.backend.starts.size == count)
        }
    }
    case("TC06 Reject resumes polling and can discover a later offer") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.vm.reject(); r.time.advance(0)
            r.time.advance(1_000); r.backend.create(992); r.time.advance(3_000)
            check(r.vm.state.value.offer?.offerId == 992L && r.backend.maxActive == 1)
        }
    }
    case("TC06 unknown action remains blocked until verified") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.backend.decision = OfferActionResult.Unknown("FAKE transport loss")
            r.vm.accept(); r.time.advance(0); val count = r.backend.starts.size
            r.time.advance(20_000); check(r.backend.starts.size == count && r.vm.state.value.pending != null)
        }
    }
    case("TC07 GET duration is added to the four-second delay between starts") {
        Rig().use { r ->
            r.backend.beforeServerTimeMs = 1_000; r.backend.afterServerTimeMs = 1_000
            r.start(); r.time.advance(12_000)
            check(r.backend.starts == listOf(100_000L, 106_000L, 112_000L)); r.trace()
        }
    }
    case("TC07 transient GET failure does not permanently stop polling") {
        Rig().use { r ->
            r.backend.loadedFailure = OffersResult.Failed("FAKE network down"); r.start()
            check(r.vm.state.value.phase == DriverOfferPhase.NETWORK_ERROR)
            r.backend.loadedFailure = null; r.backend.create(); r.time.advance(4_000)
            check(r.vm.state.value.offer != null)
        }
    }
    case("TC07 sessionExpired stops GET until re-verification") {
        Rig().use { r ->
            r.backend.loadedFailure = OffersResult.SessionExpired; r.start()
            r.time.advance(20_000); check(r.backend.starts.size == 1 && r.vm.state.value.sessionExpired)
        }
    }
    case("TC07 live offer returned after upstream delay should remain displayable") {
        Rig().use { r ->
            r.backend.create(); r.time.advance(22_000)
            r.backend.beforeServerTimeMs = 6_000; r.backend.afterServerTimeMs = 1_000
            r.backend.makeFollowingRequestsFast = true
            r.start(); r.time.advance(7_000)
            val remainingOnFakeServer = java.time.Duration.between(r.backend.serverNow(), r.backend.offer!!.expiresAt).toMillis()
            r.trace()
            println("NEAR_EXPIRY backendRemainingMs=$remainingOnFakeServer vmPhase=${r.vm.state.value.phase} vmOffer=${r.vm.state.value.offer?.offerId}")
            check(remainingOnFakeServer == 1_000L)
            val hiddenBeforeReload = r.vm.state.value.offer == null
            val original = r.backend.offer!!
            r.vm.clear(); r.vm = DriverOffersViewModel(r.backend, r.time, r.saved); r.start()
            check(r.vm.state.value.offer?.offerId == original.offerId && r.vm.state.value.offer?.expiresAt == original.expiresAt)
            check(r.vm.state.value.remainingSeconds == 1)
            println("FAKE_VM_RECREATE t=${r.time.now - 100_000}ms sameOfferId=${original.offerId} sameExpiresAt=${original.expiresAt} remaining=${r.vm.state.value.remainingSeconds}")
            check(!hiddenBeforeReload) { "Backend still has 1000ms; same valid ID is ignored until VM recreation, which shows 1 second (not 30)" }
        }
    }
    case("HOTFIX stale replay after local expiry cannot reset countdown or spin GET") {
        Rig().use { r ->
            r.backend.create(ttl = 1_000)
            val old = r.backend.offer!!
            val snapshot = OffersResult.Loaded(listOf(old), r.backend.serverNow(), r.time.now)
            r.backend.reply = { snapshot }
            r.start(); r.time.advance(1_000)
            check(r.vm.state.value.offer == null && r.backend.starts.size == 2)
            r.time.advance(8_000)
            check(r.vm.state.value.offer == null && r.backend.starts.size == 4 && r.backend.maxActive == 1)
            r.vm.accept(); r.vm.reject(); check(r.backend.actions.isEmpty())
        }
    }
    case("HOTFIX submillisecond snapshots get one immediate verification then normal cadence") {
        Rig().use { r ->
            r.backend.create()
            val snapshotTime = r.backend.serverNow()
            val almostExpired = r.backend.offer!!.copy(offeredAt = snapshotTime.minusSeconds(29), expiresAt = snapshotTime.plusNanos(500_000))
            r.backend.reply = { started -> OffersResult.Loaded(listOf(almostExpired),
                snapshotTime.plusNanos(r.backend.starts.size.toLong()), started) }
            r.start(); check(r.backend.starts.size == 2 && r.vm.state.value.offer == null)
            r.time.advance(8_000)
            check(r.backend.starts.size == 4 && r.backend.maxActive == 1 && r.vm.state.value.offer == null)
        }
    }
    case("HOTFIX fresh near-expiry correction retains original ID expiry and ends at true boundary") {
        Rig().use { r ->
            r.backend.create(); val original = r.backend.offer!!; r.time.advance(22_000)
            r.backend.beforeServerTimeMs = 6_000; r.backend.afterServerTimeMs = 1_000; r.backend.makeFollowingRequestsFast = true
            r.start(); r.time.advance(7_000)
            check(r.vm.state.value.offer == original && r.vm.state.value.remainingSeconds == 1)
            repeat(5) { r.vm.refresh(); r.time.advance(0) }
            check(r.vm.state.value.remainingSeconds == 1 && r.backend.maxActive == 1)
            r.time.advance(999); check(r.vm.state.value.remainingSeconds == 1)
            r.time.advance(1); check(r.vm.state.value.offer == null)
            r.vm.accept(); r.vm.reject(); check(r.backend.actions.isEmpty())
        }
    }
    case("HOTFIX GET response crossing Backend expiry never enables a decision") {
        Rig().use { r ->
            r.backend.create(); r.time.advance(28_000)
            r.backend.afterServerTimeMs = 3_000; r.backend.makeFollowingRequestsFast = true
            r.start(); r.time.advance(3_000)
            check(r.backend.serverNow() > r.backend.offer!!.expiresAt && r.vm.state.value.offer == null)
            r.vm.accept(); r.vm.reject(); check(r.backend.actions.isEmpty())
        }
    }
    case("HOTFIX older Backend snapshots cannot remove or replace a newer displayed offer") {
        Rig().use { r ->
            r.backend.create(); val old = r.backend.offer!!; r.start(); r.time.advance(4_000)
            r.backend.reply = { started -> OffersResult.Loaded(emptyList(), old.offeredAt, started) }
            r.vm.refresh(); r.time.advance(0); check(r.vm.state.value.offer?.offerId == 991L)
            r.backend.create(992); val newer = r.backend.offer!!
            r.backend.reply = { started -> OffersResult.Loaded(listOf(newer), r.backend.serverNow(), started) }
            r.vm.refresh(); r.time.advance(0); check(r.vm.state.value.offer?.offerId == 992L)
            r.backend.reply = { started -> OffersResult.Loaded(listOf(old), old.offeredAt, started) }
            r.vm.refresh(); r.time.advance(0); check(r.vm.state.value.offer?.offerId == 992L)
        }
    }
    for (ownerChange in listOf(false, true)) case("HOTFIX cancelled old GET arriving after new GET is ignored; ownerChange=$ownerChange") {
        Rig().use { r ->
            r.backend.create(); val old = r.backend.offer!!; val firstReply = CompletableDeferred<Unit>()
            r.backend.reply = { started ->
                if (r.backend.starts.size == 1) {
                    withContext(NonCancellable) { firstReply.await() }
                    OffersResult.Loaded(listOf(old), old.offeredAt, started)
                } else OffersResult.Loaded(listOf(r.backend.offer!!), r.backend.serverNow(), started)
            }
            r.start(); check(r.backend.starts.size == 1)
            if (ownerChange) {
                r.vm.clear(); r.vm.activateSession(999002); r.vm.setOnline(true)
            } else r.vm.setForeground(false)
            r.backend.create(992)
            r.vm.setForeground(true); r.time.advance(0)
            check(r.vm.state.value.offer?.offerId == 992L)
            firstReply.complete(Unit); r.time.advance(0)
            check(r.vm.state.value.offer?.offerId == 992L && r.backend.active == 0)
        }
    }
    case("HOTFIX active Current Order gate ignores in-flight response and prevents new GETs") {
        Rig().use { r ->
            r.vm.requireCurrentOrderVerification(); r.start(); check(r.backend.starts.isEmpty())
            r.backend.create(); val gate = CompletableDeferred<Unit>()
            r.backend.reply = { started ->
                withContext(NonCancellable) { gate.await() }
                OffersResult.Loaded(listOf(r.backend.offer!!), r.backend.serverNow(), started)
            }
            r.vm.confirmCurrentOrder(null, r.vm.currentOrderRevision); r.time.advance(0)
            check(r.backend.starts.size == 1)
            r.vm.confirmCurrentOrder(99301, r.vm.currentOrderRevision); gate.complete(Unit); r.time.advance(20_000)
            r.vm.refresh(); r.time.advance(0)
            check(r.vm.state.value.offer == null && r.backend.starts.size == 1 && r.backend.actions.isEmpty())
        }
    }
    for (action in OfferAction.entries) case("HOTFIX confirmed $action ID stays terminal after fresh GET and current-order marker clears") {
        Rig().use { r ->
            r.backend.create(); val original = r.backend.offer!!; r.start()
            if (action == OfferAction.ACCEPT) r.vm.accept() else r.vm.reject()
            r.time.advance(0); check(r.backend.actions == listOf(action))
            r.backend.offer = original; r.time.advance(1_000)
            if (action == OfferAction.ACCEPT) r.vm.confirmCurrentOrder(null, r.vm.currentOrderRevision)
            r.vm.refresh(); r.time.advance(0)
            check(r.vm.state.value.offer == null && r.backend.actions == listOf(action))
        }
    }
    for (action in OfferAction.entries) case("HOTFIX Backend Unavailable for $action stays terminal on repeated GET") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.backend.decision = OfferActionResult.Unavailable
            if (action == OfferAction.ACCEPT) r.vm.accept() else r.vm.reject()
            r.time.advance(0); r.time.advance(1_000); r.vm.refresh(); r.time.advance(0)
            check(r.vm.state.value.offer == null && r.vm.state.value.acceptedOrderId == null)
            check(r.backend.actions == listOf(action))
        }
    }
    for (action in OfferAction.entries) case("HOTFIX pending $action across local expiry blocks GET and duplicate/opposing POST") {
        Rig().use { r ->
            r.backend.create(); r.time.advance(29_000); r.start()
            r.backend.decisionGate = CompletableDeferred()
            if (action == OfferAction.ACCEPT) r.vm.accept() else r.vm.reject()
            r.time.advance(0); val loads = r.backend.starts.size
            r.vm.accept(); r.vm.reject(); r.vm.retryPending(); r.time.advance(2_000)
            check(r.vm.state.value.pending?.action == action && r.vm.state.value.offer == null)
            check(r.backend.starts.size == loads && r.backend.actions == listOf(action))
            r.backend.decisionGate!!.complete(Unit); r.time.advance(0)
            check(r.vm.state.value.pending == null && r.vm.state.value.phase ==
                if (action == OfferAction.ACCEPT) DriverOfferPhase.ACCEPTED else DriverOfferPhase.REJECTED)
        }
    }
    for (action in OfferAction.entries) case("HOTFIX ambiguous $action cannot be dismissed by expiry or empty GET") {
        Rig().use { r ->
            r.backend.create(); r.start(); r.backend.decision = OfferActionResult.Unknown("FAKE lost POST response")
            if (action == OfferAction.ACCEPT) r.vm.accept() else r.vm.reject()
            r.time.advance(0); val loads = r.backend.starts.size
            r.time.advance(60_000); r.vm.refresh(); r.vm.setForeground(false); r.vm.setForeground(true); r.time.advance(0)
            check(r.vm.state.value.pending?.action == action && r.backend.starts.size == loads)
            r.vm.retryPending(); r.time.advance(0)
            check(r.backend.actions == listOf(action, action) && r.vm.state.value.pending?.action == action)
        }
    }
    println("RESULT: PASS=$pass FAIL=$fail; FAKE Backend/virtual time, not live E2E")
    check(fail == 0) { "Polling regression failed" }
}
