package com.example.model

/**
 * Current RAVEN host semantic version used to evaluate extension compatibility.
 * Derived cleanly from the application version baseline ("1.0" -> "1.0.0").
 */
const val RAVEN_HOST_VERSION = "1.0.0"

/**
 * Strict Semantic Versioning (major.minor.patch) descriptor for RAVEN plugins and host compatibility checks.
 */
data class PluginSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<PluginSemVer> {

    override fun compareTo(other: PluginSemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val SEMVER_REGEX = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

        fun parse(raw: String): Result<PluginSemVer> {
            val cleaned = raw.trim()
            val match = SEMVER_REGEX.matchEntire(cleaned)
                ?: return Result.failure(IllegalArgumentException("Version SemVer invalide : '$raw' (attendu major.minor.patch)."))
            val major = match.groupValues[1].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Major invalide dans '$raw'."))
            val minor = match.groupValues[2].toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Minor invalide dans '$raw'."))
            val patch = match.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            if (major < 0 || minor < 0 || patch < 0) {
                return Result.failure(IllegalArgumentException("Les composants de version doivent être positifs."))
            }
            return Result.success(PluginSemVer(major, minor, patch))
        }
    }
}

enum class PluginPermissionRisk(val label: String) {
    LOW("Faible"),
    MEDIUM("Modéré"),
    SENSITIVE("Sensible")
}

/**
 * Granular capability permissions for RAVEN Extensions.
 * A plugin NEVER receives automatic access to user data: every capability must be
 * both declared in [PluginManifest.requestedPermissions] AND explicitly granted by the user.
 */
enum class PluginPermission(
    val title: String,
    val description: String,
    val risk: PluginPermissionRisk
) {
    READ_ACTIVE_USER_PUBLIC(
        title = "Profil public uniquement",
        description = "Lit votre pseudo et statut public (jamais votre email, UID Firebase ou notes privées).",
        risk = PluginPermissionRisk.LOW
    ),
    READ_SERVER_METADATA(
        title = "Métadonnées serveur non sensibles",
        description = "Lit le nom du serveur actif et la liste des salons accessibles (hors mots bloqués et paramètres privés).",
        risk = PluginPermissionRisk.LOW
    ),
    MANAGE_UI_WIDGET(
        title = "Badge d'état UI",
        description = "Affiche un indicateur d'état synthétique dans l'interface des extensions.",
        risk = PluginPermissionRisk.LOW
    ),
    LISTEN_APP_EVENTS(
        title = "Écouter les événements RAVEN",
        description = "Reçoit les signaux typés de changement de salon, vocal ou nouveaux messages.",
        risk = PluginPermissionRisk.MEDIUM
    ),
    EXECUTE_SLASH_COMMAND(
        title = "Commandes slash (/cmd)",
        description = "Enregistre et exécute des commandes slash dédiées dans les salons.",
        risk = PluginPermissionRisk.MEDIUM
    ),
    SEND_NOTIFICATIONS(
        title = "Envoyer des notifications",
        description = "Affiche des alertes dans votre boîte de réception et notifications locales (avec anti-spam).",
        risk = PluginPermissionRisk.MEDIUM
    ),
    READ_MESSAGES_SUMMARY(
        title = "Aperçus de messages récents",
        description = "Lit un aperçu expurgé (sans secrets ni PII) des derniers messages du salon actif.",
        risk = PluginPermissionRisk.SENSITIVE
    ),
    POST_CHANNEL_MESSAGE(
        title = "Publier dans un salon",
        description = "Envoie des messages étiquetés [Extension] dans le salon cible (soumis au quota).",
        risk = PluginPermissionRisk.SENSITIVE
    )
}

enum class PluginLifecycleState(val label: String) {
    REGISTERED("Enregistré"),
    INCOMPATIBLE("Incompatible"),
    DISABLED("Désactivé"),
    ACTIVE("Actif"),
    FAULTED("Quarantaine (Erreur)")
}

enum class PluginTrustTier(val label: String) {
    OFFICIAL_BUNDLED("Officiel RAVEN"),
    VERIFIED_EXTENSION("Vérifié"),
    LOCAL_DEVELOPER("Développeur Local")
}

enum class PluginCategory(val label: String) {
    PRODUCTIVITY("Productivité"),
    MODERATION("Sécurité & Modération"),
    AUDIO_VOICE("Audio & Vocal"),
    UTILITIES("Utilitaires")
}

enum class PluginEventType(val label: String) {
    MESSAGE_POSTED("Message envoyé"),
    CHANNEL_CHANGED("Changement de salon"),
    SERVER_SELECTED("Changement de serveur"),
    VOICE_JOINED("Entrée salon vocal"),
    VOICE_LEFT("Sortie salon vocal"),
    EVENT_CREATED("Événement planifié")
}

enum class PluginConfigFieldType {
    BOOLEAN,
    STRING,
    INT,
    SELECT
}

data class PluginConfigField(
    val key: String,
    val label: String,
    val description: String,
    val type: PluginConfigFieldType,
    val defaultValue: String,
    val options: List<String> = emptyList(),
    val minInt: Int = 0,
    val maxInt: Int = 1000,
    val maxLength: Int = 200
)

data class PluginSlashCommandSpec(
    val trigger: String,
    val description: String,
    val usageHint: String = ""
)

/**
 * Declarative manifest required for every RAVEN plugin/extension.
 * Enforces identity, SemVer, minimum host version, requested capabilities, and configuration schema.
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val minRavenVersion: String,
    val maxRavenVersion: String? = null,
    val description: String,
    val author: String,
    val category: PluginCategory = PluginCategory.UTILITIES,
    val trustTier: PluginTrustTier = PluginTrustTier.OFFICIAL_BUNDLED,
    val requestedPermissions: Set<PluginPermission> = emptySet(),
    val subscribedEvents: Set<PluginEventType> = emptySet(),
    val slashCommands: List<PluginSlashCommandSpec> = emptyList(),
    val configSchema: List<PluginConfigField> = emptyList(),
    val maxActionsPerMinute: Int = 20,
    val sha256Signature: String = ""
)

data class PluginCompatibilityResult(
    val isCompatible: Boolean,
    val reason: String,
    val hostVersion: String = RAVEN_HOST_VERSION,
    val minVersion: String,
    val maxVersion: String? = null
)

enum class PluginLogLevel(val label: String) {
    INFO("INFO"),
    WARN("ALERTE"),
    ERROR("ERREUR"),
    SECURITY("SÉCURITÉ")
}

data class PluginLogEntry(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val level: PluginLogLevel,
    val action: String,
    val message: String,
    val timestampMs: Long,
    val timestampLabel: String
)

data class UserPluginState(
    val pluginId: String,
    val installedVersion: String,
    val isEnabled: Boolean = false,
    val lifecycleState: PluginLifecycleState = PluginLifecycleState.DISABLED,
    val grantedPermissions: Set<PluginPermission> = emptySet(),
    val deniedPermissions: Set<PluginPermission> = emptySet(),
    val configValues: Map<String, String> = emptyMap(),
    val consecutiveErrors: Int = 0,
    val lastErrorMessage: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/**
 * Redacted public DTOs exposed through the controlled [RavenPluginApi].
 * Raw domain entities (User emails, UIDs, tokens, private server secrets) are NEVER exposed.
 */
data class PluginPublicUserSummary(
    val displayName: String,
    val statusLabel: String
)

data class PluginServerSummary(
    val serverId: String,
    val serverName: String,
    val visibleChannelNames: List<String>,
    val memberCount: Int
)

data class PluginMessagePreview(
    val channelId: String,
    val authorDisplayName: String,
    val sanitizedPreview: String,
    val timestampLabel: String
)

sealed interface RavenPluginEvent {
    val eventType: PluginEventType
    val timestampMs: Long

    data class MessagePosted(
        val serverId: String,
        val channelId: String,
        val authorDisplayName: String,
        val sanitizedPreview: String,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.MESSAGE_POSTED
    }

    data class ChannelChanged(
        val serverId: String,
        val channelId: String,
        val channelName: String,
        val channelType: String,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.CHANNEL_CHANGED
    }

    data class ServerSelected(
        val serverId: String,
        val serverName: String,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.SERVER_SELECTED
    }

    data class VoiceRoomJoined(
        val serverId: String,
        val channelId: String,
        val channelName: String,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.VOICE_JOINED
    }

    data class VoiceRoomLeft(
        val serverId: String,
        val channelId: String,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.VOICE_LEFT
    }

    data class EventScheduled(
        val serverId: String,
        val title: String,
        val startAtMs: Long,
        override val timestampMs: Long
    ) : RavenPluginEvent {
        override val eventType = PluginEventType.EVENT_CREATED
    }
}

data class PluginUiItem(
    val manifest: PluginManifest,
    val compatibility: PluginCompatibilityResult,
    val state: UserPluginState,
    val pendingPermissions: Set<PluginPermission>
) {
    val effectiveGrantedPermissions: Set<PluginPermission>
        get() = manifest.requestedPermissions.intersect(state.grantedPermissions)
}

data class PluginManagerUiState(
    val hostVersion: String = RAVEN_HOST_VERSION,
    val plugins: List<PluginUiItem> = emptyList(),
    val logs: List<PluginLogEntry> = emptyList(),
    val selectedCategory: PluginCategory? = null,
    val selectedLogLevel: PluginLogLevel? = null,
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    val activeCount: Int
        get() = plugins.count { it.state.lifecycleState == PluginLifecycleState.ACTIVE }
}
