package project.pipepipe.app.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.database.DatabaseOperations.withProgress
import project.pipepipe.app.helper.FilterHelper
import project.pipepipe.app.helper.executeClientTasksConcurrent
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.app.uistate.ListUiState
import project.pipepipe.app.uistate.SearchSuggestion
import project.pipepipe.app.uistate.SearchUiState
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString

@OptIn(FlowPreview::class)
class SearchViewModel : BaseViewModel<SearchUiState>(SearchUiState()) {

    private val searchQueryFlow = MutableStateFlow("")
    private val debounceDelay = 600L

    init {
        searchQueryFlow
            .debounce(debounceDelay)
            .distinctUntilChanged()
            .onEach { query ->
                getSuggestionInternal(query)
            }
            .launchIn(viewModelScope)
    }

    fun updateSearchQuery(query: String) {
        setState {
            it.copy(searchQuery = query)
        }
        searchQueryFlow.value = query
    }

    fun removeSuggestion(text: String) {
        viewModelScope.launch {
            DatabaseOperations.deleteSearchHistoryByText(text)
            setState {
                it.copy(searchSuggestionList = it.searchSuggestionList.filterNot { suggestion -> suggestion.isLocal && suggestion.text == text })
            }
        }
    }

    fun getSuggestion(query: String) {
        viewModelScope.launch {
            getSuggestionInternal(query)
        }
    }

    private suspend fun getSuggestionInternal(query: String) {
        if (query.isEmpty()) {
            val localHistory = DatabaseOperations.getSearchHistory()
            setState {
                it.copy(searchSuggestionList = localHistory.map { SearchSuggestion(it.search, true) })
            }
        } else {
            if (query.startsWith("http://") || query.startsWith("https://")) return
            val service = uiState.value.selectedService ?: return
            val localHistory = DatabaseOperations.getSearchHistoryByPattern(query)
            var remoteSuggestionsResponse = service.suggestionPayload?.let {
                withContext(Dispatchers.IO) {
                    try {
                        executeClientTasksConcurrent(listOf(ClientTask(payload = service.suggestionPayload!!.copy(url = it.url + query))))[0].result
                    } catch (e: Exception) {
                        DatabaseOperations.insertErrorLog(
                            e.stackTraceToString(),
                            task = "GET_SUGGESTION",
                            errorCode = "IGN_001",
                            request = it.url + query,
                            serviceId = service.serviceId
                        )
                        null
                    }
                }
            }
            try {
                if (remoteSuggestionsResponse != null && service.suggestionJsonBetween != null) {
                    remoteSuggestionsResponse = remoteSuggestionsResponse
                        .substringBeforeLast(service.suggestionJsonBetween!!.second)
                        .substringAfter(service.suggestionJsonBetween!!.first)
                }
                val remoteSuggestionsResult: List<String>? = remoteSuggestionsResponse?.let { SharedContext.objectMapper.readTree(it) }
                    ?.requireArray(service.suggestionStringPath!!.first)
                    ?.map { it.requireString(service.suggestionStringPath!!.second) }

                val allSuggestions = localHistory.map { SearchSuggestion(it.search, true) }.toMutableList()
                remoteSuggestionsResult?.let {
                    allSuggestions += remoteSuggestionsResult.map { SearchSuggestion(it, false) }
                }
                setState {
                    it.copy(searchSuggestionList = allSuggestions)
                }
            } catch (e: Exception) {
                DatabaseOperations.insertErrorLog(
                    e.stackTraceToString(),
                    task = "GET_SUGGESTION",
                    errorCode = "IGN_001",
                    request = service.suggestionPayload!!.url + query,
                    serviceId = service.serviceId
                )
            }
        }
    }

    fun updateSelectedService(service: SupportedServiceInfo) {
        if (service == uiState.value) {
            return
        }
        setState {
            it.copy(
                selectedService = service,
                selectedSearchType = service.availableSearchTypes?.get(0)?.let { searchType ->
                    initializeSearchTypeWithDefaults(searchType)
                }
            )
        }
    }

    fun updateSelectedSearchType(searchType: SearchType) {
        setState {
            it.copy(selectedSearchType = initializeSearchTypeWithDefaults(searchType))
        }
    }

    fun resetFilters() {
        updateSelectedSearchType(uiState.value.selectedSearchType!!)
    }

    private fun initializeSearchTypeWithDefaults(searchType: SearchType): SearchType {
        return searchType.copy(
            availableSearchFilterGroups = searchType.availableSearchFilterGroups?.map { group ->
                val defaultFilters = when {
                    group.defaultFilter != null -> listOf(group.defaultFilter)
                    else -> emptyList()
                }
                group.copy(selectedSearchFilters = defaultFilters.filterNotNull())
            }
        )
    }

    fun addSearchFilter(groupName: String, filter: SearchFilterItem) {
        setState { currentState ->
            currentState.copy(
                selectedSearchType = currentState.selectedSearchType?.copy(
                    availableSearchFilterGroups = currentState.selectedSearchType.availableSearchFilterGroups?.map { group ->
                        if (group.groupName == groupName) {
                            val currentFilters = group.selectedSearchFilters

                            val newFilters = if (group.onlyOneCheckable) {
                                listOf(filter)
                            } else {
                                if (currentFilters.none { it.parameter == filter.parameter }) {
                                    currentFilters + filter
                                } else {
                                    currentFilters
                                }
                            }

                            group.copy(selectedSearchFilters = newFilters)
                        } else {
                            group
                        }
                    }
                )
            )
        }
    }

    fun removeSearchFilter(groupName: String, filter: SearchFilterItem) {
        setState { currentState ->
            currentState.copy(
                selectedSearchType = currentState.selectedSearchType?.copy(
                    availableSearchFilterGroups = currentState.selectedSearchType.availableSearchFilterGroups?.map { group ->
                        if (group.groupName == groupName) {
                            val currentFilters = group.selectedSearchFilters
                            if (group.defaultFilter != null && currentFilters.size <= 1) {
                                group
                            } else {
                                val newFilters = currentFilters.filter { it.parameter != filter.parameter }
                                val finalFilters = newFilters.ifEmpty {
                                    when {
                                        group.defaultFilter != null -> listOf(group.defaultFilter)
                                        else -> emptyList()
                                    }
                                }

                                group.copy(selectedSearchFilters = finalFilters.filterNotNull())
                            }
                        } else {
                            group
                        }
                    }
                )
            )
        }
    }

    fun toggleSearchFilter(groupName: String, filter: SearchFilterItem) {
        val currentState = uiState.value
        val group = currentState.selectedSearchType?.availableSearchFilterGroups
            ?.find { it.groupName == groupName }

        val currentFilters = group?.selectedSearchFilters ?: emptyList()
        val isFilterSelected = currentFilters.any { it.parameter == filter.parameter }

        if (isFilterSelected) {
            removeSearchFilter(groupName, filter)
        } else {
            addSearchFilter(groupName, filter)
        }
    }

    fun search(url: String, serviceId: Int = uiState.value.selectedService!!.serviceId) {
        viewModelScope.launch {
            setState {
                it.copy(
                    common = it.common.copy(isLoading = true, error = null),
                    list = ListUiState()
                )
            }
            val result = withContext(Dispatchers.IO) {
                executeJobFlow(
                    SupportedJobType.FETCH_FIRST_PAGE,
                    url,
                    serviceId
                )
            }

            // Check for fatal error first
            if (result.fatalError != null) {
                setState {
                    it.copy(
                        common = it.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                        )
                    )
                }
                return@launch
            }

            // Apply filters
            val shouldFilter = result.pagedData!!.itemList.getOrNull(0) is StreamInfo

            val rawItems = (result.pagedData!!.itemList as? List<StreamInfo>) ?: emptyList()
            val (filteredItems, _) = if (shouldFilter) FilterHelper.filterStreamInfoList(
                rawItems,
                FilterHelper.FilterScope.SEARCH_RESULT
            ) else Pair(emptyList(), null)

            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    list = it.list.copy(
                        itemList = if (shouldFilter) filteredItems.withProgress() else result.pagedData!!.itemList,
                        nextPageUrl = result.pagedData!!.nextPageUrl
                    ),
                )
            }
        }
    }

    fun loadMoreResults(serviceId: Int) {
        viewModelScope.launch {
            val nextUrl = uiState.value.list.nextPageUrl ?: return@launch
            setState {
                it.copy(
                    common = it.common.copy(isLoading = true, error = null)
                )
            }
            val result = withContext(Dispatchers.IO) {
                executeJobFlow(
                    SupportedJobType.FETCH_GIVEN_PAGE,
                    nextUrl,
                    serviceId
                )
            }

            // Check for fatal error first
            if (result.fatalError != null) {
                setState {
                    it.copy(
                        common = it.common.copy(
                            isLoading = false,
                            error = ErrorInfo(result.fatalError!!.errorId!!, result.fatalError!!.code, serviceId)
                        )
                    )
                }
                return@launch
            }

            // Apply filters
            val shouldFilter = result.pagedData?.itemList?.getOrNull(0) is StreamInfo

            val rawItems = (result.pagedData?.itemList as? List<StreamInfo>) ?: emptyList()
            val (filteredItems, _) = if (shouldFilter) FilterHelper.filterStreamInfoList(
                rawItems,
                FilterHelper.FilterScope.SEARCH_RESULT
            ) else Pair(emptyList(), 0)

            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = null
                    ),
                    list = it.list.copy(
                        itemList = it.list.itemList + (if (shouldFilter) filteredItems.withProgress() else result.pagedData?.itemList ?: emptyList()),
                        nextPageUrl = result.pagedData?.nextPageUrl
                    )
                )
            }
        }
    }
}
