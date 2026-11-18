package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AiAssistantMode
import com.example.model.AiTaskCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice.ai@raven.test"
        const val BOB_EMAIL = "bob.ai@raven.test"
        const val TIMEOUT_MS = 10_000L
    }

    private val testProvider = object : RavenAiProvider {
        override val providerId: String = "emulator_test_provider"
        override val modelDisplayName: String = "Test Gemini"
        override suspend fun generateText(request: AiProviderRequest): String = "Réponse sécurisée."
        override fun generateTextStream(request: AiProviderRequest): Flow<String> = flow {
            emit("Réponse ")
            emit("sécurisée.")
        }
        override suspend fun generateStructuredJson(
            request: AiProviderRequest,
            schemaSpec: AiStructuredSchemaSpec
        ): String = """{"isSpam":false,"isSuspicious":false,"riskScore":10,"riskLevel":"LOW","categories":[],"signals":[],"recommendedAction":"OK","explanation":"RAS"}"""
    }

    private fun newAiEnv(scope: CoroutineScope): Pair<ChatRepository, AiAssistantRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        val aiRepo = AiAssistantRepository(
            chatRepo = chatRepo,
            provider = testProvider
        )
        return chatRepo to aiRepo
    }

    @Test
    fun aiHistoryQuotasAndServerPermissions_enforceFirestoreSecurityAndAuth() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (_, aliceAi) = newAiEnv(aliceScope)

        try {
            // 1. Unauthenticated call is strictly rejected
            auth.signOut()
            val unauthRes = aliceAi.sendAiChatMessage("Bonjour sans auth", AiAssistantMode.PERSONAL)
            assertTrue(unauthRes.isFailure)

            // 2. Alice signs in, creates a private community and an AI conversation persisted on Firestore
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val aliceComm = CommunityRepository(aliceAi.chatRepo)
            val suffix = UUID.randomUUID().toString().take(6)
            val srv = aliceComm.createCommunity(
                name = "AI Guild $suffix",
                description = "Serveur privé IA",
                uniqueHandleRaw = "ai-guild-$suffix",
                isPublic = false
            ).getOrThrow()

            val reply = aliceAi.sendAiChatMessage(
                rawPrompt = "Aide-moi sur mon serveur",
                mode = AiAssistantMode.SERVER,
                server = srv,
                channel = srv.channels.first()
            ).getOrThrow()
            assertEquals("Réponse sécurisée.", reply.content)

            val convId = aliceAi.activeConversationId.value!!
            val convSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("ai_conversations").document(convId).get().await()
            }
            assertTrue(convSnap.exists())
            assertEquals(aliceUid, convSnap.getString("userId"))
            assertEquals(srv.id, convSnap.getString("serverId"))

            val quotaSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("ai_usage").document("daily_quota").get().await()
            }
            assertTrue(quotaSnap.exists())
            assertTrue((quotaSnap.getLong("requestsToday") ?: 0L) >= 1L)

            // 3. Switch to Bob -> CANNOT read Alice's conversations/quota, CANNOT tamper quota or access private server AI
            val bobUid = signInTestUser(BOB_EMAIL)

            val readAliceConv = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("users").document(aliceUid)
                        .collection("ai_conversations").document(convId).get().await()
                }
            }
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                readAliceConv.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            // Bob cannot use SERVER moderation on Alice's private community
            val bobAiRes = aliceAi.analyzeModerationAndSpam(
                contentToInspect = "test",
                focusCategory = AiTaskCategory.MODERATION_HELP,
                server = srv
            )
            assertTrue(bobAiRes.isFailure)
            assertTrue(bobAiRes.exceptionOrNull() is SecurityException)

            // Bob cannot tamper with dailyLimit > 40 on his own quota
            val inflateLimit = runCatching {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("users").document(bobUid)
                        .collection("ai_usage").document("daily_quota")
                        .set(
                            mapOf(
                                "userId" to bobUid,
                                "dayKey" to "2026-11-17",
                                "requestsToday" to 1,
                                "dailyLimit" to 999
                            )
                        ).await()
                }
            }
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                inflateLimit.exceptionOrNull()?.unwrapFirestoreException()?.code
            )

            // 4. Switch back to Alice -> can delete conversation and clearAllHistory cleanly on Firestore
            signInTestUser(ALICE_EMAIL)
            aliceAi.clearAllHistory().getOrThrow()
            val afterDeleteSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("ai_conversations").document(convId).get().await()
            }
            assertFalse(afterDeleteSnap.exists())
        } finally {
            aliceAi.releaseListeners()
            aliceScope.cancel()
        }
    }
}
