package com.example.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

enum class SessionStatus(val label: String) {
    ACTIVE("Active"),
    REVOKED("Révoquée"),
    EXPIRED("Expirée");

    companion object {
        fun fromRaw(raw: String?): SessionStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class DeviceTrustState(val label: String) {
    TRUSTED("Appareil de confiance"),
    UNTRUSTED("Non vérifié"),
    BLOCKED("Bloqué");

    companion object {
        fun fromRaw(raw: String?): DeviceTrustState =
            entries.firstOrNull { it.name == raw } ?: UNTRUSTED
    }
}

enum class SecuritySeverity(val label: String) {
    INFO("Info"),
    LOW("Faible"),
    MEDIUM("Moyen"),
    HIGH("Élevé"),
    CRITICAL("Critique");

    companion object {
        fun fromRaw(raw: String?): SecuritySeverity =
            entries.firstOrNull { it.name == raw } ?: INFO
    }
}

enum class LoginOutcome(val label: String) {
    SUCCESS("Connexion réussie"),
    SUSPICIOUS_FLAGGED("Activité inhabituelle"),
    CHALLENGE_REQUIRED("Confirmation requise"),
    BLOCKED("Bloquée");

    companion object {
        fun fromRaw(raw: String?): LoginOutcome =
            entries.firstOrNull { it.name == raw } ?: SUCCESS
    }
}

enum class SensitiveActionType(val label: String) {
    REVOKE_SESSION("Révoquer une session"),
    GLOBAL_SIGN_OUT("Déconnexion globale"),
    BLOCK_DEVICE("Bloquer un appareil"),
    REMOVE_DEVICE("Supprimer un appareil"),
    CHANGE_PROTECTION_POLICY("Modifier la protection du compte"),
    SET_SECURITY_PIN("Configurer le code de sécurité"),
    MANAGE_TWO_FACTOR("Configurer la double authentification (2FA)"),
    LIFT_FREEZE("Lever le verrouillage de sécurité"),
    CRITICAL_PROFILE_CHANGE("Modification sensible du profil")
}

enum class SecurityLogCategory(val label: String) {
    ALL("Tout"),
    SESSION("Sessions"),
    DEVICE("Appareils"),
    ANOMALY("Anomalies"),
    PROTECTION("Protection"),
    TWO_FACTOR("2FA");

    companion object {
        fun fromRaw(raw: String?): SecurityLogCategory =
            entries.firstOrNull { it.name == raw } ?: SESSION
    }
}

enum class SecurityAlertStatus(val label: String) {
    OPEN("À vérifier"),
    CONFIRMED_SAFE("Confirmée légitime"),
    SECURED("Sécurisée"),
    DISMISSED("Ignorée");

    companion object {
        fun fromRaw(raw: String?): SecurityAlertStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class TwoFactorMethod(val label: String, val subtitle: String) {
    NONE("Désactivée (Prêt)", "Architecture compatible prête pour activation"),
    AUTHENTICATOR_TOTP("Application TOTP", "Code temporaire 6 chiffres (RFC 6238)"),
    PASSKEY_WEBAUTHN("Clé d'accès / Passkey", "Biométrie ou clé matérielle WebAuthn"),
    HARDWARE_KEY("Clé de sécurité FIDO2", "Clé USB / NFC dédiée")
    ;

    companion object {
        fun fromRaw(raw: String?): TwoFactorMethod =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

@Keep
@IgnoreExtraProperties
data class AccountSession(
    val sessionId: String = "",
    val userId: String = "",
    val deviceIdHash: String = "",
    val deviceName: String = "",
    val platformLabel: String = "Android",
    val appVersion: String = "1.0",
    val networkRegionLabel: String = "Réseau standard",
    val status: SessionStatus = SessionStatus.ACTIVE,
    val sessionEpoch: Long = 1L,
    val createdAtMs: Long = 0L,
    val lastActivityMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val revokedAtMs: Long? = null,
    val revokedReason: String? = null,
    val isCurrentDevice: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class ConnectedDevice(
    val deviceIdHash: String = "",
    val userId: String = "",
    val displayName: String = "",
    val modelName: String = "",
    val osVersion: String = "",
    val trustState: DeviceTrustState = DeviceTrustState.UNTRUSTED,
    val firstSeenAtMs: Long = 0L,
    val lastSeenAtMs: Long = 0L,
    val lastRegionLabel: String = "Réseau standard",
    val lastSessionId: String = ""
)

@Keep
@IgnoreExtraProperties
data class LoginHistoryEntry(
    val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val deviceIdHash: String = "",
    val deviceName: String = "",
    val authMethod: String = "GOOGLE_OIDC",
    val outcome: LoginOutcome = LoginOutcome.SUCCESS,
    val regionLabel: String = "Réseau standard",
    val timestampMs: Long = 0L,
    val anomalyReason: String? = null
)

@Keep
@IgnoreExtraProperties
data class SecurityAlert(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val severity: SecuritySeverity = SecuritySeverity.MEDIUM,
    val status: SecurityAlertStatus = SecurityAlertStatus.OPEN,
    val relatedSessionId: String? = null,
    val relatedDeviceIdHash: String? = null,
    val recommendedAction: String = "",
    val createdAtMs: Long = 0L,
    val resolvedAtMs: Long? = null
)

@Keep
@IgnoreExtraProperties
data class SecurityLogEntry(
    val id: String = "",
    val userId: String = "",
    val category: SecurityLogCategory = SecurityLogCategory.SESSION,
    val severity: SecuritySeverity = SecuritySeverity.INFO,
    val action: String = "",
    val summary: String = "",
    val actorDeviceIdHash: String = "",
    val timestampMs: Long = 0L,
    val timestampLabel: String = ""
)

@Keep
@IgnoreExtraProperties
data class TwoFactorFactorMeta(
    val factorId: String = "",
    val method: TwoFactorMethod = TwoFactorMethod.AUTHENTICATOR_TOTP,
    val label: String = "",
    val isEnabled: Boolean = true,
    val enrolledAtMs: Long = 0L,
    val lastUsedAtMs: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class AccountSecurityProfile(
    val userId: String = "",
    val sessionEpoch: Long = 1L,
    val globalSignOutAfterMs: Long = 0L,
    val lastSensitiveVerifiedAtMs: Long = 0L,
    val protectSuspiciousChanges: Boolean = true,
    val alertOnNewDevice: Boolean = true,
    val alertOnUnusualActivity: Boolean = true,
    val requireReauthForSensitiveActions: Boolean = true,
    val changeFreezeUntilMs: Long = 0L,
    val cooldownUntilMs: Long = 0L,
    val failedChallengeCount: Int = 0,
    val suspiciousAttemptCount: Int = 0,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val twoFactorReady: Boolean = true,
    val preferredTwoFactorMethod: TwoFactorMethod = TwoFactorMethod.NONE,
    val enrolledFactorsMeta: List<TwoFactorFactorMeta> = emptyList(),
    val backupCodesRemainingCount: Int = 0,
    val backupCodeHashes: List<String> = emptyList(),
    val updatedAtMs: Long = 0L
)

data class PendingSensitiveConfirmation(
    val actionType: SensitiveActionType,
    val targetId: String? = null,
    val title: String,
    val description: String,
    val boolArg: Boolean = false,
    val stringArg: String? = null,
    val methodArg: TwoFactorMethod? = null,
    val policySnapshot: AccountSecurityProfile? = null
)

data class AccountSecurityUiState(
    val securityProfile: AccountSecurityProfile = AccountSecurityProfile(),
    val sessions: List<AccountSession> = emptyList(),
    val devices: List<ConnectedDevice> = emptyList(),
    val loginHistory: List<LoginHistoryEntry> = emptyList(),
    val securityAlerts: List<SecurityAlert> = emptyList(),
    val securityLogs: List<SecurityLogEntry> = emptyList(),
    val selectedLogCategory: SecurityLogCategory = SecurityLogCategory.ALL,
    val currentSessionId: String = "",
    val currentDeviceIdHash: String = "",
    val pendingConfirmation: PendingSensitiveConfirmation? = null,
    val oneTimeBackupCodes: List<String> = emptyList(),
    val isSensitiveVerificationFresh: Boolean = false,
    val isSecurityFrozen: Boolean = false,
    val openAlertsCount: Int = 0,
    val activeSessionsCount: Int = 0,
    val remoteRevocationMessage: String? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)
