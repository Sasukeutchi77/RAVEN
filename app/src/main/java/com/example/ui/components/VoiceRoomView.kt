package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
import com.example.model.CustomRole
import com.example.model.User
import com.example.model.UserRole
import com.example.model.VoiceJukeboxState
import com.example.ui.theme.*
import com.example.util.VoiceConnectionPhase
import com.example.util.WebRtcSessionState
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

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
    localVideoTrack: VideoTrack? = null,
    remoteVideoTracks: Map<String, VideoTrack> = emptyMap(),
    eglContext: EglBase.Context? = null,
    serverCustomRoles: List<CustomRole> = emptyList(),
    canManageVoice: Boolean = false,
    canSpeak: Boolean = true,
    canStreamVideo: Boolean = true,
    canShareScreen: Boolean = true,
    participantVolumes: Map<String, Float> = emptyMap(),
    onCameraToggle: (Boolean, Boolean) -> Unit = { _, _ -> },
    onScreenShareToggle: (Boolean) -> Unit = {},
    onStartScreenShareWithIntent: (Int, Intent?) -> Unit = { _, _ -> },
    onStopScreenShare: () -> Unit = {},
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleSpeakerphone: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onLeaveVoice: () -> Unit,
    onMinimize: () -> Unit,
    onInviteClick: () -> Unit = {},
    onOpenJukebox: () -> Unit = {},
    onOpenMixer: () -> Unit = {},
    onOpenWhiteboard: () -> Unit = {},
    onParticipantVolumeChange: (String, Float) -> Unit = { _, _ -> },
    onKickParticipant: (String) -> Unit = {},
    onUpdateVoiceSettings: (isLocked: Boolean, maxParticipants: Int, allowedRoles: List<UserRole>, allowedRoleIds: List<String>) -> Unit = { _, _, _, _ -> },
    onRewardXpAndOrbs: (xp: Int, orbs: Int, reason: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val context = LocalContext.current

    // Persist camera/screenshare state via rtcSessionState across rotation when connected
    val isCameraOn = rtcSessionState.isCameraStreaming
    val useFrontCamera = rtcSessionState.useFrontCamera
    val isScreenSharing = rtcSessionState.isScreenSharing

    var showActivitiesSheetTab by remember { mutableStateOf<Int?>(null) }
    var selectedParticipantForControl by remember { mutableStateOf<User?>(null) }
    var showChannelAccessDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onCameraToggle(true, useFrontCamera)
        }
    }

    val screenProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data != null) {
            onStartScreenShareWithIntent(result.resultCode, result.data)
        }
    }

    val phaseColor = when (rtcSessionState.connectionPhase) {
        VoiceConnectionPhase.NETWORK_LOST, VoiceConnectionPhase.FAILED -> DiscordRed
        VoiceConnectionPhase.RECONNECTING, VoiceConnectionPhase.CONNECTING -> DiscordAmber
        else -> DiscordGreen
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
                                imageVector = if (channel.isLocked) Icons.Default.Lock else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = if (channel.isLocked) DiscordAmber else phaseColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = channel.name,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (channel.maxParticipants > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${channel.activeVoiceUsers.size}/${channel.maxParticipants}",
                                        color = palette.textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$serverName • ${rtcSessionState.iceStatusLabel} • ${rtcSessionState.networkTypeLabel} (${rtcSessionState.pingMs}ms)",
                            color = phaseColor,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    IconButton(
                        onClick = onToggleSpeakerphone,
                        modifier = Modifier.testTag("voice_speaker_button")
                    ) {
                        Icon(
                            imageVector = if (rtcSessionState.isSpeakerphoneOn) Icons.Default.VolumeUp else Icons.Default.PhoneInTalk,
                            contentDescription = if (rtcSessionState.isSpeakerphoneOn) "Haut-parleur actif" else "Écouteur",
                            tint = if (rtcSessionState.isSpeakerphoneOn) DiscordGreen else palette.textSecondary
                        )
                    }
                    if (canManageVoice) {
                        IconButton(
                            onClick = { showChannelAccessDialog = true },
                            modifier = Modifier.testTag("voice_channel_settings_button")
                        ) {
                            Icon(
                                imageVector = if (channel.isLocked) Icons.Default.Lock else Icons.Default.Tune,
                                contentDescription = "Paramètres & Verrouillage",
                                tint = if (channel.isLocked) DiscordAmber else palette.textSecondary
                            )
                        }
                    }
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
                        label = if (!canSpeak) "Restreint" else if (isUserMuted) "Muet" else "Micro",
                        isActive = isUserMuted,
                        activeColor = DiscordRed,
                        enabled = canSpeak,
                        onClick = onToggleMute,
                        testTag = "voice_control_mute"
                    )

                    VoiceControlButton(
                        icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        label = if (rtcSessionState.cameraPausedInBackground) "En pause" else "Vidéo",
                        isActive = isCameraOn,
                        activeColor = palette.accent,
                        enabled = canStreamVideo,
                        onClick = {
                            if (isCameraOn) {
                                onCameraToggle(false, useFrontCamera)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
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
                        enabled = canShareScreen,
                        onClick = {
                            if (isScreenSharing) {
                                onStopScreenShare()
                                onScreenShareToggle(false)
                            } else {
                                val mpm = try {
                                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                } catch (_: Throwable) {
                                    null
                                }
                                if (mpm != null) {
                                    try {
                                        screenProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                                    } catch (_: Throwable) {
                                        onScreenShareToggle(true)
                                    }
                                } else {
                                    onScreenShareToggle(true)
                                }
                            }
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
            // Reconnection / Network-loss recovery banner
            if (rtcSessionState.connectionPhase == VoiceConnectionPhase.RECONNECTING ||
                rtcSessionState.connectionPhase == VoiceConnectionPhase.NETWORK_LOST ||
                rtcSessionState.connectionPhase == VoiceConnectionPhase.FAILED
            ) {
                Surface(
                    color = phaseColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, phaseColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("voice_connection_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.WifiOff, contentDescription = null, tint = phaseColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rtcSessionState.iceStatusLabel,
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        }
                        if (rtcSessionState.networkAvailable) {
                            TextButton(
                                onClick = onReconnect,
                                modifier = Modifier.testTag("voice_reconnect_button")
                            ) {
                                Text("Reconnecter", color = phaseColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (isScreenSharing) {
                Surface(
                    color = Color(0xFF111318),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(2.dp, DiscordGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(bottom = 12.dp)
                        .testTag("voice_screenshare_preview")
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        ) {
                            Surface(color = DiscordRed, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "PARTAGE D'ÉCRAN EN DIRECT",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            TextButton(onClick = {
                                onStopScreenShare()
                                onScreenShareToggle(false)
                            }) {
                                Text("Arrêter", color = DiscordRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Diffusion active vers ${channel.activeVoiceUsers.size} membre(s)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Mini Jukebox Card
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participants (${channel.activeVoiceUsers.size}${if (channel.maxParticipants > 0) "/${channel.maxParticipants}" else ""})",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Appuyez sur un membre pour le volume",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
            }

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
                        !isMuted && !isDeafened && rtcSessionState.localMicSpeaking
                    } else {
                        !isMuted && !isDeafened && user.isSpeaking
                    }
                    val remotePeer = rtcSessionState.remotePeers.find { it.uid == user.id }
                    val vol = participantVolumes[user.id] ?: 1f

                    VoiceParticipantCard(
                        user = user,
                        isMe = isMe,
                        isSpeaking = isSpeaking,
                        isMuted = isMuted,
                        isDeafened = isDeafened,
                        isCameraOn = if (isMe) isCameraOn else (remotePeer?.isCameraOn == true),
                        isRemoteScreenSharing = !isMe && (remotePeer?.isScreenSharing == true),
                        volumePercent = if (isMe) null else (vol * 100).roundToInt(),
                        peerIceState = rtcSessionState.peerConnectionStates[user.id] ?: remotePeer?.connectionState,
                        localVideoTrack = if (isMe) localVideoTrack else null,
                        remoteVideoTrack = if (!isMe) remoteVideoTracks[user.id] else null,
                        eglContext = eglContext,
                        onFlipCamera = {
                            if (isCameraOn) onCameraToggle(true, !useFrontCamera)
                        },
                        onCardClick = {
                            if (!isMe) {
                                selectedParticipantForControl = user
                            }
                        }
                    )
                }
            }
        }
    }

    selectedParticipantForControl?.let { target ->
        ParticipantVoiceControlDialog(
            participant = target,
            currentVolume = participantVolumes[target.id] ?: 1f,
            canModerateVoice = canManageVoice && target.id != currentUser.id,
            connectionStateLabel = rtcSessionState.peerConnectionStates[target.id],
            onVolumeChange = { vol -> onParticipantVolumeChange(target.id, vol) },
            onKickFromVoice = { onKickParticipant(target.id) },
            onDismiss = { selectedParticipantForControl = null }
        )
    }

    if (showChannelAccessDialog) {
        VoiceChannelAccessDialog(
            channel = channel,
            serverCustomRoles = serverCustomRoles,
            onSave = onUpdateVoiceSettings,
            onDismiss = { showChannelAccessDialog = false }
        )
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
    isRemoteScreenSharing: Boolean = false,
    volumePercent: Int? = null,
    peerIceState: String? = null,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    onFlipCamera: () -> Unit = {},
    onCardClick: () -> Unit = {}
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

    val activeTrack = if (isMe) localVideoTrack else remoteVideoTrack
    val hasHardwareVideo = activeTrack != null && eglContext != null && (isCameraOn || isRemoteScreenSharing)
    val hasActiveVideo = isCameraOn || isRemoteScreenSharing || hasHardwareVideo

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(166.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isMe, onClick = onCardClick)
            .testTag("voice_participant_card_${user.id}"),
        color = if (hasActiveVideo) Color(0xFF172038) else palette.sidebarBg,
        shape = RoundedCornerShape(12.dp),
        border = when {
            isSpeaking && !isMuted -> BorderStroke(pulseBorder.dp, DiscordGreen)
            isRemoteScreenSharing -> BorderStroke(2.dp, DiscordGreen)
            hasActiveVideo -> BorderStroke(2.dp, palette.accent)
            else -> BorderStroke(1.dp, palette.border)
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (activeTrack != null && eglContext != null && (isCameraOn || isRemoteScreenSharing)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(eglContext, null)
                                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                setEnableHardwareScaler(true)
                                setMirror(isMe)
                                activeTrack.addSink(this)
                            }
                        },
                        onRelease = { renderer ->
                            try {
                                activeTrack.removeSink(renderer)
                                renderer.release()
                            } catch (_: Throwable) {
                            }
                        },
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                    if (isMe && isCameraOn) {
                        IconButton(
                            onClick = onFlipCamera,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xAA000000))
                                .testTag("camera_flip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Inverser la caméra",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else if (isMe && isCameraOn) {
                // Graceful fallback when running without hardware GL surface (e.g., unit tests/emulator)
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E1320))) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Caméra WebRTC active", tint = palette.accent, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Caméra WebRTC active", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(
                        onClick = onFlipCamera,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA000000))
                            .testTag("camera_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Inverser la caméra",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
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
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isMe) "${user.name} (Vous)" else user.name,
                        color = if (isSpeaking && !isMuted) DiscordGreen else palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Top-left status badges (connection & individual volume)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMe && peerIceState != null) {
                    val dotColor = when (peerIceState) {
                        "CONNECTED", "COMPLETED" -> DiscordGreen
                        "CHECKING", "RECONNECTING" -> DiscordAmber
                        else -> DiscordRed
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                if (volumePercent != null && volumePercent != 100) {
                    Surface(
                        color = Color(0x99000000),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$volumePercent%",
                            color = if (volumePercent == 0) DiscordRed else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Status icons in top right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isRemoteScreenSharing) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DiscordGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ScreenShare, contentDescription = "Partage écran", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                if (isCameraOn) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(DiscordBlurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Caméra active", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                if (isMuted) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MicOff, contentDescription = "Muet", tint = DiscordRed, modifier = Modifier.size(14.dp))
                    }
                }
                if (isDeafened) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.HeadsetOff, contentDescription = "Sourdine", tint = DiscordRed, modifier = Modifier.size(14.dp))
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
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> DiscordDarkInput.copy(alpha = 0.4f)
                        isActive -> activeColor
                        else -> DiscordDarkInput
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> DiscordTextMuted.copy(alpha = 0.5f)
                    isActive -> Color.White
                    else -> DiscordTextLight
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (!enabled) DiscordTextMuted.copy(alpha = 0.5f) else if (isActive) activeColor else DiscordTextMuted,
            fontSize = 11.sp
        )
    }
}
