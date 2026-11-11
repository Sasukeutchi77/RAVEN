package com.example.model

enum class BadgeRarity(val label: String, val color: Long) {
    SPECIAL("Spécial", 0xFF5865F2),
    RARE("Rare", 0xFF57F287),
    EPIC("Épique", 0xFFEB459E),
    LEGENDARY("Légendaire", 0xFFFEE75C)
}

data class SuperBadge(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconKey: String,
    val rarity: BadgeRarity,
    val unlockedDate: String,
    val isEquipped: Boolean = true,
    val isUnlocked: Boolean = true
)

object SuperBadgesCatalog {
    val allBadges = listOf(
        SuperBadge(
            id = "EARLY_SUPPORTER",
            title = "Raven Pionnier",
            subtitle = "Soutien de la première heure",
            description = "Attribué aux premiers membres ayant rejoint Raven lors de son lancement initial en novembre 2024.",
            iconKey = "early_supporter",
            rarity = BadgeRarity.LEGENDARY,
            unlockedDate = "10 nov. 2024",
            isEquipped = true,
            isUnlocked = true
        ),
        SuperBadge(
            id = "ACTIVE_DEV",
            title = "Développeur Actif",
            subtitle = "Artisan du code",
            description = "Reconnaissance accordée aux créateurs et développeurs d'applications contribuant à l'écosystème Raven.",
            iconKey = "active_dev",
            rarity = BadgeRarity.EPIC,
            unlockedDate = "10 nov. 2024",
            isEquipped = true,
            isUnlocked = true
        ),
        SuperBadge(
            id = "NITRO",
            title = "Super Supporter Nitro",
            subtitle = "Abonnement Raven Premium",
            description = "Débloque le partage haute définition, les bannières animées et les super réactions vectorielles.",
            iconKey = "nitro",
            rarity = BadgeRarity.EPIC,
            unlockedDate = "10 nov. 2024",
            isEquipped = true,
            isUnlocked = true
        ),
        SuperBadge(
            id = "BOOSTER",
            title = "Booster de Serveur Niveau 3",
            subtitle = "Propulseur de communauté",
            description = "Accorde des avantages sonores et textuels exclusifs à l'ensemble des membres du serveur.",
            iconKey = "booster",
            rarity = BadgeRarity.SPECIAL,
            unlockedDate = "10 nov. 2024",
            isEquipped = true,
            isUnlocked = true
        ),
        SuperBadge(
            id = "HYPESQUAD",
            title = "HypeSquad Bravery",
            subtitle = "L'esprit de bravoure",
            description = "Fait partie de la faction HypeSquad guidée par le courage et la détermination au sein de la communauté.",
            iconKey = "hypesquad",
            rarity = BadgeRarity.RARE,
            unlockedDate = "10 nov. 2024",
            isEquipped = true,
            isUnlocked = true
        ),
        SuperBadge(
            id = "BUG_HUNTER",
            title = "Chasseur de Bugs Certifié",
            subtitle = "Gardien de la stabilité",
            description = "A signalé et aidé à résoudre des anomalies critiques pour rendre Raven plus rapide et sécurisé.",
            iconKey = "bug_hunter",
            rarity = BadgeRarity.SPECIAL,
            unlockedDate = "10 nov. 2024",
            isEquipped = false,
            isUnlocked = true
        ),
        SuperBadge(
            id = "VOICE_MASTER",
            title = "Maître du Vocal",
            subtitle = "Plus de 20 heures en salon",
            description = "Passez plus de 20 heures en communication dans les salons vocaux du serveur.",
            iconKey = "voice_master",
            rarity = BadgeRarity.RARE,
            unlockedDate = "À débloquer",
            isEquipped = false,
            isUnlocked = false
        ),
        SuperBadge(
            id = "COMMUNITY_LEADER",
            title = "Pilier de Communauté",
            subtitle = "Créateur de 5 serveurs actifs",
            description = "Fondez et animez des communautés florissantes réunissant plus de 50 membres actifs.",
            iconKey = "community_leader",
            rarity = BadgeRarity.LEGENDARY,
            unlockedDate = "À débloquer",
            isEquipped = false,
            isUnlocked = false
        )
    )
}
