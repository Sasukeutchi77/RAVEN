package com.example.data

import com.example.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryUnitTest {

    private class MutableClock(var currentMs: Long = 1_700_000_000_000L)

    private fun createEnv(
        scope: TestScope,
        clock: MutableClock = MutableClock()
    ): Pair<ChatRepository, EventRepository> {
        val ctx = RuntimeEnvironment.getApplication()
        val chatRepo = ChatRepository(scope = scope, appContext = ctx)
        val eventRepo = EventRepository(chatRepo = chatRepo, nowProvider = { clock.currentMs })
        return chatRepo to eventRepo
    }

    @Test
    fun `server and voice event creation and history audit on update`() = runTest {
        val clock = MutableClock()
        val (chatRepo, eventRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }
        val voiceCh = srv.channels.first { it.type == ChannelType.VOICE }

        // 1. Authorized creator creates VOICE event with initial reminder
        val notifBefore = chatRepo.notifications.value.size
        val created = eventRepo.createEvent(
            EventDraftInput(
                title = "Session Live WebRTC",
                description = "Table ronde vocale sur l'architecture RAVEN.",
                eventType = RavenEventType.VOICE,
                visibility = EventVisibility.SERVER,
                startAtMs = clock.currentMs + 3_600_000L,
                durationMinutes = 90,
                timezoneId = "Europe/Paris",
                location = "Vocal Général",
                serverId = srv.id,
                voiceChannelId = voiceCh.id,
                maxParticipants = 12,
                initialReminderMinutes = 15
            )
        ).getOrThrow()

        assertEquals(RavenEventType.VOICE, created.eventType)
        assertEquals(voiceCh.id, created.voiceChannelId)
        assertEquals("Europe/Paris", created.timezoneId)
        assertTrue(eventRepo.currentUid() in created.goingIds)
        assertTrue(chatRepo.notifications.value.size > notifBefore)
        val reminders = eventRepo.remindersByEvent.value[created.id].orEmpty()
        assertEquals(1, reminders.size)
        assertEquals(15, reminders.first().minutesBefore)

        // 2. Synced to legacy ServerEvent drawer summary
        val syncedSrv = chatRepo.servers.value.first { it.id == srv.id }
        assertTrue(syncedSrv.events.any { it.id == created.id })

        // 3. Regular member permission check returns false for event management
        val regularMember = srv.members.firstOrNull {
            !CommunityPermissions.hasPermission(srv, it.id, RolePermission.MANAGE_EVENTS)
        }
        if (regularMember != null) {
            assertFalse(eventRepo.canManageEvent(created, regularMember.id))
        }

        // 4. Modify event title, duration, and timezone -> history tracks CREATED + UPDATED
        val updated = eventRepo.updateEvent(
            created.id,
            EventDraftInput(
                title = "Session Live WebRTC v2",
                description = "Mise à jour du programme.",
                eventType = RavenEventType.VOICE,
                visibility = EventVisibility.PUBLIC,
                startAtMs = clock.currentMs + 7_200_000L,
                durationMinutes = 120,
                timezoneId = "America/Toronto",
                location = "Studio Principal",
                serverId = srv.id,
                voiceChannelId = voiceCh.id,
                maxParticipants = 15
            )
        ).getOrThrow()

        assertEquals("Session Live WebRTC v2", updated.title)
        assertEquals("America/Toronto", updated.timezoneId)
        assertEquals(120, updated.durationMinutes)
        assertTrue(updated.isPublic)

        val history = eventRepo.historyByEvent.value[created.id].orEmpty()
        assertTrue(history.any { it.actionType == EventActionType.CREATED })
        assertTrue(history.any { it.actionType == EventActionType.UPDATED })
    }

    @Test
    fun `private invite visibility and capacity expansion with rsvp transitions`() = runTest {
        val clock = MutableClock()
        val (_, eventRepo) = createEnv(TestScope(testScheduler), clock)

        val created = eventRepo.createEvent(
            EventDraftInput(
                title = "Atelier Limité 1 Place",
                description = "Test confidentialité et capacité",
                eventType = RavenEventType.EXTERNAL,
                visibility = EventVisibility.PRIVATE_INVITE,
                startAtMs = clock.currentMs + 3_600_000L,
                durationMinutes = 60,
                timezoneId = "Europe/Paris",
                location = "Salle Privée",
                maxParticipants = 1,
                invitedUserIds = listOf("u2", "u3")
            )
        ).getOrThrow()

        assertFalse(created.isPublic)
        assertEquals(listOf(eventRepo.currentUid()), created.goingIds)
        assertTrue(eventRepo.canUserViewEvent(created, "u2"))
        assertFalse(eventRepo.canUserViewEvent(created, "uninvited_stranger"))

        // Step down -> removed from goingIds, then rejoin -> GOING
        val afterLeave = eventRepo.rsvpToEvent(created.id, RsvpStatus.NOT_GOING).getOrThrow()
        assertFalse(eventRepo.currentUid() in afterLeave.goingIds)

        val afterMaybe = eventRepo.rsvpToEvent(created.id, RsvpStatus.MAYBE).getOrThrow()
        assertTrue(eventRepo.currentUid() in afterMaybe.interestedIds)

        val expanded = eventRepo.updateEvent(
            created.id,
            EventDraftInput(
                title = created.title,
                description = created.description,
                eventType = created.eventType,
                visibility = EventVisibility.PRIVATE_INVITE,
                startAtMs = created.startAtMs,
                durationMinutes = 60,
                timezoneId = "Europe/Paris",
                location = created.location,
                maxParticipants = 2,
                invitedUserIds = listOf("u2", "u3")
            )
        ).getOrThrow()
        assertEquals(2, expanded.maxParticipants)
    }

    @Test
    fun `cancellation blocks rsvp and writes reason to history and notifications`() = runTest {
        val clock = MutableClock()
        val (chatRepo, eventRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }

        val created = eventRepo.createEvent(
            EventDraftInput(
                title = "Meetup Communautaire",
                description = "Rencontre hebdo",
                eventType = RavenEventType.SERVER,
                visibility = EventVisibility.SERVER,
                startAtMs = clock.currentMs + 3_600_000L,
                durationMinutes = 60,
                timezoneId = "UTC",
                location = "Hall Central",
                serverId = srv.id
            )
        ).getOrThrow()

        val notifBeforeCancel = chatRepo.notifications.value.size
        val cancelled = eventRepo.cancelEvent(created.id, "Orage imprévu").getOrThrow()
        assertEquals(EventStatus.CANCELLED, cancelled.status)
        assertEquals("Orage imprévu", cancelled.cancellationReason)
        assertTrue(chatRepo.notifications.value.size > notifBeforeCancel)

        val history = eventRepo.historyByEvent.value[created.id].orEmpty()
        assertTrue(history.any { it.actionType == EventActionType.CANCELLED && it.summary.contains("Orage imprévu") })

        // RSVP after cancellation MUST FAIL
        val rsvpAfterCancel = eventRepo.rsvpToEvent(created.id, RsvpStatus.GOING)
        assertTrue(rsvpAfterCancel.isFailure)
    }

    @Test
    fun `reminder ticker triggers notification when clock reaches trigger time`() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val (chatRepo, eventRepo) = createEnv(TestScope(testScheduler), clock)
        val srv = chatRepo.servers.value.first { !it.isDmTab }

        val startAt = clock.currentMs + 60 * 60_000L // +60 min
        val created = eventRepo.createEvent(
            EventDraftInput(
                title = "Tournoi Rappel Auto",
                description = "Test du timer de rappel",
                eventType = RavenEventType.SERVER,
                visibility = EventVisibility.SERVER,
                startAtMs = startAt,
                durationMinutes = 45,
                timezoneId = "Europe/Paris",
                location = "Arène 1",
                serverId = srv.id,
                initialReminderMinutes = 15
            )
        ).getOrThrow()

        // Before trigger window (t + 10 min => 50 min before start): nothing dispatched
        clock.currentMs += 10 * 60_000L
        assertEquals(0, eventRepo.checkAndDispatchReminders())

        // Inside trigger window (t + 46 min => 14 min before start): dispatched once!
        val notifBefore = chatRepo.notifications.value.size
        clock.currentMs = startAt - 14 * 60_000L
        assertEquals(1, eventRepo.checkAndDispatchReminders())
        assertTrue(chatRepo.notifications.value.size > notifBefore)

        // Repeated check does NOT re-fire already triggered reminder
        assertEquals(0, eventRepo.checkAndDispatchReminders())
        val rem = eventRepo.remindersByEvent.value[created.id].orEmpty().first()
        assertTrue(rem.triggered)
    }

    @Test
    fun `timezone formatting uses event timezone accurately`() {
        val sampleUtcMs = 1_700_000_000_000L // 2023-11-14 22:13:20 UTC
        val utcLabel = EventDateTimeUtils.formatTimeLabel(sampleUtcMs, "UTC")
        val parisLabel = EventDateTimeUtils.formatTimeLabel(sampleUtcMs, "Europe/Paris")
        assertEquals("22:13", utcLabel)
        assertEquals("23:13", parisLabel)
    }
}
