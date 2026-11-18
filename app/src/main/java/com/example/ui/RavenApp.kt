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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AiAssistantRepository
import com.example.data.BotRepository
import com.example.data.ChatRepository
import com.example.data.CommunityPermissions
import com.example.data.CommunityRepository
import com.example.data.searchCommunities
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.model.Message
import com.example.model.RavenBot
import com.example.model.RolePermission
import com.example.model.Server
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.BotDashboardViewModel
import com.example.ui.viewmodel.CommunityViewModel
import com.example.util.AudioPlayerHelper
import com.example.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RavenApp(
    repository: ChatRepository,
    isViewingFullVoiceRoom: Boolean? = null,
    onViewingFullVoiceRoomChange: ((Boolean) -> Unit)? = null
) {
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
    val localVideoTrack by repository.localVideoTrack.collectAsState()
    val remoteVideoTracks by repository.remoteVideoTracks.collectAsState()
    val voiceError by repository.voiceError.collectAsState()
    val jukeboxVolume by repository.jukeboxVolume.collectAsState()

    val communityRepo = remember(repository) { CommunityRepository(repository) }
    val communityViewModel = remember(communityRepo) { CommunityViewModel(communityRepo) }
    val communityUiState by communityViewModel.uiState.collectAsState()

    val botRepo = remember(repository) { BotRepository(repository) }
    val botViewModel = remember(botRepo) { BotDashboardViewModel(botRepo) }
    val bots by botRepo.bots.collectAsState()
    val botInstallationsByServer by botRepo.installationsByServer.collectAsState()
    val botLogsByBot by botRepo.logsByBot.collectAsState()
    val botUiState by botViewModel.uiState.collectAsState()

    val aiRepo = remember(repository) { AiAssistantRepository(repository) }
    val aiViewModel = remember(aiRepo) { AiAssistantViewModel(aiRepo) }
    val aiUiState by aiViewModel.uiState.collectAsState()

    val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

    // Navigation and drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isMemberListOpen by remember { mutableStateOf(false) }
    var internalViewingVoiceRoom by remember { mutableStateOf(false) }
    val effectiveViewingVoiceRoom = isViewingFullVoiceRoom ?: internalViewingVoiceRoom
    val setViewingVoiceRoom: (Boolean) -> Unit = { visible ->
        internalViewingVoiceRoom = visible
        onViewingFullVoiceRoomChange?.invoke(visible)
    }
    var isViewingCommunityHome by remember { mutableStateOf(false) }
    var showBotDashboardSheet by remember { mutableStateOf(false) }
    var showAiAssistantSheet by remember { mutableStateOf(false) }
    var showCreateBotDialog by remember { mutableStateOf(false) }
    var editingBot by remember { mutableStateOf<RavenBot?>(null) }
    var editingCommunityServer by remember { mutableStateOf<Server?>(null) }
    var showInvitesManagerSheet by remember { mutableStateOf(false) }
    var showRolesMatrixSheet by remember { mutableStateOf(false) }
    var showCategoriesManagerSheet by remember { mutableStateOf(false) }
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

    LaunchedEffect(botRepo) {
        botRepo.attachToChatRepository()
    }

    LaunchedEffect(aiRepo) {
        aiRepo.attachSlashCommandHook()
    }

    LaunchedEffect(selectedServerId, isFirebaseSignedIn) {
        if (selectedServerId != "dm_tab") {
            communityViewModel.observeCommunity(selectedServerId)
        }
        botViewModel.startObserving(selectedServerId)
        aiViewModel.startObserving()
    }

    LaunchedEffect(communityUiState.feedbackMessage, communityUiState.errorMessage) {
        if (communityUiState.feedbackMessage != null || communityUiState.errorMessage != null) {
            delay(3500)
            communityViewModel.clearFeedback()
        }
    }

    LaunchedEffect(botUiState.feedbackMessage, botUiState.errorMessage) {
        if (botUiState.feedbackMessage != null || botUiState.errorMessage != null) {
            delay(3500)
            botViewModel.clearFeedback()
        }
    }

    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            delay(4000)
            repository.clearVoiceError()
        }
    }

    // Active Server & Channel
    val activeServer = servers.find { it.id == selectedServerId } ?: servers.first()
    val activeChannel = activeServer.channels.find { it.id == selectedChannelId }
        ?: activeServer.channels.firstOrNull { it.type == ChannelType.TEXT }
        ?: activeServer.channels.firstOrNull()
        ?: Channel("c_def", activeServer.id, "général", ChannelType.TEXT)

    BackHandler(enabled = isMemberListOpen || effectiveViewingVoiceRoom || isViewingCommunityHome || drawerState.isOpen || (activeServer.isDmTab && selectedDmId != null)) {
        when {
            isMemberListOpen -> isMemberListOpen = false
            effectiveViewingVoiceRoom -> setViewingVoiceRoom(false)
            isViewingCommunityHome -> isViewingCommunityHome = false
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
                    isViewingCommunityHome = false
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
                },
                onOpenBotsDashboard = {
                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    showBotDashboardSheet = true
                },
                onOpenAiAssistant = {
                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    showAiAssistantSheet = true
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
                        isViewingCommunityHome = false
                        if (ch.type == ChannelType.VOICE || ch.type == ChannelType.STAGE) {
                            val joined = repository.joinVoiceChannel(ch)
                            if (joined) {
                                requestAudioPermission()
                                setViewingVoiceRoom(true)
                            }
                        } else {
                            repository.selectChannel(ch)
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
                    onOpenCommunityHome = {
                        isViewingCommunityHome = true
                        setViewingVoiceRoom(false)
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    },
                    onOpenCommunityInvites = { showInvitesManagerSheet = true },
                    onOpenRolesMatrix = { showRolesMatrixSheet = true },
                    onOpenCategoriesManager = { showCategoriesManagerSheet = true },
                    onOpenBotsDashboard = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showBotDashboardSheet = true
                    },
                    onOpenAiAssistant = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showAiAssistantSheet = true
                    },
                    onEditCommunity = { editingCommunityServer = activeServer },
                    onLeaveCommunity = {
                        communityViewModel.leaveCommunity(activeServer.id) {
                            isViewingCommunityHome = false
                        }
                    },
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
                        effectiveViewingVoiceRoom && connectedVoiceChannel != null -> {
                            val activeVoice = connectedVoiceChannel!!
                            val voiceServer = servers.find { it.id == activeVoice.serverId }
                            val canManageVoice = voiceServer != null && !voiceServer.isDmTab &&
                                (CommunityPermissions.hasPermission(voiceServer, currentUser.id, RolePermission.MANAGE_VOICE) ||
                                    CommunityPermissions.hasPermission(voiceServer, currentUser.id, RolePermission.MANAGE_CHANNELS))
                            val canSpeakVoice = voiceServer == null || voiceServer.isDmTab ||
                                CommunityPermissions.hasPermission(voiceServer, currentUser.id, RolePermission.SPEAK_VOICE)
                            val canStreamVideo = voiceServer == null || voiceServer.isDmTab ||
                                CommunityPermissions.hasPermission(voiceServer, currentUser.id, RolePermission.STREAM_VIDEO)
                            val canShareScreen = voiceServer == null || voiceServer.isDmTab ||
                                CommunityPermissions.hasPermission(voiceServer, currentUser.id, RolePermission.SHARE_SCREEN)

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
                                        setViewingVoiceRoom(false)
                                    },
                                    onMinimize = { setViewingVoiceRoom(false) }
                                )
                            } else {
                                VoiceRoomView(
                                    channel = activeVoice,
                                    serverName = voiceServer?.name ?: activeServer.name,
                                    currentUser = currentUser,
                                    isUserMuted = isUserMuted,
                                    isUserDeafened = isUserDeafened,
                                    jukeboxState = jukeboxState,
                                    rtcSessionState = rtcSessionState,
                                    localVideoTrack = localVideoTrack,
                                    remoteVideoTracks = remoteVideoTracks,
                                    eglContext = repository.getWebRtcEglContext(),
                                    serverCustomRoles = voiceServer?.customRoles.orEmpty(),
                                    canManageVoice = canManageVoice,
                                    canSpeak = canSpeakVoice,
                                    canStreamVideo = canStreamVideo,
                                    canShareScreen = canShareScreen,
                                    participantVolumes = participantVolumes,
                                    onCameraToggle = { enabled, front ->
                                        repository.setVoiceCameraEnabled(enabled, front)
                                    },
                                    onScreenShareToggle = { enabled ->
                                        repository.setVoiceScreenSharing(enabled)
                                    },
                                    onStartScreenShareWithIntent = { code, data ->
                                        requestNotificationPermission()
                                        repository.startVoiceScreenShare(code, data)
                                    },
                                    onStopScreenShare = { repository.stopVoiceScreenShare() },
                                    onToggleMute = {
                                        requestAudioPermission()
                                        repository.toggleMute()
                                    },
                                    onToggleDeafen = { repository.toggleDeafen() },
                                    onToggleSpeakerphone = { repository.toggleSpeakerphone() },
                                    onReconnect = { repository.triggerVoiceReconnect() },
                                    onLeaveVoice = {
                                        repository.leaveVoiceChannel()
                                        setViewingVoiceRoom(false)
                                    },
                                    onMinimize = { setViewingVoiceRoom(false) },
                                    onInviteClick = { showServerInviteDialog = true },
                                    onOpenJukebox = { showJukeboxSheet = true },
                                    onOpenMixer = { showMixerSheet = true },
                                    onOpenWhiteboard = { whiteboardTarget = activeChannel.id to "#${activeChannel.name}" },
                                    onParticipantVolumeChange = { uid, vol -> repository.setParticipantVolume(uid, vol) },
                                    onKickParticipant = { uid ->
                                        repository.kickUserFromVoice(activeVoice.serverId, activeVoice.id, uid)
                                    },
                                    onUpdateVoiceSettings = { locked, maxCap, roles, roleIds ->
                                        repository.updateVoiceChannelSettings(
                                            serverId = activeVoice.serverId,
                                            channelId = activeVoice.id,
                                            isLocked = locked,
                                            maxParticipants = maxCap,
                                            allowedRoles = roles,
                                            allowedRoleIds = roleIds
                                        )
                                    },
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
                                    if (repository.joinVoiceChannel(dmVoice)) {
                                        setViewingVoiceRoom(true)
                                    }
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
                                    if (repository.joinVoiceChannel(grpVoice)) {
                                        setViewingVoiceRoom(true)
                                    }
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

                        !activeServer.isDmTab && isViewingCommunityHome -> {
                            CommunityHomeView(
                                server = activeServer,
                                currentUser = currentUser,
                                unreadNotificationsCount = unreadNotificationsCount,
                                showDrawerButton = showDrawerButton,
                                onToggleDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onOpenChannel = { ch ->
                                    isViewingCommunityHome = false
                                    if (ch.type == ChannelType.VOICE || ch.type == ChannelType.STAGE) {
                                        if (repository.joinVoiceChannel(ch)) {
                                            requestAudioPermission()
                                            setViewingVoiceRoom(true)
                                        }
                                    } else {
                                        repository.selectChannel(ch)
                                    }
                                },
                                onEditCommunity = { editingCommunityServer = activeServer },
                                onOpenInvites = { showInvitesManagerSheet = true },
                                onOpenRolesMatrix = { showRolesMatrixSheet = true },
                                onOpenCategories = { showCategoriesManagerSheet = true },
                                onOpenNotifications = { showInboxSheet = true },
                                onLeaveCommunity = {
                                    communityViewModel.leaveCommunity(activeServer.id) {
                                        isViewingCommunityHome = false
                                    }
                                },
                                onPublishAnnouncement = { title, content, isPinned ->
                                    communityViewModel.publishAnnouncement(activeServer.id, title, content, isPinned)
                                },
                                onDeleteAnnouncement = { annId ->
                                    communityViewModel.deleteAnnouncement(activeServer.id, annId)
                                },
                                onCreateEvent = { title, desc, dateTime, location ->
                                    communityViewModel.createEvent(activeServer.id, title, desc, dateTime, location)
                                },
                                onToggleEventInterest = { evtId ->
                                    requestNotificationPermission()
                                    communityViewModel.toggleEventInterest(activeServer.id, evtId)
                                },
                                onTimeoutMember = { uid -> communityViewModel.timeoutMember(activeServer.id, uid) },
                                onKickMember = { uid -> communityViewModel.kickMember(activeServer.id, uid) },
                                onMemberClick = { member -> selectedUserProfile = member }
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
                                onOpenAiAssistant = { showAiAssistantSheet = true },
                                botSlashCommands = remember(activeServer.id, botInstallationsByServer, bots) {
                                    botRepo.getSlashCommandsForServer(activeServer.id)
                                },
                                currentUserId = currentUser.id,
                                currentUserName = currentUser.name
                            )
                        }
                    }
                }

                if (connectedVoiceChannel != null && !effectiveViewingVoiceRoom) {
                    FloatingVoiceBar(
                        channel = connectedVoiceChannel!!,
                        isMuted = isUserMuted,
                        rtcSessionState = rtcSessionState,
                        onBarClick = { setViewingVoiceRoom(true) },
                        onToggleMute = {
                            requestAudioPermission()
                            repository.toggleMute()
                        },
                        onDisconnect = { repository.leaveVoiceChannel() }
                    )
                }
            }

            AnimatedVisibility(
                visible = isMemberListOpen && !activeServer.isDmTab && !effectiveViewingVoiceRoom,
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
            // Community, Bot, Voice & AI Action Toast Feedback Banner
            val bannerError = voiceError ?: communityUiState.errorMessage ?: botUiState.errorMessage
                ?: if (!showAiAssistantSheet) aiUiState.errorMessage else null
            val bannerText = bannerError ?: communityUiState.feedbackMessage ?: botUiState.feedbackMessage
                ?: if (!showAiAssistantSheet) aiUiState.feedbackMessage else null
            if (bannerText != null) {
                Surface(
                    color = if (bannerError != null) DiscordRed else DiscordGreen,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .testTag("community_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            repository.clearVoiceError()
                            communityViewModel.clearFeedback()
                            botViewModel.clearFeedback()
                            aiViewModel.clearFeedback()
                        }) {
                            Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
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
                gesturesEnabled = !effectiveViewingVoiceRoom,
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

    // Create Advanced Community Modal
    if (showCreateServerDialog) {
        CreateEditCommunityDialog(
            existingServer = null,
            isOwner = true,
            onDismiss = { showCreateServerDialog = false },
            onSave = { name, desc, handle, iconText, imgUrl, banUrl, banColor, isPublic, catTag, rules, welcome ->
                communityViewModel.createCommunity(
                    name = name,
                    description = desc,
                    uniqueHandle = handle,
                    iconText = iconText,
                    imageUrl = imgUrl,
                    bannerUrl = banUrl,
                    bannerColorHex = banColor,
                    isPublic = isPublic,
                    categoryTag = catTag,
                    rulesGuidelines = rules,
                    homeWelcomeMessage = welcome,
                    onCreated = { isViewingCommunityHome = true }
                )
            }
        )
    }

    // Edit Existing Community Modal
    editingCommunityServer?.let { srvToEdit ->
        CreateEditCommunityDialog(
            existingServer = srvToEdit,
            isOwner = CommunityPermissions.isOwner(srvToEdit, currentUser.id),
            onDismiss = { editingCommunityServer = null },
            onSave = { name, desc, handle, _, imgUrl, banUrl, banColor, isPublic, catTag, rules, welcome ->
                communityViewModel.updateCommunity(
                    serverId = srvToEdit.id,
                    name = name,
                    description = desc,
                    uniqueHandle = handle,
                    imageUrl = imgUrl,
                    bannerUrl = banUrl,
                    bannerColorHex = banColor,
                    isPublic = isPublic,
                    categoryTag = catTag,
                    rulesGuidelines = rules,
                    homeWelcomeMessage = welcome
                )
            },
            onDeleteCommunity = {
                communityViewModel.deleteCommunity(srvToEdit.id) {
                    isViewingCommunityHome = false
                }
            },
            onLeaveCommunity = {
                communityViewModel.leaveCommunity(srvToEdit.id) {
                    isViewingCommunityHome = false
                }
            }
        )
    }

    // Community Invites Management Sheet
    if (showInvitesManagerSheet && !activeServer.isDmTab) {
        CommunityInvitesSheet(
            server = activeServer,
            canManageInvites = CommunityPermissions.hasPermission(activeServer, currentUser.id, RolePermission.MANAGE_INVITES),
            onCreateInvite = { maxUses, hrs -> communityViewModel.createInvite(activeServer.id, maxUses, hrs) },
            onRevokeInvite = { code -> communityViewModel.revokeInvite(activeServer.id, code) },
            onDeleteInvite = { code -> communityViewModel.deleteInvite(activeServer.id, code) },
            onDismiss = { showInvitesManagerSheet = false }
        )
    }

    // Community Roles & Permissions Matrix Sheet
    if (showRolesMatrixSheet && !activeServer.isDmTab) {
        CommunityRolesMatrixSheet(
            server = activeServer,
            canManageRoles = CommunityPermissions.hasPermission(activeServer, currentUser.id, RolePermission.MANAGE_ROLES),
            onSaveRole = { name, colorHex, secHex, perms ->
                communityViewModel.saveCustomRole(activeServer.id, name, colorHex, secHex, perms)
            },
            onAssignMemberRole = { uid, role, customId ->
                communityViewModel.assignMemberRole(activeServer.id, uid, role, customId)
            },
            onDismiss = { showRolesMatrixSheet = false }
        )
    }

    // Community Categories & Channels Manager Sheet
    if (showCategoriesManagerSheet && !activeServer.isDmTab) {
        CommunityCategoriesSheet(
            server = activeServer,
            canManageChannels = CommunityPermissions.hasPermission(activeServer, currentUser.id, RolePermission.MANAGE_CHANNELS),
            onCreateCategory = { name, isPriv -> communityViewModel.createCategory(activeServer.id, name, isPriv) },
            onDeleteCategory = { name -> communityViewModel.deleteCategory(activeServer.id, name) },
            onCreateChannel = { name, type, catName, isPriv ->
                communityViewModel.createChannel(activeServer.id, name, type, catName, isPriv)
            },
            onDeleteChannel = { chId -> communityViewModel.deleteChannel(activeServer.id, chId) },
            onDismiss = { showCategoriesManagerSheet = false }
        )
    }

    // Explore Public Communities Sheet
    if (showExploreServersSheet) {
        ExploreServersSheet(
            listings = discoverServers,
            joinedServerIds = servers.filterNot { it.isDmTab }.map { it.id }.toSet(),
            onRefreshCloud = { repository.refreshPublicServersFromCloud() },
            onJoinServer = { listing ->
                communityViewModel.joinPublicCommunity(listing) {
                    isViewingCommunityHome = true
                }
                coroutineScope.launch { drawerState.close() }
            },
            onOpenJoinByCode = { showJoinServerDialog = true },
            onDismiss = { showExploreServersSheet = false },
            onSearchCloud = { query, filter -> communityRepo.searchCommunities(query, filter) }
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

    // Join Community By Invite Code Modal
    if (showJoinServerDialog) {
        JoinServerByCodeDialog(
            onDismiss = { showJoinServerDialog = false },
            onJoinCodeAsync = { code, onResult ->
                communityViewModel.joinByInviteCode(code) { ok ->
                    if (ok) {
                        isViewingCommunityHome = true
                        coroutineScope.launch { drawerState.close() }
                        onResult(true, null)
                    } else {
                        onResult(false, communityViewModel.uiState.value.errorMessage ?: "Code invalide ou expiré.")
                    }
                }
            }
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

    // Raven Bots Dashboard
    if (showBotDashboardSheet) {
        BotDashboardSheet(
            currentUserId = currentUser.id,
            activeServer = activeServer,
            allServers = servers,
            bots = bots,
            installationsByServer = botInstallationsByServer,
            logsByBot = botLogsByBot,
            uiState = botUiState,
            onSelectBot = { botViewModel.selectBot(it) },
            onOpenCreateBot = { showCreateBotDialog = true },
            onOpenEditBot = { editingBot = it },
            onToggleBotActive = { id, active -> botViewModel.toggleBotActive(id, active) },
            onRegenerateToken = { botViewModel.regenerateToken(it) },
            onVerifyToken = { id, cand -> botViewModel.verifyToken(id, cand) },
            onClearOneTimeToken = { botViewModel.clearOneTimeToken() },
            onAddBotToServer = { botId, srvId, perms, roleTitle, customId ->
                botViewModel.addBotToServer(botId, srvId, perms, roleTitle, customId)
            },
            onRemoveBotFromServer = { botId, srvId -> botViewModel.removeBotFromServer(botId, srvId) },
            onUpdateServerBotConfig = { botId, srvId, perms, roleTitle, customId, enabled ->
                botViewModel.updateServerBotConfig(botId, srvId, perms, roleTitle, customId, enabled)
            },
            onSaveCommand = { botId, spec -> botViewModel.saveCommand(botId, spec) },
            onDeleteCommand = { botId, cmdId -> botViewModel.deleteCommand(botId, cmdId) },
            onSaveAutoResponse = { botId, rule -> botViewModel.saveAutoResponse(botId, rule) },
            onDeleteAutoResponse = { botId, ruleId -> botViewModel.deleteAutoResponse(botId, ruleId) },
            onSaveWebhook = { botId, label, url, secret, evs -> botViewModel.saveWebhook(botId, label, url, secret, evs) },
            onDeleteWebhook = { botId, whId -> botViewModel.deleteWebhook(botId, whId) },
            onTriggerTestWebhook = { botId, whId -> botViewModel.triggerTestWebhook(botId, whId) },
            onTogglePlugin = { botId, pid, en -> botViewModel.togglePlugin(botId, pid, en) },
            onUpdateRateLimit = { botId, cfg -> botViewModel.updateRateLimit(botId, cfg) },
            onDismiss = { showBotDashboardSheet = false }
        )
    }

    // Create Bot Dialog
    if (showCreateBotDialog) {
        CreateEditBotDialog(
            existingBot = null,
            onDismiss = { showCreateBotDialog = false },
            onSave = { name, handle, desc, avatar, color, pub, perms, events ->
                botViewModel.createBot(name, handle, desc, avatar, color, pub, perms, events)
            }
        )
    }

    // Edit Existing Bot Dialog
    editingBot?.let { botToEdit ->
        CreateEditBotDialog(
            existingBot = botToEdit,
            onDismiss = { editingBot = null },
            onSave = { name, handle, desc, avatar, color, pub, perms, events ->
                botViewModel.updateBot(botToEdit.id, name, handle, desc, avatar, color, pub, perms, events)
            },
            onDeleteBot = {
                botViewModel.deleteBot(botToEdit.id)
            }
        )
    }

    // RAVEN AI Assistant Modal Sheet
    if (showAiAssistantSheet) {
        AiAssistantSheet(
            uiState = aiUiState,
            viewModel = aiViewModel,
            activeServer = activeServer,
            activeChannel = activeChannel,
            channelMessages = messages[activeChannel.id].orEmpty(),
            currentUserId = currentUser.id,
            onPostToChannel = { text ->
                if (!activeServer.isDmTab) {
                    repository.sendMessage(activeChannel.id, text, null, null, 0)
                    showAiAssistantSheet = false
                }
            },
            onPublishAnnouncement = { title, content, isPinned ->
                if (!activeServer.isDmTab) {
                    communityViewModel.publishAnnouncement(activeServer.id, title, content, isPinned)
                }
            },
            onCreateEvent = { title, desc, dateTime, location ->
                if (!activeServer.isDmTab) {
                    communityViewModel.createEvent(activeServer.id, title, desc, dateTime, location)
                }
            },
            onDismiss = { showAiAssistantSheet = false }
        )
    }
}
