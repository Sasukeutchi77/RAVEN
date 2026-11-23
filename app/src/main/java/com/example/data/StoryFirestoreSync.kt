package com.example.data

import com.example.model.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object StoryFirestoreSync {
    const val COLLECTION_STORIES = "stories"
    const val SUBCOLLECTION_VIEWS = "views"
    const val COLLECTION_STORY_REPORTS = "story_reports"

    fun storyToCreateMap(story: StoryItem): Map<String, Any> = buildMap {
        put("authorId", story.authorId)
        put("authorName", story.authorName.take(64))
        story.authorAvatarUrl?.takeIf { it.isNotBlank() }?.let { put("authorAvatarUrl", it.take(800)) }
        put("authorAvatarColor", story.authorAvatarColor)
        put("mediaType", story.mediaType.name)
        story.mediaUrl?.takeIf { it.isNotBlank() }?.let { put("mediaUrl", it.take(900)) }
        story.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { put("thumbnailUrl", it.take(900)) }
        story.mediaMimeType?.takeIf { it.isNotBlank() }?.let { put("mediaMimeType", it.take(64)) }
        put("mediaSizeBytes", story.mediaSizeBytes.coerceAtLeast(0L))
        put("textContent", story.textContent.take(500))
        put("caption", story.caption.take(300))
        put("backgroundColorHex", story.backgroundColorHex)
        put("secondaryColorHex", story.secondaryColorHex)
        put("textColorHex", story.textColorHex)
        put("visibility", story.visibility.name)
        put("allowedViewerIds", story.allowedViewerIds.distinct().take(200))
        put("moderatorIds", story.moderatorIds.distinct().take(100))
        story.moderationServerId?.takeIf { it.isNotBlank() }?.let { put("moderationServerId", it.take(128)) }
        put("durationHours", story.durationHours.coerceIn(1, 168))
        put("createdAtMs", story.createdAtMs)
        put("expiresAtMs", story.expiresAtMs)
        put("viewCount", 0)
        put("viewerIds", emptyList<String>())
        put("likesCount", 0)
        put("likedByIds", emptyList<String>())
        put("reportCount", 0)
        put("moderatedHidden", false)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun storyFromDoc(doc: DocumentSnapshot): StoryItem? {
        val data = doc.data ?: return null
        val authorId = (data["authorId"] as? String) ?: return null
        val mediaType = StoryMediaType.fromRaw(data["mediaType"] as? String)
        val visibility = StoryVisibility.fromRaw(data["visibility"] as? String)
        val allowed = (data["allowedViewerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val moderators = (data["moderatorIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val viewerIds = (data["viewerIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val likedByIds = (data["likedByIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return StoryItem(
            id = doc.id,
            authorId = authorId,
            authorName = (data["authorName"] as? String).orEmpty().ifBlank { "Membre" },
            authorAvatarUrl = data["authorAvatarUrl"] as? String,
            authorAvatarColor = (data["authorAvatarColor"] as? Number)?.toLong() ?: 0xFF5865F2L,
            mediaType = mediaType,
            mediaUrl = data["mediaUrl"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            mediaMimeType = data["mediaMimeType"] as? String,
            mediaSizeBytes = (data["mediaSizeBytes"] as? Number)?.toLong() ?: 0L,
            textContent = (data["textContent"] as? String).orEmpty(),
            caption = (data["caption"] as? String).orEmpty(),
            backgroundColorHex = (data["backgroundColorHex"] as? Number)?.toLong() ?: 0xFF5865F2L,
            secondaryColorHex = (data["secondaryColorHex"] as? Number)?.toLong() ?: 0xFFEB459EL,
            textColorHex = (data["textColorHex"] as? Number)?.toLong() ?: 0xFFFFFFFFL,
            visibility = visibility,
            allowedViewerIds = allowed,
            moderatorIds = moderators,
            moderationServerId = data["moderationServerId"] as? String,
            durationHours = (data["durationHours"] as? Number)?.toInt() ?: 24,
            createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
            expiresAtMs = (data["expiresAtMs"] as? Number)?.toLong() ?: 0L,
            viewCount = (data["viewCount"] as? Number)?.toInt() ?: viewerIds.size,
            viewerIds = viewerIds,
            likesCount = (data["likesCount"] as? Number)?.toInt() ?: likedByIds.size,
            likedByIds = likedByIds,
            reportCount = (data["reportCount"] as? Number)?.toInt() ?: 0,
            moderatedHidden = (data["moderatedHidden"] as? Boolean) ?: false,
            moderationReason = data["moderationReason"] as? String,
            moderatedByUid = data["moderatedByUid"] as? String
        )
    }

    fun viewToMap(storyId: String, authorId: String, entry: StoryViewerEntry): Map<String, Any> = buildMap {
        put("viewerId", entry.viewerId)
        put("storyId", storyId)
        put("authorId", authorId)
        put("viewerName", entry.viewerName.take(64))
        entry.viewerAvatarUrl?.takeIf { it.isNotBlank() }?.let { put("viewerAvatarUrl", it.take(800)) }
        put("viewerAvatarColor", entry.viewerAvatarColor)
        put("viewedAtMs", entry.viewedAtMs)
        put("viewedAt", FieldValue.serverTimestamp())
    }

    fun viewFromDoc(doc: DocumentSnapshot): StoryViewerEntry? {
        val data = doc.data ?: return null
        val viewerId = (data["viewerId"] as? String) ?: doc.id
        return StoryViewerEntry(
            viewerId = viewerId,
            viewerName = (data["viewerName"] as? String).orEmpty().ifBlank { "Membre" },
            viewerAvatarUrl = data["viewerAvatarUrl"] as? String,
            viewerAvatarColor = (data["viewerAvatarColor"] as? Number)?.toLong() ?: 0xFF5865F2L,
            viewedAtMs = (data["viewedAtMs"] as? Number)?.toLong() ?: 0L
        )
    }

    fun reportToCreateMap(report: StoryReport): Map<String, Any> = buildMap {
        put("storyId", report.storyId)
        put("storyAuthorId", report.storyAuthorId)
        put("storyAuthorName", report.storyAuthorName.take(64))
        put("storyPreview", report.storyPreview.take(300))
        put("storyMediaType", report.storyMediaType.name)
        report.storyMediaUrl?.takeIf { it.isNotBlank() }?.let { put("storyMediaUrl", it.take(900)) }
        put("reporterUid", report.reporterUid)
        put("reporterName", report.reporterName.take(64))
        put("reason", report.reason.name)
        put("details", report.details.take(500))
        put("status", StoryReportStatus.PENDING.name)
        put("moderatorIds", report.moderatorIds.distinct().take(100))
        report.moderationServerId?.takeIf { it.isNotBlank() }?.let { put("moderationServerId", it.take(128)) }
        put("createdAtMs", report.createdAtMs)
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
    }

    fun reportFromDoc(doc: DocumentSnapshot): StoryReport? {
        val data = doc.data ?: return null
        val storyId = (data["storyId"] as? String) ?: return null
        val reporterUid = (data["reporterUid"] as? String) ?: return null
        return StoryReport(
            id = doc.id,
            storyId = storyId,
            storyAuthorId = (data["storyAuthorId"] as? String).orEmpty(),
            storyAuthorName = (data["storyAuthorName"] as? String).orEmpty(),
            storyPreview = (data["storyPreview"] as? String).orEmpty(),
            storyMediaType = StoryMediaType.fromRaw(data["storyMediaType"] as? String),
            storyMediaUrl = data["storyMediaUrl"] as? String,
            reporterUid = reporterUid,
            reporterName = (data["reporterName"] as? String).orEmpty(),
            reason = StoryReportReason.fromRaw(data["reason"] as? String),
            details = (data["details"] as? String).orEmpty(),
            status = StoryReportStatus.fromRaw(data["status"] as? String),
            moderatorIds = (data["moderatorIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            moderationServerId = data["moderationServerId"] as? String,
            reviewedByUid = data["reviewedByUid"] as? String,
            createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L
        )
    }

    fun deleteStoryCascadeBlocking(db: FirebaseFirestore, storyId: String) {
        val storyRef = db.collection(COLLECTION_STORIES).document(storyId)
        val viewsSnap = Tasks.await(storyRef.collection(SUBCOLLECTION_VIEWS).get())
        val batch = db.batch()
        viewsSnap.documents.forEach { batch.delete(it.reference) }
        batch.delete(storyRef)
        Tasks.await(batch.commit())
    }

    fun recordViewBlocking(
        db: FirebaseFirestore,
        storyId: String,
        authorId: String,
        entry: StoryViewerEntry,
        incrementCount: Boolean
    ) {
        val storyRef = db.collection(COLLECTION_STORIES).document(storyId)
        Tasks.await(
            storyRef.collection(SUBCOLLECTION_VIEWS).document(entry.viewerId)
                .set(viewToMap(storyId, authorId, entry), SetOptions.merge())
        )
        val updateMap = mutableMapOf<String, Any>(
            "viewerIds" to FieldValue.arrayUnion(entry.viewerId),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (incrementCount) {
            updateMap["viewCount"] = FieldValue.increment(1L)
        }
        Tasks.await(storyRef.update(updateMap))
    }

    fun toggleLikeBlocking(
        db: FirebaseFirestore,
        storyId: String,
        nextLikedByIds: List<String>,
        nextLikesCount: Int
    ) {
        val storyRef = db.collection(COLLECTION_STORIES).document(storyId)
        Tasks.await(
            storyRef.update(
                mapOf(
                    "likedByIds" to nextLikedByIds,
                    "likesCount" to nextLikesCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        )
    }
}
