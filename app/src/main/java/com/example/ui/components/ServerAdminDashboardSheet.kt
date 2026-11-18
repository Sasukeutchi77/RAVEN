package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.CommunityUiState
import com.example.util.CloudinaryHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerAdminDashboardSheet(
    server: Server,
    currentUser: User,
    communityUiState: CommunityUiState,
    onUpdateIdentityAndTheme: (
        name: String,
        handle: String,
        desc: String,
        iconText: String,
        iconPresetKey: String?,
        imageUrl: String?,
        bannerUrl: String?,
        accentHex: Long,
        secondaryHex: Long,
        surfaceStyle: ServerThemeSurfaceStyle,
        categoryTag: String
    ) -> Unit,
    onUpsertCategory: (categoryId: String?, name: String, isPrivate: Boolean, allowedRoleIds: List<String>) -> Unit,
    onMoveCategoryOrder: (categoryId: String, delta: Int) -> Unit,
    onDeleteCategory: (categoryName: String) -> Unit,
    onUpsertChannel: (
        channelId: String?,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        iconKey: String?,
        slowmodeSeconds: Int,
        isReadOnly: Boolean,
        isPrivate: Boolean,
        allowedRoles: List<UserRole>,
        allowedRoleIds: List<String>,
        maxParticipants: Int,
        bitrateKbps: Int
    ) -> Unit,
    onMoveChannelOrder: (channelId: String, delta: Int) -> Unit,
    onDeleteChannel: (channelId: String) -> Unit,
    onSaveOrUpdateRole: (name: String, colorHex: Long, secondaryHex: Long?, perms: Set<RolePermission>, existingRoleId: String?) -> Unit,
    onMoveRoleOrder: (roleId: String, delta: Int) -> Unit,
    onDeleteCustomRole: (roleId: String) -> Unit,
    onUpdateWelcomeAndGoodbye: (ServerWelcomeConfig, homeWelcomeMessage: String) -> Unit,
    onUpdateNotificationConfig: (ServerNotificationConfig) -> Unit,
    onUpdateRulesAndVerification: (List<String>, String, Boolean, ServerVerificationLevel) -> Unit,
    onUpdateModerationConfig: (ServerModerationConfig) -> Unit,
    onUpdatePrivacyConfig: (Boolean, ServerPrivacyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val isOwner = CommunityPermissions.isOwner(server, currentUser.id)
    val effectiveRole = CommunityPermissions.effectiveRole(server, currentUser.id)
    val isStrictAdmin = isOwner || effectiveRole == UserRole.ADMIN
    val canEditIdentity = isStrictAdmin || CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_COMMUNITY)
    val canManageChannels = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_CHANNELS)
    val canManageRoles = CommunityPermissions.hasPermission(server, currentUser.id, RolePermission.MANAGE_ROLES)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabLabels = listOf(
        "Identité & Thème",
        "Catégories & Salons",
        "Rôles & Perms",
        "Accueil & Notifs",
        "Sécurité & Privé"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("server_admin_dashboard_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 520.dp, max = 720.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(server.customization.theme.accentColorHex))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Administration • ${server.name}",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isStrictAdmin) "Accès complet (Propriétaire / Admin)" else "Mode restreint (Permissions vérifiées côté Firestore)",
                        color = if (isStrictAdmin) DiscordGreen else DiscordAmber,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textMuted)
                }
            }

            if (communityUiState.errorMessage != null) {
                Surface(
                    color = DiscordRed.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = communityUiState.errorMessage,
                        color = DiscordRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (communityUiState.feedbackMessage != null) {
                Surface(
                    color = DiscordGreen.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = communityUiState.feedbackMessage,
                        color = DiscordGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(server.customization.theme.accentColorHex),
                edgePadding = 12.dp
            ) {
                tabLabels.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("admin_tab_$idx")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> AdminIdentityAndThemeTab(
                        server = server,
                        canEdit = canEditIdentity,
                        onSave = onUpdateIdentityAndTheme
                    )
                    1 -> AdminCategoriesAndChannelsTab(
                        server = server,
                        canManageChannels = canManageChannels,
                        isAdmin = isStrictAdmin,
                        onUpsertCategory = onUpsertCategory,
                        onMoveCategoryOrder = onMoveCategoryOrder,
                        onDeleteCategory = onDeleteCategory,
                        onUpsertChannel = onUpsertChannel,
                        onMoveChannelOrder = onMoveChannelOrder,
                        onDeleteChannel = onDeleteChannel
                    )
                    2 -> AdminRolesAndPermissionsTab(
                        server = server,
                        canManageRoles = canManageRoles,
                        onSaveOrUpdateRole = onSaveOrUpdateRole,
                        onMoveRoleOrder = onMoveRoleOrder,
                        onDeleteCustomRole = onDeleteCustomRole
                    )
                    3 -> AdminWelcomeAndNotificationsTab(
                        server = server,
                        canEdit = canEditIdentity,
                        onSaveWelcome = onUpdateWelcomeAndGoodbye,
                        onSaveNotifications = onUpdateNotificationConfig
                    )
                    else -> AdminSecurityRulesAndPrivacyTab(
                        server = server,
                        isStrictAdmin = isStrictAdmin,
                        onSaveRulesAndVerification = onUpdateRulesAndVerification,
                        onSaveModeration = onUpdateModerationConfig,
                        onSavePrivacy = onUpdatePrivacyConfig
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminIdentityAndThemeTab(
    server: Server,
    canEdit: Boolean,
    onSave: (
        name: String,
        handle: String,
        desc: String,
        iconText: String,
        iconPresetKey: String?,
        imageUrl: String?,
        bannerUrl: String?,
        accentHex: Long,
        secondaryHex: Long,
        surfaceStyle: ServerThemeSurfaceStyle,
        categoryTag: String
    ) -> Unit
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = server.customization.theme

    var nameInput by remember(server.id) { mutableStateOf(server.name) }
    var handleInput by remember(server.id) { mutableStateOf(server.uniqueHandle) }
    var descInput by remember(server.id) { mutableStateOf(server.description) }
    var iconTextInput by remember(server.id) { mutableStateOf(server.iconText) }
    var iconPresetKey by remember(server.id) { mutableStateOf(theme.iconPresetKey) }
    var imageUrlInput by remember(server.id) { mutableStateOf(server.imageUrl.orEmpty()) }
    var bannerUrlInput by remember(server.id) { mutableStateOf(server.bannerUrl.orEmpty()) }
    var accentColorHex by remember(server.id) { mutableLongStateOf(theme.accentColorHex) }
    var secondaryColorHex by remember(server.id) { mutableLongStateOf(theme.secondaryColorHex) }
    var surfaceStyle by remember(server.id) { mutableStateOf(theme.surfaceStyle) }
    var categoryTagInput by remember(server.id) { mutableStateOf(server.categoryTag) }
    var isUploading by remember { mutableStateOf(false) }

    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                val uploaded = CloudinaryHelper.uploadImage(context, uri)
                if (uploaded.isNotBlank()) imageUrlInput = uploaded
                isUploading = false
            }
        }
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                val uploaded = CloudinaryHelper.uploadImage(context, uri)
                if (uploaded.isNotBlank()) bannerUrlInput = uploaded
                isUploading = false
            }
        }
    }

    val colorSwatches = listOf(0xFF5865F2L, 0xFFEB459EL, 0xFF10B981L, 0xFFF59E0BL, 0xFFEF4444L, 0xFF06B6D4L, 0xFF8B5CF6L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Brush.linearGradient(listOf(Color(accentColorHex), Color(secondaryColorHex))))
                    ) {
                        if (bannerUrlInput.isNotBlank()) {
                            AsyncImage(
                                model = bannerUrlInput,
                                contentDescription = "Aperçu bannière",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(accentColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    imageUrlInput.isNotBlank() -> AsyncImage(
                                        model = imageUrlInput,
                                        contentDescription = "Aperçu icône",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    !iconPresetKey.isNullOrBlank() -> Icon(
                                        imageVector = resolveCatalogIconVector(iconPresetKey),
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    else -> Text(iconTextInput.take(2), color = Color.White, fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(nameInput.ifBlank { server.name }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("@${handleInput.ifBlank { server.uniqueHandle }} • ${surfaceStyle.label}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Nom du serveur") },
                            singleLine = true,
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth().testTag("admin_identity_name_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = handleInput,
                                onValueChange = { handleInput = it },
                                label = { Text("Identifiant (@handle)") },
                                singleLine = true,
                                enabled = canEdit,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = categoryTagInput,
                                onValueChange = { categoryTagInput = it },
                                label = { Text("Thématique") },
                                singleLine = true,
                                enabled = canEdit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = descInput,
                            onValueChange = { descInput = it },
                            label = { Text("Description du serveur") },
                            minLines = 2,
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth().testTag("admin_identity_desc_input")
                        )
                        OutlinedTextField(
                            value = iconTextInput,
                            onValueChange = { iconTextInput = it.take(4) },
                            label = { Text("Initiales icône (2-4 car.)") },
                            singleLine = true,
                            enabled = canEdit,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = canEdit && !isUploading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Icône", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                enabled = canEdit && !isUploading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bannière", fontSize = 12.sp)
                            }
                        }

                        Text("Icône vectorielle de secours :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = iconPresetKey == null,
                                    onClick = { if (canEdit) iconPresetKey = null },
                                    label = { Text("Texte", fontSize = 11.sp) }
                                )
                            }
                            items(ChannelIconCatalog.options.take(8), key = { "srv_ico_${it.key}" }) { opt ->
                                FilterChip(
                                    selected = iconPresetKey == opt.key,
                                    onClick = { if (canEdit) iconPresetKey = opt.key },
                                    leadingIcon = { Icon(resolveCatalogIconVector(opt.key), contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text(opt.label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Text("Couleur d'accentuation du serveur :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colorSwatches) { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(if (accentColorHex == hex) 2.5.dp else 0.dp, Color.White, CircleShape)
                                        .clickable(enabled = canEdit) { accentColorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (accentColorHex == hex) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Text("Ambiance visuelle (Thème) :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ServerThemeSurfaceStyle.entries) { st ->
                                FilterChip(
                                    selected = surfaceStyle == st,
                                    onClick = { if (canEdit) surfaceStyle = st },
                                    label = { Text(st.label, fontSize = 11.sp) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSave(
                                    nameInput,
                                    handleInput,
                                    descInput,
                                    iconTextInput,
                                    iconPresetKey,
                                    imageUrlInput.ifBlank { null },
                                    bannerUrlInput.ifBlank { null },
                                    accentColorHex,
                                    secondaryColorHex,
                                    surfaceStyle,
                                    categoryTagInput
                                )
                            },
                            enabled = canEdit && nameInput.isNotBlank() && !isUploading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_identity_btn")
                        ) {
                            Text("Enregistrer Identité & Thème", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCategoriesAndChannelsTab(
    server: Server,
    canManageChannels: Boolean,
    isAdmin: Boolean,
    onUpsertCategory: (categoryId: String?, name: String, isPrivate: Boolean, allowedRoleIds: List<String>) -> Unit,
    onMoveCategoryOrder: (categoryId: String, delta: Int) -> Unit,
    onDeleteCategory: (categoryName: String) -> Unit,
    onUpsertChannel: (
        channelId: String?,
        name: String,
        topic: String,
        type: ChannelType,
        categoryName: String,
        iconKey: String?,
        slowmodeSeconds: Int,
        isReadOnly: Boolean,
        isPrivate: Boolean,
        allowedRoles: List<UserRole>,
        allowedRoleIds: List<String>,
        maxParticipants: Int,
        bitrateKbps: Int
    ) -> Unit,
    onMoveChannelOrder: (channelId: String, delta: Int) -> Unit,
    onDeleteChannel: (channelId: String) -> Unit
) {
    val palette = ravenPalette()
    val sortedCats = remember(server.categories) { server.categories.sortedBy { it.position } }
    val sortedChannels = remember(server.channels) { server.channels.sortedBy { it.position } }

    var catNameInput by remember { mutableStateOf("") }
    var catPrivate by remember { mutableStateOf(false) }

    var editingChannelId by remember { mutableStateOf<String?>(null) }
    var chName by remember { mutableStateOf("") }
    var chTopic by remember { mutableStateOf("") }
    var chType by remember { mutableStateOf(ChannelType.TEXT) }
    var chCategory by remember { mutableStateOf(sortedCats.firstOrNull()?.name ?: "SALONS TEXTUELS") }
    var chIconKey by remember { mutableStateOf<String?>("tag") }
    var chSlowmode by remember { mutableStateOf("0") }
    var chReadOnly by remember { mutableStateOf(false) }
    var chPrivate by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category Creator & Order
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CATÉGORIES & ORDRE", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = catNameInput,
                            onValueChange = { catNameInput = it },
                            label = { Text("Nouvelle catégorie") },
                            singleLine = true,
                            enabled = canManageChannels,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpsertCategory(null, catNameInput, catPrivate, emptyList())
                                catNameInput = ""
                            },
                            enabled = canManageChannels && catNameInput.isNotBlank()
                        ) { Text("Créer") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Catégorie privée (Staff)", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = catPrivate, enabled = canManageChannels, onCheckedChange = { catPrivate = it })
                    }

                    sortedCats.forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cat.name}${if (cat.isPrivate) " (Privée)" else ""}",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (canManageChannels) {
                                IconButton(onClick = { onMoveCategoryOrder(cat.id, -1) }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Monter catégorie", tint = palette.textSecondary)
                                }
                                IconButton(onClick = { onMoveCategoryOrder(cat.id, 1) }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre catégorie", tint = palette.textSecondary)
                                }
                                if (sortedCats.size > 1) {
                                    IconButton(onClick = { onDeleteCategory(cat.name) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer catégorie", tint = DiscordRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Channel Editor & Icon Customization
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingChannelId == null) "CRÉER UN SALON" else "PERSONNALISER LE SALON",
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (editingChannelId != null) {
                            TextButton(onClick = {
                                editingChannelId = null
                                chName = ""
                                chTopic = ""
                                chIconKey = "tag"
                                chSlowmode = "0"
                                chReadOnly = false
                                chPrivate = false
                            }) { Text("Réinitialiser", fontSize = 11.sp) }
                        }
                    }

                    OutlinedTextField(
                        value = chName,
                        onValueChange = { chName = it },
                        label = { Text("Nom du salon") },
                        singleLine = true,
                        enabled = canManageChannels,
                        modifier = Modifier.fillMaxWidth().testTag("admin_channel_name_input")
                    )
                    OutlinedTextField(
                        value = chTopic,
                        onValueChange = { chTopic = it },
                        label = { Text("Sujet / Description du salon") },
                        singleLine = true,
                        enabled = canManageChannels,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Type & Catégorie :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ChannelType.entries) { type ->
                            FilterChip(
                                selected = chType == type,
                                onClick = { if (canManageChannels) chType = type },
                                label = { Text(type.name, fontSize = 11.sp) }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(sortedCats, key = { "pick_cat_${it.id}" }) { cat ->
                            FilterChip(
                                selected = chCategory.equals(cat.name, ignoreCase = true),
                                onClick = { if (canManageChannels) chCategory = cat.name },
                                label = { Text(cat.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Icône du salon :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ChannelIconCatalog.options, key = { "ch_ico_${it.key}" }) { opt ->
                            FilterChip(
                                selected = chIconKey == opt.key,
                                onClick = { if (isAdmin) chIconKey = opt.key },
                                leadingIcon = { Icon(resolveCatalogIconVector(opt.key), contentDescription = null, modifier = Modifier.size(15.dp)) },
                                label = { Text(opt.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = chSlowmode,
                            onValueChange = { chSlowmode = it.filter { c -> c.isDigit() } },
                            label = { Text("Slowmode (s)") },
                            singleLine = true,
                            enabled = canManageChannels,
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Lecture seule", color = palette.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Switch(checked = chReadOnly, enabled = canManageChannels, onCheckedChange = { chReadOnly = it })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Privé (Staff)", color = palette.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Switch(checked = chPrivate, enabled = isAdmin, onCheckedChange = { chPrivate = it })
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onUpsertChannel(
                                editingChannelId, chName, chTopic, chType, chCategory, chIconKey,
                                chSlowmode.toIntOrNull() ?: 0, chReadOnly, chPrivate, emptyList(), emptyList(), 0, 64
                            )
                            editingChannelId = null
                            chName = ""
                            chTopic = ""
                        },
                        enabled = canManageChannels && chName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_channel_btn")
                    ) {
                        Text(if (editingChannelId == null) "Créer le salon" else "Enregistrer le salon", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("ORDRE DES SALONS (${sortedChannels.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        items(sortedChannels, key = { "admin_ch_${it.id}" }) { ch ->
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (editingChannelId == ch.id) palette.accent else palette.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(resolveChannelIconVector(ch), contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("#${ch.name}", color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${ch.category} • pos ${ch.position}", color = palette.textMuted, fontSize = 10.sp)
                    }
                    if (isAdmin) {
                        IconButton(onClick = { onMoveChannelOrder(ch.id, -1) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Monter salon", tint = palette.textSecondary)
                        }
                        IconButton(onClick = { onMoveChannelOrder(ch.id, 1) }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre salon", tint = palette.textSecondary)
                        }
                    }
                    if (canManageChannels) {
                        IconButton(onClick = {
                            editingChannelId = ch.id
                            chName = ch.name
                            chTopic = ch.topic
                            chType = ch.type
                            chCategory = ch.category
                            chIconKey = ch.iconKey ?: "tag"
                            chSlowmode = ch.slowmodeSeconds.toString()
                            chReadOnly = ch.isReadOnly
                            chPrivate = ch.isPrivate
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier salon", tint = palette.accent)
                        }
                        if (sortedChannels.size > 1) {
                            IconButton(onClick = { onDeleteChannel(ch.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer salon", tint = DiscordRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
