package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Central host coordinator for RAVEN Extensions / Plugins:
 * - Evaluates SemVer compatibility against [hostVersion]
 * - Enforces least-privilege per-plugin permissions & rate-limited [ScopedPluginApi]
 * - Executes plugin lifecycle hooks inside error-isolating quarantine guards
 * - Dispatches typed [RavenPluginEvent] signals and slash commands
 * - Persists user-scoped plugin states, config, and logs in Firestore (/users/{uid}/plugins, /users/{uid}/plugin_logs)
 */
class PluginRepository(
    internal val chatRepo: ChatRepository,
    val registry: RavenPluginRegistry = RavenPluginRegistry(),
    val hostVersion: String = RAVEN_HOST_VERSION,
    internal val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val MAX_CONSECUTIVE_ERRORS = 3
        private const val MAX_IN_MEMORY_LOGS = 150
    }

    private val _pluginItems = MutableStateFlow<List<PluginUiItem>>(emptyList())
    val pluginItems: StateFlow<List<PluginUiItem>> = _pluginItems.asStateFlow()

    private val _logs = MutableStateFlow<List<PluginLogEntry>>(emptyList())
    val logs: StateFlow<List<PluginLogEntry>> = _logs.asStateFlow()

    private val statesByPluginId = linkedMapOf<String, UserPluginState>()
    private val loadedPluginIds = mutableSetOf<String>()
    private val apiCacheByPluginId = mutableMapOf<String, ScopedPluginApi>()

    private var statesListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null
    private var activeListeningUid: String? = null

    init {
        ensureDefaultStatesForRegistry()
        rebuildUiItems()
    }

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    fun attachToChatRepository() {
        chatRepo.pluginCommandHandler = { targetId, rawInput ->
            handleSlashCommand(targetId, rawInput)
        }
        chatRepo.pluginMessageHandler = { serverId, channelId, message ->
            if (!message.sender.id.startsWith("ext_") && !message.sender.id.startsWith("bot_")) {
                dispatchEvent(
                    RavenPluginEvent.MessagePosted(
                        serverId = serverId,
                        channelId = channelId,
                        authorDisplayName = PluginSecurityGuard.sanitizeText(message.sender.name, 40),
                        sanitizedPreview = PluginSecurityGuard.sanitizeText(message.text, 220),
                        timestampMs = nowProvider()
                    )
                )
            }
        }
        chatRepo.pluginEventHandler = { event ->
            dispatchEvent(event)
        }
    }

    fun startListening(uid: String) {
        if (uid.isBlank() || activeListeningUid == uid) return
        stopListening()
        activeListeningUid = uid

        val db = chatRepo.firestoreDb() ?: return
        val userRef = db.collection("users").document(uid)

        statesListener = userRef.collection(PluginFirestoreSync.SUBCOLLECTION_PLUGINS)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val prevEnabled = statesByPluginId.filterValues { it.isEnabled }.keys.toSet()
                snap.documents.forEach { doc ->
                    PluginFirestoreSync.mapDocToState(doc)?.let { remoteState ->
                        val plugin = registry.findPlugin(remoteState.pluginId)
                        if (plugin != null) {
                            val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)
                            val clampedState = remoteState.copy(
                                grantedPermissions = PluginPermissionMatrix.grantOnlyDeclared(
                                    plugin.manifest,
                                    remoteState.grantedPermissions
                                ),
                                lifecycleState = if (!compat.isCompatible) {
                                    PluginLifecycleState.INCOMPATIBLE
                                } else {
                                    remoteState.lifecycleState
                                },
                                isEnabled = compat.isCompatible && remoteState.isEnabled &&
                                    remoteState.lifecycleState == PluginLifecycleState.ACTIVE
                            )
                            statesByPluginId[remoteState.pluginId] = clampedState
                            if (clampedState.isEnabled && clampedState.pluginId !in prevEnabled) {
                                runGuarded(plugin, "LIFECYCLE_REMOTE_ENABLE") { api ->
                                    if (plugin.manifest.id !in loadedPluginIds) {
                                        plugin.onLoad(PluginContext(plugin.manifest, hostVersion, clampedState.configValues))
                                        loadedPluginIds.add(plugin.manifest.id)
                                    }
                                    plugin.onEnable(api)
                                }
                            }
                        }
                    }
                }
                rebuildUiItems()
            }

        logsListener = userRef.collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val parsed = snap.documents.mapNotNull { PluginFirestoreSync.mapDocToLog(it) }
                    .sortedByDescending { it.timestampMs }
                    .take(MAX_IN_MEMORY_LOGS)
                if (parsed.isNotEmpty()) {
                    _logs.value = parsed
                }
            }
    }

    fun stopListening() {
        statesListener?.remove()
        statesListener = null
        logsListener?.remove()
        logsListener = null
        activeListeningUid = null
    }

    /**
     * Registers a verified [RavenPlugin] module safely. Rejects invalid manifests or DCL attempts.
     */
    fun registerPlugin(plugin: RavenPlugin): Result<Unit> {
        val regRes = registry.registerPlugin(plugin)
        if (regRes.isFailure) return regRes
        ensureDefaultStateFor(plugin)
        rebuildUiItems()
        recordLog(
            pluginId = plugin.manifest.id,
            pluginName = plugin.manifest.name,
            level = PluginLogLevel.INFO,
            action = "REGISTER",
            message = "Plugin v${plugin.manifest.version} enregistré (min RAVEN v${plugin.manifest.minRavenVersion})."
        )
        return Result.success(Unit)
    }

    fun unregisterPlugin(pluginId: String): Boolean {
        val existing = registry.findPlugin(pluginId) ?: return false
        if (statesByPluginId[pluginId]?.lifecycleState == PluginLifecycleState.ACTIVE) {
            runCatching { existing.onDisable() }
        }
        if (pluginId in loadedPluginIds) {
            runCatching { existing.onUnload() }
            loadedPluginIds.remove(pluginId)
        }
        apiCacheByPluginId.remove(pluginId)
        statesByPluginId.remove(pluginId)
        registry.unregisterPlugin(pluginId)
        rebuildUiItems()
        return true
    }

    suspend fun enablePlugin(
        pluginId: String,
        approvedPermissions: Set<PluginPermission>
    ): Result<UserPluginState> {
        val plugin = registry.findPlugin(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin '$pluginId' introuvable."))

        PluginSecurityGuard.validateManifest(plugin.manifest).onFailure { return Result.failure(it) }

        val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)
        if (!compat.isCompatible) {
            val blocked = ensureDefaultStateFor(plugin).copy(
                isEnabled = false,
                lifecycleState = PluginLifecycleState.INCOMPATIBLE,
                updatedAtMs = nowProvider()
            )
            statesByPluginId[pluginId] = blocked
            rebuildUiItems()
            recordLog(pluginId, plugin.manifest.name, PluginLogLevel.WARN, "COMPATIBILITY_BLOCKED", compat.reason)
            return Result.failure(IllegalStateException(compat.reason))
        }

        val current = ensureDefaultStateFor(plugin)
        if (current.lifecycleState == PluginLifecycleState.FAULTED) {
            return Result.failure(
                IllegalStateException("Extension en quarantaine suite à des erreurs répétées. Réinitialisez-la d'abord.")
            )
        }

        // Strict least-privilege: ONLY grant permissions explicitly declared AND approved by user
        val granted = PluginPermissionMatrix.grantOnlyDeclared(plugin.manifest, approvedPermissions)
        val denied = plugin.manifest.requestedPermissions - granted
        val nextState = current.copy(
            installedVersion = plugin.manifest.version,
            isEnabled = true,
            lifecycleState = PluginLifecycleState.ACTIVE,
            grantedPermissions = granted,
            deniedPermissions = denied,
            consecutiveErrors = 0,
            lastErrorMessage = null,
            updatedAtMs = nowProvider()
        )
        statesByPluginId[pluginId] = nextState

        val api = getOrCreateScopedApi(plugin)
        val ok = runGuarded(plugin, "LIFECYCLE_ENABLE") {
            if (pluginId !in loadedPluginIds) {
                plugin.onLoad(PluginContext(plugin.manifest, hostVersion, nextState.configValues))
                loadedPluginIds.add(pluginId)
            }
            plugin.onEnable(api)
        }
        if (!ok) {
            val failedState = statesByPluginId[pluginId] ?: nextState
            persistPluginState(failedState)
            rebuildUiItems()
            return Result.failure(
                IllegalStateException(failedState.lastErrorMessage ?: "Échec lors de l'activation du plugin.")
            )
        }

        persistPluginState(nextState)
        rebuildUiItems()
        recordLog(
            pluginId = pluginId,
            pluginName = plugin.manifest.name,
            level = PluginLogLevel.INFO,
            action = "ENABLE",
            message = "Activé avec ${granted.size}/${plugin.manifest.requestedPermissions.size} permission(s)."
        )
        return Result.success(nextState)
    }

    suspend fun disablePlugin(
        pluginId: String,
        reason: String = "Désactivé par l'utilisateur"
    ): Result<UserPluginState> {
        val plugin = registry.findPlugin(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin '$pluginId' introuvable."))
        val current = ensureDefaultStateFor(plugin)
        val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)

        runCatching { plugin.onDisable() }

        val next = current.copy(
            isEnabled = false,
            lifecycleState = if (compat.isCompatible) PluginLifecycleState.DISABLED else PluginLifecycleState.INCOMPATIBLE,
            updatedAtMs = nowProvider()
        )
        statesByPluginId[pluginId] = next
        persistPluginState(next)
        rebuildUiItems()
        recordLog(pluginId, plugin.manifest.name, PluginLogLevel.INFO, "DISABLE", reason)
        return Result.success(next)
    }

    suspend fun resetFaultedPlugin(pluginId: String): Result<UserPluginState> {
        val plugin = registry.findPlugin(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin '$pluginId' introuvable."))
        val current = ensureDefaultStateFor(plugin)
        val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)

        val next = current.copy(
            isEnabled = false,
            lifecycleState = if (compat.isCompatible) PluginLifecycleState.DISABLED else PluginLifecycleState.INCOMPATIBLE,
            consecutiveErrors = 0,
            lastErrorMessage = null,
            updatedAtMs = nowProvider()
        )
        statesByPluginId[pluginId] = next
        persistPluginState(next)
        rebuildUiItems()
        recordLog(pluginId, plugin.manifest.name, PluginLogLevel.INFO, "QUARANTINE_RESET", "Compteur d'erreurs réinitialisé.")
        return Result.success(next)
    }

    suspend fun setPermissionGranted(
        pluginId: String,
        permission: PluginPermission,
        granted: Boolean
    ): Result<UserPluginState> {
        val plugin = registry.findPlugin(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin '$pluginId' introuvable."))
        if (permission !in plugin.manifest.requestedPermissions) {
            return Result.failure(
                SecurityException("Le plugin '$pluginId' n'a pas déclaré la permission '${permission.name}' dans son manifeste.")
            )
        }
        val current = ensureDefaultStateFor(plugin)
        val nextGranted = if (granted) current.grantedPermissions + permission else current.grantedPermissions - permission
        val nextDenied = if (granted) current.deniedPermissions - permission else current.deniedPermissions + permission
        val next = current.copy(
            grantedPermissions = nextGranted,
            deniedPermissions = nextDenied,
            updatedAtMs = nowProvider()
        )
        statesByPluginId[pluginId] = next
        persistPluginState(next)
        rebuildUiItems()
        recordLog(
            pluginId = pluginId,
            pluginName = plugin.manifest.name,
            level = PluginLogLevel.SECURITY,
            action = if (granted) "PERM_GRANTED" else "PERM_REVOKED",
            message = "${permission.name} ${if (granted) "accordée" else "révoquée"} par l'utilisateur."
        )
        return Result.success(next)
    }

    suspend fun updatePluginConfig(
        pluginId: String,
        proposedConfig: Map<String, String>
    ): Result<UserPluginState> {
        val plugin = registry.findPlugin(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin '$pluginId' introuvable."))
        val validated = PluginSecurityGuard.validateAndNormalizeConfig(plugin.manifest.configSchema, proposedConfig)
            .getOrElse { return Result.failure(it) }

        val current = ensureDefaultStateFor(plugin)
        val next = current.copy(
            configValues = validated,
            updatedAtMs = nowProvider()
        )
        statesByPluginId[pluginId] = next

        if (next.lifecycleState == PluginLifecycleState.ACTIVE) {
            runGuarded(plugin, "CONFIG_UPDATE") { api ->
                plugin.onConfigUpdated(validated, api)
            }
        }
        persistPluginState(next)
        rebuildUiItems()
        recordLog(pluginId, plugin.manifest.name, PluginLogLevel.INFO, "CONFIG_SAVED", "Configuration mise à jour.")
        return Result.success(next)
    }

    fun dispatchEvent(event: RavenPluginEvent) {
        registry.allPlugins().forEach { plugin ->
            val state = statesByPluginId[plugin.manifest.id] ?: return@forEach
            if (state.lifecycleState != PluginLifecycleState.ACTIVE || !state.isEnabled) return@forEach
            if (event.eventType !in plugin.manifest.subscribedEvents) return@forEach

            val granted = PluginPermissionMatrix.effectiveGranted(plugin.manifest, state)
            if (PluginPermission.LISTEN_APP_EVENTS !in granted) {
                recordLog(
                    pluginId = plugin.manifest.id,
                    pluginName = plugin.manifest.name,
                    level = PluginLogLevel.SECURITY,
                    action = "EVENT_BLOCKED",
                    message = "Événement ${event.eventType.name} bloqué : permission LISTEN_APP_EVENTS manquante."
                )
                return@forEach
            }
            if (event is RavenPluginEvent.MessagePosted && PluginPermission.READ_MESSAGES_SUMMARY !in granted) {
                recordLog(
                    pluginId = plugin.manifest.id,
                    pluginName = plugin.manifest.name,
                    level = PluginLogLevel.SECURITY,
                    action = "MESSAGE_EVENT_BLOCKED",
                    message = "Lecture de message bloquée : permission READ_MESSAGES_SUMMARY requise."
                )
                return@forEach
            }

            runGuarded(plugin, "EVENT_${event.eventType.name}") { api ->
                plugin.onEvent(event, api)
            }
        }
    }

    fun handleSlashCommand(targetId: String, rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("/")) return false
        val parts = trimmed.split(" ", limit = 2)
        val cmdTrigger = parts[0].lowercase()
        val args = parts.getOrNull(1)?.trim().orEmpty()

        for (plugin in registry.allPlugins()) {
            val state = statesByPluginId[plugin.manifest.id] ?: continue
            if (state.lifecycleState != PluginLifecycleState.ACTIVE || !state.isEnabled) continue
            val declaresCommand = plugin.manifest.slashCommands.any { it.trigger.equals(cmdTrigger, ignoreCase = true) }
            if (!declaresCommand) continue

            val granted = PluginPermissionMatrix.effectiveGranted(plugin.manifest, state)
            if (PluginPermission.EXECUTE_SLASH_COMMAND !in granted) {
                recordLog(
                    pluginId = plugin.manifest.id,
                    pluginName = plugin.manifest.name,
                    level = PluginLogLevel.SECURITY,
                    action = "CMD_DENIED",
                    message = "Commande $cmdTrigger refusée : permission EXECUTE_SLASH_COMMAND manquante."
                )
                return false
            }

            var handled = false
            runGuarded(plugin, "COMMAND_$cmdTrigger") { api ->
                handled = plugin.onCommand(cmdTrigger, args, targetId, api)
            }
            if (handled) return true
        }
        return false
    }

    fun activeSlashCommands(): List<SlashCommandOption> {
        val commands = mutableListOf<SlashCommandOption>()
        registry.allPlugins().forEach { plugin ->
            val state = statesByPluginId[plugin.manifest.id] ?: return@forEach
            if (state.lifecycleState == PluginLifecycleState.ACTIVE &&
                state.isEnabled &&
                PluginPermission.EXECUTE_SLASH_COMMAND in PluginPermissionMatrix.effectiveGranted(plugin.manifest, state)
            ) {
                plugin.manifest.slashCommands.forEach { cmd ->
                    commands.add(
                        SlashCommandOption(
                            command = cmd.trigger,
                            description = "${cmd.description} (${plugin.manifest.name})",
                            usageHint = "${cmd.trigger} ${cmd.usageHint}".trim()
                        )
                    )
                }
            }
        }
        return commands
    }

    fun getControlledApiForTesting(pluginId: String): RavenPluginApi? {
        val plugin = registry.findPlugin(pluginId) ?: return null
        return getOrCreateScopedApi(plugin)
    }

    suspend fun clearLogs(): Result<Unit> {
        _logs.value = emptyList()
        val db = chatRepo.firestoreDb()
        val uid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && !uid.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val snap = Tasks.await(
                        db.collection("users").document(uid)
                            .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
                            .limit(50)
                            .get()
                    )
                    if (!snap.isEmpty) {
                        val batch = db.batch()
                        snap.documents.forEach { batch.delete(it.reference) }
                        Tasks.await(batch.commit())
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    private fun runGuarded(
        plugin: RavenPlugin,
        action: String,
        block: (RavenPluginApi) -> Unit
    ): Boolean {
        val api = getOrCreateScopedApi(plugin)
        return try {
            block(api)
            true
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val pid = plugin.manifest.id
            val current = ensureDefaultStateFor(plugin)
            val errCount = current.consecutiveErrors + 1
            val shouldQuarantine = errCount >= MAX_CONSECUTIVE_ERRORS
            val nextState = current.copy(
                consecutiveErrors = errCount,
                lastErrorMessage = t.localizedMessage ?: t::class.java.simpleName,
                isEnabled = if (shouldQuarantine) false else current.isEnabled,
                lifecycleState = if (shouldQuarantine) PluginLifecycleState.FAULTED else current.lifecycleState,
                updatedAtMs = nowProvider()
            )
            statesByPluginId[pid] = nextState
            if (shouldQuarantine) {
                runCatching { plugin.onDisable() }
                chatRepo.notifyCommunityEvent(
                    title = "Extension mise en quarantaine",
                    body = "${plugin.manifest.name} a été désactivée après $errCount erreurs consécutives.",
                    sourceLabel = "Sécurité Extensions"
                )
            }
            recordLog(
                pluginId = pid,
                pluginName = plugin.manifest.name,
                level = PluginLogLevel.ERROR,
                action = action,
                message = if (shouldQuarantine) {
                    "Quarantaine déclenchée ($errCount/$MAX_CONSECUTIVE_ERRORS) : ${nextState.lastErrorMessage}"
                } else {
                    "Erreur isolée ($errCount/$MAX_CONSECUTIVE_ERRORS) : ${nextState.lastErrorMessage}"
                }
            )
            rebuildUiItems()
            persistPluginStateAsync(nextState)
            false
        }
    }

    private fun getOrCreateScopedApi(plugin: RavenPlugin): ScopedPluginApi {
        return apiCacheByPluginId.getOrPut(plugin.manifest.id) {
            ScopedPluginApi(
                manifest = plugin.manifest,
                grantedPermsProvider = {
                    val state = statesByPluginId[plugin.manifest.id]
                    if (state != null) {
                        PluginPermissionMatrix.effectiveGranted(plugin.manifest, state)
                    } else {
                        emptySet()
                    }
                },
                configProvider = {
                    statesByPluginId[plugin.manifest.id]?.configValues
                        ?: PluginSecurityGuard.buildDefaultConfig(plugin.manifest.configSchema)
                },
                userProvider = { chatRepo.currentUser.value },
                activeServerProvider = {
                    val sid = chatRepo.selectedServerId.value
                    chatRepo.servers.value.find { it.id == sid }
                },
                messagesProvider = { chId -> chatRepo.messages.value[chId].orEmpty() },
                onPostMessage = { chId, pluginName, cleanText ->
                    chatRepo.postPluginChannelMessage(chId, plugin.manifest.id, pluginName, cleanText)
                },
                onEmitNotification = { title, body, source ->
                    chatRepo.notifyCommunityEvent(title = title, body = body, sourceLabel = source)
                },
                onRecordLog = { level, action, msg ->
                    recordLog(plugin.manifest.id, plugin.manifest.name, level, action, msg)
                },
                nowProvider = nowProvider
            )
        }
    }

    private fun ensureDefaultStatesForRegistry() {
        registry.allPlugins().forEach { ensureDefaultStateFor(it) }
    }

    private fun ensureDefaultStateFor(plugin: RavenPlugin): UserPluginState {
        return statesByPluginId.getOrPut(plugin.manifest.id) {
            val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)
            UserPluginState(
                pluginId = plugin.manifest.id,
                installedVersion = plugin.manifest.version,
                isEnabled = false,
                lifecycleState = if (compat.isCompatible) PluginLifecycleState.DISABLED else PluginLifecycleState.INCOMPATIBLE,
                grantedPermissions = emptySet(),
                deniedPermissions = emptySet(),
                configValues = PluginSecurityGuard.buildDefaultConfig(plugin.manifest.configSchema),
                updatedAtMs = nowProvider()
            )
        }
    }

    private fun rebuildUiItems() {
        _pluginItems.value = registry.allPlugins().map { plugin ->
            val compat = PluginCompatibilityChecker.checkCompatibility(plugin.manifest, hostVersion)
            val state = ensureDefaultStateFor(plugin)
            PluginUiItem(
                manifest = plugin.manifest,
                compatibility = compat,
                state = state,
                pendingPermissions = PluginPermissionMatrix.pendingPermissions(plugin.manifest, state)
            )
        }
    }

    private fun recordLog(
        pluginId: String,
        pluginName: String,
        level: PluginLogLevel,
        action: String,
        message: String
    ) {
        val now = nowProvider()
        val entry = PluginLogEntry(
            id = "plog_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            pluginId = pluginId,
            pluginName = pluginName,
            level = level,
            action = action,
            message = PluginSecurityGuard.sanitizeText(message, 320),
            timestampMs = now,
            timestampLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        )
        _logs.value = (listOf(entry) + _logs.value).take(MAX_IN_MEMORY_LOGS)

        val db = chatRepo.firestoreDb()
        val uid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && !uid.isNullOrBlank()) {
            chatRepo.repoScope().launch(Dispatchers.IO) {
                runCatching {
                    Tasks.await(
                        db.collection("users").document(uid)
                            .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS)
                            .document(entry.id)
                            .set(PluginFirestoreSync.logToMap(entry))
                    )
                }.onFailure { e ->
                    FirestoreErrorHandler.handleFirestoreError(
                        (e as? Exception) ?: Exception(e),
                        OperationType.CREATE,
                        "users/$uid/${PluginFirestoreSync.SUBCOLLECTION_PLUGIN_LOGS}/${entry.id}",
                        uid
                    )
                }
            }
        }
    }

    private suspend fun persistPluginState(state: UserPluginState) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(
                    db.collection("users").document(uid)
                        .collection(PluginFirestoreSync.SUBCOLLECTION_PLUGINS)
                        .document(state.pluginId)
                        .set(PluginFirestoreSync.stateToMap(state))
                )
            }.onFailure { e ->
                FirestoreErrorHandler.handleFirestoreError(
                    (e as? Exception) ?: Exception(e),
                    OperationType.WRITE,
                    "users/$uid/${PluginFirestoreSync.SUBCOLLECTION_PLUGINS}/${state.pluginId}",
                    uid
                )
            }
        }
    }

    private fun persistPluginStateAsync(state: UserPluginState) {
        chatRepo.repoScope().launch { persistPluginState(state) }
    }
}
