package vn.hutech.foodexpress.core.notification

import java.time.Instant

data class InboxData(val orderId: Long? = null, val offerId: Long? = null, val expiresAt: String? = null)
data class InboxItem(
    val id: Long, val type: String, val eventType: String?, val title: String, val body: String,
    val isRead: Boolean, val createdAt: Instant, val data: InboxData = InboxData(),
)
data class InboxPage(val items: List<InboxItem>, val nextCursor: String?, val unreadCount: Int)

data class InboxSignal(val id: Long, val eventType: String) {
    companion object {
        private val idPattern = Regex("[1-9][0-9]{0,18}")
        private val eventPattern = Regex("[A-Z][A-Z0-9_]{0,63}")

        fun parse(data: Map<String, String>): InboxSignal? {
            val id = data["notificationId"]?.takeIf(idPattern::matches)?.toLongOrNull() ?: return null
            val event = data["eventType"]?.takeIf(eventPattern::matches) ?: return null
            return InboxSignal(id, event)
        }
    }
}

class InboxHttpError(val status: Int) : Exception("Inbox HTTP $status")

interface InboxTransport {
    suspend fun page(token: String, unread: Boolean, cursor: String? = null, limit: Int = 20): InboxPage
    suspend fun markRead(token: String, id: Long)
}

/** A push is a hint. Only a row from the currently authenticated inbox can supply its content. */
suspend fun InboxTransport.verifySignal(token: String, signal: InboxSignal): InboxItem? =
    findItem(token, signal.id, signal.eventType)

suspend fun InboxTransport.verifyForCurrentSession(
    signal: InboxSignal, currentToken: () -> String?,
): Pair<String, InboxItem>? {
    val token = currentToken()?.takeIf { it.isNotBlank() } ?: return null
    val row = verifySignal(token, signal) ?: return null
    return (token to row).takeIf { currentToken() == token && !row.isRead }
}

suspend fun InboxTransport.findItem(token: String, id: Long, eventType: String? = null): InboxItem? {
    var cursor: String? = null
    val visited = mutableSetOf<String>()
    repeat(3) {
        val response = page(token, false, cursor, 50)
        response.items.firstOrNull { it.id == id && (eventType == null || it.eventType == eventType) }?.let { return it }
        cursor = response.nextCursor?.takeIf(visited::add) ?: return null
    }
    return null
}
