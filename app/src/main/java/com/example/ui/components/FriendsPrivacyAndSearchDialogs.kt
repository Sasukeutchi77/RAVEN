package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FriendFirestoreSync
import com.example.model.DirectMessagePrivacyPolicy
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationStatus
import com.example.model.FriendRelationship
import com.example.model.FriendRequestPolicy
import com.example.model.User
import com.example.model.UserDirectoryEntry
import com.example.model.UserStatus
import com.example.ui.theme.*

@Composable
fun FriendRelationshipCard(
    relationship: FriendRelationship,
    onOpenDm: (User) -> Unit,
    onStartCall: (User) -> Unit,
    onOpenProfile: (User) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onBlockUser: (User) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var showMenu by remember { mutableStateOf(false) }
    val user = relationship.user
    val activitySummary = when {
        relationship.lastActivityLabel.isNotBlank() -> relationship.lastActivityLabel
        user.customStatus.isNotBlank() -> user.customStatus
        relationship.lastActiveAtMs > 0L -> FriendFirestoreSync.formatRelativeActivityTime(relationship.lastActiveAtMs)
        else -> user.status.label
    }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable { onOpenProfile(user) }
            .testTag("friend_card_${relationship.friendUid}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(user.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = "Avatar de ${user.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = user.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
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
                            when (user.status) {
                                UserStatus.ONLINE -> DiscordGreen
                                UserStatus.IDLE -> DiscordAmber
                                UserStatus.DND -> DiscordRed
                                UserStatus.OFFLINE -> DiscordTextDark
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = user.tag,
                        color = palette.textMuted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = activitySummary,
                    color = if (user.status == UserStatus.ONLINE) DiscordGreen else palette.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (relationship.privateNote.isNotBlank()) {
                    Text(
                        text = "Note : ${relationship.privateNote}",
                        color = DiscordBlurple,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onOpenDm(user) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("friend_dm_${relationship.friendUid}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Message privé",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = { onStartCall(user) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("friend_call_${relationship.friendUid}")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Appeler",
                    tint = DiscordGreen,
                    modifier = Modifier.size(19.dp)
                )
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("friend_more_${relationship.friendUid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options d'ami",
                        tint = palette.textMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Voir le profil & notes") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenProfile(user)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Retirer des amis", color = DiscordAmber) },
                        leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = DiscordAmber) },
                        onClick = {
                            showMenu = false
                            onRemoveFriend(relationship.friendUid)
                        },
                        modifier = Modifier.testTag("friend_remove_${relationship.friendUid}")
                    )
                    DropdownMenuItem(
                        text = { Text("Bloquer", color = DiscordRed) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = DiscordRed) },
                        onClick = {
                            showMenu = false
                            onBlockUser(user)
                        },
                        modifier = Modifier.testTag("friend_block_${relationship.friendUid}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDirectorySearchSheet(
    searchQuery: String,
    results: List<UserDirectoryEntry>,
    onSearchQueryChange: (String) -> Unit,
    onSendFriendRequest: (User) -> Unit,
    onAcceptRequest: (String) -> Unit,
    onDeclineRequest: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onOpenDm: (User) -> Unit,
    onUnblockUser: (String) -> Unit,
    onOpenProfile: (UserDirectoryEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("friend_directory_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 640.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = DiscordBlurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rechercher des utilisateurs",
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text("Nom, Tag (#1234), Clan ou ID...", color = palette.textMuted, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.textMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer", tint = palette.textMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputBg,
                    unfocusedContainerColor = palette.inputBg,
                    focusedBorderColor = DiscordBlurple,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friend_directory_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun utilisateur trouvé pour « $searchQuery ».",
                        color = palette.textMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.user.id }) { entry ->
                        val user = entry.user
                        val canRequestByPolicy = when (entry.friendRequestPolicy) {
                            FriendRequestPolicy.EVERYONE -> true
                            FriendRequestPolicy.MUTUAL_SERVERS -> entry.mutualServers.isNotEmpty()
                            FriendRequestPolicy.NOBODY -> false
                        }

                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(entry) }
                                .testTag("directory_user_${user.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(42.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(user.avatarColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!user.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = "Avatar de ${user.name}",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (entry.showOnlineStatus) {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
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
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${user.name}${user.tag}",
                                        color = palette.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = buildList {
                                        if (entry.mutualServers.isNotEmpty()) {
                                            add("${entry.mutualServers.size} serveur(s) commun(s)")
                                        }
                                        if (entry.showLastActivity && entry.lastActivityLabel.isNotBlank()) {
                                            add(entry.lastActivityLabel)
                                        }
                                    }.joinToString(" • ").ifBlank {
                                        if (entry.showOnlineStatus) user.status.label else "Statut privé"
                                    }
                                    Text(
                                        text = subtitle,
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.privateNote.isNotBlank()) {
                                        Text(
                                            text = "Note : ${entry.privateNote}",
                                            color = DiscordBlurple,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                when (entry.relationStatus) {
                                    FriendRelationStatus.FRIENDS -> {
                                        FilledTonalButton(
                                            onClick = {
                                                onOpenDm(user)
                                                onDismiss()
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("dir_dm_${user.id}")
                                        ) {
                                            Text("DM", fontSize = 12.sp)
                                        }
                                    }

                                    FriendRelationStatus.REQUEST_SENT -> {
                                        OutlinedButton(
                                            onClick = { entry.pendingRequestId?.let(onCancelRequest) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("dir_cancel_${user.id}")
                                        ) {
                                            Text("Annuler", color = DiscordRed, fontSize = 12.sp)
                                        }
                                    }

                                    FriendRelationStatus.REQUEST_RECEIVED -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { entry.pendingRequestId?.let(onAcceptRequest) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordGreen.copy(alpha = 0.2f))
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Accepter", tint = DiscordGreen, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { entry.pendingRequestId?.let(onDeclineRequest) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordRed.copy(alpha = 0.2f))
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Refuser", tint = DiscordRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    FriendRelationStatus.BLOCKED_BY_ME -> {
                                        TextButton(
                                            onClick = { onUnblockUser(user.id) },
                                            modifier = Modifier.testTag("dir_unblock_${user.id}")
                                        ) {
                                            Text("Débloquer", color = DiscordBlurple, fontSize = 12.sp)
                                        }
                                    }

                                    FriendRelationStatus.NONE -> {
                                        Button(
                                            onClick = { onSendFriendRequest(user) },
                                            enabled = canRequestByPolicy,
                                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.testTag("dir_add_${user.id}")
                                        ) {
                                            Text(
                                                text = if (canRequestByPolicy) "Ajouter" else "Fermé",
                                                color = Color.White,
                                                fontSize = 12.sp
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendPrivacySettingsSheet(
    privacy: FriendPrivacySettings,
    onSavePrivacy: (FriendPrivacySettings) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var draft by remember(privacy) { mutableStateOf(privacy) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("friend_privacy_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = DiscordBlurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confidentialité & Demandes d'amis",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUI PEUT VOUS ENVOYER UNE DEMANDE D'AMI",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FriendRequestPolicy.entries.forEach { policy ->
                        val selected = draft.friendRequestPolicy == policy
                        Surface(
                            color = if (selected) DiscordBlurple.copy(alpha = 0.16f) else palette.inputBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) DiscordBlurple else palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { draft = draft.copy(friendRequestPolicy = policy) }
                                .testTag("friend_req_policy_${policy.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { draft = draft.copy(friendRequestPolicy = policy) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(policy.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(policy.description, color = palette.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUI PEUT DÉMARRER UN MESSAGE PRIVÉ (DM)",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    DirectMessagePrivacyPolicy.entries.forEach { policy ->
                        val selected = draft.allowDmsFrom == policy
                        Surface(
                            color = if (selected) DiscordBlurple.copy(alpha = 0.16f) else palette.inputBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) DiscordBlurple else palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { draft = draft.copy(allowDmsFrom = policy) }
                                .testTag("friend_dm_policy_${policy.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { draft = draft.copy(allowDmsFrom = policy) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(policy.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(policy.description, color = palette.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "VISIBILITÉ & NOTIFICATIONS",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    PrivacySwitchRow(
                        title = "Afficher mon statut en ligne",
                        subtitle = "Permettre à vos amis de voir quand vous êtes connecté",
                        checked = draft.showOnlineStatus,
                        onCheckedChange = { draft = draft.copy(showOnlineStatus = it) },
                        testTag = "friend_show_online_toggle"
                    )
                    HorizontalDivider(color = palette.border)
                    PrivacySwitchRow(
                        title = "Afficher ma dernière activité",
                        subtitle = "Partager l'horodatage et l'activité récente sur votre profil",
                        checked = draft.showLastActivity,
                        onCheckedChange = { draft = draft.copy(showLastActivity = it) },
                        testTag = "friend_show_activity_toggle"
                    )
                    HorizontalDivider(color = palette.border)
                    PrivacySwitchRow(
                        title = "Notifications des demandes d'amitié",
                        subtitle = "Recevoir une alerte lors d'une nouvelle demande ou acceptation",
                        checked = draft.notifyFriendRequests,
                        onCheckedChange = { draft = draft.copy(notifyFriendRequests = it) },
                        testTag = "friend_notify_requests_toggle"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler", color = palette.textMuted)
                }
                Button(
                    onClick = {
                        onSavePrivacy(draft)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("friend_privacy_save_btn")
                ) {
                    Text("Enregistrer", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PrivacySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = ravenPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = palette.textMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordBlurple)
        )
    }
}
