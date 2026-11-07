import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import vn.hutech.foodexpress.driver.data.offer.OfferMonotonicClock
@OptIn(InternalCoroutinesApi::class)
class VirtualClock : CoroutineDispatcher(), Delay, OfferMonotonicClock {
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
