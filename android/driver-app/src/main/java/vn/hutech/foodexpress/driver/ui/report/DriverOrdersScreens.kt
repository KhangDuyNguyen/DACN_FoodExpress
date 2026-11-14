package vn.hutech.foodexpress.driver.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.hutech.foodexpress.driver.ui.order.*

@Composable
fun DriverOrdersScreen(state: DriverReportsState, current: DriverCurrentOrderUiState, owner: Long?, acceptPending: Boolean,
    onSection: (Boolean) -> Unit, onCurrentRefresh: () -> Unit, onHistoryRefresh: () -> Unit, onMore: () -> Unit,
    onCurrent: () -> Unit, onHistory: (Long) -> Unit, bottomBar: @Composable () -> Unit = {}, offerContent: (@Composable () -> Unit)? = null) {
    val visible = state.forReportOwner(owner)
    val order = current.forDriver(owner)
    val history = visible.historySelected
    // Independent branches keep their own scroll position; switching to History must not inherit Active's offset.
    val activeList = key(owner) { rememberLazyListState() }
    val historyList = key(owner) { rememberLazyListState() }
    val list = if (history) historyList else activeList
    ReportScaffold("Đơn hàng", bottomBar = bottomBar) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
       ReportPullToRefresh(if (history) visible.historyLoading else order.loading,
        owner != null && visible.ownerId == owner && !state.sessionExpired && !state.accessDenied && !order.deliverySending,
        atTop = { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 },
        onRefresh = if (history) onHistoryRefresh else onCurrentRefresh) {
        LazyColumn(Modifier.fillMaxSize(), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offerContent != null) item { offerContent() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((selected, label) in listOf(false to "Đang thực hiện", true to "Lịch sử đã giao")) {
                        FilterChip(selected = history == selected, onClick = { onSection(selected) }, modifier = Modifier.weight(1f), label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ReportRed, selectedLabelColor = androidx.compose.ui.graphics.Color.White))
                    }
                }
            }
            if (owner == null || state.ownerId != owner || state.sessionExpired) item {
                ReportNotice("Đang xác minh phiên", "Vui lòng chờ xác minh tài khoản để xem đơn hàng.")
            } else if (!history) {
                if (order.deliveryPending != null || order.completion != null) item { DriverDeliveryRecoveryAction(current, owner, onCurrent) }
                when {
                    order.verified && order.error == null && order.order == null && !acceptPending && order.deliveryPending == null -> item {
                        ReportNotice("Bạn chưa có đơn đang thực hiện.", "Đơn đã nhận sẽ xuất hiện tại đây. Bạn có thể kiểm tra trạng thái nhận đơn ở Trang chủ.")
                    }
                    else -> item {
                        DriverCurrentOrderCard(if (acceptPending && order.order == null)
                            order.copy(verified = false, error = "Kết quả Nhận đơn đang được xác minh.") else order,
                            onCurrentRefresh, onOpenDetails = onCurrent)
                    }
                }
                if (order.verified && order.error == null) order.order?.let { active -> item {
                    ReportCard {
                        ReportLine(ReportSymbol.PIN, "Lấy tại: ${active.restaurantAddress}")
                        Text("Mở chi tiết đơn để xác nhận các bước giao hàng.", color = ReportMuted)
                    }
                } }
            } else if (state.ownerId != owner) item {
                ReportNotice("Đang xác minh phiên", "Vui lòng chờ xác minh tài khoản để xem lịch sử.")
            } else if (state.accessDenied) item {
                ReportNotice("Không có quyền xem lịch sử", "Vui lòng trở về Trang chủ hoặc kiểm tra lại tài khoản.", ReportSymbol.ERROR)
            } else {
                item { Text("Các đơn đã hoàn tất của bạn", color = ReportMuted) }
                if (visible.historyLoading) item { ReportLoading() }
                visible.historyError?.let { error -> item {
                    ReportNotice("Chưa thể tải lịch sử", error, ReportSymbol.ERROR,
                        if (visible.historyPage < 0) onHistoryRefresh else onMore)
                } }
                if (visible.historyPage >= 0 && visible.history.isEmpty() && !visible.historyLoading && visible.historyError == null) item {
                    ReportNotice("Chưa có đơn đã giao", "Các đơn hoàn thành sẽ được hiển thị tại đây.")
                }
                items(visible.history, key = { it.orderId }) { past ->
                    ReportCard(onClick = { onHistory(past.orderId) }) {
                        ReportTitle(past.orderCode); ReportBadge("Đã giao")
                        ReportLine(ReportSymbol.RESTAURANT, past.restaurantName)
                        ReportLine(ReportSymbol.TIME, reportTime(past.deliveredAt))
                        Text("Xem chi tiết đơn đã giao", color = ReportRed)
                    }
                }
                if (visible.historyPage >= 0 && visible.historyPage + 1 < visible.totalPages) item {
                    OutlinedButton(onClick = onMore, enabled = !visible.historyLoading, modifier = Modifier.fillMaxWidth()) { Text("Tải thêm", color = ReportRed) }
                }
            }
        }
       }
      }
    }
}

@Composable
fun DriverHistoryDetailScreen(state: DriverReportsState, owner: Long?, onBack: () -> Unit, offerContent: (@Composable () -> Unit)? = null) {
    val order = state.forReportOwner(owner).selectedHistory
    ReportScaffold("Chi tiết đơn đã giao", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offerContent != null) item { offerContent() }
            if (state.accessDenied && owner != null && state.ownerId == owner) item { ReportNotice("Không có quyền xem lịch sử", "Vui lòng kiểm tra lại tài khoản.", ReportSymbol.ERROR) }
            else if (order == null) item { ReportNotice("Chưa có dữ liệu đơn đã giao", "Quay lại Lịch sử và chọn đơn đã được xác minh.") }
            else {
                item { ReportCard { ReportIcon(ReportSymbol.CHECK, color = ReportGreen, modifier = Modifier.size(48.dp)); ReportTitle(order.orderCode); ReportBadge("Đã giao thành công") } }
                item { ReportCard {
                    ReportTitle("Thông tin đơn hàng")
                    ReportLine(ReportSymbol.RECEIPT, "Mã đơn: ${order.orderCode}")
                    ReportLine(ReportSymbol.RESTAURANT, order.restaurantName)
                    ReportLine(ReportSymbol.TIME, order.deliveredAt?.let { "Hoàn thành lúc: ${reportTime(it)}" } ?: reportTime(null))
                } }
                item { Text("Thông tin tóm tắt từ lịch sử đã giao. Địa chỉ, thông tin khách hàng và thu nhập không có trong dữ liệu lịch sử này.", color = ReportMuted) }
            }
            item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Quay lại Lịch sử", color = ReportRed) } }
        }
    }
}
