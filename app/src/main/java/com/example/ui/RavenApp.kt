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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.AccountSecurityRepository
import com.example.data.BotRepository
import com.example.data.ChatRepository
import com.example.data.CommunityPermissions
import com.example.data.CommunityRepository
import com.example.data.EventRepository
import com.example.data.FriendFirestoreSync
import com.example.data.FriendRepository
import com.example.data.GlobalSearchRepository
import com.example.data.PluginRepository
import com.example.data.PollRepository
import com.example.data.StoryRepository
import com.example.data.searchCommunities
import com.example.model.AdvancedPoll
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.model.FriendRelationStatus
import com.example.model.Message
import com.example.model.RavenBot
import com.example.model.RolePermission
import com.example.model.SearchEntityType
import com.example.model.Server
import com.example.model.ShopCatalog
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.model.resolveServerBackgroundTheme
import com.example.model.serverBackgroundTargetKey
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AccountSecurityViewModel
import com.example.ui.viewmodel.BotDashboardViewModel
import com.example.ui.viewmodel.CommunityViewModel
import com.example.ui.viewmodel.EventViewModel
import com.example.ui.viewmodel.FriendViewModel
import com.example.ui.viewmodel.GlobalSearchViewModel
import com.example.ui.viewmodel.PluginManagerViewModel
import com.example.ui.viewmodel.PollViewModel
import com.example.ui.viewmodel.StoryViewModel
import com.example.util.ActivePointerMode
import com.example.util.AudioPlayerHelper
import com.example.util.LocalRavenPlatform
import com.example.util.NotificationHelper
import com.example.util.PlatformEnvironmentDetector
import com.example.util.RavenPlatformKind
import com.example.util.desktopHoverHighlight
import com.example.util.desktopHoverPointer
import com.example.util.rememberRavenPlatformProfile
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

    val storyRepo = remember(repository) { StoryRepository(repository) }
    val storyViewModel = remember(storyRepo) { StoryViewModel(storyRepo) }
    val storyUiState by storyViewModel.uiState.collectAsState()

    val pollRepo = remember(repository) { PollRepository(repository) }
    val pollViewModel = remember(pollRepo) { PollViewModel(pollRepo) }
    val pollUiState by pollViewModel.uiState.collectAsState()

    val eventRepo = remember(repository) { EventRepository(repository) }
    val eventViewModel = remember(eventRepo) { EventViewModel(eventRepo) }
    val eventUiState by eventViewModel.uiState.collectAsState()

    val pluginRepo = remember(repository) { PluginRepository(repository) }
    val pluginViewModel = remember(pluginRepo) { PluginManagerViewModel(pluginRepo) }
    val pluginUiState by pluginViewModel.uiState.collectAsState()

    val friendRepo = remember(repository) { FriendRepository(repository) }
    val friendViewModel = remember(friendRepo, repository) { FriendViewModel(friendRepo, repository) }
    val friendUiState by friendViewModel.uiState.collectAsState()

    val globalSearchRepo = remember(repository, friendRepo, storyRepo, eventRepo, botRepo) {
        GlobalSearchRepository(
            chatRepo = repository,
            friendRepo = friendRepo,
            storyRepo = storyRepo,
            eventRepo = eventRepo,
            botRepo = botRepo
        )
    }
    val globalSearchViewModel = remember(globalSearchRepo) { GlobalSearchViewModel(globalSearchRepo) }
    val globalSearchUiState by globalSearchViewModel.uiState.collectAsState()

    val accountSecurityRepo = remember(repository) { AccountSecurityRepository(repository) }
    val accountSecurityViewModel = remember(accountSecurityRepo) { AccountSecurityViewModel(accountSecurityRepo) }
    val securityUiState by accountSecurityViewModel.uiState.collectAsState()

    val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }

    // Navigation and drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isMemberListOpen by remember { mutableStateOf(false) }
    var isDesktopMemberDockVisible by remember { mutableStateOf(true) }
    var isCompactPcSidebarOpen by remember { mutableStateOf(false) }
    var activePointerMode by remember { mutableStateOf(ActivePointerMode.TOUCH) }
    var activePlatformProfile by remember {
        mutableStateOf(
            PlatformEnvironmentDetector.evaluate(
                PlatformEnvironmentDetector.collectSignals(context)
            )
        )
    }
    var internalViewingVoiceRoom by remember { mutableStateOf(false) }
    val effectiveViewingVoiceRoom = isViewingFullVoiceRoom ?: internalViewingVoiceRoom
    val setViewingVoiceRoom: (Boolean) -> Unit = { visible ->
        internalViewingVoiceRoom = visible
        onViewingFullVoiceRoomChange?.invoke(visible)
    }
    var isViewingCommunityHome by remember { mutableStateOf(false) }
    var showBotDashboardSheet by remember { mutableStateOf(false) }
    var showServerBackgroundDialog by remember { mutableStateOf(false) }
    var showPollsHubSheet by remember { mutableStateOf(false) }
    var showAdvancedPollEditor by remember { mutableStateOf(false) }
    var editingPollDraft by remember { mutableStateOf<AdvancedPoll?>(null) }
    var showCreateStoryDialog by remember { mutableStateOf(false) }
    var viewingStoryAuthorId by remember { mutableStateOf<String?>(null) }
    var showStoryModerationSheet by remember { mutableStateOf(false) }
    var showCreateBotDialog by remember { mutableStateOf(false) }
    var editingBot by remember { mutableStateOf<RavenBot?>(null) }
    var editingCommunityServer by remember { mutableStateOf<Server?>(null) }
    var showInvitesManagerSheet by remember { mutableStateOf(false) }
    var showRolesMatrixSheet by remember { mutableStateOf(false) }
    var showCategoriesManagerSheet by remember { mutableStateOf(false) }
    var selectedUserProfile by remember { mutableStateOf<com.example.model.User?>(null) }
    var showUserDirectorySheet by remember { mutableStateOf(false) }
    var showFriendPrivacySheet by remember { mutableStateOf(false) }
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
    var showServerAdminDashboard by remember { mutableStateOf(false) }
    var showInboxSheet by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showPluginsSheet by remember { mutableStateOf(false) }
    var showGlobalSearchSheet by remember { mutableStateOf(false) }
    var showAccountSecuritySheet by remember { mutableStateOf(false) }
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

    LaunchedEffect(pluginRepo) {
        pluginRepo.attachToChatRepository()
    }

    LaunchedEffect(selectedServerId, isFirebaseSignedIn) {
        if (selectedServerId != "dm_tab") {
            communityViewModel.observeCommunity(selectedServerId)
        }
        botViewModel.startObserving(selectedServerId)
        storyViewModel.startObserving()
        pollViewModel.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
        eventViewModel.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
        pluginViewModel.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
        friendViewModel.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
        globalSearchRepo.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
        accountSecurityViewModel.syncWithAuthUid(if (isFirebaseSignedIn) repository.authInstance()?.currentUser?.uid else null)
    }

    LaunchedEffect(securityUiState.feedbackMessage, securityUiState.errorMessage) {
        if (securityUiState.feedbackMessage != null || securityUiState.errorMessage != null) {
            delay(3500)
            accountSecurityViewModel.clearFeedback()
        }
    }

    LaunchedEffect(pluginUiState.feedbackMessage, pluginUiState.errorMessage) {
        if (pluginUiState.feedbackMessage != null || pluginUiState.errorMessage != null) {
            delay(3500)
            pluginViewModel.clearFeedback()
        }
    }

    LaunchedEffect(eventUiState.feedbackMessage, eventUiState.errorMessage) {
        if (eventUiState.feedbackMessage != null || eventUiState.errorMessage != null) {
            delay(3500)
            eventViewModel.clearFeedback()
        }
    }

    LaunchedEffect(pollUiState.feedbackMessage, pollUiState.errorMessage) {
        if (pollUiState.feedbackMessage != null || pollUiState.errorMessage != null) {
            delay(3500)
            pollViewModel.clearFeedback()
        }
    }

    LaunchedEffect(storyUiState.feedbackMessage, storyUiState.errorMessage) {
        if (storyUiState.feedbackMessage != null || storyUiState.errorMessage != null) {
            delay(3500)
            storyViewModel.clearFeedback()
        }
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
    val canViewMemberList = !activeServer.isDmTab && (
        !activeServer.customization.privacy.hideMemberListForNonStaff ||
            CommunityPermissions.isOwner(activeServer, currentUser.id) ||
            CommunityPermissions.effectiveRole(activeServer, currentUser.id) in setOf(UserRole.ADMIN, UserRole.MODERATOR)
        )
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
        closeDrawerAfterSelect: Boolean = true,
        onDismissCompactSidebar: (() -> Unit)? = null
    ) {
        val closeAfterNav: () -> Unit = {
            if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
            onDismissCompactSidebar?.invoke()
        }
        Row(modifier = modifier.fillMaxHeight()) {
            ServerRail(
                servers = servers,
                selectedServerId = selectedServerId,
                serverFolders = serverFolders,
                onSelectServer = { serverId ->
                    isViewingCommunityHome = false
                    repository.selectServer(serverId)
                    closeAfterNav()
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
                unseenStoriesCount = storyUiState.unseenGroups.count { !it.isCurrentUser },
                onOpenStories = {
                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                    val targetGroup = storyUiState.unseenGroups.firstOrNull() ?: storyUiState.allGroups.firstOrNull()
                    if (targetGroup != null) {
                        viewingStoryAuthorId = targetGroup.authorId
                    } else {
                        showCreateStoryDialog = true
                    }
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
                        closeAfterNav()
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
                    onOpenServerBackground = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showServerBackgroundDialog = true
                    },
                    onOpenPollsHub = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showPollsHubSheet = true
                    },
                    onOpenPluginsManager = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showPluginsSheet = true
                    },
                    onOpenAdminDashboard = {
                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                        showServerAdminDashboard = true
                    },
                    onToggleServerClosed = { closed ->
                        communityViewModel.setServerClosed(activeServer.id, closed)
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
                val sidebarPlatform = LocalRavenPlatform.current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(palette.sidebarBg)
                ) {
                    // Top "Recherche ou lance une conversation" pill button (matches real Discord)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            color = palette.inputBg,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .desktopHoverPointer(sidebarPlatform.isPc)
                                .clickable {
                                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                    showGlobalSearchSheet = true
                                }
                                .testTag("sidebar_dm_search_btn")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Recherche ou lance une conversation",
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border, thickness = 1.dp)

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            val friendsSelected = selectedDmId.isNullOrBlank()
                            DiscordSidebarNavRow(
                                icon = Icons.Default.People,
                                label = "Amis",
                                selected = friendsSelected,
                                onClick = {
                                    repository.selectDm("")
                                    closeAfterNav()
                                },
                                testTag = "sidebar_nav_friends"
                            )
                            DiscordSidebarNavRow(
                                icon = Icons.Default.AutoAwesome,
                                label = "Nitro · Orbs",
                                selected = false,
                                onClick = {
                                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                    showOrbsShopDialog = true
                                },
                                testTag = "sidebar_nav_nitro"
                            )
                            DiscordSidebarNavRow(
                                icon = Icons.Default.Storefront,
                                label = "Boutique",
                                selected = false,
                                onClick = {
                                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                    showOrbsShopDialog = true
                                },
                                testTag = "sidebar_nav_shop"
                            )
                            DiscordSidebarNavRow(
                                icon = Icons.Default.EmojiEvents,
                                label = "Quêtes",
                                selected = false,
                                onClick = {
                                    if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                    showOrbsShopDialog = true
                                },
                                testTag = "sidebar_nav_quests"
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Messages privés",
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        if (closeDrawerAfterSelect) coroutineScope.launch { drawerState.close() }
                                        showUserDirectorySheet = true
                                    },
                                    modifier = Modifier.size(22.dp).testTag("sidebar_new_dm_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Nouveau message privé",
                                        tint = palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        items(directConversations, key = { "side_dm_${it.id}" }) { conv ->
                            val isSelected = conv.id == selectedDmId
                            val displayName = if (conv.isGroup) conv.groupName ?: "Groupe Privé" else conv.friend.name
                            val isBot = !conv.isGroup && conv.friend.role == UserRole.BOT
                            val subtitle = if (conv.isGroup) {
                                "${conv.groupMembers.size + 1} membres"
                            } else {
                                conv.friend.customStatus.ifBlank { conv.lastMessage }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) palette.inputBg else Color.Transparent)
                                    .desktopHoverHighlight(
                                        shape = RoundedCornerShape(8.dp),
                                        enabled = sidebarPlatform.isPc || sidebarPlatform.hasMouseOrTrackpad
                                    )
                                    .desktopHoverPointer(sidebarPlatform.isPc)
                                    .clickable {
                                        repository.selectDm(conv.id)
                                        closeAfterNav()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("sidebar_dm_${conv.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (conv.isGroup) Color(conv.groupIconColor)
                                                else Color(conv.friend.avatarColor)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (conv.isGroup) {
                                            Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                        } else if (!conv.friend.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = conv.friend.avatarUrl,
                                                contentDescription = displayName,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = displayName.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (!conv.isGroup) {
                                        val dotColor = when (conv.friend.status) {
                                            UserStatus.ONLINE -> DiscordGreen
                                            UserStatus.IDLE -> DiscordAmber
                                            UserStatus.DND -> DiscordRed
                                            UserStatus.OFFLINE -> DiscordTextDark
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(palette.sidebarBg)
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = displayName,
                                            color = if (isSelected || conv.unreadCount > 0) palette.textPrimary else palette.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || conv.unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isBot) {
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Surface(
                                                color = DiscordBlurple,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "BOT",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            color = palette.textMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (conv.unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(DiscordRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = conv.unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

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

    @Composable
    fun MainContentPane(
        showDrawerButton: Boolean,
        modifier: Modifier = Modifier
    ) {
        val currentPlatform = LocalRavenPlatform.current
        val isDockedRightActive = currentPlatform.useDesktopLayout &&
            currentPlatform.dockRightMemberPanel &&
            isDesktopMemberDockVisible &&
            canViewMemberList &&
            !effectiveViewingVoiceRoom &&
            !isViewingCommunityHome &&
            !activeServer.isDmTab
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
                                allFriends = friendUiState.friends.map { it.user }.ifEmpty { com.example.data.MockData.allFriends },
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
                                onAddFriend = { tag -> friendViewModel.sendFriendRequestByInput(tag) },
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
                                onAcceptFriendRequest = { reqId -> friendViewModel.acceptRequest(reqId) },
                                onDeclineOrCancelFriendRequest = { reqId -> friendViewModel.declineOrCancelRequest(reqId) },
                                onBlockUser = { user -> friendViewModel.blockUser(user) },
                                onUnblockUser = { uid -> friendViewModel.unblockUser(uid) },
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
                                showNavigationDrawerButton = showDrawerButton,
                                storyUiState = storyUiState,
                                onCreateStoryClick = { showCreateStoryDialog = true },
                                onOpenStoryGroup = { grp -> viewingStoryAuthorId = grp.authorId },
                                onOpenStoryModeration = { showStoryModerationSheet = true },
                                friends = friendUiState.friends,
                                onOpenFriendDm = { user -> friendViewModel.openDm(user) },
                                onOpenUserProfile = { user -> selectedUserProfile = user },
                                onRemoveFriend = { uid -> friendViewModel.removeFriend(uid) },
                                onDeclineFriendRequest = { reqId -> friendViewModel.declineRequest(reqId) },
                                onCancelFriendRequest = { reqId -> friendViewModel.cancelRequest(reqId) },
                                onOpenUserSearchSheet = { showUserDirectorySheet = true },
                                onOpenFriendPrivacy = { showFriendPrivacySheet = true },
                                isDesktopSidebarDocked = currentPlatform.useDesktopLayout
                            )
                        }

                        !activeServer.isDmTab && isViewingCommunityHome -> {
                            CommunityHomeView(
                                server = activeServer,
                                currentUser = currentUser,
                                unreadNotificationsCount = unreadNotificationsCount,
                                showDrawerButton = showDrawerButton,
                                applyTopInsets = showDrawerButton,
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
                                onOpenAdminDashboard = { showServerAdminDashboard = true },
                                onToggleServerClosed = { closed ->
                                    communityViewModel.setServerClosed(activeServer.id, closed)
                                },
                                onAssignMemberRole = { uid, role, customId ->
                                    communityViewModel.assignMemberRole(activeServer.id, uid, role, customId)
                                },
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
                                onMemberClick = { member -> selectedUserProfile = member },
                                pollViewModel = pollViewModel,
                                onOpenPollsHub = { showPollsHubSheet = true },
                                eventViewModel = eventViewModel,
                                knownFriends = (friendUiState.friends.map { it.user } + directConversations.filter { !it.isGroup }.map { it.friend })
                                    .distinctBy { it.id }
                                    .filter { it.id != currentUser.id },
                                onRequestNotificationPermission = { requestNotificationPermission() },
                                onJoinVoiceChannel = { ch ->
                                    isViewingCommunityHome = false
                                    if (repository.joinVoiceChannel(ch)) {
                                        requestAudioPermission()
                                        setViewingVoiceRoom(true)
                                    }
                                },
                                onOpenEventsHub = { showServerEventsSheet = true },
                                serverBackground = resolveServerBackgroundTheme(
                                    server = activeServer,
                                    channelId = null,
                                    userBackgrounds = chatBackgrounds
                                ),
                                onOpenServerBackground = { showServerBackgroundDialog = true }
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
                                onToggleMemberList = {
                                    if (canViewMemberList) {
                                        if (currentPlatform.useDesktopLayout && currentPlatform.dockRightMemberPanel) {
                                            isDesktopMemberDockVisible = !isDesktopMemberDockVisible
                                        } else {
                                            isMemberListOpen = !isMemberListOpen
                                        }
                                    }
                                },
                                unreadNotificationsCount = unreadNotificationsCount,
                                onOpenInbox = { showInboxSheet = true },
                                chatTheme = resolveServerBackgroundTheme(
                                    server = activeServer,
                                    channelId = activeChannel.id,
                                    userBackgrounds = chatBackgrounds
                                ),
                                onApplyChatTheme = { theme ->
                                    repository.setChatBackground(activeChannel.id, theme)
                                },
                                onResetChatTheme = {
                                    repository.clearChatBackground(activeChannel.id)
                                },
                                serverName = activeServer.name,
                                canManageServerBackground = CommunityPermissions.isCreatorOrAdmin(activeServer, currentUser.id) ||
                                    CommunityPermissions.hasPermission(activeServer, currentUser.id, RolePermission.MANAGE_COMMUNITY),
                                onApplyServerBackground = { theme, makeOfficial ->
                                    repository.setChatBackground(serverBackgroundTargetKey(activeServer.id), theme)
                                    if (makeOfficial) {
                                        val isPreset = ShopCatalog.initialChatThemes.any { it.id == theme.id && it.imageUrl.isNullOrBlank() }
                                        communityViewModel.updateServerOfficialBackground(
                                            server = activeServer,
                                            backgroundThemeId = if (isPreset && theme.imageUrl.isNullOrBlank()) theme.id else null,
                                            backgroundImageUrl = theme.imageUrl,
                                            backgroundOpacity = theme.opacity
                                        )
                                    }
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
                                botSlashCommands = remember(activeServer.id, botInstallationsByServer, bots, pluginUiState.plugins) {
                                    botRepo.getSlashCommandsForServer(activeServer.id) + pluginRepo.activeSlashCommands()
                                },
                                currentUserId = currentUser.id,
                                currentUserName = currentUser.name,
                                pollViewModel = pollViewModel,
                                onOpenPollsHub = { showPollsHubSheet = true },
                                onOpenAdvancedPollEditor = {
                                    editingPollDraft = null
                                    showAdvancedPollEditor = true
                                },
                                isServerClosed = activeServer.isClosed,
                                canBypassServerClosed = CommunityPermissions.isCreatorOrAdmin(activeServer, currentUser.id),
                                serverClosedByName = activeServer.closedByName,
                                onToggleServerClosed = { closed ->
                                    communityViewModel.setServerClosed(activeServer.id, closed)
                                }
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
                visible = isMemberListOpen && canViewMemberList && !effectiveViewingVoiceRoom && !isDockedRightActive,
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
            // Community, Bot, Voice, Story, Plugin & Friend Action Toast Feedback Banner
            val bannerError = voiceError ?: friendUiState.errorMessage ?: storyUiState.errorMessage ?: communityUiState.errorMessage ?: botUiState.errorMessage
                ?: (if (!showPluginsSheet) pluginUiState.errorMessage else null)
            val bannerText = bannerError ?: friendUiState.feedbackMessage ?: storyUiState.feedbackMessage ?: communityUiState.feedbackMessage ?: botUiState.feedbackMessage
                ?: (if (!showPluginsSheet) pluginUiState.feedbackMessage else null)
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
                            friendViewModel.clearFeedback()
                            storyViewModel.clearFeedback()
                            communityViewModel.clearFeedback()
                            botViewModel.clearFeedback()
                            pluginViewModel.clearFeedback()
                        }) {
                            Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Adaptive Layout using BoxWithConstraints (PC/Desktop multi-pane workspace, Tablet split-pane, Phone drawer)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val ptrType = event.changes.firstOrNull()?.type
                        val nextMode = when (ptrType) {
                            PointerType.Mouse -> ActivePointerMode.MOUSE
                            PointerType.Stylus, PointerType.Eraser -> ActivePointerMode.STYLUS
                            PointerType.Touch -> ActivePointerMode.TOUCH
                            else -> null
                        }
                        if (nextMode != null && nextMode != activePointerMode) {
                            activePointerMode = nextMode
                        }
                    }
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val modCtrlOrMeta = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                when {
                    modCtrlOrMeta && keyEvent.key == Key.K -> {
                        showGlobalSearchSheet = true
                        true
                    }
                    modCtrlOrMeta && (keyEvent.key == Key.P || keyEvent.key == Key.Comma) -> {
                        showAppSettingsDialog = true
                        true
                    }
                    modCtrlOrMeta && keyEvent.isShiftPressed && keyEvent.key == Key.S -> {
                        showAccountSecuritySheet = true
                        true
                    }
                    modCtrlOrMeta && keyEvent.key == Key.M -> {
                        requestAudioPermission()
                        repository.toggleMute()
                        true
                    }
                    keyEvent.key == Key.Escape -> {
                        when {
                            showGlobalSearchSheet -> showGlobalSearchSheet = false
                            showAccountSecuritySheet -> showAccountSecuritySheet = false
                            showAppSettingsDialog -> showAppSettingsDialog = false
                            isMemberListOpen -> isMemberListOpen = false
                            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val platformProfile = rememberRavenPlatformProfile(
            windowWidthDp = maxWidth,
            windowHeightDp = maxHeight,
            layoutOverride = appSettings.platformLayoutOverride,
            activePointerMode = activePointerMode
        )
        SideEffect {
            activePlatformProfile = platformProfile
        }

        CompositionLocalProvider(LocalRavenPlatform provides platformProfile) {
            if (platformProfile.useDesktopLayout) {
                val dockRightMembers = platformProfile.dockRightMemberPanel &&
                    isDesktopMemberDockVisible &&
                    canViewMemberList &&
                    !effectiveViewingVoiceRoom &&
                    !isViewingCommunityHome &&
                    !activeServer.isDmTab

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.primaryBg)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    // Authentic Discord Desktop Top Window Bar (Back/Forward • Centered Title • Inbox/Security/Help)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp)
                            .background(palette.primaryBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("desktop_environment_bar"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (platformProfile.windowWidthDp < 680) {
                                IconButton(
                                    onClick = { isCompactPcSidebarOpen = !isCompactPcSidebarOpen },
                                    modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc).testTag("desktop_toggle_sidebar_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isCompactPcSidebarOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                                        contentDescription = "Afficher les salons et MP",
                                        tint = palette.textPrimary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    when {
                                        effectiveViewingVoiceRoom -> setViewingVoiceRoom(false)
                                        isViewingCommunityHome -> isViewingCommunityHome = false
                                        activeServer.isDmTab && !selectedDmId.isNullOrBlank() -> repository.selectDm("")
                                        !activeServer.isDmTab -> repository.selectServer("dm_tab")
                                    }
                                },
                                modifier = Modifier.size(26.dp).desktopHoverPointer(platformProfile.isPc)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Précédent",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Suivant",
                                    tint = palette.textMuted.copy(alpha = 0.45f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Centered Context Indicator with weight(1f) so it never collides with right icons
                        val topTitle = when {
                            effectiveViewingVoiceRoom && connectedVoiceChannel != null -> connectedVoiceChannel!!.name
                            activeServer.isDmTab -> {
                                val dmConv = directConversations.find { it.id == selectedDmId }
                                if (dmConv == null) "Amis"
                                else if (dmConv.isGroup) dmConv.groupName ?: "Groupe"
                                else dmConv.friend.name
                            }
                            isViewingCommunityHome -> activeServer.name
                            else -> "# ${activeChannel.name}"
                        }
                        val topIcon = when {
                            effectiveViewingVoiceRoom -> Icons.Default.VolumeUp
                            activeServer.isDmTab && selectedDmId.isNullOrBlank() -> Icons.Default.People
                            activeServer.isDmTab -> Icons.Default.AlternateEmail
                            isViewingCommunityHome -> Icons.Default.Home
                            else -> Icons.Default.Tag
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = topIcon,
                                contentDescription = null,
                                tint = palette.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = topTitle,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Right minimalist icons (Inbox, Security Shield, Dock Members, Platform Settings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showGlobalSearchSheet = true },
                                modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc).testTag("desktop_quick_search_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Recherche globale (Ctrl+K)",
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showInboxSheet = true },
                                    modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inbox,
                                        contentDescription = "Boîte de réception",
                                        tint = if (unreadNotificationsCount > 0) palette.textPrimary else palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (unreadNotificationsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .align(Alignment.TopEnd)
                                            .padding(1.dp)
                                            .clip(CircleShape)
                                            .background(DiscordRed)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showAccountSecuritySheet = true },
                                modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc).testTag("desktop_quick_security_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Sécurité du compte",
                                    tint = if (securityUiState.openAlertsCount > 0) DiscordRed else palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (platformProfile.dockRightMemberPanel && canViewMemberList && !effectiveViewingVoiceRoom && !isViewingCommunityHome && !activeServer.isDmTab) {
                                IconButton(
                                    onClick = { isDesktopMemberDockVisible = !isDesktopMemberDockVisible },
                                    modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc).testTag("desktop_toggle_member_dock_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Panneau des membres",
                                        tint = if (isDesktopMemberDockVisible) palette.textPrimary else palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Surface(
                                color = palette.inputBg,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .desktopHoverPointer(platformProfile.isPc)
                                    .clickable {
                                        repository.updateAppSettings {
                                            it.copy(platformLayoutOverride = com.example.model.PlatformLayoutOverride.FORCE_MOBILE)
                                        }
                                    }
                                    .testTag("desktop_quick_mode_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = "Mode PC actif (appuyer pour Mobile)",
                                        tint = DiscordBlurple,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "PC",
                                        color = palette.textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showAppSettingsDialog = true },
                                modifier = Modifier.size(28.dp).desktopHoverPointer(platformProfile.isPc).testTag("desktop_platform_chip")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = platformProfile.platformBadgeLabel,
                                    tint = palette.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (platformProfile.windowWidthDp >= 680) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            SidebarPanel(
                                modifier = Modifier.width(if (platformProfile.windowWidthDp >= 1200) 326.dp else 306.dp),
                                closeDrawerAfterSelect = false
                            )

                            // Rounded top-left Discord main workspace card matching official screenshot
                            Surface(
                                shape = RoundedCornerShape(topStart = 12.dp),
                                color = palette.chatBg,
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    MainContentPane(
                                        showDrawerButton = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (dockRightMembers) {
                                        VerticalDivider(color = palette.border, thickness = 1.dp)
                                        MemberListDrawer(
                                            members = activeServer.members,
                                            customRoles = activeServer.customRoles,
                                            onClose = { isDesktopMemberDockVisible = false },
                                            onMemberClick = { member -> selectedUserProfile = member },
                                            modifier = Modifier
                                                .width(248.dp)
                                                .testTag("desktop_docked_member_panel")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Narrow/portrait PC Mode: docked ServerRail + full-width rounded workspace + slide-over Discord channel/DM sidebar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ServerRail(
                                    servers = servers,
                                    selectedServerId = selectedServerId,
                                    serverFolders = serverFolders,
                                    onSelectServer = { serverId ->
                                        isViewingCommunityHome = false
                                        repository.selectServer(serverId)
                                        isCompactPcSidebarOpen = true
                                    },
                                    onToggleFolder = { folderId -> repository.toggleServerFolderExpanded(folderId) },
                                    onCreateFolderClick = { showCreateFolderDialog = true },
                                    onCreateServerClick = { showCreateServerDialog = true },
                                    onJoinServerClick = { showJoinServerDialog = true },
                                    onExploreServersClick = { showExploreServersSheet = true },
                                    onOpenOrbsShop = { showOrbsShopDialog = true },
                                    onOpenBotsDashboard = { showBotDashboardSheet = true },
                                    unseenStoriesCount = storyUiState.unseenGroups.count { !it.isCurrentUser },
                                    onOpenStories = {
                                        val targetGroup = storyUiState.unseenGroups.firstOrNull() ?: storyUiState.allGroups.firstOrNull()
                                        if (targetGroup != null) {
                                            viewingStoryAuthorId = targetGroup.authorId
                                        } else {
                                            showCreateStoryDialog = true
                                        }
                                    }
                                )

                                Surface(
                                    shape = RoundedCornerShape(topStart = 12.dp),
                                    color = palette.chatBg,
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    MainContentPane(
                                        showDrawerButton = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            if (isCompactPcSidebarOpen) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { isCompactPcSidebarOpen = false }
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = isCompactPcSidebarOpen,
                                enter = slideInHorizontally(initialOffsetX = { -it }),
                                exit = slideOutHorizontally(targetOffsetX = { -it })
                            ) {
                                Surface(
                                    color = palette.primaryBg,
                                    shadowElevation = 12.dp,
                                    modifier = Modifier
                                        .width((platformProfile.windowWidthDp - 44).coerceIn(260, 312).dp)
                                        .fillMaxHeight()
                                ) {
                                    SidebarPanel(
                                        modifier = Modifier.fillMaxSize(),
                                        closeDrawerAfterSelect = false,
                                        onDismissCompactSidebar = { isCompactPcSidebarOpen = false }
                                    )
                                }
                            }
                        }
                    }
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
    }

    LaunchedEffect(showGlobalSearchSheet) {
        if (showGlobalSearchSheet) {
            globalSearchViewModel.refreshInitial()
        }
    }

    // Global Search Sheet (10 Permission-Aware Domains + Engine Abstraction)
    if (showGlobalSearchSheet) {
        RavenGlobalSearchSheet(
            uiState = globalSearchUiState,
            activeServerId = activeServer.id.takeIf { !activeServer.isDmTab },
            activeServerName = activeServer.name,
            isDmTab = activeServer.isDmTab,
            onQueryChange = { globalSearchViewModel.onQueryChanged(it) },
            onSubmitQuery = { globalSearchViewModel.onSubmitQuery(it) },
            onApplySuggestion = { globalSearchViewModel.onApplySuggestion(it) },
            onToggleType = { globalSearchViewModel.onToggleType(it) },
            onSelectDateRange = { range, s, e -> globalSearchViewModel.onSelectDateRange(range, s, e) },
            onSetAuthorFilter = { globalSearchViewModel.onSetAuthorFilter(it) },
            onSetSortOrder = { globalSearchViewModel.onSetSortOrder(it) },
            onSetServerScope = { globalSearchViewModel.onSetServerScope(it) },
            onSetMessageSubFilter = { globalSearchViewModel.onSetMessageSubFilter(it) },
            onClearFilters = { globalSearchViewModel.onClearFilters() },
            onLoadNextPage = { globalSearchViewModel.onLoadNextPage() },
            onDeleteHistory = { globalSearchViewModel.onDeleteHistory(it) },
            onClearHistory = { globalSearchViewModel.onClearHistory() },
            onResultClick = { res ->
                globalSearchViewModel.onOpenResult(res)
                showGlobalSearchSheet = false
                when (res.type) {
                    SearchEntityType.USER -> {
                        res.userPayload?.let { selectedUserProfile = it }
                    }
                    SearchEntityType.SERVER -> {
                        val srv = res.serverId?.let { id -> servers.find { it.id == id } }
                        if (srv != null) {
                            isViewingCommunityHome = false
                            repository.selectServer(srv.id)
                        }
                    }
                    SearchEntityType.COMMUNITY -> {
                        val joinedSrv = res.serverId?.let { id -> servers.find { it.id == id } }
                        if (joinedSrv != null) {
                            repository.selectServer(joinedSrv.id)
                            isViewingCommunityHome = true
                        } else if (res.publicListingPayload != null) {
                            communityViewModel.joinPublicCommunity(res.publicListingPayload) {
                                isViewingCommunityHome = true
                            }
                        } else {
                            showExploreServersSheet = true
                        }
                    }
                    SearchEntityType.CHANNEL -> {
                        val srv = res.serverId?.let { id -> servers.find { it.id == id } }
                        val ch = srv?.channels?.find { it.id == res.channelId || it.id == res.targetId }
                        if (srv != null && ch != null) {
                            isViewingCommunityHome = false
                            repository.selectServer(srv.id)
                            if (ch.type == ChannelType.VOICE || ch.type == ChannelType.STAGE) {
                                if (repository.joinVoiceChannel(ch)) {
                                    requestAudioPermission()
                                    setViewingVoiceRoom(true)
                                }
                            } else {
                                repository.selectChannel(ch)
                            }
                        }
                    }
                    SearchEntityType.MESSAGE -> {
                        if (!res.conversationId.isNullOrBlank()) {
                            isViewingCommunityHome = false
                            repository.selectServer("dm_tab")
                            repository.selectDm(res.conversationId)
                        } else {
                            val srv = res.serverId?.let { id -> servers.find { it.id == id } }
                            val ch = srv?.channels?.find { it.id == res.channelId || it.id == res.targetId }
                            if (srv != null && ch != null) {
                                isViewingCommunityHome = false
                                repository.selectServer(srv.id)
                                repository.selectChannel(ch)
                            }
                        }
                    }
                    SearchEntityType.CONVERSATION -> {
                        val convId = res.conversationId ?: res.targetId
                        if (!convId.isNullOrBlank()) {
                            isViewingCommunityHome = false
                            repository.selectServer("dm_tab")
                            repository.selectDm(convId)
                        }
                    }
                    SearchEntityType.EVENT -> {
                        val srv = res.serverId?.let { id -> servers.find { it.id == id } }
                        if (srv != null) {
                            repository.selectServer(srv.id)
                        }
                        showServerEventsSheet = true
                    }
                    SearchEntityType.STORY -> {
                        val authorId = res.storyPayload?.authorId ?: res.authorId
                        if (!authorId.isNullOrBlank()) {
                            viewingStoryAuthorId = authorId
                        }
                    }
                    SearchEntityType.BOT -> {
                        res.botPayload?.let { botViewModel.selectBot(it.id) }
                        showBotDashboardSheet = true
                    }
                    SearchEntityType.MARKETPLACE -> {
                        val srvPair = res.serverShopPayload
                        if (srvPair != null && servers.any { it.id == srvPair.first }) {
                            repository.selectServer(srvPair.first)
                            showServerShopSheet = true
                        } else {
                            showOrbsShopDialog = true
                        }
                    }
                    SearchEntityType.ALL -> Unit
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
            currentUserId = currentUser.id,
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
            onToggleServerClosed = { closed ->
                communityViewModel.setServerClosed(activeServer.id, closed)
            },
            onOpenFullAdminDashboard = { showServerAdminDashboard = true },
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

    // RAVEN Events Hub Sheet (Global + Server + Voice + Private Invites + History)
    if (showServerEventsSheet) {
        val knownFriends = remember(directConversations, friendUiState.friends, currentUser.id) {
            (friendUiState.friends.map { it.user } + directConversations.filter { !it.isGroup }.map { it.friend })
                .distinctBy { it.id }
                .filter { it.id != currentUser.id }
        }
        RavenEventsHubSheet(
            eventViewModel = eventViewModel,
            activeServer = activeServer.takeIf { !it.isDmTab },
            currentUserId = currentUser.id,
            knownFriends = knownFriends,
            onRequestNotificationPermission = { requestNotificationPermission() },
            onJoinVoiceChannel = { ch ->
                if (repository.joinVoiceChannel(ch)) {
                    requestAudioPermission()
                    setViewingVoiceRoom(true)
                }
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
            activePluginsCount = pluginUiState.activeCount,
            onOpenPluginsManager = { showPluginsSheet = true },
            onRequestNotificationPermission = { requestNotificationPermission() },
            onSyncToCloud = { repository.syncFullStateToFirestore() },
            onRestoreFromCloud = { repository.loadFullStateFromFirestore() },
            onUpdateSettings = { transform -> repository.updateAppSettings(transform) },
            onOpenProfile = { selectedUserProfile = currentUser },
            friendRequestPolicyLabel = friendUiState.privacySettings.friendRequestPolicy.label,
            onOpenFriendPrivacy = { showFriendPrivacySheet = true },
            activeSessionsCount = securityUiState.activeSessionsCount.coerceAtLeast(1),
            openSecurityAlertsCount = securityUiState.openAlertsCount,
            onOpenAccountSecurity = { showAccountSecuritySheet = true },
            platformProfile = activePlatformProfile,
            onDismiss = { showAppSettingsDialog = false }
        )
    }

    if (showUserDirectorySheet) {
        UserDirectorySearchSheet(
            searchQuery = friendUiState.searchQuery,
            results = friendUiState.directoryResults,
            onSearchQueryChange = { friendViewModel.setSearchQuery(it) },
            onSendFriendRequest = { user -> friendViewModel.sendFriendRequestToUser(user) },
            onAcceptRequest = { reqId -> friendViewModel.acceptRequest(reqId) },
            onDeclineRequest = { reqId -> friendViewModel.declineRequest(reqId) },
            onCancelRequest = { reqId -> friendViewModel.cancelRequest(reqId) },
            onOpenDm = { user -> friendViewModel.openDm(user) },
            onUnblockUser = { uid -> friendViewModel.unblockUser(uid) },
            onOpenProfile = { entry -> selectedUserProfile = entry.user },
            onDismiss = { showUserDirectorySheet = false }
        )
    }

    if (showFriendPrivacySheet) {
        FriendPrivacySettingsSheet(
            privacy = friendUiState.privacySettings,
            onSavePrivacy = { updated -> friendViewModel.updatePrivacy(updated) },
            onDismiss = { showFriendPrivacySheet = false }
        )
    }

    if (showPluginsSheet) {
        RavenPluginsSheet(
            pluginViewModel = pluginViewModel,
            onDismiss = { showPluginsSheet = false }
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
        val relStatus = if (isSelf) FriendRelationStatus.NONE else friendViewModel.relationStatusFor(targetUser.id)
        val pendingReq = if (isSelf) null else friendViewModel.pendingRequestFor(targetUser.id)
        val friendRel = friendUiState.friends.find { it.friendUid == targetUser.id }
        val lastActiveMs = friendRel?.lastActiveAtMs ?: targetUser.lastActiveAtMs
        val lastActivitySubtitle = if (lastActiveMs > 0L) {
            FriendFirestoreSync.formatRelativeActivityTime(lastActiveMs)
        } else ""

        UserProfileDialog(
            user = if (isSelf) currentUser else (friendRel?.user ?: targetUser),
            isCurrentUser = isSelf,
            onDismiss = { selectedUserProfile = null },
            onUpdateStatus = { status, customStatus -> repository.updateUserStatus(status, customStatus) },
            onSaveFullProfile = { name, pronouns, bio, avUrl, avCol, banUrl, banCol, statusMsg, emoji, activity, email, phone ->
                val sensitiveChanged = email != currentUser.email || phone != currentUser.phone
                val mutationAllowed = if (sensitiveChanged) {
                    accountSecurityRepo.validateSensitiveAccountMutation("Modification email/téléphone").isSuccess
                } else true
                if (mutationAllowed) {
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
                } else {
                    showAccountSecuritySheet = true
                }
            },
            onStartDirectMessage = { friend ->
                selectedUserProfile = null
                friendViewModel.openDm(friend)
            },
            onToggleBadge = { badgeId -> repository.toggleUserBadge(badgeId) },
            onOpenOrbsShop = { showOrbsShopDialog = true },
            onOpenAppSettings = if (isSelf) {
                { showAppSettingsDialog = true }
            } else null,
            privateNote = privateUserNotes[targetUser.id].orEmpty(),
            onSavePrivateNote = { note -> friendViewModel.updatePrivateNote(targetUser.id, note) },
            onSaveClanTag = { tag -> repository.updateClanTag(tag) },
            onFirebaseAuthChanged = { repository.syncFirebaseUser() },
            friendRelationStatus = relStatus,
            showOnlineStatus = if (isSelf) friendUiState.privacySettings.showOnlineStatus else true,
            lastActivityDisplay = lastActivitySubtitle,
            onSendFriendRequest = { friendViewModel.sendFriendRequestToUser(targetUser) },
            onAcceptFriendRequest = { pendingReq?.let { friendViewModel.acceptRequest(it.id) } },
            onDeclineFriendRequest = { pendingReq?.let { friendViewModel.declineRequest(it.id) } },
            onCancelFriendRequest = { pendingReq?.let { friendViewModel.cancelRequest(it.id) } },
            onRemoveFriend = {
                friendViewModel.removeFriend(targetUser.id)
                selectedUserProfile = null
            },
            onBlockUser = {
                friendViewModel.blockUser(targetUser)
                selectedUserProfile = null
            },
            onUnblockUser = { friendViewModel.unblockUser(targetUser.id) }
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

    // Server Background & Theme Dialog (preset themes or gallery)
    if (showServerBackgroundDialog && !activeServer.isDmTab) {
        val currentServerBg = resolveServerBackgroundTheme(
            server = activeServer,
            channelId = null,
            userBackgrounds = chatBackgrounds
        )
        val canManageOfficial = CommunityPermissions.isCreatorOrAdmin(activeServer, currentUser.id) ||
            CommunityPermissions.hasPermission(activeServer, currentUser.id, RolePermission.MANAGE_COMMUNITY)
        ChatBackgroundDialog(
            currentTheme = currentServerBg,
            title = "Fond du serveur • ${activeServer.name}",
            subtitle = "Choisissez un thème ou une photo de votre galerie",
            allowServerScopeToggle = true,
            initialApplyToWholeServer = true,
            canSetOfficialServerDefault = canManageOfficial,
            onApplyTheme = { theme ->
                repository.setChatBackground(activeChannel.id, theme)
            },
            onApplyServerWide = { theme, makeOfficial ->
                repository.setChatBackground(serverBackgroundTargetKey(activeServer.id), theme)
                if (makeOfficial && canManageOfficial) {
                    val isPreset = ShopCatalog.initialChatThemes.any { it.id == theme.id && it.imageUrl.isNullOrBlank() }
                    communityViewModel.updateServerOfficialBackground(
                        server = activeServer,
                        backgroundThemeId = if (isPreset && theme.imageUrl.isNullOrBlank()) theme.id else null,
                        backgroundImageUrl = theme.imageUrl,
                        backgroundOpacity = theme.opacity
                    )
                }
            },
            onResetTheme = {
                repository.clearChatBackground(serverBackgroundTargetKey(activeServer.id))
                repository.clearChatBackground(activeChannel.id)
                if (canManageOfficial) {
                    communityViewModel.updateServerOfficialBackground(
                        server = activeServer,
                        backgroundThemeId = null,
                        backgroundImageUrl = null,
                        backgroundOpacity = 0.50f
                    )
                }
            },
            onDismiss = { showServerBackgroundDialog = false }
        )
    }

    // Create Story Dialog (Text, Image, Video, TTL, Privacy)
    if (showCreateStoryDialog) {
        val storyFriends = remember(directConversations, friendUiState.friends) {
            (friendUiState.friends.map { it.user } + directConversations.filter { !it.isGroup }.map { it.friend })
                .distinctBy { it.id }
                .filter { it.id != currentUser.id }
        }
        CreateStoryDialog(
            friends = storyFriends,
            isBusy = storyUiState.isBusy,
            onDismiss = { showCreateStoryDialog = false },
            onPublishText = { txt, cap, pCol, sCol, vis, allowed, hrs ->
                storyViewModel.publishTextStory(txt, cap, pCol, sCol, vis, allowed, hrs) {
                    showCreateStoryDialog = false
                }
            },
            onPublishMedia = { uri, type, cap, vis, allowed, hrs ->
                storyViewModel.publishMediaStory(context, uri, type, cap, vis, allowed, hrs) {
                    showCreateStoryDialog = false
                }
            }
        )
    }

    // Fullscreen Story Viewer
    viewingStoryAuthorId?.let { startAuthor ->
        val viewerGroups = storyUiState.allGroups
        if (viewerGroups.isNotEmpty()) {
            StoryViewerDialog(
                groups = viewerGroups,
                initialAuthorId = startAuthor,
                currentUserId = currentUser.id,
                isModerator = storyUiState.isModerator,
                loadedViewers = storyUiState.activeStoryViewers,
                onMarkStoryViewed = { storyId -> storyViewModel.markStoryViewed(storyId) },
                onLoadViewers = { storyId -> storyViewModel.loadStoryViewers(storyId) },
                onDeleteStory = { storyId -> storyViewModel.deleteStory(storyId) },
                onReportStory = { storyId, reason, details -> storyViewModel.reportStory(storyId, reason, details) },
                onModerateHideStory = { storyId, reason -> storyViewModel.moderateStory(storyId, true, reason) },
                onReplyToStory = { storyId, reply -> storyViewModel.replyToStory(storyId, reply) },
                onToggleLike = { storyId -> storyViewModel.toggleStoryLike(storyId) },
                onDismiss = { viewingStoryAuthorId = null }
            )
        } else {
            viewingStoryAuthorId = null
        }
    }

    // Story Moderation & Cleanup Sheet
    if (showStoryModerationSheet) {
        StoryModerationSheet(
            pendingReports = storyUiState.pendingReports,
            allReports = storyUiState.allReports,
            onModerateStory = { storyId, hideOnly, reason, reportId ->
                storyViewModel.moderateStory(storyId, hideOnly, reason, reportId)
            },
            onDismissReport = { reportId -> storyViewModel.dismissReport(reportId) },
            onRunCleanup = { storyViewModel.runManualCleanup() },
            onDismiss = { showStoryModerationSheet = false }
        )
    }

    // Complete Server Customization & Administration Dashboard
    if (showServerAdminDashboard && !activeServer.isDmTab) {
        ServerAdminDashboardSheet(
            server = activeServer,
            currentUser = currentUser,
            communityUiState = communityUiState,
            onUpdateIdentityAndTheme = { name, handle, desc, iconText, iconPreset, imgUrl, banUrl, accentHex, secHex, style, catTag, bgThemeId, bgImgUrl, bgOpacity ->
                communityViewModel.updateIdentityAndTheme(
                    activeServer.id, name, handle, desc, iconText, iconPreset,
                    imgUrl, banUrl, accentHex, secHex, style, catTag, bgThemeId, bgImgUrl, bgOpacity
                )
            },
            onUpsertCategory = { catId, name, isPriv, roleIds ->
                communityViewModel.upsertCategory(activeServer.id, catId, name, isPriv, roleIds)
            },
            onMoveCategoryOrder = { catId, delta ->
                communityViewModel.moveCategoryOrder(activeServer.id, catId, delta)
            },
            onDeleteCategory = { catName ->
                communityViewModel.deleteCategory(activeServer.id, catName)
            },
            onUpsertChannel = { chId, name, topic, type, catName, iconKey, slow, readOnly, isPriv, roles, roleIds, maxP, bit ->
                communityViewModel.upsertChannelCustomization(
                    activeServer.id, chId, name, topic, type, catName, iconKey,
                    slow, readOnly, isPriv, roles, roleIds, maxP, bit
                )
            },
            onMoveChannelOrder = { chId, delta ->
                communityViewModel.moveChannelOrder(activeServer.id, chId, delta)
            },
            onDeleteChannel = { chId ->
                communityViewModel.deleteChannel(activeServer.id, chId)
            },
            onSaveOrUpdateRole = { name, colHex, secHex, perms, existingId ->
                communityViewModel.saveOrUpdateCustomRole(activeServer.id, name, colHex, secHex, perms, existingId)
            },
            onMoveRoleOrder = { roleId, delta ->
                communityViewModel.moveRoleOrder(activeServer.id, roleId, delta)
            },
            onDeleteCustomRole = { roleId ->
                communityViewModel.deleteCustomRole(activeServer.id, roleId)
            },
            onUpdateWelcomeAndGoodbye = { cfg, homeWelcome ->
                communityViewModel.updateWelcomeAndGoodbye(activeServer.id, cfg, homeWelcome)
            },
            onUpdateNotificationConfig = { cfg ->
                communityViewModel.updateNotificationConfig(activeServer.id, cfg)
            },
            onUpdateRulesAndVerification = { rules, guidelines, reqAcc, verif ->
                communityViewModel.updateRulesAndVerification(activeServer.id, rules, guidelines, reqAcc, verif)
            },
            onUpdateModerationConfig = { cfg ->
                communityViewModel.updateModerationConfig(activeServer.id, cfg)
            },
            onUpdatePrivacyConfig = { pub, cfg ->
                communityViewModel.updatePrivacyConfig(activeServer.id, pub, cfg)
            },
            onToggleServerClosed = { closed ->
                communityViewModel.setServerClosed(activeServer.id, closed)
            },
            onDismiss = { showServerAdminDashboard = false }
        )
    }

    if (showPollsHubSheet) {
        PollsHubSheet(
            pollViewModel = pollViewModel,
            activeServer = activeServer,
            activeChannel = activeChannel,
            onDismiss = { showPollsHubSheet = false }
        )
    }

    if (showAdvancedPollEditor) {
        AdvancedPollEditorDialog(
            server = activeServer.takeIf { !it.isDmTab },
            channel = if (!activeServer.isDmTab) activeChannel else null,
            initialDraft = editingPollDraft,
            onDismiss = {
                showAdvancedPollEditor = false
                editingPollDraft = null
            },
            onSaveDraft = { input -> pollViewModel.saveDraft(input) },
            onPublish = { input, shareInChannel ->
                pollViewModel.createAndPublish(input, shareInChannel)
            }
        )
    }

    if (showAccountSecuritySheet) {
        AccountSecurityHubSheet(
            securityViewModel = accountSecurityViewModel,
            isFirebaseSignedIn = isFirebaseSignedIn,
            firebaseUserEmail = firebaseUserEmail,
            onDismiss = { showAccountSecuritySheet = false }
        )
    }

    securityUiState.pendingConfirmation?.let { pending ->
        SensitiveActionConfirmationDialog(
            pending = pending,
            hasSecurityPinConfigured = !securityUiState.securityProfile.pinHash.isNullOrBlank(),
            isFirebaseSignedIn = isFirebaseSignedIn,
            failedAttempts = securityUiState.securityProfile.failedChallengeCount,
            errorMessage = securityUiState.errorMessage,
            onConfirmWithPin = { pin -> accountSecurityViewModel.confirmWithSecurityPin(pin) },
            onConfirmWithGoogleReauth = { ok -> accountSecurityViewModel.confirmWithGoogleReauth(ok) },
            onConfirmExplicitly = { accountSecurityViewModel.confirmExplicitlyWhenNoPinOrCloud() },
            onDismiss = { accountSecurityViewModel.dismissPendingConfirmation() }
        )
    }

    securityUiState.remoteRevocationMessage?.let { revReason ->
        AlertDialog(
            onDismissRequest = { accountSecurityViewModel.clearRemoteRevocationMessage() },
            title = { Text("Session déconnectée", fontWeight = FontWeight.Bold) },
            text = { Text(revReason) },
            confirmButton = {
                Button(onClick = { accountSecurityViewModel.clearRemoteRevocationMessage() }) {
                    Text("Compris")
                }
            }
        )
    }
}

@Composable
private fun DiscordSidebarNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = ravenPalette()
    val platform = LocalRavenPlatform.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.inputBg else Color.Transparent)
            .desktopHoverHighlight(
                shape = RoundedCornerShape(8.dp),
                enabled = platform.isPc || platform.hasMouseOrTrackpad
            )
            .desktopHoverPointer(platform.isPc)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.textPrimary else palette.textMuted,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (selected) palette.textPrimary else palette.textMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
