package com.example.data

import android.content.Context
import com.example.model.*
import com.example.util.OfflineNetworkMonitor
import com.example.widget.RavenHomeWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modular extension helper managing Raven's advanced features:
 * - Ephemeral message timers & View-Once media protection
 * - Detailed read receipts & multi-target forwarding
 * - Community Orbs tipping & Server Orbs treasury
 * - Forum / Support channels (posts, replies, status, upvotes)
 * - Interactive server onboarding rules acceptance
 * - Direct 1-to-1 / Group Calls with PiP & Call History
 * - Smart Offline message queue & Home Widget snapshots
 */
internal class RavenAdvancedFeaturesHelper(
    private val chatRepo: ChatRepository
) {
    private val _ephemeralTimersByTarget = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ephemeralTimersByTarget: StateFlow<Map<String, Int>> = _ephemeralTimersByTarget.asStateFlow()

    private val _forumPostsByChannel = MutableStateFlow<Map<String, List<ForumPost>>>(emptyMap())
    val forumPostsByChannel: StateFlow<Map<String, List<ForumPost>>> = _forumPostsByChannel.asStateFlow()

    private val _activeDirectCall = MutableStateFlow<DirectCallSession?>(null)
    val activeDirectCall: StateFlow<DirectCallSession?> = _activeDirectCall.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallHistoryEntry>>(emptyList())
    val callHistory: StateFlow<List<CallHistoryEntry>> = _callHistory.asStateFlow()

    private val _pendingOfflineMessages = MutableStateFlow<List<PendingOfflineMessage>>(emptyList())
    val pendingOfflineMessages: StateFlow<List<PendingOfflineMessage>> = _pendingOfflineMessages.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(true)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private var networkMonitor: OfflineNetworkMonitor? = null
    private var sweeperJob: Job? = null

    companion object {
        private const val PREFS_ADV = "raven_advanced_features_prefs"
        private const val KEY_OFFLINE_QUEUE = "offline_queue_json"
    }

    fun initialize(context: Context?) {
        loadOfflineQueue(context)
        seedDefaultForumPosts()
        if (context != null) {
            networkMonitor = OfflineNetworkMonitor(context) {
                _isDeviceOnline.value = true
                if (!chatRepo.appSettings.value.simulateOfflineMode) {
                    flushOfflineQueue()
                }
            }.also { monitor ->
                monitor.start()
                _isDeviceOnline.value = monitor.isOnline.value
            }
        }
        startEphemeralSweeper()
        refreshHomeWidget()
    }

    fun release() {
        sweeperJob?.cancel()
        sweeperJob = null
        networkMonitor?.stop()
        networkMonitor = null
    }

    fun isEffectivelyOnline(): Boolean =
        _isDeviceOnline.value && !chatRepo.appSettings.value.simulateOfflineMode

    private fun startEphemeralSweeper() {
        sweeperJob?.cancel()
        sweeperJob = chatRepo.repoScope().launch(Dispatchers.Default) {
            while (true) {
                delay(15_000L)
                purgeExpiredEphemeralMessages()
            }
        }
    }

    fun purgeExpiredEphemeralMessages() {
        val now = System.currentTimeMillis()
        val currentUid = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
        val currentMap = chatRepo.messagesMutableFlow().value
        var changed = false
        val expiredOwnedCloudIds = mutableListOf<Pair<String, String>>()
        val cleaned = currentMap.mapValues { (targetId, list) ->
            val filtered = list.filter { msg ->
                val exp = msg.ephemeralExpiresAtMs
                val expired = exp != null && exp <= now
                if (expired && (msg.sender.id == currentUid || msg.sender.id == "user_me")) {
                    expiredOwnedCloudIds.add(targetId to msg.id)
                }
                !expired
            }
            if (filtered.size != list.size) changed = true
            filtered
        }
        if (changed) {
            chatRepo.messagesMutableFlow().value = cleaned
        }
        if (expiredOwnedCloudIds.isNotEmpty()) {
            chatRepo.deleteExpiredMessagesFromCloud(expiredOwnedCloudIds)
        }
    }

    fun setEphemeralTimer(targetId: String, durationSeconds: Int) {
        val safeSec = durationSeconds.coerceAtLeast(0)
        if (safeSec == 0) {
            _ephemeralTimersByTarget.value = _ephemeralTimersByTarget.value - targetId
        } else {
            _ephemeralTimersByTarget.value = _ephemeralTimersByTarget.value + (targetId to safeSec)
        }
    }

    fun openViewOnceMessage(targetId: String, messageId: String): Message? {
        val currentUid = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
        val list = chatRepo.messagesMutableFlow().value[targetId] ?: return null
        val targetMsg = list.find { it.id == messageId } ?: return null
        if (!targetMsg.isViewOnce) return targetMsg
        val isSender = targetMsg.sender.id == currentUid || targetMsg.sender.id == "user_me"
        if (currentUid in targetMsg.viewedByIds && !isSender) {
            return null
        }
        chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (
            targetId to list.map { m ->
                if (m.id == messageId) {
                    val nextViewers = (m.viewedByIds + currentUid).distinct()
                    m.copy(
                        viewedByIds = nextViewers,
                        imageUrl = if (isSender) m.imageUrl else null,
                        text = if (isSender) m.text else "Photo vue unique déjà ouverte"
                    )
                } else m
            }
        )
        chatRepo.syncViewOnceOpened(
            targetId = targetId,
            messageId = messageId,
            viewerUid = currentUid,
            redactMedia = !isSender
        )
        return targetMsg
    }

    fun markTargetMessagesRead(targetId: String) {
        val currentUid = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
        val currentList = chatRepo.messagesMutableFlow().value[targetId] ?: return
        val updatedMessageIds = mutableListOf<String>()
        val updated = currentList.map { msg ->
            if (msg.sender.id != currentUid && msg.sender.id != "user_me" && currentUid !in msg.readByIds) {
                updatedMessageIds.add(msg.id)
                msg.copy(readByIds = (msg.readByIds + currentUid).distinct())
            } else msg
        }
        if (updatedMessageIds.isNotEmpty()) {
            chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (targetId to updated)
            chatRepo.syncMessagesRead(targetId, updatedMessageIds, currentUid)
        }
        refreshHomeWidget()
    }

    fun forwardMessageToTargets(message: Message, targetIds: List<String>) {
        if (targetIds.isEmpty()) return
        val forwardLabel = "Transféré de ${message.sender.name}"
        val nowTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        targetIds.distinct().forEach { destId ->
            val copyMsg = Message(
                id = "msg_${UUID.randomUUID().toString().take(8)}",
                channelId = destId,
                sender = chatRepo.currentUser.value,
                text = message.text.ifBlank { message.attachmentName ?: "" },
                timestamp = nowTime,
                imageUrl = if (message.isViewOnce) null else message.imageUrl,
                audioUrl = message.audioUrl,
                audioDurationSec = message.audioDurationSec,
                waveformAmplitudes = message.waveformAmplitudes,
                attachmentName = message.attachmentName,
                attachmentType = message.attachmentType,
                attachmentSizeLabel = message.attachmentSizeLabel,
                stickerId = message.stickerId,
                forwardedFrom = forwardLabel,
                readByIds = listOf(chatRepo.currentUser.value.id)
            )
            val prev = chatRepo.messagesMutableFlow().value[destId] ?: emptyList()
            chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (destId to (prev + copyMsg))
            if (destId.startsWith("dm_") || destId.startsWith("grp_")) {
                chatRepo.directConversationsMutableFlow().value = chatRepo.directConversationsMutableFlow().value.map { conv ->
                    if (conv.id == destId) {
                        conv.copy(
                            lastMessage = "↪ ${copyMsg.text.ifBlank { "Média transféré" }}",
                            lastMessageTime = nowTime
                        )
                    } else conv
                }
            }
            chatRepo.syncForwardedMessage(copyMsg, destId)
        }
        chatRepo.grantXp(10, "Message transféré")
        refreshHomeWidget()
    }

    // --- Community Orbs Tipping & Treasury ---
    fun tipOrbsOnMessage(targetId: String, messageId: String, amountOrbs: Int): Boolean {
        if (amountOrbs <= 0) return false
        val me = chatRepo.currentUser.value
        if (me.orbsBalance < amountOrbs) return false
        val list = chatRepo.messagesMutableFlow().value[targetId] ?: return false
        val targetMsg = list.find { it.id == messageId } ?: return false

        chatRepo.updateOrbsBalance(me.orbsBalance - amountOrbs)
        chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (
            targetId to list.map { m ->
                if (m.id == messageId) m.copy(totalOrbsTipped = m.totalOrbsTipped + amountOrbs) else m
            }
        )
        chatRepo.syncMessageOrbsTip(targetId, messageId, amountOrbs)
        chatRepo.notifyCommunityEvent(
            title = "Don d'Orbs envoyé",
            body = "Vous avez offert $amountOrbs Orbs à ${targetMsg.sender.name} sur un message utile !",
            sourceLabel = "Banque Orbs"
        )
        chatRepo.grantXp(15, "Soutien Orbs")
        return true
    }

    fun donateOrbsToServerTreasury(serverId: String, amountOrbs: Int): Boolean {
        if (amountOrbs <= 0) return false
        val me = chatRepo.currentUser.value
        if (me.orbsBalance < amountOrbs) return false
        val servers = chatRepo.serversMutableFlow().value
        val srv = servers.find { it.id == serverId } ?: return false

        chatRepo.updateOrbsBalance(me.orbsBalance - amountOrbs)
        val bonusBoosts = amountOrbs / 100
        val newBoostCount = srv.boostCount + bonusBoosts
        val newBoostLevel = (newBoostCount / 2).coerceIn(0, 3)
        val log = AuditLogEntry(
            id = "log_${UUID.randomUUID().toString().take(6)}",
            actorName = me.name,
            actionDescription = "a contribué $amountOrbs Orbs à la banque du serveur",
            targetName = srv.name,
            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        chatRepo.serversMutableFlow().value = servers.map {
            if (it.id == serverId) {
                it.copy(
                    treasuryOrbs = it.treasuryOrbs + amountOrbs,
                    boostCount = newBoostCount,
                    boostLevel = newBoostLevel,
                    auditLogs = listOf(log) + it.auditLogs
                )
            } else it
        }
        chatRepo.notifyCommunityEvent(
            title = "Contribution Banque Orbs",
            body = "+$amountOrbs Orbs ajoutés à la cagnotte de ${srv.name}.",
            sourceLabel = srv.name
        )
        chatRepo.grantXp(25, "Don serveur Orbs")
        return true
    }

    // --- Interactive Server Rules Onboarding ---
    fun setRequireRulesAcceptance(serverId: String, required: Boolean) {
        val srvTarget = chatRepo.serversMutableFlow().value.find { it.id == serverId } ?: return
        val actorUid = chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id
        if (!CommunityPermissions.hasPermission(srvTarget, actorUid, RolePermission.MANAGE_COMMUNITY)) {
            return
        }
        chatRepo.serversMutableFlow().value = chatRepo.serversMutableFlow().value.map { srv ->
            if (srv.id == serverId) {
                srv.copy(
                    requireRulesAcceptance = required,
                    customization = srv.customization.copy(requireRulesAcceptance = required)
                )
            } else srv
        }
    }

    fun acceptServerRules(serverId: String) {
        val uid = chatRepo.currentUser.value.id
        chatRepo.serversMutableFlow().value = chatRepo.serversMutableFlow().value.map { srv ->
            if (srv.id == serverId) {
                srv.copy(acceptedRulesMemberIds = (srv.acceptedRulesMemberIds + uid).distinct())
            } else srv
        }
        chatRepo.grantXp(20, "Règlement accepté")
    }

    // --- Forum / Entraide Channels ---
    private fun seedDefaultForumPosts() {
        val sampleChannel = chatRepo.serversMutableFlow().value
            .flatMap { it.channels }
            .firstOrNull { it.type == ChannelType.FORUM }
            ?: return
        val demoPost = ForumPost(
            id = "post_demo_1",
            channelId = sampleChannel.id,
            serverId = sampleChannel.serverId,
            title = "Comment configurer les notes vocales & le verrou biométrique ?",
            content = "Activez la biométrie dans Paramètres > Confidentialité et maintenez le bouton micro pour verrouiller vos notes vocales !",
            authorId = MockData.friendEmma.id,
            authorName = MockData.friendEmma.name,
            authorAvatarColor = MockData.friendEmma.avatarColor,
            status = ForumPostStatus.RESOLVED,
            upvotedByIds = listOf(MockData.friendThomas.id, MockData.friendLucas.id),
            replies = listOf(
                ForumReply(
                    id = "frep_1",
                    authorId = MockData.friendThomas.id,
                    authorName = MockData.friendThomas.name,
                    authorAvatarColor = MockData.friendThomas.avatarColor,
                    content = "Testé en 1,5x, la qualité audio est super nette !",
                    timestamp = "14:20"
                )
            ),
            tags = listOf("Astuce", "Audio"),
            timestamp = "Aujourd'hui"
        )
        _forumPostsByChannel.value = mapOf(sampleChannel.id to listOf(demoPost))
    }

    fun createForumPost(
        channelId: String,
        serverId: String,
        title: String,
        content: String,
        tags: List<String>,
        status: ForumPostStatus = ForumPostStatus.OPEN
    ): ForumPost? {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isBlank() || cleanContent.isBlank()) return null
        val user = chatRepo.currentUser.value
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val post = ForumPost(
            id = "fpost_${UUID.randomUUID().toString().take(8)}",
            channelId = channelId,
            serverId = serverId,
            title = cleanTitle,
            content = cleanContent,
            authorId = user.id,
            authorName = user.name,
            authorAvatarColor = user.avatarColor,
            status = status,
            upvotedByIds = listOf(user.id),
            tags = tags.ifEmpty { listOf("Discussion") },
            timestamp = now
        )
        val existing = _forumPostsByChannel.value[channelId] ?: emptyList()
        _forumPostsByChannel.value = _forumPostsByChannel.value + (channelId to (listOf(post) + existing))
        chatRepo.grantXp(20, "Sujet Forum publié")
        return post
    }

    fun toggleForumPostUpvote(channelId: String, postId: String) {
        val uid = chatRepo.currentUser.value.id
        val list = _forumPostsByChannel.value[channelId] ?: return
        _forumPostsByChannel.value = _forumPostsByChannel.value + (
            channelId to list.map { p ->
                if (p.id == postId) {
                    val hasUpvoted = uid in p.upvotedByIds
                    val nextVotes = if (hasUpvoted) p.upvotedByIds.filterNot { it == uid } else p.upvotedByIds + uid
                    p.copy(upvotedByIds = nextVotes)
                } else p
            }
        )
    }

    fun addForumReply(channelId: String, postId: String, content: String) {
        val clean = content.trim()
        if (clean.isBlank()) return
        val user = chatRepo.currentUser.value
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val reply = ForumReply(
            id = "frep_${UUID.randomUUID().toString().take(8)}",
            authorId = user.id,
            authorName = user.name,
            authorAvatarColor = user.avatarColor,
            content = clean,
            timestamp = now
        )
        val list = _forumPostsByChannel.value[channelId] ?: return
        _forumPostsByChannel.value = _forumPostsByChannel.value + (
            channelId to list.map { p ->
                if (p.id == postId) p.copy(replies = p.replies + reply) else p
            }
        )
        chatRepo.grantXp(10, "Réponse Forum")
    }

    fun updateForumPostStatus(channelId: String, postId: String, status: ForumPostStatus) {
        val list = _forumPostsByChannel.value[channelId] ?: return
        _forumPostsByChannel.value = _forumPostsByChannel.value + (
            channelId to list.map { p -> if (p.id == postId) p.copy(status = status) else p }
        )
    }

    // --- 1-to-1 / Group Direct Calls + PiP + History ---
    fun startDirectCall(
        peerUser: User,
        isVideo: Boolean,
        isGroupCall: Boolean = false,
        groupName: String? = null
    ) {
        val callId = "call_${UUID.randomUUID().toString().take(8)}"
        _activeDirectCall.value = DirectCallSession(
            id = callId,
            peerUser = peerUser,
            isGroupCall = isGroupCall,
            groupName = groupName,
            isVideo = isVideo,
            status = DirectCallStatus.RINGING_OUTGOING,
            isMuted = false,
            isSpeakerOn = true,
            isCameraOn = isVideo,
            isMinimized = false
        )
        // Transition seamlessly to connected state after brief ringtone simulation
        chatRepo.repoScope().launch {
            delay(1800L)
            val current = _activeDirectCall.value
            if (current != null && current.id == callId && current.status == DirectCallStatus.RINGING_OUTGOING) {
                _activeDirectCall.value = current.copy(
                    status = DirectCallStatus.CONNECTED,
                    connectedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun simulateIncomingCall(peerUser: User, isVideo: Boolean) {
        _activeDirectCall.value = DirectCallSession(
            id = "call_in_${UUID.randomUUID().toString().take(6)}",
            peerUser = peerUser,
            isVideo = isVideo,
            status = DirectCallStatus.RINGING_INCOMING,
            isCameraOn = isVideo,
            isMinimized = false
        )
    }

    fun answerDirectCall() {
        val current = _activeDirectCall.value ?: return
        _activeDirectCall.value = current.copy(
            status = DirectCallStatus.CONNECTED,
            connectedAtMs = System.currentTimeMillis()
        )
    }

    fun toggleCallMinimized(minimized: Boolean? = null) {
        val current = _activeDirectCall.value ?: return
        _activeDirectCall.value = current.copy(isMinimized = minimized ?: !current.isMinimized)
    }

    fun toggleCallMute() {
        val current = _activeDirectCall.value ?: return
        _activeDirectCall.value = current.copy(isMuted = !current.isMuted)
    }

    fun toggleCallCamera() {
        val current = _activeDirectCall.value ?: return
        val nextCam = !current.isCameraOn
        _activeDirectCall.value = current.copy(isCameraOn = nextCam, isVideo = current.isVideo || nextCam)
    }

    fun toggleCallSpeaker() {
        val current = _activeDirectCall.value ?: return
        _activeDirectCall.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
    }

    fun endDirectCall() {
        val current = _activeDirectCall.value ?: return
        _activeDirectCall.value = null
        val durationSec = current.connectedAtMs?.let {
            ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(1)
        } ?: 0
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val entry = CallHistoryEntry(
            id = "chist_${UUID.randomUUID().toString().take(6)}",
            peerId = current.peerUser.id,
            peerName = current.groupName ?: current.peerUser.name,
            peerAvatarColor = current.peerUser.avatarColor,
            peerAvatarUrl = current.peerUser.avatarUrl,
            isVideo = current.isVideo,
            isOutgoing = current.status != DirectCallStatus.RINGING_INCOMING,
            isMissed = current.connectedAtMs == null,
            durationSec = durationSec,
            timestamp = now
        )
        _callHistory.value = listOf(entry) + _callHistory.value.take(29)
    }

    // --- Smart Offline Queue & Widget Sync ---
    fun enqueueOfflineMessage(
        targetId: String,
        targetLabel: String,
        text: String,
        imageUrl: String?,
        audioUrl: String?,
        audioDurationSec: Int,
        waveformAmplitudes: List<Int>,
        isViewOnce: Boolean
    ): Message {
        val queuedId = "offline_${UUID.randomUUID().toString().take(8)}"
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val pending = PendingOfflineMessage(
            id = queuedId,
            targetId = targetId,
            targetLabel = targetLabel,
            text = text,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            audioDurationSec = audioDurationSec,
            waveformAmplitudes = waveformAmplitudes,
            isViewOnce = isViewOnce
        )
        _pendingOfflineMessages.value = _pendingOfflineMessages.value + pending
        persistOfflineQueue()

        val displayMsg = Message(
            id = queuedId,
            channelId = targetId,
            sender = chatRepo.currentUser.value,
            text = text,
            timestamp = now,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            audioDurationSec = audioDurationSec,
            waveformAmplitudes = waveformAmplitudes,
            isViewOnce = isViewOnce,
            isOfflineQueued = true
        )
        val prev = chatRepo.messagesMutableFlow().value[targetId] ?: emptyList()
        chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (targetId to (prev + displayMsg))
        refreshHomeWidget()
        return displayMsg
    }

    fun flushOfflineQueue(): Int {
        val pendingList = _pendingOfflineMessages.value
        if (pendingList.isEmpty()) return 0
        if (!isEffectivelyOnline()) return 0

        var sentCount = 0
        val remaining = mutableListOf<PendingOfflineMessage>()
        pendingList.forEach { item ->
            val sent = chatRepo.sendMessageInternal(
                targetId = item.targetId,
                text = item.text,
                imageUrl = item.imageUrl,
                audioUrl = item.audioUrl,
                audioDurationSec = item.audioDurationSec,
                waveformAmplitudes = item.waveformAmplitudes,
                isViewOnce = item.isViewOnce,
                bypassOfflineQueue = true
            )
            if (sent) {
                // Remove optimistic offline placeholder after confirmed dispatch
                val currMsgs = chatRepo.messagesMutableFlow().value[item.targetId].orEmpty()
                chatRepo.messagesMutableFlow().value = chatRepo.messagesMutableFlow().value + (
                    item.targetId to currMsgs.filterNot { it.id == item.id }
                )
                sentCount++
            } else {
                remaining.add(item)
            }
        }
        _pendingOfflineMessages.value = remaining
        persistOfflineQueue()

        if (sentCount > 0) {
            chatRepo.notifyCommunityEvent(
                title = "Connexion rétablie",
                body = "$sentCount message(s) en attente ont été envoyés automatiquement.",
                sourceLabel = "Mode hors-ligne"
            )
        }
        refreshHomeWidget()
        return sentCount
    }

    private fun persistOfflineQueue() {
        val ctx = chatRepo.appContextOrNull() ?: return
        try {
            val arr = JSONArray()
            _pendingOfflineMessages.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("targetId", item.targetId)
                    put("targetLabel", item.targetLabel)
                    put("text", item.text)
                    item.imageUrl?.let { put("imageUrl", it) }
                    item.audioUrl?.let { put("audioUrl", it) }
                    put("audioDurationSec", item.audioDurationSec)
                    if (item.waveformAmplitudes.isNotEmpty()) {
                        val wf = JSONArray()
                        item.waveformAmplitudes.forEach { wf.put(it) }
                        put("waveformAmplitudes", wf)
                    }
                    put("isViewOnce", item.isViewOnce)
                    put("queuedAtMs", item.queuedAtMs)
                }
                arr.put(obj)
            }
            ctx.getSharedPreferences(PREFS_ADV, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OFFLINE_QUEUE, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun loadOfflineQueue(context: Context?) {
        val ctx = context ?: return
        try {
            val raw = ctx.getSharedPreferences(PREFS_ADV, Context.MODE_PRIVATE)
                .getString(KEY_OFFLINE_QUEUE, null) ?: return
            val arr = JSONArray(raw)
            val restored = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val wfArr = obj.optJSONArray("waveformAmplitudes")
                    val waveform = buildList {
                        if (wfArr != null) {
                            for (j in 0 until wfArr.length()) {
                                add(wfArr.optInt(j, 30))
                            }
                        }
                    }
                    add(
                        PendingOfflineMessage(
                            id = obj.optString("id"),
                            targetId = obj.optString("targetId"),
                            targetLabel = obj.optString("targetLabel"),
                            text = obj.optString("text"),
                            imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
                            audioUrl = obj.optString("audioUrl").takeIf { it.isNotBlank() },
                            audioDurationSec = obj.optInt("audioDurationSec", 0),
                            waveformAmplitudes = waveform,
                            isViewOnce = obj.optBoolean("isViewOnce", false),
                            queuedAtMs = obj.optLong("queuedAtMs", System.currentTimeMillis())
                        )
                    )
                }
            }
            _pendingOfflineMessages.value = restored
        } catch (_: Exception) {
        }
    }

    fun refreshHomeWidget(activeStoriesCount: Int = 2) {
        val ctx = chatRepo.appContextOrNull() ?: return
        val unreadDm = chatRepo.directConversations.value.sumOf { it.unreadCount }
        val activeVoice = chatRepo.connectedVoiceChannel.value?.name ?: "Aucun salon vocal actif"
        val lastCh = chatRepo.selectedChannelId.value.let { cid ->
            chatRepo.servers.value.flatMap { it.channels }.find { it.id == cid }?.let { "#${it.name}" }
        } ?: "#général"
        RavenHomeWidgetProvider.saveSnapshotAndRefresh(
            context = ctx,
            unreadDmCount = unreadDm,
            activeStoriesCount = activeStoriesCount,
            activeVoiceLabel = activeVoice,
            lastChannelLabel = lastCh,
            offlineQueuedCount = _pendingOfflineMessages.value.size
        )
    }
}
