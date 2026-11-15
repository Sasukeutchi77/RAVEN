package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
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
    val isJackpot: Boolean = false
)

@Composable
fun DailyOrbsWheelDialog(
    onDismiss: () -> Unit,
    onWinOrbs: (Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSpinning by remember { mutableStateOf(false) }
    var wonPrize by remember { mutableStateOf<WheelPrize?>(null) }
    var showWonCelebration by remember { mutableStateOf(false) }

    val prizes = remember {
        listOf(
            WheelPrize(100, "100 Orbs", Color(0xFF4F46E5)),
            WheelPrize(50, "50 Orbs", Color(0xFF06B6D4)),
            WheelPrize(250, "250 Orbs", Color(0xFF9333EA)),
            WheelPrize(1000, "JACKPOT 1000", Color(0xFFF59E0B), isJackpot = true),
            WheelPrize(150, "150 Orbs", Color(0xFF0EA5E9)),
            WheelPrize(500, "500 Orbs", Color(0xFFEC4899)),
            WheelPrize(75, "75 Orbs", Color(0xFF10B981)),
            WheelPrize(300, "300 Orbs", Color(0xFF8B5CF6))
        )
    }

    val rotation = remember { Animatable(0f) }

    fun spinWheel() {
        if (isSpinning) return
        isSpinning = true
        wonPrize = null

        coroutineScope.launch {
            // Select random prize (bias towards 150-500, with 15% jackpot chance)
            val selectedIndex = if (Random.nextFloat() < 0.20f) 3 else Random.nextInt(prizes.size)
            val targetPrize = prizes[selectedIndex]

            // Calculate rotation angle so that the pointer at top (270 degrees) lands on the slice
            val sliceAngle = 360f / prizes.size
            val sliceCenterAngle = (selectedIndex * sliceAngle) + (sliceAngle / 2f)
            // Top pointer is at -90 degrees (or 270 degrees)
            val targetAngle = 360f * 6 + (270f - sliceCenterAngle)

            // Audio ticks during spin
            val tickJob = launch {
                var delayMs = 40L
                while (isSpinning) {
                    SoundFxHelper.playTick()
                    delay(delayMs)
                    delayMs = (delayMs * 1.06).toLong().coerceAtMost(350L)
                }
            }

            rotation.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(
                    durationMillis = 4200,
                    easing = CubicBezierEasing(0.12f, 0.8f, 0.32f, 1f)
                )
            )

            tickJob.cancel()
            isSpinning = false
            wonPrize = targetPrize
            showWonCelebration = true
            if (targetPrize.isJackpot) {
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
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = DiscordDarkPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
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
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ROUE DE LA FORTUNE ORBS",
                                color = DiscordTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSpinning,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextMuted)
                        }
                    }

                    Text(
                        text = "Faites tourner la roue chaque jour pour remporter jusqu'à 1000 Orbs gratuits !",
                        color = DiscordTextLight,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Wheel Container with Canvas & Pointer
                    Box(
                        modifier = Modifier
                            .size(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer glowing neon ring
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 6.dp,
                                    brush = Brush.sweepGradient(listOf(Color(0xFF818CF8), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF818CF8))),
                                    shape = CircleShape
                                )
                        )

                        // Spinning Canvas Wheel
                        Canvas(modifier = Modifier.size(268.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            val sliceAngle = 360f / prizes.size

                            rotate(rotation.value, pivot = center) {
                                prizes.forEachIndexed { i, prize ->
                                    val startAngle = i * sliceAngle
                                    // Slice Arc
                                    drawArc(
                                        color = prize.color,
                                        startAngle = startAngle,
                                        sweepAngle = sliceAngle,
                                        useCenter = true,
                                        topLeft = Offset.Zero,
                                        size = size,
                                        style = Fill
                                    )

                                    // Slice separator line
                                    val rad = Math.toRadians(startAngle.toDouble())
                                    val endX = center.x + radius * cos(rad).toFloat()
                                    val endY = center.y + radius * sin(rad).toFloat()
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.35f),
                                        start = center,
                                        end = Offset(endX, endY),
                                        strokeWidth = 2f
                                    )

                                    // Prize label text on canvas
                                    val midAngleRad = Math.toRadians((startAngle + sliceAngle / 2f).toDouble())
                                    val textDist = radius * 0.65f
                                    val textX = center.x + textDist * cos(midAngleRad).toFloat()
                                    val textY = center.y + textDist * sin(midAngleRad).toFloat()

                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = if (prize.isJackpot) 32f else 28f
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
                                    drawContext.canvas.nativeCanvas.drawText(
                                        if (prize.isJackpot) "1000 💎" else "${prize.orbs}",
                                        textX,
                                        textY + 10f,
                                        paint
                                    )
                                    drawContext.canvas.nativeCanvas.restore()
                                }

                                // Inner center circle
                                drawCircle(
                                    color = Color(0xFF1E1F22),
                                    radius = radius * 0.25f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF5865F2),
                                    radius = radius * 0.18f,
                                    center = center
                                )
                            }
                        }

                        // Top Pointer Arrow (Fixed at top)
                        Canvas(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (-6).dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path, color = Color(0xFFF59E0B), style = Fill)
                            drawPath(path, color = Color.White, style = Stroke(width = 2f))
                        }
                    }

                    // Bottom Action / Win Banner
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (wonPrize != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (wonPrize!!.isJackpot) Color(0xFFF59E0B).copy(alpha = 0.2f) else DiscordBlurple.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (wonPrize!!.isJackpot) Color(0xFFF59E0B) else DiscordBlurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gagné : +${wonPrize!!.orbs} Orbs crédités !",
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
                                containerColor = Color(0xFF5865F2),
                                disabledContainerColor = DiscordDarkInput
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("spin_wheel_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSpinning) "La roue tourne..." else if (wonPrize != null) "Rejouer un tour (Gratuit)" else "Lancer la Roue Gratuitement",
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
