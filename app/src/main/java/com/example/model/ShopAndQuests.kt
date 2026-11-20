package com.example.model

enum class ShopCategory(val title: String) {
    ALL("Tout"),
    AVATAR_DECORATION("Décorations d'avatar"),
    ELEMENTAL("Feu & Foudre 🔥⚡"),
    ANIME_SAKURA("Sakura & Dragon 🌸🐉"),
    CYBER_MECHA("Cyber & Glitch 🤖👾"),
    DIVINE_WINGS("Ailes & Couronnes 🪽👑"),
    PROFILE_EFFECT("Effets de profil ✨"),
    CHAT_THEME("Thèmes de chat 💬"),
    BADGES("Badges & Nitro 💎")
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
    val isEquipped: Boolean = false,
    val isFavorite: Boolean = false
)

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val rewardOrbs: Int,
    val currentProgress: Int,
    val maxProgress: Int,
    val isCompleted: Boolean = false,
    val isClaimed: Boolean = false,
    val actionLabel: String = "Effectuer la quête"
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
    val isClosed: Boolean = false,
    val advancedPollId: String? = null
)

object ShopCatalog {
    val initialChatThemes = listOf(
        ChatBackgroundTheme("default", "Discord Slate (Défaut)", 0xFF313338, 0xFF2B2D31),
        ChatBackgroundTheme("nebula", "Brouillard Magique", 0xFF1A102F, 0xFF4A154B, opacity = 0.45f),
        ChatBackgroundTheme("infinity", "Tourbillon Infini", 0xFF0D2538, 0xFF1A365D, opacity = 0.45f),
        ChatBackgroundTheme("cyberpunk", "Néon Cyberpunk", 0xFF2D124D, 0xFF791E88, opacity = 0.40f),
        ChatBackgroundTheme("phoenix", "Fournaise Phénix", 0xFF350E04, 0xFF7C2D12, opacity = 0.45f),
        ChatBackgroundTheme("sakura", "Jardin Zen Sakura", 0xFF2D0B24, 0xFF831843, opacity = 0.40f),
        ChatBackgroundTheme("midnight", "Minuit Corbeau", 0xFF0B0D0F, 0xFF14171A, opacity = 0.50f),
        ChatBackgroundTheme("emerald", "Forêt Mystique", 0xFF0A231C, 0xFF134E3F, opacity = 0.40f)
    )

    val initialShopItems = listOf(
        // Screenshot items
        ShopItem(
            id = "nitro_3days",
            title = "Crédits Nitro : 3 jours",
            subtitle = "Avantage Discord",
            description = "Débloquez les fonctionnalités Nitro et super réactions pendant 3 jours.",
            category = ShopCategory.BADGES,
            priceOrbs = 600,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "nitro_credits",
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
            id = "bundle_swirl_pack",
            title = "3 articles Pack tourbillon",
            subtitle = "Pack Collector Élite",
            description = "Contient le fond cosmique, la décoration Tourbillon et l'effet de profil stellaire.",
            category = ShopCategory.ALL,
            priceOrbs = 800,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "infinity_swirl",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "theme_nebula",
            title = "Brouillard magique",
            subtitle = "Thème de discussion",
            description = "Décorez vos salons d'un thème cosmique vibrant violet et nébuleux.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "nebula",
            isPurchased = true,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_magic_mist",
            title = "Brouillard magique",
            subtitle = "Décoration d'avatar",
            description = "Aura mystique tournoyante aux teintes violettes et cyan.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 350,
            rarity = BadgeRarity.EPIC,
            decorationKey = "magic_mist",
            isPurchased = true,
            isEquipped = true
        ),
        ShopItem(
            id = "theme_infinity",
            title = "Tourbillon infini",
            subtitle = "Thème de discussion",
            description = "Vagues d'énergie céleste et dégradé profond pour vos salons.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "infinity",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_infinity_swirl",
            title = "Tourbillon infini",
            subtitle = "Décoration d'avatar",
            description = "Spirale dimensionnelle aux éclats d'énergie céleste.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 500,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "infinity_swirl",
            isPurchased = false,
            isEquipped = false
        ),
        // 🔥 ELEMENTAL
        ShopItem(
            id = "dec_phoenix_flames",
            title = "Flammes du Phénix",
            subtitle = "Aura élémentaire de feu",
            description = "Brasier ardent tourbillonnant avec braises incandescentes et halo de chaleur dorée.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 480,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "phoenix_flames",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_lightning_storm",
            title = "Foudre Divine & Tempête",
            subtitle = "Éclairs à haute tension",
            description = "Arcs électriques violets et bleus crépitants avec décharges d'énergie pure.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 420,
            rarity = BadgeRarity.EPIC,
            decorationKey = "lightning_storm",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_spectral_soul",
            title = "Flammes de l'Âme Émeraude",
            subtitle = "Aura spectrale mystique",
            description = "Feu follet vert émeraude et volutes fantomatiques dansant autour de l'avatar.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 380,
            rarity = BadgeRarity.RARE,
            decorationKey = "spectral_soul",
            isPurchased = false,
            isEquipped = false
        ),
        // 🌸 SAKURA & ASTRAL
        ShopItem(
            id = "dec_sakura_blossom",
            title = "Sakura Céleste",
            subtitle = "Pétales de cerisier en vol",
            description = "Pétales de fleurs de cerisier roses flottant en spirale avec poussière d'étoiles.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 360,
            rarity = BadgeRarity.RARE,
            decorationKey = "sakura_blossom",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_astral_dragon",
            title = "Dragon Astral",
            subtitle = "Créature mythologique",
            description = "Dragon spectral cyan et or enroulé majestueusement autour de votre profil.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 550,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "astral_dragon",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_black_hole",
            title = "Trou Noir & Supernova",
            subtitle = "Disque d'accrétion galactique",
            description = "Lentille gravitationnelle courbant l'espace-temps avec plasma violet et or.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 520,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "black_hole",
            isPurchased = false,
            isEquipped = false
        ),
        // 🤖 CYBER & MECHA
        ShopItem(
            id = "dec_cyber_mecha",
            title = "Casque Mecha & Visière HUD",
            subtitle = "Technologie militaire anime",
            description = "Oreilles de mecha robotiques avec visière holographique et réticule de visée laser.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 440,
            rarity = BadgeRarity.EPIC,
            decorationKey = "cyber_mecha",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_glitch_matrix",
            title = "Glitch Holographique",
            subtitle = "Anomalie numérique cyber",
            description = "Décalage chromatique RVB avec pluie de code matrix et distorsion cyberpunk.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 390,
            rarity = BadgeRarity.RARE,
            decorationKey = "glitch_matrix",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_rgb_headset",
            title = "Casque Gamer RGB Néon",
            subtitle = "Accessoire streamer pro",
            description = "Casque audio avec spectre arc-en-ciel RGB pulsant en rythme.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 310,
            rarity = BadgeRarity.RARE,
            decorationKey = "rgb_headset",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_cyber_halo",
            title = "Couronne Cyberpunk",
            subtitle = "Spires holographiques",
            description = "Anneaux néons technologiques et spires lasers au-dessus du profil.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 300,
            rarity = BadgeRarity.RARE,
            decorationKey = "cyber_halo",
            isPurchased = false,
            isEquipped = false
        ),
        // 🪽 DIVINE & WINGS
        ShopItem(
            id = "dec_archangel_wings",
            title = "Ailes d'Archange Dorées",
            subtitle = "Ailes divines majestueuses",
            description = "Grandes ailes d'ange dorées aux plumes saintes avec auréole céleste flottante.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 580,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "archangel_wings",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_raven_wings",
            title = "Ailes du Corbeau Raven",
            subtitle = "Plumes d'ébène et violet",
            description = "Plumes d'ombres éthérées battant doucement autour de votre profil.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 450,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "raven_wings",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_royal_crown",
            title = "Couronne Royale en Diamants",
            subtitle = "Symbole de souveraineté",
            description = "Diadème d'or impérial incrusté de gemmes scintillantes et rayons solaires.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 500,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "royal_crown",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "dec_aurora_frost",
            title = "Givre Boréal & Flocons",
            subtitle = "Aura polaire de glace",
            description = "Rideau d'aurores boréales vertes et cyan avec cristaux de glace hexagonaux.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 370,
            rarity = BadgeRarity.RARE,
            decorationKey = "aurora_frost",
            isPurchased = false,
            isEquipped = false
        ),
        // ✨ PROFILE EFFECTS
        ShopItem(
            id = "eff_sakura",
            title = "Pluie de pétales Sakura",
            subtitle = "Effet de bannière de profil",
            description = "Une douce pluie de fleurs de cerisier recouvre élégamment votre bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 290,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_sakura",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "eff_supernova",
            title = "Explosion Supernova",
            subtitle = "Effet cosmique stellaire",
            description = "Pluie de météores scintillants et nova d'énergie sur votre profil.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 350,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_supernova",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "eff_lightning",
            title = "Orage d'Éclairs & Foudre",
            subtitle = "Effet dynamique d'orage",
            description = "Flashs d'éclairs spectaculaires illuminant l'arrière-plan de votre profil.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 320,
            rarity = BadgeRarity.RARE,
            decorationKey = "eff_lightning",
            isPurchased = false,
            isEquipped = false
        ),
        ShopItem(
            id = "eff_phoenix",
            title = "Fournaise de Braises",
            subtitle = "Effet incandescent de feu",
            description = "Braises brûlantes et étincelles s'élevant continuellement le long de la bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 320,
            rarity = BadgeRarity.RARE,
            decorationKey = "eff_phoenix",
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
            currentProgress = 1,
            maxProgress = 3,
            isCompleted = false,
            isClaimed = false,
            actionLabel = "Valider +XP"
        ),
        Quest(
            id = "q_sticker",
            title = "Expressif & Sticker",
            description = "Partagez un Sticker ou une réaction emoji.",
            rewardOrbs = 120,
            currentProgress = 0,
            maxProgress = 1,
            isCompleted = false,
            isClaimed = false,
            actionLabel = "Valider"
        ),
        Quest(
            id = "q_voice",
            title = "Discussion Vocale",
            description = "Rejoignez un salon vocal ou une conférence Stage.",
            rewardOrbs = 250,
            currentProgress = 0,
            maxProgress = 1,
            isCompleted = false,
            isClaimed = false,
            actionLabel = "Valider"
        ),
        Quest(
            id = "q_profile",
            title = "Créateur de style",
            description = "Personnalisez votre fond de discussion, profil ou Boost.",
            rewardOrbs = 200,
            currentProgress = 0,
            maxProgress = 1,
            isCompleted = false,
            isClaimed = false,
            actionLabel = "Valider"
        ),
        Quest(
            id = "q_poll",
            title = "Voix de la communauté",
            description = "Votez dans un sondage ou inscrivez-vous à un événement.",
            rewardOrbs = 180,
            currentProgress = 0,
            maxProgress = 1,
            isCompleted = false,
            isClaimed = false,
            actionLabel = "Valider"
        )
    )
}
