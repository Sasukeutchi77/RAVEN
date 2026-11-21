package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PluginRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class PluginManagerViewModel(
    internal val pluginRepo: PluginRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<PluginCategory?>(null)
    private val _selectedLogLevel = MutableStateFlow<PluginLogLevel?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null)

    private val _uiState = MutableStateFlow(PluginManagerUiState(hostVersion = pluginRepo.hostVersion))
    val uiState: StateFlow<PluginManagerUiState> = _uiState.asStateFlow()

    private var activeUid: String? = null

    init {
        combine(
            pluginRepo.pluginItems,
            pluginRepo.logs,
            _selectedCategory,
            _selectedLogLevel,
            combine(_isBusy, _feedback) { b, fb -> b to fb }
        ) { items, logs, cat, lvl, (busy, fb) ->
            PluginManagerUiState(
                hostVersion = pluginRepo.hostVersion,
                plugins = items,
                logs = logs,
                selectedCategory = cat,
                selectedLogLevel = lvl,
                isBusy = busy,
                feedbackMessage = fb.first,
                errorMessage = fb.second
            )
        }.also { it.launchIn(viewModelScope) }
    }

    fun syncWithAuthUid(uid: String?) {
        if (uid.isNullOrBlank()) {
            activeUid = null
            pluginRepo.stopListening()
        } else if (activeUid != uid) {
            activeUid = uid
            pluginRepo.startListening(uid)
        }
    }

    fun setCategoryFilter(cat: PluginCategory?) {
        _selectedCategory.value = cat
    }

    fun setLogLevelFilter(level: PluginLogLevel?) {
        _selectedLogLevel.value = level
    }

    fun enablePlugin(
        pluginId: String,
        approvedPermissions: Set<PluginPermission>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = pluginRepo.enablePlugin(pluginId, approvedPermissions)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Extension activée avec ${it.grantedPermissions.size} permission(s)." to null
                onSuccess()
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Activation impossible.")
            }
        }
    }

    fun disablePlugin(pluginId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = pluginRepo.disablePlugin(pluginId)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Extension désactivée." to null
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Désactivation impossible.")
            }
        }
    }

    fun resetFaulted(pluginId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = pluginRepo.resetFaultedPlugin(pluginId)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Extension sortie de quarantaine." to null
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Réinitialisation impossible.")
            }
        }
    }

    fun togglePermission(pluginId: String, permission: PluginPermission, granted: Boolean) {
        viewModelScope.launch {
            val res = pluginRepo.setPermissionGranted(pluginId, permission, granted)
            res.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Modification de permission refusée.")
            }
        }
    }

    fun saveConfig(pluginId: String, proposedConfig: Map<String, String>, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = pluginRepo.updatePluginConfig(pluginId, proposedConfig)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "Configuration enregistrée." to null
                onSuccess()
            }.onFailure { err ->
                _feedback.value = null to (err.localizedMessage ?: "Configuration invalide.")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            pluginRepo.clearLogs()
            _feedback.value = "Journaux effacés." to null
        }
    }

    fun clearFeedback() {
        _feedback.value = null to null
    }
}
