package com.example.data

import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PollRepository(
    internal val chatRepo: ChatRepository,
    internal val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _polls = MutableStateFlow<List<AdvancedPoll>>(emptyList())
    val polls: StateFlow<List<AdvancedPoll>> = _polls.asStateFlow()

    private val _votesByPoll = MutableStateFlow<Map<String, List<PollVoteRecord>>>(emptyMap())
    val votesByPoll: StateFlow<Map<String, List<PollVoteRecord>>> = _votesByPoll.asStateFlow()

    private val _myVotesByPoll = MutableStateFlow<Map<String, PollVoteRecord>>(emptyMap())
    val myVotesByPoll: StateFlow<Map<String, PollVoteRecord>> = _myVotesByPoll.asStateFlow()

    private val cloudPollsById = mutableMapOf<String, AdvancedPoll>()
    private var publicPollsListener: ListenerRegistration? = null
    private var memberPollsListener: ListenerRegistration? = null
    private var ownPollsListener: ListenerRegistration? = null
    private val activeVoteListeners = mutableMapOf<String, ListenerRegistration>()
    private val activeOwnVoteListeners = mutableMapOf<String, ListenerRegistration>()

    init {
        seedInitialDemoPollsIfOffline()
    }

    internal fun currentUid(): String =
        chatRepo.authInstance()?.currentUser?.uid ?: chatRepo.currentUser.value.id

    internal fun currentUserName(): String =
        chatRepo.currentUser.value.name.ifBlank { "Membre" }

    private fun seedInitialDemoPollsIfOffline() {
        if (chatRepo.authInstance()?.currentUser != null) return
        val now = nowProvider()
        val srv = chatRepo.servers.value.firstOrNull { !it.isDmTab }
        val generalCh = srv?.channels?.firstOrNull { it.type == ChannelType.TEXT }
        val demoMemberIds = srv?.members?.map { it.id }?.ifEmpty { listOf("user_me") } ?: listOf("user_me")

        val p1 = AdvancedPoll(
            id = "poll_demo_1",
            serverId = srv?.id,
            serverName = srv?.name ?: "Raven HQ",
            channelId = generalCh?.id,
            channelName = generalCh?.name ?: "général",
            authorId = srv?.ownerId?.ifBlank { "user_me" } ?: "user_me",
            authorName = "Thomas",
            question = "Quel thème pour la prochaine soirée communautaire ?",
            description = "Votez pour l'activité principale de vendredi soir 21h.",
            options = listOf(
                AdvancedPollOption("opt_0", "Tournoi Gaming Coop", 3),
                AdvancedPollOption("opt_1", "Session Vocal & Synthwave", 2),
                AdvancedPollOption("opt_2", "Atelier Dev & Bots", 1)
            ),
            allowsMultiple = true,
            maxChoices = 2,
            allowVoteChange = true,
            isAnonymous = false,
            visibility = PollVisibility.PUBLIC,
            memberIds = demoMemberIds,
            status = PollStatus.PUBLISHED,
            durationMinutes = 1440,
            expiresAtMs = now + 24 * 3600_000L,
            publishedAtMs = now - 3600_000L,
            createdAtMs = now - 3600_000L,
            updatedAtMs = now - 1800_000L,
            totalVotes = 6,
            totalVoters = 4
        )
        val p2 = AdvancedPoll(
            id = "poll_demo_2",
            serverId = srv?.id,
            serverName = srv?.name ?: "Raven HQ",
            channelId = generalCh?.id,
            channelName = generalCh?.name ?: "général",
            authorId = currentUid(),
            authorName = currentUserName(),
            question = "Activer le mode lent sur #général le week-end ?",
            description = "Sondage anonyme à choix unique pour la sérénité du salon.",
            options = listOf(
                AdvancedPollOption("opt_0", "Oui, 10 secondes", 2),
                AdvancedPollOption("opt_1", "Non, garder libre", 1)
            ),
            allowsMultiple = false,
            maxChoices = 1,
            allowVoteChange = true,
            isAnonymous = true,
            visibility = PollVisibility.SERVER,
            memberIds = demoMemberIds,
            status = PollStatus.PUBLISHED,
            durationMinutes = 4320,
            expiresAtMs = now + 72 * 3600_000L,
            publishedAtMs = now - 7200_000L,
            createdAtMs = now - 7200_000L,
            updatedAtMs = now - 3600_000L,
            totalVotes = 3,
            totalVoters = 3
        )
        _polls.value = listOf(p1, p2)
        _votesByPoll.value = mapOf(
            p1.id to listOf(
                PollVoteRecord(p1.id, "user_thomas", "Thomas", listOf("opt_0", "opt_1"), now - 3000_000L),
                PollVoteRecord(p1.id, "user_emma", "Emma", listOf("opt_0"), now - 2500_000L),
                PollVoteRecord(p1.id, "user_lucas", "Lucas", listOf("opt_1", "opt_2"), now - 2000_000L),
                PollVoteRecord(p1.id, "user_chloe", "Chloé", listOf("opt_0"), now - 1800_000L)
            )
        )
    }

    fun startListening(uid: String) {
        val db = chatRepo.firestoreDb() ?: return
        if (uid.isBlank() || chatRepo.authInstance()?.currentUser == null) return
        stopListening()

        publicPollsListener = db.collection("polls")
            .whereEqualTo("isPublic", true)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotPolls(snap.documents, uid)
            }

        memberPollsListener = db.collection("polls")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotPolls(snap.documents, uid)
            }

        ownPollsListener = db.collection("polls")
            .whereEqualTo("authorId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) mergeSnapshotPolls(snap.documents, uid)
            }
    }

    fun stopListening() {
        publicPollsListener?.remove()
        publicPollsListener = null
        memberPollsListener?.remove()
        memberPollsListener = null
        ownPollsListener?.remove()
        ownPollsListener = null
        activeVoteListeners.values.forEach { it.remove() }
        activeVoteListeners.clear()
        activeOwnVoteListeners.values.forEach { it.remove() }
        activeOwnVoteListeners.clear()
    }

    private fun mergeSnapshotPolls(docs: List<DocumentSnapshot>, uid: String) {
        val mapped = docs.mapNotNull { PollFirestoreSync.mapDocToPoll(it) }
        synchronized(cloudPollsById) {
            mapped.forEach { poll ->
                if (poll.status != PollStatus.DRAFT || poll.authorId == uid) {
                    cloudPollsById[poll.id] = poll
                }
            }
            _polls.value = cloudPollsById.values.sortedByDescending { it.updatedAtMs.coerceAtLeast(it.createdAtMs) }
        }
        mapped.forEach { poll ->
            if (poll.status != PollStatus.DRAFT) {
                listenVotesForPoll(poll.id, poll.isAnonymous)
            }
        }
    }

    fun listenVotesForPoll(pollId: String, isAnonymous: Boolean) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return

        if (!activeOwnVoteListeners.containsKey(pollId)) {
            activeOwnVoteListeners[pollId] = db.collection("polls").document(pollId)
                .collection("votes").document(uid)
                .addSnapshotListener { doc, _ ->
                    val record = if (doc != null && doc.exists()) PollFirestoreSync.mapDocToVote(doc) else null
                    val nextMap = _myVotesByPoll.value.toMutableMap()
                    if (record != null) nextMap[pollId] = record else nextMap.remove(pollId)
                    _myVotesByPoll.value = nextMap
                }
        }

        if (!isAnonymous && !activeVoteListeners.containsKey(pollId)) {
            activeVoteListeners[pollId] = db.collection("polls").document(pollId)
                .collection("votes")
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        val records = snap.documents.mapNotNull { PollFirestoreSync.mapDocToVote(it) }
                        _votesByPoll.value = _votesByPoll.value + (pollId to records)
                    }
                }
        } else if (isAnonymous && activeVoteListeners.containsKey(pollId)) {
            activeVoteListeners.remove(pollId)?.remove()
            _votesByPoll.value = _votesByPoll.value - pollId
        }
    }

    suspend fun saveDraft(input: PollDraftInput): Result<AdvancedPoll> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))

        val cleanQuestion = input.question.trim()
        if (cleanQuestion.length < 3) {
            return Result.failure(IllegalArgumentException("La question doit contenir au moins 3 caractères."))
        }
        val cleanOptions = input.options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanOptions.size < 2) {
            return Result.failure(IllegalArgumentException("Ajoutez au moins 2 options distinctes."))
        }
        if (cleanOptions.size > 10) {
            return Result.failure(IllegalArgumentException("Maximum 10 options autorisées."))
        }
        if (input.visibility == PollVisibility.ROLE_RESTRICTED && input.allowedRoleIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sélectionnez au moins un rôle autorisé."))
        }
        if (input.visibility != PollVisibility.PUBLIC && input.serverId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Un sondage de communauté nécessite un serveur."))
        }

        val existing = input.pollId?.let { id -> _polls.value.find { it.id == id } }
        if (existing != null) {
            if (existing.authorId != uid) {
                return Result.failure(SecurityException("Seul l'auteur peut modifier ce brouillon."))
            }
            if (existing.status != PollStatus.DRAFT) {
                return Result.failure(IllegalStateException("Modification impossible : le sondage est déjà publié."))
            }
        }

        val srv = input.serverId?.let { sid -> chatRepo.servers.value.find { it.id == sid } }
        val channel = input.channelId?.let { cid -> srv?.channels?.find { it.id == cid } }
        val localMembers = buildList {
            add(uid)
            srv?.ownerId?.takeIf { it.isNotBlank() }?.let { add(it) }
            srv?.members?.forEach { add(it.id) }
        }
        val memberIds = resolveServerMemberIds(input.serverId, localMembers)

        val now = nowProvider()
        val pollId = existing?.id ?: "poll_${UUID.randomUUID().toString().take(8)}"
        val maxChoices = if (input.allowsMultiple) {
            input.maxChoices.coerceIn(2, cleanOptions.size)
        } else {
            1
        }
        val durationMin = input.durationMinutes.coerceAtLeast(0)
        val previewExpiresAt = computeExpiresAt(now, durationMin, input.customExpiresAtMs)

        val draft = AdvancedPoll(
            id = pollId,
            serverId = input.serverId,
            serverName = srv?.name ?: "",
            channelId = input.channelId,
            channelName = channel?.name ?: "",
            authorId = uid,
            authorName = currentUserName(),
            question = cleanQuestion,
            description = input.description.trim().take(1000),
            options = cleanOptions.mapIndexed { idx, txt ->
                AdvancedPollOption(id = "opt_$idx", text = txt, voteCount = 0)
            },
            allowsMultiple = input.allowsMultiple,
            maxChoices = maxChoices,
            allowVoteChange = input.allowVoteChange,
            isAnonymous = input.isAnonymous,
            visibility = input.visibility,
            allowedRoleIds = if (input.visibility == PollVisibility.ROLE_RESTRICTED) input.allowedRoleIds.distinct() else emptyList(),
            allowedRoleNames = if (input.visibility == PollVisibility.ROLE_RESTRICTED) input.allowedRoleNames else emptyList(),
            memberIds = memberIds,
            status = PollStatus.DRAFT,
            durationMinutes = durationMin,
            expiresAtMs = previewExpiresAt,
            publishedAtMs = null,
            closedAtMs = null,
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
            totalVotes = 0,
            totalVoters = 0,
            notifyOnPublish = input.notifyOnPublish,
            notifyOnClose = input.notifyOnClose
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("polls").document(draft.id)
                            .set(PollFirestoreSync.pollToMap(draft))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "polls/${draft.id}", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        upsertLocalPoll(draft)
        return Result.success(draft)
    }

    suspend fun publishPoll(
        pollId: String,
        shareInChannel: Boolean = true,
        customExpiresAtMs: Long? = null
    ): Result<AdvancedPoll> {
        val uid = currentUid()
        val existing = _polls.value.find { it.id == pollId }
            ?: return Result.failure(IllegalArgumentException("Sondage introuvable."))
        if (existing.authorId != uid) {
            return Result.failure(SecurityException("Seul l'auteur peut publier ce brouillon."))
        }
        if (existing.status != PollStatus.DRAFT) {
            return Result.failure(IllegalStateException("Ce sondage est déjà publié."))
        }

        val now = nowProvider()
        val finalExpiresAt = computeExpiresAt(now, existing.durationMinutes, customExpiresAtMs ?: existing.expiresAtMs)
        if (finalExpiresAt != null && finalExpiresAt <= now) {
            return Result.failure(IllegalArgumentException("La date d'expiration doit être dans le futur."))
        }

        val srv = existing.serverId?.let { sid -> chatRepo.servers.value.find { it.id == sid } }
        val baseIds = buildList {
            add(uid)
            addAll(existing.memberIds)
            srv?.ownerId?.takeIf { it.isNotBlank() }?.let { add(it) }
            srv?.members?.forEach { add(it.id) }
        }
        val refreshedMemberIds = resolveServerMemberIds(existing.serverId, baseIds)

        val published = existing.copy(
            memberIds = refreshedMemberIds,
            status = PollStatus.PUBLISHED,
            publishedAtMs = now,
            expiresAtMs = finalExpiresAt,
            updatedAtMs = now
        )

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("polls").document(published.id)
                            .set(PollFirestoreSync.pollToMap(published))
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "polls/${published.id}", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        upsertLocalPoll(published)
        listenVotesForPoll(published.id, published.isAnonymous)

        if (shareInChannel && !published.channelId.isNullOrBlank()) {
            chatRepo.postAdvancedPollChannelCard(published.channelId, published)
        }
        if (published.notifyOnPublish) {
            val scopeLabel = published.serverName.ifBlank { "RAVEN Public" }
            val modeText = if (published.allowsMultiple) "Choix multiples" else "Choix unique"
            val anonText = if (published.isAnonymous) " • Anonyme" else ""
            chatRepo.notifyCommunityEvent(
                title = "Nouveau sondage : ${published.question}",
                body = "${published.authorName} a lancé un sondage ($modeText$anonText).",
                sourceLabel = scopeLabel,
                type = NotificationType.SYSTEM
            )
        }
        return Result.success(published)
    }

    suspend fun createAndPublishPoll(
        input: PollDraftInput,
        shareInChannel: Boolean = true
    ): Result<AdvancedPoll> {
        val draftRes = saveDraft(input)
        if (draftRes.isFailure) return draftRes
        return publishPoll(draftRes.getOrThrow().id, shareInChannel = shareInChannel, customExpiresAtMs = input.customExpiresAtMs)
    }

    fun canUserVote(poll: AdvancedPoll, uid: String = currentUid()): Pair<Boolean, String?> {
        val now = nowProvider()
        if (poll.status == PollStatus.DRAFT) {
            return false to "Sondage encore en brouillon."
        }
        if (poll.status == PollStatus.CLOSED) {
            return false to "Ce sondage a été clôturé manuellement."
        }
        if (poll.isExpired(now)) {
            return false to "Ce sondage est expiré."
        }
        val myExisting = _myVotesByPoll.value[poll.id]
        if (myExisting != null && !poll.allowVoteChange) {
            return false to "Votre vote unique est déjà enregistré et définitif."
        }
        if (poll.visibility == PollVisibility.PUBLIC) {
            return true to null
        }
        val srv = poll.serverId?.let { sid -> chatRepo.servers.value.find { it.id == sid } }
        val isMember = uid in poll.memberIds || (srv != null && (srv.ownerId == uid || srv.members.any { it.id == uid }))
        if (!isMember) {
            return false to "Réservé aux membres du serveur."
        }
        if (poll.visibility == PollVisibility.ROLE_RESTRICTED) {
            val allowed = hasMatchingRole(srv, poll.allowedRoleIds, uid)
            if (!allowed) {
                val rolesLabel = poll.allowedRoleNames.ifEmpty { poll.allowedRoleIds }.joinToString(", ")
                return false to "Réservé aux rôles : $rolesLabel"
            }
        }
        return true to null
    }

    private fun hasMatchingRole(srv: Server?, allowedRoleIds: List<String>, uid: String): Boolean {
        if (allowedRoleIds.isEmpty()) return true
        if (srv == null) return false
        val role = CommunityPermissions.effectiveRole(srv, uid)
        if (role.name in allowedRoleIds) return true
        if (role == UserRole.ADMIN && "role_admin" in allowedRoleIds) return true
        if (role == UserRole.MODERATOR && "role_mod" in allowedRoleIds) return true
        if (role == UserRole.MEMBER && "role_member" in allowedRoleIds) return true
        val customId = srv.members.find { it.id == uid }?.customRoleId
        return customId != null && customId in allowedRoleIds
    }

    suspend fun toggleOptionVote(pollId: String, optionId: String): Result<AdvancedPoll> {
        val poll = _polls.value.find { it.id == pollId }
            ?: return Result.failure(IllegalArgumentException("Sondage introuvable."))
        val currentSelection = _myVotesByPoll.value[pollId]?.selectedOptionIds ?: emptyList()

        val nextSelection = if (poll.allowsMultiple) {
            if (optionId in currentSelection) {
                currentSelection - optionId
            } else {
                if (currentSelection.size >= poll.maxChoices) {
                    return Result.failure(IllegalArgumentException("Maximum ${poll.maxChoices} choix autorisés."))
                }
                currentSelection + optionId
            }
        } else {
            if (optionId in currentSelection) emptyList() else listOf(optionId)
        }

        return if (nextSelection.isEmpty()) {
            retractVote(pollId)
        } else {
            castVote(pollId, nextSelection)
        }
    }

    private suspend fun resolveServerMemberIds(serverId: String?, localBase: List<String>): List<String> {
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (serverId.isNullOrBlank() || db == null || authUser == null) return localBase.distinct()
        val cloudMemberIds = withContext(Dispatchers.IO) {
            try {
                val snap = Tasks.await(db.collection("servers").document(serverId).get())
                (snap.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        return (localBase + cloudMemberIds).distinct()
    }

    suspend fun castVote(pollId: String, selectedOptionIds: List<String>): Result<AdvancedPoll> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))
        var poll = _polls.value.find { it.id == pollId }
        if (poll == null) {
            val dbLookup = chatRepo.firestoreDb()
            val authUserLookup = chatRepo.authInstance()?.currentUser
            if (dbLookup != null && authUserLookup != null) {
                poll = withContext(Dispatchers.IO) {
                    try {
                        val snap = Tasks.await(dbLookup.collection("polls").document(pollId).get())
                        PollFirestoreSync.mapDocToPoll(snap)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
        if (poll == null) return Result.failure(IllegalArgumentException("Sondage introuvable."))

        val (eligible, reason) = canUserVote(poll, uid)
        if (!eligible) {
            return Result.failure(SecurityException(reason ?: "Vote non autorisé."))
        }

        val validOptionIds = poll.options.map { it.id }.toSet()
        val cleanSelection = selectedOptionIds.distinct().filter { it in validOptionIds }
        if (cleanSelection.isEmpty()) {
            return Result.failure(IllegalArgumentException("Sélectionnez au moins une option valide."))
        }
        if (!poll.allowsMultiple && cleanSelection.size > 1) {
            return Result.failure(IllegalArgumentException("Un seul choix est autorisé pour ce sondage."))
        }
        if (poll.allowsMultiple && cleanSelection.size > poll.maxChoices) {
            return Result.failure(IllegalArgumentException("Maximum ${poll.maxChoices} options autorisées."))
        }

        val now = nowProvider()
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser

        var finalPoll: AdvancedPoll
        var finalVote: PollVoteRecord

        if (db != null && authUser != null) {
            val txRes = withContext(Dispatchers.IO) {
                try {
                    val pollRef = db.collection("polls").document(pollId)
                    val voteRef = pollRef.collection("votes").document(authUser.uid)
                    val pair = Tasks.await(db.runTransaction { tx ->
                        val pSnap = tx.get(pollRef)
                        val parsedPoll = PollFirestoreSync.mapDocToPoll(pSnap)
                            ?: throw IllegalStateException("Sondage introuvable sur le serveur.")
                        if (parsedPoll.status != PollStatus.PUBLISHED) {
                            throw SecurityException("Ce sondage n'est plus actif.")
                        }
                        if (parsedPoll.expiresAtMs != null && now > parsedPoll.expiresAtMs) {
                            throw SecurityException("Ce sondage est expiré.")
                        }
                        val vSnap = tx.get(voteRef)
                        val oldVote = if (vSnap.exists()) PollFirestoreSync.mapDocToVote(vSnap) else null
                        if (oldVote != null && !parsedPoll.allowVoteChange) {
                            throw SecurityException("Une seule réponse définitive est autorisée.")
                        }

                        val oldSet = oldVote?.selectedOptionIds?.toSet() ?: emptySet()
                        val newSet = cleanSelection.toSet()

                        val nextCounts = linkedMapOf<String, Int>()
                        val nextOptions = parsedPoll.options.map { opt ->
                            val delta = (if (opt.id in newSet) 1 else 0) - (if (opt.id in oldSet) 1 else 0)
                            val updatedCount = (opt.voteCount + delta).coerceAtLeast(0)
                            nextCounts[opt.id] = updatedCount
                            opt.copy(voteCount = updatedCount)
                        }
                        val nextTotalVoters = (parsedPoll.totalVoters + if (oldVote == null) 1 else 0).coerceAtLeast(0)
                        val nextTotalVotes = (parsedPoll.totalVotes - oldSet.size + newSet.size).coerceAtLeast(0)

                        val votePayload = PollFirestoreSync.voteToMap(
                            pollId = pollId,
                            voterId = authUser.uid,
                            voterName = currentUserName(),
                            selectedOptionIds = cleanSelection,
                            isAnonymous = parsedPoll.isAnonymous,
                            votedAtMs = now
                        )
                        tx.set(voteRef, votePayload)
                        tx.update(
                            pollRef,
                            mapOf(
                                "optionCounts" to nextCounts,
                                "totalVotes" to nextTotalVotes,
                                "totalVoters" to nextTotalVoters,
                                "updatedAtMs" to now,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )

                        val updatedPoll = parsedPoll.copy(
                            options = nextOptions,
                            totalVotes = nextTotalVotes,
                            totalVoters = nextTotalVoters,
                            updatedAtMs = now
                        )
                        val updatedVote = PollVoteRecord(
                            pollId = pollId,
                            voterId = authUser.uid,
                            voterName = if (parsedPoll.isAnonymous) "Anonyme" else currentUserName(),
                            selectedOptionIds = cleanSelection,
                            votedAtMs = now
                        )
                        updatedPoll to updatedVote
                    })
                    Result.success(pair)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "polls/$pollId", authUser.uid)
                    Result.failure<Pair<AdvancedPoll, PollVoteRecord>>(IllegalStateException(msg, e))
                }
            }
            if (txRes.isFailure) return Result.failure(txRes.exceptionOrNull()!!)
            val (p, v) = txRes.getOrThrow()
            finalPoll = p
            finalVote = v
        } else {
            val oldVote = _myVotesByPoll.value[pollId]
            val oldSet = oldVote?.selectedOptionIds?.toSet() ?: emptySet()
            val newSet = cleanSelection.toSet()
            val nextOptions = poll.options.map { opt ->
                val delta = (if (opt.id in newSet) 1 else 0) - (if (opt.id in oldSet) 1 else 0)
                opt.copy(voteCount = (opt.voteCount + delta).coerceAtLeast(0))
            }
            finalPoll = poll.copy(
                options = nextOptions,
                totalVoters = (poll.totalVoters + if (oldVote == null) 1 else 0).coerceAtLeast(0),
                totalVotes = (poll.totalVotes - oldSet.size + newSet.size).coerceAtLeast(0),
                updatedAtMs = now
            )
            finalVote = PollVoteRecord(
                pollId = pollId,
                voterId = uid,
                voterName = if (poll.isAnonymous) "Anonyme" else currentUserName(),
                selectedOptionIds = cleanSelection,
                votedAtMs = now
            )
        }

        applyLocalVoteState(finalPoll, finalVote)
        chatRepo.syncLinkedPollCardFromAdvanced(finalPoll)
        chatRepo.advanceQuest("q_poll")
        return Result.success(finalPoll)
    }

    suspend fun retractVote(pollId: String): Result<AdvancedPoll> {
        val uid = currentUid()
        if (uid.isBlank()) return Result.failure(SecurityException("Authentification requise."))
        val poll = _polls.value.find { it.id == pollId }
            ?: return Result.failure(IllegalArgumentException("Sondage introuvable."))
        if (!poll.isEffectivelyOpen(nowProvider())) {
            return Result.failure(SecurityException("Ce sondage n'accepte plus de modifications."))
        }
        if (!poll.allowVoteChange) {
            return Result.failure(SecurityException("Le retrait de vote est désactivé pour ce sondage définitif."))
        }

        val now = nowProvider()
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser

        val updatedPoll: AdvancedPoll
        if (db != null && authUser != null) {
            val txRes = withContext(Dispatchers.IO) {
                try {
                    val pollRef = db.collection("polls").document(pollId)
                    val voteRef = pollRef.collection("votes").document(authUser.uid)
                    val resPoll = Tasks.await(db.runTransaction { tx ->
                        val pSnap = tx.get(pollRef)
                        val parsedPoll = PollFirestoreSync.mapDocToPoll(pSnap)
                            ?: throw IllegalStateException("Sondage introuvable.")
                        val vSnap = tx.get(voteRef)
                        if (!vSnap.exists()) return@runTransaction parsedPoll
                        val oldVote = PollFirestoreSync.mapDocToVote(vSnap) ?: return@runTransaction parsedPoll
                        val oldSet = oldVote.selectedOptionIds.toSet()

                        val nextCounts = linkedMapOf<String, Int>()
                        val nextOptions = parsedPoll.options.map { opt ->
                            val delta = if (opt.id in oldSet) -1 else 0
                            val updatedCount = (opt.voteCount + delta).coerceAtLeast(0)
                            nextCounts[opt.id] = updatedCount
                            opt.copy(voteCount = updatedCount)
                        }
                        val nextTotalVoters = (parsedPoll.totalVoters - 1).coerceAtLeast(0)
                        val nextTotalVotes = (parsedPoll.totalVotes - oldSet.size).coerceAtLeast(0)
                        tx.delete(voteRef)
                        tx.update(
                            pollRef,
                            mapOf(
                                "optionCounts" to nextCounts,
                                "totalVotes" to nextTotalVotes,
                                "totalVoters" to nextTotalVoters,
                                "updatedAtMs" to now,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        parsedPoll.copy(
                            options = nextOptions,
                            totalVotes = nextTotalVotes,
                            totalVoters = nextTotalVoters,
                            updatedAtMs = now
                        )
                    })
                    Result.success(resPoll)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "polls/$pollId/votes/${authUser.uid}", authUser.uid)
                    Result.failure<AdvancedPoll>(IllegalStateException(msg, e))
                }
            }
            if (txRes.isFailure) return Result.failure(txRes.exceptionOrNull()!!)
            updatedPoll = txRes.getOrThrow()
        } else {
            val oldVote = _myVotesByPoll.value[pollId] ?: return Result.success(poll)
            val oldSet = oldVote.selectedOptionIds.toSet()
            val nextOptions = poll.options.map { opt ->
                val delta = if (opt.id in oldSet) -1 else 0
                opt.copy(voteCount = (opt.voteCount + delta).coerceAtLeast(0))
            }
            updatedPoll = poll.copy(
                options = nextOptions,
                totalVoters = (poll.totalVoters - 1).coerceAtLeast(0),
                totalVotes = (poll.totalVotes - oldSet.size).coerceAtLeast(0),
                updatedAtMs = now
            )
        }

        _myVotesByPoll.value = _myVotesByPoll.value - pollId
        val existingList = _votesByPoll.value[pollId].orEmpty().filterNot { it.voterId == uid }
        if (!updatedPoll.isAnonymous) {
            _votesByPoll.value = _votesByPoll.value + (pollId to existingList)
        }
        upsertLocalPoll(updatedPoll)
        chatRepo.syncLinkedPollCardFromAdvanced(updatedPoll)
        return Result.success(updatedPoll)
    }

    fun canCloseOrManagePoll(poll: AdvancedPoll, uid: String = currentUid()): Boolean {
        if (poll.authorId == uid) return true
        val srv = poll.serverId?.let { sid -> chatRepo.servers.value.find { it.id == sid } } ?: return false
        return CommunityPermissions.isOwner(srv, uid) ||
            CommunityPermissions.effectiveRole(srv, uid) in setOf(UserRole.ADMIN, UserRole.MODERATOR)
    }

    suspend fun closePollManually(pollId: String): Result<AdvancedPoll> {
        val uid = currentUid()
        val poll = _polls.value.find { it.id == pollId }
            ?: return Result.failure(IllegalArgumentException("Sondage introuvable."))
        if (!canCloseOrManagePoll(poll, uid)) {
            return Result.failure(SecurityException("Seul l'auteur ou le staff peut clôturer ce sondage."))
        }
        if (poll.status == PollStatus.CLOSED) {
            return Result.success(poll)
        }

        val now = nowProvider()
        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        db.collection("polls").document(pollId).update(
                            mapOf(
                                "status" to PollStatus.CLOSED.name,
                                "closedAtMs" to now,
                                "updatedAtMs" to now,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.UPDATE, "polls/$pollId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        val closed = poll.copy(
            status = PollStatus.CLOSED,
            closedAtMs = now,
            updatedAtMs = now,
            closeNotified = true
        )
        upsertLocalPoll(closed)
        chatRepo.syncLinkedPollCardFromAdvanced(closed)

        if (closed.notifyOnClose) {
            val stats = computeStatistics(closed)
            chatRepo.notifyCommunityEvent(
                title = "Sondage clôturé : ${closed.question}",
                body = "${stats.winnerSummary} (${stats.uniqueVoters} participant${if (stats.uniqueVoters > 1) "s" else ""}).",
                sourceLabel = closed.serverName.ifBlank { "RAVEN Sondages" },
                type = NotificationType.SYSTEM
            )
        }
        return Result.success(closed)
    }

    suspend fun deletePoll(pollId: String): Result<Unit> {
        val uid = currentUid()
        val poll = _polls.value.find { it.id == pollId }
            ?: return Result.failure(IllegalArgumentException("Sondage introuvable."))
        val srv = poll.serverId?.let { sid -> chatRepo.servers.value.find { it.id == sid } }
        val isAdmin = srv != null && (CommunityPermissions.isOwner(srv, uid) || CommunityPermissions.effectiveRole(srv, uid) == UserRole.ADMIN)
        if (poll.authorId != uid && !isAdmin) {
            return Result.failure(SecurityException("Permission insuffisante pour supprimer ce sondage."))
        }

        val db = chatRepo.firestoreDb()
        val authUser = chatRepo.authInstance()?.currentUser
        if (db != null && authUser != null) {
            val cloudRes = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(db.collection("polls").document(pollId).delete())
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "polls/$pollId", authUser.uid)
                    Result.failure<Unit>(IllegalStateException(msg, e))
                }
            }
            if (cloudRes.isFailure) return Result.failure(cloudRes.exceptionOrNull()!!)
        }

        synchronized(cloudPollsById) {
            cloudPollsById.remove(pollId)
        }
        _polls.value = _polls.value.filterNot { it.id == pollId }
        _votesByPoll.value = _votesByPoll.value - pollId
        _myVotesByPoll.value = _myVotesByPoll.value - pollId
        return Result.success(Unit)
    }

    fun checkAndNotifyExpiredPolls() {
        val now = nowProvider()
        var changed = false
        val updatedList = _polls.value.map { poll ->
            if (poll.isExpired(now) && !poll.closeNotified) {
                changed = true
                if (poll.notifyOnClose) {
                    val stats = computeStatistics(poll)
                    chatRepo.notifyCommunityEvent(
                        title = "Sondage terminé : ${poll.question}",
                        body = "Résultat final : ${stats.winnerSummary}.",
                        sourceLabel = poll.serverName.ifBlank { "RAVEN Sondages" },
                        type = NotificationType.SYSTEM
                    )
                }
                poll.copy(closeNotified = true)
            } else {
                poll
            }
        }
        if (changed) {
            _polls.value = updatedList
        }
    }

    fun computeStatistics(poll: AdvancedPoll): PollStatistics {
        val now = nowProvider()
        val totalVotes = poll.options.sumOf { it.voteCount }.coerceAtLeast(poll.totalVotes)
        val maxVotes = poll.options.maxOfOrNull { it.voteCount } ?: 0
        val sortedCounts = poll.options.map { it.voteCount }.sortedDescending()
        val secondBest = sortedCounts.getOrNull(1) ?: 0
        val margin = if (maxVotes > 0) (maxVotes - secondBest).coerceAtLeast(0) else 0

        val winningIds = if (maxVotes > 0) {
            poll.options.filter { it.voteCount == maxVotes }.map { it.id }
        } else emptyList()

        val voteRecords = if (poll.isAnonymous) emptyList() else _votesByPoll.value[poll.id].orEmpty()

        val optionStats = poll.options.map { opt ->
            val pct = if (totalVotes > 0) (opt.voteCount * 100f / totalVotes) else 0f
            val votersForOpt = if (poll.isAnonymous) {
                emptyList()
            } else {
                voteRecords.filter { opt.id in it.selectedOptionIds }.map { it.voterName }.distinct()
            }
            PollOptionStat(
                optionId = opt.id,
                text = opt.text,
                votes = opt.voteCount,
                percentage = pct,
                isLeader = opt.id in winningIds && maxVotes > 0,
                voterNames = votersForOpt
            )
        }

        val srvMemberCount = poll.serverId?.let { sid ->
            chatRepo.servers.value.find { it.id == sid }?.members?.size
        } ?: poll.memberIds.size
        val eligibleCount = srvMemberCount.coerceAtLeast(poll.totalVoters).coerceAtLeast(1)
        val participationPct = if (poll.totalVoters > 0) {
            ((poll.totalVoters * 100f) / eligibleCount).coerceIn(0f, 100f)
        } else 0f

        val winners = poll.options.filter { it.id in winningIds }
        val winnerSummary = when {
            maxVotes == 0 -> "Aucun vote enregistré"
            winners.size == 1 -> "${winners.first().text} en tête (${winners.first().voteCount} vote${if (winners.first().voteCount > 1) "s" else ""})"
            else -> "Égalité : ${winners.joinToString(" / ") { it.text }} ($maxVotes votes chacun)"
        }

        val effStatus = poll.effectiveStatus(now)
        val statusLabel = when {
            effStatus == PollStatus.DRAFT -> "Brouillon"
            poll.isExpired(now) -> "Expiré"
            effStatus == PollStatus.CLOSED -> "Clôturé manuellement"
            else -> "Actif"
        }

        return PollStatistics(
            pollId = poll.id,
            totalVotes = totalVotes,
            uniqueVoters = poll.totalVoters,
            eligibleMemberCount = eligibleCount,
            participationRatePercent = participationPct,
            winningOptionIds = winningIds,
            winnerSummary = winnerSummary,
            leadMarginVotes = margin,
            optionStats = optionStats,
            isAnonymous = poll.isAnonymous,
            statusLabel = statusLabel
        )
    }

    fun exportPollResults(poll: AdvancedPoll, format: PollExportFormat): String {
        val stats = computeStatistics(poll)
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val expiryLabel = poll.expiresAtMs?.let { dateFmt.format(Date(it)) } ?: "Sans limite"

        return when (format) {
            PollExportFormat.SUMMARY_TEXT -> buildString {
                appendLine("=== RÉSULTATS SONDAGE RAVEN ===")
                appendLine("Question : ${poll.question}")
                if (poll.description.isNotBlank()) appendLine("Contexte : ${poll.description}")
                appendLine("Auteur : ${poll.authorName}")
                appendLine("Statut : ${stats.statusLabel} | Expiration : $expiryLabel")
                appendLine("Mode : ${if (poll.allowsMultiple) "Choix multiples (max ${poll.maxChoices})" else "Choix unique"} | Anonyme : ${if (poll.isAnonymous) "Oui" else "Non"}")
                appendLine("Participation : ${stats.uniqueVoters} votant(s) • ${stats.totalVotes} vote(s) (${"%.1f".format(stats.participationRatePercent)}%)")
                appendLine()
                stats.optionStats.forEachIndexed { idx, opt ->
                    val crown = if (opt.isLeader) " ★" else ""
                    appendLine("${idx + 1}. ${opt.text} — ${opt.votes} vote(s) (${"%.1f".format(opt.percentage)}%)$crown")
                    if (!poll.isAnonymous && opt.voterNames.isNotEmpty()) {
                        appendLine("   Votants : ${opt.voterNames.joinToString(", ")}")
                    }
                }
                appendLine()
                append("Synthèse : ${stats.winnerSummary}")
            }

            PollExportFormat.CSV -> buildString {
                fun csvCell(raw: String): String = "\"${raw.replace("\"", "\"\"")}\""
                appendLine("PollId,Question,Statut,Anonyme,OptionId,Option,Votes,Pourcentage,EnTete,Votants")
                stats.optionStats.forEach { opt ->
                    val votersCol = if (poll.isAnonymous) "CONFIDENTIEL" else opt.voterNames.joinToString(" | ")
                    appendLine(
                        listOf(
                            csvCell(poll.id),
                            csvCell(poll.question),
                            csvCell(stats.statusLabel),
                            csvCell(if (poll.isAnonymous) "true" else "false"),
                            csvCell(opt.optionId),
                            csvCell(opt.text),
                            opt.votes.toString(),
                            "%.1f".format(Locale.US, opt.percentage),
                            opt.isLeader.toString(),
                            csvCell(votersCol)
                        ).joinToString(",")
                    )
                }
            }

            PollExportFormat.JSON -> {
                val root = JSONObject().apply {
                    put("pollId", poll.id)
                    put("question", poll.question)
                    put("description", poll.description)
                    put("authorName", poll.authorName)
                    put("status", stats.statusLabel)
                    put("visibility", poll.visibility.name)
                    put("allowsMultiple", poll.allowsMultiple)
                    put("maxChoices", poll.maxChoices)
                    put("isAnonymous", poll.isAnonymous)
                    put("expiresAt", expiryLabel)
                    put("totalVotes", stats.totalVotes)
                    put("uniqueVoters", stats.uniqueVoters)
                    put("participationRatePercent", stats.participationRatePercent.toDouble())
                    put("winnerSummary", stats.winnerSummary)
                    val arr = JSONArray()
                    stats.optionStats.forEach { opt ->
                        arr.put(JSONObject().apply {
                            put("optionId", opt.optionId)
                            put("text", opt.text)
                            put("votes", opt.votes)
                            put("percentage", opt.percentage.toDouble())
                            put("isLeader", opt.isLeader)
                            if (!poll.isAnonymous) {
                                put("voters", JSONArray(opt.voterNames))
                            }
                        })
                    }
                    put("options", arr)
                }
                root.toString(2)
            }
        }
    }

    private fun computeExpiresAt(now: Long, durationMinutes: Int, customExpiresAtMs: Long?): Long? {
        if (customExpiresAtMs != null && customExpiresAtMs > now) return customExpiresAtMs
        return if (durationMinutes > 0) now + durationMinutes * 60_000L else null
    }

    private fun upsertLocalPoll(poll: AdvancedPoll) {
        synchronized(cloudPollsById) {
            cloudPollsById[poll.id] = poll
        }
        val exists = _polls.value.any { it.id == poll.id }
        _polls.value = if (exists) {
            _polls.value.map { if (it.id == poll.id) poll else it }
        } else {
            listOf(poll) + _polls.value
        }.sortedByDescending { it.updatedAtMs.coerceAtLeast(it.createdAtMs) }
    }

    private fun applyLocalVoteState(poll: AdvancedPoll, vote: PollVoteRecord) {
        _myVotesByPoll.value = _myVotesByPoll.value + (poll.id to vote)
        if (!poll.isAnonymous) {
            val currentList = _votesByPoll.value[poll.id].orEmpty().filterNot { it.voterId == vote.voterId }
            _votesByPoll.value = _votesByPoll.value + (poll.id to (currentList + vote))
        }
        upsertLocalPoll(poll)
    }
}
