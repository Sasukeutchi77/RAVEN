package com.example.data

import com.example.model.UserStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthGateUnitTest {

    @Test
    fun `gmail validation accepts gmail and googlemail only`() {
        assertTrue(AuthGateRepository.isValidGmailAddress("alice.raven@gmail.com"))
        assertTrue(AuthGateRepository.isValidGmailAddress("  BOB_42@GMAIL.COM  "))
        assertTrue(AuthGateRepository.isValidGmailAddress("charlie@googlemail.com"))

        assertFalse(AuthGateRepository.isValidGmailAddress(null))
        assertFalse(AuthGateRepository.isValidGmailAddress(""))
        assertFalse(AuthGateRepository.isValidGmailAddress("alice@yahoo.com"))
        assertFalse(AuthGateRepository.isValidGmailAddress("bob@outlook.fr"))
        assertFalse(AuthGateRepository.isValidGmailAddress("not-an-email"))
        assertFalse(AuthGateRepository.isValidGmailAddress("@gmail.com"))
    }

    @Test
    fun `access code validation and strength meter evaluate digits and weak sequences`() {
        assertTrue(AuthGateRepository.isValidAccessCode("1234"))
        assertTrue(AuthGateRepository.isValidAccessCode("009988"))
        assertTrue(AuthGateRepository.isValidAccessCode("12345678"))

        assertFalse(AuthGateRepository.isValidAccessCode("123"))
        assertFalse(AuthGateRepository.isValidAccessCode("123456789"))
        assertFalse(AuthGateRepository.isValidAccessCode("12ab"))
        assertFalse(AuthGateRepository.isValidAccessCode(""))

        assertEquals(AccessCodeStrength.TOO_SHORT, AuthGateRepository.evaluateAccessCodeStrength("12"))
        assertEquals(AccessCodeStrength.WEAK, AuthGateRepository.evaluateAccessCodeStrength("1111"))
        assertEquals(AccessCodeStrength.WEAK, AuthGateRepository.evaluateAccessCodeStrength("1234"))
        assertEquals(AccessCodeStrength.WEAK, AuthGateRepository.evaluateAccessCodeStrength("654321"))
        assertEquals(AccessCodeStrength.MEDIUM, AuthGateRepository.evaluateAccessCodeStrength("4829"))
        assertEquals(AccessCodeStrength.STRONG, AuthGateRepository.evaluateAccessCodeStrength("839204"))
    }

    @Test
    fun `verifyAccessCode checks pbkdf2 pin enforces 5 attempt lockout expires cleanly and supports reset`() = runTest {
        var nowMs = 1_700_000_000_000L
        val testScope = TestScope(testScheduler)
        val chatRepo = ChatRepository(testScope)
        val authGate = AuthGateRepository(chatRepo, nowProvider = { nowMs })

        authGate.seedResolvedPinForTest(
            uid = "u_alice",
            email = "alice@gmail.com",
            pin = "2468"
        )
        assertEquals(AuthOnboardingPhase.VERIFY_CODE, authGate.state.value.phase)

        // Wrong attempts 1..4 increment failure counter and shake nonce
        repeat(4) { attempt ->
            assertFalse(authGate.verifyAccessCode("0000"))
            assertEquals(attempt + 1, authGate.state.value.failedCodeAttempts)
            assertEquals(attempt + 1, authGate.state.value.lastCodeFailedNonce)
            assertEquals(AuthOnboardingPhase.VERIFY_CODE, authGate.state.value.phase)
        }

        // 5th wrong attempt triggers 30s lockout
        assertFalse(authGate.verifyAccessCode("9999"))
        assertEquals(5, authGate.state.value.failedCodeAttempts)
        assertEquals(nowMs + 30_000L, authGate.state.value.cooldownUntilMs)

        // Even correct code is blocked during active cooldown
        assertFalse(authGate.verifyAccessCode("2468"))
        assertTrue(authGate.state.value.errorMessage.orEmpty().contains("Réessayez"))

        // Advance past cooldown -> clearLockoutIfExpired clears cooldown and correct code unlocks READY
        nowMs += 31_000L
        authGate.clearLockoutIfExpired()
        assertEquals(0L, authGate.state.value.cooldownUntilMs)
        assertEquals(0, authGate.state.value.failedCodeAttempts)

        assertTrue(authGate.verifyAccessCode("2468"))
        assertEquals(AuthOnboardingPhase.READY, authGate.state.value.phase)
        assertEquals(0, authGate.state.value.failedCodeAttempts)
        assertNull(authGate.state.value.errorMessage)

        // Reset code flow after Gmail reauth
        authGate.enterCodeResetAfterGmailReauth()
        assertEquals(AuthOnboardingPhase.RESET_CODE, authGate.state.value.phase)
        val mismatchRes = authGate.applyResetAccessCode("5566", "5577")
        assertTrue(mismatchRes.isFailure)

        val resetRes = authGate.applyResetAccessCode("5566", "5566")
        assertTrue(resetRes.isSuccess)
        assertEquals(AuthOnboardingPhase.READY, authGate.state.value.phase)
    }

    @Test
    fun `completeAccountCreation remembers gmail account applies launch lock preference and handles signOut`() = runTest {
        val ctx = RuntimeEnvironment.getApplication()
        val testScope = TestScope(testScheduler)
        val chatRepo = ChatRepository(testScope, appContext = ctx)
        val authGate = AuthGateRepository(chatRepo)

        // Missing signedInUid fails cleanly
        val noUidRes = authGate.completeAccountCreation(
            name = "Alex",
            tag = "#1234",
            bio = "Salut",
            avatarUrl = null,
            avatarColor = 0xFF5865F2L,
            status = UserStatus.ONLINE,
            accessCode = "1234",
            confirmCode = "1234"
        )
        assertTrue(noUidRes.isFailure)

        // Seed Gmail identity as if CredentialManager signed in
        authGate.seedResolvedPinForTest("u_new", "alex.raven@gmail.com", "1111")
        val shortNameRes = authGate.completeAccountCreation(
            name = "A",
            tag = "#2026",
            bio = "Bio",
            avatarUrl = "https://example.com/avatar.jpg",
            avatarColor = 0xFF10B981L,
            status = UserStatus.DND,
            accessCode = "4321",
            confirmCode = "4321"
        )
        assertTrue(shortNameRes.isFailure)

        val badCodeRes = authGate.completeAccountCreation(
            name = "Alex Raven",
            tag = "#2026",
            bio = "Bio",
            avatarUrl = "https://example.com/avatar.jpg",
            avatarColor = 0xFF10B981L,
            status = UserStatus.DND,
            accessCode = "12",
            confirmCode = "12"
        )
        assertTrue(badCodeRes.isFailure)

        val okRes = authGate.completeAccountCreation(
            name = "Alex Raven",
            tag = "2026",
            bio = "Créateur sur Raven",
            avatarUrl = "https://example.com/avatar.jpg",
            avatarColor = 0xFF10B981L,
            status = UserStatus.DND,
            accessCode = "839204",
            confirmCode = "839204",
            requireCodeOnAppLaunch = true
        )
        assertTrue(okRes.isSuccess)
        assertEquals(AuthOnboardingPhase.READY, authGate.state.value.phase)
        assertTrue(chatRepo.onboardingCompleted.value)
        assertTrue(chatRepo.appSettings.value.requireCodeOnAppLaunch)
        assertEquals("Alex Raven", chatRepo.currentUser.value.name)
        assertEquals("#2026", chatRepo.currentUser.value.tag)
        assertEquals("https://example.com/avatar.jpg", chatRepo.currentUser.value.avatarUrl)
        assertEquals(UserStatus.DND, chatRepo.currentUser.value.status)
        assertNotNull(chatRepo.currentUser.value.email)
        assertTrue(authGate.state.value.rememberedAccounts.any { it.uid == "u_new" && it.email == "alex.raven@gmail.com" })

        // Sign out retains remembered account list and sets skipSilentAutoSignIn = true
        authGate.signOut()
        assertEquals(AuthOnboardingPhase.SIGN_IN_GMAIL, authGate.state.value.phase)
        assertFalse(chatRepo.onboardingCompleted.value)
        assertTrue(authGate.state.value.skipSilentAutoSignIn)
        assertEquals(1, authGate.state.value.rememberedAccounts.size)

        // Selecting remembered account switches intent to SIGN_IN cleanly
        authGate.selectAuthIntent(AuthEntryIntent.CREATE_ACCOUNT)
        assertEquals(AuthEntryIntent.CREATE_ACCOUNT, authGate.state.value.authIntent)

        val savedAcc = authGate.state.value.rememberedAccounts.first()
        authGate.selectRememberedAccount(savedAcc)
        assertEquals(AuthEntryIntent.SIGN_IN, authGate.state.value.authIntent)
        assertEquals("alex.raven@gmail.com", authGate.state.value.gmailInput)

        // Existing account resolved while user selected CREATE_ACCOUNT switches cleanly to VERIFY_CODE (or READY with valid prefill)
        authGate.selectAuthIntent(AuthEntryIntent.CREATE_ACCOUNT)
        authGate.resolveAccountState(
            uid = "u_new",
            email = "alex.raven@gmail.com",
            displayName = "Alex Raven",
            photoUrl = null,
            isInteractiveSignIn = true,
            prefilledCode = ""
        )
        assertEquals(AuthOnboardingPhase.VERIFY_CODE, authGate.state.value.phase)
        assertEquals(AuthEntryIntent.SIGN_IN, authGate.state.value.authIntent)
        assertTrue(authGate.state.value.infoMessage.orEmpty().contains("existe déjà"))

        // Existing account with valid prefilled code enters READY directly on sign-in
        authGate.resolveAccountState(
            uid = "u_new",
            email = "alex.raven@gmail.com",
            displayName = "Alex Raven",
            photoUrl = null,
            isInteractiveSignIn = true,
            prefilledCode = "839204"
        )
        assertEquals(AuthOnboardingPhase.READY, authGate.state.value.phase)

        // Brand-new account resolved while user selected SIGN_IN routes cleanly to CREATE_PROFILE
        authGate.selectAuthIntent(AuthEntryIntent.SIGN_IN)
        authGate.resolveAccountState(
            uid = "u_brand_new",
            email = "nouveau@gmail.com",
            displayName = "Nouveau Membre",
            photoUrl = null,
            isInteractiveSignIn = true,
            prefilledCode = ""
        )
        assertEquals(AuthOnboardingPhase.CREATE_PROFILE, authGate.state.value.phase)
        assertEquals(AuthEntryIntent.CREATE_ACCOUNT, authGate.state.value.authIntent)

        authGate.removeRememberedAccount("u_new")
        assertTrue(authGate.state.value.rememberedAccounts.isEmpty())
    }
}

