package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Channel
import com.example.model.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomView(
    channel: Channel,
    serverName: String,
    currentUser: User,
    isUserMuted: Boolean,
    isUserDeafened: Boolean,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeaveVoice: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DiscordDarkCard,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = DiscordGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = channel.name,
                                color = DiscordTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$serverName • RTC Connecté (24ms)",
                            color = DiscordGreen,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.testTag("voice_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Réduire",
                            tint = DiscordTextWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Invite to voice */ }) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Inviter",
                            tint = DiscordTextLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiscordDarkSidebar
                )
            )
        },
        bottomBar = {
            // Voice Control Dock
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = DiscordDarkSidebar,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Button
                    VoiceControlButton(
                        icon = if (isUserMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isUserMuted) "Muet" else "Micro",
                        isActive = isUserMuted,
                        activeColor = DiscordRed,
                        onClick = onToggleMute,
                        testTag = "voice_control_mute"
                    )

                    // Deafen Button
                    VoiceControlButton(
                        icon = if (isUserDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                        label = if (isUserDeafened) "Sourdine" else "Casque",
                        isActive = isUserDeafened,
                        activeColor = DiscordRed,
                        onClick = onToggleDeafen,
                        testTag = "voice_control_deafen"
                    )

                    // Disconnect Button
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(DiscordRed)
                            .clickable(onClick = onLeaveVoice)
                            .testTag("voice_control_disconnect"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Déconnexion",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Participants dans le salon (${channel.activeVoiceUsers.size})",
                color = DiscordTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(channel.activeVoiceUsers, key = { it.id }) { user ->
                    val isMe = user.id == currentUser.id
                    val isMuted = if (isMe) isUserMuted else user.isMuted
                    val isDeafened = if (isMe) isUserDeafened else user.isDeafened
                    val isSpeaking = if (isMe) (!isMuted) else user.isSpeaking

                    VoiceParticipantCard(
                        user = user,
                        isMe = isMe,
                        isSpeaking = isSpeaking,
                        isMuted = isMuted,
                        isDeafened = isDeafened
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceParticipantCard(
    user: User,
    isMe: Boolean,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isDeafened: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorder"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        color = DiscordDarkSidebar,
        shape = RoundedCornerShape(12.dp),
        border = if (isSpeaking && !isMuted) {
            androidx.compose.foundation.BorderStroke(pulseBorder.dp, DiscordGreen)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(user.avatarColor))
                        .then(
                            if (isSpeaking && !isMuted) {
                                Modifier.border(3.dp, DiscordGreen, CircleShape)
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isMe) "${user.name} (Vous)" else user.name,
                        color = if (isSpeaking && !isMuted) DiscordGreen else DiscordTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Status icons in top corners
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isMuted) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DiscordDarkCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = "Muet",
                            tint = DiscordRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (isDeafened) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DiscordDarkCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetOff,
                            contentDescription = "Sourdine",
                            tint = DiscordRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Speaking indicator wave
            if (isSpeaking && !isMuted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DiscordGreen.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "En train de parler...",
                        color = DiscordGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else DiscordDarkInput),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else DiscordTextLight,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else DiscordTextMuted,
            fontSize = 11.sp
        )
    }
}
