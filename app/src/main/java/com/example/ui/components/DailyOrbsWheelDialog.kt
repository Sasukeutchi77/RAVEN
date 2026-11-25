package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.SoundFxHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class WheelPrize(
    val orbs: Int,
    val label: String,
    val color: Color,
    val accentColor: Color,
    val weight: Int,
    val isJackpot: Boolean = false
)

object OrbsWheelEngine {
    val prizes = listOf(
        WheelPrize(25, "25 Orbs", Color(0xFF0E7490), Color(0xFF22D3EE), weight = 240),
        WheelPrize(100, "100 Orbs", Color(0xFF4338CA), Color(0xFF818CF8), weight = 150),
        WheelPrize(50, "50 Orbs", Color(0xFF0369A1), Color(0xFF38BDF8), weight = 230),
        WheelPrize(300, "300 Rare", Color(0xFF7E22CE), Color(0xFFC084FC), weight = 42),
        WheelPrize(75, "75 Orbs", Color(0xFF047857), Color(0xFF34D399), weight = 180),
        WheelPrize(500, "JACKPOT 500", Color(0xFFD97706), Color(0xFFFDE047), weight = 18, isJackpot = true),
        WheelPrize(150, "150 Orbs", Color(0xFF6D28D9), Color(0xFFA78BFA), weight = 90),
        WheelPrize(200, "200 Orbs", Color(0xFFBE185D), Color(0xFFF472B6), weight = 50)
    )

    val totalWeight: Int = prizes.sumOf { it.weight }

    fun maxPrizeOrbs(): Int = prizes.maxOf { it.orbs }

    fun probabilityForOver200(): Double {
        val overWeight = prizes.filter { it.orbs > 200 }.sumOf { it.weight }
        return overWeight.toDouble() / totalWeight.toDouble()
    }

    fun weightPercent(prize: WheelPrize): Double {
        return (prize.weight.toDouble() / totalWeight.toDouble()) * 100.0
    }

    fun pickWeightedPrizeIndex(roll: Float = Random.nextFloat()): Int {
        val normalized = roll.coerceIn(0f, 0.999999f)
        val target = normalized * totalWeight
        var cumulative = 0f
        for (index in prizes.indices) {
            cumulative += prizes[index].weight
            if (target < cumulative) {
                return index
            }
        }
        return 0
    }
}

@Composable
fun DailyOrbsWheelDialog(
    onDismiss: () -> Unit,
    onWinOrbs: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSpinning by remember { mutableStateOf(false) }
    var wonPrize by remember { mutableStateOf<WheelPrize?>(null) }
    var showWonCelebration by remember { mutableStateOf(false) }
    var totalSpinsSession by remember { mutableIntStateOf(0) }

    val prizes = remember { OrbsWheelEngine.prizes }
    val rotation = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "wheel_bezel_lights")
    val lightPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpinning) 380 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lightPhase"
    )

    fun spinWheel() {
        if (isSpinning) return
        isSpinning = true
        wonPrize = null

        coroutineScope.launch {
            val selectedIndex = OrbsWheelEngine.pickWeightedPrizeIndex()
            val targetPrize = prizes[selectedIndex]

            val sliceAngle = 360f / prizes.size
            val sliceCenterAngle = (selectedIndex * sliceAngle) + (sliceAngle / 2f)
            val currentBase = rotation.value - (rotation.value % 360f)
            val jitterOffset = (Random.nextFloat() - 0.5f) * (sliceAngle * 0.36f)
            val targetAngle = currentBase + (360f * 7) + (270f - sliceCenterAngle) + jitterOffset

            val tickJob = launch {
                var delayMs = 36L
                while (isSpinning) {
                    SoundFxHelper.playTick()
                    delay(delayMs)
                    delayMs = (delayMs * 1.065).toLong().coerceAtMost(360L)
                }
            }

            rotation.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(
                    durationMillis = 4400,
                    easing = CubicBezierEasing(0.10f, 0.84f, 0.28f, 1f)
                )
            )

            tickJob.cancel()
            isSpinning = false
            totalSpinsSession += 1
            wonPrize = targetPrize
            showWonCelebration = true
            if (targetPrize.isJackpot || targetPrize.orbs > 200) {
                SoundFxHelper.playJackpot()
            } else {
                SoundFxHelper.playChime()
            }
            onWinOrbs(targetPrize.orbs)
        }
    }

    Dialog(
        onDismissRequest = { if (!isSpinning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = DiscordDarkPrimary,
            border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1F113B),
                                DiscordDarkPrimary,
                                Color(0xFF14151C)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFFDE047),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ROUE DE LA FORTUNE ORBS",
                                    color = DiscordTextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = "Tirage limité à 500 Orbs max",
                                    color = Color(0xFFFDE047),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSpinning,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_wheel_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer la roue", tint = DiscordTextLight)
                        }
                    }

                    // Transparent Odds Strip
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DiscordDarkCard.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Tentez de remporter jusqu'à 500 Orbs gratuits !",
                                color = DiscordTextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val over200Pct = (OrbsWheelEngine.probabilityForOver200() * 100.0)
                            Text(
                                text = "Gains 25–200 Orbs : ${100 - over200Pct.toInt()}% • Gros lots >200 Orbs : ${String.format("%.0f", over200Pct)}% (Jackpot 500 rare)",
                                color = DiscordTextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Wheel Container with LED Bezel, Metallic Rim, Canvas & Spring Pointer
                    Box(
                        modifier = Modifier.size(294.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer LED bezel & glow halo
                        Canvas(modifier = Modifier.size(294.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val outerR = size.width / 2f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF818CF8).copy(alpha = 0.28f), Color.Transparent),
                                    center = c,
                                    radius = outerR
                                ),
                                radius = outerR,
                                center = c
                            )
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF818CF8), Color(0xFF38BDF8), Color(0xFFF59E0B))
                                ),
                                radius = outerR - 8f,
                                center = c,
                                style = Stroke(width = 14f)
                            )
                            // 16 animated bezel LED bulbs
                            val bulbCount = 16
                            for (b in 0 until bulbCount) {
                                val ang = Math.toRadians(b * (360.0 / bulbCount))
                                val bx = c.x + (outerR - 8f) * cos(ang).toFloat()
                                val by = c.y + (outerR - 8f) * sin(ang).toFloat()
                                val lit = ((b + (lightPhase * 4).toInt()) % 2 == 0)
                                drawCircle(
                                    color = if (lit) Color(0xFFFDE047) else Color(0xFF312E81),
                                    radius = if (lit) 4.2f else 3.2f,
                                    center = Offset(bx, by)
                                )
                            }
                        }

                        // Spinning Canvas Wheel
                        Canvas(modifier = Modifier.size(264.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            val sliceAngle = 360f / prizes.size

                            rotate(rotation.value, pivot = center) {
                                prizes.forEachIndexed { i, prize ->
                                    val startAngle = i * sliceAngle
                                    drawArc(
                                        brush = Brush.radialGradient(
                                            colors = listOf(prize.accentColor, prize.color),
                                            center = center,
                                            radius = radius
                                        ),
                                        startAngle = startAngle,
                                        sweepAngle = sliceAngle,
                                        useCenter = true,
                                        topLeft = Offset.Zero,
                                        size = size,
                                        style = Fill
                                    )

                                    // Crisp metallic divider
                                    val rad = Math.toRadians(startAngle.toDouble())
                                    val endX = center.x + radius * cos(rad).toFloat()
                                    val endY = center.y + radius * sin(rad).toFloat()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.55f),
                                        start = center,
                                        end = Offset(endX, endY),
                                        strokeWidth = 2.4f
                                    )

                                    // Prize label text
                                    val midAngleRad = Math.toRadians((startAngle + sliceAngle / 2f).toDouble())
                                    val textDist = radius * 0.66f
                                    val textX = center.x + textDist * cos(midAngleRad).toFloat()
                                    val textY = center.y + textDist * sin(midAngleRad).toFloat()

                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = if (prize.isJackpot) 30f else if (prize.orbs > 200) 28f else 26f
                                        isFakeBoldText = true
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                                    }

                                    drawContext.canvas.nativeCanvas.save()
                                    drawContext.canvas.nativeCanvas.rotate(
                                        (startAngle + sliceAngle / 2f) + 90f,
                                        textX,
                                        textY
                                    )
                                    val sliceText = if (prize.isJackpot) "500 💎" else "${prize.orbs}"
                                    drawContext.canvas.nativeCanvas.drawText(
                                        sliceText,
                                        textX,
                                        textY + 10f,
                                        paint
                                    )
                                    drawContext.canvas.nativeCanvas.restore()
                                }

                                // Inner hub ring & jewel emblem
                                drawCircle(
                                    color = Color(0xFF111218),
                                    radius = radius * 0.25f,
                                    center = center
                                )
                                drawCircle(
                                    brush = Brush.sweepGradient(listOf(Color(0xFFFDE047), Color(0xFF818CF8), Color(0xFFEC4899), Color(0xFFFDE047))),
                                    radius = radius * 0.25f,
                                    center = center,
                                    style = Stroke(width = 3.5f)
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(listOf(Color(0xFF818CF8), Color(0xFF4338CA))),
                                    radius = radius * 0.18f,
                                    center = center
                                )
                            }
                        }

                        // Center static diamond icon
                        Icon(
                            imageVector = Icons.Default.Diamond,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )

                        // Top Pointer Arrow (Fixed at top)
                        Canvas(
                            modifier = Modifier
                                .size(34.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (-4).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(2f, 2f)
                                lineTo(size.width - 2f, 2f)
                                close()
                            }
                            drawPath(
                                path,
                                brush = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFD97706))),
                                style = Fill
                            )
                            drawPath(path, color = Color.White, style = Stroke(width = 2.4f))
                        }
                    }

                    // Bottom Action / Win Banner
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (wonPrize != null) {
                            val prize = wonPrize!!
                            val isRareWin = prize.orbs > 200
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    prize.isJackpot -> Color(0xFFF59E0B).copy(alpha = 0.22f)
                                    isRareWin -> Color(0xFFA855F7).copy(alpha = 0.22f)
                                    else -> DiscordBlurple.copy(alpha = 0.22f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        prize.isJackpot -> Color(0xFFFDE047)
                                        isRareWin -> Color(0xFFC084FC)
                                        else -> DiscordBlurple
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .testTag("wheel_win_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (prize.isJackpot) Icons.Default.Stars else Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = if (prize.isJackpot) Color(0xFFFDE047) else Color(0xFF818CF8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when {
                                            prize.isJackpot -> "JACKPOT MAX ! +500 Orbs crédités !"
                                            isRareWin -> "TIRAGE RARE ! +${prize.orbs} Orbs crédités !"
                                            else -> "Gagné : +${prize.orbs} Orbs crédités !"
                                        },
                                        color = DiscordTextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { spinWheel() },
                            enabled = !isSpinning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DiscordBlurple,
                                disabledContainerColor = DiscordDarkInput
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("spin_wheel_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSpinning -> "La roue tourne..."
                                    totalSpinsSession > 0 -> "Rejouer un tour (Max 500 Orbs)"
                                    else -> "Lancer la Roue (Max 500 Orbs)"
                                },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Confetti burst on win
                ConfettiEffect(
                    trigger = showWonCelebration,
                    onAnimationEnd = { showWonCelebration = false }
                )
            }
        }
    }
}
