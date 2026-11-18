package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runtime execution engine for Raven Bots:
 * - Executes custom slash commands with permission & rate-limit guards
 * - Evaluates auto-response rules & anti-loop protection on incoming messages
 * - Enforces anti-spam flood shield
 * - Executes enabled plugins (welcome messages, event announcements, custom extensions)
 * - Dispatches HMAC-signed webhooks asynchronously
 */
fun BotRepository.handleSlashCommand(targetId: String, rawInput: String): Boolean {
    val server = chatRepo.servers.value.find { srv -> !srv.isDmTab && srv.channels.any { it.id == targetId } }
        ?: return false
    val channel = server.channels.find { it.id == targetId } ?: return false

    val trimmed = rawInput.trim()
    val parts = trimmed.split(" ", limit = 2)
    val cmdTrigger = parts[0].lowercase()
    val args = parts.getOrNull(1)?.trim().orEmpty()

    val installations = installationsByServer.value[server.id].orEmpty().filter { it.isEnabledInServer }
    val botsById = bots.value.associateBy { it.id }

    for (inst in installations) {
        val bot = botsById[inst.botId] ?: continue
        val cmdSpec = bot.commands.find { it.isEnabled && it.trigger.equals(cmdTrigger, ignoreCase = true) }
            ?: continue

        val actor = chatRepo.currentUser.value
        val actorUid = currentUid()

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

        val rendered = renderBotTemplate(
            template = cmdSpec.responseTemplate,
            bot = bot,
            server = server,
            channelName = channel.name,
            actorName = actor.name,
            args = args
        )

        val verdict = spamLimiter.checkAndRecordBotAction(
            botId = bot.id,
            serverId = server.id,
            triggerKey = "cmd:$cmdTrigger",
            renderedOutput = rendered,
            cooldownSeconds = cmdSpec.cooldownSeconds,
            rateLimit = bot.rateLimit,
            senderRole = actor.role,
            senderId = actorUid
        )

        when (verdict) {
            is BotGuardVerdict.Blocked -> {
                recordLog(bot, server.id, actorUid, actor.name, "COMMAND_$cmdTrigger", verdict.reason, verdict.status)
                if (verdict.shouldAutoDisable) {
                    disableBotForAbuse(bot, server.id, verdict.reason)
                }
                chatRepo.notifyCommunityEvent(
                    title = "Protection Bot (${bot.name})",
                    body = verdict.reason,
                    sourceLabel = bot.name
                )
                return true
            }
            BotGuardVerdict.Allowed -> {
                chatRepo.postBotChannelMessage(
                    targetId = targetId,
                    botId = bot.id,
                    botName = bot.name,
                    botAvatarUrl = bot.avatarUrl,
                    botAvatarColor = bot.avatarColor,
                    text = rendered
                )
                recordLog(
                    bot = bot,
                    serverId = server.id,
                    actorUid = actorUid,
                    actorName = actor.name,
                    eventType = "COMMAND_$cmdTrigger",
                    summary = "Exécution $cmdTrigger dans #${channel.name}.",
                    status = BotLogStatus.SUCCESS
                )
                if (cmdSpec.webhookForward || BotEventTrigger.ON_COMMAND_EXECUTED in bot.subscribedEvents) {
                    dispatchEventWebhooks(
                        bot = bot,
                        inst = inst,
                        server = server,
                        event = BotEventTrigger.ON_COMMAND_EXECUTED,
                        actorUid = actorUid,
                        actorName = actor.name,
                        detail = "$cmdTrigger $args".trim()
                    )
                }
                return true
            }
        }
    }
    return false
}

fun BotRepository.handleChannelMessage(serverId: String, channelId: String, message: Message) {
    // Anti-loop shield: bots NEVER respond to bot messages
    if (message.sender.role == UserRole.BOT || message.sender.id.startsWith("bot_")) return

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

        // 2. Auto-response keyword / regex rules
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
                            text = rendered
                        )
                        recordLog(bot, serverId, message.sender.id, message.sender.name, "AUTO_${matchingRule.name}", "Auto-réponse déclenchée dans #${channel.name}.", BotLogStatus.SUCCESS)
                    }
                }
            }
        }

        // 3. Subscribed external webhooks for ON_MESSAGE_CREATE
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
                            text = result.replyMessage
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

private fun ruleMatchesText(rule: BotAutoResponseRule, text: String): Boolean {
    if (rule.pattern.isBlank() || text.isBlank()) return false
    return if (rule.isRegex) {
        runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(text) }.getOrDefault(false)
    } else {
        text.contains(rule.pattern, ignoreCase = true)
    }
}

private fun renderBotTemplate(
    template: String,
    bot: RavenBot,
    server: Server,
    channelName: String,
    actorName: String,
    args: String
): String = template
    .replace("{bot}", bot.name)
    .replace("{handle}", "@${bot.handle}")
    .replace("{server}", server.name)
    .replace("{channel}", channelName)
    .replace("{user}", actorName)
    .replace("{args}", args.ifBlank { "-" })
