package com.example.data

import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot

object AccountSecurityFirestoreSync {

    fun sessionToMap(s: AccountSession): Map<String, Any?> = mapOf(
        "sessionId" to s.sessionId,
        "userId" to s.userId,
        "deviceIdHash" to s.deviceIdHash,
        "deviceName" to s.deviceName.take(64),
        "platformLabel" to s.platformLabel.take(32),
        "appVersion" to s.appVersion.take(24),
        "networkRegionLabel" to s.networkRegionLabel.take(64),
        "status" to s.status.name,
        "sessionEpoch" to s.sessionEpoch,
        "createdAtMs" to s.createdAtMs,
        "lastActivityMs" to s.lastActivityMs,
        "expiresAtMs" to s.expiresAtMs,
        "revokedAtMs" to s.revokedAtMs,
        "revokedReason" to s.revokedReason?.take(160)
    )

    fun sessionFromDoc(doc: DocumentSnapshot, currentSessionId: String): AccountSession? {
        val sid = doc.getString("sessionId")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        return AccountSession(
            sessionId = sid,
            userId = uid,
            deviceIdHash = doc.getString("deviceIdHash") ?: "",
            deviceName = doc.getString("deviceName") ?: "Appareil Android",
            platformLabel = doc.getString("platformLabel") ?: "Android",
            appVersion = doc.getString("appVersion") ?: "1.0",
            networkRegionLabel = doc.getString("networkRegionLabel") ?: "Réseau standard",
            status = SessionStatus.fromRaw(doc.getString("status")),
            sessionEpoch = doc.getLong("sessionEpoch") ?: 1L,
            createdAtMs = doc.getLong("createdAtMs") ?: 0L,
            lastActivityMs = doc.getLong("lastActivityMs") ?: 0L,
            expiresAtMs = doc.getLong("expiresAtMs") ?: 0L,
            revokedAtMs = doc.getLong("revokedAtMs"),
            revokedReason = doc.getString("revokedReason"),
            isCurrentDevice = sid == currentSessionId
        )
    }

    fun deviceToMap(d: ConnectedDevice): Map<String, Any?> = mapOf(
        "deviceIdHash" to d.deviceIdHash,
        "userId" to d.userId,
        "displayName" to d.displayName.take(64),
        "modelName" to d.modelName.take(64),
        "osVersion" to d.osVersion.take(32),
        "trustState" to d.trustState.name,
        "firstSeenAtMs" to d.firstSeenAtMs,
        "lastSeenAtMs" to d.lastSeenAtMs,
        "lastRegionLabel" to d.lastRegionLabel.take(64),
        "lastSessionId" to d.lastSessionId
    )

    fun deviceFromDoc(doc: DocumentSnapshot): ConnectedDevice? {
        val hash = doc.getString("deviceIdHash")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        return ConnectedDevice(
            deviceIdHash = hash,
            userId = uid,
            displayName = doc.getString("displayName") ?: "Appareil",
            modelName = doc.getString("modelName") ?: "Android",
            osVersion = doc.getString("osVersion") ?: "Android",
            trustState = DeviceTrustState.fromRaw(doc.getString("trustState")),
            firstSeenAtMs = doc.getLong("firstSeenAtMs") ?: 0L,
            lastSeenAtMs = doc.getLong("lastSeenAtMs") ?: 0L,
            lastRegionLabel = doc.getString("lastRegionLabel") ?: "Réseau standard",
            lastSessionId = doc.getString("lastSessionId") ?: ""
        )
    }

    fun loginHistoryToMap(entry: LoginHistoryEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "userId" to entry.userId,
        "sessionId" to entry.sessionId,
        "deviceIdHash" to entry.deviceIdHash,
        "deviceName" to entry.deviceName.take(64),
        "authMethod" to entry.authMethod.take(32),
        "outcome" to entry.outcome.name,
        "regionLabel" to entry.regionLabel.take(64),
        "timestampMs" to entry.timestampMs,
        "anomalyReason" to entry.anomalyReason?.take(300)
    )

    fun loginHistoryFromDoc(doc: DocumentSnapshot): LoginHistoryEntry? {
        val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        return LoginHistoryEntry(
            id = id,
            userId = uid,
            sessionId = doc.getString("sessionId") ?: "",
            deviceIdHash = doc.getString("deviceIdHash") ?: "",
            deviceName = doc.getString("deviceName") ?: "Appareil",
            authMethod = doc.getString("authMethod") ?: "GOOGLE_OIDC",
            outcome = LoginOutcome.fromRaw(doc.getString("outcome")),
            regionLabel = doc.getString("regionLabel") ?: "Réseau standard",
            timestampMs = doc.getLong("timestampMs") ?: 0L,
            anomalyReason = doc.getString("anomalyReason")
        )
    }

    fun alertToMap(a: SecurityAlert): Map<String, Any?> = mapOf(
        "id" to a.id,
        "userId" to a.userId,
        "title" to a.title.take(120),
        "description" to a.description.take(500),
        "severity" to a.severity.name,
        "status" to a.status.name,
        "relatedSessionId" to a.relatedSessionId,
        "relatedDeviceIdHash" to a.relatedDeviceIdHash,
        "recommendedAction" to a.recommendedAction.take(240),
        "createdAtMs" to a.createdAtMs,
        "resolvedAtMs" to a.resolvedAtMs
    )

    fun alertFromDoc(doc: DocumentSnapshot): SecurityAlert? {
        val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        return SecurityAlert(
            id = id,
            userId = uid,
            title = doc.getString("title") ?: "Alerte de sécurité",
            description = doc.getString("description") ?: "",
            severity = SecuritySeverity.fromRaw(doc.getString("severity")),
            status = SecurityAlertStatus.fromRaw(doc.getString("status")),
            relatedSessionId = doc.getString("relatedSessionId"),
            relatedDeviceIdHash = doc.getString("relatedDeviceIdHash"),
            recommendedAction = doc.getString("recommendedAction") ?: "",
            createdAtMs = doc.getLong("createdAtMs") ?: 0L,
            resolvedAtMs = doc.getLong("resolvedAtMs")
        )
    }

    fun securityLogToMap(log: SecurityLogEntry): Map<String, Any?> = mapOf(
        "id" to log.id,
        "userId" to log.userId,
        "category" to log.category.name,
        "severity" to log.severity.name,
        "action" to log.action.take(64),
        "summary" to log.summary.take(500),
        "actorDeviceIdHash" to log.actorDeviceIdHash,
        "timestampMs" to log.timestampMs,
        "timestampLabel" to log.timestampLabel.take(32)
    )

    fun securityLogFromDoc(doc: DocumentSnapshot): SecurityLogEntry? {
        val id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        return SecurityLogEntry(
            id = id,
            userId = uid,
            category = SecurityLogCategory.fromRaw(doc.getString("category")),
            severity = SecuritySeverity.fromRaw(doc.getString("severity")),
            action = doc.getString("action") ?: "SECURITY_EVENT",
            summary = doc.getString("summary") ?: "",
            actorDeviceIdHash = doc.getString("actorDeviceIdHash") ?: "",
            timestampMs = doc.getLong("timestampMs") ?: 0L,
            timestampLabel = doc.getString("timestampLabel") ?: ""
        )
    }

    fun securityProfileToMap(p: AccountSecurityProfile): Map<String, Any?> = mapOf(
        "userId" to p.userId,
        "sessionEpoch" to p.sessionEpoch,
        "globalSignOutAfterMs" to p.globalSignOutAfterMs,
        "lastSensitiveVerifiedAtMs" to p.lastSensitiveVerifiedAtMs,
        "protectSuspiciousChanges" to p.protectSuspiciousChanges,
        "alertOnNewDevice" to p.alertOnNewDevice,
        "alertOnUnusualActivity" to p.alertOnUnusualActivity,
        "requireReauthForSensitiveActions" to p.requireReauthForSensitiveActions,
        "changeFreezeUntilMs" to p.changeFreezeUntilMs,
        "cooldownUntilMs" to p.cooldownUntilMs,
        "failedChallengeCount" to p.failedChallengeCount,
        "suspiciousAttemptCount" to p.suspiciousAttemptCount,
        "pinHash" to p.pinHash,
        "pinSalt" to p.pinSalt,
        "twoFactorReady" to p.twoFactorReady,
        "preferredTwoFactorMethod" to p.preferredTwoFactorMethod.name,
        "enrolledFactorsMeta" to p.enrolledFactorsMeta.map { f ->
            mapOf(
                "factorId" to f.factorId,
                "method" to f.method.name,
                "label" to f.label.take(48),
                "isEnabled" to f.isEnabled,
                "enrolledAtMs" to f.enrolledAtMs,
                "lastUsedAtMs" to f.lastUsedAtMs
            )
        },
        "backupCodesRemainingCount" to p.backupCodesRemainingCount,
        "backupCodeHashes" to p.backupCodeHashes,
        "updatedAtMs" to p.updatedAtMs
    )

    @Suppress("UNCHECKED_CAST")
    fun securityProfileFromDoc(doc: DocumentSnapshot): AccountSecurityProfile? {
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: return null
        val rawFactors = (doc.get("enrolledFactorsMeta") as? List<Map<String, Any?>>).orEmpty()
        val factors = rawFactors.mapNotNull { m ->
            val fid = m["factorId"] as? String ?: return@mapNotNull null
            TwoFactorFactorMeta(
                factorId = fid,
                method = TwoFactorMethod.fromRaw(m["method"] as? String),
                label = (m["label"] as? String) ?: "Facteur 2FA",
                isEnabled = (m["isEnabled"] as? Boolean) ?: true,
                enrolledAtMs = (m["enrolledAtMs"] as? Number)?.toLong() ?: 0L,
                lastUsedAtMs = (m["lastUsedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
        val hashes = (doc.get("backupCodeHashes") as? List<String>).orEmpty()
        return AccountSecurityProfile(
            userId = uid,
            sessionEpoch = doc.getLong("sessionEpoch") ?: 1L,
            globalSignOutAfterMs = doc.getLong("globalSignOutAfterMs") ?: 0L,
            lastSensitiveVerifiedAtMs = doc.getLong("lastSensitiveVerifiedAtMs") ?: 0L,
            protectSuspiciousChanges = doc.getBoolean("protectSuspiciousChanges") ?: true,
            alertOnNewDevice = doc.getBoolean("alertOnNewDevice") ?: true,
            alertOnUnusualActivity = doc.getBoolean("alertOnUnusualActivity") ?: true,
            requireReauthForSensitiveActions = doc.getBoolean("requireReauthForSensitiveActions") ?: true,
            changeFreezeUntilMs = doc.getLong("changeFreezeUntilMs") ?: 0L,
            cooldownUntilMs = doc.getLong("cooldownUntilMs") ?: 0L,
            failedChallengeCount = (doc.getLong("failedChallengeCount") ?: 0L).toInt(),
            suspiciousAttemptCount = (doc.getLong("suspiciousAttemptCount") ?: 0L).toInt(),
            pinHash = doc.getString("pinHash"),
            pinSalt = doc.getString("pinSalt"),
            twoFactorReady = doc.getBoolean("twoFactorReady") ?: true,
            preferredTwoFactorMethod = TwoFactorMethod.fromRaw(doc.getString("preferredTwoFactorMethod")),
            enrolledFactorsMeta = factors,
            backupCodesRemainingCount = (doc.getLong("backupCodesRemainingCount") ?: hashes.size.toLong()).toInt(),
            backupCodeHashes = hashes,
            updatedAtMs = doc.getLong("updatedAtMs") ?: 0L
        )
    }
}
