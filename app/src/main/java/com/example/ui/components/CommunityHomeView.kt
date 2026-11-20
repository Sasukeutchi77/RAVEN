package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.EventViewModel
import com.example.ui.viewmodel.PollViewModel

@Composable
fun CommunityHomeView(
    server: Server,
    currentUser: User,
    unreadNotificationsCount: Int,
    showDrawerButton: Boolean,
    onToggleDrawer: () -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onEditCommunity: () -> Unit,
    onOpenInvites: () -> Unit,
    onOpenRolesMatrix: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    onLeaveCommunity: () -> Unit,
    onPublishAnnouncement: (title: String, content: String, isPinned: Boolean) -> Unit,
    onDeleteAnnouncement: (announcementId: String) -> Unit,
    onCreateEvent: (title: String, desc: String, dateTime: String, location: String) -> Unit,
    onToggleEventInterest: (eventId: String) -> Unit,
    onTimeoutMember: (uid: String) -> Unit,
    onKickMember: (uid: String) -> Unit,
    onMemberClick: (User) -> Unit,
    pollViewModel: PollViewModel? = null,
    onOpenPollsHub: () -> Unit = {},
    eventViewModel: EventViewModel? = null,
    knownFriends: List<User> = emptyList(),
    onRequestNotificationPermission: () -> Unit = {},
    onJoinVoiceChannel: (Channel) -> Unit = {},
    onOpenEventsHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }

    val isOwner = CommunityPermissions.isOwner(server, currentUser.id)
    val canManageCommunity = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_COMMUNITY)
    val canManageRoles = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_ROLES)
    val canManageChannels = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_CHANNELS)
    val canManageInvites = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_INVITES)
    val canPostAnnouncements = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.POST_ANNOUNCEMENTS)
    val canManageEvents = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_EVENTS)
    val canModerate = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MODERATE_MEMBERS)

    val onlineCount = remember(server.members) { CommunityPermissions.onlineMembersCount(server) }
    val accentColor = Color(server.customBannerColorHex ?: server.iconBgColor)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.chatBg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .testTag("community_home_view")
    ) {
        // Top App Header
        Surface(color = palette.sidebarBg, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDrawerButton) {
                    IconButton(
                        onClick = onToggleDrawer,
                        modifier = Modifier.size(48.dp).testTag("community_home_drawer_btn")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = palette.textPrimary)
                    }
                }
                Icon(Icons.Default.Home, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (server.uniqueHandle.isNotBlank()) {
                        Text("@${server.uniqueHandle}", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                IconButton(onClick = onOpenNotifications, modifier = Modifier.size(48.dp).testTag("community_home_notif_btn")) {
                    BadgedBox(badge = { if (unreadNotificationsCount > 0) Badge { Text("$unreadNotificationsCount") } }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = palette.textSecondary)
                    }
                }
                IconButton(onClick = onOpenInvites, modifier = Modifier.size(48.dp).testTag("community_home_invites_btn")) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Invitations", tint = palette.accent)
                }
                if (canManageCommunity || canManageRoles || canManageChannels || isOwner) {
                    IconButton(
                        onClick = onOpenAdminDashboard,
                        modifier = Modifier.size(48.dp).testTag("community_home_admin_dashboard_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Administration avancée",
                            tint = accentColor
                        )
                    }
                } else {
                    IconButton(
                        onClick = onLeaveCommunity,
                        modifier = Modifier.size(48.dp).testTag("community_home_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Quitter la communauté",
                            tint = DiscordRed
                        )
                    }
                }
            }
        }

        // Scrollable Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = palette.sidebarBg,
            contentColor = accentColor,
            edgePadding = 12.dp
        ) {
            listOf("Accueil", "Annonces", "Événements", "Membres", "Salons", "Sondages").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    modifier = Modifier.testTag("community_tab_$idx")
                )
            }
        }

        if (selectedTab == 5) {
            if (pollViewModel != null) {
                val pollUiState by pollViewModel.uiState.collectAsState()
                var showInlinePollEditor by remember { mutableStateOf(false) }
                var inlineEditingDraft by remember { mutableStateOf<AdvancedPoll?>(null) }
                var inlineInspectPoll by remember { mutableStateOf<Pair<AdvancedPoll, Boolean>?>(null) }

                PollsFeedContent(
                    pollViewModel = pollViewModel,
                    uiState = pollUiState,
                    serverFilterId = server.id,
                    accentColor = accentColor,
                    onCreateNew = {
                        inlineEditingDraft = null
                        showInlinePollEditor = true
                    },
                    onEditDraft = { draft ->
                        inlineEditingDraft = draft
                        showInlinePollEditor = true
                    },
                    onOpenStats = { poll, startExport ->
                        inlineInspectPoll = poll to startExport
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                if (showInlinePollEditor) {
                    AdvancedPollEditorDialog(
                        server = server,
                        channel = server.channels.firstOrNull { it.type == ChannelType.TEXT },
                        initialDraft = inlineEditingDraft,
                        onDismiss = {
                            showInlinePollEditor = false
                            inlineEditingDraft = null
                        },
                        onSaveDraft = { input -> pollViewModel.saveDraft(input) },
                        onPublish = { input, shareInChannel ->
                            pollViewModel.createAndPublish(input, shareInChannel)
                        }
                    )
                }

                inlineInspectPoll?.let { (poll, startExport) ->
                    val freshPoll = pollUiState.polls.find { it.id == poll.id } ?: poll
                    val stats = remember(freshPoll, pollUiState.votesByPoll) { pollViewModel.computeStats(freshPoll) }
                    AdvancedPollStatsAndExportSheet(
                        poll = freshPoll,
                        stats = stats,
                        startOnExportTab = startExport,
                        exportContentProvider = { fmt -> pollViewModel.exportPoll(freshPoll, fmt) },
                        onDismiss = { inlineInspectPoll = null }
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onOpenPollsHub,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Poll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ouvrir les sondages avancés")
                    }
                }
            }
        } else if (selectedTab == 2 && eventViewModel != null) {
            val context = LocalContext.current
            val eventUiState by eventViewModel.uiState.collectAsState()
            var showInlineEventEditor by remember { mutableStateOf(false) }
            var inlineEditingEvent by remember { mutableStateOf<RavenEvent?>(null) }
            var inlineInspectedEventId by remember { mutableStateOf<String?>(null) }

            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                EventsFeedContent(
                    uiState = eventUiState,
                    currentUserId = currentUser.id,
                    serverFilterId = server.id,
                    canCreateEvent = canManageEvents,
                    titleLabel = "Événements • ${server.name}",
                    onSelectFilter = { eventViewModel.setFilter(it) },
                    onSearchChange = { eventViewModel.setSearchQuery(it) },
                    onOpenCreate = {
                        inlineEditingEvent = null
                        showInlineEventEditor = true
                    },
                    onOpenDetail = { ev ->
                        eventViewModel.inspectEvent(ev.id)
                        inlineInspectedEventId = ev.id
                    },
                    onQuickRsvp = { ev, status ->
                        onRequestNotificationPermission()
                        eventViewModel.rsvp(ev.id, status)
                    },
                    onJoinVoice = { chId ->
                        val ch = server.channels.find { it.id == chId }
                        if (ch != null) onJoinVoiceChannel(ch)
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onOpenEventsHub,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).testTag("open_global_events_hub_btn")
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tous les événements & invitations privées", fontSize = 12.sp)
                }
            }

            if (showInlineEventEditor) {
                RavenEventEditorDialog(
                    server = server,
                    existingEvent = inlineEditingEvent,
                    knownFriends = knownFriends,
                    currentUserId = currentUser.id,
                    isBusy = eventUiState.isBusy,
                    onDismiss = {
                        showInlineEventEditor = false
                        inlineEditingEvent = null
                    },
                    onSubmit = { input, uri ->
                        onRequestNotificationPermission()
                        val target = inlineEditingEvent
                        if (target != null) {
                            eventViewModel.updateEvent(target.id, input, uri, context) {
                                showInlineEventEditor = false
                                inlineEditingEvent = null
                            }
                        } else {
                            eventViewModel.createEvent(input, uri, context) {
                                showInlineEventEditor = false
                            }
                        }
                    }
                )
            }

            inlineInspectedEventId?.let { evId ->
                val freshEv = eventUiState.events.find { it.id == evId }
                if (freshEv == null) {
                    inlineInspectedEventId = null
                } else {
                    RavenEventDetailSheet(
                        event = freshEv,
                        currentUserId = currentUser.id,
                        canManage = eventViewModel.canManage(freshEv),
                        rsvpRecords = eventUiState.rsvpsByEvent[freshEv.id].orEmpty(),
                        reminders = eventUiState.myRemindersByEvent[freshEv.id].orEmpty(),
                        historyEntries = eventUiState.historyByEvent[freshEv.id].orEmpty(),
                        serverMembers = server.members,
                        shareChannels = server.channels,
                        onRsvp = { st ->
                            onRequestNotificationPermission()
                            eventViewModel.rsvp(freshEv.id, st)
                        },
                        onToggleReminder = { mins ->
                            onRequestNotificationPermission()
                            eventViewModel.toggleReminder(freshEv.id, mins)
                        },
                        onEdit = {
                            inlineEditingEvent = freshEv
                            inlineInspectedEventId = null
                            showInlineEventEditor = true
                        },
                        onCancelEvent = { reason -> eventViewModel.cancelEvent(freshEv.id, reason) },
                        onDeleteEvent = {
                            eventViewModel.deleteEvent(freshEv.id) {
                                inlineInspectedEventId = null
                            }
                        },
                        onJoinVoiceChannel = { chId ->
                            val ch = server.channels.find { it.id == chId }
                            if (ch != null) {
                                inlineInspectedEventId = null
                                onJoinVoiceChannel(ch)
                            }
                        },
                        onShareInChannel = { chId -> eventViewModel.shareInChannel(freshEv.id, chId) },
                        onDismiss = { inlineInspectedEventId = null }
                    )
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Banner always at top of Home tab
            if (selectedTab == 0) {
                item {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().height(136.dp)) {
                                if (!server.bannerUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = server.bannerUrl,
                                        contentDescription = "Bannière de ${server.name}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(
                                            Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.45f), palette.sidebarBg))
                                        )
                                    )
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (server.isPublic) Icons.Default.Public else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (server.isPublic) "Publique" else "Privée",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(accentColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!server.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = server.imageUrl,
                                                contentDescription = "Logo ${server.name}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(server.iconText.take(2), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(server.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                            if (server.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.Verified, contentDescription = "Vérifiée", tint = Color(0xFF00D4AA), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(
                                            text = "${server.categoryTag} • ${if (server.uniqueHandle.isNotBlank()) "@${server.uniqueHandle}" else server.inviteCode}",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(server.homeWelcomeMessage, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(server.description, color = palette.textSecondary, fontSize = 13.sp)

                                // Live stats strip
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CommunityStatPill(
                                        dotColor = DiscordGreen,
                                        label = "$onlineCount en ligne",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CommunityStatPill(
                                        dotColor = accentColor,
                                        label = "${server.members.size} membres",
                                        modifier = Modifier.weight(1f)
                                    )
                                    CommunityStatPill(
                                        dotColor = DiscordAmber,
                                        label = "${server.channels.size} salons",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Staff quick tools row
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (canManageInvites) {
                                        FilledTonalButton(
                                            onClick = onOpenInvites,
                                            modifier = Modifier.weight(1f).testTag("home_manage_invites_btn")
                                        ) {
                                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Invitations", fontSize = 11.sp)
                                        }
                                    }
                                    if (canManageRoles) {
                                        FilledTonalButton(
                                            onClick = onOpenRolesMatrix,
                                            modifier = Modifier.weight(1f).testTag("home_manage_roles_btn")
                                        ) {
                                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Rôles", fontSize = 11.sp)
                                        }
                                    }
                                    if (canManageChannels) {
                                        FilledTonalButton(
                                            onClick = onOpenCategories,
                                            modifier = Modifier.weight(1f).testTag("home_manage_channels_btn")
                                        ) {
                                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salons", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // General info & Rules card
                item {
                    Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Règlement & Sécurité", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Surface(color = accentColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "Vérif. ${server.customization.verificationLevel.label}",
                                        color = accentColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (server.serverRulesList.isNotEmpty()) {
                                server.serverRulesList.forEachIndexed { idx, rule ->
                                    Text("${idx + 1}. $rule", color = palette.textSecondary, fontSize = 13.sp)
                                }
                            } else {
                                Text(server.rulesGuidelines, color = palette.textSecondary, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = palette.border)
                            Text(
                                text = "Code d'invitation principal : ${server.inviteCode} • Niveau Boost : ${server.boostLevel}",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (server.announcements.isNotEmpty()) {
                    item {
                        val latest = server.announcements.first()
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { selectedTab = 1 }
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("DERNIÈRE ANNONCE", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                Text(latest.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(latest.content, color = palette.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Tab 1: Announcements
            if (selectedTab == 1) {
                if (canPostAnnouncements) {
                    item {
                        Button(
                            onClick = { showAnnouncementDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("publish_announcement_open_btn")
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publier une annonce officielle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (server.announcements.isEmpty()) {
                    item {
                        Text("Aucune annonce publiée pour le moment.", color = palette.textMuted, fontSize = 13.sp)
                    }
                } else {
                    items(server.announcements, key = { it.id }) { ann ->
                        Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (ann.isPinned) {
                                            Icon(Icons.Default.PushPin, contentDescription = "Épinglée", tint = DiscordAmber, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(ann.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    if (canPostAnnouncements) {
                                        IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer annonce", tint = DiscordRed)
                                        }
                                    }
                                }
                                Text(ann.content, color = palette.textSecondary, fontSize = 13.sp)
                                Text(
                                    text = "Par ${ann.authorName} (${ann.authorRoleTitle}) • ${ann.timestampLabel}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Tab 2: Events
            if (selectedTab == 2) {
                if (canManageEvents) {
                    item {
                        Button(
                            onClick = { showEventDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create_community_event_open_btn")
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Planifier un événement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (server.events.isEmpty()) {
                    item { Text("Aucun événement planifié.", color = palette.textMuted, fontSize = 13.sp) }
                } else {
                    items(server.events, key = { it.id }) { evt ->
                        val isGoing = currentUser.id in evt.interestedUserIds
                        Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(evt.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(evt.description, color = palette.textSecondary, fontSize = 13.sp)
                                Text("${evt.dateTimeLabel} • ${evt.locationLabel}", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${evt.interestedUserIds.size} participant(s)", color = palette.textMuted, fontSize = 11.sp)
                                    FilledTonalButton(
                                        onClick = { onToggleEventInterest(evt.id) },
                                        modifier = Modifier.testTag("rsvp_event_${evt.id}")
                                    ) {
                                        Text(if (isGoing) "Inscrit" else "Participer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 3: Online & Members Directory
            if (selectedTab == 3) {
                val isStaffViewer = isOwner || CommunityPermissions.effectiveRole(server, currentUser.id) in setOf(UserRole.ADMIN, UserRole.MODERATOR)
                if (server.customization.privacy.hideMemberListForNonStaff && !isStaffViewer) {
                    item {
                        Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = DiscordAmber)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "La liste détaillée des membres est masquée par les paramètres de confidentialité du serveur.",
                                    color = palette.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "MEMBRES EN LIGNE ($onlineCount / ${server.members.size})",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val sorted = CommunityPermissions.sortedMembers(server)
                    items(sorted, key = { "mem_${it.id}" }) { member ->
                        val role = CommunityPermissions.effectiveRole(server, member.id)
                        val isTargetOwner = CommunityPermissions.isOwner(server, member.id)
                    val statusColor = when (member.status) {
                        UserStatus.ONLINE -> DiscordGreen
                        UserStatus.IDLE -> DiscordAmber
                        UserStatus.DND -> DiscordRed
                        UserStatus.OFFLINE -> DiscordTextDark
                    }
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.clickable { onMemberClick(member) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Box(
                                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(member.avatarColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(member.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier.size(11.dp).align(Alignment.BottomEnd).clip(CircleShape).background(statusColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(member.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val roleLabel = when {
                                        isTargetOwner -> "Créateur"
                                        member.customRoleId != null -> server.customRoles.find { it.id == member.customRoleId }?.name ?: role.title
                                        else -> role.title
                                    }
                                    Surface(color = accentColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = roleLabel,
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${member.status.label}${if (member.isTimedOut) " • TIMEOUT" else ""}",
                                    color = if (member.isTimedOut) DiscordRed else palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (canModerate && !isTargetOwner && member.id != currentUser.id) {
                                IconButton(onClick = { onTimeoutMember(member.id) }) {
                                    Icon(Icons.Default.TimerOff, contentDescription = "Timeout", tint = DiscordAmber, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onKickMember(member.id) }) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = "Expulser", tint = DiscordRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    }
                }
            }

            // Tab 4: Categories & Channels
            if (selectedTab == 4) {
                if (canManageChannels) {
                    item {
                        Button(
                            onClick = onOpenCategories,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home_tab_manage_categories_btn")
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gérer catégories & salons", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val grouped = server.channels.groupBy { it.category }
                val orderedCats = (server.categories.sortedBy { it.position }.map { it.name } + grouped.keys)
                    .distinct()
                    .filter { CommunityPermissions.isCategoryAccessible(server, it, currentUser.id) }
                orderedCats.forEach { cat ->
                    val chList = grouped[cat].orEmpty()
                        .filter { CommunityPermissions.isChannelAccessible(server, it, currentUser.id) }
                        .sortedBy { it.position }
                    item(key = "home_cat_$cat") {
                        Text(cat.uppercase(), color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    items(chList, key = { "home_ch_${it.id}" }) { ch ->
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.clickable { onOpenChannel(ch) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = resolveChannelIconVector(ch),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ch.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = when (ch.type) {
                                            ChannelType.VOICE -> "Salon vocal"
                                            ChannelType.STAGE -> "Scène audio"
                                            ChannelType.ANNOUNCEMENT -> "Annonces"
                                            else -> "Salon textuel"
                                        },
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "Ouvrir", tint = palette.textMuted)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showAnnouncementDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var pinned by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Publier une annonce", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Contenu") }, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Épingler à l'accueil", color = palette.textSecondary, fontSize = 13.sp)
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPublishAnnouncement(title, body, pinned)
                        showAnnouncementDialog = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) { Text("Publier") }
            },
            dismissButton = {
                TextButton(onClick = { showAnnouncementDialog = false }) { Text("Annuler") }
            }
        )
    }

    if (showEventDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var dateLabel by remember { mutableStateOf("Samedi • 21:00") }
        var locLabel by remember { mutableStateOf("Vocal Principal") }
        AlertDialog(
            onDismissRequest = { showEventDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Nouvel événement", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, minLines = 2)
                    OutlinedTextField(value = dateLabel, onValueChange = { dateLabel = it }, label = { Text("Date & heure") }, singleLine = true)
                    OutlinedTextField(value = locLabel, onValueChange = { locLabel = it }, label = { Text("Lieu / Salon") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateEvent(title, desc, dateLabel, locLabel)
                        showEventDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Planifier") }
            },
            dismissButton = {
                TextButton(onClick = { showEventDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun CommunityStatPill(dotColor: Color, label: String, modifier: Modifier = Modifier) {
    val palette = ravenPalette()
    Surface(
        color = palette.inputBg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
