package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.AudioPlayerHelper
import kotlinx.coroutines.delay

@Composable
fun VoiceNoteBubbleCard(
    audioUrl: String,
    durationSec: Int,
    waveformAmplitudes: List<Int>,
    audioPlayer: AudioPlayerHelper,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var isPlayingThis by remember(audioUrl) { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(audioPlayer.playbackSpeed) }
    var playProgress by remember(audioUrl) { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlayingThis) {
        while (isPlayingThis) {
            if (audioPlayer.currentPlayingPath != audioUrl || !audioPlayer.isPlaying) {
                isPlayingThis = false
                playProgress = 0f
                break
            }
            val dur = audioPlayer.durationMs().coerceAtLeast(1)
            val pos = audioPlayer.currentPositionMs().coerceIn(0, dur)
            playProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            delay(100L)
        }
    }

    val bars = remember(waveformAmplitudes) {
        if (waveformAmplitudes.isNotEmpty()) waveformAmplitudes.take(22)
        else listOf(22, 48, 72, 38, 86, 64, 42, 78, 54, 90, 60, 35, 68, 44, 28, 52, 76, 40)
    }

    Surface(
        color = palette.inputBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier.fillMaxWidth(0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DiscordBlurple)
                    .clickable {
                        if (isPlayingThis) {
                            audioPlayer.stop()
                            isPlayingThis = false
                            playProgress = 0f
                        } else {
                            isPlayingThis = true
                            audioPlayer.play(audioUrl, currentSpeed) {
                                isPlayingThis = false
                                playProgress = 0f
                            }
                        }
                    }
                    .testTag("voice_note_play_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlayingThis) "Pause" else "Lire la note vocale",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    bars.forEachIndexed { idx, amp ->
                        val frac = idx.toFloat() / bars.size.coerceAtLeast(1)
                        val active = isPlayingThis && frac <= playProgress
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight((amp / 100f).coerceIn(0.18f, 1f))
                                .clip(CircleShape)
                                .background(if (active) DiscordBlurple else DiscordTextMuted.copy(alpha = 0.55f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (durationSec > 0) "Note vocale • ${durationSec}s" else "Note vocale",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
            }

            Surface(
                color = DiscordBlurple.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        currentSpeed = audioPlayer.cycleSpeed()
                    }
                    .testTag("voice_note_speed_btn")
            ) {
                val speedText = when {
                    currentSpeed >= 1.9f -> "2x"
                    currentSpeed >= 1.4f -> "1.5x"
                    else -> "1x"
                }
                Text(
                    text = speedText,
                    color = DiscordBlurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceNoteRecordingBanner(
    recordingSeconds: Int,
    amplitudes: List<Int>,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = DiscordRed.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DiscordRed.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("voice_recording_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(DiscordRed)
            )
            Text(
                text = String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60),
                color = DiscordTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val shown = amplitudes.takeLast(18).ifEmpty { listOf(20, 45, 30, 60, 35, 50) }
                shown.forEach { amp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight((amp / 100f).coerceIn(0.2f, 1f))
                            .clip(CircleShape)
                            .background(DiscordRed.copy(alpha = 0.8f))
                    )
                }
            }
            IconButton(onClick = onToggleLock, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Micro verrouillé" else "Verrouiller micro",
                    tint = if (isLocked) DiscordGreen else DiscordTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Annuler", tint = DiscordRed, modifier = Modifier.size(18.dp))
            }
            Surface(
                color = DiscordBlurple,
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSend)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer note audio", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun ViewOnceMediaCard(
    message: Message,
    currentUserId: String,
    onOpenViewOnce: () -> Unit
) {
    val alreadyOpenedByViewer = message.sender.id != currentUserId &&
        (currentUserId in message.viewedByIds || message.imageUrl.isNullOrBlank())
    Surface(
        color = if (alreadyOpenedByViewer) DiscordDarkInput else DiscordBlurple.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (alreadyOpenedByViewer) DiscordDarkBorder else DiscordBlurple.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !alreadyOpenedByViewer, onClick = onOpenViewOnce)
            .testTag("view_once_media_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (alreadyOpenedByViewer) Icons.Default.VisibilityOff else Icons.Default.LooksOne,
                contentDescription = "Photo vue unique",
                tint = if (alreadyOpenedByViewer) DiscordTextMuted else DiscordBlurple,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = if (alreadyOpenedByViewer) "Photo ouverte (Vue unique)" else "Photo à vue unique",
                    color = if (alreadyOpenedByViewer) DiscordTextMuted else DiscordTextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (alreadyOpenedByViewer) "Contenu expiré après consultation" else "Appuyer pour afficher une seule fois",
                    color = DiscordTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ViewOnceFullScreenDialog(
    imageUrl: String,
    senderName: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Média vue unique de $senderName",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0xAA000000), shape = CircleShape) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.LooksOne, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                        Text("Vue unique • $senderName", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clip(CircleShape).background(Color(0xAA000000))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun EphemeralTimerDialog(
    currentDurationSec: Int,
    onSelectDuration: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = DiscordBlurple)
                Text("Messages éphémères", color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Les nouveaux messages envoyés dans cette discussion seront supprimés automatiquement à l'expiration.",
                    color = DiscordTextMuted,
                    fontSize = 12.sp
                )
                EphemeralDurationOption.entries.forEach { option ->
                    val selected = currentDurationSec == option.seconds
                    Surface(
                        color = if (selected) DiscordBlurple.copy(alpha = 0.2f) else DiscordDarkInput,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selected) DiscordBlurple else DiscordDarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectDuration(option.seconds)
                                onDismiss()
                            }
                            .testTag("ephemeral_opt_${option.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(option.label, color = DiscordTextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer", color = DiscordTextMuted) }
        }
    )
}

@Composable
fun MessageForwardSheet(
    message: Message,
    conversations: List<DirectConversation>,
    servers: List<Server>,
    onConfirmForward: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    val allTargets = remember(conversations, servers) {
        buildList {
            conversations.forEach { c ->
                add(c.id to (c.groupName ?: c.friend.name) to "MP")
            }
            servers.filter { !it.isDmTab }.forEach { srv ->
                srv.channels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.ANNOUNCEMENT }.forEach { ch ->
                    add(ch.id to "#${ch.name}" to srv.name)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = DiscordBlurple)
                Text("Transférer vers...", color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                Text(
                    text = message.text.ifBlank { message.attachmentName ?: "Média partagé" },
                    color = DiscordTextMuted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allTargets, key = { it.first.first }) { (idAndTitle, subtitle) ->
                        val (id, title) = idAndTitle
                        val checked = id in selectedIds
                        Surface(
                            color = if (checked) DiscordBlurple.copy(alpha = 0.2f) else DiscordDarkInput,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedIds.remove(id) else selectedIds.add(id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(title, color = DiscordTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, color = DiscordTextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmForward(selectedIds.toList())
                    onDismiss()
                },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("confirm_forward_btn")
            ) {
                Text("Transférer (${selectedIds.size})", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = DiscordTextMuted) }
        }
    )
}

@Composable
fun MessageReadReceiptsDialog(
    message: Message,
    knownMembers: List<User>,
    onDismiss: () -> Unit
) {
    val readers = remember(message.readByIds, knownMembers) {
        message.readByIds.mapNotNull { uid ->
            knownMembers.find { it.id == uid } ?: if (uid == message.sender.id) message.sender else null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF38BDF8))
                Text("Accusés de lecture", color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${readers.size} membre(s) ont consulté ce message (${message.timestamp}).",
                    color = DiscordTextMuted,
                    fontSize = 12.sp
                )
                if (readers.isEmpty()) {
                    Text("Envoyé — en attente de lecture.", color = DiscordTextLight, fontSize = 13.sp)
                } else {
                    readers.forEach { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(user.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(user.name, color = DiscordTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", color = DiscordBlurple) }
        }
    )
}
