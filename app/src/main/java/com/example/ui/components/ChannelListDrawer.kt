package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun ChannelListDrawer(
    server: Server,
    selectedChannelId: String,
    currentUser: User,
    isUserMuted: Boolean,
    isUserDeafened: Boolean,
    onSelectChannel: (Channel) -> Unit,
    onCreateChannelClick: (category: String) -> Unit,
    onUserClick: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = DiscordDarkSidebar
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Server Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(DiscordDarkSidebar)
                    .clickable { isMenuOpen = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = server.name,
                        color = DiscordTextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Options du serveur",
                            tint = DiscordTextWhite
                        )
                    }
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false },
                    modifier = Modifier.background(DiscordDarkCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Inviter des personnes", color = DiscordBlurple) },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DiscordBlurple) },
                        onClick = { isMenuOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Créer un salon", color = DiscordTextWhite) },
                        leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null, tint = DiscordTextWhite) },
                        onClick = {
                            isMenuOpen = false
                            onCreateChannelClick("SALONS TEXTUELS")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paramètres du serveur", color = DiscordTextWhite) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = DiscordTextLight) },
                        onClick = { isMenuOpen = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Paramètres des notifications", color = DiscordTextWhite) },
                        leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = DiscordTextLight) },
                        onClick = { isMenuOpen = false }
                    )
                }
            }

            HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)

            // Channels List
            val categories = server.channels.groupBy { it.category }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
            ) {
                categories.forEach { (categoryName, channels) ->
                    item(key = "cat_$categoryName") {
                        ChannelCategoryHeader(
                            name = categoryName,
                            onAddChannel = { onCreateChannelClick(categoryName) }
                        )
                    }

                    items(channels, key = { it.id }) { channel ->
                        val isSelected = channel.id == selectedChannelId
                        ChannelRow(
                            channel = channel,
                            isSelected = isSelected,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }
            }

            // Bottom User Profile Dock
            UserBottomBar(
                user = currentUser,
                isMuted = isUserMuted,
                isDeafened = isUserDeafened,
                onUserClick = onUserClick,
                onToggleMute = onToggleMute,
                onToggleDeafen = onToggleDeafen
            )
        }
    }
}

@Composable
fun ChannelCategoryHeader(
    name: String,
    onAddChannel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp, start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DiscordTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name.uppercase(),
                color = DiscordTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        IconButton(
            onClick = onAddChannel,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Ajouter un salon",
                tint = DiscordTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isVoice = channel.type == ChannelType.VOICE
    val isAnnouncement = channel.type == ChannelType.ANNOUNCEMENT

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) DiscordDarkInput else Color.Transparent)
                .clickable(onClick = onClick)
                .testTag("channel_${channel.id}")
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    channel.isPrivate -> Icons.Default.Lock
                    isVoice -> Icons.Default.VolumeUp
                    isAnnouncement -> Icons.Default.Campaign
                    else -> Icons.Default.Tag
                },
                contentDescription = null,
                tint = if (isSelected) DiscordTextWhite else DiscordTextMuted,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = channel.name,
                color = if (isSelected) DiscordTextWhite else DiscordTextLight,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (channel.unreadCount > 0 && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        // If voice channel has active participants, display them indented underneath
        if (isVoice && channel.activeVoiceUsers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
            ) {
                channel.activeVoiceUsers.forEach { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(user.avatarColor))
                                .then(
                                    if (user.isSpeaking) Modifier.background(DiscordGreen) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.name.take(1),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = user.name,
                            color = if (user.isSpeaking) DiscordGreen else DiscordTextLight,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (user.isMuted) {
                            Icon(
                                imageVector = Icons.Default.MicOff,
                                contentDescription = "Muet",
                                tint = DiscordRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserBottomBar(
    user: User,
    isMuted: Boolean,
    isDeafened: Boolean,
    onUserClick: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        color = DiscordDarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar + Info
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onUserClick)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(user.avatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!user.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = user.name.take(1),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Online indicator
                    val statusColor = when (user.status) {
                        UserStatus.ONLINE -> DiscordGreen
                        UserStatus.IDLE -> DiscordAmber
                        UserStatus.DND -> DiscordRed
                        UserStatus.OFFLINE -> DiscordTextDark
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = user.name,
                        color = DiscordTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = user.tag,
                        color = DiscordTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Quick Voice & Audio controls
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(32.dp).testTag("bottom_mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Réactiver micro" else "Couper micro",
                    tint = if (isMuted) DiscordRed else DiscordTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleDeafen,
                modifier = Modifier.size(32.dp).testTag("bottom_deafen_button")
            ) {
                Icon(
                    imageVector = if (isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                    contentDescription = if (isDeafened) "Réactiver casque" else "Couper son",
                    tint = if (isDeafened) DiscordRed else DiscordTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onUserClick,
                modifier = Modifier.size(32.dp).testTag("bottom_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Paramètres utilisateur",
                    tint = DiscordTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
