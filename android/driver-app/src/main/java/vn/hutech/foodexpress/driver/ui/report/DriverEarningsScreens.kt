package vn.hutech.foodexpress.driver.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.hutech.foodexpress.driver.data.report.EarningsPeriod

@Composable
fun DriverEarningsScreen(state: DriverReportsState, owner: Long?, onPeriod: (EarningsPeriod) -> Unit,
    onRefresh: () -> Unit, onSelect: (Long) -> Unit, bottomBar: @Composable () -> Unit = {}, offerContent: (@Composable () -> Unit)? = null) {
    val visible = state.forReportOwner(owner)
    val list = key(owner, visible.period) { rememberLazyListState() }
    ReportScaffold("Thu nhập", bottomBar = bottomBar) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
       ReportPullToRefresh(visible.earningsLoading, owner != null && visible.ownerId == owner && !state.sessionExpired && !state.accessDenied,
        atTop = { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }, onRefresh = onRefresh) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offerContent != null) item { offerContent() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EarningsPeriod.entries.forEach { period ->
                        FilterChip(selected = visible.period == period, onClick = { onPeriod(period) }, modifier = Modifier.weight(1f),
                            label = { Text(period.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ReportRed, selectedLabelColor = androidx.compose.ui.graphics.Color.White))
                    }
                }
            }
            if (state.accessDenied && owner != null && state.ownerId == owner) item { ReportNotice("Không có quyền xem thu nhập", "Vui lòng trở về Trang chủ hoặc kiểm tra lại tài khoản.", ReportSymbol.ERROR) }
            else if (state.ownerId != owner || owner == null || state.sessionExpired) item {
                ReportNotice("Đang xác minh phiên", "Dữ liệu thu nhập chỉ hiển thị sau khi xác minh tài khoản.")
            } else {
                if (visible.earningsLoading) item { ReportLoading() }
                visible.earningsError?.let { error -> item { ReportNotice("Chưa thể tải thu nhập", error, ReportSymbol.ERROR, onRefresh) } }
                visible.earnings?.let { report ->
                    item {
                        Surface(color = ReportPink, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ReportLine(ReportSymbol.WALLET, "Tổng thu nhập")
                                Text(reportMoney(report.totalAmount), color = ReportRed, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Text("${reportDay(report.range.from)} — ${reportDay(report.range.to)}", color = ReportMuted)
                                if (visible.earningsError != null) Text("Số liệu từ lần cập nhật trước.", color = ReportMuted)
                            }
                        }
                    }
                    item { ReportTitle("Các khoản thu nhập") }
                    if (report.items.isEmpty()) item {
                        ReportNotice("Chưa có thu nhập", "Không có khoản thu nhập được ghi nhận trong khoảng ngày này.", ReportSymbol.WALLET)
                    }
                    items(report.items, key = { it.orderId }) { earning ->
                        ReportCard(onClick = { onSelect(earning.orderId) }) {
                            ReportTitle(earning.orderCode)
                            ReportLine(ReportSymbol.TIME, "Ghi nhận: ${reportTime(earning.createdAt)}")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(reportMoney(earning.totalAmount), color = ReportGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                Text("Xem chi tiết", color = ReportRed)
                            }
                        }
                    }
                }
                if (visible.earnings == null && !visible.earningsLoading && visible.earningsError == null) item { ReportLoading() }
            }
        }
       }
      }
    }
}

@Composable
fun DriverEarningDetailScreen(state: DriverReportsState, owner: Long?, onBack: () -> Unit, offerContent: (@Composable () -> Unit)? = null) {
    val earning = state.forReportOwner(owner).selectedEarning
    ReportScaffold("Chi tiết thu nhập", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (offerContent != null) item { offerContent() }
            if (state.accessDenied && owner != null && state.ownerId == owner) item { ReportNotice("Không có quyền xem thu nhập", "Vui lòng kiểm tra lại tài khoản.", ReportSymbol.ERROR) }
            else if (earning == null) item { ReportNotice("Chưa có dữ liệu khoản thu nhập", "Quay lại Thu nhập và chọn khoản đã được xác minh.", ReportSymbol.WALLET) }
            else {
                item { ReportCard { ReportTitle(earning.orderCode); ReportBadge("Đã ghi nhận") } }
                item { ReportCard {
                    ReportTitle("Thông tin đơn")
                    ReportLine(ReportSymbol.RECEIPT, "Mã đơn: ${earning.orderCode}")
                    ReportLine(ReportSymbol.TIME, "Thời gian ghi nhận: ${reportTime(earning.createdAt)}")
                } }
                item { ReportCard {
                    ReportTitle("Chi tiết thu nhập")
                    ReportLine(ReportSymbol.WALLET, "Thu nhập cơ bản: ${reportMoney(earning.amount)}")
                    ReportLine(ReportSymbol.WALLET, "Thưởng: ${reportMoney(earning.bonus)}")
                    HorizontalDivider()
                    Text("Tổng thu nhập", color = ReportInk, fontWeight = FontWeight.Bold)
                    Text(reportMoney(earning.totalAmount), color = ReportRed, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                } }
                item { Text("Số tiền đã được hệ thống ghi nhận. Phí giao hàng gốc và tỷ lệ của đơn chưa được cung cấp.", color = ReportMuted) }
            }
            item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Quay lại Thu nhập", color = ReportRed) } }
        }
    }
}
