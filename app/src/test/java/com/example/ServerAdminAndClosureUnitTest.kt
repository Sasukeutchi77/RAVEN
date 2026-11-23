package com.example

import com.example.data.ChatRepository
import com.example.data.CommunityPermissions
import com.example.data.CommunityRepository
import com.example.data.saveCustomRole
import com.example.data.assignMemberRole
import com.example.data.setServerClosed
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
class ServerAdminAndClosureUnitTest {

    private fun createRepos(scope: TestScope): Pair<ChatRepository, CommunityRepository> {
        val chatRepo = ChatRepository(scope)
        val commRepo = CommunityRepository(chatRepo)
        return chatRepo to commRepo
    }

    @Test
    fun `only creator can grant and revoke ADMIN role while non-creator admin is blocked`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val me = chatRepo.currentUser.value
        val otherCreator = User(id = "u_creator", name = "Fondateur", status = UserStatus.ONLINE, role = UserRole.ADMIN)
        val targetMember = User(id = "u_bob", name = "Bob", status = UserStatus.ONLINE, role = UserRole.MEMBER)
        val existingAdmin = User(id = "u_dana", name = "Dana", status = UserStatus.ONLINE, role = UserRole.ADMIN)

        val srv = commRepo.createCommunity("Guilde Raven", "Test hiérarchie", "guilde-raven").getOrThrow()

        // Case A: I am the creator (ownerId == me.id) -> I can promote Bob to ADMIN and revoke it
        commRepo.replaceServerInFlow(srv.copy(members = srv.members + targetMember + existingAdmin))

        val promoteRes = commRepo.assignMemberRole(srv.id, "u_bob", UserRole.ADMIN, null)
        assertTrue(promoteRes.isSuccess)
        val afterPromote = chatRepo.servers.value.first { it.id == srv.id }
        assertTrue("u_bob" in afterPromote.adminIds)
        assertEquals(UserRole.ADMIN, CommunityPermissions.effectiveRole(afterPromote, "u_bob"))

        val revokeRes = commRepo.assignMemberRole(srv.id, "u_bob", UserRole.MEMBER, null)
        assertTrue(revokeRes.isSuccess)
        val afterRevoke = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse("u_bob" in afterRevoke.adminIds)
        assertEquals(UserRole.MEMBER, CommunityPermissions.effectiveRole(afterRevoke, "u_bob"))

        // Case B: Someone else is the creator and I am only an ADMIN -> I CANNOT grant or revoke ADMIN
        val serverOwnedByOther = afterRevoke.copy(
            ownerId = otherCreator.id,
            adminIds = listOf(otherCreator.id, me.id, existingAdmin.id),
            members = listOf(otherCreator, me.copy(role = UserRole.ADMIN), targetMember, existingAdmin)
        )
        commRepo.replaceServerInFlow(serverOwnedByOther)

        // Cannot promote Bob to ADMIN
        val nonOwnerPromote = commRepo.assignMemberRole(srv.id, "u_bob", UserRole.ADMIN, null)
        assertTrue(nonOwnerPromote.isFailure)

        // Cannot revoke Dana's ADMIN status
        val nonOwnerRevokeAdmin = commRepo.assignMemberRole(srv.id, "u_dana", UserRole.MEMBER, null)
        assertTrue(nonOwnerRevokeAdmin.isFailure)

        // Cannot demote creator
        val demoteOwner = commRepo.assignMemberRole(srv.id, otherCreator.id, UserRole.MEMBER, null)
        assertTrue(demoteOwner.isFailure)

        // Cannot create an admin-equivalent custom role as non-owner admin
        val nonOwnerAdminCustomRole = commRepo.saveCustomRole(
            serverId = srv.id,
            roleName = "Faux Admin",
            colorHex = 0xFFED4245L,
            permissions = setOf(RolePermission.MANAGE_ROLES)
        )
        assertTrue(nonOwnerAdminCustomRole.isFailure)

        // ChatRepository direct calls also block non-owner admin creation & assignment
        val beforeRolesCount = chatRepo.servers.value.first { it.id == srv.id }.customRoles.size
        chatRepo.createCustomRole(srv.id, "Bypass", 0xFFED4245L, isAdmin = true, canKickBan = true, canManageChannels = true)
        assertEquals(beforeRolesCount, chatRepo.servers.value.first { it.id == srv.id }.customRoles.size)

        chatRepo.assignMemberRole(srv.id, "u_bob", UserRole.ADMIN, null)
        assertFalse("u_bob" in chatRepo.servers.value.first { it.id == srv.id }.adminIds)
    }

    @Test
    fun `admins and creator can close and reopen server and closed state gates non-admin messaging`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo) = createRepos(testScope)

        val me = chatRepo.currentUser.value
        val otherCreator = User(id = "u_creator", name = "Fondateur", status = UserStatus.ONLINE, role = UserRole.ADMIN)
        val srv = commRepo.createCommunity("Groupe WhatsApp Style", "Test ouverture/fermeture", "groupe-wa").getOrThrow()
        val textChannel = srv.channels.first { it.type == ChannelType.TEXT }

        // 1. Non-creator ADMIN can close and reopen the server
        commRepo.replaceServerInFlow(
            srv.copy(
                ownerId = otherCreator.id,
                adminIds = listOf(otherCreator.id, me.id),
                members = listOf(otherCreator, me.copy(role = UserRole.ADMIN))
            )
        )
        val closeRes = commRepo.setServerClosed(srv.id, true)
        assertTrue(closeRes.isSuccess)
        assertTrue(chatRepo.servers.value.first { it.id == srv.id }.isClosed)

        // Admin can still send messages, threads, and slash commands while closed
        val adminMsgsBefore = chatRepo.messages.value[textChannel.id].orEmpty().size
        chatRepo.sendMessage(textChannel.id, "Message admin pendant fermeture")
        val adminMsgsAfter = chatRepo.messages.value[textChannel.id].orEmpty()
        assertEquals(adminMsgsBefore + 1, adminMsgsAfter.size)

        val parentMsg = adminMsgsAfter.last()
        chatRepo.sendThreadMessage(textChannel.id, parentMsg.id, "Réponse admin thread")
        assertEquals(1, chatRepo.threads.value[parentMsg.id].orEmpty().size)

        assertTrue(chatRepo.executeSlashCommand(textChannel.id, "/xp"))

        // Reopen as Admin
        assertTrue(commRepo.setServerClosed(srv.id, false).isSuccess)
        assertFalse(chatRepo.servers.value.first { it.id == srv.id }.isClosed)

        // 2. Moderator (non-admin) and regular Member CANNOT close the server
        val modSrv = chatRepo.servers.value.first { it.id == srv.id }.copy(
            ownerId = otherCreator.id,
            adminIds = listOf(otherCreator.id),
            moderatorIds = listOf(me.id),
            members = listOf(otherCreator, me.copy(role = UserRole.MODERATOR))
        )
        commRepo.replaceServerInFlow(modSrv)
        assertTrue(commRepo.setServerClosed(srv.id, true).isFailure)
        assertFalse(chatRepo.setServerClosed(srv.id, true))

        // 3. When closed by Creator, Moderator & Member are blocked from sending messages, threads, and slash commands
        commRepo.replaceServerInFlow(modSrv.copy(isClosed = true, closedByUid = otherCreator.id, closedByName = otherCreator.name))
        assertFalse(CommunityPermissions.canSendMessageInServer(chatRepo.servers.value.first { it.id == srv.id }, me.id))

        val countBeforeBlocked = chatRepo.messages.value[textChannel.id].orEmpty().size
        chatRepo.sendMessage(textChannel.id, "Tentative modérateur serveur fermé")
        assertEquals(countBeforeBlocked, chatRepo.messages.value[textChannel.id].orEmpty().size)
        assertNotNull(chatRepo.lastSendBlockedReason.value)

        chatRepo.sendThreadMessage(textChannel.id, parentMsg.id, "Tentative thread bloqué")
        assertEquals(1, chatRepo.threads.value[parentMsg.id].orEmpty().size)

        assertFalse(chatRepo.executeSlashCommand(textChannel.id, "/xp"))

        // 4. Regular member also blocked when closed, and allowed once reopened
        val memberClosedSrv = modSrv.copy(
            moderatorIds = emptyList(),
            members = listOf(otherCreator, me.copy(role = UserRole.MEMBER)),
            isClosed = true
        )
        commRepo.replaceServerInFlow(memberClosedSrv)
        assertFalse(CommunityPermissions.canSendMessageInServer(memberClosedSrv, me.id))

        chatRepo.sendMessage(textChannel.id, "Tentative membre serveur fermé")
        assertEquals(countBeforeBlocked, chatRepo.messages.value[textChannel.id].orEmpty().size)

        commRepo.replaceServerInFlow(memberClosedSrv.copy(isClosed = false))
        assertTrue(CommunityPermissions.canSendMessageInServer(chatRepo.servers.value.first { it.id == srv.id }, me.id))
        chatRepo.sendMessage(textChannel.id, "Message membre après réouverture")
        assertEquals(countBeforeBlocked + 1, chatRepo.messages.value[textChannel.id].orEmpty().size)
    }
}
