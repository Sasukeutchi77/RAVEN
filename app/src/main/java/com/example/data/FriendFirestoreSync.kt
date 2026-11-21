package com.example.data

import com.example.model.DirectConversation
import com.example.model.DirectMessagePrivacyPolicy
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationStatus
import com.example.model.FriendRelationship
import com.example.model.FriendRequest
import com.example.model.FriendRequestDirection
import com.example.model.FriendRequestPolicy
import com.example.model.User
import com.example.model.UserDirectoryEntry
import com.example.model.UserStatus
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FriendFirestoreSync {

    fun canonicalPairId(uidA: String, uidB: String): String {
        val cleanA = uidA.trim()
        val cleanB = uidB.trim()
        return if (cleanA <= cleanB) "${cleanA}__${cleanB}" else "${cleanB}__${cleanA}"
    }

    fun canonicalDmId(uidA: String, uidB: String): String {
        return "dm_${canonicalPairId(uidA, uidB)}"
    }

    fun formatRelativeActivityTime(lastActiveAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (lastActiveAtMs <= 0L) return "Activité masquée"
        val diff = (nowMs - lastActiveAtMs).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "Actif à l'instant"
            diff < 3_600_000L -> "Actif il y a ${diff / 60_000L} min"
            diff < 86_400_000L -> "Actif il y a ${diff / 3_600_000L} h"
            else -> "Actif le ${SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(lastActiveAtMs))}"
        }
    }

    fun friendToMap(rel: FriendRelationship): HashMap<String, Any> {
        val map = hashMapOf<String, Any>(
            "friendUid" to rel.friendUid,
            "pairId" to rel.pairId,
            "friendName" to rel.user.name.ifBlank { "Membre Raven" }.take(64),
            "friendTag" to rel.user.tag.ifBlank { "#0001" }.take(16),
            "avatarColor" to rel.user.avatarColor,
            "status" to rel.user.status.name,
            "customStatus" to rel.user.customStatus.take(120),
            "lastActiveAtMs" to rel.lastActiveAtMs,
            "lastActivityLabel" to rel.lastActivityLabel.take(120),
            "privateNote" to rel.privateNote.take(400),
            "addedAtMs" to rel.addedAtMs
        )
        rel.user.avatarUrl?.takeIf { it.isNotBlank() }?.let { map["avatarUrl"] = it.take(500) }
        return map
    }

    fun friendFromDoc(doc: DocumentSnapshot, noteFallback: String = ""): FriendRelationship? {
        val friendUid = doc.getString("friendUid")?.takeIf { it.isNotBlank() } ?: doc.id
        if (friendUid.isBlank()) return null
        val name = doc.getString("friendName")?.takeIf { it.isNotBlank() } ?: "Ami Raven"
        val tag = doc.getString("friendTag")?.takeIf { it.isNotBlank() } ?: "#0001"
        val avatarColor = doc.getLong("avatarColor") ?: 0xFF5865F2L
        val avatarUrl = doc.getString("avatarUrl")
        val statusName = doc.getString("status")
        val status = UserStatus.entries.find { it.name == statusName } ?: UserStatus.OFFLINE
        val customStatus = doc.getString("customStatus") ?: ""
        val lastActiveAtMs = doc.getLong("lastActiveAtMs") ?: 0L
        val lastActivityLabel = doc.getString("lastActivityLabel") ?: ""
        val addedAtMs = doc.getLong("addedAtMs") ?: System.currentTimeMillis()
        val pairId = doc.getString("pairId")?.takeIf { it.isNotBlank() } ?: friendUid
        val note = doc.getString("privateNote")?.takeIf { it.isNotBlank() } ?: noteFallback

        val user = User(
            id = friendUid,
            name = name,
            tag = tag,
            avatarColor = avatarColor,
            avatarUrl = avatarUrl,
            status = status,
            customStatus = customStatus,
            lastActiveAtMs = lastActiveAtMs,
            lastActivityLabel = lastActivityLabel
        )
        return FriendRelationship(
            friendUid = friendUid,
            pairId = pairId,
            user = user,
            addedAtMs = addedAtMs,
            lastActiveAtMs = lastActiveAtMs,
            lastActivityLabel = lastActivityLabel.ifBlank { customStatus },
            privateNote = note
        )
    }

    fun blockedToMap(ownerUid: String, user: User, nowMs: Long = System.currentTimeMillis()): HashMap<String, Any> {
        val map = hashMapOf<String, Any>(
            "blockedUid" to user.id,
            "blockedByUid" to ownerUid,
            "name" to user.name.ifBlank { "Utilisateur" }.take(64),
            "tag" to user.tag.ifBlank { "#0001" }.take(16),
            "avatarColor" to user.avatarColor,
            "blockedAtMs" to nowMs
        )
        user.avatarUrl?.takeIf { it.isNotBlank() }?.let { map["avatarUrl"] = it.take(500) }
        return map
    }

    fun blockedFromDoc(doc: DocumentSnapshot): User? {
        val uid = doc.getString("blockedUid")?.takeIf { it.isNotBlank() } ?: doc.id
        if (uid.isBlank()) return null
        return User(
            id = uid,
            name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: "Utilisateur bloqué",
            tag = doc.getString("tag")?.takeIf { it.isNotBlank() } ?: "#0001",
            avatarColor = doc.getLong("avatarColor") ?: 0xFF35373CL,
            avatarUrl = doc.getString("avatarUrl"),
            status = UserStatus.OFFLINE
        )
    }

    fun requestToMap(
        fromUser: User,
        toUser: User,
        sharedServerId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): HashMap<String, Any> {
        val pairId = canonicalPairId(fromUser.id, toUser.id)
        val map = hashMapOf<String, Any>(
            "id" to pairId,
            "pairId" to pairId,
            "fromUid" to fromUser.id,
            "fromName" to fromUser.name.ifBlank { "Membre Raven" }.take(64),
            "fromTag" to fromUser.tag.ifBlank { "#0001" }.take(16),
            "fromAvatarColor" to fromUser.avatarColor,
            "toUid" to toUser.id,
            "toName" to toUser.name.ifBlank { "Membre Raven" }.take(64),
            "toTag" to toUser.tag.ifBlank { "#0001" }.take(16),
            "toAvatarColor" to toUser.avatarColor,
            "participantIds" to listOf(fromUser.id, toUser.id).sorted(),
            "status" to "PENDING",
            "createdAtMs" to nowMs,
            "updatedAtMs" to nowMs
        )
        fromUser.avatarUrl?.takeIf { it.isNotBlank() }?.let { map["fromAvatarUrl"] = it.take(500) }
        toUser.avatarUrl?.takeIf { it.isNotBlank() }?.let { map["toAvatarUrl"] = it.take(500) }
        sharedServerId?.takeIf { it.isNotBlank() }?.let { map["sharedServerId"] = it }
        return map
    }

    data class ParsedFriendRequestDoc(
        val status: String,
        val fromUid: String,
        val toUid: String,
        val request: FriendRequest
    )

    fun requestFromDoc(doc: DocumentSnapshot, viewerUid: String): ParsedFriendRequestDoc? {
        val status = doc.getString("status") ?: return null
        val fromUid = doc.getString("fromUid") ?: return null
        val toUid = doc.getString("toUid") ?: return null
        if (viewerUid != fromUid && viewerUid != toUid) return null
        val pairId = doc.getString("pairId")?.takeIf { it.isNotBlank() } ?: doc.id
        val direction = if (toUid == viewerUid) {
            FriendRequestDirection.RECEIVED
        } else {
            FriendRequestDirection.SENT
        }
        val otherUid = if (direction == FriendRequestDirection.RECEIVED) fromUid else toUid
        val otherName = if (direction == FriendRequestDirection.RECEIVED) {
            doc.getString("fromName") ?: "Membre Raven"
        } else {
            doc.getString("toName") ?: "Membre Raven"
        }
        val otherTag = if (direction == FriendRequestDirection.RECEIVED) {
            doc.getString("fromTag") ?: "#0001"
        } else {
            doc.getString("toTag") ?: "#0001"
        }
        val otherColor = if (direction == FriendRequestDirection.RECEIVED) {
            doc.getLong("fromAvatarColor") ?: 0xFF5865F2L
        } else {
            doc.getLong("toAvatarColor") ?: 0xFF5865F2L
        }
        val otherAvatarUrl = if (direction == FriendRequestDirection.RECEIVED) {
            doc.getString("fromAvatarUrl")
        } else {
            doc.getString("toAvatarUrl")
        }
        val createdAtMs = doc.getLong("createdAtMs") ?: System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(createdAtMs))
        val sharedServerId = doc.getString("sharedServerId")
        val otherUser = User(
            id = otherUid,
            name = otherName,
            tag = otherTag,
            avatarColor = otherColor,
            avatarUrl = otherAvatarUrl,
            status = UserStatus.ONLINE
        )
        return ParsedFriendRequestDoc(
            status = status,
            fromUid = fromUid,
            toUid = toUid,
            request = FriendRequest(
                id = pairId,
                user = otherUser,
                direction = direction,
                timestamp = timeLabel,
                pairId = pairId,
                fromUid = fromUid,
                toUid = toUid,
                sharedServerId = sharedServerId,
                createdAtMs = createdAtMs
            )
        )
    }

    fun directoryEntryFromPublicDoc(
        doc: DocumentSnapshot,
        mutualServers: List<String>,
        relationStatus: FriendRelationStatus,
        pendingRequestId: String?,
        privateNote: String
    ): UserDirectoryEntry? {
        val uid = doc.getString("userId")?.takeIf { it.isNotBlank() } ?: doc.id
        if (uid.isBlank()) return null
        val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: return null
        val tag = doc.getString("tag")?.takeIf { it.isNotBlank() } ?: "#0001"
        val avatarColor = doc.getLong("avatarColor") ?: 0xFF5865F2L
        val avatarUrl = doc.getString("avatarUrl")
        val xp = doc.getLong("xp")?.toInt() ?: 0
        val level = doc.getLong("level")?.toInt() ?: 1
        val clanTag = doc.getString("clanTag")
        val clanBadgeIcon = doc.getString("clanBadgeIcon") ?: "bolt"
        val bio = doc.getString("bio") ?: ""
        val customStatus = doc.getString("customStatus") ?: ""
        val reqPolicy = FriendRequestPolicy.entries.find {
            it.name == doc.getString("friendRequestPolicy")
        } ?: FriendRequestPolicy.EVERYONE
        val dmPolicy = DirectMessagePrivacyPolicy.entries.find {
            it.name == doc.getString("allowDmsFrom")
        } ?: DirectMessagePrivacyPolicy.EVERYONE
        val showOnline = doc.getBoolean("showOnlineStatus") ?: true
        val showActivity = doc.getBoolean("showLastActivity") ?: true

        val rawStatus = UserStatus.entries.find { it.name == doc.getString("status") } ?: UserStatus.ONLINE
        val visibleStatus = if (showOnline) rawStatus else UserStatus.OFFLINE
        val rawLastActiveMs = doc.getLong("lastActiveAtMs") ?: 0L
        val rawActivityLabel = doc.getString("lastActivityLabel") ?: ""
        val visibleLastActiveMs = if (showActivity) rawLastActiveMs else 0L
        val visibleActivityLabel = if (showActivity) rawActivityLabel else ""

        val user = User(
            id = uid,
            name = name,
            tag = tag,
            avatarColor = avatarColor,
            avatarUrl = avatarUrl,
            bio = bio,
            status = visibleStatus,
            customStatus = if (showOnline) customStatus else "",
            activity = if (showActivity) rawActivityLabel else "",
            xp = xp,
            level = level,
            clanTag = clanTag,
            clanBadgeIcon = clanBadgeIcon,
            lastActiveAtMs = visibleLastActiveMs,
            lastActivityLabel = visibleActivityLabel
        )
        return UserDirectoryEntry(
            user = user,
            lastActiveAtMs = visibleLastActiveMs,
            lastActivityLabel = visibleActivityLabel,
            friendRequestPolicy = reqPolicy,
            allowDmsFrom = dmPolicy,
            showOnlineStatus = showOnline,
            showLastActivity = showActivity,
            mutualServers = mutualServers,
            relationStatus = relationStatus,
            pendingRequestId = pendingRequestId,
            privateNote = privateNote
        )
    }

    fun privacyToPublicProfileUpdates(
        privacy: FriendPrivacySettings,
        status: UserStatus,
        lastActiveAtMs: Long,
        lastActivityLabel: String
    ): HashMap<String, Any> {
        return hashMapOf(
            "friendRequestPolicy" to privacy.friendRequestPolicy.name,
            "allowDmsFrom" to privacy.allowDmsFrom.name,
            "showOnlineStatus" to privacy.showOnlineStatus,
            "showLastActivity" to privacy.showLastActivity,
            "status" to (if (privacy.showOnlineStatus) status.name else UserStatus.OFFLINE.name),
            "lastActiveAtMs" to (if (privacy.showLastActivity) lastActiveAtMs else 0L),
            "lastActivityLabel" to (if (privacy.showLastActivity) lastActivityLabel.take(120) else ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun directConvFromDoc(
        doc: DocumentSnapshot,
        viewerUid: String,
        knownUsers: Map<String, User>
    ): DirectConversation? {
        val convId = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id
        val participants = (doc.get("participantIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (participants.isEmpty() || viewerUid !in participants) return null

        val isGroup = doc.getBoolean("isGroup") ?: (participants.size > 2)
        val groupName = doc.getString("groupName")
        val lastMessage = doc.getString("lastMessage") ?: ""
        val lastMessageTime = doc.getString("lastMessageTime") ?: ""
        val dmPairKey = doc.getString("dmPairKey")
        val participantNames = (doc.get("participantNames") as? Map<String, Any?>)
            ?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }
            ?.toMap()
            .orEmpty()

        val otherUids = participants.filter { it != viewerUid }
        val otherUsers = otherUids.map { uid ->
            knownUsers[uid] ?: User(
                id = uid,
                name = participantNames[uid] ?: "Membre Raven",
                tag = "#0001",
                avatarColor = 0xFF5865F2L,
                status = UserStatus.OFFLINE
            )
        }
        val primaryFriend = otherUsers.firstOrNull() ?: User(
            id = viewerUid,
            name = participantNames[viewerUid] ?: "Notes personnelles",
            status = UserStatus.ONLINE
        )
        return DirectConversation(
            id = convId,
            friend = primaryFriend,
            lastMessage = lastMessage,
            lastMessageTime = lastMessageTime,
            unreadCount = 0,
            isGroup = isGroup,
            groupName = groupName,
            groupMembers = if (isGroup) otherUsers else emptyList(),
            participantIds = participants.sorted(),
            dmPairKey = dmPairKey
        )
    }
}
