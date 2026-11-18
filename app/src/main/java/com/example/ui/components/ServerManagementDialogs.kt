package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChannelType
import com.example.model.CustomRole
import com.example.model.Server
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*

@Composable
fun ServerInviteDialog(
    server: Server,
    onRegenerateCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = DiscordBlurple)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Inviter sur ${server.name}",
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Partagez ce code ou ce lien pour permettre à vos amis de rejoindre instantanément ce serveur :",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = palette.inputBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.inviteCode,
                                color = palette.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("invite_code_value")
                            )
                            Text(
                                text = "raven.chat/invite/${server.inviteCode}",
                                color = DiscordBlurple,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(server.inviteCode))
                                copiedNotice = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedNotice) DiscordGreen else DiscordBlurple
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("copy_invite_code_button")
                        ) {
                            Text(
                                text = if (copiedNotice) "Copié" else "Copier",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        onRegenerateCode()
                        copiedNotice = false
                    },
                    modifier = Modifier.testTag("regenerate_invite_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Générer un nouveau code", color = DiscordBlurple, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = palette.textPrimary)
            }
        }
    )
}

@Composable
fun JoinServerByCodeDialog(
    onJoinCode: (String) -> Boolean = { false },
    onDismiss: () -> Unit,
    onJoinCodeAsync: ((String, (Boolean, String?) -> Unit) -> Unit)? = null
) {
    val palette = ravenPalette()
    var inviteCode by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Text(
                text = "Rejoindre une communauté",
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Entrez un code d'invitation ou un identifiant reçu d'une communauté.",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {
                        inviteCode = it
                        errorMsg = null
                    },
                    placeholder = { Text("RAVEN-GAMING", color = palette.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_server_code_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "EXEMPLES POPULAIRES",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("RAVEN-GAMING", "RAVEN-DEV").forEach { sample ->
                        Surface(
                            color = palette.inputBg,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { inviteCode = sample }
                        ) {
                            Text(
                                text = sample,
                                color = DiscordBlurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = errorMsg!!, color = DiscordRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onJoinCodeAsync != null) {
                        isSubmitting = true
                        onJoinCodeAsync(inviteCode) { ok, err ->
                            isSubmitting = false
                            if (ok) onDismiss() else errorMsg = err ?: "Code invalide ou expiré."
                        }
                    } else {
                        val ok = onJoinCode(inviteCode)
                        if (ok) onDismiss() else errorMsg = "Code invalide ou vide."
                    }
                },
                enabled = inviteCode.isNotBlank() && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                modifier = Modifier.testTag("join_server_submit")
            ) {
                Text(if (isSubmitting) "Vérification..." else "Rejoindre", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = palette.textMuted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsSheet(
    server: Server,
    currentUser: User,
    onRenameServer: (String) -> Unit,
    onCreateRole: (name: String, colorHex: Long, secondaryColorHex: Long?, isAdmin: Boolean, canKickBan: Boolean, canManageChannels: Boolean) -> Unit,
    onAssignMemberRole: (userId: String, role: UserRole, customRoleId: String?) -> Unit,
    onTimeoutMember: (userId: String) -> Unit,
    onKickMember: (userId: String) -> Unit,
    onBoostServer: () -> Boolean = { false },
    onUpdateServerBannerColor: (Long) -> Unit = {},
    onUpdateChannelSettings: (channelId: String, slowmodeSeconds: Int, isReadOnly: Boolean) -> Unit = { _, _, _ -> },
    onOpenFullAdminDashboard: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(0) }
    var serverNameInput by remember(server.id) { mutableStateOf(server.name) }

    // Create Role form states
    var roleNameInput by remember { mutableStateOf("") }
    var roleColorHex by remember { mutableLongStateOf(0xFFEB459E) }
    var roleSecondaryHex by remember { mutableStateOf<Long?>(0xFF5865F2) }
    var permIsAdmin by remember { mutableStateOf(false) }
    var permCanKick by remember { mutableStateOf(true) }
    var permCanManage by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("server_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 640.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gestion de ${server.name}",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${server.members.size} membres • Code ${server.inviteCode}",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
                if (onOpenFullAdminDashboard != null) {
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onOpenFullAdminDashboard()
                        },
                        modifier = Modifier.testTag("settings_open_full_admin_btn")
                    ) {
                        Text("Admin Pro", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = DiscordBlurple,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(color = palette.border) }
            ) {
                listOf("Général & Rôles", "Boost & Salons", "Modération", "Audit").forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = label,
                                color = if (selectedTab == idx) palette.textPrimary else palette.textMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            Text("NOM DU SERVEUR", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = serverNameInput,
                                    onValueChange = { serverNameInput = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("server_rename_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = palette.inputBg,
                                        unfocusedContainerColor = palette.inputBg,
                                        focusedBorderColor = DiscordBlurple,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedTextColor = palette.textPrimary,
                                        unfocusedTextColor = palette.textSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onRenameServer(serverNameInput) },
                                    enabled = serverNameInput.isNotBlank() && serverNameInput != server.name,
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                    modifier = Modifier.testTag("server_rename_save")
                                ) {
                                    Text("Sauver", color = Color.White)
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = palette.border)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("CRÉER UN RÔLE PERSONNALISÉ", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, palette.border)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = roleNameInput,
                                        onValueChange = { roleNameInput = it },
                                        placeholder = { Text("Nom du rôle (ex. Élite)", color = palette.textMuted) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("create_role_name_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = palette.inputBg,
                                            unfocusedContainerColor = palette.inputBg,
                                            focusedBorderColor = DiscordBlurple,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedTextColor = palette.textPrimary,
                                            unfocusedTextColor = palette.textSecondary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val roleColors = listOf(0xFFEB459E, 0xFF5865F2, 0xFF23A55A, 0xFFF0B232, 0xFFED4245, 0xFF00A8FC)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(roleColors) { hex ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hex))
                                                    .clickable { roleColorHex = hex },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (roleColorHex == hex) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("2e couleur dégradée (optionnel)", color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        item {
                                            FilterChip(
                                                selected = roleSecondaryHex == null,
                                                onClick = { roleSecondaryHex = null },
                                                label = { Text("Uni", fontSize = 11.sp) }
                                            )
                                        }
                                        items(roleColors) { hex ->
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hex))
                                                    .clickable { roleSecondaryHex = hex },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (roleSecondaryHex == hex) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    RolePermissionRow("Droits Administrateur", permIsAdmin) { permIsAdmin = it }
                                    RolePermissionRow("Expulser / Timeout membres", permCanKick) { permCanKick = it }
                                    RolePermissionRow("Gérer les salons", permCanManage) { permCanManage = it }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (roleNameInput.isNotBlank()) {
                                                onCreateRole(roleNameInput, roleColorHex, roleSecondaryHex, permIsAdmin, permCanKick, permCanManage)
                                                roleNameInput = ""
                                            }
                                        },
                                        enabled = roleNameInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                        modifier = Modifier.fillMaxWidth().testTag("create_role_submit")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ajouter ce rôle", color = Color.White)
                                    }
                                }
                            }
                        }

                        item {
                            Text("RÔLES EXISTANTS (${server.customRoles.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        items(server.customRoles, key = { it.id }) { role ->
                            CustomRoleRow(role = role, palette = palette)
                        }
                    }

                    1 -> {
                        item {
                            val boostProgress = when {
                                server.boostLevel >= 3 -> 1f
                                server.boostLevel == 2 -> server.boostCount / 5f
                                else -> server.boostCount / 2f
                            }.coerceIn(0f, 1f)
                            var boostFeedback by remember { mutableStateOf<String?>(null) }

                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFEB459E).copy(alpha = 0.55f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color(0xFFEB459E), modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Boost de Serveur • Niveau ${server.boostLevel}",
                                                    color = palette.textPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${server.boostCount} Boost(s) • Solde : ${currentUser.orbsBalance} Orbs",
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Surface(
                                            color = Color(0xFFEB459E).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "NIV. ${server.boostLevel}",
                                                color = Color(0xFFEB459E),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { boostProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = Color(0xFFEB459E),
                                        trackColor = palette.inputBg
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Niv. 1 (1 Boost) : Bannière colorée • Niv. 2 (2 Boosts) : Badge VIP • Niv. 3 (5 Boosts) : Statut Légendaire",
                                        color = palette.textMuted,
                                        fontSize = 10.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("COULEUR DE BANNIÈRE", color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val bannerColors = listOf(0xFFEB459E, 0xFF5865F2, 0xFF23A55A, 0xFFF0B232, 0xFF00A8FC)
                                        items(bannerColors) { hex ->
                                            val isSelected = server.customBannerColorHex == hex
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(hex))
                                                    .clickable { onUpdateServerBannerColor(hex) },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            val ok = onBoostServer()
                                            boostFeedback = if (ok) {
                                                "Serveur boosté avec succès !"
                                            } else {
                                                "Orbs insuffisants (200 Orbs requis)."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEB459E)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("boost_server_button")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Booster ce serveur (200 Orbs)", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    if (boostFeedback != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = boostFeedback!!,
                                            color = if (boostFeedback!!.contains("succès")) DiscordGreen else DiscordRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "CONTRÔLES PAR SALON (SLOWMODE & LECTURE SEULE)",
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val textChannels = server.channels.filter {
                            it.type == ChannelType.TEXT || it.type == ChannelType.ANNOUNCEMENT
                        }
                        items(textChannels, key = { "ctrl_${it.id}" }) { ch ->
                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, palette.border)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (ch.isReadOnly) Icons.Default.Lock else Icons.Default.Tag,
                                                contentDescription = null,
                                                tint = if (ch.isReadOnly) DiscordAmber else DiscordBlurple,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "#${ch.name}",
                                                color = palette.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Lecture seule", color = palette.textMuted, fontSize = 11.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Switch(
                                                checked = ch.isReadOnly,
                                                onCheckedChange = { ro ->
                                                    onUpdateChannelSettings(ch.id, ch.slowmodeSeconds, ro)
                                                },
                                                modifier = Modifier.testTag("channel_readonly_switch_${ch.id}"),
                                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordAmber)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Mode lent (Slowmode) :", color = palette.textMuted, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(listOf(0, 5, 10, 30)) { sec ->
                                            val isSelected = ch.slowmodeSeconds == sec
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onUpdateChannelSettings(ch.id, sec, ch.isReadOnly) },
                                                label = {
                                                    Text(if (sec == 0) "Off" else "${sec}s", fontSize = 11.sp)
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = DiscordBlurple,
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("channel_slowmode_${ch.id}_$sec")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        items(server.members, key = { it.id }) { member ->
                            val isSelf = member.id == currentUser.id || member.id == "user_me"
                            var roleMenuExpanded by remember { mutableStateOf(false) }

                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, palette.border)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = member.name,
                                                    color = palette.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (member.isTimedOut) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(color = DiscordAmber.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = "TIMEOUT",
                                                            color = DiscordAmber,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            val roleLabel = member.customRoleId?.let { rid ->
                                                server.customRoles.find { it.id == rid }?.name
                                            } ?: member.role.title
                                            Text(
                                                text = "Rôle : $roleLabel • Niv. ${member.level}",
                                                color = palette.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Box {
                                            TextButton(onClick = { roleMenuExpanded = true }) {
                                                Text("Rôle", color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            DropdownMenu(
                                                expanded = roleMenuExpanded,
                                                onDismissRequest = { roleMenuExpanded = false },
                                                modifier = Modifier.background(palette.cardBg)
                                            ) {
                                                server.customRoles.forEach { cr ->
                                                    DropdownMenuItem(
                                                        text = { Text(cr.name, color = Color(cr.colorHex), fontWeight = FontWeight.SemiBold) },
                                                        onClick = {
                                                            val mappedRole = when {
                                                                cr.isAdmin -> UserRole.ADMIN
                                                                cr.canKickBan -> UserRole.MODERATOR
                                                                else -> UserRole.MEMBER
                                                            }
                                                            onAssignMemberRole(member.id, mappedRole, cr.id)
                                                            roleMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!isSelf) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { onTimeoutMember(member.id) },
                                                border = BorderStroke(1.dp, DiscordAmber),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).testTag("mod_timeout_${member.id}")
                                            ) {
                                                Icon(Icons.Default.TimerOff, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (member.isTimedOut) "Lever Timeout" else "Timeout",
                                                    color = DiscordAmber,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { onKickMember(member.id) },
                                                border = BorderStroke(1.dp, DiscordRed),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).testTag("mod_kick_${member.id}")
                                            ) {
                                                Icon(Icons.Default.PersonRemove, contentDescription = null, tint = DiscordRed, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Expulser", color = DiscordRed, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        if (server.auditLogs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Aucune action enregistrée dans le journal d'audit pour le moment.",
                                        color = palette.textMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            items(server.auditLogs, key = { it.id }) { entry ->
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, palette.border)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${entry.actorName} ${entry.actionDescription} ${entry.targetName}",
                                                color = palette.textPrimary,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = entry.timestamp,
                                                color = palette.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RolePermissionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = ravenPalette()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = palette.textSecondary, fontSize = 12.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DiscordBlurple)
        )
    }
}

@Composable
private fun CustomRoleRow(role: CustomRole, palette: RavenPalette) {
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .then(
                            if (role.secondaryColorHex != null) {
                                Modifier.background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(role.colorHex), Color(role.secondaryColorHex))
                                    )
                                )
                            } else {
                                Modifier.background(Color(role.colorHex))
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (role.secondaryColorHex != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(role.colorHex), Color(role.secondaryColorHex))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = role.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(text = role.name, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (role.isAdmin) RoleTagBadge("ADMIN", DiscordRed)
                if (role.canKickBan) RoleTagBadge("MOD", DiscordGreen)
                if (role.canManageChannels) RoleTagBadge("SALONS", DiscordBlurple)
            }
        }
    }
}

@Composable
private fun RoleTagBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
