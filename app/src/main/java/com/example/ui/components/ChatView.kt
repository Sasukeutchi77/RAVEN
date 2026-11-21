package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PollUiState
import com.example.ui.viewmodel.PollViewModel
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.util.CloudinaryHelper
import com.example.util.LocalRavenPlatform
import com.example.util.desktopHoverHighlight
import com.example.util.desktopHoverPointer
import com.example.util.onSecondaryMouseClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
    channel: Channel,
    messages: List<Message>,
    replyingTo: Message?,
    searchQuery: String,
    isPinnedFilter: Boolean,
    members: List<User> = emptyList(),
    serverCustomRoles: List<CustomRole> = emptyList(),
    onSendMessage: (text: String, imageUrl: String?, audioUrl: String?, audioDurationSec: Int) -> Unit,
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onReplyClick: (Message) -> Unit,
    onCancelReply: () -> Unit,
    onPinMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onEditMessage: (messageId: String, newText: String) -> Unit = { _, _ -> },
    onOpenThread: (Message) -> Unit = {},
    onTogglePinnedFilter: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleDrawer: () -> Unit,
    onToggleMemberList: () -> Unit,
    unreadNotificationsCount: Int = 0,
    onOpenInbox: () -> Unit = {},
    chatTheme: ChatBackgroundTheme? = null,
    onApplyChatTheme: (ChatBackgroundTheme) -> Unit = {},
    onCreatePoll: (question: String, options: List<String>, allowsMultiple: Boolean) -> Unit = { _, _, _ -> },
    onVotePoll: (messageId: String, optionId: String) -> Unit = { _, _ -> },
    unlockedStickers: List<String> = emptyList(),
    orbsBalance: Int = 0,
    typingUsers: List<String> = emptyList(),
    onExecuteSlashCommand: (String) -> Boolean = { false },
    onSendSticker: (String) -> Unit = {},
    onUnlockAndSendSticker: (String, Int) -> Boolean = { _, _ -> false },
    onSendFileAttachment: (fileName: String, fileType: String, sizeLabel: String) -> Unit = { _, _, _ -> },
    onOpenGlobalSearch: () -> Unit = {},
    onOpenSharedMedia: () -> Unit = {},
    showNavigationDrawerButton: Boolean = true,
    slowmodeCooldownUntilMs: Long = 0L,
    sendBlockedReason: String? = null,
    onDismissBlockedReason: () -> Unit = {},
    chatDisplayStyle: ChatDisplayStyle = ChatDisplayStyle.COMPACT,
    glassmorphismEnabled: Boolean = true,
    initialDraft: String = "",
    onDraftChange: (String) -> Unit = {},
    onTyping: () -> Unit = {},
    scheduledMessagesForChannel: List<ScheduledMessage> = emptyList(),
    onScheduleMessage: (text: String, delayMs: Long, delayLabel: String) -> Unit = { _, _, _ -> },
    onCancelScheduledMessage: (String) -> Unit = {},
    onSendScheduledNow: (String) -> Unit = {},
    onScheduleReminder: (message: Message, delayMs: Long, delayLabel: String) -> Unit = { _, _, _ -> },
    onOpenWhiteboard: () -> Unit = {},
    onOpenAiAssistant: () -> Unit = {},
    botSlashCommands: List<SlashCommandOption> = emptyList(),
    currentUserId: String = "",
    currentUserName: String = "",
    pollViewModel: PollViewModel? = null,
    onOpenPollsHub: () -> Unit = {},
    onOpenAdvancedPollEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val platform = LocalRavenPlatform.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val pollUiState = pollViewModel?.uiState?.collectAsState()?.value
    var inspectAdvancedPollPair by remember { mutableStateOf<Pair<AdvancedPoll, Boolean>?>(null) }

    var inputText by remember(channel.id) { mutableStateOf(initialDraft) }
    var isSearchActive by remember { mutableStateOf(false) }
    var isEmojiBarOpen by remember { mutableStateOf(false) }
    var isAttachmentTrayOpen by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<Message?>(null) }
    var messageForEditDialog by remember { mutableStateOf<Message?>(null) }
    var messageForEmojiPicker by remember { mutableStateOf<Message?>(null) }
    var messageForReminderDialog by remember { mutableStateOf<Message?>(null) }
    var showScheduleMessageDialog by remember { mutableStateOf(false) }
    var showInputEmojiPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showCreatePollDialog by remember { mutableStateOf(false) }

    var slowmodeRemainingSec by remember(slowmodeCooldownUntilMs) { mutableIntStateOf(0) }
    LaunchedEffect(slowmodeCooldownUntilMs) {
        while (true) {
            val rem = ((slowmodeCooldownUntilMs - System.currentTimeMillis() + 999) / 1000).toInt()
            if (rem > 0) {
                slowmodeRemainingSec = rem
                delay(500)
            } else {
                slowmodeRemainingSec = 0
                break
            }
        }
    }

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

    // Zero-permission document / file picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var displayName = "document_partage.pdf"
            var sizeLabel = "Fichier"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                        if (sizeIdx >= 0) {
                            val bytes = cursor.getLong(sizeIdx)
                            sizeLabel = when {
                                bytes >= 1024 * 1024 -> String.format("%.1f Mo", bytes / (1024f * 1024f))
                                bytes >= 1024 -> "${bytes / 1024} Ko"
                                bytes > 0 -> "$bytes o"
                                else -> "Document"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onSendFileAttachment(displayName, mimeType, sizeLabel)
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
        containerColor = palette.chatBg,
        topBar = {
            Column(modifier = Modifier.background(palette.sidebarBg)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = resolveChannelIconVector(channel),
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        color = palette.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (channel.isPrivate) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(Privé)",
                                            color = palette.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (channel.isReadOnly) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Lock, contentDescription = "Lecture seule", tint = DiscordAmber, modifier = Modifier.size(13.dp))
                                    } else if (channel.slowmodeSeconds > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${channel.slowmodeSeconds}s",
                                            color = DiscordAmber,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (channel.topic.isNotBlank()) {
                                    Text(
                                        text = channel.topic,
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        if (showNavigationDrawerButton) {
                            IconButton(
                                onClick = onToggleDrawer,
                                modifier = Modifier.testTag("chat_toggle_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Ouvrir les salons",
                                    tint = palette.textPrimary
                                )
                            }
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
                                tint = if (isSearchActive || searchQuery.isNotBlank()) DiscordBlurple else palette.textSecondary
                            )
                        }

                        IconButton(
                            onClick = onOpenSharedMedia,
                            modifier = Modifier.testTag("chat_shared_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = "Médias & Fichiers partagés",
                                tint = palette.textSecondary
                            )
                        }

                        IconButton(
                            onClick = onTogglePinnedFilter,
                            modifier = Modifier.testTag("chat_pinned_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Messages épinglés",
                                tint = if (isPinnedFilter) DiscordBlurple else palette.textSecondary
                            )
                        }

                        IconButton(
                            onClick = { showBackgroundDialog = true },
                            modifier = Modifier.testTag("chat_wallpaper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = "Personnaliser le fond",
                                tint = if (chatTheme != null) DiscordBlurple else palette.textSecondary
                            )
                        }

                        IconButton(
                            onClick = onOpenInbox,
                            modifier = Modifier.testTag("chat_inbox_button")
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Boîte de réception",
                                    tint = if (unreadNotificationsCount > 0) DiscordBlurple else palette.textSecondary
                                )
                                if (unreadNotificationsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(DiscordRed)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onOpenPollsHub,
                            modifier = Modifier.testTag("chat_polls_hub_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Poll,
                                contentDescription = "Sondages avancés",
                                tint = Color(0xFFEB459E)
                            )
                        }

                        IconButton(
                            onClick = onOpenAiAssistant,
                            modifier = Modifier.testTag("chat_ai_assistant_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Ouvrir RAVEN AI",
                                tint = Color(0xFFA855F7)
                            )
                        }

                        IconButton(
                            onClick = onToggleMemberList,
                            modifier = Modifier.testTag("chat_members_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Membres du serveur",
                                tint = palette.textSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.sidebarBg)
                )

                // Search field bar
                AnimatedVisibility(visible = isSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        FilledTonalButton(
                            onClick = onOpenGlobalSearch,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("chat_global_search_button")
                        ) {
                            Icon(Icons.Default.ManageSearch, contentDescription = "Recherche globale", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Partout", fontSize = 11.sp)
                        }
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
                TypingIndicatorBar(typingUserNames = typingUsers)

                if (inputText.startsWith("/")) {
                    SlashCommandMenu(
                        query = inputText,
                        extraBotCommands = botSlashCommands,
                        onSelectCommand = { option ->
                            if (option.command in listOf("/pileouface", "/xp", "/clear")) {
                                onExecuteSlashCommand(option.command)
                                inputText = ""
                            } else {
                                inputText = "${option.command} "
                            }
                        }
                    )
                }

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

                // Quick emoji bar for inserting emojis into message composer
                AnimatedVisibility(visible = isEmojiBarOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DiscordDarkSidebar)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EmojiCatalog.quickReactions.forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = DiscordDarkInput,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        inputText += emoji
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 17.sp)
                                }
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = DiscordBlurple.copy(alpha = 0.2f),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    isEmojiBarOpen = false
                                    showInputEmojiPicker = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Tous les emojis",
                                    tint = DiscordBlurple,
                                    modifier = Modifier.size(18.dp)
                                )
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

                // Expandable Discord "+" Attachment & Action Tray
                AnimatedVisibility(visible = isAttachmentTrayOpen && !isRecordingAudio) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DiscordDarkSidebar)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                label = { Text("Photo", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_attachment_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    documentPickerLauncher.launch(arrayOf("*/*"))
                                },
                                label = { Text("Fichier", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_file_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    showStickerPicker = true
                                },
                                label = { Text("Stickers", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_sticker_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    if (pollViewModel != null) onOpenAdvancedPollEditor() else showCreatePollDialog = true
                                },
                                label = { Text("Sondage", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null, tint = Color(0xFFEB459E), modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_poll_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    onOpenWhiteboard()
                                },
                                label = { Text("Tableau Blanc", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_whiteboard_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    showScheduleMessageDialog = true
                                },
                                label = { Text("Programmer", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_schedule_button")
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    isAttachmentTrayOpen = false
                                    inputText = "/"
                                },
                                label = { Text("Slash /", color = DiscordTextWhite, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("chat_slash_trigger_button")
                            )
                        }
                    }
                }

                // Scheduled messages pending banner
                if (scheduledMessagesForChannel.isNotEmpty()) {
                    val nextSched = scheduledMessagesForChannel.first()
                    Surface(
                        color = palette.accent.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth().testTag("scheduled_messages_banner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = palette.accent, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${scheduledMessagesForChannel.size} programmé(s) (${nextSched.delayLabel}) : ${nextSched.text}",
                                    color = palette.textPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                TextButton(
                                    onClick = { onSendScheduledNow(nextSched.id) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Envoyer", color = DiscordGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { onCancelScheduledMessage(nextSched.id) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Annuler", color = DiscordRed, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Read-Only / Slowmode / Error notice banner
                if (channel.isReadOnly || slowmodeRemainingSec > 0 || sendBlockedReason != null) {
                    Surface(
                        color = DiscordAmber.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (channel.isReadOnly) Icons.Default.Lock else Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = DiscordAmber,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val noticeText = when {
                                    channel.isReadOnly -> "Salon en lecture seule — envoi de messages verrouillé."
                                    slowmodeRemainingSec > 0 -> "Mode lent actif : prochain envoi dans ${slowmodeRemainingSec}s"
                                    else -> sendBlockedReason ?: ""
                                }
                                Text(
                                    text = noticeText,
                                    color = DiscordAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (sendBlockedReason != null && !channel.isReadOnly && slowmodeRemainingSec == 0) {
                                IconButton(
                                    onClick = onDismissBlockedReason,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordAmber, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Input bar
                if (!isRecordingAudio) {
                    val canCompose = !channel.isReadOnly
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discord "+" Tray button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isAttachmentTrayOpen) DiscordBlurple else DiscordDarkInput)
                                .clickable(enabled = canCompose) { isAttachmentTrayOpen = !isAttachmentTrayOpen }
                                .testTag("chat_plus_tray_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAttachmentTrayOpen) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Plus d'actions",
                                tint = if (isAttachmentTrayOpen) Color.White else DiscordTextLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Stickers quick button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DiscordDarkInput)
                                .clickable(enabled = canCompose) { showStickerPicker = true }
                                .testTag("chat_quick_sticker_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Envoyer un Sticker",
                                tint = DiscordAmber,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Field
                        val dispatchChatInput = {
                            if (canCompose && slowmodeRemainingSec == 0 && inputText.isNotBlank()) {
                                val handledSlash = if (inputText.trim().startsWith("/")) {
                                    onExecuteSlashCommand(inputText)
                                } else false
                                if (!handledSlash) {
                                    onSendMessage(inputText, null, null, 0)
                                }
                                inputText = ""
                                onDraftChange("")
                                isEmojiBarOpen = false
                                isAttachmentTrayOpen = false
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onDraftChange(it)
                                if (it.isNotBlank()) onTyping()
                                if (sendBlockedReason != null) onDismissBlockedReason()
                            },
                            enabled = canCompose,
                            placeholder = {
                                Text(
                                    text = when {
                                        channel.isReadOnly -> "Salon en lecture seule"
                                        slowmodeRemainingSec > 0 -> "Slowmode (${slowmodeRemainingSec}s)..."
                                        else -> "Envoyer dans #${channel.name}"
                                    },
                                    color = DiscordTextMuted,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    val isEnterKey = keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                                    if (platform.supportsEnterToSend &&
                                        isEnterKey &&
                                        keyEvent.type == KeyEventType.KeyDown &&
                                        !keyEvent.isShiftPressed &&
                                        !keyEvent.isCtrlPressed &&
                                        !keyEvent.isAltPressed
                                    ) {
                                        dispatchChatInput()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DiscordDarkInput,
                                unfocusedContainerColor = DiscordDarkInput,
                                disabledContainerColor = DiscordDarkInput.copy(alpha = 0.5f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedTextColor = DiscordTextWhite,
                                unfocusedTextColor = DiscordTextLight
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = { isEmojiBarOpen = !isEmojiBarOpen },
                                    enabled = canCompose,
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
                                enabled = canCompose && slowmodeRemainingSec == 0,
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
                            // Send text / slash command button
                            IconButton(
                                onClick = {
                                    val handledSlash = if (inputText.trim().startsWith("/")) {
                                        onExecuteSlashCommand(inputText)
                                    } else false
                                    if (!handledSlash) {
                                        onSendMessage(inputText, null, null, 0)
                                    }
                                    inputText = ""
                                    onDraftChange("")
                                    isEmojiBarOpen = false
                                    isAttachmentTrayOpen = false
                                },
                                enabled = canCompose && slowmodeRemainingSec == 0,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (canCompose && slowmodeRemainingSec == 0) DiscordBlurple else DiscordDarkInput)
                                        .desktopHoverPointer(platform.isPc)
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
                    if (platform.supportsEnterToSend && canCompose) {
                        Text(
                            text = "Entrée pour envoyer • Maj+Entrée nouvelle ligne • Clic droit sur un message pour les options",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                        )
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
                            serverCustomRoles = serverCustomRoles,
                            chatDisplayStyle = chatDisplayStyle,
                            glassmorphismEnabled = glassmorphismEnabled,
                            currentUserId = currentUserId,
                            currentUserName = currentUserName,
                            onLongClick = { selectedMessageForActions = message },
                            onToggleReaction = { emoji -> onToggleReaction(message.id, emoji) },
                            onAddReactionClick = { messageForEmojiPicker = message },
                            onReplyClick = { onReplyClick(message) },
                            onOpenThread = { onOpenThread(message) },
                            onVotePoll = { optId -> onVotePoll(message.id, optId) },
                            pollViewModel = pollViewModel,
                            pollUiState = pollUiState,
                            onOpenAdvancedPollStats = { advPoll, startExport ->
                                inspectAdvancedPollPair = advPoll to startExport
                            }
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiCatalog.quickReactions.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DiscordDarkInput)
                                .clickable {
                                    onToggleReaction(targetMsg.id, emoji)
                                    selectedMessageForActions = null
                                }
                                .testTag("sheet_quick_reaction_$emoji"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 19.sp)
                        }
                    }

                    // Open full Emoji Picker button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DiscordBlurple.copy(alpha = 0.2f))
                            .clickable {
                                val target = targetMsg
                                selectedMessageForActions = null
                                messageForEmojiPicker = target
                            }
                            .testTag("sheet_open_emoji_picker"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddReaction,
                            contentDescription = "Choisir un autre emoji",
                            tint = DiscordBlurple,
                            modifier = Modifier.size(19.dp)
                        )
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
                    headlineContent = { Text("Ouvrir un fil de discussion", color = DiscordTextWhite) },
                    leadingContent = { Icon(Icons.Default.Forum, contentDescription = null, tint = DiscordBlurple) },
                    modifier = Modifier
                        .clickable {
                            onOpenThread(targetMsg)
                            selectedMessageForActions = null
                        }
                        .testTag("sheet_open_thread"),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                if (targetMsg.text.isNotBlank() && (targetMsg.sender.id == currentUserId || targetMsg.sender.id == "user_me")) {
                    ListItem(
                        headlineContent = { Text("Modifier le message", color = DiscordTextWhite) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = DiscordTextLight) },
                        modifier = Modifier
                            .clickable {
                                messageForEditDialog = targetMsg
                                selectedMessageForActions = null
                            }
                            .testTag("sheet_edit_message"),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

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
                    headlineContent = { Text("Me le rappeler plus tard", color = DiscordTextWhite) },
                    leadingContent = { Icon(Icons.Default.Alarm, contentDescription = null, tint = DiscordAmber) },
                    modifier = Modifier
                        .clickable {
                            messageForReminderDialog = targetMsg
                            selectedMessageForActions = null
                        }
                        .testTag("sheet_remind_message"),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

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

    if (messageForEditDialog != null) {
        val editing = messageForEditDialog!!
        EditMessageDialog(
            initialText = editing.text,
            onSave = { updated ->
                onEditMessage(editing.id, updated)
                messageForEditDialog = null
            },
            onDismiss = { messageForEditDialog = null }
        )
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

    // Full Emoji Reaction Picker for a specific message
    if (messageForEmojiPicker != null) {
        val target = messageForEmojiPicker!!
        EmojiPickerModal(
            title = "Réagir au message de ${target.sender.name}",
            onSelectEmoji = { emoji ->
                onToggleReaction(target.id, emoji)
                messageForEmojiPicker = null
            },
            onDismiss = { messageForEmojiPicker = null }
        )
    }

    // Full Emoji Picker for message input field
    if (showInputEmojiPicker) {
        EmojiPickerModal(
            title = "Insérer un emoji",
            onSelectEmoji = { emoji ->
                inputText += emoji
                showInputEmojiPicker = false
            },
            onDismiss = { showInputEmojiPicker = false }
        )
    }

    // Raven Stickers Picker
    if (showStickerPicker) {
        StickerPickerSheet(
            unlockedStickerIds = unlockedStickers,
            orbsBalance = orbsBalance,
            onSelectSticker = onSendSticker,
            onUnlockAndSendSticker = onUnlockAndSendSticker,
            onDismiss = { showStickerPicker = false }
        )
    }

    // Message Reminder Dialog
    messageForReminderDialog?.let { remMsg ->
        MessageReminderDialog(
            message = remMsg,
            onScheduleReminder = { delayMs, label ->
                onScheduleReminder(remMsg, delayMs, label)
            },
            onDismiss = { messageForReminderDialog = null }
        )
    }

    // Schedule Message Dialog
    if (showScheduleMessageDialog) {
        ScheduleMessageDialog(
            initialText = inputText,
            targetLabel = "#${channel.name}",
            onSchedule = { text, delayMs, delayLabel ->
                onScheduleMessage(text, delayMs, delayLabel)
                inputText = ""
                onDraftChange("")
            },
            onDismiss = { showScheduleMessageDialog = false }
        )
    }

    if (pollViewModel != null) {
        inspectAdvancedPollPair?.let { (advPoll, startExport) ->
            val freshPoll = pollUiState?.polls?.find { it.id == advPoll.id } ?: advPoll
            val stats = remember(freshPoll, pollUiState?.votesByPoll) { pollViewModel.computeStats(freshPoll) }
            AdvancedPollStatsAndExportSheet(
                poll = freshPoll,
                stats = stats,
                startOnExportTab = startExport,
                exportContentProvider = { fmt -> pollViewModel.exportPoll(freshPoll, fmt) },
                onDismiss = { inspectAdvancedPollPair = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageItem(
    message: Message,
    audioPlayer: AudioPlayerHelper,
    serverCustomRoles: List<CustomRole> = emptyList(),
    chatDisplayStyle: ChatDisplayStyle = ChatDisplayStyle.COMPACT,
    glassmorphismEnabled: Boolean = true,
    currentUserId: String = "",
    currentUserName: String = "",
    onLongClick: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onAddReactionClick: () -> Unit = onLongClick,
    onReplyClick: () -> Unit,
    onOpenThread: (() -> Unit)? = null,
    onVotePoll: (String) -> Unit = {},
    pollViewModel: PollViewModel? = null,
    pollUiState: PollUiState? = null,
    onOpenAdvancedPollStats: (AdvancedPoll, Boolean) -> Unit = { _, _ -> }
) {
    val palette = ravenPalette()
    val isMine = message.sender.id == currentUserId || message.sender.id == "user_me"
    val customRole = remember(message.sender.customRoleId, serverCustomRoles) {
        message.sender.customRoleId?.let { rid -> serverCustomRoles.find { it.id == rid } }
    }
    var swipeOffsetX by remember { mutableFloatStateOf(0f) }

    val isMentioned = remember(message.text, message.mentions, currentUserName) {
        message.text.contains("@everyone", ignoreCase = true) ||
            (currentUserName.isNotBlank() && message.text.contains("@$currentUserName", ignoreCase = true)) ||
            message.mentions.any {
                it.equals("everyone", ignoreCase = true) ||
                    (currentUserName.isNotBlank() && it.equals(currentUserName, ignoreCase = true))
            }
    }

    val containerModifier = when {
        chatDisplayStyle == ChatDisplayStyle.BUBBLES -> {
            val bubbleBg = when {
                message.isSystemAnnouncement -> palette.accent.copy(alpha = 0.16f)
                isMine -> palette.accent.copy(alpha = if (glassmorphismEnabled) 0.18f else 0.28f)
                else -> palette.cardBg.copy(alpha = if (glassmorphismEnabled) 0.78f else 1f)
            }
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleBg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        }
        message.isSystemAnnouncement -> Modifier
            .background(palette.accent.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
        isMentioned -> Modifier
            .background(DiscordAmber.copy(alpha = 0.11f))
            .padding(horizontal = 16.dp, vertical = 6.dp)
        else -> Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (swipeOffsetX > 18f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Glisser pour répondre",
                tint = palette.accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(20.dp)
            )
        }
        val desktopMode = LocalRavenPlatform.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        swipeOffsetX = (swipeOffsetX + delta).coerceIn(0f, 110f)
                    },
                    onDragStopped = {
                        if (swipeOffsetX >= 60f) {
                            onReplyClick()
                        }
                        swipeOffsetX = 0f
                    }
                )
                .desktopHoverHighlight(
                    shape = RoundedCornerShape(8.dp),
                    enabled = desktopMode.isPc || desktopMode.hasMouseOrTrackpad
                )
                .onSecondaryMouseClick(enabled = true) { onLongClick() }
                .clickable(onClick = onReplyClick)
                .then(containerModifier)
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
                        color = customRole?.let { Color(it.colorHex) } ?: palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!message.sender.clanTag.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = palette.accent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = message.sender.clanTag,
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (customRole != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    if (customRole.secondaryColorHex != null) {
                                        Modifier.background(
                                            Brush.horizontalGradient(
                                                listOf(Color(customRole.colorHex), Color(customRole.secondaryColorHex))
                                            )
                                        )
                                    } else {
                                        Modifier.background(Color(customRole.colorHex))
                                    }
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = customRole.name,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
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
                            UserRole.BOT -> {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = DiscordBlurple,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "BOT",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            else -> {}
                        }
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

                // Text body with mention & link styling + edited marker
                if (message.text.isNotBlank()) {
                    RichMessageText(
                        text = message.text,
                        isEdited = message.isEdited,
                        textColor = palette.textSecondary,
                        mutedColor = palette.textMuted
                    )
                    LinkPreviewCard(text = message.text)
                }

                // Sticker Bubble if present
                if (!message.stickerId.isNullOrBlank()) {
                    StickerMessageBubble(stickerId = message.stickerId)
                }

                // File Attachment Card if present
                if (!message.attachmentName.isNullOrBlank()) {
                    FileAttachmentCard(
                        fileName = message.attachmentName,
                        attachmentType = message.attachmentType,
                        sizeLabel = message.attachmentSizeLabel
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
                            .background(palette.cardBg),
                        contentScale = ContentScale.Crop
                    )
                }

                // Audio Voice Note Player with animated waveform
                if (!message.audioUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    var isPlayingThis by remember { mutableStateOf(false) }

                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth(0.88f)
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
                                VoiceWaveformVisualizer(isPlaying = isPlayingThis)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isPlayingThis) "Lecture en cours..." else "Note vocale • ${if (message.audioDurationSec > 0) "${message.audioDurationSec}s" else "Audio"}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Poll Card if present (live AdvancedPollCard when linked, fallback to basic PollCard)
                if (message.poll != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val advId = message.poll.advancedPollId
                    val linkedAdv = if (advId != null && pollViewModel != null && pollUiState != null) {
                        pollUiState.polls.find { it.id == advId }
                    } else null
                    if (linkedAdv != null && pollViewModel != null && pollUiState != null) {
                        val (canVote, blockedReason) = pollViewModel.canVote(linkedAdv)
                        AdvancedPollCard(
                            poll = linkedAdv,
                            myVote = pollUiState.myVotesByPoll[linkedAdv.id],
                            allVotes = pollUiState.votesByPoll[linkedAdv.id].orEmpty(),
                            currentUserId = pollViewModel.currentUserId(),
                            canVote = canVote,
                            voteBlockedReason = blockedReason,
                            canManage = pollViewModel.canManage(linkedAdv),
                            onToggleOption = { optId -> pollViewModel.toggleVote(linkedAdv.id, optId) },
                            onRetractVote = { pollViewModel.retractVote(linkedAdv.id) },
                            onEditDraft = {},
                            onPublishDraft = { pollViewModel.publishExistingDraft(linkedAdv.id) },
                            onClosePoll = { pollViewModel.closePollManually(linkedAdv.id) },
                            onOpenStats = { onOpenAdvancedPollStats(linkedAdv, false) },
                            onOpenExport = { onOpenAdvancedPollStats(linkedAdv, true) },
                            onDeletePoll = { pollViewModel.deletePoll(linkedAdv.id) }
                        )
                    } else {
                        PollCard(
                            poll = message.poll,
                            currentUserId = currentUserId,
                            onVoteOption = onVotePoll
                        )
                    }
                }

                // Thread pill if replies exist
                if (message.threadCount > 0 && onOpenThread != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DiscordBlurple.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenThread)
                            .testTag("message_thread_pill_${message.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${message.threadCount} réponse${if (message.threadCount > 1) "s" else ""} • Voir le fil",
                                color = DiscordBlurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Reactions badges below the message content (wrapped FlowRow + Add Reaction chip)
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reactions_row_${message.id}"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.reactions.forEach { reaction ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (reaction.userReacted) DiscordBlurple.copy(alpha = 0.25f) else DiscordDarkInput,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (reaction.userReacted) DiscordBlurple else DiscordDarkBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleReaction(reaction.emoji) }
                                    .testTag("reaction_badge_${message.id}_${reaction.emoji}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    AppReactionIcon(iconKey = reaction.emoji, size = 16.dp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = reaction.count.toString(),
                                        color = if (reaction.userReacted) Color.White else DiscordTextLight,
                                        fontSize = 12.sp,
                                        fontWeight = if (reaction.userReacted) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // "+" Add Reaction pill right beside existing badges
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DiscordDarkInput.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onAddReactionClick)
                                .testTag("reaction_add_badge_${message.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AddReaction,
                                    contentDescription = "Ajouter une réaction",
                                    tint = DiscordTextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick add-reaction & options column
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAddReactionClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("message_add_reaction_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddReaction,
                        contentDescription = "Réagir avec un emoji",
                        tint = DiscordTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier.size(28.dp)
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
    }
}
