package com.example.ui.components

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerDialog(
    groups: List<StoryAuthorGroup>,
    initialAuthorId: String,
    currentUserId: String,
    isModerator: Boolean,
    loadedViewers: List<StoryViewerEntry>,
    onMarkStoryViewed: (String) -> Unit,
    onLoadViewers: (String) -> Unit,
    onDeleteStory: (String) -> Unit,
    onReportStory: (storyId: String, StoryReportReason, String) -> Unit,
    onModerateHideStory: (storyId: String, reason: String) -> Unit,
    onReplyToStory: (storyId: String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val startGroupIndex = remember(groups, initialAuthorId) {
        groups.indexOfFirst { it.authorId == initialAuthorId }.coerceAtLeast(0)
    }
    var groupIndex by remember { mutableStateOf(startGroupIndex) }
    val currentGroup = groups.getOrNull(groupIndex)

    if (currentGroup == null || currentGroup.stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var storyIndex by remember(groupIndex) {
        val firstUnseen = currentGroup.stories.indexOfFirst { !it.hasBeenViewedBy(currentUserId) }
        mutableStateOf(if (firstUnseen >= 0 && !currentGroup.isCurrentUser) firstUnseen else 0)
    }
    val safeStoryIndex = storyIndex.coerceIn(0, currentGroup.stories.lastIndex)
    val activeStory = currentGroup.stories[safeStoryIndex]

    var isPaused by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }
    var progressFraction by remember(activeStory.id) { mutableStateOf(0f) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var replyText by remember(activeStory.id) { mutableStateOf("") }

    val goNext: () -> Unit = {
        if (safeStoryIndex < currentGroup.stories.lastIndex) {
            storyIndex = safeStoryIndex + 1
        } else if (groupIndex < groups.lastIndex) {
            groupIndex++
            storyIndex = 0
        } else {
            onDismiss()
        }
    }

    val goPrevious: () -> Unit = {
        if (safeStoryIndex > 0) {
            storyIndex = safeStoryIndex - 1
        } else if (groupIndex > 0) {
            groupIndex--
            storyIndex = (groups[groupIndex].stories.lastIndex).coerceAtLeast(0)
        }
    }

    LaunchedEffect(activeStory.id) {
        onMarkStoryViewed(activeStory.id)
    }

    // Timer for Text/Image stories (6s)
    LaunchedEffect(activeStory.id, isPaused, showViewersSheet, showReportDialog) {
        if (activeStory.mediaType != StoryMediaType.VIDEO) {
            val totalTicks = 60
            var tick = (progressFraction * totalTicks).toInt()
            while (tick < totalTicks) {
                if (!isPaused && !showViewersSheet && !showReportDialog) {
                    delay(100L)
                    tick++
                    progressFraction = tick / totalTicks.toFloat()
                } else {
                    delay(150L)
                }
            }
            goNext()
        }
    }

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
            // Story content surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeStory.id, safeStoryIndex, groupIndex) {
                        detectTapGestures(
                            onLongPress = { isPaused = true },
                            onPress = {
                                tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.32f) {
                                    goPrevious()
                                } else {
                                    goNext()
                                }
                            }
                        )
                    }
            ) {
                when (activeStory.mediaType) {
                    StoryMediaType.TEXT -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(activeStory.backgroundColorHex),
                                            Color(activeStory.secondaryColorHex)
                                        )
                                    )
                                )
                                .padding(horizontal = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeStory.textContent,
                                color = Color(activeStory.textColorHex),
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("story_viewer_text_content")
                            )
                        }
                    }

                    StoryMediaType.IMAGE -> {
                        AsyncImage(
                            model = activeStory.mediaUrl,
                            contentDescription = "Image story",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    StoryMediaType.VIDEO -> {
                        val context = LocalContext.current
                        val exoPlayer = remember(activeStory.id, activeStory.mediaUrl) {
                            ExoPlayer.Builder(context).build().apply {
                                activeStory.mediaUrl?.let { url ->
                                    setMediaItem(MediaItem.fromUri(url))
                                    prepare()
                                    playWhenReady = true
                                }
                            }
                        }

                        LaunchedEffect(isVideoMuted, exoPlayer) {
                            exoPlayer.volume = if (isVideoMuted) 0f else 1f
                        }

                        LaunchedEffect(isPaused, showViewersSheet, showReportDialog, exoPlayer) {
                            if (isPaused || showViewersSheet || showReportDialog) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }

                        LaunchedEffect(exoPlayer, activeStory.id) {
                            while (true) {
                                val dur = exoPlayer.duration
                                if (dur > 0L) {
                                    progressFraction = (exoPlayer.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                                }
                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                    goNext()
                                    break
                                }
                                delay(120L)
                            }
                        }

                        DisposableEffect(exoPlayer) {
                            onDispose {
                                exoPlayer.release()
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).also { tv ->
                                    exoPlayer.setVideoTextureView(tv)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Top overlay: segmented progress bars + author header + controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    currentGroup.stories.forEachIndexed { idx, _ ->
                        val barProgress = when {
                            idx < safeStoryIndex -> 1f
                            idx == safeStoryIndex -> progressFraction
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(currentGroup.authorAvatarColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!currentGroup.authorAvatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentGroup.authorAvatarUrl,
                                    contentDescription = currentGroup.authorName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = currentGroup.authorName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = currentGroup.authorName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = activeStory.visibility.label,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = activeStory.remainingTimeLabel(System.currentTimeMillis()),
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeStory.mediaType == StoryMediaType.VIDEO) {
                            IconButton(
                                onClick = { isVideoMuted = !isVideoMuted },
                                modifier = Modifier.testTag("story_viewer_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isVideoMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Audio vidéo",
                                    tint = Color.White
                                )
                            }
                        }
                        if (activeStory.authorId == currentUserId || isModerator) {
                            IconButton(
                                onClick = {
                                    onDeleteStory(activeStory.id)
                                    goNext()
                                },
                                modifier = Modifier.testTag("story_viewer_delete_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer cette story",
                                    tint = DiscordRed
                                )
                            }
                        }
                        if (activeStory.authorId != currentUserId) {
                            IconButton(
                                onClick = { showReportDialog = true },
                                modifier = Modifier.testTag("story_viewer_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Signaler cette story",
                                    tint = DiscordAmber
                                )
                            }
                        }
                        if (isModerator && activeStory.authorId != currentUserId) {
                            IconButton(
                                onClick = {
                                    onModerateHideStory(activeStory.id, "Masquée depuis le lecteur par un modérateur")
                                    goNext()
                                },
                                modifier = Modifier.testTag("story_viewer_mod_hide_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Masquer (Modération)",
                                    tint = DiscordRed
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("story_viewer_close_button")) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom overlay: Caption + Owner Views chip OR Reply input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeStory.caption.isNotBlank()) {
                    Text(
                        text = activeStory.caption,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (activeStory.authorId == currentUserId || isModerator) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                onLoadViewers(activeStory.id)
                                showViewersSheet = true
                            }
                            .testTag("story_viewer_views_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${activeStory.viewCount} vue(s) • Voir qui a regardé",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (activeStory.authorId != currentUserId) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = {
                                Text("Répondre à ${currentGroup.authorName}...", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                                focusedBorderColor = Color.White.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).testTag("story_reply_input")
                        )
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onReplyToStory(activeStory.id, replyText)
                                    replyText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(DiscordBlurple)
                                .testTag("story_reply_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer réponse", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showViewersSheet) {
        val displayViewers = remember(loadedViewers, activeStory.viewers) {
            if (loadedViewers.isNotEmpty()) loadedViewers else activeStory.viewers
        }
        ModalBottomSheet(
            onDismissRequest = { showViewersSheet = false },
            containerColor = DiscordDarkCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Vues (${maxOf(activeStory.viewCount, displayViewers.size)})",
                    color = DiscordTextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DiscordDarkBorder)
                Spacer(modifier = Modifier.height(8.dp))
                if (displayViewers.isEmpty()) {
                    Text(
                        text = "Personne n'a encore regardé cette story.",
                        color = DiscordTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(displayViewers, key = { it.viewerId }) { viewer ->
                            val timeLabel = remember(viewer.viewedAtMs) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(viewer.viewedAtMs))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(viewer.viewerAvatarColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = viewer.viewerName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(viewer.viewerName, color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("Vu à $timeLabel", color = DiscordTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        var chosenReason by remember { mutableStateOf(StoryReportReason.SPAM) }
        var reportDetails by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            containerColor = DiscordDarkCard,
            title = {
                Text("Signaler cette story", color = DiscordTextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryReportReason.entries.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosenReason = reason }
                        ) {
                            RadioButton(selected = chosenReason == reason, onClick = { chosenReason = reason })
                            Text(reason.label, color = DiscordTextLight, fontSize = 13.sp)
                        }
                    }
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it.take(500) },
                        label = { Text("Détails complémentaires", color = DiscordTextMuted, fontSize = 12.sp) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("story_report_details_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportStory(activeStory.id, chosenReason, reportDetails)
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                    modifier = Modifier.testTag("story_report_submit_button")
                ) {
                    Text("Signaler", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Annuler", color = DiscordTextMuted)
                }
            }
        )
    }
}
