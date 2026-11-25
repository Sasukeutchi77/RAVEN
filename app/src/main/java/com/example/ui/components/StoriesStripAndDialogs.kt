package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StoryUiState

@Composable
fun StoriesStrip(
    currentUser: User,
    uiState: StoryUiState,
    onCreateStoryClick: () -> Unit,
    onOpenAuthorGroup: (StoryAuthorGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val ownGroup = uiState.allGroups.find { it.isCurrentUser }
    val otherUnseen = uiState.unseenGroups.filterNot { it.isCurrentUser }
    val otherSeen = uiState.seenGroups.filterNot { it.isCurrentUser }

    Surface(
        color = palette.cardBg.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("stories_strip_container")
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "Stories RAVEN",
                        tint = DiscordBlurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "STORIES RAVEN",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (otherUnseen.isNotEmpty()) {
                        Surface(
                            color = DiscordBlurple.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${otherUnseen.size} non vue(s)",
                                color = DiscordBlurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {

                    Surface(
                        color = DiscordBlurple,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onCreateStoryClick)
                            .testTag("stories_publish_quick_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Publier une story",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Publier",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current user tile
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(66.dp)
                            .clickable {
                                if (ownGroup != null) onOpenAuthorGroup(ownGroup) else onCreateStoryClick()
                            }
                            .testTag("story_tile_self")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val ringBrush = if (ownGroup != null) {
                                Brush.linearGradient(listOf(DiscordBlurple, DiscordGreen))
                            } else {
                                Brush.linearGradient(listOf(DiscordDarkBorder, DiscordDarkBorder))
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, ringBrush, CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(Color(currentUser.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!currentUser.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentUser.avatarUrl,
                                        contentDescription = "Votre avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = currentUser.name.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(DiscordBlurple)
                                    .border(2.dp, palette.cardBg, CircleShape)
                                    .clickable { onCreateStoryClick() }
                                    .testTag("story_add_plus_badge"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Nouvelle story",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (ownGroup != null) "Ma story (${ownGroup.stories.size})" else "Votre story",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Unseen friend/public stories FIRST
                items(otherUnseen, key = { "unseen_${it.authorId}" }) { group ->
                    StoryAuthorAvatarTile(
                        group = group,
                        isSeen = false,
                        onClick = { onOpenAuthorGroup(group) }
                    )
                }

                // Seen stories AFTER
                items(otherSeen, key = { "seen_${it.authorId}" }) { group ->
                    StoryAuthorAvatarTile(
                        group = group,
                        isSeen = true,
                        onClick = { onOpenAuthorGroup(group) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryAuthorAvatarTile(
    group: StoryAuthorGroup,
    isSeen: Boolean,
    onClick: () -> Unit
) {
    val palette = ravenPalette()
    val ringBrush = if (isSeen) {
        Brush.linearGradient(listOf(DiscordTextDark, DiscordTextDark))
    } else {
        Brush.linearGradient(listOf(Color(0xFF5865F2), Color(0xFFEB459E), Color(0xFFF0B232)))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(66.dp)
            .clickable(onClick = onClick)
            .testTag("story_author_tile_${group.authorId}")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, ringBrush, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(group.authorAvatarColor)),
                contentAlignment = Alignment.Center
            ) {
                if (!group.authorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = group.authorAvatarUrl,
                        contentDescription = "Story de ${group.authorName}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = group.authorName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            if (!isSeen && group.unseenCount > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(DiscordBlurple)
                        .border(1.5.dp, palette.cardBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.unseenCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = group.authorName,
            color = if (isSeen) DiscordTextMuted else palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = if (isSeen) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CreateStoryDialog(
    friends: List<User>,
    servers: List<Server> = emptyList(),
    jukeboxTracks: List<MusicTrack> = emptyList(),
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onPublishText: (
        textContent: String,
        caption: String,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        visibility: StoryVisibility,
        customAllowedIds: Set<String>,
        durationHours: Int,
        allowedServerId: String?,
        stickerText: String?,
        stickerStyle: String,
        quickPollQuestion: String?,
        quickPollOptions: List<String>,
        musicTrackTitle: String?,
        musicTrackArtist: String?
    ) -> Unit,
    onPublishMedia: (
        uri: Uri,
        mediaType: StoryMediaType,
        caption: String,
        visibility: StoryVisibility,
        customAllowedIds: Set<String>,
        durationHours: Int,
        allowedServerId: String?,
        stickerText: String?,
        stickerStyle: String,
        quickPollQuestion: String?,
        quickPollOptions: List<String>,
        musicTrackTitle: String?,
        musicTrackArtist: String?
    ) -> Unit
) {
    var selectedMode by remember { mutableStateOf(StoryMediaType.TEXT) }
    var textContent by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedPalette by remember { mutableStateOf(StoryPaletteCatalog.presets.first()) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVisibility by remember { mutableStateOf(StoryVisibility.FRIENDS) }
    var selectedDuration by remember { mutableStateOf(StoryDurationOption.TWENTY_FOUR_HOURS) }
    var chosenCustomFriendIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedAllowedServerId by remember { mutableStateOf(servers.firstOrNull()?.id) }

    // Story Enrichment: Sticker, Quick Poll, Background Music
    var stickerText by remember { mutableStateOf("") }
    var stickerStyle by remember { mutableStateOf("PILL") }
    var includePoll by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptionA by remember { mutableStateOf("Oui 🔥") }
    var pollOptionB by remember { mutableStateOf("Non 👀") }
    var selectedTrackId by remember { mutableStateOf<String?>(null) }

    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedTrack = remember(selectedTrackId, jukeboxTracks) {
        jukeboxTracks.find { it.id == selectedTrackId }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            validationError = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            validationError = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoStories, contentDescription = null, tint = DiscordBlurple)
                Text("Nouvelle Story RAVEN", color = DiscordTextWhite, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Media Type tabs
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StoryMediaType.entries.forEach { mode ->
                        val active = selectedMode == mode
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedMode = mode
                                selectedMediaUri = null
                                validationError = null
                            },
                            label = { Text(mode.label, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (mode) {
                                        StoryMediaType.TEXT -> Icons.Default.TextFields
                                        StoryMediaType.IMAGE -> Icons.Default.Image
                                        StoryMediaType.VIDEO -> Icons.Default.Videocam
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("create_story_mode_${mode.name}")
                        )
                    }
                }

                when (selectedMode) {
                    StoryMediaType.TEXT -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(selectedPalette.primaryHex), Color(selectedPalette.secondaryHex))
                                    )
                                )
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = textContent.ifBlank { "Aperçu de votre story..." },
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (stickerText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = if (stickerStyle == "NEON") Color(0xFF00E5FF) else Color.White,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = stickerText,
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(StoryPaletteCatalog.presets, key = { it.id }) { preset ->
                                val isCur = selectedPalette.id == preset.id
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(preset.primaryHex), Color(preset.secondaryHex))))
                                        .border(if (isCur) 2.dp else 0.dp, Color.White, CircleShape)
                                        .clickable { selectedPalette = preset }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = textContent,
                            onValueChange = {
                                textContent = it.take(500)
                                validationError = null
                            },
                            label = { Text("Contenu texte (max 500)", color = DiscordTextMuted, fontSize = 12.sp) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().testTag("create_story_text_input")
                        )
                    }

                    StoryMediaType.IMAGE -> {
                        Surface(
                            color = DiscordDarkInput,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DiscordDarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .testTag("create_story_pick_image")
                        ) {
                            if (selectedMediaUri != null) {
                                AsyncImage(
                                    model = selectedMediaUri,
                                    contentDescription = "Image sélectionnée",
                                    modifier = Modifier.fillMaxWidth().height(150.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Choisir une image (JPG, PNG, WEBP, GIF • max 15 Mo)", color = DiscordTextLight, fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    StoryMediaType.VIDEO -> {
                        Surface(
                            color = DiscordDarkInput,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DiscordDarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                                .testTag("create_story_pick_video")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (selectedMediaUri != null) Icons.Default.CheckCircle else Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = if (selectedMediaUri != null) DiscordGreen else DiscordBlurple,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedMediaUri != null) {
                                        "Vidéo prête pour upload sécurisé (${selectedMediaUri.toString().takeLast(24)})"
                                    } else {
                                        "Choisir une vidéo (MP4, WEBM • max 50 Mo)"
                                    },
                                    color = DiscordTextLight,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(300) },
                    label = { Text("Légende optionnelle", color = DiscordTextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_story_caption_input")
                )

                // Sticker & Music enrichment card
                Surface(
                    color = DiscordDarkInput,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("AUTOCOLLANT, SONDAGE & MUSIQUE", color = DiscordBlurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = stickerText,
                            onValueChange = { stickerText = it.take(32) },
                            label = { Text("Sticker / Tag (ex. Live, Mood, Paris)", color = DiscordTextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("create_story_sticker_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("PILL" to "Classique", "NEON" to "Néon", "AMBER" to "Or").forEach { (styleId, label) ->
                                FilterChip(
                                    selected = stickerStyle == styleId,
                                    onClick = { stickerStyle = styleId },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Poll, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(16.dp))
                                Text("Sondage rapide", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Switch(
                                checked = includePoll,
                                onCheckedChange = { includePoll = it },
                                modifier = Modifier.testTag("create_story_poll_switch")
                            )
                        }
                        if (includePoll) {
                            OutlinedTextField(
                                value = pollQuestion,
                                onValueChange = { pollQuestion = it.take(90) },
                                label = { Text("Question du sondage", color = DiscordTextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("create_story_poll_question")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = pollOptionA,
                                    onValueChange = { pollOptionA = it.take(30) },
                                    label = { Text("Option 1", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = pollOptionB,
                                    onValueChange = { pollOptionB = it.take(30) },
                                    label = { Text("Option 2", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (jukeboxTracks.isNotEmpty()) {
                            Text("Ambiance Jukebox", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedTrackId == null,
                                        onClick = { selectedTrackId = null },
                                        label = { Text("Aucune", fontSize = 11.sp) }
                                    )
                                }
                                items(jukeboxTracks.take(6), key = { it.id }) { track ->
                                    FilterChip(
                                        selected = selectedTrackId == track.id,
                                        onClick = { selectedTrackId = track.id },
                                        label = { Text("🎵 ${track.title}", fontSize = 11.sp, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Availability duration chips
                Text("Durée de disponibilité", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(StoryDurationOption.entries) { opt ->
                        FilterChip(
                            selected = selectedDuration == opt,
                            onClick = { selectedDuration = opt },
                            label = { Text(opt.label, fontSize = 11.sp) },
                            modifier = Modifier.testTag("create_story_ttl_${opt.hours}")
                        )
                    }
                }

                // Privacy selection
                Text("Confidentialité", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                StoryVisibility.entries.forEach { vis ->
                    val selected = selectedVisibility == vis
                    Surface(
                        color = if (selected) DiscordBlurple.copy(alpha = 0.18f) else DiscordDarkInput,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) DiscordBlurple else Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVisibility = vis }
                            .testTag("create_story_vis_${vis.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { selectedVisibility = vis })
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(vis.label, color = DiscordTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(vis.subtitle, color = DiscordTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Server members selector
                if (selectedVisibility == StoryVisibility.SERVER_MEMBERS) {
                    Text("Serveur autorisé :", color = DiscordTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (servers.isEmpty()) {
                        Text("Aucun serveur disponible.", color = DiscordRed, fontSize = 12.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(servers, key = { it.id }) { server ->
                                FilterChip(
                                    selected = selectedAllowedServerId == server.id,
                                    onClick = { selectedAllowedServerId = server.id },
                                    label = { Text(server.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Custom users checklist
                if (selectedVisibility == StoryVisibility.CUSTOM_USERS) {
                    Text("Utilisateurs autorisés :", color = DiscordTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (friends.isEmpty()) {
                        Text("Aucun ami disponible dans votre liste.", color = DiscordRed, fontSize = 12.sp)
                    } else {
                        friends.forEach { friend ->
                            val isChecked = friend.id in chosenCustomFriendIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chosenCustomFriendIds = if (isChecked) chosenCustomFriendIds - friend.id else chosenCustomFriendIds + friend.id
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        chosenCustomFriendIds = if (it) chosenCustomFriendIds + friend.id else chosenCustomFriendIds - friend.id
                                    }
                                )
                                Text("${friend.name}${friend.tag}", color = DiscordTextLight, fontSize = 13.sp)
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(validationError!!, color = DiscordRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    if (selectedVisibility == StoryVisibility.CUSTOM_USERS && chosenCustomFriendIds.isEmpty()) {
                        validationError = "Sélectionnez au moins un utilisateur autorisé."
                        return@Button
                    }
                    if (selectedVisibility == StoryVisibility.SERVER_MEMBERS && selectedAllowedServerId.isNullOrBlank()) {
                        validationError = "Sélectionnez un serveur autorisé."
                        return@Button
                    }
                    val pollQ = if (includePoll && pollQuestion.isNotBlank()) pollQuestion.trim() else null
                    val pollOpts = if (pollQ != null) {
                        listOf(pollOptionA.trim().ifBlank { "Oui" }, pollOptionB.trim().ifBlank { "Non" })
                    } else emptyList()
                    val serverIdArg = if (selectedVisibility == StoryVisibility.SERVER_MEMBERS) selectedAllowedServerId else null
                    val stickerArg = stickerText.trim().takeIf { it.isNotBlank() }

                    when (selectedMode) {
                        StoryMediaType.TEXT -> {
                            if (textContent.isBlank()) {
                                validationError = "Veuillez saisir le message de votre story."
                                return@Button
                            }
                            onPublishText(
                                textContent,
                                caption,
                                selectedPalette.primaryHex,
                                selectedPalette.secondaryHex,
                                selectedVisibility,
                                chosenCustomFriendIds,
                                selectedDuration.hours,
                                serverIdArg,
                                stickerArg,
                                stickerStyle,
                                pollQ,
                                pollOpts,
                                selectedTrack?.title,
                                selectedTrack?.artist
                            )
                        }
                        StoryMediaType.IMAGE, StoryMediaType.VIDEO -> {
                            val uri = selectedMediaUri
                            if (uri == null) {
                                validationError = "Veuillez sélectionner un fichier ${selectedMode.label.lowercase()}."
                                return@Button
                            }
                            onPublishMedia(
                                uri,
                                selectedMode,
                                caption,
                                selectedVisibility,
                                chosenCustomFriendIds,
                                selectedDuration.hours,
                                serverIdArg,
                                stickerArg,
                                stickerStyle,
                                pollQ,
                                pollOpts,
                                selectedTrack?.title,
                                selectedTrack?.artist
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("create_story_publish_confirm")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Publier", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!isBusy) onDismiss() }) {
                Text("Annuler", color = DiscordTextMuted)
            }
        }
    )
}

