package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Channel
import com.example.ui.theme.*
import com.example.util.VoiceConnectionPhase
import com.example.util.WebRtcSessionState

@Composable
fun FloatingVoiceBar(
    channel: Channel,
    isMuted: Boolean,
    rtcSessionState: WebRtcSessionState = WebRtcSessionState(),
    onBarClick: () -> Unit,
    onToggleMute: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (rtcSessionState.connectionPhase) {
        VoiceConnectionPhase.NETWORK_LOST, VoiceConnectionPhase.FAILED -> DiscordRed
        VoiceConnectionPhase.RECONNECTING, VoiceConnectionPhase.CONNECTING -> DiscordAmber
        else -> DiscordGreen
    }
    val statusTitle = when (rtcSessionState.connectionPhase) {
        VoiceConnectionPhase.NETWORK_LOST -> "Réseau perdu • Reconnexion en attente"
        VoiceConnectionPhase.RECONNECTING -> "Reconnexion (${rtcSessionState.reconnectAttempt}/${rtcSessionState.maxReconnectAttempts})..."
        VoiceConnectionPhase.FAILED -> "Connexion instable"
        else -> "Voix connectée • ${rtcSessionState.networkTypeLabel}"
    }
    val countSuffix = if (channel.maxParticipants > 0) {
        "${channel.activeVoiceUsers.size}/${channel.maxParticipants}"
    } else {
        "${channel.activeVoiceUsers.size} part."
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBarClick)
            .testTag("floating_voice_bar"),
        color = DiscordDarkCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (rtcSessionState.connectionPhase == VoiceConnectionPhase.NETWORK_LOST ||
                            rtcSessionState.connectionPhase == VoiceConnectionPhase.RECONNECTING
                        ) {
                            Icons.Default.SyncProblem
                        } else {
                            Icons.Default.VolumeUp
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusTitle,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.isLocked) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Verrouillé",
                                tint = DiscordAmber,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    Text(
                        text = "${channel.name} • $countSuffix • ${rtcSessionState.pingMs}ms",
                        color = DiscordTextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(40.dp).testTag("floating_voice_mute")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Muet",
                        tint = if (isMuted) DiscordRed else DiscordTextLight,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DiscordRed.copy(alpha = 0.2f))
                        .testTag("floating_voice_disconnect")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Déconnecter",
                        tint = DiscordRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
