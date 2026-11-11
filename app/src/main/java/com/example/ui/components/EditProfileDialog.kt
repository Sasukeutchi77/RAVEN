package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.model.User
import com.example.ui.theme.*
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.launch

@Composable
fun EditProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onSaveProfile: (
        name: String,
        pronouns: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        bannerUrl: String?,
        bannerColor: Long,
        customStatus: String,
        customStatusEmoji: String,
        activity: String,
        email: String,
        phone: String
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(user.name) }
    var pronouns by remember { mutableStateOf(user.pronouns) }
    var bio by remember { mutableStateOf(user.bio) }
    var customStatus by remember { mutableStateOf(user.customStatus) }
    var customStatusEmoji by remember { mutableStateOf(user.customStatusEmoji.ifBlank { "🦅" }) }
    var activityText by remember { mutableStateOf(user.activity) }
    var emailText by remember { mutableStateOf(user.email ?: "") }
    var phoneText by remember { mutableStateOf(user.phone ?: "") }

    var avatarUrl by remember { mutableStateOf(user.avatarUrl) }
    var avatarColor by remember { mutableStateOf(user.avatarColor) }
    var bannerUrl by remember { mutableStateOf(user.bannerUrl) }
    var bannerColor by remember { mutableStateOf(user.bannerColor) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isUploadingBanner by remember { mutableStateOf(false) }

    // Launcher for Avatar picker
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            coroutineScope.launch {
                val cloudUrl = CloudinaryHelper.uploadImage(context, uri)
                avatarUrl = cloudUrl ?: uri.toString()
                isUploadingAvatar = false
            }
        }
    }

    // Launcher for Banner picker
    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingBanner = true
            coroutineScope.launch {
                val cloudUrl = CloudinaryHelper.uploadImage(context, uri)
                bannerUrl = cloudUrl ?: uri.toString()
                isUploadingBanner = false
            }
        }
    }

    val statusIconPresets = listOf("chat", "code", "game", "headset", "coffee", "rocket", "bolt", "star", "music", "heart")
    val avatarColors = listOf(0xFF5865F2, 0xFF57F287, 0xFFFEE75C, 0xFFED4245, 0xFFEB459E, 0xFF35373C, 0xFF1E1F22)
    val bannerColors = listOf(0xFF5865F2, 0xFF2B2D31, 0xFF1E1F22, 0xFF111214, 0xFF0D5C75, 0xFF5B2C6F, 0xFF7D1A1A)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = DiscordDarkPrimary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkSidebar)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modifier le profil",
                        color = DiscordTextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextLight)
                    }
                }

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // Preview & Image Edit Header
                    Text(
                        text = "APERÇU DE LA CARTE DE PROFIL",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = DiscordDarkCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            // Banner with Edit Action
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .background(Color(bannerColor))
                            ) {
                                if (!bannerUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = bannerUrl,
                                        contentDescription = "Bannière",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Banner edit button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xCC000000),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clickable {
                                            bannerPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isUploadingBanner) "Chargement..." else "Bannière",
                                            color = Color.White,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Avatar + live details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(Color(avatarColor))
                                            .clickable {
                                                avatarPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = name.take(1).uppercase(),
                                                color = Color.White,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Camera edit icon on avatar
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(DiscordBlurple)
                                            .clickable {
                                                avatarPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Changer l'avatar",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = name.ifBlank { "Utilisateur" },
                                            color = DiscordTextWhite,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (pronouns.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• $pronouns",
                                                color = DiscordTextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${name.lowercase().replace(" ", "")}${user.tag}",
                                        color = DiscordTextMuted,
                                        fontSize = 12.sp
                                    )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            AppStatusIcon(iconKey = customStatusEmoji, size = 14.dp, tint = DiscordBlurple)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = customStatus,
                                                color = DiscordTextLight,
                                                fontSize = 12.sp
                                            )
                                        }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Preset color selectors
                    Text(
                        text = "COULEUR DE BANNIÈRE PRÉDÉFINIE",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bannerColors) { col ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        width = if (bannerColor == col && bannerUrl == null) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        bannerColor = col
                                        bannerUrl = null
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "COULEUR D'AVATAR (SI AUCUNE PHOTO)",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(avatarColors) { col ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(
                                        width = if (avatarColor == col && avatarUrl == null) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        avatarColor = col
                                        avatarUrl = null
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = DiscordDarkBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Identity Fields
                    Text(
                        text = "INFORMATIONS DU PROFIL",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileInputField(
                        label = "NOM D'AFFICHAGE",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Votre nom ou pseudo",
                        testTag = "edit_name_input"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ProfileInputField(
                        label = "PRONOMS",
                        value = pronouns,
                        onValueChange = { pronouns = it },
                        placeholder = "ex: il/lui, elle/la, iel, they/them",
                        testTag = "edit_pronouns_input"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Icon Selector
                    Text(
                        text = "ICÔNE DE STATUT",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(statusIconPresets) { iconKey ->
                            val isSelected = customStatusEmoji == iconKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DiscordBlurple.copy(alpha = 0.25f) else DiscordDarkCard,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, DiscordBlurple) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { customStatusEmoji = iconKey }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppStatusIcon(
                                        iconKey = iconKey,
                                        size = 20.dp,
                                        tint = if (isSelected) DiscordBlurple else DiscordTextLight
                                    )
                                }
                            }
                        }
                    }

                    ProfileInputField(
                        label = "MESSAGE DE STATUT",
                        value = customStatus,
                        onValueChange = { customStatus = it },
                        placeholder = "Que faites-vous actuellement ?",
                        testTag = "edit_status_input"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bio (About Me)
                    Text(
                        text = "À PROPOS DE MOI (BIO)",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 300) bio = it },
                        placeholder = { Text("Parlez un peu de vous...", color = DiscordTextMuted, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("edit_bio_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DiscordDarkInput,
                            unfocusedContainerColor = DiscordDarkInput,
                            focusedBorderColor = DiscordBlurple,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = DiscordTextWhite,
                            unfocusedTextColor = DiscordTextLight
                        ),
                        supportingText = {
                            Text(
                                text = "${bio.length}/300 caractères",
                                color = DiscordTextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current activity
                    ProfileInputField(
                        label = "ACTIVITÉ ACTUELLE",
                        value = activityText,
                        onValueChange = { activityText = it },
                        placeholder = "ex: Code sur Raven Android, Joue à Minecraft...",
                        testTag = "edit_activity_input"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DiscordDarkBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Account Contact Info
                    Text(
                        text = "COORDONNÉES DU COMPTE",
                        color = DiscordTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileInputField(
                        label = "ADRESSE E-MAIL",
                        value = emailText,
                        onValueChange = { emailText = it },
                        placeholder = "exemple@raven.app",
                        testTag = "edit_email_input"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ProfileInputField(
                        label = "NUMÉRO DE TÉLÉPHONE",
                        value = phoneText,
                        onValueChange = { phoneText = it },
                        placeholder = "+33 6 ** ** ** 42",
                        testTag = "edit_phone_input"
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Bottom Action Bar
                Surface(
                    color = DiscordDarkSidebar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Annuler", color = DiscordTextLight, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                onSaveProfile(
                                    name.trim().ifBlank { user.name },
                                    pronouns.trim(),
                                    bio.trim(),
                                    avatarUrl,
                                    avatarColor,
                                    bannerUrl,
                                    bannerColor,
                                    customStatus.trim(),
                                    customStatusEmoji,
                                    activityText.trim(),
                                    emailText.trim(),
                                    phoneText.trim()
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_profile_button")
                        ) {
                            Text("Enregistrer les modifications", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = DiscordTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = DiscordTextMuted, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DiscordDarkInput,
                unfocusedContainerColor = DiscordDarkInput,
                focusedBorderColor = DiscordBlurple,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = DiscordTextWhite,
                unfocusedTextColor = DiscordTextLight
            )
        )
    }
}
