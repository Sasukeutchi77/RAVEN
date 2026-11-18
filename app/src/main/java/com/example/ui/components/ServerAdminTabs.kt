package com.example.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun AdminRolesAndPermissionsTab(
    server: Server,
    canManageRoles: Boolean,
    onSaveOrUpdateRole: (name: String, colorHex: Long, secondaryHex: Long?, perms: Set<RolePermission>, existingRoleId: String?) -> Unit,
    onMoveRoleOrder: (roleId: String, delta: Int) -> Unit,
    onDeleteCustomRole: (roleId: String) -> Unit
) {
    val palette = ravenPalette()
    val sortedRoles = remember(server.customRoles) { server.customRoles.sortedBy { it.position } }
    var editingRoleId by remember { mutableStateOf<String?>(null) }
    var roleName by remember { mutableStateOf("") }
    var roleColorHex by remember { mutableLongStateOf(0xFF5865F2L) }
    var roleSecondaryHex by remember { mutableStateOf<Long?>(null) }
    var selectedPerms by remember {
        mutableStateOf(setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE))
    }

    val roleSwatches = listOf(
        0xFF5865F2L, 0xFFEB459EL, 0xFF23A55AL, 0xFFF0B232L,
        0xFFED4245L, 0xFF00A8FCL, 0xFFA855F7L, 0xFF10B981L
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingRoleId == null) "CRÉER UN RÔLE" else "MODIFIER LE RÔLE",
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (editingRoleId != null) {
                            TextButton(onClick = {
                                editingRoleId = null
                                roleName = ""
                                roleColorHex = 0xFF5865F2L
                                roleSecondaryHex = null
                                selectedPerms = setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE)
                            }) {
                                Text("Nouveau rôle", fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = roleName,
                        onValueChange = { roleName = it },
                        label = { Text("Nom du rôle") },
                        singleLine = true,
                        enabled = canManageRoles,
                        modifier = Modifier.fillMaxWidth().testTag("admin_role_name_input")
                    )

                    Text("Couleur principale", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(roleSwatches) { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (roleColorHex == hex) 2.5.dp else 0.dp,
                                        color = if (roleColorHex == hex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = canManageRoles) { roleColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (roleColorHex == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Text("Permissions accordées", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    RolePermission.entries.forEach { perm ->
                        val enabledPerm = perm in selectedPerms
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = canManageRoles && perm != RolePermission.DELETE_COMMUNITY) {
                                    selectedPerms = if (enabledPerm) selectedPerms - perm else selectedPerms + perm
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.label, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(perm.description, color = palette.textMuted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(
                                checked = enabledPerm,
                                enabled = canManageRoles && perm != RolePermission.DELETE_COMMUNITY,
                                onCheckedChange = { checked ->
                                    selectedPerms = if (checked) selectedPerms + perm else selectedPerms - perm
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveOrUpdateRole(roleName, roleColorHex, roleSecondaryHex, selectedPerms, editingRoleId)
                            editingRoleId = null
                            roleName = ""
                        },
                        enabled = canManageRoles && roleName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_role_save_btn")
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (editingRoleId == null) "Créer le rôle" else "Mettre à jour le rôle", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "HIÉRARCHIE DES RÔLES (${sortedRoles.size})",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(sortedRoles, key = { "admin_role_${it.id}" }) { role ->
            val isSystemRole = role.id in setOf("role_admin", "role_mod", "role_member")
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (editingRoleId == role.id) palette.accent else palette.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(role.colorHex)))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(role.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "${role.permissions.size} permission(s)${if (isSystemRole) " • Système" else ""}",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (canManageRoles) {
                        IconButton(onClick = { onMoveRoleOrder(role.id, -1) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Monter rôle", tint = palette.textSecondary)
                        }
                        IconButton(onClick = { onMoveRoleOrder(role.id, 1) }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre rôle", tint = palette.textSecondary)
                        }
                        IconButton(onClick = {
                            editingRoleId = role.id
                            roleName = role.name
                            roleColorHex = role.colorHex
                            roleSecondaryHex = role.secondaryColorHex
                            selectedPerms = role.permissions
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifier rôle", tint = palette.accent)
                        }
                        if (!isSystemRole) {
                            IconButton(onClick = { onDeleteCustomRole(role.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer rôle", tint = DiscordRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminWelcomeAndNotificationsTab(
    server: Server,
    canEdit: Boolean,
    onSaveWelcome: (ServerWelcomeConfig, homeWelcomeMessage: String) -> Unit,
    onSaveNotifications: (ServerNotificationConfig) -> Unit
) {
    val palette = ravenPalette()
    val w = server.customization.welcome
    val n = server.customization.notifications

    var welcomeEnabled by remember(server.id, w) { mutableStateOf(w.welcomeEnabled) }
    var welcomeTemplate by remember(server.id, w) { mutableStateOf(w.welcomeMessageTemplate) }
    var welcomeChannelId by remember(server.id, w) { mutableStateOf(w.welcomeChannelId) }
    var goodbyeEnabled by remember(server.id, w) { mutableStateOf(w.goodbyeEnabled) }
    var goodbyeTemplate by remember(server.id, w) { mutableStateOf(w.goodbyeMessageTemplate) }
    var goodbyeChannelId by remember(server.id, w) { mutableStateOf(w.goodbyeChannelId) }
    var dmWelcomeEnabled by remember(server.id, w) { mutableStateOf(w.dmWelcomeEnabled) }
    var homeWelcomeMessage by remember(server.id, server.homeWelcomeMessage) { mutableStateOf(server.homeWelcomeMessage) }

    var defaultNotifLevel by remember(server.id, n) { mutableStateOf(n.defaultLevel) }
    var suppressEveryone by remember(server.id, n) { mutableStateOf(n.suppressEveryoneMentions) }
    var suppressRoles by remember(server.id, n) { mutableStateOf(n.suppressRoleMentions) }
    var highlightAnnouncements by remember(server.id, n) { mutableStateOf(n.highlightAnnouncements) }

    val textChannels = remember(server.channels) {
        server.channels.filter { it.type == ChannelType.TEXT || it.type == ChannelType.ANNOUNCEMENT }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MESSAGES D'ACCUEIL & DE DÉPART", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                    OutlinedTextField(
                        value = homeWelcomeMessage,
                        onValueChange = { homeWelcomeMessage = it },
                        label = { Text("Bannière d'accueil (Page Communauté)") },
                        minLines = 2,
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Activer les messages d'arrivée", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = welcomeEnabled, enabled = canEdit, onCheckedChange = { welcomeEnabled = it })
                    }
                    OutlinedTextField(
                        value = welcomeTemplate,
                        onValueChange = { welcomeTemplate = it },
                        label = { Text("Modèle d'accueil ({user}, {server})") },
                        enabled = canEdit && welcomeEnabled,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("admin_welcome_template_input")
                    )

                    if (textChannels.isNotEmpty()) {
                        Text("Salon d'accueil :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(textChannels, key = { "wel_ch_${it.id}" }) { ch ->
                                FilterChip(
                                    selected = welcomeChannelId == ch.id || (welcomeChannelId == null && ch == textChannels.first()),
                                    onClick = { if (canEdit) welcomeChannelId = ch.id },
                                    label = { Text("#${ch.name}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Message privé / notification de bienvenue", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = dmWelcomeEnabled, enabled = canEdit, onCheckedChange = { dmWelcomeEnabled = it })
                    }

                    HorizontalDivider(color = palette.border)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Activer les messages de départ", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = goodbyeEnabled, enabled = canEdit, onCheckedChange = { goodbyeEnabled = it })
                    }
                    OutlinedTextField(
                        value = goodbyeTemplate,
                        onValueChange = { goodbyeTemplate = it },
                        label = { Text("Modèle de départ ({user}, {server})") },
                        enabled = canEdit && goodbyeEnabled,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("admin_goodbye_template_input")
                    )

                    if (textChannels.isNotEmpty()) {
                        Text("Salon de départ :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(textChannels, key = { "bye_ch_${it.id}" }) { ch ->
                                FilterChip(
                                    selected = goodbyeChannelId == ch.id || (goodbyeChannelId == null && ch == textChannels.first()),
                                    onClick = { if (canEdit) goodbyeChannelId = ch.id },
                                    label = { Text("#${ch.name}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Surface(color = palette.inputBg, shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("APERÇU EN DIRECT", color = palette.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = welcomeTemplate.replace("{user}", "@NouveauMembre").replace("{server}", server.name),
                                color = DiscordGreen,
                                fontSize = 12.sp
                            )
                            Text(
                                text = goodbyeTemplate.replace("{user}", "@AncienMembre").replace("{server}", server.name),
                                color = DiscordAmber,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveWelcome(
                                ServerWelcomeConfig(
                                    welcomeEnabled = welcomeEnabled,
                                    welcomeMessageTemplate = welcomeTemplate,
                                    welcomeChannelId = welcomeChannelId,
                                    goodbyeEnabled = goodbyeEnabled,
                                    goodbyeMessageTemplate = goodbyeTemplate,
                                    goodbyeChannelId = goodbyeChannelId,
                                    dmWelcomeEnabled = dmWelcomeEnabled
                                ),
                                homeWelcomeMessage
                            )
                        },
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_welcome_btn")
                    ) {
                        Text("Enregistrer Accueil & Départ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PARAMÈTRES DE NOTIFICATIONS", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                    ServerNotificationLevel.entries.forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = canEdit) { defaultNotifLevel = level }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = defaultNotifLevel == level, onClick = { if (canEdit) defaultNotifLevel = level })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(level.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(level.description, color = palette.textMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Bloquer @everyone / @here", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = suppressEveryone, enabled = canEdit, onCheckedChange = { suppressEveryone = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Bloquer mentions de rôles", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = suppressRoles, enabled = canEdit, onCheckedChange = { suppressRoles = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Mettre en avant les annonces", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = highlightAnnouncements, enabled = canEdit, onCheckedChange = { highlightAnnouncements = it })
                    }

                    Button(
                        onClick = {
                            onSaveNotifications(
                                ServerNotificationConfig(
                                    defaultLevel = defaultNotifLevel,
                                    suppressEveryoneMentions = suppressEveryone,
                                    suppressRoleMentions = suppressRoles,
                                    highlightAnnouncements = highlightAnnouncements
                                )
                            )
                        },
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_notif_btn")
                    ) {
                        Text("Enregistrer les notifications", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSecurityRulesAndPrivacyTab(
    server: Server,
    isStrictAdmin: Boolean,
    onSaveRulesAndVerification: (rulesList: List<String>, guidelines: String, requireAcceptance: Boolean, level: ServerVerificationLevel) -> Unit,
    onSaveModeration: (ServerModerationConfig) -> Unit,
    onSavePrivacy: (isPublic: Boolean, ServerPrivacyConfig) -> Unit
) {
    val palette = ravenPalette()
    val cust = server.customization
    val mod = cust.moderation
    val priv = cust.privacy

    var rulesItems by remember(server.id, server.serverRulesList) {
        mutableStateOf(
            if (server.serverRulesList.isNotEmpty()) server.serverRulesList
            else server.rulesGuidelines.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        )
    }
    var newRuleText by remember { mutableStateOf("") }
    var guidelines by remember(server.id, server.rulesGuidelines) { mutableStateOf(server.rulesGuidelines) }
    var requireRulesAcceptance by remember(server.id, cust.requireRulesAcceptance) { mutableStateOf(cust.requireRulesAcceptance) }
    var verificationLevel by remember(server.id, cust.verificationLevel) { mutableStateOf(cust.verificationLevel) }

    var autoModEnabled by remember(server.id, mod) { mutableStateOf(mod.autoModEnabled) }
    var blockSpamAndLinks by remember(server.id, mod) { mutableStateOf(mod.blockSpamAndLinks) }
    var blockBadWords by remember(server.id, mod) { mutableStateOf(mod.blockBadWords) }
    var blockedWords by remember(server.id, mod) { mutableStateOf(mod.blockedWords) }
    var newWordInput by remember { mutableStateOf("") }
    var defaultSlowmode by remember(server.id, mod) { mutableStateOf(mod.defaultSlowmodeSeconds.toString()) }
    var maxMentions by remember(server.id, mod) { mutableStateOf(mod.maxMentionsPerMessage.toString()) }
    var antiRaidMode by remember(server.id, mod) { mutableStateOf(mod.antiRaidModeEnabled) }

    var isPublic by remember(server.id, server.isPublic) { mutableStateOf(server.isPublic) }
    var discoverable by remember(server.id, priv) { mutableStateOf(priv.discoverableInCatalog) }
    var allowInvites by remember(server.id, priv) { mutableStateOf(priv.allowInviteLinks) }
    var requireApproval by remember(server.id, priv) { mutableStateOf(priv.requireInviteApproval) }
    var allowMemberDms by remember(server.id, priv) { mutableStateOf(priv.allowMemberDms) }
    var hideMemberList by remember(server.id, priv) { mutableStateOf(priv.hideMemberListForNonStaff) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Rules & Verification Level
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RÈGLES & NIVEAU DE VÉRIFICATION", color = DiscordAmber, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                    rulesItems.forEachIndexed { idx, rule ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${idx + 1}. $rule", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (isStrictAdmin) {
                                IconButton(onClick = { rulesItems = rulesItems.filterIndexed { i, _ -> i != idx } }) {
                                    Icon(Icons.Default.Close, contentDescription = "Retirer règle", tint = DiscordRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (isStrictAdmin) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newRuleText,
                                onValueChange = { newRuleText = it },
                                label = { Text("Ajouter une règle") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (newRuleText.isNotBlank()) {
                                        rulesItems = rulesItems + newRuleText.trim()
                                        guidelines = rulesItems.joinToString("\n")
                                        newRuleText = ""
                                    }
                                },
                                enabled = newRuleText.isNotBlank()
                            ) { Text("+") }
                        }
                    }

                    OutlinedTextField(
                        value = guidelines,
                        onValueChange = { guidelines = it },
                        label = { Text("Charte complète du serveur") },
                        minLines = 2,
                        enabled = isStrictAdmin,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Acceptation obligatoire du règlement", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = requireRulesAcceptance, enabled = isStrictAdmin, onCheckedChange = { requireRulesAcceptance = it })
                    }

                    Text("Niveau de vérification :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    ServerVerificationLevel.entries.forEach { lvl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = isStrictAdmin) { verificationLevel = lvl }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = verificationLevel == lvl, onClick = { if (isStrictAdmin) verificationLevel = lvl })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lvl.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(lvl.description, color = palette.textMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { onSaveRulesAndVerification(rulesItems, guidelines, requireRulesAcceptance, verificationLevel) },
                        enabled = isStrictAdmin,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_rules_verif_btn")
                    ) {
                        Text("Enregistrer Règles & Vérification", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Moderation & Anti-Raid
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MODÉRATION AUTOMATIQUE & ANTI-RAID", color = DiscordRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Auto-Modération active", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = autoModEnabled, enabled = isStrictAdmin, onCheckedChange = { autoModEnabled = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Bloquer liens & spam http", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = blockSpamAndLinks, enabled = isStrictAdmin, onCheckedChange = { blockSpamAndLinks = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Filtrer les mots interdits", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = blockBadWords, enabled = isStrictAdmin, onCheckedChange = { blockBadWords = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Mode Anti-Raid (verrouille écriture membres)", color = DiscordRed, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = antiRaidMode, enabled = isStrictAdmin, onCheckedChange = { antiRaidMode = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = defaultSlowmode,
                            onValueChange = { defaultSlowmode = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Slowmode (sec)") },
                            singleLine = true,
                            enabled = isStrictAdmin,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxMentions,
                            onValueChange = { maxMentions = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Max @mentions") },
                            singleLine = true,
                            enabled = isStrictAdmin,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Mots bloqués (${blockedWords.size}) :", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(blockedWords, key = { "bw_$it" }) { word ->
                            InputChip(
                                selected = false,
                                onClick = { if (isStrictAdmin) blockedWords = blockedWords - word },
                                label = { Text(word, fontSize = 11.sp) },
                                trailingIcon = if (isStrictAdmin) {
                                    { Icon(Icons.Default.Close, contentDescription = "Supprimer mot", modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                    if (isStrictAdmin) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newWordInput,
                                onValueChange = { newWordInput = it },
                                label = { Text("Ajouter mot interdit") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val wClean = newWordInput.trim().lowercase()
                                    if (wClean.isNotBlank() && wClean !in blockedWords) {
                                        blockedWords = blockedWords + wClean
                                        newWordInput = ""
                                    }
                                },
                                enabled = newWordInput.isNotBlank()
                            ) { Text("+") }
                        }
                    }

                    Button(
                        onClick = {
                            onSaveModeration(
                                ServerModerationConfig(
                                    autoModEnabled = autoModEnabled,
                                    blockSpamAndLinks = blockSpamAndLinks,
                                    blockBadWords = blockBadWords,
                                    blockedWords = blockedWords,
                                    defaultSlowmodeSeconds = defaultSlowmode.toIntOrNull() ?: 0,
                                    maxMentionsPerMessage = maxMentions.toIntOrNull() ?: 5,
                                    antiRaidModeEnabled = antiRaidMode
                                )
                            )
                        },
                        enabled = isStrictAdmin,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_moderation_btn")
                    ) {
                        Text("Enregistrer la modération", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Privacy Settings
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, palette.border)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PARAMÈTRES DE CONFIDENTIALITÉ", color = DiscordGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Communauté publique", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = isPublic, enabled = isStrictAdmin, onCheckedChange = { isPublic = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Visible dans le catalogue Explorer", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = discoverable, enabled = isStrictAdmin && isPublic, onCheckedChange = { discoverable = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Autoriser les liens d'invitation", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = allowInvites, enabled = isStrictAdmin, onCheckedChange = { allowInvites = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Validation requise des invitations", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = requireApproval, enabled = isStrictAdmin, onCheckedChange = { requireApproval = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Autoriser les MP entre membres", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = allowMemberDms, enabled = isStrictAdmin, onCheckedChange = { allowMemberDms = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Masquer la liste des membres (hors Staff)", color = palette.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = hideMemberList, enabled = isStrictAdmin, onCheckedChange = { hideMemberList = it })
                    }

                    Button(
                        onClick = {
                            onSavePrivacy(
                                isPublic,
                                ServerPrivacyConfig(
                                    discoverableInCatalog = discoverable,
                                    allowInviteLinks = allowInvites,
                                    requireInviteApproval = requireApproval,
                                    allowMemberDms = allowMemberDms,
                                    hideMemberListForNonStaff = hideMemberList
                                )
                            )
                        },
                        enabled = isStrictAdmin,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("admin_save_privacy_btn")
                    ) {
                        Text("Enregistrer la confidentialité", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
