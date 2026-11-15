package com.example.model

enum class NotificationType {
    MENTION,
    REPLY,
    FRIEND_REQUEST,
    LEVEL_UP,
    SYSTEM
}

data class InboxNotification(
    val id: String,
    val title: String,
    val body: String,
    val sourceLabel: String,
    val timestamp: String,
    val type: NotificationType = NotificationType.SYSTEM,
    val isRead: Boolean = false
)

data class CustomRole(
    val id: String,
    val name: String,
    val colorHex: Long,
    val secondaryColorHex: Long? = null,
    val isAdmin: Boolean = false,
    val canKickBan: Boolean = false,
    val canManageChannels: Boolean = false
)

data class AuditLogEntry(
    val id: String,
    val actorName: String,
    val actionDescription: String,
    val targetName: String,
    val timestamp: String
)

enum class AppThemeMode(val label: String) {
    DARK("Sombre Discord"),
    AMOLED("AMOLED Noir Pur"),
    LIGHT("Clair Élégant")
}

enum class ChatDisplayStyle(val label: String) {
    COMPACT("Compact Discord"),
    BUBBLES("Bulles Modernes")
}

enum class AudioEqPreset(val label: String, val subtitle: String) {
    NORMAL("Standard", "Équilibre naturel"),
    STUDIO("Studio HD", "Voix cristalline & présence"),
    RADIO("Radio Pro", "Chaleur FM & compression"),
    BASS_BOOST("Bass Boost", "Basses profondes Jukebox")
}

data class AppSettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val accentColorHex: Long = 0xFF5865F2,
    val chatDisplayStyle: ChatDisplayStyle = ChatDisplayStyle.COMPACT,
    val glassmorphismEnabled: Boolean = true,
    val audioEqPreset: AudioEqPreset = AudioEqPreset.STUDIO,
    val soundEffectsEnabled: Boolean = true,
    val voiceNoiseSuppression: Boolean = true,
    val allowMemberDms: Boolean = true,
    val showActivityStatus: Boolean = true,
    val mentionNotifications: Boolean = true,
    val localNotificationsEnabled: Boolean = true
)

enum class GlobalSearchFilter(val label: String) {
    ALL("Tout"),
    IMAGES("Images"),
    FILES("Fichiers"),
    PINNED("Épinglés")
}

data class GlobalSearchResult(
    val targetId: String,
    val targetTitle: String,
    val serverName: String,
    val isDm: Boolean,
    val message: Message
)

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val addedBy: String,
    val accentHex: Long = 0xFF5865F2,
    val voteSkipUserIds: List<String> = emptyList(),
    val streamUrl: String? = null
)

data class VoiceJukeboxState(
    val isPlaying: Boolean = false,
    val currentTrack: MusicTrack? = null,
    val queue: List<MusicTrack> = emptyList(),
    val elapsedSec: Int = 0
)

data class PublicServerListing(
    val id: String,
    val name: String,
    val iconText: String,
    val iconBgColor: Long,
    val categoryTag: String,
    val description: String,
    val inviteCode: String,
    val memberCount: Int,
    val boostLevel: Int = 1,
    val isVerified: Boolean = true
)

data class ServerShopItem(
    val id: String,
    val title: String,
    val description: String,
    val priceOrbs: Int,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val roleName: String,
    val purchasedByUserIds: List<String> = emptyList()
)

data class WhiteboardStroke(
    val points: List<Pair<Float, Float>>,
    val colorHex: Long,
    val strokeWidth: Float = 6f
)

data class ScheduledMessage(
    val id: String,
    val targetId: String,
    val targetLabel: String,
    val text: String,
    val triggerAtMs: Long,
    val delayLabel: String
)

data class MessageReminder(
    val id: String,
    val messageId: String,
    val targetId: String,
    val senderName: String,
    val previewText: String,
    val triggerAtMs: Long,
    val delayLabel: String
)

data class ServerEvent(
    val id: String,
    val title: String,
    val description: String,
    val dateTimeLabel: String,
    val locationLabel: String,
    val creatorName: String,
    val interestedUserIds: List<String> = emptyList()
)

data class ServerFolder(
    val id: String,
    val name: String,
    val colorHex: Long,
    val serverIds: List<String>,
    val isExpanded: Boolean = true
)

enum class FriendRequestDirection {
    RECEIVED,
    SENT
}

data class FriendRequest(
    val id: String,
    val user: User,
    val direction: FriendRequestDirection,
    val timestamp: String
)

data class SlashCommandOption(
    val command: String,
    val description: String,
    val usageHint: String
)

object SlashCommandCatalog {
    val commands = listOf(
        SlashCommandOption("/sondage", "Créer un sondage interactif", "/sondage Question ? | Choix 1 | Choix 2"),
        SlashCommandOption("/annonce", "Publier une annonce officielle", "/annonce Maintenance ce soir à 22h"),
        SlashCommandOption("/de", "Lancer un dé à N faces", "/de 20"),
        SlashCommandOption("/pileouface", "Jouer à pile ou face", "/pileouface"),
        SlashCommandOption("/xp", "Partager votre niveau et Orbs", "/xp"),
        SlashCommandOption("/clear", "Effacer les messages récents du salon", "/clear")
    )
}

data class StickerItem(
    val id: String,
    val title: String,
    val tagline: String,
    val priceOrbs: Int,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val iconKey: String
)

object StickerCatalog {
    val allStickers = listOf(
        StickerItem("stk_wave", "Salut Raven", "Accueil chaleureux", 0, 0xFF5865F2, 0xFF3B45C4, "wave"),
        StickerItem("stk_gg", "GG Pro", "Bien joué !", 0, 0xFF23A55A, 0xFF15733D, "trophy"),
        StickerItem("stk_hype", "Hyper Vitesse", "Énergie maximale", 0, 0xFFF0B232, 0xFFD97706, "bolt"),
        StickerItem("stk_heart", "Coup de Coeur", "Soutien total", 0, 0xFFEB459E, 0xFF9D174D, "heart"),
        StickerItem("stk_crown", "Roi du Salon", "Couronne Impériale", 120, 0xFFFACC15, 0xFFB45309, "crown"),
        StickerItem("stk_rocket", "Décollage", "Vers la lune !", 150, 0xFF00A8FC, 0xFF1D4ED8, "rocket"),
        StickerItem("stk_shield", "Mode Bouclier", "Protection Admin", 180, 0xFF10B981, 0xFF047857, "shield"),
        StickerItem("stk_fire", "En Feu", "Combo légendaire", 200, 0xFFEF4444, 0xFF991B1B, "fire")
    )
}

fun defaultServerRoles(): List<CustomRole> = listOf(
    CustomRole("role_admin", "Administrateur", 0xFF5865F2, 0xFFEB459E, isAdmin = true, canKickBan = true, canManageChannels = true),
    CustomRole("role_mod", "Modérateur", 0xFF23A55A, 0xFF00D4AA, isAdmin = false, canKickBan = true, canManageChannels = true),
    CustomRole("role_vip", "VIP Orbs", 0xFFF0B232, 0xFFFF5E62, isAdmin = false, canKickBan = false, canManageChannels = false),
    CustomRole("role_member", "Membre", 0xFF949BA4, null, isAdmin = false, canKickBan = false, canManageChannels = false)
)
