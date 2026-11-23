package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Server
import com.example.model.ServerFolder
import com.example.ui.theme.*

@Composable
fun ServerRail(
    servers: List<Server>,
    selectedServerId: String,
    serverFolders: List<ServerFolder> = emptyList(),
    onSelectServer: (String) -> Unit,
    onToggleFolder: (String) -> Unit = {},
    onCreateFolderClick: () -> Unit = {},
    onCreateServerClick: () -> Unit,
    onJoinServerClick: () -> Unit = {},
    onExploreServersClick: () -> Unit = {},
    onOpenOrbsShop: () -> Unit = {},
    onOpenBotsDashboard: () -> Unit = {},
    unseenStoriesCount: Int = 0,
    onOpenStories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val serverList = servers.filterNot { it.isDmTab }
    val groupedServerIds = serverFolders.flatMap { it.serverIds }.toSet()
    val ungroupedServers = serverList.filterNot { it.id in groupedServerIds }

    Surface(
        modifier = modifier
            .width(68.dp)
            .fillMaxHeight(),
        color = palette.primaryBg
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Direct Messages / Home item at top
            item {
                val isDmActive = selectedServerId == "dm_tab"
                ServerRailItem(
                    isSelected = isDmActive,
                    onClick = { onSelectServer("dm_tab") },
                    testTag = "server_rail_dm"
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(if (isDmActive) RoundedCornerShape(14.dp) else CircleShape)
                            .background(if (isDmActive) DiscordBlurple else palette.inputBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Messages Privés",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .width(30.dp),
                    thickness = 1.5.dp,
                    color = palette.border
                )
            }

            // Collapsible Server Folders
            items(serverFolders, key = { "folder_${it.id}" }) { folder ->
                val folderServers = serverList.filter { it.id in folder.serverIds }
                if (folderServers.isNotEmpty()) {
                    val folderColor = Color(folder.colorHex)
                    Surface(
                        color = folderColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, folderColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .testTag("server_folder_${folder.id}")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(folderColor.copy(alpha = 0.35f))
                                    .clickable { onToggleFolder(folder.id) }
                                    .testTag("toggle_folder_${folder.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (folder.isExpanded) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = folder.name,
                                        tint = folderColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    // Mini 2x2 grid preview when collapsed
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        folderServers.take(4).chunked(2).forEach { rowSrvs ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                rowSrvs.forEach { s ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(13.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(s.iconBgColor)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = s.iconText.take(1),
                                                            color = Color.White,
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = folder.isExpanded) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    folderServers.forEach { server ->
                                        val isSelected = server.id == selectedServerId
                                        ServerRailIconButton(
                                            server = server,
                                            isSelected = isSelected,
                                            onSelectServer = { onSelectServer(server.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Ungrouped Normal Servers list
            items(ungroupedServers, key = { it.id }) { server ->
                val isSelected = server.id == selectedServerId
                ServerRailIconButton(
                    server = server,
                    isSelected = isSelected,
                    onSelectServer = { onSelectServer(server.id) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Add server & Discover & tools in sleek Discord circular buttons
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onCreateServerClick() }
                        .testTag("add_server_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Ajouter un serveur",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Explore public servers catalog
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onExploreServersClick() }
                        .testTag("explore_servers_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Découvrir les serveurs publics",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onCreateFolderClick() }
                        .testTag("create_folder_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Créer un dossier de serveurs",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Join server by invite code
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onJoinServerClick() }
                        .testTag("join_server_code_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = "Rejoindre par code d'invitation",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(28.dp),
                    thickness = 1.dp,
                    color = palette.border
                )

                // Orbs Shop button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onOpenOrbsShop() }
                        .testTag("server_rail_orbs_shop"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Boutique & Quêtes Orbs",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.inputBg)
                        .clickable { onOpenBotsDashboard() }
                        .testTag("server_rail_bots_dashboard"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Bots & Automations RAVEN",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("server_rail_stories_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(palette.inputBg)
                            .clickable { onOpenStories() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = "Stories RAVEN",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    if (unseenStoriesCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(17.dp)
                                .align(Alignment.TopEnd)
                                .clip(CircleShape)
                                .background(DiscordRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = unseenStoriesCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRailIconButton(
    server: Server,
    isSelected: Boolean,
    onSelectServer: () -> Unit
) {
    ServerRailItem(
        isSelected = isSelected,
        unreadCount = server.unreadCount,
        onClick = onSelectServer,
        testTag = "server_rail_${server.id}"
    ) {
        val themeAccent = server.customization.theme.accentColorHex
        val bgCol = if (isSelected) DiscordBlurple else Color(if (themeAccent != 0xFF5865F2L) themeAccent else server.iconBgColor)
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(if (isSelected) RoundedCornerShape(14.dp) else CircleShape)
                .background(bgCol),
            contentAlignment = Alignment.Center
        ) {
            val presetKey = server.customization.theme.iconPresetKey
            if (!server.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = server.imageUrl,
                    contentDescription = "Logo ${server.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!presetKey.isNullOrBlank()) {
                Icon(
                    imageVector = resolveCatalogIconVector(presetKey),
                    contentDescription = server.name,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                val iconKeys = listOf("game", "code", "rocket", "star", "shield", "bolt", "headset")
                if (iconKeys.contains(server.iconText.lowercase())) {
                    AppStatusIcon(iconKey = server.iconText, size = 20.dp, tint = Color.White)
                } else {
                    Text(
                        text = server.iconText,
                        color = Color.White,
                        fontSize = if (server.iconText.length > 2) 13.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRailItem(
    isSelected: Boolean,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    val pillHeight by animateDpAsState(
        targetValue = if (isSelected) 36.dp else if (unreadCount > 0) 8.dp else 0.dp,
        label = "pillHeight"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        // Pill indicator on left
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(pillHeight)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                .background(Color.White)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            content()

            if (unreadCount > 0 && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(DiscordRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
    }
}
