package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class CommunityRepository(internal val chatRepo: ChatRepository) {

    internal var membersListener: ListenerRegistration? = null
    internal var announcementsListener: ListenerRegistration? = null
    internal var eventsListener: ListenerRegistration? = null
    internal var invitesListener: ListenerRegistration? = null
    internal var channelsListener: ListenerRegistration? = null
    internal val customizationHelper: ServerCustomizationRepository by lazy { ServerCustomizationRepository(this) }

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    internal fun currentUserName(): String =
        chatRepo.currentUser.value.name.ifBlank { "Membre" }

    fun hasPermission(serverId: String, permission: RolePermission): Boolean {
        val srv = chatRepo.servers.value.find { it.id == serverId } ?: return false
        return CommunityPermissions.hasPermission(srv, currentUid(), permission)
    }

    suspend fun isHandleAvailable(rawHandle: String, excludeServerId: String? = null): Boolean {
        val handle = CommunityPermissions.normalizeHandle(rawHandle)
        if (!CommunityPermissions.isValidHandleFormat(handle)) return false
        if (chatRepo.servers.value.any { !it.isDmTab && it.id != excludeServerId && it.uniqueHandle.equals(handle, ignoreCase = true) }) {
            return false
        }
        val db = chatRepo.firestoreDb()
        val uid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && uid != null) {
            return withContext(Dispatchers.IO) {
                try {
                    val doc = Tasks.await(db.collection("community_handles").document(handle).get())
                    !doc.exists() || doc.getString("serverId") == excludeServerId
                } catch (_: Exception) {
                    false
                }
            }
        }
        return true
    }

    suspend fun createCommunity(
        name: String,
        description: String,
        uniqueHandleRaw: String,
        iconText: String = "",
        imageUrl: String? = null,
        bannerUrl: String? = null,
        bannerColorHex: Long = 0xFF5865F2L,
        isPublic: Boolean = true,
        categoryTag: String = "Communauté",
        rulesGuidelines: String = "Respect, entraide et bienveillance entre membres.",
        homeWelcomeMessage: String = "Bienvenue dans notre nouvelle communauté Raven !"
    ): Result<Server> {
        val cleanName = name.trim()
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Le nom doit contenir au moins 2 caractères."))
        val normalizedHandle = CommunityPermissions.normalizeHandle(uniqueHandleRaw, cleanName)
        if (!isHandleAvailable(normalizedHandle)) {
            return Result.failure(IllegalStateException("L'identifiant @$normalizedHandle est déjà utilisé."))
        }

        val uid = currentUid()
        val serverId = "comm_${UUID.randomUUID().toString().take(8)}"
        val inviteCode = "RVN-${UUID.randomUUID().toString().take(6).uppercase()}"
        val cleanDesc = description.trim().ifBlank { "Communauté officielle $cleanName sur Raven." }
        val cleanIcon = iconText.ifBlank { cleanName.take(2).uppercase() }
        val timeLabel = CommunityPermissions.nowTimeLabel()

        val defaultCategories = listOf(
            ChannelCategory("cat_info_$serverId", "INFORMATIONS", 0),
            ChannelCategory("cat_text_$serverId", "SALONS TEXTUELS", 1),
            ChannelCategory("cat_voice_$serverId", "SALONS VOCAUX", 2)
        )
        val defaultChannels = listOf(
            Channel("${serverId}_annonces", serverId, "annonces", ChannelType.ANNOUNCEMENT, "INFORMATIONS", isReadOnly = true, position = 0, iconKey = "announcement"),
            Channel("${serverId}_general", serverId, "général", ChannelType.TEXT, "SALONS TEXTUELS", position = 1, iconKey = "chat"),
            Channel("${serverId}_voice", serverId, "Vocal Principal", ChannelType.VOICE, "SALONS VOCAUX", position = 2, iconKey = "mic")
        )
        val creatorUser = chatRepo.currentUser.value.copy(id = uid, role = UserRole.ADMIN)
        val initialInvite = CommunityInvite(
            id = inviteCode, code = inviteCode, serverId = serverId, serverName = cleanName,
            createdByUid = uid, createdByName = creatorUser.name, maxUses = 0, usesCount = 0,
            isRevoked = false, createdAtLabel = timeLabel
        )
        val resolvedRules = rulesGuidelines.ifBlank { "Respect, entraide et bienveillance." }
        val newServer = Server(
            id = serverId, name = cleanName, iconText = cleanIcon, iconBgColor = bannerColorHex,
            channels = defaultChannels, members = listOf(creatorUser), ownerId = uid,
            inviteCode = inviteCode, customBannerColorHex = bannerColorHex, isPublic = isPublic,
            categoryTag = categoryTag.ifBlank { "Communauté" }, description = cleanDesc,
            uniqueHandle = normalizedHandle, imageUrl = imageUrl?.takeIf { it.isNotBlank() },
            bannerUrl = bannerUrl?.takeIf { it.isNotBlank() },
            rulesGuidelines = resolvedRules,
            serverRulesList = resolvedRules.split("\n").map { it.trim() }.filter { it.isNotBlank() },
            homeWelcomeMessage = homeWelcomeMessage.ifBlank { "Bienvenue sur $cleanName !" },
            adminIds = listOf(uid), moderatorIds = emptyList(),
            categories = defaultCategories, invites = listOf(initialInvite),
            auditLogs = listOf(AuditLogEntry("log_init_$serverId", creatorUser.name, "a créé la communauté @$normalizedHandle", cleanName, timeLabel))
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudResult = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(serverId)
                    Tasks.await(db.collection("community_handles").document(normalizedHandle).set(mapOf(
                        "handle" to normalizedHandle, "serverId" to serverId, "ownerId" to authUser.uid,
                        "name" to cleanName, "isPublic" to isPublic, "createdAt" to FieldValue.serverTimestamp()
                    )))
                    Tasks.await(srvRef.set(CommunityFirestoreSync.serverToMap(newServer, authUser.uid, listOf(authUser.uid))))
                    Tasks.await(srvRef.collection("admin_settings").document("security").set(
                        CommunityFirestoreSync.adminSecuritySettingsToMap(newServer, authUser.uid)
                    ))
                    Tasks.await(srvRef.collection("admin_settings").document("public_policy").set(
                        CommunityFirestoreSync.adminPublicPolicyToMap(newServer, authUser.uid)
                    ))
                    Tasks.await(srvRef.collection("members").document(authUser.uid).set(
                        CommunityFirestoreSync.memberToMap(creatorUser, serverId, UserRole.ADMIN, RolePermission.entries.toSet(), isPublic)
                    ))
                    Tasks.await(db.collection("community_invites").document(inviteCode).set(
                        CommunityFirestoreSync.inviteToMap(initialInvite, isPublic)
                    ))
                    defaultChannels.forEach { ch ->
                        CommunityFirestoreSync.pushChannelSafe(db, serverId, ch, isPublic, listOf(authUser.uid))
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "servers/$serverId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudResult.isFailure) return Result.failure(cloudResult.exceptionOrNull()!!)
        }

        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value + newServer
        syncPublicListingState(newServer)
        chatRepo.selectServer(serverId)
        chatRepo.notifyCommunityEvent(
            title = "Communauté créée : $cleanName",
            body = "@$normalizedHandle est prête (${if (isPublic) "Publique" else "Privée"}). Code : $inviteCode",
            sourceLabel = cleanName
        )
        return Result.success(newServer)
    }

    suspend fun updateCommunity(
        serverId: String,
        name: String,
        description: String,
        uniqueHandleRaw: String,
        imageUrl: String? = null,
        bannerUrl: String? = null,
        bannerColorHex: Long? = null,
        isPublic: Boolean,
        categoryTag: String,
        rulesGuidelines: String,
        homeWelcomeMessage: String
    ): Result<Server> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_COMMUNITY)) {
            return Result.failure(SecurityException("Permission MANAGE_COMMUNITY requise."))
        }
        val cleanName = name.trim().ifBlank { srv.name }
        val newHandle = CommunityPermissions.normalizeHandle(uniqueHandleRaw, cleanName)
        if (newHandle != srv.uniqueHandle && !isHandleAvailable(newHandle, excludeServerId = serverId)) {
            return Result.failure(IllegalStateException("L'identifiant @$newHandle est déjà pris."))
        }

        val updated = srv.copy(
            name = cleanName,
            iconText = if (srv.iconText.length <= 2) cleanName.take(2).uppercase() else srv.iconText,
            description = description.trim().ifBlank { srv.description },
            uniqueHandle = newHandle,
            imageUrl = imageUrl?.trim()?.ifBlank { null },
            bannerUrl = bannerUrl?.trim()?.ifBlank { null },
            customBannerColorHex = bannerColorHex ?: srv.customBannerColorHex,
            isPublic = isPublic,
            categoryTag = categoryTag.trim().ifBlank { srv.categoryTag },
            rulesGuidelines = rulesGuidelines.trim().ifBlank { srv.rulesGuidelines },
            homeWelcomeMessage = homeWelcomeMessage.trim().ifBlank { srv.homeWelcomeMessage },
            auditLogs = listOf(
                AuditLogEntry("log_${UUID.randomUUID().toString().take(6)}", currentUserName(), "a mis à jour la communauté (@$newHandle)", cleanName, CommunityPermissions.nowTimeLabel())
            ) + srv.auditLogs
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val memberIds = (updated.members.map { it.id } + updated.ownerId + authUser.uid).distinct()
                    Tasks.await(
                        db.collection("servers").document(serverId)
                            .set(CommunityFirestoreSync.serverToMap(updated, updated.ownerId.ifBlank { authUser.uid }, memberIds), SetOptions.merge())
                    )
                    if (newHandle != srv.uniqueHandle && updated.ownerId == authUser.uid) {
                        Tasks.await(db.collection("community_handles").document(newHandle).set(mapOf(
                            "handle" to newHandle, "serverId" to serverId, "ownerId" to authUser.uid, "name" to cleanName, "isPublic" to isPublic
                        )))
                        if (srv.uniqueHandle.isNotBlank()) {
                            runCatching { Tasks.await(db.collection("community_handles").document(srv.uniqueHandle).delete()) }
                        }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        replaceServerInFlow(updated)
        syncPublicListingState(updated)
        return Result.success(updated)
    }

    suspend fun deleteCommunity(serverId: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (!CommunityPermissions.isOwner(srv, currentUid())) {
            return Result.failure(SecurityException("Seul le créateur peut supprimer cette communauté."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("servers").document(serverId).delete())
                    if (srv.uniqueHandle.isNotBlank()) {
                        runCatching { Tasks.await(db.collection("community_handles").document(srv.uniqueHandle).delete()) }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "servers/$serverId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        removeServerFromLocalState(serverId)
        chatRepo.notifyCommunityEvent("Communauté supprimée", "${srv.name} a été supprimée définitivement.", srv.name)
        return Result.success(Unit)
    }

    suspend fun leaveCommunity(serverId: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (CommunityPermissions.isOwner(srv, currentUid())) {
            return Result.failure(IllegalStateException("Le créateur ne peut pas quitter sa communauté. Supprimez-la si nécessaire."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(serverId)
                    val snap = Tasks.await(srvRef.get())
                    if (snap.exists()) {
                        Tasks.await(srvRef.update(mapOf(
                            "memberIds" to FieldValue.arrayRemove(authUser.uid),
                            "adminIds" to FieldValue.arrayRemove(authUser.uid),
                            "moderatorIds" to FieldValue.arrayRemove(authUser.uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )))
                        runCatching { Tasks.await(srvRef.collection("members").document(authUser.uid).delete()) }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/$serverId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        customizationHelper.dispatchMembershipSystemMessage(srv, currentUserName(), isJoin = false)
        removeServerFromLocalState(serverId)
        chatRepo.notifyCommunityEvent("Communauté quittée", "Vous avez quitté ${srv.name}.", srv.name)
        chatRepo.botCommunityEventHandler?.invoke(serverId, "ON_MEMBER_LEAVE", currentUid(), currentUserName(), srv.name)
        return Result.success(Unit)
    }

    suspend fun joinPublicCommunity(listing: PublicServerListing): Result<Server> {
        val existing = chatRepo.servers.value.find { !it.isDmTab && it.id == listing.id }
        if (existing != null) {
            chatRepo.selectServer(existing.id)
            return Result.success(existing)
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        var hydratedServer: Server? = null

        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    val srvRef = db.collection("servers").document(listing.id)
                    val snap = Tasks.await(srvRef.get())
                    if (snap.exists()) {
                        Tasks.await(srvRef.update(mapOf("memberIds" to FieldValue.arrayUnion(authUser.uid), "updatedAt" to FieldValue.serverTimestamp())))
                        val parsed = CommunityFirestoreSync.mapDocToServer(snap)
                        if (parsed != null) {
                            val memberUser = chatRepo.currentUser.value.copy(id = authUser.uid, role = UserRole.MEMBER)
                            Tasks.await(srvRef.collection("members").document(authUser.uid).set(
                                CommunityFirestoreSync.memberToMap(memberUser, parsed.id, UserRole.MEMBER, setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE), true)
                            ))
                            hydratedServer = parsed.copy(members = listOf(memberUser))
                        }
                    }
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${listing.id}", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }

        if (hydratedServer != null) {
            val flow = chatRepo.serversMutableFlow()
            flow.value = flow.value + hydratedServer!!
            chatRepo.selectServer(hydratedServer!!.id)
            customizationHelper.dispatchMembershipSystemMessage(hydratedServer!!, currentUserName(), isJoin = true)
            chatRepo.botCommunityEventHandler?.invoke(hydratedServer!!.id, "ON_MEMBER_JOIN", currentUid(), currentUserName(), hydratedServer!!.name)
            return Result.success(hydratedServer!!)
        }

        chatRepo.joinPublicServer(listing)
        val joined = chatRepo.servers.value.find { it.id == listing.id }
            ?: return Result.failure(IllegalStateException("Impossible de rejoindre la communauté."))
        customizationHelper.dispatchMembershipSystemMessage(joined, currentUserName(), isJoin = true)
        chatRepo.botCommunityEventHandler?.invoke(joined.id, "ON_MEMBER_JOIN", currentUid(), currentUserName(), joined.name)
        return Result.success(joined)
    }

    suspend fun joinByInviteCode(rawCode: String): Result<Server> {
        val code = rawCode.trim().uppercase()
        if (code.isBlank()) return Result.failure(IllegalArgumentException("Code d'invitation vide."))

        val localMatch = chatRepo.servers.value.find { srv ->
            !srv.isDmTab && (srv.inviteCode.equals(code, true) || srv.invites.any { it.code.equals(code, true) })
        }
        if (localMatch != null && !localMatch.customization.privacy.allowInviteLinks) {
            return Result.failure(SecurityException("Les invitations sont désactivées dans les paramètres de confidentialité."))
        }
        val localInvite = localMatch?.invites?.find { it.code.equals(code, true) }
        if (localInvite != null && !localInvite.isUsable()) {
            return Result.failure(IllegalStateException("Cette invitation est expirée, épuisée ou révoquée."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            return withContext(Dispatchers.IO) {
                try {
                    val inviteRef = db.collection("community_invites").document(code)
                    val inviteSnap = Tasks.await(inviteRef.get())
                    if (inviteSnap.exists()) {
                        val invite = CommunityFirestoreSync.mapDocToInvite(inviteSnap)
                            ?: return@withContext Result.failure(IllegalStateException("Invitation invalide."))
                        if (!invite.isUsable()) {
                            return@withContext Result.failure(IllegalStateException("Invitation révoquée, expirée ou limite atteinte."))
                        }
                        val srvRef = db.collection("servers").document(invite.serverId)
                        val preSnap = Tasks.await(srvRef.get())
                        val preParsed = CommunityFirestoreSync.mapDocToServer(preSnap)
                            ?: return@withContext Result.failure(IllegalStateException("Communauté introuvable."))
                        if (!preParsed.customization.privacy.allowInviteLinks) {
                            return@withContext Result.failure(SecurityException("Les invitations sont désactivées sur ce serveur."))
                        }
                        Tasks.await(inviteRef.update(mapOf("usesCount" to (invite.usesCount + 1), "updatedAt" to FieldValue.serverTimestamp())))
                        Tasks.await(srvRef.update(mapOf(
                            "memberIds" to FieldValue.arrayUnion(authUser.uid),
                            "lastInviteCodeUsed" to code,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )))
                        val memberUser = chatRepo.currentUser.value.copy(id = authUser.uid, role = UserRole.MEMBER)
                        Tasks.await(srvRef.collection("members").document(authUser.uid).set(
                            CommunityFirestoreSync.memberToMap(memberUser, preParsed.id, UserRole.MEMBER, setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE), preParsed.isPublic)
                        ))
                        val finalSrv = preParsed.copy(members = listOf(memberUser))
                        val flow = chatRepo.serversMutableFlow()
                        if (flow.value.none { it.id == finalSrv.id }) {
                            flow.value = flow.value + finalSrv
                        }
                        chatRepo.selectServer(finalSrv.id)
                        customizationHelper.dispatchMembershipSystemMessage(finalSrv, currentUserName(), isJoin = true)
                        chatRepo.notifyCommunityEvent("Invitation acceptée", "Vous avez rejoint ${finalSrv.name}.", finalSrv.name)
                        chatRepo.botCommunityEventHandler?.invoke(finalSrv.id, "ON_MEMBER_JOIN", authUser.uid, currentUserName(), finalSrv.name)
                        return@withContext Result.success(finalSrv)
                    }
                    Result.failure(IllegalStateException("Code d'invitation introuvable."))
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "community_invites/$code", authUser.uid)
                    Result.failure(IllegalStateException(msg, e))
                }
            }
        }

        if (localMatch != null) {
            chatRepo.selectServer(localMatch.id)
            customizationHelper.dispatchMembershipSystemMessage(localMatch, currentUserName(), isJoin = true)
            return Result.success(localMatch)
        }
        val ok = chatRepo.joinServerByInviteCode(code)
        val joined = chatRepo.servers.value.find { it.inviteCode.equals(code, true) }
        if (ok && joined != null) {
            customizationHelper.dispatchMembershipSystemMessage(joined, currentUserName(), isJoin = true)
            return Result.success(joined)
        }
        return Result.failure(IllegalStateException("Code invalide."))
    }

    suspend fun createInvite(serverId: String, maxUses: Int = 0, expiresInHours: Int = 0): Result<CommunityInvite> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (!srv.customization.privacy.allowInviteLinks) {
            return Result.failure(SecurityException("Les liens d'invitation sont désactivés sur ce serveur."))
        }
        val uid = currentUid()
        if (!CommunityPermissions.hasPermission(srv, uid, RolePermission.MANAGE_INVITES)) {
            return Result.failure(SecurityException("Permission MANAGE_INVITES requise."))
        }
        val code = "RVN-${UUID.randomUUID().toString().take(6).uppercase()}"
        val now = System.currentTimeMillis()
        val expiresAt = if (expiresInHours > 0) now + expiresInHours * 3600_000L else null
        val invite = CommunityInvite(
            id = code, code = code, serverId = serverId, serverName = srv.name,
            createdByUid = uid, createdByName = currentUserName(),
            maxUses = maxUses.coerceAtLeast(0), usesCount = 0,
            expiresInHours = expiresInHours.coerceAtLeast(0), expiresAtMs = expiresAt,
            isRevoked = false, createdAtLabel = CommunityPermissions.nowTimeLabel()
        )
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("community_invites").document(code).set(CommunityFirestoreSync.inviteToMap(invite, srv.isPublic)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "community_invites/$code", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        replaceServerInFlow(srv.copy(invites = listOf(invite) + srv.invites, inviteCode = code))
        return Result.success(invite)
    }

    suspend fun revokeInvite(serverId: String, code: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_INVITES)) {
            return Result.failure(SecurityException("Permission MANAGE_INVITES requise."))
        }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("community_invites").document(code).update(mapOf("isRevoked" to true, "updatedAt" to FieldValue.serverTimestamp())))
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "community_invites/$code", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        replaceServerInFlow(srv.copy(invites = srv.invites.map { if (it.code == code) it.copy(isRevoked = true) else it }))
        return Result.success(Unit)
    }

    suspend fun deleteInvite(serverId: String, code: String): Result<Unit> {
        val srv = chatRepo.servers.value.find { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("Communauté introuvable."))
        if (!CommunityPermissions.hasPermission(srv, currentUid(), RolePermission.MANAGE_INVITES)) {
            return Result.failure(SecurityException("Permission MANAGE_INVITES requise."))
        }
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("community_invites").document(code).delete())
                    Result.success(Unit)
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "community_invites/$code", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        replaceServerInFlow(srv.copy(invites = srv.invites.filterNot { it.code == code }))
        return Result.success(Unit)
    }

    internal suspend fun pushServerRolesAndMetadata(updated: Server): Result<Unit> {
        val db = chatRepo.firestoreDb() ?: return Result.success(Unit)
        val authUser = chatRepo.authInstance()?.currentUser ?: return Result.success(Unit)
        return withContext(Dispatchers.IO) {
            try {
                val memberIds = (updated.members.map { it.id } + updated.ownerId + authUser.uid).distinct()
                Tasks.await(
                    db.collection("servers").document(updated.id)
                        .set(CommunityFirestoreSync.serverToMap(updated, updated.ownerId.ifBlank { authUser.uid }, memberIds), SetOptions.merge())
                )
                Result.success(Unit)
            } catch (e: Exception) {
                val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "servers/${updated.id}", authUser.uid)
                Result.failure(IllegalStateException(msg, e))
            }
        }
    }

    internal fun replaceServerInFlow(updated: Server) {
        val flow = chatRepo.serversMutableFlow()
        flow.value = flow.value.map { if (it.id == updated.id) updated else it }
    }

    internal fun syncPublicListingState(updated: Server) {
        val discFlow = chatRepo.discoverMutableFlow()
        discFlow.value = if (updated.isPublic && updated.customization.privacy.discoverableInCatalog) {
            listOf(CommunityPermissions.serverToPublicListing(updated)) + discFlow.value.filterNot { it.id == updated.id }
        } else {
            discFlow.value.filterNot { it.id == updated.id }
        }
    }

    internal fun removeServerFromLocalState(serverId: String) {
        val flow = chatRepo.serversMutableFlow()
        val remaining = flow.value.filterNot { it.id == serverId }
        flow.value = remaining
        val discFlow = chatRepo.discoverMutableFlow()
        discFlow.value = discFlow.value.filterNot { it.id == serverId }
        if (chatRepo.selectedServerId.value == serverId) {
            val next = remaining.firstOrNull { !it.isDmTab } ?: remaining.firstOrNull()
            if (next != null) chatRepo.selectServer(next.id) else chatRepo.selectedServerMutableFlow().value = "dm_tab"
        }
    }
}
