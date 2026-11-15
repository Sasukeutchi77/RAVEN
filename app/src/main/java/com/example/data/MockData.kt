package com.example.data

import com.example.model.*

object MockData {
    val currentUser = User(
        id = "user_me",
        name = "Jarvis",
        tag = "#0001",
        avatarColor = 0xFF5865F2,
        bannerColor = 0xFF2B2D31,
        bio = "Administrateur de Raven. Passionné de corbeaux, de code Kotlin et de designs soignés. Contactez-moi pour toute question sur le serveur !",
        pronouns = "il/lui",
        status = UserStatus.ONLINE,
        customStatus = "En plein développement de Raven...",
        customStatusEmoji = "code",
        badges = listOf("ACTIVE_DEV", "NITRO", "BOOSTER", "EARLY_SUPPORTER"),
        memberSince = "10 nov. 2024",
        activity = "Développe Raven sur Android",
        email = "jarvismd45@gmail.com",
        phone = "+33 6 ** ** ** 42",
        role = UserRole.ADMIN,
        clanTag = "RVN",
        clanBadgeIcon = "bolt"
    )

    val friendThomas = User(
        id = "user_thomas",
        name = "Thomas",
        tag = "#1234",
        avatarColor = 0xFF35373C,
        bannerColor = 0xFF1E1F22,
        bio = "Toujours dispo pour un salon vocal ou une session de jeu en fin de journée. Ne pas déranger après 23h !",
        pronouns = "il/lui",
        status = UserStatus.ONLINE,
        customStatus = "Dans le salon vocal 1",
        customStatusEmoji = "headset",
        badges = listOf("BOOSTER", "EARLY_SUPPORTER"),
        memberSince = "15 janv. 2024",
        activity = "Écoute de la synthwave sur Spotify",
        role = UserRole.MODERATOR,
        xp = 280,
        level = 4,
        clanTag = "PRO",
        clanBadgeIcon = "headset"
    )

    val friendEmma = User(
        id = "user_emma",
        name = "Emma",
        tag = "#5678",
        avatarColor = 0xFF4E5058,
        bannerColor = 0xFF35373C,
        bio = "UI/UX Designer et illustratrice. Envoyez-moi un DM si vous voulez de nouvelles bannières de serveur !",
        pronouns = "elle/la",
        status = UserStatus.ONLINE,
        customStatus = "Création de maquettes",
        customStatusEmoji = "star",
        badges = listOf("NITRO", "ACTIVE_DEV"),
        memberSince = "02 mars 2024",
        activity = "Travaille sur Figma",
        role = UserRole.MEMBER,
        xp = 190,
        level = 3,
        clanTag = "ART",
        clanBadgeIcon = "star"
    )

    val friendLucas = User(
        id = "user_lucas",
        name = "Lucas",
        tag = "#9012",
        avatarColor = 0xFF2B2D31,
        bannerColor = 0xFF111214,
        bio = "Développeur backend et sécurité réseau. N'hésitez pas à poser vos questions dans le salon discussion.",
        pronouns = "il/lui",
        status = UserStatus.OFFLINE,
        customStatus = "AFK pour le week-end",
        customStatusEmoji = "coffee",
        badges = listOf("EARLY_SUPPORTER"),
        memberSince = "20 août 2024",
        role = UserRole.MEMBER,
        xp = 95,
        level = 1,
        clanTag = "DEV",
        clanBadgeIcon = "code"
    )

    val allFriends = listOf(friendThomas, friendEmma, friendLucas)

    val initialServers = listOf(
        // DM Tab
        Server(
            id = "dm_tab",
            name = "Messages Privés",
            iconText = "@",
            iconBgColor = 0xFF2B2D31,
            isDmTab = true
        ),
        // Primary Server
        Server(
            id = "server_main",
            name = "Mon Serveur",
            iconText = "MS",
            iconBgColor = 0xFF1E1F22,
            channels = listOf(
                Channel(
                    id = "c_general",
                    serverId = "server_main",
                    name = "général",
                    type = ChannelType.TEXT,
                    category = "SALONS TEXTUELS"
                ),
                Channel(
                    id = "c_discussion",
                    serverId = "server_main",
                    name = "discussion",
                    type = ChannelType.TEXT,
                    category = "SALONS TEXTUELS"
                ),
                Channel(
                    id = "c_voice_1",
                    serverId = "server_main",
                    name = "Salon vocal 1",
                    type = ChannelType.VOICE,
                    category = "SALONS VOCAUX",
                    activeVoiceUsers = listOf(friendThomas)
                ),
                Channel(
                    id = "c_voice_2",
                    serverId = "server_main",
                    name = "Salon vocal 2",
                    type = ChannelType.VOICE,
                    category = "SALONS VOCAUX"
                ),
                Channel(
                    id = "c_stage_1",
                    serverId = "server_main",
                    name = "Scène Communauté",
                    type = ChannelType.STAGE,
                    category = "CONFÉRENCES & SCÈNE",
                    topic = "Table ronde hebdomadaire & Q&A",
                    activeVoiceUsers = listOf(friendThomas, friendEmma),
                    stageSpeakerIds = listOf(friendThomas.id)
                )
            ),
            members = listOf(currentUser, friendThomas, friendEmma, friendLucas),
            isPublic = true,
            categoryTag = "Communauté",
            description = "Le QG officiel Raven : vocal HD, mini-jeux et entraide Kotlin.",
            shopItems = listOf(
                ServerShopItem(
                    id = "srv_shop_vip",
                    title = "Rôle Légende Dorée",
                    description = "Débloque la couleur dégradée Or & Rubis dans la liste des membres.",
                    priceOrbs = 150,
                    primaryColorHex = 0xFFF0B232,
                    secondaryColorHex = 0xFFEB459E,
                    roleName = "Légende Dorée"
                ),
                ServerShopItem(
                    id = "srv_shop_dj",
                    title = "Pass DJ Jukebox",
                    description = "Statut DJ officiel pour animer les files d'attente musicales en vocal.",
                    priceOrbs = 100,
                    primaryColorHex = 0xFF00D4AA,
                    secondaryColorHex = 0xFF5865F2,
                    roleName = "DJ Officiel"
                ),
                ServerShopItem(
                    id = "srv_shop_cyber",
                    title = "Titre Cyber Pionnier",
                    description = "Badge dégradé Cyan Électrique visible sur ce serveur.",
                    priceOrbs = 220,
                    primaryColorHex = 0xFF00A8FC,
                    secondaryColorHex = 0xFF8B5CF6,
                    roleName = "Cyber Pionnier"
                )
            ),
            events = listOf(
                ServerEvent(
                    id = "evt_1",
                    title = "Tournoi Mini-Jeux & Orbs",
                    description = "Affrontez la communauté au Morpion et au Quiz Tech pour remporter des Orbs bonus !",
                    dateTimeLabel = "Vendredi • 21:00",
                    locationLabel = "Salon vocal 1",
                    creatorName = "Jarvis",
                    interestedUserIds = listOf(friendThomas.id, friendEmma.id)
                ),
                ServerEvent(
                    id = "evt_2",
                    title = "Q&A Scène : Nouveautés Raven",
                    description = "Présentation en direct des nouveaux thèmes, dossiers de serveurs et groupes privés.",
                    dateTimeLabel = "Dimanche • 18:30",
                    locationLabel = "Scène Communauté",
                    creatorName = "Thomas",
                    interestedUserIds = listOf(currentUser.id)
                )
            )
        )
    )

    val initialFriendRequests = listOf(
        FriendRequest(
            id = "req_1",
            user = User(
                id = "user_chloe",
                name = "Chloé",
                tag = "#4421",
                avatarColor = 0xFF791E88,
                status = UserStatus.ONLINE,
                customStatus = "Disponible pour un vocal",
                badges = listOf("NITRO")
            ),
            direction = FriendRequestDirection.RECEIVED,
            timestamp = "Il y a 10 min"
        ),
        FriendRequest(
            id = "req_2",
            user = User(
                id = "user_hugo",
                name = "Hugo",
                tag = "#8804",
                avatarColor = 0xFF1A365D,
                status = UserStatus.IDLE,
                customStatus = "Joue à Valorant"
            ),
            direction = FriendRequestDirection.SENT,
            timestamp = "Hier"
        )
    )

    val initialMessages: Map<String, List<Message>> = mapOf(
        "c_general" to listOf(
            Message(
                id = "m_1",
                channelId = "c_general",
                sender = friendThomas,
                text = "Salut tout le monde !",
                timestamp = "11:20",
                reactions = listOf(
                    Reaction(emoji = "👋", count = 2, userReacted = false),
                    Reaction(emoji = "🔥", count = 1, userReacted = true)
                )
            ),
            Message(
                id = "m_2",
                channelId = "c_general",
                sender = friendEmma,
                text = "Salut ! Quelqu'un est disponible en vocal ?",
                timestamp = "11:22",
                reactions = listOf(
                    Reaction(emoji = "🎧", count = 1, userReacted = false)
                )
            )
        ),
        "c_discussion" to listOf(
            Message(
                id = "m_disc_1",
                channelId = "c_discussion",
                sender = friendThomas,
                text = "Bienvenue dans le salon discussion.",
                timestamp = "10:00"
            )
        ),
        "dm_thomas" to listOf(
            Message(
                id = "dm_t_1",
                channelId = "dm_thomas",
                sender = friendThomas,
                text = "Salut, tu es disponible ?",
                timestamp = "11:15"
            )
        ),
        "dm_emma" to listOf(
            Message(
                id = "dm_e_1",
                channelId = "dm_emma",
                sender = friendEmma,
                text = "On se parle tout à l'heure !",
                timestamp = "Hier"
            )
        )
    )

    val initialDirectConversations = listOf(
        DirectConversation(
            id = "dm_thomas",
            friend = friendThomas,
            lastMessage = "Salut, tu es disponible ?",
            lastMessageTime = "11:15"
        ),
        DirectConversation(
            id = "dm_emma",
            friend = friendEmma,
            lastMessage = "On se parle tout à l'heure !",
            lastMessageTime = "Hier"
        )
    )

    val publicExploreServers = listOf(
        PublicServerListing(
            id = "pub_gaming_fr",
            name = "Raven Arena Esports",
            iconText = "game",
            iconBgColor = 0xFF5865F2,
            categoryTag = "Gaming",
            description = "Tournois FPS & MOBA chaque soir, vocal ultra faible latence et équipes classées.",
            inviteCode = "RAVEN-ARENA",
            memberCount = 4280,
            boostLevel = 3,
            isVerified = true
        ),
        PublicServerListing(
            id = "pub_kotlin_hub",
            name = "Kotlin & Android Lab",
            iconText = "code",
            iconBgColor = 0xFF10B981,
            categoryTag = "Développement",
            description = "Entraide Jetpack Compose, architecture mobile, revues de code et défis hebdo.",
            inviteCode = "RAVEN-DEV",
            memberCount = 2910,
            boostLevel = 2,
            isVerified = true
        ),
        PublicServerListing(
            id = "pub_synth_lounge",
            name = "Midnight Synth & Lo-Fi",
            iconText = "headset",
            iconBgColor = 0xFFEB459E,
            categoryTag = "Musique",
            description = "Sessions Listen Together 24/7, jukebox collaboratif et beatmakers indépendants.",
            inviteCode = "RAVEN-MUSIC",
            memberCount = 1840,
            boostLevel = 2,
            isVerified = true
        ),
        PublicServerListing(
            id = "pub_chill_study",
            name = "Café Nocturne & Focus",
            iconText = "star",
            iconBgColor = 0xFFF59E0B,
            categoryTag = "Chill",
            description = "Salons Pomodoro silencieux, notes partagées et discussions détendues tard le soir.",
            inviteCode = "RAVEN-CHILL",
            memberCount = 1320,
            boostLevel = 1,
            isVerified = false
        )
    )

    val jukeboxCatalogTracks = listOf(
        MusicTrack("trk_1", "Neon Horizon (Synthwave)", "Raven Audio Collective", 214, "Thomas", 0xFFEB459E),
        MusicTrack("trk_2", "Midnight Lo-Fi Coding", "Kaito Beats", 185, "Jarvis", 0xFF5865F2),
        MusicTrack("trk_3", "Cyberpunk Overdrive", "Nova Pulse", 242, "Emma", 0xFF00A8FC),
        MusicTrack("trk_4", "Crystal Rain Ambient", "Studio Aura", 198, "Lucas", 0xFF23A55A),
        MusicTrack("trk_5", "Boss Fight Chiptune", "Pixel Forge", 164, "Thomas", 0xFFF0B232)
    )
}
