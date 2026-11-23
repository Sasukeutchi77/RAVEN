package com.example.data

import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GlobalSearchRepository(
    private val chatRepo: ChatRepository,
    private val friendRepo: FriendRepository,
    private val storyRepo: StoryRepository,
    private val eventRepo: EventRepository,
    private val botRepo: BotRepository,
    private var activeEngine: RavenSearchEngine = LocalAndFirestoreSearchEngine(
        firestoreProvider = { chatRepo.firestoreDb() },
        authProvider = { chatRepo.authInstance() }
    ),
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _querySpec = MutableStateFlow(SearchQuerySpec())
    val querySpec: StateFlow<SearchQuerySpec> = _querySpec.asStateFlow()

    private val _pageResult = MutableStateFlow(SearchPageResult())
    val pageResult: StateFlow<SearchPageResult> = _pageResult.asStateFlow()

    private val _history = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val history: StateFlow<List<SearchHistoryEntry>> = _history.asStateFlow()

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

    private val _recentResults = MutableStateFlow<List<UnifiedSearchResult>>(emptyList())
    val recentResults: StateFlow<List<UnifiedSearchResult>> = _recentResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var historyListener: ListenerRegistration? = null
    private var syncedAuthUid: String? = null
    private val clickedRecents = mutableListOf<UnifiedSearchResult>()

    init {
        loadLocalHistory()
        refreshSuggestionsAndRecents()
    }

    /**
     * Allows swapping in a dedicated backend search engine (Algolia/Typesense/Vector)
     * without changing repositories, ViewModels, or UI.
     */
    fun registerSearchEngine(engine: RavenSearchEngine) {
        activeEngine = engine
    }

    fun syncWithAuthUid(uid: String?) {
        val db = chatRepo.firestoreDb()
        if (uid.isNullOrBlank() || db == null) {
            historyListener?.remove()
            historyListener = null
            syncedAuthUid = null
            return
        }
        if (syncedAuthUid == uid) return
        syncedAuthUid = uid
        historyListener?.remove()
        historyListener = db.collection("users").document(uid)
            .collection("search_history")
            .limit(20)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val cloudEntries = snap.documents.mapNotNull { doc ->
                    val q = doc.getString("query")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SearchHistoryEntry(
                        id = doc.getString("id") ?: doc.id,
                        query = q,
                        selectedTypeName = doc.getString("selectedTypeName") ?: SearchEntityType.ALL.name,
                        authorFilter = doc.getString("authorFilter") ?: "",
                        timestampMs = doc.getLong("timestampMs") ?: 0L
                    )
                }.sortedByDescending { it.timestampMs }.take(20)
                if (cloudEntries.isNotEmpty()) {
                    _history.value = cloudEntries
                    refreshSuggestionsAndRecents()
                }
            }
    }

    fun buildExecutionContext(): SearchExecutionContext {
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
        return SearchExecutionContext(
            currentUid = uid,
            servers = chatRepo.servers.value,
            publicListings = chatRepo.discoverServers.value,
            directConversations = chatRepo.directConversations.value,
            messagesByTarget = chatRepo.messages.value,
            directoryUsers = friendRepo.directoryUsers.value,
            friends = friendRepo.friends.value,
            blockedUserIds = chatRepo.blockedUsers.value.map { it.id }.toSet(),
            stories = storyRepo.stories.value,
            events = eventRepo.events.value,
            bots = botRepo.bots.value,
            botInstallationsByServer = botRepo.installationsByServer.value,
            globalShopItems = ShopCatalog.initialShopItems,
            nowMs = nowProvider()
        )
    }

    suspend fun runSearch(spec: SearchQuerySpec = _querySpec.value) {
        _isSearching.value = spec.pageIndex == 0
        _isLoadingMore.value = spec.pageIndex > 0
        _errorMessage.value = null
        try {
            val ctx = buildExecutionContext()
            val page = activeEngine.executeSearch(spec, ctx)
            _pageResult.value = page
            refreshSuggestionsAndRecents(ctx)
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Erreur pendant la recherche."
        } finally {
            _isSearching.value = false
            _isLoadingMore.value = false
        }
    }

    suspend fun updateQuery(rawQuery: String) {
        val next = _querySpec.value.copy(rawQuery = rawQuery, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun selectSingleType(type: SearchEntityType) {
        val nextSet = if (type == SearchEntityType.ALL) emptySet() else setOf(type)
        val next = _querySpec.value.copy(selectedTypes = nextSet, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun toggleTypeFilter(type: SearchEntityType) {
        if (type == SearchEntityType.ALL) {
            selectSingleType(SearchEntityType.ALL)
            return
        }
        val current = _querySpec.value.selectedTypes
        val nextSet = if (type in current) current - type else current + type
        val next = _querySpec.value.copy(selectedTypes = nextSet, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun setDateRange(range: SearchDateRange, customStartMs: Long? = null, customEndMs: Long? = null) {
        val next = _querySpec.value.copy(
            dateRange = range,
            customStartMs = customStartMs,
            customEndMs = customEndMs,
            pageIndex = 0
        )
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun setAuthorFilter(author: String) {
        val next = _querySpec.value.copy(authorFilter = author.trim(), pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun setSortOrder(sort: SearchSortOrder) {
        val next = _querySpec.value.copy(sortOrder = sort, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun setServerScope(serverId: String?) {
        val next = _querySpec.value.copy(serverScopeId = serverId, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun setMessageSubFilter(filter: GlobalSearchFilter) {
        val next = _querySpec.value.copy(messageSubFilter = filter, pageIndex = 0)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun clearAllFilters() {
        val next = SearchQuerySpec(rawQuery = _querySpec.value.rawQuery, pageSize = _querySpec.value.pageSize)
        _querySpec.value = next
        runSearch(next)
    }

    suspend fun loadNextPage() {
        if (!_pageResult.value.hasMore || _isLoadingMore.value) return
        val next = _querySpec.value.copy(pageIndex = _querySpec.value.pageIndex + 1)
        _querySpec.value = next
        runSearch(next)
    }

    fun commitQueryToHistory(queryOverride: String? = null) {
        val clean = (queryOverride ?: _querySpec.value.rawQuery).trim()
        if (clean.length < 2) return
        val typeName = _querySpec.value.selectedTypes.firstOrNull()?.name ?: SearchEntityType.ALL.name
        val entry = SearchHistoryEntry(
            id = "sh_${UUID.randomUUID().toString().take(10)}",
            query = clean.take(120),
            selectedTypeName = typeName,
            authorFilter = _querySpec.value.authorFilter.take(64),
            timestampMs = nowProvider()
        )
        val deduplicated = listOf(entry) + _history.value.filterNot {
            it.query.equals(clean, ignoreCase = true) && it.selectedTypeName == typeName
        }
        _history.value = deduplicated.take(20)
        saveLocalHistory()
        refreshSuggestionsAndRecents()

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("search_history").document(entry.id)
                            .set(
                                mapOf(
                                    "id" to entry.id,
                                    "userId" to authUid,
                                    "query" to entry.query,
                                    "selectedTypeName" to entry.selectedTypeName,
                                    "authorFilter" to entry.authorFilter,
                                    "timestampMs" to entry.timestampMs
                                )
                            )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        _history.value = _history.value.filterNot { it.id == entryId }
        saveLocalHistory()
        refreshSuggestionsAndRecents()

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("search_history").document(entryId)
                            .delete()
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearHistory() {
        val currentIds = _history.value.map { it.id }
        _history.value = emptyList()
        saveLocalHistory()
        refreshSuggestionsAndRecents()

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null && currentIds.isNotEmpty()) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                currentIds.forEach { id ->
                    runCatching {
                        Tasks.await(
                            db.collection("users").document(authUid)
                                .collection("search_history").document(id)
                                .delete()
                        )
                    }
                }
            }
        }
    }

    fun recordRecentItemInteraction(result: UnifiedSearchResult) {
        clickedRecents.removeAll { it.type == result.type && it.id == result.id }
        clickedRecents.add(0, result)
        if (clickedRecents.size > 8) {
            clickedRecents.subList(8, clickedRecents.size).clear()
        }
        if (_querySpec.value.rawQuery.isNotBlank()) {
            commitQueryToHistory(_querySpec.value.rawQuery)
        } else {
            refreshSuggestionsAndRecents()
        }
    }

    fun refreshSuggestionsAndRecents(ctx: SearchExecutionContext = buildExecutionContext()) {
        _suggestions.value = activeEngine.buildSuggestions(_querySpec.value.rawQuery, _history.value, ctx)
        val freshAccessible = activeEngine.collectRecentAccessibleItems(ctx, limit = 8)
        val merged = (clickedRecents + freshAccessible)
            .distinctBy { "${it.type.name}:${it.id}" }
            .take(8)
        _recentResults.value = merged
    }

    private fun saveLocalHistory() {
        val ctx = chatRepo.appContextOrNull() ?: return
        runCatching {
            val serialized = _history.value.joinToString("\n") { entry ->
                "${entry.id}\t${entry.query.replace("\t", " ")}\t${entry.selectedTypeName}\t${entry.authorFilter}\t${entry.timestampMs}"
            }
            ctx.getSharedPreferences("raven_search_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("history_v1", serialized)
                .apply()
        }
    }

    private fun loadLocalHistory() {
        val ctx = chatRepo.appContextOrNull() ?: return
        runCatching {
            val raw = ctx.getSharedPreferences("raven_search_prefs", android.content.Context.MODE_PRIVATE)
                .getString("history_v1", null) ?: return
            _history.value = raw.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split("\t")
                    if (parts.size < 5) null
                    else SearchHistoryEntry(
                        id = parts[0],
                        query = parts[1],
                        selectedTypeName = parts[2],
                        authorFilter = parts[3],
                        timestampMs = parts[4].toLongOrNull() ?: 0L
                    )
                }.take(20)
        }
    }
}
