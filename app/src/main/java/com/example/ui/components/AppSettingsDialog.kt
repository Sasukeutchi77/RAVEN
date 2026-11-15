package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppSettingsState
import com.example.model.AppThemeMode
import com.example.model.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    settings: AppSettingsState,
    currentUser: User,
    isCloudSyncing: Boolean = false,
    lastCloudSyncTime: String? = null,
    isFirebaseSignedIn: Boolean = false,
    firebaseUserEmail: String? = null,
    onRequestNotificationPermission: () -> Unit = {},
    onSyncToCloud: () -> Unit = {},
    onRestoreFromCloud: () -> Unit = {},
    onUpdateSettings: ((AppSettingsState) -> AppSettingsState) -> Unit,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("app_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = DiscordBlurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Paramètres de Raven",
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Theme Mode selector
            SettingsGroupCard(title = "APPARENCE & THÈME", palette = palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = settings.themeMode == mode
                        Surface(
                            color = if (isSelected) DiscordBlurple.copy(alpha = 0.2f) else palette.inputBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) DiscordBlurple else palette.border
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onUpdateSettings { it.copy(themeMode = mode) }
                                }
                                .testTag("theme_mode_${mode.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        AppThemeMode.DARK -> Icons.Default.DarkMode
                                        AppThemeMode.AMOLED -> Icons.Default.Brightness2
                                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    },
                                    contentDescription = mode.label,
                                    tint = if (isSelected) DiscordBlurple else palette.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (mode) {
                                        AppThemeMode.DARK -> "Sombre"
                                        AppThemeMode.AMOLED -> "AMOLED"
                                        AppThemeMode.LIGHT -> "Clair"
                                    },
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("COULEUR D'ACCENTUATION", color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val accents = listOf(
                        0xFF5865F2L,
                        0xFFEB459EL,
                        0xFF23A55AL,
                        0xFFF0B232L,
                        0xFF00A8FCL,
                        0xFFED4245L
                    )
                    accents.forEach { hex ->
                        val isSel = settings.accentColorHex == hex
                        Surface(
                            color = Color(hex),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            border = BorderStroke(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) Color.White else palette.border
                            ),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    onUpdateSettings { it.copy(accentColorHex = hex) }
                                }
                                .testTag("accent_color_$hex")
                        ) {
                            if (isSel) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("STYLE D'AFFICHAGE DU CHAT", color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.model.ChatDisplayStyle.entries.forEach { style ->
                        val isSel = settings.chatDisplayStyle == style
                        FilterChip(
                            selected = isSel,
                            onClick = { onUpdateSettings { it.copy(chatDisplayStyle = style) } },
                            label = { Text(style.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f).testTag("chat_style_${style.name}")
                        )
                    }
                }

                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                SettingsToggleRow(
                    icon = Icons.Default.BlurOn,
                    title = "Effet Glassmorphism",
                    subtitle = "Cartes translucides et bordures lumineuses",
                    checked = settings.glassmorphismEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(glassmorphismEnabled = enabled) }
                    },
                    testTag = "setting_glass_toggle"
                )
            }

            // Voice & Audio settings
            SettingsGroupCard(title = "VOIX & AUDIO", palette = palette) {
                SettingsToggleRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Effets sonores & Soundboard",
                    subtitle = "Jouer les sons de cloche, Orbs et Soundboard",
                    checked = settings.soundEffectsEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(soundEffectsEnabled = enabled) }
                    },
                    testTag = "setting_sound_toggle"
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                SettingsToggleRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Suppression du bruit Krisp",
                    subtitle = "Filtrer les bruits de fond en salon vocal",
                    checked = settings.voiceNoiseSuppression,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(voiceNoiseSuppression = enabled) }
                    },
                    testTag = "setting_noise_toggle"
                )
            }

            // Notifications & Privacy
            SettingsGroupCard(title = "CONFIDENTIALITÉ & NOTIFICATIONS", palette = palette) {
                SettingsToggleRow(
                    icon = Icons.Default.AlternateEmail,
                    title = "Alertes de mentions (@)",
                    subtitle = "Recevoir une alerte dans la boîte de réception",
                    checked = settings.mentionNotifications,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(mentionNotifications = enabled) }
                    },
                    testTag = "setting_mentions_toggle"
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Notifications locales Android",
                    subtitle = "Recevoir des notifications système pour XP, mentions et événements",
                    checked = settings.localNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) onRequestNotificationPermission()
                        onUpdateSettings { it.copy(localNotificationsEnabled = enabled) }
                    },
                    testTag = "setting_local_notif_toggle"
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                SettingsToggleRow(
                    icon = Icons.Default.Lock,
                    title = "Messages privés des membres",
                    subtitle = "Autoriser les membres du serveur à vous écrire",
                    checked = settings.allowMemberDms,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(allowMemberDms = enabled) }
                    },
                    testTag = "setting_dm_toggle"
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 6.dp))
                SettingsToggleRow(
                    icon = Icons.Default.SportsEsports,
                    title = "Afficher l'activité en cours",
                    subtitle = "Partager le jeu ou l'outil sur votre profil",
                    checked = settings.showActivityStatus,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(showActivityStatus = enabled) }
                    },
                    testTag = "setting_activity_toggle"
                )
            }

            // Cloud Sync Firebase
            SettingsGroupCard(title = "SYNCHRONISATION CLOUD FIREBASE", palette = palette) {
                Surface(
                    color = if (isFirebaseSignedIn) DiscordGreen.copy(alpha = 0.14f) else DiscordAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isFirebaseSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isFirebaseSignedIn) DiscordGreen else DiscordAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFirebaseSignedIn) {
                                "Connecté : ${firebaseUserEmail ?: currentUser.name}"
                            } else {
                                "Mode local — Connectez Google via votre Profil pour activer le Cloud"
                            },
                            color = if (isFirebaseSignedIn) DiscordGreen else DiscordAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (lastCloudSyncTime != null) {
                        "Dernière synchronisation : $lastCloudSyncTime (Orbs, XP, Notes privées, Boost & Dossiers)"
                    } else {
                        "Sauvegardez et restaurez votre profil, Orbs, XP, notes privées, serveurs boostés et dossiers dans Firebase Firestore."
                    },
                    color = palette.textMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncToCloud,
                        enabled = !isCloudSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.weight(1f).testTag("cloud_sync_now_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCloudSyncing) "Sync..." else "Synchroniser",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onRestoreFromCloud,
                        enabled = !isCloudSyncing,
                        modifier = Modifier.weight(1f).testTag("cloud_restore_button")
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restaurer", color = palette.textPrimary, fontSize = 12.sp)
                    }
                }
            }

            // Account Summary
            SettingsGroupCard(title = "MON COMPTE", palette = palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${currentUser.name}${currentUser.tag}",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentUser.email ?: "jarvismd45@gmail.com"} • Niv. ${currentUser.level}",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenProfile()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Profil", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    palette: RavenPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = ravenPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = palette.textMuted, fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordBlurple)
        )
    }
}
