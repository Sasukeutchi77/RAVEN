package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class ServerCustomizationRepository(
    val communityRepo: CommunityRepository
) {
    private val chatRepo: ChatRepository get() = communityRepo.chatRepo

    private fun currentUid(): String = communityRepo.currentUid()
    private fun currentUserName(): String = communityRepo.currentUserName()

    private fun requireAdminAccess(srv: Server, uid: String): Result<Unit> {
        val isOwner = CommunityPermissions.isOwner(srv, uid)
        val isAdmin = CommunityPermissions.effectiveRole(srv, uid) == UserRole.ADMIN
        val hasPerm = CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_MODERATION)
        return if (isOwner || isAdmin || hasPerm) {
            Result.success(Unit)
        } else {
            Result.failure(SecurityException("Droits Propriétaire ou Administrateur requis pour modifier ce paramètre sensible."))
        }
    }

    private fun requireStrictAdmin(srv: Server, uid: String): Result<Unit> {
        val isOwner = CommunityPermissions.isOwner(srv, uid)
        val isAdmin = CommunityPermissions.effectiveRole(srv, uid) == UserRole.ADMIN
        return if (isOwner || isAdmin) {
            Result.success(Unit)
        } else {
            Result.failure(SecurityException("Seuls le propriétaire ou un administrateur peuvent modifier la sécurité, modération ou confidentialité."))
        }
    }

    private fun appendAuditLog(srv: Server, action: String): List<AuditLogEntry> {
        val entry = AuditLogEntry(
            id = "log_${UUID.randomUUID().toString().take(8)}",
            actorName = currentUserName(),
            actionDescription = action,
            targetName = srv.name,
            timestamp = CommunityPermissions.nowTimeLabel()
        )
        return (listOf(entry) + srv.auditLogs).take(40)
    }

    suspend fun updateIdentityAndTheme(
        serverId: String,
        name: String,
        uniqueHandleRaw: String,
        description: String,
        iconText: String,
        iconPresetKey: String?,
        imageUrl: String?,
        bannerUrl: String?,
        accentColorHex: Long,
        secondaryColorHex: Long,
        surfaceStyle: ServerThemeSurfaceStyle,
        categoryTag: String,
        backgroundThemeId: String? = null,
        backgroundImageUrl: String? = null,
        backgroundOpacity: Float = 0.50f
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        requireAdminAccess(srv, uid).onFailure { return Result.failure(it) }

        val cleanName = name.trim()
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Le nom du serveur doit contenir au moins 2 caractères."))
        }
        val newHandle = CommunityPermissions.normalizeHandle(uniqueHandleRaw, cleanName)
        if (newHandle != srv.uniqueHandle && !communityRepo.isHandleAvailable(newHandle, excludeServerId = serverId)) {
            return Result.failure(IllegalStateException("L'identifiant @$newHandle est déjà utilisé."))
        }

        val cleanIconText = iconText.trim().take(4).ifBlank { cleanName.take(2).uppercase() }
        val nextTheme = srv.customization.theme.copy(
            accentColorHex = accentColorHex,
            secondaryColorHex = secondaryColorHex,
            surfaceStyle = surfaceStyle,
            iconPresetKey = iconPresetKey?.trim()?.ifBlank { null },
            backgroundThemeId = backgroundThemeId?.trim()?.ifBlank { null },
            backgroundImageUrl = backgroundImageUrl?.trim()?.ifBlank { null },
            backgroundOpacity = backgroundOpacity.coerceIn(0.15f, 0.90f)
        )
        val updated = srv.copy(
            name = cleanName,
            uniqueHandle = newHandle,
            description = description.trim().ifBlank { srv.description },
            iconText = cleanIconText,
            iconBgColor = accentColorHex,
            imageUrl = imageUrl?.trim()?.ifBlank { null },
            bannerUrl = bannerUrl?.trim()?.ifBlank { null },
            customBannerColorHex = accentColorHex,
            categoryTag = categoryTag.trim().ifBlank { srv.categoryTag },
            customization = srv.customization.copy(theme = nextTheme),
            auditLogs = appendAuditLog(srv, "a personnalisé l'apparence & l'identité")
        )

        val res = pushCustomizationToCloud(updated, syncHandleFrom = srv.uniqueHandle)
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        communityRepo.syncPublicListingState(updated)
        return Result.success(updated)
    }

    suspend fun upsertCategory(
        serverId: String,
        categoryId: String?,
        name: String,
        isPrivate: Boolean,
        allowedRoleIds: List<String> = emptyList()
    ): Result<ChannelCategory> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_CHANNELS)) {
            return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
        }
        val cleanName = name.trim().uppercase()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Nom de catégorie requis."))

        val existing = categoryId?.let { cid -> srv.categories.find { it.id == cid } }
        val targetCat = if (existing != null) {
            existing.copy(
                name = cleanName,
                isPrivate = isPrivate,
                allowedRoleIds = if (isPrivate) allowedRoleIds else emptyList()
            )
        } else {
            val nextPos = (srv.categories.maxOfOrNull { it.position } ?: -1) + 1
            ChannelCategory(
                id = "cat_${UUID.randomUUID().toString().take(6)}",
                name = cleanName,
                position = nextPos,
                isPrivate = isPrivate,
                allowedRoleIds = if (isPrivate) allowedRoleIds else emptyList()
            )
        }

        val oldName = existing?.name
        val nextCategories = if (existing != null) {
            srv.categories.map { if (it.id == targetCat.id) targetCat else it }
        } else {
            srv.categories + targetCat
        }.sortedBy { it.position }

        val updatedChannels = if (oldName != null && !oldName.equals(cleanName, ignoreCase = true)) {
            srv.channels.map { ch ->
                if (ch.category.equals(oldName, ignoreCase = true)) ch.copy(category = cleanName) else ch
            }
        } else srv.channels

        val updated = srv.copy(
            categories = nextCategories,
            channels = updatedChannels,
            auditLogs = appendAuditLog(srv, if (existing != null) "a modifié la catégorie $cleanName" else "a créé la catégorie $cleanName")
        )
        val cloudRes = pushCategoriesAndChannelsToCloud(updated, updatedChannels.filter { it.category == cleanName })
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(targetCat)
    }

    suspend fun moveCategoryOrder(serverId: String, categoryId: String, delta: Int): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_CHANNELS)) {
            return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
        }
        val sorted = srv.categories.sortedBy { it.position }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == categoryId }
        if (idx < 0) return Result.failure(IllegalArgumentException("Catégorie introuvable."))
        val targetIdx = (idx + delta).coerceIn(0, sorted.lastIndex)
        if (idx == targetIdx) return Result.success(Unit)
        val item = sorted.removeAt(idx)
        sorted.add(targetIdx, item)
        val normalized = sorted.mapIndexed { index, cat -> cat.copy(position = index) }

        val updated = srv.copy(
            categories = normalized,
            auditLogs = appendAuditLog(srv, "a réorganisé les catégories")
        )
        val cloudRes = pushCategoriesAndChannelsToCloud(updated, emptyList())
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(Unit)
    }

    suspend fun upsertChannelCustomization(
        serverId: String,
        channelId: String?,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        iconKey: String?,
        slowmodeSeconds: Int,
        isReadOnly: Boolean,
        isPrivate: Boolean,
        allowedRoles: List<UserRole> = emptyList(),
        allowedRoleIds: List<String> = emptyList(),
        maxParticipants: Int = 0,
        bitrateKbps: Int = 64
    ): Result<Channel> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        val existing = channelId?.let { cid -> srv.channels.find { it.id == cid } }
        val isChangingSensitiveChannelPerms = isPrivate != (existing?.isPrivate ?: false) ||
            allowedRoles != (existing?.allowedRoles ?: emptyList<UserRole>()) ||
            allowedRoleIds != (existing?.allowedRoleIds ?: emptyList<String>()) ||
            iconKey != existing?.iconKey
        if (isChangingSensitiveChannelPerms) {
            requireAdminAccess(srv, uid).onFailure { return Result.failure(it) }
        } else if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_CHANNELS)) {
            return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
        }

        val cleanName = if (type == ChannelType.VOICE || type == ChannelType.STAGE) {
            name.trim()
        } else {
            name.trim().lowercase().replace(" ", "-")
        }
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Nom de salon requis."))

        val cleanIcon = iconKey?.takeIf { it in ChannelIconCatalog.validKeys }
        val targetCat = categoryName.trim().ifBlank {
            if (type == ChannelType.VOICE || type == ChannelType.STAGE) "SALONS VOCAUX" else "SALONS TEXTUELS"
        }

        val channel = if (existing != null) {
            existing.copy(
                name = cleanName,
                topic = topic.trim(),
                type = type,
                category = targetCat,
                iconKey = cleanIcon,
                slowmodeSeconds = slowmodeSeconds.coerceIn(0, 3600),
                isReadOnly = isReadOnly,
                isPrivate = isPrivate,
                allowedRoles = if (isPrivate) allowedRoles else emptyList(),
                allowedRoleIds = if (isPrivate) allowedRoleIds else emptyList(),
                maxParticipants = maxParticipants.coerceIn(0, 100),
                bitrateKbps = bitrateKbps.coerceIn(8, 384)
            )
        } else {
            val nextPos = (srv.channels.maxOfOrNull { it.position } ?: -1) + 1
            Channel(
                id = "c_${UUID.randomUUID().toString().take(6)}",
                serverId = serverId,
                name = cleanName,
                type = type,
                category = targetCat,
                topic = topic.trim(),
                isPrivate = isPrivate,
                maxParticipants = maxParticipants.coerceIn(0, 100),
                bitrateKbps = bitrateKbps.coerceIn(8, 384),
                isReadOnly = isReadOnly,
                slowmodeSeconds = slowmodeSeconds.coerceIn(0, 3600),
                allowedRoles = if (isPrivate) allowedRoles else emptyList(),
                allowedRoleIds = if (isPrivate) allowedRoleIds else emptyList(),
                position = nextPos,
                iconKey = cleanIcon
            )
        }

        val nextChannels = if (existing != null) {
            srv.channels.map { if (it.id == channel.id) channel else it }
        } else {
            srv.channels + channel
        }.sortedBy { it.position }

        val updated = srv.copy(
            channels = nextChannels,
            auditLogs = appendAuditLog(srv, if (existing != null) "a personnalisé le salon #${channel.name}" else "a créé le salon #${channel.name}")
        )
        val cloudRes = pushSingleChannelToCloud(updated, channel)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(channel)
    }

    suspend fun moveChannelOrder(serverId: String, channelId: String, delta: Int): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireAdminAccess(srv, currentUid()).onFailure { return Result.failure(it) }

        val target = srv.channels.find { it.id == channelId }
            ?: return Result.failure(IllegalArgumentException("Salon introuvable."))
        val sameCategory = srv.channels.filter { it.category.equals(target.category, ignoreCase = true) }
            .sortedBy { it.position }
            .toMutableList()
        val idx = sameCategory.indexOfFirst { it.id == channelId }
        val nextIdx = (idx + delta).coerceIn(0, sameCategory.lastIndex)
        if (idx < 0 || idx == nextIdx) return Result.success(Unit)

        val moving = sameCategory.removeAt(idx)
        sameCategory.add(nextIdx, moving)
        val reorderedCategoryMap = sameCategory.mapIndexed { i, ch -> ch.id to i }.toMap()

        val otherChannels = srv.channels.filterNot { it.category.equals(target.category, ignoreCase = true) }
        val updatedChannels = (otherChannels + sameCategory.mapIndexed { i, ch -> ch.copy(position = i) })
            .sortedWith(compareBy<Channel> { it.category }.thenBy { it.position })
            .mapIndexed { globalIdx, ch ->
                val catPos = reorderedCategoryMap[ch.id]
                if (catPos != null) ch.copy(position = catPos) else ch.copy(position = globalIdx + 100)
            }
            .let { list ->
                val orderedCats = srv.categories.sortedBy { it.position }.map { it.name.uppercase() }
                list.sortedWith(
                    compareBy<Channel>(
                        { ch ->
                            val ci = orderedCats.indexOf(ch.category.uppercase())
                            if (ci >= 0) ci else Int.MAX_VALUE
                        },
                        { ch -> reorderedCategoryMap[ch.id] ?: ch.position }
                    )
                ).mapIndexed { finalPos, ch -> ch.copy(position = finalPos) }
            }

        val updated = srv.copy(
            channels = updatedChannels,
            auditLogs = appendAuditLog(srv, "a modifié l'ordre des salons (#${target.name})")
        )
        val changedChannels = updatedChannels.filter { it.category.equals(target.category, ignoreCase = true) }
        val cloudRes = pushCategoriesAndChannelsToCloud(updated, changedChannels)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(Unit)
    }

    suspend fun moveRoleOrder(serverId: String, roleId: String, delta: Int): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_ROLES)) {
            return Result.failure(SecurityException("Permission MANAGE_ROLES requise."))
        }
        val sorted = srv.customRoles.sortedBy { it.position }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == roleId }
        if (idx < 0) return Result.failure(IllegalArgumentException("Rôle introuvable."))
        val targetIdx = (idx + delta).coerceIn(0, sorted.lastIndex)
        if (idx == targetIdx) return Result.success(Unit)
        val moving = sorted.removeAt(idx)
        sorted.add(targetIdx, moving)
        val normalized = sorted.mapIndexed { pos, r -> r.copy(position = pos) }
        val updated = srv.copy(
            customRoles = normalized,
            auditLogs = appendAuditLog(srv, "a réorganisé la hiérarchie des rôles")
        )
        val cloudRes = communityRepo.pushServerRolesAndMetadata(updated)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(Unit)
    }

    suspend fun deleteCustomRole(serverId: String, roleId: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_ROLES)) {
            return Result.failure(SecurityException("Permission MANAGE_ROLES requise."))
        }
        val target = srv.customRoles.find { it.id == roleId }
            ?: return Result.failure(IllegalArgumentException("Rôle introuvable."))
        if (target.id in setOf("role_admin", "role_mod", "role_member")) {
            return Result.failure(IllegalStateException("Les rôles système de base ne peuvent pas être supprimés, mais leurs permissions sont modifiables."))
        }
        val nextRoles = srv.customRoles.filterNot { it.id == roleId }
            .mapIndexed { idx, r -> r.copy(position = idx) }
        val nextMembers = srv.members.map { m ->
            if (m.customRoleId == roleId) m.copy(customRoleId = null) else m
        }
        val nextCats = srv.categories.map { c ->
            c.copy(allowedRoleIds = c.allowedRoleIds.filterNot { it == roleId })
        }
        val updated = srv.copy(
            customRoles = nextRoles,
            members = nextMembers,
            categories = nextCats,
            auditLogs = appendAuditLog(srv, "a supprimé le rôle ${target.name}")
        )
        val cloudRes = communityRepo.pushServerRolesAndMetadata(updated)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(Unit)
    }

    suspend fun updateWelcomeAndGoodbyeConfig(
        serverId: String,
        welcomeConfig: ServerWelcomeConfig,
        homeWelcomeMessage: String
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireAdminAccess(srv, currentUid()).onFailure { return Result.failure(it) }

        val updated = srv.copy(
            homeWelcomeMessage = homeWelcomeMessage.trim().ifBlank { srv.homeWelcomeMessage },
            customization = srv.customization.copy(
                welcome = welcomeConfig.copy(
                    welcomeMessageTemplate = welcomeConfig.welcomeMessageTemplate.trim()
                        .ifBlank { "Bienvenue {user} sur {server} !" },
                    goodbyeMessageTemplate = welcomeConfig.goodbyeMessageTemplate.trim()
                        .ifBlank { "{user} a quitté {server}." }
                )
            ),
            auditLogs = appendAuditLog(srv, "a configuré les messages d'accueil et de départ")
        )
        val cloudRes = pushCustomizationToCloud(updated)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(updated)
    }

    fun dispatchMembershipSystemMessage(server: Server, memberName: String, isJoin: Boolean) {
        val cfg = server.customization.welcome
        if (isJoin && !cfg.welcomeEnabled) return
        if (!isJoin && !cfg.goodbyeEnabled) return

        val template = if (isJoin) cfg.welcomeMessageTemplate else cfg.goodbyeMessageTemplate
        val formatted = template
            .replace("{user}", memberName.ifBlank { "Nouveau membre" })
            .replace("{server}", server.name)

        val configuredChannelId = if (isJoin) cfg.welcomeChannelId else cfg.goodbyeChannelId
        val targetChannel = configuredChannelId?.let { cid -> server.channels.find { it.id == cid } }
            ?: server.channels.firstOrNull { it.type == ChannelType.TEXT && !it.isReadOnly }
            ?: server.channels.firstOrNull { it.type == ChannelType.TEXT }
            ?: server.channels.firstOrNull()

        if (targetChannel != null) {
            val sysMsg = Message(
                id = "sys_${UUID.randomUUID().toString().take(8)}",
                channelId = targetChannel.id,
                sender = User(
                    id = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id,
                    name = if (isJoin) "Accueil • ${server.name}" else "Départ • ${server.name}",
                    avatarColor = server.iconBgColor
                ),
                text = formatted,
                timestamp = CommunityPermissions.nowTimeLabel()
            )
            chatRepo.appendSystemChannelMessage(targetChannel.id, sysMsg)
        }

        if (isJoin && cfg.dmWelcomeEnabled) {
            chatRepo.notifyCommunityEvent(
                title = "Bienvenue sur ${server.name}",
                body = formatted,
                sourceLabel = server.name,
                type = NotificationType.SYSTEM
            )
        }
    }

    suspend fun updateNotificationConfig(
        serverId: String,
        config: ServerNotificationConfig
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireAdminAccess(srv, currentUid()).onFailure { return Result.failure(it) }

        val updated = srv.copy(
            customization = srv.customization.copy(notifications = config),
            auditLogs = appendAuditLog(srv, "a mis à jour les notifications (${config.defaultLevel.label})")
        )
        val cloudRes = pushCustomizationToCloud(updated)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(updated)
    }

    suspend fun updateRulesAndVerification(
        serverId: String,
        rulesList: List<String>,
        rulesGuidelines: String,
        requireRulesAcceptance: Boolean,
        verificationLevel: ServerVerificationLevel
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireStrictAdmin(srv, currentUid()).onFailure { return Result.failure(it) }

        val cleanRules = rulesList.map { it.trim() }.filter { it.isNotBlank() }
        val joinedGuidelines = rulesGuidelines.trim().ifBlank {
            if (cleanRules.isNotEmpty()) cleanRules.joinToString("\n") else srv.rulesGuidelines
        }
        val updated = srv.copy(
            rulesGuidelines = joinedGuidelines,
            serverRulesList = cleanRules.ifEmpty { joinedGuidelines.split("\n").map { it.trim() }.filter { it.isNotBlank() } },
            requireRulesAcceptance = requireRulesAcceptance,
            customization = srv.customization.copy(
                verificationLevel = verificationLevel,
                requireRulesAcceptance = requireRulesAcceptance
            ),
            auditLogs = appendAuditLog(srv, "a mis à jour les règles & vérification (${verificationLevel.label})")
        )
        val cloudRes = pushCustomizationToCloud(updated, includeSecurityDoc = true, includePublicPolicyDoc = true)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(updated)
    }

    suspend fun updateModerationConfig(
        serverId: String,
        config: ServerModerationConfig
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireStrictAdmin(srv, currentUid()).onFailure { return Result.failure(it) }

        val cleanConfig = config.copy(
            blockedWords = config.blockedWords.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct(),
            defaultSlowmodeSeconds = config.defaultSlowmodeSeconds.coerceIn(0, 600),
            maxMentionsPerMessage = config.maxMentionsPerMessage.coerceIn(1, 50)
        )
        val updated = srv.copy(
            customization = srv.customization.copy(moderation = cleanConfig),
            auditLogs = appendAuditLog(srv, "a configuré la modération (Auto-Mod=${cleanConfig.autoModEnabled}, Anti-Raid=${cleanConfig.antiRaidModeEnabled})")
        )
        val cloudRes = pushCustomizationToCloud(updated, includeSecurityDoc = true)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        return Result.success(updated)
    }

    suspend fun updatePrivacyConfig(
        serverId: String,
        isPublic: Boolean,
        config: ServerPrivacyConfig
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        requireStrictAdmin(srv, currentUid()).onFailure { return Result.failure(it) }

        val updated = srv.copy(
            isPublic = isPublic,
            customization = srv.customization.copy(privacy = config),
            auditLogs = appendAuditLog(srv, "a modifié la confidentialité (Public=$isPublic, Invitations=${config.allowInviteLinks})")
        )
        val cloudRes = pushCustomizationToCloud(updated, includeSecurityDoc = true, includePublicPolicyDoc = true)
        if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        communityRepo.replaceServerInFlow(updated)
        communityRepo.syncPublicListingState(updated)
        return Result.success(updated)
    }

    suspend fun fetchStaffSecuritySettings(serverId: String): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
        val uid = currentUid()
        if (!CommunityPermissions.isOwner(srv, uid) &&
            CommunityPermissions.effectiveRole(srv, uid) !in setOf(UserRole.ADMIN, UserRole.MODERATOR)
        ) {
            return Result.failure(SecurityException("Accès réservé au staff du serveur."))
        }
        val db = chatRepo.firestoreDb() ?: return Result.success(srv)
        val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(srv)
        return withContext(Dispatchers.IO) {
            try {
                val doc = Tasks.await(
                    db.collection("servers").document(serverId)
                        .collection("admin_settings").document("security").get()
                )
                if (doc.exists()) {
                    val merged = CommunityFirestoreSync.applyAdminSecurityDoc(doc, srv)
                    communityRepo.replaceServerInFlow(merged)
                    Result.success(merged)
                } else {
                    Result.success(srv)
                }
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, "servers/$serverId/admin_settings/security", authUser.uid)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }

    private suspend fun pushCustomizationToCloud(
        updated: Server,
        syncHandleFrom: String? = null,
        includeSecurityDoc: Boolean = false,
        includePublicPolicyDoc: Boolean = false
    ): Result<Unit> {
        val db = chatRepo.firestoreDb() ?: return Result.success(Unit)
        val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            try {
                val memberIds = (updated.members.map { it.id } + updated.ownerId + authUser.uid).distinct()
                val srvRef = db.collection("servers").document(updated.id)
                Tasks.await(
                    srvRef.set(
                        CommunityFirestoreSync.serverToMap(updated, updated.ownerId.ifBlank { authUser.uid }, memberIds),
                        SetOptions.merge()
                    )
                )
                if (syncHandleFrom != null && updated.uniqueHandle != syncHandleFrom && updated.ownerId == authUser.uid) {
                    Tasks.await(db.collection("community_handles").document(updated.uniqueHandle).set(mapOf(
                        "handle" to updated.uniqueHandle,
                        "serverId" to updated.id,
                        "ownerId" to authUser.uid,
                        "name" to updated.name,
                        "isPublic" to updated.isPublic
                    )))
                    if (syncHandleFrom.isNotBlank()) {
                        runCatching { Tasks.await(db.collection("community_handles").document(syncHandleFrom).delete()) }
                    }
                }
                if (includeSecurityDoc) {
                    Tasks.await(
                        srvRef.collection("admin_settings").document("security")
                            .set(CommunityFirestoreSync.adminSecuritySettingsToMap(updated, authUser.uid), SetOptions.merge())
                    )
                }
                if (includePublicPolicyDoc) {
                    Tasks.await(
                        srvRef.collection("admin_settings").document("public_policy")
                            .set(CommunityFirestoreSync.adminPublicPolicyToMap(updated, authUser.uid), SetOptions.merge())
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${updated.id}", authUser.uid)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }

    private suspend fun pushCategoriesAndChannelsToCloud(updated: Server, changedChannels: List<Channel>): Result<Unit> {
        val db = chatRepo.firestoreDb() ?: return Result.success(Unit)
        val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            try {
                val srvRef = db.collection("servers").document(updated.id)
                Tasks.await(
                    srvRef.update(
                        mapOf(
                            "categories" to updated.categories.sortedBy { it.position }.map {
                                mapOf("id" to it.id, "name" to it.name, "position" to it.position, "isPrivate" to it.isPrivate, "allowedRoleIds" to it.allowedRoleIds)
                            },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                )
                val memberIds = (updated.members.map { it.id } + updated.ownerId + authUser.uid).distinct()
                changedChannels.forEach { ch ->
                    Tasks.await(
                        srvRef.collection("channels").document(ch.id)
                            .set(CommunityFirestoreSync.channelToMap(ch, updated.id, updated.isPublic, memberIds), SetOptions.merge())
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${updated.id}", authUser.uid)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }

    private suspend fun pushSingleChannelToCloud(updated: Server, channel: Channel): Result<Unit> {
        val db = chatRepo.firestoreDb() ?: return Result.success(Unit)
        val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            try {
                val memberIds = (updated.members.map { it.id } + updated.ownerId + authUser.uid).distinct()
                Tasks.await(
                    db.collection("servers").document(updated.id)
                        .collection("channels").document(channel.id)
                        .set(CommunityFirestoreSync.channelToMap(channel, updated.id, updated.isPublic, memberIds), SetOptions.merge())
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${updated.id}/channels/${channel.id}", authUser.uid)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }
}
