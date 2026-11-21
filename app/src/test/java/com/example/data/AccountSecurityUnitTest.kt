package com.example.data

import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountSecurityUnitTest {

    private class MutableClock(var nowMs: Long = 1_760_100_000_000L)

    private data class Harness(
        val scope: CoroutineScope,
        val clock: MutableClock,
        val chatRepo: ChatRepository,
        val securityRepo: AccountSecurityRepository
    )

    private fun newHarness(): Harness {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = MutableClock()
        val chatRepo = ChatRepository(scope = scope)
        val securityRepo = AccountSecurityRepository(
            chatRepo = chatRepo,
            nowProvider = { clock.nowMs },
            deviceHashOverride = "dev_primary_hash_0001"
        )
        return Harness(scope, clock, chatRepo, securityRepo)
    }

    @Test
    fun pbkdf2PinHashingAndBackupCodes_neverStorePlaintextSecrets() {
        val salt = AccountSecurityCrypto.generateSaltHex(16)
        val hash = AccountSecurityCrypto.hashPinPbkdf2("4829", salt)
        assertEquals(64, hash.length)
        assertFalse(hash.contains("4829"))
        assertTrue(AccountSecurityCrypto.verifyPinPbkdf2("4829", hash, salt))
        assertFalse(AccountSecurityCrypto.verifyPinPbkdf2("9999", hash, salt))

        val codes = AccountSecurityCrypto.generateOneTimeBackupCodes(salt = "uid1:$salt", count = 6)
        assertEquals(6, codes.size)
        codes.forEach { (plain, hashed) ->
            assertTrue(plain.startsWith("RVN-"))
            assertEquals(64, hashed.length)
            assertNotEquals(plain, hashed)
            assertFalse(hashed.contains(plain))
        }
    }

    @Test
    fun anomalyDetection_newDeviceRegionShiftAndBlockedDevice() = runBlocking {
        val h = newHarness()
        try {
            // Advance clock by 2 minutes; connect second device from a different region -> triggers HIGH anomaly + alert + login history
            h.clock.nowMs += 2 * 60_000L
            val secondSessionRes = h.securityRepo.registerSignInSession(
                deviceIdHash = "dev_foreign_hash_0002",
                deviceName = "Tablet Unknown",
                platformLabel = "Android 15",
                regionLabel = "America/New_York",
                authMethod = "GOOGLE_OIDC"
            )
            assertTrue(secondSessionRes.isSuccess)
            val secondSession = secondSessionRes.getOrThrow()

            val alerts = h.securityRepo.securityAlerts.value
            assertTrue(alerts.any { it.relatedDeviceIdHash == "dev_foreign_hash_0002" && it.status == SecurityAlertStatus.OPEN })
            assertTrue(h.securityRepo.loginHistory.value.any {
                it.deviceIdHash == "dev_foreign_hash_0002" && it.outcome == LoginOutcome.SUSPICIOUS_FLAGGED
            })

            // Block that device -> active session is revoked AND future sign-in from that device is rejected
            h.securityRepo.requestConfigureSecurityPin("1357")
            val foreignDev = h.securityRepo.devices.value.first { it.deviceIdHash == "dev_foreign_hash_0002" }
            h.securityRepo.requestDeviceTrustChange(foreignDev, DeviceTrustState.BLOCKED)
            assertNotNull(h.securityRepo.pendingConfirmation.value)
            assertTrue(h.securityRepo.confirmWithSecurityPin("1357"))

            assertEquals(
                DeviceTrustState.BLOCKED,
                h.securityRepo.devices.value.first { it.deviceIdHash == "dev_foreign_hash_0002" }.trustState
            )
            assertEquals(
                SessionStatus.REVOKED,
                h.securityRepo.sessions.value.first { it.sessionId == secondSession.sessionId }.status
            )

            h.clock.nowMs += 60_000L
            val blockedAttempt = h.securityRepo.registerSignInSession(
                deviceIdHash = "dev_foreign_hash_0002",
                deviceName = "Tablet Unknown",
                regionLabel = "America/New_York"
            )
            assertTrue(blockedAttempt.isFailure)
            assertTrue(h.securityRepo.loginHistory.value.first().outcome == LoginOutcome.BLOCKED)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun globalSignOutAndSessionRevocation_incrementEpochAndInvalidateOtherSessions() = runBlocking {
        val h = newHarness()
        try {
            h.clock.nowMs += 60_000L
            val extra = h.securityRepo.registerSignInSession(
                deviceIdHash = "dev_other_hash_0003",
                deviceName = "Pixel Backup",
                regionLabel = "Europe/Paris"
            ).getOrThrow()

            val initialEpoch = h.securityRepo.securityProfile.value.sessionEpoch
            assertEquals(1L, initialEpoch)

            // Global sign out excluding current device
            h.securityRepo.performGlobalSignOutNow(includeCurrentDevice = false)

            val updatedProfile = h.securityRepo.securityProfile.value
            assertEquals(2L, updatedProfile.sessionEpoch)
            assertTrue(updatedProfile.globalSignOutAfterMs > 0L)

            val revokedOther = h.securityRepo.sessions.value.first { it.sessionId == extra.sessionId }
            assertEquals(SessionStatus.REVOKED, revokedOther.status)
            assertFalse(AccountSecurityEvaluator.isSessionValid(revokedOther, updatedProfile, h.clock.nowMs))

            val keptCurrent = h.securityRepo.sessions.value.first { it.sessionId == h.securityRepo.currentSessionId.value }
            assertEquals(SessionStatus.ACTIVE, keptCurrent.status)
            assertEquals(2L, keptCurrent.sessionEpoch)
            assertTrue(AccountSecurityEvaluator.isSessionValid(keptCurrent, updatedProfile, h.clock.nowMs))

            // Global sign out INCLUDING current device invalidates current session as well
            h.clock.nowMs += 1_000L
            h.securityRepo.performGlobalSignOutNow(includeCurrentDevice = true)
            assertEquals(3L, h.securityRepo.securityProfile.value.sessionEpoch)
            assertNotNull(h.securityRepo.remoteRevocationMessage.value)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun suspiciousBurstAndThreeFailedChallenges_triggerFreezeUntilVerified() = runBlocking {
        val h = newHarness()
        try {
            // Configure PIN first
            h.securityRepo.requestConfigureSecurityPin("2468")
            assertNotNull(h.securityRepo.securityProfile.value.pinHash)

            // 1. Trigger 3 failed PIN challenges on a sensitive action -> auto-freeze for 15 minutes
            val targetSession = h.securityRepo.registerSignInSession(
                deviceIdHash = "dev_aux_hash_0004",
                deviceName = "Desktop Web",
                regionLabel = "Europe/Paris"
            ).getOrThrow()

            h.securityRepo.requestSensitiveAction(
                PendingSensitiveConfirmation(
                    actionType = SensitiveActionType.REVOKE_SESSION,
                    targetId = targetSession.sessionId,
                    title = "Révoquer ?",
                    description = "Test"
                )
            )
            assertFalse(h.securityRepo.confirmWithSecurityPin("0000"))
            assertFalse(h.securityRepo.confirmWithSecurityPin("1111"))
            assertFalse(h.securityRepo.confirmWithSecurityPin("2222"))

            assertTrue(AccountSecurityEvaluator.isFrozen(h.securityRepo.securityProfile.value, h.clock.nowMs))
            assertTrue(h.securityRepo.validateSensitiveAccountMutation("Email").isFailure)

            // Lift freeze using valid Google re-auth confirmation
            h.securityRepo.requestLiftFreeze()
            assertNotNull(h.securityRepo.pendingConfirmation.value)
            h.securityRepo.confirmWithGoogleReauthResult(true)
            assertFalse(AccountSecurityEvaluator.isFrozen(h.securityRepo.securityProfile.value, h.clock.nowMs))

            // 2. Burst of 4 sensitive mutations within 10 minutes also triggers protective freeze
            assertTrue(h.securityRepo.validateSensitiveAccountMutation("Edit 1").isSuccess)
            assertTrue(h.securityRepo.validateSensitiveAccountMutation("Edit 2").isSuccess)
            assertTrue(h.securityRepo.validateSensitiveAccountMutation("Edit 3").isSuccess)
            assertTrue(h.securityRepo.validateSensitiveAccountMutation("Edit 4").isFailure)
            assertTrue(AccountSecurityEvaluator.isFrozen(h.securityRepo.securityProfile.value, h.clock.nowMs))
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun twoFactorPreparationAndBackupCodeRotation_storesOnlyMetaAndHashes() = runBlocking {
        val h = newHarness()
        try {
            h.securityRepo.requestPrepareTwoFactor(TwoFactorMethod.AUTHENTICATOR_TOTP, "Google Authenticator")
            // Freshness not yet verified -> prompt opens; verify via explicit confirm (no PIN set)
            assertTrue(h.securityRepo.confirmExplicitlyWhenNoPinOrCloud())

            val p1 = h.securityRepo.securityProfile.value
            assertEquals(TwoFactorMethod.AUTHENTICATOR_TOTP, p1.preferredTwoFactorMethod)
            assertEquals(1, p1.enrolledFactorsMeta.size)

            h.securityRepo.requestGenerateRecoveryCodes()
            // Still inside 5-minute verification window -> executes directly
            val codes = h.securityRepo.oneTimeBackupCodes.value
            assertEquals(6, codes.size)
            val p2 = h.securityRepo.securityProfile.value
            assertEquals(6, p2.backupCodeHashes.size)
            assertEquals(6, p2.backupCodesRemainingCount)
            codes.forEach { plain -> assertFalse(p2.backupCodeHashes.contains(plain)) }

            val twoFaLogs = h.securityRepo.securityLogs.value.filter { it.category == SecurityLogCategory.TWO_FACTOR }
            assertTrue(twoFaLogs.size >= 2)
        } finally {
            h.scope.cancel()
        }
    }
}
