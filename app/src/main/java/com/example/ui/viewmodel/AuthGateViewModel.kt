package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthEntryIntent
import com.example.data.AuthGateRepository
import com.example.data.AuthGateUiState
import com.example.data.RememberedGmailAccount
import com.example.model.UserStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthGateViewModel(
    private val authGateRepo: AuthGateRepository
) : ViewModel() {

    val uiState: StateFlow<AuthGateUiState> = authGateRepo.state

    fun evaluateStartup() {
        authGateRepo.evaluateStartupSession()
    }

    fun onAuthIntentChange(intent: AuthEntryIntent) {
        authGateRepo.selectAuthIntent(intent)
    }

    fun onGmailInputChange(email: String) {
        authGateRepo.updateGmailInput(email)
    }

    fun selectRememberedAccount(account: RememberedGmailAccount) {
        authGateRepo.selectRememberedAccount(account)
    }

    fun removeRememberedAccount(uid: String) {
        authGateRepo.removeRememberedAccount(uid)
    }

    fun setAuthError(message: String?) {
        authGateRepo.setAuthError(message)
    }

    fun clearLockoutIfExpired() {
        authGateRepo.clearLockoutIfExpired()
    }

    fun onGmailCredentialSignedIn(prefilledCode: String, isSilentAutoSignIn: Boolean = false) {
        viewModelScope.launch {
            authGateRepo.onGmailCredentialSignedIn(
                prefilledCode = prefilledCode,
                isSilentAutoSignIn = isSilentAutoSignIn
            )
        }
    }

    fun completeAccountCreation(
        name: String,
        tag: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        status: UserStatus,
        accessCode: String,
        confirmCode: String,
        requireCodeOnAppLaunch: Boolean = false
    ) {
        viewModelScope.launch {
            authGateRepo.completeAccountCreation(
                name = name,
                tag = tag,
                bio = bio,
                avatarUrl = avatarUrl,
                avatarColor = avatarColor,
                status = status,
                accessCode = accessCode,
                confirmCode = confirmCode,
                requireCodeOnAppLaunch = requireCodeOnAppLaunch
            )
        }
    }

    fun verifyAccessCode(code: String) {
        authGateRepo.verifyAccessCode(code)
    }

    fun unlockWithBiometrics() {
        authGateRepo.unlockWithBiometrics()
    }

    fun hasAccessCodeConfigured(): Boolean = authGateRepo.hasAccessCodeConfigured()

    fun verifyLockAccessCode(candidatePin: String): Boolean =
        authGateRepo.verifyAccessCodeForLock(candidatePin)

    fun onGmailReauthConfirmedForReset() {
        authGateRepo.enterCodeResetAfterGmailReauth()
    }

    fun cancelCodeReset() {
        authGateRepo.cancelCodeReset()
    }

    fun applyResetAccessCode(newCode: String, confirmCode: String) {
        viewModelScope.launch {
            authGateRepo.applyResetAccessCode(newCode, confirmCode)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authGateRepo.signOut()
        }
    }
}
