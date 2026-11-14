package vn.hutech.foodexpress.customer.restaurants.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import vn.hutech.foodexpress.customer.restaurants.data.api.RestaurantApiService
import vn.hutech.foodexpress.customer.restaurants.data.model.CuisineDto
import vn.hutech.foodexpress.customer.restaurants.data.model.RestaurantSummaryDto
import javax.inject.Inject

data class RestaurantListUiState(
    val isLoading: Boolean = false,
    val restaurants: List<RestaurantSummaryDto> = emptyList(),
    val cuisines: List<CuisineDto> = emptyList(),
    val selectedCuisineId: Int? = null,
    val keyword: String = "",
    val error: String? = null,
    val hasMore: Boolean = false,  // Fix 7: default false tránh trigger loadMore sớm
    val page: Int = 0,
)

@HiltViewModel
class RestaurantListViewModel @Inject constructor(
    private val api: RestaurantApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestaurantListUiState())
    val uiState: StateFlow<RestaurantListUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    init {
        loadCuisines()
        loadRestaurants(reset = true, keyword = "", cuisineId = null)
        observeSearch()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        _searchQuery
            .debounce(400)
            .distinctUntilChanged()
            .onEach { kw ->
                // Fix 3: truyền keyword trực tiếp vào hàm, không đọc từ stale state
                loadRestaurants(reset = true, keyword = kw, cuisineId = _uiState.value.selectedCuisineId)
            }
            .launchIn(viewModelScope)
    }

    fun onKeywordChange(kw: String) {
        _searchQuery.value = kw
        _uiState.value = _uiState.value.copy(keyword = kw)
    }

    fun onCuisineSelected(cuisineId: Int?) {
        val kw = _uiState.value.keyword
        _uiState.value = _uiState.value.copy(selectedCuisineId = cuisineId)
        // Fix 3: truyền cuisineId trực tiếp vào hàm
        loadRestaurants(reset = true, keyword = kw, cuisineId = cuisineId)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        loadRestaurants(reset = false, keyword = state.keyword, cuisineId = state.selectedCuisineId)
    }

    fun retry() {
        val state = _uiState.value
        loadRestaurants(reset = true, keyword = state.keyword, cuisineId = state.selectedCuisineId)
    }

    private fun loadCuisines() {
        viewModelScope.launch {
            runCatching { api.getCuisines() }
                .onSuccess { _uiState.value = _uiState.value.copy(cuisines = it) }
        }
    }

    // Fix 3: keyword và cuisineId truyền trực tiếp — không đọc từ stale state snapshot
    private fun loadRestaurants(reset: Boolean, keyword: String, cuisineId: Int?) {
        val currentPage = if (reset) 0 else _uiState.value.page
        val currentList = if (reset) emptyList() else _uiState.value.restaurants

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            runCatching {
                api.searchRestaurants(
                    keyword = keyword.takeIf { it.isNotBlank() },
                    cuisineId = cuisineId,
                    page = currentPage,
                    size = 20,
                )
            }.onSuccess { resp ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restaurants = currentList + resp.data,
                    page = currentPage + 1,
                    // Fix 4: dùng totalPages thay vì so sánh size
                    hasMore = (currentPage + 1) < resp.totalPages,
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = err.message ?: "Lỗi không xác định",
                )
            }
        }
    }
}
