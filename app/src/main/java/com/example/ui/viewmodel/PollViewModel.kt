package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PollRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PollUiState(
    val polls: List<AdvancedPoll> = emptyList(),
    val votesByPoll: Map<String, List<PollVoteRecord>> = emptyMap(),
    val myVotesByPoll: Map<String, PollVoteRecord> = emptyMap(),
    val activeFilter: PollHubFilter = PollHubFilter.ACTIVE,
    val searchQuery: String = "",
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class PollViewModel(val repo: PollRepository) : ViewModel() {

    private val _filter = MutableStateFlow(PollHubFilter.ACTIVE)
    private val _searchQuery = MutableStateFlow("")
    private val _busy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null)

    private val _uiState = MutableStateFlow(PollUiState())
    val uiState: StateFlow<PollUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.polls,
                repo.votesByPoll,
                repo.myVotesByPoll,
                _filter,
                _searchQuery,
                _busy,
                _feedback
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val polls = values[0] as List<AdvancedPoll>
                @Suppress("UNCHECKED_CAST")
                val votes = values[1] as Map<String, List<PollVoteRecord>>
                @Suppress("UNCHECKED_CAST")
                val myVotes = values[2] as Map<String, PollVoteRecord>
                val filter = values[3] as PollHubFilter
                val query = values[4] as String
                val busy = values[5] as Boolean
                @Suppress("UNCHECKED_CAST")
                val fbPair = values[6] as Pair<String?, String?>

                PollUiState(
                    polls = polls,
                    votesByPoll = votes,
                    myVotesByPoll = myVotes,
                    activeFilter = filter,
                    searchQuery = query,
                    isBusy = busy,
                    feedbackMessage = fbPair.first,
                    errorMessage = fbPair.second
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun syncWithAuthUid(uid: String?) {
        if (uid.isNullOrBlank()) {
            repo.stopListening()
        } else {
            repo.startListening(uid)
            repo.checkAndNotifyExpiredPolls()
        }
    }

    fun setFilter(filter: PollHubFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearFeedback() {
        _feedback.value = null to null
    }

    fun saveDraft(input: PollDraftInput, onSuccess: (AdvancedPoll) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            repo.saveDraft(input).fold(
                onSuccess = {
                    _feedback.value = "Brouillon enregistré (modifiable avant publication)." to null
                    onSuccess(it)
                },
                onFailure = { err ->
                    _feedback.value = null to (err.message ?: "Impossible d'enregistrer le brouillon.")
                }
            )
            _busy.value = false
        }
    }

    fun createAndPublish(input: PollDraftInput, shareInChannel: Boolean = true, onSuccess: (AdvancedPoll) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            repo.createAndPublishPoll(input, shareInChannel).fold(
                onSuccess = {
                    _feedback.value = "Sondage publié en temps réel !" to null
                    onSuccess(it)
                },
                onFailure = { err ->
                    _feedback.value = null to (err.message ?: "Impossible de publier le sondage.")
                }
            )
            _busy.value = false
        }
    }

    fun publishExistingDraft(pollId: String, shareInChannel: Boolean = true) {
        viewModelScope.launch {
            _busy.value = true
            repo.publishPoll(pollId, shareInChannel).fold(
                onSuccess = { _feedback.value = "Sondage publié !" to null },
                onFailure = { err -> _feedback.value = null to (err.message ?: "Erreur lors de la publication.") }
            )
            _busy.value = false
        }
    }

    fun toggleVote(pollId: String, optionId: String) {
        viewModelScope.launch {
            repo.toggleOptionVote(pollId, optionId).fold(
                onSuccess = { _feedback.value = null to null },
                onFailure = { err -> _feedback.value = null to (err.message ?: "Vote refusé.") }
            )
        }
    }

    fun retractVote(pollId: String) {
        viewModelScope.launch {
            repo.retractVote(pollId).fold(
                onSuccess = { _feedback.value = "Vote retiré." to null },
                onFailure = { err -> _feedback.value = null to (err.message ?: "Impossible de retirer le vote.") }
            )
        }
    }

    fun closePollManually(pollId: String) {
        viewModelScope.launch {
            _busy.value = true
            repo.closePollManually(pollId).fold(
                onSuccess = { _feedback.value = "Sondage clôturé manuellement." to null },
                onFailure = { err -> _feedback.value = null to (err.message ?: "Impossible de clôturer ce sondage.") }
            )
            _busy.value = false
        }
    }

    fun deletePoll(pollId: String) {
        viewModelScope.launch {
            _busy.value = true
            repo.deletePoll(pollId).fold(
                onSuccess = { _feedback.value = "Sondage supprimé." to null },
                onFailure = { err -> _feedback.value = null to (err.message ?: "Suppression refusée.") }
            )
            _busy.value = false
        }
    }

    fun exportPoll(poll: AdvancedPoll, format: PollExportFormat): String {
        return repo.exportPollResults(poll, format)
    }

    fun computeStats(poll: AdvancedPoll): PollStatistics = repo.computeStatistics(poll)

    fun canVote(poll: AdvancedPoll): Pair<Boolean, String?> = repo.canUserVote(poll)

    fun canManage(poll: AdvancedPoll): Boolean = repo.canCloseOrManagePoll(poll)

    fun currentUserId(): String = repo.currentUid()
}
