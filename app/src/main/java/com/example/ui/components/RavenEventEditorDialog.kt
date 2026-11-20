package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.EventDateTimeUtils
import com.example.model.*
import com.example.ui.theme.*
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RavenEventEditorDialog(
    server: Server?,
    existingEvent: RavenEvent? = null,
    knownFriends: List<User> = emptyList(),
    currentUserId: String,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (input: EventDraftInput, coverUri: Uri?) -> Unit
) {
    val palette = ravenPalette()
    val isEditing = existingEvent != null

    val initialTz = remember(existingEvent) {
        existingEvent?.timezoneId ?: TimeZone.getDefault().id
    }
    val defaultStartMs = remember(existingEvent) {
        existingEvent?.startAtMs ?: (System.currentTimeMillis() + 2 * 3600_000L)
    }
    val initialParts = remember(defaultStartMs, initialTz) {
        EventDateTimeUtils.extractComponents(defaultStartMs, initialTz)
    }

    var title by remember { mutableStateOf(existingEvent?.title.orEmpty()) }
    var description by remember { mutableStateOf(existingEvent?.description.orEmpty()) }
    var imageUrlInput by remember { mutableStateOf(existingEvent?.imageUrl.orEmpty()) }
    var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var bannerColorHex by remember { mutableStateOf(existingEvent?.bannerColorHex ?: 0xFF5865F2L) }
    var eventType by remember {
        mutableStateOf(
            existingEvent?.eventType ?: if (server != null) RavenEventType.SERVER else RavenEventType.EXTERNAL
        )
    }
    val voiceChannels = remember(server) {
        server?.channels?.filter { it.type == ChannelType.VOICE || it.type == ChannelType.STAGE }.orEmpty()
    }
    var selectedVoiceChannelId by remember {
        mutableStateOf(existingEvent?.voiceChannelId ?: voiceChannels.firstOrNull()?.id)
    }
    var visibility by remember {
        mutableStateOf(
            existingEvent?.visibility ?: if (server != null) EventVisibility.SERVER else EventVisibility.PRIVATE_INVITE
        )
    }
    var timezoneId by remember { mutableStateOf(initialTz) }
    var year by remember { mutableIntStateOf(initialParts.year) }
    var monthZeroBased by remember { mutableIntStateOf(initialParts.monthZeroBased) }
    var dayOfMonth by remember { mutableIntStateOf(initialParts.dayOfMonth) }
    var hourOfDay by remember { mutableIntStateOf(initialParts.hourOfDay) }
    var minute by remember { mutableIntStateOf(initialParts.minute) }
    var durationMinutes by remember { mutableIntStateOf(existingEvent?.durationMinutes ?: 60) }
    var customDurationText by remember { mutableStateOf((existingEvent?.durationMinutes ?: 60).toString()) }
    var location by remember { mutableStateOf(existingEvent?.location.orEmpty()) }
    var capacityText by remember {
        mutableStateOf(if ((existingEvent?.maxParticipants ?: 0) > 0) existingEvent!!.maxParticipants.toString() else "")
    }
    var invitedIds by remember { mutableStateOf(existingEvent?.invitedUserIds?.toSet() ?: emptySet()) }
    var initialReminderMin by remember { mutableStateOf<Int?>(if (isEditing) null else 15) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tzDropdownOpen by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedCoverUri = uri
        }
    }

    val computedStartAtMs = remember(year, monthZeroBased, dayOfMonth, hourOfDay, minute, timezoneId) {
        EventDateTimeUtils.composeEpochMs(year, monthZeroBased, dayOfMonth, hourOfDay, minute, timezoneId)
    }

    val bannerSwatches = listOf(0xFF5865F2L, 0xFFEB459EL, 0xFF10B981L, 0xFFF59E0BL, 0xFF8B5CF6L, 0xFF0EA5E9L)
    val inviteCandidates = remember(server, knownFriends, currentUserId) {
        (server?.members.orEmpty() + knownFriends)
            .distinctBy { it.id }
            .filter { it.id != currentUserId }
    }
    val timezoneOptions = remember {
        val deviceTz = TimeZone.getDefault().id
        val base = EventTimezoneCatalog.commonTimezones.toMutableList()
        if (base.none { it.first == deviceTz }) {
            base.add(0, deviceTz to "$deviceTz (Appareil)")
        }
        base
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.sidebarBg,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.92f)
                .testTag("event_editor_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = DiscordBlurple)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEditing) "Modifier l'événement" else "Créer un événement",
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(48.dp, 48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textMuted)
                    }
                }
                HorizontalDivider(color = palette.border)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cover preview & Image Picker
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(bannerColorHex), Color(bannerColorHex).copy(alpha = 0.45f), palette.cardBg)
                                )
                            )
                    ) {
                        val previewModel: Any? = pickedCoverUri ?: imageUrlInput.trim().takeIf { it.startsWith("https://") }
                        if (previewModel != null) {
                            AsyncImage(
                                model = previewModel,
                                contentDescription = "Image de couverture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.testTag("event_pick_image_button")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Choisir image", fontSize = 12.sp)
                            }
                            if (pickedCoverUri != null || imageUrlInput.isNotBlank()) {
                                FilledTonalIconButton(onClick = {
                                    pickedCoverUri = null
                                    imageUrlInput = ""
                                }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Retirer l'image")
                                }
                            }
                        }
                    }

                    // Theme banner colors
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Couleur :", color = palette.textMuted, fontSize = 12.sp)
                        bannerSwatches.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .clickable { bannerColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bannerColorHex == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        label = { Text("Nom de l'événement *") },
                        placeholder = { Text("Ex. Soirée Tournoi & Vocale") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("event_title_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 1500) description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Programme, consignes, liens utiles...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("event_description_input")
                    )

                    OutlinedTextField(
                        value = imageUrlInput,
                        onValueChange = { imageUrlInput = it },
                        label = { Text("URL d'image HTTPS (optionnel)") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Event Type
                    Text("Type d'événement", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RavenEventType.entries.forEach { type ->
                            val enabled = type != RavenEventType.SERVER || server != null
                            FilterChip(
                                selected = eventType == type,
                                enabled = enabled,
                                onClick = {
                                    eventType = type
                                    if (type == RavenEventType.VOICE && selectedVoiceChannelId == null) {
                                        selectedVoiceChannelId = voiceChannels.firstOrNull()?.id
                                    }
                                },
                                label = { Text(type.label, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (type) {
                                            RavenEventType.VOICE -> Icons.Default.Mic
                                            RavenEventType.SERVER -> Icons.Default.Dns
                                            RavenEventType.EXTERNAL -> Icons.Default.LocationOn
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("event_type_chip_${type.name}")
                            )
                        }
                    }

                    // Voice channel picker when VOICE type
                    if (eventType == RavenEventType.VOICE && voiceChannels.isNotEmpty()) {
                        Text("Salon vocal associé", color = palette.textMuted, fontSize = 12.sp)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            voiceChannels.forEach { ch ->
                                FilterChip(
                                    selected = selectedVoiceChannelId == ch.id,
                                    onClick = {
                                        selectedVoiceChannelId = ch.id
                                        if (location.isBlank()) location = ch.name
                                    },
                                    label = { Text(ch.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Visibility (Public / Serveur / Privé)
                    Text("Visibilité", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EventVisibility.entries.forEach { vis ->
                            val allowed = vis != EventVisibility.SERVER || server != null
                            FilterChip(
                                selected = visibility == vis,
                                enabled = allowed,
                                onClick = { visibility = vis },
                                label = { Text(vis.label, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (vis) {
                                            EventVisibility.PUBLIC -> Icons.Default.Public
                                            EventVisibility.SERVER -> Icons.Default.Groups
                                            EventVisibility.PRIVATE_INVITE -> Icons.Default.Lock
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("event_visibility_chip_${vis.name}")
                            )
                        }
                    }

                    if (visibility == EventVisibility.PRIVATE_INVITE && inviteCandidates.isNotEmpty()) {
                        Text(
                            text = "Inviter des participants (${invitedIds.size} sélectionné(s))",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            inviteCandidates.take(16).forEach { usr ->
                                val selected = usr.id in invitedIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        invitedIds = if (selected) invitedIds - usr.id else invitedIds + usr.id
                                    },
                                    label = { Text(usr.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Date, Heure & Fuseau horaire
                    Text("Date, heure & fuseau horaire", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("event_pick_date_button")
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(EventDateTimeUtils.formatDateLabel(computedStartAtMs, timezoneId), fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(0.8f).defaultMinSize(minHeight = 48.dp).testTag("event_pick_time_button")
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(EventDateTimeUtils.formatTimeLabel(computedStartAtMs, timezoneId), fontSize = 12.sp)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { tzDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("event_pick_timezone_button")
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val label = timezoneOptions.find { it.first == timezoneId }?.second ?: timezoneId
                            Text("Fuseau : $label", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = tzDropdownOpen,
                            onDismissRequest = { tzDropdownOpen = false },
                            modifier = Modifier.background(palette.cardBg)
                        ) {
                            timezoneOptions.forEach { (tzId, tzLabel) ->
                                DropdownMenuItem(
                                    text = { Text(tzLabel, color = palette.textPrimary, fontSize = 13.sp) },
                                    onClick = {
                                        timezoneId = tzId
                                        tzDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }

                    // Durée
                    Text("Durée (${EventDateTimeUtils.formatDuration(durationMinutes)})", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EventDurationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = durationMinutes == preset.minutes,
                                onClick = {
                                    durationMinutes = preset.minutes
                                    customDurationText = preset.minutes.toString()
                                },
                                label = { Text(preset.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customDurationText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                customDurationText = digits
                                digits.toIntOrNull()?.takeIf { it in 5..10080 }?.let { durationMinutes = it }
                            },
                            label = { Text("Durée (min)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = capacityText,
                            onValueChange = { raw -> capacityText = raw.filter { it.isDigit() }.take(4) },
                            label = { Text("Max places (vide = illimité)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f).testTag("event_capacity_input")
                        )
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { if (it.length <= 240) location = it },
                        label = { Text("Lieu / Lien / Salon") },
                        placeholder = { Text("Vocal Principal, Paris, lien visio...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("event_location_input")
                    )

                    if (!isEditing) {
                        Text("Rappel automatique", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = initialReminderMin == null,
                                onClick = { initialReminderMin = null },
                                label = { Text("Aucun", fontSize = 11.sp) }
                            )
                            EventReminderPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = initialReminderMin == preset.minutesBefore,
                                    onClick = { initialReminderMin = preset.minutesBefore },
                                    label = { Text(preset.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = palette.border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = {
                            val cap = capacityText.toIntOrNull()?.coerceIn(0, 1000) ?: 0
                            onSubmit(
                                EventDraftInput(
                                    eventId = existingEvent?.id,
                                    serverId = server?.id ?: existingEvent?.serverId,
                                    voiceChannelId = if (eventType == RavenEventType.VOICE) selectedVoiceChannelId else null,
                                    title = title,
                                    description = description,
                                    imageUrl = imageUrlInput.takeIf { it.isNotBlank() },
                                    bannerColorHex = bannerColorHex,
                                    eventType = eventType,
                                    visibility = visibility,
                                    startAtMs = computedStartAtMs,
                                    durationMinutes = durationMinutes.coerceIn(5, 10080),
                                    timezoneId = timezoneId,
                                    location = location,
                                    maxParticipants = cap,
                                    invitedUserIds = invitedIds.toList(),
                                    initialReminderMinutes = initialReminderMin
                                ),
                                pickedCoverUri
                            )
                        },
                        enabled = !isBusy && title.trim().length >= 3,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.weight(1.4f).defaultMinSize(minHeight = 48.dp).testTag("event_submit_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(if (isEditing) "Enregistrer" else "Publier l'événement", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = computedStartAtMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedMs ->
                            val picked = EventDateTimeUtils.extractComponents(pickedMs, "UTC")
                            year = picked.year
                            monthZeroBased = picked.monthZeroBased
                            dayOfMonth = picked.dayOfMonth
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = hourOfDay,
            initialMinute = minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = palette.cardBg,
            title = { Text("Choisir l'heure", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        hourOfDay = timeState.hour
                        minute = timeState.minute
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Annuler") }
            }
        )
    }
}
