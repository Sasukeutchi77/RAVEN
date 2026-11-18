package com.example.data

import com.example.model.AiUsageQuota
import com.example.model.Channel
import com.example.model.Message
import com.example.model.Server
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Redacts private PII and sensitive secrets before any prompt or channel context is sent to AI models.
 */
object AiPrivacySanitizer {

    private val botTokenRegex = Regex("rvnbot_live_[A-Za-z0-9_\\-]{8,}")
    private val googleKeyRegex = Regex("AIza[0-9A-Za-z_\\-]{20,}")
    private val bearerRegex = Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]{12,}")
    private val emailRegex = Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phoneRegex = Regex("(?:\\+?\\d[\\d\\s.-]{7,}\\d)")
    private val promptInjectionRegex = Regex(
        "(?i)(ignore\\s+all\\s+previous|system\\s+prompt|reveal\\s+secret|dump\\s+tokens?|exfiltrat)"
    )

    fun redactSensitiveData(raw: String): String {
        return raw
            .replace(botTokenRegex, "[TOKEN_BOT_MASQUÉ]")
            .replace(googleKeyRegex, "[CLÉ_MASQUÉE]")
            .replace(bearerRegex, "$1[JETON_MASQUÉ]")
            .replace(emailRegex, "[EMAIL_MASQUÉ]")
            .replace(phoneRegex, "[TÉL_MASQUÉ]")
    }

    fun hasAbusivePromptInjection(prompt: String): Boolean {
        return promptInjectionRegex.containsMatchIn(prompt)
    }

    fun sanitizePrompt(rawPrompt: String, maxChars: Int = 4000): Result<String> {
        val cleaned = rawPrompt
            .replace("\u0000", "")
            .trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Le message ne peut pas être vide."))
        }
        if (cleaned.length > maxChars) {
            return Result.failure(
                IllegalArgumentException("Le message dépasse la limite de $maxChars caractères.")
            )
        }
        return Result.success(redactSensitiveData(cleaned))
    }

    fun buildSafeServerContext(
        server: Server,
        channel: Channel?,
        recentMessages: List<Message>,
        maxMessages: Int = 25
    ): String {
        val channelsSummary = server.channels.take(15).joinToString(", ") { ch ->
            "#${ch.name}(${ch.type.name})"
        }
        val eventsSummary = server.events.take(5).joinToString("; ") { ev ->
            "${ev.title} (${ev.dateTimeLabel})"
        }
        val announcementsSummary = server.announcements.take(3).joinToString(" | ") { ann ->
            "${ann.title}: ${redactSensitiveData(ann.content.take(140))}"
        }
        val safeMsgs = recentMessages
            .takeLast(maxMessages.coerceIn(1, 80))
            .joinToString("\n") { m ->
                val cleanBody = redactSensitiveData(m.text.ifBlank { "[Pièce jointe]" }.take(350))
                "${m.sender.name} (${m.timestamp}): $cleanBody"
            }

        return buildString {
            append("Communauté: ${server.name} (@${server.uniqueHandle.ifBlank { server.id }})\n")
            append("Catégorie: ${server.categoryTag} | Visibilité: ${if (server.isPublic) "Publique" else "Privée"}\n")
            append("Description: ${redactSensitiveData(server.description.take(300))}\n")
            append("Règles: ${redactSensitiveData(server.rulesGuidelines.take(400))}\n")
            append("Membres: ${server.members.size} | Boost niveau ${server.boostLevel}\n")
            if (channelsSummary.isNotBlank()) append("Salons: $channelsSummary\n")
            if (eventsSummary.isNotBlank()) append("Événements à venir: $eventsSummary\n")
            if (announcementsSummary.isNotBlank()) append("Annonces récentes: $announcementsSummary\n")
            if (channel != null) append("Salon actif: #${channel.name} (${channel.type.name})\n")
            if (safeMsgs.isNotBlank()) {
                append("Messages récents du salon (données privées masquées):\n")
                append(safeMsgs)
            }
        }
    }
}

sealed interface QuotaCheckResult {
    data object Allowed : QuotaCheckResult
    data class Denied(val reason: String, val isAbuseStrike: Boolean = false) : QuotaCheckResult
}

/**
 * Per-user sliding-window rate limiter, daily quota tracker, and abuse/flood shield.
 */
class AiUsageRateLimiter(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val dayKeyProvider: (Long) -> String = { ms ->
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    }
) {
    private val minuteTimestampsByUser = mutableMapOf<String, MutableList<Long>>()
    private val lastPromptByUser = mutableMapOf<String, Pair<String, Long>>()

    companion object {
        const val DEFAULT_DAILY_LIMIT = 40
        const val DEFAULT_PER_MINUTE_LIMIT = 6
        const val DEFAULT_DAILY_MAX_CHARS = 120_000
        const val DUPLICATE_COOLDOWN_MS = 10_000L
        const val ABUSE_LOCKOUT_STRIKES = 3
        const val ABUSE_LOCKOUT_DURATION_MS = 120_000L
    }

    fun normalizeQuotaForToday(quota: AiUsageQuota, uid: String): AiUsageQuota {
        val now = clockMs()
        val today = dayKeyProvider(now)
        val recentMinuteCount = minuteTimestampsByUser[uid]
            ?.count { now - it < 60_000L } ?: 0
        return if (quota.dayKey != today) {
            AiUsageQuota(
                userId = uid,
                dayKey = today,
                requestsToday = 0,
                dailyLimit = quota.dailyLimit.coerceIn(1, DEFAULT_DAILY_LIMIT),
                requestsInLastMinute = recentMinuteCount,
                perMinuteLimit = quota.perMinuteLimit.coerceIn(1, DEFAULT_PER_MINUTE_LIMIT),
                abuseStrikes = 0,
                blockedUntilMs = 0L,
                estimatedCharsToday = 0
            )
        } else {
            quota.copy(
                userId = uid,
                dailyLimit = quota.dailyLimit.coerceIn(1, DEFAULT_DAILY_LIMIT),
                requestsInLastMinute = recentMinuteCount,
                perMinuteLimit = quota.perMinuteLimit.coerceIn(1, DEFAULT_PER_MINUTE_LIMIT)
            )
        }
    }

    fun checkBeforeCall(
        uid: String,
        normalizedPrompt: String,
        currentQuota: AiUsageQuota
    ): QuotaCheckResult {
        val now = clockMs()
        val quota = normalizeQuotaForToday(currentQuota, uid)

        if (quota.blockedUntilMs > now) {
            val secLeft = ((quota.blockedUntilMs - now) / 1000L).coerceAtLeast(1L)
            return QuotaCheckResult.Denied(
                "Protection anti-abus active : réessayez dans ${secLeft}s."
            )
        }

        if (AiPrivacySanitizer.hasAbusivePromptInjection(normalizedPrompt)) {
            return QuotaCheckResult.Denied(
                "Requête bloquée par la protection anti-abus (tentative d'extraction ou contournement).",
                isAbuseStrike = true
            )
        }

        if (quota.requestsToday >= quota.dailyLimit) {
            return QuotaCheckResult.Denied(
                "Limite quotidienne atteinte (${quota.dailyLimit} requêtes/jour)."
            )
        }

        if (quota.estimatedCharsToday + normalizedPrompt.length > DEFAULT_DAILY_MAX_CHARS) {
            return QuotaCheckResult.Denied(
                "Volume quotidien maximal de caractères IA atteint."
            )
        }

        val stamps = minuteTimestampsByUser.getOrPut(uid) { mutableListOf() }
        stamps.removeAll { now - it >= 60_000L }
        if (stamps.size >= quota.perMinuteLimit) {
            return QuotaCheckResult.Denied(
                "Limite de débit atteinte (${quota.perMinuteLimit} req/min). Patientez quelques secondes.",
                isAbuseStrike = true
            )
        }

        val prev = lastPromptByUser[uid]
        val sig = normalizedPrompt.lowercase().take(180)
        if (prev != null && prev.first == sig && (now - prev.second) < DUPLICATE_COOLDOWN_MS) {
            return QuotaCheckResult.Denied(
                "Requête identique envoyée trop rapidement (anti-spam).",
                isAbuseStrike = true
            )
        }

        return QuotaCheckResult.Allowed
    }

    fun recordAcceptedCall(
        uid: String,
        normalizedPrompt: String,
        currentQuota: AiUsageQuota
    ): AiUsageQuota {
        val now = clockMs()
        val quota = normalizeQuotaForToday(currentQuota, uid)
        val stamps = minuteTimestampsByUser.getOrPut(uid) { mutableListOf() }
        stamps.removeAll { now - it >= 60_000L }
        stamps.add(now)
        lastPromptByUser[uid] = normalizedPrompt.lowercase().take(180) to now

        return quota.copy(
            requestsToday = quota.requestsToday + 1,
            requestsInLastMinute = stamps.size,
            estimatedCharsToday = quota.estimatedCharsToday + normalizedPrompt.length
        )
    }

    fun recordAbuseStrike(uid: String, currentQuota: AiUsageQuota): AiUsageQuota {
        val now = clockMs()
        val quota = normalizeQuotaForToday(currentQuota, uid)
        val nextStrikes = quota.abuseStrikes + 1
        val lockUntil = if (nextStrikes >= ABUSE_LOCKOUT_STRIKES) {
            now + ABUSE_LOCKOUT_DURATION_MS
        } else {
            quota.blockedUntilMs
        }
        return quota.copy(
            abuseStrikes = nextStrikes,
            blockedUntilMs = lockUntil
        )
    }
}
