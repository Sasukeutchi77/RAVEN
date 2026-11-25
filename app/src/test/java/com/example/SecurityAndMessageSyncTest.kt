package com.example

import com.example.data.BotTokenManager
import com.example.data.ChatRepository
import com.example.data.MockData
import com.example.model.DirectConversation
import com.example.model.Message
import com.example.model.User
import com.example.model.UserRole
import com.example.util.AudioPlayerHelper
import com.example.util.CloudinaryHelper
import com.example.util.RavenMessagingService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class SecurityAndMessageSyncTest {

    @Test
    fun `message reconciliation removes deleted cloud messages while preserving queued and in-flight entries`() {
        val alice = User(id = "uid_alice", name = "Alice")
        val kept = Message(id = "m_kept", channelId = "c_general", sender = alice, text = "Keep", timestamp = "10:00")
        val remotelyDeleted = Message(id = "m_del", channelId = "c_general", sender = alice, text = "Deleted", timestamp = "10:01")
        val queued = Message(id = "offline_1", channelId = "c_general", sender = alice, text = "Offline", timestamp = "10:02", isOfflineQueued = true)
        val inFlight = Message(id = "m_flight", channelId = "c_general", sender = alice, text = "Sending", timestamp = "10:03")

        val now = 100_000L
        val reconciled = ChatRepository.reconcileTargetMessages(
            existing = listOf(kept, remotelyDeleted, queued, inFlight),
            cloudMessages = listOf(kept),
            hasPendingWrites = false,
            inFlightTimestamps = mapOf("m_flight" to (now - 1_000L)),
            nowMs = now
        )

        val ids = reconciled.map { it.id }.toSet()
        assertTrue("m_kept" in ids)
        assertFalse("m_del" in ids)
        assertTrue("offline_1" in ids)
        assertTrue("m_flight" in ids)

        val afterEmptyCloud = ChatRepository.reconcileTargetMessages(
            existing = listOf(kept, queued),
            cloudMessages = emptyList(),
            hasPendingWrites = false,
            inFlightTimestamps = emptyMap(),
            nowMs = now
        )
        assertEquals(listOf("offline_1"), afterEmptyCloud.map { it.id })
    }

    @Test
    fun `inferDmParticipants recovers canonical pair UIDs even before local conversation cache loads`() {
        val inferred = ChatRepository.inferDmParticipants(
            targetId = "dm_uid_alice__uid_bob",
            selfUid = "uid_alice",
            dmConv = null
        )
        assertEquals(listOf("uid_alice", "uid_bob"), inferred)

        val groupConv = DirectConversation(
            id = "grp_team",
            isGroup = true,
            groupName = "Team",
            groupMembers = listOf(User(id = "uid_bob", name = "Bob"), User(id = "user_me", name = "Local")),
            participantIds = listOf("uid_charlie")
        )
        val inferredGrp = ChatRepository.inferDmParticipants("grp_team", "uid_alice", groupConv)
        assertEquals(listOf("uid_alice", "uid_bob", "uid_charlie"), inferredGrp)
    }

    @Test
    fun `community RBAC blocks unauthorized server rename kick timeout and custom role creation`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)
        val owner = User(id = "owner_1", name = "Owner", role = UserRole.ADMIN)
        val member = repo.currentUser.value
        val otherMember = MockData.friendThomas

        repo.createServer("Test Guild", "TG")
        val srv = repo.servers.value.last()
        val downgradedSrv = srv.copy(
            ownerId = owner.id,
            adminIds = listOf(owner.id),
            moderatorIds = emptyList(),
            members = listOf(owner, member.copy(role = UserRole.MEMBER), otherMember)
        )
        repo.serversMutableFlow().value = repo.servers.value.map { if (it.id == srv.id) downgradedSrv else it }

        // Member lacks MANAGE_COMMUNITY / MODERATE_MEMBERS / MANAGE_ROLES / MANAGE_INVITES
        repo.renameServer(srv.id, "Hijacked")
        assertEquals("Test Guild", repo.servers.value.first { it.id == srv.id }.name)

        val prevInvite = repo.servers.value.first { it.id == srv.id }.inviteCode
        repo.regenerateInviteCode(srv.id)
        assertEquals(prevInvite, repo.servers.value.first { it.id == srv.id }.inviteCode)

        val prevRoles = repo.servers.value.first { it.id == srv.id }.customRoles.size
        repo.createCustomRole(srv.id, "FakeMod", 0xFFFF0000, isAdmin = false, canKickBan = true, canManageChannels = true)
        assertEquals(prevRoles, repo.servers.value.first { it.id == srv.id }.customRoles.size)

        repo.timeoutMember(srv.id, otherMember.id)
        assertFalse(repo.servers.value.first { it.id == srv.id }.members.first { it.id == otherMember.id }.isTimedOut)

        repo.kickMember(srv.id, otherMember.id)
        assertTrue(repo.servers.value.first { it.id == srv.id }.members.any { it.id == otherMember.id })

        // Owner CAN perform privileged actions, except self-kick
        val restoredSrv = downgradedSrv.copy(
            ownerId = member.id,
            adminIds = listOf(member.id)
        )
        repo.serversMutableFlow().value = repo.servers.value.map { if (it.id == srv.id) restoredSrv else it }
        repo.renameServer(srv.id, "Official Guild")
        assertEquals("Official Guild", repo.servers.value.first { it.id == srv.id }.name)
        repo.kickMember(srv.id, otherMember.id)
        assertFalse(repo.servers.value.first { it.id == srv.id }.members.any { it.id == otherMember.id })
    }

    @Test
    fun `offline queue preserves waveform amplitudes and retains items that fail validation on flush`() = runTest {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("raven_advanced_features_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val cm = ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNet = cm.activeNetwork
        if (activeNet != null) {
            val caps = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
            org.robolectric.Shadows.shadowOf(caps).addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            org.robolectric.Shadows.shadowOf(cm).setNetworkCapabilities(activeNet, caps)
        }

        val testScope = TestScope(testScheduler)
        val repo1 = ChatRepository(testScope, appContext = ctx)

        repo1.updateAppSettings { it.copy(simulateOfflineMode = true) }
        repo1.sendRichMessage(
            targetId = "c_general",
            text = "Audio hors-ligne",
            audioUrl = "https://cdn.example.com/voice.m4a",
            audioDurationSec = 7,
            waveformAmplitudes = listOf(18, 44, 92, 61)
        )
        assertEquals(listOf(18, 44, 92, 61), repo1.pendingOfflineMessages.value.first().waveformAmplitudes)

        // Restore persisted queue in a fresh ChatRepository instance
        val repo2 = ChatRepository(testScope, appContext = ctx)
        assertTrue(repo2.isEffectivelyOnline())
        assertEquals(1, repo2.pendingOfflineMessages.value.size)
        assertEquals(listOf(18, 44, 92, 61), repo2.pendingOfflineMessages.value.first().waveformAmplitudes)

        // Lock target channel read-only so flush rejects dispatch -> queued item MUST NOT be lost
        val srv = repo2.servers.value.first { server -> server.channels.any { it.id == "c_general" } }
        repo2.updateChannelSettings(srv.id, "c_general", slowmodeSeconds = 0, isReadOnly = true)
        repo2.updateAppSettings { it.copy(simulateOfflineMode = false) }
        val flushedWhenLocked = repo2.flushOfflineQueue()
        assertEquals(0, flushedWhenLocked)
        assertEquals(1, repo2.pendingOfflineMessages.value.size)

        // Unlock channel -> flush succeeds cleanly
        repo2.updateChannelSettings(srv.id, "c_general", slowmodeSeconds = 0, isReadOnly = false)
        val flushedWhenOpen = repo2.flushOfflineQueue()
        assertEquals(1, flushedWhenOpen)
        assertTrue(repo2.pendingOfflineMessages.value.isEmpty())
    }

    @Test
    fun `signOutCleanly clears sensitive user conversations notifications and messages`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)
        repo.sendMessage("c_general", "Private draft")
        repo.addNotification("Test", "Body", "Src")
        assertTrue(repo.messages.value.isNotEmpty())
        assertTrue(repo.notifications.value.isNotEmpty())
        assertTrue(repo.directConversations.value.isNotEmpty())

        repo.signOutCleanly()
        assertTrue(repo.messages.value.isEmpty())
        assertTrue(repo.notifications.value.isEmpty())
        assertTrue(repo.directConversations.value.isEmpty())
        assertFalse(repo.isFirebaseSignedIn.value)
    }

    @Test
    fun `SSRF webhook URL and public IP checks reject private loopback CGNAT and credentialed URLs`() {
        assertTrue(BotTokenManager.isValidHttpsWebhookUrl("https://hooks.example.org/raven"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("http://hooks.example.org/raven"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://user:pass@hooks.example.org/raven"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://127.0.0.1/hook"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://169.254.169.254/latest/meta-data"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://10.0.0.5/hook"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://100.64.0.1/hook"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://localhost.local/hook"))
        assertFalse(BotTokenManager.isValidHttpsWebhookUrl("https://[::1]/hook"))

        assertFalse(BotTokenManager.isPublicInetAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))))
        assertFalse(BotTokenManager.isPublicInetAddress(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte()))))
        assertFalse(BotTokenManager.isPublicInetAddress(InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 10))))
        assertTrue(BotTokenManager.isPublicInetAddress(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))))
    }

    @Test
    fun `media config audio source and FCM notification sanitizers reject unsafe inputs`() {
        assertTrue(CloudinaryHelper.isValidConfigToken("my_cloud-01"))
        assertFalse(CloudinaryHelper.isValidConfigToken("bad/path?x=1"))
        assertFalse(CloudinaryHelper.isValidConfigToken("../escape"))

        val player = AudioPlayerHelper()
        assertTrue(player.isAllowedAudioSource("https://res.cloudinary.com/demo/video/upload/v1/note.m4a"))
        assertTrue(player.isAllowedAudioSource("/data/user/0/com.example/cache/voice.m4a"))
        assertFalse(player.isAllowedAudioSource("javascript:alert(1)"))
        assertFalse(player.isAllowedAudioSource("content://media/external/audio/1"))
        assertFalse(player.isAllowedAudioSource("/data/cache/../secret.m4a"))

        val sanitized = RavenMessagingService.sanitizeNotificationField("Hello\u0000\nWorld\u0007!", 20)
        assertEquals("Hello World !", sanitized)
    }
}
