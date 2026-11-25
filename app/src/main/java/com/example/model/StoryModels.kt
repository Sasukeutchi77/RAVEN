package com.example.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

enum class StoryMediaType(val label: String) {
    TEXT("Texte"),
    IMAGE("Image"),
    VIDEO("Vidéo");

    companion object {
        fun fromRaw(raw: String?): StoryMediaType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class StoryVisibility(val label: String, val subtitle: String) {
    PUBLIC("Tous les contacts", "Visible par tous les membres de RAVEN"),
    FRIENDS("Amis proches", "Visible uniquement par vos amis acceptés"),
    SERVER_MEMBERS("Membres d'un serveur", "Visible uniquement par les membres du serveur choisi"),
    CUSTOM_USERS("Utilisateurs choisis", "Visible uniquement par une liste personnalisée");

    companion object {
        fun fromRaw(raw: String?): StoryVisibility =
            entries.firstOrNull { it.name == raw } ?: PUBLIC
    }
}

enum class StoryDurationOption(val hours: Int, val label: String) {
    SIX_HOURS(6, "6 heures"),
    TWELVE_HOURS(12, "12 heures"),
    TWENTY_FOUR_HOURS(24, "24 heures"),
    FORTY_EIGHT_HOURS(48, "48 heures"),
    SEVENTY_TWO_HOURS(72, "72 heures")
}

@Keep
@IgnoreExtraProperties
data class StoryViewerEntry(
    val viewerId: String = "",
    val viewerName: String = "",
    val viewerAvatarUrl: String? = null,
    val viewerAvatarColor: Long = 0xFF5865F2L,
    val viewedAtMs: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class StoryItem(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val authorAvatarColor: Long = 0xFF5865F2L,
    val mediaType: StoryMediaType = StoryMediaType.TEXT,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaMimeType: String? = null,
    val mediaSizeBytes: Long = 0L,
    val textContent: String = "",
    val caption: String = "",
    val backgroundColorHex: Long = 0xFF5865F2L,
    val secondaryColorHex: Long = 0xFFEB459EL,
    val textColorHex: Long = 0xFFFFFFFFL,
    val visibility: StoryVisibility = StoryVisibility.PUBLIC,
    val allowedViewerIds: List<String> = emptyList(),
    val allowedServerId: String? = null,
    val durationHours: Int = 24,
    val createdAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val viewCount: Int = 0,
    val viewerIds: List<String> = emptyList(),
    val viewers: List<StoryViewerEntry> = emptyList(),
    val likesCount: Int = 0,
    val likedByIds: List<String> = emptyList(),
    val stickerText: String? = null,
    val stickerStyle: String = "GLASS",
    val quickPollQuestion: String? = null,
    val quickPollOptions: List<String> = emptyList(),
    val quickPollVotes: Map<String, Int> = emptyMap(),
    val quickPollVotedByIds: List<String> = emptyList(),
    val musicTrackTitle: String? = null,
    val musicTrackArtist: String? = null
) {
    fun isExpired(nowMs: Long): Boolean = expiresAtMs <= nowMs

    fun hasBeenViewedBy(uid: String): Boolean =
        uid in viewerIds || viewers.any { it.viewerId == uid }

    fun hasBeenLikedBy(uid: String): Boolean = uid in likedByIds

    fun canBeViewedBy(
        uid: String,
        blockedAuthorIds: Set<String> = emptySet(),
        memberServerIds: Set<String> = emptySet()
    ): Boolean {
        if (authorId == uid) return true
        if (authorId in blockedAuthorIds) return false
        return when (visibility) {
            StoryVisibility.PUBLIC -> true
            StoryVisibility.FRIENDS -> uid in allowedViewerIds
            StoryVisibility.SERVER_MEMBERS ->
                (allowedServerId != null && allowedServerId in memberServerIds) || uid in allowedViewerIds
            StoryVisibility.CUSTOM_USERS -> uid in allowedViewerIds
        }
    }

    fun remainingTimeLabel(nowMs: Long): String {
        val diffMs = (expiresAtMs - nowMs).coerceAtLeast(0L)
        val totalMinutes = diffMs / 60_000L
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours >= 24 -> "Expire dans ${hours / 24}j ${hours % 24}h"
            hours >= 1 -> "Expire dans ${hours}h"
            mins >= 1 -> "Expire dans ${mins}min"
            else -> "Expire bientôt"
        }
    }
}

data class StoryAuthorGroup(
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val authorAvatarColor: Long,
    val stories: List<StoryItem>,
    val unseenCount: Int,
    val hasUnseen: Boolean,
    val latestCreatedAtMs: Long,
    val isCurrentUser: Boolean
)

data class StoryCleanupStats(
    val removedExpiredStories: Int = 0,
    val removedMediaFiles: Int = 0
)

data class StoryPalettePreset(
    val id: String,
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val textColorHex: Long = 0xFFFFFFFFL
)

object StoryPaletteCatalog {
    val presets = listOf(
        StoryPalettePreset("blurple_pink", "Blurple Néon", 0xFF5865F2L, 0xFFEB459EL),
        StoryPalettePreset("sunset_gold", "Couchant Or", 0xFFF97316L, 0xFFDB2777L),
        StoryPalettePreset("emerald_cyber", "Émeraude Cyber", 0xFF10B981L, 0xFF0284C7L),
        StoryPalettePreset("royal_violet", "Violet Impérial", 0xFF7C3AEDL, 0xFF2563EBL),
        StoryPalettePreset("crimson_ember", "Braise Rubis", 0xFFDC2626L, 0xFF7F1D1DL),
        StoryPalettePreset("midnight_slate", "Nuit Raven", 0xFF1E1F22L, 0xFF35373CL)
    )
}
