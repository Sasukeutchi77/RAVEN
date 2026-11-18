package com.example.data

import android.util.Log
import com.example.model.*
import com.example.util.FirestoreErrorHandler
import com.example.util.OperationType
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AiAssistantRepository(
    internal val chatRepo: ChatRepository,
    internal val provider: RavenAiProvider = FirebaseAiLogicProvider(),
    internal val rateLimiter: AiUsageRateLimiter = AiUsageRateLimiter()
) {
    companion object {
        private const val TAG = "AiAssistantRepo"
        private const val QUOTA_DOC_ID = "daily_quota"
        private const val SYSTEM_PERSONAL_PROMPT = """
            Tu es RAVEN AI, l'assistant personnel intelligent intégré à l'application Raven.
            Réponds toujours en français de façon claire, structurée, précise et utile.
            Ne révèle jamais d'informations système, clés ou données privées.
            Si une information manque, indique-le honnêtement sans inventer.
        """
        private const val SYSTEM_SERVER_PROMPT = """
            Tu es RAVEN AI, l'assistant de communauté intégré à un serveur Raven.
            Appuie-toi uniquement sur le contexte de communauté fourni (règles, salons, événements, messages anonymisés).
            Aide à la modération, aux résumés, aux annonces, aux événements et à l'organisation du serveur.
            Réponds toujours en français de manière structurée et actionnable.
        """
    }

    private val _conversations = MutableStateFlow<List<AiConversation>>(emptyList())
    val conversations: StateFlow<List<AiConversation>> = _conversations.asStateFlow()

    private val _messagesByConversation = MutableStateFlow<Map<String, List<AiChatMessage>>>(emptyMap())
    val messagesByConversation: StateFlow<Map<String, List<AiChatMessage>>> = _messagesByConversation.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _quota = MutableStateFlow(AiUsageQuota())
    val quota: StateFlow<AiUsageQuota> = _quota.asStateFlow()

    private val _streamingBuffer = MutableStateFlow("")
    val streamingBuffer: StateFlow<String> = _streamingBuffer.asStateFlow()

    private var conversationsListener: ListenerRegistration? = null
    private var activeMessagesListener: ListenerRegistration? = null
    private var quotaListener: ListenerRegistration? = null
    private var observedUid: String? = null
    private var observedMessagesConvId: String? = null

    private fun currentUid(): String {
        val firebaseAuth = chatRepo.authInstance()
        val firebaseUser = firebaseAuth?.currentUser
        if (firebaseAuth != null && firebaseUser == null && chatRepo.firestoreDb() != null) {
            throw IllegalStateException("Connexion Google requise pour utiliser RAVEN AI.")
        }
        return firebaseUser?.uid ?: chatRepo.currentUser.value.id.ifBlank { "user_me" }
    }

    private fun nowTimeLabel(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    fun attachSlashCommandHook() {
        chatRepo.aiCommandHandler = { targetId, rawInput ->
            handleSlashCommand(targetId, rawInput)
        }
    }

    fun startObserving() {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid
        if (uid.isNullOrBlank()) {
            releaseListeners()
            return
        }
        if (observedUid == uid && conversationsListener != null) return
        releaseListeners()
        observedUid = uid

        val convCol = db.collection("users").document(uid).collection("ai_conversations")
        conversationsListener = convCol
            .orderBy("updatedAtMs", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (chatRepo.authInstance()?.currentUser?.uid == uid) {
                        FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, convCol.path, uid)
                    }
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { AiFirestoreSync.mapDocToConversation(it) }
                _conversations.value = list
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    selectConversation(list.first().id)
                }
            }

        val quotaDoc = db.collection("users").document(uid).collection("ai_usage").document(QUOTA_DOC_ID)
        quotaListener = quotaDoc.addSnapshotListener { snap, err ->
            if (err != null) {
                if (chatRepo.authInstance()?.currentUser?.uid == uid) {
                    FirestoreErrorHandler.handleFirestoreError(err, OperationType.GET, quotaDoc.path, uid)
                }
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                val mapped = AiFirestoreSync.mapDocToQuota(snap, uid)
                _quota.value = rateLimiter.normalizeQuotaForToday(mapped, uid)
            } else {
                _quota.value = rateLimiter.normalizeQuotaForToday(_quota.value, uid)
            }
        }
    }

    fun releaseListeners() {
        conversationsListener?.remove()
        conversationsListener = null
        activeMessagesListener?.remove()
        activeMessagesListener = null
        quotaListener?.remove()
        quotaListener = null
        observedUid = null
        observedMessagesConvId = null
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        if (observedMessagesConvId == conversationId && activeMessagesListener != null) return

        activeMessagesListener?.remove()
        observedMessagesConvId = conversationId
        val msgCol = db.collection("users").document(uid)
            .collection("ai_conversations").document(conversationId)
            .collection("messages")
        activeMessagesListener = msgCol
            .orderBy("createdAtMs", Query.Direction.ASCENDING)
            .limit(120)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (chatRepo.authInstance()?.currentUser?.uid == uid) {
                        FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, msgCol.path, uid)
                    }
                    return@addSnapshotListener
                }
                val msgs = snap?.documents.orEmpty().mapNotNull { AiFirestoreSync.mapDocToMessage(it) }
                _messagesByConversation.value = _messagesByConversation.value + (conversationId to msgs)
            }
    }

    suspend fun startNewConversation(
        mode: AiAssistantMode,
        server: Server? = null,
        channel: Channel? = null,
        customTitle: String? = null
    ): Result<AiConversation> {
        return try {
            val uid = currentUid()
            if (mode == AiAssistantMode.SERVER && server != null) {
                verifyServerAccess(server, uid).getOrThrow()
            }
            val now = System.currentTimeMillis()
            val convId = "ai_conv_${UUID.randomUUID().toString().take(10)}"
            val title = customTitle?.trim()?.take(120)?.ifBlank { null }
                ?: if (mode == AiAssistantMode.SERVER && server != null) {
                    "Assistant • ${server.name}".take(120)
                } else {
                    "Discussion RAVEN AI"
                }

            val conv = AiConversation(
                id = convId,
                userId = uid,
                title = title,
                mode = mode,
                serverId = server?.id?.takeIf { mode == AiAssistantMode.SERVER && !server.isDmTab },
                serverName = server?.name?.takeIf { mode == AiAssistantMode.SERVER && !server.isDmTab },
                channelId = channel?.id?.takeIf { mode == AiAssistantMode.SERVER },
                channelName = channel?.name?.takeIf { mode == AiAssistantMode.SERVER },
                modelName = AiProviderRequest.DEFAULT_MODEL_NAME,
                lastMessagePreview = "",
                messageCount = 0,
                createdAtMs = now,
                updatedAtMs = now
            )

            persistConversation(conv, isCreate = true)
            _conversations.value = listOf(conv) + _conversations.value.filterNot { it.id == conv.id }
            selectConversation(conv.id)
            Result.success(conv)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "users/ai_conversations")
            Result.failure(e)
        }
    }

    private fun verifyServerAccess(server: Server, uid: String): Result<Unit> {
        if (server.isDmTab) return Result.success(Unit)
        val isMember = server.ownerId == uid ||
            server.adminIds.contains(uid) ||
            server.moderatorIds.contains(uid) ||
            server.members.any { it.id == uid }
        val isAllowed = server.isPublic || isMember
        return if (isAllowed) {
            Result.success(Unit)
        } else {
            Result.failure(SecurityException("Accès refusé : vous n'êtes pas membre de cette communauté."))
        }
    }

    private fun verifyStaffModerationPermission(server: Server?, uid: String): Result<Unit> {
        if (server == null || server.isDmTab) return Result.success(Unit)
        val allowed = CommunityPermissions.hasPermission(server, uid, RolePermission.MODERATE_MEMBERS) ||
            CommunityPermissions.hasPermission(server, uid, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.isOwner(server, uid)
        return if (allowed) {
            Result.success(Unit)
        } else {
            Result.failure(
                SecurityException("Permission insuffisante : rôle Modérateur ou Admin requis sur ${server.name}.")
            )
        }
    }

    private suspend fun enforceQuotaAndPrepare(uid: String, prompt: String): Result<String> {
        val sanitized = AiPrivacySanitizer.sanitizePrompt(prompt).getOrElse {
            return Result.failure(it)
        }
        val currentQuota = rateLimiter.normalizeQuotaForToday(_quota.value, uid)
        when (val verdict = rateLimiter.checkBeforeCall(uid, sanitized, currentQuota)) {
            is QuotaCheckResult.Denied -> {
                if (verdict.isAbuseStrike) {
                    val struck = rateLimiter.recordAbuseStrike(uid, currentQuota)
                    _quota.value = struck
                    persistQuota(struck)
                }
                return Result.failure(IllegalStateException(verdict.reason))
            }
            QuotaCheckResult.Allowed -> {
                val updated = rateLimiter.recordAcceptedCall(uid, sanitized, currentQuota)
                _quota.value = updated
                persistQuota(updated)
                return Result.success(sanitized)
            }
        }
    }

    suspend fun sendAiChatMessage(
        rawPrompt: String,
        mode: AiAssistantMode,
        server: Server? = null,
        channel: Channel? = null,
        recentMessages: List<Message> = emptyList(),
        taskCategory: AiTaskCategory = AiTaskCategory.CHAT
    ): Result<AiChatMessage> {
        return try {
            val uid = currentUid()
            if (mode == AiAssistantMode.SERVER && server != null) {
                verifyServerAccess(server, uid).getOrThrow()
            }
            val cleanPrompt = enforceQuotaAndPrepare(uid, rawPrompt).getOrThrow()

            val activeConv = _activeConversationId.value
                ?.let { id -> _conversations.value.find { it.id == id && it.userId == uid } }
                ?: startNewConversation(mode, server, channel, cleanPrompt.take(48)).getOrThrow()

            val userMsg = AiChatMessage(
                id = "aim_${UUID.randomUUID().toString().take(10)}",
                conversationId = activeConv.id,
                userId = uid,
                role = AiMessageRole.USER,
                content = cleanPrompt,
                taskCategory = taskCategory,
                timestampLabel = nowTimeLabel(),
                createdAtMs = System.currentTimeMillis()
            )
            appendMessageLocally(activeConv, userMsg, isFirstUserPrompt = activeConv.messageCount == 0)
            persistMessage(userMsg)

            val historyPairs = _messagesByConversation.value[activeConv.id].orEmpty()
                .filter { it.id != userMsg.id }
                .takeLast(8)
                .map { (if (it.role == AiMessageRole.USER) "user" else "model") to it.content }

            val sysInstruction = if (mode == AiAssistantMode.SERVER && server != null && !server.isDmTab) {
                val safeCtx = AiPrivacySanitizer.buildSafeServerContext(server, channel, recentMessages)
                "$SYSTEM_SERVER_PROMPT\n\nContexte du serveur:\n$safeCtx"
            } else {
                SYSTEM_PERSONAL_PROMPT.trimIndent()
            }

            _streamingBuffer.value = ""
            val acc = StringBuilder()
            provider.generateTextStream(
                AiProviderRequest(
                    systemInstruction = sysInstruction,
                    conversationHistory = historyPairs,
                    prompt = cleanPrompt
                )
            ).collect { chunk ->
                acc.append(chunk)
                _streamingBuffer.value = acc.toString()
            }
            val finalReply = acc.toString().trim()
            _streamingBuffer.value = ""

            val assistantMsg = AiChatMessage(
                id = "aim_${UUID.randomUUID().toString().take(10)}",
                conversationId = activeConv.id,
                userId = uid,
                role = AiMessageRole.ASSISTANT,
                content = finalReply,
                taskCategory = taskCategory,
                timestampLabel = nowTimeLabel(),
                createdAtMs = System.currentTimeMillis()
            )
            val refreshedConv = _conversations.value.find { it.id == activeConv.id } ?: activeConv
            appendMessageLocally(refreshedConv, assistantMsg, isFirstUserPrompt = false)
            persistMessage(assistantMsg)
            Result.success(assistantMsg)
        } catch (e: CancellationException) {
            _streamingBuffer.value = ""
            throw e
        } catch (e: Exception) {
            _streamingBuffer.value = ""
            Log.e(TAG, "sendAiChatMessage failed", e)
            Result.failure(e)
        }
    }

    suspend fun summarizeMessages(
        server: Server?,
        channel: Channel?,
        messagesToSummarize: List<Message>,
        isLongDiscussion: Boolean,
        customFocus: String = ""
    ): Result<AiChatMessage> {
        return try {
            val uid = currentUid()
            if (server != null && !server.isDmTab) {
                verifyServerAccess(server, uid).getOrThrow()
            }
            if (messagesToSummarize.isEmpty()) {
                return Result.failure(IllegalArgumentException("Aucun message à résumer dans ce salon."))
            }

            val windowSize = if (isLongDiscussion) 80 else 25
            val transcript = messagesToSummarize.takeLast(windowSize).joinToString("\n") { m ->
                val cleanText = AiPrivacySanitizer.redactSensitiveData(
                    m.text.ifBlank { "[Média ou pièce jointe]" }.take(350)
                )
                "- ${m.sender.name} (${m.timestamp}): $cleanText"
            }
            val focusNote = if (customFocus.isNotBlank()) "\nPriorité demandée : $customFocus" else ""
            val prompt = if (isLongDiscussion) {
                """
                Fais un résumé structuré de cette longue discussion (#${channel?.name ?: "salon"}) en incluant :
                1) Synthèse générale
                2) Points clés & décisions
                3) Actions à suivre & questions ouvertes$focusNote
                
                Messages (${messagesToSummarize.takeLast(windowSize).size}) :
                $transcript
                """.trimIndent()
            } else {
                """
                Résume de manière claire et concise cette conversation (#${channel?.name ?: "salon"}) en 4 à 6 puces max :$focusNote
                
                $transcript
                """.trimIndent()
            }

            sendAiChatMessage(
                rawPrompt = prompt,
                mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
                server = server,
                channel = channel,
                recentMessages = messagesToSummarize.takeLast(15),
                taskCategory = if (isLongDiscussion) AiTaskCategory.SUMMARIZE_LONG_THREAD else AiTaskCategory.SUMMARIZE_CHAT
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateTextContent(
        topic: String,
        tone: String,
        server: Server? = null
    ): Result<AiChatMessage> {
        val cleanTopic = topic.trim()
        if (cleanTopic.length < 3) {
            return Result.failure(IllegalArgumentException("Décrivez le sujet à rédiger (min. 3 caractères)."))
        }
        val prompt = "Rédige un texte de haute qualité en français (ton : $tone) sur le sujet suivant : $cleanTopic"
        return sendAiChatMessage(
            rawPrompt = prompt,
            mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
            server = server,
            taskCategory = AiTaskCategory.GENERATE_TEXT
        )
    }

    suspend fun analyzeModerationAndSpam(
        contentToInspect: String,
        focusCategory: AiTaskCategory,
        server: Server? = null,
        channel: Channel? = null,
        recentMessages: List<Message> = emptyList()
    ): Result<AiModerationReport> {
        return try {
            val uid = currentUid()
            verifyStaffModerationPermission(server, uid).getOrThrow()

            val targetText = if (contentToInspect.isNotBlank()) {
                contentToInspect
            } else {
                recentMessages.takeLast(20).joinToString("\n") { m ->
                    "${m.sender.name}: ${AiPrivacySanitizer.redactSensitiveData(m.text.take(280))}"
                }
            }
            if (targetText.isBlank()) {
                return Result.failure(IllegalArgumentException("Fournissez un texte ou des messages de salon à analyser."))
            }

            val cleanInput = enforceQuotaAndPrepare(uid, targetText).getOrThrow()
            val focusLabel = when (focusCategory) {
                AiTaskCategory.SPAM_DETECTION -> "Détection prioritaire de spam, flood, publicités non sollicitées et liens répétitifs."
                AiTaskCategory.SUSPICIOUS_CONTENT -> "Détection prioritaire de phishing, arnaques, ingénierie sociale, liens suspects ou usurpation."
                else -> "Audit complet de modération (toxicité, respect des règles, spam et contenu suspect)."
            }

            val rulesSnippet = server?.rulesGuidelines?.take(400).orEmpty()
            val rawJson = provider.generateStructuredJson(
                request = AiProviderRequest(
                    systemInstruction = "Tu es un système expert de sûreté et modération pour communautés Raven. $focusLabel",
                    prompt = "Règles serveur: $rulesSnippet\n\nContenu à analyser:\n$cleanInput"
                ),
                schemaSpec = AiStructuredSchemaSpec.MODERATION_REPORT
            )
            val report = parseModerationReportJson(rawJson)
            recordToolExchangeInHistory(
                uid = uid,
                mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
                server = server,
                channel = channel,
                userLabel = "[${focusCategory.label}] Analyse effectuée",
                assistantBody = "Risque ${report.riskLevel} (${report.riskScore}/100) • Spam: ${if (report.isSpam) "Oui" else "Non"} • Suspect: ${if (report.isSuspicious) "Oui" else "Non"}\nAction recommandée : ${report.recommendedAction}\n${report.explanation}",
                category = focusCategory
            )
            Result.success(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "analyzeModerationAndSpam failed", e)
            Result.failure(e)
        }
    }

    suspend fun draftAnnouncement(
        idea: String,
        server: Server? = null
    ): Result<AiAnnouncementDraft> {
        return try {
            val uid = currentUid()
            if (server != null && !server.isDmTab) verifyServerAccess(server, uid).getOrThrow()
            val cleanIdea = enforceQuotaAndPrepare(uid, idea).getOrThrow()
            val ctx = server?.let { "Communauté: ${it.name} (${it.categoryTag}) — ${it.description}" } ?: ""
            val rawJson = provider.generateStructuredJson(
                request = AiProviderRequest(
                    systemInstruction = "Tu es un rédacteur communautaire expert sur Raven. Génère une annonce percutante et bien structurée en français.",
                    prompt = "$ctx\nIdée d'annonce : $cleanIdea"
                ),
                schemaSpec = AiStructuredSchemaSpec.ANNOUNCEMENT_DRAFT
            )
            val draft = parseAnnouncementDraftJson(rawJson)
            recordToolExchangeInHistory(
                uid = uid,
                mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
                server = server,
                channel = null,
                userLabel = "[Aide Annonce] $cleanIdea",
                assistantBody = "Titre : ${draft.title}\nTon : ${draft.toneSummary}\n\n${draft.content}",
                category = AiTaskCategory.ANNOUNCEMENT_DRAFT
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun draftEvent(
        idea: String,
        server: Server? = null
    ): Result<AiEventDraft> {
        return try {
            val uid = currentUid()
            if (server != null && !server.isDmTab) verifyServerAccess(server, uid).getOrThrow()
            val cleanIdea = enforceQuotaAndPrepare(uid, idea).getOrThrow()
            val ctx = server?.let { "Communauté: ${it.name} (${it.categoryTag})" } ?: ""
            val rawJson = provider.generateStructuredJson(
                request = AiProviderRequest(
                    systemInstruction = "Tu es un organisateur d'événements communautaires sur Raven. Crée un événement attractif en français.",
                    prompt = "$ctx\nIdée d'événement : $cleanIdea"
                ),
                schemaSpec = AiStructuredSchemaSpec.EVENT_DRAFT
            )
            val draft = parseEventDraftJson(rawJson)
            val checklistTxt = if (draft.checklist.isNotEmpty()) "\nChecklist:\n- " + draft.checklist.joinToString("\n- ") else ""
            recordToolExchangeInHistory(
                uid = uid,
                mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
                server = server,
                channel = null,
                userLabel = "[Aide Événement] $cleanIdea",
                assistantBody = "${draft.title} (${draft.suggestedDateTimeLabel} • ${draft.suggestedLocationLabel})\n${draft.description}$checklistTxt",
                category = AiTaskCategory.EVENT_DRAFT
            )
            Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adviseServerManagement(
        question: String,
        server: Server
    ): Result<AiChatMessage> {
        return try {
            val uid = currentUid()
            val canManage = CommunityPermissions.hasPermission(server, uid, RolePermission.MANAGE_COMMUNITY) ||
                CommunityPermissions.hasPermission(server, uid, RolePermission.MANAGE_CHANNELS) ||
                CommunityPermissions.isOwner(server, uid)
            if (!canManage) {
                return Result.failure(
                    SecurityException("Accès réservé aux administrateurs/gestionnaires de ${server.name}.")
                )
            }
            val cleanQuestion = question.ifBlank {
                "Analyse la structure actuelle de ma communauté (salons, catégories, rôles, accueil, règles) et propose des améliorations concrètes."
            }
            sendAiChatMessage(
                rawPrompt = cleanQuestion,
                mode = AiAssistantMode.SERVER,
                server = server,
                taskCategory = AiTaskCategory.SERVER_MANAGEMENT_HELP
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            val uid = currentUid()
            val db = chatRepo.firestoreDb()
            if (db != null && chatRepo.authInstance()?.currentUser != null) {
                withContext(Dispatchers.IO) {
                    val convRef = db.collection("users").document(uid)
                        .collection("ai_conversations").document(conversationId)
                    val msgsSnap = Tasks.await(convRef.collection("messages").get())
                    for (doc in msgsSnap.documents) {
                        Tasks.await(doc.reference.delete())
                    }
                    Tasks.await(convRef.delete())
                }
            }
            _conversations.value = _conversations.value.filterNot { it.id == conversationId }
            _messagesByConversation.value = _messagesByConversation.value - conversationId
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = _conversations.value.firstOrNull()?.id
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, "users/ai_conversations/$conversationId")
            Result.failure(e)
        }
    }

    suspend fun clearAllHistory(): Result<Unit> {
        return try {
            val convIds = _conversations.value.map { it.id }
            for (cid in convIds) {
                deleteConversation(cid).getOrThrow()
            }
            _conversations.value = emptyList()
            _messagesByConversation.value = emptyMap()
            _activeConversationId.value = null
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun handleSlashCommand(targetId: String, rawInput: String): Boolean {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("/")) return false
        val parts = trimmed.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        val isAiCmd = AiSlashCommandCatalog.commands.any { it.command.equals(cmd, ignoreCase = true) }
        if (!isAiCmd) return false

        val server = chatRepo.servers.value.find { srv ->
            srv.channels.any { it.id == targetId }
        }
        val channel = server?.channels?.find { it.id == targetId }
        val channelMsgs = chatRepo.messages.value[targetId].orEmpty()

        chatRepo.postLocalAssistantFeedback(
            targetId = targetId,
            text = "RAVEN AI traite `$cmd` en toute sécurité via Firebase AI Logic..."
        )

        chatRepo.repoScope.launch {
            val output = when (cmd) {
                "/ai" -> {
                    val q = arg.ifBlank { "Donne-moi un résumé rapide de comment bien utiliser Raven." }
                    sendAiChatMessage(
                        rawPrompt = q,
                        mode = if (server != null && !server.isDmTab) AiAssistantMode.SERVER else AiAssistantMode.PERSONAL,
                        server = server,
                        channel = channel,
                        recentMessages = channelMsgs,
                        taskCategory = AiTaskCategory.SLASH_COMMAND
                    ).map { it.content }
                }
                "/resumer", "/longresume" -> {
                    summarizeMessages(
                        server = server,
                        channel = channel,
                        messagesToSummarize = channelMsgs,
                        isLongDiscussion = (cmd == "/longresume"),
                        customFocus = arg
                    ).map { it.content }
                }
                "/moderer", "/spam", "/suspect" -> {
                    val cat = when (cmd) {
                        "/spam" -> AiTaskCategory.SPAM_DETECTION
                        "/suspect" -> AiTaskCategory.SUSPICIOUS_CONTENT
                        else -> AiTaskCategory.MODERATION_HELP
                    }
                    analyzeModerationAndSpam(
                        contentToInspect = arg,
                        focusCategory = cat,
                        server = server,
                        channel = channel,
                        recentMessages = channelMsgs
                    ).map { rep ->
                        "Analyse ${cat.label} • Risque ${rep.riskLevel} (${rep.riskScore}/100)\nAction : ${rep.recommendedAction}\n${rep.explanation}"
                    }
                }
                "/annonceai" -> {
                    draftAnnouncement(arg.ifBlank { "Nouvelle actualité de la communauté" }, server)
                        .map { "Annonce suggérée — ${it.title}\n\n${it.content}" }
                }
                "/eventai" -> {
                    draftEvent(arg.ifBlank { "Rencontre communautaire" }, server)
                        .map { "Événement suggéré — ${it.title}\nCréneau : ${it.suggestedDateTimeLabel} • ${it.suggestedLocationLabel}\n${it.description}" }
                }
                "/gererserveur" -> {
                    if (server != null && !server.isDmTab) {
                        adviseServerManagement(arg, server).map { it.content }
                    } else {
                        Result.failure(IllegalArgumentException("Utilisez /gererserveur dans un salon de communauté."))
                    }
                }
                else -> Result.failure(IllegalArgumentException("Commande IA inconnue."))
            }

            output.onSuccess { text ->
                chatRepo.postLocalAssistantFeedback(targetId, text)
            }.onFailure { err ->
                chatRepo.postLocalAssistantFeedback(
                    targetId,
                    "Erreur RAVEN AI : ${err.localizedMessage ?: "Traitement impossible."}"
                )
            }
        }
        return true
    }

    private suspend fun recordToolExchangeInHistory(
        uid: String,
        mode: AiAssistantMode,
        server: Server?,
        channel: Channel?,
        userLabel: String,
        assistantBody: String,
        category: AiTaskCategory
    ) {
        val conv = _activeConversationId.value
            ?.let { id -> _conversations.value.find { it.id == id && it.userId == uid } }
            ?: startNewConversation(mode, server, channel, userLabel.take(48)).getOrNull()
            ?: return

        val now = System.currentTimeMillis()
        val uMsg = AiChatMessage(
            id = "aim_${UUID.randomUUID().toString().take(10)}",
            conversationId = conv.id,
            userId = uid,
            role = AiMessageRole.USER,
            content = userLabel,
            taskCategory = category,
            timestampLabel = nowTimeLabel(),
            createdAtMs = now
        )
        val aMsg = AiChatMessage(
            id = "aim_${UUID.randomUUID().toString().take(10)}",
            conversationId = conv.id,
            userId = uid,
            role = AiMessageRole.ASSISTANT,
            content = assistantBody,
            taskCategory = category,
            timestampLabel = nowTimeLabel(),
            createdAtMs = now + 1L
        )
        appendMessageLocally(conv, uMsg, isFirstUserPrompt = conv.messageCount == 0)
        persistMessage(uMsg)
        val updatedConv = _conversations.value.find { it.id == conv.id } ?: conv
        appendMessageLocally(updatedConv, aMsg, isFirstUserPrompt = false)
        persistMessage(aMsg)
    }

    private suspend fun appendMessageLocally(
        conv: AiConversation,
        message: AiChatMessage,
        isFirstUserPrompt: Boolean
    ) {
        val prevMsgs = _messagesByConversation.value[conv.id].orEmpty()
        _messagesByConversation.value = _messagesByConversation.value + (conv.id to (prevMsgs + message))

        val updatedTitle = if (isFirstUserPrompt && conv.title == "Discussion RAVEN AI") {
            message.content.take(48).trim().ifBlank { conv.title }
        } else {
            conv.title
        }
        val updatedConv = conv.copy(
            title = updatedTitle,
            lastMessagePreview = message.content.take(120),
            messageCount = prevMsgs.size + 1,
            updatedAtMs = message.createdAtMs
        )
        _conversations.value = listOf(updatedConv) + _conversations.value.filterNot { it.id == updatedConv.id }
        persistConversation(updatedConv, isCreate = false)
    }

    private suspend fun persistConversation(conv: AiConversation, isCreate: Boolean) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("users").document(uid)
                    .collection("ai_conversations").document(conv.id)
                if (isCreate) {
                    Tasks.await(ref.set(AiFirestoreSync.conversationToMap(conv)))
                } else {
                    Tasks.await(ref.set(AiFirestoreSync.conversationToMap(conv), SetOptions.merge()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "users/$uid/ai_conversations/${conv.id}", uid)
            }
        }
    }

    private suspend fun persistMessage(msg: AiChatMessage) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("users").document(uid)
                    .collection("ai_conversations").document(msg.conversationId)
                    .collection("messages").document(msg.id)
                Tasks.await(ref.set(AiFirestoreSync.messageToMap(msg)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, "users/$uid/ai_conversations/${msg.conversationId}/messages/${msg.id}", uid)
            }
        }
    }

    private suspend fun persistQuota(quota: AiUsageQuota) {
        val db = chatRepo.firestoreDb() ?: return
        val uid = chatRepo.authInstance()?.currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            try {
                val ref = db.collection("users").document(uid)
                    .collection("ai_usage").document(QUOTA_DOC_ID)
                Tasks.await(ref.set(AiFirestoreSync.quotaToMap(quota), SetOptions.merge()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "users/$uid/ai_usage/$QUOTA_DOC_ID", uid)
            }
        }
    }

    internal fun parseModerationReportJson(jsonStr: String): AiModerationReport {
        val obj = JSONObject(jsonStr)
        val catsArr = obj.optJSONArray("categories")
        val categories = if (catsArr != null) {
            (0 until catsArr.length()).mapNotNull { idx -> catsArr.optString(idx).takeIf { it.isNotBlank() } }
        } else emptyList()
        val sigArr = obj.optJSONArray("signals")
        val signals = if (sigArr != null) {
            (0 until sigArr.length()).mapNotNull { idx -> sigArr.optString(idx).takeIf { it.isNotBlank() } }
        } else emptyList()
        return AiModerationReport(
            isSpam = obj.optBoolean("isSpam", false),
            isSuspicious = obj.optBoolean("isSuspicious", false),
            riskScore = obj.optInt("riskScore", 0).coerceIn(0, 100),
            riskLevel = obj.optString("riskLevel", "LOW"),
            categories = categories,
            signals = signals,
            recommendedAction = obj.optString("recommendedAction", "Aucune action requise"),
            explanation = obj.optString("explanation", "")
        )
    }

    internal fun parseAnnouncementDraftJson(jsonStr: String): AiAnnouncementDraft {
        val obj = JSONObject(jsonStr)
        return AiAnnouncementDraft(
            title = obj.optString("title", "Annonce communautaire"),
            content = obj.optString("content", ""),
            suggestedPinned = obj.optBoolean("suggestedPinned", false),
            toneSummary = obj.optString("toneSummary", "Clair")
        )
    }

    internal fun parseEventDraftJson(jsonStr: String): AiEventDraft {
        val obj = JSONObject(jsonStr)
        val ckArr = obj.optJSONArray("checklist")
        val checklist = if (ckArr != null) {
            (0 until ckArr.length()).mapNotNull { idx -> ckArr.optString(idx).takeIf { it.isNotBlank() } }
        } else emptyList()
        return AiEventDraft(
            title = obj.optString("title", "Événement communautaire"),
            description = obj.optString("description", ""),
            suggestedDateTimeLabel = obj.optString("suggestedDateTimeLabel", "Ce vendredi 20h00"),
            suggestedLocationLabel = obj.optString("suggestedLocationLabel", "Salon Vocal Principal"),
            checklist = checklist
        )
    }
}
