package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.ChannelType
import com.example.model.PublicServerListing
import com.example.model.RolePermission
import com.example.model.UserRole
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CommunityRuleTest : FirestoreEmulatorTestBase() {

    private fun newRepo(scope: CoroutineScope): Pair<ChatRepository, CommunityRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        return chatRepo to CommunityRepository(chatRepo)
    }

    @Test
    fun fullCommunityLifecycle_enforcePermissionsOnFirestoreServer() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (aliceChat, aliceComm) = newRepo(aliceScope)
        val (bobChat, bobComm) = newRepo(bobScope)

        try {
            // 1. Alice creates a PRIVATE community with unique @handle
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val suffix = UUID.randomUUID().toString().take(6)
            val handle = "guild-$suffix"
            val created = aliceComm.createCommunity(
                name = "Guilde Elite $suffix",
                description = "Communauté privée test règles",
                uniqueHandleRaw = handle,
                imageUrl = "https://example.com/img.png",
                bannerUrl = "https://example.com/banner.png",
                isPublic = false,
                categoryTag = "Gaming",
                rulesGuidelines = "Respect total",
                homeWelcomeMessage = "Bienvenue"
            ).getOrThrow()

            // Verify Firestore documents exist
            val serverSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id).get().await()
            }
            assertTrue(serverSnap.exists())
            assertEquals(aliceUid, serverSnap.getString("ownerId"))
            assertEquals(handle, serverSnap.getString("uniqueHandle"))

            // Alice publishes announcement, creates event, creates category & channel, generates single-use invite
            assertTrue(aliceComm.publishAnnouncement(created.id, "Annonce 1", "Contenu officiel", true).isSuccess)
            val evt = aliceComm.createCommunityEvent(created.id, "Raid", "Boss", "21h", "Vocal").getOrThrow()
            assertTrue(aliceComm.createCategory(created.id, "VIP").isSuccess)
            val vipCh = aliceComm.createChannel(created.id, "salon-vip", ChannelType.TEXT, "VIP").getOrThrow()
            val validInvite = aliceComm.createInvite(created.id, maxUses = 5, expiresInHours = 24).getOrThrow()
            val revokedInvite = aliceComm.createInvite(created.id, maxUses = 5, expiresInHours = 24).getOrThrow()
            assertTrue(aliceComm.revokeInvite(created.id, revokedInvite.code).isSuccess)

            // 2. Switch to Bob (normal non-member user):
            val bobUid = signInTestUser(BOB_EMAIL)

            // Bob CANNOT read private server directly before joining
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).get().await()
                }
                fail("Non-member Bob must not read private server")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob CANNOT join with revoked invite (rejected both in repo AND by Firestore server rule if called directly)
            val revokedJoin = bobComm.joinByInviteCode(revokedInvite.code)
            assertTrue(revokedJoin.isFailure)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).update(
                        mapOf(
                            "memberIds" to FieldValue.arrayUnion(bobUid),
                            "lastInviteCodeUsed" to revokedInvite.code,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                fail("Revoked invite must be rejected by Firestore rules")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob CAN join with the valid non-revoked invite code
            val bobJoined = bobComm.joinByInviteCode(validInvite.code)
            assertTrue("Bob should join via valid invite: ${bobJoined.exceptionOrNull()?.message}", bobJoined.isSuccess)

            // 3. Server-side permission denials for normal member Bob:
            // Bob CANNOT modify community name/metadata on Firestore
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).update("name", "Hacked Name").await()
                }
                fail("Normal member Bob must not modify community metadata")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob CANNOT escalate himself in adminIds or member role doc
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .update("adminIds", FieldValue.arrayUnion(bobUid)).await()
                }
                fail("Normal member Bob must not escalate himself to adminIds")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .collection("members").document(bobUid)
                        .update("role", "ADMIN").await()
                }
                fail("Normal member Bob must not escalate his member role to ADMIN")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob CANNOT publish announcements, create invites, create channels, or delete server
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .collection("announcements").document("fake_ann").set(
                            mapOf(
                                "id" to "fake_ann",
                                "serverId" to created.id,
                                "title" to "Fake",
                                "content" to "Unauthorized",
                                "authorId" to bobUid,
                                "isPublic" to false,
                                "memberIds" to listOf(aliceUid, bobUid)
                            )
                        ).await()
                }
                fail("Normal member Bob must not publish announcements")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("community_invites").document("FAKE-CODE").set(
                        mapOf(
                            "code" to "FAKE-CODE",
                            "serverId" to created.id,
                            "createdByUid" to bobUid,
                            "isRevoked" to false,
                            "maxUses" to 0,
                            "usesCount" to 0,
                            "isPublic" to false
                        )
                    ).await()
                }
                fail("Normal member Bob must not create community invites")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).delete().await()
                }
                fail("Normal member Bob must not delete community")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob CAN RSVP to an event and CAN leave the community cleanly
            withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id)
                    .collection("events").document(evt.id)
                    .update(mapOf("interestedUserIds" to listOf(aliceUid, bobUid))).await()
            }
            assertTrue(bobComm.leaveCommunity(created.id).isSuccess)

            // 4. Switch back to Alice: Alice updates community, deletes channel, promotes Bob as MODERATOR / ADMIN, then deletes
            signInTestUser(ALICE_EMAIL)
            val reAddBob = bobComm // Alice re-invites Bob first via public join after updating to public
            assertTrue(
                aliceComm.updateCommunity(
                    serverId = created.id,
                    name = "Guilde Publique $suffix",
                    description = "Ouverte à tous",
                    uniqueHandleRaw = handle,
                    imageUrl = null,
                    bannerUrl = null,
                    bannerColorHex = 0xFF5865F2L,
                    isPublic = true,
                    categoryTag = "Communauté",
                    rulesGuidelines = "Nouvelles règles",
                    homeWelcomeMessage = "Bienvenue !"
                ).isSuccess
            )
            // Promote Bob -> MODERATOR: mod can create channel, CANNOT delete channel
            assertTrue(aliceComm.assignMemberRole(created.id, bobUid, UserRole.MODERATOR, null).isSuccess)
            signInTestUser(BOB_EMAIL)
            withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id)
                    .collection("channels").document("mod_ch").set(
                        mapOf(
                            "id" to "mod_ch",
                            "serverId" to created.id,
                            "name" to "salon-mod",
                            "type" to "TEXT",
                            "isPublic" to true,
                            "memberIds" to listOf(aliceUid, bobUid)
                        )
                    ).await()
            }
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id)
                        .collection("channels").document(vipCh.id).delete().await()
                }
                fail("Moderator Bob must not delete channel (admin-only)")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Even if Bob is promoted to ADMIN, Bob is NOT owner so Bob CANNOT delete the community itself
            signInTestUser(ALICE_EMAIL)
            assertTrue(aliceComm.assignMemberRole(created.id, bobUid, UserRole.ADMIN, null).isSuccess)
            signInTestUser(BOB_EMAIL)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(created.id).delete().await()
                }
                fail("Non-owner admin Bob must not delete community")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Owner Alice CAN delete channel and delete community + free @handle
            signInTestUser(ALICE_EMAIL)
            assertTrue(aliceComm.deleteChannel(created.id, vipCh.id).isSuccess)
            assertTrue(aliceComm.deleteCommunity(created.id).isSuccess)
            val deletedDoc = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(created.id).get().await()
            }
            assertFalse(deletedDoc.exists())
        } finally {
            aliceChat.releaseResources()
            bobChat.releaseResources()
            aliceScope.cancel()
            bobScope.cancel()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.comm@test.com"
        const val BOB_EMAIL = "bob.comm@test.com"
        const val TIMEOUT_MS = 5000L
    }
}
