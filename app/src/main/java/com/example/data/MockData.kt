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
        role = UserRole.ADMIN
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
        role = UserRole.MODERATOR
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
        role = UserRole.MEMBER
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
        role = UserRole.MEMBER
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
                )
            ),
            members = listOf(currentUser, friendThomas, friendEmma, friendLucas)
        )
    )

    val initialMessages: Map<String, List<Message>> = mapOf(
        "c_general" to listOf(
            Message(
                id = "m_1",
                channelId = "c_general",
                sender = friendThomas,
                text = "Salut tout le monde !",
                timestamp = "11:20"
            ),
            Message(
                id = "m_2",
                channelId = "c_general",
                sender = friendEmma,
                text = "Salut ! Quelqu'un est disponible en vocal ?",
                timestamp = "11:22"
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
}
