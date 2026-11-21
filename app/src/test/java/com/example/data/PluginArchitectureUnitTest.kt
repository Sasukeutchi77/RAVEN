package com.example.data

import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PluginArchitectureUnitTest {

    private fun newTestContext(
        nowClock: () -> Long = { 1_700_000_000_000L },
        extraPlugins: List<RavenPlugin> = emptyList(),
        hostVersion: String = RAVEN_HOST_VERSION
    ): Triple<CoroutineScope, ChatRepository, PluginRepository> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val chatRepo = ChatRepository(scope = scope)
        val registry = RavenPluginRegistry(BuiltInRavenPlugins.all() + extraPlugins)
        val pluginRepo = PluginRepository(
            chatRepo = chatRepo,
            registry = registry,
            hostVersion = hostVersion,
            nowProvider = nowClock
        )
        pluginRepo.attachToChatRepository()
        return Triple(scope, chatRepo, pluginRepo)
    }

    @Test
    fun compatibilityChecker_blocksIncompatibleMinAndMaxHostVersions() {
        val (scope, _, pluginRepo) = newTestContext(hostVersion = "1.0.0")
        try {
            // Built-in future plugin requires minRavenVersion 2.5.0 -> MUST be INCOMPATIBLE
            val futureItem = pluginRepo.pluginItems.value.first {
                it.manifest.id == "com.raven.ext.spatial_audio_pro"
            }
            assertFalse(futureItem.compatibility.isCompatible)
            assertEquals(PluginLifecycleState.INCOMPATIBLE, futureItem.state.lifecycleState)

            val enableRes = runBlocking {
                pluginRepo.enablePlugin(
                    pluginId = "com.raven.ext.spatial_audio_pro",
                    approvedPermissions = futureItem.manifest.requestedPermissions
                )
            }
            assertTrue(enableRes.isFailure)

            // Plugin whose maxRavenVersion is lower than hostVersion is also rejected
            val maxCompat = PluginCompatibilityChecker.checkCompatibility(
                manifest = futureItem.manifest.copy(
                    id = "com.raven.ext.legacy",
                    minRavenVersion = "0.5.0",
                    maxRavenVersion = "0.9.9"
                ),
                hostVersionRaw = "1.0.0"
            )
            assertFalse(maxCompat.isCompatible)
            assertTrue(maxCompat.reason.contains("0.9.9"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun securityGuard_rejectsDclAndMaliciousCodeInManifestCommandsAndConfig() {
        val maliciousManifest = PluginManifest(
            id = "com.raven.ext.evil_loader",
            name = "Evil",
            version = "1.0.0",
            minRavenVersion = "1.0.0",
            description = "Loads payload.dex via DexClassLoader",
            author = "Attacker"
        )
        val manifestRes = PluginSecurityGuard.validateManifest(maliciousManifest)
        assertTrue(manifestRes.isFailure)
        assertTrue(manifestRes.exceptionOrNull() is SecurityException)

        val field = PluginConfigField(
            key = "keywords",
            label = "Keywords",
            description = "Test",
            type = PluginConfigFieldType.STRING,
            defaultValue = "ok"
        )
        val badConfig = PluginSecurityGuard.validateAndNormalizeConfig(
            schema = listOf(field),
            proposed = mapOf("keywords" to "javascript:eval(document.cookie)")
        )
        assertTrue(badConfig.isFailure)
        assertTrue(badConfig.exceptionOrNull() is SecurityException)

        val registry = RavenPluginRegistry(emptyList())
        val registerRes = registry.registerPlugin(object : RavenPlugin {
            override val manifest = maliciousManifest
        })
        assertTrue(registerRes.isFailure)
    }

    @Test
    fun leastPrivilege_blocksUnauthorizedApiCallsAndRedactsUserPii() = runBlocking {
        val (scope, chatRepo, pluginRepo) = newTestContext()
        try {
            // Enable Standup with ONLY EXECUTE_SLASH_COMMAND (NO READ_ACTIVE_USER_PUBLIC, NO POST_CHANNEL_MESSAGE)
            pluginRepo.enablePlugin(
                pluginId = "com.raven.ext.standup",
                approvedPermissions = setOf(PluginPermission.EXECUTE_SLASH_COMMAND)
            ).getOrThrow()

            val api = pluginRepo.getControlledApiForTesting("com.raven.ext.standup")!!

            // Unauthorized READ_ACTIVE_USER_PUBLIC must throw PluginPermissionDeniedException (SecurityException)
            var thrownUser: Throwable? = null
            try {
                api.readPublicUserSummary()
            } catch (t: Throwable) {
                thrownUser = t
            }
            assertTrue(thrownUser is PluginPermissionDeniedException)

            // Unauthorized POST_CHANNEL_MESSAGE must throw PluginPermissionDeniedException
            var thrownPost: Throwable? = null
            try {
                api.postChannelNotice("general", "Bonjour sans droit")
            } catch (t: Throwable) {
                thrownPost = t
            }
            assertTrue(thrownPost is PluginPermissionDeniedException)

            // Security logs were recorded
            assertTrue(pluginRepo.logs.value.any {
                it.pluginId == "com.raven.ext.standup" && it.level == PluginLogLevel.SECURITY
            })

            // Undeclared permission CANNOT be granted even if passed to setPermissionGranted
            val grantUndeclared = pluginRepo.setPermissionGranted(
                "com.raven.ext.standup",
                PluginPermission.READ_MESSAGES_SUMMARY,
                true
            )
            assertTrue(grantUndeclared.isFailure)

            // Now grant READ_ACTIVE_USER_PUBLIC -> returns sanitized displayName + status without PII
            chatRepo.updateUserProfile(
                name = "AliceDev",
                pronouns = "",
                bio = "",
                avatarUrl = null,
                avatarColor = 0xFF5865F2L,
                bannerUrl = null,
                bannerColor = 0xFF5865F2L,
                customStatus = "Code",
                customStatusEmoji = "💻",
                activity = null,
                email = "secret.alice@private.com",
                phone = "+33612345678"
            )
            pluginRepo.setPermissionGranted(
                "com.raven.ext.standup",
                PluginPermission.READ_ACTIVE_USER_PUBLIC,
                true
            ).getOrThrow()

            val summary = api.readPublicUserSummary()
            assertEquals("AliceDev", summary.displayName)

            // Secret/PII redaction check on post & text sanitization
            val sanitized = PluginSecurityGuard.sanitizeText(
                "Token AIzaSyDummySecretKey1234567890123 and email alice@secret.org"
            )
            assertFalse(sanitized.contains("AIzaSy"))
            assertFalse(sanitized.contains("alice@secret.org"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun lifecycleCommandsAndEvents_pomodoroKeywordRadarAndStandupWorkEndToEnd() = runBlocking {
        var now = 1_700_000_100_000L
        val (scope, chatRepo, pluginRepo) = newTestContext(nowClock = { now })
        try {
            val activeChannelId = chatRepo.selectedChannelId.value

            // Enable Pomodoro with slash command + post + notifications
            val pomodoroItem = pluginRepo.pluginItems.value.first { it.manifest.id == "com.raven.ext.pomodoro" }
            pluginRepo.enablePlugin(
                "com.raven.ext.pomodoro",
                pomodoroItem.manifest.requestedPermissions
            ).getOrThrow()

            // Update Pomodoro config
            val updatedCfg = pluginRepo.updatePluginConfig(
                "com.raven.ext.pomodoro",
                mapOf(
                    "focus_minutes" to "35",
                    "announce_in_channel" to "true",
                    "focus_theme" to "Code"
                )
            ).getOrThrow()
            assertEquals("35", updatedCfg.configValues["focus_minutes"])

            // Invalid out-of-range int config rejected
            val badRange = pluginRepo.updatePluginConfig(
                "com.raven.ext.pomodoro",
                mapOf("focus_minutes" to "999", "announce_in_channel" to "true", "focus_theme" to "Code")
            )
            assertTrue(badRange.isFailure)

            // Active slash command registered & dispatched via ChatRepository
            assertTrue(pluginRepo.activeSlashCommands().any { it.command == "/focus" })
            val beforeMsgCount = chatRepo.messages.value[activeChannelId].orEmpty().size
            val handled = chatRepo.executeSlashCommand(activeChannelId, "/focus 30")
            assertTrue(handled)
            val afterMessages = chatRepo.messages.value[activeChannelId].orEmpty()
            assertTrue(afterMessages.size > beforeMsgCount)
            assertTrue(afterMessages.last().sender.id == "ext_com.raven.ext.pomodoro")
            assertTrue(afterMessages.last().text.contains("30 min"))

            // Enable KeywordRadar -> detects configured keyword on Channel message
            val radarItem = pluginRepo.pluginItems.value.first { it.manifest.id == "com.raven.ext.keyword_radar" }
            pluginRepo.enablePlugin(
                "com.raven.ext.keyword_radar",
                radarItem.manifest.requestedPermissions
            ).getOrThrow()
            now += 30_000L
            chatRepo.sendMessage(activeChannelId, "Le tournoi démarre ce soir !", null, null, 0)
            assertTrue(pluginRepo.logs.value.any {
                it.pluginId == "com.raven.ext.keyword_radar" && it.action == "KEYWORD_HIT"
            })

            // Disabling Pomodoro removes its slash command & skips events cleanly
            pluginRepo.disablePlugin("com.raven.ext.pomodoro").getOrThrow()
            assertFalse(pluginRepo.activeSlashCommands().any { it.command == "/focus" })
            val disabledState = pluginRepo.pluginItems.value.first { it.manifest.id == "com.raven.ext.pomodoro" }.state
            assertEquals(PluginLifecycleState.DISABLED, disabledState.lifecycleState)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun faultIsolation_quarantinesMisbehavingPluginAfterThreeErrorsWithoutAffectingOthers() = runBlocking {
        val traces = mutableListOf<String>()
        val buggyPlugin = object : RavenPlugin {
            override val manifest = PluginManifest(
                id = "com.raven.ext.buggy_probe",
                name = "Buggy Probe",
                version = "1.0.0",
                minRavenVersion = "1.0.0",
                description = "Plugin de test qui lève une exception à chaque événement.",
                author = "QA",
                trustTier = PluginTrustTier.LOCAL_DEVELOPER,
                requestedPermissions = setOf(PluginPermission.LISTEN_APP_EVENTS, PluginPermission.EXECUTE_SLASH_COMMAND),
                subscribedEvents = setOf(PluginEventType.CHANNEL_CHANGED)
            )
            override fun onLoad(context: PluginContext) { traces.add("onLoad") }
            override fun onEnable(api: RavenPluginApi) { traces.add("onEnable") }
            override fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {
                throw IllegalStateException("Crash simulé du plugin")
            }
            override fun onDisable() { traces.add("onDisable") }
        }

        val healthyPlugin = object : RavenPlugin {
            var eventsReceived = 0
            override val manifest = PluginManifest(
                id = "com.raven.ext.healthy_probe",
                name = "Healthy Probe",
                version = "1.0.0",
                minRavenVersion = "1.0.0",
                description = "Plugin sain pour vérifier l'isolation.",
                author = "QA",
                trustTier = PluginTrustTier.LOCAL_DEVELOPER,
                requestedPermissions = setOf(PluginPermission.LISTEN_APP_EVENTS),
                subscribedEvents = setOf(PluginEventType.CHANNEL_CHANGED)
            )
            override fun onEvent(event: RavenPluginEvent, api: RavenPluginApi) {
                eventsReceived += 1
            }
        }

        val (scope, _, pluginRepo) = newTestContext(extraPlugins = listOf(buggyPlugin, healthyPlugin))
        try {
            pluginRepo.enablePlugin("com.raven.ext.buggy_probe", buggyPlugin.manifest.requestedPermissions).getOrThrow()
            pluginRepo.enablePlugin("com.raven.ext.healthy_probe", healthyPlugin.manifest.requestedPermissions).getOrThrow()

            // Dispatch 3 events -> buggy plugin crashes 3 times -> automatic FAULTED quarantine, healthy plugin unaffected
            repeat(3) { idx ->
                pluginRepo.dispatchEvent(
                    RavenPluginEvent.ChannelChanged(
                        serverId = "s1",
                        channelId = "c_$idx",
                        channelName = "salon-$idx",
                        channelType = "TEXT",
                        timestampMs = 1_700_000_000_000L + idx
                    )
                )
            }

            val buggyState = pluginRepo.pluginItems.value.first { it.manifest.id == "com.raven.ext.buggy_probe" }.state
            assertEquals(PluginLifecycleState.FAULTED, buggyState.lifecycleState)
            assertFalse(buggyState.isEnabled)
            assertEquals(3, buggyState.consecutiveErrors)
            assertTrue(traces.containsAll(listOf("onLoad", "onEnable", "onDisable")))
            assertEquals(3, healthyPlugin.eventsReceived)

            // Subsequent events are NOT dispatched to the quarantined plugin
            pluginRepo.dispatchEvent(
                RavenPluginEvent.ChannelChanged("s1", "c_4", "salon-4", "TEXT", 1_700_000_000_004L)
            )
            val afterState = pluginRepo.pluginItems.value.first { it.manifest.id == "com.raven.ext.buggy_probe" }.state
            assertEquals(3, afterState.consecutiveErrors)
            assertEquals(4, healthyPlugin.eventsReceived)

            // Manual recovery resets consecutive errors back to DISABLED
            val resetState = pluginRepo.resetFaultedPlugin("com.raven.ext.buggy_probe").getOrThrow()
            assertEquals(PluginLifecycleState.DISABLED, resetState.lifecycleState)
            assertEquals(0, resetState.consecutiveErrors)
            assertNull(resetState.lastErrorMessage)
        } finally {
            scope.cancel()
        }
    }
}
