package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.EventDateTimeUtils
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.EventViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenEventsHubSheet(
    eventViewModel: EventViewModel,
    activeServer: Server?,
    currentUserId: String,
    knownFriends: List<User> = emptyList(),
    onRequestNotificationPermission: () -> Unit,
    onJoinVoiceChannel: (Channel) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val uiState by eventViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showEditor by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<RavenEvent?>(null) }
    var inspectedEventId by remember { mutableStateOf<String?>(null) }

    val canCreateServerEvent = activeServer == null || activeServer.isDmTab ||
        CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MANAGE_EVENTS)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("raven_events_hub_sheet")
    ) {
        EventsFeedContent(
            uiState = uiState,
            currentUserId = currentUserId,
            serverFilterId = null,
            canCreateEvent = true,
            titleLabel = if (activeServer != null && !activeServer.isDmTab) "Événements • ${activeServer.name}" else "Événements RAVEN",
            onSelectFilter = { eventViewModel.setFilter(it) },
            onSearchChange = { eventViewModel.setSearchQuery(it) },
            onOpenCreate = {
                editingEvent = null
                showEditor = true
            },
            onOpenDetail = { ev ->
                eventViewModel.inspectEvent(ev.id)
                inspectedEventId = ev.id
            },
            onQuickRsvp = { ev, status ->
                onRequestNotificationPermission()
                eventViewModel.rsvp(ev.id, status)
            },
            onJoinVoice = { chId ->
                val ch = activeServer?.channels?.find { it.id == chId }
                if (ch != null) {
                    onDismiss()
                    onJoinVoiceChannel(ch)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        )
    }

    if (showEditor) {
        RavenEventEditorDialog(
            server = activeServer?.takeIf { !it.isDmTab && canCreateServerEvent },
            existingEvent = editingEvent,
            knownFriends = knownFriends,
            currentUserId = currentUserId,
            isBusy = uiState.isBusy,
            onDismiss = {
                showEditor = false
                editingEvent = null
            },
            onSubmit = { input, uri ->
                onRequestNotificationPermission()
                val target = editingEvent
                if (target != null) {
                    eventViewModel.updateEvent(target.id, input, uri, context) {
                        showEditor = false
                        editingEvent = null
                    }
                } else {
                    eventViewModel.createEvent(input, uri, context) {
                        showEditor = false
                    }
                }
            }
        )
    }

    inspectedEventId?.let { evId ->
        val freshEv = uiState.events.find { it.id == evId }
        if (freshEv == null) {
            inspectedEventId = null
        } else {
            RavenEventDetailSheet(
                event = freshEv,
                currentUserId = currentUserId,
                canManage = eventViewModel.canManage(freshEv),
                rsvpRecords = uiState.rsvpsByEvent[freshEv.id].orEmpty(),
                reminders = uiState.myRemindersByEvent[freshEv.id].orEmpty(),
                historyEntries = uiState.historyByEvent[freshEv.id].orEmpty(),
                serverMembers = activeServer?.members.orEmpty(),
                shareChannels = activeServer?.channels.orEmpty(),
                onRsvp = { st ->
                    onRequestNotificationPermission()
                    eventViewModel.rsvp(freshEv.id, st)
                },
                onToggleReminder = { mins ->
                    onRequestNotificationPermission()
                    eventViewModel.toggleReminder(freshEv.id, mins)
                },
                onEdit = {
                    editingEvent = freshEv
                    inspectedEventId = null
                    showEditor = true
                },
                onCancelEvent = { reason -> eventViewModel.cancelEvent(freshEv.id, reason) },
                onDeleteEvent = {
                    eventViewModel.deleteEvent(freshEv.id) {
                        inspectedEventId = null
                    }
                },
                onJoinVoiceChannel = { chId ->
                    val ch = activeServer?.channels?.find { it.id == chId }
                    if (ch != null) {
                        inspectedEventId = null
                        onDismiss()
                        onJoinVoiceChannel(ch)
                    }
                },
                onShareInChannel = { chId -> eventViewModel.shareInChannel(freshEv.id, chId) },
                onDismiss = { inspectedEventId = null }
            )
        }
    }
}

@Composable
fun EventsFeedContent(
    uiState: EventUiState,
    currentUserId: String,
    serverFilterId: String? = null,
    canCreateEvent: Boolean = true,
    titleLabel: String = "Événements RAVEN",
    onSelectFilter: (EventHubFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenDetail: (RavenEvent) -> Unit,
    onQuickRsvp: (RavenEvent, RsvpStatus) -> Unit,
    onJoinVoice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val now = System.currentTimeMillis()
    val query = uiState.searchQuery.trim()

    val baseList = remember(uiState.events, uiState.selectedFilter, serverFilterId, query, currentUserId) {
        val scoped = if (serverFilterId.isNullOrBlank()) uiState.events else uiState.events.filter {
            it.serverId == serverFilterId || (it.visibility == EventVisibility.PUBLIC && it.serverId == serverFilterId)
        }
        val filteredByTab = when (uiState.selectedFilter) {
            EventHubFilter.UPCOMING -> scoped.filter {
                val eff = it.effectiveStatus(now)
                eff == EventStatus.SCHEDULED || eff == EventStatus.ONGOING
            }.sortedBy { it.startAtMs }

            EventHubFilter.MY_EVENTS -> scoped.filter {
                currentUserId in it.goingIds || currentUserId in it.interestedIds ||
                    currentUserId in it.waitlistIds || it.creatorId == currentUserId
            }.sortedBy { it.startAtMs }

            EventHubFilter.PRIVATE -> scoped.filter {
                it.visibility == EventVisibility.PRIVATE_INVITE || !it.isPublic
            }.sortedBy { it.startAtMs }

            EventHubFilter.HISTORY -> scoped.filter {
                val eff = it.effectiveStatus(now)
                eff == EventStatus.COMPLETED || eff == EventStatus.CANCELLED
            }.sortedByDescending { it.startAtMs }
        }
        if (query.isBlank()) filteredByTab else filteredByTab.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.location.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header + Planifier CTA
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Event, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(titleLabel, color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${uiState.upcomingEvents.size} à venir • ${uiState.historyEvents.size} archivé(s)", color = palette.textMuted, fontSize = 11.sp)
                }
            }
            if (canCreateEvent) {
                Button(
                    onClick = onOpenCreate,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp).testTag("events_hub_create_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Planifier", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Feedback banner
        val feedback = uiState.errorMessage ?: uiState.feedbackMessage
        if (feedback != null) {
            val isErr = uiState.errorMessage != null
            Surface(
                color = (if (isErr) DiscordRed else DiscordGreen).copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isErr) DiscordRed else DiscordGreen)
            ) {
                Text(
                    text = feedback,
                    color = if (isErr) DiscordRed else DiscordGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )
            }
        }

        // Search
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Rechercher un événement, un lieu...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("events_search_input")
        )

        // Filter pills
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EventHubFilter.entries) { filter ->
                FilterChip(
                    selected = uiState.selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.label, fontSize = 12.sp) },
                    modifier = Modifier.testTag("events_filter_${filter.name}")
                )
            }
        }

        // Events List
        if (baseList.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (uiState.selectedFilter == EventHubFilter.HISTORY) {
                            "Aucun événement terminé ou annulé."
                        } else {
                            "Aucun événement dans cette vue."
                        },
                        color = palette.textMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(baseList, key = { it.id }) { event ->
                    RavenEventCard(
                        event = event,
                        currentUserId = currentUserId,
                        remindersCount = uiState.myRemindersByEvent[event.id].orEmpty().size,
                        onOpenDetail = { onOpenDetail(event) },
                        onQuickRsvp = { st -> onQuickRsvp(event, st) },
                        onJoinVoice = {
                            event.voiceChannelId?.let { onJoinVoice(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RavenEventCard(
    event: RavenEvent,
    currentUserId: String,
    remindersCount: Int = 0,
    onOpenDetail: () -> Unit,
    onQuickRsvp: (RsvpStatus) -> Unit,
    onJoinVoice: () -> Unit = {}
) {
    val palette = ravenPalette()
    val now = System.currentTimeMillis()
    val effStatus = event.effectiveStatus(now)
    val accent = Color(event.bannerColorHex)
    val myStatus = event.userRsvpStatus(currentUserId)
    val waitPos = event.waitlistPosition(currentUserId)
    val isFull = event.isFull()

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when (myStatus) {
                RsvpStatus.GOING -> DiscordGreen.copy(alpha = 0.7f)
                RsvpStatus.WAITLISTED -> DiscordAmber.copy(alpha = 0.7f)
                else -> palette.border
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("raven_event_card_${event.id}")
    ) {
        Column {
            // Optional Cover Banner Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (!event.imageUrl.isNullOrBlank()) 104.dp else 52.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.3f), palette.sidebarBg)
                        )
                    )
            ) {
                if (!event.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = event.imageUrl,
                        contentDescription = event.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = when (effStatus) {
                            EventStatus.ONGOING -> DiscordGreen
                            EventStatus.CANCELLED -> DiscordRed
                            EventStatus.COMPLETED -> Color.DarkGray
                            EventStatus.SCHEDULED -> Color.Black.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = EventDateTimeUtils.formatCountdown(event.startAtMs, event.endAtMs, now, effStatus),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (remindersCount > 0) {
                            Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = "Rappel actif", tint = DiscordAmber, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("$remindersCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = event.visibility.label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${EventDateTimeUtils.formatFullSchedule(event.startAtMs, event.timezoneId)} • ${EventDateTimeUtils.formatDuration(event.durationMinutes)}",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.title,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.description.isNotBlank()) {
                    Text(
                        text = event.description,
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Location + capacity / waitlist metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (event.eventType == RavenEventType.VOICE) Icons.Default.Mic else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = event.location.ifBlank { event.eventType.label },
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val countText = buildString {
                        append("${event.goingIds.size}")
                        if (event.maxParticipants > 0) append("/${event.maxParticipants}")
                        append(" part.")
                        if (event.waitlistIds.isNotEmpty()) append(" • ${event.waitlistIds.size} att.")
                    }
                    Text(
                        text = countText,
                        color = if (isFull) DiscordAmber else palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick action row
                if (effStatus == EventStatus.SCHEDULED || effStatus == EventStatus.ONGOING) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (event.eventType == RavenEventType.VOICE && !event.voiceChannelId.isNullOrBlank()) {
                            FilledTonalButton(
                                onClick = onJoinVoice,
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DiscordGreen.copy(alpha = 0.2f)),
                                modifier = Modifier.defaultMinSize(minHeight = 42.dp)
                            ) {
                                Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Vocal", color = DiscordGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        if (effStatus == EventStatus.SCHEDULED) {
                            val rsvpLabel = when {
                                myStatus == RsvpStatus.GOING -> "Confirmé ✓"
                                myStatus == RsvpStatus.WAITLISTED -> "Attente #$waitPos"
                                isFull -> "Liste d'attente"
                                else -> "Participer"
                            }
                            Button(
                                onClick = {
                                    if (myStatus == RsvpStatus.GOING || myStatus == RsvpStatus.WAITLISTED) {
                                        onQuickRsvp(RsvpStatus.NOT_GOING)
                                    } else {
                                        onQuickRsvp(RsvpStatus.GOING)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (myStatus) {
                                        RsvpStatus.GOING -> DiscordGreen
                                        RsvpStatus.WAITLISTED -> DiscordAmber
                                        else -> DiscordBlurple
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 42.dp).testTag("event_rsvp_btn_${event.id}")
                            ) {
                                Text(rsvpLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
