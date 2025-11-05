package project.pipepipe.app.ui.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.app.uistate.SearchUiState
import project.pipepipe.app.viewmodel.SearchViewModel

class SearchViewModel : ViewModel() {
    private val sharedViewModel: SearchViewModel = SearchViewModel()
    val uiState: StateFlow<SearchUiState> = sharedViewModel.uiState

    private val searchQueryFlow = MutableStateFlow("")
    private val debounceDelay = 600L
    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(debounceDelay)
                .distinctUntilChanged()
                .collect { query ->
                    getSuggestion(query)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        sharedViewModel.updateSearchQuery(query)
        searchQueryFlow.value = query
    }

    fun getSuggestion(query: String) {
        viewModelScope.launch {
            sharedViewModel.getSuggestion(query)
        }
    }

    fun removeSuggestion(query: String) {
        viewModelScope.launch {
            sharedViewModel.removeSuggestion(query)
        }
    }

    fun updateSelectedService(service: SupportedServiceInfo) {
        sharedViewModel.updateSelectedService(service)
    }

    fun updateSelectedSearchType(searchType: SearchType) {
        sharedViewModel.updateSelectedSearchType(searchType)
    }

    fun resetFilters() {
        sharedViewModel.resetFilters()
    }

    fun addSearchFilter(groupName: String, filter: SearchFilterItem) {
        sharedViewModel.addSearchFilter(groupName, filter)
    }

    fun removeSearchFilter(groupName: String, filter: SearchFilterItem) {
        sharedViewModel.removeSearchFilter(groupName, filter)
    }

    fun toggleSearchFilter(groupName: String, filter: SearchFilterItem) {
        sharedViewModel.toggleSearchFilter(groupName, filter)
    }

    fun search(query: String, listState: LazyListState, serviceId: String = uiState.value.selectedService!!.serviceId) {
        viewModelScope.launch {
            listState.scrollToItem(0)
            sharedViewModel.search(query, serviceId)
        }
    }

    fun loadMoreResults(serviceId: String) {
        viewModelScope.launch {
            sharedViewModel.loadMoreResults(serviceId)
        }
    }
}