package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Message
import com.example.ui.theme.*
import com.example.util.AudioPlayerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSheet(
    parentMessage: Message,
    replies: List<Message>,
    audioPlayer: AudioPlayerHelper,
    currentUserId: String,
    onSendReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var replyText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("thread_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 600.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = null,
                    tint = DiscordBlurple,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fil de discussion",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${replies.size} réponse${if (replies.size > 1) "s" else ""}",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = palette.border)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                item {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = parentMessage.sender.name,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = parentMessage.timestamp,
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = parentMessage.text.ifBlank { "Pièce jointe" },
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    HorizontalDivider(
                        color = palette.border,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp)
                    )
                }

                if (replies.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucune réponse dans ce fil pour le moment. Lancez la discussion !",
                                color = palette.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(replies, key = { it.id }) { reply ->
                        MessageItem(
                            message = reply,
                            audioPlayer = audioPlayer,
                            currentUserId = currentUserId,
                            onLongClick = {},
                            onToggleReaction = {},
                            onAddReactionClick = {},
                            onReplyClick = {}
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Répondre dans le fil...", color = palette.textMuted, fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("thread_reply_input"),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            onSendReply(replyText)
                            replyText = ""
                        }
                    },
                    enabled = replyText.isNotBlank(),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (replyText.isNotBlank()) DiscordBlurple else palette.inputBg)
                        .testTag("thread_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Envoyer dans le fil",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditMessageDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var editedText by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Text(
                text = "Modifier le message",
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_message_input"),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputBg,
                    unfocusedContainerColor = palette.inputBg,
                    focusedBorderColor = DiscordBlurple,
                    unfocusedBorderColor = palette.border,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textSecondary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editedText.isNotBlank()) {
                        onSave(editedText.trim())
                    }
                },
                enabled = editedText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("edit_message_save_button")
            ) {
                Text("Enregistrer", color = Color.White)
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
fun RichMessageText(
    text: String,
    isEdited: Boolean,
    textColor: Color,
    mutedColor: Color
) {
    val mentionRegex = remember { Regex("(@\\w+|https?://\\S+)") }
    val annotated = remember(text, isEdited, textColor, mutedColor) {
        buildAnnotatedString {
            var lastIdx = 0
            mentionRegex.findAll(text).forEach { match ->
                if (match.range.first > lastIdx) {
                    append(text.substring(lastIdx, match.range.first))
                }
                val token = match.value
                val start = length
                append(token)
                val end = length
                if (token.startsWith("@")) {
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFFC9CDFB),
                            background = DiscordBlurple.copy(alpha = 0.35f),
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = start,
                        end = end
                    )
                } else {
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFF00A8FC),
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < text.length) {
                append(text.substring(lastIdx))
            }
            if (isEdited) {
                val editStart = length
                append(" (modifié)")
                addStyle(
                    style = SpanStyle(color = mutedColor, fontSize = 11.sp),
                    start = editStart,
                    end = length
                )
            }
        }
    }

    Text(
        text = annotated,
        color = textColor,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
}

@Composable
fun LinkPreviewCard(text: String) {
    val url = remember(text) {
        Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', ']')
    } ?: return
    val parsedUri = remember(url) {
        runCatching { android.net.Uri.parse(url) }.getOrNull()
    }
    val isValidWebUrl = remember(parsedUri) {
        val scheme = parsedUri?.scheme?.lowercase()
        val host = parsedUri?.host
        (scheme == "https" || scheme == "http") &&
            !host.isNullOrBlank() &&
            parsedUri.userInfo.isNullOrBlank()
    }
    if (!isValidWebUrl) return

    val palette = ravenPalette()
    val uriHandler = LocalUriHandler.current
    var showInsecureConfirm by remember(url) { mutableStateOf(false) }
    val host = remember(parsedUri) { parsedUri?.host.orEmpty() }
    val siteTitle = remember(host) {
        when {
            host.contains("github", ignoreCase = true) -> "GitHub • Dépôt & Code"
            host.contains("discord", ignoreCase = true) -> "Discord • Lien communautaire"
            host.contains("youtube", ignoreCase = true) || host.contains("youtu.be", ignoreCase = true) -> "YouTube • Vidéo"
            else -> host.replaceFirstChar { it.uppercase() }
        }
    }

    if (showInsecureConfirm) {
        AlertDialog(
            onDismissRequest = { showInsecureConfirm = false },
            containerColor = palette.cardBg,
            title = { Text("Lien non chiffré (HTTP)", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Ce lien utilise HTTP sans chiffrement ($host). Voulez-vous vraiment l'ouvrir ?",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInsecureConfirm = false
                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                ) {
                    Text("Ouvrir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsecureConfirm = false }) {
                    Text("Annuler", color = palette.textMuted)
                }
            }
        )
    }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (parsedUri?.scheme.equals("https", ignoreCase = true)) {
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {}
                } else {
                    showInsecureConfirm = true
                }
            }
            .testTag("link_preview_card")
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(DiscordBlurple)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DiscordBlurple.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = DiscordBlurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = siteTitle,
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = url,
                        color = Color(0xFF00A8FC),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Ouvrir le lien",
                    tint = palette.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val heights = remember { listOf(0.35f, 0.75f, 0.5f, 0.95f, 0.6f, 0.85f, 0.4f, 0.9f, 0.55f, 0.7f, 0.45f, 0.8f) }
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wavePhase"
    )

    Row(
        modifier = modifier.height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { index, base ->
            val factor = if (isPlaying) {
                val shift = if (index % 2 == 0) phase else (1f - phase)
                (base * 0.6f + shift * 0.4f).coerceIn(0.25f, 1f)
            } else {
                base * 0.75f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(factor)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isPlaying) DiscordBlurple else DiscordTextMuted)
            )
        }
    }
}
