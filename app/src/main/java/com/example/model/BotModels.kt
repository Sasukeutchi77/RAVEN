package com.example.model

enum class BotPermission(val label: String, val description: String) {
    SEND_MESSAGES("Envoyer des messages", "Autoriser le bot à publier des messages et réponses"),
    USE_SLASH_COMMANDS("Commandes Slash /", "Exécuter des commandes / personnalisées dans les salons"),
    READ_MESSAGE_EVENTS("Lire les messages", "Observer les messages pour déclencher les auto-réponses"),
    MODERATION_SHIELD("Bouclier Anti-Spam", "Détecter les doublons, floods et alertes modération"),
    MANAGE_ROLES("Interagir avec les rôles", "Associer le bot aux rôles du serveur"),
    MANAGE_EVENTS("Annoncer les événements", "Diffuser des rappels d'événements communautaires"),
    TRIGGER_WEBHOOKS("Appeler Webhooks HTTPS", "Envoyer des payloads signés HMAC-SHA256 vers des APIs")
}

enum class BotEventTrigger(val label: String, val description: String) {
    ON_MESSAGE_CREATE("Nouveau message", "Déclenché lorsqu'un membre publie un message"),
    ON_COMMAND_EXECUTED("Commande exécutée", "Déclenché lors de l'appel d'une commande / du bot"),
    ON_MEMBER_JOIN("Arrivée d'un membre", "Déclenché quand un membre rejoint la communauté"),
    ON_MEMBER_LEAVE("Départ d'un membre", "Déclenché quand un membre quitte la communauté"),
    ON_REACTION_ADD("Réaction ajoutée", "Déclenché lors de l'ajout d'une réaction sur un message"),
    ON_EVENT_CREATED("Événement créé", "Déclenché à la création d'un événement communautaire")
}

enum class BotLogStatus(val label: String) {
    SUCCESS("Succès"),
    BLOCKED_SPAM("Bloqué (Anti-Spam)"),
    RATE_LIMITED("Limité (Débit)"),
    PERMISSION_DENIED("Permission refusée"),
    WEBHOOK_OK("Webhook 2xx"),
    WEBHOOK_FAILED("Webhook Échec"),
    ERROR("Erreur"),
    SECURITY_ALERT("Alerte Sécurité")
}

data class BotCommandSpec(
    val id: String = "",
    val trigger: String = "", // e.g. "/ping"
    val description: String = "",
    val usageHint: String = "",
    val responseTemplate: String = "",
    val cooldownSeconds: Int = 3,
    val isEnabled: Boolean = true,
    val webhookForward: Boolean = false
)

data class BotAutoResponseRule(
    val id: String = "",
    val name: String = "",
    val pattern: String = "",
    val isRegex: Boolean = false,
    val replyTemplate: String = "",
    val cooldownSeconds: Int = 10,
    val isEnabled: Boolean = true
)

data class BotWebhookConfig(
    val id: String = "",
    val label: String = "",
    val endpointUrl: String = "",
    val hasSecret: Boolean = false,
    val subscribedEvents: Set<BotEventTrigger> = setOf(
        BotEventTrigger.ON_COMMAND_EXECUTED,
        BotEventTrigger.ON_MEMBER_JOIN
    ),
    val isEnabled: Boolean = true,
    val lastStatusCode: Int? = null,
    val lastDeliveryAtMs: Long? = null
)

data class BotActivityLog(
    val id: String = "",
    val botId: String = "",
    val ownerUid: String = "",
    val serverId: String? = null,
    val actorUid: String = "",
    val actorName: String = "",
    val eventType: String = "",
    val summary: String = "",
    val status: BotLogStatus = BotLogStatus.SUCCESS,
    val timestampLabel: String = "",
    val createdAtMs: Long = 0L
)

data class BotRateLimitConfig(
    val maxActionsPerMinute: Int = 12,
    val duplicateWindowSeconds: Int = 20,
    val autoDisableOnAbuseStrikes: Int = 5,
    val antiSpamHumanFloodThreshold: Int = 5
)

data class RavenBot(
    val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val handle: String = "",
    val avatarUrl: String? = null,
    val avatarColor: Long = 0xFF5865F2L,
    val description: String = "",
    val isActive: Boolean = true,
    val isPublicCatalog: Boolean = false,
    val tokenPrefix: String = "",
    val tokenVersion: Int = 1,
    val defaultPermissions: Set<BotPermission> = setOf(
        BotPermission.SEND_MESSAGES,
        BotPermission.USE_SLASH_COMMANDS,
        BotPermission.READ_MESSAGE_EVENTS
    ),
    val commands: List<BotCommandSpec> = emptyList(),
    val subscribedEvents: Set<BotEventTrigger> = setOf(
        BotEventTrigger.ON_COMMAND_EXECUTED,
        BotEventTrigger.ON_MESSAGE_CREATE
    ),
    val autoResponses: List<BotAutoResponseRule> = emptyList(),
    val webhooks: List<BotWebhookConfig> = emptyList(),
    val enabledPluginIds: Set<String> = emptySet(),
    val rateLimit: BotRateLimitConfig = BotRateLimitConfig(),
    val createdAtMs: Long = 0L
)

data class ServerBotInstallation(
    val botId: String = "",
    val serverId: String = "",
    val botName: String = "",
    val botHandle: String = "",
    val botAvatarUrl: String? = null,
    val botAvatarColor: Long = 0xFF5865F2L,
    val ownerUid: String = "",
    val grantedPermissions: Set<BotPermission> = setOf(
        BotPermission.SEND_MESSAGES,
        BotPermission.USE_SLASH_COMMANDS
    ),
    val assignedRoleTitle: String = "Bot Raven",
    val assignedCustomRoleId: String? = null,
    val isEnabledInServer: Boolean = true,
    val installedByUid: String = "",
    val installedAtMs: Long = 0L
)

data class BotCreationResult(
    val bot: RavenBot,
    val oneTimePlaintextToken: String
)

data class BotTokenRegenResult(
    val bot: RavenBot,
    val oneTimePlaintextToken: String
)

// --- Extensible Plugin & Automation Architecture ---

data class BotExecutionContext(
    val bot: RavenBot,
    val installation: ServerBotInstallation,
    val server: Server,
    val channelId: String?,
    val eventTrigger: BotEventTrigger,
    val actorUid: String,
    val actorName: String,
    val messageText: String = "",
    val commandArgs: String = ""
)

data class BotExecutionResult(
    val replyMessage: String? = null,
    val logSummary: String,
    val isAlert: Boolean = false
)

interface BotPluginHook {
    val id: String
    val name: String
    val description: String
    val requiredPermission: BotPermission
    fun handleEvent(context: BotExecutionContext): BotExecutionResult?
}

object BotPluginRegistry {
    private val customPlugins = linkedMapOf<String, BotPluginHook>()

    private val builtInPlugins: List<BotPluginHook> = listOf(
        object : BotPluginHook {
            override val id = "plugin_welcome"
            override val name = "Accueil Automatique"
            override val description = "Souhaite automatiquement la bienvenue aux nouveaux membres."
            override val requiredPermission = BotPermission.SEND_MESSAGES
            override fun handleEvent(context: BotExecutionContext): BotExecutionResult? {
                if (context.eventTrigger != BotEventTrigger.ON_MEMBER_JOIN) return null
                return BotExecutionResult(
                    replyMessage = "Bienvenue ${context.actorName} dans ${context.server.name} ! Pensez à consulter #annonces.",
                    logSummary = "Accueil envoyé pour ${context.actorName}"
                )
            }
        },
        object : BotPluginHook {
            override val id = "plugin_antispam"
            override val name = "Bouclier Anti-Spam"
            override val description = "Surveille les messages répétés et signale les comportements abusifs."
            override val requiredPermission = BotPermission.MODERATION_SHIELD
            override fun handleEvent(context: BotExecutionContext): BotExecutionResult? {
                // Flood analysis is performed cleanly by BotSpamAndRateLimiter in BotRuntimeEngine
                if (context.eventTrigger != BotEventTrigger.ON_MESSAGE_CREATE) return null
                return null
            }
        },
        object : BotPluginHook {
            override val id = "plugin_events_announcer"
            override val name = "Relais d'Événements"
            override val description = "Annonce automatiquement dans le chat les nouveaux événements créés."
            override val requiredPermission = BotPermission.MANAGE_EVENTS
            override fun handleEvent(context: BotExecutionContext): BotExecutionResult? {
                if (context.eventTrigger != BotEventTrigger.ON_EVENT_CREATED) return null
                return BotExecutionResult(
                    replyMessage = "Nouvel événement programmé : ${context.messageText} — organisé par ${context.actorName} !",
                    logSummary = "Événement relayé : ${context.messageText}"
                )
            }
        }
    )

    fun allPlugins(): List<BotPluginHook> = builtInPlugins + customPlugins.values

    fun findPlugin(id: String): BotPluginHook? = allPlugins().find { it.id == id }

    fun registerPlugin(plugin: BotPluginHook) {
        customPlugins[plugin.id] = plugin
    }

    fun unregisterPlugin(pluginId: String) {
        customPlugins.remove(pluginId)
    }
}
