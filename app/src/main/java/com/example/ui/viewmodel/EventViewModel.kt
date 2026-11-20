package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EventRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class EventViewModel(
    internal val eventRepo: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventUiState())
    val uiState: StateFlow<EventUiState> = _uiState.asStateFlow()

    private val _selectedFilter = MutableStateFlow(EventHubFilter.UPCOMING)
    private val _searchQuery = MutableStateFlow("")
    private val _isBusy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null)

    private var activeAuthUid: String? = null

    init {
        combine(
            eventRepo.events,
            eventRepo.remindersByEvent,
            eventRepo.rsvpsByEvent,
            eventRepo.historyByEvent,
            _selectedFilter
        ) { events, reminders, rsvps, history, filter ->
            Triple(events, reminders, Triple(rsvps, history, filter))
        }.combine(
            combine(_searchQuery, _isBusy, _feedback) { q, busy, fb -> Triple(q, busy, fb) }
        ) { (events, reminders, rhf), (query, busy, fb) ->
            val (rsvps, history, filter) = rhf
            val now = eventRepo.nowProvider()
            val upcoming = events.filter {
                val eff = it.effectiveStatus(now)
                eff == EventStatus.SCHEDULED || eff == EventStatus.ONGOING
            }.sortedBy { it.startAtMs }

            val pastAndCancelled = events.filter {
                val eff = it.effectiveStatus(now)
                eff == EventStatus.COMPLETED || eff == EventStatus.CANCELLED
            }.sortedByDescending { it.startAtMs }

            EventUiState(
                events = events,
                upcomingEvents = upcoming,
                historyEvents = pastAndCancelled,
                myRemindersByEvent = reminders,
                rsvpsByEvent = rsvps,
                historyByEvent = history,
                selectedFilter = filter,
                searchQuery = query,
                isBusy = busy,
                feedbackMessage = fb.first,
                errorMessage = fb.second
            )
        }.also { flow ->
            flow.launchIn(viewModelScope)
        }
    }

    fun syncWithAuthUid(uid: String?) {
        if (uid.isNullOrBlank()) {
            activeAuthUid = null
            eventRepo.stopListening()
        } else if (activeAuthUid != uid) {
            activeAuthUid = uid
            eventRepo.startListening(uid)
        }
    }

    fun setFilter(filter: EventHubFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun inspectEvent(eventId: String) {
        eventRepo.listenEventDetails(eventId)
    }

    fun canManage(event: RavenEvent): Boolean = eventRepo.canManageEvent(event)

    fun currentUserId(): String = eventRepo.currentUid()

    fun createEvent(
        input: EventDraftInput,
        coverUri: Uri? = null,
        context: Context? = null,
        onSuccess: (RavenEvent) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = eventRepo.createEvent(input, coverUri, context)
            _isBusy.value = false
            res.onSuccess { created ->
                _feedback.value = "Événement « ${created.title} » créé." to null
                onSuccess(created)
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Création impossible.")
            }
        }
    }

    fun updateEvent(
        eventId: String,
        input: EventDraftInput,
        coverUri: Uri? = null,
        context: Context? = null,
        onSuccess: (RavenEvent) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = eventRepo.updateEvent(eventId, input, coverUri, context)
            _isBusy.value = false
            res.onSuccess { updated ->
                _feedback.value = "Événement « ${updated.title} » mis à jour." to null
                onSuccess(updated)
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Modification impossible.")
            }
        }
    }

    fun cancelEvent(eventId: String, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = eventRepo.cancelEvent(eventId, reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Événement annulé et archivé dans l'historique." to null
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Annulation impossible.")
            }
        }
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = eventRepo.deleteEvent(eventId)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Événement supprimé." to null
                onSuccess()
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Suppression impossible.")
            }
        }
    }

    fun rsvp(eventId: String, status: RsvpStatus) {
        viewModelScope.launch {
            val res = eventRepo.rsvpToEvent(eventId, status)
            res.onSuccess { updated ->
                val actual = updated.userRsvpStatus(eventRepo.currentUid())
                val msg = when (actual) {
                    RsvpStatus.GOING -> "Participation confirmée pour « ${updated.title} »."
                    RsvpStatus.WAITLISTED -> "Liste d'attente (#${updated.waitlistPosition(eventRepo.currentUid()) ?: 1}) pour « ${updated.title} »."
                    RsvpStatus.MAYBE -> "Marqué comme intéressé(e)."
                    RsvpStatus.NOT_GOING -> "Désistement enregistré."
                }
                _feedback.value = msg to null
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "RSVP refusé.")
            }
        }
    }

    fun toggleReminder(eventId: String, minutesBefore: Int) {
        viewModelScope.launch {
            val existing = _uiState.value.myRemindersByEvent[eventId].orEmpty()
                .find { it.minutesBefore == minutesBefore }
            if (existing != null) {
                eventRepo.removeEventReminder(eventId, existing.id)
                _feedback.value = "Rappel supprimé." to null
            } else {
                val res = eventRepo.setEventReminder(eventId, minutesBefore)
                res.onSuccess {
                    _feedback.value = "Rappel programmé ($minutesBefore min avant)." to null
                }.onFailure { err ->
                    _feedback.value = null to (err.localizedMessage ?: "Rappel impossible.")
                }
            }
        }
    }

    fun shareInChannel(eventId: String, channelId: String) {
        val res = eventRepo.shareEventInChannel(eventId, channelId)
        res.onSuccess {
            _feedback.value = "Invitation partagée dans le salon." to null
        }.onFailure { err ->
            _feedback.value = null to (err.localizedMessage ?: "Partage impossible.")
        }
    }

    fun clearFeedback() {
        _feedback.value = null to null
    }
}
