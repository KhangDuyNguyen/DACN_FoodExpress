package vn.hutech.foodexpress.customer.restaurants.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.customer.restaurants.data.api.RestaurantApiService
import vn.hutech.foodexpress.customer.restaurants.data.model.MenuDto
import vn.hutech.foodexpress.customer.restaurants.data.model.RestaurantDetailDto
import javax.inject.Inject

data class RestaurantDetailUiState(
    val isLoading: Boolean = true,
    val restaurant: RestaurantDetailDto? = null,
    val menu: MenuDto? = null,
    val error: String? = null,
    val selectedCategoryId: Long? = null,
)

@HiltViewModel
class RestaurantDetailViewModel @Inject constructor(
    private val api: RestaurantApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Fix 5: không dùng checkNotNull — xử lý gracefully thay vì crash
    private val restaurantId: Long? = savedStateHandle["restaurantId"]

    private val _uiState = MutableStateFlow(RestaurantDetailUiState())
    val uiState: StateFlow<RestaurantDetailUiState> = _uiState

    init {
        if (restaurantId == null) {
            _uiState.value = RestaurantDetailUiState(
                isLoading = false,
                error = "ID quán không hợp lệ. Vui lòng quay lại và thử lại.",
            )
        } else {
            load()
        }
    }

    fun retry() {
        if (restaurantId != null) load()
    }

    fun selectCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    private fun load() {
        if (restaurantId == null) return
        _uiState.value = RestaurantDetailUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                val detailDeferred = async { api.getRestaurantDetail(restaurantId) }
                val menuDeferred = async { api.getRestaurantMenu(restaurantId) }
                Pair(detailDeferred.await(), menuDeferred.await())
            }.onSuccess { (detail, menu) ->
                _uiState.value = RestaurantDetailUiState(
                    isLoading = false,
                    restaurant = detail,
                    menu = menu,
                    selectedCategoryId = menu.categories.firstOrNull()?.id,
                )
            }.onFailure { err ->
                _uiState.value = RestaurantDetailUiState(
                    isLoading = false,
                    error = err.message ?: "Lỗi không xác định",
                )
            }
        }
    }
}
