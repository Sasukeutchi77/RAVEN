package com.example.data

import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GlobalSearchUnitTest {

    private fun newSearchHarness(nowMs: Long = 1_760_000_000_000L): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val chatRepo = ChatRepository(scope = scope)
        val friendRepo = FriendRepository(chatRepo = chatRepo)
        val storyRepo = StoryRepository(chatRepo = chatRepo, nowProvider = { nowMs })
        val eventRepo = EventRepository(chatRepo = chatRepo, nowProvider = { nowMs })
        val botRepo = BotRepository(chatRepo = chatRepo)
        val searchRepo = GlobalSearchRepository(
            chatRepo = chatRepo,
            friendRepo = friendRepo,
            storyRepo = storyRepo,
            eventRepo = eventRepo,
            botRepo = botRepo,
            nowProvider = { nowMs }
        )
        return Harness(scope, chatRepo, friendRepo, storyRepo, eventRepo, botRepo, searchRepo, nowMs)
    }

    private data class Harness(
        val scope: CoroutineScope,
        val chatRepo: ChatRepository,
        val friendRepo: FriendRepository,
        val storyRepo: StoryRepository,
        val eventRepo: EventRepository,
        val botRepo: BotRepository,
        val searchRepo: GlobalSearchRepository,
        val nowMs: Long
    )

    @Test
    fun allTenDomainsAndStrictPermissionIsolation_workDeterministically() = runBlocking {
        val now = 1_760_000_000_000L
        val uid = "uid_me_tester"
        val meUser = User(id = uid, name = "Alex", tag = "#1001", role = UserRole.MEMBER)

        val claire = User(
            id = "u_claire_s",
            name = "Claire",
            tag = "#4411",
            status = UserStatus.ONLINE,
            customStatus = "En raid",
            email = "secret_claire@private.mail",
            phone = "+33600000001",
            lastActiveAtMs = now - 60_000L,
            lastActivityLabel = "Actif maintenant"
        )
        val blockedUser = User(
            id = "u_blocked_s",
            name = "MaloryBlocked",
            tag = "#9999",
            status = UserStatus.ONLINE
        )

        val chPublic = Channel(id = "ch_open_s", serverId = "srv_acc", name = "général-search", type = ChannelType.TEXT)
        val chSecret = Channel(
            id = "ch_secret_s",
            serverId = "srv_acc",
            name = "conseil-secret",
            type = ChannelType.TEXT,
            isPrivate = true,
            allowedRoles = listOf(UserRole.ADMIN)
        )
        val accessibleServer = Server(
            id = "srv_acc",
            name = "Nexus Alpha",
            iconText = "NA",
            ownerId = "other_owner",
            isPublic = false,
            description = "Serveur principal de coordination",
            channels = listOf(chPublic, chSecret),
            members = listOf(meUser),
            shopItems = listOf(
                ServerShopItem(
                    id = "sshop_ok",
                    title = "Cadre Émeraude",
                    description = "Cadre membre",
                    priceOrbs = 150,
                    primaryColorHex = 0xFF23A55AL,
                    secondaryColorHex = 0xFF57F287L,
                    roleName = "Émeraude"
                )
            )
        )
        val unjoinedServer = Server(
            id = "srv_locked",
            name = "Coffre Secret Interdit",
            iconText = "CS",
            ownerId = "stranger_owner",
            isPublic = false,
            description = "Confidentiel",
            channels = listOf(Channel(id = "ch_locked", serverId = "srv_locked", name = "coffre-salon", type = ChannelType.TEXT)),
            members = listOf(User(id = "stranger_owner", name = "Stranger")),
            shopItems = listOf(
                ServerShopItem(
                    id = "sshop_locked",
                    title = "Objet Boutique Interdit",
                    description = "Privé",
                    priceOrbs = 999,
                    primaryColorHex = 0xFFED4245L,
                    secondaryColorHex = 0xFFF0B232L,
                    roleName = "Vault"
                )
            )
        )

        val context = SearchExecutionContext(
            currentUid = uid,
            servers = listOf(
                Server(id = "dm_tab", name = "Messages Privés", isDmTab = true),
                accessibleServer,
                unjoinedServer
            ),
            publicListings = listOf(
                PublicServerListing(
                    id = "comm_pub",
                    name = "Communauté Forge",
                    iconText = "CF",
                    iconBgColor = 0xFF5865F2L,
                    categoryTag = "Tech",
                    description = "Design et Kotlin",
                    inviteCode = "FORGE-01",
                    memberCount = 420
                ),
                PublicServerListing(
                    id = "srv_locked",
                    name = "Communauté Cachée",
                    iconText = "CC",
                    iconBgColor = 0xFF35373CL,
                    categoryTag = "Privé",
                    description = "Cachée si serveur privé",
                    inviteCode = "LOCK-01",
                    memberCount = 5
                )
            ),
            directConversations = listOf(
                DirectConversation(
                    id = "dm_ok",
                    friend = claire,
                    participantIds = listOf(uid, claire.id),
                    lastMessage = "À tout à l'heure",
                    lastMessageTime = "10:20"
                ),
                DirectConversation(
                    id = "dm_blocked",
                    friend = blockedUser,
                    participantIds = listOf(uid, blockedUser.id),
                    lastMessage = "Spam bloqué",
                    lastMessageTime = "10:21"
                )
            ),
            messagesByTarget = mapOf(
                "ch_open_s" to listOf(
                    Message(id = "msg_ok", channelId = "ch_open_s", sender = claire, text = "Rendez-vous déploiement Kotlin ce soir", timestamp = "10:15", isPinned = true)
                ),
                "ch_secret_s" to listOf(
                    Message(id = "msg_sec", channelId = "ch_secret_s", sender = User(id = "other_owner", name = "Admin"), text = "Mot de passe ultra secret admin", timestamp = "10:16")
                ),
                "ch_locked" to listOf(
                    Message(id = "msg_locked", channelId = "ch_locked", sender = User(id = "stranger_owner", name = "Stranger"), text = "Fuite serveur non rejoint", timestamp = "10:17")
                ),
                "dm_blocked" to listOf(
                    Message(id = "msg_blk", channelId = "dm_blocked", sender = blockedUser, text = "Message bloqué invisible", timestamp = "10:18")
                )
            ),
            directoryUsers = mapOf(
                claire.id to UserDirectoryEntry(user = claire, showOnlineStatus = false, showLastActivity = false),
                blockedUser.id to UserDirectoryEntry(user = blockedUser)
            ),
            friends = emptyList(),
            blockedUserIds = setOf(blockedUser.id),
            stories = listOf(
                StoryItem(
                    id = "st_ok",
                    authorId = claire.id,
                    authorName = "Claire",
                    mediaType = StoryMediaType.TEXT,
                    textContent = "Nouvelle release RAVEN prête !",
                    visibility = StoryVisibility.PUBLIC,
                    createdAtMs = now - 600_000L,
                    expiresAtMs = now + 7_200_000L
                ),
                StoryItem(
                    id = "st_expired",
                    authorId = claire.id,
                    authorName = "Claire",
                    mediaType = StoryMediaType.TEXT,
                    textContent = "Story Expirée Invisible",
                    visibility = StoryVisibility.PUBLIC,
                    createdAtMs = now - 90_000_000L,
                    expiresAtMs = now - 10_000L
                ),
                StoryItem(
                    id = "st_priv",
                    authorId = "stranger_owner",
                    authorName = "Stranger",
                    mediaType = StoryMediaType.TEXT,
                    textContent = "Story Privée Custom Invisible",
                    visibility = StoryVisibility.CUSTOM_USERS,
                    allowedViewerIds = listOf("someone_else"),
                    createdAtMs = now - 600_000L,
                    expiresAtMs = now + 7_200_000L
                ),
                StoryItem(
                    id = "st_mod",
                    authorId = claire.id,
                    authorName = "Claire",
                    mediaType = StoryMediaType.TEXT,
                    textContent = "Story Modérée Masquée",
                    visibility = StoryVisibility.PUBLIC,
                    moderatedHidden = true,
                    createdAtMs = now - 600_000L,
                    expiresAtMs = now + 7_200_000L
                )
            ),
            isStoryModerator = false,
            events = listOf(
                RavenEvent(
                    id = "ev_ok",
                    title = "Atelier Compose Live",
                    description = "Architecture modulaire",
                    creatorId = uid,
                    creatorName = "Alex",
                    visibility = EventVisibility.PUBLIC,
                    startAtMs = now + 3_600_000L
                ),
                RavenEvent(
                    id = "ev_secret",
                    title = "Réunion VIP Interdite",
                    creatorId = "stranger_owner",
                    creatorName = "Stranger",
                    visibility = EventVisibility.PRIVATE_INVITE,
                    invitedUserIds = listOf("other_vip"),
                    startAtMs = now + 7_200_000L
                )
            ),
            bots = listOf(
                RavenBot(
                    id = "bot_pub",
                    ownerUid = "dev_other",
                    name = "SentinelBot",
                    handle = "sentinel",
                    description = "Assistant public",
                    isPublicCatalog = true,
                    isActive = true
                ),
                RavenBot(
                    id = "bot_priv",
                    ownerUid = "dev_other",
                    name = "StealthBot",
                    handle = "stealth",
                    description = "Privé non installé",
                    isPublicCatalog = false,
                    isActive = true
                )
            ),
            botInstallationsByServer = emptyMap(),
            globalShopItems = ShopCatalog.initialShopItems.take(3),
            nowMs = now
        )

        val engine = LocalAndFirestoreSearchEngine()
        val page = engine.executeSearch(SearchQuerySpec(rawQuery = "", pageSize = 50, pageIndex = 0), context)
        val allItems = page.items
        assertFalse("Should not be truncated by pagination", page.hasMore)
        val typesPresent = allItems.map { it.type }.toSet()

        assertTrue("All 10 domains should be represented: $typesPresent", typesPresent.containsAll(SearchEntityType.domainTypes))

        // PII stripping & privacy masking on Claire
        val claireResult = allItems.first { it.type == SearchEntityType.USER && it.title.contains("Claire") }
        assertNull(claireResult.userPayload?.email)
        assertNull(claireResult.userPayload?.phone)
        assertEquals(UserStatus.OFFLINE, claireResult.userPayload?.status)
        assertEquals(0L, claireResult.userPayload?.lastActiveAtMs)

        // Zero private/blocked/expired/moderated/unowned leakage (check both id and targetId)
        val allKeys = (allItems.map { it.id } + allItems.mapNotNull { it.targetId }).toSet()
        listOf(
            "u_blocked_s", "srv_locked", "ch_secret_s", "ch_locked",
            "msg_sec", "msg_locked", "msg_blk", "dm_blocked",
            "ev_secret", "st_expired", "st_priv", "st_mod",
            "bot_priv", "sshop_locked"
        ).forEach { forbidden ->
            assertFalse("Forbidden entity $forbidden leaked!", allKeys.any { it.contains(forbidden) })
        }
        assertTrue(allKeys.contains("sshop_ok"))
    }

    @Test
    fun operatorsFiltersSortingPaginationHistoryAndCustomEngine_workCorrectly() = runBlocking {
        val h = newSearchHarness()
        try {
            val now = h.nowMs

            // Seed 22 public events via custom engine context wrapped in a delegating engine
            val seededEvents = (1..22).map { idx ->
                val isAuthorClaire = idx % 2 == 0
                RavenEvent(
                    id = "ev_page_$idx",
                    title = if (idx == 5) "Alpha Meetup" else "Zeta Session %02d".format(idx),
                    description = "Session communauté #$idx",
                    creatorId = if (isAuthorClaire) "u_claire" else h.chatRepo.currentUser.value.id,
                    creatorName = if (isAuthorClaire) "Claire" else "RavenAdmin",
                    visibility = EventVisibility.PUBLIC,
                    startAtMs = now - (idx * 86_400_000L)
                )
            }
            val baseEngine = LocalAndFirestoreSearchEngine()
            val cleanContext: (SearchExecutionContext) -> SearchExecutionContext = { ctx ->
                ctx.copy(
                    servers = ctx.servers.map { it.copy(events = emptyList()) },
                    events = seededEvents
                )
            }
            val seededEngine = object : RavenSearchEngine {
                override val engineId: String = "seeded_test_engine"
                override suspend fun executeSearch(spec: SearchQuerySpec, context: SearchExecutionContext): SearchPageResult {
                    return baseEngine.executeSearch(spec, cleanContext(context))
                }
                override fun buildSuggestions(rawQuery: String, history: List<SearchHistoryEntry>, context: SearchExecutionContext): List<SearchSuggestion> =
                    baseEngine.buildSuggestions(rawQuery, history, cleanContext(context))
                override fun collectRecentAccessibleItems(context: SearchExecutionContext, limit: Int): List<UnifiedSearchResult> =
                    baseEngine.collectRecentAccessibleItems(cleanContext(context), limit)
            }
            h.searchRepo.registerSearchEngine(seededEngine)

            // 1. Pagination: pageSize = 15 -> page 0 has 15 items, hasMore = true, loadNextPage loads remaining 7
            h.searchRepo.selectSingleType(SearchEntityType.EVENT)
            val page0 = h.searchRepo.pageResult.value
            assertEquals(15, page0.items.size)
            assertEquals(22, page0.totalMatchingCount)
            assertTrue(page0.hasMore)

            h.searchRepo.loadNextPage()
            val page1 = h.searchRepo.pageResult.value
            assertEquals(22, page1.items.size)
            assertFalse(page1.hasMore)

            // 2. Sorting: ALPHABETICAL, NEWEST, OLDEST
            h.searchRepo.setSortOrder(SearchSortOrder.ALPHABETICAL)
            assertEquals("Alpha Meetup", h.searchRepo.pageResult.value.items.first().title)

            h.searchRepo.setSortOrder(SearchSortOrder.NEWEST)
            assertEquals("ev_page_1", h.searchRepo.pageResult.value.items.first().targetId)

            h.searchRepo.setSortOrder(SearchSortOrder.OLDEST)
            assertEquals("ev_page_22", h.searchRepo.pageResult.value.items.first().targetId)

            // 3. Date Range filter: LAST_7_DAYS -> only events within 7 days (ev_page_1..ev_page_7)
            h.searchRepo.setSortOrder(SearchSortOrder.RELEVANCE)
            h.searchRepo.setDateRange(SearchDateRange.LAST_7_DAYS)
            assertEquals(7, h.searchRepo.pageResult.value.totalMatchingCount)

            // 4. Inline Operators: type:event de:Claire depuis:7j
            h.searchRepo.clearAllFilters()
            h.searchRepo.updateQuery("type:event de:Claire depuis:7j")
            val opItems = h.searchRepo.pageResult.value.items
            assertTrue(opItems.isNotEmpty())
            assertTrue(opItems.all { it.type == SearchEntityType.EVENT && it.authorName == "Claire" })
            assertEquals(3, opItems.size) // ev_page_2, ev_page_4, ev_page_6

            // 5. History persistence, deduplication, delete, and clear
            h.searchRepo.commitQueryToHistory("Alpha Meetup")
            h.searchRepo.commitQueryToHistory("Alpha Meetup")
            assertEquals(1, h.searchRepo.history.value.size)
            val entryId = h.searchRepo.history.value.first().id
            h.searchRepo.deleteHistoryEntry(entryId)
            assertTrue(h.searchRepo.history.value.isEmpty())

            h.searchRepo.commitQueryToHistory("Requête 1")
            h.searchRepo.commitQueryToHistory("Requête 2")
            assertEquals(2, h.searchRepo.history.value.size)
            h.searchRepo.clearHistory()
            assertTrue(h.searchRepo.history.value.isEmpty())

            // 6. Swappable custom RavenSearchEngine SPI
            val customEngine = object : RavenSearchEngine {
                override val engineId: String = "algolia_mock_spi"
                override suspend fun executeSearch(spec: SearchQuerySpec, context: SearchExecutionContext): SearchPageResult {
                    return SearchPageResult(
                        items = listOf(
                            UnifiedSearchResult(
                                id = "spi_hit_1",
                                type = SearchEntityType.BOT,
                                title = "ExternalVectorHit",
                                subtitle = "Algolia SPI",
                                snippet = "Indexed externally",
                                badgeLabel = "BOT"
                            )
                        ),
                        totalMatchingCount = 1,
                        hasMore = false,
                        engineId = engineId
                    )
                }

                override fun buildSuggestions(rawQuery: String, history: List<SearchHistoryEntry>, context: SearchExecutionContext): List<SearchSuggestion> = emptyList()
                override fun collectRecentAccessibleItems(context: SearchExecutionContext, limit: Int): List<UnifiedSearchResult> = emptyList()
            }
            h.searchRepo.registerSearchEngine(customEngine)
            h.searchRepo.updateQuery("vector")
            assertEquals("algolia_mock_spi", h.searchRepo.pageResult.value.engineId)
            assertEquals("spi_hit_1", h.searchRepo.pageResult.value.items.first().id)
        } finally {
            h.scope.cancel()
        }
    }
}
