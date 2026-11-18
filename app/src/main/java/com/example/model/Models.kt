package com.example.model

enum class UserStatus(val label: String) {
    ONLINE("En ligne"),
    IDLE("Inactif"),
    DND("Ne pas déranger"),
    OFFLINE("Hors ligne")
}

enum class UserRole(val title: String) {
    ADMIN("Administrateur"),
    MODERATOR("Modérateur"),
    MEMBER("Membre"),
    BOT("BOT")
}

enum class ChannelType {
    TEXT,
    VOICE,
    ANNOUNCEMENT,
    STAGE
}

data class User(
    val id: String = "",
    val name: String = "",
    val tag: String = "#0001",
    val avatarColor: Long = 0xFF404249,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bannerColor: Long = 0xFF5865F2,
    val bio: String = "Développeur & membre de Raven 🦅. Passionné de code et de discussion vocale !",
    val pronouns: String = "il/lui",
    val badges: List<String> = listOf("ACTIVE_DEV", "NITRO", "BOOSTER", "EARLY_SUPPORTER"),
    val memberSince: String = "10 nov. 2024",
    val activity: String = "Code sur Raven Android",
    val email: String? = "jarvis@raven.app",
    val phone: String? = "+33 6 ** ** ** 42",
    val status: UserStatus = UserStatus.ONLINE,
    val customStatus: String = "",
    val customStatusEmoji: String = "code",
    val avatarDecoration: String? = "magic_mist",
    val profileEffect: String? = null,
    val orbsBalance: Int = 1000,
    val role: UserRole = UserRole.MEMBER,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val xp: Int = 140,
    val level: Int = 2,
    val isTimedOut: Boolean = false,
    val customRoleId: String? = null,
    val clanTag: String? = "RVN",
    val clanBadgeIcon: String = "bolt",
    val unlockedStickers: List<String> = listOf("stk_wave", "stk_gg", "stk_hype", "stk_heart")
)

data class Reaction(
    val emoji: String = "",
    val count: Int = 0,
    val userReacted: Boolean = false
)

data class Message(
    val id: String = "",
    val channelId: String = "",
    val sender: User = User(),
    val text: String = "",
    val timestamp: String = "",
    val replyToText: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    val attachmentSizeLabel: String? = null,
    val stickerId: String? = null,
    val isSystemAnnouncement: Boolean = false,
    val isPinned: Boolean = false,
    val mentions: List<String> = emptyList(),
    val poll: Poll? = null,
    val isEdited: Boolean = false,
    val threadCount: Int = 0
)

data class Channel(
    val id: String = "",
    val serverId: String = "",
    val name: String = "",
    val type: ChannelType = ChannelType.TEXT,
    val category: String = "SALONS TEXTUELS",
    val topic: String = "",
    val unreadCount: Int = 0,
    val isPrivate: Boolean = false,
    val isLocked: Boolean = false,
    val maxParticipants: Int = 0, // 0 = illimité
    val slowmodeSeconds: Int = 0,
    val isReadOnly: Boolean = false,
    val allowedRoles: List<UserRole> = emptyList(),
    val allowedRoleIds: List<String> = emptyList(),
    val bitrateKbps: Int = 64,
    val kickedVoiceUids: List<String> = emptyList(),
    val activeVoiceUsers: List<User> = emptyList(),
    val stageSpeakerIds: List<String> = emptyList(),
    val stageRaisedHandIds: List<String> = emptyList()
)

data class Server(
    val id: String = "",
    val name: String = "",
    val iconText: String = "",
    val iconBgColor: Long = 0xFF35373C,
    val channels: List<Channel> = emptyList(),
    val members: List<User> = emptyList(),
    val unreadCount: Int = 0,
    val isDmTab: Boolean = false,
    val ownerId: String = "",
    val inviteCode: String = "RAVEN-2026",
    val boostCount: Int = 0,
    val boostLevel: Int = 0,
    val customBannerColorHex: Long? = null,
    val isPublic: Boolean = false,
    val categoryTag: String = "Communauté",
    val description: String = "Serveur communautaire Raven.",
    val shopItems: List<ServerShopItem> = emptyList(),
    val customRoles: List<CustomRole> = defaultServerRoles(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val events: List<ServerEvent> = emptyList(),
    val uniqueHandle: String = "",
    val imageUrl: String? = null,
    val bannerUrl: String? = null,
    val rulesGuidelines: String = "Respect, entraide et bienveillance entre membres.",
    val homeWelcomeMessage: String = "Bienvenue dans la communauté ! Explorez les salons, les annonces et les événements à venir.",
    val adminIds: List<String> = emptyList(),
    val moderatorIds: List<String> = emptyList(),
    val categories: List<ChannelCategory> = emptyList(),
    val invites: List<CommunityInvite> = emptyList(),
    val announcements: List<CommunityAnnouncement> = emptyList(),
    val isVerified: Boolean = false
)

data class DirectConversation(
    val id: String = "",
    val friend: User = User(),
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupMembers: List<User> = emptyList(),
    val groupIconColor: Long = 0xFF5865F2
)
