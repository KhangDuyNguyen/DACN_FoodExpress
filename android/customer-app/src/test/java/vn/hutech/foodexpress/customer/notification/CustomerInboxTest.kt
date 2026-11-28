package vn.hutech.foodexpress.customer.notification

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import vn.hutech.foodexpress.core.notification.*

class CustomerInboxTest {
    private val row = InboxItem(12, "ORDER", "ORDER_DELIVERED", "Đã giao", "Đơn thử nghiệm", false, Instant.EPOCH,
        InboxData(orderId = 18))

    @Test fun unauthenticatedHomeDoesNotFetchOrExposeBadge() = runBlocking {
        var calls = 0
        val source = object : InboxTransport {
            override suspend fun page(token: String, unread: Boolean, cursor: String?, limit: Int): InboxPage {
                calls++
                return InboxPage(listOf(row), null, 99)
            }
            override suspend fun markRead(token: String, id: Long) = error("Unexpected PATCH")
        }
        val controller = InboxController(source, this) { false }
        controller.bind(null)
        controller.refresh()
        yield()
        assertEquals(0, calls)
        assertEquals(InboxProblem.EXPIRED, controller.state.value.problem)
        assertNull(controller.state.value.unreadCount)
    }

    @Test fun pushCannotPassWhenSessionChangesWhileRestIsPending() = runBlocking {
        val response = CompletableDeferred<InboxPage>()
        var token: String? = "first"
        val source = object : InboxTransport {
            override suspend fun page(token: String, unread: Boolean, cursor: String?, limit: Int) = response.await()
            override suspend fun markRead(token: String, id: Long) = Unit
        }
        val pending = async { source.verifyForCurrentSession(InboxSignal(12, "ORDER_DELIVERED")) { token } }
        yield()
        token = "second"
        response.complete(InboxPage(listOf(row), null, 1))
        assertNull(pending.await())
        val verified = source.verifyForCurrentSession(InboxSignal(12, "ORDER_DELIVERED")) { token }
        assertEquals("second", verified?.first)
        assertEquals(row.id, verified?.second?.id)
    }

    @Test fun pushTapMetadataOnlySupportsPositiveOrderIds() {
        assertEquals(18L, customerOrderId(row))
        assertNull(customerOrderId(row.copy(type = "SYSTEM")))
        assertNull(customerOrderId(row.copy(data = InboxData(orderId = 0))))
        assertNull(customerOrderId(row.copy(data = InboxData())))
    }

    @Test fun forbiddenPatchClearsPrivateState() = runBlocking {
        val source = object : InboxTransport {
            override suspend fun page(token: String, unread: Boolean, cursor: String?, limit: Int) =
                InboxPage(listOf(row), null, 1)
            override suspend fun markRead(token: String, id: Long) { throw InboxHttpError(403) }
        }
        val inbox = InboxController(source, this) { true }
        inbox.bind("fixture")
        yield()
        inbox.open(12)
        yield()
        assertEquals(InboxProblem.FORBIDDEN, inbox.state.value.problem)
        assertTrue(inbox.state.value.items.isEmpty())
        assertNull(inbox.state.value.unreadCount)
    }
}
