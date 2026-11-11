package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    onApplyTheme: (ChatBackgroundTheme) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedThemeId by remember { mutableStateOf(currentTheme?.id ?: "default") }
    var customImageUrl by remember { mutableStateOf(currentTheme?.imageUrl) }
    var opacity by remember { mutableFloatStateOf(currentTheme?.opacity ?: 0.35f) }
    var isUploading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch {
                val cloudUrl = CloudinaryHelper.uploadImage(context, uri)
                customImageUrl = cloudUrl ?: uri.toString()
                selectedThemeId = "custom_image"
                isUploading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp)),
            color = DiscordDarkPrimary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkSidebar)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Wallpaper,
                            contentDescription = null,
                            tint = DiscordBlurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Fond de discussion",
                                color = DiscordTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personnalisez l'arrière-plan du salon ou DM",
                                color = DiscordTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextLight)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "THÈMES ET AMBIANCES PRÉDÉFINIS",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Themes Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(ShopCatalog.initialChatThemes) { theme ->
                            val isSelected = selectedThemeId == theme.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) DiscordBlurple else DiscordDarkBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedThemeId = theme.id
                                        customImageUrl = null
                                    },
                                color = Color(theme.primaryColor)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
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
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Upload custom photo card
                        item {
                            val isSelected = selectedThemeId == "custom_image"
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) DiscordBlurple else DiscordDarkBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                color = DiscordDarkCard
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!customImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = customImageUrl,
                                            contentDescription = "Image personnalisée",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .background(Color(0x99000000))
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = DiscordBlurple,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isUploading) "Chargement..." else if (customImageUrl != null) "Changer photo" else "Importer photo",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPACITÉ DU FOND",
                            color = DiscordTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(opacity * 100).toInt()}%",
                            color = DiscordTextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.1f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = DiscordBlurple,
                            activeTrackColor = DiscordBlurple,
                            inactiveTrackColor = DiscordDarkInput
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Bottom Action buttons
                Surface(
                    color = DiscordDarkSidebar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Annuler", color = DiscordTextMuted)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                val theme = if (selectedThemeId == "custom_image" && customImageUrl != null) {
                                    ChatBackgroundTheme(
                                        id = "custom",
                                        name = "Fond personnalisé",
                                        primaryColor = 0xFF1E1F22,
                                        imageUrl = customImageUrl,
                                        opacity = opacity
                                    )
                                } else {
                                    val base = ShopCatalog.initialChatThemes.find { it.id == selectedThemeId }
                                        ?: ShopCatalog.initialChatThemes.first()
                                    base.copy(opacity = opacity)
                                }
                                onApplyTheme(theme)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("apply_chat_theme_button")
                        ) {
                            Text("Appliquer le fond", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
