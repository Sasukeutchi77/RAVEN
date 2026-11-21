package com.example.data

import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class AccountSecurityRepository(
    private val chatRepo: ChatRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val deviceHashOverride: String? = null
) {
    val currentDeviceIdHash: String =
        deviceHashOverride ?: AccountSecurityCrypto.getOrCreateInstallDeviceHash(chatRepo.appContextOrNull())

    private val _currentSessionId = MutableStateFlow("sess_${UUID.randomUUID().toString().replace("-", "").take(14)}")
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _securityProfile = MutableStateFlow(AccountSecurityProfile(userId = chatRepo.currentUser.value.id))
    val securityProfile: StateFlow<AccountSecurityProfile> = _securityProfile.asStateFlow()

    private val _sessions = MutableStateFlow<List<AccountSession>>(emptyList())
    val sessions: StateFlow<List<AccountSession>> = _sessions.asStateFlow()

    private val _devices = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devices: StateFlow<List<ConnectedDevice>> = _devices.asStateFlow()

    private val _loginHistory = MutableStateFlow<List<LoginHistoryEntry>>(emptyList())
    val loginHistory: StateFlow<List<LoginHistoryEntry>> = _loginHistory.asStateFlow()

    private val _securityAlerts = MutableStateFlow<List<SecurityAlert>>(emptyList())
    val securityAlerts: StateFlow<List<SecurityAlert>> = _securityAlerts.asStateFlow()

    private val _securityLogs = MutableStateFlow<List<SecurityLogEntry>>(emptyList())
    val securityLogs: StateFlow<List<SecurityLogEntry>> = _securityLogs.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingSensitiveConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingSensitiveConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _oneTimeBackupCodes = MutableStateFlow<List<String>>(emptyList())
    val oneTimeBackupCodes: StateFlow<List<String>> = _oneTimeBackupCodes.asStateFlow()

    private val _remoteRevocationMessage = MutableStateFlow<String?>(null)
    val remoteRevocationMessage: StateFlow<String?> = _remoteRevocationMessage.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var sessionsListener: ListenerRegistration? = null
    private var devicesListener: ListenerRegistration? = null
    private var loginHistoryListener: ListenerRegistration? = null
    private var alertsListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null
    private var syncedUid: String? = null
    private var initialCloudSignInAtMs: Long = 0L
    private var hasSyncedInitialProfile: Boolean = false
    private val recentMutationTimestamps = mutableListOf<Long>()

    init {
        ensureLocalBootstrapSession()
    }

    private fun activeUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    private fun timeLabel(ms: Long = nowProvider()): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    private fun detectRegionLabel(): String =
        runCatching { TimeZone.getDefault().id }.getOrDefault("Europe/Paris")

    private fun ensureLocalBootstrapSession() {
        val uid = activeUid()
        val now = nowProvider()
        val model = AccountSecurityCrypto.currentDeviceModelName()
        val platform = AccountSecurityCrypto.currentPlatformLabel(chatRepo.appContextOrNull())
        val region = detectRegionLabel()

        val initialDevice = ConnectedDevice(
            deviceIdHash = currentDeviceIdHash,
            userId = uid,
            displayName = model,
            modelName = model,
            osVersion = platform,
            trustState = DeviceTrustState.TRUSTED,
            firstSeenAtMs = now,
            lastSeenAtMs = now,
            lastRegionLabel = region,
            lastSessionId = _currentSessionId.value
        )
        val initialSession = AccountSession(
            sessionId = _currentSessionId.value,
            userId = uid,
            deviceIdHash = currentDeviceIdHash,
            deviceName = model,
            platformLabel = platform,
            appVersion = "1.0",
            networkRegionLabel = region,
            status = SessionStatus.ACTIVE,
            sessionEpoch = 1L,
            createdAtMs = now,
            lastActivityMs = now,
            expiresAtMs = now + 30L * 86_400_000L,
            isCurrentDevice = true
        )
        _securityProfile.value = _securityProfile.value.copy(userId = uid, updatedAtMs = now)
        _devices.value = listOf(initialDevice)
        _sessions.value = listOf(initialSession)
        _loginHistory.value = listOf(
            LoginHistoryEntry(
                id = "lh_init_${now}",
                userId = uid,
                sessionId = initialSession.sessionId,
                deviceIdHash = currentDeviceIdHash,
                deviceName = model,
                authMethod = if (chatRepo.authInstance()?.currentUser != null) "GOOGLE_OIDC" else "SESSION_LOCAL",
                outcome = LoginOutcome.SUCCESS,
                regionLabel = region,
                timestampMs = now
            )
        )
        appendLogInternal(
            uid = uid,
            category = SecurityLogCategory.SESSION,
            severity = SecuritySeverity.INFO,
            action = "SESSION_INITIALIZED",
            summary = "Session active enregistrée sur $model ($region)."
        )
    }

    fun syncWithAuthUid(uid: String?) {
        val db = chatRepo.firestoreDb()
        if (uid.isNullOrBlank() || db == null) {
            detachListeners()
            syncedUid = null
            return
        }
        if (syncedUid == uid) return
        syncedUid = uid
        initialCloudSignInAtMs = nowProvider()
        hasSyncedInitialProfile = false
        detachListeners()

        val userDoc = db.collection("users").document(uid)
        profileListener = userDoc.collection("security_profile").document("account")
            .addSnapshotListener { snap, _ ->
                val remote = snap?.takeIf { it.exists() }?.let { AccountSecurityFirestoreSync.securityProfileFromDoc(it) }
                if (remote != null) {
                    val isInitialAdopt = !hasSyncedInitialProfile
                    hasSyncedInitialProfile = true
                    _securityProfile.value = remote
                    if (isInitialAdopt) {
                        adoptOrRevokeOnInitialSync(uid, remote)
                    } else {
                        checkCurrentSessionValidity(remote)
                    }
                } else {
                    hasSyncedInitialProfile = true
                }
            }

        sessionsListener = userDoc.collection("sessions").limit(30)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val mapped = snap.documents
                    .mapNotNull { AccountSecurityFirestoreSync.sessionFromDoc(it, _currentSessionId.value) }
                    .sortedByDescending { it.lastActivityMs }
                if (mapped.isNotEmpty()) {
                    _sessions.value = mapped
                    val mine = mapped.find { it.sessionId == _currentSessionId.value }
                    if (mine != null && mine.status == SessionStatus.REVOKED) {
                        handleRemoteCurrentSessionRevoked(mine.revokedReason ?: "Session révoquée à distance.")
                    }
                }
            }

        devicesListener = userDoc.collection("devices").limit(25)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val mapped = snap.documents.mapNotNull { AccountSecurityFirestoreSync.deviceFromDoc(it) }
                    .sortedByDescending { it.lastSeenAtMs }
                if (mapped.isNotEmpty()) _devices.value = mapped
            }

        loginHistoryListener = userDoc.collection("login_history").limit(40)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val mapped = snap.documents.mapNotNull { AccountSecurityFirestoreSync.loginHistoryFromDoc(it) }
                    .sortedByDescending { it.timestampMs }
                if (mapped.isNotEmpty()) _loginHistory.value = mapped
            }

        alertsListener = userDoc.collection("security_alerts").limit(30)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val mapped = snap.documents.mapNotNull { AccountSecurityFirestoreSync.alertFromDoc(it) }
                    .sortedByDescending { it.createdAtMs }
                if (mapped.isNotEmpty()) _securityAlerts.value = mapped
            }

        logsListener = userDoc.collection("security_logs").limit(50)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val mapped = snap.documents.mapNotNull { AccountSecurityFirestoreSync.securityLogFromDoc(it) }
                    .sortedByDescending { it.timestampMs }
                if (mapped.isNotEmpty()) _securityLogs.value = mapped
            }

        // Register current session on cloud sign-in after ensuring profile exists
        registerSignInSession(
            deviceIdHash = currentDeviceIdHash,
            deviceName = AccountSecurityCrypto.currentDeviceModelName(),
            platformLabel = AccountSecurityCrypto.currentPlatformLabel(chatRepo.appContextOrNull()),
            regionLabel = detectRegionLabel(),
            authMethod = "GOOGLE_OIDC",
            uidOverride = uid
        )
    }

    /**
     * Registers a login/session (used at auth sign-in or simulated/tested for multi-device anomaly detection).
     */
    fun registerSignInSession(
        deviceIdHash: String = currentDeviceIdHash,
        deviceName: String = AccountSecurityCrypto.currentDeviceModelName(),
        platformLabel: String = AccountSecurityCrypto.currentPlatformLabel(chatRepo.appContextOrNull()),
        regionLabel: String = detectRegionLabel(),
        authMethod: String = "GOOGLE_OIDC",
        uidOverride: String? = null
    ): Result<AccountSession> {
        val uid = uidOverride ?: activeUid()
        val now = nowProvider()
        val profile = _securityProfile.value.let { if (it.userId.isBlank()) it.copy(userId = uid) else it }

        val assessment = AccountSecurityEvaluator.evaluateLoginSignals(
            deviceIdHash = deviceIdHash,
            regionLabel = regionLabel,
            knownDevices = _devices.value,
            recentSessions = _sessions.value,
            nowMs = now
        )

        val newSessionId = if (deviceIdHash == currentDeviceIdHash && _sessions.value.none { it.sessionId == _currentSessionId.value && it.userId == uid && it.status == SessionStatus.ACTIVE }) {
            _currentSessionId.value
        } else {
            "sess_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        }

        if (assessment.isBlocked) {
            val blockedHist = LoginHistoryEntry(
                id = "lh_${UUID.randomUUID().toString().take(10)}",
                userId = uid,
                sessionId = newSessionId,
                deviceIdHash = deviceIdHash,
                deviceName = deviceName,
                authMethod = authMethod,
                outcome = LoginOutcome.BLOCKED,
                regionLabel = regionLabel,
                timestampMs = now,
                anomalyReason = assessment.reasons.joinToString(" ")
            )
            _loginHistory.value = (listOf(blockedHist) + _loginHistory.value).take(40)
            persistLoginHistoryToCloud(uid, blockedHist)

            raiseSecurityAlert(
                uid = uid,
                title = "Connexion bloquée depuis un appareil révoqué",
                description = assessment.reasons.joinToString(" "),
                severity = SecuritySeverity.CRITICAL,
                relatedSessionId = null,
                relatedDeviceIdHash = deviceIdHash,
                recommendedAction = assessment.recommendedAction
            )
            appendLogInternal(uid, SecurityLogCategory.ANOMALY, SecuritySeverity.CRITICAL, "BLOCKED_DEVICE_LOGIN", "Accès refusé pour l'appareil bloqué $deviceName.")
            return Result.failure(SecurityException("Appareil bloqué par la politique de sécurité RAVEN."))
        }

        val session = AccountSession(
            sessionId = newSessionId,
            userId = uid,
            deviceIdHash = deviceIdHash,
            deviceName = deviceName,
            platformLabel = platformLabel,
            appVersion = "1.0",
            networkRegionLabel = regionLabel,
            status = SessionStatus.ACTIVE,
            sessionEpoch = profile.sessionEpoch.coerceAtLeast(1L),
            createdAtMs = now,
            lastActivityMs = now,
            expiresAtMs = now + 30L * 86_400_000L,
            isCurrentDevice = deviceIdHash == currentDeviceIdHash
        )

        val existingDevice = _devices.value.find { it.deviceIdHash == deviceIdHash }
        val updatedDevice = existingDevice?.copy(
            displayName = existingDevice.displayName.ifBlank { deviceName },
            lastSeenAtMs = now,
            lastRegionLabel = regionLabel,
            lastSessionId = newSessionId
        ) ?: ConnectedDevice(
            deviceIdHash = deviceIdHash,
            userId = uid,
            displayName = deviceName,
            modelName = deviceName,
            osVersion = platformLabel,
            trustState = if (deviceIdHash == currentDeviceIdHash && _devices.value.none { it.userId == uid }) DeviceTrustState.TRUSTED else DeviceTrustState.UNTRUSTED,
            firstSeenAtMs = now,
            lastSeenAtMs = now,
            lastRegionLabel = regionLabel,
            lastSessionId = newSessionId
        )

        _securityProfile.value = profile
        _sessions.value = (listOf(session) + _sessions.value.filterNot { it.sessionId == newSessionId })
            .sortedByDescending { it.lastActivityMs }
        _devices.value = (listOf(updatedDevice) + _devices.value.filterNot { it.deviceIdHash == deviceIdHash })
            .sortedByDescending { it.lastSeenAtMs }

        val outcome = if (assessment.isSuspicious) LoginOutcome.SUSPICIOUS_FLAGGED else LoginOutcome.SUCCESS
        val histEntry = LoginHistoryEntry(
            id = "lh_${UUID.randomUUID().toString().take(10)}",
            userId = uid,
            sessionId = newSessionId,
            deviceIdHash = deviceIdHash,
            deviceName = deviceName,
            authMethod = authMethod,
            outcome = outcome,
            regionLabel = regionLabel,
            timestampMs = now,
            anomalyReason = assessment.reasons.firstOrNull()
        )
        _loginHistory.value = (listOf(histEntry) + _loginHistory.value).take(40)

        persistProfileToCloud(uid, profile)
        persistSessionToCloud(uid, session)
        persistDeviceToCloud(uid, updatedDevice)
        persistLoginHistoryToCloud(uid, histEntry)

        if (existingDevice == null && profile.alertOnNewDevice && deviceIdHash != currentDeviceIdHash) {
            raiseSecurityAlert(
                uid = uid,
                title = "Nouvel appareil connecté : $deviceName",
                description = "Une nouvelle session a été ouverte depuis $regionLabel.",
                severity = SecuritySeverity.MEDIUM,
                relatedSessionId = newSessionId,
                relatedDeviceIdHash = deviceIdHash,
                recommendedAction = "Confirmez cet appareil ou révoquez sa session."
            )
        } else if (assessment.isSuspicious && profile.alertOnUnusualActivity) {
            raiseSecurityAlert(
                uid = uid,
                title = "Activité inhabituelle détectée",
                description = assessment.reasons.joinToString(" "),
                severity = assessment.severity,
                relatedSessionId = newSessionId,
                relatedDeviceIdHash = deviceIdHash,
                recommendedAction = assessment.recommendedAction
            )
        }

        appendLogInternal(
            uid = uid,
            category = if (assessment.isSuspicious) SecurityLogCategory.ANOMALY else SecurityLogCategory.SESSION,
            severity = assessment.severity,
            action = if (assessment.isSuspicious) "SUSPICIOUS_LOGIN" else "LOGIN_RECORDED",
            summary = "Connexion ($authMethod) sur $deviceName · $regionLabel"
        )
        return Result.success(session)
    }

    /**
     * Evaluates whether a sensitive profile/email/account change should be allowed or frozen
     * under Suspicious Change Protection.
     */
    fun validateSensitiveAccountMutation(mutationLabel: String): Result<Unit> {
        val now = nowProvider()
        val uid = activeUid()
        val profile = _securityProfile.value

        if (AccountSecurityEvaluator.isFrozen(profile, now)) {
            appendLogInternal(
                uid,
                SecurityLogCategory.PROTECTION,
                SecuritySeverity.HIGH,
                "MUTATION_BLOCKED_FREEZE",
                "Action '$mutationLabel' bloquée : gel de sécurité actif."
            )
            _errorMessage.value = "Modifications sensibles temporairement gelées par protection anti-intrusion."
            return Result.failure(IllegalStateException(_errorMessage.value))
        }

        if (profile.protectSuspiciousChanges) {
            recentMutationTimestamps.removeAll { now - it > 10L * 60_000L }
            recentMutationTimestamps.add(now)
            if (recentMutationTimestamps.size >= 4) {
                val freezeUntil = now + AccountSecurityEvaluator.FREEZE_DURATION_MS
                val frozenProfile = profile.copy(
                    changeFreezeUntilMs = freezeUntil,
                    suspiciousAttemptCount = profile.suspiciousAttemptCount + 1,
                    updatedAtMs = now
                )
                _securityProfile.value = frozenProfile
                persistProfileToCloud(uid, frozenProfile)
                raiseSecurityAlert(
                    uid = uid,
                    title = "Changements suspects bloqués",
                    description = "Rafale inhabituelle de modifications ($mutationLabel). Verrouillage de sécurité activé pendant 15 min.",
                    severity = SecuritySeverity.HIGH,
                    relatedSessionId = _currentSessionId.value,
                    relatedDeviceIdHash = currentDeviceIdHash,
                    recommendedAction = "Vérifiez vos sessions et confirmez votre identité pour déverrouiller."
                )
                appendLogInternal(
                    uid,
                    SecurityLogCategory.PROTECTION,
                    SecuritySeverity.HIGH,
                    "SUSPICIOUS_BURST_FREEZE",
                    "Protection déclenchée suite à une rafale de modifications ($mutationLabel)."
                )
                _errorMessage.value = "Activité suspecte détectée : gel de sécurité activé pour protéger le compte."
                return Result.failure(IllegalStateException(_errorMessage.value))
            }
        }
        return Result.success(Unit)
    }

    fun requestSensitiveAction(request: PendingSensitiveConfirmation) {
        val now = nowProvider()
        val profile = _securityProfile.value
        val uid = activeUid()

        if (AccountSecurityEvaluator.isFrozen(profile, now) && request.actionType != SensitiveActionType.LIFT_FREEZE) {
            _errorMessage.value = "Compte temporairement gelé : confirmez d'abord le déverrouillage dans Sécurité."
            appendLogInternal(uid, SecurityLogCategory.PROTECTION, SecuritySeverity.HIGH, "ACTION_BLOCKED_FROZEN", "Action refusée sous gel : ${request.actionType.label}.")
            return
        }

        val mustConfirm = profile.requireReauthForSensitiveActions ||
            request.actionType in setOf(
                SensitiveActionType.GLOBAL_SIGN_OUT,
                SensitiveActionType.SET_SECURITY_PIN,
                SensitiveActionType.MANAGE_TWO_FACTOR,
                SensitiveActionType.LIFT_FREEZE
            )

        if (!mustConfirm || (AccountSecurityEvaluator.isVerificationFresh(profile, now) && request.actionType != SensitiveActionType.GLOBAL_SIGN_OUT)) {
            executeConfirmedAction(request)
        } else {
            _pendingConfirmation.value = request
        }
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    fun confirmWithGoogleReauthResult(succeeded: Boolean) {
        val pending = _pendingConfirmation.value ?: return
        if (!succeeded) {
            recordFailedChallenge("Ré-authentification Google échouée ou annulée.")
            return
        }
        markVerificationFreshAndResetStrikes()
        _pendingConfirmation.value = null
        executeConfirmedAction(pending)
    }

    fun confirmWithSecurityPin(candidatePin: String): Boolean {
        val pending = _pendingConfirmation.value ?: return false
        val profile = _securityProfile.value
        if (profile.pinHash.isNullOrBlank() || profile.pinSalt.isNullOrBlank()) {
            _errorMessage.value = "Aucun code de sécurité configuré sur ce compte."
            return false
        }
        val ok = AccountSecurityCrypto.verifyPinPbkdf2(candidatePin, profile.pinHash, profile.pinSalt)
        if (!ok) {
            recordFailedChallenge("Code de sécurité incorrect.")
            return false
        }
        markVerificationFreshAndResetStrikes()
        _pendingConfirmation.value = null
        executeConfirmedAction(pending)
        return true
    }

    fun confirmExplicitlyWhenNoPinOrCloud(): Boolean {
        val pending = _pendingConfirmation.value ?: return false
        val profile = _securityProfile.value
        if (!profile.pinHash.isNullOrBlank()) {
            _errorMessage.value = "Veuillez saisir votre code de sécurité ou utiliser la ré-authentification Google."
            return false
        }
        markVerificationFreshAndResetStrikes()
        _pendingConfirmation.value = null
        executeConfirmedAction(pending)
        return true
    }

    private fun recordFailedChallenge(reason: String) {
        val now = nowProvider()
        val uid = activeUid()
        val p = _securityProfile.value
        val nextStrikes = p.failedChallengeCount + 1
        val freeze = nextStrikes >= AccountSecurityEvaluator.MAX_FAILED_CHALLENGES_BEFORE_FREEZE
        val updated = p.copy(
            failedChallengeCount = nextStrikes,
            cooldownUntilMs = if (freeze) now + AccountSecurityEvaluator.FREEZE_DURATION_MS else p.cooldownUntilMs,
            changeFreezeUntilMs = if (freeze) now + AccountSecurityEvaluator.FREEZE_DURATION_MS else p.changeFreezeUntilMs,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
        appendLogInternal(
            uid,
            SecurityLogCategory.ANOMALY,
            if (freeze) SecuritySeverity.CRITICAL else SecuritySeverity.MEDIUM,
            "SENSITIVE_CHALLENGE_FAILED",
            "$reason (tentative $nextStrikes/${AccountSecurityEvaluator.MAX_FAILED_CHALLENGES_BEFORE_FREEZE})."
        )
        if (freeze) {
            _pendingConfirmation.value = null
            raiseSecurityAlert(
                uid = uid,
                title = "Verrouillage préventif après 3 échecs",
                description = "Plusieurs confirmations sensibles ont échoué. Actions critiques gelées 15 minutes.",
                severity = SecuritySeverity.CRITICAL,
                relatedSessionId = _currentSessionId.value,
                relatedDeviceIdHash = currentDeviceIdHash,
                recommendedAction = "Utilisez la ré-authentification Google pour reprendre le contrôle."
            )
            _errorMessage.value = "Trop de tentatives échouées : protection temporaire activée (15 min)."
        } else {
            _errorMessage.value = "$reason ($nextStrikes/${AccountSecurityEvaluator.MAX_FAILED_CHALLENGES_BEFORE_FREEZE})"
        }
    }

    private fun markVerificationFreshAndResetStrikes() {
        val now = nowProvider()
        val uid = activeUid()
        val updated = _securityProfile.value.copy(
            userId = uid,
            lastSensitiveVerifiedAtMs = now,
            failedChallengeCount = 0,
            cooldownUntilMs = 0L,
            changeFreezeUntilMs = 0L,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
    }

    private fun executeConfirmedAction(req: PendingSensitiveConfirmation) {
        when (req.actionType) {
            SensitiveActionType.REVOKE_SESSION -> req.targetId?.let { performRevokeSession(it, req.stringArg ?: "Révoquée par l'utilisateur") }
            SensitiveActionType.GLOBAL_SIGN_OUT -> performGlobalSignOutNow(includeCurrentDevice = req.boolArg)
            SensitiveActionType.BLOCK_DEVICE -> req.targetId?.let { performUpdateDeviceTrust(it, DeviceTrustState.BLOCKED) }
            SensitiveActionType.REMOVE_DEVICE -> req.targetId?.let { performRemoveDevice(it) }
            SensitiveActionType.CHANGE_PROTECTION_POLICY -> req.policySnapshot?.let { applyPolicySnapshot(it) }
            SensitiveActionType.SET_SECURITY_PIN -> req.stringArg?.let { applySecurityPin(it) }
            SensitiveActionType.MANAGE_TWO_FACTOR -> {
                if (req.targetId == "GEN_CODES") {
                    applyGenerateBackupCodes()
                } else if (req.targetId?.startsWith("REMOVE:") == true) {
                    applyRemoveTwoFactorFactor(req.targetId.removePrefix("REMOVE:"))
                } else if (req.methodArg != null) {
                    applyPrepareTwoFactorFactor(req.methodArg, req.stringArg ?: "")
                }
            }
            SensitiveActionType.LIFT_FREEZE -> {
                markVerificationFreshAndResetStrikes()
                appendLogInternal(activeUid(), SecurityLogCategory.PROTECTION, SecuritySeverity.INFO, "FREEZE_LIFTED", "Gel de sécurité levé après confirmation.")
                _feedbackMessage.value = "Verrouillage de sécurité levé."
            }
            SensitiveActionType.CRITICAL_PROFILE_CHANGE -> {
                _feedbackMessage.value = "Modification sensible autorisée."
            }
        }
    }

    fun performRevokeSession(sessionId: String, reason: String = "Révoquée par le propriétaire") {
        val uid = activeUid()
        val now = nowProvider()
        val target = _sessions.value.find { it.sessionId == sessionId } ?: return
        val revoked = target.copy(
            status = SessionStatus.REVOKED,
            revokedAtMs = now,
            revokedReason = reason
        )
        _sessions.value = _sessions.value.map { if (it.sessionId == sessionId) revoked else it }
        persistSessionToCloud(uid, revoked)

        appendLogInternal(uid, SecurityLogCategory.SESSION, SecuritySeverity.MEDIUM, "SESSION_REVOKED", "Session révoquée sur ${target.deviceName}.")
        chatRepo.addNotification(
            title = "Session révoquée",
            body = "La session sur ${target.deviceName} a été déconnectée.",
            sourceLabel = "Sécurité RAVEN",
            type = NotificationType.SYSTEM
        )
        _feedbackMessage.value = "Session sur ${target.deviceName} révoquée."

        if (sessionId == _currentSessionId.value) {
            handleRemoteCurrentSessionRevoked(reason)
        }
    }

    fun performGlobalSignOutNow(includeCurrentDevice: Boolean = false) {
        val uid = activeUid()
        val now = nowProvider()
        val currentProfile = _securityProfile.value
        val nextEpoch = currentProfile.sessionEpoch + 1L
        val updatedProfile = currentProfile.copy(
            userId = uid,
            sessionEpoch = nextEpoch,
            globalSignOutAfterMs = now,
            updatedAtMs = now
        )
        _securityProfile.value = updatedProfile
        persistProfileToCloud(uid, updatedProfile)

        val updatedSessions = _sessions.value.map { s ->
            val shouldRevoke = includeCurrentDevice || s.sessionId != _currentSessionId.value
            if (shouldRevoke && s.status == SessionStatus.ACTIVE) {
                val rev = s.copy(
                    status = SessionStatus.REVOKED,
                    revokedAtMs = now,
                    revokedReason = "Déconnexion globale (epoch #$nextEpoch)"
                )
                persistSessionToCloud(uid, rev)
                rev
            } else if (!includeCurrentDevice && s.sessionId == _currentSessionId.value) {
                val kept = s.copy(sessionEpoch = nextEpoch, createdAtMs = now, lastActivityMs = now)
                persistSessionToCloud(uid, kept)
                kept
            } else s
        }
        _sessions.value = updatedSessions

        appendLogInternal(
            uid,
            SecurityLogCategory.SESSION,
            SecuritySeverity.HIGH,
            "GLOBAL_SIGN_OUT",
            "Déconnexion globale exécutée (epoch #$nextEpoch, appareil actuel inclus: $includeCurrentDevice)."
        )
        chatRepo.addNotification(
            title = "Déconnexion globale effectuée",
            body = "Toutes les autres sessions actives ont été révoquées (Epoch #$nextEpoch).",
            sourceLabel = "Sécurité RAVEN",
            type = NotificationType.SYSTEM
        )
        _feedbackMessage.value = "Déconnexion globale appliquée à tous les autres appareils."

        if (includeCurrentDevice) {
            try {
                chatRepo.authInstance()?.signOut()
                chatRepo.syncFirebaseUser()
            } catch (_: Exception) {
            }
            _remoteRevocationMessage.value = "Déconnexion globale effectuée sur cet appareil."
        }
    }

    fun renameDevice(deviceIdHash: String, newName: String) {
        val clean = newName.trim().take(48)
        if (clean.length < 2) {
            _errorMessage.value = "Le nom de l'appareil doit contenir au moins 2 caractères."
            return
        }
        val uid = activeUid()
        val target = _devices.value.find { it.deviceIdHash == deviceIdHash } ?: return
        val updated = target.copy(displayName = clean)
        _devices.value = _devices.value.map { if (it.deviceIdHash == deviceIdHash) updated else it }
        persistDeviceToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.DEVICE, SecuritySeverity.INFO, "DEVICE_RENAMED", "Appareil renommé en '$clean'.")
        _feedbackMessage.value = "Appareil renommé."
    }

    fun requestDeviceTrustChange(device: ConnectedDevice, targetTrust: DeviceTrustState) {
        if (targetTrust == DeviceTrustState.BLOCKED) {
            requestSensitiveAction(
                PendingSensitiveConfirmation(
                    actionType = SensitiveActionType.BLOCK_DEVICE,
                    targetId = device.deviceIdHash,
                    title = "Bloquer ${device.displayName} ?",
                    description = "Cet appareil ne pourra plus ouvrir de session et toutes ses sessions actives seront immédiatement révoquées."
                )
            )
        } else {
            performUpdateDeviceTrust(device.deviceIdHash, targetTrust)
        }
    }

    private fun performUpdateDeviceTrust(deviceIdHash: String, targetTrust: DeviceTrustState) {
        val uid = activeUid()
        val now = nowProvider()
        val target = _devices.value.find { it.deviceIdHash == deviceIdHash } ?: return
        val updated = target.copy(trustState = targetTrust, lastSeenAtMs = now)
        _devices.value = _devices.value.map { if (it.deviceIdHash == deviceIdHash) updated else it }
        persistDeviceToCloud(uid, updated)

        if (targetTrust == DeviceTrustState.BLOCKED) {
            _sessions.value.filter { it.deviceIdHash == deviceIdHash && it.status == SessionStatus.ACTIVE }
                .forEach { performRevokeSession(it.sessionId, "Appareil bloqué par le propriétaire") }
        }
        appendLogInternal(uid, SecurityLogCategory.DEVICE, if (targetTrust == DeviceTrustState.BLOCKED) SecuritySeverity.HIGH else SecuritySeverity.INFO, "DEVICE_TRUST_${targetTrust.name}", "${target.displayName} marqué : ${targetTrust.label}.")
        _feedbackMessage.value = "${target.displayName} : ${targetTrust.label}."
    }

    fun requestRemoveDevice(device: ConnectedDevice) {
        requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.REMOVE_DEVICE,
                targetId = device.deviceIdHash,
                title = "Supprimer ${device.displayName} ?",
                description = "L'appareil sera dissocié et ses sessions actives révoquées."
            )
        )
    }

    private fun performRemoveDevice(deviceIdHash: String) {
        val uid = activeUid()
        _sessions.value.filter { it.deviceIdHash == deviceIdHash && it.status == SessionStatus.ACTIVE }
            .forEach { performRevokeSession(it.sessionId, "Appareil supprimé du compte") }
        _devices.value = _devices.value.filterNot { it.deviceIdHash == deviceIdHash }
        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                runCatching {
                    Tasks.await(db.collection("users").document(authUid).collection("devices").document(deviceIdHash).delete())
                }
            }
        }
        appendLogInternal(uid, SecurityLogCategory.DEVICE, SecuritySeverity.MEDIUM, "DEVICE_REMOVED", "Appareil dissocié du compte.")
        _feedbackMessage.value = "Appareil dissocié."
    }

    fun requestPolicyUpdate(updated: AccountSecurityProfile) {
        val current = _securityProfile.value
        val loweringSecurity = (!updated.protectSuspiciousChanges && current.protectSuspiciousChanges) ||
            (!updated.requireReauthForSensitiveActions && current.requireReauthForSensitiveActions)
        if (loweringSecurity) {
            requestSensitiveAction(
                PendingSensitiveConfirmation(
                    actionType = SensitiveActionType.CHANGE_PROTECTION_POLICY,
                    title = "Confirmer la modification de protection",
                    description = "Vous réduisez une protection sensible de votre compte RAVEN.",
                    policySnapshot = updated
                )
            )
        } else {
            applyPolicySnapshot(updated)
        }
    }

    private fun applyPolicySnapshot(snapshot: AccountSecurityProfile) {
        val uid = activeUid()
        val now = nowProvider()
        val finalProfile = snapshot.copy(userId = uid, updatedAtMs = now)
        _securityProfile.value = finalProfile
        persistProfileToCloud(uid, finalProfile)
        appendLogInternal(uid, SecurityLogCategory.PROTECTION, SecuritySeverity.INFO, "POLICY_UPDATED", "Paramètres de protection mis à jour.")
        _feedbackMessage.value = "Politique de sécurité enregistrée."
    }

    fun activateEmergencyLockdown(durationMinutes: Int = 30) {
        val uid = activeUid()
        val now = nowProvider()
        val until = now + durationMinutes.coerceIn(5, 1440) * 60_000L
        val updated = _securityProfile.value.copy(
            userId = uid,
            changeFreezeUntilMs = until,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.PROTECTION, SecuritySeverity.HIGH, "EMERGENCY_LOCKDOWN", "Verrouillage d'urgence activé pour $durationMinutes min.")
        chatRepo.addNotification(
            title = "Verrouillage de sécurité activé",
            body = "Les modifications sensibles sont gelées pendant $durationMinutes minutes.",
            sourceLabel = "Sécurité RAVEN",
            type = NotificationType.SYSTEM
        )
        _feedbackMessage.value = "Verrouillage de sécurité activé ($durationMinutes min)."
    }

    fun requestLiftFreeze() {
        requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.LIFT_FREEZE,
                title = "Lever le gel de sécurité",
                description = "Confirmez votre identité pour lever le verrouillage préventif du compte."
            )
        )
    }

    fun requestConfigureSecurityPin(newPin: String) {
        val clean = newPin.trim()
        if (clean.length !in 4..12) {
            _errorMessage.value = "Le code de sécurité doit contenir entre 4 et 12 caractères."
            return
        }
        if (_securityProfile.value.pinHash.isNullOrBlank()) {
            applySecurityPin(clean)
        } else {
            requestSensitiveAction(
                PendingSensitiveConfirmation(
                    actionType = SensitiveActionType.SET_SECURITY_PIN,
                    title = "Modifier le code de sécurité",
                    description = "Confirmez votre code actuel ou votre compte Google avant de le remplacer.",
                    stringArg = clean
                )
            )
        }
    }

    private fun applySecurityPin(cleanPin: String) {
        val uid = activeUid()
        val now = nowProvider()
        val salt = AccountSecurityCrypto.generateSaltHex(16)
        val hash = AccountSecurityCrypto.hashPinPbkdf2(cleanPin, salt)
        val updated = _securityProfile.value.copy(
            userId = uid,
            pinHash = hash,
            pinSalt = salt,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.PROTECTION, SecuritySeverity.INFO, "SECURITY_PIN_SET", "Code de sécurité mis à jour (empreinte PBKDF2 salée uniquement).")
        _feedbackMessage.value = "Code de confirmation sécurisé enregistré."
    }

    fun requestPrepareTwoFactor(method: TwoFactorMethod, label: String) {
        if (method == TwoFactorMethod.NONE) {
            applyPolicySnapshot(_securityProfile.value.copy(preferredTwoFactorMethod = TwoFactorMethod.NONE))
            return
        }
        requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.MANAGE_TWO_FACTOR,
                title = "Configurer ${method.label}",
                description = "Prépare ce facteur pour la vérification à deux étapes RAVEN (métadonnées sécurisées uniquement).",
                stringArg = label,
                methodArg = method
            )
        )
    }

    private fun applyPrepareTwoFactorFactor(method: TwoFactorMethod, label: String) {
        val driver = TwoFactorDriverRegistry.driverFor(method) ?: return
        val uid = activeUid()
        val now = nowProvider()
        val factor = driver.buildPendingFactorMeta(label, now)
        val p = _securityProfile.value
        val updated = p.copy(
            userId = uid,
            twoFactorReady = true,
            preferredTwoFactorMethod = method,
            enrolledFactorsMeta = (p.enrolledFactorsMeta + factor).take(8),
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.TWO_FACTOR, SecuritySeverity.INFO, "TWO_FACTOR_PREPARED", "Facteur 2FA '${factor.label}' préparé (${method.label}).")
        _feedbackMessage.value = "${factor.label} prêt pour la 2FA."
    }

    fun requestRemoveTwoFactorFactor(factorId: String) {
        requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.MANAGE_TWO_FACTOR,
                targetId = "REMOVE:$factorId",
                title = "Retirer ce facteur 2FA ?",
                description = "Ce facteur ne sera plus associé à votre profil 2FA."
            )
        )
    }

    private fun applyRemoveTwoFactorFactor(factorId: String) {
        val uid = activeUid()
        val now = nowProvider()
        val p = _securityProfile.value
        val remaining = p.enrolledFactorsMeta.filterNot { it.factorId == factorId }
        val updated = p.copy(
            userId = uid,
            enrolledFactorsMeta = remaining,
            preferredTwoFactorMethod = if (remaining.isEmpty()) TwoFactorMethod.NONE else p.preferredTwoFactorMethod,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        persistProfileToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.TWO_FACTOR, SecuritySeverity.MEDIUM, "TWO_FACTOR_REMOVED", "Facteur 2FA retiré.")
        _feedbackMessage.value = "Facteur 2FA retiré."
    }

    fun requestGenerateRecoveryCodes() {
        requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.MANAGE_TWO_FACTOR,
                targetId = "GEN_CODES",
                title = "Générer des codes de récupération 2FA",
                description = "Les anciens codes de secours seront invalidés. Seules les empreintes SHA-256 seront sauvegardées."
            )
        )
    }

    private fun applyGenerateBackupCodes() {
        val uid = activeUid()
        val now = nowProvider()
        val p = _securityProfile.value
        val salt = p.pinSalt ?: AccountSecurityCrypto.generateSaltHex(12)
        val pairs = AccountSecurityCrypto.generateOneTimeBackupCodes(salt = "$uid:$salt", count = 6)
        val updated = p.copy(
            userId = uid,
            pinSalt = p.pinSalt ?: salt,
            backupCodesRemainingCount = pairs.size,
            backupCodeHashes = pairs.map { it.second },
            twoFactorReady = true,
            updatedAtMs = now
        )
        _securityProfile.value = updated
        _oneTimeBackupCodes.value = pairs.map { it.first }
        persistProfileToCloud(uid, updated)
        appendLogInternal(uid, SecurityLogCategory.TWO_FACTOR, SecuritySeverity.MEDIUM, "BACKUP_CODES_ROTATED", "${pairs.size} codes de secours générés (seuls les hash SHA-256 sont stockés).")
        _feedbackMessage.value = "Codes de secours générés — conservez-les en lieu sûr."
    }

    fun clearOneTimeBackupCodes() {
        _oneTimeBackupCodes.value = emptyList()
    }

    fun resolveSecurityAlert(alertId: String, resolution: SecurityAlertStatus, alsoRevokeSession: Boolean = false) {
        val uid = activeUid()
        val now = nowProvider()
        val alert = _securityAlerts.value.find { it.id == alertId } ?: return
        val updated = alert.copy(status = resolution, resolvedAtMs = now)
        _securityAlerts.value = _securityAlerts.value.map { if (it.id == alertId) updated else it }
        persistAlertToCloud(uid, updated)

        if (resolution == SecurityAlertStatus.CONFIRMED_SAFE && !alert.relatedDeviceIdHash.isNullOrBlank()) {
            performUpdateDeviceTrust(alert.relatedDeviceIdHash, DeviceTrustState.TRUSTED)
        }
        if (alsoRevokeSession && !alert.relatedSessionId.isNullOrBlank()) {
            performRevokeSession(alert.relatedSessionId, "Révoquée depuis alerte de sécurité")
        }
        appendLogInternal(uid, SecurityLogCategory.ANOMALY, SecuritySeverity.INFO, "ALERT_${resolution.name}", "Alerte '${alert.title}' marquée : ${resolution.label}.")
        _feedbackMessage.value = "Alerte mise à jour : ${resolution.label}."
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    fun clearRemoteRevocationMessage() {
        _remoteRevocationMessage.value = null
    }

    private fun adoptOrRevokeOnInitialSync(uid: String, profile: AccountSecurityProfile) {
        val current = _sessions.value.find { it.sessionId == _currentSessionId.value } ?: return
        if (profile.globalSignOutAfterMs > initialCloudSignInAtMs) {
            handleRemoteCurrentSessionRevoked("Session invalidée par déconnexion globale (Epoch #${profile.sessionEpoch}).")
            return
        }
        if (current.sessionEpoch < profile.sessionEpoch) {
            val upgraded = current.copy(sessionEpoch = profile.sessionEpoch, createdAtMs = nowProvider())
            _sessions.value = _sessions.value.map { if (it.sessionId == upgraded.sessionId) upgraded else it }
            persistSessionToCloud(uid, upgraded)
        }
    }

    private fun checkCurrentSessionValidity(profile: AccountSecurityProfile) {
        val current = _sessions.value.find { it.sessionId == _currentSessionId.value } ?: return
        val now = nowProvider()
        if (!AccountSecurityEvaluator.isSessionValid(current, profile, now)) {
            handleRemoteCurrentSessionRevoked("Session invalidée par déconnexion globale (Epoch #${profile.sessionEpoch}).")
        }
    }

    private fun handleRemoteCurrentSessionRevoked(reason: String) {
        if (_remoteRevocationMessage.value != null) return
        _remoteRevocationMessage.value = reason
        chatRepo.addNotification(
            title = "Session déconnectée à distance",
            body = reason,
            sourceLabel = "Sécurité RAVEN",
            type = NotificationType.SYSTEM
        )
        try {
            chatRepo.authInstance()?.signOut()
            chatRepo.syncFirebaseUser()
        } catch (_: Exception) {
        }
    }

    private fun raiseSecurityAlert(
        uid: String,
        title: String,
        description: String,
        severity: SecuritySeverity,
        relatedSessionId: String?,
        relatedDeviceIdHash: String?,
        recommendedAction: String
    ) {
        val now = nowProvider()
        val alert = SecurityAlert(
            id = "sal_${UUID.randomUUID().toString().take(10)}",
            userId = uid,
            title = title,
            description = description,
            severity = severity,
            status = SecurityAlertStatus.OPEN,
            relatedSessionId = relatedSessionId,
            relatedDeviceIdHash = relatedDeviceIdHash,
            recommendedAction = recommendedAction,
            createdAtMs = now
        )
        _securityAlerts.value = (listOf(alert) + _securityAlerts.value).take(30)
        persistAlertToCloud(uid, alert)
        chatRepo.addNotification(
            title = title,
            body = description,
            sourceLabel = "Alerte Sécurité RAVEN",
            type = NotificationType.SYSTEM
        )
    }

    private fun appendLogInternal(
        uid: String,
        category: SecurityLogCategory,
        severity: SecuritySeverity,
        action: String,
        summary: String
    ) {
        val now = nowProvider()
        val entry = SecurityLogEntry(
            id = "slog_${UUID.randomUUID().toString().take(10)}",
            userId = uid,
            category = category,
            severity = severity,
            action = action,
            summary = summary,
            actorDeviceIdHash = currentDeviceIdHash,
            timestampMs = now,
            timestampLabel = timeLabel(now)
        )
        _securityLogs.value = (listOf(entry) + _securityLogs.value).take(60)
        persistLogToCloud(uid, entry)
    }

    private fun persistProfileToCloud(uid: String, profile: AccountSecurityProfile) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("security_profile").document("account")
                        .set(AccountSecurityFirestoreSync.securityProfileToMap(profile))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun persistSessionToCloud(uid: String, session: AccountSession) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("sessions").document(session.sessionId)
                        .set(AccountSecurityFirestoreSync.sessionToMap(session))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun persistDeviceToCloud(uid: String, device: ConnectedDevice) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("devices").document(device.deviceIdHash)
                        .set(AccountSecurityFirestoreSync.deviceToMap(device))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun persistLoginHistoryToCloud(uid: String, entry: LoginHistoryEntry) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("login_history").document(entry.id)
                        .set(AccountSecurityFirestoreSync.loginHistoryToMap(entry))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun persistAlertToCloud(uid: String, alert: SecurityAlert) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("security_alerts").document(alert.id)
                        .set(AccountSecurityFirestoreSync.alertToMap(alert))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun persistLogToCloud(uid: String, entry: SecurityLogEntry) {
        val db = chatRepo.firestoreDb() ?: return
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (authUid != uid) return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(authUid)
                        .collection("security_logs").document(entry.id)
                        .set(AccountSecurityFirestoreSync.securityLogToMap(entry))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun detachListeners() {
        profileListener?.remove(); profileListener = null
        sessionsListener?.remove(); sessionsListener = null
        devicesListener?.remove(); devicesListener = null
        loginHistoryListener?.remove(); loginHistoryListener = null
        alertsListener?.remove(); alertsListener = null
        logsListener?.remove(); logsListener = null
    }
}
