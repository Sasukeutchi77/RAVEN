package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BadgeRarity
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

object AppIconSets {
    val reactionIcons = listOf(
        "heart" to Icons.Default.Favorite,
        "fire" to Icons.Default.LocalFireDepartment,
        "thumb_up" to Icons.Default.ThumbUp,
        "star" to Icons.Default.Star,
        "smile" to Icons.Default.Mood,
        "rocket" to Icons.Default.RocketLaunch,
        "celebrate" to Icons.Default.Celebration,
        "bolt" to Icons.Default.Bolt
    )

    val statusIcons = listOf(
        "chat" to Icons.Default.Chat,
        "code" to Icons.Default.Code,
        "game" to Icons.Default.SportsEsports,
        "headset" to Icons.Default.Headphones,
        "coffee" to Icons.Default.Coffee,
        "rocket" to Icons.Default.RocketLaunch,
        "bolt" to Icons.Default.Bolt,
        "star" to Icons.Default.Star,
        "music" to Icons.Default.MusicNote,
        "heart" to Icons.Default.Favorite
    )

    private val legacyIconKeys = setOf(
        "heart", "fire", "thumb_up", "star", "smile", "rocket", "celebrate", "bolt"
    )

    fun isLegacyIconKey(key: String): Boolean {
        return key.lowercase().trim() in legacyIconKeys
    }

    fun getReactionVector(key: String): ImageVector {
        return when (key.lowercase().trim()) {
            "heart" -> Icons.Default.Favorite
            "fire" -> Icons.Default.LocalFireDepartment
            "thumb_up" -> Icons.Default.ThumbUp
            "star" -> Icons.Default.Star
            "smile" -> Icons.Default.Mood
            "rocket" -> Icons.Default.RocketLaunch
            "celebrate" -> Icons.Default.Celebration
            "bolt" -> Icons.Default.Bolt
            else -> Icons.Default.ThumbUp
        }
    }

    fun getReactionColor(key: String): Color {
        return when (key.lowercase().trim()) {
            "heart" -> DiscordRed
            "fire" -> DiscordAmber
            "thumb_up" -> DiscordBlurple
            "star" -> Color(0xFFFEE75C)
            "smile" -> DiscordGreen
            "rocket" -> DiscordBlurple
            "celebrate" -> Color(0xFFEB459E)
            "bolt" -> Color(0xFFFEE75C)
            else -> DiscordTextLight
        }
    }

    fun getStatusVector(key: String): ImageVector {
        return when (key.lowercase().trim()) {
            "chat", "💬" -> Icons.Default.Chat
            "code", "💻" -> Icons.Default.Code
            "game", "🎮" -> Icons.Default.SportsEsports
            "headset", "🎧" -> Icons.Default.Headphones
            "coffee", "☕" -> Icons.Default.Coffee
            "rocket", "🚀" -> Icons.Default.RocketLaunch
            "bolt", "⚡", "🦅" -> Icons.Default.Bolt
            "star", "✨" -> Icons.Default.Star
            "music" -> Icons.Default.MusicNote
            "heart", "❤️" -> Icons.Default.Favorite
            else -> Icons.Default.Chat
        }
    }

    fun getSuperBadgeVector(key: String): ImageVector {
        return when (key.lowercase().trim()) {
            "early_supporter" -> Icons.Default.AutoAwesome
            "active_dev" -> Icons.Default.Code
            "nitro" -> Icons.Default.Diamond
            "booster" -> Icons.Default.RocketLaunch
            "hypesquad" -> Icons.Default.Bolt
            "bug_hunter" -> Icons.Default.PestControl
            "voice_master" -> Icons.Default.RecordVoiceOver
            "community_leader" -> Icons.Default.WorkspacePremium
            else -> Icons.Default.Shield
        }
    }
}

@Composable
fun AppReactionIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color? = null
) {
    if (AppIconSets.isLegacyIconKey(iconKey)) {
        val vector = AppIconSets.getReactionVector(iconKey)
        val color = tint ?: AppIconSets.getReactionColor(iconKey)
        Icon(
            imageVector = vector,
            contentDescription = iconKey,
            tint = color,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconKey,
                fontSize = (size.value * 0.85f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AppStatusIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    tint: Color = DiscordTextLight
) {
    val vector = AppIconSets.getStatusVector(iconKey)
    Icon(
        imageVector = vector,
        contentDescription = iconKey,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun SuperBadgeMedallion(
    iconKey: String,
    rarity: BadgeRarity,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    iconSize: Dp = 16.dp,
    onClick: (() -> Unit)? = null
) {
    val vector = AppIconSets.getSuperBadgeVector(iconKey)
    val bg = Color(rarity.color)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = vector,
            contentDescription = iconKey,
            tint = bg,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun AvatarDecorationOverlay(
    decorationKey: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (decorationKey.isNullOrBlank()) return

    val infiniteTransition = rememberInfiniteTransition(label = "deco_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val wingFlap by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wingFlap"
    )
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    val fastGlitch by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastGlitch"
    )

    val canvasSize = size + 24.dp

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (size.toPx() / 2f) + 2f

            when (decorationKey) {
                "magic_mist" -> {
                    // Pulsating outer glow aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFC026D3).copy(alpha = 0.45f),
                                Color(0xFF00F0FF).copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.35f * pulse
                        ),
                        radius = baseRadius * 1.35f * pulse,
                        center = center
                    )

                    // Rotating primary chromatic mist ring
                    rotate(rotation, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF00F0FF),
                                    Color(0xFFC026D3),
                                    Color(0xFF7928CA),
                                    Color(0xFF00F0FF)
                                )
                            ),
                            radius = baseRadius + 1f,
                            center = center,
                            style = Stroke(width = 3.5f)
                        )
                    }

                    // Counter-rotating inner ring with stardust nodes
                    rotate(counterRotation, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE879F9),
                                    Color(0xFF38BDF8),
                                    Color(0xFFE879F9)
                                )
                            ),
                            radius = baseRadius - 1f,
                            center = center,
                            style = Stroke(width = 1.8f)
                        )

                        for (i in 0 until 4) {
                            val angleRad = Math.toRadians((i * 90.0))
                            val orbX = center.x + (baseRadius + 3f) * cos(angleRad).toFloat()
                            val orbY = center.y + (baseRadius + 3f) * sin(angleRad).toFloat()
                            drawCircle(
                                color = if (i % 2 == 0) Color(0xFF00F0FF) else Color(0xFFF472B6),
                                radius = 2.8f * pulse,
                                center = Offset(orbX, orbY)
                            )
                        }
                    }
                }

                "infinity_swirl" -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.35f),
                                Color(0xFFEC4899).copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.4f
                        ),
                        radius = baseRadius * 1.4f,
                        center = center
                    )

                    rotate(rotation * 1.3f, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF818CF8),
                                    Color(0xFFEC4899),
                                    Color(0xFFF43F5E),
                                    Color(0xFF38BDF8)
                                )
                            ),
                            radius = baseRadius + 2f,
                            center = center,
                            style = Stroke(width = 3.5f)
                        )

                        for (i in 0 until 3) {
                            val startDeg = (i * 120f)
                            drawArc(
                                color = if (i == 0) Color(0xFF38BDF8) else if (i == 1) Color(0xFFEC4899) else Color(0xFFF59E0B),
                                startAngle = startDeg,
                                sweepAngle = 55f,
                                useCenter = false,
                                topLeft = Offset(center.x - baseRadius - 6f, center.y - baseRadius - 6f),
                                size = androidx.compose.ui.geometry.Size((baseRadius + 6f) * 2f, (baseRadius + 6f) * 2f),
                                style = Stroke(width = 2.4f)
                            )
                        }
                    }
                }

                "cyber_halo" -> {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF10B981),
                                Color(0xFF06B6D4),
                                Color(0xFFFBBF24),
                                Color(0xFF10B981)
                            )
                        ),
                        radius = baseRadius + 2f,
                        center = center,
                        style = Stroke(width = 3.2f)
                    )

                    val topCrownPath = Path().apply {
                        val spireH = baseRadius * 0.45f * pulse
                        moveTo(center.x, center.y - baseRadius - spireH)
                        lineTo(center.x - 6f, center.y - baseRadius)
                        lineTo(center.x + 6f, center.y - baseRadius)
                        close()
                        moveTo(center.x - baseRadius * 0.5f, center.y - baseRadius * 0.9f - spireH * 0.7f)
                        lineTo(center.x - baseRadius * 0.5f - 4f, center.y - baseRadius * 0.9f)
                        lineTo(center.x - baseRadius * 0.5f + 4f, center.y - baseRadius * 0.9f)
                        close()
                        moveTo(center.x + baseRadius * 0.5f, center.y - baseRadius * 0.9f - spireH * 0.7f)
                        lineTo(center.x + baseRadius * 0.5f - 4f, center.y - baseRadius * 0.9f)
                        lineTo(center.x + baseRadius * 0.5f + 4f, center.y - baseRadius * 0.9f)
                        close()
                    }
                    drawPath(topCrownPath, color = Color(0xFF00F0FF), style = Fill)
                    drawPath(topCrownPath, color = Color.White, style = Stroke(width = 1.5f))

                    rotate(rotation * 0.5f, pivot = center) {
                        for (i in 0 until 4) {
                            val ang = Math.toRadians((i * 90.0) + 45.0)
                            val bx = center.x + (baseRadius + 4f) * cos(ang).toFloat()
                            val by = center.y + (baseRadius + 4f) * sin(ang).toFloat()
                            drawCircle(color = Color(0xFF10B981), radius = 2f, center = Offset(bx, by))
                        }
                    }
                }

                "raven_wings" -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF8B5CF6).copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.5f
                        ),
                        radius = baseRadius * 1.5f,
                        center = center
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF5865F2),
                                Color(0xFF1E1F22),
                                Color(0xFF8B5CF6),
                                Color(0xFF5865F2)
                            )
                        ),
                        radius = baseRadius + 1.5f,
                        center = center,
                        style = Stroke(width = 3f)
                    )

                    rotate(wingFlap, pivot = Offset(center.x - baseRadius, center.y)) {
                        val leftWingPath = Path().apply {
                            val lx = center.x - baseRadius
                            val ly = center.y
                            moveTo(lx, ly - 4f)
                            cubicTo(
                                lx - baseRadius * 0.5f, ly - baseRadius * 0.45f,
                                lx - baseRadius * 0.6f, ly + baseRadius * 0.1f,
                                lx - 4f, ly + 8f
                            )
                            close()
                        }
                        drawPath(leftWingPath, color = Color(0xFF2B2D31), style = Fill)
                        drawPath(leftWingPath, color = Color(0xFF8B5CF6), style = Stroke(width = 2f))
                    }

                    rotate(-wingFlap, pivot = Offset(center.x + baseRadius, center.y)) {
                        val rightWingPath = Path().apply {
                            val rx = center.x + baseRadius
                            val ry = center.y
                            moveTo(rx, ry - 4f)
                            cubicTo(
                                rx + baseRadius * 0.5f, ry - baseRadius * 0.45f,
                                rx + baseRadius * 0.6f, ry + baseRadius * 0.1f,
                                rx + 4f, ry + 8f
                            )
                            close()
                        }
                        drawPath(rightWingPath, color = Color(0xFF2B2D31), style = Fill)
                        drawPath(rightWingPath, color = Color(0xFF8B5CF6), style = Stroke(width = 2f))
                    }
                }

                "phoenix_flames" -> {
                    // Blazing inferno aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0xFFEA580C).copy(alpha = 0.55f),
                                Color(0xFFDC2626).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.5f * pulse
                        ),
                        radius = baseRadius * 1.5f * pulse,
                        center = center
                    )

                    // Fire ring base
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFDC2626),
                                Color(0xFFF97316),
                                Color(0xFFFDE047),
                                Color(0xFFDC2626)
                            )
                        ),
                        radius = baseRadius + 2f,
                        center = center,
                        style = Stroke(width = 3.5f)
                    )

                    // Dancing flame tongues with bezier curves
                    val flamePath = Path().apply {
                        val flameH = baseRadius * 0.5f * pulse
                        // Center top flame
                        moveTo(center.x - 8f, center.y - baseRadius)
                        cubicTo(
                            center.x - 4f, center.y - baseRadius - flameH * 0.8f,
                            center.x + 4f, center.y - baseRadius - flameH,
                            center.x, center.y - baseRadius - flameH * 1.2f
                        )
                        cubicTo(
                            center.x + 6f, center.y - baseRadius - flameH * 0.6f,
                            center.x + 8f, center.y - baseRadius - 2f,
                            center.x + 8f, center.y - baseRadius
                        )
                        close()

                        // Left flame
                        moveTo(center.x - baseRadius * 0.7f, center.y - baseRadius * 0.5f)
                        cubicTo(
                            center.x - baseRadius * 0.9f, center.y - baseRadius * 0.8f,
                            center.x - baseRadius * 0.8f, center.y - baseRadius - 8f,
                            center.x - baseRadius * 0.6f, center.y - baseRadius * 0.9f
                        )
                        close()

                        // Right flame
                        moveTo(center.x + baseRadius * 0.7f, center.y - baseRadius * 0.5f)
                        cubicTo(
                            center.x + baseRadius * 0.9f, center.y - baseRadius * 0.8f,
                            center.x + baseRadius * 0.8f, center.y - baseRadius - 8f,
                            center.x + baseRadius * 0.6f, center.y - baseRadius * 0.9f
                        )
                        close()
                    }
                    drawPath(flamePath, brush = Brush.verticalGradient(listOf(Color(0xFFFDE047), Color(0xFFF97316))))

                    // Rising floating embers
                    for (i in 0 until 6) {
                        val ang = (i * 60f + rotation * 0.4f) * (Math.PI / 180.0)
                        val dist = baseRadius + 4f + ((i * 3f + shimmer * 12f) % 10f)
                        val ex = center.x + dist * cos(ang).toFloat()
                        val ey = center.y + dist * sin(ang).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFFFDE047) else Color(0xFFFB923C),
                            radius = 2.2f * pulse,
                            center = Offset(ex, ey)
                        )
                    }
                }

                "lightning_storm" -> {
                    // Storm aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF38BDF8).copy(alpha = 0.45f), Color(0xFF9333EA).copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.45f
                        ),
                        radius = baseRadius * 1.45f,
                        center = center
                    )

                    // Electric ring
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFF0284C7), Color(0xFF38BDF8))),
                        radius = baseRadius + 2f,
                        center = center,
                        style = Stroke(width = 3f)
                    )

                    // Crackling zigzag lightning arcs
                    rotate(rotation * 1.8f, pivot = center) {
                        val boltPath = Path().apply {
                            for (b in 0 until 3) {
                                val deg = b * 120.0
                                val rad = Math.toRadians(deg)
                                val sx = center.x + baseRadius * cos(rad).toFloat()
                                val sy = center.y + baseRadius * sin(rad).toFloat()
                                moveTo(sx, sy)
                                val mx = center.x + (baseRadius + 8f) * cos(rad + 0.15).toFloat()
                                val my = center.y + (baseRadius + 8f) * sin(rad + 0.15).toFloat()
                                lineTo(mx, my)
                                val ex = center.x + (baseRadius + 14f) * cos(rad - 0.1).toFloat()
                                val ey = center.y + (baseRadius + 14f) * sin(rad - 0.1).toFloat()
                                lineTo(ex, ey)
                            }
                        }
                        drawPath(boltPath, color = Color.White, style = Stroke(width = 2.5f))
                        drawPath(boltPath, color = Color(0xFF38BDF8).copy(alpha = 0.6f), style = Stroke(width = 4.5f))
                    }

                    // Electric spark nodes
                    for (i in 0 until 5) {
                        val ang = (i * 72f + counterRotation * 0.8f) * (Math.PI / 180.0)
                        val sx = center.x + (baseRadius + 3f) * cos(ang).toFloat()
                        val sy = center.y + (baseRadius + 3f) * sin(ang).toFloat()
                        drawCircle(color = Color(0xFFE0E7FF), radius = 2.4f, center = Offset(sx, sy))
                    }
                }

                "spectral_soul" -> {
                    // Emerald ghost mist
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF10B981).copy(alpha = 0.45f), Color(0xFF047857).copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.4f * pulse
                        ),
                        radius = baseRadius * 1.4f * pulse,
                        center = center
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFFA7F3D0), Color(0xFF34D399))),
                        radius = baseRadius + 1.8f,
                        center = center,
                        style = Stroke(width = 3f)
                    )

                    // 3 orbiting soul wisps with teardrop tails
                    rotate(counterRotation * 1.2f, pivot = center) {
                        for (w in 0 until 3) {
                            val rad = Math.toRadians((w * 120.0))
                            val wx = center.x + (baseRadius + 4f) * cos(rad).toFloat()
                            val wy = center.y + (baseRadius + 4f) * sin(rad).toFloat()
                            drawCircle(color = Color(0xFFA7F3D0), radius = 3.6f * pulse, center = Offset(wx, wy))
                            drawCircle(color = Color.White, radius = 1.8f, center = Offset(wx, wy))
                        }
                    }
                }

                "sakura_blossom" -> {
                    // Romantic floral pink halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFF472B6).copy(alpha = 0.45f), Color(0xFFFDA4AF).copy(alpha = 0.15f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.35f
                        ),
                        radius = baseRadius * 1.35f,
                        center = center
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFFF472B6), Color(0xFFFB7185), Color(0xFFFBCFE8), Color(0xFFF472B6))),
                        radius = baseRadius + 1.8f,
                        center = center,
                        style = Stroke(width = 2.8f)
                    )

                    // 5 orbiting sakura petals
                    rotate(rotation * 0.7f, pivot = center) {
                        for (i in 0 until 5) {
                            val rad = Math.toRadians((i * 72.0))
                            val px = center.x + (baseRadius + 4f) * cos(rad).toFloat()
                            val py = center.y + (baseRadius + 4f) * sin(rad).toFloat()
                            drawOval(
                                color = Color(0xFFF472B6),
                                topLeft = Offset(px - 3.5f, py - 6f),
                                size = androidx.compose.ui.geometry.Size(7f, 12f)
                            )
                            drawCircle(color = Color(0xFFFEF08A), radius = 1.8f, center = Offset(px, py))
                        }
                    }
                }

                "astral_dragon" -> {
                    // Deep cosmic dragon aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF06B6D4).copy(alpha = 0.4f), Color(0xFF3B82F6).copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.45f
                        ),
                        radius = baseRadius * 1.45f,
                        center = center
                    )

                    // Serpentine dragon body curving along upper perimeter
                    rotate(rotation * 0.4f, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFF38BDF8), Color(0xFF06B6D4))),
                            startAngle = -160f,
                            sweepAngle = 220f,
                            useCenter = false,
                            topLeft = Offset(center.x - baseRadius - 4f, center.y - baseRadius - 4f),
                            size = androidx.compose.ui.geometry.Size((baseRadius + 4f) * 2f, (baseRadius + 4f) * 2f),
                            style = Stroke(width = 4.5f)
                        )

                        // Dragon head horns at top right
                        val hx = center.x + (baseRadius + 4f) * cos(Math.toRadians(60.0)).toFloat()
                        val hy = center.y + (baseRadius + 4f) * sin(Math.toRadians(60.0)).toFloat()
                        drawCircle(color = Color(0xFFF59E0B), radius = 4.5f, center = Offset(hx, hy))
                        drawCircle(color = Color(0xFF00F0FF), radius = 2.2f, center = Offset(hx - 1f, hy - 1f))
                    }

                    // Floating dragon pearls
                    rotate(counterRotation * 0.8f, pivot = center) {
                        for (p in 0 until 2) {
                            val rad = Math.toRadians((p * 180.0))
                            val ox = center.x + (baseRadius + 5f) * cos(rad).toFloat()
                            val oy = center.y + (baseRadius + 5f) * sin(rad).toFloat()
                            drawCircle(color = Color(0xFFFDE047), radius = 3.2f * pulse, center = Offset(ox, oy))
                        }
                    }
                }

                "black_hole" -> {
                    // Deep void distortion
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF7C3AED).copy(alpha = 0.5f), Color(0xFFF59E0B).copy(alpha = 0.25f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.5f
                        ),
                        radius = baseRadius * 1.5f,
                        center = center
                    )

                    // Tilted accretion disk
                    rotate(rotation * 1.5f, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF06B6D4), Color(0xFFF59E0B))
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(center.x - baseRadius - 6f, center.y - baseRadius * 0.6f),
                            size = androidx.compose.ui.geometry.Size((baseRadius + 6f) * 2f, baseRadius * 1.2f),
                            style = Stroke(width = 3.8f)
                        )
                    }

                    // Event horizon boundary
                    drawCircle(color = Color(0xFF09090B), radius = baseRadius - 1f, center = center, style = Stroke(width = 2.2f))
                }

                "cyber_mecha" -> {
                    // Neon cyan & lime ring
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFF00F0FF), Color(0xFF10B981), Color(0xFF38BDF8), Color(0xFF00F0FF))),
                        radius = baseRadius + 2f,
                        center = center,
                        style = Stroke(width = 3.2f)
                    )

                    // Angular robotic mecha cat ears
                    val leftEar = Path().apply {
                        val lx = center.x - baseRadius * 0.65f
                        val ly = center.y - baseRadius * 0.75f
                        moveTo(lx - 10f, ly + 2f)
                        lineTo(lx - 4f, ly - baseRadius * 0.45f * pulse)
                        lineTo(lx + 4f, ly)
                        close()
                    }
                    drawPath(leftEar, color = Color(0xFF1E293B), style = Fill)
                    drawPath(leftEar, color = Color(0xFF00F0FF), style = Stroke(width = 2.5f))

                    val rightEar = Path().apply {
                        val rx = center.x + baseRadius * 0.65f
                        val ry = center.y - baseRadius * 0.75f
                        moveTo(rx + 10f, ry + 2f)
                        lineTo(rx + 4f, ry - baseRadius * 0.45f * pulse)
                        lineTo(rx - 4f, ry)
                        close()
                    }
                    drawPath(rightEar, color = Color(0xFF1E293B), style = Fill)
                    drawPath(rightEar, color = Color(0xFF00F0FF), style = Stroke(width = 2.5f))

                    // Reticle HUD brackets
                    rotate(rotation * 0.3f, pivot = center) {
                        for (i in 0 until 4) {
                            val ang = Math.toRadians((i * 90.0) + 45.0)
                            val bx = center.x + (baseRadius + 5f) * cos(ang).toFloat()
                            val by = center.y + (baseRadius + 5f) * sin(ang).toFloat()
                            drawCircle(color = Color(0xFF10B981), radius = 2.5f, center = Offset(bx, by))
                        }
                    }
                }

                "glitch_matrix" -> {
                    // Triple chromatic aberration rings
                    val jitterX = ((fastGlitch * 10f) % 3f) - 1.5f
                    val jitterY = ((fastGlitch * 8f) % 2f) - 1f

                    // Cyan offset
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = 0.8f),
                        radius = baseRadius + 2f,
                        center = Offset(center.x - 2f + jitterX, center.y),
                        style = Stroke(width = 2f)
                    )
                    // Magenta offset
                    drawCircle(
                        color = Color(0xFFEC4899).copy(alpha = 0.8f),
                        radius = baseRadius + 2f,
                        center = Offset(center.x + 2f - jitterX, center.y),
                        style = Stroke(width = 2f)
                    )
                    // Yellow matrix ring
                    drawCircle(
                        color = Color(0xFFFDE047).copy(alpha = 0.7f),
                        radius = baseRadius + 2f,
                        center = Offset(center.x, center.y - 1.5f + jitterY),
                        style = Stroke(width = 1.8f)
                    )

                    // Matrix tick bars
                    rotate(rotation * 1.6f, pivot = center) {
                        for (i in 0 until 6) {
                            val deg = i * 60.0
                            val rad = Math.toRadians(deg)
                            val sx = center.x + (baseRadius + 2f) * cos(rad).toFloat()
                            val sy = center.y + (baseRadius + 2f) * sin(rad).toFloat()
                            val ex = center.x + (baseRadius + 7f) * cos(rad).toFloat()
                            val ey = center.y + (baseRadius + 7f) * sin(rad).toFloat()
                            drawLine(color = Color(0xFF10B981), start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 2.2f)
                        }
                    }
                }

                "rgb_headset" -> {
                    // Headband arch over top
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                            )
                        ),
                        startAngle = -170f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(center.x - baseRadius - 6f, center.y - baseRadius - 6f),
                        size = androidx.compose.ui.geometry.Size((baseRadius + 6f) * 2f, (baseRadius + 6f) * 2f),
                        style = Stroke(width = 4.2f)
                    )

                    // Left & Right earcups with glowing RGB rim
                    val lx = center.x - baseRadius - 3f
                    val ly = center.y
                    drawOval(color = Color(0xFF1E1F22), topLeft = Offset(lx - 5f, ly - 10f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                    drawOval(color = Color(0xFF00F0FF), topLeft = Offset(lx - 5f, ly - 10f), size = androidx.compose.ui.geometry.Size(8f, 20f), style = Stroke(width = 1.8f))

                    val rx = center.x + baseRadius + 3f
                    val ry = center.y
                    drawOval(color = Color(0xFF1E1F22), topLeft = Offset(rx - 3f, ly - 10f), size = androidx.compose.ui.geometry.Size(8f, 20f))
                    drawOval(color = Color(0xFFEC4899), topLeft = Offset(rx - 3f, ly - 10f), size = androidx.compose.ui.geometry.Size(8f, 20f), style = Stroke(width = 1.8f))

                    // Bouncing equalizer bars on left and right
                    val eqH = 6f * pulse
                    drawLine(color = Color(0xFF38BDF8), start = Offset(lx - 8f, ly - eqH), end = Offset(lx - 8f, ly + eqH), strokeWidth = 2f)
                    drawLine(color = Color(0xFFF472B6), start = Offset(rx + 8f, ry - eqH), end = Offset(rx + 8f, ry + eqH), strokeWidth = 2f)
                }

                "archangel_wings" -> {
                    // Holy divine rays
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFEF08A).copy(alpha = 0.5f), Color(0xFFF59E0B).copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.5f
                        ),
                        radius = baseRadius * 1.5f,
                        center = center
                    )

                    // Holy floating golden halo
                    val haloY = center.y - baseRadius - 6f
                    drawOval(
                        color = Color(0xFFFDE047),
                        topLeft = Offset(center.x - baseRadius * 0.45f, haloY - 4f),
                        size = androidx.compose.ui.geometry.Size(baseRadius * 0.9f, 8f),
                        style = Stroke(width = 2.5f)
                    )

                    // Left golden wing
                    rotate(wingFlap, pivot = Offset(center.x - baseRadius, center.y)) {
                        val lw = Path().apply {
                            val sx = center.x - baseRadius
                            val sy = center.y
                            moveTo(sx, sy - 5f)
                            cubicTo(
                                sx - baseRadius * 0.7f, sy - baseRadius * 0.6f,
                                sx - baseRadius * 0.9f, sy - baseRadius * 0.1f,
                                sx - 6f, sy + 10f
                            )
                            close()
                        }
                        drawPath(lw, color = Color(0xFFFEF08A), style = Fill)
                        drawPath(lw, color = Color(0xFFF59E0B), style = Stroke(width = 2.2f))
                    }

                    // Right golden wing
                    rotate(-wingFlap, pivot = Offset(center.x + baseRadius, center.y)) {
                        val rw = Path().apply {
                            val sx = center.x + baseRadius
                            val sy = center.y
                            moveTo(sx, sy - 5f)
                            cubicTo(
                                sx + baseRadius * 0.7f, sy - baseRadius * 0.6f,
                                sx + baseRadius * 0.9f, sy - baseRadius * 0.1f,
                                sx + 6f, sy + 10f
                            )
                            close()
                        }
                        drawPath(rw, color = Color(0xFFFEF08A), style = Fill)
                        drawPath(rw, color = Color(0xFFF59E0B), style = Stroke(width = 2.2f))
                    }
                }

                "royal_crown" -> {
                    // Golden sunburst aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFFDE047).copy(alpha = 0.45f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.4f
                        ),
                        radius = baseRadius * 1.4f,
                        center = center
                    )

                    // Base golden circlet ring
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFFF59E0B), Color(0xFFFEF08A), Color(0xFFD97706), Color(0xFFF59E0B))),
                        radius = baseRadius + 1.8f,
                        center = center,
                        style = Stroke(width = 3.2f)
                    )

                    // Imperial 5-point crown path
                    val crownPath = Path().apply {
                        val topY = center.y - baseRadius
                        val crownH = baseRadius * 0.5f * pulse
                        moveTo(center.x - baseRadius * 0.7f, topY)
                        lineTo(center.x - baseRadius * 0.65f, topY - crownH * 0.7f)
                        lineTo(center.x - baseRadius * 0.35f, topY - crownH * 0.35f)
                        lineTo(center.x, topY - crownH * 1.15f) // center tall spire
                        lineTo(center.x + baseRadius * 0.35f, topY - crownH * 0.35f)
                        lineTo(center.x + baseRadius * 0.65f, topY - crownH * 0.7f)
                        lineTo(center.x + baseRadius * 0.7f, topY)
                        close()
                    }
                    drawPath(crownPath, brush = Brush.verticalGradient(listOf(Color(0xFFFEF08A), Color(0xFFF59E0B))))
                    drawPath(crownPath, color = Color(0xFFD97706), style = Stroke(width = 2f))

                    // Center jewel sparkle
                    val tipY = center.y - baseRadius - (baseRadius * 0.5f * pulse * 1.15f)
                    drawCircle(color = Color(0xFF38BDF8), radius = 2.8f, center = Offset(center.x, tipY))
                    drawCircle(color = Color.White, radius = 1.2f, center = Offset(center.x, tipY))
                }

                "aurora_frost" -> {
                    // Northern lights aurora veil
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF2DD4BF).copy(alpha = 0.45f), Color(0xFFA78BFA).copy(alpha = 0.2f), Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.45f
                        ),
                        radius = baseRadius * 1.45f,
                        center = center
                    )

                    // Ice crystal ring
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(Color(0xFF2DD4BF), Color(0xFF38BDF8), Color(0xFFA78BFA), Color(0xFF2DD4BF))),
                        radius = baseRadius + 2f,
                        center = center,
                        style = Stroke(width = 3f)
                    )

                    // 4 rotating 6-pointed snowflakes
                    rotate(rotation * 0.8f, pivot = center) {
                        for (s in 0 until 4) {
                            val rad = Math.toRadians((s * 90.0) + 45.0)
                            val fx = center.x + (baseRadius + 4f) * cos(rad).toFloat()
                            val fy = center.y + (baseRadius + 4f) * sin(rad).toFloat()
                            // Snowflake cross lines
                            drawLine(color = Color.White, start = Offset(fx - 4f, fy), end = Offset(fx + 4f, fy), strokeWidth = 1.8f)
                            drawLine(color = Color.White, start = Offset(fx, fy - 4f), end = Offset(fx, fy + 4f), strokeWidth = 1.8f)
                            drawLine(color = Color(0xFF38BDF8), start = Offset(fx - 2.5f, fy - 2.5f), end = Offset(fx + 2.5f, fy + 2.5f), strokeWidth = 1.5f)
                        }
                    }
                }

                else -> {
                    // Rotating chromatic halo
                    rotate(rotation, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF5865F2),
                                    Color(0xFFEB459E),
                                    Color(0xFFFEE75C),
                                    Color(0xFF57F287),
                                    Color(0xFF5865F2)
                                )
                            ),
                            radius = baseRadius + 1.5f,
                            center = center,
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Canvas overlay rendering live profile banner effects
 * (Falling Sakura, Cosmic Supernova meteors, Storm lightning, Phoenix embers)
 */
@Composable
fun ProfileEffectOverlay(
    effectKey: String?,
    modifier: Modifier = Modifier
) {
    if (effectKey.isNullOrBlank()) return

    val infiniteTransition = rememberInfiniteTransition(label = "banner_fx_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bannerProgress"
    )
    val flashPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashPulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (effectKey) {
            "eff_sakura" -> {
                // Falling gentle cherry blossom petals with swaying paths
                val petalCount = 16
                for (i in 0 until petalCount) {
                    val xOffset = ((i * 47f) % w)
                    val sway = sin((progress * Math.PI * 2.0 + i)).toFloat() * 16f
                    val currentY = ((progress * h * 1.5f + (i * 31f)) % (h + 30f)) - 15f
                    val currentX = (xOffset + sway + w) % w

                    val petalColor = if (i % 2 == 0) Color(0xFFF472B6).copy(alpha = 0.85f) else Color(0xFFFDA4AF).copy(alpha = 0.75f)
                    val petalRadius = 4f + (i % 3) * 1.5f

                    drawOval(
                        color = petalColor,
                        topLeft = Offset(currentX - petalRadius, currentY - petalRadius * 1.5f),
                        size = androidx.compose.ui.geometry.Size(petalRadius * 2f, petalRadius * 3f)
                    )
                }
            }

            "eff_supernova" -> {
                // Cosmic meteors and starbursts
                val meteorCount = 6
                for (m in 0 until meteorCount) {
                    val startX = (w * 0.15f) + (m * (w * 0.18f))
                    val travelDist = progress * (h * 1.8f)
                    val mx = (startX + travelDist * 0.9f) % (w + 60f) - 30f
                    val my = (travelDist) % (h + 40f) - 20f

                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(Color.Transparent, Color(0xFF38BDF8), Color.White)
                        ),
                        start = Offset(mx - 32f, my - 24f),
                        end = Offset(mx, my),
                        strokeWidth = 2.2f
                    )
                    drawCircle(color = Color.White, radius = 2.5f, center = Offset(mx, my))
                }

                // Twinkling background stars
                for (s in 0 until 10) {
                    val sx = (s * 83f) % w
                    val sy = (s * 41f) % h
                    val starAlpha = (sin(progress * Math.PI * 3.0 + s).toFloat() + 1f) * 0.45f
                    drawCircle(color = Color(0xFFFDE047).copy(alpha = starAlpha), radius = 1.8f, center = Offset(sx, sy))
                }
            }

            "eff_lightning" -> {
                // Ambient electric flash illumination
                if (flashPulse > 0.75f) {
                    drawRect(color = Color(0xFF818CF8).copy(alpha = (flashPulse - 0.75f) * 0.8f))
                }

                // Branching electric lightning bolt
                if (flashPulse > 0.65f) {
                    val boltPath = Path().apply {
                        val startX = w * 0.45f
                        moveTo(startX, 0f)
                        lineTo(startX + 18f, h * 0.3f)
                        lineTo(startX - 12f, h * 0.55f)
                        lineTo(startX + 25f, h * 0.78f)
                        lineTo(startX + 8f, h)
                    }
                    drawPath(boltPath, color = Color(0xFF38BDF8).copy(alpha = 0.5f), style = Stroke(width = 5f))
                    drawPath(boltPath, color = Color.White, style = Stroke(width = 2.5f))

                    // Secondary branch
                    val subBranch = Path().apply {
                        moveTo(w * 0.45f + 18f, h * 0.3f)
                        lineTo(w * 0.45f + 48f, h * 0.48f)
                    }
                    drawPath(subBranch, color = Color(0xFFC084FC), style = Stroke(width = 2f))
                }
            }

            "eff_phoenix" -> {
                // Rising fire embers and thermal sparks
                val emberCount = 18
                for (e in 0 until emberCount) {
                    val xBase = (e * 59f) % w
                    val sway = sin((progress * Math.PI * 3.0 + e)).toFloat() * 14f
                    val currentY = h - ((progress * h * 1.4f + (e * 23f)) % (h + 20f))
                    val currentX = (xBase + sway + w) % w
                    val emberAlpha = (currentY / h).coerceIn(0.2f, 1f)

                    val color = if (e % 3 == 0) Color(0xFFFDE047) else if (e % 3 == 1) Color(0xFFF97316) else Color(0xFFEF4444)
                    drawCircle(color = color.copy(alpha = emberAlpha), radius = 2.6f, center = Offset(currentX, currentY))
                }
            }
        }
    }
}

