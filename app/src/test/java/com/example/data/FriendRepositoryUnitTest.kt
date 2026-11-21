package com.example.data

import com.example.model.DirectMessagePrivacyPolicy
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationStatus
import com.example.model.FriendRequestDirection
import com.example.model.FriendRequestPolicy
import com.example.model.NotificationType
import com.example.model.User
import com.example.model.UserDirectoryEntry
import com.example.model.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class FriendRepositoryUnitTest {

    private fun newContext(): Triple<CoroutineScope, ChatRepository, FriendRepository> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val chatRepo = ChatRepository(scope = scope)
        val friendRepo = FriendRepository(chatRepo = chatRepo)
        return Triple(scope, chatRepo, friendRepo)
    }

    @Test
    fun sendAcceptDeclineCancelAndDuplicatePrevention_workDeterministically() {
        val (scope, chatRepo, friendRepo) = newContext()
        try {
            val clara = User(
                id = "user_clara_99",
                name = "Clara",
                tag = "#8842",
                avatarColor = 0xFF57F287L,
                status = UserStatus.ONLINE,
                customStatus = "En vocal",
                lastActiveAtMs = System.currentTimeMillis() - 30_000L,
                lastActivityLabel = "Actif à l'instant"
            )
            friendRepo.registerDirectoryEntries(
                listOf(
                    UserDirectoryEntry(
                        user = clara,
                        friendRequestPolicy = FriendRequestPolicy.EVERYONE,
                        allowDmsFrom = DirectMessagePrivacyPolicy.EVERYONE
                    )
                )
            )

            // 1. Self-request is rejected
            assertFalse(friendRepo.sendFriendRequestToUser(chatRepo.currentUser.value))
            assertNotNull(friendRepo.errorMessage.value)

            // 2. Send valid friend request -> creates canonical pairId request and notification
            assertTrue(friendRepo.sendFriendRequestToUser(clara))
            val pendingAfterSend = chatRepo.friendRequests.value.filter { it.user.id == clara.id }
            assertEquals(1, pendingAfterSend.size)
            assertEquals(FriendRequestDirection.SENT, pendingAfterSend.first().direction)
            assertTrue(
                chatRepo.notifications.value.any {
                    it.type == NotificationType.FRIEND_REQUEST && it.body.contains("Clara")
                }
            )

            // 3. Duplicate outgoing request to same user is rejected
            assertFalse(friendRepo.sendFriendRequestToUser(clara))
            assertEquals(1, chatRepo.friendRequests.value.count { it.user.id == clara.id })

            // 4. Sender CANNOT accept their own outgoing request; sender CAN cancel it
            val sentId = pendingAfterSend.first().id
            assertFalse(friendRepo.acceptFriendRequest(sentId))
            assertTrue(friendRepo.cancelFriendRequest(sentId))
            assertTrue(chatRepo.friendRequests.value.none { it.user.id == clara.id })

            // 5. Mutual incoming request -> auto-accepts when current user sends request
            val leo = User(id = "user_leo_mutual", name = "Léo", tag = "#7654", status = UserStatus.ONLINE)
            val pairLeo = FriendFirestoreSync.canonicalPairId(chatRepo.currentUser.value.id, leo.id)
            chatRepo.friendRequestsMutableFlow().value = chatRepo.friendRequestsMutableFlow().value + com.example.model.FriendRequest(
                id = pairLeo,
                user = leo,
                direction = FriendRequestDirection.RECEIVED,
                timestamp = "12:00",
                pairId = pairLeo,
                fromUid = leo.id,
                toUid = chatRepo.currentUser.value.id
            )
            assertTrue(friendRepo.sendFriendRequestToUser(leo))
            assertTrue(friendRepo.friends.value.any { it.friendUid == leo.id })
            assertTrue(chatRepo.friendRequests.value.none { it.user.id == leo.id })

            // 6. Standard received request accept & decline
            val nina = User(id = "user_nina", name = "Nina", tag = "#2222")
            val pairNina = FriendFirestoreSync.canonicalPairId(chatRepo.currentUser.value.id, nina.id)
            chatRepo.friendRequestsMutableFlow().value = chatRepo.friendRequestsMutableFlow().value + com.example.model.FriendRequest(
                id = pairNina,
                user = nina,
                direction = FriendRequestDirection.RECEIVED,
                timestamp = "12:05",
                pairId = pairNina,
                fromUid = nina.id,
                toUid = chatRepo.currentUser.value.id
            )
            assertTrue(friendRepo.declineFriendRequest(pairNina))
            assertFalse(friendRepo.friends.value.any { it.friendUid == nina.id })
            assertTrue(chatRepo.friendRequests.value.none { it.user.id == nina.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun removeBlockUnblockAndPrivateNotes_maintainConsistentState() {
        val (scope, chatRepo, friendRepo) = newContext()
        try {
            // Mock friend Thomas already exists with dm_thomas
            assertTrue(friendRepo.friends.value.any { it.friendUid == "user_thomas" })
            assertTrue(chatRepo.directConversations.value.any { it.id == "dm_thomas" })

            // Update private note for Thomas
            friendRepo.updatePrivateNote("user_thomas", "Coéquipier Rocket League")
            assertEquals("Coéquipier Rocket League", chatRepo.privateUserNotes.value["user_thomas"])
            assertEquals(
                "Coéquipier Rocket League",
                friendRepo.friends.value.first { it.friendUid == "user_thomas" }.privateNote
            )

            // Block Emma -> removes from friends, hides 1-on-1 DM, prevents requests/DMs
            val emma = friendRepo.friends.value.first { it.friendUid == "user_emma" }.user
            friendRepo.blockUser(emma)
            assertFalse(friendRepo.friends.value.any { it.friendUid == "user_emma" })
            assertTrue(chatRepo.blockedUsers.value.any { it.id == "user_emma" })
            assertTrue(chatRepo.directConversations.value.none { !it.isGroup && it.friend.id == "user_emma" })
            assertFalse(friendRepo.sendFriendRequestToUser(emma))
            assertNull(friendRepo.openOrCreateDirectConversation(emma))

            // Unblock Emma -> no longer blocked, can request again
            friendRepo.unblockUser("user_emma")
            assertFalse(chatRepo.blockedUsers.value.any { it.id == "user_emma" })
            assertTrue(friendRepo.sendFriendRequestToUser(emma))

            // Remove Thomas -> removed from friends cleanly
            friendRepo.removeFriend("user_thomas")
            assertFalse(friendRepo.friends.value.any { it.friendUid == "user_thomas" })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun coherentDirectConversations_reuseExistingIdsWithoutDuplicatesAndRespectPrivacy() {
        val (scope, chatRepo, friendRepo) = newContext()
        try {
            // Opening DM with existing friend Thomas must reuse dm_thomas without adding a duplicate entry
            val initialDmCount = chatRepo.directConversations.value.size
            val thomas = friendRepo.friends.value.first { it.friendUid == "user_thomas" }.user
            val reused = friendRepo.openOrCreateDirectConversation(thomas)
            assertNotNull(reused)
            assertEquals("dm_thomas", reused?.id)
            assertEquals(initialDmCount, chatRepo.directConversations.value.size)

            // Stranger with FRIENDS_ONLY DM policy cannot be DMed until becoming a friend
            val privateUser = User(id = "user_priv_dm", name = "Soren", tag = "#4040", status = UserStatus.ONLINE)
            friendRepo.registerDirectoryEntries(
                listOf(
                    UserDirectoryEntry(
                        user = privateUser,
                        friendRequestPolicy = FriendRequestPolicy.EVERYONE,
                        allowDmsFrom = DirectMessagePrivacyPolicy.FRIENDS_ONLY
                    )
                )
            )
            assertNull(friendRepo.openOrCreateDirectConversation(privateUser))
            assertNotNull(friendRepo.errorMessage.value)

            // Same user with EVERYONE creates canonical dm_<pairId> once and never duplicates
            friendRepo.registerDirectoryEntries(
                listOf(
                    UserDirectoryEntry(
                        user = privateUser,
                        friendRequestPolicy = FriendRequestPolicy.EVERYONE,
                        allowDmsFrom = DirectMessagePrivacyPolicy.EVERYONE
                    )
                )
            )
            val created1 = friendRepo.openOrCreateDirectConversation(privateUser)
            val created2 = friendRepo.openOrCreateDirectConversation(privateUser)
            assertNotNull(created1)
            assertEquals(created1?.id, created2?.id)
            assertEquals(1, chatRepo.directConversations.value.count { !it.isGroup && it.friend.id == privateUser.id })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun privacyPoliciesAndUserDirectorySearch_filterMutualServersOnlineAndLastActivity() {
        val (scope, chatRepo, friendRepo) = newContext()
        try {
            val nobodyUser = User(id = "user_nobody", name = "Hermit", tag = "#0000", status = UserStatus.ONLINE)
            val mutualOnlyUser = User(id = "user_mutual_only", name = "GuildMate", tag = "#7777", status = UserStatus.IDLE)
            friendRepo.registerDirectoryEntries(
                listOf(
                    UserDirectoryEntry(
                        user = nobodyUser,
                        friendRequestPolicy = FriendRequestPolicy.NOBODY,
                        showOnlineStatus = false,
                        showLastActivity = false,
                        lastActiveAtMs = 0L,
                        lastActivityLabel = ""
                    ),
                    UserDirectoryEntry(
                        user = mutualOnlyUser,
                        friendRequestPolicy = FriendRequestPolicy.MUTUAL_SERVERS,
                        showOnlineStatus = true,
                        showLastActivity = true,
                        lastActiveAtMs = System.currentTimeMillis() - 120_000L,
                        lastActivityLabel = "Dans Raven Dev"
                    )
                )
            )

            // NOBODY blocks friend requests
            assertFalse(friendRepo.sendFriendRequestToUser(nobodyUser))
            assertTrue(friendRepo.errorMessage.value!!.contains("aucune demande"))

            // MUTUAL_SERVERS blocked if no common server
            assertFalse(friendRepo.sendFriendRequestToUser(mutualOnlyUser))
            assertTrue(friendRepo.errorMessage.value!!.contains("serveur commun"))

            // Relative activity formatting & masking check
            val now = 1_700_000_000_000L
            assertEquals("Activité masquée", FriendFirestoreSync.formatRelativeActivityTime(0L, now))
            assertEquals("Actif à l'instant", FriendFirestoreSync.formatRelativeActivityTime(now - 20_000L, now))
            assertEquals("Actif il y a 5 min", FriendFirestoreSync.formatRelativeActivityTime(now - 300_000L, now))

            // Directory search finds registered users and known friends
            val hits = friendRepo.searchUsers("GuildMate")
            assertEquals(1, hits.size)
            assertEquals("user_mutual_only", hits.first().user.id)
            assertEquals(FriendRelationStatus.NONE, hits.first().relationStatus)

            val thomasHit = friendRepo.searchUsers("#1234")
            assertTrue(thomasHit.any { it.user.id == "user_thomas" && it.relationStatus == FriendRelationStatus.FRIENDS })

            // Updating own friend privacy updates AppSettings cleanly
            friendRepo.updatePrivacySettings(
                FriendPrivacySettings(
                    friendRequestPolicy = FriendRequestPolicy.MUTUAL_SERVERS,
                    allowDmsFrom = DirectMessagePrivacyPolicy.FRIENDS_ONLY,
                    showOnlineStatus = false,
                    showLastActivity = false,
                    notifyFriendRequests = false
                )
            )
            assertFalse(chatRepo.appSettings.value.allowMemberDms)
            assertFalse(chatRepo.appSettings.value.showActivityStatus)
        } finally {
            scope.cancel()
        }
    }
}
