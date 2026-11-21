package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GlobalSearchRepository
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlobalSearchViewModel(
    private val searchRepo: GlobalSearchRepository
) : ViewModel() {

    private var instantSearchJob: Job? = null

    val uiState: StateFlow<GlobalSearchUiState> = combine(
        searchRepo.querySpec,
        searchRepo.pageResult,
        searchRepo.suggestions,
        searchRepo.history,
        searchRepo.recentResults,
        searchRepo.isSearching,
        searchRepo.isLoadingMore,
        searchRepo.errorMessage
    ) { flows ->
        val spec = flows[0] as SearchQuerySpec
        val page = flows[1] as SearchPageResult
        @Suppress("UNCHECKED_CAST")
        val suggestions = flows[2] as List<SearchSuggestion>
        @Suppress("UNCHECKED_CAST")
        val history = flows[3] as List<SearchHistoryEntry>
        @Suppress("UNCHECKED_CAST")
        val recents = flows[4] as List<UnifiedSearchResult>
        val isSearching = flows[5] as Boolean
        val isLoadingMore = flows[6] as Boolean
        val error = flows[7] as String?

        GlobalSearchUiState(
            querySpec = spec,
            results = page.items,
            totalMatchingCount = page.totalMatchingCount,
            hasMore = page.hasMore,
            countsByType = page.countsByType,
            availableAuthors = page.availableAuthors,
            suggestions = suggestions,
            history = history,
            recentResults = recents,
            isSearching = isSearching,
            isLoadingMore = isLoadingMore,
            engineId = page.engineId,
            tookMs = page.tookMs,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GlobalSearchUiState()
    )

    fun onQueryChanged(rawQuery: String) {
        instantSearchJob?.cancel()
        instantSearchJob = viewModelScope.launch {
            if (rawQuery.isNotBlank()) delay(110L)
            searchRepo.updateQuery(rawQuery)
        }
    }

    fun onSubmitQuery(rawQuery: String) {
        instantSearchJob?.cancel()
        viewModelScope.launch {
            searchRepo.updateQuery(rawQuery)
            searchRepo.commitQueryToHistory(rawQuery)
        }
    }

    fun onApplySuggestion(suggestion: SearchSuggestion) {
        instantSearchJob?.cancel()
        viewModelScope.launch {
            if (suggestion.targetType != null) {
                searchRepo.selectSingleType(suggestion.targetType)
            }
            searchRepo.updateQuery(suggestion.queryText)
        }
    }

    fun onToggleType(type: SearchEntityType) {
        viewModelScope.launch { searchRepo.toggleTypeFilter(type) }
    }

    fun onSelectSingleType(type: SearchEntityType) {
        viewModelScope.launch { searchRepo.selectSingleType(type) }
    }

    fun onSelectDateRange(range: SearchDateRange, customStartMs: Long? = null, customEndMs: Long? = null) {
        viewModelScope.launch { searchRepo.setDateRange(range, customStartMs, customEndMs) }
    }

    fun onSetAuthorFilter(author: String) {
        viewModelScope.launch { searchRepo.setAuthorFilter(author) }
    }

    fun onSetSortOrder(sort: SearchSortOrder) {
        viewModelScope.launch { searchRepo.setSortOrder(sort) }
    }

    fun onSetServerScope(serverId: String?) {
        viewModelScope.launch { searchRepo.setServerScope(serverId) }
    }

    fun onSetMessageSubFilter(filter: GlobalSearchFilter) {
        viewModelScope.launch { searchRepo.setMessageSubFilter(filter) }
    }

    fun onClearFilters() {
        viewModelScope.launch { searchRepo.clearAllFilters() }
    }

    fun onLoadNextPage() {
        viewModelScope.launch { searchRepo.loadNextPage() }
    }

    fun onDeleteHistory(entryId: String) {
        searchRepo.deleteHistoryEntry(entryId)
    }

    fun onClearHistory() {
        searchRepo.clearHistory()
    }

    fun onOpenResult(result: UnifiedSearchResult) {
        searchRepo.recordRecentItemInteraction(result)
    }

    fun refreshInitial() {
        viewModelScope.launch { searchRepo.runSearch() }
    }
}
