package com.example.data

import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object EventDateTimeUtils {
    private val validTimezoneIds: Set<String> by lazy { TimeZone.getAvailableIDs().toSet() }

    fun normalizeTimezone(tzId: String?): String {
        val trimmed = tzId?.trim().orEmpty()
        return if (trimmed.isNotBlank() && trimmed in validTimezoneIds) trimmed else TimeZone.getDefault().id
    }

    fun composeEpochMs(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
        timezoneId: String
    ): Long {
        val tz = TimeZone.getTimeZone(normalizeTimezone(timezoneId))
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun extractComponents(epochMs: Long, timezoneId: String): EventDateTimeParts {
        val tz = TimeZone.getTimeZone(normalizeTimezone(timezoneId))
        val cal = Calendar.getInstance(tz).apply { timeInMillis = epochMs }
        return EventDateTimeParts(
            year = cal.get(Calendar.YEAR),
            monthZeroBased = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
            hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE)
        )
    }

    fun formatDateLabel(epochMs: Long, timezoneId: String): String {
        val tz = TimeZone.getTimeZone(normalizeTimezone(timezoneId))
        val sdf = SimpleDateFormat("EEE dd MMM yyyy", Locale.FRENCH).apply { timeZone = tz }
        return sdf.format(Date(epochMs))
    }

    fun formatTimeLabel(epochMs: Long, timezoneId: String): String {
        val tz = TimeZone.getTimeZone(normalizeTimezone(timezoneId))
        val sdf = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = tz }
        return sdf.format(Date(epochMs))
    }

    fun formatFullSchedule(epochMs: Long, timezoneId: String): String {
        val normTz = normalizeTimezone(timezoneId)
        val tz = TimeZone.getTimeZone(normTz)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).apply { timeZone = tz }
        val shortName = tz.getDisplayName(false, TimeZone.SHORT, Locale.FRENCH)
        return "${sdf.format(Date(epochMs))} ($shortName)"
    }

    fun formatDuration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h${String.format(Locale.US, "%02d", minutes % 60)}"
    }

    fun formatCountdown(startAtMs: Long, endAtMs: Long, nowMs: Long, status: EventStatus): String {
        if (status == EventStatus.CANCELLED) return "Annulé"
        if (status == EventStatus.COMPLETED || nowMs >= endAtMs) return "Terminé"
        if (nowMs >= startAtMs) {
            val leftMin = ((endAtMs - nowMs) / 60_000L).coerceAtLeast(1L)
            return "En direct • reste ${formatDuration(leftMin.toInt())}"
        }
        val diffMs = startAtMs - nowMs
        val totalMin = (diffMs / 60_000L).coerceAtLeast(1L)
        val days = totalMin / 1440L
        val hours = (totalMin % 1440L) / 60L
        val mins = totalMin % 60L
        return when {
            days > 0 -> "Dans ${days}j ${hours}h"
            hours > 0 -> "Dans ${hours}h ${mins}m"
            else -> "Dans ${mins} min"
        }
    }
}

data class EventDateTimeParts(
    val year: Int,
    val monthZeroBased: Int,
    val dayOfMonth: Int,
    val hourOfDay: Int,
    val minute: Int
)

object EventFirestoreSync {
    const val COLLECTION_EVENTS = "events"
    const val SUBCOLLECTION_RSVPS = "rsvps"
    const val SUBCOLLECTION_REMINDERS = "reminders"
    const val SUBCOLLECTION_HISTORY = "history"

    fun eventToMap(event: RavenEvent): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to event.id,
            "serverName" to event.serverName,
            "voiceChannelName" to event.voiceChannelName,
            "creatorId" to event.creatorId,
            "creatorName" to event.creatorName,
            "title" to event.title,
            "description" to event.description,
            "bannerColorHex" to event.bannerColorHex,
            "eventType" to event.eventType.name,
            "visibility" to event.visibility.name,
            "isPublic" to (event.visibility == EventVisibility.PUBLIC),
            "startAtMs" to event.startAtMs,
            "durationMinutes" to event.durationMinutes,
            "endAtMs" to event.endAtMs,
            "timezoneId" to event.timezoneId,
            "dateLabel" to event.dateLabel,
            "timeLabel" to event.timeLabel,
            "location" to event.location,
            "maxParticipants" to event.maxParticipants,
            "goingIds" to event.goingIds,
            "interestedIds" to event.interestedIds,
            "waitlistIds" to event.waitlistIds,
            "invitedUserIds" to event.invitedUserIds,
            "memberIds" to if (event.visibility == EventVisibility.PRIVATE_INVITE) emptyList() else event.memberIds,
            "status" to event.status.name,
            "cancellationReason" to event.cancellationReason,
            "createdAtMs" to event.createdAtMs,
            "updatedAtMs" to event.updatedAtMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!event.serverId.isNullOrBlank()) map["serverId"] = event.serverId
        if (!event.voiceChannelId.isNullOrBlank()) map["voiceChannelId"] = event.voiceChannelId
        if (!event.imageUrl.isNullOrBlank()) map["imageUrl"] = event.imageUrl
        return map
    }

    fun mapDocToEvent(doc: DocumentSnapshot): RavenEvent? {
        if (!doc.exists()) return null
        val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val creatorId = doc.getString("creatorId") ?: return null
        val title = doc.getString("title") ?: return null
        val typeRaw = doc.getString("eventType") ?: RavenEventType.SERVER.name
        val eventType = enumValues<RavenEventType>().firstOrNull { it.name == typeRaw } ?: RavenEventType.SERVER
        val visRaw = doc.getString("visibility") ?: EventVisibility.SERVER.name
        val visibility = enumValues<EventVisibility>().firstOrNull { it.name == visRaw } ?: EventVisibility.SERVER
        val statusRaw = doc.getString("status") ?: EventStatus.SCHEDULED.name
        val status = enumValues<EventStatus>().firstOrNull { it.name == statusRaw } ?: EventStatus.SCHEDULED
        val tzId = EventDateTimeUtils.normalizeTimezone(doc.getString("timezoneId"))
        val startAtMs = (doc.get("startAtMs") as? Number)?.toLong() ?: System.currentTimeMillis()
        val durationMin = (doc.get("durationMinutes") as? Number)?.toInt() ?: 60

        return RavenEvent(
            id = id,
            serverId = doc.getString("serverId"),
            serverName = doc.getString("serverName").orEmpty(),
            voiceChannelId = doc.getString("voiceChannelId"),
            voiceChannelName = doc.getString("voiceChannelName").orEmpty(),
            creatorId = creatorId,
            creatorName = doc.getString("creatorName").orEmpty().ifBlank { "Organisateur" },
            title = title,
            description = doc.getString("description").orEmpty(),
            imageUrl = doc.getString("imageUrl"),
            bannerColorHex = (doc.get("bannerColorHex") as? Number)?.toLong() ?: 0xFF5865F2L,
            eventType = eventType,
            visibility = visibility,
            startAtMs = startAtMs,
            durationMinutes = durationMin,
            timezoneId = tzId,
            dateLabel = doc.getString("dateLabel").orEmpty().ifBlank { EventDateTimeUtils.formatDateLabel(startAtMs, tzId) },
            timeLabel = doc.getString("timeLabel").orEmpty().ifBlank { EventDateTimeUtils.formatTimeLabel(startAtMs, tzId) },
            location = doc.getString("location").orEmpty(),
            maxParticipants = (doc.get("maxParticipants") as? Number)?.toInt() ?: 0,
            goingIds = (doc.get("goingIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            interestedIds = (doc.get("interestedIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            waitlistIds = (doc.get("waitlistIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            invitedUserIds = (doc.get("invitedUserIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            status = status,
            cancellationReason = doc.getString("cancellationReason").orEmpty(),
            createdAtMs = (doc.get("createdAtMs") as? Number)?.toLong() ?: startAtMs,
            updatedAtMs = (doc.get("updatedAtMs") as? Number)?.toLong() ?: startAtMs
        )
    }

    fun rsvpToMap(record: EventRsvpRecord): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "uid" to record.uid,
            "eventId" to record.eventId,
            "displayName" to record.displayName,
            "avatarColor" to record.avatarColor,
            "status" to record.status.name,
            "updatedAtMs" to record.updatedAtMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!record.avatarUrl.isNullOrBlank()) map["avatarUrl"] = record.avatarUrl
        return map
    }

    fun mapDocToRsvp(doc: DocumentSnapshot): EventRsvpRecord? {
        if (!doc.exists()) return null
        val uid = doc.getString("uid") ?: doc.id
        val eventId = doc.getString("eventId") ?: return null
        val statusRaw = doc.getString("status") ?: RsvpStatus.GOING.name
        val status = enumValues<RsvpStatus>().firstOrNull { it.name == statusRaw } ?: RsvpStatus.GOING
        return EventRsvpRecord(
            uid = uid,
            eventId = eventId,
            displayName = doc.getString("displayName").orEmpty().ifBlank { "Participant" },
            avatarUrl = doc.getString("avatarUrl"),
            avatarColor = (doc.get("avatarColor") as? Number)?.toLong() ?: 0xFF5865F2L,
            status = status,
            updatedAtMs = (doc.get("updatedAtMs") as? Number)?.toLong() ?: 0L
        )
    }

    fun reminderToMap(record: EventReminderRecord): Map<String, Any?> = mapOf(
        "id" to record.id,
        "eventId" to record.eventId,
        "userId" to record.userId,
        "eventTitle" to record.eventTitle,
        "minutesBefore" to record.minutesBefore,
        "triggerAtMs" to record.triggerAtMs,
        "triggered" to record.triggered,
        "createdAtMs" to record.createdAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun mapDocToReminder(doc: DocumentSnapshot): EventReminderRecord? {
        if (!doc.exists()) return null
        val eventId = doc.getString("eventId") ?: return null
        val userId = doc.getString("userId") ?: return null
        return EventReminderRecord(
            id = doc.getString("id") ?: doc.id,
            eventId = eventId,
            userId = userId,
            eventTitle = doc.getString("eventTitle").orEmpty(),
            minutesBefore = (doc.get("minutesBefore") as? Number)?.toInt() ?: 15,
            triggerAtMs = (doc.get("triggerAtMs") as? Number)?.toLong() ?: 0L,
            triggered = doc.getBoolean("triggered") ?: false,
            createdAtMs = (doc.get("createdAtMs") as? Number)?.toLong() ?: 0L
        )
    }

    fun historyToMap(entry: EventHistoryEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "eventId" to entry.eventId,
        "actorId" to entry.actorId,
        "actorName" to entry.actorName,
        "actionType" to entry.actionType.name,
        "summary" to entry.summary,
        "timestampLabel" to entry.timestampLabel,
        "createdAtMs" to entry.createdAtMs
    )

    fun mapDocToHistory(doc: DocumentSnapshot): EventHistoryEntry? {
        if (!doc.exists()) return null
        val eventId = doc.getString("eventId") ?: return null
        val actionRaw = doc.getString("actionType") ?: EventActionType.UPDATED.name
        val action = enumValues<EventActionType>().firstOrNull { it.name == actionRaw } ?: EventActionType.UPDATED
        return EventHistoryEntry(
            id = doc.getString("id") ?: doc.id,
            eventId = eventId,
            actorId = doc.getString("actorId").orEmpty(),
            actorName = doc.getString("actorName").orEmpty().ifBlank { "Système" },
            actionType = action,
            summary = doc.getString("summary").orEmpty(),
            timestampLabel = doc.getString("timestampLabel").orEmpty(),
            createdAtMs = (doc.get("createdAtMs") as? Number)?.toLong() ?: 0L
        )
    }
}
