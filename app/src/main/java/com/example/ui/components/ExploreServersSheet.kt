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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.model.CommunitySearchFilter
import com.example.model.PublicServerListing
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreServersSheet(
    listings: List<PublicServerListing>,
    joinedServerIds: Set<String>,
    onRefreshCloud: () -> Unit,
    onJoinServer: (PublicServerListing) -> Unit,
    onOpenJoinByCode: () -> Unit,
    onDismiss: () -> Unit,
    onSearchCloud: (suspend (String, CommunitySearchFilter) -> List<PublicServerListing>)? = null
) {
    val palette = ravenPalette()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tout") }
    var communityFilter by remember { mutableStateOf(CommunitySearchFilter.ALL) }
    var cloudSearchResults by remember { mutableStateOf<List<PublicServerListing>?>(null) }

    val activeSource = remember(listings, cloudSearchResults, searchQuery, communityFilter) {
        if (onSearchCloud != null && cloudSearchResults != null && (searchQuery.isNotBlank() || communityFilter != CommunitySearchFilter.ALL)) {
            cloudSearchResults!!
        } else listings
    }

    val categories = remember(activeSource) {
        listOf("Tout") + activeSource.map { it.categoryTag }.distinct()
    }

    val filtered = remember(activeSource, searchQuery, selectedCategory, communityFilter, joinedServerIds) {
        activeSource.filter { item ->
            val catMatch = selectedCategory == "Tout" || item.categoryTag.equals(selectedCategory, ignoreCase = true)
            val filterMatch = when (communityFilter) {
                CommunitySearchFilter.ALL, CommunitySearchFilter.PUBLIC_ONLY -> true
                CommunitySearchFilter.VERIFIED -> item.isVerified
                CommunitySearchFilter.MY_COMMUNITIES -> item.id in joinedServerIds
            }
            val qMatch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.categoryTag.contains(searchQuery, ignoreCase = true) ||
                item.inviteCode.contains(searchQuery, ignoreCase = true)
            catMatch && filterMatch && qMatch
        }
    }

    LaunchedEffect(Unit) {
        onRefreshCloud()
    }

    LaunchedEffect(searchQuery, communityFilter) {
        if (onSearchCloud != null) {
            delay(180)
            cloudSearchResults = onSearchCloud(searchQuery, communityFilter)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("explore_servers_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 440.dp, max = 660.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(palette.accent, Color(0xFF00D4AA)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Découvrir les communautés",
                            color = palette.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Serveurs publics vérifiés • Rejoindre en 1 clic",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenJoinByCode()
                    },
                    modifier = Modifier.testTag("explore_open_code_button")
                ) {
                    Text("Code", color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un serveur, jeu, thème...", color = palette.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = palette.textMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", tint = palette.textMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputBg,
                    unfocusedContainerColor = palette.inputBg,
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    listOf(
                        CommunitySearchFilter.ALL,
                        CommunitySearchFilter.VERIFIED,
                        CommunitySearchFilter.MY_COMMUNITIES
                    )
                ) { f ->
                    FilterChip(
                        selected = communityFilter == f,
                        onClick = { communityFilter = f },
                        label = { Text(f.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("explore_filter_${f.name}")
                    )
                }
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.accent.copy(alpha = 0.22f),
                            selectedLabelColor = palette.accent
                        ),
                        modifier = Modifier.testTag("explore_cat_$category")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filtered, key = { it.id }) { listing ->
                    val alreadyJoined = listing.id in joinedServerIds
                    val bgColor = Color(listing.iconBgColor)
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (alreadyJoined) DiscordGreen.copy(alpha = 0.5f) else palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_card_${listing.id}")
                    ) {
                        Column {
                            // Mini Gradient Header
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(bgColor, bgColor.copy(alpha = 0.55f), palette.cardBg)
                                        )
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(bgColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val iconKeys = listOf("game", "code", "rocket", "star", "shield", "bolt", "headset")
                                            if (iconKeys.contains(listing.iconText.lowercase())) {
                                                AppStatusIcon(iconKey = listing.iconText, size = 20.dp, tint = Color.White)
                                            } else {
                                                Text(
                                                    text = listing.iconText.take(2),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = listing.name,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (listing.isVerified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(
                                                        Icons.Default.Verified,
                                                        contentDescription = "Serveur vérifié",
                                                        tint = Color(0xFF00D4AA),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${listing.categoryTag} • Code ${listing.inviteCode}",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    if (listing.boostLevel > 0) {
                                        Surface(
                                            color = Color(0xFFEB459E).copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.RocketLaunch,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEB459E),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Niv.${listing.boostLevel}",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = listing.description,
                                    color = palette.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(DiscordGreen)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${listing.memberCount} membres",
                                            color = palette.textMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onJoinServer(listing)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (alreadyJoined) DiscordGreen else palette.accent
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("explore_join_${listing.id}")
                                    ) {
                                        Text(
                                            text = if (alreadyJoined) "Ouvrir" else "Rejoindre",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
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
