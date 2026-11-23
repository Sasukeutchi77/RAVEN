package com.example.data

import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalAndFirestoreSearchEngine(
    private val firestoreProvider: () -> FirebaseFirestore? = { null },
    private val authProvider: () -> FirebaseAuth? = { null },
    private val cloudLimitPerQuery: Long = 20L
) : RavenSearchEngine {

    override val engineId: String = "raven_local_firestore_hybrid_v1"

    private data class CloudCacheEntry(
        val fetchedAtMs: Long,
        val items: List<UnifiedSearchResult>
    )

    private val cloudCache = mutableMapOf<String, CloudCacheEntry>()
    private val cacheTtlMs = 15_000L

    override suspend fun executeSearch(
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): SearchPageResult {
        val startNano = System.nanoTime()
        val parsed = SearchQueryParser.parse(spec.rawQuery)
        val effectiveDateRange = parsed.inlineDateRange ?: spec.dateRange
        val effectiveAuthor = spec.authorFilter.trim().ifBlank { parsed.inlineAuthor.orEmpty() }
        val activeTypes = when {
            spec.selectedTypes.isNotEmpty() -> spec.selectedTypes - SearchEntityType.ALL
            parsed.inlineType != null -> setOf(parsed.inlineType)
            else -> SearchEntityType.domainTypes.toSet()
        }

        // Always collect across selected types (or all 10 if ALL)
        val allMatched = mutableListOf<UnifiedSearchResult>()
        val authorPool = linkedSetOf<String>()

        if (SearchEntityType.USER in activeTypes) {
            allMatched += searchUsersDomain(parsed, spec, context)
        }
        if (SearchEntityType.SERVER in activeTypes) {
            allMatched += searchServersDomain(parsed, spec, context)
        }
        if (SearchEntityType.COMMUNITY in activeTypes) {
            allMatched += searchCommunitiesDomain(parsed, spec, context)
        }
        if (SearchEntityType.CHANNEL in activeTypes) {
            allMatched += searchChannelsDomain(parsed, spec, context)
        }
        if (SearchEntityType.MESSAGE in activeTypes) {
            allMatched += searchMessagesDomain(parsed, spec, context)
        }
        if (SearchEntityType.CONVERSATION in activeTypes) {
            allMatched += searchConversationsDomain(parsed, spec, context)
        }
        if (SearchEntityType.EVENT in activeTypes) {
            allMatched += searchEventsDomain(parsed, spec, context)
        }
        if (SearchEntityType.STORY in activeTypes) {
            allMatched += searchStoriesDomain(parsed, spec, context)
        }
        if (SearchEntityType.BOT in activeTypes) {
            allMatched += searchBotsDomain(parsed, spec, context)
        }
        if (SearchEntityType.MARKETPLACE in activeTypes) {
            allMatched += searchMarketplaceDomain(parsed, spec, context)
        }

        val deduped = allMatched.distinctBy { "${it.type.name}:${it.id}" }

        deduped.mapNotNull { it.authorName?.takeIf { a -> a.isNotBlank() } }
            .forEach { authorPool.add(it) }

        val filtered = deduped.filter { item ->
            val authorOk = effectiveAuthor.isBlank() ||
                (item.authorName?.contains(effectiveAuthor, ignoreCase = true) == true) ||
                item.title.contains(effectiveAuthor, ignoreCase = true) && item.type == SearchEntityType.USER
            val dateOk = SearchQueryParser.matchesDateFilter(
                timestampMs = item.timestampMs,
                dateRange = effectiveDateRange,
                customStartMs = spec.customStartMs,
                customEndMs = spec.customEndMs,
                nowMs = context.nowMs
            )
            val serverScopeOk = spec.serverScopeId.isNullOrBlank() ||
                spec.serverScopeId == "dm_tab" ||
                item.serverId == spec.serverScopeId ||
                item.type == SearchEntityType.MARKETPLACE && item.serverId == spec.serverScopeId
            authorOk && dateOk && serverScopeOk
        }

        val countsByType = filtered.groupingBy { it.type }.eachCount()

        val sorted = when (spec.sortOrder) {
            SearchSortOrder.RELEVANCE -> filtered.sortedWith(
                compareByDescending<UnifiedSearchResult> { it.relevanceScore }
                    .thenByDescending { it.timestampMs }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
            )
            SearchSortOrder.NEWEST -> filtered.sortedWith(
                compareByDescending<UnifiedSearchResult> { it.timestampMs }
                    .thenByDescending { it.relevanceScore }
            )
            SearchSortOrder.OLDEST -> filtered.sortedWith(
                compareBy<UnifiedSearchResult> { if (it.timestampMs <= 0L) Long.MAX_VALUE else it.timestampMs }
                    .thenByDescending { it.relevanceScore }
            )
            SearchSortOrder.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
        }

        val safePageSize = spec.pageSize.coerceIn(5, 50)
        val visibleLimit = ((spec.pageIndex.coerceAtLeast(0) + 1) * safePageSize)
        val pageItems = sorted.take(visibleLimit)
        val hasMore = sorted.size > pageItems.size
        val tookMs = ((System.nanoTime() - startNano) / 1_000_000L).coerceAtLeast(1L)

        return SearchPageResult(
            items = pageItems,
            pageIndex = spec.pageIndex.coerceAtLeast(0),
            pageSize = safePageSize,
            totalMatchingCount = sorted.size,
            hasMore = hasMore,
            countsByType = countsByType,
            availableAuthors = authorPool.toList().sorted(),
            engineId = engineId,
            tookMs = tookMs
        )
    }

    override fun buildSuggestions(
        rawQuery: String,
        history: List<SearchHistoryEntry>,
        context: SearchExecutionContext
    ): List<SearchSuggestion> {
        val q = rawQuery.trim()
        val list = mutableListOf<SearchSuggestion>()

        history
            .filter { q.isBlank() || it.query.contains(q, ignoreCase = true) }
            .take(4)
            .forEach { entry ->
                val type = SearchEntityType.fromRaw(entry.selectedTypeName)
                list += SearchSuggestion(
                    id = "hist_${entry.id}",
                    label = entry.query,
                    queryText = entry.query,
                    subtitle = if (type == SearchEntityType.ALL) "Recherche récente" else "Récent • ${type.label}",
                    targetType = type.takeIf { it != SearchEntityType.ALL },
                    isHistory = true
                )
            }

        if (q.isBlank()) {
            list += listOf(
                SearchSuggestion("op_author", "Filtrer par auteur (de:Nom)", "de:", "Opérateur auteur", SearchEntityType.MESSAGE),
                SearchSuggestion("op_today", "Aujourd'hui (depuis:24h)", "depuis:24h ", "Résultats des dernières 24h", null),
                SearchSuggestion("op_story", "Stories actives", "type:stories ", "Stories visibles selon confidentialité", SearchEntityType.STORY),
                SearchSuggestion("op_bots", "Bots du catalogue", "type:bots ", "Bots accessibles & installés", SearchEntityType.BOT)
            )
            return list.distinctBy { it.queryText.lowercase() }.take(8)
        }

        // Live contextual suggestions from accessible entities
        val accessibleServers = context.servers.filter { SearchPermissionGuard.isServerAccessible(it, context.currentUid) }
        accessibleServers
            .filter { it.name.contains(q, ignoreCase = true) }
            .take(2)
            .forEach { srv ->
                list += SearchSuggestion(
                    id = "sug_srv_${srv.id}",
                    label = srv.name,
                    queryText = srv.name,
                    subtitle = "Serveur • ${srv.members.size} membres",
                    targetType = SearchEntityType.SERVER
                )
            }

        context.friends
            .filter { it.friendUid !in context.blockedUserIds && it.user.name.contains(q, ignoreCase = true) }
            .take(2)
            .forEach { rel ->
                list += SearchSuggestion(
                    id = "sug_usr_${rel.friendUid}",
                    label = "${rel.user.name}${rel.user.tag}",
                    queryText = rel.user.name,
                    subtitle = "Ami • ${rel.user.status.label}",
                    targetType = SearchEntityType.USER
                )
            }

        accessibleServers
            .flatMap { srv -> srv.channels.filter { SearchPermissionGuard.canSeeChannel(srv, it, context.currentUid) } }
            .filter { it.name.contains(q, ignoreCase = true) }
            .take(2)
            .forEach { ch ->
                list += SearchSuggestion(
                    id = "sug_ch_${ch.id}",
                    label = "#${ch.name}",
                    queryText = ch.name,
                    subtitle = "Salon ${if (ch.type == ChannelType.VOICE) "vocal" else "textuel"}",
                    targetType = SearchEntityType.CHANNEL
                )
            }

        return list.distinctBy { "${it.targetType?.name}_${it.queryText.lowercase()}" }.take(8)
    }

    override fun collectRecentAccessibleItems(
        context: SearchExecutionContext,
        limit: Int
    ): List<UnifiedSearchResult> {
        val recent = mutableListOf<UnifiedSearchResult>()
        val now = context.nowMs

        // Active accessible stories
        context.stories
            .filter { SearchPermissionGuard.canSeeStory(it, context.currentUid, context.blockedUserIds, now) }
            .sortedByDescending { it.createdAtMs }
            .take(3)
            .forEach { story -> recent += mapStoryToResult(story, 100, now) }

        // Upcoming or recent accessible events
        context.events
            .filter { SearchPermissionGuard.canSeeEvent(it, context.currentUid, context.servers) }
            .sortedByDescending { it.updatedAtMs }
            .take(3)
            .forEach { evt -> recent += mapEventToResult(evt, 95, now) }

        // Recent accessible DM conversations
        context.directConversations
            .filter { SearchPermissionGuard.canSeeConversation(it, context.currentUid, context.blockedUserIds) }
            .take(3)
            .forEachIndexed { idx, conv ->
                recent += mapConversationToResult(conv, 90 - idx, now - idx * 180_000L)
            }

        // Latest accessible channel messages
        val accessibleServers = context.servers.filter { SearchPermissionGuard.isServerAccessible(it, context.currentUid) }
        for (srv in accessibleServers) {
            for (ch in srv.channels) {
                if (!SearchPermissionGuard.canSeeChannel(srv, ch, context.currentUid)) continue
                val lastMsg = context.messagesByTarget[ch.id]?.lastOrNull() ?: continue
                val ts = SearchQueryParser.estimateMessageTimestampMs(lastMsg.timestamp, now)
                recent += mapChannelMessageToResult(srv, ch, lastMsg, 85, ts)
                if (recent.size >= limit + 4) break
            }
        }

        return recent
            .distinctBy { "${it.type.name}:${it.id}" }
            .sortedByDescending { it.timestampMs }
            .take(limit)
    }

    // =========================================================================
    // Domain 1: USERS (Directory + Friends + Accessible Server Members)
    // =========================================================================
    private suspend fun searchUsersDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val blocked = context.blockedUserIds
        val pool = linkedMapOf<String, Pair<User, UserDirectoryEntry?>>()

        context.friends.forEach { rel ->
            if (SearchPermissionGuard.canSeeUser(rel.user, uid, blocked)) {
                pool[rel.friendUid] = rel.user to context.directoryUsers[rel.friendUid]
            }
        }
        context.directoryUsers.forEach { (id, entry) ->
            if (SearchPermissionGuard.canSeeUser(entry.user, uid, blocked) && !pool.containsKey(id)) {
                pool[id] = entry.user to entry
            }
        }
        context.servers
            .filter { SearchPermissionGuard.isServerAccessible(it, uid) }
            .flatMap { it.members }
            .forEach { member ->
                if (SearchPermissionGuard.canSeeUser(member, uid, blocked) && !pool.containsKey(member.id)) {
                    pool[member.id] = member to context.directoryUsers[member.id]
                }
            }

        // Optional bounded cloud supplement when signed in and query length >= 2
        fetchBoundedCloudPublicProfiles(q, uid, context.nowMs).forEach { entry ->
            if (SearchPermissionGuard.canSeeUser(entry.user, uid, blocked) && !pool.containsKey(entry.user.id)) {
                pool[entry.user.id] = entry.user to entry
            }
        }

        return pool.values.mapNotNull { (rawUser, dirEntry) ->
            val showOnline = dirEntry?.showOnlineStatus ?: true
            val showActivity = dirEntry?.showLastActivity ?: true
            val safeUser = SearchPermissionGuard.sanitizeUserForSearch(rawUser, showOnline, showActivity)
            val fullTag = "${safeUser.name}${safeUser.tag}"
            val subtitle = "${safeUser.status.label}${safeUser.clanTag?.let { " • [$it]" }.orEmpty()}"
            val snippet = when {
                safeUser.customStatus.isNotBlank() -> safeUser.customStatus
                safeUser.bio.isNotBlank() -> safeUser.bio
                else -> "Profil Raven"
            }
            if (q.isNotBlank() &&
                !fullTag.contains(q, ignoreCase = true) &&
                !safeUser.bio.contains(q, ignoreCase = true) &&
                !(safeUser.clanTag?.contains(q, ignoreCase = true) == true)
            ) return@mapNotNull null

            val actMs = if (showActivity) safeUser.lastActiveAtMs else 0L
            UnifiedSearchResult(
                id = "usr_${safeUser.id}",
                type = SearchEntityType.USER,
                title = safeUser.name,
                subtitle = subtitle,
                snippet = snippet,
                badgeLabel = safeUser.tag,
                targetId = safeUser.id,
                authorId = safeUser.id,
                authorName = safeUser.name,
                timestampMs = actMs,
                timestampLabel = if (actMs > 0L) FriendFirestoreSync.formatRelativeActivityTime(actMs, context.nowMs) else "Activité masquée",
                relevanceScore = SearchQueryParser.computeRelevanceScore(q, fullTag, subtitle, snippet, safeUser.name, actMs, context.nowMs),
                accentColorHex = safeUser.avatarColor,
                isPublic = true,
                userPayload = safeUser
            )
        }
    }

    // =========================================================================
    // Domain 2: SERVERS
    // =========================================================================
    private suspend fun searchServersDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val mergedServers = context.servers
            .filter { SearchPermissionGuard.isServerAccessible(it, uid) }
            .toMutableList()

        fetchBoundedCloudPublicServers(q, context.nowMs).forEach { cloudSrv ->
            if (SearchPermissionGuard.isServerAccessible(cloudSrv, uid) &&
                mergedServers.none { it.id == cloudSrv.id }
            ) {
                mergedServers += cloudSrv
            }
        }

        return mergedServers.mapNotNull { srv ->
            val ownerName = srv.members.find { it.id == srv.ownerId }?.name
                ?: srv.ownerId.takeIf { it.isNotBlank() }
                ?: "Fondateur"
            if (q.isNotBlank() &&
                !srv.name.contains(q, ignoreCase = true) &&
                !srv.description.contains(q, ignoreCase = true) &&
                !srv.categoryTag.contains(q, ignoreCase = true) &&
                !srv.uniqueHandle.contains(q, ignoreCase = true)
            ) return@mapNotNull null

            val isMember = SearchPermissionGuard.isServerMember(srv, uid)
            UnifiedSearchResult(
                id = "srv_${srv.id}",
                type = SearchEntityType.SERVER,
                title = srv.name,
                subtitle = "${srv.categoryTag} • ${srv.members.size.coerceAtLeast(1)} membres",
                snippet = srv.description,
                badgeLabel = if (isMember) "Membre" else "Public",
                serverId = srv.id,
                serverName = srv.name,
                targetId = srv.id,
                authorId = srv.ownerId,
                authorName = ownerName,
                timestampMs = context.nowMs - 3_600_000L,
                timestampLabel = if (srv.isPublic) "Serveur public" else "Serveur privé",
                relevanceScore = SearchQueryParser.computeRelevanceScore(q, srv.name, srv.categoryTag, srv.description, ownerName, context.nowMs, context.nowMs),
                accentColorHex = srv.customBannerColorHex ?: srv.iconBgColor,
                isPublic = srv.isPublic,
                publicListingPayload = CommunityPermissions.serverToPublicListing(srv)
            )
        }
    }

    // =========================================================================
    // Domain 3: COMMUNITIES (Discoverable & Joined Communities)
    // =========================================================================
    private suspend fun searchCommunitiesDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val localById = context.servers.filterNot { it.isDmTab }.associateBy { it.id }

        val listings = (context.publicListings +
            context.servers.filter { !it.isDmTab && (it.isPublic || SearchPermissionGuard.isServerMember(it, uid)) }
                .map { CommunityPermissions.serverToPublicListing(it) })
            .distinctBy { it.id }
            .toMutableList()

        fetchBoundedCloudPublicServers(q, context.nowMs).forEach { srv ->
            if (srv.isPublic && srv.customization.privacy.discoverableInCatalog && listings.none { it.id == srv.id }) {
                listings += CommunityPermissions.serverToPublicListing(srv)
            }
        }

        return listings.mapNotNull { listing ->
            val matchedLocal = localById[listing.id]
            if (!SearchPermissionGuard.canSeeCommunityListing(listing, matchedLocal, uid)) return@mapNotNull null
            val handle = matchedLocal?.uniqueHandle.orEmpty()
            if (q.isNotBlank() &&
                !listing.name.contains(q, ignoreCase = true) &&
                !listing.description.contains(q, ignoreCase = true) &&
                !listing.categoryTag.contains(q, ignoreCase = true) &&
                !handle.contains(q, ignoreCase = true)
            ) return@mapNotNull null

            UnifiedSearchResult(
                id = "comm_${listing.id}",
                type = SearchEntityType.COMMUNITY,
                title = listing.name,
                subtitle = "${listing.categoryTag} • ${listing.memberCount} membres${if (listing.isVerified) " • Vérifiée" else ""}",
                snippet = listing.description,
                badgeLabel = if (listing.isVerified) "Vérifiée" else "Communauté",
                serverId = listing.id,
                serverName = listing.name,
                targetId = listing.id,
                timestampMs = context.nowMs - 7_200_000L,
                timestampLabel = "@${handle.ifBlank { listing.inviteCode }}",
                relevanceScore = SearchQueryParser.computeRelevanceScore(q, listing.name, listing.categoryTag, listing.description, "", context.nowMs, context.nowMs),
                accentColorHex = listing.iconBgColor,
                isPublic = true,
                publicListingPayload = listing
            )
        }
    }

    // =========================================================================
    // Domain 4: CHANNELS (Strict Role & Category Access Check)
    // =========================================================================
    private fun searchChannelsDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val results = mutableListOf<UnifiedSearchResult>()

        for (srv in context.servers) {
            if (!SearchPermissionGuard.isServerAccessible(srv, uid)) continue
            for (ch in srv.channels) {
                if (!SearchPermissionGuard.canSeeChannel(srv, ch, uid)) continue
                if (q.isNotBlank() &&
                    !ch.name.contains(q, ignoreCase = true) &&
                    !ch.topic.contains(q, ignoreCase = true) &&
                    !ch.category.contains(q, ignoreCase = true) &&
                    !srv.name.contains(q, ignoreCase = true)
                ) continue

                val prefix = when (ch.type) {
                    ChannelType.VOICE -> "🔊"
                    ChannelType.STAGE -> "🎙️"
                    ChannelType.ANNOUNCEMENT -> "📢"
                    ChannelType.TEXT -> "#"
                }
                results += UnifiedSearchResult(
                    id = "ch_${ch.id}",
                    type = SearchEntityType.CHANNEL,
                    title = "$prefix ${ch.name}",
                    subtitle = "${srv.name} • ${ch.category}",
                    snippet = ch.topic.ifBlank { "Salon ${ch.type.name.lowercase(Locale.ROOT)} de ${srv.name}" },
                    badgeLabel = if (ch.isPrivate) "Privé" else ch.type.name,
                    serverId = srv.id,
                    serverName = srv.name,
                    channelId = ch.id,
                    targetId = ch.id,
                    timestampMs = context.nowMs - 1_800_000L,
                    timestampLabel = if (ch.activeVoiceUsers.isNotEmpty()) "${ch.activeVoiceUsers.size} en vocal" else srv.name,
                    relevanceScore = SearchQueryParser.computeRelevanceScore(q, ch.name, srv.name, ch.topic, "", context.nowMs, context.nowMs),
                    accentColorHex = srv.customBannerColorHex ?: srv.iconBgColor,
                    isPublic = srv.isPublic && !ch.isPrivate
                )
            }
        }
        return results
    }

    // =========================================================================
    // Domain 5: MESSAGES (Accessible Channels + Non-Blocked DMs)
    // =========================================================================
    private fun searchMessagesDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val results = mutableListOf<UnifiedSearchResult>()

        // Channel messages (strictly gated by server & channel permissions)
        for (srv in context.servers) {
            if (!SearchPermissionGuard.isServerAccessible(srv, uid)) continue
            for (ch in srv.channels) {
                if (!SearchPermissionGuard.canSeeChannel(srv, ch, uid)) continue
                val msgs = context.messagesByTarget[ch.id].orEmpty()
                for (msg in msgs) {
                    if (msg.sender.id in context.blockedUserIds) continue
                    if (!matchesMessageSubFilter(msg, spec.messageSubFilter)) continue
                    if (q.isNotBlank() && !matchesMessageQuery(msg, q)) continue
                    val ts = SearchQueryParser.estimateMessageTimestampMs(msg.timestamp, context.nowMs)
                    val score = SearchQueryParser.computeRelevanceScore(
                        q, msg.text, "${srv.name} #${ch.name}", msg.attachmentName.orEmpty(), msg.sender.name, ts, context.nowMs
                    )
                    results += mapChannelMessageToResult(srv, ch, msg, score, ts)
                }
            }
        }

        // DM messages (only if no specific server scope is set, or scope is dm_tab)
        if (spec.serverScopeId.isNullOrBlank() || spec.serverScopeId == "dm_tab") {
            for (conv in context.directConversations) {
                if (!SearchPermissionGuard.canSeeConversation(conv, uid, context.blockedUserIds)) continue
                val msgs = context.messagesByTarget[conv.id].orEmpty()
                val convTitle = if (conv.isGroup) conv.groupName ?: "Groupe" else "@${conv.friend.name}"
                for (msg in msgs) {
                    if (msg.sender.id in context.blockedUserIds) continue
                    if (!matchesMessageSubFilter(msg, spec.messageSubFilter)) continue
                    if (q.isNotBlank() && !matchesMessageQuery(msg, q)) continue
                    val ts = SearchQueryParser.estimateMessageTimestampMs(msg.timestamp, context.nowMs)
                    val score = SearchQueryParser.computeRelevanceScore(
                        q, msg.text, convTitle, msg.attachmentName.orEmpty(), msg.sender.name, ts, context.nowMs
                    )
                    results += UnifiedSearchResult(
                        id = "msg_${conv.id}_${msg.id}",
                        type = SearchEntityType.MESSAGE,
                        title = msg.sender.name,
                        subtitle = "DM • $convTitle",
                        snippet = messageSnippet(msg),
                        badgeLabel = if (msg.isPinned) "Épinglé" else "DM",
                        conversationId = conv.id,
                        targetId = conv.id,
                        authorId = msg.sender.id,
                        authorName = msg.sender.name,
                        timestampMs = ts,
                        timestampLabel = msg.timestamp,
                        relevanceScore = score,
                        accentColorHex = msg.sender.avatarColor,
                        isPublic = false,
                        messagePayload = msg
                    )
                }
            }
        }
        return results
    }

    // =========================================================================
    // Domain 6: CONVERSATIONS (DMs & Group DMs of the current user)
    // =========================================================================
    private fun searchConversationsDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        return context.directConversations.mapIndexedNotNull { idx, conv ->
            if (!SearchPermissionGuard.canSeeConversation(conv, uid, context.blockedUserIds)) return@mapIndexedNotNull null
            val title = if (conv.isGroup) conv.groupName ?: "Groupe (${conv.groupMembers.size})" else conv.friend.name
            val membersLabel = if (conv.isGroup) {
                conv.groupMembers.joinToString(", ") { it.name }
            } else {
                "${conv.friend.name}${conv.friend.tag}"
            }
            if (q.isNotBlank() &&
                !title.contains(q, ignoreCase = true) &&
                !membersLabel.contains(q, ignoreCase = true) &&
                !conv.lastMessage.contains(q, ignoreCase = true)
            ) return@mapIndexedNotNull null

            val ts = SearchQueryParser.estimateMessageTimestampMs(conv.lastMessageTime, context.nowMs - idx * 60_000L)
            mapConversationToResult(
                conv = conv,
                score = SearchQueryParser.computeRelevanceScore(q, title, membersLabel, conv.lastMessage, title, ts, context.nowMs),
                tsMs = ts
            )
        }
    }

    // =========================================================================
    // Domain 7: EVENTS (RavenEvent + Accessible ServerEvents)
    // =========================================================================
    private suspend fun searchEventsDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val eventsPool = context.events.filter {
            SearchPermissionGuard.canSeeEvent(it, uid, context.servers)
        }.toMutableList()

        fetchBoundedCloudPublicEvents(q, context.nowMs).forEach { cloudEv ->
            if (SearchPermissionGuard.canSeeEvent(cloudEv, uid, context.servers) &&
                eventsPool.none { it.id == cloudEv.id }
            ) {
                eventsPool += cloudEv
            }
        }

        val results = mutableListOf<UnifiedSearchResult>()
        eventsPool.forEach { evt ->
            if (q.isNotBlank() &&
                !evt.title.contains(q, ignoreCase = true) &&
                !evt.description.contains(q, ignoreCase = true) &&
                !evt.location.contains(q, ignoreCase = true) &&
                !evt.creatorName.contains(q, ignoreCase = true) &&
                !evt.serverName.contains(q, ignoreCase = true)
            ) return@forEach

            results += mapEventToResult(
                evt = evt,
                score = SearchQueryParser.computeRelevanceScore(q, evt.title, evt.location, evt.description, evt.creatorName, evt.startAtMs, context.nowMs),
                nowMs = context.nowMs
            )
        }

        // Also include legacy ServerEvents on accessible servers if not already duplicated
        context.servers
            .filter { SearchPermissionGuard.isServerAccessible(it, uid) }
            .forEach { srv ->
                srv.events.forEach { se ->
                    if (eventsPool.any { it.id == se.id || it.title.equals(se.title, ignoreCase = true) }) return@forEach
                    if (q.isNotBlank() &&
                        !se.title.contains(q, ignoreCase = true) &&
                        !se.description.contains(q, ignoreCase = true) &&
                        !se.locationLabel.contains(q, ignoreCase = true) &&
                        !se.creatorName.contains(q, ignoreCase = true)
                    ) return@forEach
                    results += UnifiedSearchResult(
                        id = "sevt_${se.id}",
                        type = SearchEntityType.EVENT,
                        title = se.title,
                        subtitle = "${srv.name} • ${se.dateTimeLabel}",
                        snippet = "${se.locationLabel} — ${se.description}",
                        badgeLabel = "Serveur",
                        serverId = srv.id,
                        serverName = srv.name,
                        targetId = se.id,
                        authorName = se.creatorName,
                        timestampMs = context.nowMs,
                        timestampLabel = se.dateTimeLabel,
                        relevanceScore = SearchQueryParser.computeRelevanceScore(q, se.title, se.locationLabel, se.description, se.creatorName, context.nowMs, context.nowMs),
                        accentColorHex = srv.iconBgColor,
                        isPublic = srv.isPublic
                    )
                }
            }
        return results
    }

    // =========================================================================
    // Domain 8: STORIES (Only active, non-expired, permission-allowed)
    // =========================================================================
    private fun searchStoriesDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        return context.stories.mapNotNull { story ->
            if (!SearchPermissionGuard.canSeeStory(
                    story = story,
                    uid = context.currentUid,
                    blockedIds = context.blockedUserIds,
                    nowMs = context.nowMs
                )
            ) return@mapNotNull null

            val content = "${story.textContent} ${story.caption} ${story.authorName}"
            if (q.isNotBlank() && !content.contains(q, ignoreCase = true)) return@mapNotNull null

            mapStoryToResult(
                story = story,
                score = SearchQueryParser.computeRelevanceScore(
                    q,
                    story.caption.ifBlank { story.textContent.ifBlank { "Story ${story.mediaType.label}" } },
                    story.visibility.label,
                    story.textContent,
                    story.authorName,
                    story.createdAtMs,
                    context.nowMs
                ),
                nowMs = context.nowMs
            )
        }
    }

    // =========================================================================
    // Domain 9: BOTS (Owned, Server-Installed on Accessible Servers, or Public Catalog)
    // =========================================================================
    private suspend fun searchBotsDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val accessibleServerIds = context.servers
            .filter { SearchPermissionGuard.isServerAccessible(it, uid) }
            .map { it.id }
            .toSet()

        val botsPool = context.bots.toMutableList()
        fetchBoundedCloudCatalogBots(q, context.nowMs).forEach { cloudBot ->
            if (botsPool.none { it.id == cloudBot.id }) botsPool += cloudBot
        }

        return botsPool.mapNotNull { bot ->
            if (!SearchPermissionGuard.canSeeBot(bot, uid, accessibleServerIds, context.botInstallationsByServer)) {
                return@mapNotNull null
            }
            val commandsText = bot.commands.joinToString(" ") { "${it.trigger} ${it.description}" }
            if (q.isNotBlank() &&
                !bot.name.contains(q, ignoreCase = true) &&
                !bot.handle.contains(q, ignoreCase = true) &&
                !bot.description.contains(q, ignoreCase = true) &&
                !commandsText.contains(q, ignoreCase = true)
            ) return@mapNotNull null

            UnifiedSearchResult(
                id = "bot_${bot.id}",
                type = SearchEntityType.BOT,
                title = "${bot.name} (@${bot.handle})",
                subtitle = "${bot.commands.size} commandes • ${if (bot.isPublicCatalog) "Catalogue public" else "Bot privé"}",
                snippet = bot.description,
                badgeLabel = if (bot.ownerUid == uid) "Mon Bot" else "Bot",
                targetId = bot.id,
                authorId = bot.ownerUid,
                authorName = bot.name,
                timestampMs = bot.createdAtMs,
                timestampLabel = "${bot.commands.size} cmd",
                relevanceScore = SearchQueryParser.computeRelevanceScore(q, bot.name, "@${bot.handle}", bot.description, bot.name, bot.createdAtMs, context.nowMs),
                accentColorHex = bot.avatarColor,
                isPublic = bot.isPublicCatalog,
                botPayload = bot
            )
        }
    }

    // =========================================================================
    // Domain 10: MARKETPLACE (Global Orbs Shop + Joined Server Shop Items)
    // =========================================================================
    private fun searchMarketplaceDomain(
        parsed: ParsedSearchInput,
        spec: SearchQuerySpec,
        context: SearchExecutionContext
    ): List<UnifiedSearchResult> {
        val q = parsed.cleanQuery
        val uid = context.currentUid
        val results = mutableListOf<UnifiedSearchResult>()

        context.globalShopItems.forEach { item ->
            if (q.isNotBlank() &&
                !item.title.contains(q, ignoreCase = true) &&
                !item.subtitle.contains(q, ignoreCase = true) &&
                !item.description.contains(q, ignoreCase = true) &&
                !item.category.title.contains(q, ignoreCase = true)
            ) return@forEach

            results += UnifiedSearchResult(
                id = "mkt_global_${item.id}",
                type = SearchEntityType.MARKETPLACE,
                title = item.title,
                subtitle = "${item.category.title} • ${item.priceOrbs} Orbs",
                snippet = item.description,
                badgeLabel = if (item.isPurchased) "Acquis" else "${item.priceOrbs} Orbs",
                targetId = item.id,
                timestampMs = context.nowMs - 86_400_000L,
                timestampLabel = item.rarity.name,
                relevanceScore = SearchQueryParser.computeRelevanceScore(q, item.title, item.category.title, item.description, "", context.nowMs, context.nowMs),
                accentColorHex = 0xFFF0B232L,
                isPublic = true,
                shopItemPayload = item
            )
        }

        context.servers
            .filter { SearchPermissionGuard.canSeeServerShopItem(it, uid) }
            .forEach { srv ->
                srv.shopItems.forEach { sItem ->
                    if (q.isNotBlank() &&
                        !sItem.title.contains(q, ignoreCase = true) &&
                        !sItem.description.contains(q, ignoreCase = true) &&
                        !sItem.roleName.contains(q, ignoreCase = true) &&
                        !srv.name.contains(q, ignoreCase = true)
                    ) return@forEach

                    results += UnifiedSearchResult(
                        id = "mkt_srv_${srv.id}_${sItem.id}",
                        type = SearchEntityType.MARKETPLACE,
                        title = sItem.title,
                        subtitle = "Boutique ${srv.name} • ${sItem.priceOrbs} Orbs",
                        snippet = sItem.description,
                        badgeLabel = "Serveur",
                        serverId = srv.id,
                        serverName = srv.name,
                        targetId = sItem.id,
                        timestampMs = context.nowMs - 43_200_000L,
                        timestampLabel = srv.name,
                        relevanceScore = SearchQueryParser.computeRelevanceScore(q, sItem.title, srv.name, sItem.description, srv.name, context.nowMs, context.nowMs),
                        accentColorHex = sItem.primaryColorHex,
                        isPublic = srv.isPublic,
                        serverShopPayload = srv.id to sItem
                    )
                }
            }
        return results
    }

    // =========================================================================
    // Bounded Firestore Cloud Queries (Cached & Permission-Aligned)
    // =========================================================================
    private suspend fun fetchBoundedCloudPublicProfiles(query: String, currentUid: String, nowMs: Long): List<UserDirectoryEntry> {
        val db = firestoreProvider() ?: return emptyList()
        if (authProvider()?.currentUser == null || query.length < 2) return emptyList()
        val cacheKey = "USERS:${query.lowercase(Locale.ROOT)}"
        cloudCache[cacheKey]?.takeIf { nowMs - it.fetchedAtMs < cacheTtlMs }?.let { cached ->
            return cached.items.mapNotNull { it.userPayload?.let { u -> UserDirectoryEntry(user = u) } }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = Tasks.await(
                    db.collection("public_profiles")
                        .whereEqualTo("isPublic", true)
                        .limit(cloudLimitPerQuery)
                        .get()
                )
                val entries = snap.documents.mapNotNull { doc ->
                    val targetUid = doc.getString("userId") ?: doc.id
                    if (targetUid == currentUid) return@mapNotNull null
                    FriendFirestoreSync.directoryEntryFromPublicDoc(
                        doc = doc,
                        mutualServers = emptyList(),
                        relationStatus = FriendRelationStatus.NONE,
                        pendingRequestId = null,
                        privateNote = ""
                    )
                }
                entries
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun fetchBoundedCloudPublicServers(query: String, nowMs: Long): List<Server> {
        val db = firestoreProvider() ?: return emptyList()
        if (authProvider()?.currentUser == null || query.length < 2) return emptyList()
        val cacheKey = "SERVERS:${query.lowercase(Locale.ROOT)}"
        cloudCache[cacheKey]?.takeIf { nowMs - it.fetchedAtMs < cacheTtlMs }?.let { cached ->
            return cached.items.mapNotNull { res ->
                res.publicListingPayload?.let { l ->
                    Server(
                        id = l.id,
                        name = l.name,
                        iconText = l.iconText,
                        iconBgColor = l.iconBgColor,
                        isPublic = true,
                        categoryTag = l.categoryTag,
                        description = l.description,
                        inviteCode = l.inviteCode,
                        boostLevel = l.boostLevel,
                        isVerified = l.isVerified
                    )
                }
            }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = Tasks.await(
                    db.collection("servers")
                        .whereEqualTo("isPublic", true)
                        .limit(cloudLimitPerQuery)
                        .get()
                )
                snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToServer(it) }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun fetchBoundedCloudPublicEvents(query: String, nowMs: Long): List<RavenEvent> {
        val db = firestoreProvider() ?: return emptyList()
        if (authProvider()?.currentUser == null || query.length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = Tasks.await(
                    db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                        .whereEqualTo("isPublic", true)
                        .limit(cloudLimitPerQuery)
                        .get()
                )
                snap.documents.mapNotNull { EventFirestoreSync.mapDocToEvent(it) }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun fetchBoundedCloudCatalogBots(query: String, nowMs: Long): List<RavenBot> {
        val db = firestoreProvider() ?: return emptyList()
        if (authProvider()?.currentUser == null || query.length < 2) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val snap = Tasks.await(
                    db.collection("bots")
                        .whereEqualTo("isPublicCatalog", true)
                        .limit(cloudLimitPerQuery)
                        .get()
                )
                snap.documents.mapNotNull { BotFirestoreSync.mapDocToBot(it) }
            }.getOrDefault(emptyList())
        }
    }

    // =========================================================================
    // Result Mapping Helpers
    // =========================================================================
    private fun matchesMessageSubFilter(msg: Message, filter: GlobalSearchFilter): Boolean = when (filter) {
        GlobalSearchFilter.ALL -> true
        GlobalSearchFilter.IMAGES -> !msg.imageUrl.isNullOrBlank()
        GlobalSearchFilter.FILES -> !msg.attachmentName.isNullOrBlank() || !msg.audioUrl.isNullOrBlank()
        GlobalSearchFilter.PINNED -> msg.isPinned
    }

    private fun matchesMessageQuery(msg: Message, q: String): Boolean {
        return msg.text.contains(q, ignoreCase = true) ||
            msg.sender.name.contains(q, ignoreCase = true) ||
            (msg.attachmentName?.contains(q, ignoreCase = true) == true)
    }

    private fun messageSnippet(msg: Message): String = when {
        msg.text.isNotBlank() -> msg.text
        !msg.attachmentName.isNullOrBlank() -> "Fichier : ${msg.attachmentName}"
        !msg.imageUrl.isNullOrBlank() -> "Photo partagée"
        !msg.audioUrl.isNullOrBlank() -> "Note vocale (${msg.audioDurationSec}s)"
        else -> "Message"
    }

    private fun mapChannelMessageToResult(
        srv: Server,
        ch: Channel,
        msg: Message,
        score: Int,
        tsMs: Long
    ) = UnifiedSearchResult(
        id = "msg_${ch.id}_${msg.id}",
        type = SearchEntityType.MESSAGE,
        title = msg.sender.name,
        subtitle = "${srv.name} • #${ch.name}",
        snippet = messageSnippet(msg),
        badgeLabel = if (msg.isPinned) "Épinglé" else "#${ch.name}",
        serverId = srv.id,
        serverName = srv.name,
        channelId = ch.id,
        targetId = ch.id,
        authorId = msg.sender.id,
        authorName = msg.sender.name,
        timestampMs = tsMs,
        timestampLabel = msg.timestamp,
        relevanceScore = score,
        accentColorHex = msg.sender.avatarColor,
        isPublic = srv.isPublic && !ch.isPrivate,
        messagePayload = msg
    )

    private fun mapConversationToResult(
        conv: DirectConversation,
        score: Int,
        tsMs: Long
    ): UnifiedSearchResult {
        val title = if (conv.isGroup) conv.groupName ?: "Groupe" else conv.friend.name
        val badge = if (conv.isGroup) "${conv.groupMembers.size} membres" else "DM"
        return UnifiedSearchResult(
            id = "conv_${conv.id}",
            type = SearchEntityType.CONVERSATION,
            title = title,
            subtitle = if (conv.isGroup) "Groupe privé" else "${conv.friend.name}${conv.friend.tag} • ${conv.friend.status.label}",
            snippet = conv.lastMessage.ifBlank { "Conversation privée" },
            badgeLabel = badge,
            conversationId = conv.id,
            targetId = conv.id,
            authorId = if (conv.isGroup) null else conv.friend.id,
            authorName = if (conv.isGroup) title else conv.friend.name,
            timestampMs = tsMs,
            timestampLabel = conv.lastMessageTime,
            relevanceScore = score,
            accentColorHex = if (conv.isGroup) conv.groupIconColor else conv.friend.avatarColor,
            isPublic = false
        )
    }

    private fun mapEventToResult(evt: RavenEvent, score: Int, nowMs: Long): UnifiedSearchResult {
        val dateText = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(evt.startAtMs.coerceAtLeast(nowMs)))
        return UnifiedSearchResult(
            id = "evt_${evt.id}",
            type = SearchEntityType.EVENT,
            title = evt.title,
            subtitle = "${evt.eventType.label} • ${evt.serverName.ifBlank { evt.visibility.label }}",
            snippet = "${evt.location.ifBlank { evt.dateLabel }} — ${evt.description}",
            badgeLabel = evt.status.label,
            serverId = evt.serverId,
            serverName = evt.serverName.ifBlank { null },
            targetId = evt.id,
            authorId = evt.creatorId,
            authorName = evt.creatorName,
            timestampMs = evt.startAtMs,
            timestampLabel = "${evt.dateLabel} ${evt.timeLabel}".trim().ifBlank { dateText },
            relevanceScore = score,
            accentColorHex = evt.bannerColorHex,
            isPublic = evt.isPublic,
            eventPayload = evt
        )
    }

    private fun mapStoryToResult(story: StoryItem, score: Int, nowMs: Long): UnifiedSearchResult {
        val title = story.caption.ifBlank { story.textContent.take(42).ifBlank { "Story ${story.mediaType.label}" } }
        return UnifiedSearchResult(
            id = "sty_${story.id}",
            type = SearchEntityType.STORY,
            title = title,
            subtitle = "Par ${story.authorName} • ${story.visibility.label}",
            snippet = story.textContent.ifBlank { story.caption.ifBlank { "Story média (${story.mediaType.label})" } },
            badgeLabel = story.remainingTimeLabel(nowMs),
            targetId = story.id,
            authorId = story.authorId,
            authorName = story.authorName,
            timestampMs = story.createdAtMs,
            timestampLabel = story.mediaType.label,
            relevanceScore = score,
            accentColorHex = story.backgroundColorHex,
            isPublic = story.visibility == StoryVisibility.PUBLIC,
            storyPayload = story
        )
    }
}
