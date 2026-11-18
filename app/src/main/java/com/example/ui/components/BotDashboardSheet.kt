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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.BotDashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDashboardSheet(
    currentUserId: String,
    activeServer: Server,
    allServers: List<Server>,
    bots: List<RavenBot>,
    installationsByServer: Map<String, List<ServerBotInstallation>>,
    logsByBot: Map<String, List<BotActivityLog>>,
    uiState: BotDashboardUiState,
    onSelectBot: (String?) -> Unit,
    onOpenCreateBot: () -> Unit,
    onOpenEditBot: (RavenBot) -> Unit,
    onToggleBotActive: (String, Boolean) -> Unit,
    onRegenerateToken: (String) -> Unit,
    onVerifyToken: (String, String) -> Unit,
    onClearOneTimeToken: () -> Unit,
    onAddBotToServer: (botId: String, serverId: String, perms: Set<BotPermission>, roleTitle: String, customRoleId: String?) -> Unit,
    onRemoveBotFromServer: (botId: String, serverId: String) -> Unit,
    onUpdateServerBotConfig: (botId: String, serverId: String, perms: Set<BotPermission>, roleTitle: String, customRoleId: String?, enabled: Boolean) -> Unit,
    onSaveCommand: (String, BotCommandSpec) -> Unit,
    onDeleteCommand: (String, String) -> Unit,
    onSaveAutoResponse: (String, BotAutoResponseRule) -> Unit,
    onDeleteAutoResponse: (String, String) -> Unit,
    onSaveWebhook: (String, String, String, String, Set<BotEventTrigger>) -> Unit,
    onDeleteWebhook: (String, String) -> Unit,
    onTriggerTestWebhook: (String, String) -> Unit,
    onTogglePlugin: (String, String, Boolean) -> Unit,
    onUpdateRateLimit: (String, BotRateLimitConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedBot = remember(bots, uiState.selectedBotId) {
        bots.find { it.id == uiState.selectedBotId } ?: bots.firstOrNull()
    }
    val ownedBots = remember(bots, currentUserId) {
        bots.filter { it.ownerUid == currentUserId }
    }
    val isOwnerOfSelected = selectedBot != null && selectedBot.ownerUid == currentUserId

    // One-time token display modal — cleared immediately on dismiss
    uiState.oneTimePlaintextToken?.let { tokenValue ->
        AlertDialog(
            onDismissRequest = onClearOneTimeToken,
            containerColor = palette.cardBg,
            icon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = DiscordAmber) },
            title = {
                Text(
                    text = "Jeton API de ${uiState.oneTimeTokenBotName ?: "Bot"}",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copiez ce jeton maintenant : par sécurité, il ne sera plus jamais réaffiché en clair. Seul son condensat SHA-256 est conservé.",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = palette.primaryBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DiscordAmber.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = tokenValue,
                            color = DiscordGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp).testTag("one_time_bot_token_text")
                        )
                    }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(tokenValue)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copier le jeton", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier dans le presse-papiers")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onClearOneTimeToken,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier.testTag("dismiss_one_time_token_button")
                ) {
                    Text("J'ai sauvegardé mon jeton", color = Color.White)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("bot_dashboard_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp, max = 720.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = DiscordBlurple.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SmartToy, contentDescription = "Dashboard Bots Raven", tint = DiscordBlurple)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Dashboard Bots RAVEN", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${ownedBots.size} bot(s) créés • ${bots.size} disponibles", color = palette.textMuted, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onOpenCreateBot,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("open_create_bot_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Créer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Tab Strip
            val tabs = listOf("Mes Bots", "Serveurs & Rôles", "Commandes", "Webhooks & Sécurité", "Journaux")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(tabs.indices.toList()) { index ->
                    val isSel = selectedTab == index
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedTab = index },
                        label = { Text(tabs[index], fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("bot_tab_$index")
                    )
                }
            }

            // Bot selector strip if bots exist
            if (bots.isNotEmpty() && selectedTab != 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bots, key = { it.id }) { b ->
                        val isSel = selectedBot?.id == b.id
                        FilterChip(
                            selected = isSel,
                            onClick = { onSelectBot(b.id) },
                            label = { Text("${b.name} (@${b.handle})", fontSize = 11.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape)
                                        .background(if (b.isActive) DiscordGreen else DiscordRed)
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = palette.border)

            when (selectedTab) {
                0 -> BotListTab(
                    bots = bots,
                    currentUserId = currentUserId,
                    selectedBotId = selectedBot?.id,
                    onSelectBot = onSelectBot,
                    onToggleActive = onToggleBotActive,
                    onOpenEditBot = onOpenEditBot,
                    onOpenCreateBot = onOpenCreateBot
                )
                1 -> BotServerInstallTab(
                    activeServer = activeServer,
                    allServers = allServers.filterNot { it.isDmTab },
                    currentUserId = currentUserId,
                    bots = bots,
                    installationsByServer = installationsByServer,
                    onAddBotToServer = onAddBotToServer,
                    onRemoveBotFromServer = onRemoveBotFromServer,
                    onUpdateServerBotConfig = onUpdateServerBotConfig
                )
                2 -> BotCommandsAndRulesTab(
                    bot = selectedBot,
                    isOwner = isOwnerOfSelected,
                    onSaveCommand = onSaveCommand,
                    onDeleteCommand = onDeleteCommand,
                    onSaveAutoResponse = onSaveAutoResponse,
                    onDeleteAutoResponse = onDeleteAutoResponse
                )
                3 -> BotWebhooksSecurityTab(
                    bot = selectedBot,
                    isOwner = isOwnerOfSelected,
                    lastVerifyResult = uiState.lastVerifyResult,
                    onRegenerateToken = onRegenerateToken,
                    onVerifyToken = onVerifyToken,
                    onSaveWebhook = onSaveWebhook,
                    onDeleteWebhook = onDeleteWebhook,
                    onTriggerTestWebhook = onTriggerTestWebhook,
                    onTogglePlugin = onTogglePlugin,
                    onUpdateRateLimit = onUpdateRateLimit
                )
                4 -> BotLogsTab(
                    bot = selectedBot,
                    isOwner = isOwnerOfSelected,
                    logs = selectedBot?.let { logsByBot[it.id].orEmpty() }.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun BotListTab(
    bots: List<RavenBot>,
    currentUserId: String,
    selectedBotId: String?,
    onSelectBot: (String?) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onOpenEditBot: (RavenBot) -> Unit,
    onOpenCreateBot: () -> Unit
) {
    val palette = ravenPalette()
    if (bots.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Aucun bot RAVEN configuré", color = palette.textPrimary, fontWeight = FontWeight.Bold)
            Text("Créez votre premier bot sécurisé avec jeton SHA-256, commandes / et webhooks.", color = palette.textMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onOpenCreateBot, colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)) {
                Text("Créer mon premier bot", color = Color.White)
            }
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(bots, key = { it.id }) { bot ->
            val isOwner = bot.ownerUid == currentUserId
            val isSelected = bot.id == selectedBotId
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isSelected) DiscordBlurple else palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectBot(bot.id) }
                    .testTag("bot_card_${bot.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(bot.avatarColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!bot.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = bot.avatarUrl,
                                    contentDescription = "Avatar ${bot.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(bot.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(color = DiscordBlurple.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                                    Text("BOT", color = DiscordBlurple, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                                if (!isOwner) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(color = DiscordGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("PUBLIC", color = DiscordGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text("@${bot.handle} • Jeton ${bot.tokenPrefix} (v${bot.tokenVersion})", color = palette.textMuted, fontSize = 11.sp)
                        }
                        if (isOwner) {
                            Switch(
                                checked = bot.isActive,
                                onCheckedChange = { onToggleActive(bot.id, it) },
                                modifier = Modifier.testTag("toggle_bot_active_${bot.id}")
                            )
                        }
                    }

                    Text(bot.description, color = palette.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${bot.commands.size} cmd • ${bot.autoResponses.size} règles • ${bot.webhooks.size} webhooks",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                        if (isOwner) {
                            TextButton(
                                onClick = { onOpenEditBot(bot) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("edit_bot_${bot.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Éditer / Supprimer", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotServerInstallTab(
    activeServer: Server,
    allServers: List<Server>,
    currentUserId: String,
    bots: List<RavenBot>,
    installationsByServer: Map<String, List<ServerBotInstallation>>,
    onAddBotToServer: (botId: String, serverId: String, perms: Set<BotPermission>, roleTitle: String, customRoleId: String?) -> Unit,
    onRemoveBotFromServer: (botId: String, serverId: String) -> Unit,
    onUpdateServerBotConfig: (botId: String, serverId: String, perms: Set<BotPermission>, roleTitle: String, customRoleId: String?, enabled: Boolean) -> Unit
) {
    val palette = ravenPalette()
    var targetServerId by remember(activeServer.id) {
        mutableStateOf(if (!activeServer.isDmTab) activeServer.id else allServers.firstOrNull()?.id.orEmpty())
    }
    val targetServer = allServers.find { it.id == targetServerId } ?: allServers.firstOrNull()
    val isServerAdmin = targetServer != null && (
        CommunityPermissions.hasPermission(targetServer, currentUserId, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.hasPermission(targetServer, currentUserId, RolePermission.MANAGE_ROLES)
        )
    val installedList = targetServer?.let { installationsByServer[it.id].orEmpty() }.orEmpty()
    var editingInstall by remember { mutableStateOf<ServerBotInstallation?>(null) }

    editingInstall?.let { inst ->
        var perms by remember(inst.botId) { mutableStateOf(inst.grantedPermissions) }
        var roleTitle by remember(inst.botId) { mutableStateOf(inst.assignedRoleTitle) }
        var selectedCustomRole by remember(inst.botId) { mutableStateOf(inst.assignedCustomRoleId) }
        var enabledInSrv by remember(inst.botId) { mutableStateOf(inst.isEnabledInServer) }

        AlertDialog(
            onDismissRequest = { editingInstall = null },
            containerColor = palette.cardBg,
            title = { Text("Permissions de ${inst.botName}", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = roleTitle,
                        onValueChange = { roleTitle = it },
                        label = { Text("Titre du rôle Bot") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (targetServer != null && targetServer.customRoles.isNotEmpty()) {
                        Text("Rôle personnalisé lié :", color = palette.textMuted, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(targetServer.customRoles) { cr ->
                                FilterChip(
                                    selected = selectedCustomRole == cr.id,
                                    onClick = { selectedCustomRole = if (selectedCustomRole == cr.id) null else cr.id },
                                    label = { Text(cr.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Actif sur ce serveur", color = palette.textPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = enabledInSrv, onCheckedChange = { enabledInSrv = it })
                    }
                    BotPermission.entries.forEach { p ->
                        val chk = p in perms
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                perms = if (chk) perms - p else perms + p
                            }
                        ) {
                            Checkbox(checked = chk, onCheckedChange = { c -> perms = if (c) perms + p else perms - p })
                            Text(p.label, color = palette.textPrimary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateServerBotConfig(inst.botId, inst.serverId, perms, roleTitle, selectedCustomRole, enabledInSrv)
                        editingInstall = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                ) { Text("Enregistrer", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { editingInstall = null }) { Text("Fermer") }
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("SERVEUR CIBLE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(allServers, key = { it.id }) { srv ->
                    FilterChip(
                        selected = srv.id == targetServer?.id,
                        onClick = { targetServerId = srv.id },
                        label = { Text(srv.name, fontSize = 12.sp) }
                    )
                }
            }
            if (targetServer != null && !isServerAdmin) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Lecture seule : seuls les administrateurs de ${targetServer.name} peuvent installer ou retirer des bots.",
                    color = DiscordAmber,
                    fontSize = 11.sp
                )
            }
        }

        if (installedList.isNotEmpty()) {
            item { Text("BOTS INSTALLÉS (${installedList.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            items(installedList, key = { "inst_${it.botId}" }) { inst ->
                val canRemove = isServerAdmin || inst.ownerUid == currentUserId
                Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${inst.botName} (@${inst.botHandle})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Rôle : ${inst.assignedRoleTitle} • ${inst.grantedPermissions.size} perm(s)", color = palette.textMuted, fontSize = 11.sp)
                        }
                        if (isServerAdmin) {
                            IconButton(
                                onClick = { editingInstall = inst },
                                modifier = Modifier.size(48.dp).testTag("edit_server_bot_${inst.botId}")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = "Permissions du bot", tint = DiscordBlurple)
                            }
                        }
                        if (canRemove) {
                            IconButton(
                                onClick = { onRemoveBotFromServer(inst.botId, inst.serverId) },
                                modifier = Modifier.size(48.dp).testTag("remove_server_bot_${inst.botId}")
                            ) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Retirer du serveur", tint = DiscordRed)
                            }
                        }
                    }
                }
            }
        }

        if (targetServer != null && isServerAdmin) {
            val available = bots.filter { b -> installedList.none { it.botId == b.id } }
            if (available.isNotEmpty()) {
                item { Text("AJOUTER UN BOT AU SERVEUR", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                items(available, key = { "avail_${it.id}" }) { bot ->
                    Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bot.name, color = palette.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("@${bot.handle} • ${bot.defaultPermissions.size} permissions par défaut", color = palette.textMuted, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    onAddBotToServer(bot.id, targetServer.id, bot.defaultPermissions, "Bot ${bot.name}", null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("install_bot_${bot.id}")
                            ) {
                                Text("Ajouter", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotLogsTab(
    bot: RavenBot?,
    isOwner: Boolean,
    logs: List<BotActivityLog>
) {
    val palette = ravenPalette()
    if (bot == null) {
        Text("Sélectionnez un bot pour consulter son journal d'activité.", color = palette.textMuted, fontSize = 13.sp)
        return
    }
    if (!isOwner) {
        Text("Les journaux d'activité sont strictement réservés au propriétaire du bot.", color = DiscordAmber, fontSize = 13.sp)
        return
    }
    if (logs.isEmpty()) {
        Text("Aucune activité enregistrée pour ${bot.name}.", color = palette.textMuted, fontSize = 13.sp)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(logs, key = { it.id }) { log ->
            val statusColor = when (log.status) {
                BotLogStatus.SUCCESS, BotLogStatus.WEBHOOK_OK -> DiscordGreen
                BotLogStatus.BLOCKED_SPAM, BotLogStatus.RATE_LIMITED -> DiscordAmber
                else -> DiscordRed
            }
            Surface(color = palette.cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(log.eventType, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Surface(color = statusColor.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                            Text(log.status.label, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text(log.summary, color = palette.textSecondary, fontSize = 12.sp)
                    Text("${log.timestampLabel} • par ${log.actorName.ifBlank { log.actorUid }}", color = palette.textMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
