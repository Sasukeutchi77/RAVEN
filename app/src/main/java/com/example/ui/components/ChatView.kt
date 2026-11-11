package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    channel: Channel,
    messages: List<Message>,
    replyingTo: Message?,
    searchQuery: String,
    isPinnedFilter: Boolean,
    members: List<User> = emptyList(),
    onSendMessage: (text: String, imageUrl: String?, audioUrl: String?, audioDurationSec: Int) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onReplyClick: (Message) -> Unit,
    onCancelReply: () -> Unit,
    onPinMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onTogglePinnedFilter: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleMemberList: () -> Unit,
    chatTheme: ChatBackgroundTheme? = null,
    onApplyChatTheme: (ChatBackgroundTheme) -> Unit = {},
    onCreatePoll: (question: String, options: List<String>, allowsMultiple: Boolean) -> Unit = { _, _, _ -> },
    onVotePoll: (messageId: String, optionId: String) -> Unit = { _, _ -> },
    currentUserId: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isEmojiBarOpen by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<Message?>(null) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showCreatePollDialog by remember { mutableStateOf(false) }

    // Audio recording & playback helpers
    val audioRecorder = remember { AudioRecorderHelper(context) }
    val audioPlayer = remember { AudioPlayerHelper() }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingTimerSec by remember { mutableStateOf(0) }

    // Cloudinary photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val uploadedUrl = CloudinaryHelper.uploadImage(context, uri)
                onSendMessage("", uploadedUrl, null, 0)
            }
        }
    }

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Recording timer
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingTimerSec = 0
            while (isRecordingAudio) {
                delay(1000)
                recordingTimerSec++
            }
        }
    }

    // Filter messages if search or pin active
    val displayMessages = remember(messages, searchQuery, isPinnedFilter) {
        messages.filter { msg ->
            val matchesSearch = searchQuery.isBlank() || msg.text.contains(searchQuery, ignoreCase = true) ||
                    msg.sender.name.contains(searchQuery, ignoreCase = true)
            val matchesPin = !isPinnedFilter || msg.isPinned
            matchesSearch && matchesPin
        }
    }

    // Mention suggestions when typing '@'
    val mentionQuery = remember(inputText) {
        val lastWord = inputText.substringAfterLast(" ")
        if (lastWord.startsWith("@")) lastWord.removePrefix("@") else null
    }
    val filteredMentions = remember(mentionQuery, members) {
        if (mentionQuery != null) {
            members.filter { it.name.contains(mentionQuery, ignoreCase = true) }
        } else emptyList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DiscordDarkChat,
        topBar = {
            Column(modifier = Modifier.background(DiscordDarkSidebar)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    channel.isPrivate -> Icons.Default.Lock
                                    channel.type == ChannelType.ANNOUNCEMENT -> Icons.Default.Campaign
                                    else -> Icons.Default.Tag
                                },
                                contentDescription = null,
                                tint = DiscordTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        color = DiscordTextWhite,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (channel.isPrivate) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(Privé)",
                                            color = DiscordTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (channel.topic.isNotBlank()) {
                                    Text(
                                        text = channel.topic,
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onToggleDrawer,
                            modifier = Modifier.testTag("chat_toggle_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Ouvrir les salons",
                                tint = DiscordTextWhite
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) onSearchQueryChange("")
                            },
                            modifier = Modifier.testTag("chat_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Rechercher",
                                tint = if (isSearchActive || searchQuery.isNotBlank()) DiscordBlurple else DiscordTextLight
                            )
                        }

                        IconButton(
                            onClick = onTogglePinnedFilter,
                            modifier = Modifier.testTag("chat_pinned_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Messages épinglés",
                                tint = if (isPinnedFilter) DiscordBlurple else DiscordTextLight
                            )
                        }

                        IconButton(
                            onClick = { showBackgroundDialog = true },
                            modifier = Modifier.testTag("chat_wallpaper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = "Personnaliser le fond",
                                tint = if (chatTheme != null) DiscordBlurple else DiscordTextLight
                            )
                        }

                        IconButton(
                            onClick = onToggleMemberList,
                            modifier = Modifier.testTag("chat_members_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Membres du serveur",
                                tint = DiscordTextLight
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DiscordDarkSidebar)
                )

                // Search field bar
                AnimatedVisibility(visible = isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Rechercher dans #${channel.name}", color = DiscordTextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("chat_search_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DiscordDarkInput,
                                unfocusedContainerColor = DiscordDarkInput,
                                focusedBorderColor = DiscordBlurple,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = DiscordTextWhite,
                                unfocusedTextColor = DiscordTextLight
                            ),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = DiscordTextMuted)
                                    }
                                }
                            }
                        )
                    }
                }

                // Pinned active banner
                if (isPinnedFilter) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DiscordDarkCard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PushPin, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Affichage des messages épinglés uniquement",
                                    color = DiscordTextLight,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Tout voir",
                                color = DiscordBlurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onTogglePinnedFilter() }
                            )
                        }
                    }
                }

                HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DiscordDarkChat)
            ) {
                // Mention autocomplete popover
                if (filteredMentions.isNotEmpty()) {
                    Surface(
                        color = DiscordDarkCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredMentions) { member ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = DiscordDarkInput,
                                    modifier = Modifier.clickable {
                                        val prefix = inputText.substringBeforeLast("@")
                                        inputText = "$prefix@${member.name} "
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("@", color = DiscordBlurple, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(member.name, color = DiscordTextWhite, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Replying preview
                if (replyingTo != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DiscordDarkSidebar
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = null,
                                    tint = DiscordBlurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Réponse à ${replyingTo.sender.name} : ${replyingTo.text.take(30)}...",
                                    color = DiscordTextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onCancelReply,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Annuler",
                                    tint = DiscordTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Quick reactions drawer
                AnimatedVisibility(visible = isEmojiBarOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DiscordDarkSidebar)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val quickReactions = listOf("heart", "fire", "thumb_up", "star", "smile", "rocket", "celebrate", "bolt")
                        quickReactions.forEach { key ->
                            Surface(
                                shape = CircleShape,
                                color = DiscordDarkInput,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (displayMessages.isNotEmpty()) {
                                            onToggleReaction(displayMessages.last().id, key)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppReactionIcon(iconKey = key, size = 18.dp)
                                }
                            }
                        }
                    }
                }

                // Voice note recording banner
                if (isRecordingAudio) {
                    Surface(
                        color = DiscordDarkCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(DiscordRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enregistrement vocal... ${recordingTimerSec}s",
                                    color = DiscordTextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    audioRecorder.stopRecording()
                                    isRecordingAudio = false
                                }) {
                                    Text("Annuler", color = DiscordTextMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val recordedPath = audioRecorder.stopRecording()
                                        isRecordingAudio = false
                                        if (recordedPath != null) {
                                            onSendMessage("", null, recordedPath, recordingTimerSec)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                                ) {
                                    Text("Envoyer", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Input bar
                if (!isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Image attachment button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DiscordDarkInput)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("chat_attachment_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Envoyer une photo",
                                tint = DiscordTextLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Poll creation button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DiscordDarkInput)
                                .clickable { showCreatePollDialog = true }
                                .testTag("chat_poll_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Poll,
                                contentDescription = "Créer un sondage",
                                tint = DiscordTextLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Envoyer dans #${channel.name}",
                                    color = DiscordTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DiscordDarkInput,
                                unfocusedContainerColor = DiscordDarkInput,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = DiscordTextWhite,
                                unfocusedTextColor = DiscordTextLight
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isEmojiBarOpen = !isEmojiBarOpen },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfied,
                                        contentDescription = "Emoji",
                                        tint = if (isEmojiBarOpen) DiscordBlurple else DiscordTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (inputText.isBlank()) {
                            // Mic button to record voice note
                            IconButton(
                                onClick = {
                                    val started = audioRecorder.startRecording()
                                    if (started) isRecordingAudio = true
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DiscordDarkInput)
                                    .testTag("chat_voice_record_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Enregistrer une note vocale",
                                    tint = DiscordTextLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Send text button
                            IconButton(
                                onClick = {
                                    onSendMessage(inputText, null, null, 0)
                                    inputText = ""
                                    isEmojiBarOpen = false
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DiscordBlurple)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Envoyer",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Chat Wallpaper / Theme background
            chatTheme?.let { theme ->
                if (!theme.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = theme.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val color1 = Color(theme.primaryColor)
                    val color2 = Color(theme.secondaryColor ?: theme.primaryColor)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(color1, color2)))
                    )
                }
                // Dark scrim for readability, with darkness adjusted by opacity setting
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 1f - (theme.opacity * 0.45f)))
                )
            }

            if (displayMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(DiscordDarkInput),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (channel.isPrivate) Icons.Default.Lock else Icons.Default.Tag,
                            contentDescription = null,
                            tint = DiscordBlurple,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Bienvenue dans #${channel.name} !",
                        color = DiscordTextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (channel.isPrivate) "Ce salon est privé. Seuls les membres autorisés y ont accès."
                        else "C'est le début du salon #${channel.name}. Soyez le premier à poster un message !",
                        color = DiscordTextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(displayMessages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            audioPlayer = audioPlayer,
                            currentUserId = currentUserId,
                            onLongClick = { selectedMessageForActions = message },
                            onToggleReaction = { emoji -> onToggleReaction(message.id, emoji) },
                            onReplyClick = { onReplyClick(message) },
                            onVotePoll = { optId -> onVotePoll(message.id, optId) }
                        )
                    }
                }
            }
        }
    }

    // Message options action sheet
    if (selectedMessageForActions != null) {
        val targetMsg = selectedMessageForActions!!
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = DiscordDarkCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = "Message de ${targetMsg.sender.name}",
                    color = DiscordTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val quickReactions = listOf("heart", "fire", "thumb_up", "star", "smile", "rocket", "celebrate", "bolt")
                    quickReactions.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DiscordDarkInput)
                                .clickable {
                                    onToggleReaction(targetMsg.id, key)
                                    selectedMessageForActions = null
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppReactionIcon(iconKey = key, size = 20.dp)
                        }
                    }
                }

                HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)

                ListItem(
                    headlineContent = { Text("Répondre", color = DiscordTextWhite) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = DiscordTextLight) },
                    modifier = Modifier.clickable {
                        onReplyClick(targetMsg)
                        selectedMessageForActions = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text(if (targetMsg.isPinned) "Désépingler le message" else "Épingler le message", color = DiscordTextWhite) },
                    leadingContent = { Icon(Icons.Default.PushPin, contentDescription = null, tint = DiscordTextLight) },
                    modifier = Modifier.clickable {
                        onPinMessage(targetMsg.id)
                        selectedMessageForActions = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (targetMsg.text.isNotBlank()) {
                    ListItem(
                        headlineContent = { Text("Copier le texte", color = DiscordTextWhite) },
                        leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DiscordTextLight) },
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(targetMsg.text))
                            selectedMessageForActions = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                ListItem(
                    headlineContent = { Text("Supprimer", color = DiscordRed) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = DiscordRed) },
                    modifier = Modifier.clickable {
                        onDeleteMessage(targetMsg.id)
                        selectedMessageForActions = null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    // Chat background customization dialog
    if (showBackgroundDialog) {
        ChatBackgroundDialog(
            currentTheme = chatTheme,
            onApplyTheme = { theme ->
                onApplyChatTheme(theme)
                showBackgroundDialog = false
            },
            onDismiss = { showBackgroundDialog = false }
        )
    }

    // Poll creation dialog
    if (showCreatePollDialog) {
        CreatePollDialog(
            onCreatePoll = { question, options, allowsMultiple ->
                onCreatePoll(question, options, allowsMultiple)
                showCreatePollDialog = false
            },
            onDismiss = { showCreatePollDialog = false }
        )
    }
}

@Composable
fun MessageItem(
    message: Message,
    audioPlayer: AudioPlayerHelper,
    currentUserId: String = "",
    onLongClick: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onReplyClick: () -> Unit,
    onVotePoll: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReplyClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("message_item_${message.id}")
    ) {
        // Replying quote preview if present
        if (message.replyToText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp))
                        .background(DiscordDarkBorder)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = DiscordTextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = message.replyToText,
                    color = DiscordTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Avatar with decoration
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(message.sender.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!message.sender.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = message.sender.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = message.sender.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                AvatarDecorationOverlay(
                    decorationKey = message.sender.avatarDecoration,
                    size = 40.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header (Name + Role + Timestamp + Pin)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.sender.name,
                        color = DiscordTextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Role Badge
                    when (message.sender.role) {
                        UserRole.ADMIN -> {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = DiscordBlurple,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        UserRole.MODERATOR -> {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = DiscordGreen,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "MOD",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        else -> {}
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = message.timestamp,
                        color = DiscordTextMuted,
                        fontSize = 11.sp
                    )

                    if (message.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Épinglé",
                            tint = DiscordBlurple,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Text body with mention styling
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = DiscordTextLight,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                // Image preview with Coil
                if (!message.imageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Image partagée",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DiscordDarkCard),
                        contentScale = ContentScale.Crop
                    )
                }

                // Audio Voice Note Player
                if (!message.audioUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    var isPlayingThis by remember { mutableStateOf(false) }

                    Surface(
                        color = DiscordDarkCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlayingThis) {
                                        audioPlayer.stop()
                                        isPlayingThis = false
                                    } else {
                                        audioPlayer.play(message.audioUrl) {
                                            isPlayingThis = false
                                        }
                                        isPlayingThis = true
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DiscordBlurple)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingThis) "Pause" else "Écouter",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlayingThis) "Lecture en cours..." else "Message vocal",
                                    color = DiscordTextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (message.audioDurationSec > 0) "${message.audioDurationSec}s" else "Audio",
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (isPlayingThis) DiscordBlurple else DiscordTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Poll Card if present
                if (message.poll != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PollCard(
                        poll = message.poll,
                        currentUserId = currentUserId,
                        onVoteOption = onVotePoll
                    )
                }

                // Reactions bar
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.reactions.forEach { reaction ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (reaction.userReacted) DiscordBlurple.copy(alpha = 0.25f) else DiscordDarkInput,
                                border = if (reaction.userReacted) androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onToggleReaction(reaction.emoji) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    AppReactionIcon(iconKey = reaction.emoji, size = 15.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = reaction.count.toString(),
                                        color = if (reaction.userReacted) Color.White else DiscordTextLight,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick options
            IconButton(
                onClick = onLongClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = DiscordTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
