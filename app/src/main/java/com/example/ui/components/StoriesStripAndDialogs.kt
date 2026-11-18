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
    onOpenModerationSheet: () -> Unit,
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
                    if (uiState.isModerator) {
                        Surface(
                            color = if (uiState.pendingReports.isNotEmpty()) DiscordRed.copy(alpha = 0.2f) else DiscordDarkInput,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onOpenModerationSheet)
                                .testTag("stories_moderation_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Modération des stories",
                                    tint = if (uiState.pendingReports.isNotEmpty()) DiscordRed else DiscordTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                if (uiState.pendingReports.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${uiState.pendingReports.size}",
                                        color = DiscordRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

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
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onPublishText: (
        textContent: String,
        caption: String,
        primaryColorHex: Long,
        secondaryColorHex: Long,
        visibility: StoryVisibility,
        customAllowedIds: Set<String>,
        durationHours: Int
    ) -> Unit,
    onPublishMedia: (
        uri: Uri,
        mediaType: StoryMediaType,
        caption: String,
        visibility: StoryVisibility,
        customAllowedIds: Set<String>,
        durationHours: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(StoryMediaType.TEXT) }
    var textContent by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedPalette by remember { mutableStateOf(StoryPaletteCatalog.presets.first()) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVisibility by remember { mutableStateOf(StoryVisibility.FRIENDS) }
    var selectedDuration by remember { mutableStateOf(StoryDurationOption.TWENTY_FOUR_HOURS) }
    var chosenCustomFriendIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                            Text(
                                text = textContent.ifBlank { "Aperçu de votre story..." },
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
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
                                selectedDuration.hours
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
                                selectedDuration.hours
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryModerationSheet(
    pendingReports: List<StoryReport>,
    allReports: List<StoryReport>,
    onModerateStory: (storyId: String, hideOnly: Boolean, reason: String, reportId: String) -> Unit,
    onDismissReport: (reportId: String) -> Unit,
    onRunCleanup: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = DiscordRed)
                    Column {
                        Text("Modération Stories", color = DiscordTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${pendingReports.size} signalement(s) en attente", color = DiscordTextMuted, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onRunCleanup,
                    modifier = Modifier.testTag("stories_manual_cleanup_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nettoyer expirées", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DiscordDarkBorder)
            Spacer(modifier = Modifier.height(10.dp))

            val displayList = pendingReports.ifEmpty { allReports }
            if (displayList.isEmpty()) {
                Text(
                    text = "Aucun signalement de story à traiter.",
                    color = DiscordTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(displayList, key = { it.id }) { rep ->
                        Surface(
                            color = DiscordDarkSidebar,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DiscordDarkBorder),
                            modifier = Modifier.fillMaxWidth().testTag("story_report_card_${rep.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Auteur : ${rep.storyAuthorName} • ${rep.reason.label}",
                                        color = DiscordTextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = when (rep.status) {
                                            StoryReportStatus.PENDING -> DiscordRed.copy(alpha = 0.2f)
                                            StoryReportStatus.REMOVED -> DiscordAmber.copy(alpha = 0.2f)
                                            StoryReportStatus.DISMISSED -> DiscordGreen.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = rep.status.label,
                                            color = DiscordTextWhite,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Aperçu : ${rep.storyPreview}",
                                    color = DiscordTextLight,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (rep.details.isNotBlank()) {
                                    Text(
                                        text = "Détails (${rep.reporterName}) : ${rep.details}",
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                if (rep.status == StoryReportStatus.PENDING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onModerateStory(rep.storyId, true, rep.reason.label, rep.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DiscordAmber),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Masquer", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { onModerateStory(rep.storyId, false, rep.reason.label, rep.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Supprimer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { onDismissReport(rep.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Ignorer", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
