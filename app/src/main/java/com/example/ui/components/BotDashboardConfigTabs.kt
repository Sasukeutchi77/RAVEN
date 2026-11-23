package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
internal fun BotCommandsAndRulesTab(
    bot: RavenBot?,
    isOwner: Boolean,
    onSaveCommand: (String, BotCommandSpec) -> Unit,
    onDeleteCommand: (String, String) -> Unit,
    onSaveAutoResponse: (String, BotAutoResponseRule) -> Unit,
    onDeleteAutoResponse: (String, String) -> Unit
) {
    val palette = ravenPalette()
    if (bot == null) {
        Text("Créez ou sélectionnez un bot pour gérer ses commandes et auto-réponses.", color = palette.textMuted, fontSize = 13.sp)
        return
    }
    var cmdTrigger by remember(bot.id) { mutableStateOf("/aide") }
    var cmdDesc by remember(bot.id) { mutableStateOf("Affiche l'aide rapide") }
    var cmdReply by remember(bot.id) { mutableStateOf("Salut {user} ! Bienvenue dans {server} ({time}).") }
    var cmdActionType by remember(bot.id) { mutableStateOf(BotCommandActionType.STATIC_TEMPLATE) }

    var ruleName by remember(bot.id) { mutableStateOf("Salutation") }
    var rulePattern by remember(bot.id) { mutableStateOf("bonjour") }
    var ruleReply by remember(bot.id) { mutableStateOf("Bonjour {user} ! Comment puis-je aider sur {server} ?") }
    var ruleIsRegex by remember(bot.id) { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (isOwner) {
            item {
                Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NOUVELLE COMMANDE BOT (/ OU !)", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(BotCommandActionType.entries) { actionType ->
                                FilterChip(
                                    selected = cmdActionType == actionType,
                                    onClick = { cmdActionType = actionType },
                                    label = { Text(actionType.label, fontSize = 11.sp) },
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("bot_cmd_action_${actionType.name}")
                                )
                            }
                        }
                        Text(
                            text = cmdActionType.hint,
                            color = DiscordGreen,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = cmdTrigger,
                            onValueChange = { cmdTrigger = it },
                            label = { Text("Commande (ex: /rules ou !promo)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("bot_cmd_trigger_input")
                        )
                        OutlinedTextField(
                            value = cmdDesc,
                            onValueChange = { cmdDesc = it },
                            label = { Text("Description") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cmdReply,
                            onValueChange = { cmdReply = it },
                            label = { Text("Réponse / Modèle") },
                            modifier = Modifier.fillMaxWidth().testTag("bot_cmd_reply_input")
                        )
                        Text(
                            text = "Variables : {user}, {bot}, {server}, {time}, {date}, {random:1-6}, {pick:A|B}",
                            color = palette.textMuted,
                            fontSize = 10.sp
                        )
                        Button(
                            onClick = {
                                onSaveCommand(
                                    bot.id,
                                    BotCommandSpec(
                                        trigger = cmdTrigger,
                                        description = cmdDesc,
                                        responseTemplate = cmdReply,
                                        actionType = cmdActionType
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_bot_command_button")
                        ) {
                            Text("Ajouter la commande", color = Color.White)
                        }
                    }
                }
            }
        }

        item { Text("COMMANDES ACTIVES (${bot.commands.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        items(bot.commands, key = { it.id }) { cmd ->
            Surface(color = palette.cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${cmd.trigger} • ${cmd.actionType.label} • ${cmd.cooldownSeconds}s", color = DiscordBlurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(cmd.description, color = palette.textSecondary, fontSize = 11.sp)
                        Text(cmd.responseTemplate, color = palette.textMuted, fontSize = 11.sp)
                    }
                    if (isOwner) {
                        IconButton(onClick = { onDeleteCommand(bot.id, cmd.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer commande", tint = DiscordRed)
                        }
                    }
                }
            }
        }

        if (isOwner) {
            item {
                Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NOUVELLE RÉPONSE AUTOMATIQUE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Nom de la règle") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = rulePattern,
                            onValueChange = { rulePattern = it },
                            label = { Text(if (ruleIsRegex) "Expression Regex" else "Mot-clé déclencheur") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("bot_autorule_pattern_input")
                        )
                        OutlinedTextField(
                            value = ruleReply,
                            onValueChange = { ruleReply = it },
                            label = { Text("Réponse automatique") },
                            modifier = Modifier.fillMaxWidth().testTag("bot_autorule_reply_input")
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mode Regex", color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = ruleIsRegex, onCheckedChange = { ruleIsRegex = it })
                        }
                        Button(
                            onClick = {
                                onSaveAutoResponse(
                                    bot.id,
                                    BotAutoResponseRule(
                                        name = ruleName,
                                        pattern = rulePattern,
                                        isRegex = ruleIsRegex,
                                        replyTemplate = ruleReply
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_bot_autorule_button")
                        ) {
                            Text("Enregistrer l'auto-réponse", color = Color.White)
                        }
                    }
                }
            }
        }

        item { Text("AUTO-RÉPONSES (${bot.autoResponses.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        items(bot.autoResponses, key = { it.id }) { rule ->
            Surface(color = palette.cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${rule.name} (${if (rule.isRegex) "Regex" else "Mot"}: « ${rule.pattern} »)", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(rule.replyTemplate, color = palette.textMuted, fontSize = 11.sp)
                    }
                    if (isOwner) {
                        IconButton(onClick = { onDeleteAutoResponse(bot.id, rule.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer règle", tint = DiscordRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BotWebhooksSecurityTab(
    bot: RavenBot?,
    isOwner: Boolean,
    lastVerifyResult: Boolean?,
    onRegenerateToken: (String) -> Unit,
    onVerifyToken: (String, String) -> Unit,
    onSaveWebhook: (String, String, String, String, Set<BotEventTrigger>) -> Unit,
    onDeleteWebhook: (String, String) -> Unit,
    onTriggerTestWebhook: (String, String) -> Unit,
    onTogglePlugin: (String, String, Boolean) -> Unit,
    onUpdateRateLimit: (String, BotRateLimitConfig) -> Unit
) {
    val palette = ravenPalette()
    if (bot == null) {
        Text("Sélectionnez un bot pour gérer sa sécurité et ses webhooks.", color = palette.textMuted, fontSize = 13.sp)
        return
    }
    if (!isOwner) {
        Text(
            text = "Sécurité stricte : seuls les créateurs d'un bot peuvent accéder à la gestion du jeton, des secrets webhook et de l'anti-abus.",
            color = DiscordAmber,
            fontSize = 13.sp
        )
        return
    }

    var tokenToVerify by remember(bot.id) { mutableStateOf("") }
    var webhookLabel by remember(bot.id) { mutableStateOf("API Externe") }
    var webhookUrl by remember(bot.id) { mutableStateOf("https://") }
    var webhookSecret by remember(bot.id) { mutableStateOf("") }
    var maxActionsPerMin by remember(bot.id, bot.rateLimit.maxActionsPerMinute) {
        mutableStateOf(bot.rateLimit.maxActionsPerMinute.toString())
    }
    var floodThreshold by remember(bot.id, bot.rateLimit.antiSpamHumanFloodThreshold) {
        mutableStateOf(bot.rateLimit.antiSpamHumanFloodThreshold.toString())
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        // 1. Token Security & Regeneration
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = DiscordAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SÉCURITÉ DU JETON API", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("Empreinte stockée : ${bot.tokenPrefix} (v${bot.tokenVersion}) • Hachage SHA-256 uniquement", color = palette.textMuted, fontSize = 11.sp)
                    Button(
                        onClick = { onRegenerateToken(bot.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordAmber),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("regenerate_bot_token_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Régénérer le jeton (révoque l'ancien)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = tokenToVerify,
                            onValueChange = { tokenToVerify = it },
                            label = { Text("Tester un jeton") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { onVerifyToken(bot.id, tokenToVerify) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("verify_bot_token_button")
                        ) {
                            Text("Vérifier")
                        }
                    }
                    if (lastVerifyResult != null) {
                        Text(
                            text = if (lastVerifyResult) "Jeton authentique" else "Jeton refusé",
                            color = if (lastVerifyResult) DiscordGreen else DiscordRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Extensible Plugins
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PLUGINS & AUTOMATISATIONS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    BotPluginRegistry.allPlugins().forEach { plugin ->
                        val enabled = plugin.id in bot.enabledPluginIds
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plugin.name, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(plugin.description, color = palette.textMuted, fontSize = 11.sp)
                            }
                            Switch(checked = enabled, onCheckedChange = { onTogglePlugin(bot.id, plugin.id, it) })
                        }
                    }
                }
            }
        }

        // 3. Anti-Spam & Abuse Rate Limiting
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ANTI-SPAM & LIMITATION DES ABUS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxActionsPerMin,
                            onValueChange = { maxActionsPerMin = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Actions / min max") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = floodThreshold,
                            onValueChange = { floodThreshold = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Seuil flood") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            onUpdateRateLimit(
                                bot.id,
                                bot.rateLimit.copy(
                                    maxActionsPerMinute = maxActionsPerMin.toIntOrNull() ?: 12,
                                    antiSpamHumanFloodThreshold = floodThreshold.toIntOrNull() ?: 5
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Appliquer les limites", color = Color.White)
                    }
                }
            }
        }

        // 4. HTTPS Webhooks
        item {
            Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WEBHOOKS HTTPS (HMAC-SHA256)", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = webhookLabel,
                        onValueChange = { webhookLabel = it },
                        label = { Text("Nom du webhook") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("URL HTTPS (ex: https://api.monservice.com/hook)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bot_webhook_url_input")
                    )
                    OutlinedTextField(
                        value = webhookSecret,
                        onValueChange = { webhookSecret = it },
                        label = { Text("Secret HMAC (stocké côté coffre propriétaire)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveWebhook(
                                bot.id,
                                webhookLabel,
                                webhookUrl,
                                webhookSecret,
                                bot.subscribedEvents.ifEmpty { setOf(BotEventTrigger.ON_COMMAND_EXECUTED) }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_bot_webhook_button")
                    ) {
                        Text("Ajouter le webhook HTTPS", color = Color.White)
                    }
                }
            }
        }

        items(bot.webhooks, key = { it.id }) { hook ->
            Surface(color = palette.cardBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hook.label, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(hook.endpointUrl, color = palette.textMuted, fontSize = 11.sp)
                        hook.lastStatusCode?.let {
                            Text("Dernier statut : HTTP $it", color = if (it in 200..299) DiscordGreen else DiscordRed, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = { onTriggerTestWebhook(bot.id, hook.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "Tester le webhook", tint = DiscordGreen)
                    }
                    IconButton(onClick = { onDeleteWebhook(bot.id, hook.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer le webhook", tint = DiscordRed)
                    }
                }
            }
        }
    }
}
