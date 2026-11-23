package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun stickerVectorIcon(iconKey: String): ImageVector = when (iconKey) {
    "wave" -> Icons.Default.WavingHand
    "trophy" -> Icons.Default.EmojiEvents
    "bolt" -> Icons.Default.Bolt
    "heart" -> Icons.Default.Favorite
    "crown" -> Icons.Default.Star
    "rocket" -> Icons.Default.RocketLaunch
    "shield" -> Icons.Default.Shield
    "fire" -> Icons.Default.LocalFireDepartment
    else -> Icons.Default.AutoAwesome
}

@Composable
fun StickerMessageBubble(
    stickerId: String,
    modifier: Modifier = Modifier
) {
    val sticker = remember(stickerId) {
        StickerCatalog.allStickers.find { it.id == stickerId }
            ?: StickerCatalog.allStickers.first()
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(sticker.primaryColorHex).copy(alpha = 0.65f)),
        color = Color.Transparent,
        modifier = modifier
            .padding(top = 4.dp)
            .width(156.dp)
            .testTag("sticker_bubble_$stickerId")
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(sticker.primaryColorHex),
                            Color(sticker.secondaryColorHex)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = stickerVectorIcon(sticker.iconKey),
                        contentDescription = sticker.title,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sticker.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sticker.tagline,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerSheet(
    unlockedStickerIds: List<String>,
    orbsBalance: Int,
    onSelectSticker: (String) -> Unit,
    onUnlockAndSendSticker: (String, Int) -> Boolean,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var feedbackError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("sticker_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DiscordBlurple)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stickers Raven",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = DiscordBlurple.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$orbsBalance Orbs",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (feedbackError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = feedbackError!!, color = DiscordRed, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(StickerCatalog.allStickers, key = { it.id }) { sticker ->
                    val isUnlocked = sticker.priceOrbs == 0 || unlockedStickerIds.contains(sticker.id)
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isUnlocked) Color(sticker.primaryColorHex).copy(alpha = 0.6f) else palette.border
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (isUnlocked) {
                                    onSelectSticker(sticker.id)
                                    onDismiss()
                                } else {
                                    val ok = onUnlockAndSendSticker(sticker.id, sticker.priceOrbs)
                                    if (ok) {
                                        onDismiss()
                                    } else {
                                        feedbackError = "Solde d'Orbs insuffisant pour ${sticker.title} (${sticker.priceOrbs} Orbs)."
                                    }
                                }
                            }
                            .testTag("sticker_option_${sticker.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(sticker.primaryColorHex),
                                                Color(sticker.secondaryColorHex)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = stickerVectorIcon(sticker.iconKey),
                                    contentDescription = sticker.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sticker.title,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isUnlocked) {
                                Text(
                                    text = "Envoyer",
                                    color = DiscordGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Débloquer • ${sticker.priceOrbs} Orbs",
                                    color = DiscordAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileAttachmentCard(
    fileName: String,
    attachmentType: String?,
    sizeLabel: String?,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val icon = when {
        fileName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
        fileName.endsWith(".zip", ignoreCase = true) || fileName.endsWith(".rar", ignoreCase = true) -> Icons.Default.FolderZip
        fileName.endsWith(".kt", ignoreCase = true) || fileName.endsWith(".json", ignoreCase = true) -> Icons.Default.Code
        attachmentType?.startsWith("audio") == true -> Icons.Default.AudioFile
        attachmentType?.startsWith("video") == true -> Icons.Default.VideoFile
        else -> Icons.Default.InsertDriveFile
    }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .padding(top = 6.dp)
            .fillMaxWidth(0.88f)
            .testTag("file_attachment_card")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DiscordBlurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Fichier joint",
                    tint = DiscordBlurple,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        sizeLabel?.takeIf { it.isNotBlank() },
                        attachmentType?.substringAfter("/")?.uppercase()?.takeIf { it.isNotBlank() } ?: "DOCUMENT"
                    ).joinToString(" • "),
                    color = palette.textMuted,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Fichier partagé",
                tint = DiscordGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SlashCommandMenu(
    query: String,
    extraBotCommands: List<SlashCommandOption> = emptyList(),
    onSelectCommand: (SlashCommandOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val matching = remember(query, extraBotCommands) {
        val cmdPrefix = query.substringBefore(" ").lowercase()
        (SlashCommandCatalog.commands + extraBotCommands)
            .distinctBy { it.command.lowercase() }
            .filter {
                it.command.lowercase().startsWith(cmdPrefix) || cmdPrefix == "/"
            }
    }
    if (matching.isEmpty()) return

    Surface(
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag("slash_command_menu")
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                text = "COMMANDES SLASH",
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            matching.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCommand(option) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("slash_option_${option.command.removePrefix("/")}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DiscordBlurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "/",
                                color = DiscordBlurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = option.command,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = option.description,
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "Utiliser",
                        color = DiscordBlurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBar(
    typingUserNames: List<String>,
    modifier: Modifier = Modifier
) {
    if (typingUserNames.isEmpty()) return
    val palette = ravenPalette()
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typingAlpha"
    )

    val label = if (typingUserNames.size == 1) {
        "${typingUserNames.first()} est en train d'écrire..."
    } else {
        "${typingUserNames.joinToString(", ")} sont en train d'écrire..."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.chatBg)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("typing_indicator_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(DiscordBlurple.copy(alpha = alpha))
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = palette.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CreateGroupDmDialog(
    friends: List<User>,
    onCreateGroup: (groupName: String, members: List<User>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var groupName by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Text(
                text = "Créer un Groupe Privé",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Nom du groupe (ex. Squad Soirée)", color = palette.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_group_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Text(
                    text = "SÉLECTIONNER AU MOINS 2 AMIS (${selectedIds.size})",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(friends, key = { it.id }) { friend ->
                        val isSelected = selectedIds.contains(friend.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedIds.remove(friend.id)
                                    else selectedIds.add(friend.id)
                                }
                                .padding(vertical = 4.dp)
                                .testTag("group_friend_toggle_${friend.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedIds.contains(friend.id)) selectedIds.add(friend.id)
                                    } else selectedIds.remove(friend.id)
                                }
                            )
                            Text(
                                text = "${friend.name}${friend.tag}",
                                color = palette.textPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = friends.filter { it.id in selectedIds }
                    if (chosen.size >= 2) {
                        onCreateGroup(groupName, chosen)
                        onDismiss()
                    }
                },
                enabled = selectedIds.size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("create_group_submit_button")
            ) {
                Text("Créer le groupe", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}

@Composable
fun CreateServerFolderDialog(
    servers: List<Server>,
    onCreateFolder: (name: String, colorHex: Long, serverIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var folderName by remember { mutableStateOf("") }
    val folderColors = listOf(0xFF5865F2L, 0xFF23A55AL, 0xFFF0B232L, 0xFFEB459EL, 0xFF00A8FCL, 0xFFEF4444L)
    var selectedColor by remember { mutableStateOf(folderColors.first()) }
    val realServers = remember(servers) { servers.filterNot { it.isDmTab } }
    val selectedServerIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Text(
                text = "Créer un dossier de serveurs",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Nom du dossier (ex. Gaming)", color = palette.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_folder_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    folderColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == hex) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Text(
                    text = "SERVEURS À REGROUPER (${selectedServerIds.size})",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                    items(realServers, key = { it.id }) { srv ->
                        val isSelected = selectedServerIds.contains(srv.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedServerIds.remove(srv.id)
                                    else selectedServerIds.add(srv.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedServerIds.contains(srv.id)) selectedServerIds.add(srv.id)
                                    } else selectedServerIds.remove(srv.id)
                                }
                            )
                            Text(
                                text = srv.name,
                                color = palette.textPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank() && selectedServerIds.isNotEmpty()) {
                        onCreateFolder(folderName, selectedColor, selectedServerIds.toList())
                        onDismiss()
                    }
                },
                enabled = folderName.isNotBlank() && selectedServerIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("create_folder_submit_button")
            ) {
                Text("Créer", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}
