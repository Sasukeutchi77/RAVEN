package com.example.data

import com.example.model.*
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

internal object PollFirestoreSync {

    fun pollToMap(poll: AdvancedPoll, includeServerTimestamps: Boolean = true): HashMap<String, Any> {
        val optionCounts = linkedMapOf<String, Int>()
        val optionMaps = poll.options.mapIndexed { idx, opt ->
            val key = opt.id.ifBlank { "opt_$idx" }
            optionCounts[key] = opt.voteCount.coerceAtLeast(0)
            hashMapOf<String, Any>(
                "id" to key,
                "text" to opt.text
            )
        }

        return hashMapOf<String, Any>(
            "id" to poll.id,
            "authorId" to poll.authorId,
            "authorName" to poll.authorName.ifBlank { "Membre" },
            "serverName" to poll.serverName,
            "channelName" to poll.channelName,
            "question" to poll.question,
            "description" to poll.description,
            "options" to optionMaps,
            "optionCounts" to optionCounts,
            "allowsMultiple" to poll.allowsMultiple,
            "maxChoices" to poll.maxChoices.coerceIn(1, 10),
            "allowVoteChange" to poll.allowVoteChange,
            "isAnonymous" to poll.isAnonymous,
            "visibility" to poll.visibility.name,
            "isPublic" to (poll.status != PollStatus.DRAFT && poll.visibility == PollVisibility.PUBLIC),
            "allowedRoleIds" to poll.allowedRoleIds,
            "allowedRoleNames" to poll.allowedRoleNames,
            "memberIds" to if (poll.status == PollStatus.DRAFT) emptyList<String>() else poll.memberIds.distinct(),
            "status" to poll.status.name,
            "durationMinutes" to poll.durationMinutes.coerceAtLeast(0),
            "createdAtMs" to poll.createdAtMs,
            "updatedAtMs" to poll.updatedAtMs,
            "totalVotes" to poll.totalVotes.coerceAtLeast(0),
            "totalVoters" to poll.totalVoters.coerceAtLeast(0),
            "notifyOnPublish" to poll.notifyOnPublish,
            "notifyOnClose" to poll.notifyOnClose
        ).apply {
            poll.serverId?.takeIf { it.isNotBlank() }?.let { put("serverId", it) }
            poll.channelId?.takeIf { it.isNotBlank() }?.let { put("channelId", it) }
            poll.expiresAtMs?.let { put("expiresAtMs", it) }
            poll.publishedAtMs?.let { put("publishedAtMs", it) }
            poll.closedAtMs?.let { put("closedAtMs", it) }
            if (includeServerTimestamps) {
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
        }
    }

    fun voteToMap(
        pollId: String,
        voterId: String,
        voterName: String,
        selectedOptionIds: List<String>,
        isAnonymous: Boolean,
        votedAtMs: Long
    ): HashMap<String, Any> = hashMapOf(
        "pollId" to pollId,
        "voterId" to voterId,
        "voterName" to if (isAnonymous) "Anonyme" else voterName.ifBlank { "Membre" }.take(64),
        "selectedOptionIds" to selectedOptionIds.distinct(),
        "votedAtMs" to votedAtMs,
        "updatedAt" to FieldValue.serverTimestamp()
    )

    @Suppress("UNCHECKED_CAST")
    fun mapDocToPoll(doc: DocumentSnapshot): AdvancedPoll? {
        val id = doc.getString("id") ?: doc.id
        val question = doc.getString("question") ?: return null
        val authorId = doc.getString("authorId") ?: return null
        val rawCounts = doc.get("optionCounts") as? Map<String, Any?> ?: emptyMap()
        val rawOptions = doc.get("options") as? List<Map<String, Any?>> ?: emptyList()

        val options = rawOptions.mapIndexed { idx, opt ->
            val optId = (opt["id"] as? String) ?: "opt_$idx"
            val text = (opt["text"] as? String) ?: ""
            val count = (rawCounts[optId] as? Number)?.toInt() ?: 0
            AdvancedPollOption(id = optId, text = text, voteCount = count.coerceAtLeast(0))
        }
        if (options.size < 2) return null

        val visStr = doc.getString("visibility") ?: PollVisibility.SERVER.name
        val visibility = enumValues<PollVisibility>().firstOrNull { it.name == visStr } ?: PollVisibility.SERVER

        val statusStr = doc.getString("status") ?: PollStatus.PUBLISHED.name
        val status = enumValues<PollStatus>().firstOrNull { it.name == statusStr } ?: PollStatus.PUBLISHED

        return AdvancedPoll(
            id = id,
            serverId = doc.getString("serverId"),
            serverName = doc.getString("serverName") ?: "",
            channelId = doc.getString("channelId"),
            channelName = doc.getString("channelName") ?: "",
            authorId = authorId,
            authorName = doc.getString("authorName") ?: "Membre",
            question = question,
            description = doc.getString("description") ?: "",
            options = options,
            allowsMultiple = doc.getBoolean("allowsMultiple") ?: false,
            maxChoices = (doc.getLong("maxChoices")?.toInt() ?: 1).coerceIn(1, 10),
            allowVoteChange = doc.getBoolean("allowVoteChange") ?: true,
            isAnonymous = doc.getBoolean("isAnonymous") ?: false,
            visibility = visibility,
            allowedRoleIds = (doc.get("allowedRoleIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            allowedRoleNames = (doc.get("allowedRoleNames") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            memberIds = (doc.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = status,
            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 1440,
            expiresAtMs = doc.getLong("expiresAtMs"),
            publishedAtMs = doc.getLong("publishedAtMs"),
            closedAtMs = doc.getLong("closedAtMs"),
            createdAtMs = doc.getLong("createdAtMs") ?: 0L,
            updatedAtMs = doc.getLong("updatedAtMs") ?: 0L,
            totalVotes = (doc.getLong("totalVotes")?.toInt() ?: options.sumOf { it.voteCount }).coerceAtLeast(0),
            totalVoters = (doc.getLong("totalVoters")?.toInt() ?: 0).coerceAtLeast(0),
            notifyOnPublish = doc.getBoolean("notifyOnPublish") ?: true,
            notifyOnClose = doc.getBoolean("notifyOnClose") ?: true
        )
    }

    fun mapDocToVote(doc: DocumentSnapshot): PollVoteRecord? {
        val pollId = doc.getString("pollId") ?: return null
        val voterId = doc.getString("voterId") ?: doc.id
        val voterName = doc.getString("voterName") ?: "Membre"
        val selected = (doc.get("selectedOptionIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        if (selected.isEmpty()) return null
        return PollVoteRecord(
            pollId = pollId,
            voterId = voterId,
            voterName = voterName,
            selectedOptionIds = selected,
            votedAtMs = doc.getLong("votedAtMs") ?: 0L
        )
    }
}
