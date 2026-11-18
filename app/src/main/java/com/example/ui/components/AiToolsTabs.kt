package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun AiSafetyAndSummaryTab(
    uiState: AiAssistantUiState,
    activeServer: Server,
    activeChannel: Channel,
    channelMessagesCount: Int,
    currentUserId: String,
    onSummarizeChannel: (isLong: Boolean, customFocus: String) -> Unit,
    onAnalyzeModeration: (text: String, category: AiTaskCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var summaryFocus by remember { mutableStateOf("") }
    var inspectText by remember { mutableStateOf("") }
    val canModerate = remember(activeServer, currentUserId) {
        activeServer.isDmTab ||
            CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MODERATE_MEMBERS) ||
            CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MANAGE_COMMUNITY) ||
            CommunityPermissions.isOwner(activeServer, currentUserId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Conversation & Long Thread Summarizer
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Summarize,
                        contentDescription = null,
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Résumés de discussions (#${activeChannel.name})",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$channelMessagesCount messages disponibles dans le salon. Les données privées sont masquées automatiquement avant analyse.",
                    color = palette.textMuted,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = summaryFocus,
                    onValueChange = { summaryFocus = it },
                    placeholder = {
                        Text(
                            "Axe prioritaire optionnel (ex: décisions, bugs, dates...)",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_summary_focus_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSummarizeChannel(false, summaryFocus) },
                        enabled = !uiState.isGenerating && channelMessagesCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_summarize_short_btn")
                    ) {
                        Text("Résumé rapide", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onSummarizeChannel(true, summaryFocus) },
                        enabled = !uiState.isGenerating && channelMessagesCount > 0,
                        border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_summarize_long_btn")
                    ) {
                        Text("Discussion longue", color = Color(0xFFA855F7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section 2: Moderation, Anti-Spam & Suspicious Content Audit
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = DiscordGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sûreté, Modération & Anti-Spam",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!canModerate && !activeServer.isDmTab) {
                    Surface(
                        color = DiscordAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Réservé aux modérateurs et administrateurs sur ${activeServer.name}.",
                            color = DiscordAmber,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = inspectText,
                    onValueChange = { inspectText = it },
                    placeholder = {
                        Text(
                            "Collez un message suspect à auditer (ou laissez vide pour analyser les 20 derniers messages du salon)",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_moderation_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordGreen,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAnalyzeModeration(inspectText, AiTaskCategory.MODERATION_HELP) },
                        enabled = !uiState.isGenerating && canModerate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_audit_moderation_btn")
                    ) {
                        Text("Modérer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onAnalyzeModeration(inspectText, AiTaskCategory.SPAM_DETECTION) },
                        enabled = !uiState.isGenerating && canModerate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_audit_spam_btn")
                    ) {
                        Text("Anti-Spam", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onAnalyzeModeration(inspectText, AiTaskCategory.SUSPICIOUS_CONTENT) },
                        enabled = !uiState.isGenerating && canModerate,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_audit_suspect_btn")
                    ) {
                        Text("Suspect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Moderation Report Output Card
        uiState.lastModerationReport?.let { report ->
            AiModerationReportCard(report = report)
        }
    }
}

@Composable
private fun AiModerationReportCard(report: AiModerationReport) {
    val palette = ravenPalette()
    val badgeColor = when (report.riskLevel.uppercase()) {
        "CRITICAL", "HIGH" -> DiscordRed
        "MEDIUM" -> DiscordAmber
        else -> DiscordGreen
    }
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_moderation_report_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rapport de Sûreté IA",
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${report.riskLevel} • ${report.riskScore}/100",
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { (report.riskScore / 100f).coerceIn(0f, 1f) },
                color = badgeColor,
                trackColor = palette.inputBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (report.isSpam) "Spam détecté" else "Pas de spam", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (report.isSuspicious) "Contenu suspect" else "Contenu sûr", fontSize = 11.sp) }
                )
            }
            Text(
                text = "Action recommandée : ${report.recommendedAction}",
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (report.explanation.isNotBlank()) {
                Text(text = report.explanation, color = palette.textSecondary, fontSize = 12.sp)
            }
            if (report.signals.isNotEmpty()) {
                report.signals.take(4).forEach { sig ->
                    Text(text = "• $sig", color = palette.textMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AiServerToolsTab(
    uiState: AiAssistantUiState,
    activeServer: Server,
    currentUserId: String,
    onGenerateText: (topic: String, tone: String) -> Unit,
    onDraftAnnouncement: (idea: String) -> Unit,
    onPublishAnnouncement: (title: String, content: String, isPinned: Boolean) -> Unit,
    onDraftEvent: (idea: String) -> Unit,
    onCreateEvent: (title: String, desc: String, dateTime: String, location: String) -> Unit,
    onAdviseServerManagement: (question: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var textTopic by remember { mutableStateOf("") }
    var textTone by remember { mutableStateOf("Professionnel et engageant") }
    var announcementIdea by remember { mutableStateOf("") }
    var eventIdea by remember { mutableStateOf("") }
    var managementQuestion by remember { mutableStateOf("") }

    val canPublishAnn = remember(activeServer, currentUserId) {
        !activeServer.isDmTab && CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.POST_ANNOUNCEMENTS)
    }
    val canCreateEv = remember(activeServer, currentUserId) {
        !activeServer.isDmTab && CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MANAGE_EVENTS)
    }
    val canManageServer = remember(activeServer, currentUserId) {
        !activeServer.isDmTab && (
            CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MANAGE_COMMUNITY) ||
                CommunityPermissions.hasPermission(activeServer, currentUserId, RolePermission.MANAGE_CHANNELS) ||
                CommunityPermissions.isOwner(activeServer, currentUserId)
            )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Text Generator
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Génération de textes IA", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = textTopic,
                    onValueChange = { textTopic = it },
                    placeholder = { Text("Sujet (ex: Règlement du serveur, message d'accueil...)", color = palette.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("ai_text_topic_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                OutlinedTextField(
                    value = textTone,
                    onValueChange = { textTone = it },
                    label = { Text("Ton", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Button(
                    onClick = { onGenerateText(textTopic, textTone) },
                    enabled = !uiState.isGenerating && textTopic.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_generate_text_btn")
                ) {
                    Text("Générer dans la discussion", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Announcement Assistant
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aide à la création d'annonce", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = announcementIdea,
                    onValueChange = { announcementIdea = it },
                    placeholder = { Text("Idée d'annonce (ex: Mise à jour v2 ce samedi)", color = palette.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("ai_ann_idea_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordBlurple,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Button(
                    onClick = { onDraftAnnouncement(announcementIdea) },
                    enabled = !uiState.isGenerating && announcementIdea.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_draft_ann_btn")
                ) {
                    Text("Générer le brouillon d'annonce", color = Color.White, fontWeight = FontWeight.Bold)
                }

                uiState.lastAnnouncementDraft?.let { draft ->
                    HorizontalDivider(color = palette.border)
                    Text(draft.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(draft.content, color = palette.textSecondary, fontSize = 12.sp)
                    if (canPublishAnn) {
                        FilledTonalButton(
                            onClick = { onPublishAnnouncement(draft.title, draft.content, draft.suggestedPinned) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_publish_ann_btn")
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publier sur ${activeServer.name}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Event Assistant
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aide à la création d'événement", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = eventIdea,
                    onValueChange = { eventIdea = it },
                    placeholder = { Text("Idée d'événement (ex: Tournoi communautaire vendredi)", color = palette.textMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("ai_event_idea_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.inputBg,
                        unfocusedContainerColor = palette.inputBg,
                        focusedBorderColor = DiscordGreen,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textSecondary
                    )
                )
                Button(
                    onClick = { onDraftEvent(eventIdea) },
                    enabled = !uiState.isGenerating && eventIdea.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_draft_event_btn")
                ) {
                    Text("Préparer l'événement IA", color = Color.White, fontWeight = FontWeight.Bold)
                }

                uiState.lastEventDraft?.let { draft ->
                    HorizontalDivider(color = palette.border)
                    Text(draft.title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${draft.suggestedDateTimeLabel} • ${draft.suggestedLocationLabel}",
                        color = DiscordGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(draft.description, color = palette.textSecondary, fontSize = 12.sp)
                    if (canCreateEv) {
                        FilledTonalButton(
                            onClick = {
                                onCreateEvent(
                                    draft.title,
                                    draft.description,
                                    draft.suggestedDateTimeLabel,
                                    draft.suggestedLocationLabel
                                )
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_create_event_btn")
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Créer l'événement sur ${activeServer.name}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Server Management Advisor
        if (!activeServer.isDmTab) {
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aide à la gestion du serveur", color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = managementQuestion,
                        onValueChange = { managementQuestion = it },
                        placeholder = { Text("Question optionnelle (ex: Optimiser nos rôles & salons)", color = palette.textMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.inputBg,
                            unfocusedContainerColor = palette.inputBg,
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textSecondary
                        )
                    )
                    Button(
                        onClick = { onAdviseServerManagement(managementQuestion) },
                        enabled = !uiState.isGenerating && canManageServer,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ai_server_management_btn")
                    ) {
                        Text(
                            if (canManageServer) "Auditer la communauté" else "Réservé aux admins serveur",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiHistoryAndQuotaTab(
    uiState: AiAssistantUiState,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var confirmClearAll by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quota & Anti-abuse protection status panel
        Surface(
            color = palette.cardBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quotas & Protection anti-abus", color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${uiState.quota.requestsToday}/${uiState.quota.dailyLimit} / jour",
                        color = if (uiState.quota.remainingToday <= 5) DiscordAmber else DiscordGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { (uiState.quota.requestsToday.toFloat() / uiState.quota.dailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    color = Color(0xFF7C3AED),
                    trackColor = palette.inputBg,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = "Débit minute : ${uiState.quota.requestsInLastMinute}/${uiState.quota.perMinuteLimit} • Alertes sécurité : ${uiState.quota.abuseStrikes}/3",
                    color = palette.textMuted,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historique (${uiState.conversations.size})",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (uiState.conversations.isNotEmpty()) {
                TextButton(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier.testTag("ai_clear_all_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DiscordRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tout effacer", color = DiscordRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucune conversation enregistrée.", color = palette.textMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.conversations, key = { it.id }) { conv ->
                    val isSelected = conv.id == uiState.activeConversationId
                    Surface(
                        color = if (isSelected) Color(0xFF7C3AED).copy(alpha = 0.16f) else palette.cardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF7C3AED) else palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectConversation(conv.id) }
                            .testTag("ai_conv_item_${conv.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7C3AED).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (conv.mode == AiAssistantMode.SERVER) Icons.Default.Dns else Icons.Default.Person,
                                    contentDescription = conv.mode.label,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${conv.mode.label}${conv.serverName?.let { " • $it" } ?: ""} • ${conv.messageCount} msg",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onDeleteConversation(conv.id) },
                                modifier = Modifier.testTag("ai_delete_conv_${conv.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer cette conversation",
                                    tint = DiscordRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = palette.cardBg,
            title = { Text("Supprimer l'historique ?", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Toutes vos conversations RAVEN AI stockées sur votre compte seront supprimées définitivement.",
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearAll = false
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordRed),
                    modifier = Modifier.testTag("ai_confirm_clear_all_btn")
                ) {
                    Text("Tout supprimer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Annuler", color = palette.textMuted)
                }
            }
        )
    }
}
