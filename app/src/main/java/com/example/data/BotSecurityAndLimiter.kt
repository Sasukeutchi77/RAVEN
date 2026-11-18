package com.example.data

import com.example.model.BotLogStatus
import com.example.model.BotRateLimitConfig
import com.example.model.UserRole
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BotTokenManager {
    private val secureRandom = SecureRandom()
    private val hexChars = "0123456789abcdef".toCharArray()
    private val handleRegex = Regex("^[a-z0-9_\\-]{2,40}$")

    fun generateRawToken(): String {
        val bytes = ByteArray(24) // 192 bits random secret
        secureRandom.nextBytes(bytes)
        val hex = bytesToHex(bytes)
        return "rvnbot_live_$hex"
    }

    fun maskTokenPrefix(rawToken: String): String {
        val visible = rawToken.take(16)
        return "$visible••••••••"
    }

    fun sha256Hex(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    fun verifyTokenAgainstHash(candidateToken: String, expectedHash: String): Boolean {
        if (candidateToken.isBlank() || expectedHash.length != 64) return false
        val actualHash = sha256Hex(candidateToken.trim())
        return MessageDigest.isEqual(
            actualHash.toByteArray(Charsets.UTF_8),
            expectedHash.lowercase().toByteArray(Charsets.UTF_8)
        )
    }

    fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return bytesToHex(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
    }

    fun normalizeHandle(raw: String, fallbackName: String = ""): String {
        val base = raw.ifBlank { fallbackName }
            .trim()
            .removePrefix("@")
            .lowercase()
            .replace(" ", "-")
            .replace(Regex("[^a-z0-9_\\-]"), "")
            .take(36)
        return if (base.length >= 2) base else "bot-${System.currentTimeMillis().toString().takeLast(4)}"
    }

    fun isValidHandleFormat(handle: String): Boolean = handleRegex.matches(handle)

    fun isValidHttpsWebhookUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        val parsed = trimmed.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        val host = parsed.host.lowercase()
        if (host.isBlank() || !host.contains('.')) return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
        if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.") ||
            host == "0.0.0.0" || host.startsWith("169.254.")
        ) {
            return false
        }
        return true
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexChars[v ushr 4]
            out[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(out)
    }
}

sealed class BotGuardVerdict {
    data object Allowed : BotGuardVerdict()
    data class Blocked(
        val reason: String,
        val status: BotLogStatus,
        val shouldAutoDisable: Boolean = false
    ) : BotGuardVerdict()
}

class BotSpamAndRateLimiter(
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val actionTimestamps = mutableMapOf<String, MutableList<Long>>()
    private val triggerCooldowns = mutableMapOf<String, Long>()
    private val recentOutputs = mutableMapOf<String, MutableList<Pair<Long, String>>>()
    private val abuseStrikes = mutableMapOf<String, Int>()
    private val humanMessageHistory = mutableMapOf<String, MutableList<Pair<Long, String>>>()

    @Synchronized
    fun checkAndRecordBotAction(
        botId: String,
        serverId: String,
        triggerKey: String,
        renderedOutput: String,
        cooldownSeconds: Int,
        rateLimit: BotRateLimitConfig,
        senderRole: UserRole = UserRole.MEMBER,
        senderId: String = ""
    ): BotGuardVerdict {
        // Anti-loop guard: never let a bot trigger another bot action
        if (senderRole == UserRole.BOT || senderId.startsWith("bot_")) {
            return BotGuardVerdict.Blocked(
                reason = "Boucle bot-à-bot bloquée par la protection anti-spam.",
                status = BotLogStatus.BLOCKED_SPAM,
                shouldAutoDisable = false
            )
        }

        val now = nowProvider()
        val scopeKey = "$botId::$serverId"

        // 1. Sliding 60s window rate limit
        val windowList = actionTimestamps.getOrPut(scopeKey) { mutableListOf() }
        windowList.removeAll { now - it > 60_000L }
        if (windowList.size >= rateLimit.maxActionsPerMinute.coerceAtLeast(1)) {
            val strikes = (abuseStrikes[botId] ?: 0) + 1
            abuseStrikes[botId] = strikes
            val autoDisable = rateLimit.autoDisableOnAbuseStrikes > 0 && strikes >= rateLimit.autoDisableOnAbuseStrikes
            return BotGuardVerdict.Blocked(
                reason = "Limite de débit atteinte (${rateLimit.maxActionsPerMinute}/min).",
                status = BotLogStatus.RATE_LIMITED,
                shouldAutoDisable = autoDisable
            )
        }

        // 2. Per-command / rule cooldown
        if (cooldownSeconds > 0) {
            val cdKey = "$scopeKey::$triggerKey"
            val nextAllowedAt = triggerCooldowns[cdKey] ?: 0L
            if (now < nextAllowedAt) {
                val rem = ((nextAllowedAt - now + 999L) / 1000L).coerceAtLeast(1L)
                return BotGuardVerdict.Blocked(
                    reason = "Cooldown actif sur $triggerKey (${rem}s restantes).",
                    status = BotLogStatus.RATE_LIMITED,
                    shouldAutoDisable = false
                )
            }
            triggerCooldowns[cdKey] = now + cooldownSeconds * 1000L
        }

        // 3. Duplicate content suppression
        val dupWindowMs = (rateLimit.duplicateWindowSeconds.coerceAtLeast(1)) * 1000L
        val normOutput = renderedOutput.trim().lowercase()
        val dupList = recentOutputs.getOrPut(scopeKey) { mutableListOf() }
        dupList.removeAll { now - it.first > dupWindowMs }
        val dupCount = dupList.count { it.second == normOutput }
        if (dupCount >= 2) {
            val strikes = (abuseStrikes[botId] ?: 0) + 1
            abuseStrikes[botId] = strikes
            val autoDisable = rateLimit.autoDisableOnAbuseStrikes > 0 && strikes >= rateLimit.autoDisableOnAbuseStrikes
            return BotGuardVerdict.Blocked(
                reason = "Réponse répétitive bloquée par l'anti-spam bot.",
                status = BotLogStatus.BLOCKED_SPAM,
                shouldAutoDisable = autoDisable
            )
        }

        windowList.add(now)
        dupList.add(now to normOutput)
        return BotGuardVerdict.Allowed
    }

    @Synchronized
    fun recordHumanMessageAndDetectFlood(
        serverId: String,
        channelId: String,
        userId: String,
        text: String,
        floodThreshold: Int
    ): String? {
        if (userId.startsWith("bot_") || text.isBlank()) return null
        val now = nowProvider()
        val key = "$serverId::$channelId::$userId"
        val entries = humanMessageHistory.getOrPut(key) { mutableListOf() }
        entries.removeAll { now - it.first > 12_000L }
        val norm = text.trim().lowercase()
        entries.add(now to norm)

        val sameCount = entries.count { it.second == norm }
        if (sameCount >= 3) {
            entries.clear()
            return "Messages identiques répétés (x$sameCount)"
        }
        val threshold = floodThreshold.coerceAtLeast(3)
        if (entries.size >= threshold) {
            entries.clear()
            return "Débit excessif ($threshold messages en <12s)"
        }
        return null
    }

    @Synchronized
    fun resetStrikes(botId: String) {
        abuseStrikes.remove(botId)
    }
}
