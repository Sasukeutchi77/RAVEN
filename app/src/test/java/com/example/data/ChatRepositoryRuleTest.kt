package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.MusicTrack
import com.example.model.WhiteboardStroke
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private suspend fun awaitCondition(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!block()) {
                delay(100)
            }
        }
    }

    @Test
    fun syncFirebaseUser_writesOwnerProfileAndPublicProfile_andIsolatesPii() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(
            scope = repoScope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        try {
            repo.updateUserProfile(name = "Alice Raven", bio = "Bio Cloud Test", email = ALICE_EMAIL)
            repo.updateClanTag("RVN")

            awaitCondition {
                val userDoc = firestore.collection("users").document(aliceUid).get().await()
                val pubDoc = firestore.collection("public_profiles").document(aliceUid).get().await()
                userDoc.exists() && userDoc.getString("bio") == "Bio Cloud Test" &&
                    pubDoc.exists() && pubDoc.getBoolean("isPublic") == true
            }

            // Switch to Bob: reading Alice's private user document must fail with PERMISSION_DENIED
            signInTestUser(BOB_EMAIL)
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    firestore.collection("users").document(aliceUid).get().await()
                }
                fail("Expected PERMISSION_DENIED when Bob reads Alice private profile")
            } catch (e: Exception) {
                val fsEx = e.unwrapFirestoreException()
                assertNotNull(fsEx)
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
            }

            // Bob CAN query public_profiles with aligned filter
            val pubQuery = withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("public_profiles").whereEqualTo("isPublic", true).get().await()
            }
            assertTrue(pubQuery.documents.any { it.id == aliceUid && it.getString("name") == "Alice Raven" })
        } finally {
            repo.releaseResources()
            repoScope.cancel()
        }
    }

    @Test
    fun publicServerDiscoveryAndJoin_allowsMemberJoinAndRejectsPrivateAccess() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val pubServerId = "pub_${UUID.randomUUID().toString().take(8)}"
        val privServerId = "priv_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("servers").document(pubServerId).set(
                mapOf(
                    "id" to pubServerId,
                    "name" to "Raven Open",
                    "ownerId" to aliceUid,
                    "isPublic" to true,
                    "memberIds" to listOf(aliceUid),
                    "inviteCode" to "RAVEN-OPEN",
                    "boostCount" to 0,
                    "boostLevel" to 0
                )
            ).await()
            firestore.collection("servers").document(privServerId).set(
                mapOf(
                    "id" to privServerId,
                    "name" to "Raven Secret",
                    "ownerId" to aliceUid,
                    "isPublic" to false,
                    "memberIds" to listOf(aliceUid),
                    "inviteCode" to "RAVEN-PRIV",
                    "boostCount" to 0,
                    "boostLevel" to 0
                )
            ).await()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        // Bob queries public servers
        val pubSnap = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("servers").whereEqualTo("isPublic", true).get().await()
        }
        assertTrue(pubSnap.documents.any { it.id == pubServerId })

        // Bob joins public server cleanly
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("servers").document(pubServerId)
                .update(
                    mapOf(
                        "memberIds" to FieldValue.arrayUnion(bobUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }

        // Bob cannot read private server
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("servers").document(privServerId).get().await()
            }
            fail("Expected PERMISSION_DENIED on private server read by non-member")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun repositoryMessagesWhiteboardAndJukebox_syncAndRespectSecurityRules() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(
            scope = repoScope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        try {
            val srvName = "Guild-${UUID.randomUUID().toString().take(6)}"
            repo.createServer(srvName, "GD", isPublic = false, categoryTag = "Dev")
            val createdServer = repo.servers.value.first { it.name == srvName }
            val textChannel = createdServer.channels.first()

            // Post message, whiteboard stroke, and jukebox track via repository
            repo.sendMessage(textChannel.id, "Message cloud securise")
            repo.addWhiteboardStroke(textChannel.id, WhiteboardStroke(listOf(0f to 0f, 25f to 40f), 0xFF5865F2, 5f))
            repo.joinVoiceChannel(createdServer.channels.last())
            repo.addTrackToJukebox(MusicTrack("trk_rule", "Rule Synth", "Raven", 120, ""))

            val msgCol = firestore.collection("servers").document(createdServer.id)
                .collection("channels").document(textChannel.id)
                .collection("messages")
            val wbCol = firestore.collection("servers").document(createdServer.id)
                .collection("channels").document(textChannel.id)
                .collection("whiteboard")
            val jbCol = firestore.collection("servers").document(createdServer.id)
                .collection("channels").document(createdServer.channels.last().id)
                .collection("jukebox")

            awaitCondition(timeoutMs = 8000L) {
                val msgs = msgCol.whereArrayContains("memberIds", aliceUid).get().await()
                val wb = wbCol.whereArrayContains("memberIds", aliceUid).get().await()
                val jb = jbCol.whereArrayContains("memberIds", aliceUid).get().await()
                !msgs.isEmpty && !wb.isEmpty && !jb.isEmpty
            }

            // Charlie (non-member) must get PERMISSION_DENIED on direct read & unfiltered list
            signInTestUser(CHARLIE_EMAIL)
            try {
                withTimeout(DEFAULT_TIMEOUT_MS) {
                    msgCol.get().await()
                }
                fail("Expected PERMISSION_DENIED for non-member unfiltered query")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }
        } finally {
            repo.releaseResources()
            repoScope.cancel()
        }
    }

    @Test
    fun unauthenticatedUser_accessingProtectedCollections_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("public_profiles").whereEqualTo("isPublic", true).get().await()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated public_profiles query")
        } catch (e: Exception) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.raven@test.com"
        const val BOB_EMAIL = "bob.raven@test.com"
        const val CHARLIE_EMAIL = "charlie.raven@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
