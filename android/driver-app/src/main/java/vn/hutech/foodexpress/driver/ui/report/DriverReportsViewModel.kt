package vn.hutech.foodexpress.driver.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.driver.data.report.*

enum class DriverReportsPage { NONE, EARNINGS, ORDERS }
data class DriverReportsState(
    val ownerId: Long? = null, val period: EarningsPeriod = EarningsPeriod.TODAY,
    val historySelected: Boolean = false, val earnings: DriverEarnings? = null,
    val earningsLoading: Boolean = false, val earningsError: String? = null,
    val history: List<DriverHistoryOrder> = emptyList(), val historyLoading: Boolean = false,
    val historyError: String? = null, val historyPage: Int = -1, val totalPages: Int = 0,
    val totalItems: Long? = null, val selectedEarning: DriverEarning? = null,
    val selectedHistory: DriverHistoryOrder? = null, val sessionExpired: Boolean = false, val accessDenied: Boolean = false,
)
fun DriverReportsState.forReportOwner(id: Long?): DriverReportsState = when {
    id == null || id <= 0 || ownerId != id || sessionExpired -> DriverReportsState()
    accessDenied -> DriverReportsState(ownerId = id, period = period, historySelected = historySelected, accessDenied = true)
    else -> this
}

@HiltViewModel
class DriverReportsViewModel(private val source: DriverReportsSource, private val clock: Clock) : ViewModel() {
    @Inject constructor(source: DriverReportsSource) : this(source, Clock.systemUTC())
    private val mutableState = MutableStateFlow(DriverReportsState())
    val state = mutableState.asStateFlow()
    private var owner: Long? = null
    private var authenticated = false
    private var foreground = false
    private var visible = DriverReportsPage.NONE
    private var earningJob: Job? = null
    private var historyJob: Job? = null
    private var earningGeneration = 0L
    private var historyGeneration = 0L

    fun activateSession(id: Long) {
        if (id <= 0) return
        if (owner != id) { clear(); owner = id }
        val changed = !authenticated
        authenticated = true
        mutableState.value = state.value.copy(ownerId = id, sessionExpired = false)
        if (changed) loadVisible()
    }
    fun pauseSessionVerification() {
        authenticated = false; cancelReads()
        mutableState.value = DriverReportsState(period = state.value.period, historySelected = state.value.historySelected)
    }
    fun clear() {
        cancelReads(); owner = null; authenticated = false
        mutableState.value = DriverReportsState()
    }
    fun setForeground(value: Boolean) {
        if (foreground == value) return
        foreground = value
        if (value) loadVisible() else cancelReads()
    }
    fun show(page: DriverReportsPage) {
        if (visible == page) return
        visible = page; loadVisible()
    }
    fun selectPeriod(period: EarningsPeriod) {
        if (period == state.value.period) return
        earningGeneration++; earningJob?.cancel()
        mutableState.value = state.value.copy(period = period, earnings = null, selectedEarning = null,
            earningsLoading = false, earningsError = null)
        refreshEarnings()
    }
    fun selectHistory(value: Boolean) {
        mutableState.value = state.value.copy(historySelected = value)
        if (value && state.value.historyPage < 0) refreshHistory()
    }
    fun selectEarning(id: Long): Boolean {
        val item = state.value.earnings?.items?.find { it.orderId == id } ?: return false
        if (!canRead()) return false
        mutableState.value = state.value.copy(selectedEarning = item); return true
    }
    fun selectHistoryOrder(id: Long): Boolean {
        val item = state.value.history.find { it.orderId == id } ?: return false
        if (!canRead()) return false
        mutableState.value = state.value.copy(selectedHistory = item); return true
    }
    fun invalidateAfterDelivery() {
        cancelReads()
        mutableState.value = state.value.copy(earnings = null, history = emptyList(), historyPage = -1,
            totalItems = null, totalPages = 0, selectedEarning = null, selectedHistory = null)
        loadVisible()
    }
    private fun canRead() = authenticated && foreground && owner != null && state.value.ownerId == owner && !state.value.sessionExpired && !state.value.accessDenied
    private fun loadVisible() {
        if (!canRead()) return
        when (visible) {
            DriverReportsPage.EARNINGS -> refreshEarnings()
            DriverReportsPage.ORDERS -> if (state.value.historySelected) refreshHistory()
            else -> Unit
        }
    }
    fun refreshEarnings() {
        if (!canRead() || state.value.earningsLoading) return
        val id = owner; val generation = ++earningGeneration
        val range = EarningsRange.forPeriod(state.value.period, clock)
        mutableState.value = state.value.copy(earningsLoading = true, earningsError = null)
        earningJob = viewModelScope.launch {
            val result = try { source.earnings(range) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ReportsResult.Failed("Chưa thể tải thu nhập. Vui lòng thử lại.") }
            ensureActive()
            if (!canRead() || owner != id || generation != earningGeneration) return@launch
            when (result) {
                is ReportsResult.Loaded -> mutableState.value = state.value.copy(earnings = result.value, earningsLoading = false,
                    selectedEarning = state.value.selectedEarning?.let { selected -> result.value.items.find { it.orderId == selected.orderId } })
                is ReportsResult.Failed -> mutableState.value = state.value.copy(earningsLoading = false, earningsError = result.message)
                ReportsResult.AuthRequired -> expire()
                ReportsResult.Forbidden -> denyAccess()
            }
        }
    }
    fun refreshHistory() {
        if (!canRead() || state.value.historyLoading) return
        mutableState.value = state.value.copy(history = emptyList(), historyPage = -1, totalPages = 0, totalItems = null, historyError = null)
        requestHistory(0)
    }
    fun loadMoreHistory() {
        val current = state.value
        if (!canRead() || current.historyLoading || current.historyPage < 0 || current.historyPage + 1 >= current.totalPages) return
        requestHistory(current.historyPage + 1)
    }
    private fun requestHistory(page: Int) {
        val id = owner; val generation = ++historyGeneration
        mutableState.value = state.value.copy(historyLoading = true, historyError = null)
        historyJob = viewModelScope.launch {
            val result = try { source.history(page, 10) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ReportsResult.Failed("Chưa thể tải lịch sử. Vui lòng thử lại.") }
            ensureActive()
            if (!canRead() || owner != id || generation != historyGeneration) return@launch
            when (result) {
                is ReportsResult.Loaded -> {
                    val rows = (if (page == 0) result.value.items else state.value.history + result.value.items).associateBy { it.orderId }.values.toList()
                    mutableState.value = state.value.copy(history = rows, historyLoading = false, historyPage = page,
                        totalPages = result.value.totalPages, totalItems = result.value.totalItems)
                }
                is ReportsResult.Failed -> mutableState.value = state.value.copy(historyLoading = false, historyError = result.message)
                ReportsResult.AuthRequired -> expire()
                ReportsResult.Forbidden -> denyAccess()
            }
        }
    }
    private fun expire() {
        cancelReads(); authenticated = false
        mutableState.value = DriverReportsState(sessionExpired = true)
    }
    private fun denyAccess() {
        cancelReads(); authenticated = false
        // A business permission failure must not trigger an automatic auth/read retry loop.
        mutableState.value = DriverReportsState(ownerId = owner, period = state.value.period,
            historySelected = state.value.historySelected, accessDenied = true)
    }
    private fun cancelReads() {
        earningGeneration++; historyGeneration++
        earningJob?.cancel(); historyJob?.cancel(); earningJob = null; historyJob = null
        mutableState.value = state.value.copy(earningsLoading = false, historyLoading = false)
    }
}
