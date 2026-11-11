package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Server
import com.example.ui.theme.*

@Composable
fun ServerRail(
    servers: List<Server>,
    selectedServerId: String,
    onSelectServer: (String) -> Unit,
    onCreateServerClick: () -> Unit,
    onOpenOrbsShop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight(),
        color = DiscordDarkPrimary
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Direct Messages item at top
            item {
                val isDmActive = selectedServerId == "dm_tab"
                ServerRailItem(
                    isSelected = isDmActive,
                    onClick = { onSelectServer("dm_tab") },
                    testTag = "server_rail_dm"
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(if (isDmActive) RoundedCornerShape(16.dp) else CircleShape)
                            .background(if (isDmActive) DiscordBlurple else DiscordDarkInput),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Messages Privés",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .width(32.dp),
                    thickness = 2.dp,
                    color = DiscordDarkBorder
                )
            }

            // Normal Servers list
            val serverList = servers.filterNot { it.isDmTab }
            items(serverList, key = { it.id }) { server ->
                val isSelected = server.id == selectedServerId
                ServerRailItem(
                    isSelected = isSelected,
                    unreadCount = server.unreadCount,
                    onClick = { onSelectServer(server.id) },
                    testTag = "server_rail_${server.id}"
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(if (isSelected) RoundedCornerShape(16.dp) else CircleShape)
                            .background(if (isSelected) DiscordBlurple else Color(server.iconBgColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconKeys = listOf("game", "code", "rocket", "star", "shield", "bolt", "headset")
                        if (iconKeys.contains(server.iconText.lowercase())) {
                            AppStatusIcon(iconKey = server.iconText, size = 22.dp, tint = Color.White)
                        } else {
                            Text(
                                text = server.iconText,
                                color = Color.White,
                                fontSize = if (server.iconText.length > 2) 15.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Add server button (+)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DiscordDarkInput)
                        .clickable { onCreateServerClick() }
                        .testTag("add_server_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Ajouter un serveur",
                        tint = DiscordGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Orbs Shop button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF5865F2), Color(0xFFEB459E))))
                        .clickable { onOpenOrbsShop() }
                        .testTag("server_rail_orbs_shop"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Boutique & Quêtes Orbs",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
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
