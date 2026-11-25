package com.example.model

enum class ForumPostStatus(val label: String, val colorHex: Long) {
    OPEN("Ouvert", 0xFF23A55AL),
    RESOLVED("Résolu", 0xFF5865F2L),
    ANNOUNCEMENT("Annonce", 0xFFF0B232L)
}

data class ForumReply(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarColor: Long = 0xFF5865F2L,
    val content: String = "",
    val timestamp: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ForumPost(
    val id: String = "",
    val channelId: String = "",
    val serverId: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarColor: Long = 0xFF5865F2L,
    val status: ForumPostStatus = ForumPostStatus.OPEN,
    val upvotedByIds: List<String> = emptyList(),
    val replies: List<ForumReply> = emptyList(),
    val tags: List<String> = listOf("Entraide"),
    val timestamp: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

enum class EphemeralDurationOption(val seconds: Int, val label: String) {
    OFF(0, "Désactivé"),
    TWENTY_FOUR_HOURS(86_400, "24 heures"),
    SEVEN_DAYS(604_800, "7 jours")
}

enum class DirectCallStatus(val label: String) {
    RINGING_OUTGOING("Appel en cours..."),
    RINGING_INCOMING("Appel entrant..."),
    CONNECTED("Connecté"),
    ENDED("Terminé")
}

data class DirectCallSession(
    val id: String = "",
    val peerUser: User = User(),
    val isGroupCall: Boolean = false,
    val groupName: String? = null,
    val isVideo: Boolean = false,
    val status: DirectCallStatus = DirectCallStatus.RINGING_OUTGOING,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isCameraOn: Boolean = false,
    val isMinimized: Boolean = false,
    val startedAtMs: Long = System.currentTimeMillis(),
    val connectedAtMs: Long? = null
)

data class CallHistoryEntry(
    val id: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerAvatarColor: Long = 0xFF5865F2L,
    val peerAvatarUrl: String? = null,
    val isVideo: Boolean = false,
    val isOutgoing: Boolean = true,
    val isMissed: Boolean = false,
    val durationSec: Int = 0,
    val timestamp: String = ""
)

data class PendingOfflineMessage(
    val id: String = "",
    val targetId: String = "",
    val targetLabel: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val waveformAmplitudes: List<Int> = emptyList(),
    val isViewOnce: Boolean = false,
    val queuedAtMs: Long = System.currentTimeMillis()
)

enum class MiniGameType(val label: String, val subtitle: String) {
    TIC_TAC_TOE("Morpion Raven", "Affrontez un membre en 3×3"),
    RAPID_QUIZ("Quiz Chrono", "Répondez vite pour gagner des Orbs"),
    DRAW_AND_GUESS("Dessiner & Deviner", "Croquis rapide sur le tableau du salon")
}

data class MiniGameQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

object MiniGameQuizCatalog {
    val questions = listOf(
        MiniGameQuizQuestion(
            question = "Quel protocole sécurise le hachage du code d'accès sur Raven ?",
            options = listOf("MD5 simple", "PBKDF2-HMAC-SHA256", "Base64", "ROT13"),
            correctIndex = 1
        ),
        MiniGameQuizQuestion(
            question = "Combien de secondes dure la fenêtre anti-bruteforce après 5 essais ?",
            options = listOf("10 secondes", "30 secondes", "5 minutes", "1 heure"),
            correctIndex = 1
        ),
        MiniGameQuizQuestion(
            question = "Quelle monnaie permet de débloquer autocollants et rôles sur Raven ?",
            options = listOf("Credits", "Orbs", "Gems", "Tokens"),
            correctIndex = 1
        ),
        MiniGameQuizQuestion(
            question = "Quel type de salon permet de voter et classer des sujets résolus ?",
            options = listOf("Salon Vocal", "Salon Forum", "Salon Annonce", "Scène"),
            correctIndex = 1
        ),
        MiniGameQuizQuestion(
            question = "Quelle vitesse maximale de lecture est disponible pour les notes vocales ?",
            options = listOf("1,2x", "1,5x", "2,0x", "3,0x"),
            correctIndex = 2
        )
    )

    val drawPrompts = listOf(
        "Un corbeau noir perché",
        "Un casque audio lumineux",
        "Une fusée vers les étoiles",
        "Une tasse de café fumante",
        "Un trophée d'or"
    )
}
