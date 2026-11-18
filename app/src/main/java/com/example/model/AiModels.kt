package com.example.model

enum class AiAssistantMode(val label: String) {
    PERSONAL("Assistant Personnel"),
    SERVER("Assistant Serveur")
}

enum class AiTaskCategory(val label: String) {
    CHAT("Conversation"),
    SUMMARIZE_CHAT("Résumé de salon"),
    SUMMARIZE_LONG_THREAD("Résumé discussion longue"),
    GENERATE_TEXT("Génération de texte"),
    MODERATION_HELP("Aide à la modération"),
    SPAM_DETECTION("Détection de spam"),
    SUSPICIOUS_CONTENT("Contenu suspect"),
    ANNOUNCEMENT_DRAFT("Aide annonce"),
    EVENT_DRAFT("Aide événement"),
    SERVER_MANAGEMENT_HELP("Gestion serveur"),
    SLASH_COMMAND("Commande IA")
}

enum class AiMessageRole {
    USER,
    ASSISTANT,
    SYSTEM_CONTEXT
}

data class AiChatMessage(
    val id: String = "",
    val conversationId: String = "",
    val userId: String = "",
    val role: AiMessageRole = AiMessageRole.USER,
    val content: String = "",
    val taskCategory: AiTaskCategory = AiTaskCategory.CHAT,
    val timestampLabel: String = "",
    val createdAtMs: Long = 0L
)

data class AiConversation(
    val id: String = "",
    val userId: String = "",
    val title: String = "Nouvelle discussion RAVEN AI",
    val mode: AiAssistantMode = AiAssistantMode.PERSONAL,
    val serverId: String? = null,
    val serverName: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val modelName: String = "gemini-3.8-flash",
    val lastMessagePreview: String = "",
    val messageCount: Int = 0,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class AiUsageQuota(
    val userId: String = "",
    val dayKey: String = "",
    val requestsToday: Int = 0,
    val dailyLimit: Int = 40,
    val requestsInLastMinute: Int = 0,
    val perMinuteLimit: Int = 6,
    val abuseStrikes: Int = 0,
    val blockedUntilMs: Long = 0L,
    val estimatedCharsToday: Int = 0
) {
    val remainingToday: Int
        get() = (dailyLimit - requestsToday).coerceAtLeast(0)
}

data class AiModerationReport(
    val isSpam: Boolean = false,
    val isSuspicious: Boolean = false,
    val riskScore: Int = 0,
    val riskLevel: String = "LOW",
    val categories: List<String> = emptyList(),
    val signals: List<String> = emptyList(),
    val recommendedAction: String = "Aucune action requise",
    val explanation: String = ""
)

data class AiAnnouncementDraft(
    val title: String = "",
    val content: String = "",
    val suggestedPinned: Boolean = false,
    val toneSummary: String = ""
)

data class AiEventDraft(
    val title: String = "",
    val description: String = "",
    val suggestedDateTimeLabel: String = "",
    val suggestedLocationLabel: String = "",
    val checklist: List<String> = emptyList()
)

object AiSlashCommandCatalog {
    val commands = listOf(
        SlashCommandOption("/ai", "Poser une question à RAVEN AI", "/ai Explique les bonnes règles de modération"),
        SlashCommandOption("/resumer", "Résumer les messages récents du salon", "/resumer 20"),
        SlashCommandOption("/longresume", "Résumé structuré d'une longue discussion", "/longresume points clés et décisions"),
        SlashCommandOption("/moderer", "Analyser le salon ou un message pour modération", "/moderer Analyse la discussion"),
        SlashCommandOption("/spam", "Détecter le spam dans un message ou le salon", "/spam Gagnez 500€ vite cliquez ici"),
        SlashCommandOption("/suspect", "Détecter phishing, arnaque ou contenu suspect", "/suspect Vérifie ce lien étrange"),
        SlashCommandOption("/annonceai", "Générer un brouillon d'annonce serveur", "/annonceai Nouveau tournoi vendredi soir"),
        SlashCommandOption("/eventai", "Générer un brouillon d'événement", "/eventai Soirée QA communautaire"),
        SlashCommandOption("/gererserveur", "Obtenir des conseils de gestion serveur", "/gererserveur Comment organiser mes rôles ?")
    )
}

data class AiAssistantUiState(
    val activeMode: AiAssistantMode = AiAssistantMode.PERSONAL,
    val conversations: List<AiConversation> = emptyList(),
    val activeConversationId: String? = null,
    val activeMessages: List<AiChatMessage> = emptyList(),
    val quota: AiUsageQuota = AiUsageQuota(),
    val isGenerating: Boolean = false,
    val streamingBuffer: String = "",
    val activeTaskLabel: String? = null,
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
    val lastModerationReport: AiModerationReport? = null,
    val lastAnnouncementDraft: AiAnnouncementDraft? = null,
    val lastEventDraft: AiEventDraft? = null,
    val providerDisplayLabel: String = "Firebase AI Logic • gemini-3.8-flash"
)
