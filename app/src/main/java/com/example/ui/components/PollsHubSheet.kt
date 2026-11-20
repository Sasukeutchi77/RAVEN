package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.PollUiState
import com.example.ui.viewmodel.PollViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollsHubSheet(
    pollViewModel: PollViewModel,
    activeServer: Server?,
    activeChannel: Channel?,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val uiState by pollViewModel.uiState.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<AdvancedPoll?>(null) }
    var inspectPollPair by remember { mutableStateOf<Pair<AdvancedPoll, Boolean>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.chatBg,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .testTag("polls_hub_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Poll, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Sondages avancés RAVEN", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = activeServer?.takeIf { !it.isDmTab }?.name ?: "Espace Public & Communautés",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            editingDraft = null
                            showEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("polls_hub_create_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nouveau", fontSize = 12.sp, color = Color.White)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textSecondary)
                    }
                }
            }

            PollsFeedContent(
                pollViewModel = pollViewModel,
                uiState = uiState,
                serverFilterId = null,
                accentColor = activeServer?.customBannerColorHex?.let { Color(it) } ?: DiscordBlurple,
                onCreateNew = {
                    editingDraft = null
                    showEditor = true
                },
                onEditDraft = { draft ->
                    editingDraft = draft
                    showEditor = true
                },
                onOpenStats = { poll, startExport ->
                    inspectPollPair = poll to startExport
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showEditor) {
        AdvancedPollEditorDialog(
            server = activeServer?.takeIf { !it.isDmTab },
            channel = activeChannel,
            initialDraft = editingDraft,
            onDismiss = {
                showEditor = false
                editingDraft = null
            },
            onSaveDraft = { input -> pollViewModel.saveDraft(input) },
            onPublish = { input, shareInChannel ->
                pollViewModel.createAndPublish(input, shareInChannel)
            }
        )
    }

    inspectPollPair?.let { (poll, startExport) ->
        val freshPoll = uiState.polls.find { it.id == poll.id } ?: poll
        val stats = remember(freshPoll, uiState.votesByPoll) { pollViewModel.computeStats(freshPoll) }
        AdvancedPollStatsAndExportSheet(
            poll = freshPoll,
            stats = stats,
            startOnExportTab = startExport,
            exportContentProvider = { fmt -> pollViewModel.exportPoll(freshPoll, fmt) },
            onDismiss = { inspectPollPair = null }
        )
    }
}

@Composable
fun PollsFeedContent(
    pollViewModel: PollViewModel,
    uiState: PollUiState,
    serverFilterId: String?,
    accentColor: Color,
    onCreateNew: () -> Unit,
    onEditDraft: (AdvancedPoll) -> Unit,
    onOpenStats: (AdvancedPoll, startExport: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val now = System.currentTimeMillis()
    val currentUid = pollViewModel.currentUserId()

    val filteredPolls = remember(uiState.polls, uiState.activeFilter, uiState.searchQuery, serverFilterId) {
        val scoped = if (serverFilterId != null) {
            uiState.polls.filter { it.serverId == serverFilterId || (uiState.activeFilter == PollHubFilter.PUBLIC && it.isPublic) }
        } else uiState.polls

        val byFilter = when (uiState.activeFilter) {
            PollHubFilter.ACTIVE -> scoped.filter { it.isEffectivelyOpen(now) }
            PollHubFilter.PUBLIC -> scoped.filter { it.isPublic && it.status != PollStatus.DRAFT }
            PollHubFilter.DRAFTS -> scoped.filter { it.status == PollStatus.DRAFT && it.authorId == currentUid }
            PollHubFilter.CLOSED -> scoped.filter { it.effectiveStatus(now) == PollStatus.CLOSED }
            PollHubFilter.ALL -> scoped
        }
        val q = uiState.searchQuery.trim()
        if (q.isBlank()) byFilter else {
            byFilter.filter {
                it.question.contains(q, ignoreCase = true) ||
                    it.description.contains(q, ignoreCase = true) ||
                    it.authorName.contains(q, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Feedback / Error banner
        val bannerText = uiState.errorMessage ?: uiState.feedbackMessage
        if (bannerText != null) {
            val isErr = uiState.errorMessage != null
            Surface(
                color = if (isErr) DiscordRed.copy(alpha = 0.16f) else DiscordGreen.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, if (isErr) DiscordRed else DiscordGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = bannerText,
                        color = if (isErr) DiscordRed else DiscordGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { pollViewModel.clearFeedback() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Masquer", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Search input + Create action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { pollViewModel.setSearchQuery(it) },
                placeholder = { Text("Rechercher un sondage...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("polls_search_input")
            )
            FilledTonalIconButton(
                onClick = onCreateNew,
                modifier = Modifier.size(48.dp).testTag("polls_inline_create_btn")
            ) {
                Icon(Icons.Default.AddChart, contentDescription = "Créer un sondage", tint = accentColor)
            }
        }

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PollHubFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.activeFilter == filter,
                    onClick = { pollViewModel.setFilter(filter) },
                    label = { Text(filter.label, fontSize = 12.sp) },
                    modifier = Modifier.testTag("poll_filter_${filter.name}")
                )
            }
        }

        if (filteredPolls.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Poll, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(42.dp))
                    Text(
                        text = when (uiState.activeFilter) {
                            PollHubFilter.DRAFTS -> "Aucun brouillon en attente"
                            PollHubFilter.CLOSED -> "Aucun sondage clôturé"
                            else -> "Aucun sondage dans cette vue"
                        },
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Créez un sondage simple, à choix multiples, anonyme ou réservé aux rôles.",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Créer un sondage", color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPolls, key = { it.id }) { poll ->
                    val (canVote, blockedReason) = pollViewModel.canVote(poll)
                    val canManage = pollViewModel.canManage(poll)
                    val myVote = uiState.myVotesByPoll[poll.id]
                    val votes = uiState.votesByPoll[poll.id].orEmpty()

                    AdvancedPollCard(
                        poll = poll,
                        myVote = myVote,
                        allVotes = votes,
                        currentUserId = currentUid,
                        canVote = canVote,
                        voteBlockedReason = blockedReason,
                        canManage = canManage,
                        accentColor = accentColor,
                        onToggleOption = { optId -> pollViewModel.toggleVote(poll.id, optId) },
                        onRetractVote = { pollViewModel.retractVote(poll.id) },
                        onEditDraft = { onEditDraft(poll) },
                        onPublishDraft = { pollViewModel.publishExistingDraft(poll.id) },
                        onClosePoll = { pollViewModel.closePollManually(poll.id) },
                        onOpenStats = { onOpenStats(poll, false) },
                        onOpenExport = { onOpenStats(poll, true) },
                        onDeletePoll = { pollViewModel.deletePoll(poll.id) }
                    )
                }
            }
        }
    }
}
