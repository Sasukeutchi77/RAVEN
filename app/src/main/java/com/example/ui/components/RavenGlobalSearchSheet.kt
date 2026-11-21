package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RavenGlobalSearchSheet(
    uiState: GlobalSearchUiState,
    activeServerId: String?,
    activeServerName: String,
    isDmTab: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onApplySuggestion: (SearchSuggestion) -> Unit,
    onToggleType: (SearchEntityType) -> Unit,
    onSelectDateRange: (SearchDateRange, Long?, Long?) -> Unit,
    onSetAuthorFilter: (String) -> Unit,
    onSetSortOrder: (SearchSortOrder) -> Unit,
    onSetServerScope: (String?) -> Unit,
    onSetMessageSubFilter: (GlobalSearchFilter) -> Unit,
    onClearFilters: () -> Unit,
    onLoadNextPage: () -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onResultClick: (UnifiedSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var localQuery by remember { mutableStateOf(uiState.querySpec.rawQuery) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showAuthorDialog by remember { mutableStateOf(false) }
    var showCustomDateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.querySpec.rawQuery) {
        if (uiState.querySpec.rawQuery != localQuery) {
            localQuery = uiState.querySpec.rawQuery
        }
    }

    val spec = uiState.querySpec
    val hasActiveFilters = spec.selectedTypes.isNotEmpty() ||
        spec.dateRange != SearchDateRange.ANY_TIME ||
        spec.authorFilter.isNotBlank() ||
        spec.serverScopeId != null ||
        spec.messageSubFilter != GlobalSearchFilter.ALL ||
        spec.sortOrder != SearchSortOrder.RELEVANCE

    val showDiscoveryHome = localQuery.isBlank() && !hasActiveFilters
    val showMessageSubFilters = spec.selectedTypes.isEmpty() || SearchEntityType.MESSAGE in spec.selectedTypes

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("global_search_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.ManageSearch,
                        contentDescription = null,
                        tint = DiscordBlurple,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Recherche Globale RAVEN",
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Accès sécurisé · ${uiState.totalMatchingCount} résultat(s) · ${uiState.tookMs} ms",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                if (!isDmTab && !activeServerId.isNullOrBlank()) {
                    val isScoped = spec.serverScopeId == activeServerId
                    FilterChip(
                        selected = isScoped,
                        onClick = { onSetServerScope(if (isScoped) null else activeServerId) },
                        label = {
                            Text(
                                text = if (isScoped) activeServerName else "Partout",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("global_search_scope_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Instant Search Input
            OutlinedTextField(
                value = localQuery,
                onValueChange = {
                    localQuery = it
                    onQueryChange(it)
                },
                placeholder = {
                    Text(
                        "Rechercher partout (utilisateurs, salons, bots, events, de:auteur, type:event)...",
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = DiscordBlurple
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = DiscordBlurple)
                    }
                },
                trailingIcon = {
                    if (localQuery.isNotBlank()) {
                        IconButton(onClick = {
                            localQuery = ""
                            onQueryChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = palette.textMuted)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitQuery(localQuery) }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputBg,
                    unfocusedContainerColor = palette.inputBg,
                    focusedBorderColor = DiscordBlurple,
                    unfocusedBorderColor = palette.border,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_search_input")
            )

            // Inline operator helper strip
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val operators = listOf(
                    "de:" to "Auteur",
                    "type:event" to "Événements",
                    "type:bot" to "Bots",
                    "depuis:7j" to "7 jours"
                )
                items(operators) { (token, desc) ->
                    AssistChip(
                        onClick = {
                            val updated = if (localQuery.isBlank()) "$token " else "$localQuery $token"
                            localQuery = updated
                            onQueryChange(updated)
                        },
                        label = { Text("$token · $desc", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 10 Domain Types + ALL Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAllSelected = spec.selectedTypes.isEmpty()
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { onToggleType(SearchEntityType.ALL) },
                        label = {
                            val count = uiState.countsByType.values.sum()
                            Text(
                                text = if (count > 0) "Tout ($count)" else "Tout",
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.testTag("search_type_ALL")
                    )
                }
                items(SearchEntityType.domainTypes) { type ->
                    val selected = type in spec.selectedTypes
                    val count = uiState.countsByType[type] ?: 0
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleType(type) },
                        leadingIcon = {
                            Icon(
                                imageVector = searchTypeIcon(type),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (count > 0) "${type.pluralLabel} ($count)" else type.pluralLabel,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.testTag("search_type_${type.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filters & Sort Controls Row (Date, Author, Sort, Reset)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Box {
                        FilterChip(
                            selected = spec.dateRange != SearchDateRange.ANY_TIME,
                            onClick = { showDateMenu = true },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(
                                    text = if (spec.dateRange == SearchDateRange.CUSTOM && spec.customStartMs != null) {
                                        "Depuis ${formatShortDay(spec.customStartMs)}"
                                    } else spec.dateRange.label,
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier.testTag("search_date_chip")
                        )
                        DropdownMenu(
                            expanded = showDateMenu,
                            onDismissRequest = { showDateMenu = false }
                        ) {
                            SearchDateRange.entries.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(range.label) },
                                    onClick = {
                                        showDateMenu = false
                                        if (range == SearchDateRange.CUSTOM) {
                                            showCustomDateDialog = true
                                        } else {
                                            onSelectDateRange(range, null, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    FilterChip(
                        selected = spec.authorFilter.isNotBlank(),
                        onClick = { showAuthorDialog = true },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = {
                            Text(
                                text = if (spec.authorFilter.isNotBlank()) "Auteur : ${spec.authorFilter}" else "Auteur",
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("search_author_filter")
                    )
                }

                item {
                    Box {
                        FilterChip(
                            selected = spec.sortOrder != SearchSortOrder.RELEVANCE,
                            onClick = { showSortMenu = true },
                            leadingIcon = {
                                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text("Tri : ${spec.sortOrder.label}", fontSize = 11.sp)
                            },
                            modifier = Modifier.testTag("search_sort_chip")
                        )
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SearchSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        showSortMenu = false
                                        onSetSortOrder(order)
                                    }
                                )
                            }
                        }
                    }
                }

                if (hasActiveFilters) {
                    item {
                        TextButton(
                            onClick = onClearFilters,
                            modifier = Modifier.testTag("search_reset_filters")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Réinitialiser", fontSize = 11.sp)
                        }
                    }
                }
            }

            if (showMessageSubFilters && !showDiscoveryHome) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GlobalSearchFilter.entries) { sub ->
                        FilterChip(
                            selected = spec.messageSubFilter == sub,
                            onClick = { onSetMessageSubFilter(sub) },
                            label = { Text("Msg · ${sub.label}", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Main Scrollable Body: Suggestions + History + Recents OR Filtered Results + Pagination
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showDiscoveryHome) {
                    // Search History
                    if (uiState.history.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Historique récent",
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = onClearHistory,
                                    modifier = Modifier.testTag("clear_search_history")
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tout effacer", fontSize = 11.sp)
                                }
                            }
                        }
                        items(uiState.history.take(8), key = { "hist_${it.id}" }) { entry ->
                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        localQuery = entry.query
                                        onQueryChange(entry.query)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.query, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        val entryType = SearchEntityType.fromRaw(entry.selectedTypeName)
                                        if (entryType != SearchEntityType.ALL || entry.authorFilter.isNotBlank()) {
                                            Text(
                                                text = listOfNotNull(
                                                    entryType.takeIf { it != SearchEntityType.ALL }?.pluralLabel,
                                                    entry.authorFilter.takeIf { it.isNotBlank() }?.let { "Auteur: $it" }
                                                ).joinToString(" · "),
                                                color = palette.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteHistory(entry.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Supprimer de l'historique", tint = palette.textMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Contextual Suggestions
                    if (uiState.suggestions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Suggestions intelligentes",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.suggestions.take(8), key = { it.id }) { suggestion ->
                                    SuggestionCard(
                                        suggestion = suggestion,
                                        palette = palette,
                                        onClick = {
                                            localQuery = suggestion.queryText
                                            onApplySuggestion(suggestion)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Recent Accessible Results
                    if (uiState.recentResults.isNotEmpty()) {
                        item {
                            Text(
                                text = "Résultats récents",
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(uiState.recentResults, key = { "recent_${it.type.name}_${it.id}" }) { recent ->
                            SearchResultCard(
                                result = recent,
                                palette = palette,
                                onClick = { onResultClick(recent) }
                            )
                        }
                    }
                } else {
                    // Live Suggestions strip while typing
                    if (localQuery.isNotBlank() && uiState.suggestions.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(uiState.suggestions.take(5), key = { "live_${it.id}" }) { sug ->
                                    AssistChip(
                                        onClick = {
                                            localQuery = sug.queryText
                                            onApplySuggestion(sug)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (sug.isHistory) Icons.Default.History else Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = { Text(sug.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.results.isEmpty() && !uiState.isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(44.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Aucun résultat autorisé trouvé",
                                        color = palette.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Essayez d'autres mots-clés ou élargissez vos filtres.",
                                        color = palette.textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.results, key = { "${it.type.name}_${it.id}" }) { result ->
                            SearchResultCard(
                                result = result,
                                palette = palette,
                                onClick = { onResultClick(result) }
                            )
                        }

                        if (uiState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = onLoadNextPage,
                                        enabled = !uiState.isLoadingMore,
                                        modifier = Modifier.testTag("search_load_more_button")
                                    ) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            "Afficher plus (${uiState.results.size}/${uiState.totalMatchingCount})",
                                            fontSize = 13.sp
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

    if (showAuthorDialog) {
        AuthorFilterDialog(
            currentAuthor = spec.authorFilter,
            availableAuthors = uiState.availableAuthors,
            onApply = { author ->
                showAuthorDialog = false
                onSetAuthorFilter(author)
            },
            onDismiss = { showAuthorDialog = false }
        )
    }

    if (showCustomDateDialog) {
        CustomDateRangeDialog(
            onApply = { startMs, endMs ->
                showCustomDateDialog = false
                onSelectDateRange(SearchDateRange.CUSTOM, startMs, endMs)
            },
            onDismiss = { showCustomDateDialog = false }
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SearchSuggestion,
    palette: RavenPalette,
    onClick: () -> Unit
) {
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .widthIn(min = 140.dp, max = 210.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = suggestion.targetType?.let { searchTypeIcon(it) } ?: Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = DiscordBlurple,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = suggestion.label,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = suggestion.subtitle,
                color = palette.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: UnifiedSearchResult,
    palette: RavenPalette,
    onClick: () -> Unit
) {
    val accent = Color(result.accentColorHex)
    Surface(
        color = palette.cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_${result.id}")
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = searchTypeIcon(result.type),
                    contentDescription = result.type.label,
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = result.badgeLabel,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (result.timestampLabel.isNotBlank()) {
                        Text(
                            text = result.timestampLabel,
                            color = palette.textMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.title,
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.subtitle.isNotBlank()) {
                    Text(
                        text = result.subtitle,
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = result.snippet,
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ouvrir",
                tint = palette.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AuthorFilterDialog(
    currentAuthor: String,
    availableAuthors: List<String>,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(currentAuthor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrer par auteur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Nom de l'auteur ou créateur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (availableAuthors.isNotEmpty()) {
                    Text("Auteurs suggérés :", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(availableAuthors.take(10)) { author ->
                            AssistChip(
                                onClick = { draft = author },
                                label = { Text(author, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(draft) }) { Text("Appliquer") }
        },
        dismissButton = {
            Row {
                if (currentAuthor.isNotBlank()) {
                    TextButton(onClick = { onApply("") }) { Text("Effacer") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

@Composable
private fun CustomDateRangeDialog(
    onApply: (Long?, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var daysBackText by remember { mutableStateOf("14") }
    var untilDaysBackText by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plage de dates personnalisée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Définissez une fenêtre relative en jours par rapport à aujourd'hui :",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = daysBackText,
                    onValueChange = { daysBackText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Depuis il y a (jours)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = untilDaysBackText,
                    onValueChange = { untilDaysBackText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Jusqu'à il y a (jours, 0 = maintenant)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val now = System.currentTimeMillis()
                val fromDays = (daysBackText.toLongOrNull() ?: 14L).coerceAtLeast(1L)
                val toDays = (untilDaysBackText.toLongOrNull() ?: 0L).coerceAtLeast(0L)
                val startMs = now - fromDays * 86_400_000L
                val endMs = if (toDays == 0L) now else now - toDays * 86_400_000L
                onApply(minOf(startMs, endMs), maxOf(startMs, endMs))
            }) { Text("Appliquer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

private fun searchTypeIcon(type: SearchEntityType): ImageVector = when (type) {
    SearchEntityType.ALL -> Icons.Default.ManageSearch
    SearchEntityType.USER -> Icons.Default.Person
    SearchEntityType.SERVER -> Icons.Default.Dns
    SearchEntityType.COMMUNITY -> Icons.Default.Public
    SearchEntityType.CHANNEL -> Icons.Default.Tag
    SearchEntityType.MESSAGE -> Icons.Default.Forum
    SearchEntityType.CONVERSATION -> Icons.Default.ChatBubble
    SearchEntityType.EVENT -> Icons.Default.Event
    SearchEntityType.STORY -> Icons.Default.AutoStories
    SearchEntityType.BOT -> Icons.Default.SmartToy
    SearchEntityType.MARKETPLACE -> Icons.Default.Storefront
}

private fun formatShortDay(ms: Long): String =
    SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ms))
