package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.MockData
import com.example.model.AudioEqPreset
import com.example.model.MusicTrack
import com.example.model.User
import com.example.model.VoiceJukeboxState
import com.example.ui.theme.*

@Composable
fun CameraXPreviewBox(
    useFrontCamera: Boolean,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var cameraError by remember { mutableStateOf(false) }

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1320))) {
        if (hasPermission && !cameraError) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    try {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val selector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                            } catch (_: Exception) {
                                cameraError = true
                            }
                        }, ContextCompat.getMainExecutor(context))
                    } catch (_: Exception) {
                        cameraError = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Flux vidéo actif", tint = DiscordBlurple, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (!hasPermission) "Autorisez la caméra" else "Flux Caméra HD simulé",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Camera flip button
        IconButton(
            onClick = onFlipCamera,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xAA000000))
                .testTag("camera_flip_button")
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Inverser la caméra",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AnimatedAudioVisualizer(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jukebox_eq")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 18
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
        for (i in 0 until barCount) {
            val normalizedHeight = if (isPlaying) {
                val wave = kotlin.math.sin(phase + i * 0.65f) * 0.4f + 0.55f
                wave.coerceIn(0.18f, 1f)
            } else {
                0.15f
            }
            val barH = size.height * normalizedHeight
            drawRoundRect(
                color = if (i % 2 == 0) accentColor else accentColor.copy(alpha = 0.65f),
                topLeft = Offset(i * (barWidth + gap), size.height - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceJukeboxSheet(
    jukeboxState: VoiceJukeboxState,
    currentUserId: String,
    masterVolume: Float = 0.85f,
    onMasterVolumeChange: (Float) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSkipTrack: () -> Unit,
    onVoteSkip: () -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var customTrackTitle by remember { mutableStateOf("") }
    var customStreamUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("voice_jukebox_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 670.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFEB459E))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Jukebox Listen Together", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("ExoPlayer Media3 + Égaliseur temps réel", color = palette.textMuted, fontSize = 12.sp)
                }
            }

            // Now Playing Card
            val current = jukeboxState.currentTrack
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, (current?.let { Color(it.accentHex) } ?: palette.accent).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (current != null) {
                        val accent = Color(current.accentHex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(current.title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = if (!current.streamUrl.isNullOrBlank()) "${current.artist} • Flux audio HTTP" else "${current.artist} • Ajouté par ${current.addedBy}",
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = if (jukeboxState.isPlaying) "EN LECTURE" else "EN PAUSE",
                                    color = accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AnimatedAudioVisualizer(
                            isPlaying = jukeboxState.isPlaying,
                            accentColor = accent,
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val progress = (jukeboxState.elapsedSec.toFloat() / current.durationSec.coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accent,
                            trackColor = palette.inputBg,
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatSec(jukeboxState.elapsedSec), color = palette.textMuted, fontSize = 11.sp)
                            Text(formatSec(current.durationSec), color = palette.textMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Volume Jukebox", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                            Slider(
                                value = masterVolume,
                                onValueChange = onMasterVolumeChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                modifier = Modifier.weight(1f).testTag("jukebox_volume_slider")
                            )
                            Text("${(masterVolume * 100).toInt()}%", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onTogglePlayPause,
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                modifier = Modifier.weight(1f).testTag("jukebox_play_pause_button")
                            ) {
                                Icon(if (jukeboxState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (jukeboxState.isPlaying) "Pause" else "Lire", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onVoteSkip,
                                modifier = Modifier.testTag("jukebox_vote_skip_button")
                            ) {
                                val voted = currentUserId in current.voteSkipUserIds
                                Icon(Icons.Default.ThumbDown, contentDescription = "Voter passer", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (voted) "Voté (${current.voteSkipUserIds.size}/2)" else "Passer (${current.voteSkipUserIds.size}/2)", color = palette.textPrimary, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = onSkipTrack,
                                modifier = Modifier.clip(CircleShape).background(palette.inputBg).testTag("jukebox_skip_button")
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Titre suivant", tint = palette.textPrimary)
                            }
                        }
                    } else {
                        Text("Aucun morceau en cours. Ajoutez un titre ci-dessous !", color = palette.textMuted, fontSize = 13.sp)
                    }
                }
            }

            // Quick custom track + optional HTTP(S) stream URL input
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customTrackTitle,
                        onValueChange = { customTrackTitle = it },
                        placeholder = { Text("Titre personnalisé...", color = palette.textMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.inputBg,
                            unfocusedContainerColor = palette.inputBg,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textSecondary
                        ),
                        modifier = Modifier.weight(1f).testTag("jukebox_custom_input")
                    )
                    Button(
                        onClick = {
                            if (customTrackTitle.isNotBlank()) {
                                val cleanStream = customStreamUrl.trim().takeIf {
                                    it.startsWith("http://") || it.startsWith("https://")
                                }
                                onAddTrack(
                                    MusicTrack(
                                        id = "custom_${System.currentTimeMillis()}",
                                        title = customTrackTitle.trim(),
                                        artist = if (cleanStream != null) "Flux Web" else "Sélection Membre",
                                        durationSec = 180,
                                        addedBy = "",
                                        accentHex = 0xFF00A8FC,
                                        streamUrl = cleanStream
                                    )
                                )
                                customTrackTitle = ""
                                customStreamUrl = ""
                            }
                        },
                        enabled = customTrackTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier.testTag("jukebox_add_custom_button")
                    ) {
                        Text("Ajouter", color = Color.White, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = customStreamUrl,
                    onValueChange = { customStreamUrl = it },
                    placeholder = { Text("URL stream MP3/AAC optionnelle (https://...)", color = palette.textMuted, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("jukebox_stream_url_input")
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (jukeboxState.queue.isNotEmpty()) {
                    item {
                        Text("FILE D'ATTENTE (${jukeboxState.queue.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    items(jukeboxState.queue, key = { "q_${it.id}" }) { track ->
                        Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(track.accentHex)))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${track.artist} • par ${track.addedBy}", color = palette.textMuted, fontSize = 11.sp)
                                }
                                Text(formatSec(track.durationSec), color = palette.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("CATALOGUE RAVEN AUDIO", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                items(MockData.jukeboxCatalogTracks, key = { "cat_${it.id}" }) { catalogTrack ->
                    Surface(
                        color = palette.inputBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onAddTrack(catalogTrack) }.testTag("jukebox_catalog_${catalogTrack.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(catalogTrack.title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(catalogTrack.artist, color = palette.textMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.AddCircle, contentDescription = "Ajouter à la file", tint = Color(catalogTrack.accentHex))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMixerSheet(
    participants: List<User>,
    currentUserId: String,
    volumesByUserId: Map<String, Float>,
    currentEqPreset: AudioEqPreset,
    onVolumeChange: (userId: String, volume: Float) -> Unit,
    onSelectEqPreset: (AudioEqPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("voice_mixer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Equalizer, contentDescription = null, tint = DiscordGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Mixeur Vocal & Égaliseur", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Volume individuel jusqu'à 200% & profils audio", color = palette.textMuted, fontSize = 12.sp)
                }
            }

            Text("PROFIL AUDIO MICRO & ÉCOUTE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AudioEqPreset.entries.chunked(2).forEach { rowPresets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        rowPresets.forEach { preset ->
                            val isSelected = currentEqPreset == preset
                            Surface(
                                color = if (isSelected) palette.accent.copy(alpha = 0.2f) else palette.cardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectEqPreset(preset) }
                                    .testTag("eq_preset_${preset.name}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(preset.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(preset.subtitle, color = palette.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border)
            Text("VOLUME PAR PARTICIPANT", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            participants.filterNot { it.id == currentUserId }.forEach { member ->
                val vol = volumesByUserId[member.id] ?: 1f
                Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(member.name, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${(vol * 100).toInt()}%",
                                color = if (vol == 0f) DiscordRed else DiscordGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = vol,
                            onValueChange = { onVolumeChange(member.id, it) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent),
                            modifier = Modifier.testTag("mixer_slider_${member.id}")
                        )
                    }
                }
            }
        }
    }
}

private fun formatSec(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
