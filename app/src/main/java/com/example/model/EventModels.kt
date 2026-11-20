package com.example.model

enum class RavenEventType(val label: String) {
    VOICE("Événement vocal"),
    SERVER("Événement serveur"),
    EXTERNAL("Lieu / Externe")
}

enum class EventVisibility(val label: String) {
    PUBLIC("Public"),
    SERVER("Serveur"),
    PRIVATE_INVITE("Privé (sur invitation)")
}

enum class EventStatus(val label: String) {
    SCHEDULED("Planifié"),
    ONGOING("En cours"),
    COMPLETED("Terminé"),
    CANCELLED("Annulé")
}

enum class RsvpStatus(val label: String) {
    GOING("Participe"),
    MAYBE("Intéressé(e)"),
    WAITLISTED("Liste d'attente"),
    NOT_GOING("Absent")
}

enum class EventActionType(val label: String) {
    CREATED("Événement créé"),
    UPDATED("Événement modifié"),
    CANCELLED("Événement annulé"),
    RSVP_GOING("Participation confirmée"),
    RSVP_MAYBE("Marqué intéressé(e)"),
    RSVP_WAITLIST("Ajout en liste d'attente"),
    RSVP_CANCELLED("Désistement"),
    WAITLIST_PROMOTED("Promu depuis la liste d'attente"),
    COMPLETED("Événement terminé")
}

enum class EventReminderPreset(val minutesBefore: Int, val label: String) {
    AT_START(0, "À l'heure exacte"),
    MIN_5(5, "5 min avant"),
    MIN_15(15, "15 min avant"),
    MIN_30(30, "30 min avant"),
    HOUR_1(60, "1 h avant"),
    DAY_1(1440, "24 h avant")
}

enum class EventDurationPreset(val minutes: Int, val label: String) {
    MIN_30(30, "30 min"),
    HOUR_1(60, "1h"),
    HOUR_1_30(90, "1h30"),
    HOUR_2(120, "2h"),
    HOUR_3(180, "3h"),
    HOUR_4(240, "4h"),
    FULL_DAY(480, "Journée (8h)")
}

enum class EventHubFilter(val label: String) {
    UPCOMING("À venir"),
    MY_EVENTS("Mes RSVP"),
    PRIVATE("Privés"),
    HISTORY("Historique")
}

object EventTimezoneCatalog {
    val commonTimezones: List<Pair<String, String>> = listOf(
        "Europe/Paris" to "Paris (CET/CEST)",
        "Europe/London" to "Londres (GMT/BST)",
        "America/Toronto" to "Montréal / Toronto (EST)",
        "America/New_York" to "New York (EST/EDT)",
        "America/Los_Angeles" to "Los Angeles (PST/PDT)",
        "UTC" to "UTC (Temps universel)",
        "Africa/Casablanca" to "Casablanca (WET)",
        "Asia/Tokyo" to "Tokyo (JST)"
    )
}

data class RavenEvent(
    val id: String,
    val serverId: String? = null,
    val serverName: String = "",
    val voiceChannelId: String? = null,
    val voiceChannelName: String = "",
    val creatorId: String,
    val creatorName: String,
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    val bannerColorHex: Long = 0xFF5865F2L,
    val eventType: RavenEventType = RavenEventType.SERVER,
    val visibility: EventVisibility = EventVisibility.SERVER,
    val startAtMs: Long,
    val durationMinutes: Int = 60,
    val timezoneId: String = "Europe/Paris",
    val dateLabel: String = "",
    val timeLabel: String = "",
    val location: String = "",
    val maxParticipants: Int = 0, // 0 = illimité
    val goingIds: List<String> = emptyList(),
    val interestedIds: List<String> = emptyList(),
    val waitlistIds: List<String> = emptyList(),
    val invitedUserIds: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val status: EventStatus = EventStatus.SCHEDULED,
    val cancellationReason: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val isPublic: Boolean
        get() = visibility == EventVisibility.PUBLIC

    val endAtMs: Long
        get() = startAtMs + durationMinutes.coerceAtLeast(1) * 60_000L

    fun effectiveStatus(nowMs: Long = System.currentTimeMillis()): EventStatus = when (status) {
        EventStatus.CANCELLED -> EventStatus.CANCELLED
        EventStatus.COMPLETED -> EventStatus.COMPLETED
        else -> when {
            nowMs >= endAtMs -> EventStatus.COMPLETED
            nowMs in startAtMs until endAtMs -> EventStatus.ONGOING
            else -> EventStatus.SCHEDULED
        }
    }

    fun isFull(): Boolean = maxParticipants > 0 && goingIds.size >= maxParticipants

    fun spotsRemaining(): Int? =
        if (maxParticipants <= 0) null else (maxParticipants - goingIds.size).coerceAtLeast(0)

    fun userRsvpStatus(uid: String): RsvpStatus = when {
        uid in goingIds -> RsvpStatus.GOING
        uid in waitlistIds -> RsvpStatus.WAITLISTED
        uid in interestedIds -> RsvpStatus.MAYBE
        else -> RsvpStatus.NOT_GOING
    }

    fun waitlistPosition(uid: String): Int? {
        val idx = waitlistIds.indexOf(uid)
        return if (idx >= 0) idx + 1 else null
    }
}

data class EventDraftInput(
    val eventId: String? = null,
    val serverId: String? = null,
    val voiceChannelId: String? = null,
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    val bannerColorHex: Long = 0xFF5865F2L,
    val eventType: RavenEventType = RavenEventType.SERVER,
    val visibility: EventVisibility = EventVisibility.SERVER,
    val startAtMs: Long,
    val durationMinutes: Int = 60,
    val timezoneId: String = "Europe/Paris",
    val location: String = "",
    val maxParticipants: Int = 0,
    val invitedUserIds: List<String> = emptyList(),
    val initialReminderMinutes: Int? = 15
)

data class EventRsvpRecord(
    val uid: String,
    val eventId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarColor: Long = 0xFF5865F2L,
    val status: RsvpStatus = RsvpStatus.GOING,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class EventReminderRecord(
    val id: String,
    val eventId: String,
    val userId: String,
    val eventTitle: String,
    val minutesBefore: Int,
    val triggerAtMs: Long,
    val triggered: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class EventHistoryEntry(
    val id: String,
    val eventId: String,
    val actorId: String,
    val actorName: String,
    val actionType: EventActionType,
    val summary: String,
    val timestampLabel: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class EventUiState(
    val events: List<RavenEvent> = emptyList(),
    val upcomingEvents: List<RavenEvent> = emptyList(),
    val historyEvents: List<RavenEvent> = emptyList(),
    val myRemindersByEvent: Map<String, List<EventReminderRecord>> = emptyMap(),
    val rsvpsByEvent: Map<String, List<EventRsvpRecord>> = emptyMap(),
    val historyByEvent: Map<String, List<EventHistoryEntry>> = emptyMap(),
    val selectedFilter: EventHubFilter = EventHubFilter.UPCOMING,
    val searchQuery: String = "",
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)
