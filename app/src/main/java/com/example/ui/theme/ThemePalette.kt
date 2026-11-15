package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.AppThemeMode

data class RavenPalette(
    val primaryBg: Color,
    val sidebarBg: Color,
    val chatBg: Color,
    val inputBg: Color,
    val cardBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color = DiscordBlurple
)

@Composable
fun ravenPalette(): RavenPalette {
    val accent = LocalAccentColor.current
    return when (LocalAppThemeMode.current) {
        AppThemeMode.DARK -> RavenPalette(
            primaryBg = DiscordDarkPrimary,
            sidebarBg = DiscordDarkSidebar,
            chatBg = DiscordDarkChat,
            inputBg = DiscordDarkInput,
            cardBg = DiscordDarkCard,
            border = DiscordDarkBorder,
            textPrimary = DiscordTextWhite,
            textSecondary = DiscordTextLight,
            textMuted = DiscordTextMuted,
            accent = accent
        )
        AppThemeMode.AMOLED -> RavenPalette(
            primaryBg = Color.Black,
            sidebarBg = Color(0xFF070709),
            chatBg = Color.Black,
            inputBg = Color(0xFF141519),
            cardBg = Color(0xFF0D0E12),
            border = Color(0xFF23252C),
            textPrimary = DiscordTextWhite,
            textSecondary = DiscordTextLight,
            textMuted = DiscordTextMuted,
            accent = accent
        )
        AppThemeMode.LIGHT -> RavenPalette(
            primaryBg = Color(0xFFE3E5E8),
            sidebarBg = Color(0xFFF2F3F5),
            chatBg = Color(0xFFFFFFFF),
            inputBg = Color(0xFFEBEDEF),
            cardBg = Color(0xFFF7F8FA),
            border = Color(0xFFD4D7DD),
            textPrimary = Color(0xFF1E1F22),
            textSecondary = Color(0xFF313338),
            textMuted = Color(0xFF5C616B),
            accent = accent
        )
    }
}
