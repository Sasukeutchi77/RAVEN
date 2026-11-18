package com.example.data

import android.content.Context
import android.net.Uri
import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.example.util.StoryMediaSecurity
import com.example.util.StoryMediaValidationResult
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class StoryRepository(
    internal val chatRepo: ChatRepository,
    internal val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _stories = MutableStateFlow<List<StoryItem>>(emptyList())
    val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private val _reports = MutableStateFlow<List<StoryReport>>(emptyList())
    val reports: StateFlow<List<StoryReport>> = _reports.asStateFlow()

    private val _lastCleanupStats = MutableStateFlow(StoryCleanupStats())
    val lastCleanupStats: StateFlow<StoryCleanupStats> = _lastCleanupStats.asStateFlow()

    private var publicStoriesListener: ListenerRegistration? = null
    private var sharedStoriesListener: ListenerRegistration? = null
    private var ownStoriesListener: ListenerRegistration? = null
    private var modStoriesListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null
    private var expirationTickerJob: Job? = null

    private val cloudStoriesMap = mutableMapOf<String, StoryItem>()

    init {
        seedInitialDemoStoriesIfOffline()
    }

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    internal fun currentUserName(): String =
        chatRepo.currentUser.value.name.ifBlank { "Membre" }

    internal fun friendIds(): Set<String> {
        val convFriends = chatRepo.directConversations.value
            .filter { !it.isGroup }
            .map { it.friend.id }
        val knownFriends = MockData.allFriends.map { it.id }
        val blocked = blockedAuthorIds()
        return (convFriends + knownFriends).toSet() - blocked - currentUid()
    }

    internal fun blockedAuthorIds(): Set<String> =
        chatRepo.blockedUsers.value.map { it.id }.toSet()

    internal fun communityStaffContext(): Pair<String?, List<String>> {
        val activeSrv = chatRepo.servers.value.firstOrNull { srv ->
            !srv.isDmTab && (srv.id == chatRepo.selectedServerId.value || currentUid() in srv.members.map { it.id })
        } ?: chatRepo.servers.value.firstOrNull { !it.isDmTab }
        val modIds = buildSet {
            if (activeSrv != null) {
                if (activeSrv.ownerId.isNotBlank()) add(activeSrv.ownerId)
                addAll(activeSrv.adminIds)
                addAll(activeSrv.moderatorIds)
                activeSrv.members
                    .filter { it.role == UserRole.ADMIN || it.role == UserRole.MODERATOR }
                    .forEach { add(it.id) }
            }
            add("user_me")
        }.toList()
        return activeSrv?.id to modIds
    }

    fun isCurrentUserModerator(): Boolean {
        val uid = currentUid()
        if (uid == "user_me" && chatRepo.currentUser.value.role in listOf(UserRole.ADMIN, UserRole.MODERATOR)) {
            return true
        }
        return chatRepo.servers.value.any { srv ->
            !srv.isDmTab && CommunityPermissions.hasPermission(srv, uid, RolePermission.MODERATE_MEMBERS)
        }
    }

    private fun seedInitialDemoStoriesIfOffline() {
        val authUser = chatRepo.authInstance()?.currentUser
        if (authUser != null) return
        val now = nowProvider()
        val me = currentUid()
        val thomas = MockData.friendThomas
        val emma = MockData.friendEmma
        val (_, modIds) = communityStaffContext()
        _stories.value = listOf(
            StoryItem(
                id = "story_demo_thomas",
                authorId = thomas.id,
                authorName = thomas.name,
                authorAvatarUrl = thomas.avatarUrl,
                authorAvatarColor = thomas.avatarColor,
                mediaType = StoryMediaType.TEXT,
                textContent = "Session vocale synthwave & dev Android ce soir à 21h ! 🎧🦅",
                caption = "Rejoignez #Salon vocal 1",
                backgroundColorHex = 0xFF5865F2L,
                secondaryColorHex = 0xFFEB459EL,
                visibility = StoryVisibility.FRIENDS,
                allowedViewerIds = listOf(me, thomas.id, emma.id),
                moderatorIds = modIds,
                durationHours = 24,
                createdAtMs = now - 3600_000L * 2,
                expiresAtMs = now + 3600_000L * 22,
                viewCount = 1,
                viewerIds = listOf(emma.id),
                viewers = listOf(
                    StoryViewerEntry(emma.id, emma.name, emma.avatarUrl, emma.avatarColor, now - 1800_000L)
                )
            ),
            StoryItem(
                id = "story_demo_emma",
                authorId = emma.id,
                authorName = emma.name,
                authorAvatarUrl = emma.avatarUrl,
                authorAvatarColor = emma.avatarColor,
                mediaType = StoryMediaType.TEXT,
                textContent = "Nouvelles icônes & badges Raven en cours de design ✨🎨",
                caption = "Dites-moi ce que vous en pensez !",
                backgroundColorHex = 0xFF10B981L,
                secondaryColorHex = 0xFF0284C7L,
                visibility = StoryVisibility.PUBLIC,
                allowedViewerIds = listOf(me, emma.id),
                moderatorIds = modIds,
                durationHours = 24,
                createdAtMs = now - 3600_000L * 4,
                expiresAtMs = now + 3600_000L * 20,
                viewCount = 1,
                viewerIds = listOf(me),
                viewers = listOf(
                    StoryViewerEntry(me, chatRepo.currentUser.value.name, chatRepo.currentUser.value.avatarUrl, chatRepo.currentUser.value.avatarColor, now - 900_000L)
                )
            )
        )
    }

    fun startRealtimeSync() {
        stopRealtimeSync()
        startExpirationTicker()

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db == null || authUser == null) return
        val uid = authUser.uid

        // Remove offline demo stories once connected to live Firestore
        if (_stories.value.all { it.id.startsWith("story_demo_") }) {
            _stories.value = emptyList()
        }

        val storiesCol = db.collection(StoryFirestoreSync.COLLECTION_STORIES)

        publicStoriesListener = storiesCol.whereEqualTo("visibility", StoryVisibility.PUBLIC.name)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeCloudSnapshot(snap.documents.mapNotNull { StoryFirestoreSync.storyFromDoc(it) })
            }

        sharedStoriesListener = storiesCol.whereArrayContains("allowedViewerIds", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeCloudSnapshot(snap.documents.mapNotNull { StoryFirestoreSync.storyFromDoc(it) })
            }

        ownStoriesListener = storiesCol.whereEqualTo("authorId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeCloudSnapshot(snap.documents.mapNotNull { StoryFirestoreSync.storyFromDoc(it) })
            }

        if (isCurrentUserModerator()) {
            modStoriesListener = storiesCol.whereArrayContains("moderatorIds", uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) mergeCloudSnapshot(snap.documents.mapNotNull { StoryFirestoreSync.storyFromDoc(it) })
                }

            reportsListener = db.collection(StoryFirestoreSync.COLLECTION_STORY_REPORTS)
                .whereArrayContains("moderatorIds", uid)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val cloudReports = snap.documents.mapNotNull { StoryFirestoreSync.reportFromDoc(it) }
                            .sortedByDescending { it.createdAtMs }
                        _reports.value = cloudReports
                    }
                }
        }
    }

    fun stopRealtimeSync() {
        publicStoriesListener?.remove()
        sharedStoriesListener?.remove()
        ownStoriesListener?.remove()
        modStoriesListener?.remove()
        reportsListener?.remove()
        publicStoriesListener = null
        sharedStoriesListener = null
        ownStoriesListener = null
        modStoriesListener = null
        reportsListener = null
        expirationTickerJob?.cancel()
        expirationTickerJob = null
    }

    private fun startExpirationTicker() {
        expirationTickerJob?.cancel()
        expirationTickerJob = chatRepo.repoScope.launch {
            while (true) {
                delay(60_000L)
                cleanupExpiredStories()
            }
        }
    }

    private fun mergeCloudSnapshot(docs: List<StoryItem>) {
        synchronized(cloudStoriesMap) {
            docs.forEach { story ->
                val existing = cloudStoriesMap[story.id]
                cloudStoriesMap[story.id] = story.copy(
                    viewers = existing?.viewers?.takeIf { it.isNotEmpty() } ?: story.viewers
                )
            }
            val now = nowProvider()
            _stories.value = cloudStoriesMap.values
                .filter { !it.isExpired(now) }
                .sortedByDescending { it.createdAtMs }
        }
    }

    fun activeVisibleStories(): List<StoryItem> {
        val now = nowProvider()
        val uid = currentUid()
        val blocked = blockedAuthorIds()
        val isMod = isCurrentUserModerator()
        return _stories.value
            .filter { !it.isExpired(now) && it.canBeViewedBy(uid, blocked, isMod) }
            .sortedByDescending { it.createdAtMs }
    }

    fun groupedStories(): Pair<List<StoryAuthorGroup>, List<StoryAuthorGroup>> {
        val uid = currentUid()
        val active = activeVisibleStories()
        val groups = active.groupBy { it.authorId }.map { (authorId, authorStories) ->
            val sorted = authorStories.sortedBy { it.createdAtMs }
            val unseen = sorted.count { !it.hasBeenViewedBy(uid) && authorId != uid }
            val latest = sorted.maxByOrNull { it.createdAtMs }!!
            StoryAuthorGroup(
                authorId = authorId,
                authorName = latest.authorName,
                authorAvatarUrl = latest.authorAvatarUrl,
                authorAvatarColor = latest.authorAvatarColor,
                stories = sorted,
                unseenCount = unseen,
                hasUnseen = unseen > 0,
                latestCreatedAtMs = latest.createdAtMs,
                isCurrentUser = authorId == uid
            )
        }
        val unseenGroups = groups.filter { it.hasUnseen || (it.isCurrentUser && it.stories.any { s -> !s.hasBeenViewedBy(uid) }) }
            .sortedWith(compareByDescending<StoryAuthorGroup> { it.isCurrentUser }.thenByDescending { it.latestCreatedAtMs })
        val seenGroups = groups.filter { it !in unseenGroups }
            .sortedWith(compareByDescending<StoryAuthorGroup> { it.isCurrentUser }.thenByDescending { it.latestCreatedAtMs })
        return unseenGroups to seenGroups
    }

    suspend fun publishTextStory(
        textContent: String,
        caption: String = "",
        backgroundColorHex: Long = 0xFF5865F2L,
        secondaryColorHex: Long = 0xFFEB459EL,
        textColorHex: Long = 0xFFFFFFFFL,
        visibility: StoryVisibility = StoryVisibility.PUBLIC,
        customAllowedViewerIds: Set<String> = emptySet(),
        durationHours: Int = 24
    ): Result<StoryItem> {
        val cleanText = textContent.trim()
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Le texte de la story ne peut pas être vide."))
        }
        if (cleanText.length > 500) {
            return Result.failure(IllegalArgumentException("Le texte de la story ne peut pas dépasser 500 caractères."))
        }
        return createStoryInternal(
            mediaType = StoryMediaType.TEXT,
            mediaUrl = null,
            mediaMimeType = null,
            mediaSizeBytes = 0L,
            textContent = cleanText,
            caption = caption.trim().take(300),
            backgroundColorHex = backgroundColorHex,
            secondaryColorHex = secondaryColorHex,
            textColorHex = textColorHex,
            visibility = visibility,
            customAllowedViewerIds = customAllowedViewerIds,
            durationHours = durationHours
        )
    }

    suspend fun publishMediaStoryFromUri(
        context: Context,
        uri: Uri,
        mediaType: StoryMediaType,
        caption: String = "",
        visibility: StoryVisibility = StoryVisibility.PUBLIC,
        customAllowedViewerIds: Set<String> = emptySet(),
        durationHours: Int = 24
    ): Result<StoryItem> {
        if (mediaType == StoryMediaType.TEXT) {
            return Result.failure(IllegalArgumentException("Type média invalide."))
        }
        return when (val validation = StoryMediaSecurity.secureCopyAndUploadStoryMedia(context, uri, mediaType)) {
            is StoryMediaValidationResult.Invalid -> Result.failure(IllegalArgumentException(validation.reason))
            is StoryMediaValidationResult.Valid -> publishValidatedMediaStory(
                mediaUrl = validation.mediaUrl,
                mediaType = mediaType,
                mimeType = validation.mimeType,
                sizeBytes = validation.sizeBytes,
                caption = caption,
                visibility = visibility,
                customAllowedViewerIds = customAllowedViewerIds,
                durationHours = durationHours
            )
        }
    }

    suspend fun publishValidatedMediaStory(
        mediaUrl: String,
        mediaType: StoryMediaType,
        mimeType: String? = null,
        sizeBytes: Long = 0L,
        caption: String = "",
        visibility: StoryVisibility = StoryVisibility.PUBLIC,
        customAllowedViewerIds: Set<String> = emptySet(),
        durationHours: Int = 24
    ): Result<StoryItem> {
        if (mediaType == StoryMediaType.TEXT) {
            return Result.failure(IllegalArgumentException("Utilisez publishTextStory pour une story texte."))
        }
        if (!StoryMediaSecurity.isValidStoryMediaReference(mediaUrl)) {
            return Result.failure(SecurityException("Source média refusée : chemin ou protocole non sécurisé."))
        }
        val maxSize = if (mediaType == StoryMediaType.VIDEO) StoryMediaSecurity.MAX_VIDEO_BYTES else StoryMediaSecurity.MAX_IMAGE_BYTES
        if (sizeBytes > maxSize) {
            return Result.failure(IllegalArgumentException("Fichier média trop volumineux."))
        }
        return createStoryInternal(
            mediaType = mediaType,
            mediaUrl = mediaUrl.trim(),
            mediaMimeType = mimeType,
            mediaSizeBytes = sizeBytes,
            textContent = "",
            caption = caption.trim().take(300),
            backgroundColorHex = 0xFF111214L,
            secondaryColorHex = 0xFF1E1F22L,
            textColorHex = 0xFFFFFFFFL,
            visibility = visibility,
            customAllowedViewerIds = customAllowedViewerIds,
            durationHours = durationHours
        )
    }

    private suspend fun createStoryInternal(
        mediaType: StoryMediaType,
        mediaUrl: String?,
        mediaMimeType: String?,
        mediaSizeBytes: Long,
        textContent: String,
        caption: String,
        backgroundColorHex: Long,
        secondaryColorHex: Long,
        textColorHex: Long,
        visibility: StoryVisibility,
        customAllowedViewerIds: Set<String>,
        durationHours: Int
    ): Result<StoryItem> {
        val safeHours = durationHours.coerceIn(1, 168)
        if (visibility == StoryVisibility.CUSTOM_USERS && customAllowedViewerIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sélectionnez au moins un utilisateur pour une story personnalisée."))
        }

        val uid = currentUid()
        val now = nowProvider()
        val expiresAt = now + safeHours * 3600_000L
        val user = chatRepo.currentUser.value
        val (modServerId, modIds) = communityStaffContext()

        val resolvedAllowed = when (visibility) {
            StoryVisibility.PUBLIC -> emptyList()
            StoryVisibility.FRIENDS -> (friendIds() + uid).distinct().take(200)
            StoryVisibility.CUSTOM_USERS -> (customAllowedViewerIds + uid - blockedAuthorIds()).distinct().take(200)
        }

        val storyId = "story_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val newStory = StoryItem(
            id = storyId,
            authorId = uid,
            authorName = user.name.ifBlank { "Membre" },
            authorAvatarUrl = user.avatarUrl,
            authorAvatarColor = user.avatarColor,
            mediaType = mediaType,
            mediaUrl = mediaUrl,
            thumbnailUrl = if (mediaType == StoryMediaType.IMAGE) mediaUrl else null,
            mediaMimeType = mediaMimeType,
            mediaSizeBytes = mediaSizeBytes,
            textContent = textContent,
            caption = caption,
            backgroundColorHex = backgroundColorHex,
            secondaryColorHex = secondaryColorHex,
            textColorHex = textColorHex,
            visibility = visibility,
            allowedViewerIds = resolvedAllowed,
            moderatorIds = modIds,
            moderationServerId = modServerId,
            durationHours = safeHours,
            createdAtMs = now,
            expiresAtMs = expiresAt,
            viewCount = 0,
            viewerIds = emptyList(),
            viewers = emptyList()
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val firestoreStory = newStory.copy(
                authorId = authUser.uid,
                allowedViewerIds = if (visibility == StoryVisibility.PUBLIC) emptyList() else (resolvedAllowed + authUser.uid).distinct().take(200)
            )
            val syncRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection(StoryFirestoreSync.COLLECTION_STORIES)
                            .document(storyId)
                            .set(StoryFirestoreSync.storyToCreateMap(firestoreStory))
                    )
                    Result.success(firestoreStory)
                } catch (e: Exception) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "stories/$storyId")
                    Result.failure(e)
                }
            }
            if (syncRes.isFailure) return syncRes
        }

        synchronized(cloudStoriesMap) {
            cloudStoriesMap[newStory.id] = newStory
        }
        _stories.value = listOf(newStory) + _stories.value.filterNot { it.id == newStory.id }

        chatRepo.grantXp(20, "Story RAVEN publiée")
        chatRepo.notifyCommunityEvent(
            title = "Story publiée",
            body = "Votre story (${mediaType.label}, ${visibility.label}) est en ligne pour ${safeHours}h.",
            sourceLabel = "Stories RAVEN",
            type = NotificationType.SYSTEM
        )
        return Result.success(newStory)
    }

    suspend fun markStoryViewed(storyId: String): Result<StoryItem> {
        val now = nowProvider()
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))

        if (story.isExpired(now)) {
            cleanupExpiredStories()
            return Result.failure(IllegalStateException("Cette story a expiré."))
        }

        val uid = currentUid()
        if (!story.canBeViewedBy(uid, blockedAuthorIds(), isCurrentUserModerator())) {
            return Result.failure(SecurityException("Accès refusé à cette story."))
        }

        // Author viewing their own story does not inflate view counter
        if (story.authorId == uid) {
            return Result.success(story)
        }

        val alreadyViewed = story.hasBeenViewedBy(uid)
        val viewer = chatRepo.currentUser.value
        val viewerEntry = StoryViewerEntry(
            viewerId = uid,
            viewerName = viewer.name.ifBlank { "Membre" },
            viewerAvatarUrl = viewer.avatarUrl,
            viewerAvatarColor = viewer.avatarColor,
            viewedAtMs = now
        )

        val updatedViewerIds = if (uid in story.viewerIds) story.viewerIds else story.viewerIds + uid
        val updatedViewers = story.viewers.filterNot { it.viewerId == uid } + viewerEntry
        val updatedCount = if (alreadyViewed) story.viewCount else maxOf(story.viewCount + 1, updatedViewerIds.size)
        val updatedStory = story.copy(
            viewerIds = updatedViewerIds,
            viewers = updatedViewers,
            viewCount = updatedCount
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                try {
                    StoryFirestoreSync.recordViewBlocking(
                        db = db,
                        storyId = storyId,
                        authorId = story.authorId,
                        entry = viewerEntry.copy(viewerId = authUser.uid),
                        incrementCount = !alreadyViewed
                    )
                } catch (e: Exception) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId/views/${authUser.uid}")
                }
            }
        }

        synchronized(cloudStoriesMap) {
            cloudStoriesMap[storyId] = updatedStory
        }
        _stories.value = _stories.value.map { if (it.id == storyId) updatedStory else it }
        return Result.success(updatedStory)
    }

    suspend fun getStoryViewers(storyId: String): Result<List<StoryViewerEntry>> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val uid = currentUid()
        if (story.authorId != uid && !isCurrentUserModerator()) {
            return Result.failure(SecurityException("Seul l'auteur ou un modérateur peut consulter la liste des vues."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val remoteViewers = withContext(Dispatchers.IO) {
                try {
                    val snap = Tasks.await(
                        db.collection(StoryFirestoreSync.COLLECTION_STORIES)
                            .document(storyId)
                            .collection(StoryFirestoreSync.SUBCOLLECTION_VIEWS)
                            .get()
                    )
                    snap.documents.mapNotNull { StoryFirestoreSync.viewFromDoc(it) }
                        .sortedByDescending { it.viewedAtMs }
                } catch (_: Exception) {
                    null
                }
            }
            if (remoteViewers != null) {
                val merged = (remoteViewers + story.viewers)
                    .associateBy { it.viewerId }
                    .values
                    .sortedByDescending { it.viewedAtMs }
                val updated = story.copy(
                    viewers = merged,
                    viewCount = maxOf(story.viewCount, merged.size)
                )
                _stories.value = _stories.value.map { if (it.id == storyId) updated else it }
                return Result.success(merged)
            }
        }
        return Result.success(story.viewers.sortedByDescending { it.viewedAtMs })
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val uid = currentUid()
        val isMod = isCurrentUserModerator()
        if (story.authorId != uid && !isMod) {
            return Result.failure(SecurityException("Vous n'êtes pas autorisé à supprimer cette story."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudDelete = withContext(Dispatchers.IO) {
                try {
                    StoryFirestoreSync.deleteStoryCascadeBlocking(db, storyId)
                    Result.success(Unit)
                } catch (e: Exception) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "stories/$storyId")
                    Result.failure(e)
                }
            }
            if (cloudDelete.isFailure) return cloudDelete
        }

        StoryMediaSecurity.deleteLocalMediaIfOwned(story.mediaUrl)
        synchronized(cloudStoriesMap) {
            cloudStoriesMap.remove(storyId)
        }
        _stories.value = _stories.value.filterNot { it.id == storyId }
        _reports.value = _reports.value.map { r ->
            if (r.storyId == storyId && r.status == StoryReportStatus.PENDING) {
                r.copy(status = StoryReportStatus.REMOVED, reviewedByUid = uid)
            } else r
        }
        return Result.success(Unit)
    }

    suspend fun reportStory(
        storyId: String,
        reason: StoryReportReason,
        details: String = ""
    ): Result<StoryReport> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val uid = currentUid()
        if (!story.canBeViewedBy(uid, blockedAuthorIds(), isCurrentUserModerator())) {
            return Result.failure(SecurityException("Impossible de signaler une story inaccessible."))
        }
        val duplicatePending = _reports.value.any {
            it.storyId == storyId && it.reporterUid == uid && it.status == StoryReportStatus.PENDING
        }
        if (duplicatePending) {
            return Result.failure(IllegalStateException("Vous avez déjà signalé cette story."))
        }

        val now = nowProvider()
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val preview = story.textContent.ifBlank { story.caption }.ifBlank { "Story ${story.mediaType.label}" }
        val report = StoryReport(
            id = reportId,
            storyId = storyId,
            storyAuthorId = story.authorId,
            storyAuthorName = story.authorName,
            storyPreview = preview.take(300),
            storyMediaType = story.mediaType,
            storyMediaUrl = story.mediaUrl,
            reporterUid = uid,
            reporterName = currentUserName(),
            reason = reason,
            details = details.trim().take(500),
            status = StoryReportStatus.PENDING,
            moderatorIds = story.moderatorIds.ifEmpty { communityStaffContext().second },
            moderationServerId = story.moderationServerId ?: communityStaffContext().first,
            createdAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudReport = withContext(Dispatchers.IO) {
                try {
                    val savedReport = report.copy(reporterUid = authUser.uid)
                    Tasks.await(
                        db.collection(StoryFirestoreSync.COLLECTION_STORY_REPORTS)
                            .document(reportId)
                            .set(StoryFirestoreSync.reportToCreateMap(savedReport))
                    )
                    Tasks.await(
                        db.collection(StoryFirestoreSync.COLLECTION_STORIES)
                            .document(storyId)
                            .update(
                                mapOf(
                                    "reportCount" to FieldValue.increment(1L),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                    )
                    Result.success(savedReport)
                } catch (e: Exception) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "story_reports/$reportId")
                    Result.failure(e)
                }
            }
            if (cloudReport.isFailure) return cloudReport
        }

        _reports.value = listOf(report) + _reports.value
        _stories.value = _stories.value.map {
            if (it.id == storyId) it.copy(reportCount = it.reportCount + 1) else it
        }
        chatRepo.notifyCommunityEvent(
            title = "Signalement envoyé",
            body = "Votre signalement pour la story de ${story.authorName} (${reason.label}) a été transmis aux modérateurs.",
            sourceLabel = "Modération Stories",
            type = NotificationType.SYSTEM
        )
        return Result.success(report)
    }

    suspend fun moderateStory(
        storyId: String,
        hideOnly: Boolean,
        reason: String,
        reportId: String? = null
    ): Result<Unit> {
        if (!isCurrentUserModerator()) {
            return Result.failure(SecurityException("Action réservée aux modérateurs ou administrateurs."))
        }
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val modUid = currentUid()
        val cleanReason = reason.trim().ifBlank { "Non conforme aux règles de la communauté" }

        if (!hideOnly) {
            val delRes = deleteStory(storyId)
            if (delRes.isFailure) return delRes
        } else {
            val db = chatRepo.firestoreDb()
            val authUser = chatRepo.authInstance()?.currentUser
            if (db != null && authUser != null) {
                val res = withContext(Dispatchers.IO) {
                    try {
                        Tasks.await(
                            db.collection(StoryFirestoreSync.COLLECTION_STORIES).document(storyId).update(
                                mapOf(
                                    "moderatedHidden" to true,
                                    "moderationReason" to cleanReason.take(240),
                                    "moderatedByUid" to authUser.uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        )
                        Result.success(Unit)
                    } catch (e: Exception) {
                        FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
                        Result.failure(e)
                    }
                }
                if (res.isFailure) return res
            }
            val hiddenStory = story.copy(
                moderatedHidden = true,
                moderationReason = cleanReason,
                moderatedByUid = modUid
            )
            synchronized(cloudStoriesMap) {
                cloudStoriesMap[storyId] = hiddenStory
            }
            _stories.value = _stories.value.map { if (it.id == storyId) hiddenStory else it }
        }

        // Update matching reports status
        val targetReportIds = _reports.value
            .filter { it.id == reportId || (it.storyId == storyId && it.status == StoryReportStatus.PENDING) }
            .map { it.id }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                targetReportIds.forEach { rId ->
                    try {
                        Tasks.await(
                            db.collection(StoryFirestoreSync.COLLECTION_STORY_REPORTS).document(rId).update(
                                mapOf(
                                    "status" to StoryReportStatus.REMOVED.name,
                                    "reviewedByUid" to authUser.uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        _reports.value = _reports.value.map { rep ->
            if (rep.id in targetReportIds) {
                rep.copy(status = StoryReportStatus.REMOVED, reviewedByUid = modUid)
            } else rep
        }
        chatRepo.notifyCommunityEvent(
            title = if (hideOnly) "Story masquée par la modération" else "Story supprimée par la modération",
            body = "Story de ${story.authorName} modérée ($cleanReason).",
            sourceLabel = "Modération Stories",
            type = NotificationType.SYSTEM
        )
        return Result.success(Unit)
    }

    suspend fun dismissReport(reportId: String): Result<Unit> {
        if (!isCurrentUserModerator()) {
            return Result.failure(SecurityException("Action réservée aux modérateurs."))
        }
        val report = _reports.value.find { it.id == reportId }
            ?: return Result.failure(NoSuchElementException("Signalement introuvable."))
        val modUid = currentUid()
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val res = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection(StoryFirestoreSync.COLLECTION_STORY_REPORTS).document(reportId).update(
                            mapOf(
                                "status" to StoryReportStatus.DISMISSED.name,
                                "reviewedByUid" to authUser.uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (res.isFailure) return res
        }
        _reports.value = _reports.value.map {
            if (it.id == report.id) it.copy(status = StoryReportStatus.DISMISSED, reviewedByUid = modUid) else it
        }
        return Result.success(Unit)
    }

    fun replyToStory(storyId: String, replyText: String): Result<Unit> {
        val clean = replyText.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Réponse vide."))
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val authorUser = MockData.allFriends.find { it.id == story.authorId }
            ?: User(id = story.authorId, name = story.authorName, avatarUrl = story.authorAvatarUrl, avatarColor = story.authorAvatarColor)

        val preview = story.textContent.ifBlank { story.caption }.ifBlank { "Story ${story.mediaType.label}" }.take(60)
        chatRepo.sendStoryReplyDm(authorUser, preview, clean)
        chatRepo.notifyCommunityEvent(
            title = "Réponse envoyée à ${story.authorName}",
            body = "En réponse à sa story : « $clean »",
            sourceLabel = "Stories RAVEN",
            type = NotificationType.REPLY
        )
        return Result.success(Unit)
    }

    suspend fun cleanupExpiredStories(): StoryCleanupStats {
        val now = nowProvider()
        val expiredStories = _stories.value.filter { it.isExpired(now) }
        val activeStories = _stories.value.filterNot { it.isExpired(now) }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            withContext(Dispatchers.IO) {
                expiredStories.forEach { expired ->
                    if (expired.authorId == authUser.uid || isCurrentUserModerator()) {
                        try {
                            StoryFirestoreSync.deleteStoryCascadeBlocking(db, expired.id)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        expiredStories.forEach { expired ->
            StoryMediaSecurity.deleteLocalMediaIfOwned(expired.mediaUrl)
            synchronized(cloudStoriesMap) {
                cloudStoriesMap.remove(expired.id)
            }
        }
        _stories.value = activeStories

        val expiredIds = expiredStories.map { it.id }.toSet()
        val beforeReportsCount = _reports.value.size
        _reports.value = _reports.value.filterNot { rep ->
            rep.storyId in expiredIds && rep.status != StoryReportStatus.PENDING
        }
        val removedReports = (beforeReportsCount - _reports.value.size).coerceAtLeast(0)

        var removedFiles = 0
        chatRepo.appContextOrNull()?.let { ctx ->
            val activeMediaPaths = activeStories.mapNotNull { it.mediaUrl }.toSet()
            removedFiles = StoryMediaSecurity.cleanupExpiredLocalMedia(
                mediaDir = StoryMediaSecurity.storyMediaDir(ctx),
                activeMediaPaths = activeMediaPaths,
                nowMs = now
            )
        }

        val stats = StoryCleanupStats(
            removedExpiredStories = expiredStories.size,
            removedMediaFiles = removedFiles,
            removedExpiredReports = removedReports
        )
        _lastCleanupStats.value = stats
        return stats
    }
}
