package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StoryUiState
import com.example.util.AudioPlayerHelper
import com.example.util.AudioRecorderHelper
import com.example.util.CloudinaryHelper
import com.example.util.LocalRavenPlatform
import com.example.util.desktopHoverHighlight
import com.example.util.desktopHoverPointer
import com.example.util.onSecondaryMouseClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectMessagesView(
    conversations: List<DirectConversation>,
    allFriends: List<User>,
    selectedDmId: String?,
    messages: Map<String, List<Message>>,
    currentUser: User,
    onSelectDm: (String) -> Unit,
    onCloseDm: () -> Unit,
    onSendMessage: (dmId: String, text: String, imageUrl: String?, audioUrl: String?, audioDurationSec: Int) -> Unit,
    onToggleReaction: (dmId: String, messageId: String, emoji: String) -> Unit,
    onAddFriend: (String) -> Boolean,
    onStartCall: (User) -> Unit,
    onStartGroupCall: (DirectConversation) -> Unit = {},
    onToggleDrawer: () -> Unit,
    unreadNotificationsCount: Int = 0,
    onOpenInbox: () -> Unit = {},
    chatThemes: Map<String, ChatBackgroundTheme> = emptyMap(),
    onApplyChatTheme: (dmId: String, ChatBackgroundTheme) -> Unit = { _, _ -> },
    friendRequests: List<FriendRequest> = emptyList(),
    blockedUsers: List<User> = emptyList(),
    typingUsersByTarget: Map<String, List<String>> = emptyMap(),
    unlockedStickers: List<String> = emptyList(),
    orbsBalance: Int = 0,
    onCreateGroup: (String, List<User>) -> Unit = { _, _ -> },
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineOrCancelFriendRequest: (String) -> Unit = {},
    onBlockUser: (User) -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    onSendSticker: (dmId: String, stickerId: String) -> Unit = { _, _ -> },
    onUnlockAndSendSticker: (dmId: String, stickerId: String, priceOrbs: Int) -> Boolean = { _, _, _ -> false },
    onSendFileAttachment: (dmId: String, fileName: String, fileType: String, sizeLabel: String) -> Unit = { _, _, _, _ -> },
    onOpenGlobalSearch: () -> Unit = {},
    onOpenSharedMedia: (dmId: String, title: String) -> Unit = { _, _ -> },
    showNavigationDrawerButton: Boolean = true,
    storyUiState: StoryUiState = StoryUiState(),
    onCreateStoryClick: () -> Unit = {},
    onOpenStoryGroup: (StoryAuthorGroup) -> Unit = {},
    friends: List<FriendRelationship> = emptyList(),
    onOpenFriendDm: (User) -> Unit = {},
    onOpenUserProfile: (User) -> Unit = {},
    onRemoveFriend: (String) -> Unit = {},
    onDeclineFriendRequest: ((String) -> Unit)? = null,
    onCancelFriendRequest: ((String) -> Unit)? = null,
    onOpenUserSearchSheet: () -> Unit = {},
    onOpenFriendPrivacy: () -> Unit = {},
    useSplitPane: Boolean = LocalRavenPlatform.current.useSplitPaneDm,
    isDesktopSidebarDocked: Boolean = LocalRavenPlatform.current.useDesktopLayout,
    bots: List<RavenBot> = emptyList(),
    botSlashCommandsForConv: (DirectConversation) -> List<SlashCommandOption> = { emptyList() },
    onExecuteDmCommand: (dmId: String, rawInput: String) -> Boolean = { _, _ -> false },
    onOpenBotDm: (String) -> Unit = {},
    onSendRichMessage: ((dmId: String, text: String, imageUrl: String?, audioUrl: String?, audioDurationSec: Int, waveformAmplitudes: List<Int>, isViewOnce: Boolean) -> Unit)? = null,
    onStartVideoCall: ((User) -> Unit)? = null,
    onOpenCallHistory: (() -> Unit)? = null,
    ephemeralTimersByTarget: Map<String, Int> = emptyMap(),
    onSetEphemeralTimer: (targetId: String, seconds: Int) -> Unit = { _, _ -> },
    onOpenViewOnce: (Message) -> Unit = {},
    onOpenForwardSheet: (Message) -> Unit = {},
    onTipOrbsOnMessage: (Message, Int) -> Unit = { _, _ -> },
    onScheduleReminder: (targetId: String, message: Message, delayMs: Long, label: String) -> Unit = { _, _, _, _ -> },
    lockedConversationIds: Set<String> = emptySet(),
    onToggleLockConversation: (dmId: String) -> Unit = {},
    biometricUnlockEnabled: Boolean = false,
    hasAccessCodeConfigured: Boolean = false,
    onVerifyLockAccessCode: (String) -> Boolean = { false },
    isEffectivelyOnline: Boolean = true,
    pendingOfflineCount: Int = 0,
    onFlushOfflineQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val platform = LocalRavenPlatform.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var selectedFilter by remember { mutableStateOf("En ligne") }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var isAttachmentTrayOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var dmInputText by remember { mutableStateOf("") }
    var selectedDmMessageForActions by remember { mutableStateOf<Message?>(null) }
    var dmMessageForEmojiPicker by remember { mutableStateOf<Message?>(null) }
    var showDmInputEmojiPicker by remember { mutableStateOf(false) }
    var showEphemeralDialogForDm by remember { mutableStateOf<String?>(null) }
    var dmMessageForReadReceipts by remember { mutableStateOf<Message?>(null) }
    var dmMessageForTipOrbs by remember { mutableStateOf<Message?>(null) }
    var dmMessageForReminder by remember { mutableStateOf<Message?>(null) }
    var viewOncePreviewMsg by remember { mutableStateOf<Message?>(null) }
    var pickNextPhotoAsViewOnce by remember { mutableStateOf(false) }
    val sessionUnlockedDmIds = remember { mutableStateListOf<String>() }
    LaunchedEffect(currentUser.id) {
        sessionUnlockedDmIds.clear()
    }

    val audioRecorder = remember { AudioRecorderHelper(context) }
    val audioPlayer = remember { AudioPlayerHelper() }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isRecordingLocked by remember { mutableStateOf(false) }
    var recordingTimerSec by remember { mutableStateOf(0) }
    val recordingAmplitudes = remember { mutableStateListOf<Int>() }

    val activeConversation = conversations.find { it.id == selectedDmId }

    // Cloudinary / gallery photo picker for DMs
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val asViewOnce = pickNextPhotoAsViewOnce
        pickNextPhotoAsViewOnce = false
        if (uri != null && activeConversation != null) {
            coroutineScope.launch {
                val uploadedUrl = CloudinaryHelper.uploadImage(context, uri)
                if (uploadedUrl.isNotBlank()) {
                    if (onSendRichMessage != null) {
                        onSendRichMessage(activeConversation.id, "", uploadedUrl, null, 0, emptyList(), asViewOnce)
                    } else {
                        onSendMessage(activeConversation.id, "", uploadedUrl, null, 0)
                    }
                }
            }
        }
    }

    // Document picker for DMs
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && activeConversation != null) {
            var displayName = "fichier_partage.pdf"
            var sizeLabel = "Document"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
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
            } catch (_: Exception) {}
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onSendFileAttachment(activeConversation.id, displayName, mime, sizeLabel)
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingTimerSec = 0
            recordingAmplitudes.clear()
            var ticks = 0
            while (isRecordingAudio) {
                recordingAmplitudes.add(audioRecorder.sampleNormalizedAmplitude())
                if (recordingAmplitudes.size > 36) recordingAmplitudes.removeAt(0)
                delay(250L)
                ticks++
                if (ticks % 4 == 0) recordingTimerSec++
            }
        } else {
            isRecordingLocked = false
        }
    }

    @Composable
    fun renderConversationPane(paneConversation: DirectConversation, paneModifier: Modifier, showBackNav: Boolean) {
        val activeConversation = paneConversation
        val conv = paneConversation
        val isBotDm = activeConversation.isBotChat ||
            activeConversation.id.startsWith("dm_bot_") ||
            activeConversation.friend.role == UserRole.BOT
        val isConvLocked = activeConversation.id in lockedConversationIds
        val isGateActive = isConvLocked && activeConversation.id !in sessionUnlockedDmIds
        val convEphemeralSec = ephemeralTimersByTarget[activeConversation.id] ?: 0
        Scaffold(
            modifier = paneModifier.fillMaxSize(),
            containerColor = palette.chatBg,
            topBar = {
                Column(modifier = Modifier.background(palette.chatBg)) {
                    TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !activeConversation.isGroup && !isBotDm) {
                                onOpenUserProfile(activeConversation.friend)
                            }
                        ) {
                            if (activeConversation.isGroup) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(activeConversation.groupIconColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Groupe",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = activeConversation.groupName ?: "Groupe Privé",
                                        color = DiscordTextWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${activeConversation.groupMembers.size + 1} membres",
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(activeConversation.friend.avatarColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!activeConversation.friend.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = activeConversation.friend.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = activeConversation.friend.name.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(
                                                when (activeConversation.friend.status) {
                                                    UserStatus.ONLINE -> DiscordGreen
                                                    UserStatus.IDLE -> DiscordAmber
                                                    UserStatus.DND -> DiscordRed
                                                    UserStatus.OFFLINE -> DiscordTextDark
                                                }
                                            )
                                    )
                                    AvatarDecorationOverlay(
                                        decorationKey = activeConversation.friend.avatarDecoration,
                                        size = 34.dp,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = activeConversation.friend.name,
                                            color = DiscordTextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isBotDm) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = DiscordGreen.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "BOT",
                                                    color = DiscordGreen,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (isBotDm) {
                                            "Assistant WhatsApp Raven • /menu"
                                        } else activeConversation.friend.customStatus.ifBlank {
                                            activeConversation.friend.lastActivityLabel.ifBlank { activeConversation.friend.status.label }
                                        },
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
                        if (showBackNav) {
                            IconButton(
                                onClick = onCloseDm,
                                modifier = Modifier.testTag("dm_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Retour",
                                    tint = DiscordTextWhite
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val title = if (activeConversation.isGroup) {
                                    activeConversation.groupName ?: "Groupe"
                                } else {
                                    "@${activeConversation.friend.name}"
                                }
                                onOpenSharedMedia(activeConversation.id, title)
                            },
                            modifier = Modifier.testTag("dm_shared_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = "Médias & Fichiers partagés",
                                tint = DiscordTextLight
                            )
                        }

                        val activeTheme = chatThemes[activeConversation.id]
                        IconButton(
                            onClick = { showWallpaperDialog = true },
                            modifier = Modifier.testTag("dm_wallpaper_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = "Fond d'écran",
                                tint = if (activeTheme != null) DiscordBlurple else DiscordTextLight
                            )
                        }

                        if (isBotDm) {
                            TextButton(
                                onClick = { onExecuteDmCommand(activeConversation.id, "/menu") },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("dm_bot_menu_button")
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = "Menu Bot", tint = DiscordGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Menu", color = DiscordGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (activeConversation.isGroup) {
                                        onStartGroupCall(activeConversation)
                                    } else {
                                        onStartCall(activeConversation.friend)
                                    }
                                },
                                modifier = Modifier.testTag("dm_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Appel audio",
                                    tint = DiscordGreen
                                )
                            }

                            if (!activeConversation.isGroup && onStartVideoCall != null) {
                                IconButton(
                                    onClick = { onStartVideoCall(activeConversation.friend) },
                                    modifier = Modifier.testTag("dm_video_call_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Appel vidéo",
                                        tint = DiscordBlurple
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onToggleLockConversation(activeConversation.id) },
                                modifier = Modifier.testTag("dm_lock_conversation_button")
                            ) {
                                Icon(
                                    imageVector = if (isConvLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (isConvLocked) "Déverrouiller la discussion" else "Verrouiller la discussion",
                                    tint = if (isConvLocked) DiscordAmber else DiscordTextLight
                                )
                            }

                            if (!activeConversation.isGroup) {
                                IconButton(
                                    onClick = { onBlockUser(activeConversation.friend) },
                                    modifier = Modifier.testTag("dm_block_user_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = "Bloquer cet utilisateur",
                                        tint = palette.textSecondary
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.chatBg)
                )
                    if (!isEffectivelyOnline || pendingOfflineCount > 0 || convEphemeralSec > 0) {
                        Surface(
                            color = if (!isEffectivelyOnline) DiscordAmber.copy(alpha = 0.16f) else DiscordBlurple.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (!isEffectivelyOnline) Icons.Default.WifiOff else Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = if (!isEffectivelyOnline) DiscordAmber else DiscordBlurple,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            if (!isEffectivelyOnline) append("Hors-ligne ($pendingOfflineCount en attente)")
                                            else if (pendingOfflineCount > 0) append("$pendingOfflineCount message(s) en attente")
                                            if (convEphemeralSec > 0) {
                                                if (isNotEmpty()) append(" • ")
                                                val lbl = EphemeralDurationOption.entries.find { it.seconds == convEphemeralSec }?.label ?: "${convEphemeralSec}s"
                                                append("Éphémères : $lbl")
                                            }
                                        },
                                        color = palette.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isEffectivelyOnline && pendingOfflineCount > 0) {
                                    TextButton(
                                        onClick = onFlushOfflineQueue,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Synchroniser", color = DiscordGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = palette.border, thickness = 1.dp)
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.chatBg)
                ) {
                    TypingIndicatorBar(typingUserNames = typingUsersByTarget[activeConversation.id] ?: emptyList())
                    if (isRecordingAudio) {
                        VoiceNoteRecordingBanner(
                            recordingSeconds = recordingTimerSec,
                            amplitudes = recordingAmplitudes.toList(),
                            isLocked = isRecordingLocked,
                            onToggleLock = { isRecordingLocked = !isRecordingLocked },
                            onCancel = {
                                audioRecorder.stopRecording()
                                isRecordingAudio = false
                            },
                            onSend = {
                                val path = audioRecorder.stopRecording()
                                val amps = recordingAmplitudes.toList()
                                isRecordingAudio = false
                                if (path != null) {
                                    if (onSendRichMessage != null) {
                                        onSendRichMessage(activeConversation.id, "", null, path, recordingTimerSec, amps, false)
                                    } else {
                                        onSendMessage(activeConversation.id, "", null, path, recordingTimerSec)
                                    }
                                }
                            }
                        )
                    } else {
                        AnimatedVisibility(visible = isAttachmentTrayOpen) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DiscordDarkSidebar)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    AssistChip(
                                        onClick = {
                                            isAttachmentTrayOpen = false
                                            pickNextPhotoAsViewOnce = false
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        label = { Text("Photo", color = DiscordTextWhite, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            isAttachmentTrayOpen = false
                                            pickNextPhotoAsViewOnce = true
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        label = { Text("Vue unique", color = DiscordTextWhite, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.LooksOne, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("dm_view_once_button")
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            isAttachmentTrayOpen = false
                                            showEphemeralDialogForDm = activeConversation.id
                                        },
                                        label = { Text("Éphémères", color = DiscordTextWhite, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("dm_ephemeral_button")
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
                                        modifier = Modifier.testTag("dm_file_button")
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
                                        modifier = Modifier.testTag("dm_sticker_button")
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            isAttachmentTrayOpen = false
                                            dmInputText = "/"
                                        },
                                        label = { Text("Commandes /", color = DiscordTextWhite, fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("dm_slash_trigger_button")
                                    )
                                }
                            }
                        }

                        if (dmInputText.startsWith("/") || dmInputText.startsWith("!")) {
                            SlashCommandMenu(
                                query = dmInputText,
                                extraBotCommands = botSlashCommandsForConv(conv),
                                onSelectCommand = { option ->
                                    val normalized = "/" + option.command.removePrefix("/").removePrefix("!").lowercase()
                                    if (normalized in listOf("/menu", "/aide", "/ping", "/botinfo", "/quiz", "/pileouface", "/xp", "/clear")) {
                                        val handled = onExecuteDmCommand(conv.id, option.command)
                                        if (!handled) onSendMessage(conv.id, option.command, null, null, 0)
                                        dmInputText = ""
                                    } else {
                                        dmInputText = "${option.command} "
                                    }
                                }
                            )
                        }

                        Surface(
                            color = palette.inputBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Plus tray button
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isAttachmentTrayOpen) DiscordBlurple else Color.Transparent)
                                        .clickable { isAttachmentTrayOpen = !isAttachmentTrayOpen }
                                        .testTag("dm_plus_tray_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAttachmentTrayOpen) Icons.Default.Close else Icons.Default.AddCircle,
                                        contentDescription = "Plus d'options",
                                        tint = if (isAttachmentTrayOpen) Color.White else palette.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }

                                val dispatchDmSend = {
                                    if (dmInputText.isNotBlank()) {
                                        val trimmed = dmInputText.trim()
                                        val handledCmd = if (trimmed.startsWith("/") || trimmed.startsWith("!")) {
                                            onExecuteDmCommand(conv.id, dmInputText)
                                        } else false
                                        if (!handledCmd) {
                                            onSendMessage(conv.id, dmInputText, null, null, 0)
                                        }
                                        dmInputText = ""
                                    }
                                }

                                OutlinedTextField(
                                    value = dmInputText,
                                    onValueChange = { dmInputText = it },
                                    placeholder = {
                                        val placeholderMsg = when {
                                            isBotDm -> "Écrire à ${conv.friend.name} (/menu, 1, 2...)"
                                            conv.isGroup -> "Envoyer un message à ${conv.groupName ?: "ce groupe"} (@bot)"
                                            else -> "Envoyer un message à @${conv.friend.name}"
                                        }
                                        Text(
                                            text = placeholderMsg,
                                            color = palette.textMuted,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onPreviewKeyEvent { keyEvent ->
                                            val isEnter = keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                                            if (platform.supportsEnterToSend &&
                                                isEnter &&
                                                keyEvent.type == KeyEventType.KeyDown &&
                                                !keyEvent.isShiftPressed &&
                                                !keyEvent.isCtrlPressed &&
                                                !keyEvent.isAltPressed
                                            ) {
                                                dispatchDmSend()
                                                true
                                            } else false
                                        }
                                        .testTag("dm_input_field"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textPrimary
                                    ),
                                    maxLines = 4
                                )

                                IconButton(
                                    onClick = { showStickerPicker = true },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("dm_quick_sticker_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Envoyer un Sticker",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showDmInputEmojiPicker = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentSatisfied,
                                        contentDescription = "Insérer un emoji",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                if (dmInputText.isBlank()) {
                                    IconButton(
                                        onClick = {
                                            val started = audioRecorder.startRecording()
                                            if (started) isRecordingAudio = true
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Enregistrer audio",
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = dispatchDmSend,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(DiscordBlurple)
                                            .desktopHoverPointer(platform.isPc)
                                            .testTag("dm_send_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Envoyer",
                                            tint = Color.White,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (platform.supportsEnterToSend) {
                            Text(
                                text = "Entrée pour envoyer • Maj+Entrée nouvelle ligne",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 6.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            val dmMessages = messages[activeConversation.id] ?: emptyList()
            val activeTheme = chatThemes[activeConversation.id]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Wallpaper layer
                activeTheme?.let { theme ->
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
                    // Dark scrim for readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = (0.78f - theme.opacity * 0.62f).coerceIn(0.16f, 0.78f)))
                    )
                }

                if (isGateActive) {
                    var unlockErrorMessage by remember(activeConversation.id) { mutableStateOf<String?>(null) }
                    var pinInput by remember(activeConversation.id) { mutableStateOf("") }
                    val canUseBiometric = biometricUnlockEnabled &&
                        com.example.util.BiometricAuthHelper.isBiometricAvailable(context)
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("dm_locked_gate"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, DiscordAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.widthIn(max = 360.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(44.dp))
                                Text("Discussion verrouillée", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Confirmez votre identité pour lire les messages de ${activeConversation.groupName ?: activeConversation.friend.name}.",
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                if (hasAccessCodeConfigured) {
                                    OutlinedTextField(
                                        value = pinInput,
                                        onValueChange = { next ->
                                            if (next.length <= 12 && next.all { it.isDigit() }) {
                                                pinInput = next
                                                unlockErrorMessage = null
                                            }
                                        },
                                        label = { Text("Code d'accès Raven", fontSize = 12.sp) },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = KeyboardType.NumberPassword
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("dm_unlock_pin_input")
                                    )
                                    Button(
                                        onClick = {
                                            if (onVerifyLockAccessCode(pinInput)) {
                                                unlockErrorMessage = null
                                                pinInput = ""
                                                sessionUnlockedDmIds.add(activeConversation.id)
                                            } else {
                                                unlockErrorMessage = "Code d'accès incorrect."
                                            }
                                        },
                                        enabled = pinInput.length >= 4,
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dm_unlock_pin_btn")
                                    ) {
                                        Text("Déverrouiller par code", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (canUseBiometric) {
                                    Button(
                                        onClick = {
                                            unlockErrorMessage = null
                                            com.example.util.BiometricAuthHelper.authenticate(
                                                context = context,
                                                title = "Déverrouiller la discussion",
                                                subtitle = "Accès confidentiel à ${activeConversation.groupName ?: activeConversation.friend.name}",
                                                onSuccess = {
                                                    unlockErrorMessage = null
                                                    sessionUnlockedDmIds.add(activeConversation.id)
                                                },
                                                onError = { err ->
                                                    unlockErrorMessage = err.ifBlank {
                                                        "Authentification biométrique annulée ou refusée."
                                                    }
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasAccessCodeConfigured) palette.inputBg else DiscordBlurple
                                        ),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dm_unlock_gate_btn")
                                    ) {
                                        Text(
                                            text = if (hasAccessCodeConfigured) "Utiliser la biométrie" else "Déverrouiller",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (!hasAccessCodeConfigured) {
                                    Text(
                                        text = "Activez la biométrie ou configurez un code de sécurité dans vos paramètres de compte pour déverrouiller cette discussion.",
                                        color = DiscordRed,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (!unlockErrorMessage.isNullOrBlank()) {
                                    Text(
                                        text = unlockErrorMessage.orEmpty(),
                                        color = DiscordRed,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.testTag("dm_unlock_error_text")
                                    )
                                }
                            }
                        }
                    }
                } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            if (activeConversation.isGroup) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(activeConversation.groupIconColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = "Groupe Privé",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = activeConversation.groupName ?: "Groupe Privé",
                                    color = DiscordTextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Membres : ${(activeConversation.groupMembers + currentUser).joinToString(", ") { it.name }}",
                                    color = DiscordTextMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(Color(activeConversation.friend.avatarColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!activeConversation.friend.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = activeConversation.friend.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = activeConversation.friend.name.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    AvatarDecorationOverlay(
                                        decorationKey = activeConversation.friend.avatarDecoration,
                                        size = 64.dp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = activeConversation.friend.name,
                                    color = DiscordTextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${activeConversation.friend.name}${activeConversation.friend.tag}",
                                    color = DiscordTextMuted,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "C'est le début de votre historique de messages privés avec ${activeConversation.friend.name}.",
                                    color = DiscordTextLight,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)
                        }
                    }

                    items(dmMessages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            audioPlayer = audioPlayer,
                            currentUserId = currentUser.id,
                            currentUserName = currentUser.name,
                            onLongClick = { selectedDmMessageForActions = message },
                            onToggleReaction = { emoji -> onToggleReaction(activeConversation.id, message.id, emoji) },
                            onAddReactionClick = { dmMessageForEmojiPicker = message },
                            onReplyClick = { selectedDmMessageForActions = message },
                            onBotQuickReplyClick = { payload ->
                                if (!onExecuteDmCommand(activeConversation.id, payload)) {
                                    onSendMessage(activeConversation.id, payload, null, null, 0)
                                }
                            },
                            onOpenViewOnce = { msg ->
                                if (!msg.imageUrl.isNullOrBlank()) {
                                    viewOncePreviewMsg = msg
                                }
                            }
                        )
                    }
                }
                }
            }

            if (selectedDmMessageForActions != null) {
                val targetMsg = selectedDmMessageForActions!!
                ModalBottomSheet(
                    onDismissRequest = { selectedDmMessageForActions = null },
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
                                            onToggleReaction(activeConversation.id, targetMsg.id, emoji)
                                            selectedDmMessageForActions = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 19.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DiscordBlurple.copy(alpha = 0.2f))
                                    .clickable {
                                        val target = targetMsg
                                        selectedDmMessageForActions = null
                                        dmMessageForEmojiPicker = target
                                    },
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

                        if (targetMsg.text.isNotBlank()) {
                            HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)
                            ListItem(
                                headlineContent = { Text("Copier le texte", color = DiscordTextWhite) },
                                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DiscordTextLight) },
                                modifier = Modifier.clickable {
                                    clipboardManager.setText(AnnotatedString(targetMsg.text))
                                    selectedDmMessageForActions = null
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        ListItem(
                            headlineContent = { Text("Transférer vers d'autres salons/MP", color = DiscordTextWhite) },
                            leadingContent = { Icon(Icons.Default.ForwardToInbox, contentDescription = null, tint = DiscordBlurple) },
                            modifier = Modifier.clickable {
                                onOpenForwardSheet(targetMsg)
                                selectedDmMessageForActions = null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        ListItem(
                            headlineContent = { Text("Accusés de lecture", color = DiscordTextWhite) },
                            leadingContent = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            modifier = Modifier.clickable {
                                dmMessageForReadReceipts = targetMsg
                                selectedDmMessageForActions = null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )

                        if (targetMsg.sender.id != currentUser.id && targetMsg.sender.id != "user_me") {
                            ListItem(
                                headlineContent = { Text("Offrir des Orbs", color = DiscordAmber) },
                                leadingContent = { Icon(Icons.Default.Toll, contentDescription = null, tint = DiscordAmber) },
                                modifier = Modifier.clickable {
                                    dmMessageForTipOrbs = targetMsg
                                    selectedDmMessageForActions = null
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }

                        ListItem(
                            headlineContent = { Text("Me le rappeler plus tard", color = DiscordTextWhite) },
                            leadingContent = { Icon(Icons.Default.Alarm, contentDescription = null, tint = DiscordAmber) },
                            modifier = Modifier.clickable {
                                dmMessageForReminder = targetMsg
                                selectedDmMessageForActions = null
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            if (dmMessageForEmojiPicker != null) {
                val target = dmMessageForEmojiPicker!!
                EmojiPickerModal(
                    title = "Réagir au message de ${target.sender.name}",
                    onSelectEmoji = { emoji ->
                        onToggleReaction(activeConversation.id, target.id, emoji)
                        dmMessageForEmojiPicker = null
                    },
                    onDismiss = { dmMessageForEmojiPicker = null }
                )
            }

            if (showDmInputEmojiPicker) {
                EmojiPickerModal(
                    title = "Insérer un emoji",
                    onSelectEmoji = { emoji ->
                        dmInputText += emoji
                        showDmInputEmojiPicker = false
                    },
                    onDismiss = { showDmInputEmojiPicker = false }
                )
            }

            if (showWallpaperDialog) {
                ChatBackgroundDialog(
                    currentTheme = activeTheme,
                    onApplyTheme = { theme ->
                        onApplyChatTheme(activeConversation.id, theme)
                        showWallpaperDialog = false
                    },
                    onDismiss = { showWallpaperDialog = false }
                )
            }

            if (showStickerPicker) {
                StickerPickerSheet(
                    unlockedStickerIds = unlockedStickers,
                    orbsBalance = orbsBalance,
                    onSelectSticker = { stkId -> onSendSticker(conv.id, stkId) },
                    onUnlockAndSendSticker = { stkId, price ->
                        onUnlockAndSendSticker(conv.id, stkId, price)
                    },
                    onDismiss = { showStickerPicker = false }
                )
            }

            showEphemeralDialogForDm?.let { targetDmId ->
                EphemeralTimerDialog(
                    currentDurationSec = ephemeralTimersByTarget[targetDmId] ?: 0,
                    onSelectDuration = { sec -> onSetEphemeralTimer(targetDmId, sec) },
                    onDismiss = { showEphemeralDialogForDm = null }
                )
            }

            dmMessageForReadReceipts?.let { receiptMsg ->
                val knownUsers = remember(allFriends, conv, currentUser) {
                    (allFriends + conv.groupMembers + conv.friend + currentUser).distinctBy { it.id }
                }
                MessageReadReceiptsDialog(
                    message = receiptMsg,
                    knownMembers = knownUsers,
                    onDismiss = { dmMessageForReadReceipts = null }
                )
            }

            dmMessageForTipOrbs?.let { tipMsg ->
                MessageTipOrbsDialog(
                    message = tipMsg,
                    currentOrbsBalance = orbsBalance,
                    onTipOrbs = { amount -> onTipOrbsOnMessage(tipMsg, amount) },
                    onDismiss = { dmMessageForTipOrbs = null }
                )
            }

            dmMessageForReminder?.let { remMsg ->
                MessageReminderDialog(
                    message = remMsg,
                    onScheduleReminder = { delayMs, label ->
                        onScheduleReminder(conv.id, remMsg, delayMs, label)
                    },
                    onDismiss = { dmMessageForReminder = null }
                )
            }

            viewOncePreviewMsg?.let { voMsg ->
                val imgUrl = voMsg.imageUrl
                if (!imgUrl.isNullOrBlank()) {
                    ViewOnceFullScreenDialog(
                        imageUrl = imgUrl,
                        senderName = voMsg.sender.name,
                        onDismiss = {
                            onOpenViewOnce(voMsg)
                            viewOncePreviewMsg = null
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun renderDirectoryPane(paneModifier: Modifier) {
        Scaffold(
            modifier = paneModifier.fillMaxSize(),
            containerColor = palette.sidebarBg,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Messages Privés",
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (showNavigationDrawerButton) {
                                IconButton(
                                    onClick = onToggleDrawer,
                                    modifier = Modifier.testTag("dm_toggle_drawer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Ouvrir les serveurs",
                                        tint = palette.textPrimary
                                    )
                                }
                            }
                        },
                        actions = {
                            if (onOpenCallHistory != null) {
                                IconButton(
                                    onClick = onOpenCallHistory,
                                    modifier = Modifier.testTag("dm_call_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Historique d'appels",
                                        tint = palette.textSecondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = onOpenUserSearchSheet,
                                modifier = Modifier.testTag("dm_search_users_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = "Rechercher des utilisateurs",
                                    tint = palette.textSecondary
                                )
                            }
                            IconButton(
                                onClick = onOpenFriendPrivacy,
                                modifier = Modifier.testTag("dm_friend_privacy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Confidentialité des amis",
                                    tint = palette.textSecondary
                                )
                            }
                            IconButton(
                                onClick = onOpenGlobalSearch,
                                modifier = Modifier.testTag("dm_global_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ManageSearch,
                                    contentDescription = "Recherche globale",
                                    tint = palette.textSecondary
                                )
                            }
                            IconButton(
                                onClick = onOpenInbox,
                                modifier = Modifier.testTag("dm_inbox_button")
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
                                onClick = { showCreateGroupDialog = true },
                                modifier = Modifier.testTag("dm_create_group_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "Créer un Groupe Privé",
                                    tint = DiscordBlurple
                                )
                            }
                            IconButton(
                                onClick = { showAddFriendDialog = true },
                                modifier = Modifier.testTag("dm_add_friend_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Ajouter un ami",
                                    tint = DiscordGreen
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.sidebarBg)
                    )

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher un ami ou une conversation", color = DiscordTextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .height(48.dp)
                            .testTag("dm_search_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DiscordDarkInput,
                            unfocusedContainerColor = DiscordDarkInput,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DiscordTextWhite,
                            unfocusedTextColor = DiscordTextLight
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = DiscordTextMuted, modifier = Modifier.size(18.dp))
                        }
                    )

                    // Filter chips row
                    val onlineFriendsCount = friends.count { it.user.status != UserStatus.OFFLINE }
                    val dmReadyBots = remember(bots, searchQuery) {
                        bots.filter {
                            it.isActive && it.allowDirectMessages &&
                                (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.handle.contains(searchQuery, ignoreCase = true) ||
                                    it.description.contains(searchQuery, ignoreCase = true))
                        }
                    }
                    val filters = listOf("En ligne", "Amis", "Bots", "Tous", "En attente", "Bloqués", "+ Ajouter")
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters) { filter ->
                            val isSelected = selectedFilter == filter
                            val isAdd = filter == "+ Ajouter"
                            val displayLabel = when (filter) {
                                "En ligne" -> if (onlineFriendsCount > 0) "En ligne ($onlineFriendsCount)" else "En ligne"
                                "Amis" -> if (friends.isNotEmpty()) "Amis (${friends.size})" else "Amis"
                                "Bots" -> if (dmReadyBots.isNotEmpty()) "Bots (${dmReadyBots.size})" else "Bots"
                                "En attente" -> if (friendRequests.isNotEmpty()) "En attente (${friendRequests.size})" else "En attente"
                                "Bloqués" -> if (blockedUsers.isNotEmpty()) "Bloqués (${blockedUsers.size})" else "Bloqués"
                                else -> filter
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isAdd -> DiscordBlurple
                                    isSelected -> palette.inputBg
                                    else -> Color.Transparent
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isAdd) showAddFriendDialog = true else selectedFilter = filter
                                    }
                                    .testTag("dm_filter_$filter")
                            ) {
                                Text(
                                    text = if (isAdd) "Ajouter" else displayLabel,
                                    color = when {
                                        isAdd -> Color.White
                                        isSelected -> palette.textPrimary
                                        else -> palette.textSecondary
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border, thickness = 1.dp)
                }
            }
        ) { paddingValues ->
            val filteredConversations = remember(conversations, searchQuery, selectedFilter) {
                conversations.filter { conv ->
                    val nameTarget = if (conv.isGroup) conv.groupName ?: "" else conv.friend.name
                    val matchesSearch = searchQuery.isBlank() || nameTarget.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = when (selectedFilter) {
                        "En ligne" -> conv.isGroup || conv.isBotChat || conv.friend.status != UserStatus.OFFLINE
                        "Tous" -> true
                        else -> false
                    }
                    matchesSearch && matchesFilter
                }
            }
            val filteredFriends = remember(friends, searchQuery) {
                friends.filter { rel ->
                    searchQuery.isBlank() ||
                        rel.user.name.contains(searchQuery, ignoreCase = true) ||
                        "${rel.user.name}${rel.user.tag}".contains(searchQuery, ignoreCase = true) ||
                        rel.privateNote.contains(searchQuery, ignoreCase = true)
                }
            }
            val onlineFilteredFriends = remember(filteredFriends) {
                filteredFriends.filter { it.user.status != UserStatus.OFFLINE }
            }
            val offlineFilteredFriends = remember(filteredFriends) {
                filteredFriends.filter { it.user.status == UserStatus.OFFLINE }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                when (selectedFilter) {
                    "Amis" -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AMIS EN LIGNE (${onlineFilteredFriends.size})",
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = onOpenUserSearchSheet,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("friends_open_directory_btn")
                                ) {
                                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Annuaire", color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (filteredFriends.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucun ami dans cette liste pour le moment. Utilisez + Ajouter ou l'annuaire pour trouver des membres.",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                        } else {
                            items(onlineFilteredFriends, key = { "online_${it.friendUid}" }) { rel ->
                                FriendRelationshipCard(
                                    relationship = rel,
                                    onOpenDm = { u -> onOpenFriendDm(u) },
                                    onStartCall = onStartCall,
                                    onOpenProfile = onOpenUserProfile,
                                    onRemoveFriend = onRemoveFriend,
                                    onBlockUser = onBlockUser
                                )
                            }
                            if (offlineFilteredFriends.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "HORS LIGNE & DERNIÈRE ACTIVITÉ (${offlineFilteredFriends.size})",
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(offlineFilteredFriends, key = { "offline_${it.friendUid}" }) { rel ->
                                    FriendRelationshipCard(
                                        relationship = rel,
                                        onOpenDm = { u -> onOpenFriendDm(u) },
                                        onStartCall = onStartCall,
                                        onOpenProfile = onOpenUserProfile,
                                        onRemoveFriend = onRemoveFriend,
                                        onBlockUser = onBlockUser
                                    )
                                }
                            }
                        }
                    }

                    "Bots" -> {
                        val availableBots = bots.filter {
                            it.isActive && it.allowDirectMessages &&
                                (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.handle.contains(searchQuery, ignoreCase = true) ||
                                    it.description.contains(searchQuery, ignoreCase = true))
                        }
                        item {
                            Text(
                                text = "BOTS WHATSAPP EN DM (${availableBots.size})",
                                color = DiscordGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        if (availableBots.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucun bot disponible en message privé pour le moment. Créez-en un depuis le Dashboard Bots !",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                        } else {
                            items(availableBots, key = { "dm_bot_dir_${it.id}" }) { bot ->
                                BotDmDirectoryCard(
                                    bot = bot,
                                    onOpenChat = { onOpenBotDm(bot.id) }
                                )
                            }
                        }
                    }

                    "En attente" -> {
                        val incomingReqs = friendRequests.filter { it.direction == FriendRequestDirection.RECEIVED }
                        val outgoingReqs = friendRequests.filter { it.direction == FriendRequestDirection.SENT }
                        item {
                            Text(
                                text = "DEMANDES REÇUES (${incomingReqs.size})",
                                color = DiscordTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        if (incomingReqs.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucune demande reçue.",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(incomingReqs, key = { "in_${it.id}" }) { req ->
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .clickable { onOpenUserProfile(req.user) }
                                        .testTag("friend_request_${req.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(req.user.avatarColor)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = req.user.name.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "${req.user.name}${req.user.tag}",
                                                    color = palette.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Demande reçue • ${req.timestamp}",
                                                    color = DiscordGreen,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            IconButton(
                                                onClick = { onAcceptFriendRequest(req.id) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordGreen.copy(alpha = 0.2f))
                                                    .testTag("accept_friend_req_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Accepter", tint = DiscordGreen, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    (onDeclineFriendRequest ?: onDeclineOrCancelFriendRequest)(req.id)
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordRed.copy(alpha = 0.2f))
                                                    .testTag("decline_friend_req_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Refuser", tint = DiscordRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "DEMANDES ENVOYÉES (${outgoingReqs.size})",
                                color = DiscordTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        if (outgoingReqs.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucune demande envoyée en attente.",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        } else {
                            items(outgoingReqs, key = { "out_${it.id}" }) { req ->
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .clickable { onOpenUserProfile(req.user) }
                                        .testTag("friend_request_${req.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(req.user.avatarColor)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = req.user.name.take(1).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "${req.user.name}${req.user.tag}",
                                                    color = palette.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Demande envoyée • ${req.timestamp}",
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                (onCancelFriendRequest ?: onDeclineOrCancelFriendRequest)(req.id)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("cancel_friend_req_${req.id}")
                                        ) {
                                            Text("Annuler", color = DiscordRed, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Bloqués" -> {
                        item {
                            Text(
                                text = "UTILISATEURS BLOQUÉS (${blockedUsers.size})",
                                color = DiscordTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        if (blockedUsers.isEmpty()) {
                            item {
                                Text(
                                    text = "Aucun utilisateur bloqué.",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        } else {
                            items(blockedUsers, key = { it.id }) { blocked ->
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .testTag("blocked_user_${blocked.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(blocked.avatarColor)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(blocked.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "${blocked.name}${blocked.tag}",
                                                color = palette.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        TextButton(
                                            onClick = { onUnblockUser(blocked.id) },
                                            modifier = Modifier.testTag("unblock_user_${blocked.id}")
                                        ) {
                                            Text("Débloquer", color = DiscordBlurple, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            StoriesStrip(
                                currentUser = currentUser,
                                uiState = storyUiState,
                                onCreateStoryClick = onCreateStoryClick,
                                onOpenAuthorGroup = onOpenStoryGroup
                            )
                        }

                        if (selectedFilter == "En ligne" && onlineFilteredFriends.isNotEmpty()) {
                            item {
                                Text(
                                    text = "AMIS EN LIGNE (${onlineFilteredFriends.size})",
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(onlineFilteredFriends, key = { "strip_online_${it.friendUid}" }) { rel ->
                                FriendRelationshipCard(
                                    relationship = rel,
                                    onOpenDm = { u -> onOpenFriendDm(u) },
                                    onStartCall = onStartCall,
                                    onOpenProfile = onOpenUserProfile,
                                    onRemoveFriend = onRemoveFriend,
                                    onBlockUser = onBlockUser
                                )
                            }
                        }

                        item {
                            Text(
                                text = "MESSAGES DIRECTS & GROUPES (${filteredConversations.size})",
                                color = DiscordTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        items(filteredConversations, key = { it.id }) { conv ->
                            DirectConversationRow(
                                conversation = conv,
                                isSelected = useSplitPane && conv.id == selectedDmId,
                                onClick = { onSelectDm(conv.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun renderDesktopFriendsDashboard(paneModifier: Modifier) {
        val filteredFriends = remember(friends, searchQuery) {
            friends.filter { rel ->
                searchQuery.isBlank() ||
                    rel.user.name.contains(searchQuery, ignoreCase = true) ||
                    "${rel.user.name}${rel.user.tag}".contains(searchQuery, ignoreCase = true) ||
                    rel.privateNote.contains(searchQuery, ignoreCase = true)
            }
        }
        val onlineFilteredFriends = remember(filteredFriends) {
            filteredFriends.filter { it.user.status != UserStatus.OFFLINE }
        }
        val offlineFilteredFriends = remember(filteredFriends) {
            filteredFriends.filter { it.user.status == UserStatus.OFFLINE }
        }
        val activeStories = storyUiState.allGroups
        val incomingReqs = friendRequests.filter { it.direction == FriendRequestDirection.RECEIVED }
        val outgoingReqs = friendRequests.filter { it.direction == FriendRequestDirection.SENT }

        Column(
            modifier = paneModifier
                .fillMaxSize()
                .background(palette.chatBg)
        ) {
            // Discord Desktop "Amis" Top Bar: Amis • En ligne | Tous | En attente | Bloqués | [Ajouter]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Amis",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Amis",
                                color = palette.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "  •  ",
                                color = palette.textMuted,
                                fontSize = 14.sp
                            )
                        }
                    }

                    val desktopTabs = listOf("En ligne", "Tous", "Bots", "En attente", "Bloqués")
                    val dmReadyBotsCount = bots.count { it.isActive && it.allowDirectMessages }
                    items(desktopTabs) { tab ->
                        val isSelected = selectedFilter == tab || (tab == "Tous" && selectedFilter == "Amis")
                        val countBadge = when (tab) {
                            "Bots" -> dmReadyBotsCount
                            "En attente" -> friendRequests.size
                            "Bloqués" -> blockedUsers.size
                            else -> 0
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) palette.inputBg else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .desktopHoverHighlight(
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = platform.isPc || platform.hasMouseOrTrackpad
                                )
                                .desktopHoverPointer(platform.isPc)
                                .clickable { selectedFilter = tab }
                                .testTag("dm_filter_$tab")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = tab,
                                    color = if (isSelected) palette.textPrimary else palette.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                                if (countBadge > 0) {
                                    Surface(
                                        color = DiscordRed,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = countBadge.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DiscordBlurple,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .desktopHoverPointer(platform.isPc)
                                .clickable { showAddFriendDialog = true }
                                .testTag("dm_filter_+ Ajouter")
                        ) {
                            Text(
                                text = "Ajouter",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Right Discord toolbar icons: Create Group DM, Search Directory, Privacy, Inbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier.size(34.dp).testTag("dm_create_group_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Nouveau groupe privé",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenUserSearchSheet,
                        modifier = Modifier.size(34.dp).testTag("dm_search_users_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = "Annuaire des utilisateurs",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenFriendPrivacy,
                        modifier = Modifier.size(34.dp).testTag("dm_friend_privacy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Confidentialité des amis",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenInbox,
                        modifier = Modifier.size(34.dp).testTag("dm_inbox_button")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "Boîte de réception",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                            if (unreadNotificationsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(DiscordRed)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            // Split Desktop Workspace: Center Friends Canvas + Right "En ligne" Activity Panel
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val showActivityPane = maxWidth >= 660.dp
                Row(modifier = Modifier.fillMaxSize()) {
                    // Center Friends Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val effectiveFilter = if (selectedFilter == "Amis") "Tous" else selectedFilter
                        when (effectiveFilter) {
                            "Bots" -> {
                                val availableBots = bots.filter {
                                    it.isActive && it.allowDirectMessages &&
                                        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                                            it.handle.contains(searchQuery, ignoreCase = true) ||
                                            it.description.contains(searchQuery, ignoreCase = true))
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text(
                                            text = "BOTS WHATSAPP DISPONIBLES EN DM (${availableBots.size})",
                                            color = DiscordGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    if (availableBots.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Aucun bot actif compatible DM pour le moment.",
                                                    color = palette.textMuted,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(availableBots, key = { "desk_bot_${it.id}" }) { bot ->
                                            BotDmDirectoryCard(
                                                bot = bot,
                                                onOpenChat = { onOpenBotDm(bot.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            "En ligne" -> {
                                if (onlineFilteredFriends.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp)
                                            .testTag("dm_desktop_empty_state"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Il n'y a aucun ami en ligne pour le moment. Reviens plus tard !",
                                            color = palette.textMuted,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                placeholder = { Text("Rechercher", color = palette.textMuted, fontSize = 13.sp) },
                                                singleLine = true,
                                                leadingIcon = {
                                                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = palette.sidebarBg,
                                                    unfocusedContainerColor = palette.sidebarBg,
                                                    focusedBorderColor = palette.border,
                                                    unfocusedBorderColor = palette.border,
                                                    focusedTextColor = palette.textPrimary,
                                                    unfocusedTextColor = palette.textPrimary
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 10.dp)
                                                    .testTag("dm_search_input")
                                            )
                                            Text(
                                                text = "EN LIGNE — ${onlineFilteredFriends.size}",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                            )
                                        }
                                        items(onlineFilteredFriends, key = { "desk_on_${it.friendUid}" }) { rel ->
                                            FriendRelationshipCard(
                                                relationship = rel,
                                                onOpenDm = { u -> onOpenFriendDm(u) },
                                                onStartCall = onStartCall,
                                                onOpenProfile = onOpenUserProfile,
                                                onRemoveFriend = onRemoveFriend,
                                                onBlockUser = onBlockUser
                                            )
                                        }
                                    }
                                }
                            }

                            "Tous" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Rechercher", color = palette.textMuted, fontSize = 13.sp) },
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(Icons.Default.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = palette.sidebarBg,
                                                unfocusedContainerColor = palette.sidebarBg,
                                                focusedBorderColor = palette.border,
                                                unfocusedBorderColor = palette.border,
                                                focusedTextColor = palette.textPrimary,
                                                unfocusedTextColor = palette.textPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                                .testTag("dm_search_input")
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "TOUS LES AMIS — ${filteredFriends.size}",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            TextButton(
                                                onClick = onOpenUserSearchSheet,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.testTag("friends_open_directory_btn")
                                            ) {
                                                Icon(Icons.Default.PersonSearch, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Annuaire", color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    if (filteredFriends.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 48.dp)
                                                    .testTag("dm_desktop_empty_state"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Aucun ami trouvé. Cliquez sur Ajouter ou explorez l'annuaire !",
                                                    color = palette.textMuted,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(onlineFilteredFriends + offlineFilteredFriends, key = { "desk_all_${it.friendUid}" }) { rel ->
                                            FriendRelationshipCard(
                                                relationship = rel,
                                                onOpenDm = { u -> onOpenFriendDm(u) },
                                                onStartCall = onStartCall,
                                                onOpenProfile = onOpenUserProfile,
                                                onRemoveFriend = onRemoveFriend,
                                                onBlockUser = onBlockUser
                                            )
                                        }
                                    }
                                }
                            }

                            "En attente" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        Text(
                                            text = "EN ATTENTE — ${friendRequests.size}",
                                            color = palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    if (friendRequests.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Il n'y a aucune demande d'ami en attente.",
                                                    color = palette.textMuted,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(incomingReqs, key = { "desk_in_${it.id}" }) { req ->
                                            Surface(
                                                color = palette.cardBg,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, palette.border),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenUserProfile(req.user) }
                                                    .testTag("friend_request_${req.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                        Box(
                                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(req.user.avatarColor)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(req.user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text("${req.user.name}${req.user.tag}", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                            Text("Demande d'ami reçue • ${req.timestamp}", color = DiscordGreen, fontSize = 11.sp)
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        IconButton(
                                                            onClick = { onAcceptFriendRequest(req.id) },
                                                            modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.inputBg).testTag("accept_friend_req_${req.id}")
                                                        ) {
                                                            Icon(Icons.Default.Check, contentDescription = "Accepter", tint = DiscordGreen, modifier = Modifier.size(18.dp))
                                                        }
                                                        IconButton(
                                                            onClick = { (onDeclineFriendRequest ?: onDeclineOrCancelFriendRequest)(req.id) },
                                                            modifier = Modifier.size(34.dp).clip(CircleShape).background(palette.inputBg).testTag("decline_friend_req_${req.id}")
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Refuser", tint = DiscordRed, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        items(outgoingReqs, key = { "desk_out_${it.id}" }) { req ->
                                            Surface(
                                                color = palette.cardBg,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, palette.border),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenUserProfile(req.user) }
                                                    .testTag("friend_request_${req.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                        Box(
                                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(req.user.avatarColor)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(req.user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text("${req.user.name}${req.user.tag}", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                            Text("Demande d'ami envoyée • ${req.timestamp}", color = palette.textMuted, fontSize = 11.sp)
                                                        }
                                                    }
                                                    OutlinedButton(
                                                        onClick = { (onCancelFriendRequest ?: onDeclineOrCancelFriendRequest)(req.id) },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.testTag("cancel_friend_req_${req.id}")
                                                    ) {
                                                        Text("Annuler", color = DiscordRed, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "Bloqués" -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item {
                                        Text(
                                            text = "BLOQUÉS — ${blockedUsers.size}",
                                            color = palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    if (blockedUsers.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Vous n'avez bloqué aucun utilisateur.",
                                                    color = palette.textMuted,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(blockedUsers, key = { "desk_blk_${it.id}" }) { blocked ->
                                            Surface(
                                                color = palette.cardBg,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, palette.border),
                                                modifier = Modifier.fillMaxWidth().testTag("blocked_user_${blocked.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(blocked.avatarColor)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(blocked.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text("${blocked.name}${blocked.tag}", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    TextButton(
                                                        onClick = { onUnblockUser(blocked.id) },
                                                        modifier = Modifier.testTag("unblock_user_${blocked.id}")
                                                    ) {
                                                        Text("Débloquer", color = DiscordBlurple, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showActivityPane) {
                        VerticalDivider(color = palette.border, thickness = 1.dp)

                        // Right Discord "En ligne" (Active Now) Panel
                        Column(
                            modifier = Modifier
                                .width(308.dp)
                                .fillMaxHeight()
                                .background(palette.chatBg)
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "En ligne",
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            val activeFriendsWithStatus = onlineFilteredFriends.filter {
                                it.user.customStatus.isNotBlank() || it.user.lastActivityLabel.isNotBlank()
                            }

                            if (activeStories.isNotEmpty()) {
                                StoriesStrip(
                                    currentUser = currentUser,
                                    uiState = storyUiState,
                                    onCreateStoryClick = onCreateStoryClick,
                                    onOpenAuthorGroup = onOpenStoryGroup
                                )
                            }

                            if (activeFriendsWithStatus.isNotEmpty()) {
                                activeFriendsWithStatus.take(4).forEach { rel ->
                                    Surface(
                                        color = palette.cardBg,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenFriendDm(rel.user) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(rel.user.avatarColor)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(rel.user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(rel.user.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Text(
                                                    text = rel.user.customStatus.ifBlank { rel.user.lastActivityLabel },
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Tout est calme... pour le moment.",
                                            color = palette.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Quand un ami commencera une activité, comme jouer à un jeu ou passer du temps sur le chat vocal, ce sera affiché ici !",
                                            color = palette.textMuted,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isDesktopSidebarDocked) {
        if (activeConversation != null) {
            renderConversationPane(activeConversation, modifier, showBackNav = false)
        } else {
            renderDesktopFriendsDashboard(modifier)
        }
    } else if (useSplitPane) {
        Row(modifier = modifier.fillMaxSize()) {
            renderDirectoryPane(Modifier.width(320.dp))
            VerticalDivider(color = palette.border, thickness = 1.dp)
            if (activeConversation != null) {
                renderConversationPane(activeConversation, Modifier.weight(1f), showBackNav = false)
            } else {
                renderDesktopFriendsDashboard(Modifier.weight(1f))
            }
        }
    } else {
        if (activeConversation != null) {
            renderConversationPane(activeConversation, modifier, showBackNav = true)
        } else {
            renderDirectoryPane(modifier)
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDmDialog(
            friends = allFriends,
            onCreateGroup = { groupName, chosenMembers ->
                onCreateGroup(groupName, chosenMembers)
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    // Add friend dialog
    if (showAddFriendDialog) {
        var friendTagInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddFriendDialog = false },
            containerColor = DiscordDarkCard,
            title = {
                Text("Ajouter un ami", color = DiscordTextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Vous pouvez ajouter des amis avec leur nom d'utilisateur et tag (ex. Shadow#1337).",
                        color = DiscordTextMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = friendTagInput,
                        onValueChange = {
                            friendTagInput = it
                            errorMsg = null
                        },
                        placeholder = { Text("NomUtilisateur#0000", color = DiscordTextDark) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DiscordDarkInput,
                            unfocusedContainerColor = DiscordDarkInput,
                            focusedBorderColor = DiscordBlurple,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DiscordTextWhite,
                            unfocusedTextColor = DiscordTextLight
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_friend_input")
                    )
                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = errorMsg!!, color = DiscordRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (friendTagInput.isNotBlank()) {
                            val success = onAddFriend(friendTagInput.trim())
                            if (success) {
                                showAddFriendDialog = false
                            } else {
                                errorMsg = "Impossible d'ajouter cet ami."
                            }
                        } else {
                            errorMsg = "Veuillez entrer un tag valide."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier.testTag("add_friend_submit")
                ) {
                    Text("Envoyer la demande", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Annuler", color = DiscordTextMuted)
                }
            }
        )
    }
}

@Composable
fun DirectConversationRow(
    conversation: DirectConversation,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val platform = LocalRavenPlatform.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DiscordBlurple.copy(alpha = 0.22f) else Color.Transparent)
            .desktopHoverHighlight(
                shape = RoundedCornerShape(10.dp),
                enabled = platform.isPc || platform.hasMouseOrTrackpad
            )
            .desktopHoverPointer(platform.isPc)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("dm_row_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + Status dot (or Group badge)
        if (conversation.isGroup) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(conversation.groupIconColor)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Groupe",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Box {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(conversation.friend.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!conversation.friend.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = conversation.friend.avatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = conversation.friend.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(
                            when (conversation.friend.status) {
                                UserStatus.ONLINE -> DiscordGreen
                                UserStatus.IDLE -> DiscordAmber
                                UserStatus.DND -> DiscordRed
                                UserStatus.OFFLINE -> DiscordTextDark
                            }
                        )
                )
                AvatarDecorationOverlay(
                    decorationKey = conversation.friend.avatarDecoration,
                    size = 44.dp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (conversation.isGroup) {
                            "${conversation.groupName ?: "Groupe"} (${conversation.groupMembers.size + 1})"
                        } else conversation.friend.name,
                        color = DiscordTextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (conversation.isBotChat || conversation.friend.role == UserRole.BOT) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = DiscordGreen.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "BOT",
                                color = DiscordGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = conversation.lastMessageTime,
                    color = DiscordTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage,
                    color = if (conversation.unreadCount > 0) DiscordTextWhite else DiscordTextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(DiscordRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BotDmDirectoryCard(
    bot: RavenBot,
    onOpenChat: () -> Unit
) {
    val palette = ravenPalette()
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onOpenChat)
            .testTag("dm_bot_card_${bot.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(bot.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!bot.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = bot.avatarUrl,
                            contentDescription = "Avatar ${bot.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bot.name,
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = DiscordGreen.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "BOT",
                                color = DiscordGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = "@${bot.handle} • ${bot.commands.size} commandes (/menu, /calc...)",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bot.description,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onOpenChat,
                colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("dm_open_bot_chat_${bot.id}")
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Discuter", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
