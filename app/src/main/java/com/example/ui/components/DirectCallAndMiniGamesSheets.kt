package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun DirectCallOverlay(
    session: DirectCallSession,
    onAnswer: () -> Unit,
    onDeclineOrEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onMinimize: () -> Unit
) {
    var elapsedSec by remember(session.id, session.connectedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(session.status, session.connectedAtMs) {
        while (session.status == DirectCallStatus.CONNECTED) {
            val start = session.connectedAtMs ?: System.currentTimeMillis()
            elapsedSec = ((System.currentTimeMillis() - start) / 1000L).toInt().coerceAtLeast(0)
            delay(1000L)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val ringScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring_scale"
    )

    Surface(
        color = Color(0xFF070A14),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("direct_call_overlay")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF131A33), Color(0xFF070A14), Color(0xFF04060D))
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0x3338BDF8), shape = CircleShape) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (session.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (session.isVideo) "Appel vidéo chiffré" else "Appel audio HD",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onMinimize, modifier = Modifier.testTag("call_minimize_pip_btn")) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Mode miniature", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (session.status != DirectCallStatus.CONNECTED) {
                        Box(
                            modifier = Modifier
                                .size(134.dp)
                                .graphicsLayer {
                                    scaleX = ringScale
                                    scaleY = ringScale
                                }
                                .clip(CircleShape)
                                .background(DiscordBlurple.copy(alpha = 0.24f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color(session.peerUser.avatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!session.peerUser.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = session.peerUser.avatarUrl,
                                contentDescription = session.peerUser.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = (session.groupName ?: session.peerUser.name).take(1).uppercase(),
                                color = Color.White,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = session.groupName ?: session.peerUser.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (session.status == DirectCallStatus.CONNECTED) {
                        String.format("%02d:%02d", elapsedSec / 60, elapsedSec % 60)
                    } else {
                        session.status.label
                    },
                    color = if (session.status == DirectCallStatus.CONNECTED) DiscordGreen else Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (session.isCameraOn) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color(0xFF111827),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                        modifier = Modifier.size(width = 190.dp, height = 110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Caméra locale active", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (session.status == DirectCallStatus.RINGING_INCOMING) {
                    FloatingActionButton(
                        onClick = onDeclineOrEnd,
                        containerColor = DiscordRed,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Refuser", tint = Color.White)
                    }
                    FloatingActionButton(
                        onClick = onAnswer,
                        containerColor = DiscordGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp).testTag("call_answer_btn")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Décrocher", tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(if (session.isMuted) Color.White else Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Micro",
                            tint = if (session.isMuted) Color.Black else Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleCamera,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(if (session.isCameraOn) Color.White else Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = if (session.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Caméra",
                            tint = if (session.isCameraOn) Color.Black else Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleSpeaker,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(if (session.isSpeakerOn) DiscordBlurple else Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Haut-parleur",
                            tint = Color.White
                        )
                    }
                    FloatingActionButton(
                        onClick = onDeclineOrEnd,
                        containerColor = DiscordRed,
                        shape = CircleShape,
                        modifier = Modifier.size(58.dp).testTag("call_hangup_btn")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Raccrocher", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun DirectCallFloatingPipBar(
    session: DirectCallSession,
    onExpand: () -> Unit,
    onToggleMute: () -> Unit,
    onHangUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEE0F172A),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DiscordGreen.copy(alpha = 0.6f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(12.dp)
            .clickable(onClick = onExpand)
            .testTag("direct_call_pip_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(session.peerUser.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text((session.groupName ?: session.peerUser.name).take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(session.groupName ?: session.peerUser.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(session.status.label, color = DiscordGreen, fontSize = 11.sp)
            }
            IconButton(onClick = onToggleMute, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Muet",
                    tint = if (session.isMuted) DiscordRed else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onHangUp,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(DiscordRed)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Quitter appel", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistorySheet(
    history: List<CallHistoryEntry>,
    onCallBack: (CallHistoryEntry, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DiscordDarkCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = DiscordBlurple)
                Text("Historique des appels", color = DiscordTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (history.isEmpty()) {
                Text("Aucun appel récent. Lancez un appel audio ou vidéo depuis un MP !", color = DiscordTextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history, key = { it.id }) { item ->
                        Surface(color = DiscordDarkInput, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(item.peerAvatarColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(item.peerName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(item.peerName, color = DiscordTextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        val detail = when {
                                            item.isMissed -> "Manqué"
                                            item.durationSec > 0 -> "${item.durationSec}s"
                                            else -> "Terminé"
                                        }
                                        Text("${if (item.isOutgoing) "Sortant" else "Entrant"} • $detail • ${item.timestamp}", color = DiscordTextMuted, fontSize = 11.sp)
                                    }
                                }
                                Row {
                                    IconButton(onClick = { onCallBack(item, false); onDismiss() }) {
                                        Icon(Icons.Default.Call, contentDescription = "Rappeler audio", tint = DiscordGreen)
                                    }
                                    IconButton(onClick = { onCallBack(item, true); onDismiss() }) {
                                        Icon(Icons.Default.Videocam, contentDescription = "Rappeler vidéo", tint = DiscordBlurple)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelMiniGamesSheet(
    targetName: String,
    onPostResultToChannel: (String) -> Unit,
    onRewardOrbs: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGame by remember { mutableStateOf(MiniGameType.TIC_TAC_TOE) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DiscordDarkCard) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = DiscordBlurple)
                Column {
                    Text("Mini-jeux en direct", color = DiscordTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Partie synchronisée dans $targetName", color = DiscordTextMuted, fontSize = 11.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                MiniGameType.entries.forEach { game ->
                    FilterChip(
                        selected = selectedGame == game,
                        onClick = { selectedGame = game },
                        label = { Text(game.label, fontSize = 11.sp) }
                    )
                }
            }

            when (selectedGame) {
                MiniGameType.TIC_TAC_TOE -> TicTacToeMiniGame(onWin = { winner ->
                    if (winner == "X") {
                        onRewardOrbs(30, "Victoire Morpion")
                        onPostResultToChannel("🎮 Morpion Raven : victoire remportée (+30 Orbs) !")
                    }
                })

                MiniGameType.RAPID_QUIZ -> RapidQuizMiniGame(onComplete = { score, total ->
                    val earned = score * 15
                    if (earned > 0) onRewardOrbs(earned, "Quiz Chrono")
                    onPostResultToChannel("🧠 Quiz Chrono terminé : $score/$total bonnes réponses (+$earned Orbs) !")
                })

                MiniGameType.DRAW_AND_GUESS -> DrawAndGuessMiniGame(onShareSketch = { prompt ->
                    onRewardOrbs(20, "Dessiner & Deviner")
                    onPostResultToChannel("🎨 Dessiner & Deviner lancé ! Mot mystère prêt pour le salon (${prompt.length} lettres).")
                })
            }
        }
    }
}

@Composable
private fun TicTacToeMiniGame(onWin: (String) -> Unit) {
    var board by remember { mutableStateOf(List(9) { "" }) }
    var currentTurn by remember { mutableStateOf("X") }
    var winner by remember { mutableStateOf<String?>(null) }

    fun checkWinner(b: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            val a = b[line[0]]
            if (a.isNotEmpty() && a == b[line[1]] && a == b[line[2]]) return a
        }
        return if (b.all { it.isNotEmpty() }) "DRAW" else null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (winner) {
                "X" -> "Victoire ! (+30 Orbs)"
                "O" -> "Raven Bot gagne !"
                "DRAW" -> "Match nul !"
                else -> "À votre tour (X)"
            },
            color = DiscordTextWhite,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0..2) {
                    val idx = row * 3 + col
                    Surface(
                        color = DiscordDarkInput,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DiscordDarkBorder),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = winner == null && board[idx].isEmpty()) {
                                val next = board.toMutableList()
                                next[idx] = "X"
                                val w1 = checkWinner(next)
                                if (w1 != null) {
                                    board = next
                                    winner = w1
                                    onWin(w1)
                                } else {
                                    val botChoice = next.indices.firstOrNull { next[it].isEmpty() }
                                    if (botChoice != null) next[botChoice] = "O"
                                    board = next
                                    val w2 = checkWinner(next)
                                    winner = w2
                                    if (w2 != null) onWin(w2)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = board[idx],
                                color = if (board[idx] == "X") DiscordBlurple else DiscordGreen,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextButton(onClick = { board = List(9) { "" }; winner = null; currentTurn = "X" }) {
            Text("Rejouer", color = DiscordBlurple)
        }
    }
}

@Composable
private fun RapidQuizMiniGame(onComplete: (Int, Int) -> Unit) {
    val questions = MiniGameQuizCatalog.questions
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (finished) {
            Text("Score final : $score / ${questions.size}", color = DiscordGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { index = 0; score = 0; finished = false },
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
            ) {
                Text("Recommencer", color = Color.White)
            }
        } else {
            val q = questions[index]
            Text("Question ${index + 1}/${questions.size}", color = DiscordTextMuted, fontSize = 11.sp)
            Text(q.question, color = DiscordTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            q.options.forEachIndexed { optIdx, label ->
                Surface(
                    color = DiscordDarkInput,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val nextScore = if (optIdx == q.correctIndex) score + 1 else score
                            score = nextScore
                            if (index < questions.lastIndex) {
                                index++
                            } else {
                                finished = true
                                onComplete(nextScore, questions.size)
                            }
                        }
                ) {
                    Text(label, color = DiscordTextLight, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawAndGuessMiniGame(onShareSketch: (String) -> Unit) {
    var promptIdx by remember { mutableIntStateOf(0) }
    val prompt = MiniGameQuizCatalog.drawPrompts[promptIdx % MiniGameQuizCatalog.drawPrompts.size]
    val paths = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("À dessiner : $prompt", color = DiscordBlurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextButton(onClick = { promptIdx++; paths.clear() }) { Text("Autre mot", color = DiscordTextMuted) }
        }
        Surface(
            color = Color(0xFF0E1322),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DiscordDarkBorder),
            modifier = Modifier.fillMaxWidth().height(170.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start -> currentStroke = listOf(start) },
                            onDrag = { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + change.position
                            },
                            onDragEnd = {
                                if (currentStroke.size > 1) paths.add(currentStroke)
                                currentStroke = emptyList()
                            }
                        )
                    }
            ) {
                (paths + listOf(currentStroke)).forEach { pts ->
                    if (pts.size > 1) {
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, color = Color(0xFF38BDF8), style = Stroke(width = 6f, cap = StrokeCap.Round))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { paths.clear() }) { Text("Effacer", color = DiscordTextMuted) }
            Button(
                onClick = { onShareSketch(prompt) },
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.weight(1f)
            ) {
                Text("Partager dans le salon (+20 Orbs)", color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}
