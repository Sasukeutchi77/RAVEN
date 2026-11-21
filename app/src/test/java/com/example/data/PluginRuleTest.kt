package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.PluginLifecycleState
import com.example.model.PluginLogEntry
import com.example.model.PluginLogLevel
import com.example.model.PluginPermission
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PluginRuleTest : FirestoreEmulatorTestBase() {

    private companion object {
        const val ALICE_EMAIL = "alice_plugin_rule@test.raven.local"
        const val BOB_EMAIL = "bob_plugin_rule@test.raven.local"
        const val TIMEOUT_MS = 10_000L
    }

    private fun newPluginRepo(scope: CoroutineScope): PluginRepository {
        val chatRepo = ChatRepository(
            scope = scope,
            databaseId = databaseId,
            injectedFirestore = firestore,
            injectedAuth = auth
        )
        return PluginRepository(chatRepo = chatRepo)
    }

    @Test
    fun pluginsPersistence_enforcesOwnerIsolationLeastPrivilegeAndImmutableLogs() {
        runBlocking {
            val aliceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val alicePlugins = newPluginRepo(aliceScope)
            try {
                val aliceUid = signInTestUser(ALICE_EMAIL)

                // 1. Alice enables Standup plugin -> state & log persisted under /users/{aliceUid}/plugins & /plugin_logs
                val enabledState = alicePlugins.enablePlugin(
                    pluginId = "com.raven.ext.standup",
                    approvedPermissions = setOf(
                        PluginPermission.EXECUTE_SLASH_COMMAND,
                        PluginPermission.POST_CHANNEL_MESSAGE
                    )
                ).getOrThrow()
                assertEquals(PluginLifecycleState.ACTIVE, enabledState.lifecycleState)
                assertTrue(enabledState.isEnabled)

                val savedSnap = withTimeout(TIMEOUT_MS) {
                    firestore.collection("users").document(aliceUid)
                        .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGINS)
                        .document("com.raven.ext.standup")
                        .get()
                        .await()
                }
                assertTrue(savedSnap.exists())
                assertEquals("ACTIVE", savedSnap.getString("lifecycleState"))
                assertEquals(true, savedSnap.getBoolean("isEnabled"))

                // 2. Alice writes an immutable security log
                val secLog = PluginLogEntry(
                    id = "plog_sec_rule_1",
                    pluginId = "com.raven.ext.standup",
                    pluginName = "Assistant Daily Standup",
                    level = PluginLogLevel.SECURITY,
                    action = "PERMISSION_CHECK",
                    message = "Accès restreint vérifié",
                    timestampMs = System.currentTimeMillis(),
                    timestampLabel = "12:00:00"
                )
                withTimeout(TIMEOUT_MS) {
                    firestore.collection("users").document(aliceUid)
                        .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
                        .document(secLog.id)
                        .set(PluginFirestoreSync.logToMap(secLog))
                        .await()
                }

                // Updating an existing log MUST be denied (immutable audit log)
                try {
                    withTimeout(TIMEOUT_MS) {
                        firestore.collection("users").document(aliceUid)
                            .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
                            .document(secLog.id)
                            .update("level", "INFO")
                            .await()
                    }
                    fail("Updating a plugin audit log should fail under Firestore rules")
                } catch (e: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
                }

                // 3. Cross-user isolation: Bob signs in and CANNOT read or tamper with Alice's plugins or logs
                signInTestUser(BOB_EMAIL)
                try {
                    withTimeout(TIMEOUT_MS) {
                        firestore.collection("users").document(aliceUid)
                            .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGINS)
                            .document("com.raven.ext.standup")
                            .get()
                            .await()
                    }
                    fail("Bob should not be able to read Alice's plugins")
                } catch (e: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
                }

                try {
                    withTimeout(TIMEOUT_MS) {
                        firestore.collection("users").document(aliceUid)
                            .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
                            .document(secLog.id)
                            .get()
                            .await()
                    }
                    fail("Bob should not be able to read Alice's plugin logs")
                } catch (e: FirebaseFirestoreException) {
                    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
                }
            } finally {
                aliceScope.cancel()
            }
        }
    }
}
