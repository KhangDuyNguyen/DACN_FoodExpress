package vn.hutech.foodexpress.driver.notification

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import vn.hutech.foodexpress.core.notification.*
import vn.hutech.foodexpress.driver.data.offer.DriverOffer
import vn.hutech.foodexpress.driver.data.offer.OffersResult
import org.junit.Assert.*
import org.junit.Test
import vn.hutech.foodexpress.core.network.NetworkModule

class DriverInboxTest {
    private fun item(id: Long = 1, read: Boolean = false) = InboxItem(id, "ORDER", "OFFER_CREATED",
        "Lời mời", "Đơn thử nghiệm", read, Instant.EPOCH, InboxData(orderId = 11, offerId = 20))

    private class Transport(var result: InboxPage) : InboxTransport {
        var error: Exception? = null
        var patchError: Exception? = null
        var pageCalls = mutableListOf<Triple<String, Boolean, String?>>()
        var readCalls = mutableListOf<Long>()
        var pendingPage: CompletableDeferred<InboxPage>? = null
        override suspend fun page(token: String, unread: Boolean, cursor: String?, limit: Int): InboxPage {
            pageCalls.add(Triple(token, unread, cursor))
            error?.let { throw it }
            return pendingPage?.await() ?: result
        }
        override suspend fun markRead(token: String, id: Long) {
            patchError?.let { throw it }
            readCalls.add(id)
        }
    }

    @Test fun cursorDeduplicatesAndCountComesFromServer() = runBlocking {
        val api = Transport(InboxPage(listOf(item(2)), "cursor-a", 14))
        val inbox = InboxController(api, this) { it == "driver-fixture" }
        inbox.bind("driver-fixture")
        yield()
        assertEquals(14, inbox.state.value.unreadCount)
        api.result = InboxPage(listOf(item(2), item(1)), "cursor-a", 15)
        inbox.more()
        yield()
        assertEquals(listOf(2L, 1L), inbox.state.value.items.map { it.id })
        assertNull(inbox.state.value.nextCursor)
        inbox.filter(true)
        yield()
        assertEquals(Triple("driver-fixture", true, null), api.pageCalls.last())
        assertEquals(15, inbox.state.value.unreadCount)
    }

    @Test fun temporaryFailurePreservesRowsAndUnauthorizedClearsThem() = runBlocking {
        val api = Transport(InboxPage(listOf(item()), null, 1))
        val inbox = InboxController(api, this) { true }
        inbox.bind("driver-fixture")
        yield()
        api.error = IOException()
        inbox.refresh()
        yield()
        assertEquals(1, inbox.state.value.items.size)
        assertEquals(InboxProblem.OFFLINE, inbox.state.value.problem)
        api.error = InboxHttpError(401)
        inbox.refresh()
        yield()
        assertEquals(InboxProblem.EXPIRED, inbox.state.value.problem)
        assertTrue(inbox.state.value.items.isEmpty())
        assertNull(inbox.state.value.unreadCount)
    }

    @Test fun patchFailurePreservesUnreadAndSuccessUsesBackendCount() = runBlocking {
        val api = Transport(InboxPage(listOf(item()), null, 1))
        val inbox = InboxController(api, this) { true }
        inbox.bind("driver-fixture")
        yield()
        api.patchError = InboxHttpError(500)
        inbox.open(1)
        yield()
        assertTrue(inbox.state.value.readFailed)
        assertFalse(inbox.state.value.items.single().isRead)
        api.patchError = null
        api.result = InboxPage(listOf(item(read = true)), null, 0)
        inbox.open(1)
        yield()
        yield()
        assertEquals(listOf(1L), api.readCalls)
        assertTrue(inbox.state.value.items.single().isRead)
        assertEquals(0, inbox.state.value.unreadCount)
    }

    @Test fun switchingAccountsCancelsOldFetch() = runBlocking {
        val api = Transport(InboxPage(listOf(item(2)), null, 1))
        val original = CompletableDeferred<InboxPage>()
        api.pendingPage = original
        var active = "a"
        val inbox = InboxController(api, this) { it == active }
        inbox.bind("a")
        yield()
        active = "b"
        api.pendingPage = null
        inbox.bind("b")
        original.complete(InboxPage(listOf(item(1)), null, 99))
        yield()
        assertEquals(2L, inbox.state.value.items.single().id)
        assertEquals(1, inbox.state.value.unreadCount)
    }

    @Test fun deviceRefreshAndAccountSwitchReleaseOldBinding() = runBlocking {
        val access = MutableStateFlow<String?>(null)
        val device = MutableStateFlow<String?>(null)
        val calls = mutableListOf<String>()
        val binding = DeviceBindingSync({ a, d -> calls.add("register $a $d") }, { a, d -> calls.add("remove $a $d") })
        val task = launch { binding.collect(access, device) { "one" } }
        suspend fun awaitCalls(count: Int) {
            repeat(40) { if (calls.size >= count) return; yield() }
            error("Binding call count $count was not reached")
        }
        try {
            repeat(5) { yield() }
            assertTrue(calls.isEmpty())
            access.value = "a"; awaitCalls(1)
            device.value = "two"; awaitCalls(3)
            access.value = "b"; awaitCalls(5)
            access.value = null; awaitCalls(6)
            assertEquals(listOf("register a one", "remove a one", "register a two",
                "remove a two", "register b two", "remove b two"), calls)
        } finally { task.cancel() }
    }

    @Test fun offerRequiresExactIdsAndFutureServerDeadline() {
        val now = LocalDateTime.of(2026, 11, 26, 15, 0)
        val offer = DriverOffer(20, 11, "FIX", "Demo", "Demo", 10.0, 106.0, "Demo", 1.0, now, now.plusSeconds(8))
        fun loaded(value: DriverOffer) = OffersResult.Loaded(listOf(value), now, 0)
        assertEquals(OfferNotice.ACTIVE, verifyOfferNotice(item(), loaded(offer)))
        assertEquals(OfferNotice.EXPIRED, verifyOfferNotice(item(), loaded(offer.copy(orderId = 12))))
        assertEquals(OfferNotice.EXPIRED, verifyOfferNotice(item(), loaded(offer.copy(expiresAt = now))))
        assertEquals(OfferNotice.UNKNOWN, verifyOfferNotice(item(), OffersResult.Failed("offline")))
    }

    @Test fun pushOfferDeadlineUsesRequestStartAndVerifiedOfferExpiry() {
        val now = LocalDateTime.of(2026, 11, 27, 15, 0)
        val offer = DriverOffer(20, 11, "FIX", "Demo", "Demo", 10.0, 106.0, "Demo", 1.0, now, now.plusSeconds(8))
        val requestStarted = 120_000L
        val verified = OffersResult.Loaded(listOf(offer), now, requestStarted)
        val deadline = requireNotNull(offerNotificationDeadline(item(), verified))

        assertEquals(128_000L, deadline.elapsedDeadlineMillis)
        assertEquals(6, deadline.remainingSeconds(requestStarted + 2_000))
        assertEquals(0, deadline.remainingSeconds(requestStarted + 8_000))
        assertNull(offerNotificationDeadline(item(), verified.copy(offers = listOf(offer.copy(offerId = 21)))))
        assertNull(offerNotificationDeadline(item(), verified.copy(serverTime = offer.expiresAt)))
    }

    @Test fun pushSignalMustMatchOwnedRestRowAndEvent() = runBlocking {
        val api = Transport(InboxPage(listOf(item()), null, 1))
        assertNull(InboxSignal.parse(mapOf("notificationId" to "-1", "eventType" to "OFFER_CREATED")))
        assertNull(api.verifySignal("a", InboxSignal(1, "ORDER_CANCELLED")))
        assertNull(api.verifySignal("a", InboxSignal(4, "OFFER_CREATED")))
        assertEquals(1L, api.verifySignal("a", InboxSignal(1, "OFFER_CREATED"))?.id)
    }

    @Test fun retrofitUsesBoundAuthAndCursorAndAcceptsOnly204ForPatch() = runBlocking {
        val server = ServerSocket(0, 3, InetAddress.getByName("127.0.0.1"))
        val requests = java.util.concurrent.LinkedBlockingQueue<List<String>>()
        val payload = """{"items":[{"id":11,"type":"ORDER","eventType":null,"title":"Món đã xong","body":"Đơn thử nghiệm","isRead":false,"createdAt":"2026-11-26T08:00:00Z","data":{}}],"nextCursor":null,"unreadCount":31}"""
            .toByteArray()
        val worker = Thread {
            repeat(3) {
                if (server.isClosed) return@Thread
                server.accept().use { connection ->
                    connection.soTimeout = 5000
                    val reader = connection.getInputStream().bufferedReader()
                    val (method, path) = reader.readLine().split(' ')
                    val headers = generateSequence(reader::readLine).takeWhile { it.isNotEmpty() }.toList()
                    val authorization = headers.first { it.startsWith("Authorization:", ignoreCase = true) }.substringAfter(':').trim()
                    requests.add(listOf(method, path, authorization))
                    val status = when {
                        method == "GET" -> "200 OK"
                        path.endsWith("/11/read") -> "204 No Content"
                        else -> "403 Forbidden"
                    }
                    val body = if (method == "GET") payload else ByteArray(0)
                    val output = connection.getOutputStream()
                    output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(body)
                }
            }
        }.also { it.isDaemon = true; it.start() }
        try {
            val api = RetrofitInbox(NetworkModule.provideRetrofit().newBuilder()
                .baseUrl("http://127.0.0.1:${server.localPort}/api/").build())
            val page = api.page("unit-fixture", true, "after")
            assertEquals(31, page.unreadCount)
            assertEquals("Món đã xong", page.items.single().title)
            api.markRead("unit-fixture", 11)
            assertEquals(403, (runCatching { api.markRead("unit-fixture", 12) }.exceptionOrNull() as InboxHttpError).status)
            assertEquals(listOf("GET", "/api/notifications?unreadOnly=true&cursor=after&limit=20", "Bearer unit-fixture"), requests.poll())
            assertEquals(listOf("PATCH", "/api/notifications/11/read", "Bearer unit-fixture"), requests.poll())
        } finally { server.close(); worker.join(5000) }
    }
}
