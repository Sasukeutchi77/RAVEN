package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.*
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

class PollRuleTest : FirestoreEmulatorTestBase() {

    private fun newRepos(scope: CoroutineScope): Triple<ChatRepository, CommunityRepository, PollRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        val commRepo = CommunityRepository(chatRepo)
        val pollRepo = PollRepository(chatRepo)
        return Triple(chatRepo, commRepo, pollRepo)
    }

    @Test
    fun advancedPollsLifecycle_enforcesFirestoreTransactionsAnonymityRolesAndAntiFraudRules() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (_, aliceComm, alicePolls) = newRepos(aliceScope)
        val (_, bobComm, bobPolls) = newRepos(bobScope)

        try {
            signInTestUser(ALICE_EMAIL)
            val suffix = UUID.randomUUID().toString().take(6)
            val server = aliceComm.createCommunity(
                name = "Poll Studio $suffix",
                description = "Communauté sondages",
                uniqueHandleRaw = "polls-$suffix",
                imageUrl = null,
                bannerUrl = null,
                isPublic = true,
                categoryTag = "Tech",
                rulesGuidelines = "Respect",
                homeWelcomeMessage = "Bienvenue"
            ).getOrThrow()

            val ch = server.channels.firstOrNull()
                ?: Channel("ch_gen_$suffix", server.id, "général", ChannelType.TEXT)

            val invite = aliceComm.createInvite(server.id, maxUses = 5, expiresInHours = 24).getOrThrow()

            // 1. Alice creates a DRAFT, verifies Bob cannot read it, then Alice edits DRAFT before publishing
            val draft = alicePolls.saveDraft(
                PollDraftInput(
                    serverId = server.id,
                    channelId = ch.id,
                    question = "Brouillon initial ?",
                    options = listOf("Choix A", "Choix B"),
                    allowsMultiple = true,
                    maxChoices = 2,
                    isAnonymous = false,
                    visibility = PollVisibility.PUBLIC,
                    durationMinutes = 1440
                )
            ).getOrThrow()
            assertEquals(PollStatus.DRAFT, draft.status)

            val editedDraft = alicePolls.saveDraft(
                PollDraftInput(
                    pollId = draft.id,
                    serverId = server.id,
                    channelId = ch.id,
                    question = "Quel thème pour la semaine ?",
                    description = "Choix multiples jusqu'à 2 options.",
                    options = listOf("Design M3", "WebRTC", "IA Gemini"),
                    allowsMultiple = true,
                    maxChoices = 2,
                    allowVoteChange = true,
                    isAnonymous = false,
                    visibility = PollVisibility.PUBLIC,
                    durationMinutes = 1440
                )
            ).getOrThrow()
            assertEquals("Quel thème pour la semaine ?", editedDraft.question)

            // Switch to Bob and verify Bob CANNOT read Alice's unpublished draft
            val bobUid = signInTestUser(BOB_EMAIL)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("polls").document(draft.id).get().await()
                }
                fail("Bob must NOT be allowed to read Alice's DRAFT poll")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Bob joins the community as MEMBER
            bobComm.joinByInviteCode(invite.code).getOrThrow()

            // Alice publishes the poll + creates an anonymous poll + creates an Admin-only role-restricted poll
            signInTestUser(ALICE_EMAIL)
            val publishedPublic = alicePolls.publishPoll(editedDraft.id, shareInChannel = false).getOrThrow()
            assertEquals(PollStatus.PUBLISHED, publishedPublic.status)

            val anonPoll = alicePolls.createAndPublishPoll(
                PollDraftInput(
                    serverId = server.id,
                    question = "Vote anonyme unique ?",
                    options = listOf("Pour", "Contre"),
                    allowsMultiple = false,
                    allowVoteChange = false,
                    isAnonymous = true,
                    visibility = PollVisibility.SERVER,
                    durationMinutes = 60
                ),
                shareInChannel = false
            ).getOrThrow()

            val adminOnlyPoll = alicePolls.createAndPublishPoll(
                PollDraftInput(
                    serverId = server.id,
                    question = "Vote réservé aux administrateurs ?",
                    options = listOf("Approuver", "Rejeter"),
                    allowsMultiple = false,
                    isAnonymous = false,
                    visibility = PollVisibility.ROLE_RESTRICTED,
                    allowedRoleIds = listOf(UserRole.ADMIN.name),
                    allowedRoleNames = listOf("Administrateur"),
                    durationMinutes = 60
                ),
                shareInChannel = false
            ).getOrThrow()

            // Post-publish edit by author must be rejected
            val postEditRes = alicePolls.saveDraft(
                PollDraftInput(
                    pollId = publishedPublic.id,
                    serverId = server.id,
                    question = "Tentative édition post-publication",
                    options = listOf("A", "B")
                )
            )
            assertTrue(postEditRes.isFailure)

            // 2. Switch to Bob (MEMBER): listen & vote via real PollRepository.runTransaction
            signInTestUser(BOB_EMAIL)
            val bobPubSnap = withTimeout(TIMEOUT_MS) { firestore.collection("polls").document(publishedPublic.id).get().await() }
            val bobAnonSnap = withTimeout(TIMEOUT_MS) { firestore.collection("polls").document(anonPoll.id).get().await() }
            assertEquals(PollStatus.PUBLISHED.name, bobPubSnap.getString("status"))
            assertEquals(PollStatus.PUBLISHED.name, bobAnonSnap.getString("status"))

            bobPolls.startListening(bobUid)
            kotlinx.coroutines.delay(600)

            // Bob votes for opt_0 and opt_2 on multi-choice public poll
            val vote1 = bobPolls.castVote(publishedPublic.id, listOf("opt_0", "opt_2")).getOrThrow()
            assertEquals(1, vote1.totalVoters)
            assertEquals(2, vote1.totalVotes)
            assertEquals(1, vote1.options[0].voteCount)
            assertEquals(0, vote1.options[1].voteCount)
            assertEquals(1, vote1.options[2].voteCount)

            // Bob modifies his vote to only opt_1
            val voteModified = bobPolls.castVote(publishedPublic.id, listOf("opt_1")).getOrThrow()
            assertEquals(1, voteModified.totalVoters)
            assertEquals(1, voteModified.totalVotes)
            assertEquals(0, voteModified.options[0].voteCount)
            assertEquals(1, voteModified.options[1].voteCount)

            // Bob votes on anonymous single-definitive poll -> allowed once, blocked if retrying
            val anonAfterVote = bobPolls.castVote(anonPoll.id, listOf("opt_0")).getOrThrow()
            assertEquals(1, anonAfterVote.totalVoters)
            val anonDocSnap = withTimeout(TIMEOUT_MS) {
                firestore.collection("polls").document(anonPoll.id).collection("votes").document(bobUid).get().await()
            }
            assertEquals("Anonyme", anonDocSnap.getString("voterName"))

            // Second vote attempt on definitive poll (allowVoteChange=false) fails
            assertTrue(bobPolls.castVote(anonPoll.id, listOf("opt_1")).isFailure)

            // Bob (MEMBER) is REJECTED when trying to vote on ADMIN-only poll (Firestore rule check)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.runTransaction { tx ->
                        val pRef = firestore.collection("polls").document(adminOnlyPoll.id)
                        val vRef = pRef.collection("votes").document(bobUid)
                        tx.set(
                            vRef,
                            mapOf(
                                "pollId" to adminOnlyPoll.id,
                                "voterId" to bobUid,
                                "voterName" to "Bob",
                                "selectedOptionIds" to listOf("opt_0"),
                                "votedAtMs" to System.currentTimeMillis()
                            )
                        )
                        tx.update(
                            pRef,
                            mapOf(
                                "optionCounts" to mapOf("opt_0" to 1, "opt_1" to 0),
                                "totalVotes" to 1,
                                "totalVoters" to 1
                            )
                        )
                    }.await()
                }
                fail("Member Bob must NOT vote on ADMIN-only restricted poll")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // 3. Switch back to Alice:
            // Alice CANNOT list votes on anonymous poll!
            signInTestUser(ALICE_EMAIL)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("polls").document(anonPoll.id).collection("votes").get().await()
                }
                fail("Anonymous poll votes collection must NEVER be listable")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Alice (author) CANNOT delete Bob's vote doc
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("polls").document(publishedPublic.id)
                        .collection("votes").document(bobUid).delete().await()
                }
                fail("Author must NOT delete another user's vote")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // Alice CAN vote on her own admin-restricted poll & manually close the public poll
            alicePolls.castVote(adminOnlyPoll.id, listOf("opt_0")).getOrThrow()
            val closed = alicePolls.closePollManually(publishedPublic.id).getOrThrow()
            assertEquals(PollStatus.CLOSED, closed.status)
        } finally {
            alicePolls.stopListening()
            bobPolls.stopListening()
            aliceScope.cancel()
            bobScope.cancel()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.poll@test.com"
        const val BOB_EMAIL = "bob.poll@test.com"
        const val TIMEOUT_MS = 5000L
    }
}
