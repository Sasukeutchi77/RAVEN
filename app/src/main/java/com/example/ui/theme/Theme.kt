package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.model.AppThemeMode

val LocalAppThemeMode = compositionLocalOf { AppThemeMode.DARK }
val LocalAccentColor = compositionLocalOf { DiscordBlurple }

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentColorHex: Long = 0xFF5865F2,
    content: @Composable () -> Unit
) {
    val accent = Color(accentColorHex)
    val scheme = when (themeMode) {
        AppThemeMode.DARK -> darkColorScheme(
            primary = accent,
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
        AppThemeMode.AMOLED -> darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF09090B),
            onPrimaryContainer = DiscordTextWhite,
            secondary = DiscordTextMuted,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF1F2024),
            onSecondaryContainer = DiscordTextLight,
            tertiary = DiscordGreen,
            onTertiary = Color.White,
            background = Color.Black,
            onBackground = DiscordTextLight,
            surface = Color(0xFF0A0A0C),
            onSurface = DiscordTextWhite,
            surfaceVariant = Color(0xFF16171A),
            onSurfaceVariant = DiscordTextLight,
            surfaceContainer = Color.Black,
            outline = Color(0xFF27272A),
            error = DiscordRed,
            onError = Color.White
        )
        AppThemeMode.LIGHT -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF2F3F5),
            onPrimaryContainer = Color(0xFF1E1F22),
            secondary = Color(0xFF4E5058),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE3E5E8),
            onSecondaryContainer = Color(0xFF313338),
            tertiary = DiscordGreen,
            onTertiary = Color.White,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF2E3035),
            surface = Color(0xFFF2F3F5),
            onSurface = Color(0xFF1E1F22),
            surfaceVariant = Color(0xFFE3E5E8),
            onSurfaceVariant = Color(0xFF313338),
            surfaceContainer = Color(0xFFEBEDEF),
            outline = Color(0xFFD1D5DB),
            error = DiscordRed,
            onError = Color.White
        )
    }
    CompositionLocalProvider(
        LocalAppThemeMode provides themeMode,
        LocalAccentColor provides accent
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}
