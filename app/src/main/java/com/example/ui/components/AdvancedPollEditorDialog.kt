package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdvancedPollEditorDialog(
    server: Server?,
    channel: Channel?,
    initialDraft: AdvancedPoll? = null,
    onDismiss: () -> Unit,
    onSaveDraft: (PollDraftInput) -> Unit,
    onPublish: (PollDraftInput, shareInChannel: Boolean) -> Unit
) {
    val palette = ravenPalette()
    var question by remember(initialDraft) { mutableStateOf(initialDraft?.question ?: "") }
    var description by remember(initialDraft) { mutableStateOf(initialDraft?.description ?: "") }
    val optionsState = remember(initialDraft) {
        mutableStateListOf<String>().apply {
            val existing = initialDraft?.options?.map { it.text }.orEmpty()
            if (existing.size >= 2) addAll(existing) else addAll(listOf("", ""))
        }
    }
    var allowsMultiple by remember(initialDraft) { mutableStateOf(initialDraft?.allowsMultiple ?: false) }
    var maxChoices by remember(initialDraft) { mutableIntStateOf((initialDraft?.maxChoices ?: 2).coerceAtLeast(2)) }
    var allowVoteChange by remember(initialDraft) { mutableStateOf(initialDraft?.allowVoteChange ?: true) }
    var isAnonymous by remember(initialDraft) { mutableStateOf(initialDraft?.isAnonymous ?: false) }
    var visibility by remember(initialDraft) {
        mutableStateOf(
            initialDraft?.visibility ?: if (server != null && !server.isDmTab) PollVisibility.SERVER else PollVisibility.PUBLIC
        )
    }
    var selectedRoleIds by remember(initialDraft) {
        mutableStateOf(initialDraft?.allowedRoleIds?.toSet() ?: setOf(UserRole.ADMIN.name))
    }
    var selectedDurationMinutes by remember(initialDraft) { mutableIntStateOf(initialDraft?.durationMinutes ?: 1440) }
    var notifyOnPublish by remember(initialDraft) { mutableStateOf(initialDraft?.notifyOnPublish ?: true) }
    var notifyOnClose by remember(initialDraft) { mutableStateOf(initialDraft?.notifyOnClose ?: true) }
    var shareInChannel by remember { mutableStateOf(channel != null) }

    val availableRoles: List<Pair<String, String>> = remember(server) {
        buildList {
            add(UserRole.ADMIN.name to "Administrateur")
            add(UserRole.MODERATOR.name to "Modérateur")
            add(UserRole.MEMBER.name to "Membre")
            server?.customRoles?.forEach { cr ->
                if (cr.id !in setOf("role_admin", "role_mod", "role_member")) {
                    add(cr.id to cr.name)
                }
            }
        }
    }

    val validOptions = optionsState.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val canSubmit = question.trim().length >= 3 &&
        validOptions.size >= 2 &&
        (visibility != PollVisibility.ROLE_RESTRICTED || selectedRoleIds.isNotEmpty()) &&
        (visibility == PollVisibility.PUBLIC || (server != null && !server.isDmTab))

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.getDefault()) }
    val previewExpiryText = remember(selectedDurationMinutes) {
        if (selectedDurationMinutes <= 0) {
            "Aucune date d'expiration (sondage permanent jusqu'à clôture manuelle)"
        } else {
            val expMs = System.currentTimeMillis() + selectedDurationMinutes * 60_000L
            "Expiration prévue le ${dateFormatter.format(Date(expMs))}"
        }
    }

    fun buildInput(): PollDraftInput {
        val roleNames = availableRoles.filter { it.first in selectedRoleIds }.map { it.second }
        return PollDraftInput(
            pollId = initialDraft?.id,
            serverId = server?.takeIf { !it.isDmTab }?.id,
            channelId = channel?.id,
            question = question.trim(),
            description = description.trim(),
            options = validOptions,
            allowsMultiple = allowsMultiple,
            maxChoices = if (allowsMultiple) maxChoices.coerceIn(2, validOptions.size.coerceAtLeast(2)) else 1,
            allowVoteChange = allowVoteChange,
            isAnonymous = isAnonymous,
            visibility = visibility,
            allowedRoleIds = if (visibility == PollVisibility.ROLE_RESTRICTED) selectedRoleIds.toList() else emptyList(),
            allowedRoleNames = if (visibility == PollVisibility.ROLE_RESTRICTED) roleNames else emptyList(),
            durationMinutes = selectedDurationMinutes,
            notifyOnPublish = notifyOnPublish,
            notifyOnClose = notifyOnClose
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = palette.cardBg,
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.90f)
                .testTag("advanced_poll_editor_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Poll, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (initialDraft != null) "Modifier le brouillon" else "Nouveau sondage avancé",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = server?.takeIf { !it.isDmTab }?.let { "${it.name}${channel?.let { ch -> " • #${ch.name}" } ?: ""}" }
                                    ?: "Sondage public RAVEN",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textSecondary)
                    }
                }

                HorizontalDivider(color = palette.border)

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("QUESTION & CONTEXTE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Question du sondage *") },
                        placeholder = { Text("Ex. Quelle fonctionnalité prioriser ?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("poll_question_input")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / contexte (optionnel)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("poll_description_input")
                    )

                    // Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OPTIONS (${optionsState.size}/10)", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (optionsState.size < 10) {
                            TextButton(
                                onClick = { optionsState.add("") },
                                modifier = Modifier.testTag("poll_add_option_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ajouter un choix")
                            }
                        }
                    }
                    optionsState.forEachIndexed { idx, optText ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = optText,
                                onValueChange = { optionsState[idx] = it },
                                label = { Text("Choix ${idx + 1}") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("poll_option_input_$idx")
                            )
                            if (optionsState.size > 2) {
                                IconButton(
                                    onClick = { optionsState.removeAt(idx) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Retirer l'option", tint = DiscordRed)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border)

                    // Vote Rules & Anonymity
                    Text("RÈGLES DE VOTE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !allowsMultiple,
                            onClick = { allowsMultiple = false },
                            label = { Text("Sondage simple (1 choix)") },
                            modifier = Modifier.weight(1f).testTag("poll_mode_single")
                        )
                        FilterChip(
                            selected = allowsMultiple,
                            onClick = { allowsMultiple = true },
                            label = { Text("Choix multiples") },
                            modifier = Modifier.weight(1f).testTag("poll_mode_multiple")
                        )
                    }

                    if (allowsMultiple) {
                        val upper = validOptions.size.coerceAtLeast(2)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nombre max de réponses par utilisateur : ${maxChoices.coerceIn(2, upper)}", color = palette.textPrimary, fontSize = 13.sp)
                            Row {
                                IconButton(
                                    onClick = { if (maxChoices > 2) maxChoices-- },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Diminuer", tint = palette.textSecondary)
                                }
                                IconButton(
                                    onClick = { if (maxChoices < upper) maxChoices++ },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Augmenter", tint = DiscordBlurple)
                                }
                            }
                        }
                    }

                    SwitchRowItem(
                        title = "Autoriser la modification du vote",
                        subtitle = if (allowVoteChange) "Les utilisateurs peuvent modifier/retirer leur vote jusqu'à expiration" else "Une réponse unique définitive par utilisateur",
                        checked = allowVoteChange,
                        onCheckedChange = { allowVoteChange = it },
                        testTag = "poll_allow_change_switch"
                    )

                    SwitchRowItem(
                        title = "Sondage anonyme",
                        subtitle = "Masque l'identité de tous les votants sur le client et Firestore",
                        checked = isAnonymous,
                        onCheckedChange = { isAnonymous = it },
                        testTag = "poll_anonymous_switch"
                    )

                    HorizontalDivider(color = palette.border)

                    // Duration & Expiration
                    Text("DURÉE & EXPIRATION", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PollDurationPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedDurationMinutes == preset.minutes,
                                onClick = { selectedDurationMinutes = preset.minutes },
                                label = { Text(preset.label, fontSize = 12.sp) },
                                modifier = Modifier.testTag("poll_duration_${preset.name}")
                            )
                        }
                    }
                    Text(
                        text = previewExpiryText,
                        color = DiscordBlurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    HorizontalDivider(color = palette.border)

                    // Visibility & Roles
                    Text("VISIBILITÉ & ACCÈS PAR RÔLE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PollVisibility.entries.forEach { vis ->
                            val enabled = vis == PollVisibility.PUBLIC || (server != null && !server.isDmTab)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (visibility == vis) DiscordBlurple.copy(alpha = 0.14f) else palette.inputBg,
                                border = BorderStroke(1.dp, if (visibility == vis) DiscordBlurple else palette.border),
                                modifier = Modifier.fillMaxWidth().testTag("poll_visibility_${vis.name}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = visibility == vis,
                                        onClick = { if (enabled) visibility = vis },
                                        enabled = enabled
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(vis.label, color = if (enabled) palette.textPrimary else palette.textMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(vis.description, color = palette.textMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (visibility == PollVisibility.ROLE_RESTRICTED) {
                        Text("Rôles autorisés à voter :", color = DiscordAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableRoles.forEach { (roleId, roleLabel) ->
                                val picked = roleId in selectedRoleIds
                                FilterChip(
                                    selected = picked,
                                    onClick = {
                                        selectedRoleIds = if (picked) selectedRoleIds - roleId else selectedRoleIds + roleId
                                    },
                                    label = { Text(roleLabel, fontSize = 12.sp) },
                                    modifier = Modifier.testTag("poll_role_chip_$roleId")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = palette.border)

                    // Notifications & Sharing
                    Text("NOTIFICATIONS & PARTAGE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    SwitchRowItem(
                        title = "Notifier lors de la publication",
                        subtitle = "Envoie une alerte dans la boîte de réception RAVEN",
                        checked = notifyOnPublish,
                        onCheckedChange = { notifyOnPublish = it },
                        testTag = "poll_notify_publish_switch"
                    )
                    SwitchRowItem(
                        title = "Notifier à la clôture / expiration",
                        subtitle = "Annonce l'option gagnante dès la fermeture du sondage",
                        checked = notifyOnClose,
                        onCheckedChange = { notifyOnClose = it },
                        testTag = "poll_notify_close_switch"
                    )
                    if (channel != null) {
                        SwitchRowItem(
                            title = "Publier la carte dans #${channel.name}",
                            subtitle = "Partage automatiquement le sondage dans le salon actif",
                            checked = shareInChannel,
                            onCheckedChange = { shareInChannel = it },
                            testTag = "poll_share_channel_switch"
                        )
                    }
                }

                HorizontalDivider(color = palette.border)

                // Footer Buttons: Draft & Publish
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (canSubmit) {
                                onSaveDraft(buildInput())
                                onDismiss()
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("poll_save_draft_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (initialDraft != null) "Mettre à jour" else "Brouillon", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (canSubmit) {
                                onPublish(buildInput(), shareInChannel)
                                onDismiss()
                            }
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("poll_publish_btn")
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Publier", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRowItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = ravenPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = palette.textMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
