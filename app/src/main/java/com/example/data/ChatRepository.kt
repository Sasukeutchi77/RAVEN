package com.example.data

import android.util.Log
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatRepository(
    private val scope: CoroutineScope,
    private val databaseId: String? = null
) {
    private val _currentUser = MutableStateFlow(MockData.currentUser)
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private val _servers = MutableStateFlow(MockData.initialServers)
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _selectedServerId = MutableStateFlow("server_main")
    val selectedServerId: StateFlow<String> = _selectedServerId.asStateFlow()

    private val _selectedChannelId = MutableStateFlow("c_general")
    val selectedChannelId: StateFlow<String> = _selectedChannelId.asStateFlow()

    private val _messages = MutableStateFlow(MockData.initialMessages)
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    private val _directConversations = MutableStateFlow(MockData.initialDirectConversations)
    val directConversations: StateFlow<List<DirectConversation>> = _directConversations.asStateFlow()

    private val _selectedDmId = MutableStateFlow<String?>("dm_thomas")
    val selectedDmId: StateFlow<String?> = _selectedDmId.asStateFlow()

    // Voice State
    private val _connectedVoiceChannel = MutableStateFlow<Channel?>(null)
    val connectedVoiceChannel: StateFlow<Channel?> = _connectedVoiceChannel.asStateFlow()

    private val _isUserMuted = MutableStateFlow(false)
    val isUserMuted: StateFlow<Boolean> = _isUserMuted.asStateFlow()

    private val _isUserDeafened = MutableStateFlow(false)
    val isUserDeafened: StateFlow<Boolean> = _isUserDeafened.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<Message?>(null)
    val replyingToMessage: StateFlow<Message?> = _replyingToMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pinnedMessagesOnly = MutableStateFlow(false)
    val pinnedMessagesOnly: StateFlow<Boolean> = _pinnedMessagesOnly.asStateFlow()

    private val _chatBackgrounds = MutableStateFlow<Map<String, ChatBackgroundTheme>>(emptyMap())
    val chatBackgrounds: StateFlow<Map<String, ChatBackgroundTheme>> = _chatBackgrounds.asStateFlow()

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (!databaseId.isNullOrBlank()) {
                FirebaseFirestore.getInstance(databaseId)
            } else null
        } catch (e: Exception) {
            Log.w("ChatRepo", "Firestore not available or failed initialization", e)
            null
        }
    }

    private var activeListener: ListenerRegistration? = null

    init {
        // Voice heartbeat simulation
        scope.launch(Dispatchers.Default) {
            while (true) {
                delay(3500)
                val voice = _connectedVoiceChannel.value ?: continue
                val updatedUsers = voice.activeVoiceUsers.map { user ->
                    if (user.id != _currentUser.value.id) {
                        user.copy(isSpeaking = (0..10).random() > 5)
                    } else {
                        user
                    }
                }
                _connectedVoiceChannel.value = voice.copy(activeVoiceUsers = updatedUsers)
            }
        }
    }

    fun syncFirebaseUser() {
        val authUser = try {
            Firebase.auth.currentUser
        } catch (e: Exception) {
            null
        } ?: return
        _currentUser.value = _currentUser.value.copy(
            id = authUser.uid,
            name = authUser.displayName ?: _currentUser.value.name,
            avatarUrl = authUser.photoUrl?.toString() ?: _currentUser.value.avatarUrl,
            email = authUser.email ?: _currentUser.value.email,
            role = UserRole.ADMIN
        )
    }

    fun selectServer(serverId: String) {
        _selectedServerId.value = serverId
        val server = _servers.value.find { it.id == serverId }
        if (server != null && !server.isDmTab) {
            val firstText = server.channels.firstOrNull { it.type == ChannelType.TEXT }
                ?: server.channels.firstOrNull()
            firstText?.let { selectChannel(it) }
        }
    }

    fun selectChannel(channel: Channel) {
        if (channel.type == ChannelType.VOICE) {
            joinVoiceChannel(channel)
        } else {
            _selectedChannelId.value = channel.id
            listenToChannelMessages(channel.id)
        }
    }

    fun selectDm(dmId: String) {
        _selectedDmId.value = dmId
        _directConversations.value = _directConversations.value.map {
            if (it.id == dmId) it.copy(unreadCount = 0) else it
        }
        if (dmId.isNotBlank()) {
            listenToDmMessages(dmId)
        }
    }

    private fun listenToChannelMessages(channelId: String) {
        val db = firestore ?: return
        val currentUserId = try {
            Firebase.auth.currentUser?.uid
        } catch (e: Exception) {
            null
        } ?: return
        activeListener?.remove()

        activeListener = db.collection("channels").document(channelId).collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepo", "Snapshot listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val firestoreMsgs = snapshot.documents.mapNotNull { doc ->
                        val text = doc.getString("text") ?: ""
                        val senderName = doc.getString("senderName") ?: "Membre"
                        val senderId = doc.getString("senderId") ?: ""
                        val imageUrl = doc.getString("imageUrl")
                        val audioUrl = doc.getString("audioUrl")
                        val replyToText = doc.getString("replyToText")
                        val isPinned = doc.getBoolean("isPinned") ?: false

                        Message(
                            id = doc.id,
                            channelId = channelId,
                            sender = User(id = senderId, name = senderName, avatarColor = 0xFF404249),
                            text = text,
                            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                            replyToText = replyToText,
                            imageUrl = imageUrl,
                            audioUrl = audioUrl,
                            isPinned = isPinned
                        )
                    }
                    val existing = _messages.value[channelId] ?: emptyList()
                    val merged = (existing + firestoreMsgs).distinctBy { it.id }
                    _messages.value = _messages.value + (channelId to merged)
                }
            }
    }

    private fun listenToDmMessages(dmId: String) {
        val db = firestore ?: return
        val currentUser = try {
            Firebase.auth.currentUser
        } catch (e: Exception) {
            null
        } ?: return
        activeListener?.remove()

        activeListener = db.collection("direct_conversations").document(dmId).collection("messages")
            .orderBy("createdAt")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.isEmpty) {
                    val firestoreMsgs = snapshot.documents.mapNotNull { doc ->
                        Message(
                            id = doc.id,
                            channelId = dmId,
                            sender = User(id = doc.getString("senderId") ?: "", name = doc.getString("senderName") ?: "Ami"),
                            text = doc.getString("text") ?: "",
                            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                            imageUrl = doc.getString("imageUrl"),
                            audioUrl = doc.getString("audioUrl")
                        )
                    }
                    val existing = _messages.value[dmId] ?: emptyList()
                    val merged = (existing + firestoreMsgs).distinctBy { it.id }
                    _messages.value = _messages.value + (dmId to merged)
                }
            }
    }

    fun setReplyingTo(message: Message?) {
        _replyingToMessage.value = message
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun togglePinnedFilter() {
        _pinnedMessagesOnly.value = !_pinnedMessagesOnly.value
    }

    fun sendMessage(
        targetId: String,
        text: String,
        imageUrl: String? = null,
        audioUrl: String? = null,
        audioDurationSec: Int = 0,
        attachmentName: String? = null,
        attachmentType: String? = null
    ) {
        if (text.isBlank() && imageUrl == null && audioUrl == null && attachmentName == null) return

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        val replyText = _replyingToMessage.value?.let {
            "${it.sender.name}: ${it.text.take(40)}..."
        }

        // Parse mentions
        val mentions = text.split(" ")
            .filter { it.startsWith("@") && it.length > 1 }
            .map { it.removePrefix("@") }

        val newMessage = Message(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            channelId = targetId,
            sender = _currentUser.value,
            text = text.trim(),
            timestamp = currentTime,
            replyToText = replyText,
            reactions = emptyList(),
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            audioDurationSec = audioDurationSec,
            attachmentName = attachmentName,
            attachmentType = attachmentType,
            mentions = mentions
        )

        // Optimistic local update
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + newMessage))
        _replyingToMessage.value = null

        // Update DM last message
        if (_selectedServerId.value == "dm_tab") {
            val preview = when {
                text.isNotBlank() -> text
                imageUrl != null -> "📷 Image partagée"
                audioUrl != null -> "🎤 Note vocale ($audioDurationSec s)"
                else -> "Pièce jointe"
            }
            _directConversations.value = _directConversations.value.map {
                if (it.id == targetId) it.copy(lastMessage = preview, lastMessageTime = currentTime) else it
            }
        }

        // Sync with Firestore if available and authenticated
        val db = firestore
        val user = try {
            Firebase.auth.currentUser
        } catch (e: Exception) {
            null
        }
        if (db != null && user != null) {
            val payload = hashMapOf<String, Any>(
                "senderId" to user.uid,
                "senderName" to (user.displayName ?: _currentUser.value.name),
                "text" to text.trim(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            imageUrl?.let { payload["imageUrl"] = it }
            audioUrl?.let { payload["audioUrl"] = it }
            replyText?.let { payload["replyToText"] = it }

            scope.launch(Dispatchers.IO) {
                try {
                    if (_selectedServerId.value == "dm_tab") {
                        db.collection("direct_conversations").document(targetId)
                            .collection("messages").document(newMessage.id).set(payload)
                    } else {
                        db.collection("channels").document(targetId)
                            .collection("messages").document(newMessage.id).set(payload)
                    }
                } catch (e: Exception) {
                    Log.w("ChatRepo", "Firestore write failed", e)
                }
            }
        }
    }

    fun toggleReaction(channelId: String, messageId: String, emoji: String) {
        val currentList = _messages.value[channelId] ?: return
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId) {
                val existing = msg.reactions.find { it.emoji == emoji }
                val newReactions = if (existing != null) {
                    if (existing.userReacted) {
                        if (existing.count <= 1) {
                            msg.reactions.filterNot { it.emoji == emoji }
                        } else {
                            msg.reactions.map {
                                if (it.emoji == emoji) it.copy(count = it.count - 1, userReacted = false) else it
                            }
                        }
                    } else {
                        msg.reactions.map {
                            if (it.emoji == emoji) it.copy(count = it.count + 1, userReacted = true) else it
                        }
                    }
                } else {
                    msg.reactions + Reaction(emoji = emoji, count = 1, userReacted = true)
                }
                msg.copy(reactions = newReactions)
            } else {
                msg
            }
        }
        _messages.value = _messages.value + (channelId to updatedList)
    }

    fun togglePinMessage(channelId: String, messageId: String) {
        val currentList = _messages.value[channelId] ?: return
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId) msg.copy(isPinned = !msg.isPinned) else msg
        }
        _messages.value = _messages.value + (channelId to updatedList)
    }

    fun deleteMessage(channelId: String, messageId: String) {
        val currentList = _messages.value[channelId] ?: return
        val updatedList = currentList.filterNot { it.id == messageId }
        _messages.value = _messages.value + (channelId to updatedList)
    }

    fun joinVoiceChannel(channel: Channel) {
        val userInVoice = _currentUser.value.copy(
            isSpeaking = false,
            isMuted = _isUserMuted.value,
            isDeafened = _isUserDeafened.value
        )
        val currentParticipants = channel.activeVoiceUsers.filterNot { it.id == userInVoice.id }
        val updatedChannel = channel.copy(activeVoiceUsers = currentParticipants + userInVoice)
        _connectedVoiceChannel.value = updatedChannel

        _servers.value = _servers.value.map { server ->
            if (server.id == channel.serverId) {
                server.copy(channels = server.channels.map {
                    if (it.id == channel.id) updatedChannel else it
                })
            } else server
        }
    }

    fun leaveVoiceChannel() {
        val channel = _connectedVoiceChannel.value ?: return
        val updatedParticipants = channel.activeVoiceUsers.filterNot { it.id == _currentUser.value.id }
        val updatedChannel = channel.copy(activeVoiceUsers = updatedParticipants)
        _connectedVoiceChannel.value = null

        _servers.value = _servers.value.map { server ->
            if (server.id == channel.serverId) {
                server.copy(channels = server.channels.map {
                    if (it.id == channel.id) updatedChannel else it
                })
            } else server
        }
    }

    fun toggleMute() {
        val newMute = !_isUserMuted.value
        _isUserMuted.value = newMute
        val channel = _connectedVoiceChannel.value
        if (channel != null) {
            val updated = channel.activeVoiceUsers.map {
                if (it.id == _currentUser.value.id) it.copy(isMuted = newMute) else it
            }
            _connectedVoiceChannel.value = channel.copy(activeVoiceUsers = updated)
        }
    }

    fun toggleDeafen() {
        val newDeafen = !_isUserDeafened.value
        _isUserDeafened.value = newDeafen
        if (newDeafen) {
            _isUserMuted.value = true
        }
        val channel = _connectedVoiceChannel.value
        if (channel != null) {
            val updated = channel.activeVoiceUsers.map {
                if (it.id == _currentUser.value.id) it.copy(isDeafened = newDeafen, isMuted = if (newDeafen) true else it.isMuted) else it
            }
            _connectedVoiceChannel.value = channel.copy(activeVoiceUsers = updated)
        }
    }

    fun updateUserStatus(status: UserStatus, customStatus: String? = null) {
        _currentUser.value = _currentUser.value.copy(
            status = status,
            customStatus = customStatus ?: _currentUser.value.customStatus
        )
        saveProfileToFirestore()
    }

    fun updateUserProfile(
        name: String? = null,
        pronouns: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        avatarColor: Long? = null,
        bannerUrl: String? = null,
        bannerColor: Long? = null,
        customStatus: String? = null,
        customStatusEmoji: String? = null,
        status: UserStatus? = null,
        activity: String? = null,
        email: String? = null,
        phone: String? = null
    ) {
        val curr = _currentUser.value
        _currentUser.value = curr.copy(
            name = name ?: curr.name,
            pronouns = pronouns ?: curr.pronouns,
            bio = bio ?: curr.bio,
            avatarUrl = if (avatarUrl != null) avatarUrl.ifBlank { null } else curr.avatarUrl,
            avatarColor = avatarColor ?: curr.avatarColor,
            bannerUrl = if (bannerUrl != null) bannerUrl.ifBlank { null } else curr.bannerUrl,
            bannerColor = bannerColor ?: curr.bannerColor,
            customStatus = customStatus ?: curr.customStatus,
            customStatusEmoji = customStatusEmoji ?: curr.customStatusEmoji,
            status = status ?: curr.status,
            activity = activity ?: curr.activity,
            email = email ?: curr.email,
            phone = phone ?: curr.phone
        )
        saveProfileToFirestore()
    }

    fun toggleUserBadge(badgeId: String) {
        val curr = _currentUser.value
        val updated = if (curr.badges.contains(badgeId)) {
            curr.badges.filterNot { it == badgeId }
        } else {
            curr.badges + badgeId
        }
        _currentUser.value = curr.copy(badges = updated)
        saveProfileToFirestore()
    }

    fun setChatBackground(targetId: String, theme: ChatBackgroundTheme) {
        _chatBackgrounds.value = _chatBackgrounds.value + (targetId to theme)
    }

    fun updateOrbsBalance(balance: Int) {
        _currentUser.value = _currentUser.value.copy(orbsBalance = balance)
        saveProfileToFirestore()
    }

    fun equipAvatarDecoration(decorationKey: String?) {
        _currentUser.value = _currentUser.value.copy(avatarDecoration = decorationKey)
        saveProfileToFirestore()
    }

    fun createPoll(
        targetId: String,
        question: String,
        options: List<String>,
        allowsMultiple: Boolean = false
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val pollOptions = options.filter { it.isNotBlank() }.mapIndexed { idx, opt ->
            PollOption(id = "opt_$idx", text = opt.trim(), votes = emptyList())
        }
        val poll = Poll(question = question.trim(), options = pollOptions, allowsMultiple = allowsMultiple)
        val newMessage = Message(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            channelId = targetId,
            sender = _currentUser.value,
            text = "Sondage : $question",
            timestamp = currentTime,
            poll = poll
        )
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + newMessage))
    }

    fun votePoll(targetId: String, messageId: String, optionId: String) {
        val currentList = _messages.value[targetId] ?: return
        val userId = _currentUser.value.id
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId && msg.poll != null) {
                val p = msg.poll
                val updatedOptions = p.options.map { opt ->
                    val userVotedThis = opt.votes.contains(userId)
                    if (opt.id == optionId) {
                        if (userVotedThis) {
                            opt.copy(votes = opt.votes - userId)
                        } else {
                            opt.copy(votes = opt.votes + userId)
                        }
                    } else if (!p.allowsMultiple) {
                        opt.copy(votes = opt.votes - userId)
                    } else {
                        opt
                    }
                }
                msg.copy(poll = p.copy(options = updatedOptions))
            } else {
                msg
            }
        }
        _messages.value = _messages.value + (targetId to updatedList)
    }

    private fun saveProfileToFirestore() {
        val db = firestore ?: return
        val authUser = try { Firebase.auth.currentUser } catch (e: Exception) { null } ?: return
        val user = _currentUser.value
        val data = hashMapOf<String, Any>(
            "name" to user.name,
            "bio" to user.bio,
            "pronouns" to user.pronouns,
            "status" to user.status.name,
            "customStatus" to user.customStatus,
            "customStatusEmoji" to user.customStatusEmoji,
            "avatarColor" to user.avatarColor,
            "bannerColor" to user.bannerColor,
            "activity" to user.activity
        )
        user.avatarUrl?.let { data["avatarUrl"] = it }
        user.bannerUrl?.let { data["bannerUrl"] = it }
        user.email?.let { data["email"] = it }

        scope.launch(Dispatchers.IO) {
            try {
                db.collection("users").document(authUser.uid).set(data)
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync user profile to Firestore", e)
            }
        }
    }

    fun createServer(name: String, iconText: String) {
        val newServerId = "server_${UUID.randomUUID().toString().take(6)}"
        val defaultChannels = listOf(
            Channel(
                id = "${newServerId}_general",
                serverId = newServerId,
                name = "général",
                type = ChannelType.TEXT,
                category = "SALONS TEXTUELS"
            ),
            Channel(
                id = "${newServerId}_voice",
                serverId = newServerId,
                name = "Salon vocal",
                type = ChannelType.VOICE,
                category = "SALONS VOCAUX"
            )
        )
        val newServer = Server(
            id = newServerId,
            name = name,
            iconText = iconText.ifBlank { name.take(2).uppercase() },
            iconBgColor = 0xFF2B2D31,
            channels = defaultChannels,
            members = listOf(_currentUser.value),
            ownerId = _currentUser.value.id
        )
        _servers.value = _servers.value + newServer
        selectServer(newServerId)
    }

    fun createChannel(
        serverId: String,
        name: String,
        type: ChannelType,
        category: String,
        isPrivate: Boolean = false,
        allowedRoles: List<UserRole> = emptyList()
    ) {
        val cleanName = name.trim().lowercase().replace(" ", "-")
        val newChannel = Channel(
            id = "c_${UUID.randomUUID().toString().take(6)}",
            serverId = serverId,
            name = cleanName,
            type = type,
            category = category.ifBlank { if (type == ChannelType.VOICE) "SALONS VOCAUX" else "SALONS TEXTUELS" },
            isPrivate = isPrivate,
            allowedRoles = if (isPrivate) (allowedRoles.ifEmpty { listOf(UserRole.ADMIN, UserRole.MODERATOR) }) else emptyList()
        )
        _servers.value = _servers.value.map { s ->
            if (s.id == serverId) s.copy(channels = s.channels + newChannel) else s
        }
        if (type == ChannelType.TEXT) {
            selectChannel(newChannel)
        }
    }

    fun addFriend(tagOrName: String): Boolean {
        if (tagOrName.isBlank()) return false
        val newFriend = User(
            id = "user_${UUID.randomUUID().toString().take(6)}",
            name = tagOrName.substringBefore("#"),
            tag = if (tagOrName.contains("#")) "#${tagOrName.substringAfter("#")}" else "#${(1000..9999).random()}",
            avatarColor = 0xFF35373C,
            status = UserStatus.ONLINE
        )
        val newDm = DirectConversation(
            id = "dm_${newFriend.id}",
            friend = newFriend,
            lastMessage = "Vous êtes maintenant amis avec ${newFriend.name} !",
            lastMessageTime = "À l'instant",
            unreadCount = 0
        )
        _directConversations.value = listOf(newDm) + _directConversations.value
        selectDm(newDm.id)
        return true
    }
}
