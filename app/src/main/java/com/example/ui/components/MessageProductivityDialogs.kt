package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.*

@Composable
fun MessageReminderDialog(
    message: Message,
    onScheduleReminder: (delayMs: Long, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val delays = listOf(
        Triple(4_000L, "Dans 5 sec (Démo)", "Notification rapide de démonstration"),
        Triple(15 * 60_000L, "Dans 15 min", "Rappel court terme"),
        Triple(60 * 60_000L, "Dans 1 heure", "Pour plus tard")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = DiscordAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Me rappeler ce message", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = palette.inputBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${message.sender.name} : ${message.text.ifBlank { "Média" }}",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                delays.forEach { (ms, label, sub) ->
                    Surface(
                        color = palette.sidebarBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onScheduleReminder(ms, label)
                                onDismiss()
                            }
                            .testTag("reminder_delay_${ms}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(label, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(sub, color = palette.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}

@Composable
fun ScheduleMessageDialog(
    initialText: String,
    targetLabel: String,
    onSchedule: (text: String, delayMs: Long, delayLabel: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var messageText by remember { mutableStateOf(initialText) }
    var selectedDelay by remember { mutableStateOf(5_000L to "Dans 5 sec") }

    val options = listOf(
        5_000L to "Dans 5 sec",
        60_000L to "Dans 1 min",
        15 * 60_000L to "Dans 15 min",
        60 * 60_000L to "Dans 1 heure"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ScheduleSend, contentDescription = null, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Programmer l'envoi", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Destination : $targetLabel", color = palette.textMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Contenu du message programmé...", color = palette.textMuted) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_msg_input")
                )
                Text("DÉLAI D'ENVOI", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                options.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { opt ->
                            val isSel = selectedDelay.first == opt.first
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedDelay = opt },
                                label = { Text(opt.second, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).testTag("schedule_delay_${opt.first}")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSchedule(messageText, selectedDelay.first, selectedDelay.second)
                        onDismiss()
                    }
                },
                enabled = messageText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.testTag("schedule_msg_confirm")
            ) {
                Text("Programmer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}
