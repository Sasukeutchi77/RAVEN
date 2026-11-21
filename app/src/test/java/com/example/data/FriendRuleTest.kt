package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.DirectMessagePrivacyPolicy
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationship
import com.example.model.FriendRequestPolicy
import com.example.model.User
import com.example.model.UserStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class FriendRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice_friends_rule@test.raven.local"
        const val BOB_EMAIL = "bob_friends_rule@test.raven.local"
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun friendLifecyclePrivacyAndOwnerIsolation_areEnforcedByFirestoreRules() = kotlinx.coroutines.runBlocking {
        // 1. Alice sets up her public profile with EVERYONE allowed
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceUser = User(id = aliceUid, name = "Alice", tag = "#1111", status = UserStatus.ONLINE)
        withTimeout(TIMEOUT_MS) {
            firestore.collection("public_profiles").document(aliceUid).set(
                mapOf(
                    "userId" to aliceUid,
                    "name" to "Alice",
                    "tag" to "#1111",
                    "isPublic" to true,
                    "friendRequestPolicy" to FriendRequestPolicy.EVERYONE.name,
                    "allowDmsFrom" to DirectMessagePrivacyPolicy.FRIENDS_ONLY.name,
                    "showOnlineStatus" to true,
                    "showLastActivity" to true
                )
            ).await()
        }

        // 2. Bob sets up his public profile (EVERYONE initially)
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobUser = User(id = bobUid, name = "Bob", tag = "#2222", status = UserStatus.ONLINE)
        withTimeout(TIMEOUT_MS) {
            firestore.collection("public_profiles").document(bobUid).set(
                mapOf(
                    "userId" to bobUid,
                    "name" to "Bob",
                    "tag" to "#2222",
                    "isPublic" to true,
                    "friendRequestPolicy" to FriendRequestPolicy.EVERYONE.name,
                    "allowDmsFrom" to DirectMessagePrivacyPolicy.EVERYONE.name,
                    "showOnlineStatus" to true,
                    "showLastActivity" to true
                )
            ).await()
        }

        // 3. Alice's DM policy is FRIENDS_ONLY -> Bob cannot create DM with Alice before being friends
        val pairId = FriendFirestoreSync.canonicalPairId(aliceUid, bobUid)
        val dmId = FriendFirestoreSync.canonicalDmId(aliceUid, bobUid)
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("direct_conversations").document(dmId).set(
                    mapOf(
                        "id" to dmId,
                        "participantIds" to listOf(aliceUid, bobUid).sorted(),
                        "dmPairKey" to pairId,
                        "isGroup" to false,
                        "lastMessage" to "Salut non-ami"
                    )
                ).await()
            }
            fail("Non-friend DM create must be denied when recipient has FRIENDS_ONLY")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 4. Bob sends canonical friend_request to Alice -> succeeds; Bob CANNOT accept his own request
        val reqMap = FriendFirestoreSync.requestToMap(fromUser = bobUser, toUser = aliceUser)
        withTimeout(TIMEOUT_MS) {
            firestore.collection("friend_requests").document(pairId).set(reqMap).await()
        }
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("friend_requests").document(pairId)
                    .update("status", "ACCEPTED").await()
            }
            fail("Sender cannot accept their own friend request")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 5. Bob CANNOT write into Alice's /users/{aliceUid}/friends list
        val fakeFriendOnAlice = FriendFirestoreSync.friendToMap(
            FriendRelationship(friendUid = bobUid, pairId = pairId, user = bobUser)
        )
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("friends").document(bobUid)
                    .set(fakeFriendOnAlice).await()
            }
            fail("Bob must NEVER be able to write Alice's friend list")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 6. Alice signs back in, accepts friend request, writes ONLY her own /friends/{bobUid}
        signInTestUser(ALICE_EMAIL)
        withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("friends").document(bobUid)
                .set(
                    FriendFirestoreSync.friendToMap(
                        FriendRelationship(friendUid = bobUid, pairId = pairId, user = bobUser)
                    )
                ).await()
            firestore.collection("friend_requests").document(pairId)
                .update("status", "ACCEPTED").await()
        }

        // 7. Bob signs in, materializes his own /users/{bobUid}/friends/{aliceUid}, and can now open coherent DM
        signInTestUser(BOB_EMAIL)
        withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(bobUid)
                .collection("friends").document(aliceUid)
                .set(
                    FriendFirestoreSync.friendToMap(
                        FriendRelationship(friendUid = aliceUid, pairId = pairId, user = aliceUser)
                    )
                ).await()
            firestore.collection("direct_conversations").document(dmId).set(
                mapOf(
                    "id" to dmId,
                    "participantIds" to listOf(aliceUid, bobUid).sorted(),
                    "dmPairKey" to pairId,
                    "isGroup" to false,
                    "lastMessage" to "Coucou Alice !"
                )
            ).await()
        }

        // 8. Bob blocks Alice -> Alice CANNOT send DM message or view Bob's blocked list
        withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(bobUid)
                .collection("blocked_users").document(aliceUid)
                .set(FriendFirestoreSync.blockedToMap(bobUid, aliceUser)).await()
            firestore.collection("users").document(bobUid)
                .collection("friends").document(aliceUid)
                .delete().await()
        }

        signInTestUser(ALICE_EMAIL)
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(bobUid)
                    .collection("blocked_users").document(aliceUid)
                    .get().await()
            }
            fail("Alice cannot inspect Bob's blocked_users list")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("direct_conversations").document(dmId)
                    .collection("messages").document("msg_blocked_1")
                    .set(
                        mapOf(
                            "senderId" to aliceUid,
                            "senderName" to "Alice",
                            "text" to "Je suis bloquée",
                            "participantIds" to listOf(aliceUid, bobUid).sorted()
                        )
                    ).await()
            }
            fail("Alice cannot send DM messages while blocked by Bob")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
