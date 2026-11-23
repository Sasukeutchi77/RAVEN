package com.example.model

enum class ServerVerificationLevel(
    val label: String,
    val description: String
) {
    NONE("Aucun", "Aucune restriction avant d'écrire."),
    LOW("Faible", "Compte Raven vérifié requis."),
    MEDIUM("Moyen", "Profil actif requis pour envoyer des messages."),
    HIGH("Élevé", "Protection Anti-Raid : membres non vérifiés restreints.")
}

enum class ServerNotificationLevel(
    val label: String,
    val description: String
) {
    ALL_MESSAGES("Tous les messages", "Notifications activées pour chaque message."),
    MENTIONS_ONLY("@Mentions uniquement", "Uniquement mentions, réponses et annonces."),
    NONE("Silencieux", "Aucune notification par défaut.")
}

enum class ServerThemeSurfaceStyle(
    val label: String,
    val previewBgHex: Long
) {
    DARK_SLATE("Ardoise Sombre", 0xFF313338),
    MIDNIGHT_BLUE("Bleu Minuit", 0xFF172554),
    ROYAL_PURPLE("Violet Royal", 0xFF2E1065),
    EMERALD_CYBER("Émeraude Cyber", 0xFF064E3B),
    AMOLED_DARK("Noir Pur AMOLED", 0xFF09090B)
}

data class ChannelIconOption(
    val key: String,
    val label: String
)

object ChannelIconCatalog {
    val options: List<ChannelIconOption> = listOf(
        ChannelIconOption("tag", "Standard #"),
        ChannelIconOption("chat", "Discussion"),
        ChannelIconOption("announcement", "Annonce"),
        ChannelIconOption("game", "Gaming"),
        ChannelIconOption("code", "Dev / Code"),
        ChannelIconOption("music", "Musique"),
        ChannelIconOption("mic", "Vocal"),
        ChannelIconOption("stage", "Scène"),
        ChannelIconOption("rules", "Règlement"),
        ChannelIconOption("shield", "Staff / Mod"),
        ChannelIconOption("star", "VIP / Favoris"),
        ChannelIconOption("event", "Événements"),
        ChannelIconOption("photo", "Médias"),
        ChannelIconOption("help", "Aide / Support"),
        ChannelIconOption("rocket", "Projets")
    )

    val validKeys: Set<String> = options.map { it.key }.toSet()
}

data class ServerThemeConfig(
    val accentColorHex: Long = 0xFF5865F2L,
    val secondaryColorHex: Long = 0xFFEB459EL,
    val surfaceStyle: ServerThemeSurfaceStyle = ServerThemeSurfaceStyle.DARK_SLATE,
    val iconPresetKey: String? = null,
    val backgroundThemeId: String? = null,
    val backgroundImageUrl: String? = null,
    val backgroundOpacity: Float = 0.50f
) {
    fun toChatBackgroundTheme(): ChatBackgroundTheme? {
        val safeOpacity = backgroundOpacity.coerceIn(0.15f, 0.90f)
        if (!backgroundImageUrl.isNullOrBlank()) {
            return ChatBackgroundTheme(
                id = "server_custom_image",
                name = "Fond du serveur",
                primaryColor = accentColorHex,
                secondaryColor = secondaryColorHex,
                imageUrl = backgroundImageUrl,
                opacity = safeOpacity
            )
        }
        val themeId = backgroundThemeId?.takeIf { it.isNotBlank() && it != "default" }
        if (themeId != null) {
            val preset = ShopCatalog.initialChatThemes.find { it.id == themeId }
            if (preset != null) {
                return preset.copy(opacity = safeOpacity)
            }
        }
        if (surfaceStyle != ServerThemeSurfaceStyle.DARK_SLATE) {
            return ChatBackgroundTheme(
                id = "server_surface_${surfaceStyle.name.lowercase()}",
                name = surfaceStyle.label,
                primaryColor = surfaceStyle.previewBgHex,
                secondaryColor = accentColorHex,
                opacity = safeOpacity
            )
        }
        return null
    }
}

fun serverBackgroundTargetKey(serverId: String): String = "server_$serverId"

fun resolveServerBackgroundTheme(
    server: Server,
    channelId: String? = null,
    userBackgrounds: Map<String, ChatBackgroundTheme> = emptyMap()
): ChatBackgroundTheme? {
    if (!channelId.isNullOrBlank()) {
        userBackgrounds[channelId]?.let { return it }
    }
    userBackgrounds[serverBackgroundTargetKey(server.id)]?.let { return it }
    return server.customization.theme.toChatBackgroundTheme()
}

data class ServerWelcomeConfig(
    val welcomeEnabled: Boolean = true,
    val welcomeMessageTemplate: String = "Bienvenue {user} sur {server} ! Présentez-vous et explorez les salons.",
    val welcomeChannelId: String? = null,
    val goodbyeEnabled: Boolean = true,
    val goodbyeMessageTemplate: String = "{user} a quitté {server}.",
    val goodbyeChannelId: String? = null,
    val dmWelcomeEnabled: Boolean = false
)

data class ServerNotificationConfig(
    val defaultLevel: ServerNotificationLevel = ServerNotificationLevel.MENTIONS_ONLY,
    val suppressEveryoneMentions: Boolean = false,
    val suppressRoleMentions: Boolean = false,
    val highlightAnnouncements: Boolean = true
)

data class ServerModerationConfig(
    val autoModEnabled: Boolean = true,
    val blockSpamAndLinks: Boolean = false,
    val blockBadWords: Boolean = true,
    val blockedWords: List<String> = listOf("scam", "phishing", "freenitro"),
    val defaultSlowmodeSeconds: Int = 0,
    val maxMentionsPerMessage: Int = 5,
    val antiRaidModeEnabled: Boolean = false
)

data class ServerPrivacyConfig(
    val discoverableInCatalog: Boolean = true,
    val allowInviteLinks: Boolean = true,
    val requireInviteApproval: Boolean = false,
    val allowMemberDms: Boolean = true,
    val hideMemberListForNonStaff: Boolean = false
)

data class ServerCustomizationSettings(
    val theme: ServerThemeConfig = ServerThemeConfig(),
    val welcome: ServerWelcomeConfig = ServerWelcomeConfig(),
    val notifications: ServerNotificationConfig = ServerNotificationConfig(),
    val verificationLevel: ServerVerificationLevel = ServerVerificationLevel.LOW,
    val requireRulesAcceptance: Boolean = false,
    val moderation: ServerModerationConfig = ServerModerationConfig(),
    val privacy: ServerPrivacyConfig = ServerPrivacyConfig()
)
