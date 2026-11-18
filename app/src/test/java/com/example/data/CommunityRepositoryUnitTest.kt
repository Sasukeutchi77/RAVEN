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
class CommunityRepositoryUnitTest {

    private fun createRepos(scope: TestScope): Pair<ChatRepository, CommunityRepository> {
        val chatRepo = ChatRepository(scope)
        val commRepo = CommunityRepository(chatRepo)
        return chatRepo to commRepo
    }

    @Test
    fun `creation normalizes unique handle rejects duplicates and sets creator admin with defaults`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val createdRes = commRepo.createCommunity(
            name = "Raven Builders",
            description = "Espace officiel des bâtisseurs",
            uniqueHandleRaw = "@Raven Builders!",
            imageUrl = "https://cdn.raven.app/avatar.png",
            bannerUrl = "https://cdn.raven.app/banner.png",
            bannerColorHex = 0xFFEB459EL,
            isPublic = true,
            categoryTag = "Développement",
            rulesGuidelines = "Pas de spam.",
            homeWelcomeMessage = "Bienvenue chez Builders"
        )
        assertTrue(createdRes.isSuccess)
        val server = createdRes.getOrThrow()

        assertEquals("raven-builders", server.uniqueHandle)
        assertEquals("https://cdn.raven.app/avatar.png", server.imageUrl)
        assertEquals("https://cdn.raven.app/banner.png", server.bannerUrl)
        assertTrue(server.isPublic)
        assertEquals("Pas de spam.", server.rulesGuidelines)
        assertEquals("Bienvenue chez Builders", server.homeWelcomeMessage)
        assertEquals(listOf("INFORMATIONS", "SALONS TEXTUELS", "SALONS VOCAUX"), server.categories.map { it.name })
        assertTrue(server.channels.any { it.type == ChannelType.TEXT })
        assertTrue(server.channels.any { it.type == ChannelType.VOICE })
        assertTrue(server.channels.any { it.type == ChannelType.ANNOUNCEMENT })
        assertTrue(CommunityPermissions.isOwner(server, chatRepo.currentUser.value.id))
        assertTrue(chatRepo.discoverServers.value.any { it.id == server.id })
        assertTrue(chatRepo.notifications.value.any { it.sourceLabel == "Raven Builders" })

        // Duplicate unique handle rejected
        val dupRes = commRepo.createCommunity(
            name = "Autre Raven Builders",
            description = "Tentative doublon",
            uniqueHandleRaw = "raven-builders"
        )
        assertTrue(dupRes.isFailure)
    }

    @Test
    fun `modification updates metadata media visibility and discover listing`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val srv = commRepo.createCommunity(
            name = "Studio Alpha",
            description = "Ancienne description",
            uniqueHandleRaw = "studio-alpha",
            isPublic = true
        ).getOrThrow()

        val updatedRes = commRepo.updateCommunity(
            serverId = srv.id,
            name = "Studio Omega",
            description = "Nouvelle description avancée",
            uniqueHandleRaw = "studio-omega",
            imageUrl = "https://cdn.raven.app/omega.png",
            bannerUrl = "https://cdn.raven.app/omega-banner.png",
            bannerColorHex = 0xFF23A55AL,
            isPublic = false,
            categoryTag = "Création",
            rulesGuidelines = "Bienveillance obligatoire.",
            homeWelcomeMessage = "Accueil Studio Omega"
        )
        assertTrue(updatedRes.isSuccess)
        val updated = updatedRes.getOrThrow()
        assertEquals("Studio Omega", updated.name)
        assertEquals("studio-omega", updated.uniqueHandle)
        assertFalse(updated.isPublic)
        assertEquals("https://cdn.raven.app/omega-banner.png", updated.bannerUrl)
        // Private community must no longer appear in public discover catalog
        assertFalse(chatRepo.discoverServers.value.any { it.id == srv.id })
        // Old handle should now be free again
        assertTrue(commRepo.isHandleAvailable("studio-alpha"))
    }

    @Test
    fun `invitations support maxUses expiration revocation deletion and joining`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val srv = commRepo.createCommunity("Club Privé", "Sur invitation", "club-prive", isPublic = false).getOrThrow()

        val invRes = commRepo.createInvite(srv.id, maxUses = 5, expiresInHours = 24)
        assertTrue(invRes.isSuccess)
        val invite = invRes.getOrThrow()
        assertEquals(5, invite.maxUses)
        assertTrue(invite.isUsable())

        // Revoke invite
        assertTrue(commRepo.revokeInvite(srv.id, invite.code).isSuccess)
        val joinRevoked = commRepo.joinByInviteCode(invite.code)
        assertTrue(joinRevoked.isFailure)

        // Delete invite
        assertTrue(commRepo.deleteInvite(srv.id, invite.code).isSuccess)
        val reloaded = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse(reloaded.invites.any { it.code == invite.code })

        // Join existing public listing via repository
        val listing = PublicServerListing(
            id = "pub_test_1",
            name = "Public Hub",
            iconText = "PH",
            iconBgColor = 0xFF5865F2,
            categoryTag = "Communauté",
            description = "Communauté ouverte",
            inviteCode = "RAVEN-OPEN-1",
            memberCount = 42
        )
        val joined = commRepo.joinPublicCommunity(listing)
        assertTrue(joined.isSuccess)
        assertTrue(chatRepo.servers.value.any { it.id == "pub_test_1" })
    }

    @Test
    fun `leave community succeeds for member and is blocked for owner`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        // Owner cannot leave own community
        val ownSrv = commRepo.createCommunity("Ma Guilde", "Desc", "ma-guilde").getOrThrow()
        assertTrue(commRepo.leaveCommunity(ownSrv.id).isFailure)

        // Normal member can leave server they did not own
        val joinedOther = commRepo.joinPublicCommunity(
            PublicServerListing("srv_other", "Autre Commu", "AC", 0xFF23A55A, "Gaming", "Test", "CODE-1", 10)
        ).getOrThrow()
        assertTrue(chatRepo.servers.value.any { it.id == joinedOther.id })

        val leftRes = commRepo.leaveCommunity(joinedOther.id)
        assertTrue(leftRes.isSuccess)
        assertFalse(chatRepo.servers.value.any { it.id == joinedOther.id })
    }

    @Test
    fun `roles permissions timeout and kick enforce staff hierarchy`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val me = chatRepo.currentUser.value
        val memberUser = User(id = "u_member", name = "Bob", status = UserStatus.ONLINE, role = UserRole.MEMBER)
        val modUser = User(id = "u_mod", name = "Claire", status = UserStatus.OFFLINE, role = UserRole.MEMBER)

        val srv = commRepo.createCommunity("Hierarchie Hub", "Test rôles", "hierarchie-hub").getOrThrow()
        // Add Bob & Claire to members
        commRepo.replaceServerInFlow(srv.copy(members = srv.members + memberUser + modUser))

        // Online count check (me = ONLINE, Bob = ONLINE, Claire = OFFLINE -> 2)
        val srvWithMembers = chatRepo.servers.value.first { it.id == srv.id }
        assertEquals(2, CommunityPermissions.onlineMembersCount(srvWithMembers))

        // Create custom role with specific permissions (announcement only)
        val customRole = commRepo.saveCustomRole(
            serverId = srv.id,
            roleName = "Rédacteur",
            colorHex = 0xFFF0B232L,
            permissions = setOf(RolePermission.SEND_MESSAGES, RolePermission.POST_ANNOUNCEMENTS)
        ).getOrThrow()

        // Assign Bob the custom role and Claire as MODERATOR
        assertTrue(commRepo.assignMemberRole(srv.id, "u_member", UserRole.MEMBER, customRole.id).isSuccess)
        assertTrue(commRepo.assignMemberRole(srv.id, "u_mod", UserRole.MODERATOR, null).isSuccess)

        val updated = chatRepo.servers.value.first { it.id == srv.id }
        assertTrue(CommunityPermissions.hasPermission(updated, me.id, RolePermission.DELETE_COMMUNITY))
        assertFalse(CommunityPermissions.hasPermission(updated, "u_mod", RolePermission.DELETE_COMMUNITY))
        assertFalse(CommunityPermissions.hasPermission(updated, "u_mod", RolePermission.MANAGE_ROLES))
        assertTrue(CommunityPermissions.hasPermission(updated, "u_mod", RolePermission.MODERATE_MEMBERS))
        assertTrue(CommunityPermissions.hasPermission(updated, "u_member", RolePermission.POST_ANNOUNCEMENTS))
        assertFalse(CommunityPermissions.hasPermission(updated, "u_member", RolePermission.MODERATE_MEMBERS))

        // Timeout Bob removes SEND_MESSAGES and CONNECT_VOICE
        val timedOut = commRepo.timeoutMember(srv.id, "u_member").getOrThrow()
        assertTrue(timedOut)
        val afterTimeout = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse(CommunityPermissions.hasPermission(afterTimeout, "u_member", RolePermission.SEND_MESSAGES))
        assertFalse(CommunityPermissions.hasPermission(afterTimeout, "u_member", RolePermission.CONNECT_VOICE))

        // Kick Bob
        assertTrue(commRepo.kickMember(srv.id, "u_member").isSuccess)
        val afterKick = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse(afterKick.members.any { it.id == "u_member" })

        // Unauthorized member fails to update community or delete community
        val foreignSrv = srv.copy(id = "foreign_srv", ownerId = "someone_else", adminIds = listOf("someone_else"), moderatorIds = emptyList(), members = listOf(me.copy(role = UserRole.MEMBER)))
        chatRepo.serversMutableFlow().value = chatRepo.servers.value + foreignSrv
        val illegalUpdate = commRepo.updateCommunity("foreign_srv", "Hack", "Hack", "hack", null, null, null, true, "All", "Rules", "Welcome")
        assertTrue(illegalUpdate.isFailure)
        assertTrue(illegalUpdate.exceptionOrNull() is SecurityException)
        val illegalDelete = commRepo.deleteCommunity("foreign_srv")
        assertTrue(illegalDelete.isFailure)
        assertTrue(illegalDelete.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `categories channels announcements events search and owner deletion work end to end`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val srv = commRepo.createCommunity("Raven Esports", "Tournois & communauté", "raven-esports").getOrThrow()

        // Create category + channels
        val cat = commRepo.createCategory(srv.id, "TOURNOIS").getOrThrow()
        assertEquals("TOURNOIS", cat.name)
        val textCh = commRepo.createChannel(srv.id, "inscriptions", ChannelType.TEXT, cat.name).getOrThrow()
        val voiceCh = commRepo.createChannel(srv.id, "Arena Vocale", ChannelType.VOICE, cat.name).getOrThrow()
        assertEquals(ChannelType.VOICE, voiceCh.type)

        // Delete category reassigns channels to SALONS TEXTUELS
        assertTrue(commRepo.deleteCategory(srv.id, cat.name).isSuccess)
        val afterCatDel = chatRepo.servers.value.first { it.id == srv.id }
        assertEquals("SALONS TEXTUELS", afterCatDel.channels.first { it.id == textCh.id }.category)

        // Delete channel
        assertTrue(commRepo.deleteChannel(srv.id, textCh.id).isSuccess)
        assertFalse(chatRepo.servers.value.first { it.id == srv.id }.channels.any { it.id == textCh.id })

        // Announcements & Event + RSVP
        val ann = commRepo.publishAnnouncement(srv.id, "Saison 2 lancée", "Inscriptions ouvertes !", isPinned = true).getOrThrow()
        assertEquals(1, chatRepo.servers.value.first { it.id == srv.id }.announcements.size)
        val evt = commRepo.createCommunityEvent(srv.id, "Coupe Raven", "Match 5v5", "Dimanche 20h", "Arena Vocale").getOrThrow()
        assertTrue(commRepo.toggleEventInterest(srv.id, evt.id).isSuccess)
        assertTrue(commRepo.deleteAnnouncement(srv.id, ann.id).isSuccess)

        // Search communities by @handle
        val results = commRepo.searchCommunities("@raven-esports", CommunitySearchFilter.ALL)
        assertTrue(results.any { it.id == srv.id })

        // Owner deletes community cleanly
        val delRes = commRepo.deleteCommunity(srv.id)
        assertTrue(delRes.isSuccess)
        assertFalse(chatRepo.servers.value.any { it.id == srv.id })
        assertTrue(commRepo.isHandleAvailable("raven-esports"))
    }
}
