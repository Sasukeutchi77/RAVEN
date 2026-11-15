package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatRepository
import com.example.model.AppThemeMode
import com.example.model.AudioEqPreset
import com.example.model.ChannelType
import com.example.model.ChatDisplayStyle
import com.example.model.MusicTrack
import com.example.model.NotificationType
import com.example.model.WhiteboardStroke
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
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Raven", appName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository sends message and joins voice`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        assertEquals("server_main", repo.selectedServerId.value)

        // Send message
        repo.sendMessage("c_general", "Test Discord message")
        val messages = repo.messages.value["c_general"]
        assertNotNull(messages)
        assertTrue(messages!!.any { it.text == "Test Discord message" })

        // Join Voice Channel
        val voiceChannel = repo.servers.value.first { it.id == "server_main" }
            .channels.first { it.type == ChannelType.VOICE }
        repo.joinVoiceChannel(voiceChannel)
        assertNotNull(repo.connectedVoiceChannel.value)
        assertEquals(voiceChannel.id, repo.connectedVoiceChannel.value?.id)

        // Leave Voice
        repo.leaveVoiceChannel()
        assertNull(repo.connectedVoiceChannel.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository updates full user profile`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        repo.updateUserProfile(
            name = "Alexandre",
            pronouns = "iel",
            bio = "Nouvelle bio Discord",
            customStatus = "En réunion",
            customStatusEmoji = "code",
            activity = "Développe une app"
        )

        val updated = repo.currentUser.value
        assertEquals("Alexandre", updated.name)
        assertEquals("iel", updated.pronouns)
        assertEquals("Nouvelle bio Discord", updated.bio)
        assertEquals("En réunion", updated.customStatus)
        assertEquals("code", updated.customStatusEmoji)
        assertEquals("Développe une app", updated.activity)

        // Test toggle Super Badge
        val initialBadges = updated.badges
        assertTrue(initialBadges.contains("ACTIVE_DEV"))
        repo.toggleUserBadge("ACTIVE_DEV")
        assertFalse(repo.currentUser.value.badges.contains("ACTIVE_DEV"))
        repo.toggleUserBadge("ACTIVE_DEV")
        assertTrue(repo.currentUser.value.badges.contains("ACTIVE_DEV"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository toggles emoji reactions on messages`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        val targetMsgId = "m_1"
        repo.toggleReaction("c_general", targetMsgId, "😂")
        val afterAdd = repo.messages.value["c_general"]?.first { it.id == targetMsgId }?.reactions
        assertNotNull(afterAdd)
        val laughReaction = afterAdd?.find { it.emoji == "😂" }
        assertNotNull(laughReaction)
        assertEquals(1, laughReaction?.count)
        assertTrue(laughReaction?.userReacted == true)

        repo.toggleReaction("c_general", targetMsgId, "😂")
        val afterRemove = repo.messages.value["c_general"]?.first { it.id == targetMsgId }?.reactions
        assertNull(afterRemove?.find { it.emoji == "😂" })

        repo.toggleReaction("c_general", targetMsgId, "👋")
        val waveAfterAdd = repo.messages.value["c_general"]?.first { it.id == targetMsgId }?.reactions?.find { it.emoji == "👋" }
        assertEquals(3, waveAfterAdd?.count)
        assertTrue(waveAfterAdd?.userReacted == true)

        repo.toggleReaction("c_general", targetMsgId, "👋")
        val waveAfterRemove = repo.messages.value["c_general"]?.first { it.id == targetMsgId }?.reactions?.find { it.emoji == "👋" }
        assertEquals(2, waveAfterRemove?.count)
        assertFalse(waveAfterRemove?.userReacted == true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository supports message editing and threads`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        repo.editMessage("c_general", "m_1", "Message modifié !")
        val edited = repo.messages.value["c_general"]?.first { it.id == "m_1" }
        assertEquals("Message modifié !", edited?.text)
        assertTrue(edited?.isEdited == true)

        repo.sendThreadMessage("c_general", "m_1", "Première réponse dans le fil")
        repo.sendThreadMessage("c_general", "m_1", "Deuxième réponse")
        val threadList = repo.threads.value["m_1"]
        assertNotNull(threadList)
        assertEquals(2, threadList?.size)
        val parentAfter = repo.messages.value["c_general"]?.first { it.id == "m_1" }
        assertEquals(2, parentAfter?.threadCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `xp level up rewards orbs and creates inbox notification`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        val initialLevel = repo.currentUser.value.level
        val initialOrbs = repo.currentUser.value.orbsBalance
        repo.grantXp(100, "Test XP")

        assertEquals(initialLevel + 1, repo.currentUser.value.level)
        assertEquals(initialOrbs + 50, repo.currentUser.value.orbsBalance)
        assertTrue(repo.notifications.value.any { it.type == NotificationType.LEVEL_UP && it.title.contains("Niveau ${initialLevel + 1}") })

        repo.markAllNotificationsRead()
        assertTrue(repo.notifications.value.all { it.isRead })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `server management roles moderation audit logs and invite codes work`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        repo.renameServer("server_main", "Raven QG")
        repo.createCustomRole("server_main", "VIP Orbs", 0xFFEB459E, isAdmin = false, canKickBan = true, canManageChannels = true)
        val server = repo.servers.value.first { it.id == "server_main" }
        assertEquals("Raven QG", server.name)
        assertTrue(server.customRoles.any { it.name == "VIP Orbs" })
        assertTrue(server.auditLogs.size >= 2)

        val targetMember = server.members.first { it.id != repo.currentUser.value.id && it.id != "user_me" }
        repo.timeoutMember("server_main", targetMember.id)
        val timedOutMember = repo.servers.value.first { it.id == "server_main" }.members.first { it.id == targetMember.id }
        assertTrue(timedOutMember.isTimedOut)

        repo.kickMember("server_main", targetMember.id)
        assertFalse(repo.servers.value.first { it.id == "server_main" }.members.any { it.id == targetMember.id })

        val joined = repo.joinServerByInviteCode("RAVEN-GAMING")
        assertTrue(joined)
        assertTrue(repo.servers.value.any { it.inviteCode == "RAVEN-GAMING" })

        repo.updateAppSettings { it.copy(themeMode = AppThemeMode.AMOLED, soundEffectsEnabled = false) }
        val updatedSettings = repo.appSettings.value
        assertEquals(AppThemeMode.AMOLED, updatedSettings.themeMode)
        assertFalse(updatedSettings.soundEffectsEnabled)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `server events stage slash commands stickers files folders and groups work`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        // 1. Server Events
        val initialEventsCount = repo.servers.value.first { it.id == "server_main" }.events.size
        repo.createServerEvent("server_main", "Nuit Gaming", "Tournoi Coop", "Vendredi 21h", "Salon vocal 1")
        val updatedEvents = repo.servers.value.first { it.id == "server_main" }.events
        assertEquals(initialEventsCount + 1, updatedEvents.size)
        val evt = updatedEvents.first { it.title == "Nuit Gaming" }
        repo.toggleEventInterest("server_main", evt.id) // removes my initial interest
        assertFalse(repo.servers.value.first { it.id == "server_main" }.events.first { it.id == evt.id }.interestedUserIds.contains(repo.currentUser.value.id))
        repo.toggleEventInterest("server_main", evt.id) // re-adds
        assertTrue(repo.servers.value.first { it.id == "server_main" }.events.first { it.id == evt.id }.interestedUserIds.contains(repo.currentUser.value.id))

        // 2. Stage Channel
        val stageChannel = repo.servers.value.first { it.id == "server_main" }.channels.first { it.type == ChannelType.STAGE }
        repo.selectChannel(stageChannel)
        assertEquals(stageChannel.id, repo.connectedVoiceChannel.value?.id)
        repo.toggleRaiseHand(stageChannel.id)
        assertTrue(repo.connectedVoiceChannel.value?.stageRaisedHandIds?.contains(repo.currentUser.value.id) == true)
        repo.promoteToSpeaker(stageChannel.id, repo.currentUser.value.id)
        assertTrue(repo.connectedVoiceChannel.value?.stageSpeakerIds?.contains(repo.currentUser.value.id) == true)
        repo.moveToAudience(stageChannel.id, repo.currentUser.value.id)
        assertFalse(repo.connectedVoiceChannel.value?.stageSpeakerIds?.contains(repo.currentUser.value.id) == true)
        repo.leaveVoiceChannel()

        // 3. Slash Commands
        assertTrue(repo.executeSlashCommand("c_general", "/de 20"))
        assertTrue(repo.messages.value["c_general"]?.any { it.text.contains("Lancer de dé (D20)") } == true)
        assertTrue(repo.executeSlashCommand("c_general", "/annonce Maintenance test"))
        assertTrue(repo.messages.value["c_general"]?.any { it.isSystemAnnouncement && it.text.contains("Maintenance test") } == true)
        assertTrue(repo.executeSlashCommand("c_general", "/sondage Langage ? | Kotlin | Java"))
        assertTrue(repo.messages.value["c_general"]?.any { it.poll?.question == "Langage ?" } == true)

        // 4. Stickers & File Attachments
        val initialOrbs = repo.currentUser.value.orbsBalance
        assertTrue(repo.unlockSticker("stk_crown", 120))
        assertEquals(initialOrbs - 120, repo.currentUser.value.orbsBalance)
        assertTrue(repo.currentUser.value.unlockedStickers.contains("stk_crown"))
        repo.sendSticker("c_general", "stk_crown")
        assertTrue(repo.messages.value["c_general"]?.any { it.stickerId == "stk_crown" } == true)

        repo.sendFileAttachment("c_general", "architecture.pdf", "application/pdf", "2.4 Mo")
        assertTrue(repo.messages.value["c_general"]?.any { it.attachmentName == "architecture.pdf" && it.attachmentSizeLabel == "2.4 Mo" } == true)

        // 5. Server Folders
        repo.createServerFolder("Dev & Jeux", 0xFF5865F2, listOf("server_main"))
        assertEquals(1, repo.serverFolders.value.size)
        val fldId = repo.serverFolders.value.first().id
        assertTrue(repo.serverFolders.value.first().isExpanded)
        repo.toggleServerFolderExpanded(fldId)
        assertFalse(repo.serverFolders.value.first().isExpanded)

        // 6. Group DMs & Friend Requests / Blocking
        val grp = repo.createGroupConversation("Équipe Kotlin", listOf(com.example.data.MockData.friendThomas, com.example.data.MockData.friendEmma))
        assertNotNull(grp)
        assertTrue(grp?.isGroup == true)
        assertEquals("Équipe Kotlin", grp?.groupName)

        val initialReqCount = repo.friendRequests.value.size
        assertTrue(initialReqCount >= 1)
        val firstReqId = repo.friendRequests.value.first().id
        repo.acceptFriendRequest(firstReqId)
        assertEquals(initialReqCount - 1, repo.friendRequests.value.size)

        val friendToBlock = com.example.data.MockData.friendEmma
        repo.blockUser(friendToBlock)
        assertTrue(repo.blockedUsers.value.any { it.id == friendToBlock.id })
        repo.unblockUser(friendToBlock.id)
        assertFalse(repo.blockedUsers.value.any { it.id == friendToBlock.id })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `server boost slowmode readonly global search quests streak and private notes work`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        // 1. Server Boost & Banner customization
        val orbsBeforeBoost = repo.currentUser.value.orbsBalance
        assertTrue(repo.boostServer("server_main"))
        assertEquals(orbsBeforeBoost - 200, repo.currentUser.value.orbsBalance)
        assertEquals(1, repo.servers.value.first { it.id == "server_main" }.boostCount)
        assertEquals(1, repo.servers.value.first { it.id == "server_main" }.boostLevel)
        assertTrue(repo.boostServer("server_main"))
        assertEquals(2, repo.servers.value.first { it.id == "server_main" }.boostLevel)
        repo.updateServerBannerColor("server_main", 0xFF23A55A)
        assertEquals(0xFF23A55A, repo.servers.value.first { it.id == "server_main" }.customBannerColorHex)

        // 2. Per-channel Slowmode & Read-Only enforcement
        repo.updateChannelSettings("server_main", "c_discussion", slowmodeSeconds = 10, isReadOnly = false)
        val updatedDisc = repo.servers.value.first { it.id == "server_main" }.channels.first { it.id == "c_discussion" }
        assertEquals(10, updatedDisc.slowmodeSeconds)
        assertFalse(updatedDisc.isReadOnly)

        val beforeCount = repo.messages.value["c_discussion"]?.size ?: 0
        repo.sendMessage("c_discussion", "Premier message slowmode")
        assertEquals(beforeCount + 1, repo.messages.value["c_discussion"]?.size ?: 0)
        // Rapid second send should be blocked by 10s slowmode
        repo.sendMessage("c_discussion", "Deuxième message trop vite")
        assertEquals(beforeCount + 1, repo.messages.value["c_discussion"]?.size ?: 0)
        assertNotNull(repo.lastSendBlockedReason.value)

        // Lock read-only
        repo.updateChannelSettings("server_main", "c_general", slowmodeSeconds = 0, isReadOnly = true)
        val beforeReadOnly = repo.messages.value["c_general"]?.size ?: 0
        repo.sendMessage("c_general", "Tentative en lecture seule")
        assertEquals(beforeReadOnly, repo.messages.value["c_general"]?.size ?: 0)
        assertTrue(repo.lastSendBlockedReason.value?.contains("lecture seule") == true)
        // Unlock back
        repo.updateChannelSettings("server_main", "c_general", slowmodeSeconds = 0, isReadOnly = false)

        // 3. Global Search across channels & DMs
        repo.sendMessage("c_general", "Recherche motcle_unique_raven dans salon")
        val searchHits = repo.searchAllMessages(
            serverId = null,
            query = "motcle_unique_raven",
            filter = com.example.model.GlobalSearchFilter.ALL
        )
        assertEquals(1, searchHits.size)
        assertEquals("c_general", searchHits.first().targetId)

        // 4. Live Quests & Daily Streak
        val msgQuestAfter = repo.quests.value.first { it.id == "q_msg" }
        assertTrue(msgQuestAfter.isCompleted)
        val stickerQuestBefore = repo.quests.value.first { it.id == "q_sticker" }
        assertFalse(stickerQuestBefore.isCompleted)
        val firstGenMsgId = repo.messages.value["c_general"]!!.first().id
        repo.toggleReaction("c_general", firstGenMsgId, "🔥")
        val stickerQuestAfter = repo.quests.value.first { it.id == "q_sticker" }
        assertTrue(stickerQuestAfter.isCompleted)
        val orbsBeforeClaim = repo.currentUser.value.orbsBalance
        val questReward = repo.claimQuestReward("q_msg")
        assertTrue(questReward > 0)
        assertEquals(orbsBeforeClaim + questReward, repo.currentUser.value.orbsBalance)
        assertEquals(0, repo.claimQuestReward("q_msg"))

        val streakBefore = repo.dailyStreak.value
        assertFalse(repo.streakClaimedToday.value)
        val streakReward = repo.claimDailyStreakReward()
        assertTrue(streakReward > 0)
        assertEquals(streakBefore + 1, repo.dailyStreak.value)
        assertTrue(repo.streakClaimedToday.value)
        assertEquals(0, repo.claimDailyStreakReward())

        // 5. Private Notes on User Profiles
        repo.setPrivateUserNote("user_thomas", "Ami de jeu FPS - dispo 21h")
        assertEquals("Ami de jeu FPS - dispo 21h", repo.privateUserNotes.value["user_thomas"])
        repo.setPrivateUserNote("user_thomas", "   ")
        assertNull(repo.privateUserNotes.value["user_thomas"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `discovery jukebox mixer customization leaderboard shop whiteboard and productivity work`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        // 1. Discovery & Public Servers
        val initialDiscoverCount = repo.discoverServers.value.size
        assertTrue(initialDiscoverCount >= 3)
        repo.createServer("Mon Clan Public", "MC", isPublic = true, categoryTag = "Gaming")
        assertEquals(initialDiscoverCount + 1, repo.discoverServers.value.size)
        val firstListing = repo.discoverServers.value.last()
        assertTrue(repo.joinPublicServer(firstListing))
        assertEquals(firstListing.id, repo.selectedServerId.value)

        // Switch back to server_main
        repo.selectServer("server_main")

        // 2. Collaborative Voice Jukebox & Mixer + EQ
        val initialTitle = repo.jukeboxState.value.currentTrack?.title
        assertNotNull(initialTitle)
        repo.toggleJukeboxPlayPause()
        assertTrue(repo.jukeboxState.value.isPlaying)
        val customTrack = MusicTrack("trk_unit", "Neon Night", "Synth", 180, "")
        repo.addTrackToJukebox(customTrack)
        assertTrue(repo.jukeboxState.value.queue.any { it.title == "Neon Night" })
        repo.skipJukeboxTrack()
        assertNotEquals(initialTitle, repo.jukeboxState.value.currentTrack?.title)
        assertTrue(repo.voteSkipCurrentTrack()) // skips because currentUser added it

        repo.setParticipantVolume("user_thomas", 1.75f)
        assertEquals(1.75f, repo.participantVolumes.value["user_thomas"] ?: 0f, 0.001f)
        repo.setAudioEqPreset(AudioEqPreset.BASS_BOOST)
        assertEquals(AudioEqPreset.BASS_BOOST, repo.appSettings.value.audioEqPreset)

        // 3. Customization, Clan Tag & Gradient Roles
        repo.updateAppSettings {
            it.copy(
                accentColorHex = 0xFF00D4AA,
                chatDisplayStyle = ChatDisplayStyle.BUBBLES,
                glassmorphismEnabled = false
            )
        }
        assertEquals(0xFF00D4AA, repo.appSettings.value.accentColorHex)
        assertEquals(ChatDisplayStyle.BUBBLES, repo.appSettings.value.chatDisplayStyle)
        assertFalse(repo.appSettings.value.glassmorphismEnabled)

        repo.updateClanTag("devpro")
        assertEquals("DEVP", repo.currentUser.value.clanTag)

        repo.createCustomRole(
            serverId = "server_main",
            roleName = "Légende",
            colorHex = 0xFFFF5E62,
            isAdmin = false,
            canKickBan = false,
            canManageChannels = false,
            secondaryColorHex = 0xFFFF9966
        )
        val legendRole = repo.servers.value.first { it.id == "server_main" }.customRoles.first { it.name == "Légende" }
        assertEquals(0xFFFF9966, legendRole.secondaryColorHex)

        // 4. Leaderboard, Whiteboard & Server Shop
        val leaderboard = repo.getServerLeaderboard("server_main")
        assertTrue(leaderboard.isNotEmpty())
        assertTrue(leaderboard.first().second >= leaderboard.last().second)

        val stroke1 = WhiteboardStroke(listOf(0f to 0f, 50f to 50f), 0xFF5865F2, 6f)
        val stroke2 = WhiteboardStroke(listOf(10f to 10f, 60f to 60f), 0xFFEB459E, 8f)
        repo.addWhiteboardStroke("c_general", stroke1)
        repo.addWhiteboardStroke("c_general", stroke2)
        assertEquals(2, repo.whiteboardStrokes.value["c_general"]?.size)
        repo.undoWhiteboardStroke("c_general")
        assertEquals(1, repo.whiteboardStrokes.value["c_general"]?.size)
        repo.shareWhiteboardToChannel("c_general")
        assertTrue(repo.messages.value["c_general"]?.any { it.text.contains("Tableau blanc collaboratif") } == true)
        repo.clearWhiteboard("c_general")
        assertTrue(repo.whiteboardStrokes.value["c_general"].isNullOrEmpty())

        repo.createServerShopItem("server_main", "Champion Orbs", "Titre doré", 150, 0xFFFACC15, 0xFFEB459E)
        val createdItem = repo.servers.value.first { it.id == "server_main" }.shopItems.first { it.title == "Champion Orbs" }
        val orbsBeforeShop = repo.currentUser.value.orbsBalance
        assertTrue(repo.purchaseServerShopItem("server_main", createdItem.id))
        val purchasedItem = repo.servers.value.first { it.id == "server_main" }.shopItems.first { it.id == createdItem.id }
        assertTrue(repo.currentUser.value.id in purchasedItem.purchasedByUserIds)
        assertEquals(orbsBeforeShop - 150 + 50, repo.currentUser.value.orbsBalance)
        assertEquals("role_${createdItem.id}", repo.currentUser.value.customRoleId)

        // 5. Drafts, Scheduled Messages & Message Reminders
        repo.updateDraft("c_general", "Brouillon en cours...")
        assertEquals("Brouillon en cours...", repo.draftsByTarget.value["c_general"])
        repo.updateDraft("c_general", "")
        assertNull(repo.draftsByTarget.value["c_general"])

        val scheduled = repo.scheduleMessage("c_general", "#général", "Message programmé test", 60_000L, "Dans 1 min")
        assertNotNull(scheduled)
        assertEquals(1, repo.scheduledMessages.value.size)
        repo.sendScheduledMessageNow(scheduled!!.id)
        assertTrue(repo.scheduledMessages.value.isEmpty())
        assertTrue(repo.messages.value["c_general"]?.any { it.text == "Message programmé test" } == true)

        val scheduled2 = repo.scheduleMessage("c_general", "#général", "À annuler", 60_000L, "Dans 1 min")
        assertNotNull(scheduled2)
        repo.cancelScheduledMessage(scheduled2!!.id)
        assertTrue(repo.scheduledMessages.value.isEmpty())

        val targetMsg = repo.messages.value["c_general"]!!.first()
        val reminder = repo.scheduleMessageReminder(targetMsg, 60_000L, "Dans 1 min")
        assertTrue(repo.messageReminders.value.any { it.id == reminder.id })
    }
}
