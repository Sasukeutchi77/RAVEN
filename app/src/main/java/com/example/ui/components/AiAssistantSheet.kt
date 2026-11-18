package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiAssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    uiState: AiAssistantUiState,
    viewModel: AiAssistantViewModel,
    activeServer: Server,
    activeChannel: Channel,
    channelMessages: List<Message>,
    currentUserId: String,
    onPostToChannel: (text: String) -> Unit,
    onPublishAnnouncement: (title: String, content: String, isPinned: Boolean) -> Unit,
    onCreateEvent: (title: String, desc: String, dateTime: String, location: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chat IA", "Résumé & Sûreté", "Outils", "Historique")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("ai_assistant_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF2563EB)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "RAVEN AI",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "RAVEN AI",
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${uiState.providerDisplayLabel} • ${uiState.quota.remainingToday}/${uiState.quota.dailyLimit} restants",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        viewModel.startNewConversation(activeServer, activeChannel)
                        selectedTab = 0
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_new_conversation_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nouvelle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Assistant Mode Switcher (Personal vs Server)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiAssistantMode.entries.forEach { mode ->
                    val isSelected = uiState.activeMode == mode
                    val isServerDisabled = mode == AiAssistantMode.SERVER && activeServer.isDmTab
                    FilterChip(
                        selected = isSelected,
                        enabled = !isServerDisabled,
                        onClick = { viewModel.setMode(mode) },
                        label = {
                            val labelTxt = if (mode == AiAssistantMode.SERVER && !activeServer.isDmTab) {
                                "Serveur (${activeServer.name.take(14)})"
                            } else {
                                mode.label
                            }
                            Text(labelTxt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == AiAssistantMode.SERVER) Icons.Default.Dns else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_mode_${mode.name.lowercase()}")
                    )
                }
            }

            // Inline error or feedback banner
            val bannerText = uiState.errorMessage ?: uiState.feedbackMessage
            if (bannerText != null) {
                val isError = uiState.errorMessage != null
                Surface(
                    color = if (isError) DiscordRed.copy(alpha = 0.16f) else DiscordGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("ai_sheet_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerText,
                            color = if (isError) DiscordRed else DiscordGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearFeedback() }) {
                            Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = palette.sidebarBg,
                contentColor = Color(0xFFA855F7)
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("ai_tab_$idx")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> AiConversationTab(
                        uiState = uiState,
                        activeServer = activeServer,
                        activeChannel = activeChannel,
                        onSendPrompt = { prompt ->
                            viewModel.sendPrompt(prompt, activeServer, activeChannel, channelMessages)
                        },
                        onPostToChannel = onPostToChannel
                    )
                    1 -> AiSafetyAndSummaryTab(
                        uiState = uiState,
                        activeServer = activeServer,
                        activeChannel = activeChannel,
                        channelMessagesCount = channelMessages.size,
                        currentUserId = currentUserId,
                        onSummarizeChannel = { isLong, focus ->
                            viewModel.summarizeChannel(activeServer, activeChannel, channelMessages, isLong, focus)
                            selectedTab = 0
                        },
                        onAnalyzeModeration = { text, category ->
                            viewModel.analyzeModeration(text, category, activeServer, activeChannel, channelMessages)
                        }
                    )
                    2 -> AiServerToolsTab(
                        uiState = uiState,
                        activeServer = activeServer,
                        currentUserId = currentUserId,
                        onGenerateText = { topic, tone ->
                            viewModel.generateText(topic, tone, if (activeServer.isDmTab) null else activeServer)
                            selectedTab = 0
                        },
                        onDraftAnnouncement = { idea ->
                            viewModel.draftAnnouncement(idea, if (activeServer.isDmTab) null else activeServer)
                        },
                        onPublishAnnouncement = onPublishAnnouncement,
                        onDraftEvent = { idea ->
                            viewModel.draftEvent(idea, if (activeServer.isDmTab) null else activeServer)
                        },
                        onCreateEvent = onCreateEvent,
                        onAdviseServerManagement = { q ->
                            viewModel.adviseServerManagement(q, activeServer)
                            selectedTab = 0
                        }
                    )
                    3 -> AiHistoryAndQuotaTab(
                        uiState = uiState,
                        onSelectConversation = { id ->
                            viewModel.selectConversation(id)
                            selectedTab = 0
                        },
                        onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                        onClearAllHistory = { viewModel.clearAllHistory() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AiConversationTab(
    uiState: AiAssistantUiState,
    activeServer: Server,
    activeChannel: Channel,
    onSendPrompt: (String) -> Unit,
    onPostToChannel: (String) -> Unit
) {
    val palette = ravenPalette()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var promptText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.activeMessages.size, uiState.streamingBuffer) {
        val total = uiState.activeMessages.size + if (uiState.streamingBuffer.isNotBlank()) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    val quickPrompts = remember(uiState.activeMode, activeChannel.name) {
        if (uiState.activeMode == AiAssistantMode.SERVER && !activeServer.isDmTab) {
            listOf(
                "Résume les points essentiels de #${activeChannel.name}",
                "Propose 5 règles claires pour ce serveur",
                "Idées d'animation communautaire pour cette semaine"
            )
        } else {
            listOf(
                "Aide-moi à rédiger un message professionnel",
                "Donne-moi un plan d'action productif aujourd'hui",
                "Explique les commandes IA disponibles sur Raven"
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { suggestion ->
                SuggestionChip(
                    onClick = {
                        if (!uiState.isGenerating) onSendPrompt(suggestion)
                    },
                    label = { Text(suggestion, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.activeMessages.isEmpty() && uiState.streamingBuffer.isBlank()) {
                item {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Bienvenue dans RAVEN AI",
                                color = palette.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Posez vos questions, résumez un salon, détectez le spam ou préparez des annonces. Toutes vos données sensibles sont protégées.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Commandes salon : /ai, /resumer, /longresume, /moderer, /spam, /suspect, /annonceai, /eventai, /gererserveur",
                                color = Color(0xFFA855F7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            items(uiState.activeMessages, key = { it.id }) { msg ->
                val isUser = msg.role == AiMessageRole.USER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) Color(0xFF7C3AED) else palette.cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isUser) null else BorderStroke(1.dp, palette.border),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = msg.content,
                                color = if (isUser) Color.White else palette.textPrimary,
                                fontSize = 13.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${msg.taskCategory.label} • ${msg.timestampLabel}",
                                    color = if (isUser) Color.White.copy(alpha = 0.75f) else palette.textMuted,
                                    fontSize = 10.sp
                                )
                                if (!isUser) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Copier",
                                            color = Color(0xFFA855F7),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { clipboard.setText(AnnotatedString(msg.content)) }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                        if (!activeServer.isDmTab) {
                                            Text(
                                                text = "Partager #${activeChannel.name}",
                                                color = DiscordBlurple,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { onPostToChannel(msg.content) }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.streamingBuffer.isNotBlank()) {
                item(key = "streaming_bubble") {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .testTag("ai_streaming_bubble")
                    ) {
                        Text(
                            text = uiState.streamingBuffer,
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        if (uiState.isGenerating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF7C3AED)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.activeTaskLabel ?: "RAVEN AI répond...",
                    color = palette.textMuted,
                    fontSize = 12.sp
                )
            }
        }

        // Composer
        Surface(
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Demandez à RAVEN AI...", color = palette.textMuted, fontSize = 13.sp) },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                IconButton(
                    onClick = {
                        val toSend = promptText.trim()
                        if (toSend.isNotEmpty() && !uiState.isGenerating) {
                            promptText = ""
                            onSendPrompt(toSend)
                        }
                    },
                    enabled = promptText.isNotBlank() && !uiState.isGenerating,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (promptText.isNotBlank()) Color(0xFF7C3AED) else palette.inputBg)
                        .testTag("ai_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Envoyer à RAVEN AI",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
