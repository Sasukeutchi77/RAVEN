package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.SoundFxHelper

private data class SoundPadItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val action: () -> Unit
)

private data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIdx: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceActivitiesSheet(
    initialTab: Int = 0, // 0: Soundboard, 1: Mini-jeux
    onRewardXpAndOrbs: (xp: Int, orbs: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var lastSoundPlayed by remember { mutableStateOf<String?>(null) }
    var selectedGame by remember { mutableIntStateOf(0) } // 0: RPS, 1: TicTacToe, 2: Quiz

    val soundPads = remember {
        listOf(
            SoundPadItem("applause", "Applaudissements", Icons.Default.ThumbUp, DiscordGreen) { SoundFxHelper.playApplause() },
            SoundPadItem("airhorn", "Corne de brume", Icons.Default.Campaign, DiscordRed) { SoundFxHelper.playAirhorn() },
            SoundPadItem("drumroll", "Roulement", Icons.Default.GraphicEq, DiscordAmber) { SoundFxHelper.playDrumroll() },
            SoundPadItem("rimshot", "Ba-Dum-Tss", Icons.Default.MusicNote, DiscordBlurple) { SoundFxHelper.playRimshot() },
            SoundPadItem("jackpot", "Fanfare Victoire", Icons.Default.EmojiEvents, Color(0xFFEB459E)) { SoundFxHelper.playJackpot() },
            SoundPadItem("levelup", "Niveau Supérieur", Icons.Default.AutoAwesome, Color(0xFF00A8FC)) { SoundFxHelper.playLevelUp() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("voice_activities_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 600.dp)
                .padding(bottom = 24.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = DiscordBlurple,
                divider = { HorizontalDivider(color = palette.border) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SurroundSound, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Soundboard", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mini-Jeux Vocaux", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (lastSoundPlayed != null) {
                        Surface(
                            color = DiscordBlurple.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Son diffusé dans le vocal : $lastSoundPlayed",
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(soundPads, key = { it.id }) { pad ->
                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, pad.accent.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        pad.action()
                                        lastSoundPlayed = pad.label
                                    }
                                    .testTag("soundboard_pad_${pad.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(pad.accent.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(pad.icon, contentDescription = pad.label, tint = pad.accent, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = pad.label,
                                        color = palette.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Chifoumi", "Morpion", "Quiz Tech").forEachIndexed { idx, name ->
                            FilterChip(
                                selected = selectedGame == idx,
                                onClick = { selectedGame = idx },
                                label = { Text(name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DiscordBlurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedGame) {
                        0 -> RockPaperScissorsGame(onWin = {
                            onRewardXpAndOrbs(20, 15, "Victoire Chifoumi")
                        })
                        1 -> TicTacToeGame(onWin = {
                            onRewardXpAndOrbs(30, 25, "Victoire Morpion")
                        })
                        else -> TechQuizGame(onCorrect = {
                            onRewardXpAndOrbs(25, 20, "Bonne réponse Quiz")
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun RockPaperScissorsGame(onWin: () -> Unit) {
    val palette = ravenPalette()
    val choices = listOf("Pierre", "Feuille", "Ciseaux")
    var resultText by remember { mutableStateOf("Choisissez votre coup pour défier le Bot Raven !") }
    var score by remember { mutableIntStateOf(0) }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Score victoires : $score (+15 Orbs / victoire)", color = DiscordGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(resultText, color = palette.textPrimary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEachIndexed { idx, label ->
                    Button(
                        onClick = {
                            val bot = (0..2).random()
                            if (idx == bot) {
                                resultText = "Égalité ! Vous et Raven Bot avez joué $label."
                            } else if ((idx == 0 && bot == 2) || (idx == 1 && bot == 0) || (idx == 2 && bot == 1)) {
                                score++
                                SoundFxHelper.playChime()
                                resultText = "Gagné ! $label bat ${choices[bot]}. (+20 XP, +15 Orbs)"
                                onWin()
                            } else {
                                resultText = "Perdu ! ${choices[bot]} bat $label."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TicTacToeGame(onWin: () -> Unit) {
    val palette = ravenPalette()
    var board by remember { mutableStateOf(List(9) { "" }) }
    var status by remember { mutableStateOf("À vous de jouer (X)") }
    var gameOver by remember { mutableStateOf(false) }

    fun checkWinner(b: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (l in lines) {
            if (b[l[0]].isNotEmpty() && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]]
        }
        return if (b.all { it.isNotEmpty() }) "DRAW" else null
    }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            Surface(
                                color = palette.inputBg,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !gameOver && board[idx].isEmpty()) {
                                        val next = board.toMutableList()
                                        next[idx] = "X"
                                        val res1 = checkWinner(next)
                                        if (res1 == "X") {
                                            board = next
                                            status = "Victoire ! (+30 XP, +25 Orbs)"
                                            gameOver = true
                                            SoundFxHelper.playJackpot()
                                            onWin()
                                        } else if (res1 == "DRAW") {
                                            board = next
                                            status = "Match nul !"
                                            gameOver = true
                                        } else {
                                            val emptyCells = next.indices.filter { next[it].isEmpty() }
                                            if (emptyCells.isNotEmpty()) {
                                                next[emptyCells.random()] = "O"
                                            }
                                            board = next
                                            when (checkWinner(next)) {
                                                "O" -> {
                                                    status = "Raven Bot a gagné !"
                                                    gameOver = true
                                                }
                                                "DRAW" -> {
                                                    status = "Match nul !"
                                                    gameOver = true
                                                }
                                                else -> status = "À vous de jouer (X)"
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = board[idx],
                                        color = if (board[idx] == "X") DiscordBlurple else DiscordGreen,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = {
                board = List(9) { "" }
                status = "À vous de jouer (X)"
                gameOver = false
            }) {
                Text("Rejouer", color = DiscordBlurple)
            }
        }
    }
}

@Composable
private fun TechQuizGame(onCorrect: () -> Unit) {
    val palette = ravenPalette()
    val questions = remember {
        listOf(
            QuizQuestion("Quel langage officiel est recommandé par Google pour Jetpack Compose ?", listOf("Kotlin", "Swift", "Ruby", "PHP"), 0),
            QuizQuestion("Dans Discord/Raven, quel préfixe désigne un salon textuel ?", listOf("@", "#", "&", "$"), 1),
            QuizQuestion("Que signifie FPS dans les jeux et le streaming vocal ?", listOf("Frames Per Second", "Fast Packet Sync", "File Protocol Stream", "Full Pixel Scale"), 0)
        )
    }
    var qIndex by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val currentQ = questions[qIndex]

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Question ${qIndex + 1}/${questions.size}",
                color = DiscordBlurple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = currentQ.question,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            currentQ.options.forEachIndexed { idx, opt ->
                OutlinedButton(
                    onClick = {
                        if (idx == currentQ.correctIdx) {
                            feedback = "Bonne réponse ! (+25 XP, +20 Orbs)"
                            SoundFxHelper.playChime()
                            onCorrect()
                        } else {
                            feedback = "Mauvaise réponse. La bonne réponse était ${currentQ.options[currentQ.correctIdx]}."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text(opt, color = palette.textPrimary, fontSize = 13.sp)
                }
            }
            if (feedback != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = feedback!!, color = DiscordGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    qIndex = (qIndex + 1) % questions.size
                    feedback = null
                }) {
                    Text("Question suivante", color = DiscordBlurple)
                }
            }
        }
    }
}
