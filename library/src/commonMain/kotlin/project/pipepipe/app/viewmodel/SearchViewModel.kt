package project.pipepipe.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.FilterHelper
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.app.uistate.SearchSuggestion
import project.pipepipe.app.uistate.SearchUiState
import project.pipepipe.shared.infoitem.StreamInfo
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.shared.job.ClientTask
import project.pipepipe.shared.job.SupportedJobType
import project.pipepipe.app.helper.executeClientTasksConcurrent
import project.pipepipe.app.helper.executeJobFlow
import project.pipepipe.shared.utils.json.asJson
import project.pipepipe.shared.utils.json.requireArray
import project.pipepipe.shared.utils.json.requireString

class SearchViewModel() : BaseViewModel<SearchUiState>(SearchUiState()) {

    fun updateSearchQuery(query: String) {
        setState {
            it.copy(searchQuery = query)
        }
    }

    suspend fun getSuggestion(query: String) {

        if (query.isEmpty()) {
            val localHistory = DatabaseOperations.getSearchHistory()
            setState {
                it.copy(searchSuggestionList = localHistory.map { SearchSuggestion(it.search, true) })
            }
        } else {
            val service = uiState.value.selectedService!!
            val localHistory = DatabaseOperations.getSearchHistoryByPattern(query)
            var remoteSuggestionsReponse = service.suggestionPayload?.let {
                withContext(Dispatchers.IO) {
                    executeClientTasksConcurrent(listOf(ClientTask(payload = service.suggestionPayload.copy(url = it.url + query))))[0].result
                }
            }
            if (remoteSuggestionsReponse != null && service.suggestionJsonBetween != null) {
                remoteSuggestionsReponse = remoteSuggestionsReponse
                    .substringBeforeLast(service.suggestionJsonBetween.second)
                    .substringAfter(service.suggestionJsonBetween.first)
            }
            val remoteSuggestionsResult: List<String>? = remoteSuggestionsReponse?.let { SharedContext.objectMapper.readTree(it)}
                ?.requireArray(service.suggestionStringPath!!.first)
                ?.map { it.requireString(service.suggestionStringPath.second) }

            val allSuggestions = localHistory.map { SearchSuggestion(it.search, true) }.toMutableList()
            remoteSuggestionsResult?.let {
                allSuggestions += remoteSuggestionsResult.map { SearchSuggestion(it, false) }
            }
            setState {
                it.copy(searchSuggestionList = allSuggestions)
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
                group.copy(selectedSearchFilters = defaultFilters)
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

                                group.copy(selectedSearchFilters = finalFilters)
                            }
                        } else {
                            group
                        }
                    }
                )
            )
        }
    }

    // Helper function to toggle a filter (add if not present, remove if present)
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
    suspend fun search(url: String) {
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
            )
        }
        val result = withContext(Dispatchers.IO) {
            executeJobFlow(
                SupportedJobType.FETCH_FIRST_PAGE,
                url,
                uiState.value.selectedService!!.serviceId
            )
        }

        // Check for fatal error first
        if (result.fatalError != null) {
            setState {
                it.copy(
                    common = it.common.copy(
                        isLoading = false,
                        error = ErrorInfo(result.fatalError.errorId!!, result.fatalError.code)
                    )
                )
            }
            return
        }


        // Apply filters
        val shouldFilter = result.pagedData!!.itemList.get(0) is StreamInfo

        val rawItems = (result.pagedData.itemList as? List<StreamInfo>) ?: emptyList()
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
                    itemList = if(shouldFilter)filteredItems else result.pagedData.itemList,
                    nextPageUrl = result.pagedData.nextPageUrl
                ),
            )
        }
    }

    suspend fun loadMoreResults(serviceId: String) {
        val nextUrl = uiState.value.list.nextPageUrl ?: return
        setState {
            it.copy(
                common = it.common.copy(isLoading = true)
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
                        error = ErrorInfo(result.fatalError.errorId!!, result.fatalError.code)
                    )
                )
            }
            return
        }

        // Apply filters
        val rawItems = (result.pagedData?.itemList as? List<StreamInfo>) ?: emptyList()
        val (filteredItems, _) = FilterHelper.filterStreamInfoList(
            rawItems,
            FilterHelper.FilterScope.SEARCH_RESULT
        )

        setState {
            it.copy(
                common = it.common.copy(
                    isLoading = false,
                    error = null
                ),
                list = it.list.copy(
                    itemList = it.list.itemList + filteredItems,
                    nextPageUrl = result.pagedData?.nextPageUrl
                )
            )
        }
    }
}