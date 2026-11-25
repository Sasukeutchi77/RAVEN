package com.example

import com.example.data.ChatRepository
import com.example.data.MockData
import com.example.data.StoryRepository
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
class RavenAdvancedFeaturesUnitTest {

    @Test
    fun `ephemeral timers view once read receipts and forwarding work properly`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        // 1. Ephemeral timer sets expiration on newly sent messages
        repo.setEphemeralTimer("c_general", 3600)
        assertEquals(3600, repo.ephemeralTimersByTarget.value["c_general"])
        repo.sendMessage("c_general", "Message éphémère 1h")
        val ephMsg = repo.messages.value["c_general"]!!.last()
        assertNotNull(ephMsg.ephemeralExpiresAtMs)

        // Reset ephemeral timer
        repo.setEphemeralTimer("c_general", 0)
        assertNull(repo.ephemeralTimersByTarget.value["c_general"])

        // 2. View-Once photo sent by peer expires cleanly for recipient on open
        val peer = MockData.friendThomas
        val peerViewOnce = Message(
            id = "vo_test_1",
            channelId = "dm_thomas",
            sender = peer,
            text = "Photo secrète",
            timestamp = "12:00",
            imageUrl = "https://example.com/secret.jpg",
            isViewOnce = true
        )
        repo.forwardMessageToTargets(peerViewOnce, listOf("c_discussion"))
        val forwarded = repo.messages.value["c_discussion"]!!.last()
        // View-once photos never leak image URL when forwarded
        assertNull(forwarded.imageUrl)
        assertTrue(forwarded.forwardedFrom?.contains(peer.name) == true)

        // Mark unread peer messages read
        repo.markTargetMessagesRead("c_general")
        val unreadCheck = repo.messages.value["c_general"]!!.first { it.sender.id != repo.currentUser.value.id }
        assertTrue(repo.currentUser.value.id in unreadCheck.readByIds)
    }

    @Test
    fun `orbs tipping server treasury and rules onboarding gate work`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        repo.updateOrbsBalance(500)
        val initialTargetMsg = repo.messages.value["c_general"]!!.first()

        // 1. Tip Orbs on message
        assertTrue(repo.tipOrbsOnMessage("c_general", initialTargetMsg.id, 50))
        assertEquals(450, repo.currentUser.value.orbsBalance)
        val updatedMsg = repo.messages.value["c_general"]!!.first { it.id == initialTargetMsg.id }
        assertEquals(50, updatedMsg.totalOrbsTipped)

        // Insufficient Orbs blocked
        assertFalse(repo.tipOrbsOnMessage("c_general", initialTargetMsg.id, 5000))

        // 2. Server Orbs Treasury donation increases treasury and boosts
        val boostsBefore = repo.servers.value.first { it.id == "server_main" }.boostCount
        assertTrue(repo.donateOrbsToServerTreasury("server_main", 200))
        val updatedSrv = repo.servers.value.first { it.id == "server_main" }
        assertEquals(200, updatedSrv.treasuryOrbs)
        assertEquals(boostsBefore + 2, updatedSrv.boostCount)

        // 3. Server Rules Acceptance onboarding
        repo.setRequireRulesAcceptance("server_main", true)
        val srvReq = repo.servers.value.first { it.id == "server_main" }
        assertTrue(srvReq.requireRulesAcceptance)
        assertTrue(srvReq.customization.requireRulesAcceptance)

        repo.acceptServerRules("server_main")
        val srvAccepted = repo.servers.value.first { it.id == "server_main" }
        assertTrue(repo.currentUser.value.id in srvAccepted.acceptedRulesMemberIds)
    }

    @Test
    fun `forum channels support creating upvoting replying and resolving topics`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        val post = repo.createForumPost(
            channelId = "c_forum_aide",
            serverId = "server_main",
            title = "Widget écran d'accueil",
            content = "Comment épingler le widget Raven ?",
            tags = listOf("Aide", "Android"),
            status = ForumPostStatus.OPEN
        )
        assertNotNull(post)
        val postId = post!!.id

        // Toggle upvote off then on
        repo.toggleForumPostUpvote("c_forum_aide", postId)
        val afterDown = repo.forumPostsByChannel.value["c_forum_aide"]!!.first { it.id == postId }
        assertFalse(repo.currentUser.value.id in afterDown.upvotedByIds)

        repo.toggleForumPostUpvote("c_forum_aide", postId)
        val afterUp = repo.forumPostsByChannel.value["c_forum_aide"]!!.first { it.id == postId }
        assertTrue(repo.currentUser.value.id in afterUp.upvotedByIds)

        // Add reply & mark resolved
        repo.addForumReply("c_forum_aide", postId, "Appui long sur le bureau > Widgets > Raven !")
        repo.updateForumPostStatus("c_forum_aide", postId, ForumPostStatus.RESOLVED)

        val finalPost = repo.forumPostsByChannel.value["c_forum_aide"]!!.first { it.id == postId }
        assertEquals(1, finalPost.replies.size)
        assertEquals(ForumPostStatus.RESOLVED, finalPost.status)
    }

    @Test
    fun `rich stories support stickers music tags quick polls and server member visibility`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)
        val storyRepo = StoryRepository(repo)

        val res = storyRepo.publishTextStory(
            textContent = "Soirée tournois Raven !",
            visibility = StoryVisibility.SERVER_MEMBERS,
            allowedServerId = "server_main",
            stickerText = "LIVE",
            stickerStyle = "NEON",
            quickPollQuestion = "Quel jeu ce soir ?",
            quickPollOptions = listOf("Valorant", "Minecraft"),
            musicTrackTitle = "Midnight Drive",
            musicTrackArtist = "Raven Synth"
        )
        assertTrue(res.isSuccess)
        val story = res.getOrThrow()
        assertEquals("LIVE", story.stickerText)
        assertEquals("NEON", story.stickerStyle)
        assertEquals("Midnight Drive", story.musicTrackTitle)
        assertEquals("server_main", story.allowedServerId)
        assertEquals(2, story.quickPollOptions.size)

        // Vote on quick poll
        assertTrue(storyRepo.voteOnStoryQuickPoll(story.id, "Valorant").isSuccess)
        val votedStory = storyRepo.stories.value.first { it.id == story.id }
        assertEquals(1, votedStory.quickPollVotes["Valorant"])
        assertTrue(repo.currentUser.value.id in votedStory.quickPollVotedByIds)

        // Member of server_main can view it; user outside server cannot
        assertTrue(votedStory.canBeViewedBy("some_other_member", emptySet(), setOf("server_main")))
        assertFalse(votedStory.canBeViewedBy("outsider_uid", emptySet(), setOf("other_server")))
    }

    @Test
    fun `smart offline mode queues messages and auto flushes when back online`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        // Turn on offline simulation
        repo.updateAppSettings { it.copy(simulateOfflineMode = true) }
        assertFalse(repo.isEffectivelyOnline())

        repo.sendRichMessage(
            targetId = "c_general",
            text = "Message rédigé dans le métro",
            waveformAmplitudes = listOf(20, 50, 80),
            audioDurationSec = 4
        )
        assertEquals(1, repo.pendingOfflineMessages.value.size)
        val queuedMsg = repo.messages.value["c_general"]!!.last()
        assertTrue(queuedMsg.isOfflineQueued)

        // Restore online status -> should flush automatically
        repo.updateAppSettings { it.copy(simulateOfflineMode = false) }
        assertTrue(repo.isEffectivelyOnline())
        assertTrue(repo.pendingOfflineMessages.value.isEmpty())

        val flushedMsg = repo.messages.value["c_general"]!!.last { it.text == "Message rédigé dans le métro" }
        assertFalse(flushedMsg.isOfflineQueued)
    }

    @Test
    fun `direct call lifecycle supports answer pip minimize and call history`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)
        val peer = MockData.friendEmma

        repo.simulateIncomingCall(peer, isVideo = true)
        val incoming = repo.activeDirectCall.value
        assertNotNull(incoming)
        assertEquals(DirectCallStatus.RINGING_INCOMING, incoming!!.status)
        assertTrue(incoming.isVideo)

        repo.answerDirectCall()
        assertEquals(DirectCallStatus.CONNECTED, repo.activeDirectCall.value!!.status)

        repo.toggleCallMinimized(true)
        repo.toggleCallMute()
        repo.toggleCallCamera()
        val updated = repo.activeDirectCall.value!!
        assertTrue(updated.isMinimized)
        assertTrue(updated.isMuted)
        assertFalse(updated.isCameraOn)

        repo.endDirectCall()
        assertNull(repo.activeDirectCall.value)
        assertEquals(1, repo.callHistory.value.size)
        assertEquals(peer.name, repo.callHistory.value.first().peerName)
    }
}
