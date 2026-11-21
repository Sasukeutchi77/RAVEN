package com.example.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

enum class SearchEntityType(val label: String, val pluralLabel: String) {
    ALL("Tout", "Tous les résultats"),
    USER("Utilisateur", "Utilisateurs"),
    SERVER("Serveur", "Serveurs"),
    COMMUNITY("Communauté", "Communautés"),
    CHANNEL("Salon", "Salons"),
    MESSAGE("Message", "Messages"),
    CONVERSATION("Conversation", "Conversations"),
    EVENT("Événement", "Événements"),
    STORY("Story", "Stories"),
    BOT("Bot", "Bots"),
    MARKETPLACE("Marketplace", "Marketplace");

    companion object {
        fun fromRaw(raw: String?): SearchEntityType =
            entries.firstOrNull { it.name == raw } ?: ALL

        val domainTypes: List<SearchEntityType>
            get() = entries.filter { it != ALL }
    }
}

enum class SearchSortOrder(val label: String) {
    RELEVANCE("Pertinence"),
    NEWEST("Plus récents"),
    OLDEST("Plus anciens"),
    ALPHABETICAL("A → Z")
}

enum class SearchDateRange(val label: String, val windowMs: Long?) {
    ANY_TIME("Toutes dates", null),
    TODAY("Aujourd'hui (24h)", 24L * 3_600_000L),
    LAST_7_DAYS("7 derniers jours", 7L * 86_400_000L),
    LAST_30_DAYS("30 derniers jours", 30L * 86_400_000L),
    CUSTOM("Plage personnalisée", null)
}

data class SearchQuerySpec(
    val rawQuery: String = "",
    val selectedTypes: Set<SearchEntityType> = emptySet(),
    val dateRange: SearchDateRange = SearchDateRange.ANY_TIME,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
    val authorFilter: String = "",
    val serverScopeId: String? = null,
    val messageSubFilter: GlobalSearchFilter = GlobalSearchFilter.ALL,
    val sortOrder: SearchSortOrder = SearchSortOrder.RELEVANCE,
    val pageSize: Int = 15,
    val pageIndex: Int = 0
)

data class UnifiedSearchResult(
    val id: String,
    val type: SearchEntityType,
    val title: String,
    val subtitle: String,
    val snippet: String,
    val badgeLabel: String,
    val serverId: String? = null,
    val serverName: String? = null,
    val channelId: String? = null,
    val conversationId: String? = null,
    val targetId: String? = null,
    val authorId: String? = null,
    val authorName: String? = null,
    val timestampMs: Long = 0L,
    val timestampLabel: String = "",
    val relevanceScore: Int = 0,
    val accentColorHex: Long = 0xFF5865F2L,
    val isPublic: Boolean = false,
    val userPayload: User? = null,
    val messagePayload: Message? = null,
    val storyPayload: StoryItem? = null,
    val eventPayload: RavenEvent? = null,
    val botPayload: RavenBot? = null,
    val shopItemPayload: ShopItem? = null,
    val serverShopPayload: Pair<String, ServerShopItem>? = null,
    val publicListingPayload: PublicServerListing? = null
)

data class SearchSuggestion(
    val id: String,
    val label: String,
    val queryText: String,
    val subtitle: String,
    val targetType: SearchEntityType? = null,
    val isHistory: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class SearchHistoryEntry(
    val id: String = "",
    val query: String = "",
    val selectedTypeName: String = SearchEntityType.ALL.name,
    val authorFilter: String = "",
    val timestampMs: Long = 0L
)

data class SearchPageResult(
    val items: List<UnifiedSearchResult> = emptyList(),
    val pageIndex: Int = 0,
    val pageSize: Int = 15,
    val totalMatchingCount: Int = 0,
    val hasMore: Boolean = false,
    val countsByType: Map<SearchEntityType, Int> = emptyMap(),
    val availableAuthors: List<String> = emptyList(),
    val engineId: String = "raven_local_firestore_hybrid_v1",
    val tookMs: Long = 0L
)

data class GlobalSearchUiState(
    val querySpec: SearchQuerySpec = SearchQuerySpec(),
    val results: List<UnifiedSearchResult> = emptyList(),
    val totalMatchingCount: Int = 0,
    val hasMore: Boolean = false,
    val countsByType: Map<SearchEntityType, Int> = emptyMap(),
    val availableAuthors: List<String> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val history: List<SearchHistoryEntry> = emptyList(),
    val recentResults: List<UnifiedSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val engineId: String = "raven_local_firestore_hybrid_v1",
    val tookMs: Long = 0L,
    val errorMessage: String? = null
)
