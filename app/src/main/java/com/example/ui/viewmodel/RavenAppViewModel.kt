package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Config-change resilient ViewModel that owns ChatRepository & WebRtcVoiceManager
 * so screen rotation never drops an active voice call, camera stream, or UI voice view state.
 */
class RavenAppViewModel(application: Application) : AndroidViewModel(application) {

    val repository: ChatRepository by lazy {
        val dbId = try {
            application.getString(R.string.firestore_database_id)
        } catch (_: Exception) {
            null
        }
        ChatRepository(
            scope = viewModelScope,
            databaseId = dbId,
            appContext = application.applicationContext
        )
    }

    private val _isViewingFullVoiceRoom = MutableStateFlow(false)
    val isViewingFullVoiceRoom: StateFlow<Boolean> = _isViewingFullVoiceRoom.asStateFlow()

    private val _hasCompletedEntryAnimation = MutableStateFlow(false)
    val hasCompletedEntryAnimation: StateFlow<Boolean> = _hasCompletedEntryAnimation.asStateFlow()

    fun setViewingFullVoiceRoom(viewing: Boolean) {
        _isViewingFullVoiceRoom.value = viewing
    }

    fun markEntryAnimationCompleted() {
        _hasCompletedEntryAnimation.value = true
    }

    override fun onCleared() {
        repository.releaseResources()
        super.onCleared()
    }
}
