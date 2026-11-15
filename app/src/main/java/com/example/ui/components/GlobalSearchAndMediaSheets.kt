package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.GlobalSearchFilter
import com.example.model.GlobalSearchResult
import com.example.model.Message
import com.example.ui.theme.*
import com.example.util.AudioPlayerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    activeServerName: String,
    isDmTab: Boolean,
    onSearch: (query: String, filter: GlobalSearchFilter, currentServerOnly: Boolean) -> List<GlobalSearchResult>,
    onNavigateToResult: (GlobalSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(GlobalSearchFilter.ALL) }
    var currentServerOnly by remember { mutableStateOf(false) }

    val results = remember(query, selectedFilter, currentServerOnly) {
        onSearch(query, selectedFilter, currentServerOnly)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("global_search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 660.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ManageSearch, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recherche Globale",
                        color = palette.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isDmTab) {
                    FilterChip(
                        selected = currentServerOnly,
                        onClick = { currentServerOnly = !currentServerOnly },
                        label = {
                            Text(
                                text = if (currentServerOnly) activeServerName else "Partout",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("global_search_scope_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text("Mot-clé, auteur, nom de fichier...", color = palette.textMuted, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = DiscordBlurple)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = palette.textMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputBg,
                    unfocusedContainerColor = palette.inputBg,
                    focusedBorderColor = DiscordBlurple,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GlobalSearchFilter.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DiscordBlurple,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("global_search_filter_${filter.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${results.size} résultat(s)",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aucun message correspondant.", color = palette.textMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { "${it.targetId}_${it.message.id}" }) { result ->
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onNavigateToResult(result)
                                    onDismiss()
                                }
                                .testTag("global_search_result_${result.message.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = DiscordBlurple.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${result.serverName} • ${result.targetTitle}",
                                            color = DiscordBlurple,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = result.message.timestamp,
                                        color = palette.textMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = result.message.sender.name,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val snippet = when {
                                    result.message.text.isNotBlank() -> result.message.text
                                    !result.message.attachmentName.isNullOrBlank() -> "Fichier : ${result.message.attachmentName}"
                                    !result.message.imageUrl.isNullOrBlank() -> "Photo partagée"
                                    !result.message.audioUrl.isNullOrBlank() -> "Note vocale (${result.message.audioDurationSec}s)"
                                    else -> "Message"
                                }
                                Text(
                                    text = snippet,
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMediaAndFilesSheet(
    title: String,
    messages: List<Message>,
    audioPlayer: AudioPlayerHelper,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var selectedTab by remember { mutableIntStateOf(0) }

    val imageMessages = remember(messages) { messages.filter { !it.imageUrl.isNullOrBlank() }.reversed() }
    val fileMessages = remember(messages) { messages.filter { !it.attachmentName.isNullOrBlank() }.reversed() }
    val audioMessages = remember(messages) { messages.filter { !it.audioUrl.isNullOrBlank() }.reversed() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("shared_media_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 640.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CollectionsBookmark, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Médias & Fichiers partagés",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = DiscordBlurple,
                divider = { HorizontalDivider(color = palette.border) }
            ) {
                listOf(
                    "Photos (${imageMessages.size})",
                    "Fichiers (${fileMessages.size})",
                    "Vocaux (${audioMessages.size})"
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = label,
                                color = if (selectedTab == index) palette.textPrimary else palette.textMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        if (imageMessages.isEmpty()) {
                            EmptyMediaPlaceholder("Aucune photo partagée ici pour le moment.", Icons.Default.PhotoLibrary)
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(imageMessages, key = { it.id }) { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = palette.cardBg,
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.height(140.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = msg.imageUrl,
                                                contentDescription = "Image de ${msg.sender.name}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "${msg.sender.name} • ${msg.timestamp}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (fileMessages.isEmpty()) {
                            EmptyMediaPlaceholder("Aucun fichier ou document partagé.", Icons.Default.FolderOpen)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(fileMessages, key = { it.id }) { msg ->
                                    Surface(
                                        color = palette.cardBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(DiscordBlurple.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Description, contentDescription = null, tint = DiscordBlurple)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = msg.attachmentName ?: "Fichier",
                                                    color = palette.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${msg.attachmentSizeLabel ?: "Document"} • par ${msg.sender.name} (${msg.timestamp})",
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (audioMessages.isEmpty()) {
                            EmptyMediaPlaceholder("Aucune note vocale partagée.", Icons.Default.GraphicEq)
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(audioMessages, key = { it.id }) { msg ->
                                    var isPlaying by remember { mutableStateOf(false) }
                                    Surface(
                                        color = palette.cardBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, palette.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val path = msg.audioUrl ?: return@IconButton
                                                    if (isPlaying) {
                                                        audioPlayer.stop()
                                                        isPlaying = false
                                                    } else {
                                                        isPlaying = true
                                                        audioPlayer.play(path) { isPlaying = false }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordBlurple)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Arrêter" else "Lire la note vocale",
                                                    tint = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Note vocale de ${msg.sender.name}",
                                                    color = palette.textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Durée ${msg.audioDurationSec}s • ${msg.timestamp}",
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMediaPlaceholder(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val palette = ravenPalette()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text, color = palette.textMuted, fontSize = 13.sp)
        }
    }
}
