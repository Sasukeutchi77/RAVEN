package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class GlobalSearchRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice_search_rule@test.raven.local"
        const val BOB_EMAIL = "bob_search_rule@test.raven.local"
        const val TIMEOUT_MS = 10_000L
    }

    @Test
    fun searchHistoryOwnerIsolationAndScopedCatalogQueries_areEnforcedByRules() = kotlinx.coroutines.runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val now = System.currentTimeMillis()

        // 1. Alice saves a valid search history entry and reads it back
        val historyId = "sh_rule_1"
        withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("search_history").document(historyId)
                .set(
                    mapOf(
                        "id" to historyId,
                        "userId" to aliceUid,
                        "query" to "Kotlin Compose",
                        "selectedTypeName" to "MESSAGE",
                        "authorFilter" to "",
                        "timestampMs" to now
                    )
                ).await()
        }

        val aliceSnap = withTimeout(TIMEOUT_MS) {
            firestore.collection("users").document(aliceUid)
                .collection("search_history").get().await()
        }
        assertEquals(1, aliceSnap.size())

        // Seed public vs private servers owned by Alice
        withTimeout(TIMEOUT_MS) {
            firestore.collection("servers").document("srv_search_pub").set(
                mapOf(
                    "name" to "Serveur Public SDK",
                    "ownerId" to aliceUid,
                    "memberIds" to listOf(aliceUid),
                    "isPublic" to true
                )
            ).await()
            firestore.collection("servers").document("srv_search_priv").set(
                mapOf(
                    "name" to "Serveur Privé SDK",
                    "ownerId" to aliceUid,
                    "memberIds" to listOf(aliceUid),
                    "isPublic" to false
                )
            ).await()
        }

        // 2. Bob signs in: CANNOT read or list Alice's search_history
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("search_history").get().await()
            }
            fail("Cross-user search_history read must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 3. Bob CANNOT delete Alice's search_history entry
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("search_history").document(historyId).delete().await()
            }
            fail("Cross-user search_history delete must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }

        // 4. Bob CAN query public servers with whereEqualTo("isPublic", true), which excludes private server
        val pubServers = withTimeout(TIMEOUT_MS) {
            firestore.collection("servers")
                .whereEqualTo("isPublic", true)
                .limit(20)
                .get().await()
        }
        assertTrue(pubServers.documents.any { it.id == "srv_search_pub" })
        assertFalse(pubServers.documents.any { it.id == "srv_search_priv" })

        // 5. Bob CANNOT run unfiltered query on /servers (must be scoped to public or memberIds)
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("servers").get().await()
            }
            fail("Unfiltered collection scan on /servers must be denied")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
