package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountSecurityEvaluator
import com.example.data.AccountSecurityRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AccountSecurityViewModel(
    private val securityRepo: AccountSecurityRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _selectedLogCategory = MutableStateFlow(SecurityLogCategory.ALL)

    val uiState: StateFlow<AccountSecurityUiState> = combine(
        securityRepo.securityProfile,
        securityRepo.sessions,
        securityRepo.devices,
        securityRepo.loginHistory,
        securityRepo.securityAlerts,
        securityRepo.securityLogs,
        _selectedLogCategory,
        securityRepo.currentSessionId,
        securityRepo.pendingConfirmation,
        securityRepo.oneTimeBackupCodes,
        securityRepo.remoteRevocationMessage,
        securityRepo.feedbackMessage,
        securityRepo.errorMessage,
        securityRepo.isBusy
    ) { flows ->
        val profile = flows[0] as AccountSecurityProfile
        @Suppress("UNCHECKED_CAST")
        val sessions = flows[1] as List<AccountSession>
        @Suppress("UNCHECKED_CAST")
        val devices = flows[2] as List<ConnectedDevice>
        @Suppress("UNCHECKED_CAST")
        val history = flows[3] as List<LoginHistoryEntry>
        @Suppress("UNCHECKED_CAST")
        val alerts = flows[4] as List<SecurityAlert>
        @Suppress("UNCHECKED_CAST")
        val logs = flows[5] as List<SecurityLogEntry>
        val selectedCat = flows[6] as SecurityLogCategory
        val currentSid = flows[7] as String
        val pendingConfirm = flows[8] as PendingSensitiveConfirmation?
        @Suppress("UNCHECKED_CAST")
        val backupCodes = flows[9] as List<String>
        val remoteRevMsg = flows[10] as String?
        val feedback = flows[11] as String?
        val error = flows[12] as String?
        val busy = flows[13] as Boolean

        val now = nowProvider()
        val filteredLogs = if (selectedCat == SecurityLogCategory.ALL) logs else logs.filter { it.category == selectedCat }

        AccountSecurityUiState(
            securityProfile = profile,
            sessions = sessions,
            devices = devices,
            loginHistory = history,
            securityAlerts = alerts,
            securityLogs = filteredLogs,
            selectedLogCategory = selectedCat,
            currentSessionId = currentSid,
            currentDeviceIdHash = securityRepo.currentDeviceIdHash,
            pendingConfirmation = pendingConfirm,
            oneTimeBackupCodes = backupCodes,
            isSensitiveVerificationFresh = AccountSecurityEvaluator.isVerificationFresh(profile, now),
            isSecurityFrozen = AccountSecurityEvaluator.isFrozen(profile, now),
            openAlertsCount = alerts.count { it.status == SecurityAlertStatus.OPEN },
            activeSessionsCount = sessions.count { it.status == SessionStatus.ACTIVE },
            remoteRevocationMessage = remoteRevMsg,
            feedbackMessage = feedback,
            errorMessage = error,
            isBusy = busy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AccountSecurityUiState()
    )

    fun syncWithAuthUid(uid: String?) {
        securityRepo.syncWithAuthUid(uid)
    }

    fun selectLogCategory(category: SecurityLogCategory) {
        _selectedLogCategory.value = category
    }

    fun requestRevokeSession(session: AccountSession) {
        securityRepo.requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.REVOKE_SESSION,
                targetId = session.sessionId,
                title = "Révoquer la session ?",
                description = "La session active sur '${session.deviceName}' (${session.networkRegionLabel}) sera immédiatement déconnectée.",
                stringArg = "Révoquée par l'utilisateur"
            )
        )
    }

    fun requestGlobalSignOut(includeCurrentDevice: Boolean) {
        securityRepo.requestSensitiveAction(
            PendingSensitiveConfirmation(
                actionType = SensitiveActionType.GLOBAL_SIGN_OUT,
                title = "Déconnexion globale du compte",
                description = if (includeCurrentDevice) {
                    "Toutes les sessions (y compris cet appareil) seront révoquées immédiatement."
                } else {
                    "Toutes les autres sessions actives sur vos appareils seront révoquées."
                },
                boolArg = includeCurrentDevice
            )
        )
    }

    fun renameDevice(deviceIdHash: String, newName: String) {
        securityRepo.renameDevice(deviceIdHash, newName)
    }

    fun setDeviceTrust(device: ConnectedDevice, trustState: DeviceTrustState) {
        securityRepo.requestDeviceTrustChange(device, trustState)
    }

    fun removeDevice(device: ConnectedDevice) {
        securityRepo.requestRemoveDevice(device)
    }

    fun updatePolicies(
        protectSuspiciousChanges: Boolean = uiState.value.securityProfile.protectSuspiciousChanges,
        alertOnNewDevice: Boolean = uiState.value.securityProfile.alertOnNewDevice,
        alertOnUnusualActivity: Boolean = uiState.value.securityProfile.alertOnUnusualActivity,
        requireReauthForSensitiveActions: Boolean = uiState.value.securityProfile.requireReauthForSensitiveActions
    ) {
        securityRepo.requestPolicyUpdate(
            uiState.value.securityProfile.copy(
                protectSuspiciousChanges = protectSuspiciousChanges,
                alertOnNewDevice = alertOnNewDevice,
                alertOnUnusualActivity = alertOnUnusualActivity,
                requireReauthForSensitiveActions = requireReauthForSensitiveActions
            )
        )
    }

    fun activateEmergencyLockdown() {
        securityRepo.activateEmergencyLockdown(30)
    }

    fun requestLiftFreeze() {
        securityRepo.requestLiftFreeze()
    }

    fun requestConfigureSecurityPin(newPin: String) {
        securityRepo.requestConfigureSecurityPin(newPin)
    }

    fun requestPrepareTwoFactor(method: TwoFactorMethod, label: String) {
        securityRepo.requestPrepareTwoFactor(method, label)
    }

    fun requestRemoveTwoFactorFactor(factorId: String) {
        securityRepo.requestRemoveTwoFactorFactor(factorId)
    }

    fun requestGenerateRecoveryCodes() {
        securityRepo.requestGenerateRecoveryCodes()
    }

    fun clearOneTimeBackupCodes() {
        securityRepo.clearOneTimeBackupCodes()
    }

    fun resolveAlert(alertId: String, status: SecurityAlertStatus, alsoRevokeSession: Boolean = false) {
        securityRepo.resolveSecurityAlert(alertId, status, alsoRevokeSession)
    }

    fun confirmWithGoogleReauth(succeeded: Boolean) {
        securityRepo.confirmWithGoogleReauthResult(succeeded)
    }

    fun confirmWithSecurityPin(pin: String): Boolean =
        securityRepo.confirmWithSecurityPin(pin)

    fun confirmExplicitlyWhenNoPinOrCloud(): Boolean =
        securityRepo.confirmExplicitlyWhenNoPinOrCloud()

    fun dismissPendingConfirmation() {
        securityRepo.dismissPendingConfirmation()
    }

    fun clearFeedback() {
        securityRepo.clearFeedback()
    }

    fun clearRemoteRevocationMessage() {
        securityRepo.clearRemoteRevocationMessage()
    }
}
