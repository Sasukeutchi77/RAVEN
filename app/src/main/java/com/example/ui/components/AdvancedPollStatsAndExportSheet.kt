package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPollStatsAndExportSheet(
    poll: AdvancedPoll,
    stats: PollStatistics,
    startOnExportTab: Boolean = false,
    exportContentProvider: (PollExportFormat) -> String,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var activeTab by remember(startOnExportTab) { mutableIntStateOf(if (startOnExportTab) 1 else 0) }
    var selectedFormat by remember { mutableStateOf(PollExportFormat.SUMMARY_TEXT) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    val exportPayload = remember(poll, selectedFormat) { exportContentProvider(selectedFormat) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("poll_stats_export_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Statistiques & Exportation",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = poll.question,
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = palette.textSecondary)
                }
            }

            TabRow(selectedTabIndex = activeTab, containerColor = palette.sidebarBg) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Statistiques", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("poll_stats_tab")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Exporter (CSV / JSON)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("poll_export_tab")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == 0) {
                    // KPI Summary Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMetricBox("Votants uniques", "${stats.uniqueVoters}", DiscordBlurple, Modifier.weight(1f))
                        StatMetricBox("Votes exprimés", "${stats.totalVotes}", DiscordGreen, Modifier.weight(1f))
                        StatMetricBox("Participation", "${stats.participationRatePercent.roundToInt()}%", Color(0xFF00D4AA), Modifier.weight(1f))
                    }

                    // Winner / Lead summary card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DiscordBlurple.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Synthèse (${stats.statusLabel})", color = DiscordAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(stats.winnerSummary, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                if (stats.leadMarginVotes > 0) {
                                    Text(
                                        text = "Avance : +${stats.leadMarginVotes} vote(s) sur le 2e choix",
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    if (stats.isAnonymous) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Sondage anonyme : les identités des votants sont chiffrées/masquées et protégées par Firestore.",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Text("RÉPARTITION DÉTAILLÉE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    stats.optionStats.forEach { opt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.inputBg,
                            border = BorderStroke(1.dp, if (opt.isLeader) DiscordGreen.copy(alpha = 0.5f) else palette.border)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = opt.text,
                                        color = palette.textPrimary,
                                        fontWeight = if (opt.isLeader) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${opt.votes} vote(s) • ${"%.1f".format(opt.percentage)}%",
                                        color = if (opt.isLeader) DiscordGreen else palette.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.border)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((opt.percentage / 100f).coerceIn(0f, 1f))
                                            .background(if (opt.isLeader) DiscordGreen else DiscordBlurple)
                                    )
                                }
                                if (!stats.isAnonymous && opt.voterNames.isNotEmpty()) {
                                    Text(
                                        text = "Votants (${opt.voterNames.size}) : ${opt.voterNames.joinToString(", ")}",
                                        color = palette.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text("FORMAT D'EXPORTATION", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PollExportFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                label = { Text(fmt.label, fontSize = 12.sp) },
                                modifier = Modifier.testTag("poll_export_fmt_${fmt.name}")
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.inputBg,
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportPayload,
                            color = palette.textLightOrPrimary(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp).testTag("poll_export_preview")
                        )
                    }

                    if (copiedNotice != null) {
                        Text(copiedNotice!!, color = DiscordGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(exportPayload))
                                copiedNotice = "Résultats (${selectedFormat.label}) copiés dans le presse-papiers."
                            },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("poll_export_copy_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copier")
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = selectedFormat.mimeType
                                    putExtra(Intent.EXTRA_SUBJECT, "Résultats Sondage : ${poll.question}")
                                    putExtra(Intent.EXTRA_TEXT, exportPayload)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Exporter les résultats du sondage"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("poll_export_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Partager", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = palette.textMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RavenPalette.textLightOrPrimary(): Color = textPrimary
