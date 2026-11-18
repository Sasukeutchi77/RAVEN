package com.example.model

enum class RolePermission(val label: String, val description: String) {
    MANAGE_COMMUNITY("Gérer la communauté", "Modifier le nom, l'image, la bannière, la description et la visibilité"),
    DELETE_COMMUNITY("Supprimer la communauté", "Supprimer définitivement la communauté (Créateur)"),
    MANAGE_ROLES("Gérer les rôles", "Créer, modifier et attribuer les rôles et permissions"),
    MANAGE_CHANNELS("Gérer salons & catégories", "Créer, organiser ou supprimer des salons et catégories"),
    MANAGE_INVITES("Gérer les invitations", "Créer, suivre et révoquer des codes d'invitation"),
    MANAGE_EVENTS("Gérer les événements", "Planifier et supprimer des événements communautaires"),
    POST_ANNOUNCEMENTS("Publier des annonces", "Publier et épingler des annonces officielles"),
    MODERATE_MEMBERS("Modérer les membres", "Timeout ou expulser des membres"),
    SEND_MESSAGES("Envoyer des messages", "Participer aux discussions textuelles"),
    CONNECT_VOICE("Rejoindre le vocal", "Se connecter aux salons vocaux et scènes"),
    SPEAK_VOICE("Parler en vocal", "Activer le microphone dans les salons vocaux"),
    STREAM_VIDEO("Partager sa caméra", "Diffuser son flux vidéo dans les salons vocaux"),
    SHARE_SCREEN("Partager son écran", "Diffuser son écran en direct dans un salon vocal"),
    MANAGE_VOICE("Modérer le vocal", "Verrouiller un salon vocal, exclure ou limiter l'accès")
}

fun defaultPermissionsForFlags(
    isAdmin: Boolean,
    canKickBan: Boolean,
    canManageChannels: Boolean
): Set<RolePermission> = buildSet {
    add(RolePermission.SEND_MESSAGES)
    add(RolePermission.CONNECT_VOICE)
    add(RolePermission.SPEAK_VOICE)
    add(RolePermission.STREAM_VIDEO)
    add(RolePermission.SHARE_SCREEN)
    if (canKickBan) {
        add(RolePermission.MODERATE_MEMBERS)
        add(RolePermission.MANAGE_INVITES)
        add(RolePermission.MANAGE_EVENTS)
        add(RolePermission.POST_ANNOUNCEMENTS)
        add(RolePermission.MANAGE_VOICE)
    }
    if (canManageChannels) {
        add(RolePermission.MANAGE_CHANNELS)
        add(RolePermission.MANAGE_VOICE)
    }
    if (isAdmin) {
        addAll(RolePermission.entries.filter { it != RolePermission.DELETE_COMMUNITY })
    }
}

data class ChannelCategory(
    val id: String = "",
    val name: String = "",
    val position: Int = 0,
    val isPrivate: Boolean = false,
    val allowedRoleIds: List<String> = emptyList()
)

data class CommunityInvite(
    val id: String = "",
    val code: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val maxUses: Int = 0, // 0 = illimité
    val usesCount: Int = 0,
    val expiresInHours: Int = 0, // 0 = n'expire jamais
    val expiresAtMs: Long? = null,
    val isRevoked: Boolean = false,
    val createdAtLabel: String = "À l'instant"
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs != null && nowMs > expiresAtMs

    fun isExhausted(): Boolean = maxUses > 0 && usesCount >= maxUses

    fun isUsable(nowMs: Long = System.currentTimeMillis()): Boolean =
        !isRevoked && !isExpired(nowMs) && !isExhausted()
}

data class CommunityAnnouncement(
    val id: String = "",
    val serverId: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRoleTitle: String = "Administrateur",
    val isPinned: Boolean = true,
    val timestampLabel: String = "",
    val createdAtMs: Long = 0L
)

data class CommunityMember(
    val uid: String = "",
    val serverId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val avatarColor: Long = 0xFF5865F2,
    val role: UserRole = UserRole.MEMBER,
    val customRoleId: String? = null,
    val permissions: Set<RolePermission> = setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE),
    val status: UserStatus = UserStatus.ONLINE,
    val isTimedOut: Boolean = false,
    val xp: Int = 0,
    val level: Int = 1,
    val clanTag: String? = null,
    val joinedAtMs: Long = 0L
)

enum class CommunityVisibility(val label: String, val isPublic: Boolean) {
    PUBLIC("Publique (visible dans la recherche)", true),
    PRIVATE("Privée (sur invitation uniquement)", false)
}

enum class CommunitySearchFilter(val label: String) {
    ALL("Toutes"),
    PUBLIC_ONLY("Publiques"),
    VERIFIED("Vérifiées"),
    MY_COMMUNITIES("Mes communautés")
}
