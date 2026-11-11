package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.BadgeRarity
import com.example.model.SuperBadge
import com.example.model.SuperBadgesCatalog
import com.example.ui.theme.*

@Composable
fun SuperBadgesDialog(
    equippedBadgeIds: List<String>,
    onDismiss: () -> Unit,
    onToggleEquipBadge: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Tous") }
    var selectedBadgeDetail by remember { mutableStateOf<SuperBadge?>(null) }

    val badges = remember(equippedBadgeIds) {
        SuperBadgesCatalog.allBadges.map { badge ->
            badge.copy(isEquipped = equippedBadgeIds.contains(badge.id))
        }
    }

    val filteredBadges = remember(badges, selectedFilter) {
        when (selectedFilter) {
            "Équipés" -> badges.filter { it.isEquipped }
            "Légendaires & Rares" -> badges.filter { it.rarity == BadgeRarity.LEGENDARY || it.rarity == BadgeRarity.EPIC }
            "À débloquer" -> badges.filter { !it.isUnlocked }
            else -> badges
        }
    }

    val unlockedCount = badges.count { it.isUnlocked }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp)),
            color = DiscordDarkPrimary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkSidebar)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(DiscordBlurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = DiscordBlurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Vitrine des Super Badges",
                                color = DiscordTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$unlockedCount sur ${badges.size} badges débloqués",
                                color = DiscordGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextLight)
                    }
                }

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkSidebar)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf("Tous", "Équipés", "Légendaires & Rares", "À débloquer")
                    items(filters) { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DiscordBlurple else DiscordDarkInput,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) Color.White else DiscordTextLight,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = DiscordDarkBorder)

                // Badges List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBadges, key = { it.id }) { badge ->
                        SuperBadgeCard(
                            badge = badge,
                            onClick = { selectedBadgeDetail = badge },
                            onToggleEquip = { onToggleEquipBadge(badge.id) }
                        )
                    }
                }
            }
        }
    }

    // Detail Modal for a specific badge
    selectedBadgeDetail?.let { badge ->
        AlertDialog(
            onDismissRequest = { selectedBadgeDetail = null },
            containerColor = DiscordDarkCard,
            icon = {
                SuperBadgeMedallion(
                    iconKey = badge.iconKey,
                    rarity = badge.rarity,
                    size = 54.dp,
                    iconSize = 28.dp
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = badge.title,
                        color = DiscordTextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(badge.rarity.color).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge.rarity.label.uppercase(),
                            color = Color(badge.rarity.color),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = badge.subtitle,
                        color = DiscordTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = badge.description,
                        color = DiscordTextLight,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Statut : ${if (badge.isUnlocked) "Obtenu le ${badge.unlockedDate}" else "Verrouillé - À accomplir"}",
                        color = if (badge.isUnlocked) DiscordGreen else DiscordTextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                if (badge.isUnlocked) {
                    Button(
                        onClick = {
                            onToggleEquipBadge(badge.id)
                            selectedBadgeDetail = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (badge.isEquipped) DiscordDarkInput else DiscordBlurple
                        )
                    ) {
                        Text(
                            text = if (badge.isEquipped) "Retirer du profil" else "Afficher sur le profil",
                            color = Color.White
                        )
                    }
                } else {
                    TextButton(onClick = { selectedBadgeDetail = null }) {
                        Text("Fermer", color = DiscordTextMuted)
                    }
                }
            },
            dismissButton = {
                if (badge.isUnlocked) {
                    TextButton(onClick = { selectedBadgeDetail = null }) {
                        Text("Fermer", color = DiscordTextMuted)
                    }
                }
            }
        )
    }
}

@Composable
private fun SuperBadgeCard(
    badge: SuperBadge,
    onClick: () -> Unit,
    onToggleEquip: () -> Unit
) {
    val rarityColor = Color(badge.rarity.color)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (badge.isEquipped) 1.5.dp else 1.dp,
                color = if (badge.isEquipped) rarityColor.copy(alpha = 0.8f) else DiscordDarkBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("super_badge_${badge.id}"),
        color = DiscordDarkCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuperBadgeMedallion(
                iconKey = badge.iconKey,
                rarity = badge.rarity,
                size = 44.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = badge.title,
                        color = if (badge.isUnlocked) DiscordTextWhite else DiscordTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = rarityColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge.rarity.label,
                            color = rarityColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = badge.subtitle,
                    color = DiscordTextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (badge.isUnlocked) {
                IconButton(
                    onClick = onToggleEquip,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (badge.isEquipped) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        contentDescription = if (badge.isEquipped) "Équipé" else "Équiper",
                        tint = if (badge.isEquipped) DiscordBlurple else DiscordTextDark
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Verrouillé",
                    tint = DiscordTextDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
