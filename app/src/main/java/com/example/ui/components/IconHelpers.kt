package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BadgeRarity
import com.example.ui.theme.*

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

    fun getReactionVector(key: String): ImageVector {
        return when (key.lowercase().trim()) {
            "heart", "❤️" -> Icons.Default.Favorite
            "fire", "🔥" -> Icons.Default.LocalFireDepartment
            "thumb_up", "👍" -> Icons.Default.ThumbUp
            "star", "✨" -> Icons.Default.Star
            "smile", "😂" -> Icons.Default.Mood
            "rocket", "🚀" -> Icons.Default.RocketLaunch
            "celebrate", "🎉" -> Icons.Default.Celebration
            "bolt", "⚡", "🦅" -> Icons.Default.Bolt
            else -> Icons.Default.ThumbUp
        }
    }

    fun getReactionColor(key: String): Color {
        return when (key.lowercase().trim()) {
            "heart", "❤️" -> DiscordRed
            "fire", "🔥" -> DiscordAmber
            "thumb_up", "👍" -> DiscordBlurple
            "star", "✨" -> Color(0xFFFEE75C)
            "smile", "😂" -> DiscordGreen
            "rocket", "🚀" -> DiscordBlurple
            "celebrate", "🎉" -> Color(0xFFEB459E)
            "bolt", "⚡", "🦅" -> Color(0xFFFEE75C)
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
    val vector = AppIconSets.getReactionVector(iconKey)
    val color = tint ?: AppIconSets.getReactionColor(iconKey)
    Icon(
        imageVector = vector,
        contentDescription = iconKey,
        tint = color,
        modifier = modifier.size(size)
    )
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

    val gradientBrush = when (decorationKey) {
        "magic_mist" -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(
                Color(0xFF00F0FF),
                Color(0xFFC026D3),
                Color(0xFF7928CA),
                Color(0xFF00F0FF)
            )
        )
        "infinity_swirl" -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(
                Color(0xFF38BDF8),
                Color(0xFF818CF8),
                Color(0xFFEC4899),
                Color(0xFF38BDF8)
            )
        )
        "cyber_halo" -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(
                Color(0xFF10B981),
                Color(0xFFFBBF24),
                Color(0xFF06B6D4),
                Color(0xFF10B981)
            )
        )
        "raven_wings" -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(
                Color(0xFF5865F2),
                Color(0xFF2B2D31),
                Color(0xFF8B5CF6),
                Color(0xFF5865F2)
            )
        )
        else -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(Color(0xFF5865F2), Color(0xFFEB459E), Color(0xFF5865F2))
        )
    }

    Box(
        modifier = modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .border(
                width = 3.dp,
                brush = gradientBrush,
                shape = CircleShape
            )
    )
}
