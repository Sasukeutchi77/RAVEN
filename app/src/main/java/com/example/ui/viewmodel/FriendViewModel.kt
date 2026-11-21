package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.FriendRepository
import com.example.model.FriendPrivacySettings
import com.example.model.FriendRelationStatus
import com.example.model.FriendRequest
import com.example.model.FriendRequestDirection
import com.example.model.FriendUiState
import com.example.model.User
import com.example.model.UserStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FriendViewModel(
    private val friendRepo: FriendRepository,
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<FriendUiState> = combine(
        friendRepo.friends,
        chatRepo.friendRequests,
        chatRepo.blockedUsers,
        friendRepo.directoryUsers,
        friendRepo.privacySettings,
        _searchQuery,
        chatRepo.privateUserNotes,
        friendRepo.isBusy,
        friendRepo.feedbackMessage,
        friendRepo.errorMessage
    ) { values ->
        val friends = values[0] as List<com.example.model.FriendRelationship>
        val requests = values[1] as List<FriendRequest>
        val blocked = values[2] as List<User>
        val privacy = values[4] as FriendPrivacySettings
        val query = values[5] as String
        val busy = values[7] as Boolean
        val feedback = values[8] as String?
        val error = values[9] as String?

        val online = friends.filter { it.user.status != UserStatus.OFFLINE }
        val incoming = requests.filter { it.direction == FriendRequestDirection.RECEIVED }
        val outgoing = requests.filter { it.direction == FriendRequestDirection.SENT }
        val directoryResults = friendRepo.searchUsers(query)

        FriendUiState(
            friends = friends,
            onlineFriends = online,
            incomingRequests = incoming,
            outgoingRequests = outgoing,
            blockedUsers = blocked,
            directoryResults = directoryResults,
            privacySettings = privacy,
            searchQuery = query,
            isBusy = busy,
            feedbackMessage = feedback,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FriendUiState()
    )

    fun syncWithAuthUid(uid: String?) {
        friendRepo.syncWithAuthUid(uid)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun sendFriendRequestByInput(tagOrNameOrUid: String): Boolean =
        friendRepo.sendFriendRequestByInput(tagOrNameOrUid)

    fun sendFriendRequestToUser(user: User): Boolean =
        friendRepo.sendFriendRequestToUser(user)

    fun acceptRequest(requestId: String) {
        friendRepo.acceptFriendRequest(requestId)
    }

    fun declineRequest(requestId: String) {
        friendRepo.declineFriendRequest(requestId)
    }

    fun cancelRequest(requestId: String) {
        friendRepo.cancelFriendRequest(requestId)
    }

    fun declineOrCancelRequest(requestId: String) {
        friendRepo.declineOrCancelFriendRequest(requestId)
    }

    fun removeFriend(friendUid: String) {
        friendRepo.removeFriend(friendUid)
    }

    fun blockUser(user: User) {
        friendRepo.blockUser(user)
    }

    fun unblockUser(userId: String) {
        friendRepo.unblockUser(userId)
    }

    fun openDm(user: User, bypassDmPolicy: Boolean = false): Boolean =
        friendRepo.openOrCreateDirectConversation(user, bypassDmPolicy = bypassDmPolicy) != null

    fun updatePrivacy(settings: FriendPrivacySettings) {
        friendRepo.updatePrivacySettings(settings)
    }

    fun updatePrivateNote(userId: String, note: String) {
        friendRepo.updatePrivateNote(userId, note)
    }

    fun relationStatusFor(uid: String): FriendRelationStatus =
        friendRepo.relationStatusFor(uid)

    fun pendingRequestFor(uid: String): FriendRequest? =
        friendRepo.pendingRequestFor(uid)

    fun clearFeedback() {
        friendRepo.clearFeedback()
    }
}
