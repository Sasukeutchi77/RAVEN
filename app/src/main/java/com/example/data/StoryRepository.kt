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

    private val _lastCleanupStats = MutableStateFlow(StoryCleanupStats())
    val lastCleanupStats: StateFlow<StoryCleanupStats> = _lastCleanupStats.asStateFlow()

    private var publicStoriesListener: ListenerRegistration? = null
    private var sharedStoriesListener: ListenerRegistration? = null
    private var ownStoriesListener: ListenerRegistration? = null
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

    private fun seedInitialDemoStoriesIfOffline() {
        val authUser = chatRepo.authInstance()?.currentUser
        if (authUser != null) return
        val now = nowProvider()
        val me = currentUid()
        val thomas = MockData.friendThomas
        val emma = MockData.friendEmma
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
    }

    fun stopRealtimeSync() {
        publicStoriesListener?.remove()
        sharedStoriesListener?.remove()
        ownStoriesListener?.remove()
        publicStoriesListener = null
        sharedStoriesListener = null
        ownStoriesListener = null
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

    internal fun memberServerIds(): Set<String> =
        chatRepo.servers.value.filter { !it.isDmTab }.map { it.id }.toSet()

    fun activeVisibleStories(): List<StoryItem> {
        val now = nowProvider()
        val uid = currentUid()
        val blocked = blockedAuthorIds()
        val srvIds = memberServerIds()
        return _stories.value
            .filter { !it.isExpired(now) && it.canBeViewedBy(uid, blocked, srvIds) }
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
        durationHours: Int = 24,
        allowedServerId: String? = null,
        stickerText: String? = null,
        stickerStyle: String = "GLASS",
        quickPollQuestion: String? = null,
        quickPollOptions: List<String> = emptyList(),
        musicTrackTitle: String? = null,
        musicTrackArtist: String? = null
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
            durationHours = durationHours,
            allowedServerId = allowedServerId,
            stickerText = stickerText,
            stickerStyle = stickerStyle,
            quickPollQuestion = quickPollQuestion,
            quickPollOptions = quickPollOptions,
            musicTrackTitle = musicTrackTitle,
            musicTrackArtist = musicTrackArtist
        )
    }

    suspend fun publishMediaStoryFromUri(
        context: Context,
        uri: Uri,
        mediaType: StoryMediaType,
        caption: String = "",
        visibility: StoryVisibility = StoryVisibility.PUBLIC,
        customAllowedViewerIds: Set<String> = emptySet(),
        durationHours: Int = 24,
        allowedServerId: String? = null,
        stickerText: String? = null,
        stickerStyle: String = "GLASS",
        quickPollQuestion: String? = null,
        quickPollOptions: List<String> = emptyList(),
        musicTrackTitle: String? = null,
        musicTrackArtist: String? = null
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
                durationHours = durationHours,
                allowedServerId = allowedServerId,
                stickerText = stickerText,
                stickerStyle = stickerStyle,
                quickPollQuestion = quickPollQuestion,
                quickPollOptions = quickPollOptions,
                musicTrackTitle = musicTrackTitle,
                musicTrackArtist = musicTrackArtist
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
        durationHours: Int = 24,
        allowedServerId: String? = null,
        stickerText: String? = null,
        stickerStyle: String = "GLASS",
        quickPollQuestion: String? = null,
        quickPollOptions: List<String> = emptyList(),
        musicTrackTitle: String? = null,
        musicTrackArtist: String? = null
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
            durationHours = durationHours,
            allowedServerId = allowedServerId,
            stickerText = stickerText,
            stickerStyle = stickerStyle,
            quickPollQuestion = quickPollQuestion,
            quickPollOptions = quickPollOptions,
            musicTrackTitle = musicTrackTitle,
            musicTrackArtist = musicTrackArtist
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
        durationHours: Int,
        allowedServerId: String? = null,
        stickerText: String? = null,
        stickerStyle: String = "GLASS",
        quickPollQuestion: String? = null,
        quickPollOptions: List<String> = emptyList(),
        musicTrackTitle: String? = null,
        musicTrackArtist: String? = null
    ): Result<StoryItem> {
        val safeHours = durationHours.coerceIn(1, 168)
        if (visibility == StoryVisibility.CUSTOM_USERS && customAllowedViewerIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sélectionnez au moins un utilisateur pour une story personnalisée."))
        }
        if (visibility == StoryVisibility.SERVER_MEMBERS && allowedServerId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Sélectionnez un serveur pour restreindre cette story."))
        }

        val uid = currentUid()
        val now = nowProvider()
        val expiresAt = now + safeHours * 3600_000L
        val user = chatRepo.currentUser.value

        val resolvedAllowed = when (visibility) {
            StoryVisibility.PUBLIC -> emptyList()
            StoryVisibility.FRIENDS -> (friendIds() + uid).distinct().take(200)
            StoryVisibility.SERVER_MEMBERS -> {
                val srvMembers = chatRepo.servers.value
                    .find { it.id == allowedServerId }
                    ?.members
                    ?.map { it.id }
                    .orEmpty()
                (srvMembers + uid - blockedAuthorIds()).distinct().take(200)
            }
            StoryVisibility.CUSTOM_USERS -> (customAllowedViewerIds + uid - blockedAuthorIds()).distinct().take(200)
        }

        val cleanPollQ = quickPollQuestion?.trim()?.takeIf { it.isNotBlank() }?.take(120)
        val cleanPollOpts = if (cleanPollQ != null) {
            quickPollOptions.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(4)
        } else {
            emptyList()
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
            allowedServerId = allowedServerId,
            durationHours = safeHours,
            createdAtMs = now,
            expiresAtMs = expiresAt,
            viewCount = 0,
            viewerIds = emptyList(),
            viewers = emptyList(),
            stickerText = stickerText?.trim()?.takeIf { it.isNotBlank() }?.take(100),
            stickerStyle = stickerStyle.ifBlank { "GLASS" },
            quickPollQuestion = cleanPollQ,
            quickPollOptions = cleanPollOpts,
            quickPollVotes = cleanPollOpts.associateWith { 0 },
            musicTrackTitle = musicTrackTitle?.trim()?.takeIf { it.isNotBlank() },
            musicTrackArtist = musicTrackArtist?.trim()?.takeIf { it.isNotBlank() }
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
        if (!story.canBeViewedBy(uid, blockedAuthorIds())) {
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
        if (story.authorId != uid) {
            return Result.failure(SecurityException("Seul l'auteur de la story peut voir qui l'a consultée."))
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

    suspend fun toggleStoryLike(storyId: String): Result<StoryItem> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val now = nowProvider()
        if (story.isExpired(now)) {
            cleanupExpiredStories()
            return Result.failure(IllegalStateException("Cette story a expiré."))
        }
        val uid = currentUid()
        if (story.authorId == uid) {
            return Result.failure(SecurityException("Vous ne pouvez pas liker votre propre story."))
        }
        if (!story.canBeViewedBy(uid, blockedAuthorIds())) {
            return Result.failure(SecurityException("Accès refusé à cette story."))
        }

        val alreadyLiked = story.hasBeenLikedBy(uid)
        val nextLikedIds = if (alreadyLiked) {
            story.likedByIds.filterNot { it == uid }
        } else {
            (story.likedByIds + uid).distinct()
        }
        val nextLikesCount = if (alreadyLiked) {
            (story.likesCount - 1).coerceAtLeast(nextLikedIds.size)
        } else {
            maxOf(story.likesCount + 1, nextLikedIds.size)
        }
        val updatedStory = story.copy(
            likedByIds = nextLikedIds,
            likesCount = nextLikesCount
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null && !storyId.startsWith("story_demo_")) {
            val cloudResult = withContext(Dispatchers.IO) {
                try {
                    val cloudLikedIds = if (alreadyLiked) {
                        story.likedByIds.filterNot { it == authUser.uid }
                    } else {
                        (story.likedByIds + authUser.uid).distinct()
                    }
                    StoryFirestoreSync.toggleLikeBlocking(
                        db = db,
                        storyId = storyId,
                        nextLikedByIds = cloudLikedIds,
                        nextLikesCount = if (alreadyLiked) {
                            (story.likesCount - 1).coerceAtLeast(0)
                        } else {
                            story.likesCount + 1
                        }
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
                    Result.failure(e)
                }
            }
            if (cloudResult.isFailure) {
                return Result.failure(cloudResult.exceptionOrNull()!!)
            }
        }

        synchronized(cloudStoriesMap) {
            cloudStoriesMap[storyId] = updatedStory
        }
        _stories.value = _stories.value.map { if (it.id == storyId) updatedStory else it }
        return Result.success(updatedStory)
    }

    suspend fun voteOnStoryQuickPoll(storyId: String, option: String): Result<StoryItem> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        if (story.quickPollQuestion.isNullOrBlank() || option !in story.quickPollOptions) {
            return Result.failure(IllegalArgumentException("Option de sondage invalide."))
        }
        val uid = currentUid()
        if (uid in story.quickPollVotedByIds) {
            return Result.success(story)
        }
        val nextVotes = story.quickPollVotes.toMutableMap().apply {
            this[option] = (this[option] ?: 0) + 1
        }
        val nextVotedByIds = (story.quickPollVotedByIds + uid).distinct()
        val updated = story.copy(
            quickPollVotes = nextVotes,
            quickPollVotedByIds = nextVotedByIds
        )
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null && !storyId.startsWith("story_demo_")) {
            withContext(Dispatchers.IO) {
                runCatching {
                    StoryFirestoreSync.voteQuickPollBlocking(
                        db = db,
                        storyId = storyId,
                        nextVotes = nextVotes,
                        nextVotedByIds = (story.quickPollVotedByIds + authUser.uid).distinct()
                    )
                }
            }
        }
        synchronized(cloudStoriesMap) {
            cloudStoriesMap[storyId] = updated
        }
        _stories.value = _stories.value.map { if (it.id == storyId) updated else it }
        return Result.success(updated)
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val story = _stories.value.find { it.id == storyId }
            ?: return Result.failure(NoSuchElementException("Story introuvable."))
        val uid = currentUid()
        if (story.authorId != uid) {
            return Result.failure(SecurityException("Seul l'auteur de la story peut la supprimer."))
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
                    if (expired.authorId == authUser.uid) {
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
            removedMediaFiles = removedFiles
        )
        _lastCleanupStats.value = stats
        return stats
    }
}
