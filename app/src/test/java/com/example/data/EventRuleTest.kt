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

class EventRuleTest : FirestoreEmulatorTestBase() {

    private fun newRepos(scope: CoroutineScope): Triple<ChatRepository, CommunityRepository, EventRepository> {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        val commRepo = CommunityRepository(chatRepo)
        val eventRepo = EventRepository(chatRepo)
        return Triple(chatRepo, commRepo, eventRepo)
    }

    @Test
    fun eventsLifecycle_enforcesPermissionsPrivateIsolationRsvpWaitlistAndReminders() = runBlocking {
        val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bobScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (_, aliceComm, aliceEvents) = newRepos(aliceScope)
        val (_, bobComm, bobEvents) = newRepos(bobScope)

        try {
            val aliceUid = signInTestUser(ALICE_EMAIL)
            val suffix = UUID.randomUUID().toString().take(6)
            val server = aliceComm.createCommunity(
                name = "Events Arena $suffix",
                description = "Serveur événements RAVEN",
                uniqueHandleRaw = "events-$suffix",
                imageUrl = null,
                bannerUrl = null,
                isPublic = true,
                categoryTag = "Gaming",
                rulesGuidelines = "Fair play",
                homeWelcomeMessage = "Bienvenue !"
            ).getOrThrow()
            val invite = aliceComm.createInvite(server.id, maxUses = 5, expiresInHours = 24).getOrThrow()

            // 1. Alice (owner/admin) creates a capped public server event (maxParticipants = 1)
            val futureMs = System.currentTimeMillis() + 3_600_000L
            val serverEvent = aliceEvents.createEvent(
                EventDraftInput(
                    title = "Tournoi Vocal 1 Place",
                    description = "Capacité stricte 1 joueur + liste d'attente FIFO.",
                    eventType = RavenEventType.VOICE,
                    visibility = EventVisibility.PUBLIC,
                    startAtMs = futureMs,
                    durationMinutes = 90,
                    timezoneId = "Europe/Paris",
                    location = "Vocal Principal",
                    serverId = server.id,
                    voiceChannelId = server.channels.firstOrNull()?.id ?: "ch_voice_$suffix",
                    maxParticipants = 1,
                    initialReminderMinutes = 15
                )
            ).getOrThrow()
            assertEquals(listOf(aliceUid), serverEvent.goingIds)

            // 2. Bob joins server as regular MEMBER
            val bobUid = signInTestUser(BOB_EMAIL)
            bobComm.joinByInviteCode(invite.code).getOrThrow()

            // 3. Regular member Bob MUST BE DENIED creating/cancelling server events
            val bobCreateServerEv = bobEvents.createEvent(
                EventDraftInput(
                    title = "Événement non autorisé",
                    description = "Doit être bloqué par Firestore rules",
                    eventType = RavenEventType.SERVER,
                    visibility = EventVisibility.SERVER,
                    startAtMs = futureMs + 3_600_000L,
                    durationMinutes = 60,
                    timezoneId = "UTC",
                    location = "Hall",
                    serverId = server.id
                )
            )
            assertTrue(bobCreateServerEv.isFailure)

            val bobCancelAliceEv = bobEvents.cancelEvent(serverEvent.id, "Sabotage")
            assertTrue(bobCancelAliceEv.isFailure)

            // 4. Bob taps GOING on full event (1/1) -> placed in waitlistIds
            val afterBobRsvp = bobEvents.rsvpToEvent(serverEvent.id, RsvpStatus.GOING).getOrThrow()
            assertEquals(listOf(aliceUid), afterBobRsvp.goingIds)
            assertEquals(listOf(bobUid), afterBobRsvp.waitlistIds)

            // Bob sets a personal reminder (5 min before)
            val bobReminder = bobEvents.setEventReminder(serverEvent.id, 5).getOrThrow()
            assertEquals(bobUid, bobReminder.userId)

            // 5. Alice steps down (NOT_GOING) -> Bob is AUTOMATICALLY promoted from FIFO waitlist into goingIds
            signInTestUser(ALICE_EMAIL)
            val afterAliceLeaves = aliceEvents.rsvpToEvent(serverEvent.id, RsvpStatus.NOT_GOING).getOrThrow()
            assertEquals(listOf(bobUid), afterAliceLeaves.goingIds)
            assertTrue(afterAliceLeaves.waitlistIds.isEmpty())

            // Alice CANNOT read Bob's private reminder
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection(EventFirestoreSync.COLLECTION_EVENTS)
                        .document(serverEvent.id)
                        .collection(EventFirestoreSync.SUBCOLLECTION_REMINDERS)
                        .document(bobReminder.id)
                        .get()
                        .await()
                }
                fail("Alice must NOT be allowed to read Bob's private event reminder")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // 6. Bob creates a personal PRIVATE_INVITE event NOT inviting Alice
            signInTestUser(BOB_EMAIL)
            val bobPrivateEvent = bobEvents.createEvent(
                EventDraftInput(
                    title = "Dîner Secret Bob",
                    description = "Uniquement sur invitation",
                    eventType = RavenEventType.EXTERNAL,
                    visibility = EventVisibility.PRIVATE_INVITE,
                    startAtMs = futureMs + 7_200_000L,
                    durationMinutes = 120,
                    timezoneId = "Europe/Paris",
                    location = "Lyon",
                    invitedUserIds = emptyList()
                )
            ).getOrThrow()

            // Alice CANNOT read Bob's private invite event
            signInTestUser(ALICE_EMAIL)
            try {
                withTimeout(TIMEOUT_MS) {
                    firestore.collection(EventFirestoreSync.COLLECTION_EVENTS)
                        .document(bobPrivateEvent.id)
                        .get()
                        .await()
                }
                fail("Uninvited Alice must NOT read Bob's PRIVATE_INVITE event")
            } catch (e: Exception) {
                assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.unwrapFirestoreException()?.code)
            }

            // 7. Alice modifies & cancels her server event; subsequent RSVP MUST fail
            aliceEvents.updateEvent(
                serverEvent.id,
                EventDraftInput(
                    title = "Tournoi Vocal Reporté",
                    description = "Nouvelle capacité",
                    eventType = RavenEventType.VOICE,
                    visibility = EventVisibility.PUBLIC,
                    startAtMs = futureMs + 1_800_000L,
                    durationMinutes = 120,
                    timezoneId = "Europe/Paris",
                    location = "Vocal Principal",
                    serverId = server.id,
                    voiceChannelId = serverEvent.voiceChannelId,
                    maxParticipants = 5
                )
            ).getOrThrow()

            val cancelled = aliceEvents.cancelEvent(serverEvent.id, "Maintenance serveur").getOrThrow()
            assertEquals(EventStatus.CANCELLED, cancelled.status)

            signInTestUser(BOB_EMAIL)
            val rsvpCancelled = bobEvents.rsvpToEvent(serverEvent.id, RsvpStatus.GOING)
            assertTrue(rsvpCancelled.isFailure)
        } finally {
            aliceEvents.stopListening()
            bobEvents.stopListening()
            aliceScope.cancel()
            bobScope.cancel()
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.event@test.com"
        const val BOB_EMAIL = "bob.event@test.com"
        const val TIMEOUT_MS = 5000L
    }
}
