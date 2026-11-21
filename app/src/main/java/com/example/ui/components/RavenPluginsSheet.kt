package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PluginManagerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RavenPluginsSheet(
    pluginViewModel: PluginManagerViewModel,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val uiState by pluginViewModel.uiState.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) } // 0=Extensions, 1=Logs, 2=Sécurité & SDK
    var consentingPlugin by remember { mutableStateOf<PluginUiItem?>(null) }
    var inspectingPluginId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("raven_plugins_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
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
                    Icon(Icons.Default.Extension, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Extensions & Plugins RAVEN",
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${uiState.activeCount} actif(s) • Hôte v${uiState.hostVersion} • Sandbox actif",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    color = DiscordGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DiscordGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("DCL Bloqué", color = DiscordGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Feedback banner
            val feedback = uiState.errorMessage ?: uiState.feedbackMessage
            if (feedback != null) {
                val isErr = uiState.errorMessage != null
                Surface(
                    color = (if (isErr) DiscordRed else DiscordGreen).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isErr) DiscordRed else DiscordGreen)
                ) {
                    Text(
                        text = feedback,
                        color = if (isErr) DiscordRed else DiscordGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = palette.cardBg,
                contentColor = DiscordBlurple
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Extensions (${uiState.plugins.size})", fontSize = 12.sp) },
                    modifier = Modifier.testTag("plugins_tab_list")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Journaux (${uiState.logs.size})", fontSize = 12.sp) },
                    modifier = Modifier.testTag("plugins_tab_logs")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("SDK & Sécurité", fontSize = 12.sp) },
                    modifier = Modifier.testTag("plugins_tab_sdk")
                )
            }

            when (activeTab) {
                0 -> {
                    // Category filter row
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == null,
                                onClick = { pluginViewModel.setCategoryFilter(null) },
                                label = { Text("Toutes", fontSize = 11.sp) }
                            )
                        }
                        items(PluginCategory.entries) { cat ->
                            FilterChip(
                                selected = uiState.selectedCategory == cat,
                                onClick = { pluginViewModel.setCategoryFilter(cat) },
                                label = { Text(cat.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    val filtered = remember(uiState.plugins, uiState.selectedCategory) {
                        val c = uiState.selectedCategory
                        if (c == null) uiState.plugins else uiState.plugins.filter { it.manifest.category == c }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.manifest.id }) { item ->
                            PluginCatalogCard(
                                item = item,
                                onToggleSwitch = { checked ->
                                    if (checked) {
                                        consentingPlugin = item
                                    } else {
                                        pluginViewModel.disablePlugin(item.manifest.id)
                                    }
                                },
                                onOpenConfigAndPerms = { inspectingPluginId = item.manifest.id },
                                onResetFaulted = { pluginViewModel.resetFaulted(item.manifest.id) }
                            )
                        }
                    }
                }

                1 -> {
                    // Logs tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = uiState.selectedLogLevel == null,
                                    onClick = { pluginViewModel.setLogLevelFilter(null) },
                                    label = { Text("Tous", fontSize = 11.sp) }
                                )
                            }
                            items(PluginLogLevel.entries) { lvl ->
                                FilterChip(
                                    selected = uiState.selectedLogLevel == lvl,
                                    onClick = { pluginViewModel.setLogLevelFilter(lvl) },
                                    label = { Text(lvl.label, fontSize = 11.sp) }
                                )
                            }
                        }
                        IconButton(
                            onClick = { pluginViewModel.clearLogs() },
                            modifier = Modifier.size(48.dp).testTag("plugins_clear_logs_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Effacer les journaux", tint = DiscordRed)
                        }
                    }

                    val filteredLogs = remember(uiState.logs, uiState.selectedLogLevel) {
                        val l = uiState.selectedLogLevel
                        if (l == null) uiState.logs else uiState.logs.filter { it.level == l }
                    }

                    if (filteredLogs.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Aucun événement enregistré dans le journal.", color = palette.textMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { log ->
                                val lvlColor = when (log.level) {
                                    PluginLogLevel.INFO -> DiscordBlurple
                                    PluginLogLevel.WARN -> DiscordAmber
                                    PluginLogLevel.ERROR -> DiscordRed
                                    PluginLogLevel.SECURITY -> Color(0xFFEB459E)
                                }
                                Surface(
                                    color = palette.cardBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, lvlColor.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Surface(color = lvlColor.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                                                    Text(
                                                        log.level.label,
                                                        color = lvlColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(log.pluginName, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(log.timestampLabel, color = palette.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Text("${log.action} • ${log.message}", color = palette.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    PluginArchitectureOverviewTab(
                        hostVersion = uiState.hostVersion,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }

    // Explicit permission consent modal before first/new activation
    consentingPlugin?.let { item ->
        PluginConsentActivationDialog(
            item = item,
            onDismiss = { consentingPlugin = null },
            onConfirmEnable = { approved ->
                pluginViewModel.enablePlugin(item.manifest.id, approved) {
                    consentingPlugin = null
                }
            }
        )
    }

    // Detailed Permissions + Configuration editor for an extension
    inspectingPluginId?.let { pid ->
        val freshItem = uiState.plugins.find { it.manifest.id == pid }
        if (freshItem == null) {
            inspectingPluginId = null
        } else {
            PluginConfigAndPermissionsDialog(
                item = freshItem,
                isBusy = uiState.isBusy,
                onTogglePermission = { perm, granted ->
                    pluginViewModel.togglePermission(freshItem.manifest.id, perm, granted)
                },
                onSaveConfig = { cfg ->
                    pluginViewModel.saveConfig(freshItem.manifest.id, cfg) {
                        inspectingPluginId = null
                    }
                },
                onDismiss = { inspectingPluginId = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCatalogCard(
    item: PluginUiItem,
    onToggleSwitch: (Boolean) -> Unit,
    onOpenConfigAndPerms: () -> Unit,
    onResetFaulted: () -> Unit
) {
    val palette = ravenPalette()
    val m = item.manifest
    val s = item.state
    val compat = item.compatibility

    val stateColor = when (s.lifecycleState) {
        PluginLifecycleState.ACTIVE -> DiscordGreen
        PluginLifecycleState.INCOMPATIBLE -> DiscordAmber
        PluginLifecycleState.FAULTED -> DiscordRed
        PluginLifecycleState.DISABLED, PluginLifecycleState.REGISTERED -> palette.textMuted
    }

    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (s.lifecycleState == PluginLifecycleState.ACTIVE) DiscordBlurple.copy(alpha = 0.55f) else palette.border),
        modifier = Modifier.fillMaxWidth().testTag("plugin_card_${m.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(m.name, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Surface(color = DiscordBlurple.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                            Text("v${m.version}", color = DiscordBlurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("${m.id} • ${m.author}", color = palette.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Switch(
                    checked = s.isEnabled && s.lifecycleState == PluginLifecycleState.ACTIVE,
                    onCheckedChange = onToggleSwitch,
                    enabled = compat.isCompatible && s.lifecycleState != PluginLifecycleState.FAULTED,
                    modifier = Modifier.testTag("plugin_switch_${m.id}")
                )
            }

            Text(m.description, color = palette.textSecondary, fontSize = 12.sp)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = stateColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        s.lifecycleState.label,
                        color = stateColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Surface(color = palette.inputBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "Min RAVEN v${m.minRavenVersion}",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Surface(color = palette.inputBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "${item.effectiveGrantedPermissions.size}/${m.requestedPermissions.size} permissions",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                m.slashCommands.forEach { cmd ->
                    Surface(color = DiscordBlurple.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            cmd.trigger,
                            color = DiscordBlurple,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (!compat.isCompatible) {
                Surface(
                    color = DiscordAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DiscordAmber.copy(alpha = 0.5f))
                ) {
                    Text(
                        compat.reason,
                        color = DiscordAmber,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }
            }

            if (s.lifecycleState == PluginLifecycleState.FAULTED) {
                Surface(
                    color = DiscordRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DiscordRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Quarantaine (${s.consecutiveErrors} erreurs) : ${s.lastErrorMessage ?: "Erreur critique"}",
                            color = DiscordRed,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onResetFaulted,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("plugin_reset_${m.id}")
                        ) {
                            Text("Réinitialiser", color = DiscordRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onOpenConfigAndPerms,
                    enabled = compat.isCompatible,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp).testTag("plugin_config_btn_${m.id}")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Permissions & Config", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PluginConsentActivationDialog(
    item: PluginUiItem,
    onDismiss: () -> Unit,
    onConfirmEnable: (Set<PluginPermission>) -> Unit
) {
    val palette = ravenPalette()
    val m = item.manifest
    // Default selection starts with existing grants (if any) or ONLY low-risk capabilities;
    // sensitive permissions ALWAYS require explicit user opt-in.
    val selected = remember(m.id, item.state.grantedPermissions) {
        mutableStateMapOf<PluginPermission, Boolean>().apply {
            m.requestedPermissions.forEach { perm ->
                val defaultChecked = if (item.state.grantedPermissions.isNotEmpty()) {
                    perm in item.state.grantedPermissions
                } else {
                    perm.risk != PluginPermissionRisk.SENSITIVE
                }
                put(perm, defaultChecked)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        title = {
            Column {
                Text("Activer « ${m.name} » ?", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Contrôlez exactement ce que cette extension peut lire ou exécuter.", color = palette.textMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (m.requestedPermissions.isEmpty()) {
                    Text("Cette extension ne demande aucune permission sensible.", color = DiscordGreen, fontSize = 12.sp)
                } else {
                    m.requestedPermissions.forEach { perm ->
                        val riskColor = when (perm.risk) {
                            PluginPermissionRisk.LOW -> DiscordGreen
                            PluginPermissionRisk.MEDIUM -> DiscordAmber
                            PluginPermissionRisk.SENSITIVE -> DiscordRed
                        }
                        Surface(
                            color = palette.inputBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected[perm] == true,
                                    onCheckedChange = { checked -> selected[perm] = checked },
                                    modifier = Modifier.testTag("consent_perm_${perm.name}")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(perm.title, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(perm.risk.label, color = riskColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(perm.description, color = palette.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val approved = selected.filterValues { it }.keys
                    onConfirmEnable(approved)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                modifier = Modifier.testTag("confirm_enable_plugin_btn")
            ) {
                Text("Activer avec ces droits", color = Color.White, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginConfigAndPermissionsDialog(
    item: PluginUiItem,
    isBusy: Boolean,
    onTogglePermission: (PluginPermission, Boolean) -> Unit,
    onSaveConfig: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val m = item.manifest
    val draftConfig = remember(item.manifest.id, item.state.configValues) {
        mutableStateMapOf<String, String>().apply {
            m.configSchema.forEach { field ->
                put(field.key, item.state.configValues[field.key] ?: field.defaultValue)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = palette.sidebarBg,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("plugin_config_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.name, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${m.id} • v${m.version}", color = palette.textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textSecondary)
                    }
                }

                Text("PERMISSIONS DÉCLARÉES", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                m.requestedPermissions.forEach { perm ->
                    val isGranted = perm in item.effectiveGrantedPermissions
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("${perm.title} (${perm.risk.label})", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(perm.description, color = palette.textMuted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isGranted,
                                onCheckedChange = { onTogglePermission(perm, it) },
                                modifier = Modifier.testTag("perm_switch_${perm.name}")
                            )
                        }
                    }
                }

                if (m.configSchema.isNotEmpty()) {
                    HorizontalDivider(color = palette.border)
                    Text("CONFIGURATION TYPÉE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    m.configSchema.forEach { field ->
                        val cur = draftConfig[field.key] ?: field.defaultValue
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(field.label, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(field.description, color = palette.textMuted, fontSize = 11.sp)
                                when (field.type) {
                                    PluginConfigFieldType.BOOLEAN -> {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(if (cur == "true") "Activé" else "Désactivé", color = palette.textSecondary, fontSize = 12.sp)
                                            Switch(
                                                checked = cur == "true",
                                                onCheckedChange = { draftConfig[field.key] = if (it) "true" else "false" }
                                            )
                                        }
                                    }
                                    PluginConfigFieldType.INT -> {
                                        OutlinedTextField(
                                            value = cur,
                                            onValueChange = { draftConfig[field.key] = it.filter { ch -> ch.isDigit() } },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            supportingText = { Text("Plage autorisée : ${field.minInt} à ${field.maxInt}", fontSize = 10.sp) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    PluginConfigFieldType.STRING -> {
                                        OutlinedTextField(
                                            value = cur,
                                            onValueChange = { draftConfig[field.key] = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    PluginConfigFieldType.SELECT -> {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            field.options.forEach { opt ->
                                                FilterChip(
                                                    selected = cur == opt,
                                                    onClick = { draftConfig[field.key] = opt },
                                                    label = { Text(opt, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onSaveConfig(draftConfig.toMap()) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("save_plugin_config_btn")
                    ) {
                        Text("Enregistrer la configuration", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginArchitectureOverviewTab(
    hostVersion: String,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(
            "1. Sandbox & Zéro Code Distant (DCL Interdit)" to
                "RAVEN interdit tout téléchargement ou exécution dynamique de fichiers .dex, .jar, .so ou scripts distants non vérifiés. Chaque extension est enregistrée et validée via PluginManifest.",
            "2. Principe du Moindre Privilège" to
                "Aucune extension ne reçoit d'accès automatique aux données privées (email, UID, tokens, Firestore). Chaque capability passera par ScopedPluginApi après consentement explicite.",
            "3. Compatibilité SemVer (Hôte v$hostVersion)" to
                "Chaque PluginManifest déclare minRavenVersion (et maxRavenVersion optionnel). Toute extension incompatibles est bloquée avant chargement.",
            "4. Cycle de Vie & Bus d'Événements Isolé" to
                "onLoad → onEnable → onConfigUpdated / onEvent / onCommand → onDisable → onUnload. Toute exception est isolée et déclenche une mise en quarantaine automatique après 3 erreurs."
        ).forEach { (title, body) ->
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(body, color = palette.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
