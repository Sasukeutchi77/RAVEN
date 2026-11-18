package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CommunityPermissions
import com.example.model.CommunityVisibility
import com.example.model.Server
import com.example.ui.theme.*
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditCommunityDialog(
    existingServer: Server? = null,
    isOwner: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        uniqueHandle: String,
        iconText: String,
        imageUrl: String?,
        bannerUrl: String?,
        bannerColorHex: Long,
        isPublic: Boolean,
        categoryTag: String,
        rulesGuidelines: String,
        homeWelcomeMessage: String
    ) -> Unit,
    onDeleteCommunity: (() -> Unit)? = null,
    onLeaveCommunity: (() -> Unit)? = null
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = existingServer != null

    var name by remember(existingServer?.id) { mutableStateOf(existingServer?.name ?: "") }
    var handle by remember(existingServer?.id) { mutableStateOf(existingServer?.uniqueHandle ?: "") }
    var handleEditedManually by remember(existingServer?.id) { mutableStateOf(isEditing && !existingServer?.uniqueHandle.isNullOrBlank()) }
    var description by remember(existingServer?.id) { mutableStateOf(existingServer?.description ?: "") }
    var imageUrl by remember(existingServer?.id) { mutableStateOf(existingServer?.imageUrl ?: "") }
    var bannerUrl by remember(existingServer?.id) { mutableStateOf(existingServer?.bannerUrl ?: "") }
    var bannerColorHex by remember(existingServer?.id) {
        mutableLongStateOf(existingServer?.customBannerColorHex ?: existingServer?.iconBgColor ?: 0xFF5865F2L)
    }
    var selectedIconKey by remember(existingServer?.id) {
        mutableStateOf(existingServer?.iconText?.takeIf { it.length > 2 } ?: "initials")
    }
    var isPublic by remember(existingServer?.id) { mutableStateOf(existingServer?.isPublic ?: true) }
    var categoryTag by remember(existingServer?.id) { mutableStateOf(existingServer?.categoryTag ?: "Communauté") }
    var rules by remember(existingServer?.id) { mutableStateOf(existingServer?.rulesGuidelines ?: "Respect, entraide et bienveillance entre membres.") }
    var welcomeMsg by remember(existingServer?.id) { mutableStateOf(existingServer?.homeWelcomeMessage ?: "Bienvenue dans notre communauté Raven !") }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingMedia = true
                imageUrl = CloudinaryHelper.uploadImage(context, uri)
                isUploadingMedia = false
            }
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingMedia = true
                bannerUrl = CloudinaryHelper.uploadImage(context, uri)
                isUploadingMedia = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("create_edit_community_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 460.dp, max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEditing) "Modifier la communauté" else "Créer une communauté avancée",
                color = palette.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            // Live Hero Preview Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.cardBg,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                    if (bannerUrl.isNotBlank()) {
                        AsyncImage(
                            model = bannerUrl,
                            contentDescription = "Aperçu bannière communauté",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(bannerColorHex), Color(bannerColorHex).copy(alpha = 0.4f), palette.cardBg)
                                    )
                                )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(bannerColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Avatar communauté",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = name.take(2).uppercase().ifBlank { "RV" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank { "Ma Communauté" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            val displayHandle = CommunityPermissions.normalizeHandle(handle, name.ifBlank { "raven" })
                            Text(
                                text = "@$displayHandle • ${if (isPublic) "Publique" else "Privée"}",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            if (isUploadingMedia) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = palette.accent)
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (!handleEditedManually) {
                        handle = CommunityPermissions.normalizeHandle(it, "raven")
                    }
                },
                label = { Text("Nom de la communauté") },
                placeholder = { Text("ex. Raven Creators Hub") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("community_name_input")
            )

            OutlinedTextField(
                value = handle,
                onValueChange = {
                    handleEditedManually = true
                    handle = it.removePrefix("@")
                },
                label = { Text("Identifiant unique (@handle)") },
                prefix = { Text("@", color = palette.accent, fontWeight = FontWeight.Bold) },
                placeholder = { Text("raven-creators") },
                supportingText = {
                    val norm = CommunityPermissions.normalizeHandle(handle, name.ifBlank { "raven" })
                    Text("Identifiant réservé : @$norm", fontSize = 11.sp)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("community_handle_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Présentez votre communauté en quelques phrases...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("community_desc_input")
            )

            // Image & Banner pickers + URLs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("URL Image (avatar)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("community_image_url_input")
                )
                FilledTonalButton(
                    onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically).testTag("community_pick_avatar_btn")
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Choisir une image avatar")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bannerUrl,
                    onValueChange = { bannerUrl = it },
                    label = { Text("URL Bannière") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("community_banner_url_input")
                )
                FilledTonalButton(
                    onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterVertically).testTag("community_pick_banner_btn")
                ) {
                    Icon(Icons.Default.Wallpaper, contentDescription = "Choisir une bannière")
                }
            }

            // Banner Accent Color & Category
            Text("COULEUR DE THÈME", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val colors = listOf(0xFF5865F2L, 0xFFEB459EL, 0xFF23A55AL, 0xFFF0B232L, 0xFF00A8FCL, 0xFF8B5CF6L)
                items(colors) { hex ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .clickable { bannerColorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bannerColorHex == hex) {
                            Icon(Icons.Default.Check, contentDescription = "Sélectionné", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Text("VISIBILITÉ", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CommunityVisibility.entries.forEach { vis ->
                    val selected = isPublic == vis.isPublic
                    FilterChip(
                        selected = selected,
                        onClick = { isPublic = vis.isPublic },
                        leadingIcon = {
                            Icon(
                                if (vis.isPublic) Icons.Default.Public else Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(if (vis.isPublic) "Publique" else "Privée", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f).testTag("community_vis_${vis.name}")
                    )
                }
            }

            Text("CATÉGORIE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("Communauté", "Gaming", "Développement", "Musique", "Éducation", "Création")) { cat ->
                    FilterChip(
                        selected = categoryTag == cat,
                        onClick = { categoryTag = cat },
                        label = { Text(cat, fontSize = 11.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = welcomeMsg,
                onValueChange = { welcomeMsg = it },
                label = { Text("Message d'accueil (Page d'accueil)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("community_welcome_input")
            )

            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                label = { Text("Règlement et lignes directrices") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("community_rules_input")
            )

            Button(
                onClick = {
                    val finalIcon = if (selectedIconKey == "initials") {
                        name.trim().take(2).uppercase().ifBlank { "RV" }
                    } else selectedIconKey
                    onSave(
                        name.trim(),
                        description.trim(),
                        handle.trim().ifBlank { name.trim() },
                        finalIcon,
                        imageUrl.trim().ifBlank { null },
                        bannerUrl.trim().ifBlank { null },
                        bannerColorHex,
                        isPublic,
                        categoryTag,
                        rules.trim(),
                        welcomeMsg.trim()
                    )
                    onDismiss()
                },
                enabled = name.trim().length >= 2 && !isUploadingMedia,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("community_save_submit")
            ) {
                Icon(if (isEditing) Icons.Default.Save else Icons.Default.AddCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditing) "Enregistrer les modifications" else "Créer la communauté", fontWeight = FontWeight.Bold)
            }

            if (isEditing && !isOwner && onLeaveCommunity != null) {
                OutlinedButton(
                    onClick = {
                        onLeaveCommunity()
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, DiscordRed),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("community_leave_btn")
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = DiscordRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Quitter cette communauté", color = DiscordRed, fontWeight = FontWeight.Bold)
                }
            }

            if (isEditing && isOwner && onDeleteCommunity != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    border = BorderStroke(1.dp, DiscordRed),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("community_delete_btn")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DiscordRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Supprimer la communauté", color = DiscordRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeleteConfirm && onDeleteCommunity != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = palette.cardBg,
            title = { Text("Supprimer définitivement ?", color = DiscordRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Cette action supprimera ${existingServer?.name}, ses salons, ses annonces et libérera son identifiant.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCommunity()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                    modifier = Modifier.testTag("community_confirm_delete_btn")
                ) {
                    Text("Supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Annuler", color = palette.textMuted)
                }
            }
        )
    }
}
