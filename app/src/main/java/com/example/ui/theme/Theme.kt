package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DiscordBlurple,
    onPrimary = Color.White,
    primaryContainer = DiscordDarkSidebar,
    onPrimaryContainer = DiscordTextWhite,
    secondary = DiscordTextMuted,
    onSecondary = Color.White,
    secondaryContainer = DiscordDarkBorder,
    onSecondaryContainer = DiscordTextLight,
    tertiary = DiscordGreen,
    onTertiary = Color.White,
    background = DiscordDarkChat,
    onBackground = DiscordTextLight,
    surface = DiscordDarkSidebar,
    onSurface = DiscordTextWhite,
    surfaceVariant = DiscordDarkInput,
    onSurfaceVariant = DiscordTextLight,
    surfaceContainer = DiscordDarkPrimary,
    outline = DiscordDarkBorder,
    error = DiscordRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Discord is dark by default, creating a sleek, immersive gaming/community aesthetic
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
