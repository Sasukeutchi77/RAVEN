package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StoryRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    val unseenGroups: List<StoryAuthorGroup> = emptyList(),
    val seenGroups: List<StoryAuthorGroup> = emptyList(),
    val allGroups: List<StoryAuthorGroup> = emptyList(),
    val activeStoryViewers: List<StoryViewerEntry> = emptyList(),
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class StoryViewModel(private val repo: StoryRepository) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _viewers = MutableStateFlow<List<StoryViewerEntry>>(emptyList())
    private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null)

    private val _uiState = MutableStateFlow(StoryUiState())
    val uiState: StateFlow<StoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.stories,
                _busy,
                _viewers,
                _feedback
            ) { _, busy, viewers, feedback ->
                val (unseen, seen) = repo.groupedStories()
                val (fb, err) = feedback
                StoryUiState(
                    unseenGroups = unseen,
                    seenGroups = seen,
                    allGroups = unseen + seen,
                    activeStoryViewers = viewers,
                    isBusy = busy,
                    feedbackMessage = fb,
                    errorMessage = err
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun startObserving() {
        repo.startRealtimeSync()
        viewModelScope.launch {
            repo.cleanupExpiredStories()
        }
    }

    fun clearFeedback() {
        _feedback.value = null to null
    }

    fun publishTextStory(
        textContent: String,
        caption: String,
        backgroundColorHex: Long,
        secondaryColorHex: Long,
        visibility: StoryVisibility,
        customAllowedViewerIds: Set<String>,
        durationHours: Int,
        onPublished: (StoryItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.publishTextStory(
                textContent = textContent,
                caption = caption,
                backgroundColorHex = backgroundColorHex,
                secondaryColorHex = secondaryColorHex,
                visibility = visibility,
                customAllowedViewerIds = customAllowedViewerIds,
                durationHours = durationHours
            )
            _busy.value = false
            res.onSuccess { created ->
                _feedback.value = "Story publiée avec succès !" to null
                onPublished(created)
            }.onFailure { err ->
                _feedback.value = null to (err.message ?: "Impossible de publier la story.")
            }
        }
    }

    fun publishMediaStory(
        context: Context,
        uri: Uri,
        mediaType: StoryMediaType,
        caption: String,
        visibility: StoryVisibility,
        customAllowedViewerIds: Set<String>,
        durationHours: Int,
        onPublished: (StoryItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.publishMediaStoryFromUri(
                context = context,
                uri = uri,
                mediaType = mediaType,
                caption = caption,
                visibility = visibility,
                customAllowedViewerIds = customAllowedViewerIds,
                durationHours = durationHours
            )
            _busy.value = false
            res.onSuccess { created ->
                _feedback.value = "Story ${mediaType.label.lowercase()} publiée !" to null
                onPublished(created)
            }.onFailure { err ->
                _feedback.value = null to (err.message ?: "Erreur lors de l'envoi sécurisé du média.")
            }
        }
    }

    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            repo.markStoryViewed(storyId)
        }
    }

    fun loadStoryViewers(storyId: String) {
        viewModelScope.launch {
            val res = repo.getStoryViewers(storyId)
            res.onSuccess { entries ->
                _viewers.value = entries
            }.onFailure { err ->
                _feedback.value = null to (err.message ?: "Impossible de charger les vues.")
            }
        }
    }

    fun toggleStoryLike(storyId: String) {
        viewModelScope.launch {
            val res = repo.toggleStoryLike(storyId)
            res.onFailure { err ->
                _feedback.value = null to (err.message ?: "Impossible d'aimer cette story.")
            }
        }
    }

    fun deleteStory(storyId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val res = repo.deleteStory(storyId)
            _busy.value = false
            res.onSuccess {
                _feedback.value = "Story supprimée." to null
                onDeleted()
            }.onFailure { err ->
                _feedback.value = null to (err.message ?: "Suppression refusée.")
            }
        }
    }


    fun replyToStory(storyId: String, text: String) {
        val res = repo.replyToStory(storyId, text)
        res.onSuccess {
            _feedback.value = "Réponse envoyée en message privé !" to null
        }.onFailure { err ->
            _feedback.value = null to (err.message ?: "Envoi impossible.")
        }
    }

    fun runManualCleanup() {
        viewModelScope.launch {
            val stats = repo.cleanupExpiredStories()
            _feedback.value = "Nettoyage effectué : ${stats.removedExpiredStories} expirée(s), ${stats.removedMediaFiles} fichier(s) purgé(s)." to null
        }
    }

    override fun onCleared() {
        repo.stopRealtimeSync()
        super.onCleared()
    }
}
