package com.example.data

import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

internal object AiFirestoreSync {

    fun conversationToMap(conv: AiConversation): HashMap<String, Any> = hashMapOf<String, Any>(
        "id" to conv.id,
        "userId" to conv.userId,
        "title" to conv.title.take(120),
        "mode" to conv.mode.name,
        "modelName" to conv.modelName.take(64),
        "lastMessagePreview" to conv.lastMessagePreview.take(300),
        "messageCount" to conv.messageCount,
        "createdAtMs" to conv.createdAtMs,
        "updatedAtMs" to conv.updatedAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        conv.serverId?.takeIf { it.isNotBlank() }?.let { put("serverId", it) }
        conv.serverName?.takeIf { it.isNotBlank() }?.let { put("serverName", it.take(80)) }
        conv.channelId?.takeIf { it.isNotBlank() }?.let { put("channelId", it) }
        conv.channelName?.takeIf { it.isNotBlank() }?.let { put("channelName", it.take(80)) }
    }

    fun messageToMap(msg: AiChatMessage): HashMap<String, Any> = hashMapOf(
        "id" to msg.id,
        "conversationId" to msg.conversationId,
        "userId" to msg.userId,
        "role" to msg.role.name,
        "content" to msg.content.take(16000),
        "taskCategory" to msg.taskCategory.name,
        "timestampLabel" to msg.timestampLabel.take(24),
        "createdAtMs" to msg.createdAtMs,
        "createdAt" to FieldValue.serverTimestamp()
    )

    fun quotaToMap(quota: AiUsageQuota): HashMap<String, Any> = hashMapOf(
        "userId" to quota.userId,
        "dayKey" to quota.dayKey,
        "requestsToday" to quota.requestsToday,
        "dailyLimit" to quota.dailyLimit,
        "abuseStrikes" to quota.abuseStrikes,
        "blockedUntilMs" to quota.blockedUntilMs,
        "estimatedCharsToday" to quota.estimatedCharsToday,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun mapDocToConversation(doc: DocumentSnapshot): AiConversation? {
        val id = doc.getString("id") ?: doc.id
        val userId = doc.getString("userId") ?: return null
        val title = doc.getString("title") ?: "Discussion IA"
        val modeRaw = doc.getString("mode") ?: AiAssistantMode.PERSONAL.name
        val mode = AiAssistantMode.entries.find { it.name == modeRaw } ?: AiAssistantMode.PERSONAL
        return AiConversation(
            id = id,
            userId = userId,
            title = title,
            mode = mode,
            serverId = doc.getString("serverId"),
            serverName = doc.getString("serverName"),
            channelId = doc.getString("channelId"),
            channelName = doc.getString("channelName"),
            modelName = doc.getString("modelName") ?: AiProviderRequest.DEFAULT_MODEL_NAME,
            lastMessagePreview = doc.getString("lastMessagePreview") ?: "",
            messageCount = (doc.get("messageCount") as? Number)?.toInt() ?: 0,
            createdAtMs = (doc.get("createdAtMs") as? Number)?.toLong() ?: 0L,
            updatedAtMs = (doc.get("updatedAtMs") as? Number)?.toLong() ?: 0L
        )
    }

    fun mapDocToMessage(doc: DocumentSnapshot): AiChatMessage? {
        val id = doc.getString("id") ?: doc.id
        val convId = doc.getString("conversationId") ?: return null
        val userId = doc.getString("userId") ?: return null
        val roleRaw = doc.getString("role") ?: AiMessageRole.USER.name
        val catRaw = doc.getString("taskCategory") ?: AiTaskCategory.CHAT.name
        return AiChatMessage(
            id = id,
            conversationId = convId,
            userId = userId,
            role = AiMessageRole.entries.find { it.name == roleRaw } ?: AiMessageRole.USER,
            content = doc.getString("content") ?: "",
            taskCategory = AiTaskCategory.entries.find { it.name == catRaw } ?: AiTaskCategory.CHAT,
            timestampLabel = doc.getString("timestampLabel") ?: "",
            createdAtMs = (doc.get("createdAtMs") as? Number)?.toLong() ?: 0L
        )
    }

    fun mapDocToQuota(doc: DocumentSnapshot, fallbackUid: String): AiUsageQuota {
        return AiUsageQuota(
            userId = doc.getString("userId") ?: fallbackUid,
            dayKey = doc.getString("dayKey") ?: "",
            requestsToday = (doc.get("requestsToday") as? Number)?.toInt() ?: 0,
            dailyLimit = (doc.get("dailyLimit") as? Number)?.toInt() ?: AiUsageRateLimiter.DEFAULT_DAILY_LIMIT,
            abuseStrikes = (doc.get("abuseStrikes") as? Number)?.toInt() ?: 0,
            blockedUntilMs = (doc.get("blockedUntilMs") as? Number)?.toLong() ?: 0L,
            estimatedCharsToday = (doc.get("estimatedCharsToday") as? Number)?.toInt() ?: 0
        )
    }
}
