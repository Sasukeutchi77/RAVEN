package com.example.data

import android.util.Log
import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object BotFirestoreSync {

    private val webhookHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun botHandleToMap(bot: RavenBot): HashMap<String, Any> = hashMapOf(
        "handle" to bot.handle,
        "botId" to bot.id,
        "ownerUid" to bot.ownerUid,
        "name" to bot.name,
        "createdAt" to FieldValue.serverTimestamp()
    )

    /**
     * Maps public/dashboard bot metadata WITHOUT storing any plaintext token or webhook secrets.
     */
    fun botToMap(bot: RavenBot): HashMap<String, Any> = hashMapOf<String, Any>(
        "id" to bot.id,
        "ownerUid" to bot.ownerUid,
        "name" to bot.name,
        "handle" to bot.handle,
        "avatarColor" to bot.avatarColor,
        "description" to bot.description,
        "isActive" to bot.isActive,
        "isPublicCatalog" to bot.isPublicCatalog,
        "allowDirectMessages" to bot.allowDirectMessages,
        "allowGroupMentions" to bot.allowGroupMentions,
        "welcomeDmMessage" to bot.welcomeDmMessage,
        "tokenPrefix" to bot.tokenPrefix,
        "tokenVersion" to bot.tokenVersion,
        "defaultPermissions" to bot.defaultPermissions.map { it.name },
        "commands" to bot.commands.map { c ->
            mapOf(
                "id" to c.id,
                "trigger" to c.trigger,
                "description" to c.description,
                "usageHint" to c.usageHint,
                "responseTemplate" to c.responseTemplate,
                "cooldownSeconds" to c.cooldownSeconds,
                "isEnabled" to c.isEnabled,
                "webhookForward" to c.webhookForward,
                "actionType" to c.actionType.name,
                "quickReplies" to c.quickReplies.map { qr ->
                    mapOf("label" to qr.label, "payload" to qr.payload)
                }
            )
        },
        "subscribedEvents" to bot.subscribedEvents.map { it.name },
        "autoResponses" to bot.autoResponses.map { r ->
            mapOf(
                "id" to r.id,
                "name" to r.name,
                "pattern" to r.pattern,
                "isRegex" to r.isRegex,
                "replyTemplate" to r.replyTemplate,
                "cooldownSeconds" to r.cooldownSeconds,
                "isEnabled" to r.isEnabled
            )
        },
        "webhooksPublicMeta" to bot.webhooks.map { w ->
            buildMap<String, Any> {
                put("id", w.id)
                put("label", w.label)
                put("endpointUrl", w.endpointUrl)
                put("hasSecret", w.hasSecret)
                put("subscribedEvents", w.subscribedEvents.map { it.name })
                put("isEnabled", w.isEnabled)
                w.lastStatusCode?.let { put("lastStatusCode", it) }
                w.lastDeliveryAtMs?.let { put("lastDeliveryAtMs", it) }
            }
        },
        "enabledPluginIds" to bot.enabledPluginIds.toList(),
        "rateLimit" to mapOf(
            "maxActionsPerMinute" to bot.rateLimit.maxActionsPerMinute,
            "duplicateWindowSeconds" to bot.rateLimit.duplicateWindowSeconds,
            "autoDisableOnAbuseStrikes" to bot.rateLimit.autoDisableOnAbuseStrikes,
            "antiSpamHumanFloodThreshold" to bot.rateLimit.antiSpamHumanFloodThreshold
        ),
        "createdAtMs" to bot.createdAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        bot.avatarUrl?.takeIf { it.isNotBlank() }?.let { put("avatarUrl", it) }
        bot.starterPresetId?.takeIf { it.isNotBlank() }?.let { put("starterPresetId", it) }
    }

    fun botSecretToMap(
        botId: String,
        ownerUid: String,
        tokenHash: String,
        tokenPrefix: String,
        tokenVersion: Int,
        webhookSecrets: Map<String, String> = emptyMap()
    ): HashMap<String, Any> = hashMapOf(
        "botId" to botId,
        "ownerUid" to ownerUid,
        "tokenHash" to tokenHash,
        "tokenPrefix" to tokenPrefix,
        "tokenVersion" to tokenVersion,
        "webhookSecrets" to webhookSecrets,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun serverBotToMap(inst: ServerBotInstallation): HashMap<String, Any> = hashMapOf<String, Any>(
        "botId" to inst.botId,
        "serverId" to inst.serverId,
        "botName" to inst.botName,
        "botHandle" to inst.botHandle,
        "botAvatarColor" to inst.botAvatarColor,
        "ownerUid" to inst.ownerUid,
        "grantedPermissions" to inst.grantedPermissions.map { it.name },
        "assignedRoleTitle" to inst.assignedRoleTitle,
        "isEnabledInServer" to inst.isEnabledInServer,
        "installedByUid" to inst.installedByUid,
        "installedAtMs" to inst.installedAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        inst.botAvatarUrl?.takeIf { it.isNotBlank() }?.let { put("botAvatarUrl", it) }
        inst.assignedCustomRoleId?.takeIf { it.isNotBlank() }?.let { put("assignedCustomRoleId", it) }
    }

    fun botLogToMap(log: BotActivityLog): HashMap<String, Any> = hashMapOf<String, Any>(
        "id" to log.id,
        "botId" to log.botId,
        "ownerUid" to log.ownerUid,
        "actorUid" to log.actorUid,
        "actorName" to log.actorName,
        "eventType" to log.eventType,
        "summary" to log.summary.take(480),
        "status" to log.status.name,
        "timestampLabel" to log.timestampLabel,
        "createdAtMs" to log.createdAtMs
    ).apply {
        log.serverId?.takeIf { it.isNotBlank() }?.let { put("serverId", it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun mapDocToBot(doc: DocumentSnapshot): RavenBot? {
        val id = doc.getString("id") ?: doc.id
        val ownerUid = doc.getString("ownerUid") ?: return null
        val name = doc.getString("name") ?: return null
        val handle = doc.getString("handle") ?: return null

        val defaultPerms = (doc.get("defaultPermissions") as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.let { p -> BotPermission.entries.find { e -> e.name == p } } }
            .toSet()

        val commands = (doc.get("commands") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val cid = m["id"] as? String ?: return@mapNotNull null
            val trigger = m["trigger"] as? String ?: return@mapNotNull null
            val actionName = m["actionType"] as? String
            val actionType = BotCommandActionType.entries.find { it.name == actionName }
                ?: BotCommandActionType.STATIC_TEMPLATE
            val quickReplies = (m["quickReplies"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { qr ->
                val label = (qr["label"] as? String)?.trim().orEmpty()
                val payload = (qr["payload"] as? String)?.trim().orEmpty()
                if (label.isNotEmpty() && payload.isNotEmpty()) BotQuickReply(label, payload) else null
            }
            BotCommandSpec(
                id = cid,
                trigger = trigger,
                description = m["description"] as? String ?: "",
                usageHint = m["usageHint"] as? String ?: trigger,
                responseTemplate = m["responseTemplate"] as? String ?: "",
                cooldownSeconds = (m["cooldownSeconds"] as? Number)?.toInt() ?: 3,
                isEnabled = m["isEnabled"] as? Boolean ?: true,
                webhookForward = m["webhookForward"] as? Boolean ?: false,
                actionType = actionType,
                quickReplies = quickReplies
            )
        }

        val events = (doc.get("subscribedEvents") as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.let { e -> BotEventTrigger.entries.find { ev -> ev.name == e } } }
            .toSet()

        val autoRules = (doc.get("autoResponses") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val rid = m["id"] as? String ?: return@mapNotNull null
            BotAutoResponseRule(
                id = rid,
                name = m["name"] as? String ?: "Règle",
                pattern = m["pattern"] as? String ?: "",
                isRegex = m["isRegex"] as? Boolean ?: false,
                replyTemplate = m["replyTemplate"] as? String ?: "",
                cooldownSeconds = (m["cooldownSeconds"] as? Number)?.toInt() ?: 10,
                isEnabled = m["isEnabled"] as? Boolean ?: true
            )
        }

        val webhooks = (doc.get("webhooksPublicMeta") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
            val wid = m["id"] as? String ?: return@mapNotNull null
            val subEvs = (m["subscribedEvents"] as? List<*>).orEmpty()
                .mapNotNull { (it as? String)?.let { e -> BotEventTrigger.entries.find { ev -> ev.name == e } } }
                .toSet()
            BotWebhookConfig(
                id = wid,
                label = m["label"] as? String ?: "Webhook",
                endpointUrl = m["endpointUrl"] as? String ?: "",
                hasSecret = m["hasSecret"] as? Boolean ?: false,
                subscribedEvents = subEvs,
                isEnabled = m["isEnabled"] as? Boolean ?: true,
                lastStatusCode = (m["lastStatusCode"] as? Number)?.toInt(),
                lastDeliveryAtMs = (m["lastDeliveryAtMs"] as? Number)?.toLong()
            )
        }

        val enabledPlugins = (doc.get("enabledPluginIds") as? List<*>).orEmpty()
            .mapNotNull { it as? String }
            .toSet()

        val rlMap = doc.get("rateLimit") as? Map<String, Any?>
        val rateLimit = BotRateLimitConfig(
            maxActionsPerMinute = (rlMap?.get("maxActionsPerMinute") as? Number)?.toInt() ?: 12,
            duplicateWindowSeconds = (rlMap?.get("duplicateWindowSeconds") as? Number)?.toInt() ?: 20,
            autoDisableOnAbuseStrikes = (rlMap?.get("autoDisableOnAbuseStrikes") as? Number)?.toInt() ?: 5,
            antiSpamHumanFloodThreshold = (rlMap?.get("antiSpamHumanFloodThreshold") as? Number)?.toInt() ?: 5
        )

        return RavenBot(
            id = id,
            ownerUid = ownerUid,
            name = name,
            handle = handle,
            avatarUrl = doc.getString("avatarUrl"),
            avatarColor = doc.getLong("avatarColor") ?: 0xFF5865F2L,
            description = doc.getString("description") ?: "",
            isActive = doc.getBoolean("isActive") ?: true,
            isPublicCatalog = doc.getBoolean("isPublicCatalog") ?: false,
            allowDirectMessages = doc.getBoolean("allowDirectMessages") ?: true,
            allowGroupMentions = doc.getBoolean("allowGroupMentions") ?: true,
            welcomeDmMessage = doc.getString("welcomeDmMessage")
                ?: "Salut {user} ! Je suis {bot} ({handle}). Écris /menu, un chiffre (1, 2, 3...) ou choisis une action rapide ci-dessous.",
            starterPresetId = doc.getString("starterPresetId"),
            tokenPrefix = doc.getString("tokenPrefix") ?: "rvnbot_live_••••••••",
            tokenVersion = doc.getLong("tokenVersion")?.toInt() ?: 1,
            defaultPermissions = defaultPerms,
            commands = commands,
            subscribedEvents = events,
            autoResponses = autoRules,
            webhooks = webhooks,
            enabledPluginIds = enabledPlugins,
            rateLimit = rateLimit,
            createdAtMs = doc.getLong("createdAtMs") ?: 0L
        )
    }

    fun mapDocToServerBot(doc: DocumentSnapshot): ServerBotInstallation? {
        val botId = doc.getString("botId") ?: doc.id
        val serverId = doc.getString("serverId") ?: return null
        val botName = doc.getString("botName") ?: return null
        val perms = (doc.get("grantedPermissions") as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.let { p -> BotPermission.entries.find { e -> e.name == p } } }
            .toSet()
        return ServerBotInstallation(
            botId = botId,
            serverId = serverId,
            botName = botName,
            botHandle = doc.getString("botHandle") ?: "",
            botAvatarUrl = doc.getString("botAvatarUrl"),
            botAvatarColor = doc.getLong("botAvatarColor") ?: 0xFF5865F2L,
            ownerUid = doc.getString("ownerUid") ?: "",
            grantedPermissions = perms,
            assignedRoleTitle = doc.getString("assignedRoleTitle") ?: "Bot Raven",
            assignedCustomRoleId = doc.getString("assignedCustomRoleId"),
            isEnabledInServer = doc.getBoolean("isEnabledInServer") ?: true,
            installedByUid = doc.getString("installedByUid") ?: "",
            installedAtMs = doc.getLong("installedAtMs") ?: 0L
        )
    }

    fun mapDocToBotLog(doc: DocumentSnapshot): BotActivityLog? {
        val id = doc.getString("id") ?: doc.id
        val botId = doc.getString("botId") ?: return null
        val statusName = doc.getString("status") ?: BotLogStatus.SUCCESS.name
        return BotActivityLog(
            id = id,
            botId = botId,
            ownerUid = doc.getString("ownerUid") ?: "",
            serverId = doc.getString("serverId"),
            actorUid = doc.getString("actorUid") ?: "",
            actorName = doc.getString("actorName") ?: "",
            eventType = doc.getString("eventType") ?: "EVENT",
            summary = doc.getString("summary") ?: "",
            status = BotLogStatus.entries.find { it.name == statusName } ?: BotLogStatus.SUCCESS,
            timestampLabel = doc.getString("timestampLabel") ?: "",
            createdAtMs = doc.getLong("createdAtMs") ?: 0L
        )
    }

    suspend fun dispatchWebhook(
        config: BotWebhookConfig,
        event: BotEventTrigger,
        payloadJson: String,
        secret: String?
    ): Pair<Int?, String?> = withContext(Dispatchers.IO) {
        if (!config.isEnabled || !BotTokenManager.isValidHttpsWebhookUrl(config.endpointUrl)) {
            return@withContext null to "URL webhook HTTPS invalide ou désactivée."
        }
        try {
            val host = java.net.URI(config.endpointUrl.trim()).host
                ?: return@withContext null to "Hôte webhook invalide."
            val resolved = java.net.InetAddress.getAllByName(host)
            if (resolved.isEmpty() || resolved.any { !BotTokenManager.isPublicInetAddress(it) }) {
                return@withContext null to "Adresse réseau non publique bloquée par sécurité."
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = payloadJson.toRequestBody(mediaType)
            val reqBuilder = Request.Builder()
                .url(config.endpointUrl.trim())
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-Raven-Event", event.name)
                .header("X-Raven-Webhook-Id", config.id)

            if (!secret.isNullOrBlank()) {
                val sig = BotTokenManager.hmacSha256Hex(secret, payloadJson)
                reqBuilder.header("X-Raven-Signature", "sha256=$sig")
            }

            webhookHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.code to null
                } else {
                    resp.code to "HTTP ${resp.code}"
                }
            }
        } catch (e: Exception) {
            Log.w("BotWebhook", "Webhook dispatch failed: ${e.message}")
            null to (e.message ?: "Erreur réseau webhook")
        }
    }
}
