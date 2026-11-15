package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ChatRepository
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.model.Message
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.AudioPlayerHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.launch

@Composable
fun RavenApp(repository: ChatRepository) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val palette = ravenPalette()
    val sharedAudioPlayer = remember { AudioPlayerHelper() }

    val currentUser by repository.currentUser.collectAsState()
    val servers by repository.servers.collectAsState()
    val selectedServerId by repository.selectedServerId.collectAsState()
    val selectedChannelId by repository.selectedChannelId.collectAsState()
    val messages by repository.messages.collectAsState()
    val directConversations by repository.directConversations.collectAsState()
    val selectedDmId by repository.selectedDmId.collectAsState()
    val connectedVoiceChannel by repository.connectedVoiceChannel.collectAsState()
    val isUserMuted by repository.isUserMuted.collectAsState()
    val isUserDeafened by repository.isUserDeafened.collectAsState()
    val replyingTo by repository.replyingToMessage.collectAsState()
    val searchQuery by repository.searchQuery.collectAsState()
    val isPinnedFilter by repository.pinnedMessagesOnly.collectAsState()
    val chatBackgrounds by repository.chatBackgrounds.collectAsState()
    val threads by repository.threads.collectAsState()
    val notifications by repository.notifications.collectAsState()
    val appSettings by repository.appSettings.collectAsState()
    val serverFolders by repository.serverFolders.collectAsState()
    val friendRequests by repository.friendRequests.collectAsState()
    val blockedUsers by repository.blockedUsers.collectAsState()
    val typingUsersByTarget by repository.typingUsersByTarget.collectAsState()
    val isCloudSyncing by repository.isCloudSyncing.collectAsState()
    val lastCloudSyncTime by repository.lastCloudSyncTime.collectAsState()
    val quests by repository.quests.collectAsState()
    val dailyStreak by repository.dailyStreak.collectAsState()
    val streakClaimedToday by repository.streakClaimedToday.collectAsState()
    val privateUserNotes by repository.privateUserNotes.collectAsState()
    val slowmodeCooldowns by repository.slowmodeCooldownUntilByChannel.collectAsState()
    val lastSendBlockedReason by repository.lastSendBlockedReason.collectAsState()
    val isFirebaseSignedIn by repository.isFirebaseSignedIn.collectAsState()
    val firebaseUserEmail by repository.firebaseUserEmail.collectAsState()
    val pendingLocalNotification by repository.pendingLocalNotification.collectAsState()
    val discoverServers by repository.discoverServers.collectAsState()
    val jukeboxState by repository.jukeboxState.collectAsState()
    val participantVolumes by repository.participantVolumes.collectAsState()
    val whiteboardStrokes by repository.whiteboardStrokes.collectAsState()
    val draftsByTarget by repository.draftsByTarget.collectAsState()
    val scheduledMessages by repository.scheduledMessages.collectAsState()
    val rtcSessionState by repository.rtcSessionState.collectAsState()
    val remoteVideoTracks by repository.remoteVideoTracks.collectAsState()
    val jukeboxVolume by repository.jukeboxVolume.collectAsState()

    val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

    // Navigation and drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isMemberListOpen by remember { mutableStateOf(false) }
    var isViewingFullVoiceRoom by remember { mutableStateOf(false) }
    var selectedUserProfile by remember { mutableStateOf<com.example.model.User?>(null) }
    var showCreateServerDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showJoinServerDialog by remember { mutableStateOf(false) }
    var showExploreServersSheet by remember { mutableStateOf(false) }
    var showLeaderboardSheet by remember { mutableStateOf(false) }
    var showServerShopSheet by remember { mutableStateOf(false) }
    var showJukeboxSheet by remember { mutableStateOf(false) }
    var showMixerSheet by remember { mutableStateOf(false) }
    var whiteboardTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showCreateChannelCategory by remember { mutableStateOf<String?>(null) }
    var showOrbsShopDialog by remember { mutableStateOf(false) }
    var showServerInviteDialog by remember { mutableStateOf(false) }
    var showServerEventsSheet by remember { mutableStateOf(false) }
    var showServerSettingsSheet by remember { mutableStateOf(false) }
    var showInboxSheet by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showGlobalSearchSheet by remember { mutableStateOf(false) }
    var sharedMediaTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeThreadParent by remember { mutableStateOf<Message?>(null) }

    // Permissions launchers
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestAudioPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestNotificationPermission = {
        NotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannel(context)
    }

    LaunchedEffect(pendingLocalNotification, appSettings.localNotificationsEnabled) {
        val notif = pendingLocalNotification ?: return@LaunchedEffect
        if (appSettings.localNotificationsEnabled) {
            NotificationHelper.showNotification(context, notif.title, notif.body)
        }
        repository.consumePendingLocalNotification()
    }

    // Active Server & Channel
    val activeServer = servers.find { it.id == selectedServerId } ?: servers.first()
    val activeChannel = activeServer.channels.find { it.id == selectedChannelId }
        ?: activeServer.channels.firstOrNull { it.type == ChannelType.TEXT }
        ?: activeServer.channels.firstOrNull()
        ?: Channel("c_def", activeServer.id, "général", ChannelType.TEXT)

    BackHandler(enabled = isMemberListOpen || isViewingFullVoiceRoom || drawerState.isOpen || (activeServer.isDmTab && selectedDmId != null)) {
        when {
            isMemberListOpen -> isMemberListOpen = false
            isViewingFullVoiceRoom -> isViewingFullVoiceRoom = false
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            activeServer.isDmTab && selectedDmId != null -> repository.selectDm("")
        }
    }

    @Composable
    fun SidebarPanel(
        modifier: Modifier = Modifier,
        closeDrawerAfterSelect: Boolean = true
    ) {
        Row(modifier = modifier.fillMaxHeight()) {
            ServerRail(
                servers = servers,
                selectedServerId = selectedServerId,
                serverFolders = serverFolders,
                onSelectServer = { serverId ->
                    repository.selectServer(serverId)
                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                },
                onToggleFolder = { folderId -> repository.toggleServerFolderExpanded(folderId) },
                onCreateFolderClick = { showCreateFolderDialog = true },
                onCreateServerClick = { showCreateServerDialog = true },
                onJoinServerClick = { showJoinServerDialog = true },
                onExploreServersClick = { showExploreServersSheet = true },
                onOpenOrbsShop = {
                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    showOrbsShopDialog = true
                }
            )

            if (!activeServer.isDmTab) {
                ChannelListDrawer(
                    server = activeServer,
                    selectedChannelId = selectedChannelId,
                    currentUser = currentUser,
                    isUserMuted = isUserMuted,
                    isUserDeafened = isUserDeafened,
                    onSelectChannel = { ch ->
                        repository.selectChannel(ch)
                        if (ch.type == ChannelType.VOICE || ch.type == ChannelType.STAGE) {
                            requestAudioPermission()
                            isViewingFullVoiceRoom = true
                        }
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    },
                    onCreateChannelClick = { category -> showCreateChannelCategory = category },
                    onInvitePeople = { showServerInviteDialog = true },
                    onOpenEvents = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showServerEventsSheet = true
                    },
                    onOpenGlobalSearch = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showGlobalSearchSheet = true
                    },
                    onOpenLeaderboard = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showLeaderboardSheet = true
                    },
                    onOpenServerShop = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showServerShopSheet = true
                    },
                    onOpenServerSettings = { showServerSettingsSheet = true },
                    onOpenNotifications = { showInboxSheet = true },
                    onUserClick = { selectedUserProfile = currentUser },
                    onOpenAppSettings = { showAppSettingsDialog = true },
                    onToggleMute = {
                        requestAudioPermission()
                        repository.toggleMute()
                    },
                    onToggleDeafen = { repository.toggleDeafen() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.sidebarBg)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Messages Privés",
                            color = palette.textPrimary,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Glissez ou cliquez sur un contact pour continuer votre discussion.",
                            color = palette.textMuted,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = {
                                if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                showGlobalSearchSheet = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recherche globale", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        UserBottomBar(
                            user = currentUser,
                            isMuted = isUserMuted,
                            isDeafened = isUserDeafened,
                            onUserClick = { selectedUserProfile = currentUser },
                            onOpenAppSettings = { showAppSettingsDialog = true },
                            onToggleMute = {
                                requestAudioPermission()
                                repository.toggleMute()
                            },
                            onToggleDeafen = { repository.toggleDeafen() }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MainContentPane(
        showDrawerButton: Boolean,
        modifier: Modifier = Modifier
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isViewingFullVoiceRoom && connectedVoiceChannel != null -> {
                            val activeVoice = connectedVoiceChannel!!
                            if (activeVoice.type == ChannelType.STAGE) {
                                StageRoomView(
                                    channel = activeVoice,
                                    serverName = activeServer.name,
                                    serverMembers = activeServer.members,
                                    currentUser = currentUser,
                                    isUserMuted = isUserMuted,
                                    isUserDeafened = isUserDeafened,
                                    onRaiseHand = { repository.toggleRaiseHand(activeVoice.id) },
                                    onPromoteSpeaker = { uid -> repository.promoteToSpeaker(activeVoice.id, uid) },
                                    onMoveToAudience = { uid -> repository.moveToAudience(activeVoice.id, uid) },
                                    onToggleMute = {
                                        requestAudioPermission()
                                        repository.toggleMute()
                                    },
                                    onToggleDeafen = { repository.toggleDeafen() },
                                    onLeaveVoice = {
                                        repository.leaveVoiceChannel()
                                        isViewingFullVoiceRoom = false
                                    },
                                    onMinimize = { isViewingFullVoiceRoom = false }
                                )
                            } else {
                                VoiceRoomView(
                                    channel = activeVoice,
                                    serverName = activeServer.name,
                                    currentUser = currentUser,
                                    isUserMuted = isUserMuted,
                                    isUserDeafened = isUserDeafened,
                                    jukeboxState = jukeboxState,
                                    rtcSessionState = rtcSessionState,
                                    remoteVideoTracks = remoteVideoTracks,
                                    eglContext = repository.getWebRtcEglContext(),
                                    onCameraToggle = { enabled, front ->
                                        repository.setVoiceCameraEnabled(enabled, front)
                                    },
                                    onScreenShareToggle = { enabled ->
                                        repository.setVoiceScreenSharing(enabled)
                                    },
                                    onToggleMute = {
                                        requestAudioPermission()
                                        repository.toggleMute()
                                    },
                                    onToggleDeafen = { repository.toggleDeafen() },
                                    onLeaveVoice = {
                                        repository.leaveVoiceChannel()
                                        isViewingFullVoiceRoom = false
                                    },
                                    onMinimize = { isViewingFullVoiceRoom = false },
                                    onInviteClick = { showServerInviteDialog = true },
                                    onOpenJukebox = { showJukeboxSheet = true },
                                    onOpenMixer = { showMixerSheet = true },
                                    onOpenWhiteboard = { whiteboardTarget = activeChannel.id to "#${activeChannel.name}" },
                                    onRewardXpAndOrbs = { xp, orbs, reason ->
                                        repository.grantXp(xp, reason)
                                        repository.updateOrbsBalance(currentUser.orbsBalance + orbs)
                                    }
                                )
                            }
                        }

                        activeServer.isDmTab -> {
                            DirectMessagesView(
                                conversations = directConversations,
                                allFriends = com.example.data.MockData.allFriends,
                                selectedDmId = selectedDmId,
                                messages = messages,
                                currentUser = currentUser,
                                onSelectDm = { repository.selectDm(it) },
                                onCloseDm = { repository.selectDm("") },
                                onSendMessage = { dmId, text, imgUrl, audUrl, audDur ->
                                    repository.sendMessage(dmId, text, imgUrl, audUrl, audDur)
                                },
                                onToggleReaction = { dmId, msgId, emoji ->
                                    repository.toggleReaction(dmId, msgId, emoji)
                                },
                                onAddFriend = { tag -> repository.addFriend(tag) },
                                onStartCall = { friend ->
                                    requestAudioPermission()
                                    val dmVoice = Channel(
                                        id = "voice_${friend.id}",
                                        serverId = "dm_tab",
                                        name = "Appel avec ${friend.name}",
                                        type = ChannelType.VOICE,
                                        activeVoiceUsers = listOf(friend)
                                    )
                                    repository.joinVoiceChannel(dmVoice)
                                    isViewingFullVoiceRoom = true
                                },
                                onStartGroupCall = { groupConv ->
                                    requestAudioPermission()
                                    val grpVoice = Channel(
                                        id = "voice_${groupConv.id}",
                                        serverId = "dm_tab",
                                        name = groupConv.groupName ?: "Appel de groupe",
                                        type = ChannelType.VOICE,
                                        activeVoiceUsers = groupConv.groupMembers
                                    )
                                    repository.joinVoiceChannel(grpVoice)
                                    isViewingFullVoiceRoom = true
                                },
                                onToggleDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                unreadNotificationsCount = unreadNotificationsCount,
                                onOpenInbox = { showInboxSheet = true },
                                chatThemes = chatBackgrounds,
                                onApplyChatTheme = { dmId, theme ->
                                    repository.setChatBackground(dmId, theme)
                                },
                                friendRequests = friendRequests,
                                blockedUsers = blockedUsers,
                                typingUsersByTarget = typingUsersByTarget,
                                unlockedStickers = currentUser.unlockedStickers,
                                orbsBalance = currentUser.orbsBalance,
                                onCreateGroup = { groupName, chosenFriends ->
                                    repository.createGroupConversation(groupName, chosenFriends)
                                },
                                onAcceptFriendRequest = { reqId -> repository.acceptFriendRequest(reqId) },
                                onDeclineOrCancelFriendRequest = { reqId -> repository.declineOrCancelFriendRequest(reqId) },
                                onBlockUser = { user -> repository.blockUser(user) },
                                onUnblockUser = { uid -> repository.unblockUser(uid) },
                                onSendSticker = { dmId, stkId -> repository.sendSticker(dmId, stkId) },
                                onUnlockAndSendSticker = { dmId, stkId, price ->
                                    val unlocked = repository.unlockSticker(stkId, price)
                                    if (unlocked) repository.sendSticker(dmId, stkId)
                                    unlocked
                                },
                                onSendFileAttachment = { dmId, name, type, size ->
                                    repository.sendFileAttachment(dmId, name, type, size)
                                },
                                onOpenGlobalSearch = { showGlobalSearchSheet = true },
                                onOpenSharedMedia = { dmId, title -> sharedMediaTarget = dmId to title },
                                showNavigationDrawerButton = showDrawerButton
                            )
                        }

                        else -> {
                            ChatView(
                                channel = activeChannel,
                                messages = messages[activeChannel.id] ?: emptyList(),
                                replyingTo = replyingTo,
                                searchQuery = searchQuery,
                                isPinnedFilter = isPinnedFilter,
                                members = activeServer.members,
                                serverCustomRoles = activeServer.customRoles,
                                onSendMessage = { text, imgUrl, audUrl, audDur ->
                                    repository.sendMessage(activeChannel.id, text, imgUrl, audUrl, audDur)
                                },
                                onToggleReaction = { msgId, emoji ->
                                    repository.toggleReaction(activeChannel.id, msgId, emoji)
                                },
                                onReplyClick = { repository.setReplyingTo(it) },
                                onCancelReply = { repository.setReplyingTo(null) },
                                onPinMessage = { repository.togglePinMessage(activeChannel.id, it) },
                                onDeleteMessage = { repository.deleteMessage(activeChannel.id, it) },
                                onEditMessage = { msgId, newText ->
                                    repository.editMessage(activeChannel.id, msgId, newText)
                                },
                                onOpenThread = { parentMsg -> activeThreadParent = parentMsg },
                                onTogglePinnedFilter = { repository.togglePinnedFilter() },
                                onSearchQueryChange = { repository.setSearchQuery(it) },
                                onToggleDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onToggleMemberList = { isMemberListOpen = !isMemberListOpen },
                                unreadNotificationsCount = unreadNotificationsCount,
                                onOpenInbox = { showInboxSheet = true },
                                chatTheme = chatBackgrounds[activeChannel.id],
                                onApplyChatTheme = { theme ->
                                    repository.setChatBackground(activeChannel.id, theme)
                                },
                                onCreatePoll = { question, options, allowsMultiple ->
                                    repository.createPoll(activeChannel.id, question, options, allowsMultiple)
                                },
                                onVotePoll = { messageId, optionId ->
                                    repository.votePoll(activeChannel.id, messageId, optionId)
                                },
                                unlockedStickers = currentUser.unlockedStickers,
                                orbsBalance = currentUser.orbsBalance,
                                typingUsers = typingUsersByTarget[activeChannel.id] ?: emptyList(),
                                onExecuteSlashCommand = { raw ->
                                    repository.executeSlashCommand(activeChannel.id, raw)
                                },
                                onSendSticker = { stkId ->
                                    repository.sendSticker(activeChannel.id, stkId)
                                },
                                onUnlockAndSendSticker = { stkId, price ->
                                    val ok = repository.unlockSticker(stkId, price)
                                    if (ok) repository.sendSticker(activeChannel.id, stkId)
                                    ok
                                },
                                onSendFileAttachment = { name, type, size ->
                                    repository.sendFileAttachment(activeChannel.id, name, type, size)
                                },
                                onOpenGlobalSearch = { showGlobalSearchSheet = true },
                                onOpenSharedMedia = {
                                    sharedMediaTarget = activeChannel.id to "${activeServer.name} • #${activeChannel.name}"
                                },
                                showNavigationDrawerButton = showDrawerButton,
                                slowmodeCooldownUntilMs = slowmodeCooldowns[activeChannel.id] ?: 0L,
                                sendBlockedReason = lastSendBlockedReason,
                                onDismissBlockedReason = { repository.clearSendBlockedReason() },
                                chatDisplayStyle = appSettings.chatDisplayStyle,
                                glassmorphismEnabled = appSettings.glassmorphismEnabled,
                                initialDraft = draftsByTarget[activeChannel.id].orEmpty(),
                                onDraftChange = { draft -> repository.updateDraft(activeChannel.id, draft) },
                                onTyping = { repository.broadcastTyping(activeChannel.id) },
                                scheduledMessagesForChannel = scheduledMessages.filter { it.targetId == activeChannel.id },
                                onScheduleMessage = { text, delayMs, delayLabel ->
                                    requestNotificationPermission()
                                    repository.scheduleMessage(activeChannel.id, "#${activeChannel.name}", text, delayMs, delayLabel)
                                },
                                onCancelScheduledMessage = { schedId -> repository.cancelScheduledMessage(schedId) },
                                onSendScheduledNow = { schedId -> repository.sendScheduledMessageNow(schedId) },
                                onScheduleReminder = { msg, delayMs, delayLabel ->
                                    requestNotificationPermission()
                                    repository.scheduleMessageReminder(msg, delayMs, delayLabel)
                                },
                                onOpenWhiteboard = { whiteboardTarget = activeChannel.id to "#${activeChannel.name}" },
                                currentUserId = currentUser.id,
                                currentUserName = currentUser.name
                            )
                        }
                    }
                }

                if (connectedVoiceChannel != null && !isViewingFullVoiceRoom) {
                    FloatingVoiceBar(
                        channel = connectedVoiceChannel!!,
                        isMuted = isUserMuted,
                        onBarClick = { isViewingFullVoiceRoom = true },
                        onToggleMute = {
                            requestAudioPermission()
                            repository.toggleMute()
                        },
                        onDisconnect = { repository.leaveVoiceChannel() }
                    )
                }
            }

            AnimatedVisibility(
                visible = isMemberListOpen && !activeServer.isDmTab && !isViewingFullVoiceRoom,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    MemberListDrawer(
                        members = activeServer.members,
                        customRoles = activeServer.customRoles,
                        onClose = { isMemberListOpen = false },
                        onMemberClick = { member ->
                            isMemberListOpen = false
                            selectedUserProfile = member
                        }
                    )
                }
            }
        }
    }

    // Adaptive Layout using BoxWithConstraints (3 columns on wide screens/tablets, drawer on phones)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            ) {
                SidebarPanel(
                    modifier = Modifier.width(332.dp),
                    closeDrawerAfterSelect = false
                )
                VerticalDivider(color = palette.border, thickness = 1.dp)
                MainContentPane(
                    showDrawerButton = false,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !isViewingFullVoiceRoom,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = palette.primaryBg,
                        drawerContentColor = palette.textSecondary,
                        modifier = Modifier.width(340.dp)
                    ) {
                        SidebarPanel(
                            modifier = Modifier.fillMaxSize(),
                            closeDrawerAfterSelect = true
                        )
                    }
                }
            ) {
                MainContentPane(
                    showDrawerButton = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Global Search Sheet
    if (showGlobalSearchSheet) {
        GlobalSearchSheet(
            activeServerName = activeServer.name,
            isDmTab = activeServer.isDmTab,
            onSearch = { query, filter, currentServerOnly ->
                repository.searchAllMessages(
                    serverId = if (currentServerOnly) activeServer.id else null,
                    query = query,
                    filter = filter
                )
            },
            onNavigateToResult = { res ->
                if (res.isDm) {
                    repository.selectServer("dm_tab")
                    repository.selectDm(res.targetId)
                } else {
                    val targetServer = servers.find { srv -> srv.channels.any { it.id == res.targetId } }
                    val targetChannel = targetServer?.channels?.find { it.id == res.targetId }
                    if (targetServer != null && targetChannel != null) {
                        repository.selectServer(targetServer.id)
                        repository.selectChannel(targetChannel)
                    }
                }
            },
            onDismiss = { showGlobalSearchSheet = false }
        )
    }

    // Shared Media & Files Gallery Sheet
    sharedMediaTarget?.let { (targetId, title) ->
        SharedMediaAndFilesSheet(
            title = title,
            messages = messages[targetId].orEmpty(),
            audioPlayer = sharedAudioPlayer,
            onDismiss = { sharedMediaTarget = null }
        )
    }

    // Create Server Modal
    if (showCreateServerDialog) {
        CreateServerDialog(
            onDismiss = { showCreateServerDialog = false },
            onCreateServer = { name, emoji, isPublic, categoryTag ->
                repository.createServer(name, emoji, isPublic, categoryTag)
            }
        )
    }

    // Explore Public Servers Sheet
    if (showExploreServersSheet) {
        ExploreServersSheet(
            listings = discoverServers,
            joinedServerIds = servers.filterNot { it.isDmTab }.map { it.id }.toSet(),
            onRefreshCloud = { repository.refreshPublicServersFromCloud() },
            onJoinServer = { listing ->
                repository.joinPublicServer(listing)
                coroutineScope.launch { drawerState.close() }
            },
            onOpenJoinByCode = { showJoinServerDialog = true },
            onDismiss = { showExploreServersSheet = false }
        )
    }

    // Server Leaderboard Sheet
    if (showLeaderboardSheet && !activeServer.isDmTab) {
        ServerLeaderboardSheet(
            serverName = activeServer.name,
            rankedMembers = repository.getServerLeaderboard(activeServer.id),
            currentUserId = currentUser.id,
            onMemberClick = { member ->
                showLeaderboardSheet = false
                selectedUserProfile = member
            },
            onDismiss = { showLeaderboardSheet = false }
        )
    }

    // Internal Server Orbs Shop Sheet
    if (showServerShopSheet && !activeServer.isDmTab) {
        ServerShopSheet(
            server = activeServer,
            currentUser = currentUser,
            onPurchaseItem = { itemId -> repository.purchaseServerShopItem(activeServer.id, itemId) },
            onCreateShopItem = { title, desc, price, c1, c2 ->
                repository.createServerShopItem(activeServer.id, title, desc, price, c1, c2)
            },
            onDismiss = { showServerShopSheet = false }
        )
    }

    // Collaborative Interactive Whiteboard Sheet
    whiteboardTarget?.let { (targetId, targetLabel) ->
        WhiteboardSheet(
            targetLabel = targetLabel,
            strokes = whiteboardStrokes[targetId].orEmpty(),
            onAddStroke = { stroke -> repository.addWhiteboardStroke(targetId, stroke) },
            onUndo = { repository.undoWhiteboardStroke(targetId) },
            onClear = { repository.clearWhiteboard(targetId) },
            onShareToChat = { repository.shareWhiteboardToChannel(targetId) },
            onDismiss = { whiteboardTarget = null }
        )
    }

    // Voice Collaborative Jukebox Sheet
    if (showJukeboxSheet) {
        VoiceJukeboxSheet(
            jukeboxState = jukeboxState,
            currentUserId = currentUser.id,
            masterVolume = jukeboxVolume,
            onMasterVolumeChange = { vol -> repository.setJukeboxVolume(vol) },
            onTogglePlayPause = { repository.toggleJukeboxPlayPause() },
            onSkipTrack = { repository.skipJukeboxTrack() },
            onVoteSkip = { repository.voteSkipCurrentTrack() },
            onAddTrack = { track -> repository.addTrackToJukebox(track) },
            onDismiss = { showJukeboxSheet = false }
        )
    }

    // Voice Individual Mixer & Audio EQ Sheet
    if (showMixerSheet && connectedVoiceChannel != null) {
        VoiceMixerSheet(
            participants = connectedVoiceChannel!!.activeVoiceUsers,
            currentUserId = currentUser.id,
            volumesByUserId = participantVolumes,
            currentEqPreset = appSettings.audioEqPreset,
            onVolumeChange = { uid, vol -> repository.setParticipantVolume(uid, vol) },
            onSelectEqPreset = { preset -> repository.setAudioEqPreset(preset) },
            onDismiss = { showMixerSheet = false }
        )
    }

    // Join Server By Code Modal
    if (showJoinServerDialog) {
        JoinServerByCodeDialog(
            onJoinCode = { code ->
                val joined = repository.joinServerByInviteCode(code)
                if (joined) {
                    coroutineScope.launch { drawerState.close() }
                }
                joined
            },
            onDismiss = { showJoinServerDialog = false }
        )
    }

    // Server Invite Dialog
    if (showServerInviteDialog) {
        ServerInviteDialog(
            server = activeServer,
            onRegenerateCode = { repository.regenerateInviteCode(activeServer.id) },
            onDismiss = { showServerInviteDialog = false }
        )
    }

    // Server Settings, Boost & Channel Controls Sheet
    if (showServerSettingsSheet) {
        ServerSettingsSheet(
            server = activeServer,
            currentUser = currentUser,
            onRenameServer = { newName -> repository.renameServer(activeServer.id, newName) },
            onCreateRole = { name, colorHex, secondaryColorHex, isAdmin, canKick, canManage ->
                repository.createCustomRole(activeServer.id, name, colorHex, isAdmin, canKick, canManage, secondaryColorHex)
            },
            onAssignMemberRole = { userId, role, customRoleId ->
                repository.assignMemberRole(activeServer.id, userId, role, customRoleId)
            },
            onTimeoutMember = { userId -> repository.timeoutMember(activeServer.id, userId) },
            onKickMember = { userId -> repository.kickMember(activeServer.id, userId) },
            onBoostServer = { repository.boostServer(activeServer.id) },
            onUpdateServerBannerColor = { colorHex -> repository.updateServerBannerColor(activeServer.id, colorHex) },
            onUpdateChannelSettings = { chId, slowSec, readOnly ->
                repository.updateChannelSettings(activeServer.id, chId, slowSec, readOnly)
            },
            onDismiss = { showServerSettingsSheet = false }
        )
    }

    // Thread Sheet
    if (activeThreadParent != null) {
        val parent = activeThreadParent!!
        ThreadSheet(
            parentMessage = parent,
            replies = threads[parent.id] ?: emptyList(),
            audioPlayer = sharedAudioPlayer,
            currentUserId = currentUser.id,
            onSendReply = { text -> repository.sendThreadMessage(parent.channelId, parent.id, text) },
            onDismiss = { activeThreadParent = null }
        )
    }

    // Create Server Folder Modal
    if (showCreateFolderDialog) {
        CreateServerFolderDialog(
            servers = servers,
            onCreateFolder = { name, colorHex, srvIds -> repository.createServerFolder(name, colorHex, srvIds) },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    // Server Events Sheet
    if (showServerEventsSheet && !activeServer.isDmTab) {
        ServerEventsSheet(
            server = activeServer,
            currentUserId = currentUser.id,
            onToggleInterest = { evtId ->
                requestNotificationPermission()
                repository.toggleEventInterest(activeServer.id, evtId)
            },
            onCreateEvent = { title, desc, dateTime, location ->
                repository.createServerEvent(activeServer.id, title, desc, dateTime, location)
            },
            onDismiss = { showServerEventsSheet = false }
        )
    }

    // Notifications Inbox Sheet
    if (showInboxSheet) {
        InboxSheet(
            notifications = notifications,
            onMarkRead = { repository.markNotificationRead(it) },
            onMarkAllRead = { repository.markAllNotificationsRead() },
            onDismiss = { showInboxSheet = false }
        )
    }

    // Full Application Settings Sheet
    if (showAppSettingsDialog) {
        AppSettingsDialog(
            settings = appSettings,
            currentUser = currentUser,
            isCloudSyncing = isCloudSyncing,
            lastCloudSyncTime = lastCloudSyncTime,
            isFirebaseSignedIn = isFirebaseSignedIn,
            firebaseUserEmail = firebaseUserEmail,
            onRequestNotificationPermission = { requestNotificationPermission() },
            onSyncToCloud = { repository.syncFullStateToFirestore() },
            onRestoreFromCloud = { repository.loadFullStateFromFirestore() },
            onUpdateSettings = { transform -> repository.updateAppSettings(transform) },
            onOpenProfile = { selectedUserProfile = currentUser },
            onDismiss = { showAppSettingsDialog = false }
        )
    }

    // Create Channel Modal
    if (showCreateChannelCategory != null) {
        CreateChannelDialog(
            category = showCreateChannelCategory!!,
            onDismiss = { showCreateChannelCategory = null },
            onCreateChannel = { name, type, isPrivate ->
                repository.createChannel(activeServer.id, name, type, showCreateChannelCategory!!, isPrivate)
            }
        )
    }

    // User Profile Bottom Sheet
    selectedUserProfile?.let { targetUser ->
        val isSelf = targetUser.id == currentUser.id || targetUser.id == "user_me"
        UserProfileDialog(
            user = if (isSelf) currentUser else targetUser,
            isCurrentUser = isSelf,
            onDismiss = { selectedUserProfile = null },
            onUpdateStatus = { status, customStatus -> repository.updateUserStatus(status, customStatus) },
            onSaveFullProfile = { name, pronouns, bio, avUrl, avCol, banUrl, banCol, statusMsg, emoji, activity, email, phone ->
                repository.updateUserProfile(
                    name = name,
                    pronouns = pronouns,
                    bio = bio,
                    avatarUrl = avUrl,
                    avatarColor = avCol,
                    bannerUrl = banUrl,
                    bannerColor = banCol,
                    customStatus = statusMsg,
                    customStatusEmoji = emoji,
                    activity = activity,
                    email = email,
                    phone = phone
                )
            },
            onStartDirectMessage = { friend ->
                selectedUserProfile = null
                repository.selectServer("dm_tab")
                val existingDm = directConversations.find { it.friend.id == friend.id }
                if (existingDm != null) {
                    repository.selectDm(existingDm.id)
                } else {
                    repository.addFriend("${friend.name}${friend.tag}")
                }
            },
            onToggleBadge = { badgeId -> repository.toggleUserBadge(badgeId) },
            onOpenOrbsShop = { showOrbsShopDialog = true },
            onOpenAppSettings = if (isSelf) {
                { showAppSettingsDialog = true }
            } else null,
            privateNote = privateUserNotes[targetUser.id].orEmpty(),
            onSavePrivateNote = { note -> repository.setPrivateUserNote(targetUser.id, note) },
            onSaveClanTag = { tag -> repository.updateClanTag(tag) },
            onFirebaseAuthChanged = { repository.syncFirebaseUser() }
        )
    }

    // Discord Orbs Shop, Live Quests & Daily Streak Dialog
    if (showOrbsShopDialog) {
        OrbsShopDialog(
            user = currentUser,
            quests = quests,
            dailyStreak = dailyStreak,
            streakClaimedToday = streakClaimedToday,
            onClaimQuest = { qId -> repository.claimQuestReward(qId) },
            onCompleteQuestAction = { qId -> repository.completeQuestInstantly(qId) },
            onClaimDailyStreak = { repository.claimDailyStreakReward() },
            onDismiss = { showOrbsShopDialog = false },
            onUpdateOrbsBalance = { newBalance -> repository.updateOrbsBalance(newBalance) },
            onEquipDecoration = { decoKey -> repository.equipAvatarDecoration(decoKey) },
            onUnlockBadge = { badgeId -> repository.toggleUserBadge(badgeId) },
            onEquipProfileEffect = { effectKey -> repository.equipProfileEffect(effectKey) }
        )
    }
}
