package com.example.data

import com.example.model.*

/**
 * Thrown whenever a plugin invokes a controlled [RavenPluginApi] capability without
 * explicit user consent for that [PluginPermission].
 */
class PluginPermissionDeniedException(
    val pluginId: String,
    val missingPermission: PluginPermission
) : SecurityException(
    "Plugin '$pluginId' non autorisé : permission '${missingPermission.name}' requise."
)

/**
 * Read-only initialization context provided during [RavenPlugin.onLoad].
 */
data class PluginContext(
    val manifest: PluginManifest,
    val hostVersion: String,
    val config: Map<String, String>
)

/**
 * Controlled internal API exposed to active plugins.
 * Plugins NEVER receive direct access to repositories, Firestore, Auth, tokens, or raw User records.
 */
interface RavenPluginApi {
    val pluginId: String
    fun grantedPermissions(): Set<PluginPermission>
    fun readConfig(): Map<String, String>
    fun readPublicUserSummary(): PluginPublicUserSummary
    fun readActiveServerSummary(): PluginServerSummary?
    fun readRecentMessagePreviews(channelId: String, limit: Int = 5): List<PluginMessagePreview>
    fun postChannelNotice(channelId: String, text: String): Boolean
    fun emitNotification(title: String, body: String): Boolean
    fun logInfo(action: String, message: String)
    fun logWarn(action: String, message: String)
}

/**
 * Contract implemented by every verified RAVEN extension/plugin.
 * All lifecycle methods are invoked inside an error-isolating sandbox by [PluginRepository].
 */
interface RavenPlugin {
    val manifest: PluginManifest
    fun onLoad(context: PluginContext) {}
    fun onEnable(api: RavenPluginApi) {}
    fun onConfigUpdated(config: Map<String, String>, api: RavenPluginApi) {}
    fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {}
    fun onCommand(command: String, args: String, channelId: String, api: RavenPluginApi): Boolean = false
    fun onDisable() {}
    fun onUnload() {}
}

/**
 * Sandbox proxy enforcing permissions, sliding-window rate limits, PII redaction,
 * and automatic SECURITY audit logging on every call.
 */
internal class ScopedPluginApi(
    private val manifest: PluginManifest,
    private val grantedPermsProvider: () -> Set<PluginPermission>,
    private val configProvider: () -> Map<String, String>,
    private val userProvider: () -> User,
    private val activeServerProvider: () -> Server?,
    private val messagesProvider: (String) -> List<Message>,
    private val onPostMessage: (channelId: String, pluginName: String, cleanText: String) -> Unit,
    private val onEmitNotification: (title: String, body: String, sourceLabel: String) -> Unit,
    private val onRecordLog: (PluginLogLevel, action: String, message: String) -> Unit,
    private val nowProvider: () -> Long
) : RavenPluginApi {

    private val actionTimestampsMs = ArrayDeque<Long>()

    override val pluginId: String
        get() = manifest.id

    override fun grantedPermissions(): Set<PluginPermission> =
        manifest.requestedPermissions.intersect(grantedPermsProvider())

    override fun readConfig(): Map<String, String> = configProvider().toMap()

    override fun readPublicUserSummary(): PluginPublicUserSummary {
        requirePermission(PluginPermission.READ_ACTIVE_USER_PUBLIC, "READ_PUBLIC_USER")
        val u = userProvider()
        return PluginPublicUserSummary(
            displayName = PluginSecurityGuard.sanitizeText(u.name, 48),
            statusLabel = u.status.label
        )
    }

    override fun readActiveServerSummary(): PluginServerSummary? {
        requirePermission(PluginPermission.READ_SERVER_METADATA, "READ_SERVER_META")
        val srv = activeServerProvider()?.takeIf { !it.isDmTab } ?: return null
        return PluginServerSummary(
            serverId = srv.id,
            serverName = srv.name,
            visibleChannelNames = srv.channels.filter { !it.isPrivate }.map { it.name }.take(25),
            memberCount = srv.members.size
        )
    }

    override fun readRecentMessagePreviews(channelId: String, limit: Int): List<PluginMessagePreview> {
        requirePermission(PluginPermission.READ_MESSAGES_SUMMARY, "READ_MESSAGES")
        val bounded = limit.coerceIn(1, 10)
        return messagesProvider(channelId)
            .takeLast(bounded)
            .map { msg ->
                PluginMessagePreview(
                    channelId = channelId,
                    authorDisplayName = PluginSecurityGuard.sanitizeText(msg.sender.name, 40),
                    sanitizedPreview = PluginSecurityGuard.sanitizeText(msg.text, 160),
                    timestampLabel = msg.timestamp
                )
            }
    }

    override fun postChannelNotice(channelId: String, text: String): Boolean {
        requirePermission(PluginPermission.POST_CHANNEL_MESSAGE, "POST_MESSAGE")
        if (!consumeActionQuota()) {
            onRecordLog(PluginLogLevel.WARN, "RATE_LIMIT", "Quota d'envoi atteint (${manifest.maxActionsPerMinute}/min).")
            return false
        }
        val clean = PluginSecurityGuard.sanitizeText(text, 350)
        if (clean.isBlank()) return false
        onPostMessage(channelId, manifest.name, clean)
        onRecordLog(PluginLogLevel.INFO, "POST_MESSAGE", "Message publié dans #$channelId.")
        return true
    }

    override fun emitNotification(title: String, body: String): Boolean {
        requirePermission(PluginPermission.SEND_NOTIFICATIONS, "EMIT_NOTIFICATION")
        if (!consumeActionQuota()) {
            onRecordLog(PluginLogLevel.WARN, "RATE_LIMIT", "Quota de notifications atteint (${manifest.maxActionsPerMinute}/min).")
            return false
        }
        val cleanTitle = PluginSecurityGuard.sanitizeText(title, 80)
        val cleanBody = PluginSecurityGuard.sanitizeText(body, 200)
        if (cleanTitle.isBlank() || cleanBody.isBlank()) return false
        onEmitNotification(cleanTitle, cleanBody, manifest.name)
        onRecordLog(PluginLogLevel.INFO, "NOTIFICATION", "$cleanTitle — $cleanBody")
        return true
    }

    override fun logInfo(action: String, message: String) {
        onRecordLog(PluginLogLevel.INFO, action.take(40), PluginSecurityGuard.sanitizeText(message, 250))
    }

    override fun logWarn(action: String, message: String) {
        onRecordLog(PluginLogLevel.WARN, action.take(40), PluginSecurityGuard.sanitizeText(message, 250))
    }

    private fun requirePermission(permission: PluginPermission, action: String) {
        val granted = grantedPermissions()
        if (permission !in granted) {
            onRecordLog(
                PluginLogLevel.SECURITY,
                "PERMISSION_DENIED_$action",
                "Accès bloqué : la permission ${permission.name} n'est pas accordée."
            )
            throw PluginPermissionDeniedException(manifest.id, permission)
        }
    }

    private fun consumeActionQuota(): Boolean {
        val now = nowProvider()
        val windowStart = now - 60_000L
        while (actionTimestampsMs.isNotEmpty() && actionTimestampsMs.first() < windowStart) {
            actionTimestampsMs.removeFirst()
        }
        if (actionTimestampsMs.size >= manifest.maxActionsPerMinute) {
            return false
        }
        actionTimestampsMs.addLast(now)
        return true
    }
}

/**
 * Curated bundled RAVEN extensions plus an explicit future-version extension
 * demonstrating compatibility enforcement.
 */
object BuiltInRavenPlugins {

    fun all(): List<RavenPlugin> = listOf(
        PomodoroFocusPlugin(),
        KeywordRadarPlugin(),
        StandupHelperPlugin(),
        LinkHygienePlugin(),
        FutureSpatialAudioPreviewPlugin()
    )

    class PomodoroFocusPlugin : RavenPlugin {
        override val manifest = PluginManifest(
            id = "com.raven.ext.pomodoro",
            name = "Focus & Pomodoro",
            version = "1.2.0",
            minRavenVersion = "1.0.0",
            description = "Lance des sessions de travail minutées (/focus) avec annonce optionnelle dans le salon et alerte dédiée.",
            author = "RAVEN Core Extensions",
            category = PluginCategory.PRODUCTIVITY,
            trustTier = PluginTrustTier.OFFICIAL_BUNDLED,
            requestedPermissions = setOf(
                PluginPermission.EXECUTE_SLASH_COMMAND,
                PluginPermission.POST_CHANNEL_MESSAGE,
                PluginPermission.SEND_NOTIFICATIONS,
                PluginPermission.LISTEN_APP_EVENTS
            ),
            subscribedEvents = setOf(PluginEventType.VOICE_JOINED),
            slashCommands = listOf(
                PluginSlashCommandSpec("/focus", "Démarre une session Focus (ex: /focus 25)", "[minutes]")
            ),
            configSchema = listOf(
                PluginConfigField(
                    key = "focus_minutes",
                    label = "Durée par défaut (min)",
                    description = "Durée utilisée si aucun argument n'est saisi.",
                    type = PluginConfigFieldType.INT,
                    defaultValue = "25",
                    minInt = 5,
                    maxInt = 90
                ),
                PluginConfigField(
                    key = "announce_in_channel",
                    label = "Annoncer dans le salon",
                    description = "Publie un message d'engagement Focus dans le salon actif.",
                    type = PluginConfigFieldType.BOOLEAN,
                    defaultValue = "true"
                ),
                PluginConfigField(
                    key = "focus_theme",
                    label = "Mode Focus",
                    description = "Profil affiché lors du démarrage de la session.",
                    type = PluginConfigFieldType.SELECT,
                    defaultValue = "Deep Work",
                    options = listOf("Deep Work", "Étude", "Code", "Révision")
                )
            ),
            maxActionsPerMinute = 10,
            sha256Signature = "rvn_sig_pomodoro_v1_2_0"
        )

        override fun onEnable(api: RavenPluginApi) {
            api.logInfo("LIFECYCLE_ENABLE", "Extension Focus & Pomodoro prête.")
        }

        override fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {
            if (event is RavenPluginEvent.VoiceRoomJoined) {
                val cfg = api.readConfig()
                val theme = cfg["focus_theme"] ?: "Deep Work"
                api.logInfo("VOICE_FOCUS_HINT", "Salon vocal '${event.channelName}' rejoint (mode $theme).")
            }
        }

        override fun onCommand(command: String, args: String, channelId: String, api: RavenPluginApi): Boolean {
            if (!command.equals("/focus", ignoreCase = true)) return false
            val cfg = api.readConfig()
            val defaultMin = cfg["focus_minutes"]?.toIntOrNull() ?: 25
            val minutes = args.trim().toIntOrNull()?.coerceIn(5, 90) ?: defaultMin
            val theme = cfg["focus_theme"] ?: "Deep Work"
            val announce = cfg["announce_in_channel"] == "true"

            if (PluginPermission.SEND_NOTIFICATIONS in api.grantedPermissions()) {
                api.emitNotification("Session Focus ($theme)", "Minuteur démarré pour $minutes min.")
            }
            if (announce && PluginPermission.POST_CHANNEL_MESSAGE in api.grantedPermissions()) {
                api.postChannelNotice(channelId, "⏱️ Session **$theme** lancée pour **$minutes min**. Bon courage !")
            }
            api.logInfo("CMD_FOCUS", "Session $theme ($minutes min) déclenchée sur #$channelId.")
            return true
        }
    }

    class KeywordRadarPlugin : RavenPlugin {
        private var lastAlertMs: Long = 0L

        override val manifest = PluginManifest(
            id = "com.raven.ext.keyword_radar",
            name = "Radar Mots-Clés",
            version = "1.1.0",
            minRavenVersion = "1.0.0",
            description = "Détecte vos mots-clés prioritaires dans les salons suivis et déclenche une alerte discrète sans lire vos données privées.",
            author = "RAVEN Core Extensions",
            category = PluginCategory.UTILITIES,
            trustTier = PluginTrustTier.OFFICIAL_BUNDLED,
            requestedPermissions = setOf(
                PluginPermission.LISTEN_APP_EVENTS,
                PluginPermission.READ_MESSAGES_SUMMARY,
                PluginPermission.SEND_NOTIFICATIONS
            ),
            subscribedEvents = setOf(PluginEventType.MESSAGE_POSTED),
            configSchema = listOf(
                PluginConfigField(
                    key = "keywords",
                    label = "Mots-clés surveillés",
                    description = "Liste séparée par des virgules.",
                    type = PluginConfigFieldType.STRING,
                    defaultValue = "urgent,tournoi,réunion",
                    maxLength = 150
                ),
                PluginConfigField(
                    key = "cooldown_sec",
                    label = "Délai anti-spam (sec)",
                    description = "Intervalle minimal entre deux alertes.",
                    type = PluginConfigFieldType.INT,
                    defaultValue = "20",
                    minInt = 5,
                    maxInt = 300
                ),
                PluginConfigField(
                    key = "case_sensitive",
                    label = "Sensible à la casse",
                    description = "Distingue majuscules et minuscules.",
                    type = PluginConfigFieldType.BOOLEAN,
                    defaultValue = "false"
                )
            ),
            maxActionsPerMinute = 12,
            sha256Signature = "rvn_sig_keyword_radar_v1_1_0"
        )

        override fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {
            if (event !is RavenPluginEvent.MessagePosted) return
            val cfg = api.readConfig()
            val keywords = cfg["keywords"].orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.length >= 2 }
            if (keywords.isEmpty()) return

            val ignoreCase = cfg["case_sensitive"] != "true"
            val matched = keywords.firstOrNull { kw -> event.sanitizedPreview.contains(kw, ignoreCase = ignoreCase) }
                ?: return

            val cooldownMs = (cfg["cooldown_sec"]?.toLongOrNull() ?: 20L) * 1000L
            if (event.timestampMs - lastAlertMs < cooldownMs) return
            lastAlertMs = event.timestampMs

            if (PluginPermission.SEND_NOTIFICATIONS in api.grantedPermissions()) {
                api.emitNotification(
                    title = "Mot-clé détecté : « $matched »",
                    body = "${event.authorDisplayName} : ${event.sanitizedPreview}"
                )
            }
            api.logInfo("KEYWORD_HIT", "Mot-clé '$matched' repéré dans #${event.channelId}.")
        }
    }

    class StandupHelperPlugin : RavenPlugin {
        override val manifest = PluginManifest(
            id = "com.raven.ext.standup",
            name = "Assistant Daily Standup",
            version = "1.0.0",
            minRavenVersion = "1.0.0",
            description = "Formate automatiquement un rapport quotidien (/standup fait | objectif | blocage) adapté à la communauté.",
            author = "RAVEN Productivity Lab",
            category = PluginCategory.PRODUCTIVITY,
            trustTier = PluginTrustTier.VERIFIED_EXTENSION,
            requestedPermissions = setOf(
                PluginPermission.EXECUTE_SLASH_COMMAND,
                PluginPermission.POST_CHANNEL_MESSAGE,
                PluginPermission.READ_ACTIVE_USER_PUBLIC,
                PluginPermission.READ_SERVER_METADATA
            ),
            slashCommands = listOf(
                PluginSlashCommandSpec("/standup", "Publie un point d'avancement formaté", "fait | aujourd'hui | blocage")
            ),
            configSchema = listOf(
                PluginConfigField(
                    key = "template_style",
                    label = "Style du rapport",
                    description = "Format d'affichage dans le salon.",
                    type = PluginConfigFieldType.SELECT,
                    defaultValue = "Détaillé",
                    options = listOf("Compact", "Détaillé")
                ),
                PluginConfigField(
                    key = "include_author",
                    label = "Inclure votre pseudo public",
                    description = "Ajoute votre nom affiché dans l'en-tête.",
                    type = PluginConfigFieldType.BOOLEAN,
                    defaultValue = "true"
                )
            ),
            maxActionsPerMinute = 10,
            sha256Signature = "rvn_sig_standup_v1_0_0"
        )

        override fun onCommand(command: String, args: String, channelId: String, api: RavenPluginApi): Boolean {
            if (!command.equals("/standup", ignoreCase = true)) return false
            val cfg = api.readConfig()
            val style = cfg["template_style"] ?: "Détaillé"
            val authorHeader = if (cfg["include_author"] == "true" &&
                PluginPermission.READ_ACTIVE_USER_PUBLIC in api.grantedPermissions()
            ) {
                " (${api.readPublicUserSummary().displayName})"
            } else ""

            val parts = args.split("|").map { it.trim() }.filter { it.isNotBlank() }
            val yesterday = parts.getOrElse(0) { "Tâches précédentes terminées" }
            val today = parts.getOrElse(1) { "Poursuite des priorités du jour" }
            val blockers = parts.getOrElse(2) { "Aucun blocage" }

            val body = if (style == "Compact") {
                "📋 Standup$authorHeader — Hier: $yesterday • Aujourd'hui: $today • Blocages: $blockers"
            } else {
                "📋 **Daily Standup$authorHeader**\n✅ **Fait :** $yesterday\n🎯 **Objectif :** $today\n🚧 **Blocages :** $blockers"
            }
            api.postChannelNotice(channelId, body)
            return true
        }
    }

    class LinkHygienePlugin : RavenPlugin {
        private val suspiciousDomains = listOf("http://", "bit.ly/", "tinyurl.com/", "t.co/")

        override val manifest = PluginManifest(
            id = "com.raven.ext.link_hygiene",
            name = "Inspecteur de Liens",
            version = "1.3.0",
            minRavenVersion = "1.0.0",
            description = "Signale localement les liens non chiffrés (HTTP) ou raccourcis potentiellement risqués dans les messages reçus.",
            author = "RAVEN Security Team",
            category = PluginCategory.MODERATION,
            trustTier = PluginTrustTier.OFFICIAL_BUNDLED,
            requestedPermissions = setOf(
                PluginPermission.LISTEN_APP_EVENTS,
                PluginPermission.READ_MESSAGES_SUMMARY,
                PluginPermission.SEND_NOTIFICATIONS,
                PluginPermission.POST_CHANNEL_MESSAGE
            ),
            subscribedEvents = setOf(PluginEventType.MESSAGE_POSTED),
            configSchema = listOf(
                PluginConfigField(
                    key = "warn_in_channel",
                    label = "Avertir dans le salon",
                    description = "Poste un rappel de prudence dans le salon si un lien suspect apparaît.",
                    type = PluginConfigFieldType.BOOLEAN,
                    defaultValue = "false"
                ),
                PluginConfigField(
                    key = "notify_user",
                    label = "Notification de sécurité",
                    description = "Envoie une alerte personnelle lors de la détection.",
                    type = PluginConfigFieldType.BOOLEAN,
                    defaultValue = "true"
                )
            ),
            maxActionsPerMinute = 15,
            sha256Signature = "rvn_sig_link_hygiene_v1_3_0"
        )

        override fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {
            if (event !is RavenPluginEvent.MessagePosted) return
            val lower = event.sanitizedPreview.lowercase()
            val hit = suspiciousDomains.firstOrNull { lower.contains(it) } ?: return
            val cfg = api.readConfig()

            api.logWarn("SUSPICIOUS_LINK", "Marqueur '$hit' repéré dans #${event.channelId}.")
            if (cfg["notify_user"] == "true" && PluginPermission.SEND_NOTIFICATIONS in api.grantedPermissions()) {
                api.emitNotification(
                    title = "Lien à vérifier ($hit)",
                    body = "Un lien non chiffré ou raccourci a été détecté dans le salon."
                )
            }
            if (cfg["warn_in_channel"] == "true" && PluginPermission.POST_CHANNEL_MESSAGE in api.grantedPermissions()) {
                api.postChannelNotice(
                    event.channelId,
                    "🛡️ Prudence : un lien non chiffré ou raccourci ($hit) a été détecté. Vérifiez sa destination."
                )
            }
        }
    }

    class FutureSpatialAudioPreviewPlugin : RavenPlugin {
        override val manifest = PluginManifest(
            id = "com.raven.ext.spatial_audio_pro",
            name = "Audio Spatial 3D",
            version = "2.0.0",
            minRavenVersion = "2.5.0",
            description = "Spatialisation binaurale avancée pour les salons vocaux (nécessite le moteur audio RAVEN v2.5.0+).",
            author = "RAVEN Audio Lab",
            category = PluginCategory.AUDIO_VOICE,
            trustTier = PluginTrustTier.VERIFIED_EXTENSION,
            requestedPermissions = setOf(
                PluginPermission.LISTEN_APP_EVENTS,
                PluginPermission.MANAGE_UI_WIDGET
            ),
            subscribedEvents = setOf(PluginEventType.VOICE_JOINED, PluginEventType.VOICE_LEFT),
            sha256Signature = "rvn_sig_spatial_v2_0_0"
        )
    }
}

/**
 * Thread-safe registry for bundled and statically compiled third-party [RavenPlugin] modules.
 * External binary code loading is explicitly disallowed: every registered plugin passes
 * [PluginSecurityGuard.validateManifest] prior to entry.
 */
class RavenPluginRegistry(
    initialPlugins: List<RavenPlugin> = BuiltInRavenPlugins.all()
) {
    private val plugins = linkedMapOf<String, RavenPlugin>()

    init {
        initialPlugins.forEach { registerPlugin(it) }
    }

    @Synchronized
    fun registerPlugin(plugin: RavenPlugin): Result<Unit> {
        PluginSecurityGuard.validateManifest(plugin.manifest).onFailure { return Result.failure(it) }
        if (plugin.manifest.id in plugins) {
            return Result.failure(IllegalStateException("Un plugin avec l'ID '${plugin.manifest.id}' est déjà enregistré."))
        }
        plugins[plugin.manifest.id] = plugin
        return Result.success(Unit)
    }

    @Synchronized
    fun unregisterPlugin(pluginId: String): RavenPlugin? = plugins.remove(pluginId)

    @Synchronized
    fun allPlugins(): List<RavenPlugin> = plugins.values.toList()

    @Synchronized
    fun findPlugin(pluginId: String): RavenPlugin? = plugins[pluginId]
}
