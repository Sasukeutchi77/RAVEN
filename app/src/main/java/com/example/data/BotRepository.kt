package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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

class BotRepository(
    internal val chatRepo: ChatRepository,
    internal val webhookDispatcher: suspend (
        BotWebhookConfig,
        BotEventTrigger,
        String,
        String?
    ) -> Pair<Int?, String?> = BotFirestoreSync::dispatchWebhook
) {
    private val _bots = MutableStateFlow<List<RavenBot>>(emptyList())
    val bots: StateFlow<List<RavenBot>> = _bots.asStateFlow()

    private val _installationsByServer = MutableStateFlow<Map<String, List<ServerBotInstallation>>>(emptyMap())
    val installationsByServer: StateFlow<Map<String, List<ServerBotInstallation>>> = _installationsByServer.asStateFlow()

    private val _logsByBot = MutableStateFlow<Map<String, List<BotActivityLog>>>(emptyMap())
    val logsByBot: StateFlow<Map<String, List<BotActivityLog>>> = _logsByBot.asStateFlow()

    internal val spamLimiter = BotSpamAndRateLimiter()

    // Ephemeral owner-scoped hashes and webhook secrets (never written to /bots/{botId} public docs)
    private val inMemoryTokenHashesByBot = mutableMapOf<String, String>()
    private val inMemoryWebhookSecretsByBot = mutableMapOf<String, MutableMap<String, String>>()

    private var ownerBotsListener: ListenerRegistration? = null
    private var catalogBotsListener: ListenerRegistration? = null
    private var serverBotsListener: ListenerRegistration? = null
    private var activeBotLogsListener: ListenerRegistration? = null
    private var observedLogsBotId: String? = null

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    internal fun currentUserName(): String =
        chatRepo.currentUser.value.name.ifBlank { "Membre" }

    internal fun nowTimeLabel(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    internal fun webhookSecretFor(botId: String, webhookId: String): String? =
        inMemoryWebhookSecretsByBot[botId]?.get(webhookId)

    fun attachToChatRepository() {
        chatRepo.botCommandHandler = { targetId, rawInput ->
            handleSlashCommand(targetId, rawInput)
        }
        chatRepo.botMessageHandler = { serverId, channelId, message ->
            handleChannelMessage(serverId, channelId, message)
        }
        chatRepo.botCommunityEventHandler = { serverId, eventType, actorUid, actorName, detail ->
            val trigger = BotEventTrigger.entries.find { it.name == eventType }
            if (trigger != null) {
                handleCommunityEvent(serverId, trigger, actorUid, actorName, detail)
            }
        }
    }

    suspend fun isBotHandleAvailable(rawHandle: String, excludeBotId: String? = null): Boolean {
        val handle = BotTokenManager.normalizeHandle(rawHandle)
        if (!BotTokenManager.isValidHandleFormat(handle)) return false
        if (_bots.value.any { it.id != excludeBotId && it.handle.equals(handle, ignoreCase = true) }) {
            return false
        }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            return withContext(Dispatchers.IO) {
                try {
                    val doc = Tasks.await(db.collection("bot_handles").document(handle).get())
                    !doc.exists() || doc.getString("botId") == excludeBotId
                } catch (_: Exception) {
                    false
                }
            }
        }
        return true
    }

    suspend fun createBot(
        name: String,
        handleRaw: String,
        description: String,
        avatarUrl: String? = null,
        avatarColor: Long = 0xFF5865F2L,
        isPublicCatalog: Boolean = false,
        defaultPermissions: Set<BotPermission> = setOf(
            BotPermission.SEND_MESSAGES,
            BotPermission.USE_SLASH_COMMANDS,
            BotPermission.READ_MESSAGE_EVENTS
        ),
        subscribedEvents: Set<BotEventTrigger> = setOf(
            BotEventTrigger.ON_COMMAND_EXECUTED,
            BotEventTrigger.ON_MESSAGE_CREATE
        )
    ): Result<BotCreationResult> {
        val cleanName = name.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Le nom du bot doit contenir au moins 2 caractères."))
        }
        val normalizedHandle = BotTokenManager.normalizeHandle(handleRaw, cleanName)
        if (!isBotHandleAvailable(normalizedHandle)) {
            return Result.failure(IllegalStateException("L'identifiant @$normalizedHandle est déjà utilisé."))
        }

        val uid = currentUid()
        val botId = "bot_${UUID.randomUUID().toString().take(10)}"
        val rawToken = BotTokenManager.generateRawToken()
        val tokenHash = BotTokenManager.sha256Hex(rawToken)
        val tokenPrefix = BotTokenManager.maskTokenPrefix(rawToken)

        val starterCommands = listOf(
            BotCommandSpec(
                id = "cmd_ping_$botId",
                trigger = "/ping",
                description = "Vérifie que $cleanName répond en direct",
                usageHint = "/ping",
                responseTemplate = "$cleanName est en ligne ! Latence OK pour {user} dans #{channel}.",
                cooldownSeconds = 2,
                isEnabled = true
            ),
            BotCommandSpec(
                id = "cmd_info_$botId",
                trigger = "/botinfo",
                description = "Affiche le profil et le statut de $cleanName",
                usageHint = "/botinfo",
                responseTemplate = "Bot {bot} (@$normalizedHandle) actif sur {server}. Demandé par {user}.",
                cooldownSeconds = 5,
                isEnabled = true
            )
        )

        val newBot = RavenBot(
            id = botId,
            ownerUid = uid,
            name = cleanName,
            handle = normalizedHandle,
            avatarUrl = avatarUrl?.trim()?.ifBlank { null },
            avatarColor = avatarColor,
            description = description.trim().ifBlank { "Bot Raven automatisé @$normalizedHandle" },
            isActive = true,
            isPublicCatalog = isPublicCatalog,
            tokenPrefix = tokenPrefix,
            tokenVersion = 1,
            defaultPermissions = defaultPermissions.ifEmpty {
                setOf(BotPermission.SEND_MESSAGES, BotPermission.USE_SLASH_COMMANDS)
            },
            commands = starterCommands,
            subscribedEvents = subscribedEvents,
            enabledPluginIds = setOf("plugin_welcome"),
            createdAtMs = System.currentTimeMillis()
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudBot = newBot.copy(ownerUid = authUser.uid)
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("bot_handles").document(normalizedHandle)
                            .set(BotFirestoreSync.botHandleToMap(cloudBot))
                    )
                    Tasks.await(
                        db.collection("bots").document(botId)
                            .set(BotFirestoreSync.botToMap(cloudBot))
                    )
                    Tasks.await(
                        db.collection("bots").document(botId)
                            .collection("secrets").document("token_meta")
                            .set(
                                BotFirestoreSync.botSecretToMap(
                                    botId = botId,
                                    ownerUid = authUser.uid,
                                    tokenHash = tokenHash,
                                    tokenPrefix = tokenPrefix,
                                    tokenVersion = 1
                                )
                            )
                    )
                    Result.success(cloudBot)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "bots/$botId", authUser.uid)
                    Result.failure<RavenBot>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            val savedBot = res.getOrThrow()
            inMemoryTokenHashesByBot[botId] = tokenHash
            upsertBotInFlow(savedBot)
            recordLog(
                bot = savedBot,
                serverId = null,
                actorUid = authUser.uid,
                actorName = currentUserName(),
                eventType = "BOT_CREATED",
                summary = "Bot @$normalizedHandle créé avec jeton v1 ($tokenPrefix).",
                status = BotLogStatus.SUCCESS
            )
            return Result.success(BotCreationResult(savedBot, rawToken))
        }

        inMemoryTokenHashesByBot[botId] = tokenHash
        upsertBotInFlow(newBot)
        recordLog(
            bot = newBot,
            serverId = null,
            actorUid = uid,
            actorName = currentUserName(),
            eventType = "BOT_CREATED",
            summary = "Bot @$normalizedHandle créé avec jeton v1 ($tokenPrefix).",
            status = BotLogStatus.SUCCESS
        )
        return Result.success(BotCreationResult(newBot, rawToken))
    }

    suspend fun updateBot(
        botId: String,
        name: String,
        handleRaw: String,
        description: String,
        avatarUrl: String? = null,
        avatarColor: Long? = null,
        isPublicCatalog: Boolean? = null,
        defaultPermissions: Set<BotPermission>? = null,
        subscribedEvents: Set<BotEventTrigger>? = null
    ): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val cleanName = name.trim().ifBlank { bot.name }
        val nextHandle = BotTokenManager.normalizeHandle(handleRaw, cleanName)
        if (nextHandle != bot.handle && !isBotHandleAvailable(nextHandle, excludeBotId = botId)) {
            return Result.failure(IllegalStateException("L'identifiant @$nextHandle est déjà réservé."))
        }

        val updated = bot.copy(
            name = cleanName,
            handle = nextHandle,
            description = description.trim().ifBlank { bot.description },
            avatarUrl = avatarUrl?.trim()?.ifBlank { null },
            avatarColor = avatarColor ?: bot.avatarColor,
            isPublicCatalog = isPublicCatalog ?: bot.isPublicCatalog,
            defaultPermissions = defaultPermissions ?: bot.defaultPermissions,
            subscribedEvents = subscribedEvents ?: bot.subscribedEvents
        )

        val saveRes = persistBotDoc(updated)
        if (saveRes.isFailure) return Result.failure(saveRes.exceptionOrNull()!!)

        if (nextHandle != bot.handle) {
            val db = chatRepo.firestoreDb()
            val authUser = chatRepo.authInstance()?.currentUser
            if (db != null && authUser != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        Tasks.await(db.collection("bot_handles").document(nextHandle).set(BotFirestoreSync.botHandleToMap(updated)))
                        Tasks.await(db.collection("bot_handles").document(bot.handle).delete())
                    }
                }
            }
        }
        syncBotIdentityInLocalServers(updated)
        recordLog(updated, null, currentUid(), currentUserName(), "BOT_UPDATED", "Métadonnées du bot mises à jour.", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    suspend fun toggleBotActive(botId: String, active: Boolean): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val updated = bot.copy(isActive = active)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        if (active) spamLimiter.resetStrikes(botId)
        recordLog(
            bot = updated,
            serverId = null,
            actorUid = currentUid(),
            actorName = currentUserName(),
            eventType = if (active) "BOT_ENABLED" else "BOT_DISABLED",
            summary = if (active) "Bot réactivé globalement." else "Bot désactivé globalement.",
            status = BotLogStatus.SUCCESS
        )
        return Result.success(updated)
    }

    suspend fun regenerateBotToken(botId: String): Result<BotTokenRegenResult> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val rawToken = BotTokenManager.generateRawToken()
        val tokenHash = BotTokenManager.sha256Hex(rawToken)
        val tokenPrefix = BotTokenManager.maskTokenPrefix(rawToken)
        val nextVersion = bot.tokenVersion + 1
        val updated = bot.copy(tokenPrefix = tokenPrefix, tokenVersion = nextVersion)

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("bots").document(botId)
                            .collection("secrets").document("token_meta")
                            .set(
                                BotFirestoreSync.botSecretToMap(
                                    botId = botId,
                                    ownerUid = authUser.uid,
                                    tokenHash = tokenHash,
                                    tokenPrefix = tokenPrefix,
                                    tokenVersion = nextVersion,
                                    webhookSecrets = inMemoryWebhookSecretsByBot[botId].orEmpty()
                                )
                            )
                    )
                    Tasks.await(
                        db.collection("bots").document(botId)
                            .set(BotFirestoreSync.botToMap(updated), SetOptions.merge())
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "bots/$botId/secrets", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        inMemoryTokenHashesByBot[botId] = tokenHash
        upsertBotInFlow(updated)
        recordLog(
            bot = updated,
            serverId = null,
            actorUid = currentUid(),
            actorName = currentUserName(),
            eventType = "TOKEN_REGENERATED",
            summary = "Jeton régénéré (v$nextVersion • $tokenPrefix). Ancien jeton révoqué.",
            status = BotLogStatus.SECURITY_ALERT
        )
        return Result.success(BotTokenRegenResult(updated, rawToken))
    }

    suspend fun verifyBotToken(botId: String, candidateToken: String): Result<Boolean> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        val expectedHash: String? = if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                try {
                    val snap = Tasks.await(
                        db.collection("bots").document(botId)
                            .collection("secrets").document("token_meta")
                            .get()
                    )
                    snap.getString("tokenHash")
                } catch (_: Exception) {
                    inMemoryTokenHashesByBot[botId]
                }
            }
        } else {
            inMemoryTokenHashesByBot[botId]
        }
        val ok = expectedHash != null && BotTokenManager.verifyTokenAgainstHash(candidateToken, expectedHash)
        recordLog(
            bot = bot,
            serverId = null,
            actorUid = currentUid(),
            actorName = currentUserName(),
            eventType = "TOKEN_VERIFY",
            summary = if (ok) "Vérification du jeton réussie." else "Échec de vérification de jeton !",
            status = if (ok) BotLogStatus.SUCCESS else BotLogStatus.SECURITY_ALERT
        )
        return Result.success(ok)
    }

    suspend fun deleteBot(botId: String): Result<Unit> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }

        val installedServers = _installationsByServer.value.entries
            .filter { (_, list) -> list.any { it.botId == botId } }
            .map { it.key }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    installedServers.forEach { sid ->
                        runCatching { Tasks.await(db.collection("servers").document(sid).collection("bots").document(botId).delete()) }
                        runCatching { Tasks.await(db.collection("servers").document(sid).collection("members").document("bot_$botId").delete()) }
                    }
                    runCatching { Tasks.await(db.collection("bots").document(botId).collection("secrets").document("token_meta").delete()) }
                    runCatching { Tasks.await(db.collection("bot_handles").document(bot.handle).delete()) }
                    Tasks.await(db.collection("bots").document(botId).delete())
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "bots/$botId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        installedServers.forEach { sid -> removeBotMemberFromLocalServer(sid, botId) }
        _installationsByServer.value = _installationsByServer.value.mapValues { (_, list) ->
            list.filterNot { it.botId == botId }
        }
        inMemoryTokenHashesByBot.remove(botId)
        inMemoryWebhookSecretsByBot.remove(botId)
        _bots.value = _bots.value.filterNot { it.id == botId }
        _logsByBot.value = _logsByBot.value - botId
        return Result.success(Unit)
    }

    // --- Server Installation, Permissions & Role Assignment ---

    suspend fun addBotToServer(
        botId: String,
        serverId: String,
        grantedPermissions: Set<BotPermission>? = null,
        assignedRoleTitle: String = "Bot Raven",
        assignedCustomRoleId: String? = null
    ): Result<ServerBotInstallation> {
        val srv = chatRepo.servers.value.find { it.id == serverId && !it.isDmTab }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        val canManageServer = CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_ROLES)
        if (!canManageServer) {
            return Result.failure(SecurityException("Permission Administrateur requise pour ajouter un bot à ce serveur."))
        }
        val bot = _bots.value.find { it.id == botId }
            ?: return Result.failure(IllegalArgumentException("Bot introuvable."))
        if (bot.ownerUid != uid && !bot.isPublicCatalog) {
            return Result.failure(SecurityException("Ce bot est privé et appartient à un autre créateur."))
        }

        val perms = (grantedPermissions ?: bot.defaultPermissions).ifEmpty {
            setOf(BotPermission.SEND_MESSAGES, BotPermission.USE_SLASH_COMMANDS)
        }
        val inst = ServerBotInstallation(
            botId = bot.id,
            serverId = serverId,
            botName = bot.name,
            botHandle = bot.handle,
            botAvatarUrl = bot.avatarUrl,
            botAvatarColor = bot.avatarColor,
            ownerUid = bot.ownerUid,
            grantedPermissions = perms,
            assignedRoleTitle = assignedRoleTitle.trim().ifBlank { "Bot Raven" },
            assignedCustomRoleId = assignedCustomRoleId,
            isEnabledInServer = true,
            installedByUid = uid,
            installedAtMs = System.currentTimeMillis()
        )

        val botMemberUser = User(
            id = "bot_${bot.id}",
            name = bot.name,
            tag = "#BOT",
            avatarUrl = bot.avatarUrl,
            avatarColor = bot.avatarColor,
            role = UserRole.BOT,
            customRoleId = assignedCustomRoleId,
            status = if (bot.isActive) UserStatus.ONLINE else UserStatus.OFFLINE
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(serverId)
                    Tasks.await(srvRef.collection("bots").document(bot.id).set(BotFirestoreSync.serverBotToMap(inst)))
                    Tasks.await(
                        srvRef.collection("members").document("bot_${bot.id}").set(
                            CommunityFirestoreSync.memberToMap(
                                botMemberUser,
                                serverId,
                                UserRole.BOT,
                                setOf(RolePermission.SEND_MESSAGES),
                                srv.isPublic
                            ),
                            SetOptions.merge()
                        )
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "servers/$serverId/bots/${bot.id}", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        upsertInstallationInFlow(serverId, inst)
        upsertBotMemberInLocalServer(serverId, botMemberUser)
        recordLog(
            bot = bot,
            serverId = serverId,
            actorUid = uid,
            actorName = currentUserName(),
            eventType = "BOT_ADDED_SERVER",
            summary = "Installé sur ${srv.name} avec rôle '${inst.assignedRoleTitle}'.",
            status = BotLogStatus.SUCCESS
        )
        return Result.success(inst)
    }

    suspend fun removeBotFromServer(botId: String, serverId: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId && !it.isDmTab }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        val bot = _bots.value.find { it.id == botId }
        val isServerAdmin = CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_ROLES)
        val isBotOwner = bot?.ownerUid == uid
        if (!isServerAdmin && !isBotOwner) {
            return Result.failure(SecurityException("Permission Administrateur ou propriétaire du bot requise pour retirer ce bot."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(serverId)
                    Tasks.await(srvRef.collection("bots").document(botId).delete())
                    runCatching { Tasks.await(srvRef.collection("members").document("bot_$botId").delete()) }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "servers/$serverId/bots/$botId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        val existing = _installationsByServer.value[serverId].orEmpty()
        _installationsByServer.value = _installationsByServer.value + (serverId to existing.filterNot { it.botId == botId })
        removeBotMemberFromLocalServer(serverId, botId)
        if (bot != null) {
            recordLog(
                bot = bot,
                serverId = serverId,
                actorUid = uid,
                actorName = currentUserName(),
                eventType = "BOT_REMOVED_SERVER",
                summary = "Retiré de ${srv.name}.",
                status = BotLogStatus.SUCCESS
            )
        }
        return Result.success(Unit)
    }

    suspend fun updateServerBotConfig(
        botId: String,
        serverId: String,
        grantedPermissions: Set<BotPermission>,
        assignedRoleTitle: String,
        assignedCustomRoleId: String?,
        isEnabledInServer: Boolean
    ): Result<ServerBotInstallation> {
        val srv = chatRepo.servers.value.find { it.id == serverId && !it.isDmTab }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        val isServerAdmin = CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_ROLES)
        if (!isServerAdmin) {
            return Result.failure(SecurityException("Seul un administrateur du serveur peut configurer les permissions du bot."))
        }
        val currentInst = _installationsByServer.value[serverId].orEmpty().find { it.botId == botId }
            ?: return Result.failure(IllegalArgumentException("Ce bot n'est pas installé sur ce serveur."))

        val updated = currentInst.copy(
            grantedPermissions = grantedPermissions,
            assignedRoleTitle = assignedRoleTitle.trim().ifBlank { "Bot Raven" },
            assignedCustomRoleId = assignedCustomRoleId,
            isEnabledInServer = isEnabledInServer
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(serverId)
                    Tasks.await(srvRef.collection("bots").document(botId).set(BotFirestoreSync.serverBotToMap(updated)))
                    runCatching {
                        Tasks.await(
                            srvRef.collection("members").document("bot_$botId").update(
                                mapOf(
                                    "customRoleId" to assignedCustomRoleId,
                                    "status" to if (isEnabledInServer) UserStatus.ONLINE.name else UserStatus.OFFLINE.name,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        )
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId/bots/$botId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        upsertInstallationInFlow(serverId, updated)
        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value.map { server ->
            if (server.id == serverId) {
                server.copy(members = server.members.map { m ->
                    if (m.id == "bot_$botId") m.copy(
                        customRoleId = assignedCustomRoleId,
                        status = if (isEnabledInServer) UserStatus.ONLINE else UserStatus.OFFLINE
                    ) else m
                })
            } else server
        }
        _bots.value.find { it.id == botId }?.let { b ->
            recordLog(
                bot = b,
                serverId = serverId,
                actorUid = uid,
                actorName = currentUserName(),
                eventType = "SERVER_PERMS_UPDATED",
                summary = "Permissions (${grantedPermissions.size}) & rôle '${updated.assignedRoleTitle}' mis à jour.",
                status = BotLogStatus.SUCCESS
            )
        }
        return Result.success(updated)
    }

    // --- Commands, Auto-Responses, Webhooks, Plugins, Rate-Limits ---

    suspend fun saveBotCommand(botId: String, spec: BotCommandSpec): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val normalizedCmd = "/" + spec.trigger.trim().removePrefix("/").lowercase().replace(" ", "")
        if (normalizedCmd.length < 2) {
            return Result.failure(IllegalArgumentException("La commande doit commencer par / suivi d'au moins 1 caractère."))
        }
        if (spec.responseTemplate.isBlank()) {
            return Result.failure(IllegalArgumentException("Le message de réponse est requis."))
        }
        val cleanSpec = spec.copy(
            id = spec.id.ifBlank { "cmd_${UUID.randomUUID().toString().take(8)}" },
            trigger = normalizedCmd,
            usageHint = spec.usageHint.ifBlank { normalizedCmd },
            cooldownSeconds = spec.cooldownSeconds.coerceIn(0, 120)
        )
        val nextCommands = if (bot.commands.any { it.id == cleanSpec.id }) {
            bot.commands.map { if (it.id == cleanSpec.id) cleanSpec else it }
        } else {
            bot.commands.filterNot { it.trigger.equals(normalizedCmd, ignoreCase = true) } + cleanSpec
        }
        val updated = bot.copy(commands = nextCommands)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        recordLog(updated, null, currentUid(), currentUserName(), "CMD_SAVED", "Commande $normalizedCmd configurée.", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    suspend fun deleteBotCommand(botId: String, commandId: String): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val updated = bot.copy(commands = bot.commands.filterNot { it.id == commandId })
        val res = persistBotDoc(updated)
        return if (res.isSuccess) Result.success(updated) else Result.failure(res.exceptionOrNull()!!)
    }

    suspend fun saveAutoResponse(botId: String, rule: BotAutoResponseRule): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        if (rule.pattern.isBlank() || rule.replyTemplate.isBlank()) {
            return Result.failure(IllegalArgumentException("Motif déclencheur et réponse automatique requis."))
        }
        if (rule.isRegex) {
            val regexValid = runCatching { Regex(rule.pattern) }.isSuccess
            if (!regexValid) {
                return Result.failure(IllegalArgumentException("Expression régulière invalide."))
            }
        }
        val clean = rule.copy(
            id = rule.id.ifBlank { "auto_${UUID.randomUUID().toString().take(8)}" },
            name = rule.name.trim().ifBlank { "Règle ${rule.pattern.take(16)}" },
            cooldownSeconds = rule.cooldownSeconds.coerceIn(1, 300)
        )
        val nextRules = if (bot.autoResponses.any { it.id == clean.id }) {
            bot.autoResponses.map { if (it.id == clean.id) clean else it }
        } else {
            bot.autoResponses + clean
        }
        val updated = bot.copy(autoResponses = nextRules)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        recordLog(updated, null, currentUid(), currentUserName(), "AUTO_RULE_SAVED", "Auto-réponse '${clean.name}' enregistrée.", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    suspend fun deleteAutoResponse(botId: String, ruleId: String): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val updated = bot.copy(autoResponses = bot.autoResponses.filterNot { it.id == ruleId })
        val res = persistBotDoc(updated)
        return if (res.isSuccess) Result.success(updated) else Result.failure(res.exceptionOrNull()!!)
    }

    suspend fun saveWebhook(
        botId: String,
        label: String,
        endpointUrl: String,
        secret: String,
        subscribedEvents: Set<BotEventTrigger>
    ): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val cleanUrl = endpointUrl.trim()
        if (!BotTokenManager.isValidHttpsWebhookUrl(cleanUrl)) {
            return Result.failure(IllegalArgumentException("URL webhook invalide : utilisez un domaine HTTPS public valide."))
        }
        val wid = "wh_${UUID.randomUUID().toString().take(8)}"
        val cleanSecret = secret.trim()
        val hasSecret = cleanSecret.isNotBlank()
        if (hasSecret) {
            inMemoryWebhookSecretsByBot.getOrPut(botId) { mutableMapOf() }[wid] = cleanSecret
        }
        val hook = BotWebhookConfig(
            id = wid,
            label = label.trim().ifBlank { "Webhook Externe" },
            endpointUrl = cleanUrl,
            hasSecret = hasSecret,
            subscribedEvents = subscribedEvents.ifEmpty { setOf(BotEventTrigger.ON_COMMAND_EXECUTED) },
            isEnabled = true
        )
        val updated = bot.copy(webhooks = bot.webhooks + hook)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)

        if (hasSecret) {
            val db = chatRepo.firestoreDb()
            val authUser = chatRepo.authInstance()?.currentUser
            if (db != null && authUser != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        Tasks.await(
                            db.collection("bots").document(botId).collection("secrets").document("token_meta")
                                .update(mapOf("webhookSecrets.$wid" to cleanSecret, "updatedAt" to FieldValue.serverTimestamp()))
                        )
                    }
                }
            }
        }
        recordLog(updated, null, currentUid(), currentUserName(), "WEBHOOK_ADDED", "Webhook '${hook.label}' ajouté ($cleanUrl).", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    suspend fun deleteWebhook(botId: String, webhookId: String): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        inMemoryWebhookSecretsByBot[botId]?.remove(webhookId)
        val updated = bot.copy(webhooks = bot.webhooks.filterNot { it.id == webhookId })
        val res = persistBotDoc(updated)
        return if (res.isSuccess) Result.success(updated) else Result.failure(res.exceptionOrNull()!!)
    }

    suspend fun triggerTestWebhook(botId: String, webhookId: String): Result<Int> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val hook = bot.webhooks.find { it.id == webhookId }
            ?: return Result.failure(IllegalArgumentException("Webhook introuvable."))
        val secret = webhookSecretFor(botId, webhookId)
        val payload = """{"event":"PING_TEST","botId":"${bot.id}","botHandle":"${bot.handle}","timestampMs":${System.currentTimeMillis()}}"""
        val (code, err) = webhookDispatcher(hook, BotEventTrigger.ON_COMMAND_EXECUTED, payload, secret)
        val now = System.currentTimeMillis()
        val updatedHooks = bot.webhooks.map {
            if (it.id == webhookId) it.copy(lastStatusCode = code, lastDeliveryAtMs = now) else it
        }
        val updated = bot.copy(webhooks = updatedHooks)
        persistBotDoc(updated)
        recordLog(
            bot = updated,
            serverId = null,
            actorUid = currentUid(),
            actorName = currentUserName(),
            eventType = "WEBHOOK_TEST",
            summary = if (err == null) "Test webhook HTTP $code réussi." else "Test webhook échoué : $err",
            status = if (err == null) BotLogStatus.WEBHOOK_OK else BotLogStatus.WEBHOOK_FAILED
        )
        return if (code != null && err == null) {
            Result.success(code)
        } else {
            Result.failure(IllegalStateException(err ?: "Échec d'envoi webhook"))
        }
    }

    suspend fun toggleBotPlugin(botId: String, pluginId: String, enabled: Boolean): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val nextPlugins = if (enabled) bot.enabledPluginIds + pluginId else bot.enabledPluginIds - pluginId
        val updated = bot.copy(enabledPluginIds = nextPlugins)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        recordLog(updated, null, currentUid(), currentUserName(), "PLUGIN_TOGGLED", "Plugin '$pluginId' ${if (enabled) "activé" else "désactivé"}.", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    suspend fun updateRateLimitConfig(botId: String, config: BotRateLimitConfig): Result<RavenBot> {
        val bot = requireOwnedBot(botId).getOrElse { return Result.failure(it) }
        val safeConfig = BotRateLimitConfig(
            maxActionsPerMinute = config.maxActionsPerMinute.coerceIn(1, 120),
            duplicateWindowSeconds = config.duplicateWindowSeconds.coerceIn(2, 300),
            autoDisableOnAbuseStrikes = config.autoDisableOnAbuseStrikes.coerceIn(1, 50),
            antiSpamHumanFloodThreshold = config.antiSpamHumanFloodThreshold.coerceIn(3, 30)
        )
        val updated = bot.copy(rateLimit = safeConfig)
        val res = persistBotDoc(updated)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        recordLog(updated, null, currentUid(), currentUserName(), "RATE_LIMIT_UPDATED", "Limites mises à jour (${safeConfig.maxActionsPerMinute}/min).", BotLogStatus.SUCCESS)
        return Result.success(updated)
    }

    fun getSlashCommandsForServer(serverId: String): List<SlashCommandOption> {
        val installs = _installationsByServer.value[serverId].orEmpty().filter { it.isEnabledInServer }
        val botsById = _bots.value.associateBy { it.id }
        return installs.flatMap { inst ->
            val bot = botsById[inst.botId] ?: return@flatMap emptyList()
            if (!bot.isActive || BotPermission.USE_SLASH_COMMANDS !in inst.grantedPermissions) {
                return@flatMap emptyList()
            }
            bot.commands.filter { it.isEnabled }.map { cmd ->
                SlashCommandOption(
                    command = cmd.trigger,
                    description = "[${bot.name}] ${cmd.description}",
                    usageHint = cmd.usageHint
                )
            }
        }.distinctBy { it.command }
    }

    // --- Listeners ---

    fun startListeningBots() {
        releaseBotListeners()
        val db = chatRepo.firestoreDb() ?: return
        val authUser = chatRepo.authInstance()?.currentUser ?: return

        ownerBotsListener = db.collection("bots")
            .whereEqualTo("ownerUid", authUser.uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val owned = snap.documents.mapNotNull { BotFirestoreSync.mapDocToBot(it) }
                val nonOwnedCatalog = _bots.value.filter { it.ownerUid != authUser.uid && it.isPublicCatalog }
                _bots.value = (owned + nonOwnedCatalog).distinctBy { it.id }
            }

        catalogBotsListener = db.collection("bots")
            .whereEqualTo("isPublicCatalog", true)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val catalog = snap.documents.mapNotNull { BotFirestoreSync.mapDocToBot(it) }
                val existingOwned = _bots.value.filter { it.ownerUid == authUser.uid }
                _bots.value = (existingOwned + catalog).distinctBy { it.id }
            }
    }

    fun observeServerBots(serverId: String) {
        serverBotsListener?.remove()
        serverBotsListener = null
        if (serverId.isBlank() || serverId == "dm_tab") return
        val db = chatRepo.firestoreDb() ?: return
        if (chatRepo.authInstance()?.currentUser == null) return

        serverBotsListener = db.collection("servers").document(serverId)
            .collection("bots")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { BotFirestoreSync.mapDocToServerBot(it) }
                _installationsByServer.value = _installationsByServer.value + (serverId to list)
            }
    }

    fun observeBotLogs(botId: String) {
        if (observedLogsBotId == botId && activeBotLogsListener != null) return
        activeBotLogsListener?.remove()
        activeBotLogsListener = null
        observedLogsBotId = botId
        val db = chatRepo.firestoreDb() ?: return
        val authUser = chatRepo.authInstance()?.currentUser ?: return
        val bot = _bots.value.find { it.id == botId } ?: return
        if (bot.ownerUid != authUser.uid) return

        activeBotLogsListener = db.collection("bots").document(botId)
            .collection("logs")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val parsed = snap.documents.mapNotNull { BotFirestoreSync.mapDocToBotLog(it) }
                    .sortedByDescending { it.createdAtMs }
                    .take(100)
                if (parsed.isNotEmpty()) {
                    _logsByBot.value = _logsByBot.value + (botId to parsed)
                }
            }
    }

    fun releaseListeners() {
        releaseBotListeners()
        serverBotsListener?.remove()
        serverBotsListener = null
    }

    private fun releaseBotListeners() {
        ownerBotsListener?.remove()
        catalogBotsListener?.remove()
        activeBotLogsListener?.remove()
        ownerBotsListener = null
        catalogBotsListener = null
        activeBotLogsListener = null
        observedLogsBotId = null
    }

    // --- Internal Helpers ---

    internal fun requireOwnedBot(botId: String): Result<RavenBot> {
        val bot = _bots.value.find { it.id == botId }
            ?: return Result.failure(IllegalArgumentException("Bot introuvable."))
        if (bot.ownerUid != currentUid()) {
            return Result.failure(SecurityException("Accès refusé : seul le propriétaire de ce bot peut effectuer cette action."))
        }
        return Result.success(bot)
    }

    internal suspend fun persistBotDoc(bot: RavenBot): Result<Unit> {
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("bots").document(bot.id).set(BotFirestoreSync.botToMap(bot), SetOptions.merge()))
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "bots/${bot.id}", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return res
        }
        upsertBotInFlow(bot)
        return Result.success(Unit)
    }

    internal fun upsertBotInFlow(bot: RavenBot) {
        val curr = _bots.value
        _bots.value = if (curr.any { it.id == bot.id }) {
            curr.map { if (it.id == bot.id) bot else it }
        } else {
            listOf(bot) + curr
        }
    }

    internal fun upsertInstallationInFlow(serverId: String, inst: ServerBotInstallation) {
        val existing = _installationsByServer.value[serverId].orEmpty()
        val updatedList = if (existing.any { it.botId == inst.botId }) {
            existing.map { if (it.botId == inst.botId) inst else it }
        } else {
            existing + inst
        }
        _installationsByServer.value = _installationsByServer.value + (serverId to updatedList)
    }

    private fun syncBotIdentityInLocalServers(bot: RavenBot) {
        _installationsByServer.value = _installationsByServer.value.mapValues { (_, list) ->
            list.map {
                if (it.botId == bot.id) it.copy(
                    botName = bot.name,
                    botHandle = bot.handle,
                    botAvatarUrl = bot.avatarUrl,
                    botAvatarColor = bot.avatarColor
                ) else it
            }
        }
        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value.map { srv ->
            srv.copy(members = srv.members.map { m ->
                if (m.id == "bot_${bot.id}") m.copy(
                    name = bot.name,
                    avatarUrl = bot.avatarUrl,
                    avatarColor = bot.avatarColor
                ) else m
            })
        }
    }

    private fun upsertBotMemberInLocalServer(serverId: String, botUser: User) {
        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value.map { srv ->
            if (srv.id == serverId) {
                val filtered = srv.members.filterNot { it.id == botUser.id }
                srv.copy(members = filtered + botUser)
            } else srv
        }
    }

    private fun removeBotMemberFromLocalServer(serverId: String, botId: String) {
        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value.map { srv ->
            if (srv.id == serverId) {
                srv.copy(members = srv.members.filterNot { it.id == "bot_$botId" })
            } else srv
        }
    }

    internal fun recordLog(
        bot: RavenBot,
        serverId: String?,
        actorUid: String,
        actorName: String,
        eventType: String,
        summary: String,
        status: BotLogStatus
    ) {
        val log = BotActivityLog(
            id = "blog_${UUID.randomUUID().toString().take(8)}",
            botId = bot.id,
            ownerUid = bot.ownerUid,
            serverId = serverId,
            actorUid = actorUid,
            actorName = actorName,
            eventType = eventType,
            summary = summary,
            status = status,
            timestampLabel = nowTimeLabel(),
            createdAtMs = System.currentTimeMillis()
        )
        val existing = _logsByBot.value[bot.id].orEmpty()
        _logsByBot.value = _logsByBot.value + (bot.id to (listOf(log) + existing).take(100))

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                runCatching {
                    Tasks.await(
                        db.collection("bots").document(bot.id)
                            .collection("logs").document(log.id)
                            .set(BotFirestoreSync.botLogToMap(log))
                    )
                }
            }
        }
    }
}
