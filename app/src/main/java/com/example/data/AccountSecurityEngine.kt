package com.example.data

import android.content.Context
import android.os.Build
import com.example.model.*
import com.example.util.PlatformEnvironmentDetector
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic helpers for Account Security:
 * - Never reads sensitive hardware identifiers (IMEI/MAC/SERIAL).
 * - Never persists plaintext PINs, tokens, or 2FA recovery codes.
 */
object AccountSecurityCrypto {
    private val secureRandom = SecureRandom()
    private val hexChars = "0123456789abcdef".toCharArray()
    private const val PBKDF2_ITERATIONS = 10_000
    private const val PBKDF2_KEY_BITS = 256

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    fun generateSaltHex(byteCount: Int = 16): String {
        val bytes = ByteArray(byteCount.coerceIn(8, 32))
        secureRandom.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    fun hashPinPbkdf2(pin: String, saltHex: String): String {
        val cleanPin = pin.trim()
        require(cleanPin.length in 4..12) { "Le code de sécurité doit contenir entre 4 et 12 caractères." }
        val spec = PBEKeySpec(
            cleanPin.toCharArray(),
            saltHex.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_BITS
        )
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            bytesToHex(factory.generateSecret(spec).encoded)
        } catch (_: Exception) {
            // Portable fallback on restricted JCA environments (salted multi-round SHA-256)
            var current = "$saltHex:$cleanPin"
            repeat(64) {
                current = sha256Hex(current)
            }
            current
        } finally {
            spec.clearPassword()
        }
    }

    fun verifyPinPbkdf2(candidatePin: String, expectedHash: String?, saltHex: String?): Boolean {
        if (candidatePin.isBlank() || expectedHash.isNullOrBlank() || saltHex.isNullOrBlank()) return false
        val actual = runCatching { hashPinPbkdf2(candidatePin, saltHex) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    fun generateOneTimeBackupCodes(salt: String, count: Int = 6): List<Pair<String, String>> {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..count.coerceIn(1, 10)).map {
            val part1 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
            val part2 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
            val displayCode = "RVN-$part1-$part2"
            val hashed = sha256Hex("$salt:$displayCode")
            displayCode to hashed
        }
    }

    fun getOrCreateInstallDeviceHash(context: Context?): String {
        val rawInstallId = if (context != null) {
            val prefs = context.getSharedPreferences("raven_sec_install", Context.MODE_PRIVATE)
            prefs.getString("install_uuid", null) ?: UUID.randomUUID().toString().also { generated ->
                prefs.edit().putString("install_uuid", generated).apply()
            }
        } else {
            "jvm-test-device-anchor"
        }
        return sha256Hex("raven_dev_v1:$rawInstallId")
    }

    fun currentDeviceModelName(): String {
        val model = (Build.MODEL ?: "Android Device").trim()
        val manufacturer = (Build.MANUFACTURER ?: "").trim()
        return when {
            model.isBlank() -> "Appareil Android"
            manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true) ->
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model".take(48)
            else -> model.take(48)
        }
    }

    fun currentPlatformLabel(context: Context? = null): String {
        return PlatformEnvironmentDetector.detectSessionPlatformLabel(context)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexChars[v ushr 4]
            out[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(out)
    }
}

data class AnomalyAssessment(
    val isSuspicious: Boolean,
    val isBlocked: Boolean,
    val severity: SecuritySeverity,
    val reasons: List<String>,
    val recommendedAction: String
)

/**
 * Pure anomaly detection and suspicious-change protection engine.
 */
object AccountSecurityEvaluator {
    const val SENSITIVE_VERIFICATION_TTL_MS = 5L * 60_000L
    const val MAX_FAILED_CHALLENGES_BEFORE_FREEZE = 3
    const val FREEZE_DURATION_MS = 15L * 60_000L
    const val CONCURRENT_REGION_SHIFT_WINDOW_MS = 30L * 60_000L

    fun evaluateLoginSignals(
        deviceIdHash: String,
        regionLabel: String,
        knownDevices: List<ConnectedDevice>,
        recentSessions: List<AccountSession>,
        nowMs: Long
    ): AnomalyAssessment {
        val known = knownDevices.find { it.deviceIdHash == deviceIdHash }
        if (known?.trustState == DeviceTrustState.BLOCKED) {
            return AnomalyAssessment(
                isSuspicious = true,
                isBlocked = true,
                severity = SecuritySeverity.CRITICAL,
                reasons = listOf("Tentative d'accès depuis un appareil bloqué (${known.displayName})."),
                recommendedAction = "Effectuer une déconnexion globale et vérifier vos alertes."
            )
        }

        val reasons = mutableListOf<String>()
        var severity = SecuritySeverity.INFO

        if (known == null) {
            reasons += "Nouvel appareil détecté pour la première fois."
            severity = SecuritySeverity.MEDIUM
        } else if (known.trustState == DeviceTrustState.UNTRUSTED) {
            reasons += "Connexion depuis un appareil non encore marqué comme fiable."
            severity = SecuritySeverity.LOW
        }

        val recentForeignSession = recentSessions.firstOrNull { s ->
            s.status == SessionStatus.ACTIVE &&
                s.deviceIdHash != deviceIdHash &&
                (nowMs - s.lastActivityMs) in 0L..CONCURRENT_REGION_SHIFT_WINDOW_MS &&
                s.networkRegionLabel.isNotBlank() &&
                !s.networkRegionLabel.equals(regionLabel, ignoreCase = true)
        }
        if (recentForeignSession != null) {
            reasons += "Changement rapide de réseau/région ($regionLabel vs ${recentForeignSession.networkRegionLabel}) en moins de 30 min."
            severity = SecuritySeverity.HIGH
        }

        val activeDeviceCount = recentSessions
            .filter { it.status == SessionStatus.ACTIVE && it.expiresAtMs > nowMs }
            .map { it.deviceIdHash }
            .distinct()
            .size
        if (activeDeviceCount >= 5 && known == null) {
            reasons += "Nombre élevé d'appareils actifs simultanés ($activeDeviceCount)."
            severity = SecuritySeverity.HIGH
        }

        return AnomalyAssessment(
            isSuspicious = reasons.isNotEmpty() && severity != SecuritySeverity.INFO && severity != SecuritySeverity.LOW,
            isBlocked = false,
            severity = severity,
            reasons = reasons,
            recommendedAction = if (severity >= SecuritySeverity.HIGH) {
                "Révoquer la session suspecte ou lancer une déconnexion globale."
            } else {
                "Confirmez s'il s'agit bien de votre appareil."
            }
        )
    }

    fun isSessionValid(
        session: AccountSession,
        profile: AccountSecurityProfile,
        nowMs: Long
    ): Boolean {
        if (session.status != SessionStatus.ACTIVE) return false
        if (session.expiresAtMs in 1L..nowMs) return false
        if (session.sessionEpoch < profile.sessionEpoch) return false
        if (profile.globalSignOutAfterMs > 0L && session.createdAtMs in 1L until profile.globalSignOutAfterMs) return false
        return true
    }

    fun isVerificationFresh(profile: AccountSecurityProfile, nowMs: Long): Boolean {
        if (profile.lastSensitiveVerifiedAtMs <= 0L) return false
        return (nowMs - profile.lastSensitiveVerifiedAtMs) in 0L..SENSITIVE_VERIFICATION_TTL_MS
    }

    fun isFrozen(profile: AccountSecurityProfile, nowMs: Long): Boolean {
        return profile.changeFreezeUntilMs > nowMs || profile.cooldownUntilMs > nowMs
    }
}

/**
 * Extensible SPI for future 2FA providers (TOTP Authenticator, WebAuthn/Passkeys, Hardware Keys).
 * Stores ONLY public factor metadata; secret material never resides in shared Firestore documents.
 */
interface TwoFactorAuthDriver {
    val method: TwoFactorMethod
    fun isSupported(): Boolean
    fun buildPendingFactorMeta(label: String, nowMs: Long): TwoFactorFactorMeta
}

object TwoFactorDriverRegistry {
    private val drivers: Map<TwoFactorMethod, TwoFactorAuthDriver> = mapOf(
        TwoFactorMethod.AUTHENTICATOR_TOTP to object : TwoFactorAuthDriver {
            override val method = TwoFactorMethod.AUTHENTICATOR_TOTP
            override fun isSupported(): Boolean = true
            override fun buildPendingFactorMeta(label: String, nowMs: Long) = TwoFactorFactorMeta(
                factorId = "2fa_totp_${nowMs.toString().takeLast(6)}",
                method = method,
                label = label.ifBlank { "Application TOTP RAVEN" }.take(48),
                isEnabled = true,
                enrolledAtMs = nowMs,
                lastUsedAtMs = nowMs
            )
        },
        TwoFactorMethod.PASSKEY_WEBAUTHN to object : TwoFactorAuthDriver {
            override val method = TwoFactorMethod.PASSKEY_WEBAUTHN
            override fun isSupported(): Boolean = true
            override fun buildPendingFactorMeta(label: String, nowMs: Long) = TwoFactorFactorMeta(
                factorId = "2fa_pk_${nowMs.toString().takeLast(6)}",
                method = method,
                label = label.ifBlank { "Passkey Android" }.take(48),
                isEnabled = true,
                enrolledAtMs = nowMs,
                lastUsedAtMs = nowMs
            )
        },
        TwoFactorMethod.HARDWARE_KEY to object : TwoFactorAuthDriver {
            override val method = TwoFactorMethod.HARDWARE_KEY
            override fun isSupported(): Boolean = true
            override fun buildPendingFactorMeta(label: String, nowMs: Long) = TwoFactorFactorMeta(
                factorId = "2fa_hw_${nowMs.toString().takeLast(6)}",
                method = method,
                label = label.ifBlank { "Clé FIDO2" }.take(48),
                isEnabled = true,
                enrolledAtMs = nowMs,
                lastUsedAtMs = nowMs
            )
        }
    )

    fun driverFor(method: TwoFactorMethod): TwoFactorAuthDriver? = drivers[method]
}
