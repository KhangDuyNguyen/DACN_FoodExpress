package vn.hutech.foodexpress.driver.ui.offer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import vn.hutech.foodexpress.driver.data.offer.OfferAction

private val OfferRed = Color(0xFFE7192D)
private val OfferPink = Color(0xFFFFF0F2)
private val OfferInk = Color(0xFF20252D)
private val OfferMuted = Color(0xFF596B82)

@Composable
fun DriverOfferDialog(state: DriverOffersUiState, onAccept: () -> Unit, onReject: () -> Unit, onRefresh: () -> Unit) {
    val offer = state.offer ?: return
    if (state.remainingSeconds <= 0 && !state.sending) return
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, tonalElevation = 0.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Đơn hàng mới", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = OfferInk)
                        Text("Food Express", fontSize = 12.sp, color = OfferRed)
                    }
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { state.remainingSeconds / 30f }, modifier = Modifier.fillMaxSize(),
                            color = OfferRed, trackColor = OfferPink, strokeWidth = 4.dp)
                        Text(String.format(Locale.ROOT, "00:%02d", state.remainingSeconds), color = OfferRed,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Text("Đơn ${offer.orderCode}", fontWeight = FontWeight.SemiBold, color = OfferInk)
                HorizontalDivider(color = OfferPink)
                OfferAddress("ĐIỂM LẤY • NHÀ HÀNG", offer.restaurantName, offer.restaurantAddress)
                Surface(color = OfferPink, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Khoảng cách đến quán", color = OfferMuted, fontSize = 12.sp)
                        Text("${String.format(Locale.forLanguageTag("vi-VN"), "%.2f", offer.distanceKm)} km",
                            color = OfferRed, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                }
                OfferAddress("ĐỊA CHỈ GIAO HÀNG", offer.deliveryAddress, null)
                Text("Thời hạn được tính theo máy chủ.", fontSize = 12.sp, color = OfferMuted)
                if (state.sending) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = OfferRed, strokeWidth = 2.dp)
                        Text("Đang xử lý…", color = OfferMuted)
                    }
                } else if (state.phase == DriverOfferPhase.NETWORK_ERROR) {
                    Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = onRefresh) { Text("Tải lại lời mời", color = OfferRed) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onReject, enabled = !state.sending, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, OfferRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OfferRed)) { Text("Từ chối") }
                    Button(onClick = onAccept, enabled = !state.sending, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = OfferRed)) { Text("Nhận đơn") }
                }
            }
        }
    }
}

@Composable
private fun OfferAddress(label: String, title: String, address: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = OfferRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(title, color = OfferInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        address?.let { Text(it, color = OfferMuted, fontSize = 13.sp, lineHeight = 19.sp) }
    }
}

/** Errors and unresolved actions stay available; confirmed Reject is transient, screen-scoped feedback. */
@Composable
fun DriverOfferStatusCard(state: DriverOffersUiState, onRetryPending: () -> Unit, onRefresh: () -> Unit) {
    val message = state.message ?: return
    if (state.phase == DriverOfferPhase.REJECTED) return
    if (state.offer != null) return
    val accepted = state.phase == DriverOfferPhase.ACCEPTED
    val accent = if (accepted) Color(0xFF087D3C) else OfferRed
    Surface(color = if (accepted) Color(0xFFE5F7EB) else OfferPink, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (state.phase) {
                DriverOfferPhase.ACCEPTED -> "Đã nhận đơn"
                DriverOfferPhase.REJECTED -> "Đã từ chối lời mời"
                DriverOfferPhase.EXPIRED -> "Đã hết thời gian nhận"
                DriverOfferPhase.UNAVAILABLE -> "Lời mời không còn khả dụng"
                DriverOfferPhase.ACCEPT_UNKNOWN -> "Chưa xác định kết quả Nhận đơn"
                DriverOfferPhase.REJECT_UNKNOWN -> "Chưa xác định kết quả Từ chối"
                DriverOfferPhase.SENDING_ACCEPT, DriverOfferPhase.SENDING_REJECT -> "Đang xác minh thao tác"
                else -> "Chưa kết nối được máy chủ"
            }, color = accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(message, color = OfferInk, fontSize = 13.sp, lineHeight = 19.sp)
            state.pending?.let { pending ->
                Text("orderId: ${pending.orderId} • offerId: ${pending.offerId}", color = OfferMuted, fontSize = 12.sp)
                if (state.sending) CircularProgressIndicator(Modifier.size(24.dp), color = OfferRed, strokeWidth = 2.dp)
                else Button(onClick = onRetryPending, colors = ButtonDefaults.buttonColors(containerColor = OfferRed)) {
                    Text(if (pending.action == OfferAction.ACCEPT) "Xác minh Nhận đơn" else "Xác minh Từ chối")
                }
            }
            if (state.pending == null && !accepted) TextButton(onClick = onRefresh) { Text("Tải lại lời mời", color = OfferRed) }
        }
    }
}

/** One host outside the NavHost. A route change cancels and consumes only this confirmed notice. */
@Composable
fun DriverRejectedOfferNotice(state: DriverOffersUiState, screen: String?, origin: String?, owner: Long?,
    active: Boolean, onConsumed: (DriverOffersUiState) -> Unit) {
    val host = remember(owner) { SnackbarHostState() }
    LaunchedEffect(state.phase, state.message, screen, owner, active) {
        if (state.phase == DriverOfferPhase.REJECTED && state.message != null) {
            try {
                if (owner != null && active && screen != null && screen == origin)
                    host.showSnackbar("Đã từ chối lời mời", duration = SnackbarDuration.Short)
            } finally { onConsumed(state) }
        }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        contentAlignment = Alignment.BottomCenter) { SnackbarHost(host) }
}
