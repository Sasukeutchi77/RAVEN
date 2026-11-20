package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.example.util.StoryMediaSecurity
import com.example.util.StoryMediaValidationResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EventRepository(
    internal val chatRepo: ChatRepository,
    internal val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _events = MutableStateFlow<List<RavenEvent>>(emptyList())
    val events: StateFlow<List<RavenEvent>> = _events.asStateFlow()

    private val _rsvpsByEvent = MutableStateFlow<Map<String, List<EventRsvpRecord>>>(emptyMap())
    val rsvpsByEvent: StateFlow<Map<String, List<EventRsvpRecord>>> = _rsvpsByEvent.asStateFlow()

    private val _remindersByEvent = MutableStateFlow<Map<String, List<EventReminderRecord>>>(emptyMap())
    val remindersByEvent: StateFlow<Map<String, List<EventReminderRecord>>> = _remindersByEvent.asStateFlow()

    private val _historyByEvent = MutableStateFlow<Map<String, List<EventHistoryEntry>>>(emptyMap())
    val historyByEvent: StateFlow<Map<String, List<EventHistoryEntry>>> = _historyByEvent.asStateFlow()

    private val cloudEventsMap = mutableMapOf<String, RavenEvent>()
    private var publicEventsListener: ListenerRegistration? = null
    private var memberEventsListener: ListenerRegistration? = null
    private var invitedEventsListener: ListenerRegistration? = null
    private var ownEventsListener: ListenerRegistration? = null
    private val rsvpListeners = mutableMapOf<String, ListenerRegistration>()
    private val historyListeners = mutableMapOf<String, ListenerRegistration>()
    private val reminderListeners = mutableMapOf<String, ListenerRegistration>()
    private var reminderTickerJob: Job? = null

    init {
        seedInitialDemoEventsIfOffline()
    }

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    internal fun currentUserName(): String =
        chatRepo.currentUser.value.name.ifBlank { "Membre" }

    private fun seedInitialDemoEventsIfOffline() {
        if (chatRepo.authInstance()?.currentUser != null) return
        val now = nowProvider()
        val me = currentUid()
        val srv = chatRepo.servers.value.firstOrNull { !it.isDmTab }
        val voiceCh = srv?.channels?.firstOrNull { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }
        val srvMembers = srv?.members?.map { it.id }?.ifEmpty { listOf(me) } ?: listOf(me)
        val tz = "Europe/Paris"

        val start1 = now + 3 * 3600_000L
        val e1 = RavenEvent(
            id = "evt_demo_voice",
            serverId = srv?.id,
            serverName = srv?.name ?: "Raven HQ",
            voiceChannelId = voiceCh?.id,
            voiceChannelName = voiceCh?.name ?: "Vocal Principal",
            creatorId = srv?.ownerId?.ifBlank { me } ?: me,
            creatorName = "Thomas",
            title = "Tournoi Synthwave & Session Vocal",
            description = "Rejoignez le salon vocal pour le grand tournoi communautaire suivi d'un debrief audio en direct.",
            bannerColorHex = 0xFF5865F2L,
            eventType = RavenEventType.VOICE,
            visibility = EventVisibility.PUBLIC,
            startAtMs = start1,
            durationMinutes = 120,
            timezoneId = tz,
            dateLabel = EventDateTimeUtils.formatDateLabel(start1, tz),
            timeLabel = EventDateTimeUtils.formatTimeLabel(start1, tz),
            location = voiceCh?.name ?: "Vocal Principal",
            maxParticipants = 12,
            goingIds = listOf("user_thomas", "user_emma"),
            interestedIds = listOf("user_lucas"),
            waitlistIds = emptyList(),
            memberIds = srvMembers,
            status = EventStatus.SCHEDULED,
            createdAtMs = now - 7200_000L,
            updatedAtMs = now - 3600_000L
        )

        val start2 = now + 26 * 3600_000L
        val e2 = RavenEvent(
            id = "evt_demo_private",
            serverId = null,
            serverName = "",
            creatorId = me,
            creatorName = currentUserName(),
            title = "Atelier Privé Bots & Architecture",
            description = "Session sur invitation uniquement pour concevoir les nouveaux plugins RAVEN.",
            bannerColorHex = 0xFF8B5CF6L,
            eventType = RavenEventType.EXTERNAL,
            visibility = EventVisibility.PRIVATE_INVITE,
            startAtMs = start2,
            durationMinutes = 90,
            timezoneId = "America/Toronto",
            dateLabel = EventDateTimeUtils.formatDateLabel(start2, "America/Toronto"),
            timeLabel = EventDateTimeUtils.formatTimeLabel(start2, "America/Toronto"),
            location = "Lien Visio Privé • Salle B",
            maxParticipants = 5,
            goingIds = listOf(me, "user_thomas"),
            invitedUserIds = listOf(me, "user_thomas", "user_emma"),
            memberIds = emptyList(),
            status = EventStatus.SCHEDULED,
            createdAtMs = now - 3600_000L,
            updatedAtMs = now - 1800_000L
        )

        val pastStart = now - 48 * 3600_000L
        val e3 = RavenEvent(
            id = "evt_demo_past",
            serverId = srv?.id,
            serverName = srv?.name ?: "Raven HQ",
            creatorId = srv?.ownerId?.ifBlank { me } ?: me,
            creatorName = "Emma",
            title = "Lancement RAVEN 2.0",
            description = "Présentation rétrospective des nouveautés communautaires.",
            bannerColorHex = 0xFF10B981L,
            eventType = RavenEventType.SERVER,
            visibility = EventVisibility.SERVER,
            startAtMs = pastStart,
            durationMinutes = 60,
            timezoneId = tz,
            dateLabel = EventDateTimeUtils.formatDateLabel(pastStart, tz),
            timeLabel = EventDateTimeUtils.formatTimeLabel(pastStart, tz),
            location = "Amphi Communauté",
            maxParticipants = 0,
            goingIds = listOf(me, "user_emma", "user_thomas"),
            memberIds = srvMembers,
            status = EventStatus.COMPLETED,
            createdAtMs = pastStart - 86400_000L,
            updatedAtMs = pastStart + 3600_000L
        )

        _events.value = listOf(e1, e2, e3)
        _historyByEvent.value = mapOf(
            e1.id to listOf(
                EventHistoryEntry(
                    id = "hist_demo_1",
                    eventId = e1.id,
                    actorId = e1.creatorId,
                    actorName = e1.creatorName,
                    actionType = EventActionType.CREATED,
                    summary = "Événement vocal créé (capacité 12 places).",
                    timestampLabel = "Il y a 2h",
                    createdAtMs = now - 7200_000L
                )
            ),
            e3.id to listOf(
                EventHistoryEntry(
                    id = "hist_demo_3",
                    eventId = e3.id,
                    actorId = e3.creatorId,
                    actorName = e3.creatorName,
                    actionType = EventActionType.COMPLETED,
                    summary = "Événement terminé avec 3 participant(s).",
                    timestampLabel = "Avant-hier",
                    createdAtMs = pastStart + 3600_000L
                )
            )
        )
    }

    fun startListening(uid: String) {
        val db = chatRepo.firestoreDb() ?: return
        if (uid.isBlank() || chatRepo.authInstance()?.currentUser == null) return
        stopListening()
        startReminderTicker()

        val col = db.collection(EventFirestoreSync.COLLECTION_EVENTS)

        publicEventsListener = col.whereEqualTo("isPublic", true)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotEvents(snap.documents, uid)
            }

        memberEventsListener = col.whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotEvents(snap.documents, uid)
            }

        invitedEventsListener = col.whereArrayContains("invitedUserIds", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotEvents(snap.documents, uid)
            }

        ownEventsListener = col.whereEqualTo("creatorId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotEvents(snap.documents, uid)
            }
    }

    fun stopListening() {
        publicEventsListener?.remove(); publicEventsListener = null
        memberEventsListener?.remove(); memberEventsListener = null
        invitedEventsListener?.remove(); invitedEventsListener = null
        ownEventsListener?.remove(); ownEventsListener = null
        rsvpListeners.values.forEach { it.remove() }; rsvpListeners.clear()
        historyListeners.values.forEach { it.remove() }; historyListeners.clear()
        reminderListeners.values.forEach { it.remove() }; reminderListeners.clear()
        reminderTickerJob?.cancel(); reminderTickerJob = null
    }

    private fun startReminderTicker() {
        reminderTickerJob?.cancel()
        reminderTickerJob = chatRepo.repoScope.launch {
            while (true) {
                delay(30_000L)
                checkAndDispatchReminders()
            }
        }
    }

    private fun mergeSnapshotEvents(docs: List<DocumentSnapshot>, uid: String) {
        val mapped = docs.mapNotNull { EventFirestoreSync.mapDocToEvent(it) }
        synchronized(cloudEventsMap) {
            mapped.forEach { ev ->
                if (canUserViewEvent(ev, uid)) {
                    cloudEventsMap[ev.id] = ev
                }
            }
            _events.value = cloudEventsMap.values.sortedBy { it.startAtMs }
        }
        mapped.forEach { ev ->
            if (canUserViewEvent(ev, uid)) {
                listenOwnReminderForEvent(ev.id, uid)
            }
        }
    }

    private fun listenOwnReminderForEvent(eventId: String, uid: String) {
        val db = chatRepo.firestoreDb() ?: return
        if (reminderListeners.containsKey(eventId)) return
        reminderListeners[eventId] = db.collection(EventFirestoreSync.COLLECTION_EVENTS)
            .document(eventId)
            .collection(EventFirestoreSync.SUBCOLLECTION_REMINDERS)
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val list = snap.documents.mapNotNull { EventFirestoreSync.mapDocToReminder(it) }
                        .sortedBy { it.triggerAtMs }
                    _remindersByEvent.value = _remindersByEvent.value + (eventId to list)
                }
            }
    }

    fun listenEventDetails(eventId: String) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (eventId.isBlank()) return

        if (!rsvpListeners.containsKey(eventId)) {
            rsvpListeners[eventId] = db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                .document(eventId)
                .collection(EventFirestoreSync.SUBCOLLECTION_RSVPS)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val list = snap.documents.mapNotNull { EventFirestoreSync.mapDocToRsvp(it) }
                            .sortedByDescending { it.updatedAtMs }
                        _rsvpsByEvent.value = _rsvpsByEvent.value + (eventId to list)
                    }
                }
        }

        if (!historyListeners.containsKey(eventId)) {
            historyListeners[eventId] = db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                .document(eventId)
                .collection(EventFirestoreSync.SUBCOLLECTION_HISTORY)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val list = snap.documents.mapNotNull { EventFirestoreSync.mapDocToHistory(it) }
                            .sortedByDescending { it.createdAtMs }
                        _historyByEvent.value = _historyByEvent.value + (eventId to list)
                    }
                }
        }

        listenOwnReminderForEvent(eventId, uid)
    }

    fun canUserViewEvent(event: RavenEvent, uid: String = currentUid()): Boolean {
        if (event.creatorId == uid) return true
        if (event.isPublic) return true
        if (uid in event.invitedUserIds) return true
        if (event.visibility == EventVisibility.PRIVATE_INVITE) return false
        if (uid in event.memberIds) return true
        val srv = event.serverId?.let { sId -> chatRepo.servers.value.find { it.id == sId } }
        return srv != null && (srv.isPublic || srv.ownerId == uid || uid in srv.members.map { it.id })
    }

    fun canManageEvent(event: RavenEvent, uid: String = currentUid()): Boolean {
        if (event.creatorId == uid) return true
        val sId = event.serverId ?: return false
        val srv = chatRepo.servers.value.find { it.id == sId } ?: return false
        return CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_EVENTS)
    }

    private suspend fun resolveServerMemberIds(serverId: String?, baseIds: List<String>): List<String> {
        if (serverId.isNullOrBlank()) return baseIds.distinct()
        val combined = baseIds.toMutableSet()
        chatRepo.servers.value.find { it.id == serverId }?.let { srv ->
            srv.ownerId.takeIf { it.isNotBlank() }?.let { combined.add(it) }
            combined.addAll(srv.adminIds)
            combined.addAll(srv.moderatorIds)
            combined.addAll(srv.members.map { it.id })
        }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val srvSnap = Tasks.await(db.collection("servers").document(serverId).get())
                    if (srvSnap.exists()) {
                        (srvSnap.get("memberIds") as? List<*>)?.filterIsInstance<String>()?.let { combined.addAll(it) }
                        srvSnap.getString("ownerId")?.takeIf { it.isNotBlank() }?.let { combined.add(it) }
                    }
                }
            }
        }
        return combined.filter { it.isNotBlank() }.distinct()
    }

    private suspend fun resolveCoverImageUrl(
        inputImageUrl: String?,
        coverUri: Uri?,
        context: Context?
    ): Result<String?> {
        if (coverUri != null && context != null) {
            return when (val validated = StoryMediaSecurity.secureCopyAndUploadStoryMedia(context, coverUri, StoryMediaType.IMAGE)) {
                is StoryMediaValidationResult.Valid -> Result.success(validated.mediaUrl)
                is StoryMediaValidationResult.Invalid -> Result.failure(IllegalArgumentException(validated.reason))
            }
        }
        val clean = inputImageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return Result.success(null)
        if (StoryMediaSecurity.isValidStoryMediaReference(clean)) {
            return Result.success(clean)
        }
        return Result.failure(IllegalArgumentException("URL d'image invalide : utilisez HTTPS ou le sélecteur sécurisé."))
    }

    suspend fun createEvent(
        input: EventDraftInput,
        coverUri: Uri? = null,
        context: Context? = null
    ): Result<RavenEvent> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))
        val cleanTitle = input.title.trim()
        if (cleanTitle.length < 3 || cleanTitle.length > 120) {
            return Result.failure(IllegalArgumentException("Le titre doit contenir entre 3 et 120 caractères."))
        }
        if (input.durationMinutes !in 5..10080) {
            return Result.failure(IllegalArgumentException("La durée doit être comprise entre 5 min et 7 jours."))
        }
        if (input.maxParticipants < 0 || input.maxParticipants > 1000) {
            return Result.failure(IllegalArgumentException("Capacité invalide (0 = illimité, max 1000)."))
        }

        val srv = input.serverId?.let { sId -> chatRepo.servers.value.find { it.id == sId } }
        if (!input.serverId.isNullOrBlank()) {
            if (srv == null) return Result.failure(IllegalArgumentException("Serveur introuvable."))
            if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_EVENTS)) {
                return Result.failure(SecurityException("Permission MANAGE_EVENTS requise sur ce serveur."))
            }
        }
        if (input.visibility == EventVisibility.SERVER && input.serverId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Un événement serveur doit être rattaché à un serveur."))
        }

        val imageRes = resolveCoverImageUrl(input.imageUrl, coverUri, context)
        if (imageRes.isFailure) return Result.failure(imageRes.exceptionOrNull()!!)

        val voiceChannel = if (input.eventType == RavenEventType.VOICE && srv != null) {
            srv.channels.find { it.id == input.voiceChannelId }
                ?: srv.channels.firstOrNull { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }
        } else null

        if (input.eventType == RavenEventType.VOICE && !input.serverId.isNullOrBlank() && voiceChannel == null) {
            return Result.failure(IllegalArgumentException("Aucun salon vocal disponible sur ce serveur."))
        }

        val tzId = EventDateTimeUtils.normalizeTimezone(input.timezoneId)
        val now = nowProvider()
        val eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val resolvedMembers = if (input.visibility == EventVisibility.PRIVATE_INVITE) {
            emptyList()
        } else {
            resolveServerMemberIds(input.serverId, listOf(uid))
        }
        val invitedList = if (input.visibility == EventVisibility.PRIVATE_INVITE) {
            (input.invitedUserIds + uid).filter { it.isNotBlank() }.distinct()
        } else {
            input.invitedUserIds.filter { it.isNotBlank() }.distinct()
        }

        val resolvedLocation = input.location.trim().ifBlank {
            voiceChannel?.name ?: (srv?.name ?: "En ligne")
        }

        val event = RavenEvent(
            id = eventId,
            serverId = input.serverId,
            serverName = srv?.name.orEmpty(),
            voiceChannelId = voiceChannel?.id ?: input.voiceChannelId,
            voiceChannelName = voiceChannel?.name.orEmpty(),
            creatorId = uid,
            creatorName = currentUserName(),
            title = cleanTitle,
            description = input.description.trim(),
            imageUrl = imageRes.getOrNull(),
            bannerColorHex = input.bannerColorHex,
            eventType = input.eventType,
            visibility = input.visibility,
            startAtMs = input.startAtMs,
            durationMinutes = input.durationMinutes,
            timezoneId = tzId,
            dateLabel = EventDateTimeUtils.formatDateLabel(input.startAtMs, tzId),
            timeLabel = EventDateTimeUtils.formatTimeLabel(input.startAtMs, tzId),
            location = resolvedLocation,
            maxParticipants = input.maxParticipants,
            goingIds = listOf(uid),
            interestedIds = emptyList(),
            waitlistIds = emptyList(),
            invitedUserIds = invitedList,
            memberIds = resolvedMembers,
            status = EventStatus.SCHEDULED,
            createdAtMs = now,
            updatedAtMs = now
        )

        val historyEntry = EventHistoryEntry(
            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            eventId = event.id,
            actorId = uid,
            actorName = currentUserName(),
            actionType = EventActionType.CREATED,
            summary = "Création : ${EventDateTimeUtils.formatFullSchedule(event.startAtMs, event.timezoneId)} • ${event.location}",
            timestampLabel = formatTimeShort(now),
            createdAtMs = now
        )

        val rsvpEntry = EventRsvpRecord(
            uid = uid,
            eventId = event.id,
            displayName = currentUserName(),
            avatarUrl = chatRepo.currentUser.value.avatarUrl,
            avatarColor = chatRepo.currentUser.value.avatarColor,
            status = RsvpStatus.GOING,
            updatedAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    val evRef = db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(event.id)
                    Tasks.await(evRef.set(EventFirestoreSync.eventToMap(event)))
                    Tasks.await(
                        evRef.collection(EventFirestoreSync.SUBCOLLECTION_RSVPS).document(uid)
                            .set(EventFirestoreSync.rsvpToMap(rsvpEntry))
                    )
                    Tasks.await(
                        evRef.collection(EventFirestoreSync.SUBCOLLECTION_HISTORY).document(historyEntry.id)
                            .set(EventFirestoreSync.historyToMap(historyEntry))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.CREATE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/${event.id}",
                        authUser.uid
                    )
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        upsertLocalEvent(event)
        appendLocalHistory(historyEntry)
        upsertLocalRsvp(rsvpEntry)
        syncServerEventSummary(event)

        if (input.initialReminderMinutes != null) {
            setEventReminder(event.id, input.initialReminderMinutes)
        }

        val scheduleStr = EventDateTimeUtils.formatFullSchedule(event.startAtMs, event.timezoneId)
        chatRepo.notifyCommunityEvent(
            title = "Nouvel événement : ${event.title}",
            body = "$scheduleStr • ${event.location}",
            sourceLabel = event.serverName.ifBlank { "Événements RAVEN" }
        )
        if (!event.serverId.isNullOrBlank()) {
            chatRepo.botCommunityEventHandler?.invoke(
                event.serverId,
                "ON_EVENT_CREATED",
                uid,
                currentUserName(),
                "${event.title} ($scheduleStr)"
            )
        }
        return Result.success(event)
    }

    suspend fun updateEvent(
        eventId: String,
        input: EventDraftInput,
        coverUri: Uri? = null,
        context: Context? = null
    ): Result<RavenEvent> {
        val uid = currentUid()
        val existing = findEventById(eventId)
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        if (!canManageEvent(existing, uid)) {
            return Result.failure(SecurityException("Vous n'avez pas la permission de modifier cet événement."))
        }
        if (existing.status == EventStatus.CANCELLED) {
            return Result.failure(IllegalStateException("Impossible de modifier un événement déjà annulé."))
        }
        val cleanTitle = input.title.trim()
        if (cleanTitle.length < 3 || cleanTitle.length > 120) {
            return Result.failure(IllegalArgumentException("Le titre doit contenir entre 3 et 120 caractères."))
        }
        if (input.durationMinutes !in 5..10080) {
            return Result.failure(IllegalArgumentException("Durée invalide."))
        }
        if (input.maxParticipants in 1 until existing.goingIds.size) {
            return Result.failure(IllegalArgumentException("La capacité (${input.maxParticipants}) ne peut pas être inférieure aux ${existing.goingIds.size} participants déjà confirmés."))
        }

        val imageRes = resolveCoverImageUrl(input.imageUrl ?: existing.imageUrl, coverUri, context)
        if (imageRes.isFailure) return Result.failure(imageRes.exceptionOrNull()!!)

        val tzId = EventDateTimeUtils.normalizeTimezone(input.timezoneId)
        val srv = (input.serverId ?: existing.serverId)?.let { sId -> chatRepo.servers.value.find { it.id == sId } }
        val voiceChannel = if (input.eventType == RavenEventType.VOICE && srv != null) {
            srv.channels.find { it.id == input.voiceChannelId }
                ?: srv.channels.firstOrNull { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }
        } else null

        // Auto-promote waitlisted users if capacity increased or became unlimited
        val nextGoing = existing.goingIds.toMutableList()
        val nextWaitlist = existing.waitlistIds.toMutableList()
        val promotedUids = mutableListOf<String>()
        while (nextWaitlist.isNotEmpty() && (input.maxParticipants == 0 || nextGoing.size < input.maxParticipants)) {
            val nextUid = nextWaitlist.removeAt(0)
            if (nextUid !in nextGoing) {
                nextGoing.add(nextUid)
                promotedUids.add(nextUid)
            }
        }

        val resolvedMembers = if (input.visibility == EventVisibility.PRIVATE_INVITE) {
            emptyList()
        } else {
            resolveServerMemberIds(existing.serverId, existing.memberIds + uid)
        }
        val invitedList = if (input.visibility == EventVisibility.PRIVATE_INVITE) {
            (input.invitedUserIds + existing.creatorId + uid).filter { it.isNotBlank() }.distinct()
        } else {
            input.invitedUserIds.filter { it.isNotBlank() }.distinct()
        }

        val now = nowProvider()
        val updated = existing.copy(
            voiceChannelId = voiceChannel?.id ?: input.voiceChannelId,
            voiceChannelName = voiceChannel?.name ?: existing.voiceChannelName,
            title = cleanTitle,
            description = input.description.trim(),
            imageUrl = imageRes.getOrNull(),
            bannerColorHex = input.bannerColorHex,
            eventType = input.eventType,
            visibility = input.visibility,
            startAtMs = input.startAtMs,
            durationMinutes = input.durationMinutes,
            timezoneId = tzId,
            dateLabel = EventDateTimeUtils.formatDateLabel(input.startAtMs, tzId),
            timeLabel = EventDateTimeUtils.formatTimeLabel(input.startAtMs, tzId),
            location = input.location.trim().ifBlank { existing.location },
            maxParticipants = input.maxParticipants,
            goingIds = nextGoing,
            waitlistIds = nextWaitlist,
            invitedUserIds = invitedList,
            memberIds = resolvedMembers,
            updatedAtMs = now
        )

        val changedSummary = buildString {
            val diffs = mutableListOf<String>()
            if (existing.title != updated.title) diffs.add("titre")
            if (existing.startAtMs != updated.startAtMs || existing.timezoneId != updated.timezoneId) diffs.add("date/heure")
            if (existing.durationMinutes != updated.durationMinutes) diffs.add("durée")
            if (existing.location != updated.location) diffs.add("lieu")
            if (existing.visibility != updated.visibility) diffs.add("visibilité")
            if (existing.maxParticipants != updated.maxParticipants) diffs.add("capacité")
            if (promotedUids.isNotEmpty()) diffs.add("${promotedUids.size} promu(s) depuis liste d'attente")
            append(if (diffs.isEmpty()) "Détails mis à jour" else "Modification (${diffs.joinToString(", ")})")
        }

        val historyEntry = EventHistoryEntry(
            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            eventId = eventId,
            actorId = uid,
            actorName = currentUserName(),
            actionType = EventActionType.UPDATED,
            summary = changedSummary,
            timestampLabel = formatTimeShort(now),
            createdAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    val evRef = db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(eventId)
                    Tasks.await(evRef.set(EventFirestoreSync.eventToMap(updated)))
                    Tasks.await(
                        evRef.collection(EventFirestoreSync.SUBCOLLECTION_HISTORY).document(historyEntry.id)
                            .set(EventFirestoreSync.historyToMap(historyEntry))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.UPDATE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId",
                        authUser.uid
                    )
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        upsertLocalEvent(updated)
        appendLocalHistory(historyEntry)
        syncServerEventSummary(updated)

        chatRepo.notifyCommunityEvent(
            title = "Événement modifié : ${updated.title}",
            body = "$changedSummary • ${EventDateTimeUtils.formatFullSchedule(updated.startAtMs, updated.timezoneId)}",
            sourceLabel = updated.serverName.ifBlank { "Événements RAVEN" }
        )
        return Result.success(updated)
    }

    suspend fun cancelEvent(eventId: String, reason: String = ""): Result<RavenEvent> {
        val uid = currentUid()
        val existing = findEventById(eventId)
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        if (!canManageEvent(existing, uid)) {
            return Result.failure(SecurityException("Permission insuffisante pour annuler cet événement."))
        }
        if (existing.status == EventStatus.CANCELLED) {
            return Result.success(existing)
        }

        val now = nowProvider()
        val cleanReason = reason.trim().ifBlank { "Annulé par l'organisateur" }
        val cancelled = existing.copy(
            status = EventStatus.CANCELLED,
            cancellationReason = cleanReason,
            updatedAtMs = now
        )
        val historyEntry = EventHistoryEntry(
            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            eventId = eventId,
            actorId = uid,
            actorName = currentUserName(),
            actionType = EventActionType.CANCELLED,
            summary = "Annulé : $cleanReason",
            timestampLabel = formatTimeShort(now),
            createdAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    val evRef = db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(eventId)
                    Tasks.await(evRef.set(EventFirestoreSync.eventToMap(cancelled)))
                    Tasks.await(
                        evRef.collection(EventFirestoreSync.SUBCOLLECTION_HISTORY).document(historyEntry.id)
                            .set(EventFirestoreSync.historyToMap(historyEntry))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.UPDATE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId",
                        authUser.uid
                    )
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        upsertLocalEvent(cancelled)
        appendLocalHistory(historyEntry)
        syncServerEventSummary(cancelled)

        chatRepo.notifyCommunityEvent(
            title = "Événement annulé : ${cancelled.title}",
            body = cleanReason,
            sourceLabel = cancelled.serverName.ifBlank { "Événements RAVEN" }
        )
        return Result.success(cancelled)
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> {
        val uid = currentUid()
        val existing = findEventById(eventId)
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        if (!canManageEvent(existing, uid)) {
            return Result.failure(SecurityException("Permission insuffisante pour supprimer cet événement."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    val evRef = db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(eventId)
                    Tasks.await(evRef.delete())
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.DELETE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId",
                        authUser.uid
                    )
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return cloudRes
        }

        StoryMediaSecurity.deleteLocalMediaIfOwned(existing.imageUrl)
        synchronized(cloudEventsMap) { cloudEventsMap.remove(eventId) }
        _events.value = _events.value.filterNot { it.id == eventId }
        _rsvpsByEvent.value = _rsvpsByEvent.value - eventId
        _remindersByEvent.value = _remindersByEvent.value - eventId
        _historyByEvent.value = _historyByEvent.value - eventId

        existing.serverId?.let { sId ->
            chatRepo.servers.value.find { it.id == sId }?.let { srv ->
                chatRepo.serversMutableFlow().value = chatRepo.servers.value.map {
                    if (it.id == sId) it.copy(events = it.events.filterNot { ev -> ev.id == eventId }) else it
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun rsvpToEvent(eventId: String, desiredStatus: RsvpStatus): Result<RavenEvent> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))
        val userName = currentUserName()
        val now = nowProvider()

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            return withContext(Dispatchers.IO) {
                try {
                    val evRef = db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(eventId)
                    val txResult = Tasks.await(db.runTransaction { tx ->
                        val snap = tx.get(evRef)
                        val ev = EventFirestoreSync.mapDocToEvent(snap)
                            ?: throw IllegalArgumentException("Événement introuvable.")
                        val computed = applyRsvpTransition(ev, uid, desiredStatus, now)
                        tx.update(
                            evRef,
                            mapOf(
                                "goingIds" to computed.updatedEvent.goingIds,
                                "interestedIds" to computed.updatedEvent.interestedIds,
                                "waitlistIds" to computed.updatedEvent.waitlistIds,
                                "updatedAtMs" to now,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        )
                        val rsvpRecord = EventRsvpRecord(
                            uid = uid,
                            eventId = eventId,
                            displayName = userName,
                            avatarUrl = chatRepo.currentUser.value.avatarUrl,
                            avatarColor = chatRepo.currentUser.value.avatarColor,
                            status = computed.finalStatusForActor,
                            updatedAtMs = now
                        )
                        tx.set(
                            evRef.collection(EventFirestoreSync.SUBCOLLECTION_RSVPS).document(uid),
                            EventFirestoreSync.rsvpToMap(rsvpRecord)
                        )
                        val histEntry = EventHistoryEntry(
                            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                            eventId = eventId,
                            actorId = uid,
                            actorName = userName,
                            actionType = computed.actionType,
                            summary = computed.summary,
                            timestampLabel = formatTimeShort(now),
                            createdAtMs = now
                        )
                        tx.set(
                            evRef.collection(EventFirestoreSync.SUBCOLLECTION_HISTORY).document(histEntry.id),
                            EventFirestoreSync.historyToMap(histEntry)
                        )
                        Triple(computed, rsvpRecord, histEntry)
                    })

                    val (computed, rsvpRecord, histEntry) = txResult
                    upsertLocalEvent(computed.updatedEvent)
                    upsertLocalRsvp(rsvpRecord)
                    appendLocalHistory(histEntry)
                    syncServerEventSummary(computed.updatedEvent)
                    emitWaitlistPromotionNotificationIfAny(computed)
                    Result.success(computed.updatedEvent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.UPDATE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId",
                        authUser.uid
                    )
                    Result.failure(IllegalStateException(msg, e))
                }
            }
        }

        // Offline / unit-test deterministic transition
        val ev = findEventById(eventId)
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        return try {
            val computed = applyRsvpTransition(ev, uid, desiredStatus, now)
            val rsvpRecord = EventRsvpRecord(
                uid = uid,
                eventId = eventId,
                displayName = userName,
                avatarUrl = chatRepo.currentUser.value.avatarUrl,
                avatarColor = chatRepo.currentUser.value.avatarColor,
                status = computed.finalStatusForActor,
                updatedAtMs = now
            )
            val histEntry = EventHistoryEntry(
                id = "hist_${UUID.randomUUID().toString().replace("-", "").take(10)}",
                eventId = eventId,
                actorId = uid,
                actorName = userName,
                actionType = computed.actionType,
                summary = computed.summary,
                timestampLabel = formatTimeShort(now),
                createdAtMs = now
            )
            upsertLocalEvent(computed.updatedEvent)
            upsertLocalRsvp(rsvpRecord)
            appendLocalHistory(histEntry)
            syncServerEventSummary(computed.updatedEvent)
            emitWaitlistPromotionNotificationIfAny(computed)
            Result.success(computed.updatedEvent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class RsvpTransitionResult(
        val updatedEvent: RavenEvent,
        val finalStatusForActor: RsvpStatus,
        val actionType: EventActionType,
        val summary: String,
        val promotedUid: String?
    )

    private fun applyRsvpTransition(
        ev: RavenEvent,
        uid: String,
        desiredStatus: RsvpStatus,
        now: Long
    ): RsvpTransitionResult {
        if (!canUserViewEvent(ev, uid)) {
            throw SecurityException("Vous n'avez pas accès à cet événement.")
        }
        val eff = ev.effectiveStatus(now)
        if (eff == EventStatus.CANCELLED) {
            throw IllegalStateException("Cet événement est annulé.")
        }
        if (eff == EventStatus.COMPLETED) {
            throw IllegalStateException("Cet événement est déjà terminé.")
        }

        val wasGoing = uid in ev.goingIds
        val nextGoing = ev.goingIds.toMutableList()
        val nextInterested = ev.interestedIds.toMutableList()
        val nextWaitlist = ev.waitlistIds.toMutableList()
        var promotedUid: String? = null
        val finalStatus: RsvpStatus
        val actionType: EventActionType
        val summary: String

        when (desiredStatus) {
            RsvpStatus.GOING -> {
                nextInterested.remove(uid)
                if (wasGoing) {
                    finalStatus = RsvpStatus.GOING
                    actionType = EventActionType.RSVP_GOING
                    summary = "${currentUserName()} confirme sa participation."
                } else if (ev.maxParticipants == 0 || nextGoing.size < ev.maxParticipants) {
                    nextWaitlist.remove(uid)
                    nextGoing.add(uid)
                    finalStatus = RsvpStatus.GOING
                    actionType = EventActionType.RSVP_GOING
                    summary = "${currentUserName()} participe à l'événement."
                } else {
                    if (uid !in nextWaitlist) nextWaitlist.add(uid)
                    finalStatus = RsvpStatus.WAITLISTED
                    actionType = EventActionType.RSVP_WAITLIST
                    summary = "${currentUserName()} rejoint la liste d'attente (#${nextWaitlist.indexOf(uid) + 1})."
                }
            }

            RsvpStatus.WAITLISTED -> {
                nextGoing.remove(uid)
                nextInterested.remove(uid)
                if (ev.maxParticipants == 0 || nextGoing.size < ev.maxParticipants) {
                    nextWaitlist.remove(uid)
                    nextGoing.add(uid)
                    finalStatus = RsvpStatus.GOING
                    actionType = EventActionType.RSVP_GOING
                    summary = "Place disponible : ${currentUserName()} confirmé directement."
                } else {
                    if (uid !in nextWaitlist) nextWaitlist.add(uid)
                    finalStatus = RsvpStatus.WAITLISTED
                    actionType = EventActionType.RSVP_WAITLIST
                    summary = "${currentUserName()} est sur liste d'attente (#${nextWaitlist.indexOf(uid) + 1})."
                }
            }

            RsvpStatus.MAYBE -> {
                nextGoing.remove(uid)
                nextWaitlist.remove(uid)
                if (uid !in nextInterested) nextInterested.add(uid)
                if (wasGoing && nextWaitlist.isNotEmpty()) {
                    val candidate = nextWaitlist.removeAt(0)
                    if (candidate !in nextGoing) {
                        nextGoing.add(candidate)
                        promotedUid = candidate
                    }
                }
                finalStatus = RsvpStatus.MAYBE
                actionType = EventActionType.RSVP_MAYBE
                summary = if (promotedUid != null) {
                    "${currentUserName()} est intéressé(e) ; un inscrit en attente a été promu."
                } else {
                    "${currentUserName()} est intéressé(e)."
                }
            }

            RsvpStatus.NOT_GOING -> {
                nextGoing.remove(uid)
                nextInterested.remove(uid)
                nextWaitlist.remove(uid)
                if (wasGoing && nextWaitlist.isNotEmpty()) {
                    val candidate = nextWaitlist.removeAt(0)
                    if (candidate !in nextGoing) {
                        nextGoing.add(candidate)
                        promotedUid = candidate
                    }
                }
                finalStatus = RsvpStatus.NOT_GOING
                actionType = if (promotedUid != null) EventActionType.WAITLIST_PROMOTED else EventActionType.RSVP_CANCELLED
                summary = if (promotedUid != null) {
                    "${currentUserName()} s'est désisté(e) ; le suivant en liste d'attente est confirmé."
                } else {
                    "${currentUserName()} s'est désisté(e)."
                }
            }
        }

        return RsvpTransitionResult(
            updatedEvent = ev.copy(
                goingIds = nextGoing.distinct(),
                interestedIds = nextInterested.distinct(),
                waitlistIds = nextWaitlist.distinct(),
                updatedAtMs = now
            ),
            finalStatusForActor = finalStatus,
            actionType = actionType,
            summary = summary,
            promotedUid = promotedUid
        )
    }

    private fun emitWaitlistPromotionNotificationIfAny(res: RsvpTransitionResult) {
        val promoted = res.promotedUid ?: return
        val ev = res.updatedEvent
        val isSelfPromoted = promoted == currentUid()
        chatRepo.notifyCommunityEvent(
            title = if (isSelfPromoted) "Place libérée ! Vous êtes confirmé(e)" else "Promotion liste d'attente",
            body = if (isSelfPromoted) {
                "Une place s'est libérée pour « ${ev.title} » — votre participation est confirmée !"
            } else {
                "Un participant en liste d'attente a obtenu une place pour « ${ev.title} »."
            },
            sourceLabel = ev.serverName.ifBlank { "Événements RAVEN" }
        )
    }

    suspend fun setEventReminder(eventId: String, minutesBefore: Int): Result<EventReminderRecord> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))
        val ev = findEventById(eventId)
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        if (!canUserViewEvent(ev, uid)) {
            return Result.failure(SecurityException("Accès refusé à cet événement."))
        }
        if (ev.effectiveStatus(nowProvider()) != EventStatus.SCHEDULED) {
            return Result.failure(IllegalStateException("Les rappels ne peuvent être planifiés que sur un événement à venir."))
        }

        val now = nowProvider()
        val clampedMin = minutesBefore.coerceIn(0, 10080)
        val reminderId = "${uid}_$clampedMin"
        val triggerAt = (ev.startAtMs - clampedMin * 60_000L).coerceAtLeast(now)
        val record = EventReminderRecord(
            id = reminderId,
            eventId = eventId,
            userId = uid,
            eventTitle = ev.title,
            minutesBefore = clampedMin,
            triggerAtMs = triggerAt,
            triggered = false,
            createdAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(EventFirestoreSync.SUBCOLLECTION_REMINDERS)
                            .document(reminderId)
                            .set(EventFirestoreSync.reminderToMap(record))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(
                        e,
                        OperationType.CREATE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId/${EventFirestoreSync.SUBCOLLECTION_REMINDERS}/$reminderId",
                        authUser.uid
                    )
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        val existing = _remindersByEvent.value[eventId].orEmpty().filterNot { it.id == reminderId }
        _remindersByEvent.value = _remindersByEvent.value + (eventId to (existing + record).sortedBy { it.triggerAtMs })
        checkAndDispatchReminders()
        return Result.success(record)
    }

    suspend fun removeEventReminder(eventId: String, reminderId: String): Result<Unit> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    Tasks.await(
                        db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(EventFirestoreSync.SUBCOLLECTION_REMINDERS)
                            .document(reminderId)
                            .delete()
                    )
                }.onFailure { e ->
                    FirestoreErrorHandler.handleFirestoreError(
                        e as Exception,
                        OperationType.DELETE,
                        "${EventFirestoreSync.COLLECTION_EVENTS}/$eventId/${EventFirestoreSync.SUBCOLLECTION_REMINDERS}/$reminderId",
                        authUser.uid
                    )
                }
            }
        }
        val next = _remindersByEvent.value[eventId].orEmpty().filterNot { it.id == reminderId }
        _remindersByEvent.value = _remindersByEvent.value + (eventId to next)
        return Result.success(Unit)
    }

    suspend fun checkAndDispatchReminders(): Int {
        val now = nowProvider()
        val uid = currentUid()
        var dispatched = 0
        val currentMap = _remindersByEvent.value.toMutableMap()

        currentMap.forEach { (eventId, reminders) ->
            val ev = findEventById(eventId) ?: return@forEach
            val eff = ev.effectiveStatus(now)
            if (eff == EventStatus.CANCELLED || eff == EventStatus.COMPLETED) return@forEach

            val updatedList = reminders.map { rem ->
                if (!rem.triggered && rem.userId == uid && now >= rem.triggerAtMs && now <= ev.endAtMs) {
                    val whenLabel = if (rem.minutesBefore == 0) "commence maintenant" else "commence dans ${rem.minutesBefore} min"
                    chatRepo.notifyCommunityEvent(
                        title = "Rappel événement : ${ev.title}",
                        body = "$whenLabel • ${ev.location} (${EventDateTimeUtils.formatTimeLabel(ev.startAtMs, ev.timezoneId)})",
                        sourceLabel = ev.serverName.ifBlank { "Rappels RAVEN" }
                    )
                    dispatched++
                    val triggeredRem = rem.copy(triggered = true)
                    val db = chatRepo.firestoreDb()
                    val authUser = chatRepo.authInstance()?.currentUser
                    if (db != null && authUser != null) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                Tasks.await(
                                    db.collection(EventFirestoreSync.COLLECTION_EVENTS)
                                        .document(eventId)
                                        .collection(EventFirestoreSync.SUBCOLLECTION_REMINDERS)
                                        .document(rem.id)
                                        .update("triggered", true)
                                )
                            }
                        }
                    }
                    triggeredRem
                } else {
                    rem
                }
            }
            currentMap[eventId] = updatedList
        }
        if (dispatched > 0) {
            _remindersByEvent.value = currentMap
        }
        return dispatched
    }

    fun shareEventInChannel(eventId: String, channelId: String): Result<Unit> {
        val ev = _events.value.find { it.id == eventId }
            ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
        val nowLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowProvider()))
        val msg = Message(
            id = "msg_evt_${UUID.randomUUID().toString().take(8)}",
            sender = chatRepo.currentUser.value,
            text = "📅 **Événement : ${ev.title}**\n🕒 ${EventDateTimeUtils.formatFullSchedule(ev.startAtMs, ev.timezoneId)} (${EventDateTimeUtils.formatDuration(ev.durationMinutes)})\n📍 ${ev.location}\n👥 ${ev.goingIds.size} participant(s)${if (ev.maxParticipants > 0) " / ${ev.maxParticipants}" else ""}",
            timestamp = nowLabel
        )
        chatRepo.appendSystemChannelMessage(channelId, msg)
        return Result.success(Unit)
    }

    private suspend fun findEventById(eventId: String): RavenEvent? {
        _events.value.find { it.id == eventId }?.let { return it }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    val snap = Tasks.await(db.collection(EventFirestoreSync.COLLECTION_EVENTS).document(eventId).get())
                    EventFirestoreSync.mapDocToEvent(snap)
                }.getOrNull()
            }
            if (fetched != null) {
                upsertLocalEvent(fetched)
                return fetched
            }
        }
        return null
    }

    private fun upsertLocalEvent(event: RavenEvent) {
        synchronized(cloudEventsMap) {
            cloudEventsMap[event.id] = event
        }
        val curr = _events.value.filterNot { it.id == event.id }
        _events.value = (curr + event).sortedBy { it.startAtMs }
    }

    private fun upsertLocalRsvp(record: EventRsvpRecord) {
        val curr = _rsvpsByEvent.value[record.eventId].orEmpty().filterNot { it.uid == record.uid }
        _rsvpsByEvent.value = _rsvpsByEvent.value + (record.eventId to (listOf(record) + curr))
    }

    private fun appendLocalHistory(entry: EventHistoryEntry) {
        val curr = _historyByEvent.value[entry.eventId].orEmpty().filterNot { it.id == entry.id }
        _historyByEvent.value = _historyByEvent.value + (entry.eventId to (listOf(entry) + curr))
    }

    private fun syncServerEventSummary(event: RavenEvent) {
        val sId = event.serverId ?: return
        val servers = chatRepo.servers.value
        if (servers.none { it.id == sId }) return
        val summary = ServerEvent(
            id = event.id,
            title = event.title,
            description = event.description,
            dateTimeLabel = "${event.dateLabel} • ${event.timeLabel}",
            locationLabel = event.location,
            creatorName = event.creatorName,
            interestedUserIds = event.goingIds
        )
        chatRepo.serversMutableFlow().value = servers.map { srv ->
            if (srv.id != sId) srv
            else {
                val filtered = srv.events.filterNot { it.id == event.id }
                val nextEvents = if (event.status == EventStatus.CANCELLED) filtered else listOf(summary) + filtered
                srv.copy(events = nextEvents)
            }
        }
    }

    private fun formatTimeShort(epochMs: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH).format(Date(epochMs))
}
