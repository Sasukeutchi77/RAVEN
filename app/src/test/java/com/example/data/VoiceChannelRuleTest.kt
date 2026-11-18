package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.model.UserRole
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

class VoiceChannelRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice.voice@raven.test"
        const val BOB_EMAIL = "bob.voice@raven.test"
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun voiceChannelLockAndKickRules_enforcedOnFirestoreEmulator() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val aliceChat = ChatRepository(
            scope = aliceScope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        try {
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val aliceComm = CommunityRepository(aliceChat)
            val suffix = UUID.randomUUID().toString().take(6)
            val srv = aliceComm.createCommunity(
                name = "Voice Comm $suffix",
                description = "Tests vocaux",
                uniqueHandleRaw = "voice-comm-$suffix",
                isPublic = true
            ).getOrThrow()

            val voiceChannel = srv.channels.first { it.type == ChannelType.VOICE }
            aliceChat.updateVoiceChannelSettings(
                serverId = srv.id,
                channelId = voiceChannel.id,
                isLocked = false,
                maxParticipants = 5,
                allowedRoles = emptyList()
            )
            // Persist channel doc to emulator explicitly before non-owner joins
            CommunityFirestoreSync.pushChannelSafe(
                db = firestore,
                serverId = srv.id,
                channel = voiceChannel.copy(isLocked = false, maxParticipants = 5),
                isPublic = true,
                memberIds = listOf(aliceUid)
            )

            // Bob signs in, joins community, joins unlocked voice_peers successfully
            auth.signOut()
            val bobUid = signInTestUser(BOB_EMAIL)
            val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val bobChat = ChatRepository(bobScope, databaseId, injectedFirestore = firestore, injectedAuth = auth)
            val bobComm = CommunityRepository(bobChat)
            try {
                bobComm.joinPublicCommunity(CommunityPermissions.serverToPublicListing(srv)).getOrThrow()
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(srv.id)
                        .collection("channels").document(voiceChannel.id)
                        .collection("voice_peers").document(bobUid)
                        .set(
                            mapOf(
                                "uid" to bobUid,
                                "displayName" to "Bob",
                                "memberIds" to listOf(aliceUid, bobUid),
                                "isPublic" to true,
                                "isMuted" to false,
                                "isSpeaking" to true,
                                "connectionState" to "CONNECTED"
                            )
                        ).await()
                }

                // Bob cannot lock the channel himself (permission denied on Firestore)
                var bobLockRejected = false
                try {
                    withTimeout(TIMEOUT_MS) {
                        firestore.collection("servers").document(srv.id)
                            .collection("channels").document(voiceChannel.id)
                            .update("isLocked", true).await()
                    }
                } catch (e: FirebaseFirestoreException) {
                    bobLockRejected = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                }
                assertTrue("Regular member must not lock voice channel", bobLockRejected)

                // Alice (Owner) locks the channel and kicks Bob
                auth.signOut()
                signInTestUser(ALICE_EMAIL)
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(srv.id)
                        .collection("channels").document(voiceChannel.id)
                        .update(
                            mapOf(
                                "isLocked" to true,
                                "kickedVoiceUids" to listOf(bobUid)
                            )
                        ).await()
                    firestore.collection("servers").document(srv.id)
                        .collection("channels").document(voiceChannel.id)
                        .collection("voice_peers").document(bobUid)
                        .delete().await()
                }

                // Bob cannot write back into locked/kicked voice_peers
                auth.signOut()
                signInTestUser(BOB_EMAIL)
                var bobRejoinRejected = false
                try {
                    withTimeout(TIMEOUT_MS) {
                        firestore.collection("servers").document(srv.id)
                            .collection("channels").document(voiceChannel.id)
                            .collection("voice_peers").document(bobUid)
                            .set(
                                mapOf(
                                    "uid" to bobUid,
                                    "displayName" to "Bob",
                                    "memberIds" to listOf(aliceUid, bobUid),
                                    "isPublic" to true
                                )
                            ).await()
                    }
                } catch (e: FirebaseFirestoreException) {
                    bobRejoinRejected = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                }
                assertTrue("Bob must be rejected from locked/kicked voice_peers", bobRejoinRejected)
            } finally {
                bobScope.cancel()
            }
        } finally {
            aliceScope.cancel()
        }
    }
}
