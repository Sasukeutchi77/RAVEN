package com.example.model

enum class FriendRequestPolicy(val label: String, val description: String) {
    EVERYONE(
        label = "Tout le monde",
        description = "N'importe quel utilisateur de Raven peut vous envoyer une demande d'ami."
    ),
    MUTUAL_SERVERS(
        label = "Serveurs communs uniquement",
        description = "Seuls les membres partageant au moins un serveur avec vous peuvent vous ajouter."
    ),
    NOBODY(
        label = "Personne",
        description = "Bloque toutes les nouvelles demandes d'amitié entrantes."
    )
}

enum class DirectMessagePrivacyPolicy(val label: String, val description: String) {
    EVERYONE(
        label = "Tout le monde",
        description = "Autorise les messages privés de tous les membres Raven non bloqués."
    ),
    FRIENDS_ONLY(
        label = "Amis uniquement",
        description = "Seuls vos amis confirmés peuvent vous envoyer de nouveaux messages privés."
    ),
    MUTUAL_SERVERS(
        label = "Amis & serveurs communs",
        description = "Autorise vos amis et les membres de vos serveurs communs."
    )
}

data class FriendPrivacySettings(
    val friendRequestPolicy: FriendRequestPolicy = FriendRequestPolicy.EVERYONE,
    val allowDmsFrom: DirectMessagePrivacyPolicy = DirectMessagePrivacyPolicy.EVERYONE,
    val showOnlineStatus: Boolean = true,
    val showLastActivity: Boolean = true,
    val notifyFriendRequests: Boolean = true
)

enum class FriendRelationStatus {
    NONE,
    FRIENDS,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    BLOCKED_BY_ME
}

/**
 * Represents an entry in the current user's own friend list (/users/{uid}/friends/{friendUid}).
 * Another user can never mutate this document.
 */
data class FriendRelationship(
    val friendUid: String,
    val pairId: String,
    val user: User,
    val addedAtMs: Long = System.currentTimeMillis(),
    val lastActiveAtMs: Long = user.lastActiveAtMs,
    val lastActivityLabel: String = user.activity.ifBlank { user.customStatus },
    val privateNote: String = ""
)

/**
 * Search/directory entry aggregated from public_profiles and community members,
 * strictly omitting sensitive PII and respecting the target user's privacy settings.
 */
data class UserDirectoryEntry(
    val user: User,
    val lastActiveAtMs: Long = user.lastActiveAtMs,
    val lastActivityLabel: String = "",
    val friendRequestPolicy: FriendRequestPolicy = FriendRequestPolicy.EVERYONE,
    val allowDmsFrom: DirectMessagePrivacyPolicy = DirectMessagePrivacyPolicy.EVERYONE,
    val showOnlineStatus: Boolean = true,
    val showLastActivity: Boolean = true,
    val mutualServers: List<String> = emptyList(),
    val relationStatus: FriendRelationStatus = FriendRelationStatus.NONE,
    val pendingRequestId: String? = null,
    val privateNote: String = ""
)

data class FriendUiState(
    val friends: List<FriendRelationship> = emptyList(),
    val onlineFriends: List<FriendRelationship> = emptyList(),
    val incomingRequests: List<FriendRequest> = emptyList(),
    val outgoingRequests: List<FriendRequest> = emptyList(),
    val blockedUsers: List<User> = emptyList(),
    val directoryResults: List<UserDirectoryEntry> = emptyList(),
    val privacySettings: FriendPrivacySettings = FriendPrivacySettings(),
    val searchQuery: String = "",
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)
