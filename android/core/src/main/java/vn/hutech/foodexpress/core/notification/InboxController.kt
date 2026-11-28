package vn.hutech.foodexpress.core.notification

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class InboxProblem { EXPIRED, FORBIDDEN, OFFLINE, UNAVAILABLE }

data class InboxState(
    val items: List<InboxItem> = emptyList(), val unreadCount: Int? = null, val unreadOnly: Boolean = false,
    val nextCursor: String? = null, val loading: Boolean = false, val reconnecting: Boolean = false,
    val problem: InboxProblem? = null, val readFailed: Boolean = false,
)

/** Session boundaries and operations are called by the screen owner on the main dispatcher. */
class InboxController(
    private val transport: InboxTransport, private val scope: CoroutineScope, private val owns: (String) -> Boolean,
) {
    private val mutable = MutableStateFlow(InboxState(problem = InboxProblem.EXPIRED))
    val state = mutable.asStateFlow()
    private var token: String? = null
    private var epoch = 0L
    private var readJob: Job? = null
    private var pageJob: Job? = null
    private val cursors = mutableSetOf<String>()

    fun bind(access: String?) {
        val next = access?.takeIf { it.isNotBlank() && owns(it) }
        if (next == token) {
            if (next == null && state.value.problem != InboxProblem.EXPIRED) {
                mutable.value = InboxState(problem = InboxProblem.EXPIRED)
            }
            return
        }
        resetRequests()
        token = next
        mutable.value = if (next == null) InboxState(problem = InboxProblem.EXPIRED) else InboxState()
        if (next != null) refresh()
    }

    fun refresh() = load(append = false)
    fun more() = load(append = true)

    fun filter(unread: Boolean) {
        if (state.value.unreadOnly == unread || token == null) return
        pageJob?.cancel()
        mutable.value = InboxState(unreadOnly = unread, unreadCount = state.value.unreadCount)
        refresh()
    }

    fun open(id: Long, onRead: suspend (InboxItem, String) -> Unit = { _, _ -> }) {
        val access = token?.takeIf(owns) ?: return
        val row = state.value.items.firstOrNull { it.id == id } ?: return
        if (readJob?.isActive == true) return
        val generation = epoch
        mutable.value = state.value.copy(readFailed = false)
        readJob = scope.launch {
            try {
                transport.markRead(access, id)
                currentCoroutineContext().ensureActive()
                if (!current(access, generation)) return@launch
                val previous = state.value
                mutable.value = previous.copy(items = previous.items.mapNotNull {
                    when {
                        it.id != id -> it
                        previous.unreadOnly -> null
                        else -> it.copy(isRead = true)
                    }
                })
                refresh()
                onRead(row, access)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (current(access, generation) && !deny(error)) mutable.value = state.value.copy(readFailed = true)
            }
        }
    }

    private fun load(append: Boolean) {
        val access = token?.takeIf(owns) ?: return
        val previous = state.value
        val cursor = if (append) previous.nextCursor ?: return else null
        if (append && pageJob?.isActive == true) return
        pageJob?.cancel()
        val generation = epoch
        if (!append) cursors.clear()
        mutable.value = previous.copy(loading = true, reconnecting = previous.problem == InboxProblem.OFFLINE, problem = null)
        pageJob = scope.launch {
            try {
                val page = transport.page(access, previous.unreadOnly, cursor)
                currentCoroutineContext().ensureActive()
                if (!current(access, generation)) return@launch
                if (cursor != null) cursors.add(cursor)
                val existing = if (append) state.value.items else emptyList()
                val rows = (existing + page.items).distinctBy { it.id }.filter { !previous.unreadOnly || !it.isRead }
                mutable.value = state.value.copy(items = rows, unreadCount = page.unreadCount,
                    nextCursor = page.nextCursor?.takeUnless(cursors::contains), loading = false, reconnecting = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (!current(access, generation)) return@launch
                if (!deny(error)) mutable.value = state.value.copy(loading = false, reconnecting = false,
                    problem = if (error is IOException) InboxProblem.OFFLINE else InboxProblem.UNAVAILABLE)
            }
        }
    }

    private fun current(access: String, generation: Long) = token == access && epoch == generation && owns(access)

    private fun deny(error: Exception): Boolean {
        val problem = when ((error as? InboxHttpError)?.status) {
            401 -> InboxProblem.EXPIRED
            403 -> InboxProblem.FORBIDDEN
            else -> return false
        }
        resetRequests()
        token = null
        mutable.value = InboxState(problem = problem)
        return true
    }

    private fun resetRequests() {
        epoch++
        readJob?.cancel()
        pageJob?.cancel()
        cursors.clear()
    }
}
