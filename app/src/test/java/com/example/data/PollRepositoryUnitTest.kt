package com.example.data

import com.example.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class PollRepositoryUnitTest {

    private class MutableClock(var currentMs: Long = 1_700_000_000_000L)

    private fun createEnv(
        scope: TestScope,
        clock: MutableClock = MutableClock()
    ): Pair<ChatRepository, PollRepository> {
        val ctx = RuntimeEnvironment.getApplication()
        val chatRepo = ChatRepository(scope = scope, appContext = ctx)
        val pollRepo = PollRepository(chatRepo = chatRepo, nowProvider = { clock.currentMs })
        return chatRepo to pollRepo
    }

    @Test
    fun `draft creation edit before publish then lock after publish and channel sharing`() = runTest {
        val clock = MutableClock()
        val (chatRepo, pollRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }
        val ch = srv.channels.first { it.type == ChannelType.TEXT }

        // Save draft
        val draft = pollRepo.saveDraft(
            PollDraftInput(
                serverId = srv.id,
                channelId = ch.id,
                question = "Version initiale brouillon ?",
                description = "Contexte initial",
                options = listOf("Alpha", "Beta"),
                allowsMultiple = false,
                durationMinutes = 60
            )
        ).getOrThrow()

        assertEquals(PollStatus.DRAFT, draft.status)
        assertNull(draft.publishedAtMs)

        // Modify draft BEFORE publish
        val editedDraft = pollRepo.saveDraft(
            PollDraftInput(
                pollId = draft.id,
                serverId = srv.id,
                channelId = ch.id,
                question = "Version finale avant publication ?",
                description = "Description mise à jour",
                options = listOf("Alpha", "Beta", "Gamma"),
                allowsMultiple = true,
                maxChoices = 2,
                durationMinutes = 120
            )
        ).getOrThrow()
        assertEquals(draft.id, editedDraft.id)
        assertEquals("Version finale avant publication ?", editedDraft.question)
        assertEquals(3, editedDraft.options.size)

        // Publish draft & share in channel + verify notification emitted
        val notifBefore = chatRepo.notifications.value.size
        val published = pollRepo.publishPoll(editedDraft.id, shareInChannel = true).getOrThrow()
        assertEquals(PollStatus.PUBLISHED, published.status)
        assertNotNull(published.publishedAtMs)
        assertEquals(clock.currentMs + 120 * 60_000L, published.expiresAtMs)

        // Trying to edit after publish MUST FAIL
        val editAfterPublish = pollRepo.saveDraft(
            PollDraftInput(
                pollId = published.id,
                serverId = srv.id,
                channelId = ch.id,
                question = "Edition post-publication interdite ?",
                options = listOf("X", "Y")
            )
        )
        assertTrue(editAfterPublish.isFailure)

        // Channel message linked to advancedPollId exists
        val msgs = chatRepo.messages.value[ch.id].orEmpty()
        assertTrue(msgs.any { it.poll?.advancedPollId == published.id })

        // Notification created for publication
        assertTrue(chatRepo.notifications.value.size > notifBefore)
    }

    @Test
    fun `single choice definitive vote versus multiple choices and retraction`() = runTest {
        val clock = MutableClock()
        val (chatRepo, pollRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }

        // 1. Single-choice definitive (1 réponse non modifiable par utilisateur)
        val singleDefinitive = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = srv.id,
                question = "Vote unique définitif ?",
                options = listOf("Oui", "Non"),
                allowsMultiple = false,
                allowVoteChange = false,
                visibility = PollVisibility.SERVER,
                durationMinutes = 60
            ),
            shareInChannel = false
        ).getOrThrow()

        val afterVote = pollRepo.toggleOptionVote(singleDefinitive.id, "opt_0").getOrThrow()
        assertEquals(1, afterVote.totalVoters)
        assertEquals(1, afterVote.totalVotes)
        assertEquals(1, afterVote.options[0].voteCount)

        // Attempting to switch or retract when allowVoteChange=false MUST FAIL
        assertTrue(pollRepo.toggleOptionVote(singleDefinitive.id, "opt_1").isFailure)
        assertTrue(pollRepo.retractVote(singleDefinitive.id).isFailure)

        // 2. Multiple choices with maxChoices=2 and modifiable votes
        val multiModifiable = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = srv.id,
                question = "Choisissez 2 langages :",
                options = listOf("Kotlin", "Rust", "TypeScript"),
                allowsMultiple = true,
                maxChoices = 2,
                allowVoteChange = true,
                visibility = PollVisibility.PUBLIC,
                durationMinutes = 1440
            ),
            shareInChannel = false
        ).getOrThrow()

        pollRepo.toggleOptionVote(multiModifiable.id, "opt_0").getOrThrow()
        val withTwo = pollRepo.toggleOptionVote(multiModifiable.id, "opt_1").getOrThrow()
        assertEquals(1, withTwo.totalVoters)
        assertEquals(2, withTwo.totalVotes)
        assertEquals(1, withTwo.options[0].voteCount)
        assertEquals(1, withTwo.options[1].voteCount)

        // Toggling a 3rd option exceeds maxChoices=2 -> fails cleanly
        val exceedMax = pollRepo.toggleOptionVote(multiModifiable.id, "opt_2")
        assertTrue(exceedMax.isFailure)

        // Toggling opt_0 again removes it while keeping opt_1
        val afterDeselect = pollRepo.toggleOptionVote(multiModifiable.id, "opt_0").getOrThrow()
        assertEquals(1, afterDeselect.totalVoters)
        assertEquals(1, afterDeselect.totalVotes)
        assertEquals(0, afterDeselect.options[0].voteCount)
        assertEquals(1, afterDeselect.options[1].voteCount)

        // Retracting entire vote drops totalVoters to 0
        val afterRetract = pollRepo.retractVote(multiModifiable.id).getOrThrow()
        assertEquals(0, afterRetract.totalVoters)
        assertEquals(0, afterRetract.totalVotes)
    }

    @Test
    fun `anonymous poll masks voter identity and stats exclude voter breakdown`() = runTest {
        val clock = MutableClock()
        val (_, pollRepo) = createEnv(TestScope(testScheduler), clock)

        val anonPoll = pollRepo.createAndPublishPoll(
            PollDraftInput(
                question = "Sondage anonyme ?",
                options = listOf("Pour", "Contre"),
                isAnonymous = true,
                visibility = PollVisibility.PUBLIC,
                durationMinutes = 60
            ),
            shareInChannel = false
        ).getOrThrow()

        pollRepo.toggleOptionVote(anonPoll.id, "opt_0").getOrThrow()

        val myVote = pollRepo.myVotesByPoll.value[anonPoll.id]
        assertNotNull(myVote)
        assertEquals("Anonyme", myVote!!.voterName)

        // Public vote breakdown is hidden for anonymous poll
        val stats = pollRepo.computeStatistics(pollRepo.polls.value.first { it.id == anonPoll.id })
        assertTrue(stats.isAnonymous)
        assertTrue(stats.optionStats.all { it.voterNames.isEmpty() })
        assertEquals(1, stats.uniqueVoters)
        assertEquals(100.0, stats.optionStats[0].percentage.toDouble(), 0.01)
    }

    @Test
    fun `role restricted polls enforce server role eligibility`() = runTest {
        val clock = MutableClock()
        val (chatRepo, pollRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }
        // Make currentUser a regular MEMBER on srv for this test
        val me = chatRepo.currentUser.value.copy(role = UserRole.MEMBER)
        val modSrv = srv.copy(
            ownerId = "someone_else",
            adminIds = listOf("someone_else"),
            moderatorIds = emptyList(),
            members = srv.members.map { if (it.id == me.id) me else it }
        )
        CommunityRepository(chatRepo).replaceServerInFlow(modSrv)

        val adminOnlyPoll = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = modSrv.id,
                question = "Décision réservée aux admins ?",
                options = listOf("Approuver", "Rejeter"),
                visibility = PollVisibility.ROLE_RESTRICTED,
                allowedRoleIds = listOf(UserRole.ADMIN.name),
                allowedRoleNames = listOf("Administrateur"),
                durationMinutes = 60
            ),
            shareInChannel = false
        ).getOrThrow()

        val (canVote, reason) = pollRepo.canUserVote(adminOnlyPoll)
        assertFalse(canVote)
        assertNotNull(reason)
        assertTrue(pollRepo.toggleOptionVote(adminOnlyPoll.id, "opt_0").isFailure)

        // Member-allowed poll succeeds
        val memberPoll = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = modSrv.id,
                question = "Sondage ouvert aux membres ?",
                options = listOf("Oui", "Non"),
                visibility = PollVisibility.ROLE_RESTRICTED,
                allowedRoleIds = listOf(UserRole.MEMBER.name),
                allowedRoleNames = listOf("Membre"),
                durationMinutes = 60
            ),
            shareInChannel = false
        ).getOrThrow()

        val (canVoteMember, _) = pollRepo.canUserVote(memberPoll)
        assertTrue(canVoteMember)
        assertTrue(pollRepo.toggleOptionVote(memberPoll.id, "opt_0").isSuccess)
    }

    @Test
    fun `expiration manual close notifications statistics and txt csv json exports`() = runTest {
        val clock = MutableClock()
        val (chatRepo, pollRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }

        val poll = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = srv.id,
                question = "Meilleur framework Android ?",
                options = listOf("Jetpack Compose", "XML Views"),
                visibility = PollVisibility.SERVER,
                durationMinutes = 15,
                notifyOnPublish = true,
                notifyOnClose = true
            ),
            shareInChannel = false
        ).getOrThrow()

        pollRepo.toggleOptionVote(poll.id, "opt_0").getOrThrow()
        val latest = pollRepo.polls.value.first { it.id == poll.id }

        // Statistics
        val stats = pollRepo.computeStatistics(latest)
        assertEquals(1, stats.uniqueVoters)
        assertEquals(1, stats.totalVotes)
        assertEquals(1, stats.leadMarginVotes)
        assertTrue(stats.winnerSummary.contains("Jetpack Compose"))

        // Export formats: Text, CSV, JSON
        val txtExport = pollRepo.exportPollResults(latest, PollExportFormat.SUMMARY_TEXT)
        assertTrue(txtExport.contains("Meilleur framework Android ?"))
        assertTrue(txtExport.contains("Jetpack Compose"))

        val csvExport = pollRepo.exportPollResults(latest, PollExportFormat.CSV)
        assertTrue(csvExport.startsWith("PollId,Question,Statut,Anonyme,OptionId,Option,Votes,Pourcentage,EnTete,Votants"))
        assertTrue(csvExport.contains("\"Jetpack Compose\",1,100.0"))

        val jsonExport = pollRepo.exportPollResults(latest, PollExportFormat.JSON)
        val parsedJson = JSONObject(jsonExport)
        assertEquals(latest.id, parsedJson.getString("pollId"))
        assertEquals(1, parsedJson.getInt("totalVotes"))
        assertEquals(2, parsedJson.getJSONArray("options").length())

        // Manual close locks voting & sends close notification
        val notifsBeforeClose = chatRepo.notifications.value.size
        val closed = pollRepo.closePollManually(latest.id).getOrThrow()
        assertEquals(PollStatus.CLOSED, closed.status)
        assertTrue(chatRepo.notifications.value.size > notifsBeforeClose)
        assertTrue(pollRepo.toggleOptionVote(closed.id, "opt_1").isFailure)

        // Duration expiration also closes poll & emits expiration notification
        val timedPoll = pollRepo.createAndPublishPoll(
            PollDraftInput(
                serverId = srv.id,
                question = "Sondage rapide 15m ?",
                options = listOf("A", "B"),
                visibility = PollVisibility.PUBLIC,
                durationMinutes = 15,
                notifyOnClose = true
            ),
            shareInChannel = false
        ).getOrThrow()

        clock.currentMs += 16 * 60_000L
        assertTrue(timedPoll.isExpired(clock.currentMs))
        assertEquals(PollStatus.CLOSED, timedPoll.effectiveStatus(clock.currentMs))

        val beforeExpiryNotif = chatRepo.notifications.value.size
        pollRepo.checkAndNotifyExpiredPolls()
        assertTrue(chatRepo.notifications.value.size > beforeExpiryNotif)
        assertTrue(pollRepo.toggleOptionVote(timedPoll.id, "opt_0").isFailure)
    }
}
