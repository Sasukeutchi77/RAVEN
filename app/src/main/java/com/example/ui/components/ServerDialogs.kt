package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelType
import com.example.ui.theme.*

@Composable
fun CreateServerDialog(
    onDismiss: () -> Unit,
    onCreateServer: (name: String, iconText: String, isPublic: Boolean, categoryTag: String) -> Unit
) {
    var serverName by remember { mutableStateOf("") }
    var selectedIconKey by remember { mutableStateOf("initials") }
    var isPublic by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("Communauté") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Text(
                text = "Créer votre serveur",
                color = DiscordTextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Votre serveur est le lieu où vous et vos amis pouvez vous réunir. Donnez-lui un nom et une icône.",
                    color = DiscordTextMuted,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Vector icon / initials selector
                Text(
                    text = "STYLE D'ICÔNE DU SERVEUR",
                    color = DiscordTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val serverIconOptions = listOf("initials", "game", "code", "rocket", "star", "shield", "bolt")
                    serverIconOptions.forEach { iconKey ->
                        val isSelected = selectedIconKey == iconKey
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) DiscordBlurple else DiscordDarkInput)
                                .clickable { selectedIconKey = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconKey == "initials") {
                                Text(
                                    text = if (serverName.isNotBlank()) serverName.take(2).uppercase() else "RV",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                AppStatusIcon(iconKey = iconKey, size = 18.dp, tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "NOM DU SERVEUR",
                    color = DiscordTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    placeholder = { Text("ex. Raven Lounge", color = DiscordTextDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DiscordDarkInput,
                        unfocusedContainerColor = DiscordDarkInput,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = DiscordTextWhite,
                        unfocusedTextColor = DiscordTextLight
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("create_server_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Publier dans Découvrir", color = DiscordTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Visible dans le Hub public", color = DiscordTextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordGreen)
                    )
                }

                if (isPublic) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Gaming", "Développement", "Musique", "Chill").forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverName.isNotBlank()) {
                        val icon = if (selectedIconKey == "initials") {
                            serverName.take(2).uppercase().ifBlank { "RV" }
                        } else {
                            selectedIconKey
                        }
                        onCreateServer(serverName.trim(), icon, isPublic, selectedCategory)
                        onDismiss()
                    }
                },
                enabled = serverName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("create_server_submit")
            ) {
                Text("Créer le serveur", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = DiscordTextMuted)
            }
        }
    )
}

@Composable
fun CreateChannelDialog(
    category: String,
    onDismiss: () -> Unit,
    onCreateChannel: (name: String, type: ChannelType, isPrivate: Boolean) -> Unit
) {
    var channelName by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf(ChannelType.TEXT) }
    var isPrivateChannel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Text(
                text = "Créer un salon",
                color = DiscordTextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "TYPE DE SALON",
                    color = DiscordTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Text Channel option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channelType == ChannelType.TEXT) DiscordDarkInput else Color.Transparent)
                        .clickable { channelType = ChannelType.TEXT }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = DiscordTextLight)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Textuel", color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Poster des messages, images et notes audio", color = DiscordTextMuted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = channelType == ChannelType.TEXT,
                        onClick = { channelType = ChannelType.TEXT },
                        colors = RadioButtonDefaults.colors(selectedColor = DiscordBlurple)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Forum Channel option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channelType == ChannelType.FORUM) DiscordDarkInput else Color.Transparent)
                        .clickable { channelType = ChannelType.FORUM }
                        .padding(10.dp)
                        .testTag("create_channel_type_forum"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = DiscordBlurple)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Forum / Entraide", color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sujets structurés avec votes, tags Résolu & réponses", color = DiscordTextMuted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = channelType == ChannelType.FORUM,
                        onClick = { channelType = ChannelType.FORUM },
                        colors = RadioButtonDefaults.colors(selectedColor = DiscordBlurple)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Voice Channel option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channelType == ChannelType.VOICE) DiscordDarkInput else Color.Transparent)
                        .clickable { channelType = ChannelType.VOICE }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = DiscordGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vocal", color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Parler en direct avec les autres membres", color = DiscordTextMuted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = channelType == ChannelType.VOICE,
                        onClick = { channelType = ChannelType.VOICE },
                        colors = RadioButtonDefaults.colors(selectedColor = DiscordBlurple)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stage Channel option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (channelType == ChannelType.STAGE) DiscordDarkInput else Color.Transparent)
                        .clickable { channelType = ChannelType.STAGE }
                        .padding(10.dp)
                        .testTag("create_channel_type_stage"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Podcasts, contentDescription = null, tint = DiscordAmber)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scène / Conférence", color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Orateurs sur scène, auditoire et mains levées", color = DiscordTextMuted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = channelType == ChannelType.STAGE,
                        onClick = { channelType = ChannelType.STAGE },
                        colors = RadioButtonDefaults.colors(selectedColor = DiscordBlurple)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "NOM DU SALON",
                    color = DiscordTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    placeholder = { Text("nouveau-salon", color = DiscordTextDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DiscordDarkInput,
                        unfocusedContainerColor = DiscordDarkInput,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = DiscordTextWhite,
                        unfocusedTextColor = DiscordTextLight
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("create_channel_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Private Channel toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = DiscordTextLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Salon privé", color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Visible uniquement par les modérateurs et admins", color = DiscordTextMuted, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = isPrivateChannel,
                        onCheckedChange = { isPrivateChannel = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DiscordBlurple,
                            uncheckedTrackColor = DiscordDarkInput
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (channelName.isNotBlank()) {
                        onCreateChannel(channelName.trim(), channelType, isPrivateChannel)
                        onDismiss()
                    }
                },
                enabled = channelName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("create_channel_submit")
            ) {
                Text("Créer le salon", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = DiscordTextMuted)
            }
        }
    )
}
