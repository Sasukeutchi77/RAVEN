package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.BotPermission
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

class BotRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice.bots@raven.test"
        const val BOB_EMAIL = "bob.bots@raven.test"
        const val TIMEOUT_MS = 10_000L
    }

    private fun newEnv(scope: CoroutineScope): Triple<ChatRepository, CommunityRepository, BotRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        val commRepo = CommunityRepository(chatRepo)
        val botRepo = BotRepository(chatRepo)
        botRepo.attachToChatRepository()
        return Triple(chatRepo, commRepo, botRepo)
    }

    @Test
    fun fullBotLifecycle_enforcesTokenSecurityOwnershipAndServerPermissionsOnFirestore() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (aliceChat, aliceComm, aliceBotRepo) = newEnv(aliceScope)
        val (_, _, _) = newEnv(bobScope)

        try {
            // 1. Alice signs in, creates a community + a private bot
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val suffix = UUID.randomUUID().toString().take(6)
            val srv = aliceComm.createCommunity(
                name = "Bot Lab $suffix",
                description = "Serveur de test bots",
                uniqueHandleRaw = "bot-lab-$suffix",
                isPublic = true
            ).getOrThrow()

            val created = aliceBotRepo.createBot(
                name = "GuardBot $suffix",
                handleRaw = "guard-$suffix",
                description = "Protection serveur",
                isPublicCatalog = false
            ).getOrThrow()
            val bot = created.bot
            val rawToken = created.oneTimePlaintextToken

            // Verify Firestore /bots doc has NO plaintext token or tokenHash
            val botSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("bots").document(bot.id).get().await()
            }
            assertTrue(botSnap.exists())
            assertEquals(aliceUid, botSnap.getString("ownerUid"))
            assertNull(botSnap.getString("token"))
            assertNull(botSnap.getString("tokenHash"))

            // Verify secret doc has SHA-256 hash and authenticates rawToken
            val secretSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("bots").document(bot.id)
                    .collection("secrets").document("token_meta").get().await()
            }
            assertTrue(secretSnap.exists())
            assertEquals(64, secretSnap.getString("tokenHash")?.length)
            assertTrue(aliceBotRepo.verifyBotToken(bot.id, rawToken).getOrThrow())

            // Token regeneration updates version & invalidates old token on Firestore
            val regen = aliceBotRepo.regenerateBotToken(bot.id).getOrThrow()
            assertEquals(2, regen.bot.tokenVersion)
            assertFalse(aliceBotRepo.verifyBotToken(bot.id, rawToken).getOrThrow())
            assertTrue(aliceBotRepo.verifyBotToken(bot.id, regen.oneTimePlaintextToken).getOrThrow())

            // Alice installs bot onto srv, and verified bot slash command writes bot message to Firestore
            aliceBotRepo.addBotToServer(
                botId = bot.id,
                serverId = srv.id,
                grantedPermissions = setOf(BotPermission.SEND_MESSAGES, BotPermission.USE_SLASH_COMMANDS),
                assignedRoleTitle = "Gardien Bot"
            ).getOrThrow()

            val installSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(srv.id)
                    .collection("bots").document(bot.id).get().await()
            }
            assertTrue(installSnap.exists())

            val chId = srv.channels.first { !it.isReadOnly }.id
            assertTrue(aliceChat.executeSlashCommand(chId, "/ping"))

            // 2. Switch to Bob (normal non-owner user) and join Alice's public community as MEMBER
            val bobUid = signInTestUser(BOB_EMAIL)
            withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(srv.id)
                    .update("memberIds", com.google.firebase.firestore.FieldValue.arrayUnion(bobUid))
                    .await()
            }

            // Bob CANNOT read Alice's private bot, CANNOT read /secrets, CANNOT hijack ownership or delete Alice's bot
            val secretReadByBob = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("bots").document(bot.id)
                        .collection("secrets").document("token_meta").get().await()
                }
            }
            assertTrue(secretReadByBob.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                secretReadByBob.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            val botHijackByBob = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("bots").document(bot.id)
                        .update(mapOf("ownerUid" to bobUid, "name" to "Hijacked"))
                        .await()
                }
            }
            assertTrue(botHijackByBob.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                botHijackByBob.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            val botDeleteByBob = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("bots").document(bot.id).delete().await()
                }
            }
            assertTrue(botDeleteByBob.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                botDeleteByBob.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            // Normal member Bob CANNOT remove or modify server's installed bot
            val removeServerBotByBob = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("servers").document(srv.id)
                        .collection("bots").document(bot.id).delete().await()
                }
            }
            assertTrue(removeServerBotByBob.isFailure)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                removeServerBotByBob.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            // 3. Switch back to Alice: removes bot from server & deletes bot completely
            signInTestUser(ALICE_EMAIL)
            assertTrue(aliceBotRepo.removeBotFromServer(bot.id, srv.id).isSuccess)
            val deletedInstSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").document(srv.id)
                    .collection("bots").document(bot.id).get().await()
            }
            assertFalse(deletedInstSnap.exists())

            assertTrue(aliceBotRepo.deleteBot(bot.id).isSuccess)
            val deletedBotSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("bots").document(bot.id).get().await()
            }
            assertFalse(deletedBotSnap.exists())
        } finally {
            aliceScope.cancel()
            bobScope.cancel()
        }
    }
}
