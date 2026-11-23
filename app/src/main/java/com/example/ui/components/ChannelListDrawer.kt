package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.LocalRavenPlatform
import com.example.util.desktopHoverHighlight
import com.example.util.desktopHoverPointer

@Composable
fun ChannelListDrawer(
    server: Server,
    selectedChannelId: String,
    currentUser: User,
    isUserMuted: Boolean,
    isUserDeafened: Boolean,
    onSelectChannel: (Channel) -> Unit,
    onCreateChannelClick: (category: String) -> Unit,
    onInvitePeople: () -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenServerShop: () -> Unit = {},
    onOpenServerSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenCommunityHome: () -> Unit = {},
    onOpenCommunityInvites: () -> Unit = {},
    onOpenRolesMatrix: () -> Unit = {},
    onOpenCategoriesManager: () -> Unit = {},
    onOpenBotsDashboard: () -> Unit = {},
    onOpenServerBackground: () -> Unit = {},
    onOpenPollsHub: () -> Unit = {},
    onOpenPluginsManager: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit = {},
    onToggleServerClosed: (Boolean) -> Unit = {},
    onEditCommunity: () -> Unit = {},
    onLeaveCommunity: () -> Unit = {},
    onUserClick: () -> Unit,
    onOpenAppSettings: () -> Unit = onUserClick,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var isMenuOpen by remember { mutableStateOf(false) }
    val isOwner = remember(server, currentUser.id) { CommunityPermissions.isOwner(server, currentUser.id) }
    val canToggleServerClosed = remember(server, currentUser.id) {
        CommunityPermissions.canToggleServerClosed(server, currentUser.id)
    }
    val canManageCommunity = remember(server, currentUser.id) {
        CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_COMMUNITY)
    }
    val canManageRoles = remember(server, currentUser.id) {
        CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_ROLES)
    }
    val canManageChannels = remember(server, currentUser.id) {
        CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_CHANNELS)
    }
    val onlineCount = remember(server.members) { CommunityPermissions.onlineMembersCount(server) }

    Surface(
        modifier = modifier.fillMaxHeight(),
        color = palette.sidebarBg
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Server Header (with optional Boosted or Customized theme gradient / background preview)
            val theme = server.customization.theme
            val themeAccentHex = theme.accentColorHex
            val boostHex = server.customBannerColorHex ?: themeAccentHex
            val bgTheme = theme.toChatBackgroundTheme()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (server.boostLevel > 0) 68.dp else 56.dp)
                    .then(
                        when {
                            bgTheme != null && bgTheme.imageUrl.isNullOrBlank() -> Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(bgTheme.primaryColor).copy(alpha = 0.85f),
                                        Color(bgTheme.secondaryColor ?: boostHex).copy(alpha = 0.55f),
                                        palette.sidebarBg
                                    )
                                )
                            )
                            server.boostLevel > 0 || themeAccentHex != 0xFF5865F2L -> Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(boostHex).copy(alpha = if (server.boostLevel > 0) 0.5f else 0.28f),
                                        palette.sidebarBg
                                    )
                                )
                            )
                            else -> Modifier.background(palette.sidebarBg)
                        }
                    )
                    .clickable { isMenuOpen = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = server.name,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (server.isClosed) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = DiscordRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "FERMÉ",
                                        color = DiscordRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (server.boostLevel > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = "Serveur Boosté",
                                    tint = Color(0xFFEB459E),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Niv. ${server.boostLevel} • ${server.boostCount} Boost(s)",
                                    color = Color(0xFFEB459E),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Options du serveur",
                            tint = palette.textPrimary
                        )
                    }
                }

                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false },
                    modifier = Modifier.background(palette.cardBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("Accueil Communauté", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = palette.accent) },
                        onClick = {
                            isMenuOpen = false
                            onOpenCommunityHome()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Gérer les invitations", color = DiscordBlurple) },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DiscordBlurple) },
                        onClick = {
                            isMenuOpen = false
                            onOpenCommunityInvites()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bots & Automations", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = DiscordBlurple) },
                        onClick = {
                            isMenuOpen = false
                            onOpenBotsDashboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fond & thème du serveur", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = DiscordBlurple) },
                        onClick = {
                            isMenuOpen = false
                            onOpenServerBackground()
                        },
                        modifier = Modifier.testTag("drawer_server_background_btn")
                    )
                    DropdownMenuItem(
                        text = { Text("Sondages avancés", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null, tint = Color(0xFFEB459E)) },
                        onClick = {
                            isMenuOpen = false
                            onOpenPollsHub()
                        },
                        modifier = Modifier.testTag("drawer_menu_polls_hub")
                    )
                    DropdownMenuItem(
                        text = { Text("Extensions & Plugins", color = palette.textPrimary, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, tint = DiscordBlurple) },
                        onClick = {
                            isMenuOpen = false
                            onOpenPluginsManager()
                        },
                        modifier = Modifier.testTag("drawer_plugins_btn")
                    )
                    if (canManageRoles) {
                        DropdownMenuItem(
                            text = { Text("Rôles & Permissions", color = palette.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00D4AA)) },
                            onClick = {
                                isMenuOpen = false
                                onOpenRolesMatrix()
                            }
                        )
                    }
                    if (canManageChannels) {
                        DropdownMenuItem(
                            text = { Text("Catégories & Salons", color = palette.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = palette.textPrimary) },
                            onClick = {
                                isMenuOpen = false
                                onOpenCategoriesManager()
                            }
                        )
                    }
                    if (canToggleServerClosed) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (server.isClosed) "Ouvrir le serveur (tous les membres)" else "Fermer le serveur (admins uniquement)",
                                    color = if (server.isClosed) DiscordGreen else DiscordRed,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (server.isClosed) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (server.isClosed) DiscordGreen else DiscordRed
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onToggleServerClosed(!server.isClosed)
                            },
                            modifier = Modifier.testTag("drawer_toggle_server_closed")
                        )
                    }
                    if (canManageCommunity || canManageRoles || canManageChannels || isOwner) {
                        DropdownMenuItem(
                            text = { Text("Administration complète", color = palette.accent, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = palette.accent) },
                            onClick = {
                                isMenuOpen = false
                                onOpenAdminDashboard()
                            }
                        )
                    }
                    if (canManageCommunity) {
                        DropdownMenuItem(
                            text = { Text("Modifier la communauté", color = palette.textPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = DiscordAmber) },
                            onClick = {
                                isMenuOpen = false
                                onEditCommunity()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Inviter rapidement", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = palette.textSecondary) },
                        onClick = {
                            isMenuOpen = false
                            onInvitePeople()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Créer un salon", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null, tint = palette.textPrimary) },
                        onClick = {
                            isMenuOpen = false
                            onCreateChannelClick("SALONS TEXTUELS")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Classement & Podium", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15)) },
                        onClick = {
                            isMenuOpen = false
                            onOpenLeaderboard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Boutique du serveur", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = DiscordAmber) },
                        onClick = {
                            isMenuOpen = false
                            onOpenServerShop()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paramètres du serveur", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = palette.textSecondary) },
                        onClick = {
                            isMenuOpen = false
                            onOpenServerSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Boîte de réception", color = palette.textPrimary) },
                        leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = palette.textSecondary) },
                        onClick = {
                            isMenuOpen = false
                            onOpenNotifications()
                        }
                    )
                    if (!isOwner) {
                        HorizontalDivider(color = DiscordDarkBorder)
                        DropdownMenuItem(
                            text = { Text("Quitter la communauté", color = DiscordRed, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = DiscordRed) },
                            onClick = {
                                isMenuOpen = false
                                onLeaveCommunity()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.border, thickness = 1.dp)

            // Quick Pills: Events & Global Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenEvents)
                        .testTag("drawer_events_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "Événements du serveur",
                                tint = DiscordBlurple,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Événements",
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (server.events.isNotEmpty()) {
                            Surface(
                                color = DiscordBlurple.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = server.events.size.toString(),
                                    color = DiscordBlurple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenLeaderboard)
                        .testTag("drawer_leaderboard_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Classement du serveur",
                            tint = Color(0xFFFACC15),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenServerShop)
                        .testTag("drawer_server_shop_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "Boutique du serveur",
                            tint = DiscordAmber,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenGlobalSearch)
                        .testTag("drawer_global_search_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageSearch,
                            contentDescription = "Recherche globale",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Community Home Card right above channels list
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenCommunityHome)
                    .testTag("drawer_community_home_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Page d'accueil de la communauté",
                        tint = palette.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accueil Communauté",
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val handleText = if (server.uniqueHandle.isNotBlank()) "@${server.uniqueHandle} • " else ""
                        Text(
                            text = "${handleText}$onlineCount en ligne • ${server.members.size} membres",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Channels List (sorted by position, supports category privacy & custom icons)
            val groupedChannels = server.channels.groupBy { it.category }
            val orderedCategories = (server.categories.sortedBy { it.position }.map { it.name } + groupedChannels.keys)
                .distinct()
                .filter { catName -> CommunityPermissions.isCategoryAccessible(server, catName, currentUser.id) }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                orderedCategories.forEach { categoryName ->
                    val channels = groupedChannels[categoryName].orEmpty()
                        .filter { CommunityPermissions.isChannelAccessible(server, it, currentUser.id) }
                        .sortedBy { it.position }
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
                onOpenAppSettings = onOpenAppSettings,
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
    val palette = ravenPalette()
    val platform = LocalRavenPlatform.current
    val isVoice = channel.type == ChannelType.VOICE
    val isStage = channel.type == ChannelType.STAGE

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) palette.inputBg else Color.Transparent)
                .desktopHoverHighlight(
                    shape = RoundedCornerShape(8.dp),
                    enabled = platform.isPc || platform.hasMouseOrTrackpad
                )
                .desktopHoverPointer(platform.isPc)
                .clickable(onClick = onClick)
                .testTag("channel_${channel.id}")
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = resolveChannelIconVector(channel),
                contentDescription = null,
                tint = when {
                    channel.isLocked -> DiscordRed
                    isStage -> DiscordGreen
                    isSelected -> palette.textPrimary
                    else -> palette.textMuted
                },
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = channel.name,
                color = if (isSelected) palette.textPrimary else palette.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if ((isVoice || isStage) && (channel.maxParticipants > 0 || channel.activeVoiceUsers.isNotEmpty())) {
                val count = channel.activeVoiceUsers.size
                val isFull = channel.maxParticipants > 0 && count >= channel.maxParticipants
                Surface(
                    color = (if (isFull) DiscordRed else DiscordDarkInput).copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = if (channel.maxParticipants > 0) "$count/${channel.maxParticipants}" else "$count",
                        color = if (isFull) Color.White else DiscordTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            if (channel.isReadOnly) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lecture seule",
                    tint = DiscordAmber,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else if (channel.slowmodeSeconds > 0) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Mode lent",
                    tint = DiscordTextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

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
    onOpenAppSettings: () -> Unit = onUserClick,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit
) {
    val palette = ravenPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.border)
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
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onUserClick)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
                                    text = user.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val statusColor = when (user.status) {
                            UserStatus.ONLINE -> DiscordGreen
                            UserStatus.IDLE -> DiscordAmber
                            UserStatus.DND -> DiscordRed
                            UserStatus.OFFLINE -> DiscordTextDark
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(palette.cardBg)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = user.name,
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = user.customStatus.ifBlank { user.status.label },
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(30.dp).testTag("bottom_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Réactiver micro" else "Couper micro",
                        tint = if (isMuted) DiscordRed else palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onToggleDeafen,
                    modifier = Modifier.size(30.dp).testTag("bottom_deafen_button")
                ) {
                    Icon(
                        imageVector = if (isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                        contentDescription = if (isDeafened) "Réactiver casque" else "Couper son",
                        tint = if (isDeafened) DiscordRed else palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.size(30.dp).testTag("bottom_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Paramètres de l'application",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
