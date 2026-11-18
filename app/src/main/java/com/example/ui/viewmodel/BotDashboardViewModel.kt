package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BotRepository
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BotDashboardUiState(
    val isLoading: Boolean = false,
    val selectedBotId: String? = null,
    val oneTimePlaintextToken: String? = null,
    val oneTimeTokenBotName: String? = null,
    val lastVerifyResult: Boolean? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class BotDashboardViewModel(
    val botRepo: BotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotDashboardUiState())
    val uiState: StateFlow<BotDashboardUiState> = _uiState.asStateFlow()

    fun startObserving(activeServerId: String?) {
        botRepo.startListeningBots()
        if (!activeServerId.isNullOrBlank() && activeServerId != "dm_tab") {
            botRepo.observeServerBots(activeServerId)
        }
    }

    fun selectBot(botId: String?) {
        _uiState.value = _uiState.value.copy(selectedBotId = botId, lastVerifyResult = null)
        if (!botId.isNullOrBlank()) {
            botRepo.observeBotLogs(botId)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null, errorMessage = null)
    }

    fun clearOneTimeToken() {
        _uiState.value = _uiState.value.copy(
            oneTimePlaintextToken = null,
            oneTimeTokenBotName = null
        )
    }

    private inline fun runBotAction(
        successMessage: String? = null,
        crossinline block: suspend () -> Result<*>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, feedbackMessage = null)
            val res = block()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                feedbackMessage = if (res.isSuccess) successMessage else null,
                errorMessage = res.exceptionOrNull()?.message
            )
        }
    }

    fun createBot(
        name: String,
        handle: String,
        description: String,
        avatarUrl: String?,
        avatarColor: Long,
        isPublicCatalog: Boolean,
        defaultPermissions: Set<BotPermission>,
        subscribedEvents: Set<BotEventTrigger>,
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, feedbackMessage = null)
            val res = botRepo.createBot(
                name = name,
                handleRaw = handle,
                description = description,
                avatarUrl = avatarUrl,
                avatarColor = avatarColor,
                isPublicCatalog = isPublicCatalog,
                defaultPermissions = defaultPermissions,
                subscribedEvents = subscribedEvents
            )
            res.onSuccess { created ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedBotId = created.bot.id,
                    oneTimePlaintextToken = created.oneTimePlaintextToken,
                    oneTimeTokenBotName = created.bot.name,
                    feedbackMessage = "Bot @${created.bot.handle} créé !"
                )
                botRepo.observeBotLogs(created.bot.id)
                onCreated()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun updateBot(
        botId: String,
        name: String,
        handle: String,
        description: String,
        avatarUrl: String?,
        avatarColor: Long,
        isPublicCatalog: Boolean,
        defaultPermissions: Set<BotPermission>,
        subscribedEvents: Set<BotEventTrigger>
    ) = runBotAction("Bot mis à jour.") {
        botRepo.updateBot(
            botId = botId,
            name = name,
            handleRaw = handle,
            description = description,
            avatarUrl = avatarUrl,
            avatarColor = avatarColor,
            isPublicCatalog = isPublicCatalog,
            defaultPermissions = defaultPermissions,
            subscribedEvents = subscribedEvents
        )
    }

    fun toggleBotActive(botId: String, active: Boolean) = runBotAction(
        if (active) "Bot activé." else "Bot désactivé."
    ) {
        botRepo.toggleBotActive(botId, active)
    }

    fun regenerateToken(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, feedbackMessage = null)
            val res = botRepo.regenerateBotToken(botId)
            res.onSuccess { regen ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    oneTimePlaintextToken = regen.oneTimePlaintextToken,
                    oneTimeTokenBotName = regen.bot.name,
                    feedbackMessage = "Nouveau jeton v${regen.bot.tokenVersion} généré. L'ancien est révoqué."
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun verifyToken(botId: String, candidate: String) {
        viewModelScope.launch {
            val res = botRepo.verifyBotToken(botId, candidate)
            res.onSuccess { valid ->
                _uiState.value = _uiState.value.copy(
                    lastVerifyResult = valid,
                    feedbackMessage = if (valid) "Jeton valide (SHA-256 vérifié)." else null,
                    errorMessage = if (!valid) "Jeton invalide ou révoqué." else null
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.message)
            }
        }
    }

    fun deleteBot(botId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val res = botRepo.deleteBot(botId)
            res.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedBotId = if (_uiState.value.selectedBotId == botId) null else _uiState.value.selectedBotId,
                    feedbackMessage = "Bot supprimé définitivement."
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = err.message)
            }
        }
    }

    fun addBotToServer(
        botId: String,
        serverId: String,
        permissions: Set<BotPermission>? = null,
        roleTitle: String = "Bot Raven",
        customRoleId: String? = null
    ) = runBotAction("Bot ajouté au serveur !") {
        botRepo.addBotToServer(botId, serverId, permissions, roleTitle, customRoleId)
    }

    fun removeBotFromServer(botId: String, serverId: String) = runBotAction("Bot retiré du serveur.") {
        botRepo.removeBotFromServer(botId, serverId)
    }

    fun updateServerBotConfig(
        botId: String,
        serverId: String,
        permissions: Set<BotPermission>,
        roleTitle: String,
        customRoleId: String?,
        isEnabledInServer: Boolean
    ) = runBotAction("Permissions et rôle du bot mis à jour.") {
        botRepo.updateServerBotConfig(botId, serverId, permissions, roleTitle, customRoleId, isEnabledInServer)
    }

    fun saveCommand(botId: String, spec: BotCommandSpec) = runBotAction("Commande enregistrée !") {
        botRepo.saveBotCommand(botId, spec)
    }

    fun deleteCommand(botId: String, commandId: String) = runBotAction("Commande supprimée.") {
        botRepo.deleteBotCommand(botId, commandId)
    }

    fun saveAutoResponse(botId: String, rule: BotAutoResponseRule) = runBotAction("Auto-réponse enregistrée !") {
        botRepo.saveAutoResponse(botId, rule)
    }

    fun deleteAutoResponse(botId: String, ruleId: String) = runBotAction("Auto-réponse supprimée.") {
        botRepo.deleteAutoResponse(botId, ruleId)
    }

    fun saveWebhook(
        botId: String,
        label: String,
        url: String,
        secret: String,
        events: Set<BotEventTrigger>
    ) = runBotAction("Webhook HTTPS sécurisé ajouté !") {
        botRepo.saveWebhook(botId, label, url, secret, events)
    }

    fun deleteWebhook(botId: String, webhookId: String) = runBotAction("Webhook supprimé.") {
        botRepo.deleteWebhook(botId, webhookId)
    }

    fun triggerTestWebhook(botId: String, webhookId: String) = runBotAction("Test Webhook envoyé !") {
        botRepo.triggerTestWebhook(botId, webhookId)
    }

    fun togglePlugin(botId: String, pluginId: String, enabled: Boolean) = runBotAction(
        if (enabled) "Plugin activé." else "Plugin désactivé."
    ) {
        botRepo.toggleBotPlugin(botId, pluginId, enabled)
    }

    fun updateRateLimit(botId: String, config: BotRateLimitConfig) = runBotAction("Protection anti-abus mise à jour.") {
        botRepo.updateRateLimitConfig(botId, config)
    }

    override fun onCleared() {
        botRepo.releaseListeners()
        super.onCleared()
    }
}
