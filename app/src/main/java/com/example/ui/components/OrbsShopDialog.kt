package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbsShopDialog(
    user: User,
    onDismiss: () -> Unit,
    onUpdateOrbsBalance: (Int) -> Unit,
    onEquipDecoration: (String?) -> Unit,
    onUnlockBadge: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(ShopCategory.ALL) }
    var userOrbs by remember { mutableIntStateOf(user.orbsBalance) }
    var equippedDeco by remember { mutableStateOf(user.avatarDecoration) }

    var shopItems by remember {
        mutableStateOf(ShopCatalog.initialShopItems.map { item ->
            if (item.category == ShopCategory.AVATAR_DECORATION && item.decorationKey == user.avatarDecoration) {
                item.copy(isPurchased = true, isEquipped = true)
            } else {
                item
            }
        })
    }

    var quests by remember { mutableStateOf(ShopCatalog.initialQuests) }
    var showQuestsSheet by remember { mutableStateOf(false) }
    var selectedItemDetail by remember { mutableStateOf<ShopItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = DiscordDarkPrimary
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Navigation Bar like screenshot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiscordDarkSidebar)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextLight)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Storefront, contentDescription = null, tint = DiscordTextWhite, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Orbs & Boutique",
                            color = DiscordTextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Orbs Balance Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DiscordDarkInput,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { showQuestsSheet = true }
                                .testTag("orbs_balance_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Diamond,
                                    contentDescription = "Orbs",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$userOrbs",
                                    color = DiscordTextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { showQuestsSheet = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Assignment, contentDescription = "Quêtes", tint = DiscordTextLight)
                        }
                    }
                }

                // Hero Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2E1065),
                                    DiscordDarkPrimary
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Column {
                        Text(
                            text = "RAVEN ORBS",
                            color = DiscordTextWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gagnez des Orbs via les quêtes. Dépensez-les dans la boutique.",
                            color = DiscordTextLight,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showQuestsSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkCard),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Obtiens des Orbs", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Categories Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ShopCategory.values()) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DiscordBlurple else DiscordDarkInput,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.title,
                                color = if (isSelected) Color.White else DiscordTextLight,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Shop Items Grid
                val filteredItems = remember(shopItems, selectedCategory) {
                    if (selectedCategory == ShopCategory.ALL) shopItems
                    else shopItems.filter { it.category == selectedCategory }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp, top = 4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ShopItemCard(
                            item = item,
                            onClick = { selectedItemDetail = item },
                            onActionClick = {
                                if (item.isPurchased) {
                                    // Toggle equip
                                    val newEquipped = !item.isEquipped
                                    shopItems = shopItems.map {
                                        if (it.id == item.id) it.copy(isEquipped = newEquipped)
                                        else if (it.category == item.category && newEquipped) it.copy(isEquipped = false)
                                        else it
                                    }
                                    if (item.category == ShopCategory.AVATAR_DECORATION) {
                                        val active = if (newEquipped) item.decorationKey else null
                                        equippedDeco = active
                                        onEquipDecoration(active)
                                    }
                                } else {
                                    // Purchase with Orbs
                                    if (userOrbs >= item.priceOrbs) {
                                        userOrbs -= item.priceOrbs
                                        onUpdateOrbsBalance(userOrbs)
                                        shopItems = shopItems.map {
                                            if (it.id == item.id) it.copy(isPurchased = true, isEquipped = true)
                                            else if (it.category == item.category) it.copy(isEquipped = false)
                                            else it
                                        }
                                        if (item.category == ShopCategory.AVATAR_DECORATION) {
                                            equippedDeco = item.decorationKey
                                            onEquipDecoration(item.decorationKey)
                                        } else if (item.category == ShopCategory.BADGES) {
                                            onUnlockBadge("ORBS_NEOPHYTE")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Quests Bottom Sheet
    if (showQuestsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuestsSheet = false },
            containerColor = DiscordDarkSidebar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quêtes Raven Orbs",
                        color = DiscordTextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DiscordDarkInput
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$userOrbs Orbs", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                quests.forEach { quest ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DiscordDarkCard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quest.title,
                                    color = DiscordTextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = quest.description,
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { quest.currentProgress.toFloat() / quest.maxProgress },
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = DiscordBlurple,
                                    trackColor = DiscordDarkInput
                                )
                            }

                            if (quest.isCompleted && !quest.isClaimed) {
                                Button(
                                    onClick = {
                                        userOrbs += quest.rewardOrbs
                                        onUpdateOrbsBalance(userOrbs)
                                        quests = quests.map {
                                            if (it.id == quest.id) it.copy(isClaimed = true) else it
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+${quest.rewardOrbs} ⬢", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (quest.isClaimed) {
                                Text("Réclamé", color = DiscordTextDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Surface(
                                    color = DiscordDarkInput,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "+${quest.rewardOrbs} Orbs",
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun ShopItemCard(
    item: ShopItem,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (item.isEquipped) 2.dp else 1.dp,
                color = if (item.isEquipped) DiscordBlurple else DiscordDarkBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        color = DiscordDarkCard
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Card Top Row: Orbs price tag & Favorite icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DiscordDarkInput
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${item.priceOrbs}",
                            color = DiscordTextWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = DiscordTextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Avatar Decoration Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Base Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF35373C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = DiscordTextLight,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Decorative Ring Overlay
                AvatarDecorationOverlay(
                    decorationKey = item.decorationKey,
                    size = 46.dp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.title,
                color = DiscordTextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.description,
                color = DiscordTextMuted,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Button
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        item.isEquipped -> DiscordDarkInput
                        item.isPurchased -> DiscordBlurple
                        else -> DiscordBlurple
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                Text(
                    text = when {
                        item.isEquipped -> "Équipé"
                        item.isPurchased -> "Équiper"
                        else -> "Acheter"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
