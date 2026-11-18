package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Channel
import com.example.model.CustomRole
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Dialog for controlling an individual participant's volume (0%..200%) or excluding them (staff).
 */
@Composable
fun ParticipantVoiceControlDialog(
    participant: User,
    currentVolume: Float,
    canModerateVoice: Boolean,
    connectionStateLabel: String?,
    onVolumeChange: (Float) -> Unit,
    onKickFromVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var sliderVal by remember(participant.id, currentVolume) { mutableFloatStateOf(currentVolume.coerceIn(0f, 2f)) }
    val percent = (sliderVal * 100).roundToInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(participant.avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = participant.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = participant.name,
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${participant.role.title} • ${connectionStateLabel ?: "Connecté"}",
                        color = palette.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Volume individuel : $percent%",
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = sliderVal,
                    onValueChange = {
                        sliderVal = it
                        onVolumeChange(it)
                    },
                    valueRange = 0f..2f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent
                    ),
                    modifier = Modifier.testTag("participant_volume_slider_${participant.id}")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0f to "Muet 0%", 1f to "Normal 100%", 2f to "Boost 200%").forEach { (preset, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(sliderVal - preset) < 0.08f,
                            onClick = {
                                sliderVal = preset
                                onVolumeChange(preset)
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                if (canModerateVoice) {
                    HorizontalDivider(color = palette.border)
                    Button(
                        onClick = {
                            onKickFromVoice()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("kick_voice_participant_${participant.id}")
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exclure du salon vocal", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = palette.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Staff dialog to configure Voice Channel Access: Lock/Unlock, Max Participants limit, and Allowed Roles.
 */
@Composable
fun VoiceChannelAccessDialog(
    channel: Channel,
    serverCustomRoles: List<CustomRole>,
    onSave: (isLocked: Boolean, maxParticipants: Int, allowedRoles: List<UserRole>, allowedRoleIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var isLocked by remember(channel.id) { mutableStateOf(channel.isLocked) }
    var maxParticipants by remember(channel.id) { mutableIntStateOf(channel.maxParticipants) }
    var selectedBaseRoles by remember(channel.id) { mutableStateOf(channel.allowedRoles.toSet()) }
    var selectedCustomRoleIds by remember(channel.id) { mutableStateOf(channel.allowedRoleIds.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Paramètres vocaux • ${channel.name}",
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = palette.sidebarBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isLocked) DiscordRed.copy(alpha = 0.5f) else palette.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Verrouiller le salon",
                                color = palette.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Empêche de nouveaux membres de rejoindre (sauf modérateurs).",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it },
                            modifier = Modifier.testTag("voice_lock_switch")
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "LIMITE DE PARTICIPANTS",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(0 to "Illimité", 2 to "2 max", 5 to "5 max", 10 to "10 max", 25 to "25 max")) { (cap, label) ->
                            FilterChip(
                                selected = maxParticipants == cap,
                                onClick = { maxParticipants = cap },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "RÔLES AUTORISÉS (VIDE = TOUS)",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(UserRole.ADMIN, UserRole.MODERATOR, UserRole.MEMBER)) { role ->
                            val selected = role in selectedBaseRoles
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedBaseRoles = if (selected) {
                                        selectedBaseRoles - role
                                    } else {
                                        selectedBaseRoles + role
                                    }
                                },
                                label = { Text(role.title, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (serverCustomRoles.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(serverCustomRoles, key = { it.id }) { customRole ->
                                val selected = customRole.id in selectedCustomRoleIds
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCustomRoleIds = if (selected) {
                                            selectedCustomRoleIds - customRole.id
                                        } else {
                                            selectedCustomRoleIds + customRole.id
                                        }
                                    },
                                    label = { Text(customRole.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        isLocked,
                        maxParticipants,
                        selectedBaseRoles.toList(),
                        selectedCustomRoleIds.toList()
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.testTag("save_voice_access_btn")
            ) {
                Text("Enregistrer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}
