package com.example.data

import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SearchExecutionContext(
    val currentUid: String,
    val servers: List<Server>,
    val publicListings: List<PublicServerListing>,
    val directConversations: List<DirectConversation>,
    val messagesByTarget: Map<String, List<Message>>,
    val directoryUsers: Map<String, UserDirectoryEntry>,
    val friends: List<FriendRelationship>,
    val blockedUserIds: Set<String>,
    val stories: List<StoryItem>,
    val events: List<RavenEvent>,
    val bots: List<RavenBot>,
    val botInstallationsByServer: Map<String, List<ServerBotInstallation>>,
    val globalShopItems: List<ShopItem>,
    val nowMs: Long = System.currentTimeMillis()
)

interface SearchDomainAdapter {
    val domainType: SearchEntityType
    suspend fun searchDomain(
        parsedQuery: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult>
}

/**
 * Pluggable search engine SPI so RAVEN can swap or combine the local+Firestore
 * hybrid engine with a dedicated full-text/vector engine (e.g. Algolia, Typesense)
 * without altering repositories, ViewModels, or Compose UI.
 */
interface RavenSearchEngine {
    val engineId: String
    suspend fun executeSearch(
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): SearchPageResult

    fun buildSuggestions(
        rawQuery: String,
        history: List<SearchHistoryEntry>,
        context: SearchExecutionContext
    ): List<SearchSuggestion>

    fun collectRecentAccessibleItems(
        context: SearchExecutionContext,
        limit: Int = 8
    ): List<UnifiedSearchResult>
}

data class ParsedSearchInput(
    val cleanQuery: String,
    val inlineAuthor: String?,
    val inlineType: SearchEntityType?,
    val inlineDateRange: SearchDateRange?
)

object SearchQueryParser {

    fun parse(rawQuery: String): ParsedSearchInput {
        val tokens = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val remaining = mutableListOf<String>()
        var inlineAuthor: String? = null
        var inlineType: SearchEntityType? = null
        var inlineDateRange: SearchDateRange? = null

        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            when {
                lower.startsWith("de:") || lower.startsWith("author:") || lower.startsWith("from:") -> {
                    val value = token.substringAfter(":").trim()
                    if (value.isNotBlank()) inlineAuthor = value
                }
                lower.startsWith("type:") || lower.startsWith("dans:") -> {
                    val value = lower.substringAfter(":").trim()
                    inlineType = when (value) {
                        "user", "users", "utilisateur", "utilisateurs", "ami", "amis" -> SearchEntityType.USER
                        "server", "servers", "serveur", "serveurs" -> SearchEntityType.SERVER
                        "community", "communities", "communaute", "communautes", "communauté" -> SearchEntityType.COMMUNITY
                        "channel", "channels", "salon", "salons" -> SearchEntityType.CHANNEL
                        "message", "messages", "msg" -> SearchEntityType.MESSAGE
                        "dm", "conv", "conversation", "conversations" -> SearchEntityType.CONVERSATION
                        "event", "events", "evenement", "evenements", "événement" -> SearchEntityType.EVENT
                        "story", "stories" -> SearchEntityType.STORY
                        "bot", "bots" -> SearchEntityType.BOT
                        "shop", "market", "marketplace", "boutique" -> SearchEntityType.MARKETPLACE
                        else -> null
                    }
                }
                lower.startsWith("date:") || lower.startsWith("depuis:") -> {
                    val value = lower.substringAfter(":").trim()
                    inlineDateRange = when (value) {
                        "aujourd'hui", "aujourdhui", "today", "24h", "1j" -> SearchDateRange.TODAY
                        "7j", "7d", "semaine", "week" -> SearchDateRange.LAST_7_DAYS
                        "30j", "30d", "mois", "month" -> SearchDateRange.LAST_30_DAYS
                        else -> null
                    }
                }
                else -> remaining.add(token)
            }
        }

        return ParsedSearchInput(
            cleanQuery = remaining.joinToString(" ").trim(),
            inlineAuthor = inlineAuthor,
            inlineType = inlineType,
            inlineDateRange = inlineDateRange
        )
    }

    fun computeRelevanceScore(
        query: String,
        title: String,
        subtitle: String = "",
        snippet: String = "",
        author: String = "",
        recencyBoostMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        if (query.isBlank()) {
            val ageHours = ((nowMs - recencyBoostMs).coerceAtLeast(0L) / 3_600_000L).toInt()
            return (100 - ageHours.coerceAtMost(90)).coerceAtLeast(10)
        }
        val q = query.trim().lowercase(Locale.ROOT)
        val tLower = title.lowercase(Locale.ROOT)
        val sLower = subtitle.lowercase(Locale.ROOT)
        val snLower = snippet.lowercase(Locale.ROOT)
        val aLower = author.lowercase(Locale.ROOT)

        var score = 0
        if (tLower == q) score += 160
        else if (tLower.startsWith(q)) score += 120
        else if (tLower.contains(q)) score += 85

        if (aLower == q) score += 70
        else if (aLower.contains(q)) score += 45

        if (sLower.contains(q)) score += 35
        if (snLower.contains(q)) score += 30

        val words = q.split(" ").filter { it.length >= 2 }
        if (words.size > 1) {
            val matchedWords = words.count { w ->
                tLower.contains(w) || sLower.contains(w) || snLower.contains(w) || aLower.contains(w)
            }
            score += matchedWords * 18
        }

        if (recencyBoostMs > 0L) {
            val ageDays = ((nowMs - recencyBoostMs).coerceAtLeast(0L) / 86_400_000L).toInt()
            score += (20 - ageDays.coerceAtMost(20))
        }
        return score
    }

    fun matchesDateFilter(
        timestampMs: Long,
        dateRange: SearchDateRange,
        customStartMs: Long?,
        customEndMs: Long?,
        nowMs: Long
    ): Boolean {
        if (dateRange == SearchDateRange.ANY_TIME) return true
        if (timestampMs <= 0L) return false
        return when (dateRange) {
            SearchDateRange.ANY_TIME -> true
            SearchDateRange.TODAY -> timestampMs >= nowMs - 24L * 3_600_000L
            SearchDateRange.LAST_7_DAYS -> timestampMs >= nowMs - 7L * 86_400_000L
            SearchDateRange.LAST_30_DAYS -> timestampMs >= nowMs - 30L * 86_400_000L
            SearchDateRange.CUSTOM -> {
                val startOk = customStartMs == null || timestampMs >= customStartMs
                val endOk = customEndMs == null || timestampMs <= customEndMs
                startOk && endOk
            }
        }
    }

    fun estimateMessageTimestampMs(timestampLabel: String, nowMs: Long): Long {
        val clean = timestampLabel.trim()
        if (clean.isBlank() || clean.equals("À l'instant", ignoreCase = true) || clean.contains("Maintenant", ignoreCase = true)) {
            return nowMs - 30_000L
        }
        if (clean.contains("Hier", ignoreCase = true)) {
            return nowMs - 86_400_000L
        }
        val hhmmMatch = Regex("""(\d{1,2}):(\d{2})""").find(clean)
        if (hhmmMatch != null) {
            val hh = hhmmMatch.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 12
            val mm = hhmmMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hh)
                set(Calendar.MINUTE, mm)
                set(Calendar.SECOND, 0)
            }
            val candidate = cal.timeInMillis
            return if (candidate > nowMs) candidate - 86_400_000L else candidate
        }
        val parsed = runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(clean)?.time
        }.getOrNull()
        return parsed ?: (nowMs - 3_600_000L)
    }
}

/**
 * Pure permission boundary ensuring search NEVER leaks private, blocked, role-restricted,
 * uninvited, expired, moderated-hidden, or unowned resources.
 */
object SearchPermissionGuard {

    fun isServerAccessible(server: Server, uid: String): Boolean {
        if (server.isDmTab) return false
        if (server.isPublic) return true
        return server.ownerId == uid ||
            uid in server.adminIds ||
            uid in server.moderatorIds ||
            server.members.any { it.id == uid }
    }

    fun isServerMember(server: Server, uid: String): Boolean {
        if (server.isDmTab) return false
        return server.ownerId == uid ||
            uid in server.adminIds ||
            uid in server.moderatorIds ||
            server.members.any { it.id == uid }
    }

    fun canSeeCommunityListing(listing: PublicServerListing, matchedLocalServer: Server?, uid: String): Boolean {
        if (matchedLocalServer == null) return true
        if (isServerMember(matchedLocalServer, uid)) return true
        return matchedLocalServer.isPublic && matchedLocalServer.customization.privacy.discoverableInCatalog
    }

    fun canSeeChannel(server: Server, channel: Channel, uid: String): Boolean {
        if (!isServerAccessible(server, uid)) return false
        if (!isServerMember(server, uid) && server.isPublic && channel.isPrivate) return false
        return CommunityPermissions.isChannelAccessible(server, channel, uid)
    }

    fun canSeeConversation(conv: DirectConversation, uid: String, blockedIds: Set<String>): Boolean {
        if (conv.isGroup) {
            return conv.participantIds.isEmpty() || uid in conv.participantIds || conv.groupMembers.any { it.id == uid }
        }
        if (conv.friend.id in blockedIds) return false
        return conv.participantIds.isEmpty() || uid in conv.participantIds
    }

    fun canSeeUser(targetUser: User, uid: String, blockedIds: Set<String>): Boolean {
        if (targetUser.id.isBlank() || targetUser.id == uid || targetUser.id == "user_me") return false
        if (targetUser.id in blockedIds) return false
        return true
    }

    fun sanitizeUserForSearch(
        user: User,
        showOnlineStatus: Boolean = true,
        showLastActivity: Boolean = true
    ): User {
        return user.copy(
            email = null,
            phone = null,
            status = if (showOnlineStatus) user.status else UserStatus.OFFLINE,
            customStatus = if (showOnlineStatus) user.customStatus else "",
            lastActiveAtMs = if (showLastActivity) user.lastActiveAtMs else 0L,
            lastActivityLabel = if (showLastActivity) user.lastActivityLabel else ""
        )
    }

    fun canSeeStory(
        story: StoryItem,
        uid: String,
        blockedIds: Set<String>,
        nowMs: Long
    ): Boolean {
        if (story.isExpired(nowMs)) return false
        return story.canBeViewedBy(uid = uid, blockedAuthorIds = blockedIds)
    }

    fun canSeeEvent(
        event: RavenEvent,
        uid: String,
        servers: List<Server>
    ): Boolean {
        if (event.creatorId == uid) return true
        if (event.isPublic) return true
        if (uid in event.invitedUserIds) return true
        if (event.visibility == EventVisibility.PRIVATE_INVITE) return false
        if (uid in event.memberIds) return true
        val srv = event.serverId?.let { sId -> servers.find { it.id == sId } } ?: return false
        return isServerAccessible(srv, uid)
    }

    fun canSeeBot(
        bot: RavenBot,
        uid: String,
        accessibleServerIds: Set<String>,
        botInstallationsByServer: Map<String, List<ServerBotInstallation>>
    ): Boolean {
        if (bot.ownerUid == uid) return true
        if (bot.isPublicCatalog) return true
        return accessibleServerIds.any { srvId ->
            botInstallationsByServer[srvId].orEmpty().any { it.botId == bot.id }
        }
    }

    fun canSeeServerShopItem(server: Server, uid: String): Boolean {
        return isServerMember(server, uid)
    }
}
