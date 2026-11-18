package com.example.data

import android.util.Log
import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

internal object CommunityFirestoreSync {

    fun serverToMap(
        srv: Server,
        ownerUid: String,
        memberIds: List<String>
    ): HashMap<String, Any> = hashMapOf<String, Any>(
        "id" to srv.id,
        "name" to srv.name,
        "iconText" to srv.iconText,
        "iconBgColor" to srv.iconBgColor,
        "ownerId" to ownerUid,
        "adminIds" to (srv.adminIds + ownerUid).distinct(),
        "moderatorIds" to srv.moderatorIds.distinct(),
        "uniqueHandle" to srv.uniqueHandle,
        "inviteCode" to srv.inviteCode,
        "boostCount" to srv.boostCount,
        "boostLevel" to srv.boostLevel,
        "isPublic" to srv.isPublic,
        "isVerified" to srv.isVerified,
        "categoryTag" to srv.categoryTag,
        "description" to srv.description,
        "rulesGuidelines" to srv.rulesGuidelines,
        "homeWelcomeMessage" to srv.homeWelcomeMessage,
        "categories" to srv.categories.map { cat ->
            mapOf(
                "id" to cat.id,
                "name" to cat.name,
                "position" to cat.position,
                "isPrivate" to cat.isPrivate,
                "allowedRoleIds" to cat.allowedRoleIds
            )
        },
        "customRoles" to srv.customRoles.map { r ->
            buildMap<String, Any> {
                put("id", r.id)
                put("name", r.name)
                put("colorHex", r.colorHex)
                r.secondaryColorHex?.let { put("secondaryColorHex", it) }
                put("isAdmin", r.isAdmin)
                put("canKickBan", r.canKickBan)
                put("canManageChannels", r.canManageChannels)
                put("permissions", r.permissions.map { it.name })
                put("position", r.position)
            }
        },
        "memberIds" to memberIds.distinct(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        srv.customBannerColorHex?.let { put("customBannerColorHex", it) }
        srv.imageUrl?.takeIf { it.isNotBlank() }?.let { put("imageUrl", it) }
        srv.bannerUrl?.takeIf { it.isNotBlank() }?.let { put("bannerUrl", it) }
    }

    fun memberToMap(
        member: User,
        serverId: String,
        role: UserRole,
        permissions: Set<RolePermission>,
        isPublicServer: Boolean
    ): HashMap<String, Any> = hashMapOf<String, Any>(
        "uid" to member.id,
        "serverId" to serverId,
        "name" to member.name.ifBlank { "Membre" },
        "avatarColor" to member.avatarColor,
        "role" to role.name,
        "permissions" to permissions.map { it.name },
        "status" to member.status.name,
        "isTimedOut" to member.isTimedOut,
        "xp" to member.xp,
        "level" to member.level,
        "isPublic" to isPublicServer,
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        member.avatarUrl?.takeIf { it.isNotBlank() }?.let { put("avatarUrl", it) }
        member.customRoleId?.takeIf { it.isNotBlank() }?.let { put("customRoleId", it) }
        member.clanTag?.takeIf { it.isNotBlank() }?.let { put("clanTag", it) }
    }

    fun inviteToMap(invite: CommunityInvite, isPublicServer: Boolean): HashMap<String, Any> =
        hashMapOf<String, Any>(
            "id" to invite.id,
            "code" to invite.code,
            "serverId" to invite.serverId,
            "serverName" to invite.serverName,
            "createdByUid" to invite.createdByUid,
            "createdByName" to invite.createdByName,
            "maxUses" to invite.maxUses,
            "usesCount" to invite.usesCount,
            "expiresInHours" to invite.expiresInHours,
            "isRevoked" to invite.isRevoked,
            "isPublic" to isPublicServer,
            "createdAtLabel" to invite.createdAtLabel,
            "createdAtMs" to System.currentTimeMillis()
        ).apply {
            invite.expiresAtMs?.let { put("expiresAtMs", it) }
        }

    fun channelToMap(
        channel: Channel,
        serverId: String,
        isPublicServer: Boolean,
        memberIds: List<String>
    ): HashMap<String, Any> = hashMapOf(
        "id" to channel.id,
        "serverId" to serverId,
        "name" to channel.name,
        "type" to channel.type.name,
        "category" to channel.category,
        "topic" to channel.topic,
        "isPrivate" to channel.isPrivate,
        "isLocked" to channel.isLocked,
        "maxParticipants" to channel.maxParticipants,
        "bitrateKbps" to channel.bitrateKbps,
        "isReadOnly" to channel.isReadOnly,
        "slowmodeSeconds" to channel.slowmodeSeconds,
        "allowedRoles" to channel.allowedRoles.map { it.name },
        "allowedRoleIds" to channel.allowedRoleIds,
        "kickedVoiceUids" to channel.kickedVoiceUids,
        "isPublic" to isPublicServer,
        "memberIds" to memberIds.distinct(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    @Suppress("UNCHECKED_CAST")
    fun mapDocToServer(doc: DocumentSnapshot): Server? {
        val id = doc.getString("id") ?: doc.id
        val name = doc.getString("name") ?: return null
        val iconText = doc.getString("iconText") ?: name.take(2).uppercase()
        val iconBgColor = doc.getLong("iconBgColor") ?: 0xFF5865F2L
        val ownerId = doc.getString("ownerId") ?: ""
        val inviteCode = doc.getString("inviteCode") ?: "RAVEN-2026"
        val uniqueHandle = doc.getString("uniqueHandle") ?: ""
        val isPublic = doc.getBoolean("isPublic") ?: false
        val isVerified = doc.getBoolean("isVerified") ?: false
        val categoryTag = doc.getString("categoryTag") ?: "Communauté"
        val description = doc.getString("description") ?: "Communauté Raven"
        val rulesGuidelines = doc.getString("rulesGuidelines") ?: "Respect, entraide et bienveillance."
        val homeWelcome = doc.getString("homeWelcomeMessage") ?: "Bienvenue dans la communauté !"
        val imageUrl = doc.getString("imageUrl")
        val bannerUrl = doc.getString("bannerUrl")
        val customBannerHex = doc.getLong("customBannerColorHex")
        val boostCount = doc.getLong("boostCount")?.toInt() ?: 0
        val boostLevel = doc.getLong("boostLevel")?.toInt() ?: 0
        val adminIds = (doc.get("adminIds") as? List<*>)?.mapNotNull { it as? String } ?: listOf(ownerId)
        val modIds = (doc.get("moderatorIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val categories = (doc.get("categories") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val cid = m["id"] as? String ?: return@mapNotNull null
            val cname = m["name"] as? String ?: return@mapNotNull null
            ChannelCategory(
                id = cid,
                name = cname,
                position = (m["position"] as? Number)?.toInt() ?: 0,
                isPrivate = m["isPrivate"] as? Boolean ?: false,
                allowedRoleIds = (m["allowedRoleIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        }

        val customRoles = (doc.get("customRoles") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val rid = m["id"] as? String ?: return@mapNotNull null
            val rname = m["name"] as? String ?: return@mapNotNull null
            val isAdmin = m["isAdmin"] as? Boolean ?: false
            val canKick = m["canKickBan"] as? Boolean ?: false
            val canManage = m["canManageChannels"] as? Boolean ?: false
            val permStrings = (m["permissions"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val perms = if (permStrings.isNotEmpty()) {
                permStrings.mapNotNull { pName -> RolePermission.entries.find { it.name == pName } }.toSet()
            } else {
                defaultPermissionsForFlags(isAdmin, canKick, canManage)
            }
            CustomRole(
                id = rid,
                name = rname,
                colorHex = (m["colorHex"] as? Number)?.toLong() ?: 0xFF5865F2L,
                secondaryColorHex = (m["secondaryColorHex"] as? Number)?.toLong(),
                isAdmin = isAdmin,
                canKickBan = canKick,
                canManageChannels = canManage,
                permissions = perms,
                position = (m["position"] as? Number)?.toInt() ?: 0
            )
        }.ifEmpty { defaultServerRoles() }

        return Server(
            id = id,
            name = name,
            iconText = iconText,
            iconBgColor = iconBgColor,
            ownerId = ownerId,
            inviteCode = inviteCode,
            uniqueHandle = uniqueHandle,
            imageUrl = imageUrl,
            bannerUrl = bannerUrl,
            customBannerColorHex = customBannerHex,
            boostCount = boostCount,
            boostLevel = boostLevel,
            isPublic = isPublic,
            isVerified = isVerified,
            categoryTag = categoryTag,
            description = description,
            rulesGuidelines = rulesGuidelines,
            homeWelcomeMessage = homeWelcome,
            adminIds = adminIds,
            moderatorIds = modIds,
            categories = categories,
            customRoles = customRoles,
            channels = listOf(
                Channel("${id}_annonces", id, "annonces", ChannelType.ANNOUNCEMENT, "INFORMATIONS", isReadOnly = true),
                Channel("${id}_general", id, "général", ChannelType.TEXT, "SALONS TEXTUELS"),
                Channel("${id}_voice", id, "Salon vocal", ChannelType.VOICE, "SALONS VOCAUX")
            )
        )
    }

    fun mapDocToUserMember(doc: DocumentSnapshot): User? {
        val uid = doc.getString("uid") ?: doc.id
        val name = doc.getString("name") ?: return null
        val roleStr = doc.getString("role") ?: UserRole.MEMBER.name
        val statusStr = doc.getString("status") ?: UserStatus.ONLINE.name
        return User(
            id = uid,
            name = name,
            avatarUrl = doc.getString("avatarUrl"),
            avatarColor = doc.getLong("avatarColor") ?: 0xFF5865F2L,
            role = UserRole.entries.find { it.name == roleStr } ?: UserRole.MEMBER,
            customRoleId = doc.getString("customRoleId"),
            status = UserStatus.entries.find { it.name == statusStr } ?: UserStatus.ONLINE,
            isTimedOut = doc.getBoolean("isTimedOut") ?: false,
            xp = doc.getLong("xp")?.toInt() ?: 0,
            level = doc.getLong("level")?.toInt() ?: 1,
            clanTag = doc.getString("clanTag")
        )
    }

    fun mapDocToInvite(doc: DocumentSnapshot): CommunityInvite? {
        val code = doc.getString("code") ?: doc.id
        val serverId = doc.getString("serverId") ?: return null
        return CommunityInvite(
            id = doc.id,
            code = code,
            serverId = serverId,
            serverName = doc.getString("serverName") ?: "",
            createdByUid = doc.getString("createdByUid") ?: "",
            createdByName = doc.getString("createdByName") ?: "Staff",
            maxUses = doc.getLong("maxUses")?.toInt() ?: 0,
            usesCount = doc.getLong("usesCount")?.toInt() ?: 0,
            expiresInHours = doc.getLong("expiresInHours")?.toInt() ?: 0,
            expiresAtMs = doc.getLong("expiresAtMs"),
            isRevoked = doc.getBoolean("isRevoked") ?: false,
            createdAtLabel = doc.getString("createdAtLabel") ?: "À l'instant"
        )
    }

    fun mapDocToAnnouncement(doc: DocumentSnapshot): CommunityAnnouncement? {
        val serverId = doc.getString("serverId") ?: return null
        val title = doc.getString("title") ?: return null
        val content = doc.getString("content") ?: return null
        return CommunityAnnouncement(
            id = doc.id,
            serverId = serverId,
            title = title,
            content = content,
            authorId = doc.getString("authorId") ?: "",
            authorName = doc.getString("authorName") ?: "Administrateur",
            authorRoleTitle = doc.getString("authorRoleTitle") ?: "Administrateur",
            isPinned = doc.getBoolean("isPinned") ?: true,
            timestampLabel = doc.getString("timestampLabel") ?: "À l'instant",
            createdAtMs = doc.getLong("createdAtMs") ?: 0L
        )
    }

    fun mapDocToEvent(doc: DocumentSnapshot): ServerEvent? {
        val title = doc.getString("title") ?: return null
        @Suppress("UNCHECKED_CAST")
        val interested = (doc.get("interestedUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return ServerEvent(
            id = doc.id,
            title = title,
            description = doc.getString("description") ?: "",
            dateTimeLabel = doc.getString("dateTimeLabel") ?: "À venir",
            locationLabel = doc.getString("locationLabel") ?: "Vocal",
            creatorName = doc.getString("creatorName") ?: "Organisateur",
            interestedUserIds = interested
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun mapDocToChannel(doc: DocumentSnapshot): Channel? {
        val name = doc.getString("name") ?: return null
        val serverId = doc.getString("serverId") ?: return null
        val typeStr = doc.getString("type") ?: ChannelType.TEXT.name
        val allowed = (doc.get("allowedRoles") as? List<*>)?.mapNotNull { r ->
            (r as? String)?.let { nameStr -> UserRole.entries.find { it.name == nameStr } }
        } ?: emptyList()
        val allowedRoleIds = (doc.get("allowedRoleIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val kickedVoiceUids = (doc.get("kickedVoiceUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return Channel(
            id = doc.id,
            serverId = serverId,
            name = name,
            type = ChannelType.entries.find { it.name == typeStr } ?: ChannelType.TEXT,
            category = doc.getString("category") ?: "SALONS TEXTUELS",
            topic = doc.getString("topic") ?: "",
            isPrivate = doc.getBoolean("isPrivate") ?: false,
            isLocked = doc.getBoolean("isLocked") ?: false,
            maxParticipants = doc.getLong("maxParticipants")?.toInt() ?: 0,
            bitrateKbps = doc.getLong("bitrateKbps")?.toInt() ?: 64,
            isReadOnly = doc.getBoolean("isReadOnly") ?: false,
            slowmodeSeconds = doc.getLong("slowmodeSeconds")?.toInt() ?: 0,
            allowedRoles = allowed,
            allowedRoleIds = allowedRoleIds,
            kickedVoiceUids = kickedVoiceUids
        )
    }

    fun pushChannelSafe(
        db: FirebaseFirestore,
        serverId: String,
        channel: Channel,
        isPublic: Boolean,
        memberIds: List<String>
    ) {
        try {
            Tasks.await(
                db.collection("servers").document(serverId)
                    .collection("channels").document(channel.id)
                    .set(channelToMap(channel, serverId, isPublic, memberIds), SetOptions.merge())
            )
        } catch (e: Exception) {
            Log.w("CommunitySync", "Channel sync warning", e)
        }
    }
}
