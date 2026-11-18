package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiAssistantRepository
import com.example.data.AiProviderException
import com.example.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AiAssistantViewModel(
    private val aiRepo: AiAssistantRepository
) : ViewModel() {

    private val _activeMode = MutableStateFlow(AiAssistantMode.PERSONAL)
    private val _isGenerating = MutableStateFlow(false)
    private val _activeTaskLabel = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _lastModerationReport = MutableStateFlow<AiModerationReport?>(null)
    private val _lastAnnouncementDraft = MutableStateFlow<AiAnnouncementDraft?>(null)
    private val _lastEventDraft = MutableStateFlow<AiEventDraft?>(null)

    private val _uiState = MutableStateFlow(
        AiAssistantUiState(providerDisplayLabel = aiRepo.provider.modelDisplayName)
    )
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _activeMode,
                aiRepo.conversations,
                aiRepo.activeConversationId,
                aiRepo.messagesByConversation,
                aiRepo.quota,
                aiRepo.streamingBuffer,
                _isGenerating,
                _activeTaskLabel,
                _errorMessage,
                _feedbackMessage,
                _lastModerationReport,
                _lastAnnouncementDraft,
                _lastEventDraft
            ) { values ->
                val mode = values[0] as AiAssistantMode
                @Suppress("UNCHECKED_CAST")
                val convs = values[1] as List<AiConversation>
                val activeId = values[2] as String?
                @Suppress("UNCHECKED_CAST")
                val msgMap = values[3] as Map<String, List<AiChatMessage>>
                val quota = values[4] as AiUsageQuota
                val stream = values[5] as String
                val generating = values[6] as Boolean
                val taskLabel = values[7] as String?
                val err = values[8] as String?
                val fb = values[9] as String?
                val modRep = values[10] as AiModerationReport?
                val annDraft = values[11] as AiAnnouncementDraft?
                val evDraft = values[12] as AiEventDraft?

                AiAssistantUiState(
                    activeMode = mode,
                    conversations = convs,
                    activeConversationId = activeId,
                    activeMessages = activeId?.let { msgMap[it].orEmpty() }.orEmpty(),
                    quota = quota,
                    isGenerating = generating,
                    streamingBuffer = stream,
                    activeTaskLabel = taskLabel,
                    errorMessage = err,
                    feedbackMessage = fb,
                    lastModerationReport = modRep,
                    lastAnnouncementDraft = annDraft,
                    lastEventDraft = evDraft,
                    providerDisplayLabel = aiRepo.provider.modelDisplayName
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }

    fun startObserving() {
        aiRepo.startObserving()
    }

    fun setMode(mode: AiAssistantMode) {
        _activeMode.value = mode
    }

    fun selectConversation(conversationId: String) {
        aiRepo.selectConversation(conversationId)
    }

    fun clearFeedback() {
        _errorMessage.value = null
        _feedbackMessage.value = null
    }

    fun startNewConversation(server: Server?, channel: Channel?) {
        viewModelScope.launch {
            aiRepo.startNewConversation(_activeMode.value, server, channel)
                .onFailure { _errorMessage.value = formatAiError(it) }
                .onSuccess { _feedbackMessage.value = "Nouvelle conversation RAVEN AI prête." }
        }
    }

    fun sendPrompt(
        prompt: String,
        server: Server?,
        channel: Channel?,
        recentMessages: List<Message>
    ) {
        runAiTask("RAVEN AI réfléchit...") {
            aiRepo.sendAiChatMessage(
                rawPrompt = prompt,
                mode = _activeMode.value,
                server = server,
                channel = channel,
                recentMessages = recentMessages
            ).getOrThrow()
        }
    }

    fun summarizeChannel(
        server: Server?,
        channel: Channel?,
        recentMessages: List<Message>,
        isLongDiscussion: Boolean,
        customFocus: String = ""
    ) {
        val label = if (isLongDiscussion) "Synthèse de la discussion longue..." else "Résumé du salon en cours..."
        runAiTask(label) {
            aiRepo.summarizeMessages(server, channel, recentMessages, isLongDiscussion, customFocus).getOrThrow()
            _feedbackMessage.value = "Résumé généré dans la conversation."
        }
    }

    fun generateText(topic: String, tone: String, server: Server?) {
        runAiTask("Rédaction du texte...") {
            aiRepo.generateTextContent(topic, tone, server).getOrThrow()
            _feedbackMessage.value = "Texte généré avec succès."
        }
    }

    fun analyzeModeration(
        textToAnalyze: String,
        category: AiTaskCategory,
        server: Server?,
        channel: Channel?,
        recentMessages: List<Message>
    ) {
        runAiTask("Audit de sécurité & modération...") {
            val report = aiRepo.analyzeModerationAndSpam(
                contentToInspect = textToAnalyze,
                focusCategory = category,
                server = server,
                channel = channel,
                recentMessages = recentMessages
            ).getOrThrow()
            _lastModerationReport.value = report
        }
    }

    fun draftAnnouncement(idea: String, server: Server?) {
        runAiTask("Création du brouillon d'annonce...") {
            val draft = aiRepo.draftAnnouncement(idea, server).getOrThrow()
            _lastAnnouncementDraft.value = draft
        }
    }

    fun draftEvent(idea: String, server: Server?) {
        runAiTask("Préparation de l'événement...") {
            val draft = aiRepo.draftEvent(idea, server).getOrThrow()
            _lastEventDraft.value = draft
        }
    }

    fun adviseServerManagement(question: String, server: Server) {
        runAiTask("Analyse de la communauté...") {
            aiRepo.adviseServerManagement(question, server).getOrThrow()
            _feedbackMessage.value = "Recommandations serveur prêtes."
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            aiRepo.deleteConversation(conversationId)
                .onFailure { _errorMessage.value = formatAiError(it) }
                .onSuccess { _feedbackMessage.value = "Conversation supprimée." }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            aiRepo.clearAllHistory()
                .onFailure { _errorMessage.value = formatAiError(it) }
                .onSuccess {
                    _lastModerationReport.value = null
                    _lastAnnouncementDraft.value = null
                    _lastEventDraft.value = null
                    _feedbackMessage.value = "Tout l'historique RAVEN AI a été supprimé."
                }
        }
    }

    private fun runAiTask(label: String, block: suspend () -> Unit) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            _activeTaskLabel.value = label
            _errorMessage.value = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = formatAiError(e)
            } finally {
                _isGenerating.value = false
                _activeTaskLabel.value = null
            }
        }
    }

    private fun formatAiError(error: Throwable): String {
        return when (error) {
            is AiProviderException -> error.message
            else -> error.localizedMessage ?: "Une erreur RAVEN AI est survenue."
        }
    }
}
