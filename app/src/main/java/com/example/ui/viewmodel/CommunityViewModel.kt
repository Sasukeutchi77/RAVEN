package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunityUiState(
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val searchFilter: CommunitySearchFilter = CommunitySearchFilter.ALL,
    val searchResults: List<PublicServerListing> = emptyList()
)

class CommunityViewModel(
    val communityRepo: CommunityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    fun observeCommunity(serverId: String) {
        communityRepo.startListeningCommunity(serverId)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, errorMessage = null)
    }

    private inline fun runCommunityAction(
        successMessage: String? = null,
        crossinline block: suspend () -> Result<*>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, feedbackMessage = null)
            val result = block()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                feedbackMessage = if (result.isSuccess) successMessage else null,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun createCommunity(
        name: String,
        description: String,
        uniqueHandle: String,
        iconText: String,
        imageUrl: String?,
        bannerUrl: String?,
        bannerColorHex: Long,
        isPublic: Boolean,
        categoryTag: String,
        rulesGuidelines: String,
        homeWelcomeMessage: String,
        onCreated: (Server) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, feedbackMessage = null)
            val res = communityRepo.createCommunity(
                name, description, uniqueHandle, iconText, imageUrl, bannerUrl,
                bannerColorHex, isPublic, categoryTag, rulesGuidelines, homeWelcomeMessage
            )
            res.onSuccess { srv ->
                _uiState.value = _uiState.value.copy(isLoading = false, feedbackMessage = "Communauté ${srv.name} créée !")
                onCreated(srv)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun updateCommunity(
        serverId: String,
        name: String,
        description: String,
        uniqueHandle: String,
        imageUrl: String?,
        bannerUrl: String?,
        bannerColorHex: Long?,
        isPublic: Boolean,
        categoryTag: String,
        rulesGuidelines: String,
        homeWelcomeMessage: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = communityRepo.updateCommunity(
                serverId, name, description, uniqueHandle, imageUrl, bannerUrl,
                bannerColorHex, isPublic, categoryTag, rulesGuidelines, homeWelcomeMessage
            )
            res.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, feedbackMessage = "Communauté mise à jour.")
                onSuccess()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun deleteCommunity(serverId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = communityRepo.deleteCommunity(serverId)
            res.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, feedbackMessage = "Communauté supprimée.")
                onDeleted()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun leaveCommunity(serverId: String, onLeft: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = communityRepo.leaveCommunity(serverId)
            res.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, feedbackMessage = "Vous avez quitté la communauté.")
                onLeft()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun joinPublicCommunity(listing: PublicServerListing, onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = communityRepo.joinPublicCommunity(listing)
            res.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false, feedbackMessage = "Communauté rejointe !")
                onJoined()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun joinByInviteCode(code: String, onJoined: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = communityRepo.joinByInviteCode(code)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                feedbackMessage = if (res.isSuccess) "Invitation acceptée !" else null,
                errorMessage = res.exceptionOrNull()?.message
            )
            onJoined(res.isSuccess)
        }
    }

    fun createInvite(serverId: String, maxUses: Int, expiresInHours: Int) =
        runCommunityAction("Invitation générée !") { communityRepo.createInvite(serverId, maxUses, expiresInHours) }

    fun revokeInvite(serverId: String, code: String) =
        runCommunityAction("Invitation révoquée.") { communityRepo.revokeInvite(serverId, code) }

    fun deleteInvite(serverId: String, code: String) =
        runCommunityAction("Invitation supprimée.") { communityRepo.deleteInvite(serverId, code) }

    fun saveCustomRole(serverId: String, name: String, colorHex: Long, secondaryHex: Long?, perms: Set<RolePermission>) =
        runCommunityAction("Rôle enregistré !") { communityRepo.saveCustomRole(serverId, name, colorHex, secondaryHex, perms) }

    fun assignMemberRole(serverId: String, targetUid: String, role: UserRole, customRoleId: String?) =
        runCommunityAction("Rôle mis à jour.") { communityRepo.assignMemberRole(serverId, targetUid, role, customRoleId) }

    fun timeoutMember(serverId: String, targetUid: String) =
        runCommunityAction("Statut Timeout modifié.") { communityRepo.timeoutMember(serverId, targetUid) }

    fun kickMember(serverId: String, targetUid: String) =
        runCommunityAction("Membre expulsé.") { communityRepo.kickMember(serverId, targetUid) }

    fun createCategory(serverId: String, name: String, isPrivate: Boolean = false) =
        runCommunityAction("Catégorie créée !") { communityRepo.createCategory(serverId, name, isPrivate) }

    fun deleteCategory(serverId: String, name: String) =
        runCommunityAction("Catégorie supprimée.") { communityRepo.deleteCategory(serverId, name) }

    fun createChannel(serverId: String, name: String, type: ChannelType, categoryName: String, isPrivate: Boolean) =
        runCommunityAction("Salon créé !") { communityRepo.createChannel(serverId, name, type, categoryName, isPrivate) }

    fun deleteChannel(serverId: String, channelId: String) =
        runCommunityAction("Salon supprimé.") { communityRepo.deleteChannel(serverId, channelId) }

    fun publishAnnouncement(serverId: String, title: String, content: String, isPinned: Boolean) =
        runCommunityAction("Annonce publiée !") { communityRepo.publishAnnouncement(serverId, title, content, isPinned) }

    fun deleteAnnouncement(serverId: String, announcementId: String) =
        runCommunityAction("Annonce supprimée.") { communityRepo.deleteAnnouncement(serverId, announcementId) }

    fun createEvent(serverId: String, title: String, description: String, dateTimeLabel: String, locationLabel: String) =
        runCommunityAction("Événement planifié !") { communityRepo.createCommunityEvent(serverId, title, description, dateTimeLabel, locationLabel) }

    fun toggleEventInterest(serverId: String, eventId: String) =
        runCommunityAction { communityRepo.toggleEventInterest(serverId, eventId) }

    fun searchCommunities(query: String = _uiState.value.searchQuery, filter: CommunitySearchFilter = _uiState.value.searchFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searchQuery = query, searchFilter = filter)
            val list = communityRepo.searchCommunities(query, filter)
            _uiState.value = _uiState.value.copy(searchResults = list)
        }
    }

    override fun onCleared() {
        communityRepo.releaseListeners()
        super.onCleared()
    }
}
