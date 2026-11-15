package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.*
import com.example.util.JukeboxAudioEngine
import com.example.util.RemoteVoicePeerState
import com.example.util.WebRtcSessionState
import com.example.util.WebRtcVoiceManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatRepository(
    private val scope: CoroutineScope,
    private val databaseId: String? = null,
    private val appContext: Context? = null,
    private val injectedFirestore: FirebaseFirestore? = null,
    private val injectedAuth: FirebaseAuth? = null
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

    private val _threads = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val threads: StateFlow<Map<String, List<Message>>> = _threads.asStateFlow()

    private val _notifications = MutableStateFlow(
        listOf(
            InboxNotification(
                id = "notif_welcome",
                title = "Bienvenue sur Raven !",
                body = "Thomas vous a mentionné dans #général : @Jarvis prêt pour le vocal ?",
                sourceLabel = "Mon Serveur • #général",
                timestamp = "11:20",
                type = NotificationType.MENTION,
                isRead = false
            ),
            InboxNotification(
                id = "notif_orbs",
                title = "Bonus Orbs & XP actif",
                body = "Gagnez de l'XP à chaque message, réaction ou partie en vocal pour monter de niveau et gagner des Orbs.",
                sourceLabel = "Système XP",
                timestamp = "10:05",
                type = NotificationType.LEVEL_UP,
                isRead = false
            )
        )
    )
    val notifications: StateFlow<List<InboxNotification>> = _notifications.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettingsState())
    val appSettings: StateFlow<AppSettingsState> = _appSettings.asStateFlow()

    private val _serverFolders = MutableStateFlow<List<ServerFolder>>(emptyList())
    val serverFolders: StateFlow<List<ServerFolder>> = _serverFolders.asStateFlow()

    private val _friendRequests = MutableStateFlow(MockData.initialFriendRequests)
    val friendRequests: StateFlow<List<FriendRequest>> = _friendRequests.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<User>>(emptyList())
    val blockedUsers: StateFlow<List<User>> = _blockedUsers.asStateFlow()

    private val _typingUsersByTarget = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val typingUsersByTarget: StateFlow<Map<String, List<String>>> = _typingUsersByTarget.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _lastCloudSyncTime = MutableStateFlow<String?>(null)
    val lastCloudSyncTime: StateFlow<String?> = _lastCloudSyncTime.asStateFlow()

    private val _quests = MutableStateFlow(ShopCatalog.initialQuests)
    val quests: StateFlow<List<Quest>> = _quests.asStateFlow()

    private val _dailyStreak = MutableStateFlow(3)
    val dailyStreak: StateFlow<Int> = _dailyStreak.asStateFlow()

    private val _streakClaimedToday = MutableStateFlow(false)
    val streakClaimedToday: StateFlow<Boolean> = _streakClaimedToday.asStateFlow()

    private val _privateUserNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val privateUserNotes: StateFlow<Map<String, String>> = _privateUserNotes.asStateFlow()

    private val _slowmodeCooldownUntilByChannel = MutableStateFlow<Map<String, Long>>(emptyMap())
    val slowmodeCooldownUntilByChannel: StateFlow<Map<String, Long>> = _slowmodeCooldownUntilByChannel.asStateFlow()

    private val _lastSendBlockedReason = MutableStateFlow<String?>(null)
    val lastSendBlockedReason: StateFlow<String?> = _lastSendBlockedReason.asStateFlow()

    private val _isFirebaseSignedIn = MutableStateFlow(false)
    val isFirebaseSignedIn: StateFlow<Boolean> = _isFirebaseSignedIn.asStateFlow()

    private val _firebaseUserEmail = MutableStateFlow<String?>(null)
    val firebaseUserEmail: StateFlow<String?> = _firebaseUserEmail.asStateFlow()

    private val _pendingLocalNotification = MutableStateFlow<InboxNotification?>(null)
    val pendingLocalNotification: StateFlow<InboxNotification?> = _pendingLocalNotification.asStateFlow()

    // Bloc 1: Public Discovery
    private val _discoverServers = MutableStateFlow(MockData.publicExploreServers)
    val discoverServers: StateFlow<List<PublicServerListing>> = _discoverServers.asStateFlow()

    // Bloc 2: Voice Jukebox & Mixer
    private val _jukeboxState = MutableStateFlow(
        VoiceJukeboxState(
            isPlaying = false,
            currentTrack = MockData.jukeboxCatalogTracks.firstOrNull(),
            queue = MockData.jukeboxCatalogTracks.drop(1).take(3),
            elapsedSec = 24
        )
    )
    val jukeboxState: StateFlow<VoiceJukeboxState> = _jukeboxState.asStateFlow()

    private val _participantVolumes = MutableStateFlow<Map<String, Float>>(emptyMap())
    val participantVolumes: StateFlow<Map<String, Float>> = _participantVolumes.asStateFlow()

    // Bloc 4: Whiteboard strokes per channel / voice room
    private val _whiteboardStrokes = MutableStateFlow<Map<String, List<WhiteboardStroke>>>(emptyMap())
    val whiteboardStrokes: StateFlow<Map<String, List<WhiteboardStroke>>> = _whiteboardStrokes.asStateFlow()

    // Bloc 5: Drafts, Scheduled Messages & Reminders
    private val _draftsByTarget = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftsByTarget: StateFlow<Map<String, String>> = _draftsByTarget.asStateFlow()

    private val _scheduledMessages = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val scheduledMessages: StateFlow<List<ScheduledMessage>> = _scheduledMessages.asStateFlow()

    private val _messageReminders = MutableStateFlow<List<MessageReminder>>(emptyList())
    val messageReminders: StateFlow<List<MessageReminder>> = _messageReminders.asStateFlow()

    private val _rtcSessionState = MutableStateFlow(WebRtcSessionState())
    val rtcSessionState: StateFlow<WebRtcSessionState> = _rtcSessionState.asStateFlow()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val _jukeboxVolume = MutableStateFlow(0.85f)
    val jukeboxVolume: StateFlow<Float> = _jukeboxVolume.asStateFlow()

    private var cachedFcmToken: String? = appContext?.let {
        try {
            it.getSharedPreferences("raven_prefs", Context.MODE_PRIVATE).getString("fcm_token", null)
        } catch (_: Exception) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        injectedFirestore ?: try {
            if (!databaseId.isNullOrBlank()) {
                FirebaseFirestore.getInstance(databaseId)
            } else null
        } catch (e: Exception) {
            Log.w("ChatRepo", "Firestore not available or failed initialization", e)
            null
        }
    }

    private fun currentAuth(): FirebaseAuth? = injectedAuth ?: try {
        Firebase.auth
    } catch (_: Exception) {
        null
    }

    private val jukeboxAudioEngine: JukeboxAudioEngine? by lazy {
        appContext?.let { ctx ->
            JukeboxAudioEngine(
                context = ctx,
                scope = scope,
                onProgressTick = { posSec ->
                    val jb = _jukeboxState.value
                    val trk = jb.currentTrack
                    if (jb.isPlaying && trk != null && trk.streamUrl != null) {
                        _jukeboxState.value = jb.copy(elapsedSec = posSec.coerceAtMost(trk.durationSec))
                    }
                },
                onTrackEnded = {
                    if (_jukeboxState.value.currentTrack?.streamUrl != null) {
                        skipJukeboxTrack()
                    }
                }
            )
        }
    }

    private val webRtcVoiceManager: WebRtcVoiceManager? by lazy {
        appContext?.let { ctx -> WebRtcVoiceManager(ctx, scope) }
    }

    private var activeListener: ListenerRegistration? = null
    private var activeChannelMetaListener: ListenerRegistration? = null
    private var activeWhiteboardListener: ListenerRegistration? = null
    private var activeJukeboxListener: ListenerRegistration? = null
    private var activeNotificationsListener: ListenerRegistration? = null
    private var activePublicProfilesListener: ListenerRegistration? = null

    init {
        // Voice heartbeat simulation + Jukebox playback ticker
        scope.launch(Dispatchers.Default) {
            var tickCount = 0
            while (true) {
                delay(1000)
                tickCount++
                val jb = _jukeboxState.value
                val track = jb.currentTrack
                if (jb.isPlaying && track != null) {
                    val nextElapsed = jb.elapsedSec + 1
                    if (nextElapsed >= track.durationSec) {
                        val nextTrack = jb.queue.firstOrNull()
                        val remaining = if (jb.queue.size > 1) jb.queue.drop(1) else emptyList()
                        _jukeboxState.value = jb.copy(
                            currentTrack = nextTrack,
                            queue = remaining,
                            elapsedSec = 0,
                            isPlaying = nextTrack != null
                        )
                    } else {
                        _jukeboxState.value = jb.copy(elapsedSec = nextElapsed)
                    }
                }
                if (tickCount % 3 == 0) {
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
        // Forward WebRTC state & remote video tracks when runtime context is attached
        webRtcVoiceManager?.let { rtc ->
            scope.launch {
                rtc.sessionState.collect { state ->
                    _rtcSessionState.value = state
                    val connected = _connectedVoiceChannel.value
                    if (connected != null && state.remotePeers.isNotEmpty()) {
                        val mergedUsers = mergeVoiceUsersWithRemotePeers(connected.activeVoiceUsers, state.remotePeers)
                        if (mergedUsers != connected.activeVoiceUsers) {
                            _connectedVoiceChannel.value = connected.copy(activeVoiceUsers = mergedUsers)
                        }
                    }
                }
            }
            scope.launch {
                rtc.remoteVideoTracks.collect { tracks ->
                    _remoteVideoTracks.value = tracks
                }
            }
        }

        // Automatically detect already-signed-in Firebase user and restore cloud state
        val existingAuth = currentAuth()?.currentUser
        if (existingAuth != null) {
            syncFirebaseUser()
        }
    }

    private fun mergeVoiceUsersWithRemotePeers(
        localUsers: List<User>,
        remotePeers: List<RemoteVoicePeerState>
    ): List<User> {
        val byId = localUsers.associateBy { it.id }.toMutableMap()
        remotePeers.forEach { peer ->
            val existing = byId[peer.uid]
            byId[peer.uid] = existing?.copy(
                name = peer.displayName,
                isMuted = peer.isMuted,
                isDeafened = peer.isDeafened,
                isSpeaking = peer.isSpeaking
            ) ?: User(
                id = peer.uid,
                name = peer.displayName,
                avatarColor = 0xFF5865F2,
                status = UserStatus.ONLINE,
                isMuted = peer.isMuted,
                isDeafened = peer.isDeafened,
                isSpeaking = peer.isSpeaking
            )
        }
        return byId.values.toList()
    }

    fun getWebRtcEglContext(): EglBase.Context? = webRtcVoiceManager?.getEglContext()

    fun setVoiceCameraEnabled(enabled: Boolean, useFrontCamera: Boolean = true) {
        webRtcVoiceManager?.setCameraEnabled(enabled, useFrontCamera)
    }

    fun setVoiceScreenSharing(enabled: Boolean) {
        webRtcVoiceManager?.setScreenSharing(enabled)
    }

    fun setJukeboxVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _jukeboxVolume.value = clamped
        jukeboxAudioEngine?.setVolume(clamped)
    }

    fun updateFcmToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        cachedFcmToken = clean
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                db.collection("users").document(uid)
                    .set(mapOf("userId" to uid, "name" to _currentUser.value.name, "fcmToken" to clean), SetOptions.merge())
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to save FCM token", e)
            }
        }
    }

    fun releaseResources() {
        activeListener?.remove()
        activeChannelMetaListener?.remove()
        activeWhiteboardListener?.remove()
        activeJukeboxListener?.remove()
        activeNotificationsListener?.remove()
        activePublicProfilesListener?.remove()
        jukeboxAudioEngine?.stopAndRelease()
        webRtcVoiceManager?.leaveRoom()
    }

    fun syncFirebaseUser() {
        val authUser = currentAuth()?.currentUser
        if (authUser == null) {
            _isFirebaseSignedIn.value = false
            _firebaseUserEmail.value = null
            activeListener?.remove()
            activeChannelMetaListener?.remove()
            activeWhiteboardListener?.remove()
            activeJukeboxListener?.remove()
            activeNotificationsListener?.remove()
            activePublicProfilesListener?.remove()
            return
        }
        _isFirebaseSignedIn.value = true
        _firebaseUserEmail.value = authUser.email
        _currentUser.value = _currentUser.value.copy(
            id = authUser.uid,
            name = authUser.displayName?.takeIf { it.isNotBlank() } ?: _currentUser.value.name,
            avatarUrl = authUser.photoUrl?.toString() ?: _currentUser.value.avatarUrl,
            email = authUser.email ?: _currentUser.value.email,
            role = UserRole.ADMIN
        )
        loadFullStateFromFirestore()
        listenToUserNotifications(authUser.uid)
        listenToPublicProfiles()
        refreshPublicServersFromCloud()
        selectChannel(
            _servers.value.find { it.id == _selectedServerId.value }
                ?.channels?.find { it.id == _selectedChannelId.value }
                ?: return
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
        if (channel.type == ChannelType.VOICE || channel.type == ChannelType.STAGE) {
            joinVoiceChannel(channel)
        } else {
            _selectedChannelId.value = channel.id
            listenToChannelMessages(channel.id)
            listenToWhiteboard(channel.id)
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

    private fun serverScopeInfo(serverId: String): Pair<Boolean, List<String>> {
        val uid = currentAuth()?.currentUser?.uid
        val srv = _servers.value.find { it.id == serverId }
        val isPublic = srv?.isPublic == true
        val memberIds = buildList {
            uid?.let { add(it) }
            srv?.members?.forEach { m -> if (m.id != "user_me") add(m.id) }
        }.distinct()
        return isPublic to memberIds
    }

    private fun ensureServerAndChannelInFirestore(
        db: FirebaseFirestore,
        serverId: String,
        channel: Channel,
        authUid: String
    ): Pair<Boolean, List<String>> {
        val srvRef = db.collection("servers").document(serverId)
        val localServer = _servers.value.find { it.id == serverId }
        var isPublic = localServer?.isPublic == true
        var memberIds = listOf(authUid)

        try {
            val srvSnap = try { Tasks.await(srvRef.get()) } catch (_: Exception) { null }
            if (srvSnap == null || !srvSnap.exists()) {
                val initName = localServer?.name ?: "Serveur Raven"
                val payload = hashMapOf<String, Any>(
                    "id" to serverId,
                    "name" to initName,
                    "iconText" to (localServer?.iconText ?: initName.take(2).uppercase()),
                    "iconBgColor" to (localServer?.iconBgColor ?: 0xFF5865F2L),
                    "ownerId" to authUid,
                    "inviteCode" to (localServer?.inviteCode ?: "RAVEN-2026"),
                    "boostCount" to (localServer?.boostCount ?: 0),
                    "boostLevel" to (localServer?.boostLevel ?: 0),
                    "isPublic" to isPublic,
                    "categoryTag" to (localServer?.categoryTag ?: "Communauté"),
                    "description" to (localServer?.description ?: "Serveur communautaire Raven"),
                    "memberIds" to memberIds
                )
                Tasks.await(srvRef.set(payload))
            } else {
                isPublic = srvSnap.getBoolean("isPublic") ?: isPublic
                @Suppress("UNCHECKED_CAST")
                val existingMembers = (srvSnap.get("memberIds") as? List<String>).orEmpty()
                memberIds = (existingMembers + authUid).distinct()
            }

            val chRef = srvRef.collection("channels").document(channel.id)
            Tasks.await(
                chRef.set(
                    mapOf(
                        "id" to channel.id,
                        "serverId" to serverId,
                        "name" to channel.name,
                        "type" to channel.type.name,
                        "category" to channel.category,
                        "isPublic" to isPublic,
                        "slowmodeSeconds" to channel.slowmodeSeconds,
                        "isReadOnly" to channel.isReadOnly,
                        "memberIds" to memberIds
                    ),
                    SetOptions.merge()
                )
            )
        } catch (e: Exception) {
            Log.w("ChatRepo", "ensureServerAndChannelInFirestore warning", e)
        }
        return isPublic to memberIds
    }

    private fun listenToChannelMessages(channelId: String) {
        val db = firestore ?: return
        val currentUserId = currentAuth()?.currentUser?.uid ?: return
        val serverId = findChannelById(channelId)?.serverId ?: _selectedServerId.value
        if (serverId.isBlank() || serverId == "dm_tab") return

        activeListener?.remove()
        activeChannelMetaListener?.remove()

        val channelRef = db.collection("servers").document(serverId)
            .collection("channels").document(channelId)
        val (isPublic, _) = serverScopeInfo(serverId)

        val messagesQuery = if (isPublic) {
            channelRef.collection("messages").whereEqualTo("isPublic", true)
        } else {
            channelRef.collection("messages").whereArrayContains("memberIds", currentUserId)
        }

        activeListener = messagesQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepo", "Channel messages snapshot error", error)
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                val sortedDocs = snapshot.documents.sortedBy { it.getLong("createdAtMs") ?: 0L }
                val firestoreMsgs = sortedDocs.mapNotNull { doc ->
                    mapFirestoreDocToMessage(doc, channelId, currentUserId)
                }
                val existing = _messages.value[channelId] ?: emptyList()
                val byId = existing.associateBy { it.id }.toMutableMap()
                firestoreMsgs.forEach { byId[it.id] = it }
                _messages.value = _messages.value + (channelId to byId.values.toList())
            }
        }

        activeChannelMetaListener = channelRef.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
            val slowSec = snap.getLong("slowmodeSeconds")?.toInt()
            val readOnly = snap.getBoolean("isReadOnly")
            if (slowSec != null || readOnly != null) {
                _servers.value = _servers.value.map { s ->
                    if (s.id == serverId) {
                        s.copy(channels = s.channels.map { c ->
                            if (c.id == channelId) c.copy(
                                slowmodeSeconds = slowSec ?: c.slowmodeSeconds,
                                isReadOnly = readOnly ?: c.isReadOnly
                            ) else c
                        })
                    } else s
                }
            }
            val typingUid = snap.getString("lastTypingUid") ?: return@addSnapshotListener
            val typingName = snap.getString("lastTypingName") ?: return@addSnapshotListener
            val updatedAt = snap.getLong("lastTypingMs") ?: 0L
            if (typingUid != currentUserId && System.currentTimeMillis() - updatedAt < 5000L) {
                val curr = _typingUsersByTarget.value[channelId].orEmpty()
                if (typingName !in curr) {
                    _typingUsersByTarget.value = _typingUsersByTarget.value + (channelId to (curr + typingName))
                }
            }
        }
    }

    private fun listenToDmMessages(dmId: String) {
        val db = firestore ?: return
        val currentUser = currentAuth()?.currentUser ?: return
        activeListener?.remove()

        activeListener = db.collection("direct_conversations").document(dmId)
            .collection("messages")
            .whereArrayContains("participantIds", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepo", "DM messages snapshot error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val sortedDocs = snapshot.documents.sortedBy { it.getLong("createdAtMs") ?: 0L }
                    val firestoreMsgs = sortedDocs.mapNotNull { doc ->
                        mapFirestoreDocToMessage(doc, dmId, currentUser.uid)
                    }
                    val existing = _messages.value[dmId] ?: emptyList()
                    val byId = existing.associateBy { it.id }.toMutableMap()
                    firestoreMsgs.forEach { byId[it.id] = it }
                    _messages.value = _messages.value + (dmId to byId.values.toList())
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapFirestoreDocToMessage(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        targetId: String,
        currentUserId: String
    ): Message? {
        val text = doc.getString("text") ?: ""
        val senderName = doc.getString("senderName") ?: "Membre"
        val senderId = doc.getString("senderId") ?: ""
        val imageUrl = doc.getString("imageUrl")
        val audioUrl = doc.getString("audioUrl")
        val replyToText = doc.getString("replyToText")
        val stickerId = doc.getString("stickerId")
        val attachmentName = doc.getString("attachmentName")
        val attachmentType = doc.getString("attachmentType")
        val attachmentSizeLabel = doc.getString("attachmentSizeLabel")
        val isPinned = doc.getBoolean("isPinned") ?: false
        val isEdited = doc.getBoolean("isEdited") ?: false
        val threadCount = doc.getLong("threadCount")?.toInt() ?: 0
        val reactions = parseReactionsFromDoc(doc, currentUserId)
        val createdAtMs = doc.getLong("createdAtMs")
        val timeLabel = if (createdAtMs != null && createdAtMs > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAtMs))
        } else {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }

        val rawPoll = doc.get("poll") as? Map<String, Any?>
        val parsedPoll = rawPoll?.let { pollMap ->
            val q = pollMap["question"] as? String ?: ""
            val allowsMultiple = pollMap["allowsMultiple"] as? Boolean ?: false
            val opts = (pollMap["options"] as? List<Map<String, Any?>>).orEmpty().mapIndexed { idx, opt ->
                val votes = (opt["votes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                PollOption(
                    id = (opt["id"] as? String) ?: "opt_$idx",
                    text = (opt["text"] as? String) ?: "",
                    votes = votes
                )
            }
            if (q.isNotBlank() && opts.isNotEmpty()) Poll(q, opts, allowsMultiple) else null
        }

        return Message(
            id = doc.id,
            channelId = targetId,
            sender = User(id = senderId, name = senderName, avatarColor = 0xFF404249),
            text = text,
            timestamp = timeLabel,
            replyToText = replyToText,
            reactions = reactions,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            stickerId = stickerId,
            attachmentName = attachmentName,
            attachmentType = attachmentType,
            attachmentSizeLabel = attachmentSizeLabel,
            isPinned = isPinned,
            isEdited = isEdited,
            threadCount = threadCount,
            poll = parsedPoll
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseReactionsFromDoc(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        currentUserId: String
    ): List<Reaction> {
        val rawMap = doc.get("reactions") as? Map<String, Any?> ?: return emptyList()
        return rawMap.mapNotNull { (emoji, value) ->
            when (value) {
                is Number -> {
                    val count = value.toInt()
                    if (count > 0) Reaction(emoji = emoji, count = count, userReacted = false) else null
                }
                is Map<*, *> -> {
                    val count = (value["count"] as? Number)?.toInt() ?: 0
                    val users = (value["users"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (count > 0) {
                        Reaction(
                            emoji = emoji,
                            count = count,
                            userReacted = currentUserId in users
                        )
                    } else null
                }
                else -> null
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

    fun clearSendBlockedReason() {
        _lastSendBlockedReason.value = null
    }

    private fun findChannelById(channelId: String): Channel? {
        return _servers.value.asSequence().flatMap { it.channels.asSequence() }.find { it.id == channelId }
    }

    private fun canPostInTarget(targetId: String): Boolean {
        val ch = findChannelById(targetId) ?: return true
        if (ch.isReadOnly) {
            _lastSendBlockedReason.value = "Ce salon est verrouillé en lecture seule."
            return false
        }
        if (ch.slowmodeSeconds > 0) {
            val now = System.currentTimeMillis()
            val until = _slowmodeCooldownUntilByChannel.value[targetId] ?: 0L
            if (now < until) {
                val remSec = ((until - now + 999) / 1000).toInt().coerceAtLeast(1)
                _lastSendBlockedReason.value = "Mode lent actif : patientez encore ${remSec}s."
                return false
            }
            _slowmodeCooldownUntilByChannel.value =
                _slowmodeCooldownUntilByChannel.value + (targetId to (now + ch.slowmodeSeconds * 1000L))
        }
        _lastSendBlockedReason.value = null
        return true
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
        if (!canPostInTarget(targetId)) return

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

        // Optimistic local update & clear draft
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + newMessage))
        _replyingToMessage.value = null
        if (_draftsByTarget.value.containsKey(targetId)) {
            _draftsByTarget.value = _draftsByTarget.value - targetId
        }

        // Update DM last message
        if (_selectedServerId.value == "dm_tab" || targetId.startsWith("dm_") || targetId.startsWith("grp_")) {
            val preview = when {
                text.isNotBlank() -> text
                imageUrl != null -> "Image partagée"
                audioUrl != null -> "Note vocale ($audioDurationSec s)"
                else -> "Pièce jointe"
            }
            _directConversations.value = _directConversations.value.map {
                if (it.id == targetId) it.copy(lastMessage = preview, lastMessageTime = currentTime) else it
            }
        }

        // Grant XP & progress quest for sending a message
        grantXp(15, "Message envoyé")
        advanceQuest("q_msg")

        // Simulate realistic brief typing indicator from another member in channel / DM
        simulateBriefReplyTyping(targetId)

        // Sync with Firestore if available and authenticated
        syncNewMessageToFirestore(newMessage, targetId)
    }

    private fun syncNewMessageToFirestore(newMessage: Message, targetId: String) {
        val db = firestore ?: return
        val user = currentAuth()?.currentUser ?: return
        val targetChannel = findChannelById(targetId)
        val payload = hashMapOf<String, Any>(
            "senderId" to user.uid,
            "senderName" to (user.displayName?.takeIf { it.isNotBlank() } ?: _currentUser.value.name),
            "text" to newMessage.text,
            "isPinned" to newMessage.isPinned,
            "isEdited" to newMessage.isEdited,
            "threadCount" to newMessage.threadCount,
            "createdAtMs" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        newMessage.imageUrl?.let { payload["imageUrl"] = it }
        newMessage.audioUrl?.let { payload["audioUrl"] = it }
        newMessage.replyToText?.let { payload["replyToText"] = it }
        newMessage.stickerId?.let { payload["stickerId"] = it }
        newMessage.attachmentName?.let { payload["attachmentName"] = it }
        newMessage.poll?.let { poll ->
            payload["poll"] = mapOf(
                "question" to poll.question,
                "allowsMultiple" to poll.allowsMultiple,
                "options" to poll.options.map { opt ->
                    mapOf("id" to opt.id, "text" to opt.text, "votes" to opt.votes)
                }
            )
        }

        scope.launch(Dispatchers.IO) {
            try {
                if (targetChannel == null) {
                    val participantIds = listOf(user.uid)
                    payload["participantIds"] = participantIds
                    val dmDoc = db.collection("direct_conversations").document(targetId)
                    Tasks.await(dmDoc.set(
                        mapOf(
                            "id" to targetId,
                            "participantIds" to participantIds,
                            "lastMessage" to newMessage.text.ifBlank { newMessage.attachmentName ?: "Média" },
                            "lastMessageTime" to newMessage.timestamp,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ))
                    Tasks.await(dmDoc.collection("messages").document(newMessage.id).set(payload))
                } else {
                    val (isPublic, memberIds) = ensureServerAndChannelInFirestore(db, targetChannel.serverId, targetChannel, user.uid)
                    payload["isPublic"] = isPublic
                    payload["memberIds"] = memberIds
                    Tasks.await(db.collection("servers").document(targetChannel.serverId)
                        .collection("channels").document(targetId)
                        .collection("messages").document(newMessage.id)
                        .set(payload))
                }
            } catch (e: Exception) {
                Log.w("ChatRepo", "Firestore message write failed", e)
            }
        }
    }

    private fun resolveMessageDocRef(db: FirebaseFirestore, targetId: String, messageId: String) =
        findChannelById(targetId)?.let { ch ->
            db.collection("servers").document(ch.serverId)
                .collection("channels").document(ch.id)
                .collection("messages").document(messageId)
        } ?: db.collection("direct_conversations").document(targetId)
            .collection("messages").document(messageId)

    fun toggleReaction(channelId: String, messageId: String, emoji: String) {
        val currentList = _messages.value[channelId] ?: return
        var updatedMessageReactions: List<Reaction>? = null
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
                updatedMessageReactions = newReactions
                msg.copy(reactions = newReactions)
            } else {
                msg
            }
        }
        _messages.value = _messages.value + (channelId to updatedList)
        advanceQuest("q_sticker")

        val db = firestore ?: return
        val authUser = currentAuth()?.currentUser ?: return
        val reactionsSnapshot = updatedMessageReactions ?: return
        val firestoreReactions = reactionsSnapshot.associate { r ->
            r.emoji to mapOf(
                "count" to r.count,
                "users" to if (r.userReacted) listOf(authUser.uid) else emptyList()
            )
        }
        scope.launch(Dispatchers.IO) {
            try {
                resolveMessageDocRef(db, channelId, messageId)
                    .update(mapOf("reactions" to firestoreReactions, "updatedAt" to FieldValue.serverTimestamp()))
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync reaction to Firestore", e)
            }
        }
    }

    fun togglePinMessage(channelId: String, messageId: String) {
        val currentList = _messages.value[channelId] ?: return
        var nextPinned = false
        val updatedList = currentList.map { msg ->
            if (msg.id == messageId) {
                nextPinned = !msg.isPinned
                msg.copy(isPinned = nextPinned)
            } else msg
        }
        _messages.value = _messages.value + (channelId to updatedList)

        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        scope.launch(Dispatchers.IO) {
            try {
                resolveMessageDocRef(db, channelId, messageId)
                    .update(mapOf("isPinned" to nextPinned, "updatedAt" to FieldValue.serverTimestamp()))
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync pin status", e)
            }
        }
    }

    fun deleteMessage(channelId: String, messageId: String) {
        val currentList = _messages.value[channelId] ?: return
        val updatedList = currentList.filterNot { it.id == messageId }
        _messages.value = _messages.value + (channelId to updatedList)

        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        scope.launch(Dispatchers.IO) {
            try {
                resolveMessageDocRef(db, channelId, messageId).delete()
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to delete message in Firestore", e)
            }
        }
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
        advanceQuest("q_voice")

        val uid = currentAuth()?.currentUser?.uid ?: _currentUser.value.id
        val (isPublic, memberIds) = serverScopeInfo(channel.serverId)
        val db = firestore
        if (db != null && channel.serverId != "dm_tab" && currentAuth()?.currentUser != null) {
            scope.launch(Dispatchers.IO) {
                ensureServerAndChannelInFirestore(db, channel.serverId, channel, uid)
            }
        }
        webRtcVoiceManager?.joinRoom(
            serverId = channel.serverId,
            channelId = channel.id,
            currentUid = uid,
            displayName = _currentUser.value.name,
            isPublic = isPublic,
            memberIds = memberIds,
            firestore = if (currentAuth()?.currentUser != null) db else null,
            isMuted = _isUserMuted.value,
            isDeafened = _isUserDeafened.value
        )
        listenToWhiteboard(channel.id)
        listenToJukebox(channel.id)
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
        webRtcVoiceManager?.leaveRoom()
        jukeboxAudioEngine?.pause()
    }

    fun toggleMute() {
        val newMute = !_isUserMuted.value
        _isUserMuted.value = newMute
        webRtcVoiceManager?.setMuted(newMute)
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
        webRtcVoiceManager?.setDeafened(newDeafen)
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
        advanceQuest("q_profile")
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
        advanceQuest("q_profile")
    }

    fun updateOrbsBalance(balance: Int) {
        _currentUser.value = _currentUser.value.copy(orbsBalance = balance)
        saveProfileToFirestore()
    }

    fun equipAvatarDecoration(decorationKey: String?) {
        _currentUser.value = _currentUser.value.copy(avatarDecoration = decorationKey)
        saveProfileToFirestore()
    }

    fun equipProfileEffect(effectKey: String?) {
        _currentUser.value = _currentUser.value.copy(profileEffect = effectKey)
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
        if (!canPostInTarget(targetId)) return
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + newMessage))
        syncNewMessageToFirestore(newMessage, targetId)
    }

    fun votePoll(targetId: String, messageId: String, optionId: String) {
        val currentList = _messages.value[targetId] ?: return
        val userId = _currentUser.value.id
        var updatedPoll: Poll? = null
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
                updatedPoll = p.copy(options = updatedOptions)
                msg.copy(poll = updatedPoll)
            } else {
                msg
            }
        }
        _messages.value = _messages.value + (targetId to updatedList)
        advanceQuest("q_poll")

        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        val pollSnapshot = updatedPoll ?: return
        scope.launch(Dispatchers.IO) {
            try {
                resolveMessageDocRef(db, targetId, messageId).update(
                    mapOf(
                        "poll" to mapOf(
                            "question" to pollSnapshot.question,
                            "allowsMultiple" to pollSnapshot.allowsMultiple,
                            "options" to pollSnapshot.options.map { opt ->
                                mapOf("id" to opt.id, "text" to opt.text, "votes" to opt.votes)
                            }
                        ),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync poll vote", e)
            }
        }
    }

    private fun saveProfileToFirestore() {
        syncFullStateToFirestore()
    }

    fun syncFullStateToFirestore() {
        val db = firestore ?: return
        val authUser = currentAuth()?.currentUser ?: return
        val user = _currentUser.value
        val settings = _appSettings.value

        val bgSerialized = _chatBackgrounds.value.mapValues { (_, theme) ->
            mapOf(
                "id" to theme.id,
                "name" to theme.name,
                "primaryColor" to theme.primaryColor,
                "secondaryColor" to (theme.secondaryColor ?: theme.primaryColor),
                "opacity" to theme.opacity.toDouble()
            )
        }
        val foldersSerialized = _serverFolders.value.map { folder ->
            mapOf(
                "id" to folder.id,
                "name" to folder.name,
                "colorHex" to folder.colorHex,
                "serverIds" to folder.serverIds,
                "isExpanded" to folder.isExpanded
            )
        }

        val data = hashMapOf<String, Any>(
            "userId" to authUser.uid,
            "name" to user.name,
            "bio" to user.bio,
            "pronouns" to user.pronouns,
            "status" to user.status.name,
            "customStatus" to user.customStatus,
            "customStatusEmoji" to user.customStatusEmoji,
            "avatarColor" to user.avatarColor,
            "bannerColor" to user.bannerColor,
            "activity" to user.activity,
            "orbsBalance" to user.orbsBalance,
            "xp" to user.xp,
            "level" to user.level,
            "badges" to user.badges,
            "unlockedStickers" to user.unlockedStickers,
            "themeMode" to settings.themeMode.name,
            "accentColorHex" to settings.accentColorHex,
            "chatDisplayStyle" to settings.chatDisplayStyle.name,
            "glassmorphismEnabled" to settings.glassmorphismEnabled,
            "audioEqPreset" to settings.audioEqPreset.name,
            "clanTag" to (user.clanTag ?: "RVN"),
            "clanBadgeIcon" to user.clanBadgeIcon,
            "soundEffectsEnabled" to settings.soundEffectsEnabled,
            "voiceNoiseSuppression" to settings.voiceNoiseSuppression,
            "localNotificationsEnabled" to settings.localNotificationsEnabled,
            "dailyStreak" to _dailyStreak.value,
            "streakClaimedToday" to _streakClaimedToday.value,
            "privateUserNotes" to _privateUserNotes.value,
            "chatBackgrounds" to bgSerialized,
            "serverFolders" to foldersSerialized
        )
        user.avatarUrl?.let { data["avatarUrl"] = it }
        user.bannerUrl?.let { data["bannerUrl"] = it }
        user.email?.let { data["email"] = it }
        user.avatarDecoration?.let { data["avatarDecoration"] = it }
        user.profileEffect?.let { data["profileEffect"] = it }
        cachedFcmToken?.let { data["fcmToken"] = it }

        val publicProfile = hashMapOf<String, Any>(
            "userId" to authUser.uid,
            "name" to user.name,
            "avatarColor" to user.avatarColor,
            "xp" to user.xp,
            "level" to user.level,
            "clanTag" to (user.clanTag ?: "RVN"),
            "clanBadgeIcon" to user.clanBadgeIcon,
            "isPublic" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        user.avatarUrl?.let { publicProfile["avatarUrl"] = it }

        _isCloudSyncing.value = true
        scope.launch(Dispatchers.IO) {
            try {
                Tasks.await(db.collection("users").document(authUser.uid).set(data))
                Tasks.await(db.collection("public_profiles").document(authUser.uid).set(publicProfile))
                // Also sync user-owned custom servers (skip default shared mock server_main unless explicitly owned)
                _servers.value.filter { !it.isDmTab && (it.ownerId == authUser.uid || (it.id != "server_main" && (it.ownerId == user.id || it.ownerId == "user_me" || it.boostCount > 0))) }.forEach { srv ->
                    val srvPayload = hashMapOf<String, Any>(
                        "id" to srv.id,
                        "name" to srv.name,
                        "iconText" to srv.iconText,
                        "iconBgColor" to srv.iconBgColor,
                        "ownerId" to authUser.uid,
                        "inviteCode" to srv.inviteCode,
                        "boostCount" to srv.boostCount,
                        "boostLevel" to srv.boostLevel,
                        "isPublic" to srv.isPublic,
                        "categoryTag" to srv.categoryTag,
                        "description" to srv.description,
                        "customRoles" to srv.customRoles.map { r ->
                            buildMap<String, Any> {
                                put("id", r.id)
                                put("name", r.name)
                                put("colorHex", r.colorHex)
                                r.secondaryColorHex?.let { put("secondaryColorHex", it) }
                                put("isAdmin", r.isAdmin)
                                put("canKickBan", r.canKickBan)
                                put("canManageChannels", r.canManageChannels)
                            }
                        },
                        "events" to srv.events.map { e ->
                            mapOf(
                                "id" to e.id,
                                "title" to e.title,
                                "description" to e.description,
                                "dateTimeLabel" to e.dateTimeLabel,
                                "locationLabel" to e.locationLabel,
                                "creatorName" to e.creatorName,
                                "interestedUserIds" to e.interestedUserIds
                            )
                        },
                        "shopItems" to srv.shopItems.map { item ->
                            mapOf(
                                "id" to item.id,
                                "title" to item.title,
                                "description" to item.description,
                                "priceOrbs" to item.priceOrbs,
                                "primaryColorHex" to item.primaryColorHex,
                                "secondaryColorHex" to item.secondaryColorHex,
                                "roleName" to item.roleName,
                                "purchasedByUserIds" to item.purchasedByUserIds
                            )
                        },
                        "memberIds" to (srv.members.map { if (it.id == "user_me") authUser.uid else it.id } + authUser.uid).distinct()
                    )
                    srv.customBannerColorHex?.let { srvPayload["customBannerColorHex"] = it }
                    Tasks.await(db.collection("servers").document(srv.id).set(srvPayload))
                }
                _lastCloudSyncTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync full state to Firestore", e)
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadFullStateFromFirestore() {
        val db = firestore ?: return
        val authUser = currentAuth()?.currentUser ?: return
        _isCloudSyncing.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val doc = Tasks.await(
                    db.collection("users").document(authUser.uid).get()
                )
                if (doc.exists()) {
                    val curr = _currentUser.value
                    val loadedStickers = (doc.get("unlockedStickers") as? List<*>)?.mapNotNull { it as? String }
                        ?: curr.unlockedStickers
                    val loadedBadges = (doc.get("badges") as? List<*>)?.mapNotNull { it as? String }
                        ?: curr.badges
                    _currentUser.value = curr.copy(
                        name = doc.getString("name") ?: curr.name,
                        bio = doc.getString("bio") ?: curr.bio,
                        pronouns = doc.getString("pronouns") ?: curr.pronouns,
                        customStatus = doc.getString("customStatus") ?: curr.customStatus,
                        customStatusEmoji = doc.getString("customStatusEmoji") ?: curr.customStatusEmoji,
                        activity = doc.getString("activity") ?: curr.activity,
                        orbsBalance = (doc.getLong("orbsBalance")?.toInt()) ?: curr.orbsBalance,
                        xp = (doc.getLong("xp")?.toInt()) ?: curr.xp,
                        level = (doc.getLong("level")?.toInt()) ?: curr.level,
                        avatarDecoration = doc.getString("avatarDecoration") ?: curr.avatarDecoration,
                        profileEffect = doc.getString("profileEffect") ?: curr.profileEffect,
                        badges = loadedBadges,
                        unlockedStickers = loadedStickers
                    )
                    doc.getLong("dailyStreak")?.toInt()?.let { _dailyStreak.value = it }
                    doc.getBoolean("streakClaimedToday")?.let { _streakClaimedToday.value = it }
                    val rawNotes = doc.get("privateUserNotes") as? Map<String, Any?>
                    if (rawNotes != null) {
                        _privateUserNotes.value = rawNotes.mapNotNull { (k, v) ->
                            val str = v as? String ?: return@mapNotNull null
                            k to str
                        }.toMap()
                    }
                    val loadedClanTag = doc.getString("clanTag")
                    val loadedClanIcon = doc.getString("clanBadgeIcon")
                    _currentUser.value = _currentUser.value.copy(
                        clanTag = loadedClanTag ?: _currentUser.value.clanTag,
                        clanBadgeIcon = loadedClanIcon ?: _currentUser.value.clanBadgeIcon
                    )
                    val themeStr = doc.getString("themeMode")
                    if (themeStr != null) {
                        val mode = AppThemeMode.entries.find { it.name == themeStr } ?: AppThemeMode.DARK
                        val accentHex = doc.getLong("accentColorHex") ?: _appSettings.value.accentColorHex
                        val styleStr = doc.getString("chatDisplayStyle")
                        val displayStyle = ChatDisplayStyle.entries.find { it.name == styleStr } ?: _appSettings.value.chatDisplayStyle
                        val glassOn = doc.getBoolean("glassmorphismEnabled") ?: _appSettings.value.glassmorphismEnabled
                        val eqStr = doc.getString("audioEqPreset")
                        val eqPreset = AudioEqPreset.entries.find { it.name == eqStr } ?: _appSettings.value.audioEqPreset
                        val soundOn = doc.getBoolean("soundEffectsEnabled") ?: _appSettings.value.soundEffectsEnabled
                        val noiseOn = doc.getBoolean("voiceNoiseSuppression") ?: _appSettings.value.voiceNoiseSuppression
                        val notifOn = doc.getBoolean("localNotificationsEnabled") ?: _appSettings.value.localNotificationsEnabled
                        _appSettings.value = _appSettings.value.copy(
                            themeMode = mode,
                            accentColorHex = accentHex,
                            chatDisplayStyle = displayStyle,
                            glassmorphismEnabled = glassOn,
                            audioEqPreset = eqPreset,
                            soundEffectsEnabled = soundOn,
                            voiceNoiseSuppression = noiseOn,
                            localNotificationsEnabled = notifOn
                        )
                        jukeboxAudioEngine?.applyEqPreset(eqPreset)
                        com.example.util.SoundFxHelper.isSoundEnabled = soundOn
                    }
                    val rawFolders = doc.get("serverFolders") as? List<Map<String, Any?>>
                    if (rawFolders != null) {
                        _serverFolders.value = rawFolders.mapNotNull { map ->
                            val id = map["id"] as? String ?: return@mapNotNull null
                            val name = map["name"] as? String ?: "Dossier"
                            val colorHex = (map["colorHex"] as? Number)?.toLong() ?: 0xFF5865F2
                            val sIds = (map["serverIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            val exp = (map["isExpanded"] as? Boolean) ?: true
                            ServerFolder(id, name, colorHex, sIds, exp)
                        }
                    }
                }
                // Restore user member/owned servers (aligned query: memberIds array-contains)
                val srvDocs = Tasks.await(
                    db.collection("servers").whereArrayContains("memberIds", authUser.uid).get()
                )
                if (!srvDocs.isEmpty) {
                    val currentServers = _servers.value.toMutableList()
                    for (srvDoc in srvDocs.documents) {
                        val sId = srvDoc.getString("id") ?: srvDoc.id
                        val sName = srvDoc.getString("name") ?: continue
                        val sIcon = srvDoc.getString("iconText") ?: sName.take(2).uppercase()
                        val sInvite = srvDoc.getString("inviteCode") ?: "RAVEN-2026"
                        val sOwner = srvDoc.getString("ownerId") ?: authUser.uid
                        val sIsPublic = srvDoc.getBoolean("isPublic") ?: false
                        val sCat = srvDoc.getString("categoryTag") ?: "Communauté"
                        val sDesc = srvDoc.getString("description") ?: "Communauté Raven"
                        val bCount = srvDoc.getLong("boostCount")?.toInt() ?: 0
                        val bLevel = srvDoc.getLong("boostLevel")?.toInt() ?: 0
                        val bBanner = srvDoc.getLong("customBannerColorHex")
                        val existingIdx = currentServers.indexOfFirst { it.id == sId }
                        if (existingIdx >= 0) {
                            currentServers[existingIdx] = currentServers[existingIdx].copy(
                                name = sName,
                                isPublic = sIsPublic,
                                boostCount = maxOf(currentServers[existingIdx].boostCount, bCount),
                                boostLevel = maxOf(currentServers[existingIdx].boostLevel, bLevel),
                                customBannerColorHex = bBanner ?: currentServers[existingIdx].customBannerColorHex
                            )
                        } else {
                            currentServers.add(
                                Server(
                                    id = sId,
                                    name = sName,
                                    iconText = sIcon,
                                    ownerId = sOwner,
                                    inviteCode = sInvite,
                                    isPublic = sIsPublic,
                                    categoryTag = sCat,
                                    description = sDesc,
                                    boostCount = bCount,
                                    boostLevel = bLevel,
                                    customBannerColorHex = bBanner,
                                    channels = listOf(
                                        Channel("${sId}_general", sId, "général", ChannelType.TEXT, "SALONS TEXTUELS"),
                                        Channel("${sId}_voice", sId, "Salon vocal", ChannelType.VOICE, "SALONS VOCAUX")
                                    ),
                                    members = listOf(_currentUser.value)
                                )
                            )
                        }
                    }
                    _servers.value = currentServers
                }
                _lastCloudSyncTime.value = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to load state from Firestore", e)
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }

    fun editMessage(channelId: String, messageId: String, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isBlank()) return
        val currentList = _messages.value[channelId] ?: return
        val updated = currentList.map { msg ->
            if (msg.id == messageId) msg.copy(text = trimmed, isEdited = true) else msg
        }
        _messages.value = _messages.value + (channelId to updated)

        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        scope.launch(Dispatchers.IO) {
            try {
                resolveMessageDocRef(db, channelId, messageId).update(
                    mapOf("text" to trimmed, "isEdited" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync edited message", e)
            }
        }
    }

    fun sendThreadMessage(channelId: String, parentMessageId: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val replyMsg = Message(
            id = "th_${UUID.randomUUID().toString().take(8)}",
            channelId = channelId,
            sender = _currentUser.value,
            text = trimmed,
            timestamp = currentTime
        )
        val existing = _threads.value[parentMessageId] ?: emptyList()
        val updatedReplies = existing + replyMsg
        _threads.value = _threads.value + (parentMessageId to updatedReplies)

        val channelMessages = _messages.value[channelId] ?: emptyList()
        _messages.value = _messages.value + (channelId to channelMessages.map {
            if (it.id == parentMessageId) it.copy(threadCount = updatedReplies.size) else it
        })
        grantXp(10, "Réponse dans un fil")
    }

    fun grantXp(amount: Int, reason: String) {
        if (amount <= 0) return
        val curr = _currentUser.value
        var newXp = curr.xp + amount
        var newLevel = curr.level
        var bonusOrbs = 0
        while (newXp >= newLevel * 100) {
            newXp -= newLevel * 100
            newLevel++
            bonusOrbs += 50
        }
        val updatedUser = curr.copy(
            xp = newXp,
            level = newLevel,
            orbsBalance = curr.orbsBalance + bonusOrbs
        )
        _currentUser.value = updatedUser
        _servers.value = _servers.value.map { server ->
            server.copy(members = server.members.map {
                if (it.id == curr.id || it.id == "user_me") updatedUser else it
            })
        }
        if (bonusOrbs > 0) {
            com.example.util.SoundFxHelper.playLevelUp()
            addNotification(
                title = "Niveau $newLevel atteint !",
                body = "Félicitations ! Vous passez au niveau $newLevel ($reason) et gagnez +$bonusOrbs Orbs bonus.",
                sourceLabel = "Progression Raven",
                type = NotificationType.LEVEL_UP
            )
        }
    }

    private fun listenToUserNotifications(uid: String) {
        val db = firestore ?: return
        activeNotificationsListener?.remove()
        activeNotificationsListener = db.collection("users").document(uid)
            .collection("notifications")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener
                val cloudNotifs = snap.documents.sortedByDescending { it.getLong("createdAtMs") ?: 0L }
                    .mapNotNull { doc ->
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val body = doc.getString("body") ?: ""
                        val sourceLabel = doc.getString("sourceLabel") ?: "Raven"
                        val typeStr = doc.getString("type")
                        val nType = NotificationType.entries.find { it.name == typeStr } ?: NotificationType.SYSTEM
                        val isRead = doc.getBoolean("isRead") ?: false
                        val ms = doc.getLong("createdAtMs") ?: System.currentTimeMillis()
                        InboxNotification(
                            id = doc.id,
                            title = title,
                            body = body,
                            sourceLabel = sourceLabel,
                            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms)),
                            type = nType,
                            isRead = isRead
                        )
                    }
                val merged = (cloudNotifs + _notifications.value).distinctBy { it.id }
                _notifications.value = merged
            }
    }

    private fun listenToPublicProfiles() {
        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        activePublicProfilesListener?.remove()
        activePublicProfilesListener = db.collection("public_profiles")
            .whereEqualTo("isPublic", true)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || snap.isEmpty) return@addSnapshotListener
                val profileMap = snap.documents.associate { doc ->
                    val uid = doc.getString("userId") ?: doc.id
                    uid to Triple(
                        doc.getLong("xp")?.toInt() ?: 0,
                        doc.getLong("level")?.toInt() ?: 1,
                        doc.getString("clanTag")
                    )
                }
                _servers.value = _servers.value.map { srv ->
                    srv.copy(members = srv.members.map { m ->
                        val p = profileMap[m.id]
                        if (p != null) m.copy(xp = p.first, level = p.second, clanTag = p.third ?: m.clanTag) else m
                    })
                }
            }
    }

    fun addNotification(
        title: String,
        body: String,
        sourceLabel: String,
        type: NotificationType = NotificationType.SYSTEM
    ) {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entry = InboxNotification(
            id = "notif_${UUID.randomUUID().toString().take(8)}",
            title = title,
            body = body,
            sourceLabel = sourceLabel,
            timestamp = currentTime,
            type = type,
            isRead = false
        )
        _notifications.value = listOf(entry) + _notifications.value
        if (_appSettings.value.localNotificationsEnabled) {
            _pendingLocalNotification.value = entry
        }
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                db.collection("users").document(uid)
                    .collection("notifications").document(entry.id)
                    .set(
                        mapOf(
                            "userId" to uid,
                            "title" to title,
                            "body" to body,
                            "sourceLabel" to sourceLabel,
                            "type" to type.name,
                            "isRead" to false,
                            "createdAtMs" to System.currentTimeMillis()
                        )
                    )
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to save notification to Firestore", e)
            }
        }
    }

    fun consumePendingLocalNotification() {
        _pendingLocalNotification.value = null
    }

    fun markNotificationRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            try {
                db.collection("users").document(uid).collection("notifications").document(notificationId)
                    .update("isRead", true)
            } catch (_: Exception) {
            }
        }
    }

    fun markAllNotificationsRead() {
        val ids = _notifications.value.map { it.id }
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        scope.launch(Dispatchers.IO) {
            ids.forEach { nid ->
                try {
                    db.collection("users").document(uid).collection("notifications").document(nid)
                        .update("isRead", true)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun updateAppSettings(transform: (AppSettingsState) -> AppSettingsState) {
        val next = transform(_appSettings.value)
        _appSettings.value = next
        com.example.util.SoundFxHelper.isSoundEnabled = next.soundEffectsEnabled
    }

    fun renameServer(serverId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a renommé le serveur en",
                    targetName = clean,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(name = clean, auditLogs = listOf(log) + server.auditLogs)
            } else server
        }
    }

    fun regenerateInviteCode(serverId: String) {
        val randomCode = "RAVEN-${(1000..9999).random()}"
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a généré un nouveau code d'invitation",
                    targetName = randomCode,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(inviteCode = randomCode, auditLogs = listOf(log) + server.auditLogs)
            } else server
        }
    }

    fun joinServerByInviteCode(code: String): Boolean {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return false
        val existing = _servers.value.find { !it.isDmTab && it.inviteCode.equals(normalized, ignoreCase = true) }
        if (existing != null) {
            selectServer(existing.id)
            return true
        }
        val discoverMatch = _discoverServers.value.find { it.inviteCode.equals(normalized, ignoreCase = true) }
        if (discoverMatch != null) {
            return joinPublicServer(discoverMatch)
        }
        val cleanName = if (normalized.startsWith("RAVEN-")) {
            "Communauté ${normalized.removePrefix("RAVEN-")}"
        } else {
            "Serveur $normalized"
        }
        val newServerId = "server_${UUID.randomUUID().toString().take(6)}"
        val generalId = "${newServerId}_general"
        val joined = Server(
            id = newServerId,
            name = cleanName,
            iconText = cleanName.take(2).uppercase(),
            iconBgColor = 0xFF313338,
            channels = listOf(
                Channel(generalId, newServerId, "général", ChannelType.TEXT, "SALONS TEXTUELS"),
                Channel("${newServerId}_voice", newServerId, "Salon vocal", ChannelType.VOICE, "SALONS VOCAUX")
            ),
            members = listOf(_currentUser.value, MockData.friendThomas, MockData.friendEmma),
            inviteCode = normalized,
            ownerId = MockData.friendThomas.id
        )
        _servers.value = _servers.value + joined
        _messages.value = _messages.value + (
            generalId to listOf(
                Message(
                    id = "welcome_$newServerId",
                    channelId = generalId,
                    sender = MockData.friendThomas,
                    text = "Bienvenue sur $cleanName via l'invitation $normalized !",
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
            )
        )
        selectServer(newServerId)
        return true
    }

    fun createCustomRole(
        serverId: String,
        roleName: String,
        colorHex: Long,
        isAdmin: Boolean,
        canKickBan: Boolean,
        canManageChannels: Boolean,
        secondaryColorHex: Long? = null
    ) {
        val clean = roleName.trim()
        if (clean.isBlank()) return
        val newRole = CustomRole(
            id = "role_${UUID.randomUUID().toString().take(6)}",
            name = clean,
            colorHex = colorHex,
            secondaryColorHex = secondaryColorHex,
            isAdmin = isAdmin,
            canKickBan = canKickBan,
            canManageChannels = canManageChannels
        )
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a créé le rôle",
                    targetName = clean,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(
                    customRoles = server.customRoles + newRole,
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }
    }

    fun assignMemberRole(serverId: String, userId: String, role: UserRole, customRoleId: String? = null) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val targetName = server.members.find { it.id == userId }?.name ?: "Membre"
                val roleLabel = customRoleId?.let { rid -> server.customRoles.find { it.id == rid }?.name } ?: role.name
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a attribué le rôle $roleLabel à",
                    targetName = targetName,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(
                    members = server.members.map {
                        if (it.id == userId) it.copy(role = role, customRoleId = customRoleId) else it
                    },
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }
    }

    fun timeoutMember(serverId: String, userId: String) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val target = server.members.find { it.id == userId } ?: return@map server
                val nextTimedOut = !target.isTimedOut
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = if (nextTimedOut) "a exclu temporairement (Timeout)" else "a levé le Timeout de",
                    targetName = target.name,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(
                    members = server.members.map {
                        if (it.id == userId) it.copy(isTimedOut = nextTimedOut) else it
                    },
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }
    }

    fun kickMember(serverId: String, userId: String) {
        if (userId == _currentUser.value.id || userId == "user_me") return
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val target = server.members.find { it.id == userId } ?: return@map server
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a expulsé du serveur",
                    targetName = target.name,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(
                    members = server.members.filterNot { it.id == userId },
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }
    }

    fun createServer(
        name: String,
        iconText: String,
        isPublic: Boolean = false,
        categoryTag: String = "Communauté",
        description: String = "Serveur communautaire Raven."
    ) {
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
        val inviteCode = "RAVEN-${(1000..9999).random()}"
        val newServer = Server(
            id = newServerId,
            name = name,
            iconText = iconText.ifBlank { name.take(2).uppercase() },
            iconBgColor = 0xFF2B2D31,
            channels = defaultChannels,
            members = listOf(_currentUser.value),
            ownerId = _currentUser.value.id,
            inviteCode = inviteCode,
            isPublic = isPublic,
            categoryTag = categoryTag,
            description = description
        )
        _servers.value = _servers.value + newServer
        if (isPublic) {
            _discoverServers.value = listOf(
                PublicServerListing(
                    id = newServerId,
                    name = name,
                    iconText = newServer.iconText,
                    iconBgColor = newServer.iconBgColor,
                    categoryTag = categoryTag,
                    description = description,
                    inviteCode = inviteCode,
                    memberCount = 1,
                    boostLevel = 0,
                    isVerified = false
                )
            ) + _discoverServers.value
        }
        selectServer(newServerId)
        syncFullStateToFirestore()
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
            if (s.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a créé le salon",
                    targetName = "#$cleanName",
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                s.copy(channels = s.channels + newChannel, auditLogs = listOf(log) + s.auditLogs)
            } else s
        }
        val db = firestore
        val uid = currentAuth()?.currentUser?.uid
        if (db != null && uid != null) {
            scope.launch(Dispatchers.IO) {
                ensureServerAndChannelInFirestore(db, serverId, newChannel, uid)
            }
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
        addNotification(
            title = "Nouvel ami ajouté",
            body = "${newFriend.name}${newFriend.tag} a rejoint votre liste d'amis.",
            sourceLabel = "Messages Privés",
            type = NotificationType.FRIEND_REQUEST
        )
        return true
    }

    // --- Group DMs ---
    fun createGroupConversation(groupName: String, members: List<User>): DirectConversation? {
        if (members.size < 2) return null
        val cleanName = groupName.trim().ifBlank { members.joinToString(", ") { it.name }.take(28) }
        val groupId = "grp_${UUID.randomUUID().toString().take(8)}"
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val firstMember = members.first()
        val groupConv = DirectConversation(
            id = groupId,
            friend = firstMember,
            lastMessage = "Groupe créé avec ${members.size} membres",
            lastMessageTime = currentTime,
            unreadCount = 0,
            isGroup = true,
            groupName = cleanName,
            groupMembers = members,
            groupIconColor = 0xFF5865F2
        )
        _directConversations.value = listOf(groupConv) + _directConversations.value
        _messages.value = _messages.value + (
            groupId to listOf(
                Message(
                    id = "grp_init_$groupId",
                    channelId = groupId,
                    sender = _currentUser.value,
                    text = "Groupe privé « $cleanName » créé avec ${members.joinToString(", ") { it.name }} !",
                    timestamp = currentTime,
                    isSystemAnnouncement = true
                )
            )
        )
        selectServer("dm_tab")
        selectDm(groupId)
        return groupConv
    }

    // --- Friend Requests & Blocking ---
    fun acceptFriendRequest(requestId: String) {
        val req = _friendRequests.value.find { it.id == requestId } ?: return
        _friendRequests.value = _friendRequests.value.filterNot { it.id == requestId }
        val newDm = DirectConversation(
            id = "dm_${req.user.id}",
            friend = req.user,
            lastMessage = "Demande d'ami acceptée !",
            lastMessageTime = "À l'instant"
        )
        if (_directConversations.value.none { it.friend.id == req.user.id }) {
            _directConversations.value = listOf(newDm) + _directConversations.value
        }
        addNotification(
            title = "Nouvel ami : ${req.user.name}",
            body = "Vous êtes désormais ami avec ${req.user.name}${req.user.tag}.",
            sourceLabel = "Amis",
            type = NotificationType.FRIEND_REQUEST
        )
    }

    fun declineOrCancelFriendRequest(requestId: String) {
        _friendRequests.value = _friendRequests.value.filterNot { it.id == requestId }
    }

    fun blockUser(user: User) {
        if (_blockedUsers.value.none { it.id == user.id }) {
            _blockedUsers.value = _blockedUsers.value + user
        }
        _directConversations.value = _directConversations.value.filterNot { !it.isGroup && it.friend.id == user.id }
        if (_selectedDmId.value == "dm_${user.id}") {
            _selectedDmId.value = null
        }
    }

    fun unblockUser(userId: String) {
        _blockedUsers.value = _blockedUsers.value.filterNot { it.id == userId }
    }

    // --- Server Events ---
    fun createServerEvent(
        serverId: String,
        title: String,
        description: String,
        dateTimeLabel: String,
        locationLabel: String
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val newEvent = ServerEvent(
            id = "evt_${UUID.randomUUID().toString().take(6)}",
            title = cleanTitle,
            description = description.trim().ifBlank { "Événement communautaire sur Raven." },
            dateTimeLabel = dateTimeLabel.trim().ifBlank { "Ce week-end • 20:00" },
            locationLabel = locationLabel.trim().ifBlank { "Salon vocal 1" },
            creatorName = _currentUser.value.name,
            interestedUserIds = listOf(_currentUser.value.id)
        )
        _servers.value = _servers.value.map { s ->
            if (s.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a planifié l'événement",
                    targetName = cleanTitle,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                s.copy(events = listOf(newEvent) + s.events, auditLogs = listOf(log) + s.auditLogs)
            } else s
        }
        addNotification(
            title = "Nouvel événement : $cleanTitle",
            body = "${newEvent.dateTimeLabel} • ${newEvent.locationLabel}",
            sourceLabel = "Événements",
            type = NotificationType.SYSTEM
        )
    }

    fun toggleEventInterest(serverId: String, eventId: String) {
        val uid = _currentUser.value.id
        _servers.value = _servers.value.map { s ->
            if (s.id == serverId) {
                s.copy(
                    events = s.events.map { evt ->
                        if (evt.id == eventId) {
                            val interested = if (evt.interestedUserIds.contains(uid)) {
                                evt.interestedUserIds - uid
                            } else {
                                addNotification(
                                    title = "Rappel d'événement",
                                    body = "Vous participez à « ${evt.title} » (${evt.dateTimeLabel}).",
                                    sourceLabel = s.name,
                                    type = NotificationType.SYSTEM
                                )
                                advanceQuest("q_poll")
                                evt.interestedUserIds + uid
                            }
                            evt.copy(interestedUserIds = interested)
                        } else evt
                    }
                )
            } else s
        }
    }

    // --- Stage Channels ---
    fun toggleRaiseHand(channelId: String) {
        val uid = _currentUser.value.id
        val current = _connectedVoiceChannel.value ?: return
        if (current.id != channelId) return
        val nextHands = if (current.stageRaisedHandIds.contains(uid)) {
            current.stageRaisedHandIds - uid
        } else {
            current.stageRaisedHandIds + uid
        }
        updateStageChannel(current.copy(stageRaisedHandIds = nextHands))
    }

    fun promoteToSpeaker(channelId: String, userId: String) {
        val current = _connectedVoiceChannel.value ?: return
        if (current.id != channelId) return
        val updated = current.copy(
            stageSpeakerIds = (current.stageSpeakerIds + userId).distinct(),
            stageRaisedHandIds = current.stageRaisedHandIds - userId
        )
        updateStageChannel(updated)
    }

    fun moveToAudience(channelId: String, userId: String) {
        val current = _connectedVoiceChannel.value ?: return
        if (current.id != channelId) return
        val updated = current.copy(
            stageSpeakerIds = current.stageSpeakerIds - userId,
            stageRaisedHandIds = current.stageRaisedHandIds - userId
        )
        updateStageChannel(updated)
    }

    private fun updateStageChannel(updated: Channel) {
        _connectedVoiceChannel.value = updated
        _servers.value = _servers.value.map { s ->
            if (s.id == updated.serverId) {
                s.copy(channels = s.channels.map { if (it.id == updated.id) updated else it })
            } else s
        }
    }

    // --- Server Folders ---
    fun createServerFolder(name: String, colorHex: Long, serverIds: List<String>) {
        if (name.isBlank() || serverIds.isEmpty()) return
        val folder = ServerFolder(
            id = "fld_${UUID.randomUUID().toString().take(6)}",
            name = name.trim(),
            colorHex = colorHex,
            serverIds = serverIds.distinct(),
            isExpanded = true
        )
        // Ensure each server is removed from previous folders so it stays uniquely grouped
        val cleaned = _serverFolders.value.map {
            it.copy(serverIds = it.serverIds.filterNot { id -> id in serverIds })
        }.filter { it.serverIds.isNotEmpty() }
        _serverFolders.value = cleaned + folder
        syncFullStateToFirestore()
    }

    fun toggleServerFolderExpanded(folderId: String) {
        _serverFolders.value = _serverFolders.value.map {
            if (it.id == folderId) it.copy(isExpanded = !it.isExpanded) else it
        }
    }

    // --- Stickers & File Attachments ---
    fun unlockSticker(stickerId: String, priceOrbs: Int): Boolean {
        val curr = _currentUser.value
        if (curr.unlockedStickers.contains(stickerId)) return true
        if (curr.orbsBalance < priceOrbs) return false
        _currentUser.value = curr.copy(
            orbsBalance = curr.orbsBalance - priceOrbs,
            unlockedStickers = curr.unlockedStickers + stickerId
        )
        com.example.util.SoundFxHelper.playChime()
        syncFullStateToFirestore()
        return true
    }

    fun sendSticker(targetId: String, stickerId: String) {
        val sticker = StickerCatalog.allStickers.find { it.id == stickerId } ?: return
        if (!canPostInTarget(targetId)) return
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = Message(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            channelId = targetId,
            sender = _currentUser.value,
            text = "",
            timestamp = currentTime,
            stickerId = sticker.id
        )
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + msg))
        grantXp(10, "Sticker partagé")
        advanceQuest("q_sticker")
        syncNewMessageToFirestore(msg, targetId)
    }

    fun sendFileAttachment(targetId: String, fileName: String, fileType: String, sizeLabel: String) {
        if (!canPostInTarget(targetId)) return
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = Message(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            channelId = targetId,
            sender = _currentUser.value,
            text = "",
            timestamp = currentTime,
            attachmentName = fileName,
            attachmentType = fileType,
            attachmentSizeLabel = sizeLabel
        )
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + msg))
        grantXp(15, "Fichier partagé")
        advanceQuest("q_msg")
        syncNewMessageToFirestore(msg, targetId)
    }

    // --- Slash Commands ---
    fun executeSlashCommand(targetId: String, rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("/")) return false
        val parts = trimmed.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        when (cmd) {
            "/de" -> {
                val sides = arg.toIntOrNull()?.coerceIn(2, 1000) ?: 6
                val rolled = (1..sides).random()
                postCommandMessage(targetId, "Lancer de dé (D$sides) : résultat = $rolled", currentTime)
            }
            "/pileouface" -> {
                val side = if ((0..1).random() == 0) "Pile" else "Face"
                postCommandMessage(targetId, "Lancer de pièce : $side !", currentTime)
            }
            "/xp" -> {
                val u = _currentUser.value
                postCommandMessage(
                    targetId,
                    "${u.name} • Niveau ${u.level} (${u.xp}/${u.level * 100} XP) • ${u.orbsBalance} Orbs",
                    currentTime
                )
            }
            "/annonce" -> {
                val body = arg.ifBlank { "Annonce importante sur le serveur !" }
                postCommandMessage(targetId, "ANNONCE : $body", currentTime, isAnnouncement = true)
            }
            "/sondage" -> {
                val segments = arg.split("|").map { it.trim() }.filter { it.isNotBlank() }
                if (segments.size >= 3) {
                    createPoll(targetId, segments.first(), segments.drop(1))
                } else {
                    createPoll(
                        targetId,
                        arg.ifBlank { "Session vocale ce soir ?" },
                        listOf("Oui, chaud !", "Plus tard", "Pas dispo")
                    )
                }
            }
            "/clear" -> {
                _messages.value = _messages.value + (targetId to emptyList())
            }
            else -> return false
        }
        return true
    }

    private fun postCommandMessage(
        targetId: String,
        text: String,
        timestamp: String,
        isAnnouncement: Boolean = false
    ) {
        val msg = Message(
            id = "cmd_${UUID.randomUUID().toString().take(8)}",
            channelId = targetId,
            sender = _currentUser.value,
            text = text,
            timestamp = timestamp,
            isSystemAnnouncement = isAnnouncement
        )
        val currentList = _messages.value[targetId] ?: emptyList()
        _messages.value = _messages.value + (targetId to (currentList + msg))
        grantXp(10, "Commande Slash")
    }

    // --- Typing Indicator ---
    fun setTypingUser(targetId: String, userName: String, isTyping: Boolean) {
        val curr = _typingUsersByTarget.value[targetId] ?: emptyList()
        val updated = if (isTyping) (curr + userName).distinct() else curr.filterNot { it == userName }
        _typingUsersByTarget.value = _typingUsersByTarget.value + (targetId to updated)
    }

    private fun simulateBriefReplyTyping(targetId: String) {
        val responderName = if (targetId.startsWith("dm_")) {
            _directConversations.value.find { it.id == targetId }?.friend?.name
        } else {
            MockData.friendThomas.name
        } ?: return

        scope.launch {
            delay(450)
            setTypingUser(targetId, responderName, true)
            delay(2200)
            setTypingUser(targetId, responderName, false)
        }
    }

    // --- Server Boost & Per-Channel Controls (Slowmode / Read-Only) ---
    fun boostServer(serverId: String, orbsCost: Int = 200): Boolean {
        val curr = _currentUser.value
        if (curr.orbsBalance < orbsCost) return false

        val updatedBadges = if (curr.badges.contains("BOOSTER")) curr.badges else curr.badges + "BOOSTER"
        _currentUser.value = curr.copy(
            orbsBalance = curr.orbsBalance - orbsCost,
            badges = updatedBadges
        )

        var targetServerName = ""
        var newLevel = 1
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId && !server.isDmTab) {
                val nextCount = server.boostCount + 1
                newLevel = when {
                    nextCount >= 5 -> 3
                    nextCount >= 2 -> 2
                    else -> 1
                }
                targetServerName = server.name
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = curr.name,
                    actionDescription = "a boosté le serveur (Niveau $newLevel) sur",
                    targetName = server.name,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(
                    boostCount = nextCount,
                    boostLevel = newLevel,
                    customBannerColorHex = server.customBannerColorHex ?: 0xFFEB459E,
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }

        if (targetServerName.isNotBlank()) {
            addNotification(
                title = "Boost de serveur activé !",
                body = "$targetServerName passe au Niveau $newLevel grâce à votre Boost.",
                sourceLabel = targetServerName,
                type = NotificationType.LEVEL_UP
            )
            grantXp(25, "Boost de serveur")
            advanceQuest("q_profile")
            com.example.util.SoundFxHelper.playLevelUp()
            syncFullStateToFirestore()
            return true
        }
        return false
    }

    fun updateServerBannerColor(serverId: String, colorHex: Long) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a personnalisé la bannière de",
                    targetName = server.name,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(customBannerColorHex = colorHex, auditLogs = listOf(log) + server.auditLogs)
            } else server
        }
        syncFullStateToFirestore()
    }

    fun updateChannelSettings(
        serverId: String,
        channelId: String,
        slowmodeSeconds: Int,
        isReadOnly: Boolean
    ) {
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                var chName = ""
                val updatedChannels = server.channels.map { ch ->
                    if (ch.id == channelId) {
                        chName = ch.name
                        ch.copy(slowmodeSeconds = slowmodeSeconds, isReadOnly = isReadOnly)
                    } else ch
                }
                val log = AuditLogEntry(
                    id = "log_${UUID.randomUUID().toString().take(6)}",
                    actorName = _currentUser.value.name,
                    actionDescription = "a configuré #${chName.ifBlank { "salon" }} (Slowmode ${slowmodeSeconds}s${if (isReadOnly) ", Lecture seule" else ""}) sur",
                    targetName = server.name,
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                server.copy(channels = updatedChannels, auditLogs = listOf(log) + server.auditLogs)
            } else server
        }
        val db = firestore
        val uid = currentAuth()?.currentUser?.uid
        val updatedCh = findChannelById(channelId)
        if (db != null && uid != null && updatedCh != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    ensureServerAndChannelInFirestore(db, serverId, updatedCh, uid)
                } catch (_: Exception) {
                }
            }
        }
    }

    // --- Live Quests & Daily Streak ---
    fun advanceQuest(questId: String, amount: Int = 1) {
        var justCompletedTitle: String? = null
        _quests.value = _quests.value.map { quest ->
            if (quest.id == questId && !quest.isCompleted) {
                val nextProg = (quest.currentProgress + amount).coerceAtMost(quest.maxProgress)
                val completed = nextProg >= quest.maxProgress
                if (completed) {
                    justCompletedTitle = quest.title
                }
                quest.copy(currentProgress = nextProg, isCompleted = completed)
            } else quest
        }
        justCompletedTitle?.let { title ->
            addNotification(
                title = "Quête terminée : $title",
                body = "Ouvrez la Boutique Orbs pour réclamer votre récompense !",
                sourceLabel = "Quêtes Raven",
                type = NotificationType.SYSTEM
            )
        }
    }

    fun completeQuestInstantly(questId: String) {
        _quests.value = _quests.value.map { quest ->
            if (quest.id == questId) {
                quest.copy(currentProgress = quest.maxProgress, isCompleted = true)
            } else quest
        }
        com.example.util.SoundFxHelper.playChime()
    }

    fun claimQuestReward(questId: String): Int {
        val target = _quests.value.find { it.id == questId } ?: return 0
        if (!target.isCompleted || target.isClaimed) return 0
        _quests.value = _quests.value.map {
            if (it.id == questId) it.copy(isClaimed = true) else it
        }
        updateOrbsBalance(_currentUser.value.orbsBalance + target.rewardOrbs)
        grantXp(20, "Quête réclamée")
        com.example.util.SoundFxHelper.playChime()
        return target.rewardOrbs
    }

    fun claimDailyStreakReward(): Int {
        if (_streakClaimedToday.value) return 0
        val nextStreak = _dailyStreak.value + 1
        val rewardOrbs = 50 + (nextStreak * 15).coerceAtMost(200)
        _dailyStreak.value = nextStreak
        _streakClaimedToday.value = true
        updateOrbsBalance(_currentUser.value.orbsBalance + rewardOrbs)
        grantXp(30, "Série quotidienne Jour $nextStreak")
        addNotification(
            title = "Série quotidienne : Jour $nextStreak !",
            body = "+$rewardOrbs Orbs et +30 XP crédités sur votre compte.",
            sourceLabel = "Récompense Quotidienne",
            type = NotificationType.LEVEL_UP
        )
        com.example.util.SoundFxHelper.playLevelUp()
        syncFullStateToFirestore()
        return rewardOrbs
    }

    // --- Private Notes on User Profiles ---
    fun setPrivateUserNote(userId: String, note: String) {
        val trimmed = note.trim()
        _privateUserNotes.value = if (trimmed.isBlank()) {
            _privateUserNotes.value - userId
        } else {
            _privateUserNotes.value + (userId to trimmed)
        }
        syncFullStateToFirestore()
    }

    // --- Global Multi-Channel / Multi-DM Search ---
    fun searchAllMessages(
        serverId: String? = null,
        query: String,
        filter: GlobalSearchFilter = GlobalSearchFilter.ALL
    ): List<GlobalSearchResult> {
        val q = query.trim()
        val results = mutableListOf<GlobalSearchResult>()

        val targetServers = if (serverId != null && serverId != "dm_tab") {
            _servers.value.filter { it.id == serverId }
        } else {
            _servers.value.filterNot { it.isDmTab }
        }

        for (srv in targetServers) {
            for (ch in srv.channels) {
                val chMsgs = _messages.value[ch.id].orEmpty()
                for (msg in chMsgs) {
                    if (matchesSearchFilter(msg, q, filter)) {
                        results.add(
                            GlobalSearchResult(
                                targetId = ch.id,
                                targetTitle = "#${ch.name}",
                                serverName = srv.name,
                                isDm = false,
                                message = msg
                            )
                        )
                    }
                }
            }
        }

        if (serverId == null || serverId == "dm_tab") {
            for (conv in _directConversations.value) {
                val dmMsgs = _messages.value[conv.id].orEmpty()
                val title = if (conv.isGroup) conv.groupName ?: "Groupe" else "@${conv.friend.name}"
                for (msg in dmMsgs) {
                    if (matchesSearchFilter(msg, q, filter)) {
                        results.add(
                            GlobalSearchResult(
                                targetId = conv.id,
                                targetTitle = title,
                                serverName = "Messages Privés",
                                isDm = true,
                                message = msg
                            )
                        )
                    }
                }
            }
        }
        return results.reversed()
    }

    private fun matchesSearchFilter(msg: Message, query: String, filter: GlobalSearchFilter): Boolean {
        val matchesFilter = when (filter) {
            GlobalSearchFilter.ALL -> true
            GlobalSearchFilter.IMAGES -> !msg.imageUrl.isNullOrBlank()
            GlobalSearchFilter.FILES -> !msg.attachmentName.isNullOrBlank() || !msg.audioUrl.isNullOrBlank()
            GlobalSearchFilter.PINNED -> msg.isPinned
        }
        if (!matchesFilter) return false
        if (query.isBlank()) return true
        return msg.text.contains(query, ignoreCase = true) ||
            msg.sender.name.contains(query, ignoreCase = true) ||
            (msg.attachmentName?.contains(query, ignoreCase = true) == true)
    }

    // --- Bloc 1: Public Discovery & Live Typing Broadcast ---
    fun joinPublicServer(listing: PublicServerListing): Boolean {
        val existing = _servers.value.find {
            !it.isDmTab && (it.id == listing.id || it.inviteCode.equals(listing.inviteCode, ignoreCase = true))
        }
        if (existing != null) {
            selectServer(existing.id)
            return true
        }
        val srvId = listing.id
        val genId = "${srvId}_general"
        val voiceId = "${srvId}_voice"
        val joined = Server(
            id = srvId,
            name = listing.name,
            iconText = listing.iconText,
            iconBgColor = listing.iconBgColor,
            channels = listOf(
                Channel(genId, srvId, "général", ChannelType.TEXT, "SALONS TEXTUELS"),
                Channel("${srvId}_annonces", srvId, "annonces", ChannelType.ANNOUNCEMENT, "SALONS TEXTUELS", isReadOnly = true),
                Channel(voiceId, srvId, "Vocal Principal", ChannelType.VOICE, "SALONS VOCAUX", activeVoiceUsers = listOf(MockData.friendThomas))
            ),
            members = listOf(_currentUser.value, MockData.friendThomas, MockData.friendEmma),
            inviteCode = listing.inviteCode,
            boostCount = listing.boostLevel * 2,
            boostLevel = listing.boostLevel,
            customBannerColorHex = listing.iconBgColor,
            isPublic = true,
            categoryTag = listing.categoryTag,
            description = listing.description,
            ownerId = MockData.friendThomas.id
        )
        _servers.value = _servers.value + joined
        _messages.value = _messages.value + (
            genId to listOf(
                Message(
                    id = "pub_welcome_$srvId",
                    channelId = genId,
                    sender = MockData.friendThomas,
                    text = "Bienvenue sur ${listing.name} (${listing.categoryTag}) ! ${listing.description}",
                    timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                    isSystemAnnouncement = true
                )
            )
        )
        addNotification(
            title = "Serveur rejoint : ${listing.name}",
            body = "Vous avez rejoint la communauté ${listing.categoryTag} (${listing.inviteCode}).",
            sourceLabel = listing.name,
            type = NotificationType.SYSTEM
        )
        grantXp(20, "Découverte communauté")
        selectServer(srvId)

        val db = firestore
        val uid = currentAuth()?.currentUser?.uid
        if (db != null && uid != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val ref = db.collection("servers").document(srvId)
                    val snap = Tasks.await(ref.get())
                    if (snap.exists() && snap.getBoolean("isPublic") == true) {
                        Tasks.await(
                            ref.update(
                                mapOf(
                                    "memberIds" to FieldValue.arrayUnion(uid),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w("ChatRepo", "Failed to join public server in Firestore", e)
                }
            }
        }
        return true
    }

    fun refreshPublicServersFromCloud() {
        val db = firestore ?: return
        val authUser = currentAuth()?.currentUser ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val snap = Tasks.await(
                    db.collection("servers").whereEqualTo("isPublic", true).limit(20).get()
                )
                val cloudListings = snap.documents.mapNotNull { doc ->
                    val sName = doc.getString("name") ?: return@mapNotNull null
                    PublicServerListing(
                        id = doc.getString("id") ?: doc.id,
                        name = sName,
                        iconText = doc.getString("iconText") ?: sName.take(2).uppercase(),
                        iconBgColor = doc.getLong("iconBgColor") ?: 0xFF5865F2,
                        categoryTag = doc.getString("categoryTag") ?: "Communauté",
                        description = doc.getString("description") ?: "Communauté active sur Raven.",
                        inviteCode = doc.getString("inviteCode") ?: "RAVEN-2026",
                        memberCount = ((doc.get("memberIds") as? List<*>)?.size ?: 1).coerceAtLeast(1),
                        boostLevel = doc.getLong("boostLevel")?.toInt() ?: 0,
                        isVerified = doc.getString("ownerId") != authUser.uid
                    )
                }
                if (cloudListings.isNotEmpty()) {
                    _discoverServers.value = (cloudListings + MockData.publicExploreServers).distinctBy { it.inviteCode }
                }
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to fetch public discover servers", e)
            }
        }
    }

    fun broadcastTyping(targetId: String) {
        val db = firestore ?: return
        val authUser = currentAuth()?.currentUser ?: return
        val ch = findChannelById(targetId) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val (isPublic, memberIds) = ensureServerAndChannelInFirestore(db, ch.serverId, ch, authUser.uid)
                db.collection("servers").document(ch.serverId)
                    .collection("channels").document(targetId)
                    .set(
                        mapOf(
                            "id" to targetId,
                            "serverId" to ch.serverId,
                            "name" to ch.name,
                            "type" to ch.type.name,
                            "isPublic" to isPublic,
                            "memberIds" to memberIds,
                            "lastTypingUid" to authUser.uid,
                            "lastTypingName" to _currentUser.value.name,
                            "lastTypingMs" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    )
            } catch (_: Exception) {
            }
        }
    }

    // --- Bloc 2: Collaborative Voice Jukebox & Mixer ---
    fun toggleJukeboxPlayPause() {
        val curr = _jukeboxState.value
        val next = if (curr.currentTrack == null && curr.queue.isNotEmpty()) {
            curr.copy(
                currentTrack = curr.queue.first(),
                queue = curr.queue.drop(1),
                elapsedSec = 0,
                isPlaying = true
            )
        } else {
            curr.copy(isPlaying = !curr.isPlaying)
        }
        _jukeboxState.value = next
        jukeboxAudioEngine?.syncWithState(next.currentTrack, next.isPlaying, next.elapsedSec)
        syncJukeboxToFirestore()
        com.example.util.SoundFxHelper.playTick()
    }

    fun addTrackToJukebox(track: MusicTrack) {
        val tagged = track.copy(
            id = "${track.id}_${UUID.randomUUID().toString().take(4)}",
            addedBy = _currentUser.value.name,
            voteSkipUserIds = emptyList()
        )
        val curr = _jukeboxState.value
        val next = if (curr.currentTrack == null) {
            curr.copy(currentTrack = tagged, elapsedSec = 0, isPlaying = true)
        } else {
            curr.copy(queue = curr.queue + tagged)
        }
        _jukeboxState.value = next
        jukeboxAudioEngine?.syncWithState(next.currentTrack, next.isPlaying, next.elapsedSec)
        syncJukeboxToFirestore()
        grantXp(10, "Ajout au Jukebox")
        advanceQuest("q_voice")
        com.example.util.SoundFxHelper.playChime()
    }

    fun skipJukeboxTrack() {
        val curr = _jukeboxState.value
        val nextTrack = curr.queue.firstOrNull()
        val remaining = if (curr.queue.size > 1) curr.queue.drop(1) else emptyList()
        val next = curr.copy(
            currentTrack = nextTrack,
            queue = remaining,
            elapsedSec = 0,
            isPlaying = nextTrack != null
        )
        _jukeboxState.value = next
        jukeboxAudioEngine?.syncWithState(next.currentTrack, next.isPlaying, 0)
        syncJukeboxToFirestore()
    }

    fun voteSkipCurrentTrack(): Boolean {
        val curr = _jukeboxState.value
        val track = curr.currentTrack ?: return false
        val uid = _currentUser.value.id
        if (uid in track.voteSkipUserIds) return false
        val updatedVotes = track.voteSkipUserIds + uid
        val threshold = 2
        if (updatedVotes.size >= threshold || track.addedBy == _currentUser.value.name) {
            skipJukeboxTrack()
            return true
        }
        _jukeboxState.value = curr.copy(currentTrack = track.copy(voteSkipUserIds = updatedVotes))
        syncJukeboxToFirestore()
        return false
    }

    private fun syncJukeboxToFirestore() {
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        val voice = _connectedVoiceChannel.value ?: return
        if (voice.serverId == "dm_tab") return
        val jb = _jukeboxState.value

        fun trackToMap(t: MusicTrack): Map<String, Any> = buildMap {
            put("id", t.id)
            put("title", t.title)
            put("artist", t.artist)
            put("durationSec", t.durationSec)
            put("addedBy", t.addedBy)
            put("accentHex", t.accentHex)
            put("voteSkipUserIds", t.voteSkipUserIds)
            t.streamUrl?.let { put("streamUrl", it) }
        }

        scope.launch(Dispatchers.IO) {
            try {
                val (isPublic, memberIds) = ensureServerAndChannelInFirestore(db, voice.serverId, voice, uid)
                val payload = hashMapOf<String, Any>(
                    "serverId" to voice.serverId,
                    "updatedBy" to uid,
                    "isPlaying" to jb.isPlaying,
                    "elapsedSec" to jb.elapsedSec,
                    "queue" to jb.queue.map(::trackToMap),
                    "memberIds" to memberIds,
                    "isPublic" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                jb.currentTrack?.let { payload["currentTrack"] = trackToMap(it) }
                Tasks.await(db.collection("servers").document(voice.serverId)
                    .collection("channels").document(voice.id)
                    .collection("jukebox").document("state")
                    .set(payload))
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync jukebox to Firestore", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun listenToJukebox(channelId: String) {
        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        val ch = findChannelById(channelId) ?: return
        if (ch.serverId == "dm_tab") return
        activeJukeboxListener?.remove()

        fun parseTrack(map: Map<String, Any?>?): MusicTrack? {
            map ?: return null
            val title = map["title"] as? String ?: return null
            return MusicTrack(
                id = (map["id"] as? String) ?: "trk_cloud",
                title = title,
                artist = (map["artist"] as? String) ?: "Raven Audio",
                durationSec = (map["durationSec"] as? Number)?.toInt() ?: 180,
                addedBy = (map["addedBy"] as? String) ?: "Membre",
                accentHex = (map["accentHex"] as? Number)?.toLong() ?: 0xFF5865F2,
                voteSkipUserIds = (map["voteSkipUserIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                streamUrl = map["streamUrl"] as? String
            )
        }

        val (isPublic, _) = serverScopeInfo(ch.serverId)
        val uid = currentAuth()?.currentUser?.uid ?: return
        val jbCol = db.collection("servers").document(ch.serverId)
            .collection("channels").document(channelId)
            .collection("jukebox")
        val jbQuery = if (isPublic) jbCol.whereEqualTo("isPublic", true) else jbCol.whereArrayContains("memberIds", uid)

        activeJukeboxListener = jbQuery.addSnapshotListener { qSnap, err ->
            if (err != null || qSnap == null || qSnap.isEmpty) return@addSnapshotListener
            val snap = qSnap.documents.firstOrNull { it.id == "state" } ?: qSnap.documents.first()
            val isPlaying = snap.getBoolean("isPlaying") ?: false
            val elapsed = snap.getLong("elapsedSec")?.toInt() ?: 0
            val currentTrack = parseTrack(snap.get("currentTrack") as? Map<String, Any?>)
            val queue = (snap.get("queue") as? List<Map<String, Any?>>).orEmpty().mapNotNull(::parseTrack)
            val updated = VoiceJukeboxState(
                isPlaying = isPlaying,
                currentTrack = currentTrack,
                queue = queue,
                elapsedSec = elapsed
            )
            _jukeboxState.value = updated
            jukeboxAudioEngine?.syncWithState(currentTrack, isPlaying, elapsed)
        }
    }

    fun setParticipantVolume(userId: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 2f)
        _participantVolumes.value = _participantVolumes.value + (userId to clamped)
        webRtcVoiceManager?.setRemotePeerVolume(userId, clamped)
    }

    fun setAudioEqPreset(preset: AudioEqPreset) {
        _appSettings.value = _appSettings.value.copy(audioEqPreset = preset)
        jukeboxAudioEngine?.applyEqPreset(preset)
        syncFullStateToFirestore()
    }

    // --- Bloc 3: Theme Customizer & Clan Tag ---
    fun updateClanTag(tag: String, iconKey: String = _currentUser.value.clanBadgeIcon) {
        val normalized = tag.trim().uppercase().take(4).ifBlank { null }
        val updated = _currentUser.value.copy(clanTag = normalized, clanBadgeIcon = iconKey)
        _currentUser.value = updated
        _servers.value = _servers.value.map { srv ->
            srv.copy(members = srv.members.map {
                if (it.id == updated.id || it.id == "user_me") updated else it
            })
        }
        advanceQuest("q_profile")
        syncFullStateToFirestore()
    }

    // --- Bloc 4: Leaderboard, Whiteboard & Server Shop ---
    fun getServerLeaderboard(serverId: String): List<Pair<User, Int>> {
        val srv = _servers.value.find { it.id == serverId } ?: return emptyList()
        return srv.members.map { member ->
            val score = (member.level * 100) + member.xp + (if ("BOOSTER" in member.badges) 75 else 0)
            member to score
        }.sortedByDescending { it.second }
    }

    fun addWhiteboardStroke(targetId: String, stroke: WhiteboardStroke) {
        val existing = _whiteboardStrokes.value[targetId].orEmpty()
        val updated = (existing + stroke).takeLast(150)
        _whiteboardStrokes.value = _whiteboardStrokes.value + (targetId to updated)
        syncWhiteboardToFirestore(targetId, updated)
    }

    fun undoWhiteboardStroke(targetId: String) {
        val existing = _whiteboardStrokes.value[targetId].orEmpty()
        if (existing.isNotEmpty()) {
            val updated = existing.dropLast(1)
            _whiteboardStrokes.value = _whiteboardStrokes.value + (targetId to updated)
            syncWhiteboardToFirestore(targetId, updated)
        }
    }

    fun clearWhiteboard(targetId: String) {
        _whiteboardStrokes.value = _whiteboardStrokes.value + (targetId to emptyList())
        syncWhiteboardToFirestore(targetId, emptyList())
    }

    private fun syncWhiteboardToFirestore(channelId: String, strokes: List<WhiteboardStroke>) {
        val db = firestore ?: return
        val uid = currentAuth()?.currentUser?.uid ?: return
        val ch = findChannelById(channelId) ?: return
        val serialized = strokes.map { st ->
            mapOf(
                "colorHex" to st.colorHex,
                "strokeWidth" to st.strokeWidth.toDouble(),
                "points" to st.points.map { (x, y) -> "$x,$y" }
            )
        }
        scope.launch(Dispatchers.IO) {
            try {
                val (isPublic, memberIds) = ensureServerAndChannelInFirestore(db, ch.serverId, ch, uid)
                Tasks.await(db.collection("servers").document(ch.serverId)
                    .collection("channels").document(channelId)
                    .collection("whiteboard").document("canvas")
                    .set(
                        mapOf(
                            "serverId" to ch.serverId,
                            "updatedBy" to uid,
                            "memberIds" to memberIds,
                            "isPublic" to isPublic,
                            "strokes" to serialized,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ))
            } catch (e: Exception) {
                Log.w("ChatRepo", "Failed to sync whiteboard to Firestore", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun listenToWhiteboard(channelId: String) {
        val db = firestore ?: return
        currentAuth()?.currentUser ?: return
        val ch = findChannelById(channelId) ?: return
        if (ch.serverId == "dm_tab") return
        activeWhiteboardListener?.remove()

        val (isPublic, _) = serverScopeInfo(ch.serverId)
        val uid = currentAuth()?.currentUser?.uid ?: return
        val wbCol = db.collection("servers").document(ch.serverId)
            .collection("channels").document(channelId)
            .collection("whiteboard")
        val wbQuery = if (isPublic) wbCol.whereEqualTo("isPublic", true) else wbCol.whereArrayContains("memberIds", uid)

        activeWhiteboardListener = wbQuery.addSnapshotListener { qSnap, err ->
            if (err != null || qSnap == null || qSnap.isEmpty) return@addSnapshotListener
            val snap = qSnap.documents.firstOrNull { it.id == "canvas" } ?: qSnap.documents.first()
            val rawStrokes = snap.get("strokes") as? List<Map<String, Any?>> ?: return@addSnapshotListener
            val parsed = rawStrokes.mapNotNull { st ->
                val pts = (st["points"] as? List<*>)?.mapNotNull { entry ->
                    val str = entry as? String ?: return@mapNotNull null
                    val parts = str.split(",")
                    val x = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
                    val y = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
                    x to y
                }.orEmpty()
                if (pts.isEmpty()) return@mapNotNull null
                WhiteboardStroke(
                    points = pts,
                    colorHex = (st["colorHex"] as? Number)?.toLong() ?: 0xFF5865F2,
                    strokeWidth = (st["strokeWidth"] as? Number)?.toFloat() ?: 6f
                )
            }
            _whiteboardStrokes.value = _whiteboardStrokes.value + (channelId to parsed)
        }
    }

    fun shareWhiteboardToChannel(targetId: String) {
        val strokesCount = _whiteboardStrokes.value[targetId]?.size ?: 0
        sendMessage(
            targetId = targetId,
            text = "🎨 Tableau blanc collaboratif partagé ($strokesCount tracés) — ouvrez le Tableau Blanc pour dessiner ensemble !"
        )
    }

    fun createServerShopItem(
        serverId: String,
        title: String,
        description: String,
        priceOrbs: Int,
        primaryColorHex: Long,
        secondaryColorHex: Long
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        val newItem = ServerShopItem(
            id = "srv_shop_${UUID.randomUUID().toString().take(6)}",
            title = cleanTitle,
            description = description.trim().ifBlank { "Rôle exclusif du serveur" },
            priceOrbs = priceOrbs.coerceIn(10, 5000),
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            roleName = cleanTitle
        )
        _servers.value = _servers.value.map { srv ->
            if (srv.id == serverId) srv.copy(shopItems = listOf(newItem) + srv.shopItems) else srv
        }
    }

    fun purchaseServerShopItem(serverId: String, itemId: String): Boolean {
        val srv = _servers.value.find { it.id == serverId } ?: return false
        val item = srv.shopItems.find { it.id == itemId } ?: return false
        val curr = _currentUser.value
        if (curr.id in item.purchasedByUserIds) return true
        if (curr.orbsBalance < item.priceOrbs) return false

        val roleId = "role_${item.id}"
        val newRole = CustomRole(
            id = roleId,
            name = item.roleName,
            colorHex = item.primaryColorHex,
            secondaryColorHex = item.secondaryColorHex
        )
        val updatedUser = curr.copy(
            orbsBalance = curr.orbsBalance - item.priceOrbs,
            customRoleId = roleId
        )
        _currentUser.value = updatedUser

        val log = AuditLogEntry(
            id = "log_${UUID.randomUUID().toString().take(6)}",
            actorName = curr.name,
            actionDescription = "a acheté « ${item.title} » dans la boutique de",
            targetName = srv.name,
            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        _servers.value = _servers.value.map { server ->
            if (server.id == serverId) {
                val updatedRoles = if (server.customRoles.any { it.id == roleId }) server.customRoles else (server.customRoles + newRole)
                server.copy(
                    customRoles = updatedRoles,
                    members = server.members.map { if (it.id == curr.id || it.id == "user_me") updatedUser else it },
                    shopItems = server.shopItems.map {
                        if (it.id == itemId) it.copy(purchasedByUserIds = it.purchasedByUserIds + curr.id) else it
                    },
                    auditLogs = listOf(log) + server.auditLogs
                )
            } else server
        }
        grantXp(25, "Achat Boutique Serveur")
        com.example.util.SoundFxHelper.playLevelUp()
        syncFullStateToFirestore()
        return true
    }

    // --- Bloc 5: Drafts, Scheduled Messages & Message Reminders ---
    fun updateDraft(targetId: String, text: String) {
        if (text.isBlank()) {
            if (_draftsByTarget.value.containsKey(targetId)) {
                _draftsByTarget.value = _draftsByTarget.value - targetId
            }
        } else {
            _draftsByTarget.value = _draftsByTarget.value + (targetId to text)
        }
    }

    fun scheduleMessage(
        targetId: String,
        targetLabel: String,
        text: String,
        delayMs: Long,
        delayLabel: String
    ): ScheduledMessage? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val sched = ScheduledMessage(
            id = "sched_${UUID.randomUUID().toString().take(6)}",
            targetId = targetId,
            targetLabel = targetLabel,
            text = clean,
            triggerAtMs = System.currentTimeMillis() + delayMs,
            delayLabel = delayLabel
        )
        _scheduledMessages.value = _scheduledMessages.value + sched
        updateDraft(targetId, "")
        scope.launch {
            delay(delayMs.coerceAtLeast(100L))
            if (_scheduledMessages.value.any { it.id == sched.id }) {
                sendScheduledMessageNow(sched.id)
            }
        }
        return sched
    }

    fun cancelScheduledMessage(scheduledId: String) {
        _scheduledMessages.value = _scheduledMessages.value.filterNot { it.id == scheduledId }
    }

    fun sendScheduledMessageNow(scheduledId: String) {
        val sched = _scheduledMessages.value.find { it.id == scheduledId } ?: return
        _scheduledMessages.value = _scheduledMessages.value.filterNot { it.id == scheduledId }
        sendMessage(sched.targetId, sched.text)
        addNotification(
            title = "Message programmé envoyé",
            body = "Envoyé dans ${sched.targetLabel} : ${sched.text.take(40)}",
            sourceLabel = sched.targetLabel,
            type = NotificationType.SYSTEM
        )
    }

    fun scheduleMessageReminder(
        message: Message,
        delayMs: Long,
        delayLabel: String
    ): MessageReminder {
        val reminder = MessageReminder(
            id = "rem_${UUID.randomUUID().toString().take(6)}",
            messageId = message.id,
            targetId = message.channelId,
            senderName = message.sender.name,
            previewText = message.text.ifBlank { "Média partagé" }.take(60),
            triggerAtMs = System.currentTimeMillis() + delayMs,
            delayLabel = delayLabel
        )
        _messageReminders.value = _messageReminders.value + reminder
        scope.launch {
            delay(delayMs.coerceAtLeast(100L))
            if (_messageReminders.value.any { it.id == reminder.id }) {
                _messageReminders.value = _messageReminders.value.filterNot { it.id == reminder.id }
                addNotification(
                    title = "Rappel : message de ${reminder.senderName}",
                    body = reminder.previewText,
                    sourceLabel = "Rappel Raven ($delayLabel)",
                    type = NotificationType.MENTION
                )
            }
        }
        return reminder
    }
}
