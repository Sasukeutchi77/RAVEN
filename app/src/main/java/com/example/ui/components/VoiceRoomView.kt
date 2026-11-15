package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.model.Channel
import com.example.model.User
import com.example.model.VoiceJukeboxState
import com.example.ui.theme.*
import com.example.util.WebRtcSessionState
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomView(
    channel: Channel,
    serverName: String,
    currentUser: User,
    isUserMuted: Boolean,
    isUserDeafened: Boolean,
    jukeboxState: VoiceJukeboxState = VoiceJukeboxState(),
    rtcSessionState: WebRtcSessionState = WebRtcSessionState(),
    remoteVideoTracks: Map<String, VideoTrack> = emptyMap(),
    eglContext: EglBase.Context? = null,
    onCameraToggle: (Boolean, Boolean) -> Unit = { _, _ -> },
    onScreenShareToggle: (Boolean) -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onLeaveVoice: () -> Unit,
    onMinimize: () -> Unit,
    onInviteClick: () -> Unit = {},
    onOpenJukebox: () -> Unit = {},
    onOpenMixer: () -> Unit = {},
    onOpenWhiteboard: () -> Unit = {},
    onRewardXpAndOrbs: (xp: Int, orbs: Int, reason: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    var isCameraOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isScreenSharing by remember { mutableStateOf(false) }
    var showActivitiesSheetTab by remember { mutableStateOf<Int?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isCameraOn = true
            onCameraToggle(true, useFrontCamera)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.cardBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = DiscordGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = channel.name,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$serverName • ${rtcSessionState.iceStatusLabel} (${rtcSessionState.pingMs}ms)",
                            color = DiscordGreen,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.testTag("voice_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Réduire",
                            tint = palette.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onInviteClick, modifier = Modifier.testTag("voice_invite_button")) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Inviter",
                            tint = palette.textSecondary
                        )
                    }
                    IconButton(onClick = onToggleDeafen, modifier = Modifier.testTag("voice_header_deafen")) {
                        Icon(
                            imageVector = if (isUserDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                            contentDescription = "Sourdine",
                            tint = if (isUserDeafened) DiscordRed else palette.textSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.sidebarBg
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                shape = RoundedCornerShape(24.dp),
                color = palette.sidebarBg,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VoiceControlButton(
                        icon = if (isUserMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isUserMuted) "Muet" else "Micro",
                        isActive = isUserMuted,
                        activeColor = DiscordRed,
                        onClick = onToggleMute,
                        testTag = "voice_control_mute"
                    )

                    VoiceControlButton(
                        icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = "Vidéo",
                        isActive = isCameraOn,
                        activeColor = palette.accent,
                        onClick = {
                            if (isCameraOn) {
                                isCameraOn = false
                                onCameraToggle(false, useFrontCamera)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    isCameraOn = true
                                    onCameraToggle(true, useFrontCamera)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                        testTag = "voice_control_camera"
                    )

                    VoiceControlButton(
                        icon = if (isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                        label = "Écran",
                        isActive = isScreenSharing,
                        activeColor = DiscordGreen,
                        onClick = {
                            isScreenSharing = !isScreenSharing
                            onScreenShareToggle(isScreenSharing)
                        },
                        testTag = "voice_control_screenshare"
                    )

                    VoiceControlButton(
                        icon = Icons.Default.SurroundSound,
                        label = "Sons",
                        isActive = showActivitiesSheetTab == 0,
                        activeColor = DiscordBlurple,
                        onClick = { showActivitiesSheetTab = 0 },
                        testTag = "voice_control_soundboard"
                    )

                    VoiceControlButton(
                        icon = Icons.Default.SportsEsports,
                        label = "Jeux",
                        isActive = showActivitiesSheetTab == 1,
                        activeColor = DiscordBlurple,
                        onClick = { showActivitiesSheetTab = 1 },
                        testTag = "voice_control_activities"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DiscordRed)
                            .clickable(onClick = onLeaveVoice)
                            .testTag("voice_control_disconnect"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Déconnexion",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isScreenSharing) {
                Surface(
                    color = Color(0xFF111318),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, DiscordGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(bottom = 12.dp)
                        .testTag("voice_screenshare_preview")
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Surface(color = DiscordRed, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "EN DIRECT • 1080p 60FPS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(34.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Vous partagez votre écran avec ${channel.activeVoiceUsers.size} membres",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Mini Jukebox & Quick Collaborative Pills
            jukeboxState.currentTrack?.let { track ->
                Surface(
                    color = palette.sidebarBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(track.accentHex).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenJukebox)
                        .testTag("voice_jukebox_mini_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Jukebox",
                            tint = Color(track.accentHex),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (jukeboxState.isPlaying) "${track.artist} • En lecture" else "${track.artist} • En pause",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        AnimatedAudioVisualizer(
                            isPlaying = jukeboxState.isPlaying,
                            accentColor = Color(track.accentHex),
                            modifier = Modifier.width(64.dp).height(22.dp)
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                item {
                    AssistChip(
                        onClick = onOpenJukebox,
                        label = { Text("Jukebox", color = palette.textPrimary, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFEB459E), modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("voice_open_jukebox_chip")
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenMixer,
                        label = { Text("Mixeur & EQ", color = palette.textPrimary, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("voice_open_mixer_chip")
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenWhiteboard,
                        label = { Text("Tableau Blanc", color = palette.textPrimary, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("voice_open_whiteboard_chip")
                    )
                }
            }

            Text(
                text = "Participants dans le salon (${channel.activeVoiceUsers.size})",
                color = palette.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(channel.activeVoiceUsers, key = { it.id }) { user ->
                    val isMe = user.id == currentUser.id
                    val isMuted = if (isMe) isUserMuted else user.isMuted
                    val isDeafened = if (isMe) isUserDeafened else user.isDeafened
                    val isSpeaking = if (isMe) {
                        !isMuted && (rtcSessionState.localMicSpeaking || rtcSessionState.localMicLevel == 0f)
                    } else {
                        user.isSpeaking
                    }

                    VoiceParticipantCard(
                        user = user,
                        isMe = isMe,
                        isSpeaking = isSpeaking,
                        isMuted = isMuted,
                        isDeafened = isDeafened,
                        isCameraOn = isMe && isCameraOn,
                        remoteVideoTrack = if (!isMe) remoteVideoTracks[user.id] else null,
                        eglContext = eglContext,
                        useFrontCamera = useFrontCamera,
                        onFlipCamera = {
                            useFrontCamera = !useFrontCamera
                            if (isCameraOn) onCameraToggle(true, useFrontCamera)
                        }
                    )
                }
            }
        }
    }

    if (showActivitiesSheetTab != null) {
        VoiceActivitiesSheet(
            initialTab = showActivitiesSheetTab!!,
            onRewardXpAndOrbs = onRewardXpAndOrbs,
            onDismiss = { showActivitiesSheetTab = null }
        )
    }
}

@Composable
fun VoiceParticipantCard(
    user: User,
    isMe: Boolean,
    isSpeaking: Boolean,
    isMuted: Boolean,
    isDeafened: Boolean,
    isCameraOn: Boolean = false,
    remoteVideoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    useFrontCamera: Boolean = true,
    onFlipCamera: () -> Unit = {}
) {
    val palette = ravenPalette()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseBorder by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseBorder"
    )

    val hasActiveVideo = isCameraOn || (remoteVideoTrack != null && eglContext != null)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        color = if (hasActiveVideo) Color(0xFF172038) else palette.sidebarBg,
        shape = RoundedCornerShape(12.dp),
        border = when {
            isSpeaking && !isMuted -> BorderStroke(pulseBorder.dp, DiscordGreen)
            hasActiveVideo -> BorderStroke(2.dp, palette.accent)
            else -> BorderStroke(1.dp, palette.border)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isCameraOn && isMe) {
                CameraXPreviewBox(
                    useFrontCamera = useFrontCamera,
                    onFlipCamera = onFlipCamera,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (remoteVideoTrack != null && eglContext != null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setEnableHardwareScaler(true)
                            remoteVideoTrack.addSink(this)
                        }
                    },
                    onRelease = { renderer ->
                        try {
                            remoteVideoTrack.removeSink(renderer)
                            renderer.release()
                        } catch (_: Throwable) {
                        }
                    },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(user.avatarColor))
                            .then(
                                if (isSpeaking && !isMuted) {
                                    Modifier.border(3.dp, DiscordGreen, CircleShape)
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.name.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (isMe) "${user.name} (Vous)" else user.name,
                            color = if (isSpeaking && !isMuted) DiscordGreen else palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Status icons in top corners
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isCameraOn) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DiscordBlurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Caméra active",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (isMuted) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = "Muet",
                            tint = DiscordRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (isDeafened) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HeadsetOff,
                            contentDescription = "Sourdine",
                            tint = DiscordRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Speaking indicator wave
            if (isSpeaking && !isMuted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DiscordGreen.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    AnimatedAudioVisualizer(
                        isPlaying = true,
                        accentColor = DiscordGreen,
                        modifier = Modifier.width(24.dp).height(10.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCameraOn) "Vidéo & Voix" else "Parle...",
                        color = DiscordGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else DiscordDarkInput),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else DiscordTextLight,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else DiscordTextMuted,
            fontSize = 11.sp
        )
    }
}
