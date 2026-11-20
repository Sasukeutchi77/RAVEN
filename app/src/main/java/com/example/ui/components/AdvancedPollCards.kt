package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatPollDeadline(poll: AdvancedPoll, nowMs: Long = System.currentTimeMillis()): String {
    val exp = poll.expiresAtMs ?: return "Sans expiration"
    val fmt = SimpleDateFormat("dd/MM à HH:mm", Locale.getDefault())
    val dateStr = fmt.format(Date(exp))
    if (nowMs > exp) return "Expiré le $dateStr"
    val diffMin = ((exp - nowMs) / 60_000L).coerceAtLeast(1L)
    val rel = when {
        diffMin < 60L -> "${diffMin}m restantes"
        diffMin < 1440L -> "${diffMin / 60L}h ${diffMin % 60L}m restantes"
        else -> "${diffMin / 1440L}j restant(s)"
    }
    return "Fin le $dateStr • $rel"
}

@Composable
fun AdvancedPollCard(
    poll: AdvancedPoll,
    myVote: PollVoteRecord?,
    allVotes: List<PollVoteRecord>,
    currentUserId: String,
    canVote: Boolean,
    voteBlockedReason: String?,
    canManage: Boolean,
    accentColor: Color = DiscordBlurple,
    onToggleOption: (String) -> Unit,
    onRetractVote: () -> Unit,
    onEditDraft: () -> Unit,
    onPublishDraft: () -> Unit,
    onClosePoll: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenExport: () -> Unit,
    onDeletePoll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    val now = System.currentTimeMillis()
    val effStatus = poll.effectiveStatus(now)
    val isAuthor = poll.authorId == currentUserId
    val selectedIds = myVote?.selectedOptionIds?.toSet() ?: emptySet()
    val totalOptionVotes = poll.options.sumOf { it.voteCount }.coerceAtLeast(poll.totalVotes)
    val maxOptVotes = poll.options.maxOfOrNull { it.voteCount } ?: 0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.cardBg,
        border = BorderStroke(
            width = 1.dp,
            color = when (effStatus) {
                PollStatus.DRAFT -> DiscordAmber.copy(alpha = 0.55f)
                PollStatus.PUBLISHED -> accentColor.copy(alpha = 0.45f)
                PollStatus.CLOSED -> palette.border
            }
        ),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("advanced_poll_card_${poll.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Status + Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val (statusColor, statusText) = when {
                        effStatus == PollStatus.DRAFT -> DiscordAmber to "BROUILLON"
                        poll.isExpired(now) -> DiscordRed to "EXPIRÉ"
                        effStatus == PollStatus.CLOSED -> DiscordRed to "CLÔTURÉ"
                        else -> DiscordGreen to "EN COURS"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    val visLabel = when (poll.visibility) {
                        PollVisibility.PUBLIC -> "Public"
                        PollVisibility.SERVER -> "Communauté"
                        PollVisibility.ROLE_RESTRICTED -> "Rôles"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = visLabel,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    if (poll.isAnonymous) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.18f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = "Anonyme",
                                    tint = Color(0xFFC4B5FD),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "Anonyme",
                                    color = Color(0xFFC4B5FD),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = if (poll.allowsMultiple) "Choix multiples (${poll.maxChoices})" else "Choix unique",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Question + Context
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = poll.question,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (poll.description.isNotBlank()) {
                    Text(
                        text = poll.description,
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = buildString {
                        append("Par ${poll.authorName}")
                        if (poll.serverName.isNotBlank()) append(" • ${poll.serverName}")
                        if (poll.channelName.isNotBlank()) append(" #${poll.channelName}")
                    },
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Restrictions or single definitive vote notice
            if (poll.visibility == PollVisibility.ROLE_RESTRICTED && poll.allowedRoleNames.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DiscordAmber.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, DiscordAmber.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Rôles autorisés : ${poll.allowedRoleNames.joinToString(", ")}",
                            color = DiscordAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (voteBlockedReason != null && effStatus == PollStatus.PUBLISHED) {
                Text(
                    text = voteBlockedReason,
                    color = DiscordAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Options with real-time animated progress fill
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                poll.options.forEach { opt ->
                    val isSelected = opt.id in selectedIds
                    val pct = if (totalOptionVotes > 0) (opt.voteCount.toFloat() / totalOptionVotes).coerceIn(0f, 1f) else 0f
                    val animatedPct by animateFloatAsState(
                        targetValue = pct,
                        animationSpec = tween(durationMillis = 320),
                        label = "poll_opt_pct_${opt.id}"
                    )
                    val pctInt = (pct * 100f).toInt()
                    val isWinning = effStatus != PollStatus.DRAFT && maxOptVotes > 0 && opt.voteCount == maxOptVotes
                    val optVoters = if (!poll.isAnonymous) {
                        allVotes.filter { opt.id in it.selectedOptionIds }.map { it.voterName }.distinct()
                    } else emptyList()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.16f) else palette.inputBg,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = when {
                                isSelected -> accentColor
                                isWinning -> DiscordGreen.copy(alpha = 0.5f)
                                else -> palette.border
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = canVote) { onToggleOption(opt.id) }
                            .testTag("poll_option_${poll.id}_${opt.id}")
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedPct)
                                    .background(
                                        if (isSelected) accentColor.copy(alpha = 0.28f)
                                        else if (isWinning) DiscordGreen.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(if (poll.allowsMultiple) RoundedCornerShape(5.dp) else CircleShape)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isSelected) accentColor else palette.textMuted,
                                                    shape = if (poll.allowsMultiple) RoundedCornerShape(5.dp) else CircleShape
                                                )
                                                .background(if (isSelected) accentColor else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Choisi",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = opt.text,
                                            color = if (isSelected) Color.White else palette.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected || isWinning) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isWinning) {
                                            Icon(
                                                Icons.Default.EmojiEvents,
                                                contentDescription = "En tête",
                                                tint = DiscordAmber,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "$pctInt% (${opt.voteCount})",
                                            color = if (isWinning) palette.textPrimary else palette.textMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (optVoters.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Votants : ${optVoters.take(4).joinToString(", ")}${if (optVoters.size > 4) " +${optVoters.size - 4}" else ""}",
                                        color = palette.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Metadata & Expiration bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${poll.totalVoters} votant(s) • $totalOptionVotes vote(s)",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatPollDeadline(poll, now),
                    color = if (poll.isExpired(now)) DiscordRed else palette.textMuted,
                    fontSize = 11.sp
                )
            }

            if (!poll.allowVoteChange) {
                Text(
                    text = "Réponse unique définitive (non modifiable après soumission)",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.6f))

            // Actions Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (effStatus == PollStatus.DRAFT && isAuthor) {
                    FilledTonalButton(
                        onClick = onEditDraft,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("poll_edit_draft_${poll.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Modifier", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onPublishDraft,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("poll_publish_draft_${poll.id}")
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Publier", fontSize = 12.sp)
                    }
                } else {
                    TextButton(
                        onClick = onOpenStats,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("poll_stats_btn_${poll.id}")
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(4.dp))
                        Text("Stats", fontSize = 12.sp, color = accentColor)
                    }
                    TextButton(
                        onClick = onOpenExport,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("poll_export_btn_${poll.id}")
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(15.dp), tint = palette.textSecondary)
                        Spacer(Modifier.width(4.dp))
                        Text("Exporter", fontSize = 12.sp, color = palette.textSecondary)
                    }
                    if (selectedIds.isNotEmpty() && canVote && poll.allowVoteChange) {
                        TextButton(
                            onClick = onRetractVote,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        ) {
                            Text("Retirer vote", fontSize = 11.sp, color = DiscordAmber)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (effStatus == PollStatus.PUBLISHED && canManage) {
                    IconButton(
                        onClick = onClosePoll,
                        modifier = Modifier.size(40.dp).testTag("poll_close_btn_${poll.id}")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Clôturer manuellement", tint = DiscordAmber, modifier = Modifier.size(18.dp))
                    }
                }
                if (isAuthor || canManage) {
                    IconButton(
                        onClick = onDeletePoll,
                        modifier = Modifier.size(40.dp).testTag("poll_delete_btn_${poll.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer le sondage", tint = DiscordRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
