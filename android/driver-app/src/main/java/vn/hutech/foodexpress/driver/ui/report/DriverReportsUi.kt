package vn.hutech.foodexpress.driver.ui.report

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val ReportRed = Color(0xFFE7192D)
internal val ReportPink = Color(0xFFFFF0F2)
internal val ReportInk = Color(0xFF18202B)
internal val ReportMuted = Color(0xFF505968)
internal val ReportGreen = Color(0xFF087D32)
fun reportMoney(value: BigDecimal): String = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"))).format(value) + "đ"
internal fun reportTime(value: LocalDateTime?) = value?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "Chưa có thời điểm hoàn thành"
internal fun reportDay(value: java.time.LocalDate): String = value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportScaffold(title: String, onBack: (() -> Unit)? = null, onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = Color(0xFFFFFBFC), topBar = {
        TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { ReportIcon(ReportSymbol.BACK, "Quay lại", Color.White) }
        }, actions = {
            if (onRefresh != null) IconButton(onClick = onRefresh, enabled = !refreshing) {
                ReportIcon(ReportSymbol.REFRESH, "Làm mới", Color.White)
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ReportRed, titleContentColor = Color.White))
    }, bottomBar = { Column(Modifier.navigationBarsPadding()) { bottomBar() } }, content = content)
}

/** Material3 gesture/indicator, matching Dashboard. Short and empty LazyColumns still receive pulls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportPullToRefresh(refreshing: Boolean, enabled: Boolean, atTop: () -> Boolean,
    onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val pull = rememberPullToRefreshState()
    var startedAtTop by remember { mutableStateOf(false) }
    val latestAtTop by rememberUpdatedState(atTop)
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            startedAtTop = latestAtTop()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!latestAtTop()) startedAtTop = false
            } while (event.changes.any { it.pressed })
        }
    }.pullToRefresh(isRefreshing = refreshing, state = pull, threshold = 100.dp,
        enabled = enabled && !refreshing && startedAtTop,
        onRefresh = { if (enabled && !refreshing && startedAtTop && atTop()) onRefresh() })) {
        content()
        PullToRefreshDefaults.Indicator(state = pull, isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter), containerColor = Color.White, color = ReportRed)
    }
}

@Composable
internal fun ReportCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    if (onClick == null) Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } else Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
internal fun ReportTitle(text: String) = Text(text, color = ReportInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
internal fun ReportLine(symbol: ReportSymbol, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReportIcon(symbol)
        Text(text, Modifier.weight(1f), color = ReportInk)
    }
}
@Composable
internal fun ReportBadge(text: String) {
    Surface(color = Color(0xFFE9F8ED), shape = RoundedCornerShape(20.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = ReportGreen, fontWeight = FontWeight.Bold)
    }
}
@Composable
internal fun ReportNotice(title: String, message: String, symbol: ReportSymbol = ReportSymbol.RECEIPT,
    onRetry: (() -> Unit)? = null) {
    ReportCard {
        ReportIcon(symbol, modifier = Modifier.size(40.dp))
        ReportTitle(title)
        Text(message, color = ReportMuted)
        if (onRetry != null) Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ReportRed)) { Text("Thử lại") }
    }
}
@Composable
internal fun ReportLoading() {
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = ReportRed, strokeWidth = 2.dp)
        Text("Đang tải dữ liệu…", color = ReportMuted)
    }
}

internal enum class ReportSymbol(path: String) {
    WALLET("M3,5 L19,5 L19,9 M3,5 L3,21 L21,21 L21,9 L3,9 M16,13 L21,13 L21,17 L16,17 Z M3,5 L17,2 L17,5"),
    RECEIPT("M5,2 L19,2 L19,22 L16,20 L13,22 L10,20 L7,22 L5,20 Z M8,7 L16,7 M8,11 L16,11 M8,15 L13,15"),
    RESTAURANT("M4,10 L4,21 L20,21 L20,10 M3,10 L5,3 L19,3 L21,10 M3,10 Q5,14 7,10 Q9,14 12,10 Q15,14 17,10 Q19,14 21,10 M9,21 L9,15 L15,15 L15,21"),
    TIME("M22,12 C22,25 2,25 2,12 C2,-1 22,-1 22,12 Z M12,6 L12,12 L16,14"),
    PIN("M12,22 C9,18 4,14 4,9 C4,-1 20,-1 20,9 C20,14 15,18 12,22 M15,9 C15,13 9,13 9,9 C9,5 15,5 15,9 Z"),
    CHECK("M3,12 L9,18 L21,5"), BACK("M15,4 L7,12 L15,20"),
    REFRESH("M20,10 C20,-1 2,0 3,13 C4,25 22,23 21,14 M20,3 L20,10 L13,10"),
    ERROR("M12,3 L22,21 L2,21 Z M12,9 L12,14 M12,17 L12,18");
    val vector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()
}
@Composable
internal fun ReportIcon(symbol: ReportSymbol, description: String? = null, color: Color = ReportRed, modifier: Modifier = Modifier) {
    Icon(symbol.vector, contentDescription = description, tint = color, modifier = modifier.size(24.dp))
}

// Capture the Activity baseline outside destinations, before M06 or overlapping transitions own the bar.
@Suppress("DEPRECATION")
@Composable
fun DriverReportsSystemBars(active: Boolean, lifecycle: Lifecycle? = null) {
    val view = LocalView.current
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    val window = activity?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    val previous = remember(window) { controller?.isAppearanceLightStatusBars }
    val color = remember(window) { window?.statusBarColor }
    if (active) DisposableEffect(window, view, lifecycle) {
        var attached = true
        fun apply() {
            controller?.isAppearanceLightStatusBars = false
            window?.statusBarColor = android.graphics.Color.rgb(231, 25, 45)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) view.post {
                if (attached && lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) apply()
            }
        }
        apply()
        lifecycle?.addObserver(observer)
        onDispose {
            attached = false
            lifecycle?.removeObserver(observer)
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
            if (color != null) window?.statusBarColor = color
        }
    }
}
