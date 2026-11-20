package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.EventDateTimeUtils
import com.example.model.*
import com.example.ui.theme.*
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RavenEventDetailSheet(
    event: RavenEvent,
    currentUserId: String,
    canManage: Boolean,
    rsvpRecords: List<EventRsvpRecord>,
    reminders: List<EventReminderRecord>,
    historyEntries: List<EventHistoryEntry>,
    serverMembers: List<User> = emptyList(),
    shareChannels: List<Channel> = emptyList(),
    onRsvp: (RsvpStatus) -> Unit,
    onToggleReminder: (Int) -> Unit,
    onEdit: () -> Unit,
    onCancelEvent: (String) -> Unit,
    onDeleteEvent: () -> Unit,
    onJoinVoiceChannel: (String) -> Unit,
    onShareInChannel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var activeTab by remember { mutableIntStateOf(0) } // 0=Participants, 1=Rappels, 2=Historique
    var showCancelDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val effStatus = event.effectiveStatus(now)
    val myRsvp = event.userRsvpStatus(currentUserId)
    val myWaitPos = event.waitlistPosition(currentUserId)
    val isFull = event.isFull()
    val accent = Color(event.bannerColorHex)
    val deviceTzId = remember { TimeZone.getDefault().id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("event_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cover banner hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent, accent.copy(alpha = 0.45f), palette.cardBg)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                    )
                }

                // Top status & visibility pills
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusColor = when (effStatus) {
                        EventStatus.ONGOING -> DiscordGreen
                        EventStatus.CANCELLED -> DiscordRed
                        EventStatus.COMPLETED -> DiscordTextMuted
                        EventStatus.SCHEDULED -> DiscordBlurple
                    }
                    Surface(color = statusColor, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = EventDateTimeUtils.formatCountdown(event.startAtMs, event.endAtMs, now, effStatus),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = event.visibility.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = event.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Organisé par ${event.creatorName} • ${event.eventType.label}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }

            // Metadata & Schedule
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${EventDateTimeUtils.formatFullSchedule(event.startAtMs, event.timezoneId)} • Durée ${EventDateTimeUtils.formatDuration(event.durationMinutes)}",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (deviceTzId != event.timezoneId) {
                                Text(
                                    text = "Votre heure locale : ${EventDateTimeUtils.formatFullSchedule(event.startAtMs, deviceTzId)}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (event.eventType == RavenEventType.VOICE) Icons.Default.Mic else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = DiscordGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = event.location.ifBlank { event.voiceChannelName.ifBlank { "En ligne" } },
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (event.description.isNotBlank()) {
                        Text(
                            text = event.description,
                            color = palette.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (effStatus == EventStatus.CANCELLED && event.cancellationReason.isNotBlank()) {
                        Text(
                            text = "Motif d'annulation : ${event.cancellationReason}",
                            color = DiscordRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Voice Quick-Join CTA
            if (event.eventType == RavenEventType.VOICE &&
                !event.voiceChannelId.isNullOrBlank() &&
                effStatus in setOf(EventStatus.SCHEDULED, EventStatus.ONGOING)
            ) {
                Button(
                    onClick = { onJoinVoiceChannel(event.voiceChannelId) },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("event_join_voice_button")
                ) {
                    Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Rejoindre le salon vocal (${event.voiceChannelName.ifBlank { event.location }})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // RSVP Buttons
            if (effStatus == EventStatus.SCHEDULED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val primaryLabel = when {
                        myRsvp == RsvpStatus.GOING -> "Confirmé ✓"
                        myRsvp == RsvpStatus.WAITLISTED -> "Attente #$myWaitPos"
                        isFull -> "Liste d'attente"
                        else -> "Participer"
                    }
                    Button(
                        onClick = {
                            if (myRsvp == RsvpStatus.GOING || myRsvp == RsvpStatus.WAITLISTED) {
                                onRsvp(RsvpStatus.NOT_GOING)
                            } else {
                                onRsvp(RsvpStatus.GOING)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (myRsvp) {
                                RsvpStatus.GOING -> DiscordGreen
                                RsvpStatus.WAITLISTED -> DiscordAmber
                                else -> DiscordBlurple
                            }
                        ),
                        modifier = Modifier.weight(1.2f).defaultMinSize(minHeight = 48.dp).testTag("detail_rsvp_going_btn")
                    ) {
                        Text(primaryLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            onRsvp(if (myRsvp == RsvpStatus.MAYBE) RsvpStatus.NOT_GOING else RsvpStatus.MAYBE)
                        },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("detail_rsvp_maybe_btn")
                    ) {
                        Text(
                            text = if (myRsvp == RsvpStatus.MAYBE) "Intéressé ✓" else "Intéressé",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Management row for creator / staff
            if (canManage) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (effStatus != EventStatus.CANCELLED) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("detail_edit_event_btn")) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Modifier", fontSize = 12.sp)
                        }
                    }
                    if (shareChannels.isNotEmpty() && effStatus == EventStatus.SCHEDULED) {
                        OutlinedButton(onClick = { showShareDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Partager", fontSize = 12.sp)
                        }
                    }
                    if (effStatus == EventStatus.SCHEDULED) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DiscordAmber),
                            modifier = Modifier.testTag("detail_cancel_event_btn")
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Annuler", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = onDeleteEvent,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiscordRed),
                        modifier = Modifier.testTag("detail_delete_event_btn")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Supprimer", fontSize = 12.sp)
                    }
                }
            }

            // Sub-tabs: Participants / Rappels / Historique
            TabRow(selectedTabIndex = activeTab, containerColor = palette.cardBg) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Participants (${event.goingIds.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Rappels (${reminders.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Historique (${historyEntries.size})", fontSize = 12.sp) }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                when (activeTab) {
                    0 -> {
                        val capLabel = if (event.maxParticipants > 0) "${event.goingIds.size}/${event.maxParticipants} places" else "${event.goingIds.size} confirmé(s) (illimité)"
                        item {
                            Text("CONFIRMÉS — $capLabel", color = DiscordGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (event.goingIds.isEmpty()) {
                            item { Text("Aucun participant confirmé.", color = palette.textMuted, fontSize = 12.sp) }
                        } else {
                            items(event.goingIds, key = { "go_$it" }) { uid ->
                                val rec = rsvpRecords.find { it.uid == uid }
                                val member = serverMembers.find { it.id == uid }
                                val name = rec?.displayName ?: member?.name ?: if (uid == event.creatorId) event.creatorName else "Participant ($uid)"
                                ParticipantRowItem(name = name, badge = if (uid == event.creatorId) "Organisateur" else "Confirmé", badgeColor = DiscordGreen)
                            }
                        }

                        if (event.waitlistIds.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text("LISTE D'ATTENTE (${event.waitlistIds.size})", color = DiscordAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            items(event.waitlistIds.indices.toList(), key = { idx -> "wl_${event.waitlistIds[idx]}" }) { idx ->
                                val uid = event.waitlistIds[idx]
                                val rec = rsvpRecords.find { it.uid == uid }
                                val member = serverMembers.find { it.id == uid }
                                val name = rec?.displayName ?: member?.name ?: "Membre ($uid)"
                                ParticipantRowItem(name = name, badge = "File #${idx + 1}", badgeColor = DiscordAmber)
                            }
                        }

                        if (event.interestedIds.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text("INTÉRESSÉS (${event.interestedIds.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            items(event.interestedIds, key = { "int_$it" }) { uid ->
                                val rec = rsvpRecords.find { it.uid == uid }
                                val member = serverMembers.find { it.id == uid }
                                val name = rec?.displayName ?: member?.name ?: "Membre ($uid)"
                                ParticipantRowItem(name = name, badge = "Intéressé", badgeColor = DiscordBlurple)
                            }
                        }
                    }

                    1 -> {
                        item {
                            Text(
                                text = "Configurez vos rappels automatiques avant le début de l'événement :",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                EventReminderPreset.entries.forEach { preset ->
                                    val active = reminders.any { it.minutesBefore == preset.minutesBefore }
                                    FilterChip(
                                        selected = active,
                                        enabled = effStatus == EventStatus.SCHEDULED,
                                        onClick = { onToggleReminder(preset.minutesBefore) },
                                        label = { Text(preset.label, fontSize = 12.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (active) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("reminder_chip_${preset.minutesBefore}")
                                    )
                                }
                            }
                        }
                        items(reminders, key = { it.id }) { rem ->
                            Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, palette.border)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("${rem.minutesBefore} min avant", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (rem.triggered) "Rappel envoyé ✓" else "Programmé pour ${EventDateTimeUtils.formatTimeLabel(rem.triggerAtMs, event.timezoneId)}",
                                            color = if (rem.triggered) DiscordGreen else palette.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(onClick = { onToggleReminder(rem.minutesBefore) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer rappel", tint = DiscordRed)
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (historyEntries.isEmpty()) {
                            item { Text("Aucun historique enregistré.", color = palette.textMuted, fontSize = 12.sp) }
                        } else {
                            items(historyEntries, key = { it.id }) { entry ->
                                Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, palette.border)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(entry.actionType.label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(entry.timestampLabel, color = palette.textMuted, fontSize = 11.sp)
                                        }
                                        Text(entry.summary, color = palette.textPrimary, fontSize = 13.sp)
                                        Text("Par ${entry.actorName}", color = palette.textMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Annuler l'événement ?", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif d'annulation") },
                    placeholder = { Text("Reporté à la semaine prochaine...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelEvent(reason)
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordRed)
                ) { Text("Confirmer l'annulation", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Retour") }
            }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            containerColor = palette.cardBg,
            title = { Text("Partager dans un salon", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shareChannels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.ANNOUNCEMENT }.forEach { ch ->
                        OutlinedButton(
                            onClick = {
                                onShareInChannel(ch.id)
                                showShareDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("# ${ch.name}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Fermer") }
            }
        )
    }
}

@Composable
private fun ParticipantRowItem(
    name: String,
    badge: String,
    badgeColor: Color
) {
    val palette = ravenPalette()
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1).uppercase(), color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(color = badgeColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                Text(badge, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}
