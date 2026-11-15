package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Channel
import com.example.model.Server
import com.example.model.ServerEvent
import com.example.model.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEventsSheet(
    server: Server,
    currentUserId: String,
    onToggleInterest: (String) -> Unit,
    onCreateEvent: (title: String, description: String, dateTimeLabel: String, locationLabel: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var showCreateForm by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventDesc by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("Demain • 21:00") }
    var eventLocation by remember { mutableStateOf("Scène Communauté") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("server_events_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = DiscordBlurple,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Événements • ${server.name}",
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${server.events.size} événement${if (server.events.size > 1) "s" else ""} prévu${if (server.events.size > 1) "s" else ""}",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = { showCreateForm = !showCreateForm },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("toggle_create_event_button")
                ) {
                    Icon(
                        imageVector = if (showCreateForm) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showCreateForm) "Fermer" else "Créer",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showCreateForm) {
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Planifier un événement",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            placeholder = { Text("Titre (ex. Soirée Tournoi)", color = palette.textMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("create_event_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = palette.inputBg,
                                unfocusedContainerColor = palette.inputBg,
                                focusedBorderColor = DiscordBlurple,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textSecondary
                            )
                        )
                        OutlinedTextField(
                            value = eventDesc,
                            onValueChange = { eventDesc = it },
                            placeholder = { Text("Description...", color = palette.textMuted) },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = palette.inputBg,
                                unfocusedContainerColor = palette.inputBg,
                                focusedBorderColor = DiscordBlurple,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = palette.textPrimary,
                                unfocusedTextColor = palette.textSecondary
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = eventDate,
                                onValueChange = { eventDate = it },
                                label = { Text("Date & heure", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = palette.inputBg,
                                    unfocusedContainerColor = palette.inputBg,
                                    focusedBorderColor = DiscordBlurple,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textSecondary
                                )
                            )
                            OutlinedTextField(
                                value = eventLocation,
                                onValueChange = { eventLocation = it },
                                label = { Text("Lieu / Salon", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = palette.inputBg,
                                    unfocusedContainerColor = palette.inputBg,
                                    focusedBorderColor = DiscordBlurple,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textSecondary
                                )
                            )
                        }
                        Button(
                            onClick = {
                                if (eventTitle.isNotBlank()) {
                                    onCreateEvent(eventTitle, eventDesc, eventDate, eventLocation)
                                    eventTitle = ""
                                    eventDesc = ""
                                    showCreateForm = false
                                }
                            },
                            enabled = eventTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                            modifier = Modifier.fillMaxWidth().testTag("submit_create_event_button")
                        ) {
                            Text("Publier l'événement", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (server.events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucun événement planifié pour le moment.",
                            color = palette.textMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(server.events, key = { it.id }) { event ->
                        val isInterested = event.interestedUserIds.contains(currentUserId) ||
                            event.interestedUserIds.contains("user_me")
                        ServerEventCard(
                            event = event,
                            isInterested = isInterested,
                            onToggleInterest = { onToggleInterest(event.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerEventCard(
    event: ServerEvent,
    isInterested: Boolean,
    onToggleInterest: () -> Unit
) {
    val palette = ravenPalette()
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isInterested) DiscordGreen.copy(alpha = 0.6f) else palette.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("event_card_${event.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = DiscordBlurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = event.dateTimeLabel,
                        color = DiscordBlurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = palette.inputBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${event.interestedUserIds.size}",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = event.title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.description,
                color = palette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${event.locationLabel} • Par ${event.creatorName}",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onToggleInterest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInterested) DiscordGreen else palette.inputBg
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("event_interest_button_${event.id}")
                ) {
                    Icon(
                        imageVector = if (isInterested) Icons.Default.NotificationsActive else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isInterested) Color.White else DiscordAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isInterested) "Intéressé(e)" else "Ça m'intéresse",
                        color = if (isInterested) Color.White else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageRoomView(
    channel: Channel,
    serverName: String,
    serverMembers: List<User>,
    currentUser: User,
    isUserMuted: Boolean,
    isUserDeafened: Boolean,
    onRaiseHand: () -> Unit,
    onPromoteSpeaker: (String) -> Unit,
    onMoveToAudience: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeaveVoice: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val allParticipants = remember(channel.activeVoiceUsers, serverMembers, currentUser) {
        (channel.activeVoiceUsers + serverMembers + currentUser).distinctBy { it.id }
    }
    val speakers = remember(allParticipants, channel.stageSpeakerIds) {
        allParticipants.filter { channel.stageSpeakerIds.contains(it.id) }
    }
    val raisedHands = remember(allParticipants, channel.stageRaisedHandIds) {
        allParticipants.filter { channel.stageRaisedHandIds.contains(it.id) && !channel.stageSpeakerIds.contains(it.id) }
    }
    val audience = remember(allParticipants, channel.stageSpeakerIds) {
        allParticipants.filterNot { channel.stageSpeakerIds.contains(it.id) }
    }

    val isCurrentSpeaker = channel.stageSpeakerIds.contains(currentUser.id) ||
        channel.stageSpeakerIds.contains("user_me")
    val hasRaisedHand = channel.stageRaisedHandIds.contains(currentUser.id) ||
        channel.stageRaisedHandIds.contains("user_me")

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.primaryBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = DiscordGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.name,
                                    color = palette.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = DiscordRed,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "EN DIRECT",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Scène • $serverName",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.testTag("stage_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Réduire la scène",
                            tint = palette.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.sidebarBg)
            )
        },
        bottomBar = {
            Surface(
                color = palette.sidebarBg,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCurrentSpeaker) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isUserMuted) Color.White else palette.inputBg)
                                .testTag("stage_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isUserMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isUserMuted) "Réactiver micro" else "Couper micro",
                                tint = if (isUserMuted) Color.Black else palette.textPrimary
                            )
                        }
                        FilledTonalButton(
                            onClick = { onMoveToAudience(currentUser.id) },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.cardBg),
                            modifier = Modifier.testTag("stage_step_down_button")
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Quitter la scène", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onRaiseHand,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasRaisedHand) DiscordAmber else DiscordBlurple
                            ),
                            modifier = Modifier.testTag("stage_raise_hand_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasRaisedHand) "Main levée" else "Lever la main",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleDeafen,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isUserDeafened) Color.White else palette.inputBg)
                    ) {
                        Icon(
                            imageVector = if (isUserDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                            contentDescription = "Casque",
                            tint = if (isUserDeafened) Color.Black else palette.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onLeaveVoice,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(DiscordRed)
                            .testTag("stage_leave_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Quitter la scène",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (channel.topic.isNotBlank()) {
                item {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, tint = DiscordBlurple)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = channel.topic,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Speakers section
            item {
                Text(
                    text = "ORATEURS SUR SCÈNE (${speakers.size})",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(speakers, key = { it.id }) { speaker ->
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, DiscordGreen),
                            modifier = Modifier
                                .width(142.dp)
                                .testTag("stage_speaker_${speaker.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(speaker.avatarColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!speaker.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = speaker.avatarUrl,
                                            contentDescription = speaker.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = speaker.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = speaker.name,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Orateur",
                                    color = DiscordGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onMoveToAudience(speaker.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Auditoire", color = palette.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Raised hands queue
            if (raisedHands.isNotEmpty()) {
                item {
                    Text(
                        text = "MAINS LEVÉES (${raisedHands.size})",
                        color = DiscordAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        raisedHands.forEach { listener ->
                            Surface(
                                color = DiscordAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DiscordAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PanTool, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = listener.name,
                                            color = palette.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Button(
                                        onClick = { onPromoteSpeaker(listener.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("promote_speaker_${listener.id}")
                                    ) {
                                        Text("Inviter à parler", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Audience listeners
            item {
                Text(
                    text = "AUDITOIRE (${audience.size})",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(audience, key = { "aud_${it.id}" }) { listener ->
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(listener.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = listener.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = listener.name,
                                    color = palette.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "À l'écoute",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        TextButton(
                            onClick = { onPromoteSpeaker(listener.id) },
                            modifier = Modifier.testTag("stage_invite_${listener.id}")
                        ) {
                            Text("Monter", color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
