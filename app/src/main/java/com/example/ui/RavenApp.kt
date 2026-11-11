package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ChatRepository
import com.example.model.Channel
import com.example.model.ChannelType
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun RavenApp(repository: ChatRepository) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentUser by repository.currentUser.collectAsState()
    val servers by repository.servers.collectAsState()
    val selectedServerId by repository.selectedServerId.collectAsState()
    val selectedChannelId by repository.selectedChannelId.collectAsState()
    val messages by repository.messages.collectAsState()
    val directConversations by repository.directConversations.collectAsState()
    val selectedDmId by repository.selectedDmId.collectAsState()
    val connectedVoiceChannel by repository.connectedVoiceChannel.collectAsState()
    val isUserMuted by repository.isUserMuted.collectAsState()
    val isUserDeafened by repository.isUserDeafened.collectAsState()
    val replyingTo by repository.replyingToMessage.collectAsState()
    val searchQuery by repository.searchQuery.collectAsState()
    val isPinnedFilter by repository.pinnedMessagesOnly.collectAsState()
    val chatBackgrounds by repository.chatBackgrounds.collectAsState()

    // Navigation and drawer states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var isMemberListOpen by remember { mutableStateOf(false) }
    var isViewingFullVoiceRoom by remember { mutableStateOf(false) }
    var selectedUserProfile by remember { mutableStateOf<com.example.model.User?>(null) }
    var showCreateServerDialog by remember { mutableStateOf(false) }
    var showCreateChannelCategory by remember { mutableStateOf<String?>(null) }
    var showOrbsShopDialog by remember { mutableStateOf(false) }

    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission handled
    }

    val requestAudioPermission = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Active Server
    val activeServer = servers.find { it.id == selectedServerId } ?: servers.first()
    // Active Channel
    val activeChannel = activeServer.channels.find { it.id == selectedChannelId }
        ?: activeServer.channels.firstOrNull { it.type == ChannelType.TEXT }
        ?: activeServer.channels.firstOrNull()
        ?: Channel("c_def", activeServer.id, "général", ChannelType.TEXT)

    // Handle back button hierarchically
    BackHandler(enabled = isMemberListOpen || isViewingFullVoiceRoom || drawerState.isOpen || (activeServer.isDmTab && selectedDmId != null)) {
        when {
            isMemberListOpen -> isMemberListOpen = false
            isViewingFullVoiceRoom -> isViewingFullVoiceRoom = false
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            activeServer.isDmTab && selectedDmId != null -> repository.selectDm("")
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isViewingFullVoiceRoom,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DiscordDarkPrimary,
                drawerContentColor = DiscordTextLight,
                modifier = Modifier.width(340.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Server Rail
                    ServerRail(
                        servers = servers,
                        selectedServerId = selectedServerId,
                        onSelectServer = { serverId ->
                            repository.selectServer(serverId)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onCreateServerClick = { showCreateServerDialog = true },
                        onOpenOrbsShop = {
                            coroutineScope.launch { drawerState.close() }
                            showOrbsShopDialog = true
                        }
                    )

                    // Right Channels list for the selected server
                    if (!activeServer.isDmTab) {
                        ChannelListDrawer(
                            server = activeServer,
                            selectedChannelId = selectedChannelId,
                            currentUser = currentUser,
                            isUserMuted = isUserMuted,
                            isUserDeafened = isUserDeafened,
                            onSelectChannel = { ch ->
                                repository.selectChannel(ch)
                                if (ch.type == ChannelType.VOICE) {
                                    requestAudioPermission()
                                    isViewingFullVoiceRoom = true
                                }
                                coroutineScope.launch { drawerState.close() }
                            },
                            onCreateChannelClick = { category ->
                                showCreateChannelCategory = category
                            },
                            onUserClick = { selectedUserProfile = currentUser },
                            onToggleMute = {
                                requestAudioPermission()
                                repository.toggleMute()
                            },
                            onToggleDeafen = { repository.toggleDeafen() },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Quick switch when in DM tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(DiscordDarkSidebar)
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Messages Privés",
                                    color = DiscordTextWhite,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Glissez ou cliquez sur un contact pour continuer votre discussion.",
                                    color = DiscordTextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content pane
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        isViewingFullVoiceRoom && connectedVoiceChannel != null -> {
                            VoiceRoomView(
                                channel = connectedVoiceChannel!!,
                                serverName = activeServer.name,
                                currentUser = currentUser,
                                isUserMuted = isUserMuted,
                                isUserDeafened = isUserDeafened,
                                onToggleMute = {
                                    requestAudioPermission()
                                    repository.toggleMute()
                                },
                                onToggleDeafen = { repository.toggleDeafen() },
                                onLeaveVoice = {
                                    repository.leaveVoiceChannel()
                                    isViewingFullVoiceRoom = false
                                },
                                onMinimize = { isViewingFullVoiceRoom = false }
                            )
                        }

                        activeServer.isDmTab -> {
                            DirectMessagesView(
                                conversations = directConversations,
                                allFriends = com.example.data.MockData.allFriends,
                                selectedDmId = selectedDmId,
                                messages = messages,
                                currentUser = currentUser,
                                onSelectDm = { repository.selectDm(it) },
                                onCloseDm = { repository.selectDm("") },
                                onSendMessage = { dmId, text, imgUrl, audUrl, audDur ->
                                    repository.sendMessage(dmId, text, imgUrl, audUrl, audDur)
                                },
                                onToggleReaction = { dmId, msgId, emoji ->
                                    repository.toggleReaction(dmId, msgId, emoji)
                                },
                                onAddFriend = { tag -> repository.addFriend(tag) },
                                onStartCall = { friend ->
                                    requestAudioPermission()
                                    val dmVoice = Channel(
                                        id = "voice_${friend.id}",
                                        serverId = "dm_tab",
                                        name = "Appel avec ${friend.name}",
                                        type = ChannelType.VOICE,
                                        activeVoiceUsers = listOf(friend)
                                    )
                                    repository.joinVoiceChannel(dmVoice)
                                    isViewingFullVoiceRoom = true
                                },
                                onToggleDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                chatThemes = chatBackgrounds,
                                onApplyChatTheme = { dmId, theme ->
                                    repository.setChatBackground(dmId, theme)
                                }
                            )
                        }

                        else -> {
                            ChatView(
                                channel = activeChannel,
                                messages = messages[activeChannel.id] ?: emptyList(),
                                replyingTo = replyingTo,
                                searchQuery = searchQuery,
                                isPinnedFilter = isPinnedFilter,
                                members = activeServer.members,
                                onSendMessage = { text, imgUrl, audUrl, audDur ->
                                    repository.sendMessage(activeChannel.id, text, imgUrl, audUrl, audDur)
                                },
                                onToggleReaction = { msgId, emoji ->
                                    repository.toggleReaction(activeChannel.id, msgId, emoji)
                                },
                                onReplyClick = { repository.setReplyingTo(it) },
                                onCancelReply = { repository.setReplyingTo(null) },
                                onPinMessage = { repository.togglePinMessage(activeChannel.id, it) },
                                onDeleteMessage = { repository.deleteMessage(activeChannel.id, it) },
                                onTogglePinnedFilter = { repository.togglePinnedFilter() },
                                onSearchQueryChange = { repository.setSearchQuery(it) },
                                onToggleDrawer = {
                                    coroutineScope.launch { drawerState.open() }
                                },
                                onToggleMemberList = { isMemberListOpen = !isMemberListOpen },
                                chatTheme = chatBackgrounds[activeChannel.id],
                                onApplyChatTheme = { theme ->
                                    repository.setChatBackground(activeChannel.id, theme)
                                },
                                onCreatePoll = { question, options, allowsMultiple ->
                                    repository.createPoll(activeChannel.id, question, options, allowsMultiple)
                                },
                                onVotePoll = { messageId, optionId ->
                                    repository.votePoll(activeChannel.id, messageId, optionId)
                                },
                                currentUserId = currentUser.id
                            )
                        }
                    }
                }

                // Persistent Floating Voice Bar if connected to voice and not on full voice screen
                if (connectedVoiceChannel != null && !isViewingFullVoiceRoom) {
                    FloatingVoiceBar(
                        channel = connectedVoiceChannel!!,
                        isMuted = isUserMuted,
                        onBarClick = { isViewingFullVoiceRoom = true },
                        onToggleMute = {
                            requestAudioPermission()
                            repository.toggleMute()
                        },
                        onDisconnect = { repository.leaveVoiceChannel() }
                    )
                }
            }

            // Member list slide-out overlay
            AnimatedVisibility(
                visible = isMemberListOpen && !activeServer.isDmTab && !isViewingFullVoiceRoom,
                enter = slideInHorizontally(initialOffsetX = { it }),
                exit = slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    MemberListDrawer(
                        members = activeServer.members,
                        onClose = { isMemberListOpen = false },
                        onMemberClick = { member ->
                            isMemberListOpen = false
                            selectedUserProfile = member
                        }
                    )
                }
            }
        }
    }

    // Create Server Modal
    if (showCreateServerDialog) {
        CreateServerDialog(
            onDismiss = { showCreateServerDialog = false },
            onCreateServer = { name, emoji ->
                repository.createServer(name, emoji)
            }
        )
    }

    // Create Channel Modal
    if (showCreateChannelCategory != null) {
        CreateChannelDialog(
            category = showCreateChannelCategory!!,
            onDismiss = { showCreateChannelCategory = null },
            onCreateChannel = { name, type, isPrivate ->
                repository.createChannel(activeServer.id, name, type, showCreateChannelCategory!!, isPrivate)
            }
        )
    }

    // User Profile Bottom Sheet
    selectedUserProfile?.let { targetUser ->
        val isSelf = targetUser.id == currentUser.id || targetUser.id == "user_me"
        UserProfileDialog(
            user = if (isSelf) currentUser else targetUser,
            isCurrentUser = isSelf,
            onDismiss = { selectedUserProfile = null },
            onUpdateStatus = { status, customStatus ->
                repository.updateUserStatus(status, customStatus)
            },
            onSaveFullProfile = { name, pronouns, bio, avUrl, avCol, banUrl, banCol, statusMsg, emoji, activity, email, phone ->
                repository.updateUserProfile(
                    name = name,
                    pronouns = pronouns,
                    bio = bio,
                    avatarUrl = avUrl,
                    avatarColor = avCol,
                    bannerUrl = banUrl,
                    bannerColor = banCol,
                    customStatus = statusMsg,
                    customStatusEmoji = emoji,
                    activity = activity,
                    email = email,
                    phone = phone
                )
            },
            onStartDirectMessage = { friend ->
                selectedUserProfile = null
                repository.selectServer("dm_tab")
                val existingDm = directConversations.find { it.friend.id == friend.id }
                if (existingDm != null) {
                    repository.selectDm(existingDm.id)
                } else {
                    repository.addFriend("${friend.name}${friend.tag}")
                }
            },
            onToggleBadge = { badgeId ->
                repository.toggleUserBadge(badgeId)
            },
            onOpenOrbsShop = {
                showOrbsShopDialog = true
            }
        )
    }

    // Discord Orbs Shop and Quests Dialog
    if (showOrbsShopDialog) {
        OrbsShopDialog(
            user = currentUser,
            onDismiss = { showOrbsShopDialog = false },
            onUpdateOrbsBalance = { newBalance ->
                repository.updateOrbsBalance(newBalance)
            },
            onEquipDecoration = { decoKey ->
                repository.equipAvatarDecoration(decoKey)
            },
            onUnlockBadge = { badgeId ->
                repository.toggleUserBadge(badgeId)
            }
        )
    }
}
