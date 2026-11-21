package com.example.data

import android.util.Log
import com.example.model.DirectConversation
import com.example.model.DirectMessagePrivacyPolicy
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationStatus
import com.example.model.FriendRelationship
import com.example.model.FriendRequest
import com.example.model.FriendRequestDirection
import com.example.model.FriendRequestPolicy
import com.example.model.NotificationType
import com.example.model.User
import com.example.model.UserDirectoryEntry
import com.example.model.UserStatus
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FriendRepository(
    private val chatRepo: ChatRepository
) {
    private val _friends = MutableStateFlow<List<FriendRelationship>>(emptyList())
    val friends: StateFlow<List<FriendRelationship>> = _friends.asStateFlow()

    private val _directoryUsers = MutableStateFlow<Map<String, UserDirectoryEntry>>(emptyMap())
    val directoryUsers: StateFlow<Map<String, UserDirectoryEntry>> = _directoryUsers.asStateFlow()

    private val _privacySettings = MutableStateFlow(FriendPrivacySettings())
    val privacySettings: StateFlow<FriendPrivacySettings> = _privacySettings.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeAuthUid: String? = null
    private var friendsListener: ListenerRegistration? = null
    private var blockedListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null
    private var dmsListener: ListenerRegistration? = null
    private var publicDirectoryListener: ListenerRegistration? = null
    private val notifiedRequestIds = mutableSetOf<String>()
    private val materializedPairIds = mutableSetOf<String>()

    init {
        seedOfflineDefaults()
        chatRepo.friendBridgeHook = this
    }

    private fun seedOfflineDefaults() {
        val now = System.currentTimeMillis()
        val meUid = currentUid()
        val seededFriends = MockData.allFriends.mapIndexed { index, friend ->
            val offsetMs = when (index) {
                0 -> 45_000L
                1 -> 5 * 60_000L
                else -> 2 * 3_600_000L
            }
            val actTime = now - offsetMs
            val actLabel = friend.activity.ifBlank {
                friend.customStatus.ifBlank { FriendFirestoreSync.formatRelativeActivityTime(actTime, now) }
            }
            FriendRelationship(
                friendUid = friend.id,
                pairId = FriendFirestoreSync.canonicalPairId(meUid, friend.id),
                user = friend.copy(lastActiveAtMs = actTime, lastActivityLabel = actLabel),
                addedAtMs = now - (index + 1) * 86_400_000L,
                lastActiveAtMs = actTime,
                lastActivityLabel = actLabel,
                privateNote = chatRepo.privateUserNotes.value[friend.id].orEmpty()
            )
        }
        _friends.value = seededFriends

        // Normalize initial requests with deterministic pairIds
        val reqFlow = chatRepo.friendRequestsMutableFlow()
        reqFlow.value = reqFlow.value.map { req ->
            val fromUid = if (req.direction == FriendRequestDirection.SENT) meUid else req.user.id
            val toUid = if (req.direction == FriendRequestDirection.RECEIVED) meUid else req.user.id
            val pairId = FriendFirestoreSync.canonicalPairId(fromUid, toUid)
            req.copy(
                id = pairId,
                pairId = pairId,
                fromUid = fromUid,
                toUid = toUid
            )
        }

        // Normalize initial DMs with participantIds & dmPairKey while preserving existing IDs (dm_thomas, dm_emma)
        val dmFlow = chatRepo.directConversationsMutableFlow()
        dmFlow.value = dmFlow.value.map { conv ->
            if (conv.isGroup) {
                val pIds = (conv.groupMembers.map { it.id } + meUid).distinct().sorted()
                conv.copy(participantIds = pIds)
            } else {
                val pairKey = FriendFirestoreSync.canonicalPairId(meUid, conv.friend.id)
                conv.copy(
                    participantIds = listOf(meUid, conv.friend.id).distinct().sorted(),
                    dmPairKey = pairKey
                )
            }
        }
    }

    private fun currentUid(): String {
        return chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
    }

    fun syncWithAuthUid(uid: String?) {
        val db = chatRepo.firestoreDb()
        if (uid.isNullOrBlank() || db == null) {
            stopListeners()
            activeAuthUid = null
            if (_friends.value.isEmpty()) {
                seedOfflineDefaults()
            }
            return
        }
        if (activeAuthUid == uid) return
        activeAuthUid = uid
        startListeners(db, uid)
    }

    private fun stopListeners() {
        friendsListener?.remove()
        blockedListener?.remove()
        requestsListener?.remove()
        dmsListener?.remove()
        publicDirectoryListener?.remove()
        friendsListener = null
        blockedListener = null
        requestsListener = null
        dmsListener = null
        publicDirectoryListener = null
    }

    private fun startListeners(db: com.google.firebase.firestore.FirebaseFirestore, uid: String) {
        stopListeners()

        // 1. User's OWN friend list (owner-only read/write)
        friendsListener = db.collection("users").document(uid)
            .collection("friends")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val notes = chatRepo.privateUserNotes.value
                val parsed = snap.documents.mapNotNull { doc ->
                    val raw = FriendFirestoreSync.friendFromDoc(doc, notes[doc.id].orEmpty()) ?: return@mapNotNull null
                    val liveDir = _directoryUsers.value[raw.friendUid]
                    if (liveDir != null) {
                        raw.copy(
                            user = raw.user.copy(
                                name = liveDir.user.name,
                                avatarUrl = liveDir.user.avatarUrl ?: raw.user.avatarUrl,
                                status = if (liveDir.showOnlineStatus) liveDir.user.status else UserStatus.OFFLINE,
                                customStatus = if (liveDir.showOnlineStatus) liveDir.user.customStatus else "",
                                lastActiveAtMs = if (liveDir.showLastActivity) liveDir.lastActiveAtMs else 0L,
                                lastActivityLabel = if (liveDir.showLastActivity) liveDir.lastActivityLabel else ""
                            ),
                            lastActiveAtMs = if (liveDir.showLastActivity) liveDir.lastActiveAtMs else 0L,
                            lastActivityLabel = if (liveDir.showLastActivity) liveDir.lastActivityLabel else ""
                        )
                    } else raw
                }.distinctBy { it.friendUid }
                _friends.value = parsed
            }

        // 2. User's OWN blocked users list (owner-only)
        blockedListener = db.collection("users").document(uid)
            .collection("blocked_users")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val blocked = snap.documents.mapNotNull { FriendFirestoreSync.blockedFromDoc(it) }
                    .distinctBy { it.id }
                chatRepo.blockedUsersMutableFlow().value = blocked
                val blockedIds = blocked.map { it.id }.toSet()
                if (blockedIds.isNotEmpty()) {
                    chatRepo.directConversationsMutableFlow().value =
                        chatRepo.directConversationsMutableFlow().value.filterNot {
                            !it.isGroup && it.friend.id in blockedIds
                        }
                }
            }

        // 3. Shared friend_requests where participantIds contains uid
        requestsListener = db.collection("friend_requests")
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val activePending = mutableListOf<FriendRequest>()
                val blockedIds = chatRepo.blockedUsers.value.map { it.id }.toSet()

                snap.documents.forEach { doc ->
                    val parsed = FriendFirestoreSync.requestFromDoc(doc, uid) ?: return@forEach
                    when (parsed.status) {
                        "PENDING" -> {
                            if (parsed.request.user.id !in blockedIds) {
                                activePending.add(parsed.request)
                                if (parsed.request.direction == FriendRequestDirection.RECEIVED &&
                                    notifiedRequestIds.add(parsed.request.id) &&
                                    _privacySettings.value.notifyFriendRequests
                                ) {
                                    chatRepo.notifyCommunityEvent(
                                        title = "Nouvelle demande d'ami",
                                        body = "${parsed.request.user.name}${parsed.request.user.tag} souhaite vous ajouter en ami.",
                                        sourceLabel = "Amis Raven",
                                        type = NotificationType.FRIEND_REQUEST
                                    )
                                }
                            }
                        }

                        "ACCEPTED" -> {
                            val otherUser = parsed.request.user
                            val pairId = parsed.request.pairId
                            val alreadyFriend = _friends.value.any { it.friendUid == otherUser.id }
                            if (!alreadyFriend && otherUser.id !in blockedIds && materializedPairIds.add(pairId)) {
                                val rel = FriendRelationship(
                                    friendUid = otherUser.id,
                                    pairId = pairId,
                                    user = otherUser,
                                    addedAtMs = System.currentTimeMillis(),
                                    lastActiveAtMs = otherUser.lastActiveAtMs,
                                    lastActivityLabel = otherUser.lastActivityLabel,
                                    privateNote = chatRepo.privateUserNotes.value[otherUser.id].orEmpty()
                                )
                                _friends.value = (_friends.value + rel).distinctBy { it.friendUid }
                                // Sender materializes ONLY into their OWN /users/{uid}/friends/{otherUid}
                                writeOwnFriendDoc(db, uid, rel)
                                if (parsed.fromUid == uid && notifiedRequestIds.add("acc_$pairId")) {
                                    chatRepo.notifyCommunityEvent(
                                        title = "Demande acceptée !",
                                        body = "${otherUser.name}${otherUser.tag} a accepté votre demande d'ami.",
                                        sourceLabel = "Amis Raven",
                                        type = NotificationType.FRIEND_REQUEST
                                    )
                                }
                            }
                        }
                    }
                }
                chatRepo.friendRequestsMutableFlow().value = activePending.distinctBy { it.id }
            }

        // 4. Coherent Direct Conversations listener
        dmsListener = db.collection("direct_conversations")
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val blockedIds = chatRepo.blockedUsers.value.map { it.id }.toSet()
                val knownUsers = buildKnownUsersMap()
                val cloudConvs = snap.documents.mapNotNull { doc ->
                    FriendFirestoreSync.directConvFromDoc(doc, uid, knownUsers)
                }.filter { conv ->
                    conv.isGroup || conv.friend.id !in blockedIds
                }
                if (cloudConvs.isNotEmpty()) {
                    mergeDirectConversationsCoherently(cloudConvs, uid)
                }
            }

        // 5. Public profiles directory listener (non-PII + privacy flags + activity)
        publicDirectoryListener = db.collection("public_profiles")
            .whereEqualTo("isPublic", true)
            .limit(80)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val myNotes = chatRepo.privateUserNotes.value
                val entries = snap.documents.mapNotNull { doc ->
                    val targetUid = doc.getString("userId") ?: doc.id
                    if (targetUid == uid) return@mapNotNull null
                    val entry = FriendFirestoreSync.directoryEntryFromPublicDoc(
                        doc = doc,
                        mutualServers = computeMutualServerNames(targetUid),
                        relationStatus = relationStatusFor(targetUid),
                        pendingRequestId = pendingRequestFor(targetUid)?.id,
                        privateNote = myNotes[targetUid].orEmpty()
                    ) ?: return@mapNotNull null
                    targetUid to entry
                }.toMap()
                _directoryUsers.value = entries

                // Refresh live friend online status & activity respecting target's privacy settings
                if (entries.isNotEmpty()) {
                    _friends.value = _friends.value.map { rel ->
                        val dir = entries[rel.friendUid] ?: return@map rel
                        rel.copy(
                            user = rel.user.copy(
                                name = dir.user.name,
                                avatarUrl = dir.user.avatarUrl ?: rel.user.avatarUrl,
                                status = if (dir.showOnlineStatus) dir.user.status else UserStatus.OFFLINE,
                                customStatus = if (dir.showOnlineStatus) dir.user.customStatus else "",
                                lastActiveAtMs = if (dir.showLastActivity) dir.lastActiveAtMs else 0L,
                                lastActivityLabel = if (dir.showLastActivity) dir.lastActivityLabel else ""
                            ),
                            lastActiveAtMs = if (dir.showLastActivity) dir.lastActiveAtMs else 0L,
                            lastActivityLabel = if (dir.showLastActivity) dir.lastActivityLabel else ""
                        )
                    }
                }
            }
    }

    private fun mergeDirectConversationsCoherently(cloudConvs: List<DirectConversation>, myUid: String) {
        val current = chatRepo.directConversationsMutableFlow().value
        val result = current.toMutableList()
        cloudConvs.forEach { cloud ->
            val existingIdx = result.indexOfFirst { local ->
                if (local.id == cloud.id) true
                else if (!local.isGroup && !cloud.isGroup) {
                    local.friend.id == cloud.friend.id ||
                        (!local.dmPairKey.isNullOrBlank() && local.dmPairKey == cloud.dmPairKey) ||
                        local.id == "dm_${cloud.friend.id}"
                } else false
            }
            if (existingIdx >= 0) {
                val existing = result[existingIdx]
                result[existingIdx] = existing.copy(
                    lastMessage = cloud.lastMessage.ifBlank { existing.lastMessage },
                    lastMessageTime = cloud.lastMessageTime.ifBlank { existing.lastMessageTime },
                    participantIds = cloud.participantIds.ifEmpty { existing.participantIds },
                    dmPairKey = cloud.dmPairKey ?: existing.dmPairKey ?: FriendFirestoreSync.canonicalPairId(myUid, existing.friend.id)
                )
            } else {
                result.add(0, cloud)
            }
        }
        chatRepo.directConversationsMutableFlow().value = deduplicateDirectConversations(result, myUid)
    }

    private fun deduplicateDirectConversations(list: List<DirectConversation>, myUid: String): List<DirectConversation> {
        val seenKeys = mutableSetOf<String>()
        val deduped = mutableListOf<DirectConversation>()
        list.forEach { conv ->
            val key = if (conv.isGroup) {
                "grp:${conv.id}"
            } else {
                "dm:${FriendFirestoreSync.canonicalPairId(myUid, conv.friend.id)}"
            }
            if (seenKeys.add(key)) {
                deduped.add(conv)
            }
        }
        return deduped
    }

    private fun buildKnownUsersMap(): Map<String, User> {
        val map = mutableMapOf<String, User>()
        MockData.allFriends.forEach { map[it.id] = it }
        chatRepo.servers.value.flatMap { it.members }.forEach { map[it.id] = it }
        _friends.value.forEach { map[it.friendUid] = it.user }
        chatRepo.friendRequests.value.forEach { map[it.user.id] = it.user }
        _directoryUsers.value.forEach { (uid, entry) -> map[uid] = entry.user }
        return map
    }

    fun computeMutualServerIds(targetUid: String): List<String> {
        val me = currentUid()
        return chatRepo.servers.value
            .filter { !it.isDmTab }
            .filter { srv ->
                val memberIds = srv.members.map { it.id }.toSet()
                val meIn = srv.ownerId == me || me in memberIds || "user_me" in memberIds
                val otherIn = srv.ownerId == targetUid || targetUid in memberIds
                meIn && otherIn
            }
            .map { it.id }
    }

    fun computeMutualServerNames(targetUid: String): List<String> {
        val ids = computeMutualServerIds(targetUid).toSet()
        return chatRepo.servers.value.filter { it.id in ids }.map { it.name }
    }

    fun relationStatusFor(targetUid: String): FriendRelationStatus {
        if (chatRepo.blockedUsers.value.any { it.id == targetUid }) return FriendRelationStatus.BLOCKED_BY_ME
        if (_friends.value.any { it.friendUid == targetUid }) return FriendRelationStatus.FRIENDS
        val pending = chatRepo.friendRequests.value.find { it.user.id == targetUid }
        return when (pending?.direction) {
            FriendRequestDirection.SENT -> FriendRelationStatus.REQUEST_SENT
            FriendRequestDirection.RECEIVED -> FriendRelationStatus.REQUEST_RECEIVED
            null -> FriendRelationStatus.NONE
        }
    }

    fun pendingRequestFor(targetUid: String): FriendRequest? {
        return chatRepo.friendRequests.value.find { it.user.id == targetUid }
    }

    fun registerDirectoryEntries(entries: List<UserDirectoryEntry>) {
        val updated = _directoryUsers.value.toMutableMap()
        entries.forEach { updated[it.user.id] = it }
        _directoryUsers.value = updated
    }

    fun searchUsers(query: String): List<UserDirectoryEntry> {
        val meUid = currentUid()
        val trimmed = query.trim()
        val myNotes = chatRepo.privateUserNotes.value

        val pool = linkedMapOf<String, UserDirectoryEntry>()
        _directoryUsers.value.forEach { (uid, entry) ->
            if (uid != meUid && uid != "user_me") {
                pool[uid] = entry.copy(
                    mutualServers = computeMutualServerNames(uid),
                    relationStatus = relationStatusFor(uid),
                    pendingRequestId = pendingRequestFor(uid)?.id,
                    privateNote = myNotes[uid].orEmpty()
                )
            }
        }
        _friends.value.forEach { rel ->
            val uid = rel.friendUid
            if (uid != meUid && !pool.containsKey(uid)) {
                pool[uid] = UserDirectoryEntry(
                    user = rel.user,
                    lastActiveAtMs = rel.lastActiveAtMs,
                    lastActivityLabel = rel.lastActivityLabel,
                    mutualServers = computeMutualServerNames(uid),
                    relationStatus = relationStatusFor(uid),
                    pendingRequestId = pendingRequestFor(uid)?.id,
                    privateNote = rel.privateNote.ifBlank { myNotes[uid].orEmpty() }
                )
            }
        }
        chatRepo.friendRequests.value.forEach { req ->
            val uid = req.user.id
            if (uid != meUid && !pool.containsKey(uid)) {
                pool[uid] = UserDirectoryEntry(
                    user = req.user,
                    lastActiveAtMs = req.user.lastActiveAtMs,
                    lastActivityLabel = req.user.lastActivityLabel.ifBlank { req.user.customStatus },
                    mutualServers = computeMutualServerNames(uid),
                    relationStatus = relationStatusFor(uid),
                    pendingRequestId = req.id,
                    privateNote = myNotes[uid].orEmpty()
                )
            }
        }
        chatRepo.servers.value.filter { !it.isDmTab }.flatMap { it.members }.forEach { member ->
            if (member.id != meUid && member.id != "user_me" && !pool.containsKey(member.id)) {
                pool[member.id] = UserDirectoryEntry(
                    user = member,
                    lastActiveAtMs = member.lastActiveAtMs,
                    lastActivityLabel = member.activity.ifBlank { member.customStatus },
                    mutualServers = computeMutualServerNames(member.id),
                    relationStatus = relationStatusFor(member.id),
                    pendingRequestId = pendingRequestFor(member.id)?.id,
                    privateNote = myNotes[member.id].orEmpty()
                )
            }
        }
        chatRepo.blockedUsers.value.forEach { blocked ->
            if (blocked.id != meUid && !pool.containsKey(blocked.id)) {
                pool[blocked.id] = UserDirectoryEntry(
                    user = blocked,
                    mutualServers = computeMutualServerNames(blocked.id),
                    relationStatus = FriendRelationStatus.BLOCKED_BY_ME,
                    privateNote = myNotes[blocked.id].orEmpty()
                )
            }
        }

        val all = pool.values.toList()
        if (trimmed.isBlank()) return all.sortedBy { it.user.name.lowercase() }
        return all.filter { entry ->
            val fullTag = "${entry.user.name}${entry.user.tag}"
            entry.user.name.contains(trimmed, ignoreCase = true) ||
                fullTag.contains(trimmed, ignoreCase = true) ||
                entry.user.id.contains(trimmed, ignoreCase = true) ||
                (entry.user.clanTag?.contains(trimmed, ignoreCase = true) == true)
        }.sortedBy { it.user.name.lowercase() }
    }

    fun sendFriendRequestByInput(tagOrNameOrUid: String): Boolean {
        val clean = tagOrNameOrUid.trim()
        if (clean.isBlank()) {
            _errorMessage.value = "Veuillez saisir un nom d'utilisateur, un tag ou un identifiant."
            return false
        }
        val cleanName = clean.substringBefore("#").trim()
        val cleanTag = if (clean.contains("#")) "#${clean.substringAfter("#").trim()}" else null

        val match = searchUsers("").find { entry ->
            entry.user.id.equals(clean, ignoreCase = true) ||
                "${entry.user.name}${entry.user.tag}".equals(clean, ignoreCase = true) ||
                (cleanTag == null && entry.user.name.equals(cleanName, ignoreCase = true)) ||
                (cleanTag != null && entry.user.name.equals(cleanName, ignoreCase = true) && entry.user.tag.equals(cleanTag, ignoreCase = true))
        }
        if (match != null) {
            return sendFriendRequestToUser(match.user)
        }
        // Fallback when user enters an external tag offline (creates pending outgoing request, zero duplicates)
        val syntheticUid = "user_${cleanName.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${(cleanTag ?: "#0001").removePrefix("#")}"
        val targetUser = User(
            id = syntheticUid,
            name = cleanName.ifBlank { clean },
            tag = cleanTag ?: "#${(1000..9999).random()}",
            avatarColor = 0xFF5865F2L,
            status = UserStatus.ONLINE
        )
        return sendFriendRequestToUser(targetUser)
    }

    fun sendFriendRequestToUser(target: User): Boolean {
        clearMessages()
        val meUid = currentUid()
        if (target.id.isBlank() || target.id == meUid || target.id == "user_me") {
            _errorMessage.value = "Vous ne pouvez pas vous envoyer une demande d'ami."
            return false
        }
        if (chatRepo.blockedUsers.value.any { it.id == target.id }) {
            _errorMessage.value = "Débloquez ${target.name} avant de lui envoyer une demande d'ami."
            return false
        }
        if (_friends.value.any { it.friendUid == target.id }) {
            _errorMessage.value = "Vous êtes déjà ami avec ${target.name}."
            return false
        }

        // Prevent duplicates in either direction
        val existingPending = chatRepo.friendRequests.value.find { it.user.id == target.id }
        if (existingPending != null) {
            if (existingPending.direction == FriendRequestDirection.RECEIVED) {
                acceptFriendRequest(existingPending.id)
                _feedbackMessage.value = "Demande mutuelle acceptée avec ${target.name} !"
                return true
            }
            _errorMessage.value = "Une demande d'ami est déjà en attente pour ${target.name}."
            return false
        }

        // Enforce target user's FriendRequestPolicy
        val dirEntry = _directoryUsers.value[target.id]
        val policy = dirEntry?.friendRequestPolicy ?: FriendRequestPolicy.EVERYONE
        if (policy == FriendRequestPolicy.NOBODY) {
            _errorMessage.value = "${target.name} n'accepte aucune demande d'ami."
            return false
        }
        val mutualServerIds = computeMutualServerIds(target.id)
        if (policy == FriendRequestPolicy.MUTUAL_SERVERS && mutualServerIds.isEmpty()) {
            _errorMessage.value = "${target.name} n'accepte les demandes que depuis un serveur commun."
            return false
        }

        val pairId = FriendFirestoreSync.canonicalPairId(meUid, target.id)
        val now = System.currentTimeMillis()
        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val newRequest = FriendRequest(
            id = pairId,
            user = target,
            direction = FriendRequestDirection.SENT,
            timestamp = timeLabel,
            pairId = pairId,
            fromUid = meUid,
            toUid = target.id,
            sharedServerId = mutualServerIds.firstOrNull(),
            createdAtMs = now
        )
        chatRepo.friendRequestsMutableFlow().value =
            (listOf(newRequest) + chatRepo.friendRequestsMutableFlow().value).distinctBy { it.pairId }

        _feedbackMessage.value = "Demande d'ami envoyée à ${target.name}${target.tag}."
        chatRepo.notifyCommunityEvent(
            title = "Demande d'ami envoyée",
            body = "En attente de confirmation de ${target.name}${target.tag}.",
            sourceLabel = "Amis Raven",
            type = NotificationType.FRIEND_REQUEST
        )

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            val fromUser = chatRepo.currentUser.value.copy(id = authUid)
            val payload = FriendFirestoreSync.requestToMap(
                fromUser = fromUser,
                toUser = target,
                sharedServerId = mutualServerIds.firstOrNull(),
                nowMs = now
            )
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("friend_requests").document(pairId).set(payload))
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to create Firestore friend_request", e)
                }
            }
        }
        return true
    }

    fun acceptFriendRequest(requestId: String): Boolean {
        clearMessages()
        val req = chatRepo.friendRequests.value.find { it.id == requestId || it.pairId == requestId } ?: return false
        if (req.direction != FriendRequestDirection.RECEIVED) {
            _errorMessage.value = "Seul le destinataire peut accepter cette demande."
            return false
        }
        val meUid = currentUid()
        val friendUser = req.user
        val now = System.currentTimeMillis()
        val pairId = FriendFirestoreSync.canonicalPairId(meUid, friendUser.id)
        val note = chatRepo.privateUserNotes.value[friendUser.id].orEmpty()
        val rel = FriendRelationship(
            friendUid = friendUser.id,
            pairId = pairId,
            user = friendUser,
            addedAtMs = now,
            lastActiveAtMs = friendUser.lastActiveAtMs,
            lastActivityLabel = friendUser.lastActivityLabel.ifBlank { friendUser.customStatus },
            privateNote = note
        )
        materializedPairIds.add(pairId)
        chatRepo.friendRequestsMutableFlow().value =
            chatRepo.friendRequestsMutableFlow().value.filterNot { it.id == req.id || it.pairId == req.pairId }
        _friends.value = (_friends.value.filterNot { it.friendUid == friendUser.id } + rel)

        ensureCoherentDirectConversation(friendUser, bypassDmPolicy = true, openNow = false)
        _feedbackMessage.value = "${friendUser.name} a été ajouté à vos amis !"
        chatRepo.notifyCommunityEvent(
            title = "Nouvel ami : ${friendUser.name}",
            body = "Vous êtes désormais ami avec ${friendUser.name}${friendUser.tag}.",
            sourceLabel = "Amis Raven",
            type = NotificationType.FRIEND_REQUEST
        )

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    // STRICT RULE: only write to current user's OWN /users/{authUid}/friends/{friendUid}
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("friends").document(friendUser.id)
                            .set(FriendFirestoreSync.friendToMap(rel))
                    )
                    Tasks.await(
                        db.collection("friend_requests").document(pairId)
                            .update(mapOf("status" to "ACCEPTED", "updatedAtMs" to now))
                    )
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to accept friend request in Firestore", e)
                }
            }
        }
        return true
    }

    fun declineFriendRequest(requestId: String): Boolean {
        clearMessages()
        val req = chatRepo.friendRequests.value.find { it.id == requestId || it.pairId == requestId } ?: return false
        if (req.direction != FriendRequestDirection.RECEIVED) {
            _errorMessage.value = "Utilisez « Annuler » pour retirer une demande envoyée."
            return false
        }
        chatRepo.friendRequestsMutableFlow().value =
            chatRepo.friendRequestsMutableFlow().value.filterNot { it.id == req.id || it.pairId == req.pairId }
        _feedbackMessage.value = "Demande de ${req.user.name} refusée."
        updateRemoteRequestStatus(req.pairId, "DECLINED")
        return true
    }

    fun cancelFriendRequest(requestId: String): Boolean {
        clearMessages()
        val req = chatRepo.friendRequests.value.find { it.id == requestId || it.pairId == requestId } ?: return false
        if (req.direction != FriendRequestDirection.SENT) {
            _errorMessage.value = "Utilisez « Refuser » pour décliner une demande reçue."
            return false
        }
        chatRepo.friendRequestsMutableFlow().value =
            chatRepo.friendRequestsMutableFlow().value.filterNot { it.id == req.id || it.pairId == req.pairId }
        _feedbackMessage.value = "Demande envoyée à ${req.user.name} annulée."
        updateRemoteRequestStatus(req.pairId, "CANCELLED")
        return true
    }

    fun declineOrCancelFriendRequest(requestId: String): Boolean {
        val req = chatRepo.friendRequests.value.find { it.id == requestId || it.pairId == requestId } ?: return false
        return if (req.direction == FriendRequestDirection.RECEIVED) {
            declineFriendRequest(req.id)
        } else {
            cancelFriendRequest(req.id)
        }
    }

    fun removeFriend(friendUid: String) {
        clearMessages()
        val existing = _friends.value.find { it.friendUid == friendUid } ?: return
        val meUid = currentUid()
        val pairId = FriendFirestoreSync.canonicalPairId(meUid, friendUid)
        _friends.value = _friends.value.filterNot { it.friendUid == friendUid }
        materializedPairIds.remove(pairId)

        val selectedDm = chatRepo.selectedDmId.value
        val matchedDm = chatRepo.directConversations.value.find { !it.isGroup && it.friend.id == friendUid }
        if (selectedDm != null && matchedDm != null && selectedDm == matchedDm.id) {
            chatRepo.selectedDmIdMutableFlow().value = null
        }

        _feedbackMessage.value = "${existing.user.name} a été retiré de votre liste d'amis."

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    // Owner deletes ONLY their own /users/{authUid}/friends/{friendUid}
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("friends").document(friendUid)
                            .delete()
                    )
                    updateRemoteRequestStatusSync(db, pairId, "CANCELLED")
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to remove friend from Firestore", e)
                }
            }
        }
    }

    fun blockUser(user: User) {
        clearMessages()
        val meUid = currentUid()
        if (user.id == meUid || user.id == "user_me") return
        val pairId = FriendFirestoreSync.canonicalPairId(meUid, user.id)
        val now = System.currentTimeMillis()

        val blockedFlow = chatRepo.blockedUsersMutableFlow()
        if (blockedFlow.value.none { it.id == user.id }) {
            blockedFlow.value = blockedFlow.value + user.copy(status = UserStatus.OFFLINE)
        }
        _friends.value = _friends.value.filterNot { it.friendUid == user.id }
        chatRepo.friendRequestsMutableFlow().value =
            chatRepo.friendRequestsMutableFlow().value.filterNot { it.user.id == user.id }
        materializedPairIds.remove(pairId)

        val matchedDmIds = chatRepo.directConversationsMutableFlow().value
            .filter { !it.isGroup && it.friend.id == user.id }
            .map { it.id }
            .toSet()
        chatRepo.directConversationsMutableFlow().value =
            chatRepo.directConversationsMutableFlow().value.filterNot { it.id in matchedDmIds }
        if (chatRepo.selectedDmId.value in matchedDmIds || chatRepo.selectedDmId.value == "dm_${user.id}") {
            chatRepo.selectedDmIdMutableFlow().value = null
        }

        _feedbackMessage.value = "${user.name} a été bloqué."

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("blocked_users").document(user.id)
                            .set(FriendFirestoreSync.blockedToMap(authUid, user, now))
                    )
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("friends").document(user.id)
                            .delete()
                    )
                    updateRemoteRequestStatusSync(db, pairId, "CANCELLED")
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to block user in Firestore", e)
                }
            }
        }
    }

    fun unblockUser(userId: String) {
        clearMessages()
        val existing = chatRepo.blockedUsers.value.find { it.id == userId }
        chatRepo.blockedUsersMutableFlow().value =
            chatRepo.blockedUsersMutableFlow().value.filterNot { it.id == userId }
        _feedbackMessage.value = "${existing?.name ?: "L'utilisateur"} a été débloqué."

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("blocked_users").document(userId)
                            .delete()
                    )
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to unblock user in Firestore", e)
                }
            }
        }
    }

    fun openOrCreateDirectConversation(
        targetUser: User,
        bypassDmPolicy: Boolean = false
    ): DirectConversation? {
        return ensureCoherentDirectConversation(targetUser, bypassDmPolicy = bypassDmPolicy, openNow = true)
    }

    private fun ensureCoherentDirectConversation(
        targetUser: User,
        bypassDmPolicy: Boolean,
        openNow: Boolean
    ): DirectConversation? {
        if (chatRepo.blockedUsers.value.any { it.id == targetUser.id }) {
            _errorMessage.value = "Impossible d'envoyer un message privé à un utilisateur bloqué."
            return null
        }

        val meUid = currentUid()
        val pairKey = FriendFirestoreSync.canonicalPairId(meUid, targetUser.id)
        val dmFlow = chatRepo.directConversationsMutableFlow()

        // Coherently reuse ANY existing 1-on-1 conversation (including dm_thomas, dm_emma, dm_<uid>, or dm_<pairId>)
        val existing = dmFlow.value.find { conv ->
            !conv.isGroup && (
                conv.friend.id == targetUser.id ||
                    conv.dmPairKey == pairKey ||
                    conv.id == "dm_${targetUser.id}" ||
                    conv.id == FriendFirestoreSync.canonicalDmId(meUid, targetUser.id)
                )
        }
        if (existing != null) {
            val normalized = existing.copy(
                friend = if (existing.friend.id == targetUser.id) targetUser.copy(
                    avatarColor = existing.friend.avatarColor,
                    bio = existing.friend.bio.ifBlank { targetUser.bio }
                ) else existing.friend,
                participantIds = listOf(meUid, targetUser.id).distinct().sorted(),
                dmPairKey = pairKey
            )
            dmFlow.value = dmFlow.value.map { if (it.id == existing.id) normalized else it }
            if (openNow) {
                chatRepo.selectServer("dm_tab")
                chatRepo.selectDm(normalized.id)
            }
            return normalized
        }

        if (!bypassDmPolicy) {
            val dirEntry = _directoryUsers.value[targetUser.id]
            val dmPolicy = dirEntry?.allowDmsFrom ?: DirectMessagePrivacyPolicy.EVERYONE
            val isFriend = _friends.value.any { it.friendUid == targetUser.id }
            val hasMutualServer = computeMutualServerIds(targetUser.id).isNotEmpty()
            when (dmPolicy) {
                DirectMessagePrivacyPolicy.FRIENDS_ONLY -> if (!isFriend) {
                    _errorMessage.value = "${targetUser.name} autorise les DM uniquement pour ses amis."
                    return null
                }
                DirectMessagePrivacyPolicy.MUTUAL_SERVERS -> if (!isFriend && !hasMutualServer) {
                    _errorMessage.value = "${targetUser.name} autorise les DM uniquement avec ses amis et serveurs communs."
                    return null
                }
                DirectMessagePrivacyPolicy.EVERYONE -> Unit
            }
        }

        val canonicalId = FriendFirestoreSync.canonicalDmId(meUid, targetUser.id)
        val created = DirectConversation(
            id = canonicalId,
            friend = targetUser,
            lastMessage = "Début de votre conversation privée avec ${targetUser.name}",
            lastMessageTime = "À l'instant",
            unreadCount = 0,
            isGroup = false,
            participantIds = listOf(meUid, targetUser.id).distinct().sorted(),
            dmPairKey = pairKey
        )
        dmFlow.value = deduplicateDirectConversations(listOf(created) + dmFlow.value, meUid)
        if (openNow) {
            chatRepo.selectServer("dm_tab")
            chatRepo.selectDm(created.id)
        }

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        if (db != null && authUid != null) {
            val myName = chatRepo.currentUser.value.name
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("direct_conversations").document(created.id).set(
                            mapOf(
                                "id" to created.id,
                                "participantIds" to listOf(authUid, targetUser.id).distinct().sorted(),
                                "participantNames" to mapOf(authUid to myName, targetUser.id to targetUser.name),
                                "dmPairKey" to pairKey,
                                "isGroup" to false,
                                "lastMessage" to created.lastMessage,
                                "lastMessageTime" to created.lastMessageTime,
                                "updatedAtMs" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        )
                    )
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to create DM doc in Firestore", e)
                }
            }
        }
        return created
    }

    fun updatePrivateNote(userId: String, note: String) {
        val clean = note.trim()
        chatRepo.setPrivateUserNote(userId, clean)
        _friends.value = _friends.value.map { rel ->
            if (rel.friendUid == userId) rel.copy(privateNote = clean) else rel
        }
        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid
        val isFriend = _friends.value.any { it.friendUid == userId }
        if (db != null && authUid != null && isFriend) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid)
                            .collection("friends").document(userId)
                            .update("privateNote", clean.take(400))
                    )
                } catch (_: Exception) {
                }
            }
        }
        _feedbackMessage.value = "Note personnelle enregistrée."
    }

    fun updatePrivacySettings(updated: FriendPrivacySettings) {
        _privacySettings.value = updated
        chatRepo.updateAppSettings { app ->
            app.copy(
                allowMemberDms = updated.allowDmsFrom != DirectMessagePrivacyPolicy.FRIENDS_ONLY,
                showActivityStatus = updated.showLastActivity
            )
        }
        _feedbackMessage.value = "Paramètres de confidentialité des amis mis à jour."

        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (db != null) {
            val curr = chatRepo.currentUser.value
            val actLabel = curr.activity.ifBlank { curr.customStatus }
            val now = curr.lastActiveAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val publicUpdates = FriendFirestoreSync.privacyToPublicProfileUpdates(
                privacy = updated,
                status = curr.status,
                lastActiveAtMs = now,
                lastActivityLabel = actLabel
            )
            publicUpdates["userId"] = authUid
            publicUpdates["name"] = curr.name
            publicUpdates["isPublic"] = true
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("users").document(authUid).set(
                            mapOf(
                                "userId" to authUid,
                                "name" to curr.name,
                                "friendRequestPolicy" to updated.friendRequestPolicy.name,
                                "allowDmsFrom" to updated.allowDmsFrom.name,
                                "showOnlineStatus" to updated.showOnlineStatus,
                                "showLastActivity" to updated.showLastActivity,
                                "notifyFriendRequests" to updated.notifyFriendRequests
                            ),
                            SetOptions.merge()
                        )
                    )
                    Tasks.await(
                        db.collection("public_profiles").document(authUid).set(publicUpdates, SetOptions.merge())
                    )
                } catch (e: Exception) {
                    Log.w("FriendRepo", "Failed to save privacy settings", e)
                }
            }
        }
    }

    fun loadPrivacyFromUserDoc(
        reqPolicyStr: String?,
        dmPolicyStr: String?,
        showOnline: Boolean?,
        showActivity: Boolean?,
        notifyRequests: Boolean?
    ) {
        val current = _privacySettings.value
        _privacySettings.value = current.copy(
            friendRequestPolicy = FriendRequestPolicy.entries.find { it.name == reqPolicyStr } ?: current.friendRequestPolicy,
            allowDmsFrom = DirectMessagePrivacyPolicy.entries.find { it.name == dmPolicyStr } ?: current.allowDmsFrom,
            showOnlineStatus = showOnline ?: current.showOnlineStatus,
            showLastActivity = showActivity ?: current.showLastActivity,
            notifyFriendRequests = notifyRequests ?: current.notifyFriendRequests
        )
    }

    fun touchCurrentUserActivity(activityLabel: String? = null) {
        val now = System.currentTimeMillis()
        val curr = chatRepo.currentUser.value
        val nextLabel = activityLabel?.trim() ?: curr.lastActivityLabel.ifBlank { curr.activity }
        chatRepo.currentUserMutableFlow().value = curr.copy(
            lastActiveAtMs = now,
            lastActivityLabel = nextLabel
        )
        val db = chatRepo.firestoreDb()
        val authUid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (db != null && _privacySettings.value.showLastActivity) {
            chatRepo.repoScope.launch(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("public_profiles").document(authUid).set(
                            mapOf(
                                "userId" to authUid,
                                "name" to curr.name,
                                "isPublic" to true,
                                "status" to if (_privacySettings.value.showOnlineStatus) curr.status.name else UserStatus.OFFLINE.name,
                                "lastActiveAtMs" to now,
                                "lastActivityLabel" to nextLabel.take(120)
                            ),
                            SetOptions.merge()
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    fun syncHookAccept(req: FriendRequest) {
        val friendUser = req.user
        val meUid = currentUid()
        val pairId = FriendFirestoreSync.canonicalPairId(meUid, friendUser.id)
        val rel = FriendRelationship(
            friendUid = friendUser.id,
            pairId = pairId,
            user = friendUser,
            addedAtMs = System.currentTimeMillis(),
            lastActiveAtMs = friendUser.lastActiveAtMs,
            lastActivityLabel = friendUser.lastActivityLabel.ifBlank { friendUser.customStatus },
            privateNote = chatRepo.privateUserNotes.value[friendUser.id].orEmpty()
        )
        _friends.value = (_friends.value.filterNot { it.friendUid == friendUser.id } + rel)
    }

    fun syncHookBlock(user: User) {
        _friends.value = _friends.value.filterNot { it.friendUid == user.id }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    private fun clearMessages() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    private fun writeOwnFriendDoc(
        db: com.google.firebase.firestore.FirebaseFirestore,
        uid: String,
        rel: FriendRelationship
    ) {
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                Tasks.await(
                    db.collection("users").document(uid)
                        .collection("friends").document(rel.friendUid)
                        .set(FriendFirestoreSync.friendToMap(rel), SetOptions.merge())
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun updateRemoteRequestStatus(pairId: String, status: String) {
        val db = chatRepo.firestoreDb() ?: return
        chatRepo.authInstance()?.currentUser ?: return
        chatRepo.repoScope.launch(Dispatchers.IO) {
            updateRemoteRequestStatusSync(db, pairId, status)
        }
    }

    private fun updateRemoteRequestStatusSync(
        db: com.google.firebase.firestore.FirebaseFirestore,
        pairId: String,
        status: String
    ) {
        try {
            val ref = db.collection("friend_requests").document(pairId)
            val snap = Tasks.await(ref.get())
            if (snap.exists()) {
                Tasks.await(
                    ref.update(
                        mapOf(
                            "status" to status,
                            "updatedAtMs" to System.currentTimeMillis()
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("FriendRepo", "Failed to update request status ($status)", e)
        }
    }
}
