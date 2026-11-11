package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
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
    onToggleDrawer: () -> Unit,
    chatThemes: Map<String, ChatBackgroundTheme> = emptyMap(),
    onApplyChatTheme: (dmId: String, ChatBackgroundTheme) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedFilter by remember { mutableStateOf("En ligne") }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var dmInputText by remember { mutableStateOf("") }

    val audioRecorder = remember { AudioRecorderHelper(context) }
    val audioPlayer = remember { AudioPlayerHelper() }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingTimerSec by remember { mutableStateOf(0) }

    val activeConversation = conversations.find { it.id == selectedDmId }

    // Cloudinary / gallery photo picker for DMs
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activeConversation != null) {
            coroutineScope.launch {
                val uploadedUrl = CloudinaryHelper.uploadImage(context, uri)
                onSendMessage(activeConversation.id, "", uploadedUrl, null, 0)
            }
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingTimerSec = 0
            while (isRecordingAudio) {
                delay(1000)
                recordingTimerSec++
            }
        }
    }

    if (activeConversation != null) {
        // Active 1-on-1 DM Chat View
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = DiscordDarkChat,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    size = 34.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeConversation.friend.name,
                                    color = DiscordTextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeConversation.friend.customStatus.ifBlank { activeConversation.friend.status.label },
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
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
                    },
                    actions = {
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

                        IconButton(
                            onClick = { onStartCall(activeConversation.friend) },
                            modifier = Modifier.testTag("dm_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Appeler",
                                tint = DiscordTextLight
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DiscordDarkSidebar)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkChat)
                ) {
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
                                        fontSize = 13.sp
                                    )
                                }
                                Row {
                                    TextButton(onClick = {
                                        audioRecorder.stopRecording()
                                        isRecordingAudio = false
                                    }) {
                                        Text("Annuler", color = DiscordTextMuted)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            val path = audioRecorder.stopRecording()
                                            isRecordingAudio = false
                                            if (path != null) {
                                                onSendMessage(activeConversation.id, "", null, path, recordingTimerSec)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                                    ) {
                                        Text("Envoyer", color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Photo picker button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(DiscordDarkInput)
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Partager une photo",
                                    tint = DiscordTextLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedTextField(
                                value = dmInputText,
                                onValueChange = { dmInputText = it },
                                placeholder = {
                                    Text(
                                        text = "Envoyer un message à @${activeConversation.friend.name}",
                                        color = DiscordTextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dm_input_field"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DiscordDarkInput,
                                    unfocusedContainerColor = DiscordDarkInput,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = DiscordTextWhite,
                                    unfocusedTextColor = DiscordTextLight
                                ),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (dmInputText.isBlank()) {
                                IconButton(
                                    onClick = {
                                        val started = audioRecorder.startRecording()
                                        if (started) isRecordingAudio = true
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DiscordDarkInput)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Enregistrer audio",
                                        tint = DiscordTextLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        onSendMessage(activeConversation.id, dmInputText, null, null, 0)
                                        dmInputText = ""
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(DiscordBlurple)
                                        .testTag("dm_send_button")
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
                            .background(Color.Black.copy(alpha = 1f - (theme.opacity * 0.45f)))
                    )
                }

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
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)
                        }
                    }

                    items(dmMessages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            audioPlayer = audioPlayer,
                            currentUserId = currentUser.id,
                            onLongClick = { },
                            onToggleReaction = { emoji -> onToggleReaction(activeConversation.id, message.id, emoji) },
                            onReplyClick = { }
                        )
                    }
                }
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
        }
    } else {
        // DM List & Friends Dashboard
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = DiscordDarkSidebar,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Messages Privés",
                                color = DiscordTextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onToggleDrawer,
                                modifier = Modifier.testTag("dm_toggle_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Ouvrir les serveurs",
                                    tint = DiscordTextWhite
                                )
                            }
                        },
                        actions = {
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = DiscordDarkSidebar)
                    )

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher ou démarrer une conversation", color = DiscordTextMuted, fontSize = 13.sp) },
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
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("En ligne", "Tous", "En attente", "+ Ajouter")) { filter ->
                            val isSelected = selectedFilter == filter
                            val isAdd = filter == "+ Ajouter"
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when {
                                    isAdd -> DiscordGreen.copy(alpha = 0.2f)
                                    isSelected -> DiscordBlurple
                                    else -> DiscordDarkInput
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isAdd) showAddFriendDialog = true else selectedFilter = filter
                                    }
                            ) {
                                Text(
                                    text = filter,
                                    color = when {
                                        isAdd -> DiscordGreen
                                        isSelected -> Color.White
                                        else -> DiscordTextLight
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)
                }
            }
        ) { paddingValues ->
            val filteredConversations = remember(conversations, searchQuery, selectedFilter) {
                conversations.filter { conv ->
                    val matchesSearch = searchQuery.isBlank() || conv.friend.name.contains(searchQuery, ignoreCase = true)
                    val matchesFilter = when (selectedFilter) {
                        "En ligne" -> conv.friend.status != UserStatus.OFFLINE
                        "Tous" -> true
                        "En attente" -> false
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = "MESSAGES DIRECTS (${filteredConversations.size})",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                items(filteredConversations, key = { it.id }) { conv ->
                    DirectConversationRow(
                        conversation = conv,
                        onClick = { onSelectDm(conv.id) }
                    )
                }
            }
        }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("dm_row_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + Status dot
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

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.friend.name,
                    color = DiscordTextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
