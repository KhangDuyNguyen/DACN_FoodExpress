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
    val hasMore: Boolean = true,
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
        loadRestaurants(reset = true)
        observeSearch()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        _searchQuery
            .debounce(400)
            .distinctUntilChanged()
            .onEach { loadRestaurants(reset = true) }
            .launchIn(viewModelScope)
    }

    fun onKeywordChange(kw: String) {
        _searchQuery.value = kw
        _uiState.value = _uiState.value.copy(keyword = kw)
    }

    fun onCuisineSelected(cuisineId: Int?) {
        _uiState.value = _uiState.value.copy(selectedCuisineId = cuisineId)
        loadRestaurants(reset = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return
        loadRestaurants(reset = false)
    }

    fun retry() = loadRestaurants(reset = true)

    private fun loadCuisines() {
        viewModelScope.launch {
            runCatching { api.getCuisines() }
                .onSuccess { _uiState.value = _uiState.value.copy(cuisines = it) }
        }
    }

    private fun loadRestaurants(reset: Boolean) {
        val state = _uiState.value
        val nextPage = if (reset) 0 else state.page
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                api.searchRestaurants(
                    keyword = state.keyword.takeIf { it.isNotBlank() },
                    cuisineId = state.selectedCuisineId,
                    page = nextPage,
                    size = 20,
                )
            }.onSuccess { resp ->
                val newList = if (reset) resp.data else state.restaurants + resp.data
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restaurants = newList,
                    page = nextPage + 1,
                    hasMore = resp.data.size >= 20,
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
