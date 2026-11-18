package com.example.data

import com.example.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class BotRepositoryUnitTest {

    private fun createEnv(
        scope: TestScope,
        webhookDispatcher: suspend (BotWebhookConfig, BotEventTrigger, String, String?) -> Pair<Int?, String?> =
            { _, _, _, _ -> 200 to null }
    ): Triple<ChatRepository, CommunityRepository, BotRepository> {
        val chatRepo = ChatRepository(scope)
        val commRepo = CommunityRepository(chatRepo)
        val botRepo = BotRepository(chatRepo, webhookDispatcher)
        botRepo.attachToChatRepository()
        return Triple(chatRepo, commRepo, botRepo)
    }

    @Test
    fun `creation normalizes handle rejects duplicate provides one-time token and never exposes plaintext`() = runTest {
        val testScope = TestScope(testScheduler)
        val (_, _, botRepo) = createEnv(testScope)

        val creation = botRepo.createBot(
            name = "Sentinel Mod",
            handleRaw = "@Sentinel Mod!",
            description = "Bot modérateur automatisé"
        ).getOrThrow()

        assertEquals("sentinel-mod", creation.bot.handle)
        assertTrue(creation.oneTimePlaintextToken.startsWith("rvnbot_live_"))
        assertTrue(creation.bot.tokenPrefix.endsWith("••••••••"))
        assertFalse(creation.bot.tokenPrefix == creation.oneTimePlaintextToken)

        // Constant-time SHA-256 verification matches one-time token & rejects wrong token
        assertTrue(botRepo.verifyBotToken(creation.bot.id, creation.oneTimePlaintextToken).getOrThrow())
        assertFalse(botRepo.verifyBotToken(creation.bot.id, "rvnbot_live_fake_invalid").getOrThrow())

        // Duplicate handle rejected
        val dup = botRepo.createBot(name = "Clone", handleRaw = "sentinel-mod", description = "Duplicate")
        assertTrue(dup.isFailure)
    }

    @Test
    fun `token regeneration invalidates old token immediately and rejects unauthorized user`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, _, botRepo) = createEnv(testScope)

        val created = botRepo.createBot("VaultBot", "vault-bot", "Gestion coffre").getOrThrow()
        val oldToken = created.oneTimePlaintextToken

        val regen = botRepo.regenerateBotToken(created.bot.id).getOrThrow()
        assertEquals(2, regen.bot.tokenVersion)
        assertNotEquals(oldToken, regen.oneTimePlaintextToken)
        assertFalse(botRepo.verifyBotToken(created.bot.id, oldToken).getOrThrow())
        assertTrue(botRepo.verifyBotToken(created.bot.id, regen.oneTimePlaintextToken).getOrThrow())

        // Simulate unauthorized user takeover attempt
        chatRepo.updateUserProfile(name = "Intruder", pronouns = "", bio = "", avatarUrl = null, avatarColor = 0L, bannerUrl = null, bannerColor = 0L, customStatus = "", customStatusEmoji = "", activity = "", email = null, phone = null)
        // Force switch uid locally in ChatRepository via auth-less currentUser id mutation
        val otherBot = botRepo.bots.value.first().copy(id = "bot_foreign", ownerUid = "foreign_uid_999", handle = "foreign-bot")
        botRepo.upsertBotInFlow(otherBot)

        assertTrue(botRepo.toggleBotActive("bot_foreign", false).isFailure)
        assertTrue(botRepo.regenerateBotToken("bot_foreign").isFailure)
        assertTrue(botRepo.deleteBot("bot_foreign").isFailure)
        assertTrue(botRepo.verifyBotToken("bot_foreign", "anything").isFailure)
    }

    @Test
    fun `add and remove bot on server enforces admin permissions and syncs BOT member role`() = runTest {
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo, botRepo) = createEnv(testScope)

        val srv = commRepo.createCommunity("Dev Guild", "Communauté dev", "dev-guild").getOrThrow()
        val bot = botRepo.createBot("HelperBot", "helper-bot", "Assistant").getOrThrow().bot

        // Admin (owner) installs bot with custom role & permissions
        val inst = botRepo.addBotToServer(
            botId = bot.id,
            serverId = srv.id,
            grantedPermissions = setOf(BotPermission.SEND_MESSAGES, BotPermission.USE_SLASH_COMMANDS),
            assignedRoleTitle = "Assistant Officiel"
        ).getOrThrow()

        assertEquals(srv.id, inst.serverId)
        val updatedSrv = chatRepo.servers.value.first { it.id == srv.id }
        assertTrue(updatedSrv.members.any { it.id == "bot_${bot.id}" && it.role == UserRole.BOT })

        // Unauthorized regular member simulation: create a second server owned by someone else where currentUser is just MEMBER
        val otherServer = Server(
            id = "srv_foreign",
            name = "Other Guild",
            ownerId = "other_owner",
            adminIds = listOf("other_owner"),
            members = listOf(chatRepo.currentUser.value.copy(role = UserRole.MEMBER)),
            channels = listOf(Channel("c_f1", "srv_foreign", "general", ChannelType.TEXT))
        )
        chatRepo.serversMutableFlow().value = chatRepo.serversMutableFlow().value + otherServer

        val deniedInstall = botRepo.addBotToServer(bot.id, "srv_foreign")
        assertTrue(deniedInstall.isFailure)

        // Admin removes bot from own server cleanly
        assertTrue(botRepo.removeBotFromServer(bot.id, srv.id).isSuccess)
        val afterRemove = chatRepo.servers.value.first { it.id == srv.id }
        assertFalse(afterRemove.members.any { it.id == "bot_${bot.id}" })
        assertTrue(botRepo.installationsByServer.value[srv.id].orEmpty().isEmpty())
    }

    @Test
    fun `commands auto-responses permissions spam shield rate limiter and custom plugins work end to end`() = runTest {
        val webhookDeliveries = mutableListOf<Triple<BotEventTrigger, String, String?>>()
        val testScope = TestScope(testScheduler)
        val (chatRepo, commRepo, botRepo) = createEnv(testScope) { cfg, ev, payload, secret ->
            webhookDeliveries.add(Triple(ev, payload, secret))
            200 to null
        }

        val srv = commRepo.createCommunity("Arena", "Communauté Test Bots", "arena-bots").getOrThrow()
        val textCh = srv.channels.first { it.type == ChannelType.TEXT }
        val bot = botRepo.createBot(
            name = "ModBot",
            handleRaw = "mod-bot",
            description = "Bot commandes + anti-spam"
        ).getOrThrow().bot

        // Validate SSRF / non-HTTPS webhook rejection vs valid HTTPS acceptance
        assertTrue(botRepo.saveWebhook(bot.id, "Bad", "http://127.0.0.1:8080/hook", "sec", setOf(BotEventTrigger.ON_COMMAND_EXECUTED)).isFailure)
        assertTrue(botRepo.saveWebhook(bot.id, "ProdHook", "https://hooks.raven.example/events", "super_secret_hmac", setOf(BotEventTrigger.ON_COMMAND_EXECUTED)).isSuccess)

        // Add custom slash command with cooldown = 0 and custom auto-response
        botRepo.saveBotCommand(
            bot.id,
            BotCommandSpec(
                id = "cmd_rules",
                trigger = "/rules",
                description = "Affiche le règlement",
                responseTemplate = "Règlement de {server} demandé par {user} : soyez respectueux.",
                cooldownSeconds = 0,
                webhookForward = true
            )
        ).getOrThrow()

        botRepo.saveAutoResponse(
            bot.id,
            BotAutoResponseRule(
                id = "rule_hello",
                name = "Accueil mot-clé",
                pattern = "salut",
                replyTemplate = "Salut {user} sur {server} !",
                cooldownSeconds = 0
            )
        ).getOrThrow()

        // Enable anti-spam plugin & tighter rate limit for test
        botRepo.toggleBotPlugin(bot.id, "plugin_antispam", true).getOrThrow()
        botRepo.updateRateLimitConfig(
            bot.id,
            BotRateLimitConfig(
                maxActionsPerMinute = 4,
                duplicateWindowSeconds = 60,
                autoDisableOnAbuseStrikes = 2,
                antiSpamHumanFloodThreshold = 3
            )
        ).getOrThrow()

        // Install bot initially WITHOUT USE_SLASH_COMMANDS -> permission denied logged & no bot reply
        botRepo.addBotToServer(
            botId = bot.id,
            serverId = srv.id,
            grantedPermissions = setOf(BotPermission.READ_MESSAGE_EVENTS),
            assignedRoleTitle = "Observateur"
        ).getOrThrow()
        assertTrue(chatRepo.executeSlashCommand(textCh.id, "/rules"))
        assertTrue(chatRepo.messages.value[textCh.id].orEmpty().none { it.sender.role == UserRole.BOT })
        assertTrue(botRepo.logsByBot.value[bot.id].orEmpty().any { it.status == BotLogStatus.PERMISSION_DENIED })

        // Grant full bot permissions
        botRepo.updateServerBotConfig(
            botId = bot.id,
            serverId = srv.id,
            grantedPermissions = BotPermission.entries.toSet(),
            assignedRoleTitle = "Sentinelle",
            assignedCustomRoleId = null,
            isEnabledInServer = true
        ).getOrThrow()

        // Verify SlashCommandMenu exposure & execute /rules
        assertTrue(botRepo.getSlashCommandsForServer(srv.id).any { it.command == "/rules" })
        assertTrue(chatRepo.executeSlashCommand(textCh.id, "/rules"))
        val afterCmd = chatRepo.messages.value[textCh.id].orEmpty()
        assertTrue(afterCmd.any { it.sender.role == UserRole.BOT && it.text.contains("Règlement de Arena") })

        // Trigger auto-response on human message + verify human flood anti-spam triggers on 3rd rapid message
        chatRepo.sendMessage(textCh.id, "salut tout le monde")
        assertTrue(chatRepo.messages.value[textCh.id].orEmpty().any { it.sender.role == UserRole.BOT && it.text.startsWith("Salut ") })

        chatRepo.sendMessage(textCh.id, "spam identique")
        chatRepo.sendMessage(textCh.id, "spam identique")
        chatRepo.sendMessage(textCh.id, "spam identique")
        assertTrue(chatRepo.messages.value[textCh.id].orEmpty().any { it.sender.role == UserRole.BOT && it.text.contains("[Anti-Spam]") })

        // Trigger repeated identical command outputs -> blocked by duplicate spam / rate limit
        chatRepo.executeSlashCommand(textCh.id, "/rules")
        chatRepo.executeSlashCommand(textCh.id, "/rules")
        chatRepo.executeSlashCommand(textCh.id, "/rules")
        assertTrue(
            botRepo.logsByBot.value[bot.id].orEmpty().any {
                it.status == BotLogStatus.BLOCKED_SPAM || it.status == BotLogStatus.RATE_LIMITED
            }
        )

        // Register & test an extensible external BotPluginHook
        val customHook = object : BotPluginHook {
            override val id = "plugin_custom_test"
            override val name = "Custom Ext"
            override val description = "Plugin d'extension future"
            override val requiredPermission = BotPermission.MANAGE_EVENTS
            override fun handleEvent(context: BotExecutionContext): BotExecutionResult? {
                if (context.eventTrigger != BotEventTrigger.ON_EVENT_CREATED) return null
                return BotExecutionResult(replyMessage = "Extension plugin OK: ${context.messageText}", logSummary = "Plugin custom exécuté")
            }
        }
        BotPluginRegistry.registerPlugin(customHook)
        botRepo.toggleBotActive(bot.id, true).getOrThrow()
        botRepo.toggleBotPlugin(bot.id, "plugin_custom_test", true).getOrThrow()
        commRepo.createCommunityEvent(srv.id, "Tournoi Bots", "Finale", "21h", "Vocal").getOrThrow()
        assertTrue(chatRepo.messages.value[textCh.id].orEmpty().any { it.text.contains("Extension plugin OK: Tournoi Bots") })
        BotPluginRegistry.unregisterPlugin("plugin_custom_test")

        // Delete bot -> cleans everything from server & bot registry
        assertTrue(botRepo.deleteBot(bot.id).isSuccess)
        assertTrue(botRepo.bots.value.none { it.id == bot.id })
        assertTrue(chatRepo.servers.value.first { it.id == srv.id }.members.none { it.id == "bot_${bot.id}" })
    }
}
