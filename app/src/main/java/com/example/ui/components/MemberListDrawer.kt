package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.model.User
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.ui.theme.*

@Composable
fun MemberListDrawer(
    members: List<User>,
    onClose: () -> Unit,
    onMemberClick: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp),
        color = DiscordDarkSidebar
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Membres (${members.size})",
                    color = DiscordTextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp).testTag("close_member_list")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = DiscordTextLight
                    )
                }
            }

            HorizontalDivider(color = DiscordDarkBorder, thickness = 1.dp)

            val admins = members.filter { it.role == UserRole.ADMIN }
            val mods = members.filter { it.role == UserRole.MODERATOR }
            val online = members.filter { it.role == UserRole.MEMBER && it.status != UserStatus.OFFLINE }
            val bots = members.filter { it.role == UserRole.BOT }
            val offline = members.filter { it.status == UserStatus.OFFLINE && it.role != UserRole.BOT }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (admins.isNotEmpty()) {
                    item { RoleCategoryHeader("ADMINISTRATEURS — ${admins.size}") }
                    items(admins, key = { it.id }) { user ->
                        MemberItemRow(user = user, onClick = { onMemberClick(user) })
                    }
                }

                if (mods.isNotEmpty()) {
                    item { RoleCategoryHeader("MODÉRATEURS — ${mods.size}") }
                    items(mods, key = { it.id }) { user ->
                        MemberItemRow(user = user, onClick = { onMemberClick(user) })
                    }
                }

                if (bots.isNotEmpty()) {
                    item { RoleCategoryHeader("BOTS — ${bots.size}") }
                    items(bots, key = { it.id }) { user ->
                        MemberItemRow(user = user, onClick = { onMemberClick(user) })
                    }
                }

                if (online.isNotEmpty()) {
                    item { RoleCategoryHeader("EN LIGNE — ${online.size}") }
                    items(online, key = { it.id }) { user ->
                        MemberItemRow(user = user, onClick = { onMemberClick(user) })
                    }
                }

                if (offline.isNotEmpty()) {
                    item { RoleCategoryHeader("HORS LIGNE — ${offline.size}") }
                    items(offline, key = { it.id }) { user ->
                        MemberItemRow(user = user, onClick = { onMemberClick(user) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleCategoryHeader(title: String) {
    Text(
        text = title,
        color = DiscordTextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun MemberItemRow(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(
                        when (user.status) {
                            UserStatus.ONLINE -> DiscordGreen
                            UserStatus.IDLE -> DiscordAmber
                            UserStatus.DND -> DiscordRed
                            UserStatus.OFFLINE -> DiscordTextDark
                        }
                    )
            )
            AvatarDecorationOverlay(
                decorationKey = user.avatarDecoration,
                size = 32.dp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name,
                    color = if (user.status == UserStatus.OFFLINE) DiscordTextMuted else DiscordTextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.role == UserRole.BOT) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = DiscordBlurple,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "BOT",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            if (user.customStatus.isNotBlank()) {
                Text(
                    text = user.customStatus,
                    color = DiscordTextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
