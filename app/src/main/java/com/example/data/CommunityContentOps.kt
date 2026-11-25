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

suspend fun CommunityRepository.saveCustomRole(
    serverId: String,
    roleName: String,
    colorHex: Long,
    secondaryColorHex: Long? = null,
    permissions: Set<RolePermission>,
    existingRoleId: String? = null
): Result<CustomRole> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_ROLES)) {
        return Result.failure(SecurityException("Permission MANAGE_ROLES requise."))
    }
    val clean = roleName.trim()
    if (clean.isBlank()) return Result.failure(IllegalArgumentException("Nom de rôle requis."))

    val isAdmin = RolePermission.MANAGE_COMMUNITY in permissions || RolePermission.MANAGE_ROLES in permissions
    if (isAdmin && !CommunityPermissions.isOwner(srv, currentUid())) {
        return Result.failure(SecurityException("Seul le créateur du serveur ou de la communauté peut créer un rôle administrateur."))
    }
    val canKick = RolePermission.MODERATE_MEMBERS in permissions
    val canManageCh = RolePermission.MANAGE_CHANNELS in permissions
    val role = CustomRole(
        id = existingRoleId ?: "role_${UUID.randomUUID().toString().take(6)}",
        name = clean,
        colorHex = colorHex,
        secondaryColorHex = secondaryColorHex,
        isAdmin = isAdmin,
        canKickBan = canKick,
        canManageChannels = canManageCh,
        permissions = permissions,
        position = srv.customRoles.size
    )
    val nextRoles = if (srv.customRoles.any { it.id == role.id }) {
        srv.customRoles.map { if (it.id == role.id) role else it }
    } else {
        srv.customRoles + role
    }
    val updated = srv.copy(customRoles = nextRoles)
    val dbRes = pushServerRolesAndMetadata(updated)
    if (dbRes.isFailure) return Result.failure(dbRes.exceptionOrNull()!!)
    replaceServerInFlow(updated)
    return Result.success(role)
}

suspend fun CommunityRepository.assignMemberRole(
    serverId: String,
    targetUid: String,
    role: UserRole,
    customRoleId: String? = null
): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    val actorUid = currentUid()
    val customRole = customRoleId?.let { rid -> srv.customRoles.find { it.id == rid } }
    if (targetUid == srv.ownerId) {
        return Result.failure(SecurityException("Le rôle du créateur du serveur ne peut pas être modifié."))
    }
    if (!CommunityPermissions.canManageAdminRole(srv, actorUid, targetUid, role, customRole)) {
        return Result.failure(SecurityException("Seul le créateur du serveur ou de la communauté peut nommer ou retirer un administrateur."))
    }

    val isActorOwner = CommunityPermissions.isOwner(srv, actorUid)
    val nextAdmins = when (role) {
        UserRole.ADMIN -> (srv.adminIds + targetUid).distinct()
        else -> srv.adminIds - targetUid
    }
    val nextMods = when (role) {
        UserRole.MODERATOR -> (srv.moderatorIds + targetUid).distinct()
        else -> srv.moderatorIds - targetUid
    }
    val customPerms = customRole?.permissions
        ?: when (role) {
            UserRole.ADMIN -> RolePermission.entries.filter { it != RolePermission.DELETE_COMMUNITY }.toSet()
            UserRole.MODERATOR -> defaultPermissionsForFlags(false, canKickBan = true, canManageChannels = true)
            else -> setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE)
        }
    val nextMembers = srv.members.map {
        if (it.id == targetUid) it.copy(role = role, customRoleId = customRoleId) else it
    }
    val targetName = srv.members.find { it.id == targetUid }?.name ?: "Membre"
    val wasAdmin = CommunityPermissions.effectiveRole(srv, targetUid) == UserRole.ADMIN
    val actionDesc = when {
        role == UserRole.ADMIN -> "a nommé administrateur"
        wasAdmin && role != UserRole.ADMIN -> "a retiré le titre d'administrateur à"
        else -> "a attribué le rôle ${customRole?.name ?: role.title} à"
    }
    val logEntry = AuditLogEntry(
        id = "log_${UUID.randomUUID().toString().take(8)}",
        actorName = currentUserName(),
        actionDescription = actionDesc,
        targetName = targetName,
        timestamp = CommunityPermissions.nowTimeLabel()
    )
    val updated = srv.copy(
        adminIds = nextAdmins,
        moderatorIds = nextMods,
        members = nextMembers,
        auditLogs = (listOf(logEntry) + srv.auditLogs).take(40)
    )

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val srvRef = db.collection("servers").document(serverId)
                val srvUpdate = hashMapOf<String, Any>(
                    "moderatorIds" to nextMods,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isActorOwner) {
                    srvUpdate["adminIds"] = nextAdmins
                }
                Tasks.await(srvRef.update(srvUpdate))
                val targetMember = nextMembers.find { it.id == targetUid } ?: User(id = targetUid, name = "Membre")
                Tasks.await(
                    srvRef.collection("members").document(targetUid).set(
                        CommunityFirestoreSync.memberToMap(targetMember, serverId, role, customPerms, srv.isPublic),
                        SetOptions.merge()
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId/members/$targetUid", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(updated)
    return Result.success(Unit)
}

suspend fun CommunityRepository.setServerClosed(
    serverId: String,
    closed: Boolean
): Result<Boolean> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Serveur introuvable."))
    val actorUid = currentUid()
    if (!CommunityPermissions.canToggleServerClosed(srv, actorUid)) {
        return Result.failure(SecurityException("Seuls les administrateurs et le créateur peuvent fermer ou ouvrir ce serveur."))
    }
    val actorName = currentUserName()
    val nowMs = System.currentTimeMillis()
    val closedByUidVal = if (closed) actorUid else null
    val closedByNameVal = if (closed) actorName else null
    val closedAtMsVal = if (closed) nowMs else null
    val logEntry = AuditLogEntry(
        id = "log_${UUID.randomUUID().toString().take(8)}",
        actorName = actorName,
        actionDescription = if (closed) "a fermé le serveur (admins uniquement)" else "a rouvert le serveur à tous les membres",
        targetName = srv.name,
        timestamp = CommunityPermissions.nowTimeLabel()
    )
    val updated = srv.copy(
        isClosed = closed,
        closedByUid = closedByUidVal,
        closedByName = closedByNameVal,
        closedAtMs = closedAtMsVal,
        auditLogs = (listOf(logEntry) + srv.auditLogs).take(40)
    )

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val payload = hashMapOf<String, Any?>(
                    "isClosed" to closed,
                    "closedByUid" to closedByUidVal,
                    "closedByName" to closedByNameVal,
                    "closedAtMs" to closedAtMsVal,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                Tasks.await(db.collection("servers").document(serverId).update(payload))
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(updated)
    return Result.success(closed)
}

suspend fun CommunityRepository.timeoutMember(serverId: String, targetUid: String): Result<Boolean> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MODERATE_MEMBERS)) {
        return Result.failure(SecurityException("Permission MODERATE_MEMBERS requise."))
    }
    if (targetUid == srv.ownerId || targetUid == currentUid()) {
        return Result.failure(IllegalStateException("Action impossible sur ce membre."))
    }
    val target = srv.members.find { it.id == targetUid }
        ?: return Result.failure(IllegalArgumentException("Membre introuvable."))
    val nextTimedOut = !target.isTimedOut

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("servers").document(serverId).collection("members").document(targetUid)
                        .update(mapOf("isTimedOut" to nextTimedOut, "updatedAt" to FieldValue.serverTimestamp()))
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId/members/$targetUid", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }

    val updatedMembers = srv.members.map { if (it.id == targetUid) it.copy(isTimedOut = nextTimedOut) else it }
    replaceServerInFlow(srv.copy(members = updatedMembers))
    return Result.success(nextTimedOut)
}

suspend fun CommunityRepository.kickMember(serverId: String, targetUid: String): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MODERATE_MEMBERS)) {
        return Result.failure(SecurityException("Permission MODERATE_MEMBERS requise."))
    }
    if (targetUid == srv.ownerId || targetUid == currentUid()) {
        return Result.failure(IllegalStateException("Impossible d'expulser ce membre."))
    }

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val srvRef = db.collection("servers").document(serverId)
                Tasks.await(srvRef.update(mapOf(
                    "memberIds" to FieldValue.arrayRemove(targetUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )))
                runCatching { Tasks.await(srvRef.collection("members").document(targetUid).delete()) }
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "servers/$serverId/members/$targetUid", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }

    val kickedName = srv.members.find { it.id == targetUid }?.name ?: "Membre"
    customizationHelper.dispatchMembershipSystemMessage(srv, kickedName, isJoin = false)
    replaceServerInFlow(srv.copy(members = srv.members.filterNot { it.id == targetUid }))
    return Result.success(Unit)
}

suspend fun CommunityRepository.createCategory(
    serverId: String,
    categoryName: String,
    isPrivate: Boolean = false
): Result<ChannelCategory> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_CHANNELS)) {
        return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
    }
    val clean = categoryName.trim().uppercase()
    if (clean.isBlank()) return Result.failure(IllegalArgumentException("Nom de catégorie vide."))
    val cat = ChannelCategory("cat_${UUID.randomUUID().toString().take(6)}", clean, srv.categories.size, isPrivate)
    val updated = srv.copy(categories = srv.categories + cat)
    val syncRes = pushCategoriesToCloud(updated)
    if (syncRes.isFailure) return Result.failure(syncRes.exceptionOrNull()!!)
    replaceServerInFlow(updated)
    return Result.success(cat)
}

suspend fun CommunityRepository.deleteCategory(serverId: String, categoryName: String): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_CHANNELS)) {
        return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
    }
    val fallbackCategory = "SALONS TEXTUELS"
    val updated = srv.copy(
        categories = srv.categories.filterNot { it.name.equals(categoryName, ignoreCase = true) },
        channels = srv.channels.map {
            if (it.category.equals(categoryName, ignoreCase = true)) it.copy(category = fallbackCategory) else it
        }
    )
    val syncRes = pushCategoriesToCloud(updated)
    if (syncRes.isFailure) return Result.failure(syncRes.exceptionOrNull()!!)
    replaceServerInFlow(updated)
    return Result.success(Unit)
}

private suspend fun CommunityRepository.pushCategoriesToCloud(updated: Server): Result<Unit> {
    val db = chatRepo.firestoreDb() ?: return Result.success(Unit)
    val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(Unit)
    return withContext(Dispatchers.IO) {
        try {
            Tasks.await(db.collection("servers").document(updated.id).update(
                mapOf(
                    "categories" to updated.categories.map {
                        mapOf("id" to it.id, "name" to it.name, "position" to it.position, "isPrivate" to it.isPrivate, "allowedRoleIds" to it.allowedRoleIds)
                    },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ))
            Result.success(Unit)
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${updated.id}", authUser.uid)
            Result.failure(IllegalStateException(msg, e))
        }
    }
}

suspend fun CommunityRepository.createChannel(
    serverId: String,
    name: String,
    type: ChannelType,
    categoryName: String,
    isPrivate: Boolean = false,
    allowedRoles: List<UserRole> = emptyList()
): Result<Channel> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_CHANNELS)) {
        return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
    }
    val clean = name.trim().lowercase().replace(" ", "-")
    if (clean.isBlank()) return Result.failure(IllegalArgumentException("Nom de salon requis."))
    val catLabel = categoryName.ifBlank { if (type == ChannelType.VOICE) "SALONS VOCAUX" else "SALONS TEXTUELS" }
    val nextPos = (srv.channels.maxOfOrNull { it.position } ?: -1) + 1
    val defaultIcon = when (type) {
        ChannelType.ANNOUNCEMENT -> "announcement"
        ChannelType.VOICE -> "mic"
        ChannelType.STAGE -> "stage"
        ChannelType.FORUM -> "forum"
        ChannelType.TEXT -> "tag"
    }
    val ch = Channel(
        id = "c_${UUID.randomUUID().toString().take(6)}",
        serverId = serverId,
        name = clean,
        type = type,
        category = catLabel,
        isPrivate = isPrivate,
        allowedRoles = if (isPrivate) allowedRoles.ifEmpty { listOf(UserRole.ADMIN, UserRole.MODERATOR) } else emptyList(),
        position = nextPos,
        iconKey = defaultIcon
    )
    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val memberIds = (srv.members.map { it.id } + authUser.uid).distinct()
                Tasks.await(
                    db.collection("servers").document(serverId).collection("channels").document(ch.id)
                        .set(CommunityFirestoreSync.channelToMap(ch, serverId, srv.isPublic, memberIds))
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "servers/$serverId/channels/${ch.id}", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(srv.copy(channels = srv.channels + ch))
    return Result.success(ch)
}

suspend fun CommunityRepository.deleteChannel(serverId: String, channelId: String): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_CHANNELS)) {
        return Result.failure(SecurityException("Permission MANAGE_CHANNELS requise."))
    }
    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                Tasks.await(db.collection("servers").document(serverId).collection("channels").document(channelId).delete())
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "servers/$serverId/channels/$channelId", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(srv.copy(channels = srv.channels.filterNot { it.id == channelId }))
    return Result.success(Unit)
}

suspend fun CommunityRepository.publishAnnouncement(
    serverId: String,
    title: String,
    content: String,
    isPinned: Boolean = true
): Result<CommunityAnnouncement> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    val uid = currentUid()
    if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.POST_ANNOUNCEMENTS)) {
        return Result.failure(SecurityException("Permission POST_ANNOUNCEMENTS requise."))
    }
    val cleanTitle = title.trim()
    val cleanContent = content.trim()
    if (cleanTitle.isBlank() || cleanContent.isBlank()) {
        return Result.failure(IllegalArgumentException("Titre et contenu requis."))
    }
    val roleTitle = CommunityPermissions.effectiveRole(srv, uid).title
    val ann = CommunityAnnouncement(
        id = "ann_${UUID.randomUUID().toString().take(8)}",
        serverId = serverId,
        title = cleanTitle,
        content = cleanContent,
        authorId = uid,
        authorName = currentUserName(),
        authorRoleTitle = roleTitle,
        isPinned = isPinned,
        timestampLabel = CommunityPermissions.nowTimeLabel(),
        createdAtMs = System.currentTimeMillis()
    )
    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val memberIds = (srv.members.map { it.id } + authUser.uid).distinct()
                Tasks.await(
                    db.collection("servers").document(serverId).collection("announcements").document(ann.id).set(
                        mapOf(
                            "id" to ann.id,
                            "serverId" to serverId,
                            "title" to ann.title,
                            "content" to ann.content,
                            "authorId" to authUser.uid,
                            "authorName" to ann.authorName,
                            "authorRoleTitle" to ann.authorRoleTitle,
                            "isPinned" to ann.isPinned,
                            "timestampLabel" to ann.timestampLabel,
                            "createdAtMs" to ann.createdAtMs,
                            "isPublic" to srv.isPublic,
                            "memberIds" to memberIds
                        )
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "servers/$serverId/announcements/${ann.id}", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }

    replaceServerInFlow(srv.copy(announcements = listOf(ann) + srv.announcements))
    val notifCfg = srv.customization.notifications
    if (notifCfg.defaultLevel != ServerNotificationLevel.NONE || notifCfg.highlightAnnouncements) {
        chatRepo.notifyCommunityEvent("Annonce : $cleanTitle", cleanContent.take(120), srv.name)
    }
    return Result.success(ann)
}

suspend fun CommunityRepository.deleteAnnouncement(serverId: String, announcementId: String): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.POST_ANNOUNCEMENTS)) {
        return Result.failure(SecurityException("Permission POST_ANNOUNCEMENTS requise."))
    }
    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                Tasks.await(db.collection("servers").document(serverId).collection("announcements").document(announcementId).delete())
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "servers/$serverId/announcements/$announcementId", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(srv.copy(announcements = srv.announcements.filterNot { it.id == announcementId }))
    return Result.success(Unit)
}

suspend fun CommunityRepository.createCommunityEvent(
    serverId: String,
    title: String,
    description: String,
    dateTimeLabel: String,
    locationLabel: String
): Result<ServerEvent> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    val uid = currentUid()
    if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_EVENTS)) {
        return Result.failure(SecurityException("Permission MANAGE_EVENTS requise."))
    }
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank()) return Result.failure(IllegalArgumentException("Titre d'événement requis."))
    val evt = ServerEvent(
        id = "evt_${UUID.randomUUID().toString().take(6)}",
        title = cleanTitle,
        description = description.trim().ifBlank { "Événement communautaire." },
        dateTimeLabel = dateTimeLabel.trim().ifBlank { "Ce week-end • 20:00" },
        locationLabel = locationLabel.trim().ifBlank { "Vocal Principal" },
        creatorName = currentUserName(),
        interestedUserIds = listOf(uid)
    )
    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        val res = withContext(Dispatchers.IO) {
            try {
                val memberIds = (srv.members.map { it.id } + authUser.uid).distinct()
                Tasks.await(
                    db.collection("servers").document(serverId).collection("events").document(evt.id).set(
                        mapOf(
                            "id" to evt.id,
                            "serverId" to serverId,
                            "title" to evt.title,
                            "description" to evt.description,
                            "dateTimeLabel" to evt.dateTimeLabel,
                            "locationLabel" to evt.locationLabel,
                            "creatorId" to authUser.uid,
                            "creatorName" to evt.creatorName,
                            "interestedUserIds" to evt.interestedUserIds,
                            "isPublic" to srv.isPublic,
                            "memberIds" to memberIds
                        )
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "servers/$serverId/events/${evt.id}", authUser.uid)
                Result.failure<Unit>(IllegalStateException(msg, e))
            }
        }
        if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
    }
    replaceServerInFlow(srv.copy(events = listOf(evt) + srv.events))
    chatRepo.notifyCommunityEvent("Événement : $cleanTitle", "${evt.dateTimeLabel} • ${evt.locationLabel}", srv.name)
    chatRepo.botCommunityEventHandler?.invoke(serverId, "ON_EVENT_CREATED", uid, currentUserName(), "${evt.title} (${evt.dateTimeLabel})")
    return Result.success(evt)
}

suspend fun CommunityRepository.toggleEventInterest(serverId: String, eventId: String): Result<Unit> {
    val srv = chatRepo.servers.value.find { it.id == serverId }
        ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
    val evt = srv.events.find { it.id == eventId }
        ?: return Result.failure(IllegalArgumentException("Événement introuvable."))
    val uid = currentUid()
    val nextInterested = if (uid in evt.interestedUserIds) evt.interestedUserIds - uid else evt.interestedUserIds + uid

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(
                    db.collection("servers").document(serverId).collection("events").document(eventId)
                        .update(mapOf("interestedUserIds" to nextInterested, "updatedAt" to FieldValue.serverTimestamp()))
                )
            }.onFailure { e ->
                FirestoreErrorHandler.handleFirestoreError(e as Exception, OperationType.UPDATE, "servers/$serverId/events/$eventId", authUser.uid)
            }
        }
    }
    replaceServerInFlow(srv.copy(events = srv.events.map { if (it.id == eventId) it.copy(interestedUserIds = nextInterested) else it }))
    return Result.success(Unit)
}

suspend fun CommunityRepository.searchCommunities(query: String, filter: CommunitySearchFilter): List<PublicServerListing> {
    val q = query.trim().removePrefix("@")
    val uid = currentUid()
    val localServers = chatRepo.servers.value.filterNot { it.isDmTab }
    val localListings = localServers.map { CommunityPermissions.serverToPublicListing(it) }
    val allMerged = (localListings + chatRepo.discoverServers.value).distinctBy { it.id }.toMutableList()

    val db = chatRepo.firestoreDb()
    val authUser = chatRepo.authInstance()?.currentUser
    if (db != null && authUser != null) {
        withContext(Dispatchers.IO) {
            runCatching {
                val snap = Tasks.await(db.collection("servers").whereEqualTo("isPublic", true).limit(30).get())
                snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToServer(it) }.forEach { srv ->
                    if (allMerged.none { it.id == srv.id }) {
                        allMerged.add(CommunityPermissions.serverToPublicListing(srv))
                    }
                }
            }
        }
    }

    val myIds = localServers.map { it.id }.toSet()
    return allMerged.filter { item ->
        val srvObj = localServers.find { it.id == item.id }
        val discoverableOk = filter == CommunitySearchFilter.MY_COMMUNITIES ||
            srvObj == null ||
            (srvObj.isPublic && srvObj.customization.privacy.discoverableInCatalog)
        val filterOk = when (filter) {
            CommunitySearchFilter.ALL -> true
            CommunitySearchFilter.PUBLIC_ONLY -> true
            CommunitySearchFilter.VERIFIED -> item.isVerified
            CommunitySearchFilter.MY_COMMUNITIES -> item.id in myIds
        }
        val queryOk = q.isBlank() ||
            item.name.contains(q, ignoreCase = true) ||
            item.description.contains(q, ignoreCase = true) ||
            item.categoryTag.contains(q, ignoreCase = true) ||
            (srvObj?.uniqueHandle?.contains(q, ignoreCase = true) == true)
        discoverableOk && filterOk && queryOk && (filter != CommunitySearchFilter.MY_COMMUNITIES || srvObj == null || uid in srvObj.members.map { it.id } || srvObj.ownerId == uid || srvObj.id == "server_main")
    }
}

fun CommunityRepository.startListeningCommunity(serverId: String) {
    releaseListeners()
    if (serverId.isBlank() || serverId == "dm_tab") return
    val db = chatRepo.firestoreDb() ?: return
    val authUser = chatRepo.authInstance()?.currentUser ?: return
    val srv = chatRepo.servers.value.find { it.id == serverId } ?: return
    val srvRef = db.collection("servers").document(serverId)

    membersListener = srvRef.collection("members").addSnapshotListener { snap, err ->
        if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener
        val members = snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToUserMember(it) }
        if (members.isNotEmpty()) {
            chatRepo.servers.value.find { it.id == serverId }?.let { curr ->
                replaceServerInFlow(curr.copy(members = members))
            }
        }
    }

    val chQuery = if (srv.isPublic) srvRef.collection("channels").whereEqualTo("isPublic", true)
    else srvRef.collection("channels").whereArrayContains("memberIds", authUser.uid)
    channelsListener = chQuery.addSnapshotListener { snap, err ->
        if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener
        val remoteChannels = snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToChannel(it) }
            .sortedBy { it.position }
        if (remoteChannels.isNotEmpty()) {
            chatRepo.servers.value.find { it.id == serverId }?.let { curr ->
                replaceServerInFlow(curr.copy(channels = remoteChannels))
            }
        }
    }

    val annQuery = if (srv.isPublic) srvRef.collection("announcements").whereEqualTo("isPublic", true)
    else srvRef.collection("announcements").whereArrayContains("memberIds", authUser.uid)
    announcementsListener = annQuery.addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val anns = snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToAnnouncement(it) }.sortedByDescending { it.createdAtMs }
        chatRepo.servers.value.find { it.id == serverId }?.let { curr ->
            replaceServerInFlow(curr.copy(announcements = anns))
        }
    }

    val evtQuery = if (srv.isPublic) srvRef.collection("events").whereEqualTo("isPublic", true)
    else srvRef.collection("events").whereArrayContains("memberIds", authUser.uid)
    eventsListener = evtQuery.addSnapshotListener { snap, err ->
        if (err != null || snap == null) return@addSnapshotListener
        val evts = snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToEvent(it) }
        if (evts.isNotEmpty()) {
            chatRepo.servers.value.find { it.id == serverId }?.let { curr ->
                replaceServerInFlow(curr.copy(events = evts))
            }
        }
    }

    if (CommunityPermissions.hasPermission(srv, authUser.uid, RolePermission.MANAGE_INVITES)) {
        invitesListener = db.collection("community_invites").whereEqualTo("serverId", serverId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val invs = snap.documents.mapNotNull { CommunityFirestoreSync.mapDocToInvite(it) }
                chatRepo.servers.value.find { it.id == serverId }?.let { curr ->
                    replaceServerInFlow(curr.copy(invites = invs))
                }
            }
    }
}

fun CommunityRepository.releaseListeners() {
    membersListener?.remove()
    channelsListener?.remove()
    announcementsListener?.remove()
    eventsListener?.remove()
    invitesListener?.remove()
    membersListener = null
    channelsListener = null
    announcementsListener = null
    eventsListener = null
    invitesListener = null
}
