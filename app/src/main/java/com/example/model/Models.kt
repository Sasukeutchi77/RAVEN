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
    ANNOUNCEMENT
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
    val isDeafened: Boolean = false
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
    val isPinned: Boolean = false,
    val mentions: List<String> = emptyList(),
    val poll: Poll? = null
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
    val allowedRoles: List<UserRole> = emptyList(),
    val activeVoiceUsers: List<User> = emptyList()
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
    val ownerId: String = ""
)

data class DirectConversation(
    val id: String = "",
    val friend: User = User(),
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0
)
