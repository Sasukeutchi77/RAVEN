package com.example.data

import com.example.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ServerCustomizationUnitTest {

    private fun createRepos(scope: TestScope): Triple<ChatRepository, CommunityRepository, ServerCustomizationRepository> {
        val chatRepo = ChatRepository(scope)
        val commRepo = CommunityRepository(chatRepo)
        val custRepo = ServerCustomizationRepository(commRepo)
        return Triple(chatRepo, commRepo, custRepo)
    }

    @Test
    fun `identity theme colors icon preset banner and description customize live`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Raven Lab", "Original desc", "raven-lab", isPublic = true).getOrThrow()

        val updated = custRepo.updateIdentityAndTheme(
            serverId = srv.id,
            name = "Raven Horizon",
            uniqueHandleRaw = "raven-horizon",
            description = "Serveur ultra personnalisé",
            iconText = "RH",
            iconPresetKey = "rocket",
            imageUrl = "https://cdn.raven.app/rh-icon.png",
            bannerUrl = "https://cdn.raven.app/rh-banner.png",
            accentColorHex = 0xFFEB459EL,
            secondaryColorHex = 0xFF10B981L,
            surfaceStyle = ServerThemeSurfaceStyle.ROYAL_PURPLE,
            categoryTag = "Tech"
        ).getOrThrow()

        assertEquals("Raven Horizon", updated.name)
        assertEquals("raven-horizon", updated.uniqueHandle)
        assertEquals("Serveur ultra personnalisé", updated.description)
        assertEquals("RH", updated.iconText)
        assertEquals("rocket", updated.customization.theme.iconPresetKey)
        assertEquals("https://cdn.raven.app/rh-icon.png", updated.imageUrl)
        assertEquals("https://cdn.raven.app/rh-banner.png", updated.bannerUrl)
        assertEquals(0xFFEB459EL, updated.customization.theme.accentColorHex)
        assertEquals(0xFF10B981L, updated.customization.theme.secondaryColorHex)
        assertEquals(ServerThemeSurfaceStyle.ROYAL_PURPLE, updated.customization.theme.surfaceStyle)
        assertEquals("Tech", updated.categoryTag)
        assertTrue(chatRepo.servers.value.any { it.id == srv.id && it.name == "Raven Horizon" })
    }

    @Test
    fun `categories channels ordering custom icons and category privacy work cleanly`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Order Arena", "Desc", "order-arena").getOrThrow()
        val me = chatRepo.currentUser.value
        val regularMember = User(id = "mem_bob", name = "Bob", role = UserRole.MEMBER)
        commRepo.replaceServerInFlow(srv.copy(members = srv.members + regularMember))

        // Create private category restricted to custom role or staff
        val vipRole = commRepo.saveCustomRole(srv.id, "VIP", 0xFFF59E0BL, permissions = setOf(RolePermission.SEND_MESSAGES)).getOrThrow()
        val privCat = custRepo.upsertCategory(srv.id, null, "ESPACE VIP", isPrivate = true, allowedRoleIds = listOf(vipRole.id)).getOrThrow()
        assertEquals("ESPACE VIP", privCat.name)

        // Move category up
        val beforeCats = chatRepo.servers.value.first { it.id == srv.id }.categories.sortedBy { it.position }
        val lastCat = beforeCats.last()
        assertTrue(custRepo.moveCategoryOrder(srv.id, lastCat.id, -1).isSuccess)
        val afterCats = chatRepo.servers.value.first { it.id == srv.id }.categories.sortedBy { it.position }
        assertNotEquals(lastCat.id, afterCats.last().id)

        // Bob (without vipRole) cannot access VIP category; owner can
        val latestSrv = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse(CommunityPermissions.isCategoryAccessible(latestSrv, "ESPACE VIP", "mem_bob"))
        assertTrue(CommunityPermissions.isCategoryAccessible(latestSrv, "ESPACE VIP", me.id))

        // Create two channels in same category with custom icons & reorder them
        val ch1 = custRepo.upsertChannelCustomization(
            srv.id, null, "dev-kotlin", "Espace Kotlin", ChannelType.TEXT, "SALONS TEXTUELS",
            iconKey = "code", slowmodeSeconds = 5, isReadOnly = false, isPrivate = false
        ).getOrThrow()
        val ch2 = custRepo.upsertChannelCustomization(
            srv.id, null, "tournoi-fps", "Matchs", ChannelType.TEXT, "SALONS TEXTUELS",
            iconKey = "game", slowmodeSeconds = 0, isReadOnly = false, isPrivate = false
        ).getOrThrow()

        assertEquals("code", ch1.iconKey)
        assertEquals("game", ch2.iconKey)

        // Move ch2 up by -1 step inside SALONS TEXTUELS
        assertTrue(custRepo.moveChannelOrder(srv.id, ch2.id, -1).isSuccess)
        val reordered = chatRepo.servers.value.first { it.id == srv.id }.channels
            .filter { it.category == "SALONS TEXTUELS" }
            .sortedBy { it.position }
        val idx1 = reordered.indexOfFirst { it.id == ch1.id }
        val idx2 = reordered.indexOfFirst { it.id == ch2.id }
        assertTrue(idx2 < idx1)
    }

    @Test
    fun `roles editing hierarchy reordering and custom role deletion succeed`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Role Guild", "Desc", "role-guild").getOrThrow()
        val helperRole = commRepo.saveCustomRole(
            srv.id, "Guide", 0xFF00A8FCL,
            permissions = setOf(RolePermission.SEND_MESSAGES, RolePermission.POST_ANNOUNCEMENTS)
        ).getOrThrow()
        val vipRole = commRepo.saveCustomRole(
            srv.id, "Diamant", 0xFFA855F7L,
            permissions = setOf(RolePermission.SEND_MESSAGES, RolePermission.MANAGE_MODERATION)
        ).getOrThrow()

        // Edit existing role
        val edited = commRepo.saveCustomRole(
            srv.id, "Super Guide", 0xFF10B981L,
            permissions = setOf(RolePermission.SEND_MESSAGES, RolePermission.MANAGE_EVENTS),
            existingRoleId = helperRole.id
        ).getOrThrow()
        assertEquals("Super Guide", edited.name)
        assertTrue(RolePermission.MANAGE_EVENTS in edited.permissions)

        // Reorder VIP up
        val beforePos = chatRepo.servers.value.first { it.id == srv.id }.customRoles.indexOfFirst { it.id == vipRole.id }
        assertTrue(custRepo.moveRoleOrder(srv.id, vipRole.id, -1).isSuccess)
        val afterPos = chatRepo.servers.value.first { it.id == srv.id }.customRoles.indexOfFirst { it.id == vipRole.id }
        assertTrue(afterPos < beforePos)

        // Delete custom role succeeds, system role blocked
        assertTrue(custRepo.deleteCustomRole(srv.id, "role_admin").isFailure)
        assertTrue(custRepo.deleteCustomRole(srv.id, vipRole.id).isSuccess)
        assertFalse(chatRepo.servers.value.first { it.id == srv.id }.customRoles.any { it.id == vipRole.id })
    }

    @Test
    fun `welcome goodbye notifications dispatch formatted messages and honor notification suppression`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Accueil Club", "Desc", "accueil-club", isPublic = true).getOrThrow()
        val textCh = srv.channels.first { it.type == ChannelType.TEXT && !it.isReadOnly }

        // Configure welcome/goodbye templates + DM welcome
        custRepo.updateWelcomeAndGoodbyeConfig(
            serverId = srv.id,
            welcomeConfig = ServerWelcomeConfig(
                welcomeEnabled = true,
                welcomeMessageTemplate = "Salutations {user}, bienvenu(e) dans {server} !",
                welcomeChannelId = textCh.id,
                goodbyeEnabled = true,
                goodbyeMessageTemplate = "Au revoir {user} de {server}.",
                goodbyeChannelId = textCh.id,
                dmWelcomeEnabled = true
            ),
            homeWelcomeMessage = "Accueil personnalisé V2"
        ).getOrThrow()

        val configuredSrv = chatRepo.servers.value.first { it.id == srv.id }
        custRepo.dispatchMembershipSystemMessage(configuredSrv, "Alice", isJoin = true)
        custRepo.dispatchMembershipSystemMessage(configuredSrv, "Bob", isJoin = false)

        val msgs = chatRepo.messages.value[textCh.id].orEmpty()
        assertTrue(msgs.any { it.text == "Salutations Alice, bienvenu(e) dans Accueil Club !" })
        assertTrue(msgs.any { it.text == "Au revoir Bob de Accueil Club." })
        assertTrue(chatRepo.notifications.value.any { it.title.contains("Bienvenue sur Accueil Club") })

        // Notification config suppresses announcement notification when ALL/NONE/suppress
        custRepo.updateNotificationConfig(
            srv.id,
            ServerNotificationConfig(
                defaultLevel = ServerNotificationLevel.NONE,
                suppressEveryoneMentions = true,
                suppressRoleMentions = true,
                highlightAnnouncements = false
            )
        ).getOrThrow()
        val notifBefore = chatRepo.notifications.value.size
        commRepo.publishAnnouncement(srv.id, "Annonce silencieuse", "Info @everyone", isPinned = true).getOrThrow()
        assertEquals(notifBefore, chatRepo.notifications.value.size)
    }

    @Test
    fun `verification level automod blockedWords antiRaid and privacy enforce restrictions on members while exempting staff`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Securite Hub", "Desc", "securite-hub", isPublic = true).getOrThrow()
        val targetCh = srv.channels.first { it.type == ChannelType.TEXT && !it.isReadOnly }

        // Update rules & HIGH verification level
        custRepo.updateRulesAndVerification(
            serverId = srv.id,
            rulesList = listOf("Respect obligatoire", "Pas de liens suspects"),
            rulesGuidelines = "Respect obligatoire\nPas de liens suspects",
            requireRulesAcceptance = true,
            verificationLevel = ServerVerificationLevel.HIGH
        ).getOrThrow()

        // Configure Auto-Mod with blocked word, link blocking, and max 2 mentions
        custRepo.updateModerationConfig(
            srv.id,
            ServerModerationConfig(
                autoModEnabled = true,
                blockSpamAndLinks = true,
                blockBadWords = true,
                blockedWords = listOf("arghbad"),
                defaultSlowmodeSeconds = 0,
                maxMentionsPerMessage = 2,
                antiRaidModeEnabled = false
            )
        ).getOrThrow()

        // Switch server ownership to another user so currentUser acts as a regular level-1 member with no badges
        val memberMe = chatRepo.currentUser.value.copy(level = 1, badges = emptyList())
        chatRepo.currentUserMutableFlow().value = memberMe
        val nonOwnerSrv = chatRepo.servers.value.first { it.id == srv.id }.copy(
            ownerId = "other_owner",
            adminIds = listOf("other_owner"),
            moderatorIds = emptyList(),
            members = listOf(User(id = "other_owner", name = "Boss"), memberMe.copy(role = UserRole.MEMBER))
        )
        commRepo.replaceServerInFlow(nonOwnerSrv)

        // HIGH verification blocks low-level unbadged member
        chatRepo.sendMessage(targetCh.id, "Bonjour tout le monde")
        assertTrue(chatRepo.lastSendBlockedReason.value?.contains("Vérification Élevée") == true)

        // Upgrade member level to 5 -> bypasses HIGH verification, now Auto-Mod filters run
        val verifiedMe = memberMe.copy(level = 5, role = UserRole.MEMBER)
        chatRepo.currentUserMutableFlow().value = verifiedMe
        commRepo.replaceServerInFlow(nonOwnerSrv.copy(members = listOf(User(id = "other_owner", name = "Boss"), verifiedMe)))

        // Blocked word rejected
        chatRepo.sendMessage(targetCh.id, "Ce mot arghbad est interdit")
        assertTrue(chatRepo.lastSendBlockedReason.value?.contains("terme interdit") == true)

        // External link rejected
        chatRepo.sendMessage(targetCh.id, "Cliquez sur https://evil.example.com")
        assertTrue(chatRepo.lastSendBlockedReason.value?.contains("liens externes") == true)

        // Excessive mentions rejected (>2)
        chatRepo.sendMessage(targetCh.id, "Hey @a @b @c !")
        assertTrue(chatRepo.lastSendBlockedReason.value?.contains("trop de mentions") == true)

        // Clean message succeeds
        val msgCountBefore = chatRepo.messages.value[targetCh.id]?.size ?: 0
        chatRepo.sendMessage(targetCh.id, "Message conforme pour le serveur !")
        assertNull(chatRepo.lastSendBlockedReason.value)
        assertEquals(msgCountBefore + 1, chatRepo.messages.value[targetCh.id]?.size)

        // Anti-Raid enabled by owner blocks regular member
        commRepo.replaceServerInFlow(
            chatRepo.servers.value.first { it.id == srv.id }.copy(
                customization = chatRepo.servers.value.first { it.id == srv.id }.customization.copy(
                    moderation = nonOwnerSrv.customization.moderation.copy(antiRaidModeEnabled = true)
                )
            )
        )
        chatRepo.sendMessage(targetCh.id, "Tentative pendant raid")
        assertTrue(chatRepo.lastSendBlockedReason.value?.contains("Anti-Raid") == true)

        // Restore currentUser as Owner -> Staff is exempt from Anti-Raid and Auto-Mod
        val ownerSrv = chatRepo.servers.value.first { it.id == srv.id }.copy(ownerId = verifiedMe.id, adminIds = listOf(verifiedMe.id))
        commRepo.replaceServerInFlow(ownerSrv)
        chatRepo.sendMessage(targetCh.id, "Annonce Staff https://raven.app pendant anti-raid")
        assertNull(chatRepo.lastSendBlockedReason.value)
    }

    @Test
    fun `privacy config controls catalog discovery and invites, and blocks non-admin modifications`() = runTest {
        val scope = TestScope(testScheduler)
        val (chatRepo, commRepo, custRepo) = createRepos(scope)

        val srv = commRepo.createCommunity("Private Vault", "Desc", "private-vault", isPublic = true).getOrThrow()
        assertTrue(chatRepo.discoverServers.value.any { it.id == srv.id })

        // Disable catalog discovery and invite links
        custRepo.updatePrivacyConfig(
            serverId = srv.id,
            isPublic = true,
            config = ServerPrivacyConfig(
                discoverableInCatalog = false,
                allowInviteLinks = false,
                requireInviteApproval = true,
                allowMemberDms = false,
                hideMemberListForNonStaff = true
            )
        ).getOrThrow()

        assertFalse(chatRepo.discoverServers.value.any { it.id == srv.id })
        val searchRes = commRepo.searchCommunities("Private Vault", CommunitySearchFilter.PUBLIC_ONLY)
        assertFalse(searchRes.any { it.id == srv.id })

        // Creating an invite is rejected when allowInviteLinks == false
        val blockedInv = commRepo.createInvite(srv.id, maxUses = 5, expiresInHours = 24)
        assertTrue(blockedInv.isFailure)
        assertTrue(blockedInv.exceptionOrNull() is SecurityException)

        // Non-admin (Moderator / Member) fails to modify sensitive privacy, moderation, rules, or identity
        val me = chatRepo.currentUser.value
        val modSrv = chatRepo.servers.value.first { it.id == srv.id }.copy(
            ownerId = "real_owner",
            adminIds = listOf("real_owner"),
            moderatorIds = listOf(me.id),
            members = listOf(User("real_owner", "Owner"), me.copy(role = UserRole.MODERATOR))
        )
        commRepo.replaceServerInFlow(modSrv)

        val modPrivacyAttempt = custRepo.updatePrivacyConfig(srv.id, true, ServerPrivacyConfig(allowInviteLinks = true))
        assertTrue(modPrivacyAttempt.isFailure)
        assertTrue(modPrivacyAttempt.exceptionOrNull() is SecurityException)

        val modModerationAttempt = custRepo.updateModerationConfig(srv.id, ServerModerationConfig(autoModEnabled = false))
        assertTrue(modModerationAttempt.isFailure)
        assertTrue(modModerationAttempt.exceptionOrNull() is SecurityException)

        val modThemeAttempt = custRepo.updateIdentityAndTheme(
            srv.id, "Hack", "hack", "desc", "HK", null, null, null,
            0xFF000000L, 0xFF000000L, ServerThemeSurfaceStyle.AMOLED_DARK, "Hack"
        )
        assertTrue(modThemeAttempt.isFailure)
        assertTrue(modThemeAttempt.exceptionOrNull() is SecurityException)
    }
}
