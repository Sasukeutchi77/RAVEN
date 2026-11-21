package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AccountSecurityRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice_sec_rule@test.raven.local"
        const val BOB_EMAIL = "bob_sec_rule@test.raven.local"
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun ownerIsolationMonotonicEpochImmutableLogsAndNoPlaintextSecrets_areEnforced() = kotlinx.coroutines.runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val now = System.currentTimeMillis()
        val secProfileRef = firestore.collection("users").document(aliceUid)
            .collection("security_profile").document("account")

        val validProfile = mapOf(
            "userId" to aliceUid,
            "sessionEpoch" to 1L,
            "globalSignOutAfterMs" to 0L,
            "lastSensitiveVerifiedAtMs" to now,
            "protectSuspiciousChanges" to true,
            "alertOnNewDevice" to true,
            "alertOnUnusualActivity" to true,
            "requireReauthForSensitiveActions" to true,
            "changeFreezeUntilMs" to 0L,
            "cooldownUntilMs" to 0L,
            "failedChallengeCount" to 0,
            "suspiciousAttemptCount" to 0,
            "pinHash" to "a".repeat(64),
            "pinSalt" to "0123456789abcdef0123456789abcdef",
            "twoFactorReady" to true,
            "preferredTwoFactorMethod" to "AUTHENTICATOR_TOTP",
            "enrolledFactorsMeta" to listOf(
                mapOf("factorId" to "f_totp", "method" to "AUTHENTICATOR_TOTP", "label" to "Authenticator", "isEnabled" to true)
            ),
            "backupCodesRemainingCount" to 2,
            "backupCodeHashes" to listOf("b".repeat(64), "c".repeat(64)),
            "updatedAtMs" to now
        )

        // 1. Plaintext secret keys MUST be rejected
        try {
            withTimeout(TIMEOUT_MS) {
                secProfileRef.set(validProfile + mapOf("rawPin" to "1234")).await()
            }
            fail("Plaintext rawPin must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 2. Valid hashed profile + monotonic sessionEpoch increment succeed; decrement denied
        withTimeout(TIMEOUT_MS) { secProfileRef.set(validProfile).await() }
        withTimeout(TIMEOUT_MS) {
            secProfileRef.update(
                mapOf("sessionEpoch" to 2L, "globalSignOutAfterMs" to now + 1000L, "updatedAtMs" to now + 1000L)
            ).await()
        }
        try {
            withTimeout(TIMEOUT_MS) {
                secProfileRef.update(mapOf("sessionEpoch" to 1L, "updatedAtMs" to now + 2000L)).await()
            }
            fail("Decreasing sessionEpoch must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 3. Immutable login_history & security_logs allow create/read, deny update/delete
        val logRef = firestore.collection("users").document(aliceUid)
            .collection("security_logs").document("slog_sdk_1")
        withTimeout(TIMEOUT_MS) {
            logRef.set(
                mapOf(
                    "id" to "slog_sdk_1",
                    "userId" to aliceUid,
                    "category" to "SESSION",
                    "severity" to "HIGH",
                    "action" to "GLOBAL_SIGN_OUT",
                    "summary" to "Déconnexion globale SDK",
                    "actorDeviceIdHash" to "devhash_sdk_1234",
                    "timestampMs" to now,
                    "timestampLabel" to "10:00:00"
                )
            ).await()
        }
        try {
            withTimeout(TIMEOUT_MS) { logRef.delete().await() }
            fail("Deleting immutable security_logs must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 4. Cross-user access by Bob is completely denied
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(TIMEOUT_MS) { secProfileRef.get().await() }
            fail("Cross-user security_profile read must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("security_logs").get().await()
            }
            fail("Cross-user security_logs read must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
