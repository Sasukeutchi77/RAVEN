package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

data class ConfettiParticle(
    val initialX: Float,
    val initialY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float,
    val isCircle: Boolean
)

@Composable
fun ConfettiEffect(
    trigger: Boolean,
    onAnimationEnd: () -> Unit = {}
) {
    if (!trigger) return

    val progress = remember { Animatable(0f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFF818CF8), // Discord Blurple light
            Color(0xFFC084FC), // Purple
            Color(0xFFF472B6), // Pink
            Color(0xFF38BDF8), // Cyan
            Color(0xFF34D399), // Emerald
            Color(0xFFFBBF24), // Gold
            Color(0xFFFFFFFF)  // Sparkle white
        )
        List(75) {
            ConfettiParticle(
                initialX = Random.nextFloat(),
                initialY = Random.nextFloat() * 0.25f,
                velocityX = (Random.nextFloat() - 0.5f) * 600f,
                velocityY = Random.nextFloat() * 800f + 400f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                color = colors.random(),
                size = Random.nextFloat() * 10f + 8f,
                isCircle = Random.nextBoolean()
            )
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
        onAnimationEnd()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        val alpha = (1f - (t * 0.95f)).coerceIn(0f, 1f)

        particles.forEach { p ->
            val x = (p.initialX * size.width) + (p.velocityX * t)
            val y = (p.initialY * size.height) + (p.velocityY * t) + (0.5f * 980f * t * t)
            val rot = p.rotationSpeed * t

            rotate(rot, pivot = Offset(x, y)) {
                if (p.isCircle) {
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size / 2f,
                        center = Offset(x, y)
                    )
                } else {
                    drawRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}
