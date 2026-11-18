package com.example.util

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.media.AudioManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.ChatRepository
import com.example.data.CommunityPermissions
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.model.CustomRole
import com.example.model.RolePermission
import com.example.model.Server
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.viewmodel.RavenAppViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.webrtc.PeerConnection

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdvancedVoiceWebRtcTest {

    @Test
    fun `join leave cleanup foreground service audio mode and individual volumes work cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testScope = TestScope(testScheduler)
        var serviceStartCount = 0
        var serviceStopCount = 0
        var lastMutedFlag = false
        var lastScreenFlag = false

        val manager = WebRtcVoiceManager(
            context = context,
            scope = testScope,
            startServiceHook = { _, muted, screen ->
                serviceStartCount++
                lastMutedFlag = muted
                lastScreenFlag = screen
            },
            stopServiceHook = {
                serviceStopCount++
            }
        )

        manager.joinRoom(
            serverId = "srv_voice",
            channelId = "ch_v1",
            channelName = "Vocal Team",
            currentUid = "uid_me",
            displayName = "Jarvis",
            isPublic = false,
            memberIds = listOf("uid_me", "uid_alice"),
            firestore = null,
            isMuted = false,
            isDeafened = false
        )
        runCurrent()

        assertTrue(manager.sessionState.value.isConnected)
        assertEquals(VoiceConnectionPhase.CONNECTED, manager.sessionState.value.connectionPhase)
        assertTrue(serviceStartCount >= 1)
        assertFalse(lastMutedFlag)

        // Mute & Deafen propagate to service and session state
        manager.setMuted(true)
        runCurrent()
        assertTrue(lastMutedFlag)

        manager.setDeafened(true)
        assertTrue(lastMutedFlag)
        manager.setDeafened(false)
        manager.setMuted(false)

        // Individual peer volume mixing clamps cleanly between 0f and 2f
        manager.setRemotePeerVolume("uid_alice", 1.75f)
        assertEquals(1.75f, manager.getRemotePeerVolume("uid_alice"), 0.001f)
        manager.setRemotePeerVolume("uid_alice", 4.5f)
        assertEquals(2.0f, manager.getRemotePeerVolume("uid_alice"), 0.001f)
        manager.setRemotePeerVolume("uid_alice", -0.5f)
        assertEquals(0.0f, manager.getRemotePeerVolume("uid_alice"), 0.001f)

        // Leave & full shutdown reset everything cleanly and restore AudioManager
        manager.shutdown()
        runCurrent()

        assertFalse(manager.sessionState.value.isConnected)
        assertEquals(VoiceConnectionPhase.DISCONNECTED, manager.sessionState.value.connectionPhase)
        assertTrue(serviceStopCount >= 1)
        val audioManager = context.getSystemService(AudioManager::class.java)
        assertNotEquals(AudioManager.MODE_IN_COMMUNICATION, audioManager.mode)
    }

    @Test
    fun `channel permissions locked state role restrictions capacity limit and voice kick enforced`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(scope = testScope, appContext = context)

        val vipRole = CustomRole(
            id = "role_vip",
            name = "VIP",
            colorHex = 0xFFF0B232L,
            permissions = setOf(
                RolePermission.SEND_MESSAGES,
                RolePermission.CONNECT_VOICE,
                RolePermission.SPEAK_VOICE
            )
        )
        val noVoiceRole = CustomRole(
            id = "role_muted_role",
            name = "Sans Vocal",
            colorHex = 0xFF888888L,
            permissions = setOf(RolePermission.SEND_MESSAGES)
        )

        val lockedChannel = Channel(
            id = "ch_locked",
            serverId = "srv_perm",
            name = "Salon Verrouillé",
            type = ChannelType.VOICE,
            isLocked = true
        )
        val fullChannel = Channel(
            id = "ch_full",
            serverId = "srv_perm",
            name = "Duo Cap",
            type = ChannelType.VOICE,
            maxParticipants = 2,
            activeVoiceUsers = listOf(
                User(id = "u_1", name = "U1"),
                User(id = "u_2", name = "U2")
            )
        )
        val vipChannel = Channel(
            id = "ch_vip",
            serverId = "srv_perm",
            name = "Salon VIP",
            type = ChannelType.VOICE,
            isPrivate = true,
            allowedRoleIds = listOf("role_vip")
        )

        val memberUser = User(id = "u_bob", name = "Bob", role = UserRole.MEMBER)
        val vipUser = User(id = "u_claire", name = "Claire", role = UserRole.MEMBER, customRoleId = "role_vip")
        val noVoiceUser = User(id = "u_restricted", name = "Dan", role = UserRole.MEMBER, customRoleId = "role_muted_role")
        val modUser = User(id = "u_mod", name = "Alex", role = UserRole.MODERATOR)

        val server = Server(
            id = "srv_perm",
            name = "Voice Lab",
            iconText = "VL",
            ownerId = "u_owner",
            adminIds = listOf("u_owner"),
            moderatorIds = listOf("u_mod"),
            customRoles = listOf(vipRole, noVoiceRole),
            members = listOf(
                User(id = "u_owner", name = "Owner", role = UserRole.ADMIN),
                memberUser,
                vipUser,
                noVoiceUser,
                modUser
            ),
            channels = listOf(lockedChannel, fullChannel, vipChannel)
        )

        // Regular member blocked on locked, full, and VIP channels
        assertFalse(CommunityPermissions.canJoinVoiceChannel(server, lockedChannel, memberUser.id).allowed)
        assertFalse(CommunityPermissions.canJoinVoiceChannel(server, fullChannel, memberUser.id).allowed)
        assertFalse(CommunityPermissions.canJoinVoiceChannel(server, vipChannel, memberUser.id).allowed)

        // User missing CONNECT_VOICE cannot join even unlocked channel
        val openChannel = lockedChannel.copy(id = "ch_open", isLocked = false)
        assertFalse(CommunityPermissions.canJoinVoiceChannel(server, openChannel, noVoiceUser.id).allowed)

        // VIP custom role allowed in VIP channel; Moderator bypasses locked channel
        assertTrue(CommunityPermissions.canJoinVoiceChannel(server, vipChannel, vipUser.id).allowed)
        assertTrue(CommunityPermissions.canJoinVoiceChannel(server, lockedChannel, modUser.id).allowed)

        // Repository rejects joining locked channel for regular member and exposes error
        repo.serversMutableFlow().value = repo.servers.value + server
        repo.updateUserProfile(name = "Bob")
        // Force currentUser id to regular member u_bob for this test scenario
        val field = ChatRepository::class.java.getDeclaredField("_currentUser")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<User>).value = memberUser

        val joinLockedResult = repo.joinVoiceChannel(lockedChannel)
        assertFalse(joinLockedResult)
        assertNull(repo.connectedVoiceChannel.value)
        assertTrue(repo.voiceError.value?.contains("verrouillé", ignoreCase = true) == true)

        val joinFullResult = repo.joinVoiceChannel(fullChannel)
        assertFalse(joinFullResult)
        assertTrue(repo.voiceError.value?.contains("complet", ignoreCase = true) == true)

        // Now promote acting user to Moderator -> can join locked channel and kick a user from fullChannel
        @Suppress("UNCHECKED_CAST")
        (field.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<User>).value = modUser
        assertTrue(repo.joinVoiceChannel(fullChannel))
        assertTrue(repo.kickUserFromVoice("srv_perm", "ch_full", "u_1"))
        val updatedFull = repo.servers.value.first { it.id == "srv_perm" }.channels.first { it.id == "ch_full" }
        assertFalse(updatedFull.activeVoiceUsers.any { it.id == "u_1" })
        assertTrue("u_1" in updatedFull.kickedVoiceUids)
        assertFalse(CommunityPermissions.canJoinVoiceChannel(server.copy(channels = listOf(updatedFull)), updatedFull, "u_1").allowed)

        repo.releaseResources()
    }

    @Test
    fun `network loss triggers auto-disconnect after grace timeout and network recovery triggers reconnect`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testScope = TestScope(testScheduler)
        val reasons = mutableListOf<VoiceDisconnectReason>()

        val manager = WebRtcVoiceManager(
            context = context,
            scope = testScope,
            networkGraceTimeoutMs = 500L,
            reconnectBackoffBaseMs = 100L,
            maxReconnectAttempts = 3,
            onAutoDisconnected = { reasons.add(it) },
            startServiceHook = { _, _, _ -> },
            stopServiceHook = {}
        )

        manager.joinRoom(
            serverId = "srv_net",
            channelId = "ch_net",
            currentUid = "u_me",
            displayName = "Jarvis",
            isPublic = true,
            memberIds = listOf("u_me"),
            firestore = null,
            isMuted = false,
            isDeafened = false
        )
        runCurrent()

        // 1. Simulate network loss then recovery before timeout -> cancels grace disconnect & reconnects
        manager.onNetworkLostDetected()
        runCurrent()
        assertEquals(VoiceConnectionPhase.NETWORK_LOST, manager.sessionState.value.connectionPhase)
        assertFalse(manager.sessionState.value.networkAvailable)

        advanceTimeBy(200L)
        manager.onNetworkRestoredOrChanged("5G / 4G", networkSwitched = true)
        advanceTimeBy(300L)
        runCurrent()

        assertTrue(manager.sessionState.value.isConnected)
        assertTrue(manager.sessionState.value.networkAvailable)
        assertEquals("5G / 4G", manager.sessionState.value.networkTypeLabel)
        assertTrue(reasons.isEmpty())

        // 2. Simulate prolonged network loss exceeding grace timeout -> auto-disconnects
        manager.onNetworkLostDetected()
        advanceTimeBy(650L)
        runCurrent()

        assertFalse(manager.sessionState.value.isConnected)
        assertEquals(listOf(VoiceDisconnectReason.NETWORK_TIMEOUT), reasons)
        manager.shutdown()
    }

    @Test
    fun `repeated ICE failures exhaust backoff retries and auto-disconnect cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testScope = TestScope(testScheduler)
        val reasons = mutableListOf<VoiceDisconnectReason>()

        val manager = WebRtcVoiceManager(
            context = context,
            scope = testScope,
            networkGraceTimeoutMs = 5_000L,
            reconnectBackoffBaseMs = 80L,
            maxReconnectAttempts = 2,
            onAutoDisconnected = { reasons.add(it) },
            startServiceHook = { _, _, _ -> },
            stopServiceHook = {}
        )

        manager.joinRoom(
            serverId = "srv_ice",
            channelId = "ch_ice",
            currentUid = "u_me",
            displayName = "Jarvis",
            isPublic = false,
            memberIds = listOf("u_me", "u_peer"),
            firestore = null,
            isMuted = false,
            isDeafened = false
        )
        runCurrent()

        manager.onIceConnectionStateChanged("u_peer", PeerConnection.IceConnectionState.FAILED)
        advanceTimeBy(1_000L)
        runCurrent()

        assertFalse(manager.sessionState.value.isConnected)
        assertEquals(listOf(VoiceDisconnectReason.RECONNECT_EXHAUSTED), reasons)
        manager.shutdown()
    }

    @Test
    fun `background pauses camera while keeping audio alive and foreground resumes camera`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val testScope = TestScope(testScheduler)
        val manager = WebRtcVoiceManager(
            context = context,
            scope = testScope,
            startServiceHook = { _, _, _ -> },
            stopServiceHook = {}
        )

        manager.joinRoom(
            serverId = "srv_bg",
            channelId = "ch_bg",
            currentUid = "u_me",
            displayName = "Jarvis",
            isPublic = true,
            memberIds = listOf("u_me"),
            firestore = null,
            isMuted = false,
            isDeafened = false
        )
        manager.setCameraEnabled(true, useFrontCamera = false)
        assertTrue(manager.sessionState.value.isCameraStreaming)
        assertFalse(manager.sessionState.value.useFrontCamera)

        // Move app to background -> camera paused automatically, voice stays connected
        manager.onAppBackgroundChanged(true)
        assertTrue(manager.sessionState.value.isConnected)
        assertFalse(manager.sessionState.value.isCameraStreaming)
        assertTrue(manager.sessionState.value.cameraPausedInBackground)

        // Return to foreground -> camera resumes automatically with preserved lens choice
        manager.onAppBackgroundChanged(false)
        assertTrue(manager.sessionState.value.isCameraStreaming)
        assertFalse(manager.sessionState.value.cameraPausedInBackground)
        assertFalse(manager.sessionState.value.useFrontCamera)

        // Screen share with MediaProjection consent intent + stop
        val fakeConsentIntent = Intent("com.example.TEST_PROJECTION")
        assertFalse(manager.startScreenSharing(Activity.RESULT_CANCELED, fakeConsentIntent))
        assertTrue(manager.startScreenSharing(Activity.RESULT_OK, fakeConsentIntent))
        assertTrue(manager.sessionState.value.isScreenSharing)

        manager.stopScreenSharing()
        assertFalse(manager.sessionState.value.isScreenSharing)
        manager.shutdown()
    }

    @Test
    fun `screen rotation preserves active voice call camera screen share and full room UI via ViewModel`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val vm = ViewModelProvider(activity)[RavenAppViewModel::class.java]
        val repo = vm.repository

        val voiceChannel = repo.servers.value.first { it.id == "server_main" }
            .channels.first { it.type == ChannelType.VOICE }
        assertTrue(repo.joinVoiceChannel(voiceChannel))
        vm.setViewingFullVoiceRoom(true)
        repo.setVoiceCameraEnabled(true, useFrontCamera = true)
        repo.setVoiceScreenSharing(true)

        assertTrue(repo.rtcSessionState.value.isConnected)
        assertTrue(repo.rtcSessionState.value.isCameraStreaming)
        assertTrue(repo.rtcSessionState.value.isScreenSharing)

        // Simulate Android orientation rotation (recreate Activity)
        controller.recreate()
        val recreatedActivity = controller.get()
        val recreatedVm = ViewModelProvider(recreatedActivity)[RavenAppViewModel::class.java]

        // Same ViewModel/Repository instance & active voice session survive rotation intact
        assertSame(repo, recreatedVm.repository)
        assertNotNull(recreatedVm.repository.connectedVoiceChannel.value)
        assertEquals(voiceChannel.id, recreatedVm.repository.connectedVoiceChannel.value?.id)
        assertTrue(recreatedVm.isViewingFullVoiceRoom.value)
        assertTrue(recreatedVm.repository.rtcSessionState.value.isConnected)
        assertTrue(recreatedVm.repository.rtcSessionState.value.isCameraStreaming)
        assertTrue(recreatedVm.repository.rtcSessionState.value.isScreenSharing)

        // Clean shutdown releases resources when activity genuinely finishes
        recreatedVm.repository.leaveVoiceChannel()
        assertNull(recreatedVm.repository.connectedVoiceChannel.value)
        assertFalse(recreatedVm.repository.rtcSessionState.value.isConnected)
        controller.pause().stop().destroy()
    }
}
