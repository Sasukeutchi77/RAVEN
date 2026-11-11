package com.example.model

enum class ShopCategory(val title: String) {
    ALL("Tout"),
    AVATAR_DECORATION("Décorations d'avatar"),
    PROFILE_EFFECT("Effets de profil"),
    CHAT_THEME("Thèmes de chat"),
    BADGES("Badges & Nitro")
}

data class ShopItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String,
    val category: ShopCategory,
    val priceOrbs: Int,
    val rarity: BadgeRarity = BadgeRarity.SPECIAL,
    val decorationKey: String,
    val isPurchased: Boolean = false,
    val isEquipped: Boolean = false
)

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val rewardOrbs: Int,
    val currentProgress: Int,
    val maxProgress: Int,
    val isCompleted: Boolean = false,
    val isClaimed: Boolean = false
)

data class ChatBackgroundTheme(
    val id: String,
    val name: String,
    val primaryColor: Long,
    val secondaryColor: Long? = null,
    val imageUrl: String? = null,
    val opacity: Float = 0.35f
)

data class PollOption(
    val id: String,
    val text: String,
    val votes: List<String> = emptyList()
)

data class Poll(
    val question: String,
    val options: List<PollOption>,
    val allowsMultiple: Boolean = false,
    val durationLabel: String = "Se termine dans 24h",
    val isClosed: Boolean = false
)

object ShopCatalog {
    val initialChatThemes = listOf(
        ChatBackgroundTheme("default", "Discord Slate (Défaut)", 0xFF313338, 0xFF2B2D31),
        ChatBackgroundTheme("nebula", "Brouillard Magique", 0xFF1A102F, 0xFF4A154B, opacity = 0.45f),
        ChatBackgroundTheme("infinity", "Tourbillon Infini", 0xFF0D2538, 0xFF1A365D, opacity = 0.45f),
        ChatBackgroundTheme("cyberpunk", "Néon Cyberpunk", 0xFF2D124D, 0xFF791E88, opacity = 0.40f),
        ChatBackgroundTheme("midnight", "Minuit Corbeau", 0xFF0B0D0F, 0xFF14171A, opacity = 0.50f),
        ChatBackgroundTheme("emerald", "Forêt Mystique", 0xFF0A231C, 0xFF134E3F, opacity = 0.40f)
    )

    val initialShopItems = listOf(
        ShopItem(
            id = "dec_magic_mist",
            title = "Brouillard magique",
            description = "Aura mystique tournoyante aux teintes violettes et cyan.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 350,
            rarity = BadgeRarity.EPIC,
            decorationKey = "magic_mist",
            isPurchased = true,
            isEquipped = true
        ),
        ShopItem(
            id = "dec_infinity_swirl",
            title = "Tourbillon infini",
            description = "Spirale dimensionnelle aux éclats d'énergie céleste.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 500,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "infinity_swirl",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_cyber_halo",
            title = "Couronne Cyberpunk",
            description = "Anneaux néons holographiques scintillants.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 300,
            rarity = BadgeRarity.RARE,
            decorationKey = "cyber_halo",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_raven_wings",
            title = "Ailes du Corbeau",
            description = "Plumes d'ombres éthérées enveloppant votre profil.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 450,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "raven_wings",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "eff_supernova",
            title = "Effet Supernova",
            description = "Explosion d'étoiles filantes sur la bannière de profil.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 400,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_supernova",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "theme_nebula",
            title = "Fond de chat Brouillard Magique",
            description = "Décorez vos salons d'un thème cosmique vibrant.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "nebula",
            isPurchased = true,
            isEquipped = false
        ),
        ShopItem(
            id = "theme_cyber",
            title = "Fond de chat Cyberpunk",
            description = "Ambiance nocturne futuriste pour vos discussions.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "cyberpunk",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "badge_orbs_neophyte",
            title = "Badge Néophyte des Orbs",
            subtitle = "Soutien de la boutique",
            description = "Badge exclusif affiché sur votre profil Raven certifiant vos exploits d'Orbs.",
            category = ShopCategory.BADGES,
            priceOrbs = 150,
            rarity = BadgeRarity.RARE,
            decorationKey = "badge_orbs",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "nitro_3days",
            title = "Crédits Nitro : 3 jours",
            description = "Débloquez les fonctionnalités Nitro et super réactions pendant 3 jours.",
            category = ShopCategory.BADGES,
            priceOrbs = 600,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "nitro_credits",
            isPurchased = false,
            isEquipped = false
        )
    )

    val initialQuests = listOf(
        Quest(
            id = "q_msg",
            title = "Bavard de salon",
            description = "Envoyez 3 messages dans un salon textuel ou privé.",
            rewardOrbs = 150,
            currentProgress = 3,
            maxProgress = 3,
            isCompleted = true,
            isClaimed = false
        ),
        Quest(
            id = "q_voice",
            title = "Discussion Vocale",
            description = "Rejoignez un salon vocal et restez connecté au moins 1 minute.",
            rewardOrbs = 250,
            currentProgress = 1,
            maxProgress = 1,
            isCompleted = true,
            isClaimed = false
        ),
        Quest(
            id = "q_profile",
            title = "Créateur de style",
            description = "Personnalisez votre fond de discussion ou votre bannière.",
            rewardOrbs = 200,
            currentProgress = 0,
            maxProgress = 1,
            isCompleted = false,
            isClaimed = false
        ),
        Quest(
            id = "q_friend",
            title = "Cercle d'amis",
            description = "Ajoutez un ami avec son tag #0000.",
            rewardOrbs = 300,
            currentProgress = 1,
            maxProgress = 1,
            isCompleted = true,
            isClaimed = false
        )
    )
}
