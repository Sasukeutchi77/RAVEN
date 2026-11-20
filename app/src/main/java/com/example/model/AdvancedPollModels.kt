package com.example.model

enum class PollStatus(val label: String) {
    DRAFT("Brouillon"),
    PUBLISHED("En cours"),
    CLOSED("Clôturé")
}

enum class PollVisibility(val label: String, val description: String) {
    PUBLIC("Public", "Visible et ouvert à tous les utilisateurs de RAVEN"),
    SERVER("Communauté", "Réservé aux membres de ce serveur"),
    ROLE_RESTRICTED("Rôles spécifiques", "Réservé à certains rôles sélectionnés")
}

enum class PollDurationPreset(val label: String, val minutes: Int) {
    MIN_15("15 min", 15),
    HOUR_1("1 heure", 60),
    HOURS_6("6 heures", 360),
    HOURS_24("24 heures", 1440),
    DAYS_3("3 jours", 4320),
    DAYS_7("7 jours", 10080),
    NO_LIMIT("Sans limite", 0)
}

enum class PollExportFormat(val label: String, val mimeType: String, val extension: String) {
    SUMMARY_TEXT("Résumé Texte", "text/plain", "txt"),
    CSV("CSV (Tableur)", "text/csv", "csv"),
    JSON("JSON Structuré", "application/json", "json")
}

enum class PollHubFilter(val label: String) {
    ACTIVE("En cours"),
    PUBLIC("Publics"),
    DRAFTS("Brouillons"),
    CLOSED("Terminés"),
    ALL("Tous")
}

data class AdvancedPollOption(
    val id: String = "",
    val text: String = "",
    val voteCount: Int = 0
)

data class PollVoteRecord(
    val pollId: String = "",
    val voterId: String = "",
    val voterName: String = "",
    val selectedOptionIds: List<String> = emptyList(),
    val votedAtMs: Long = 0L
)

data class AdvancedPoll(
    val id: String = "",
    val serverId: String? = null,
    val serverName: String = "",
    val channelId: String? = null,
    val channelName: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val question: String = "",
    val description: String = "",
    val options: List<AdvancedPollOption> = emptyList(),
    val allowsMultiple: Boolean = false,
    val maxChoices: Int = 1,
    val allowVoteChange: Boolean = true,
    val isAnonymous: Boolean = false,
    val visibility: PollVisibility = PollVisibility.SERVER,
    val allowedRoleIds: List<String> = emptyList(),
    val allowedRoleNames: List<String> = emptyList(),
    val memberIds: List<String> = emptyList(),
    val status: PollStatus = PollStatus.DRAFT,
    val durationMinutes: Int = 1440,
    val expiresAtMs: Long? = null,
    val publishedAtMs: Long? = null,
    val closedAtMs: Long? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val totalVotes: Int = 0,
    val totalVoters: Int = 0,
    val notifyOnPublish: Boolean = true,
    val notifyOnClose: Boolean = true,
    val closeNotified: Boolean = false
) {
    val isPublic: Boolean
        get() = visibility == PollVisibility.PUBLIC

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        status == PollStatus.PUBLISHED && expiresAtMs != null && nowMs > expiresAtMs

    fun effectiveStatus(nowMs: Long = System.currentTimeMillis()): PollStatus =
        when {
            status == PollStatus.CLOSED || isExpired(nowMs) -> PollStatus.CLOSED
            else -> status
        }

    fun isEffectivelyOpen(nowMs: Long = System.currentTimeMillis()): Boolean =
        status == PollStatus.PUBLISHED && (expiresAtMs == null || nowMs <= expiresAtMs)

    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long? =
        expiresAtMs?.let { (it - nowMs).coerceAtLeast(0L) }
}

data class PollDraftInput(
    val pollId: String? = null,
    val serverId: String? = null,
    val channelId: String? = null,
    val question: String,
    val description: String = "",
    val options: List<String>,
    val allowsMultiple: Boolean = false,
    val maxChoices: Int = 1,
    val allowVoteChange: Boolean = true,
    val isAnonymous: Boolean = false,
    val visibility: PollVisibility = PollVisibility.SERVER,
    val allowedRoleIds: List<String> = emptyList(),
    val allowedRoleNames: List<String> = emptyList(),
    val durationMinutes: Int = 1440,
    val customExpiresAtMs: Long? = null,
    val notifyOnPublish: Boolean = true,
    val notifyOnClose: Boolean = true
)

data class PollOptionStat(
    val optionId: String,
    val text: String,
    val votes: Int,
    val percentage: Float,
    val isLeader: Boolean,
    val voterNames: List<String> = emptyList()
)

data class PollStatistics(
    val pollId: String,
    val totalVotes: Int,
    val uniqueVoters: Int,
    val eligibleMemberCount: Int,
    val participationRatePercent: Float,
    val winningOptionIds: List<String>,
    val winnerSummary: String,
    val leadMarginVotes: Int,
    val optionStats: List<PollOptionStat>,
    val isAnonymous: Boolean,
    val statusLabel: String
)
