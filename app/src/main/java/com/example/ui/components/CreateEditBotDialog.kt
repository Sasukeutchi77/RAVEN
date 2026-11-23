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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.BotTokenManager
import com.example.model.BotEventTrigger
import com.example.model.BotPermission
import com.example.model.RavenBot
import com.example.ui.theme.*
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditBotDialog(
    existingBot: RavenBot? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        handle: String,
        description: String,
        avatarUrl: String?,
        avatarColor: Long,
        isPublicCatalog: Boolean,
        defaultPermissions: Set<BotPermission>,
        subscribedEvents: Set<BotEventTrigger>,
        allowDirectMessages: Boolean,
        allowGroupMentions: Boolean,
        welcomeDmMessage: String,
        starterPresetId: String?
    ) -> Unit,
    onDeleteBot: (() -> Unit)? = null
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = existingBot != null

    var name by remember(existingBot?.id) { mutableStateOf(existingBot?.name ?: "") }
    var handle by remember(existingBot?.id) { mutableStateOf(existingBot?.handle ?: "") }
    var handleTouched by remember(existingBot?.id) { mutableStateOf(isEditing) }
    var description by remember(existingBot?.id) { mutableStateOf(existingBot?.description ?: "") }
    var avatarUrl by remember(existingBot?.id) { mutableStateOf(existingBot?.avatarUrl ?: "") }
    var avatarColor by remember(existingBot?.id) { mutableLongStateOf(existingBot?.avatarColor ?: 0xFF5865F2L) }
    var isPublicCatalog by remember(existingBot?.id) { mutableStateOf(existingBot?.isPublicCatalog ?: false) }
    var defaultPerms by remember(existingBot?.id) {
        mutableStateOf(
            existingBot?.defaultPermissions ?: setOf(
                BotPermission.SEND_MESSAGES,
                BotPermission.USE_SLASH_COMMANDS,
                BotPermission.READ_MESSAGE_EVENTS
            )
        )
    }
    var subscribedEvents by remember(existingBot?.id) {
        mutableStateOf(
            existingBot?.subscribedEvents ?: setOf(
                BotEventTrigger.ON_COMMAND_EXECUTED,
                BotEventTrigger.ON_MESSAGE_CREATE
            )
        )
    }
    var allowDirectMessages by remember(existingBot?.id) { mutableStateOf(existingBot?.allowDirectMessages ?: true) }
    var allowGroupMentions by remember(existingBot?.id) { mutableStateOf(existingBot?.allowGroupMentions ?: true) }
    var starterPresetId by remember(existingBot?.id) {
        mutableStateOf(existingBot?.starterPresetId ?: com.example.model.BotStarterCatalog.presets.first().id)
    }
    var welcomeDmMessage by remember(existingBot?.id) {
        mutableStateOf(
            existingBot?.welcomeDmMessage
                ?: com.example.model.BotStarterCatalog.presets.first().welcomeDmMessage
        )
    }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploadingAvatar = true
                avatarUrl = CloudinaryHelper.uploadImage(context, uri)
                isUploadingAvatar = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("create_edit_bot_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(avatarColor)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar du bot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditing) "Configurer ${existingBot?.name}" else "Nouveau Bot RAVEN",
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEditing) "@${existingBot?.handle}" else "Génération automatique d'un jeton SHA-256 sécurisé",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (!isEditing) {
                Text("MODÈLE WHATSAPP PRÊT À L'EMPLOI", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(com.example.model.BotStarterCatalog.presets, key = { it.id }) { preset ->
                        val selected = preset.id == starterPresetId
                        FilterChip(
                            selected = selected,
                            onClick = {
                                starterPresetId = preset.id
                                avatarColor = preset.defaultColor
                                welcomeDmMessage = preset.welcomeDmMessage
                                if (description.isBlank()) description = preset.subtitle
                            },
                            label = { Text(preset.title, fontSize = 12.sp) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("bot_preset_${preset.id}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (!handleTouched) {
                        handle = BotTokenManager.normalizeHandle("", it)
                    }
                },
                label = { Text("Nom du bot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("bot_name_input")
            )

            OutlinedTextField(
                value = handle,
                onValueChange = {
                    handleTouched = true
                    handle = it.lowercase().replace(" ", "-")
                },
                label = { Text("Identifiant unique (@handle)") },
                prefix = { Text("@", color = palette.textMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("bot_handle_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description & rôle du bot") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("bot_desc_input")
            )

            // Avatar URL + Picker
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("URL Avatar (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("bot_avatar_input")
                )
                FilledTonalButton(
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !isUploadingAvatar,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Choisir avatar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isUploadingAvatar) "Envoi..." else "Galerie", fontSize = 12.sp)
                }
            }

            // Accent Color Selector
            val colorSwatches = listOf(0xFF5865F2L, 0xFF23A55AL, 0xFFEB459EL, 0xFFF0B232L, 0xFF00A8FCL, 0xFFEF4444L)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(colorSwatches) { hex ->
                    val selected = hex == avatarColor
                    Surface(
                        shape = CircleShape,
                        color = Color(hex),
                        border = if (selected) BorderStroke(2.dp, Color.White) else null,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { avatarColor = hex }
                    ) {}
                }
            }

            // Public Catalog visibility switch
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Partager dans le catalogue public", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Permet aux administrateurs d'autres serveurs d'ajouter ce bot (sans exposer votre jeton).", color = palette.textMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isPublicCatalog,
                        onCheckedChange = { isPublicCatalog = it },
                        modifier = Modifier.testTag("bot_public_catalog_switch")
                    )
                }
            }

            // WhatsApp DM & Group Mentions Configuration
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FONCTIONNEMENT WHATSAPP (DM & GROUPES)", color = DiscordGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Autoriser les messages privés (DM)", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Permet aux membres de discuter en 1-à-1 avec ce bot dans l'onglet DM.", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = allowDirectMessages,
                            onCheckedChange = { allowDirectMessages = it },
                            modifier = Modifier.testTag("bot_allow_dm_switch")
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Répondre dans les groupes & DMs (@handle)", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Active l'invocation par @${handle.ifBlank { "bot" }} ou /commande dans les groupes privés.", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = allowGroupMentions,
                            onCheckedChange = { allowGroupMentions = it },
                            modifier = Modifier.testTag("bot_allow_group_mentions_switch")
                        )
                    }
                    if (allowDirectMessages) {
                        OutlinedTextField(
                            value = welcomeDmMessage,
                            onValueChange = { welcomeDmMessage = it },
                            label = { Text("Message d'accueil DM ({user}, {bot}, {handle})") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().testTag("bot_welcome_dm_input")
                        )
                    }
                }
            }

            Text("PERMISSIONS PAR DÉFAUT", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            BotPermission.entries.forEach { perm ->
                val checked = perm in defaultPerms
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            defaultPerms = if (checked) defaultPerms - perm else defaultPerms + perm
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            defaultPerms = if (isChecked) defaultPerms + perm else defaultPerms - perm
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(perm.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(perm.description, color = palette.textMuted, fontSize = 11.sp)
                    }
                }
            }

            Text("ÉVÉNEMENTS ÉCOUTÉS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            BotEventTrigger.entries.forEach { ev ->
                val selected = ev in subscribedEvents
                FilterChip(
                    selected = selected,
                    onClick = {
                        subscribedEvents = if (selected) subscribedEvents - ev else subscribedEvents + ev
                    },
                    label = { Text(ev.label, fontSize = 12.sp) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Annuler")
                }
                Button(
                    onClick = {
                        onSave(
                            name,
                            handle,
                            description,
                            avatarUrl.ifBlank { null },
                            avatarColor,
                            isPublicCatalog,
                            defaultPerms,
                            subscribedEvents,
                            allowDirectMessages,
                            allowGroupMentions,
                            welcomeDmMessage,
                            if (isEditing) existingBot?.starterPresetId else starterPresetId
                        )
                        onDismiss()
                    },
                    enabled = name.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save_bot_button")
                ) {
                    Text(if (isEditing) "Enregistrer" else "Créer le bot", color = Color.White)
                }
            }

            if (isEditing && onDeleteBot != null) {
                HorizontalDivider(color = palette.border)
                if (!confirmDelete) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiscordRed),
                        border = BorderStroke(1.dp, DiscordRed),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_bot_prompt_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Supprimer ce bot définitivement")
                    }
                } else {
                    Button(
                        onClick = {
                            onDeleteBot()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_delete_bot_button")
                    ) {
                        Text("Confirmer la suppression totale", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
