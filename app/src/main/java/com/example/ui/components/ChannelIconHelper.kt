package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.Channel
import com.example.model.ChannelType

fun resolveCatalogIconVector(
    iconKey: String?,
    fallbackType: ChannelType = ChannelType.TEXT
): ImageVector {
    return when (iconKey?.lowercase()?.trim()) {
        "tag" -> Icons.Default.Tag
        "chat" -> Icons.Default.ChatBubbleOutline
        "announcement" -> Icons.Default.Campaign
        "game" -> Icons.Default.SportsEsports
        "code" -> Icons.Default.Code
        "music" -> Icons.Default.MusicNote
        "mic" -> Icons.Default.VolumeUp
        "stage" -> Icons.Default.Podcasts
        "rules" -> Icons.Default.Gavel
        "shield" -> Icons.Default.Shield
        "star" -> Icons.Default.Star
        "event" -> Icons.Default.Event
        "photo" -> Icons.Default.PhotoLibrary
        "help" -> Icons.Default.SupportAgent
        "rocket" -> Icons.Default.RocketLaunch
        "forum" -> Icons.Default.Forum
        else -> when (fallbackType) {
            ChannelType.STAGE -> Icons.Default.Podcasts
            ChannelType.VOICE -> Icons.Default.VolumeUp
            ChannelType.ANNOUNCEMENT -> Icons.Default.Campaign
            ChannelType.FORUM -> Icons.Default.Forum
            else -> Icons.Default.Tag
        }
    }
}

fun resolveChannelIconVector(channel: Channel): ImageVector {
    if (!channel.iconKey.isNullOrBlank()) {
        return resolveCatalogIconVector(channel.iconKey, channel.type)
    }
    return when {
        channel.isLocked -> Icons.Default.Lock
        channel.isPrivate -> Icons.Default.Lock
        channel.type == ChannelType.STAGE -> Icons.Default.Podcasts
        channel.type == ChannelType.VOICE -> Icons.Default.VolumeUp
        channel.type == ChannelType.ANNOUNCEMENT -> Icons.Default.Campaign
        channel.type == ChannelType.FORUM -> Icons.Default.Forum
        else -> Icons.Default.Tag
    }
}
