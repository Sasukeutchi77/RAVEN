package com.example.data

import com.example.model.*

/**
 * Evaluates SemVer compatibility between a [PluginManifest] and the current RAVEN host runtime.
 */
object PluginCompatibilityChecker {

    fun checkCompatibility(
        manifest: PluginManifest,
        hostVersionRaw: String = RAVEN_HOST_VERSION
    ): PluginCompatibilityResult {
        val hostVer = PluginSemVer.parse(hostVersionRaw).getOrElse {
            return PluginCompatibilityResult(
                isCompatible = false,
                reason = "Version hôte RAVEN invalide ($hostVersionRaw).",
                hostVersion = hostVersionRaw,
                minVersion = manifest.minRavenVersion,
                maxVersion = manifest.maxRavenVersion
            )
        }
        val pluginVer = PluginSemVer.parse(manifest.version).getOrElse {
            return PluginCompatibilityResult(
                isCompatible = false,
                reason = "Version du plugin invalide (${manifest.version}).",
                hostVersion = hostVer.toString(),
                minVersion = manifest.minRavenVersion,
                maxVersion = manifest.maxRavenVersion
            )
        }
        val minVer = PluginSemVer.parse(manifest.minRavenVersion).getOrElse {
            return PluginCompatibilityResult(
                isCompatible = false,
                reason = "Version minimale RAVEN invalide (${manifest.minRavenVersion}).",
                hostVersion = hostVer.toString(),
                minVersion = manifest.minRavenVersion,
                maxVersion = manifest.maxRavenVersion
            )
        }

        if (hostVer < minVer) {
            return PluginCompatibilityResult(
                isCompatible = false,
                reason = "Incompatible : requiert RAVEN v$minVer ou supérieur (installé : v$hostVer).",
                hostVersion = hostVer.toString(),
                minVersion = minVer.toString(),
                maxVersion = manifest.maxRavenVersion
            )
        }

        val maxRaw = manifest.maxRavenVersion
        if (!maxRaw.isNullOrBlank()) {
            val maxVer = PluginSemVer.parse(maxRaw).getOrElse {
                return PluginCompatibilityResult(
                    isCompatible = false,
                    reason = "Version maximale RAVEN invalide ($maxRaw).",
                    hostVersion = hostVer.toString(),
                    minVersion = minVer.toString(),
                    maxVersion = maxRaw
                )
            }
            if (hostVer > maxVer) {
                return PluginCompatibilityResult(
                    isCompatible = false,
                    reason = "Obsolète : ce plugin ($pluginVer) supporte au maximum RAVEN v$maxVer.",
                    hostVersion = hostVer.toString(),
                    minVersion = minVer.toString(),
                    maxVersion = maxVer.toString()
                )
            }
        }

        return PluginCompatibilityResult(
            isCompatible = true,
            reason = "Compatible avec RAVEN v$hostVer (v$pluginVer).",
            hostVersion = hostVer.toString(),
            minVersion = minVer.toString(),
            maxVersion = manifest.maxRavenVersion
        )
    }
}

/**
 * Security validator enforcing:
 * - Strict canonical plugin IDs and command triggers
 * - Absolute prohibition of Dynamic Code Loading (DCL: .dex/.jar/.so/eval/ClassLoader/remote scripts)
 * - Schema-bound configuration validation
 * - PII/token redaction before exposing messages or posting plugin output
 */
object PluginSecurityGuard {

    private val PLUGIN_ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")
    private val COMMAND_TRIGGER_REGEX = Regex("^/[a-z0-9_-]{2,24}$")

    private val FORBIDDEN_DCL_MARKERS = listOf(
        ".dex",
        ".jar",
        ".apk",
        ".so",
        "dexclassloader",
        "pathclassloader",
        "inmemorydexclassloader",
        "system.load",
        "javascript:",
        "vbscript:",
        "<script",
        "eval(",
        "runtime.getruntime"
    )

    private val SECRET_REDACTION_PATTERNS = listOf(
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        Regex("rvn_bot_[A-Za-z0-9._-]{8,}"),
        Regex("AIza[0-9A-Za-z\\-_]{20,}"),
        Regex("Bearer\\s+[A-Za-z0-9._\\-]{10,}", RegexOption.IGNORE_CASE)
    )

    fun assertNoDynamicCodeAttempt(raw: String, fieldLabel: String): Result<Unit> {
        val lower = raw.lowercase()
        val hit = FORBIDDEN_DCL_MARKERS.firstOrNull { marker -> lower.contains(marker) }
        if (hit != null) {
            return Result.failure(
                SecurityException("Exécution dynamique ou code non vérifié interdit dans '$fieldLabel' (marqueur détecté : $hit).")
            )
        }
        return Result.success(Unit)
    }

    fun validateManifest(manifest: PluginManifest): Result<Unit> {
        if (!PLUGIN_ID_REGEX.matches(manifest.id)) {
            return Result.failure(
                IllegalArgumentException("Identifiant plugin invalide '${manifest.id}'. Format requis : minuscules, chiffres, '.', '_' ou '-' (3..64 car.).")
            )
        }
        if (manifest.name.isBlank() || manifest.name.length > 60) {
            return Result.failure(IllegalArgumentException("Le nom du plugin doit contenir entre 1 et 60 caractères."))
        }
        if (manifest.author.isBlank() || manifest.author.length > 60) {
            return Result.failure(IllegalArgumentException("L'auteur du plugin est requis (max 60 caractères)."))
        }
        if (manifest.description.length > 500) {
            return Result.failure(IllegalArgumentException("La description ne doit pas dépasser 500 caractères."))
        }

        PluginSemVer.parse(manifest.version).onFailure { return Result.failure(it) }
        PluginSemVer.parse(manifest.minRavenVersion).onFailure { return Result.failure(it) }
        manifest.maxRavenVersion?.takeIf { it.isNotBlank() }?.let { maxV ->
            PluginSemVer.parse(maxV).onFailure { return Result.failure(it) }
        }

        if (manifest.maxActionsPerMinute !in 1..60) {
            return Result.failure(IllegalArgumentException("Le quota d'actions par minute doit être entre 1 et 60."))
        }

        assertNoDynamicCodeAttempt(manifest.id, "id").onFailure { return Result.failure(it) }
        assertNoDynamicCodeAttempt(manifest.name, "name").onFailure { return Result.failure(it) }
        assertNoDynamicCodeAttempt(manifest.description, "description").onFailure { return Result.failure(it) }
        assertNoDynamicCodeAttempt(manifest.sha256Signature, "sha256Signature").onFailure { return Result.failure(it) }

        manifest.slashCommands.forEach { cmd ->
            if (!COMMAND_TRIGGER_REGEX.matches(cmd.trigger.lowercase())) {
                return Result.failure(IllegalArgumentException("Commande slash invalide '${cmd.trigger}' (ex: /focus)."))
            }
            assertNoDynamicCodeAttempt(cmd.description, "slashCommand").onFailure { return Result.failure(it) }
        }

        manifest.configSchema.forEach { field ->
            if (field.key.isBlank() || field.key.length > 48) {
                return Result.failure(IllegalArgumentException("Clé de configuration invalide '${field.key}'."))
            }
            assertNoDynamicCodeAttempt(field.defaultValue, "configDefault:${field.key}").onFailure { return Result.failure(it) }
        }

        return Result.success(Unit)
    }

    fun buildDefaultConfig(schema: List<PluginConfigField>): Map<String, String> =
        schema.associate { it.key to it.defaultValue }

    fun validateAndNormalizeConfig(
        schema: List<PluginConfigField>,
        proposed: Map<String, String>
    ): Result<Map<String, String>> {
        val allowedKeys = schema.associateBy { it.key }
        val unknown = proposed.keys.firstOrNull { it !in allowedKeys }
        if (unknown != null) {
            return Result.failure(IllegalArgumentException("Clé de configuration inconnue '$unknown'."))
        }

        val normalized = linkedMapOf<String, String>()
        for (field in schema) {
            val rawVal = (proposed[field.key] ?: field.defaultValue).trim()
            assertNoDynamicCodeAttempt(rawVal, "config:${field.key}").onFailure { return Result.failure(it) }

            when (field.type) {
                PluginConfigFieldType.BOOLEAN -> {
                    val parsed = when (rawVal.lowercase()) {
                        "true", "1", "oui" -> "true"
                        "false", "0", "non" -> "false"
                        else -> return Result.failure(
                            IllegalArgumentException("Valeur booléenne invalide pour '${field.label}'.")
                        )
                    }
                    normalized[field.key] = parsed
                }
                PluginConfigFieldType.INT -> {
                    val num = rawVal.toIntOrNull()
                        ?: return Result.failure(IllegalArgumentException("Nombre entier requis pour '${field.label}'."))
                    if (num !in field.minInt..field.maxInt) {
                        return Result.failure(
                            IllegalArgumentException("'${field.label}' doit être compris entre ${field.minInt} et ${field.maxInt}.")
                        )
                    }
                    normalized[field.key] = num.toString()
                }
                PluginConfigFieldType.STRING -> {
                    if (rawVal.length > field.maxLength) {
                        return Result.failure(
                            IllegalArgumentException("'${field.label}' dépasse ${field.maxLength} caractères.")
                        )
                    }
                    normalized[field.key] = rawVal
                }
                PluginConfigFieldType.SELECT -> {
                    if (field.options.isNotEmpty() && rawVal !in field.options) {
                        return Result.failure(
                            IllegalArgumentException("Option '$rawVal' non autorisée pour '${field.label}'.")
                        )
                    }
                    normalized[field.key] = rawVal
                }
            }
        }
        return Result.success(normalized)
    }

    fun sanitizeText(raw: String, maxLen: Int = 360): String {
        var result = raw.trim()
        SECRET_REDACTION_PATTERNS.forEach { regex ->
            result = result.replace(regex, "[CONFIDENTIEL]")
        }
        if (result.length > maxLen) {
            result = result.take(maxLen - 1) + "…"
        }
        return result
    }
}

/**
 * Enforces least-privilege permission boundaries between a plugin's requested permissions
 * and the permissions explicitly consented to by the user.
 */
object PluginPermissionMatrix {

    fun effectiveGranted(manifest: PluginManifest, state: UserPluginState): Set<PluginPermission> =
        manifest.requestedPermissions.intersect(state.grantedPermissions)

    fun pendingPermissions(manifest: PluginManifest, state: UserPluginState): Set<PluginPermission> =
        manifest.requestedPermissions - state.grantedPermissions - state.deniedPermissions

    fun grantOnlyDeclared(
        manifest: PluginManifest,
        requestedGrant: Set<PluginPermission>
    ): Set<PluginPermission> = manifest.requestedPermissions.intersect(requestedGrant)
}
