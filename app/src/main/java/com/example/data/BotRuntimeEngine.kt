package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Runtime execution engine for Raven Bots (Channels & WhatsApp-style DMs):
 * - Executes custom slash (/) and bang (!) commands with action engines:
 *   Interactive Menus, Help, Safe Calculator, Live Polls, Timers/Reminders,
 *   Note Vault, Mini-Games (Roll/Choose/8ball/Quiz), and Webhooks
 * - Supports 1-on-1 Bot Direct Chats (dm_bot_*) & @bot-handle mentions in Group/Normal DMs
 * - Interprets WhatsApp numbered replies ("1", "2", "3"...) mapped to quick-reply buttons
 * - Enforces permission & rate-limit / anti-spam guards + anti-loop protection
 */
fun BotRepository.handleSlashCommand(targetId: String, rawInput: String): Boolean {
    val trimmed = rawInput.trim()
    if (!trimmed.startsWith("/") && !trimmed.startsWith("!")) return false
    val parts = trimmed.split(" ", limit = 2)
    val cmdRaw = parts[0].lowercase()
    val cmdTrigger = "/" + cmdRaw.removePrefix("/").removePrefix("!")
    val args = parts.getOrNull(1)?.trim().orEmpty()

    val server = chatRepo.servers.value.find { srv -> !srv.isDmTab && srv.channels.any { it.id == targetId } }
    val channel = server?.channels?.find { it.id == targetId }

    val actor = chatRepo.currentUser.value
    val actorUid = currentUid()

    if (server != null && channel != null) {
        val installations = installationsByServer.value[server.id].orEmpty().filter { it.isEnabledInServer }
        val botsById = bots.value.associateBy { it.id }
        for (inst in installations) {
            val bot = botsById[inst.botId] ?: continue
            val cmdSpec = bot.commands.find {
                it.isEnabled && normalizeTrigger(it.trigger) == cmdTrigger
            } ?: continue

            if (!bot.isActive) {
                recordLog(bot, server.id, actorUid, actor.name, "COMMAND_$cmdTrigger", "Bot désactivé globalement.", BotLogStatus.ERROR)
                return true
            }
            if (BotPermission.USE_SLASH_COMMANDS !in inst.grantedPermissions || BotPermission.SEND_MESSAGES !in inst.grantedPermissions) {
                recordLog(
                    bot = bot,
                    serverId = server.id,
                    actorUid = actorUid,
                    actorName = actor.name,
                    eventType = "COMMAND_$cmdTrigger",
                    summary = "Permission USE_SLASH_COMMANDS ou SEND_MESSAGES refusée sur ${server.name}.",
                    status = BotLogStatus.PERMISSION_DENIED
                )
                chatRepo.notifyCommunityEvent(
                    title = "Permission Bot refusée",
                    body = "${bot.name} n'a pas la permission d'exécuter $cmdTrigger sur ${server.name}.",
                    sourceLabel = bot.name
                )
                return true
            }

            executeMatchedCommand(
                bot = bot,
                cmdSpec = cmdSpec,
                targetId = targetId,
                server = server,
                inst = inst,
                channelName = channel.name,
                actorUid = actorUid,
                actorName = actor.name,
                actorRole = actor.role,
                args = args
            )
            return true
        }
        return false
    }

    // DM / Group DM context
    val conv = chatRepo.directConversations.value.find { it.id == targetId }
    val isBotDm = conv?.isBotChat == true || targetId.startsWith("dm_bot_")
    val candidateBots = if (isBotDm) {
        val targetBotId = conv?.botId ?: targetId.removePrefix("dm_bot_")
        listOfNotNull(bots.value.find { it.id == targetBotId })
    } else {
        bots.value.filter {
            it.isActive &&
                it.allowGroupMentions &&
                (it.ownerUid == actorUid || it.isPublicCatalog)
        }
    }

    for (bot in candidateBots) {
        val cmdSpec = bot.commands.find {
            it.isEnabled && normalizeTrigger(it.trigger) == cmdTrigger
        } ?: continue

        if (!bot.isActive) {
            recordLog(bot, null, actorUid, actor.name, "DM_COMMAND_$cmdTrigger", "Bot désactivé globalement.", BotLogStatus.ERROR)
            return true
        }
        if (isBotDm && !bot.allowDirectMessages) {
            recordLog(bot, null, actorUid, actor.name, "DM_COMMAND_$cmdTrigger", "Messages privés désactivés pour ce bot.", BotLogStatus.PERMISSION_DENIED)
            return true
        }
        if (BotPermission.USE_SLASH_COMMANDS !in bot.defaultPermissions || BotPermission.SEND_MESSAGES !in bot.defaultPermissions) {
            recordLog(
                bot = bot,
                serverId = null,
                actorUid = actorUid,
                actorName = actor.name,
                eventType = "DM_COMMAND_$cmdTrigger",
                summary = "Permissions par défaut insuffisantes pour $cmdTrigger en DM.",
                status = BotLogStatus.PERMISSION_DENIED
            )
            return true
        }

        val dmLabel = conv?.groupName ?: conv?.friend?.name ?: bot.name
        executeMatchedCommand(
            bot = bot,
            cmdSpec = cmdSpec,
            targetId = targetId,
            server = null,
            inst = null,
            channelName = dmLabel,
            actorUid = actorUid,
            actorName = actor.name,
            actorRole = actor.role,
            args = args
        )
        return true
    }
    return false
}

private fun BotRepository.executeMatchedCommand(
    bot: RavenBot,
    cmdSpec: BotCommandSpec,
    targetId: String,
    server: Server?,
    inst: ServerBotInstallation?,
    channelName: String,
    actorUid: String,
    actorName: String,
    actorRole: UserRole,
    args: String
) {
    val actionOutcome = evaluateBotCommandAction(
        bot = bot,
        cmdSpec = cmdSpec,
        targetId = targetId,
        server = server,
        channelName = channelName,
        actorName = actorName,
        args = args
    )
    val scopeKey = server?.id ?: targetId

    val verdict = spamLimiter.checkAndRecordBotAction(
        botId = bot.id,
        serverId = scopeKey,
        triggerKey = "cmd:${cmdSpec.trigger}",
        renderedOutput = actionOutcome.renderedText,
        cooldownSeconds = cmdSpec.cooldownSeconds,
        rateLimit = bot.rateLimit,
        senderRole = actorRole,
        senderId = actorUid
    )

    when (verdict) {
        is BotGuardVerdict.Blocked -> {
            recordLog(bot, server?.id, actorUid, actorName, "COMMAND_${cmdSpec.trigger}", verdict.reason, verdict.status)
            if (verdict.shouldAutoDisable) {
                disableBotForAbuse(bot, server?.id ?: "dm", verdict.reason)
            }
            chatRepo.notifyCommunityEvent(
                title = "Protection Bot (${bot.name})",
                body = verdict.reason,
                sourceLabel = bot.name
            )
        }
        BotGuardVerdict.Allowed -> {
            chatRepo.postBotChannelMessage(
                targetId = targetId,
                botId = bot.id,
                botName = bot.name,
                botAvatarUrl = bot.avatarUrl,
                botAvatarColor = bot.avatarColor,
                text = actionOutcome.renderedText,
                quickReplies = actionOutcome.quickReplies,
                poll = actionOutcome.poll
            )

            // Launch timed reminder if requested
            actionOutcome.reminderTask?.let { (delayMs, reminderText) ->
                chatRepo.repoScope.launch {
                    delay(delayMs)
                    chatRepo.postBotChannelMessage(
                        targetId = targetId,
                        botId = bot.id,
                        botName = bot.name,
                        botAvatarUrl = bot.avatarUrl,
                        botAvatarColor = bot.avatarColor,
                        text = "Rappel de ${bot.name} pour $actorName : $reminderText",
                        isAnnouncement = true,
                        quickReplies = listOf(BotQuickReply("Menu", "/menu"))
                    )
                    chatRepo.addNotification(
                        title = "Rappel Bot (${bot.name})",
                        body = reminderText,
                        sourceLabel = if (server != null) "#$channelName" else "DM ${bot.name}",
                        type = NotificationType.MENTION
                    )
                }
            }

            recordLog(
                bot = bot,
                serverId = server?.id,
                actorUid = actorUid,
                actorName = actorName,
                eventType = "COMMAND_${cmdSpec.trigger}",
                summary = "Exécution ${cmdSpec.trigger} dans ${if (server != null) "#$channelName" else "DM $channelName"}.",
                status = BotLogStatus.SUCCESS
            )
            if (server != null && inst != null && (cmdSpec.webhookForward || BotEventTrigger.ON_COMMAND_EXECUTED in bot.subscribedEvents)) {
                dispatchEventWebhooks(
                    bot = bot,
                    inst = inst,
                    server = server,
                    event = BotEventTrigger.ON_COMMAND_EXECUTED,
                    actorUid = actorUid,
                    actorName = actorName,
                    detail = "${cmdSpec.trigger} $args".trim()
                )
            }
        }
    }
}

fun BotRepository.handleChannelMessage(serverId: String, channelId: String, message: Message) {
    // Anti-loop shield: bots NEVER respond to bot messages
    if (message.sender.role == UserRole.BOT || message.sender.id.startsWith("bot_") || message.isBotMessage) return

    val server = chatRepo.servers.value.find { it.id == serverId } ?: return
    val channel = server.channels.find { it.id == channelId } ?: return
    val installations = installationsByServer.value[serverId].orEmpty().filter { it.isEnabledInServer }
    if (installations.isEmpty()) return

    val botsById = bots.value.associateBy { it.id }

    for (inst in installations) {
        val bot = botsById[inst.botId] ?: continue
        if (!bot.isActive) continue

        // 1. Human flood anti-spam moderation shield plugin
        if ("plugin_antispam" in bot.enabledPluginIds && BotPermission.MODERATION_SHIELD in inst.grantedPermissions) {
            val floodAlert = spamLimiter.recordHumanMessageAndDetectFlood(
                serverId = serverId,
                channelId = channelId,
                userId = message.sender.id,
                text = message.text,
                floodThreshold = bot.rateLimit.antiSpamHumanFloodThreshold
            )
            if (floodAlert != null) {
                recordLog(
                    bot = bot,
                    serverId = serverId,
                    actorUid = message.sender.id,
                    actorName = message.sender.name,
                    eventType = "ANTI_SPAM_SHIELD",
                    summary = "Spam détecté de ${message.sender.name} : $floodAlert",
                    status = BotLogStatus.BLOCKED_SPAM
                )
                if (BotPermission.SEND_MESSAGES in inst.grantedPermissions) {
                    chatRepo.postBotChannelMessage(
                        targetId = channelId,
                        botId = bot.id,
                        botName = bot.name,
                        botAvatarUrl = bot.avatarUrl,
                        botAvatarColor = bot.avatarColor,
                        text = "[Anti-Spam] ${message.sender.name}, merci de ralentir ($floodAlert).",
                        isAnnouncement = false
                    )
                }
                continue
            }
        }

        // 2. @bot-handle mention inside channel
        val trimmed = message.text.trim()
        val mentionPrefix = "@${bot.handle}"
        if (trimmed.startsWith(mentionPrefix, ignoreCase = true) &&
            BotPermission.USE_SLASH_COMMANDS in inst.grantedPermissions &&
            BotPermission.SEND_MESSAGES in inst.grantedPermissions
        ) {
            val remainder = trimmed.substring(mentionPrefix.length).trim()
            if (remainder.startsWith("/") || remainder.startsWith("!")) {
                if (handleSlashCommand(channelId, remainder)) continue
            } else if (remainder.isBlank() || isWhatsAppGreetingOrMenu(remainder)) {
                val menuCmd = bot.commands.find { it.isEnabled && it.actionType == BotCommandActionType.INTERACTIVE_MENU }
                    ?: bot.commands.firstOrNull { it.isEnabled }
                if (menuCmd != null) {
                    executeMatchedCommand(
                        bot = bot,
                        cmdSpec = menuCmd,
                        targetId = channelId,
                        server = server,
                        inst = inst,
                        channelName = channel.name,
                        actorUid = message.sender.id,
                        actorName = message.sender.name,
                        actorRole = message.sender.role,
                        args = remainder
                    )
                    continue
                }
            }
        }

        // 3. Auto-response keyword / regex rules
        if (BotEventTrigger.ON_MESSAGE_CREATE in bot.subscribedEvents &&
            BotPermission.READ_MESSAGE_EVENTS in inst.grantedPermissions &&
            BotPermission.SEND_MESSAGES in inst.grantedPermissions
        ) {
            val matchingRule = bot.autoResponses.firstOrNull { rule ->
                rule.isEnabled && ruleMatchesText(rule, message.text)
            }
            if (matchingRule != null) {
                val rendered = renderBotTemplate(
                    template = matchingRule.replyTemplate,
                    bot = bot,
                    server = server,
                    channelName = channel.name,
                    actorName = message.sender.name,
                    args = message.text
                )
                val verdict = spamLimiter.checkAndRecordBotAction(
                    botId = bot.id,
                    serverId = serverId,
                    triggerKey = "auto:${matchingRule.id}",
                    renderedOutput = rendered,
                    cooldownSeconds = matchingRule.cooldownSeconds,
                    rateLimit = bot.rateLimit,
                    senderRole = message.sender.role,
                    senderId = message.sender.id
                )
                when (verdict) {
                    is BotGuardVerdict.Blocked -> {
                        recordLog(bot, serverId, message.sender.id, message.sender.name, "AUTO_${matchingRule.name}", verdict.reason, verdict.status)
                        if (verdict.shouldAutoDisable) {
                            disableBotForAbuse(bot, serverId, verdict.reason)
                        }
                    }
                    BotGuardVerdict.Allowed -> {
                        chatRepo.postBotChannelMessage(
                            targetId = channelId,
                            botId = bot.id,
                            botName = bot.name,
                            botAvatarUrl = bot.avatarUrl,
                            botAvatarColor = bot.avatarColor,
                            text = rendered,
                            quickReplies = listOf(BotQuickReply("Menu", "/menu"))
                        )
                        recordLog(bot, serverId, message.sender.id, message.sender.name, "AUTO_${matchingRule.name}", "Auto-réponse déclenchée dans #${channel.name}.", BotLogStatus.SUCCESS)
                    }
                }
            }
        }

        // 4. Subscribed external webhooks for ON_MESSAGE_CREATE
        if (BotEventTrigger.ON_MESSAGE_CREATE in bot.subscribedEvents) {
            dispatchEventWebhooks(
                bot = bot,
                inst = inst,
                server = server,
                event = BotEventTrigger.ON_MESSAGE_CREATE,
                actorUid = message.sender.id,
                actorName = message.sender.name,
                detail = message.text.take(240)
            )
        }
    }
}

/**
 * WhatsApp-style Bot interaction inside Direct Messages (1-on-1 Bot DM & Group/Friend DMs).
 */
fun BotRepository.handleDirectMessage(dmId: String, message: Message) {
    // Anti-loop shield: bots NEVER respond to bot messages
    if (message.sender.role == UserRole.BOT || message.sender.id.startsWith("bot_") || message.isBotMessage) return

    val conv = chatRepo.directConversations.value.find { it.id == dmId }
    val isBotDm = conv?.isBotChat == true || dmId.startsWith("dm_bot_")
    val trimmed = message.text.trim()
    if (trimmed.isBlank()) return

    if (isBotDm) {
        val botId = conv?.botId ?: dmId.removePrefix("dm_bot_")
        val bot = bots.value.find { it.id == botId } ?: return
        if (!bot.isActive) {
            recordLog(bot, null, message.sender.id, message.sender.name, "DM_BLOCKED", "Bot désactivé.", BotLogStatus.ERROR)
            return
        }
        if (!bot.allowDirectMessages) {
            recordLog(bot, null, message.sender.id, message.sender.name, "DM_BLOCKED", "DMs désactivés pour ce bot.", BotLogStatus.PERMISSION_DENIED)
            return
        }
        if (BotPermission.SEND_MESSAGES !in bot.defaultPermissions) {
            recordLog(bot, null, message.sender.id, message.sender.name, "DM_BLOCKED", "Permission SEND_MESSAGES absente.", BotLogStatus.PERMISSION_DENIED)
            return
        }

        // 1. Direct slash or bang command (even when sent as plain DM message)
        if (trimmed.startsWith("/") || trimmed.startsWith("!")) {
            if (handleSlashCommand(dmId, trimmed)) return
        }

        // 2. WhatsApp numbered reply ("1", "2", "3"...) mapped to last bot message quickReplies or commands
        val chosenNumber = trimmed.toIntOrNull()
        if (chosenNumber != null && chosenNumber >= 1) {
            val previousBotReplies = chatRepo.messages.value[dmId].orEmpty()
                .lastOrNull { (it.isBotMessage || it.sender.role == UserRole.BOT) && it.botQuickReplies.isNotEmpty() }
                ?.botQuickReplies.orEmpty()
            val mappedPayload = previousBotReplies.getOrNull(chosenNumber - 1)?.payload
                ?: bot.commands.filter { it.isEnabled }.getOrNull(chosenNumber - 1)?.trigger
            if (!mappedPayload.isNullOrBlank() && handleSlashCommand(dmId, mappedPayload)) {
                return
            }
        }

        // 3. @bot-handle prefix stripping in 1-on-1 DM
        val mentionPrefix = "@${bot.handle}"
        val effectiveText = if (trimmed.startsWith(mentionPrefix, ignoreCase = true)) {
            val after = trimmed.substring(mentionPrefix.length).trim()
            if (after.startsWith("/") || after.startsWith("!")) {
                if (handleSlashCommand(dmId, after)) return
            }
            after
        } else trimmed

        // 4. Auto-response keyword / regex rules in 1-on-1 DM
        if (BotPermission.READ_MESSAGE_EVENTS in bot.defaultPermissions) {
            val matchingRule = bot.autoResponses.firstOrNull { rule ->
                rule.isEnabled && ruleMatchesText(rule, effectiveText)
            }
            if (matchingRule != null) {
                val rendered = renderBotTemplate(
                    template = matchingRule.replyTemplate,
                    bot = bot,
                    server = null,
                    channelName = "DM ${bot.name}",
                    actorName = message.sender.name,
                    args = effectiveText
                )
                val verdict = spamLimiter.checkAndRecordBotAction(
                    botId = bot.id,
                    serverId = dmId,
                    triggerKey = "dm_auto:${matchingRule.id}",
                    renderedOutput = rendered,
                    cooldownSeconds = matchingRule.cooldownSeconds,
                    rateLimit = bot.rateLimit,
                    senderRole = message.sender.role,
                    senderId = message.sender.id
                )
                if (verdict is BotGuardVerdict.Allowed) {
                    chatRepo.postBotChannelMessage(
                        targetId = dmId,
                        botId = bot.id,
                        botName = bot.name,
                        botAvatarUrl = bot.avatarUrl,
                        botAvatarColor = bot.avatarColor,
                        text = rendered,
                        quickReplies = listOf(BotQuickReply("Menu", "/menu"), BotQuickReply("Aide", "/aide"))
                    )
                    recordLog(bot, null, message.sender.id, message.sender.name, "DM_AUTO_${matchingRule.name}", "Auto-réponse DM envoyée.", BotLogStatus.SUCCESS)
                }
                return
            }
        }

        // 5. WhatsApp Greeting / Menu / Default Interactive Assistant Reply
        val menuSpec = bot.commands.find { it.isEnabled && it.actionType == BotCommandActionType.INTERACTIVE_MENU }
            ?: bot.commands.firstOrNull { it.isEnabled }
        if (effectiveText.isBlank() || isWhatsAppGreetingOrMenu(effectiveText)) {
            if (menuSpec != null) {
                executeMatchedCommand(
                    bot = bot,
                    cmdSpec = menuSpec,
                    targetId = dmId,
                    server = null,
                    inst = null,
                    channelName = bot.name,
                    actorUid = message.sender.id,
                    actorName = message.sender.name,
                    actorRole = message.sender.role,
                    args = ""
                )
                return
            }
        }

        // Helpful fallback in 1-on-1 Bot DM
        val fallbackText = "Bien reçu, ${message.sender.name} ! Utilisez /menu, tapez un numéro (1-${bot.commands.count { it.isEnabled }.coerceAtLeast(1).coerceAtMost(5)}) ou cliquez sur un raccourci :"
        val verdict = spamLimiter.checkAndRecordBotAction(
            botId = bot.id,
            serverId = dmId,
            triggerKey = "dm_fallback",
            renderedOutput = fallbackText,
            cooldownSeconds = 1,
            rateLimit = bot.rateLimit,
            senderRole = message.sender.role,
            senderId = message.sender.id
        )
        if (verdict is BotGuardVerdict.Allowed) {
            val pills = bot.commands.filter { it.isEnabled }.take(4).mapIndexed { i, c ->
                BotQuickReply("${i + 1}. ${c.trigger}", c.trigger)
            }
            chatRepo.postBotChannelMessage(
                targetId = dmId,
                botId = bot.id,
                botName = bot.name,
                botAvatarUrl = bot.avatarUrl,
                botAvatarColor = bot.avatarColor,
                text = fallbackText,
                quickReplies = pills
            )
            recordLog(bot, null, message.sender.id, message.sender.name, "DM_ASSISTANT", "Réponse interactive DM envoyée.", BotLogStatus.SUCCESS)
        }
        return
    }

    // Group DM or Normal DM: respond only if allowGroupMentions = true and mentioned or command/rule matched
    val accessibleBots = bots.value.filter {
        it.isActive &&
            it.allowGroupMentions &&
            (it.ownerUid == message.sender.id || it.isPublicCatalog) &&
            BotPermission.SEND_MESSAGES in it.defaultPermissions
    }
    for (bot in accessibleBots) {
        val mentionPrefix = "@${bot.handle}"
        if (trimmed.startsWith(mentionPrefix, ignoreCase = true)) {
            val remainder = trimmed.substring(mentionPrefix.length).trim()
            if (remainder.startsWith("/") || remainder.startsWith("!")) {
                if (handleSlashCommand(dmId, remainder)) return
            }
            val menuCmd = bot.commands.find { it.isEnabled && it.actionType == BotCommandActionType.INTERACTIVE_MENU }
                ?: bot.commands.firstOrNull { it.isEnabled }
            if (menuCmd != null) {
                executeMatchedCommand(
                    bot = bot,
                    cmdSpec = menuCmd,
                    targetId = dmId,
                    server = null,
                    inst = null,
                    channelName = conv?.groupName ?: conv?.friend?.name ?: "Groupe",
                    actorUid = message.sender.id,
                    actorName = message.sender.name,
                    actorRole = message.sender.role,
                    args = remainder
                )
                return
            }
        }

        if (BotEventTrigger.ON_MESSAGE_CREATE in bot.subscribedEvents &&
            BotPermission.READ_MESSAGE_EVENTS in bot.defaultPermissions
        ) {
            val matchingRule = bot.autoResponses.firstOrNull { it.isEnabled && ruleMatchesText(it, trimmed) }
            if (matchingRule != null) {
                val rendered = renderBotTemplate(
                    template = matchingRule.replyTemplate,
                    bot = bot,
                    server = null,
                    channelName = conv?.groupName ?: conv?.friend?.name ?: "DM",
                    actorName = message.sender.name,
                    args = trimmed
                )
                val verdict = spamLimiter.checkAndRecordBotAction(
                    botId = bot.id,
                    serverId = dmId,
                    triggerKey = "grpdm_auto:${matchingRule.id}",
                    renderedOutput = rendered,
                    cooldownSeconds = matchingRule.cooldownSeconds,
                    rateLimit = bot.rateLimit,
                    senderRole = message.sender.role,
                    senderId = message.sender.id
                )
                if (verdict is BotGuardVerdict.Allowed) {
                    chatRepo.postBotChannelMessage(
                        targetId = dmId,
                        botId = bot.id,
                        botName = bot.name,
                        botAvatarUrl = bot.avatarUrl,
                        botAvatarColor = bot.avatarColor,
                        text = rendered,
                        quickReplies = listOf(BotQuickReply("Menu", "/menu"))
                    )
                    recordLog(bot, null, message.sender.id, message.sender.name, "GROUP_DM_AUTO", "Auto-réponse DM/Groupe déclenchée.", BotLogStatus.SUCCESS)
                }
                return
            }
        }
    }
}

fun BotRepository.handleCommunityEvent(
    serverId: String,
    trigger: BotEventTrigger,
    actorUid: String,
    actorName: String,
    detail: String
) {
    val server = chatRepo.servers.value.find { it.id == serverId } ?: return
    val defaultTextChannel = server.channels.firstOrNull { it.type == ChannelType.TEXT } ?: server.channels.firstOrNull()
    val installations = installationsByServer.value[serverId].orEmpty().filter { it.isEnabledInServer }
    val botsById = bots.value.associateBy { it.id }

    for (inst in installations) {
        val bot = botsById[inst.botId] ?: continue
        if (!bot.isActive) continue

        // Run extensible registered plugins
        bot.enabledPluginIds.forEach { pluginId ->
            val plugin = BotPluginRegistry.findPlugin(pluginId) ?: return@forEach
            if (plugin.requiredPermission !in inst.grantedPermissions) return@forEach
            val ctx = BotExecutionContext(
                bot = bot,
                installation = inst,
                server = server,
                channelId = defaultTextChannel?.id,
                eventTrigger = trigger,
                actorUid = actorUid,
                actorName = actorName,
                messageText = detail
            )
            val result = plugin.handleEvent(ctx)
            if (result != null) {
                if (result.replyMessage != null && defaultTextChannel != null && BotPermission.SEND_MESSAGES in inst.grantedPermissions) {
                    val verdict = spamLimiter.checkAndRecordBotAction(
                        botId = bot.id,
                        serverId = serverId,
                        triggerKey = "plugin:${plugin.id}:${trigger.name}",
                        renderedOutput = result.replyMessage,
                        cooldownSeconds = 2,
                        rateLimit = bot.rateLimit
                    )
                    if (verdict is BotGuardVerdict.Allowed) {
                        chatRepo.postBotChannelMessage(
                            targetId = defaultTextChannel.id,
                            botId = bot.id,
                            botName = bot.name,
                            botAvatarUrl = bot.avatarUrl,
                            botAvatarColor = bot.avatarColor,
                            text = result.replyMessage,
                            quickReplies = result.quickReplies
                        )
                    }
                }
                recordLog(
                    bot = bot,
                    serverId = serverId,
                    actorUid = actorUid,
                    actorName = actorName,
                    eventType = "PLUGIN_${plugin.id}",
                    summary = result.logSummary,
                    status = if (result.isAlert) BotLogStatus.SECURITY_ALERT else BotLogStatus.SUCCESS
                )
            }
        }

        if (trigger in bot.subscribedEvents) {
            dispatchEventWebhooks(bot, inst, server, trigger, actorUid, actorName, detail)
        }
    }
}

private fun BotRepository.dispatchEventWebhooks(
    bot: RavenBot,
    inst: ServerBotInstallation,
    server: Server,
    event: BotEventTrigger,
    actorUid: String,
    actorName: String,
    detail: String
) {
    val matchingHooks = bot.webhooks.filter { it.isEnabled && event in it.subscribedEvents }
    if (matchingHooks.isEmpty()) return
    if (BotPermission.TRIGGER_WEBHOOKS !in inst.grantedPermissions) {
        recordLog(bot, server.id, actorUid, actorName, "WEBHOOK_${event.name}", "Permission TRIGGER_WEBHOOKS non accordée sur ${server.name}.", BotLogStatus.PERMISSION_DENIED)
        return
    }

    val safeDetail = detail.replace("\"", "\\\"").replace("\n", " ")
    val payloadJson = """{"event":"${event.name}","botId":"${bot.id}","serverId":"${server.id}","serverName":"${server.name}","actorUid":"$actorUid","actorName":"$actorName","detail":"$safeDetail"}"""

    matchingHooks.forEach { hook ->
        val secret = webhookSecretFor(bot.id, hook.id)
        chatRepo.repoScope.launch(Dispatchers.IO) {
            try {
                val (statusCode, error) = webhookDispatcher(hook, event, payloadJson, secret)
                recordLog(
                    bot = bot,
                    serverId = server.id,
                    actorUid = actorUid,
                    actorName = actorName,
                    eventType = "WEBHOOK_${event.name}",
                    summary = if (error == null) "Webhook '${hook.label}' HTTP $statusCode OK." else "Webhook '${hook.label}' échec : $error",
                    status = if (error == null) BotLogStatus.WEBHOOK_OK else BotLogStatus.WEBHOOK_FAILED
                )
            } catch (e: Exception) {
                val errMsg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "bots/${bot.id}/webhooks/${hook.id}", actorUid)
                recordLog(bot, server.id, actorUid, actorName, "WEBHOOK_ERROR", errMsg, BotLogStatus.WEBHOOK_FAILED)
            }
        }
    }
}

private fun BotRepository.disableBotForAbuse(bot: RavenBot, serverId: String, reason: String) {
    val disabled = bot.copy(isActive = false)
    upsertBotInFlow(disabled)
    chatRepo.repoScope.launch(Dispatchers.IO) {
        persistBotDoc(disabled)
    }
    recordLog(
        bot = disabled,
        serverId = serverId,
        actorUid = bot.ownerUid,
        actorName = "Système Sécurité Raven",
        eventType = "AUTO_DISABLED",
        summary = "Bot suspendu automatiquement suite à abus répétés : $reason",
        status = BotLogStatus.SECURITY_ALERT
    )
}

// --- Powerful WhatsApp Action Engine Helpers ---

private data class BotCommandOutcome(
    val renderedText: String,
    val quickReplies: List<BotQuickReply> = emptyList(),
    val poll: Poll? = null,
    val reminderTask: Pair<Long, String>? = null
)

private fun BotRepository.evaluateBotCommandAction(
    bot: RavenBot,
    cmdSpec: BotCommandSpec,
    targetId: String,
    server: Server?,
    channelName: String,
    actorName: String,
    args: String
): BotCommandOutcome {
    return when (cmdSpec.actionType) {
        BotCommandActionType.INTERACTIVE_MENU -> {
            val activeCmds = bot.commands.filter { it.isEnabled && normalizeTrigger(it.trigger) != "/menu" }.take(6)
            val lines = activeCmds.mapIndexed { idx, c ->
                "${idx + 1}. ${c.trigger} — ${c.description}"
            }
            val header = renderBotTemplate(
                template = cmdSpec.responseTemplate.ifBlank { "Menu WhatsApp de {bot} ({handle}) :" },
                bot = bot,
                server = server,
                channelName = channelName,
                actorName = actorName,
                args = args
            )
            val body = if (lines.isEmpty()) header else "$header\n${lines.joinToString("\n")}\n\nRépondez par un chiffre (1-${activeCmds.size}) ou cliquez ci-dessous :"
            val replies = if (cmdSpec.quickReplies.isNotEmpty()) {
                cmdSpec.quickReplies
            } else {
                activeCmds.take(5).mapIndexed { idx, c -> BotQuickReply("${idx + 1}. ${c.trigger}", c.trigger) }
            }
            BotCommandOutcome(body, replies)
        }

        BotCommandActionType.BOT_HELP -> {
            val cmds = bot.commands.filter { it.isEnabled }
            val lines = cmds.joinToString("\n") { "• ${it.usageHint.ifBlank { it.trigger }} : ${it.description}" }
            val header = renderBotTemplate(cmdSpec.responseTemplate, bot, server, channelName, actorName, args)
            BotCommandOutcome(
                renderedText = "$header\n$lines",
                quickReplies = cmdSpec.quickReplies.ifEmpty {
                    listOf(BotQuickReply("Menu", "/menu"), BotQuickReply("Calculatrice", "/calc 10+5*2"))
                }
            )
        }

        BotCommandActionType.CALCULATOR -> {
            if (args.isBlank()) {
                BotCommandOutcome(
                    renderedText = "Calculatrice ${bot.name} : indiquez une expression, ex: /calc (15+5)*3",
                    quickReplies = listOf(BotQuickReply("(15+5)*3", "/calc (15+5)*3"), BotQuickReply("Menu", "/menu"))
                )
            } else {
                val res = evaluateSafeMathExpression(args)
                val formatted = res?.let { value ->
                    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e12) {
                        value.toLong().toString()
                    } else {
                        DecimalFormat("#.####").format(value)
                    }
                }
                val text = if (formatted != null) {
                    "${bot.name} • Calcul : $args = $formatted"
                } else {
                    "${bot.name} • Expression invalide : « $args ». Opérateurs autorisés : + - * / % ( )"
                }
                BotCommandOutcome(text, listOf(BotQuickReply("Menu", "/menu")))
            }
        }

        BotCommandActionType.POLL -> {
            val segments = args.split("|").map { it.trim() }.filter { it.isNotBlank() }
            val question = segments.firstOrNull() ?: "Sondage rapide de $actorName ?"
            val optionTexts = if (segments.size >= 3) segments.drop(1).take(5) else listOf("Oui", "Non", "À voir")
            val pollOptions = optionTexts.mapIndexed { idx, txt ->
                PollOption(id = "bot_opt_$idx", text = txt, votes = emptyList())
            }
            val poll = Poll(
                question = question,
                options = pollOptions,
                allowsMultiple = false,
                durationLabel = "Sondage Bot • ${bot.name}"
            )
            BotCommandOutcome(
                renderedText = "${bot.name} lance un sondage pour $actorName :",
                quickReplies = listOf(BotQuickReply("Menu", "/menu")),
                poll = poll
            )
        }

        BotCommandActionType.REMINDER -> {
            val parts = args.split(" ", limit = 2)
            val delayToken = parts.getOrNull(0).orEmpty()
            val delayMs = parseReminderDurationMs(delayToken)
            val label = if (delayMs != null) delayToken else "10s"
            val effectiveDelay = (delayMs ?: 10_000L).coerceIn(30L, 86_400_000L)
            val reminderText = if (delayMs != null) {
                parts.getOrNull(1)?.trim().orEmpty().ifBlank { "Rappel demandé par $actorName" }
            } else {
                args.ifBlank { "Rappel demandé par $actorName" }
            }
            BotCommandOutcome(
                renderedText = "Rappel programmé par ${bot.name} dans $label : « $reminderText »",
                quickReplies = listOf(BotQuickReply("Menu", "/menu")),
                reminderTask = effectiveDelay to reminderText
            )
        }

        BotCommandActionType.NOTE_VAULT -> {
            val scopeNotes = notesVaultByScope.getOrPut("${bot.id}:$targetId") { mutableListOf() }
            val tokens = args.split(" ", limit = 2)
            val subCmd = tokens.getOrNull(0)?.lowercase().orEmpty()
            val subArg = tokens.getOrNull(1)?.trim().orEmpty()
            val text = when {
                subCmd == "add" && subArg.isNotBlank() -> {
                    scopeNotes.add(subArg.take(180))
                    "Note #${scopeNotes.size} enregistrée : « $subArg » (${scopeNotes.size} au total)."
                }
                subCmd == "del" || subCmd == "remove" -> {
                    val idx = (subArg.toIntOrNull() ?: -1) - 1
                    if (idx in scopeNotes.indices) {
                        val removed = scopeNotes.removeAt(idx)
                        "Note #${idx + 1} supprimée (« $removed »)."
                    } else {
                        "Numéro de note invalide. Total actuel : ${scopeNotes.size}."
                    }
                }
                subCmd == "clear" -> {
                    scopeNotes.clear()
                    "Toutes les notes de cette conversation ont été effacées."
                }
                subCmd.isNotBlank() && subCmd != "list" -> {
                    scopeNotes.add(args.take(180))
                    "Note #${scopeNotes.size} ajoutée : « $args »."
                }
                else -> {
                    if (scopeNotes.isEmpty()) {
                        "Bloc-notes vide. Tapez : /note add Acheter café"
                    } else {
                        val listed = scopeNotes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")
                        "Notes enregistrées (${scopeNotes.size}) :\n$listed"
                    }
                }
            }
            BotCommandOutcome(
                renderedText = text,
                quickReplies = cmdSpec.quickReplies.ifEmpty {
                    listOf(BotQuickReply("Voir notes", "/note list"), BotQuickReply("Vider", "/note clear"), BotQuickReply("Menu", "/menu"))
                }
            )
        }

        BotCommandActionType.RANDOM_GAME -> {
            val normalized = normalizeTrigger(cmdSpec.trigger)
            when (normalized) {
                "/roll" -> {
                    val diceMatch = Regex("^(\\d+)d(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(args)
                    val text = if (diceMatch != null) {
                        val count = diceMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 20) ?: 1
                        val sides = diceMatch.groupValues[2].toIntOrNull()?.coerceIn(2, 1000) ?: 6
                        val rolls = List(count) { Random.nextInt(1, sides + 1) }
                        "${bot.name} lance ${count}D$sides pour $actorName : [${rolls.joinToString(", ")}] (Total = ${rolls.sum()})"
                    } else {
                        val sides = args.toIntOrNull()?.coerceIn(2, 1000) ?: 6
                        val res = Random.nextInt(1, sides + 1)
                        "${bot.name} lance 1D$sides pour $actorName : résultat = $res"
                    }
                    BotCommandOutcome(text, listOf(BotQuickReply("Relancer", cmdSpec.usageHint.ifBlank { "/roll 6" }), BotQuickReply("Menu", "/menu")))
                }
                "/choose" -> {
                    val options = args.split("|", ",").map { it.trim() }.filter { it.isNotBlank() }
                    val text = if (options.size >= 2) {
                        val picked = options.random()
                        "${bot.name} choisit : $picked (parmi ${options.size} options)"
                    } else {
                        "Indiquez au moins 2 choix séparés par | (ex: /choose Pizza | Sushi)"
                    }
                    BotCommandOutcome(text, listOf(BotQuickReply("Menu", "/menu")))
                }
                "/8ball" -> {
                    val answers = listOf(
                        "Oui, sans aucun doute !",
                        "C'est très bien parti.",
                        "Probablement oui.",
                        "Demande floue, réessayez plus tard.",
                        "Mieux vaut ne pas compter dessus.",
                        "Non, peu probable."
                    )
                    val qPrefix = if (args.isNotBlank()) "« $args » -> " else ""
                    BotCommandOutcome(
                        renderedText = "Boule magique (${bot.name}) : $qPrefix${answers.random()}",
                        quickReplies = listOf(BotQuickReply("Rejouer", "/8ball"), BotQuickReply("Menu", "/menu"))
                    )
                }
                else -> {
                    // /quiz
                    val quizzes = listOf(
                        "Quiz Raven : Quel langage officiel propulse Jetpack Compose sur Android ?\nA) Kotlin  B) Ruby  C) Pascal  (Réponse : Kotlin)" to
                            listOf(BotQuickReply("Autre question", "/quiz"), BotQuickReply("Menu", "/menu")),
                        "Quiz Raven : Quel algorithme de hachage protège les jetons Bot Raven ?\nA) MD5  B) SHA-256  C) ROT13  (Réponse : SHA-256)" to
                            listOf(BotQuickReply("Autre question", "/quiz"), BotQuickReply("Menu", "/menu")),
                        "Quiz Raven : Quel protocole gère l'audio/vidéo temps réel dans les salons vocaux ?\nA) FTP  B) WebRTC  C) SMTP  (Réponse : WebRTC)" to
                            listOf(BotQuickReply("Autre question", "/quiz"), BotQuickReply("Menu", "/menu"))
                    )
                    val picked = quizzes.random()
                    BotCommandOutcome(picked.first, picked.second)
                }
            }
        }

        BotCommandActionType.STATIC_TEMPLATE,
        BotCommandActionType.WEBHOOK -> {
            val rendered = renderBotTemplate(
                template = cmdSpec.responseTemplate,
                bot = bot,
                server = server,
                channelName = channelName,
                actorName = actorName,
                args = args
            )
            BotCommandOutcome(rendered, cmdSpec.quickReplies)
        }
    }
}

private fun normalizeTrigger(trigger: String): String =
    "/" + trigger.trim().removePrefix("/").removePrefix("!").lowercase()

private fun isWhatsAppGreetingOrMenu(text: String): Boolean {
    val clean = text.trim().lowercase()
    return clean in setOf(
        "bonjour", "bonsoir", "salut", "slt", "hello", "hi", "hey",
        "coucou", "menu", "aide", "help", "start", "options", "?"
    )
}

private fun ruleMatchesText(rule: BotAutoResponseRule, text: String): Boolean {
    if (rule.pattern.isBlank() || text.isBlank()) return false
    return if (rule.isRegex) {
        runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
    } else {
        text.contains(rule.pattern, ignoreCase = true)
    }
}

internal fun renderBotTemplate(
    template: String,
    bot: RavenBot,
    server: Server?,
    channelName: String,
    actorName: String,
    args: String
): String {
    val now = Date()
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
    val dayStr = SimpleDateFormat("EEEE", Locale.FRENCH).format(now)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }

    var out = template
        .replace("{bot}", bot.name)
        .replace("{handle}", "@${bot.handle}")
        .replace("{server}", server?.name ?: "Message Privé")
        .replace("{channel}", channelName)
        .replace("{user}", actorName)
        .replace("{args}", args.ifBlank { "-" })
        .replace("{time}", timeStr)
        .replace("{date}", dateStr)
        .replace("{day}", dayStr)
        .replace("{memberCount}", (server?.members?.size ?: 2).toString())

    // Dynamic {random:min-max}
    out = Regex("\\{random:(\\d+)-(\\d+)\\}").replace(out) { m ->
        val min = m.groupValues[1].toIntOrNull() ?: 1
        val max = m.groupValues[2].toIntOrNull() ?: 10
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        Random.nextInt(lo, hi + 1).toString()
    }

    // Dynamic {pick:a|b|c}
    out = Regex("\\{pick:([^}]+)\\}").replace(out) { m ->
        val choices = m.groupValues[1].split("|").map { it.trim() }.filter { it.isNotBlank() }
        choices.randomOrNull() ?: "-"
    }

    return out
}

private fun parseReminderDurationMs(token: String): Long? {
    val match = Regex("^(\\d+)(ms|s|m|h)$", RegexOption.IGNORE_CASE).matchEntire(token.trim()) ?: return null
    val qty = match.groupValues[1].toLongOrNull() ?: return null
    return when (match.groupValues[2].lowercase()) {
        "ms" -> qty
        "s" -> qty * 1000L
        "m" -> qty * 60_000L
        "h" -> qty * 3_600_000L
        else -> null
    }
}

internal fun evaluateSafeMathExpression(raw: String): Double? {
    val expr = raw.replace(" ", "").replace(",", ".")
    if (expr.isBlank() || expr.length > 100) return null
    var pos = 0
    var currentChar = if (expr.isNotEmpty()) expr[0] else '\u0000'

    fun nextChar() {
        pos++
        currentChar = if (pos < expr.length) expr[pos] else '\u0000'
    }

    fun eat(charToEat: Char): Boolean {
        if (currentChar == charToEat) {
            nextChar()
            return true
        }
        return false
    }

    var parseExpression: (() -> Double?)? = null

    fun parseFactor(): Double? {
        if (eat('+')) return parseFactor()
        if (eat('-')) return parseFactor()?.let { -it }
        val startPos = pos
        val value: Double = if (eat('(')) {
            val inner = parseExpression?.invoke() ?: return null
            if (!eat(')')) return null
            inner
        } else if (currentChar in '0'..'9' || currentChar == '.') {
            while (currentChar in '0'..'9' || currentChar == '.') nextChar()
            expr.substring(startPos, pos).toDoubleOrNull() ?: return null
        } else {
            return null
        }
        return value
    }

    fun parseTerm(): Double? {
        var x = parseFactor() ?: return null
        while (true) {
            when {
                eat('*') -> x *= (parseFactor() ?: return null)
                eat('/') -> {
                    val d = parseFactor() ?: return null
                    if (d == 0.0) return null
                    x /= d
                }
                eat('%') -> {
                    val d = parseFactor() ?: return null
                    if (d == 0.0) return null
                    x %= d
                }
                else -> return x
            }
        }
    }

    parseExpression = parseExpr@{
        var x = parseTerm()
        if (x == null) null
        else {
            while (true) {
                when {
                    eat('+') -> {
                        val right = parseTerm() ?: return@parseExpr null
                        x += right
                    }
                    eat('-') -> {
                        val right = parseTerm() ?: return@parseExpr null
                        x -= right
                    }
                    else -> break
                }
            }
            x
        }
    }

    val result = parseExpression.invoke() ?: return null
    if (pos < expr.length) return null
    return if (result.isFinite()) result else null
}
