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
    val isFavorite: Boolean = false,
    val previewColors: List<Long> = emptyList(),
    val isNew: Boolean = false,
    val isLimited: Boolean = false,
    val badgeId: String? = null,
    val bundleItemIds: List<String> = emptyList(),
    val tierTag: String = ""
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
    val avatarDecorationCategories = setOf(
        ShopCategory.AVATAR_DECORATION,
        ShopCategory.ELEMENTAL,
        ShopCategory.ANIME_SAKURA,
        ShopCategory.CYBER_MECHA,
        ShopCategory.DIVINE_WINGS
    )

    val initialChatThemes = listOf(
        ChatBackgroundTheme("default", "Ardoise Raven (Défaut)", 0xFF313338, 0xFF2B2D31, opacity = 0.35f),
        ChatBackgroundTheme("nebula", "Brouillard Magique", 0xFF1A102F, 0xFF4A154B, opacity = 0.55f),
        ChatBackgroundTheme("infinity", "Tourbillon Infini", 0xFF0D2538, 0xFF1A365D, opacity = 0.55f),
        ChatBackgroundTheme("cyberpunk", "Néon Cyberpunk", 0xFF2D124D, 0xFF791E88, opacity = 0.50f),
        ChatBackgroundTheme("phoenix", "Fournaise Phénix", 0xFF350E04, 0xFF7C2D12, opacity = 0.55f),
        ChatBackgroundTheme("sakura", "Jardin Zen Sakura", 0xFF2D0B24, 0xFF831843, opacity = 0.50f),
        ChatBackgroundTheme("midnight", "Minuit Corbeau", 0xFF0B0D0F, 0xFF14171A, opacity = 0.60f),
        ChatBackgroundTheme("emerald", "Forêt Mystique", 0xFF0A231C, 0xFF134E3F, opacity = 0.50f),
        ChatBackgroundTheme("aurora", "Aurore Boréale", 0xFF042F2E, 0xFF312E81, opacity = 0.55f),
        ChatBackgroundTheme("ocean_abyss", "Océan Abyssal", 0xFF082F49, 0xFF0F172A, opacity = 0.55f),
        ChatBackgroundTheme("gold_carbon", "Or & Carbone", 0xFF1C1917, 0xFF78350F, opacity = 0.50f),
        ChatBackgroundTheme("sunset_velvet", "Coucher de Soleil", 0xFF4C0519, 0xFF9A3412, opacity = 0.50f)
    )

    val initialShopItems = listOf(
        // Badges & Pass
        ShopItem(
            id = "nitro_3days",
            title = "Pass Nitro Crystal : 3 jours",
            subtitle = "Privilège & Badge Nitro",
            description = "Active les super-réactions, l'aura Nitro dorée sur le profil et le badge Raven Nitro.",
            category = ShopCategory.BADGES,
            priceOrbs = 600,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "nitro_credits",
            previewColors = listOf(0xFF6366F1, 0xFFEC4899, 0xFFF472B6),
            badgeId = "NITRO",
            tierTag = "Privilège"
        ),
        ShopItem(
            id = "badge_orbs_neophyte",
            title = "Badge Néophyte des Orbs",
            subtitle = "Médaillon Prismatique",
            description = "Badge cristallin facetté affiché sur votre profil Raven certifiant vos exploits d'Orbs.",
            category = ShopCategory.BADGES,
            priceOrbs = 150,
            rarity = BadgeRarity.RARE,
            decorationKey = "badge_orbs",
            previewColors = listOf(0xFF38BDF8, 0xFFC084FC, 0xFFEC4899),
            badgeId = "ORBS_NEOPHYTE",
            tierTag = "Profil"
        ),
        ShopItem(
            id = "badge_cyber_legend",
            title = "Badge Légende Cyber-Orbs",
            subtitle = "Insigne Holographique",
            description = "Emblème cybernétique néon gravé aux couleurs des maîtres technologiques Raven.",
            category = ShopCategory.BADGES,
            priceOrbs = 280,
            rarity = BadgeRarity.EPIC,
            decorationKey = "badge_cyber_legend",
            previewColors = listOf(0xFF00F0FF, 0xFF6366F1, 0xFF10B981),
            isNew = true,
            badgeId = "CYBER_LEGEND",
            tierTag = "Hologramme"
        ),
        ShopItem(
            id = "badge_void_monarch",
            title = "Badge Monarque du Néant",
            subtitle = "Sceau Impérial Astral",
            description = "Couronne solaire éclipsée en or noir et rubis, symbole suprême des collectionneurs.",
            category = ShopCategory.BADGES,
            priceOrbs = 450,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "badge_void_monarch",
            previewColors = listOf(0xFFF59E0B, 0xFFDC2626, 0xFF9333EA),
            isNew = true,
            isLimited = true,
            badgeId = "VOID_MONARCH",
            tierTag = "Prestige"
        ),

        // Collector Bundles (ALL category packs)
        ShopItem(
            id = "bundle_swirl_pack",
            title = "Pack Tourbillon Cosmique (3x)",
            subtitle = "Déco + Effet + Thème",
            description = "Inclut la décoration Tourbillon Infini, l'effet de bannière Supernova et le thème de salon céleste.",
            category = ShopCategory.ALL,
            priceOrbs = 780,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "infinity_swirl",
            previewColors = listOf(0xFF38BDF8, 0xFF818CF8, 0xFFEC4899),
            bundleItemIds = listOf("theme_infinity", "dec_infinity_swirl", "eff_supernova"),
            tierTag = "Pack -25%"
        ),
        ShopItem(
            id = "bundle_kitsune_sakura",
            title = "Pack Kitsune & Sakura (3x)",
            subtitle = "Collection Spirituelle",
            description = "Débloque d'un coup le Masque Kitsune Mystique, la pluie de pétales Sakura et le thème Jardin Zen.",
            category = ShopCategory.ALL,
            priceOrbs = 850,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "kitsune_spirit",
            previewColors = listOf(0xFFF472B6, 0xFFEF4444, 0xFFFDE047),
            isNew = true,
            isLimited = true,
            bundleItemIds = listOf("dec_kitsune_spirit", "eff_sakura", "theme_sakura"),
            tierTag = "Pack -20%"
        ),
        ShopItem(
            id = "bundle_cyber_overdrive",
            title = "Pack Cyber Overdrive (4x)",
            subtitle = "Édition Hacker Néon",
            description = "Réunit Prisme Holographique, Pluie Matrix Néon, Thème Cyberpunk et le badge Légende Cyber.",
            category = ShopCategory.ALL,
            priceOrbs = 890,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "prism_hologram",
            previewColors = listOf(0xFF00F0FF, 0xFF10B981, 0xFF8B5CF6),
            isNew = true,
            badgeId = "CYBER_LEGEND",
            bundleItemIds = listOf("dec_prism_hologram", "eff_cyber_rain", "theme_cyberpunk", "badge_cyber_legend"),
            tierTag = "Pack -30%"
        ),
        ShopItem(
            id = "bundle_celestial_emperor",
            title = "Pack Empereur Céleste (4x)",
            subtitle = "Édition Collector Suprême",
            description = "Éclipse Écarlate, Plumes Célestes Dorées, thème Or & Carbone Prestige et badge Monarque du Néant.",
            category = ShopCategory.ALL,
            priceOrbs = 980,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "eclipse_crimson",
            previewColors = listOf(0xFFFDE047, 0xFFDC2626, 0xFF1C1917),
            isNew = true,
            isLimited = true,
            badgeId = "VOID_MONARCH",
            bundleItemIds = listOf("dec_eclipse_crimson", "eff_golden_feathers", "theme_gold_carbon", "badge_void_monarch"),
            tierTag = "Ultime"
        ),

        // Avatar Decorations - Signature Cosmic
        ShopItem(
            id = "dec_magic_mist",
            title = "Brouillard Magique Pro",
            subtitle = "Aura nébuleuse bi-couche",
            description = "Double anneau éthéré violet-cyan avec particules d'étoiles en orbite constante.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 350,
            rarity = BadgeRarity.EPIC,
            decorationKey = "magic_mist",
            isPurchased = true,
            isEquipped = true,
            previewColors = listOf(0xFF00F0FF, 0xFFC026D3, 0xFF7928CA),
            tierTag = "Animé"
        ),
        ShopItem(
            id = "dec_infinity_swirl",
            title = "Tourbillon Infini",
            subtitle = "Vortex dimensionnel 3D",
            description = "Spirale galactique multi-arcs aux éclats d'énergie stellaire et comètes orbitales.",
            category = ShopCategory.AVATAR_DECORATION,
            priceOrbs = 500,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "infinity_swirl",
            previewColors = listOf(0xFF38BDF8, 0xFF818CF8, 0xFFEC4899),
            tierTag = "3D Vortex"
        ),

        // 🔥 ELEMENTAL
        ShopItem(
            id = "dec_eclipse_crimson",
            title = "Éclipse Écarlate",
            subtitle = "Couronne solaire & lunes",
            description = "Soleil noir cerné de plasma cramoisi incandescent et deux lunes gravitant autour de votre avatar.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 490,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "eclipse_crimson",
            previewColors = listOf(0xFFDC2626, 0xFFF97316, 0xFFFDE047),
            isNew = true,
            isLimited = true,
            tierTag = "Éclipse"
        ),
        ShopItem(
            id = "dec_quantum_vortex",
            title = "Vortex Quantique",
            subtitle = "Orbitales atomiques néon",
            description = "Trois anneaux elliptiques entrecroisés avec noyaux d'antimatière bleue haute énergie.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 460,
            rarity = BadgeRarity.EPIC,
            decorationKey = "quantum_vortex",
            previewColors = listOf(0xFF06B6D4, 0xFF3B82F6, 0xFFE0F2FE),
            isNew = true,
            tierTag = "Quantique"
        ),
        ShopItem(
            id = "dec_phoenix_flames",
            title = "Flammes du Phénix",
            subtitle = "Brasier ardent & braises",
            description = "Couronne de flammes dansantes multicouches avec étincelles d'or incandescente.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 480,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "phoenix_flames",
            previewColors = listOf(0xFFFDE047, 0xFFF97316, 0xFFDC2626),
            tierTag = "Pyro"
        ),
        ShopItem(
            id = "dec_lightning_storm",
            title = "Foudre Divine & Tempête",
            subtitle = "Arcs plasma haute tension",
            description = "Décharges électriques cyan et améthyste crépitant dynamiquement tout autour de l'avatar.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 420,
            rarity = BadgeRarity.EPIC,
            decorationKey = "lightning_storm",
            previewColors = listOf(0xFF38BDF8, 0xFFC084FC, 0xFFE0E7FF),
            tierTag = "Foudre"
        ),
        ShopItem(
            id = "dec_spectral_soul",
            title = "Âme Émeraude Spectrale",
            subtitle = "Feux follets mystiques",
            description = "Volutes spectrales vert émeraude et essences lumineuses flottant en apesanteur.",
            category = ShopCategory.ELEMENTAL,
            priceOrbs = 380,
            rarity = BadgeRarity.RARE,
            decorationKey = "spectral_soul",
            previewColors = listOf(0xFF34D399, 0xFF059669, 0xFFA7F3D0),
            tierTag = "Spectral"
        ),

        // 🌸 ANIME_SAKURA
        ShopItem(
            id = "dec_kitsune_spirit",
            title = "Masque Kitsune Mystique",
            subtitle = "Esprit renard légendaire",
            description = "Oreilles sacrées de Kitsune blanc et vermillon accompagnées de feux follets spirituels.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 540,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "kitsune_spirit",
            previewColors = listOf(0xFFF43F5E, 0xFFFDE047, 0xFFFFFFFF),
            isNew = true,
            isLimited = true,
            tierTag = "Mythique"
        ),
        ShopItem(
            id = "dec_katana_slash",
            title = "Lame Katana Néon",
            subtitle = "Entailles dimensionnelles",
            description = "Coups de sabre fulgurants rose électrique et cyan fendant l'air avec étincelles.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 470,
            rarity = BadgeRarity.EPIC,
            decorationKey = "katana_slash",
            previewColors = listOf(0xFFEC4899, 0xFF00F0FF, 0xFFFFFFFF),
            isNew = true,
            tierTag = "Anime FX"
        ),
        ShopItem(
            id = "dec_sakura_blossom",
            title = "Sakura Céleste",
            subtitle = "Pétales de cerisier en vol",
            description = "Couronne de fleurs de cerisier japonaises tourbillonnant avec poussière dorée.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 360,
            rarity = BadgeRarity.RARE,
            decorationKey = "sakura_blossom",
            previewColors = listOf(0xFFF472B6, 0xFFFDA4AF, 0xFFFEF08A),
            tierTag = "Floral"
        ),
        ShopItem(
            id = "dec_astral_dragon",
            title = "Dragon Astral",
            subtitle = "Gardien céleste d'or",
            description = "Serpent draconique cyan et or enroulé autour du cadre avec perles solaires en orbite.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 550,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "astral_dragon",
            previewColors = listOf(0xFF06B6D4, 0xFFF59E0B, 0xFFFDE047),
            tierTag = "Mythique"
        ),
        ShopItem(
            id = "dec_black_hole",
            title = "Trou Noir & Supernova",
            subtitle = "Disque d'accrétion spatial",
            description = "Singularité gravitationnelle inclinée à anneaux plasma doré, rubis et ultraviolet.",
            category = ShopCategory.ANIME_SAKURA,
            priceOrbs = 520,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "black_hole",
            previewColors = listOf(0xFFF59E0B, 0xFF8B5CF6, 0xFF06B6D4),
            tierTag = "Cosmique"
        ),

        // 🤖 CYBER_MECHA
        ShopItem(
            id = "dec_prism_hologram",
            title = "Prisme Holographique",
            subtitle = "Cristaux géométriques 3D",
            description = "Octaèdre futuriste rotatif projetant des faisceaux laser arc-en-ciel haute précision.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 450,
            rarity = BadgeRarity.EPIC,
            decorationKey = "prism_hologram",
            previewColors = listOf(0xFF00F0FF, 0xFFA855F7, 0xFF34D399),
            isNew = true,
            tierTag = "Holo 3D"
        ),
        ShopItem(
            id = "dec_neural_cyber",
            title = "Circuit Neural RVB",
            subtitle = "Impulsions cybernétiques",
            description = "Pistes de processeur illuminées avec nœuds synaptiques en cascade autour du profil.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 410,
            rarity = BadgeRarity.RARE,
            decorationKey = "neural_cyber",
            previewColors = listOf(0xFF10B981, 0xFF38BDF8, 0xFF818CF8),
            isNew = true,
            tierTag = "Tech"
        ),
        ShopItem(
            id = "dec_cyber_mecha",
            title = "Casque Mecha & Visière HUD",
            subtitle = "Armure tactique anime",
            description = "Oreilles cybernétiques articulées avec capteurs LED cyan et réticules de visée.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 440,
            rarity = BadgeRarity.EPIC,
            decorationKey = "cyber_mecha",
            previewColors = listOf(0xFF00F0FF, 0xFF10B981, 0xFF1E293B),
            tierTag = "Mecha"
        ),
        ShopItem(
            id = "dec_glitch_matrix",
            title = "Glitch Holographique",
            subtitle = "Distorsion RVB & Matrix",
            description = "Décalage chromatique dynamique cyan/magenta accompagné de segments binaires néon.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 390,
            rarity = BadgeRarity.RARE,
            decorationKey = "glitch_matrix",
            previewColors = listOf(0xFF00F0FF, 0xFFEC4899, 0xFF10B981),
            tierTag = "Glitch"
        ),
        ShopItem(
            id = "dec_rgb_headset",
            title = "Casque Gamer RGB Pro",
            subtitle = "Spectre audio réactif",
            description = "Arceau arc-en-ciel intégral, oreillettes lumineuses et égaliseur animé en temps réel.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 310,
            rarity = BadgeRarity.RARE,
            decorationKey = "rgb_headset",
            previewColors = listOf(0xFFFF00FF, 0xFF00FFFF, 0xFF38BDF8),
            tierTag = "Gaming"
        ),
        ShopItem(
            id = "dec_cyber_halo",
            title = "Couronne Cyberpunk",
            subtitle = "Spires lasers néon",
            description = "Diadème futuriste à pointes lumineuses cyan et émeraude flottant au sommet.",
            category = ShopCategory.CYBER_MECHA,
            priceOrbs = 300,
            rarity = BadgeRarity.RARE,
            decorationKey = "cyber_halo",
            previewColors = listOf(0xFF10B981, 0xFF00F0FF, 0xFFFBBF24),
            tierTag = "Cyber"
        ),

        // 🪽 DIVINE_WINGS
        ShopItem(
            id = "dec_starlight_crown",
            title = "Couronne Constellation",
            subtitle = "Étoiles célestes dorées",
            description = "Halo d'astres scintillants reliés par des filaments de lumière stellaire.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 560,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "starlight_crown",
            previewColors = listOf(0xFFFDE047, 0xFF38BDF8, 0xFFFFFFFF),
            isNew = true,
            tierTag = "Astral"
        ),
        ShopItem(
            id = "dec_obsidian_seraph",
            title = "Ailes Séraphin Obsidienne",
            subtitle = "Triple envergure divine",
            description = "Ailes majestueuses d'obsidienne bordées d'or impérial et de gemmes améthyste.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 590,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "obsidian_seraph",
            previewColors = listOf(0xFFA855F7, 0xFFF59E0B, 0xFF1E1B4B),
            isNew = true,
            isLimited = true,
            tierTag = "Séraphin"
        ),
        ShopItem(
            id = "dec_archangel_wings",
            title = "Ailes d'Archange Dorées",
            subtitle = "Envergure sacrée animée",
            description = "Grandes ailes dorées battant doucement sous une auréole rayonnante de lumière pure.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 580,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "archangel_wings",
            previewColors = listOf(0xFFFEF08A, 0xFFF59E0B, 0xFFFDE047),
            tierTag = "Divin"
        ),
        ShopItem(
            id = "dec_raven_wings",
            title = "Ailes du Corbeau Raven",
            subtitle = "Plumes d'ébène & améthyste",
            description = "Plumes d'ombres éthérées battant au rythme d'une aura violette impériale.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 450,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "raven_wings",
            previewColors = listOf(0xFF5865F2, 0xFF8B5CF6, 0xFF2B2D31),
            tierTag = "Signature"
        ),
        ShopItem(
            id = "dec_royal_crown",
            title = "Couronne Royale Diamants",
            subtitle = "Diadème impérial serti",
            description = "Couronne d'or massif ornée de saphirs scintillants et halo solaire radiant.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 500,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "royal_crown",
            previewColors = listOf(0xFFFEF08A, 0xFFF59E0B, 0xFF38BDF8),
            tierTag = "Royal"
        ),
        ShopItem(
            id = "dec_aurora_frost",
            title = "Givre Boréal & Flocons",
            subtitle = "Cristaux polaires",
            description = "Voile boréal turquoise et cristaux de glace hexagonaux en rotation continue.",
            category = ShopCategory.DIVINE_WINGS,
            priceOrbs = 370,
            rarity = BadgeRarity.RARE,
            decorationKey = "aurora_frost",
            previewColors = listOf(0xFF2DD4BF, 0xFF38BDF8, 0xFFA78BFA),
            tierTag = "Glace"
        ),

        // ✨ PROFILE EFFECTS
        ShopItem(
            id = "eff_aurora_curtain",
            title = "Rideau Aurore Boréale",
            subtitle = "Vagues polaires animées",
            description = "Des rubans lumineux émeraude et indigo ondoient gracieusement sur toute la bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 340,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_aurora_curtain",
            previewColors = listOf(0xFF2DD4BF, 0xFF818CF8, 0xFF34D399),
            isNew = true,
            tierTag = "Bannière"
        ),
        ShopItem(
            id = "eff_cyber_rain",
            title = "Pluie Matrix Néon",
            subtitle = "Flux de données cyber",
            description = "Cascades de faisceaux numériques vert néon et cyan défilant sur votre carte de profil.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 360,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_cyber_rain",
            previewColors = listOf(0xFF10B981, 0xFF00F0FF, 0xFF064E3B),
            isNew = true,
            tierTag = "Matrix"
        ),
        ShopItem(
            id = "eff_golden_feathers",
            title = "Plumes Célestes Dorées",
            subtitle = "Chute divine scintillante",
            description = "Plumes d'or et particules de lumière tombant en vol plané sur votre bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 390,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "eff_golden_feathers",
            previewColors = listOf(0xFFFDE047, 0xFFF59E0B, 0xFFFEF08A),
            isNew = true,
            tierTag = "Céleste"
        ),
        ShopItem(
            id = "eff_crimson_eclipse",
            title = "Éclipse de Sang",
            subtitle = "Ondes d'astre noir",
            description = "Un astre sombre pulse au centre de votre bannière en émettant des ondes écarlates.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 380,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "eff_crimson_eclipse",
            previewColors = listOf(0xFFDC2626, 0xFFF97316, 0xFF09090B),
            isNew = true,
            tierTag = "Éclipse"
        ),
        ShopItem(
            id = "eff_sakura",
            title = "Pluie de pétales Sakura",
            subtitle = "Effet de bannière floral",
            description = "Une douce pluie de fleurs de cerisier recouvre élégamment votre bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 290,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_sakura",
            previewColors = listOf(0xFFF472B6, 0xFFFDA4AF),
            tierTag = "Bannière"
        ),
        ShopItem(
            id = "eff_supernova",
            title = "Explosion Supernova",
            subtitle = "Pluie de météores",
            description = "Météores scintillants et étoiles filantes traversant votre profil à haute vitesse.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 350,
            rarity = BadgeRarity.EPIC,
            decorationKey = "eff_supernova",
            previewColors = listOf(0xFF38BDF8, 0xFFFDE047, 0xFF818CF8),
            tierTag = "Cosmique"
        ),
        ShopItem(
            id = "eff_lightning",
            title = "Orage d'Éclairs & Foudre",
            subtitle = "Tempête électrique",
            description = "Éclairs ramifiés et flashs électriques illuminant l'arrière-plan de votre profil.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 320,
            rarity = BadgeRarity.RARE,
            decorationKey = "eff_lightning",
            previewColors = listOf(0xFF38BDF8, 0xFFC084FC),
            tierTag = "Orage"
        ),
        ShopItem(
            id = "eff_phoenix",
            title = "Fournaise de Braises",
            subtitle = "Braises incandescentes",
            description = "Étincelles brûlantes s'élevant continuellement le long de la bannière.",
            category = ShopCategory.PROFILE_EFFECT,
            priceOrbs = 320,
            rarity = BadgeRarity.RARE,
            decorationKey = "eff_phoenix",
            previewColors = listOf(0xFFFDE047, 0xFFF97316, 0xFFEF4444),
            tierTag = "Feu"
        ),

        // 💬 CHAT THEMES
        ShopItem(
            id = "theme_nebula",
            title = "Brouillard Magique",
            subtitle = "Thème de discussion",
            description = "Décorez vos salons d'un thème cosmique vibrant violet et nébuleux.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "nebula",
            isPurchased = true,
            previewColors = listOf(0xFF1A102F, 0xFF4A154B),
            tierTag = "Thème"
        ),
        ShopItem(
            id = "theme_infinity",
            title = "Tourbillon Infini",
            subtitle = "Thème de discussion",
            description = "Vagues d'énergie céleste et dégradé océan-indigo pour vos salons.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 200,
            rarity = BadgeRarity.SPECIAL,
            decorationKey = "infinity",
            previewColors = listOf(0xFF0D2538, 0xFF1A365D),
            tierTag = "Thème"
        ),
        ShopItem(
            id = "theme_cyberpunk",
            title = "Néon Cyberpunk",
            subtitle = "Thème synthwave futuriste",
            description = "Atmosphère Night City violet électrique et magenta pour vos conversations.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 240,
            rarity = BadgeRarity.EPIC,
            decorationKey = "cyberpunk",
            previewColors = listOf(0xFF2D124D, 0xFF791E88),
            isNew = true,
            tierTag = "Synthwave"
        ),
        ShopItem(
            id = "theme_sakura",
            title = "Jardin Zen Sakura",
            subtitle = "Thème cerisier nocturne",
            description = "Douce ambiance japonaise rose pourpre et carmin apaisante.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 240,
            rarity = BadgeRarity.EPIC,
            decorationKey = "sakura",
            previewColors = listOf(0xFF2D0B24, 0xFF831843),
            isNew = true,
            tierTag = "Zen"
        ),
        ShopItem(
            id = "theme_aurora",
            title = "Aurore Boréale",
            subtitle = "Thème polaire émeraude",
            description = "Lueurs nordiques turquoise et bleu nuit profond pour votre salon actif.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 260,
            rarity = BadgeRarity.EPIC,
            decorationKey = "aurora",
            previewColors = listOf(0xFF042F2E, 0xFF312E81),
            isNew = true,
            tierTag = "Polaire"
        ),
        ShopItem(
            id = "theme_phoenix",
            title = "Fournaise Phénix",
            subtitle = "Thème ambre & braises",
            description = "Teintes volcaniques rouges et ambrées chaleureuses.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 240,
            rarity = BadgeRarity.RARE,
            decorationKey = "phoenix",
            previewColors = listOf(0xFF350E04, 0xFF7C2D12),
            isNew = true,
            tierTag = "Ardent"
        ),
        ShopItem(
            id = "theme_gold_carbon",
            title = "Or & Carbone Prestige",
            subtitle = "Thème VIP luxe sombre",
            description = "Noir carbone satiné rehaussé de reflets or ambré exclusifs.",
            category = ShopCategory.CHAT_THEME,
            priceOrbs = 320,
            rarity = BadgeRarity.LEGENDARY,
            decorationKey = "gold_carbon",
            previewColors = listOf(0xFF1C1917, 0xFF78350F),
            isNew = true,
            tierTag = "VIP Or"
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
