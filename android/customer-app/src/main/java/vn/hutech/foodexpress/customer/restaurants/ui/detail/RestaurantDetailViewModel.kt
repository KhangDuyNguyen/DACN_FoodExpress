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

    private val restaurantId: Long = checkNotNull(savedStateHandle["restaurantId"])

    private val _uiState = MutableStateFlow(RestaurantDetailUiState())
    val uiState: StateFlow<RestaurantDetailUiState> = _uiState

    init { load() }

    fun retry() = load()

    fun selectCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedCategoryId = categoryId)
    }

    private fun load() {
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
