package vn.hutech.foodexpress.core.notification

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InboxBell(color: Color, count: Int?, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics {
        contentDescription = count?.let { "Thông báo, $it chưa đọc" } ?: "Thông báo"
    }) {
        BadgedBox(badge = { if (count != null && count > 0) Badge { Text(if (count > 99) "99+" else "$count") } }) {
            Canvas(Modifier.size(23.dp)) {
                val x = size.width
                val y = size.height
                val outline = Path().apply {
                    moveTo(x * .15f, y * .76f)
                    lineTo(x * .27f, y * .61f)
                    cubicTo(x * .23f, y * .04f, x * .77f, y * .04f, x * .73f, y * .61f)
                    lineTo(x * .85f, y * .76f)
                    close()
                }
                drawPath(outline, color, style = Stroke(1.6.dp.toPx()))
                drawCircle(color, 1.5.dp.toPx(), Offset(x * .5f, y * .87f))
            }
        }
    }
}

@Composable
fun InboxScreen(
    state: InboxState, accent: Color, onBack: () -> Unit, onFilter: (Boolean) -> Unit,
    onRetry: () -> Unit, onMore: () -> Unit, onOpen: (Long) -> Unit, notice: String? = null,
    onRequestPush: (() -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
    val hourFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
    val accentTint = accent.copy(alpha = .08f)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Quay lại" }) {
                Text("←", fontSize = 24.sp)
            }
            Text("Thông báo", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onRequestPush != null) TextButton(onClick = onRequestPush) { Text("Bật thông báo", color = accent) }
        }
        if (state.problem != InboxProblem.EXPIRED && state.problem != InboxProblem.FORBIDDEN) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(false to "Tất cả", true to ("Chưa đọc" + (state.unreadCount?.let { " ($it)" } ?: ""))).forEach { (filter, label) ->
                    if (filter == state.unreadOnly) {
                        Button(onClick = { onFilter(filter) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text(label) }
                    } else OutlinedButton(onClick = { onFilter(filter) }) { Text(label) }
                }
            }
        }
        if (notice != null) Text(notice, Modifier.padding(vertical = 8.dp), color = accent)
        if (state.readFailed) Text("Không thể đánh dấu đã đọc. Vui lòng thử lại.", Modifier.padding(vertical = 8.dp), color = accent)
        val message = when (state.problem) {
            InboxProblem.EXPIRED -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            InboxProblem.FORBIDDEN -> "Bạn không có quyền xem thông báo."
            InboxProblem.OFFLINE -> "Không thể kết nối. Kiểm tra Internet và thử lại."
            InboxProblem.UNAVAILABLE -> "Không thể tải thông báo."
            null -> when {
                state.reconnecting -> "Đang kết nối lại…"
                state.loading && state.items.isEmpty() -> "Đang tải thông báo…"
                !state.loading && state.items.isEmpty() && state.unreadOnly -> "Bạn không có thông báo chưa đọc"
                !state.loading && state.items.isEmpty() -> "Chưa có thông báo nào"
                else -> null
            }
        }
        if (message != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                Text(message, Modifier.padding(top = 8.dp))
                if (state.problem in setOf(InboxProblem.OFFLINE, InboxProblem.UNAVAILABLE)) {
                    OutlinedButton(onClick = onRetry) { Text("Thử lại", color = accent) }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.items.groupBy { it.createdAt.atZone(zone).toLocalDate() }.forEach { (date, rows) ->
                item(key = date.toString()) {
                    Text(if (date == LocalDate.now(zone)) "Hôm nay" else date.format(dayFormat), fontWeight = FontWeight.Bold)
                }
                items(rows, key = InboxItem::id) { row ->
                    val expires = row.data.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    val expired = row.eventType == "OFFER_CREATED" && expires?.let { it <= Instant.now() } == true
                    Surface(shape = RoundedCornerShape(12.dp), color = if (row.isRead) MaterialTheme.colorScheme.surfaceVariant else accentTint) {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(row.id) }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = accent.copy(alpha = .13f)) {
                                Text(eventIcon(row.eventType), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = accent, fontSize = 19.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(row.title, fontWeight = FontWeight.Bold)
                                Text(row.body, fontSize = 13.sp)
                                if (expired) Text("Đơn hàng đã hết hiệu lực", color = accent, fontWeight = FontWeight.SemiBold)
                                Text(row.createdAt.atZone(zone).format(hourFormat), fontSize = 12.sp)
                            }
                            if (!row.isRead) Surface(shape = CircleShape, color = accent,
                                modifier = Modifier.size(8.dp).semantics { contentDescription = "Chưa đọc" }) {}
                        }
                    }
                }
            }
            if (state.nextCursor != null) item {
                OutlinedButton(onClick = onMore, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.loading) "Đang tải thêm…" else "Tải thêm", color = accent)
                }
            }
        }
    }
}

private fun eventIcon(event: String?): String = when (event) {
    "OFFER_CREATED", "ORDER_PLACED" -> "□"
    "ORDER_PREPARING", "ORDER_READY_FOR_PICKUP" -> "◷"
    "ORDER_DELIVERING" -> "➜"
    "ORDER_DELIVERED" -> "✓"
    "ORDER_CANCELLED" -> "×"
    else -> "•"
}
