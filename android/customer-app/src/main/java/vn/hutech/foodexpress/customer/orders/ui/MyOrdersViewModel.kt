package vn.hutech.foodexpress.customer.orders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.customer.orders.data.api.CreateReviewRequest
import vn.hutech.foodexpress.customer.orders.data.api.OrderApiService
import vn.hutech.foodexpress.customer.orders.data.api.OrderSummary
import vn.hutech.foodexpress.customer.orders.data.api.ReviewResponse
import javax.inject.Inject

data class MyOrdersUiState(
    val isLoading: Boolean = false,
    val orders: List<OrderSummary> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val error: String? = null,
)

sealed class ReviewState {
    data object Idle : ReviewState()
    data object Loading : ReviewState()
    data class Loaded(val review: ReviewResponse) : ReviewState()   // Đã có review
    data object NotFound : ReviewState()                            // Chưa review
    data object Submitting : ReviewState()
    data object Success : ReviewState()
    data class Error(val msg: String) : ReviewState()
}

@HiltViewModel
class MyOrdersViewModel @Inject constructor(
    private val api: OrderApiService,
) : ViewModel() {

    // Token lưu tạm — trong production nên lấy từ DataStore/SharedPrefs
    // Hiện tại app Android chưa có auth flow cho customer nên dùng placeholder
    private val token: String = ""   // TODO(tuần 7): lấy token từ auth session

    private val _uiState = MutableStateFlow(MyOrdersUiState())
    val uiState: StateFlow<MyOrdersUiState> = _uiState

    private val _reviewState = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val reviewState: StateFlow<ReviewState> = _reviewState

    init { loadOrders(0) }

    fun loadPage(page: Int) = loadOrders(page)

    private fun loadOrders(page: Int) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.getMyOrders(
                    authorization = "Bearer $token",
                    page = page,
                    size = 10,
                    status = null,
                )
            }.onSuccess { resp ->
                _uiState.value = MyOrdersUiState(
                    isLoading = false,
                    orders = resp.items,
                    page = resp.page,
                    totalPages = resp.totalPages,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = err.message ?: "Lỗi tải đơn hàng",
                )
            }
        }
    }

    fun loadReview(orderId: Long) {
        _reviewState.value = ReviewState.Loading
        viewModelScope.launch {
            runCatching { api.getOrderReview("Bearer $token", orderId) }
                .onSuccess { _reviewState.value = ReviewState.Loaded(it) }
                .onFailure { _reviewState.value = ReviewState.NotFound }
        }
    }

    fun submitReview(orderId: Long, restaurantRating: Int, driverRating: Int?, comment: String?) {
        _reviewState.value = ReviewState.Submitting
        viewModelScope.launch {
            runCatching {
                api.createReview(
                    "Bearer $token", orderId,
                    CreateReviewRequest(restaurantRating, driverRating, comment?.takeIf { it.isNotBlank() })
                )
            }.onSuccess { _reviewState.value = ReviewState.Success }
                .onFailure { _reviewState.value = ReviewState.Error(it.message ?: "Lỗi gửi đánh giá") }
        }
    }

    fun resetReviewState() { _reviewState.value = ReviewState.Idle }
}
