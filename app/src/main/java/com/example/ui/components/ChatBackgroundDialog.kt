package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.ChatBackgroundTheme
import com.example.model.ShopCatalog
import com.example.ui.theme.*
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.launch

@Composable
fun ChatBackgroundDialog(
    currentTheme: ChatBackgroundTheme?,
    onDismiss: () -> Unit,
    onApplyTheme: (ChatBackgroundTheme) -> Unit,
    title: String = "Fond & thème",
    subtitle: String = "Choisissez un thème ou une image de votre galerie",
    allowServerScopeToggle: Boolean = false,
    initialApplyToWholeServer: Boolean = false,
    canSetOfficialServerDefault: Boolean = false,
    onApplyServerWide: ((theme: ChatBackgroundTheme, makeOfficialForAll: Boolean) -> Unit)? = null,
    onResetTheme: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val palette = ravenPalette()

    val initialIsCustomImg = !currentTheme?.imageUrl.isNullOrBlank()
    val initialMatchingPreset = if (!initialIsCustomImg && currentTheme != null) {
        ShopCatalog.initialChatThemes.find { it.id == currentTheme.id }?.id
    } else null

    var selectedThemeId by remember {
        mutableStateOf(
            when {
                initialIsCustomImg -> "custom_image"
                initialMatchingPreset != null -> initialMatchingPreset
                else -> ShopCatalog.initialChatThemes.first().id
            }
        )
    }
    var customImageUrl by remember { mutableStateOf(currentTheme?.imageUrl) }
    var opacity by remember { mutableFloatStateOf((currentTheme?.opacity ?: 0.50f).coerceIn(0.15f, 0.90f)) }
    var isUploading by remember { mutableStateOf(false) }
    var applyToWholeServer by remember { mutableStateOf(initialApplyToWholeServer) }
    var makeOfficialForAll by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch {
                val persisted = CloudinaryHelper.persistImage(context, uri, subDir = "backgrounds")
                customImageUrl = persisted.ifBlank { uri.toString() }
                selectedThemeId = "custom_image"
                isUploading = false
            }
        }
    }

    val previewTheme = remember(selectedThemeId, customImageUrl, opacity) {
        if (selectedThemeId == "custom_image" && !customImageUrl.isNullOrBlank()) {
            ChatBackgroundTheme(
                id = "custom_image",
                name = "Image galerie",
                primaryColor = 0xFF1E1F22,
                secondaryColor = 0xFF111214,
                imageUrl = customImageUrl,
                opacity = opacity
            )
        } else {
            val base = ShopCatalog.initialChatThemes.find { it.id == selectedThemeId }
                ?: ShopCatalog.initialChatThemes.first()
            base.copy(opacity = opacity)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp)),
            color = palette.primaryBg,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.sidebarBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = DiscordBlurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textSecondary)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Live Preview Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(106.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    ) {
                        if (!previewTheme.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = previewTheme.imageUrl,
                                contentDescription = "Aperçu du fond",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(previewTheme.primaryColor),
                                                Color(previewTheme.secondaryColor ?: previewTheme.primaryColor)
                                            )
                                        )
                                    )
                            )
                        }
                        val scrimAlpha = (0.78f - opacity * 0.62f).coerceIn(0.16f, 0.78f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = scrimAlpha))
                                .padding(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = palette.cardBg.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Aperçu en direct • ${previewTheme.name}",
                                        color = palette.textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = DiscordBlurple.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Bienvenue dans la communauté !",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Gallery Upload Row
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selectedThemeId == "custom_image") 1.5.dp else 1.dp,
                            color = if (selectedThemeId == "custom_image") DiscordBlurple else palette.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isUploading) {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("pick_gallery_background_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DiscordBlurple.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!customImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = customImageUrl,
                                        contentDescription = "Image choisie",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = DiscordBlurple
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isUploading -> "Enregistrement de l'image..."
                                        !customImageUrl.isNullOrBlank() -> "Image de votre galerie sélectionnée"
                                        else -> "Choisir une photo dans ma galerie"
                                    },
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (!customImageUrl.isNullOrBlank()) "Appuyez pour changer de photo" else "JPG, PNG, WEBP depuis votre téléphone",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (!customImageUrl.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        customImageUrl = null
                                        if (selectedThemeId == "custom_image") {
                                            selectedThemeId = ShopCatalog.initialChatThemes.first().id
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Retirer la photo", tint = DiscordRed)
                                }
                            } else {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
                            }
                        }
                    }

                    // Server-wide vs Channel scope toggle
                    if (allowServerScopeToggle && onApplyServerWide != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "APPLIQUER À :",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !applyToWholeServer,
                                    onClick = {
                                        applyToWholeServer = false
                                        makeOfficialForAll = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("Ce salon", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).testTag("bg_scope_channel_chip")
                                )
                                FilterChip(
                                    selected = applyToWholeServer,
                                    onClick = { applyToWholeServer = true },
                                    leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("Tout le serveur", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).testTag("bg_scope_server_chip")
                                )
                            }
                            if (applyToWholeServer && canSetOfficialServerDefault) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { makeOfficialForAll = !makeOfficialForAll }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = makeOfficialForAll,
                                        onCheckedChange = { makeOfficialForAll = it }
                                    )
                                    Text(
                                        text = "Définir comme thème officiel pour tous les membres",
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "THÈMES PRÉDÉFINIS (${ShopCatalog.initialChatThemes.size})",
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Themes Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ShopCatalog.initialChatThemes, key = { it.id }) { theme ->
                            val isSelected = selectedThemeId == theme.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) DiscordBlurple else palette.border,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedThemeId = theme.id
                                    }
                                    .testTag("theme_preset_${theme.id}"),
                                color = Color(theme.primaryColor)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(theme.primaryColor),
                                                    Color(theme.secondaryColor ?: theme.primaryColor)
                                                )
                                            )
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = theme.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.BottomStart)
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(DiscordBlurple)
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Sélectionné",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "VISIBILITÉ DU FOND",
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(opacity * 100).toInt()}%",
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.15f..0.90f,
                            colors = SliderDefaults.colors(
                                thumbColor = DiscordBlurple,
                                activeTrackColor = DiscordBlurple,
                                inactiveTrackColor = palette.inputBg
                            )
                        )
                    }
                }

                // Bottom Action buttons
                Surface(
                    color = palette.sidebarBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onResetTheme != null) {
                            TextButton(
                                onClick = {
                                    onResetTheme()
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("reset_chat_theme_button")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = DiscordRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Par défaut", color = DiscordRed, fontSize = 12.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onDismiss) {
                                Text("Annuler", color = palette.textMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                enabled = !isUploading,
                                onClick = {
                                    if (selectedThemeId == "default" && onResetTheme != null && !applyToWholeServer) {
                                        onResetTheme()
                                    } else if (applyToWholeServer && onApplyServerWide != null) {
                                        onApplyServerWide(previewTheme, makeOfficialForAll)
                                    } else {
                                        onApplyTheme(previewTheme)
                                    }
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("apply_chat_theme_button")
                            ) {
                                Text("Appliquer", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
