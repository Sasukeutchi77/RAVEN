package com.example.model

enum class BotPermission(val label: String, val description: String) {
    SEND_MESSAGES("Envoyer des messages", "Autoriser le bot à publier des messages et réponses"),
    USE_SLASH_COMMANDS("Commandes Slash / & !", "Exécuter des commandes / ou ! dans les salons et DMs"),
    READ_MESSAGE_EVENTS("Lire les messages", "Observer les messages pour déclencher les auto-réponses"),
    MODERATION_SHIELD("Bouclier Anti-Spam", "Détecter les doublons, floods et alertes modération"),
    MANAGE_ROLES("Interagir avec les rôles", "Associer le bot aux rôles du serveur"),
    MANAGE_EVENTS("Annoncer les événements", "Diffuser des rappels d'événements communautaires"),
    TRIGGER_WEBHOOKS("Appeler Webhooks HTTPS", "Envoyer des payloads signés HMAC-SHA256 vers des APIs")
}

enum class BotEventTrigger(val label: String, val description: String) {
    ON_MESSAGE_CREATE("Nouveau message", "Déclenché lorsqu'un membre publie un message"),
    ON_COMMAND_EXECUTED("Commande exécutée", "Déclenché lors de l'appel d'une commande / ou !"),
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

enum class BotCommandActionType(val label: String, val hint: String) {
    STATIC_TEMPLATE("Texte / Modèle", "Réponse dynamique avec variables"),
    INTERACTIVE_MENU("Menu WhatsApp", "Affiche un menu numéroté + boutons rapides"),
    BOT_HELP("Aide / Commandes", "Liste automatiquement les commandes du bot"),
    CALCULATOR("Calculatrice", "Évalue une expression mathématique (/calc 2*(3+4))"),
    POLL("Sondage", "Crée un sondage interactif en un message"),
    REMINDER("Rappel minuté", "Programme un rappel (/rappel 10s Pause)"),
    NOTE_VAULT("Notes / Tâches", "Bloc-notes rapide (/note add|list|del|clear)"),
    RANDOM_GAME("Mini-Jeux", "Dés, tirage, 8ball ou quiz (/roll, /choose, /8ball, /quiz)"),
    WEBHOOK("Webhook", "Répond puis transfère vers un webhook HTTPS")
}

data class BotQuickReply(
    val label: String = "",
    val payload: String = ""
)

data class BotCommandSpec(
    val id: String = "",
    val trigger: String = "", // e.g. "/ping" or "/menu"
    val description: String = "",
    val usageHint: String = "",
    val responseTemplate: String = "",
    val cooldownSeconds: Int = 3,
    val isEnabled: Boolean = true,
    val webhookForward: Boolean = false,
    val actionType: BotCommandActionType = BotCommandActionType.STATIC_TEMPLATE,
    val quickReplies: List<BotQuickReply> = emptyList()
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
    val allowDirectMessages: Boolean = true,
    val allowGroupMentions: Boolean = true,
    val welcomeDmMessage: String = "Salut {user} ! Je suis {bot} ({handle}). Écris /menu, un chiffre (1, 2, 3...) ou choisis une action rapide ci-dessous.",
    val starterPresetId: String? = null,
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

// --- Starter Presets (WhatsApp-style Ready-to-Use Kits) ---

data class BotStarterPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultColor: Long,
    val defaultPlugins: Set<String>,
    val welcomeDmMessage: String
)

object BotStarterCatalog {
    val presets: List<BotStarterPreset> = listOf(
        BotStarterPreset(
            id = "assistant_wa",
            title = "Assistant WhatsApp",
            subtitle = "Menu numéroté, rappels, notes, calculatrice & sondages",
            defaultColor = 0xFF25D366L,
            defaultPlugins = setOf("plugin_welcome"),
            welcomeDmMessage = "Bienvenue {user} ! Je suis {bot} ({handle}), votre assistant Raven en DM et en salon.\nChoisissez une option rapide ou tapez /menu :"
        ),
        BotStarterPreset(
            id = "moderator_guard",
            title = "Modérateur & Anti-Spam",
            subtitle = "Bouclier anti-flood, accueil des membres & règles",
            defaultColor = 0xFF5865F2L,
            defaultPlugins = setOf("plugin_welcome", "plugin_antispam", "plugin_events_announcer"),
            welcomeDmMessage = "Bonjour {user}, {bot} veille sur la communauté. Tapez /menu pour accéder aux outils & règles."
        ),
        BotStarterPreset(
            id = "games_quiz",
            title = "Quiz & Mini-Jeux",
            subtitle = "Dés, boule magique 8ball, tirages & quiz rapides",
            defaultColor = 0xFFEB459EL,
            defaultPlugins = emptySet(),
            welcomeDmMessage = "Prêt à jouer {user} ? Lancez /quiz, /roll 20 ou /8ball !"
        ),
        BotStarterPreset(
            id = "utilities_pro",
            title = "Utilitaires & Sondages",
            subtitle = "Notes, minuteurs, calculateur & votes instantanés",
            defaultColor = 0xFF00A8FCL,
            defaultPlugins = setOf("plugin_events_announcer"),
            welcomeDmMessage = "{bot} prêt pour organiser vos notes, rappels et sondages. Essayez /note ou /sondagebot !"
        )
    )

    fun starterCommandsFor(botId: String, botName: String, handle: String, presetId: String?): List<BotCommandSpec> {
        val base = listOf(
            BotCommandSpec(
                id = "cmd_menu_$botId",
                trigger = "/menu",
                description = "Affiche le menu interactif WhatsApp de $botName",
                usageHint = "/menu",
                responseTemplate = "Menu interactif de {bot} ({handle}) — {time} :",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.INTERACTIVE_MENU,
                quickReplies = listOf(
                    BotQuickReply("1. Aide", "/aide"),
                    BotQuickReply("2. Ping", "/ping"),
                    BotQuickReply("3. Bloc-notes", "/note list"),
                    BotQuickReply("4. Lancer dé", "/roll 6"),
                    BotQuickReply("5. Quiz", "/quiz")
                )
            ),
            BotCommandSpec(
                id = "cmd_aide_$botId",
                trigger = "/aide",
                description = "Liste toutes les commandes disponibles",
                usageHint = "/aide",
                responseTemplate = "Commandes de {bot} ({handle}) :",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.BOT_HELP,
                quickReplies = listOf(
                    BotQuickReply("Menu", "/menu"),
                    BotQuickReply("Calculatrice", "/calc 12*4+8"),
                    BotQuickReply("Sondage", "/sondagebot Dispo ce soir ? | Oui | Non")
                )
            ),
            BotCommandSpec(
                id = "cmd_ping_$botId",
                trigger = "/ping",
                description = "Vérifie la réactivité de $botName",
                usageHint = "/ping",
                responseTemplate = "{bot} (@$handle) en ligne pour {user} dans #{channel} ({time}).",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.STATIC_TEMPLATE,
                quickReplies = listOf(
                    BotQuickReply("Menu", "/menu"),
                    BotQuickReply("Infos Bot", "/botinfo")
                )
            ),
            BotCommandSpec(
                id = "cmd_calc_$botId",
                trigger = "/calc",
                description = "Calculatrice mathématique instantanée",
                usageHint = "/calc (15+5)*3",
                responseTemplate = "Résultat calculé par {bot} :",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.CALCULATOR
            ),
            BotCommandSpec(
                id = "cmd_note_$botId",
                trigger = "/note",
                description = "Bloc-notes personnel ou du salon",
                usageHint = "/note add Acheter café | list | del 1 | clear",
                responseTemplate = "Bloc-notes de {bot} :",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.NOTE_VAULT,
                quickReplies = listOf(
                    BotQuickReply("Voir notes", "/note list"),
                    BotQuickReply("Vider", "/note clear")
                )
            ),
            BotCommandSpec(
                id = "cmd_rappel_$botId",
                trigger = "/rappel",
                description = "Programme un rappel automatique minuté",
                usageHint = "/rappel 10s Vérifier le vocal",
                responseTemplate = "Rappel programmé par {bot}",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.REMINDER
            ),
            BotCommandSpec(
                id = "cmd_poll_$botId",
                trigger = "/sondagebot",
                description = "Publie un sondage interactif rapide",
                usageHint = "/sondagebot Sujet ? | Choix A | Choix B",
                responseTemplate = "Sondage lancé par {bot}",
                cooldownSeconds = 2,
                isEnabled = true,
                actionType = BotCommandActionType.POLL
            ),
            BotCommandSpec(
                id = "cmd_roll_$botId",
                trigger = "/roll",
                description = "Lance un dé (NdM ou max)",
                usageHint = "/roll 20",
                responseTemplate = "Tirage {bot}",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.RANDOM_GAME
            ),
            BotCommandSpec(
                id = "cmd_choose_$botId",
                trigger = "/choose",
                description = "Choisit aléatoirement entre plusieurs options",
                usageHint = "/choose Pizza | Sushi | Burger",
                responseTemplate = "Choix {bot}",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.RANDOM_GAME
            ),
            BotCommandSpec(
                id = "cmd_8ball_$botId",
                trigger = "/8ball",
                description = "Pose une question à la boule magique",
                usageHint = "/8ball Va-t-on gagner ?",
                responseTemplate = "Oracle {bot}",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.RANDOM_GAME
            ),
            BotCommandSpec(
                id = "cmd_quiz_$botId",
                trigger = "/quiz",
                description = "Mini-quiz rapide avec boutons de réponse",
                usageHint = "/quiz",
                responseTemplate = "Quiz {bot}",
                cooldownSeconds = 1,
                isEnabled = true,
                actionType = BotCommandActionType.RANDOM_GAME,
                quickReplies = listOf(
                    BotQuickReply("Autre question", "/quiz"),
                    BotQuickReply("Menu", "/menu")
                )
            ),
            BotCommandSpec(
                id = "cmd_info_$botId",
                trigger = "/botinfo",
                description = "Affiche le profil et le statut de $botName",
                usageHint = "/botinfo",
                responseTemplate = "Bot {bot} ({handle}) actif sur {server} ({memberCount} membres) — {date} {time}. Demandé par {user}.",
                cooldownSeconds = 2,
                isEnabled = true,
                actionType = BotCommandActionType.STATIC_TEMPLATE
            )
        )
        return when (presetId) {
            "moderator_guard" -> base + BotCommandSpec(
                id = "cmd_regles_$botId",
                trigger = "/regles",
                description = "Rappelle les règles de bonne conduite",
                usageHint = "/regles",
                responseTemplate = "Règlement de {server} : 1) Respect mutuel 2) Pas de spam/liens suspects 3) Utilisez les bons salons.",
                cooldownSeconds = 2,
                isEnabled = true,
                actionType = BotCommandActionType.STATIC_TEMPLATE,
                quickReplies = listOf(BotQuickReply("Menu", "/menu"), BotQuickReply("Aide", "/aide"))
            )
            else -> base
        }
    }

    fun starterAutoResponsesFor(botId: String, botName: String, presetId: String?): List<BotAutoResponseRule> {
        val base = listOf(
            BotAutoResponseRule(
                id = "auto_menu_$botId",
                name = "Menu rapide WhatsApp",
                pattern = "aide-moi",
                isRegex = false,
                replyTemplate = "Salut {user} ! Voici ce que $botName peut faire : tapez /menu, /calc 5*8, /note add ..., /rappel 15s ... ou /sondagebot.",
                cooldownSeconds = 5,
                isEnabled = true
            )
        )
        return if (presetId == "moderator_guard") {
            base + BotAutoResponseRule(
                id = "auto_rules_$botId",
                name = "Rappel règles",
                pattern = "règles",
                isRegex = false,
                replyTemplate = "Respect & entraide sur {server} ({user}). Tapez /regles pour les détails.",
                cooldownSeconds = 8,
                isEnabled = true
            )
        } else base
    }
}

// --- Extensible Plugin & Automation Architecture ---

data class BotExecutionContext(
    val bot: RavenBot,
    val installation: ServerBotInstallation? = null,
    val server: Server? = null,
    val channelId: String? = null,
    val dmConversationId: String? = null,
    val eventTrigger: BotEventTrigger,
    val actorUid: String,
    val actorName: String,
    val messageText: String = "",
    val commandArgs: String = ""
)

data class BotExecutionResult(
    val replyMessage: String? = null,
    val logSummary: String,
    val isAlert: Boolean = false,
    val quickReplies: List<BotQuickReply> = emptyList()
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
                val targetName = context.server?.name ?: "Raven"
                return BotExecutionResult(
                    replyMessage = "Bienvenue ${context.actorName} dans $targetName ! Tapez /menu ou consultez #annonces.",
                    logSummary = "Accueil envoyé pour ${context.actorName}",
                    quickReplies = listOf(BotQuickReply("Menu", "/menu"), BotQuickReply("Aide", "/aide"))
                )
            }
        },
        object : BotPluginHook {
            override val id = "plugin_antispam"
            override val name = "Bouclier Anti-Spam"
            override val description = "Surveille les messages répétés et signale les comportements abusifs."
            override val requiredPermission = BotPermission.MODERATION_SHIELD
            override fun handleEvent(context: BotExecutionContext): BotExecutionResult? {
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
