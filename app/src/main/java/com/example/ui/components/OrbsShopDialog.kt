package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.SoundFxHelper
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbsShopDialog(
    user: User,
    quests: List<Quest> = ShopCatalog.initialQuests,
    dailyStreak: Int = 3,
    streakClaimedToday: Boolean = false,
    onClaimQuest: (String) -> Int = { 0 },
    onCompleteQuestAction: (String) -> Unit = {},
    onClaimDailyStreak: () -> Int = { 0 },
    onDismiss: () -> Unit,
    onUpdateOrbsBalance: (Int) -> Unit,
    onEquipDecoration: (String?) -> Unit,
    onUnlockBadge: (String) -> Unit,
    onEquipProfileEffect: (String?) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(ShopCategory.ALL) }
    var userOrbs by remember(user.orbsBalance) { mutableIntStateOf(user.orbsBalance) }
    var equippedDeco by remember { mutableStateOf(user.avatarDecoration) }
    var tryOnDeco by remember { mutableStateOf(user.avatarDecoration) }
    var showTryOnStudio by remember { mutableStateOf(false) }

    var shopItems by remember {
        mutableStateOf(ShopCatalog.initialShopItems.map { item ->
            val isAvatarDeco = item.category in listOf(
                ShopCategory.AVATAR_DECORATION,
                ShopCategory.ELEMENTAL,
                ShopCategory.ANIME_SAKURA,
                ShopCategory.CYBER_MECHA,
                ShopCategory.DIVINE_WINGS
            )
            if (isAvatarDeco && item.decorationKey == user.avatarDecoration) {
                item.copy(isPurchased = true, isEquipped = true)
            } else if (item.category == ShopCategory.PROFILE_EFFECT && item.decorationKey == user.profileEffect) {
                item.copy(isPurchased = true, isEquipped = true)
            } else {
                item
            }
        })
    }

    var showQuestsSheet by remember { mutableStateOf(false) }
    var showWheelDialog by remember { mutableStateOf(false) }
    var selectedItemDetail by remember { mutableStateOf<ShopItem?>(null) }
    var showConfetti by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DiscordDarkPrimary
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Navigation Bar matching Screenshot 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DiscordDarkSidebar)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Close button with notification badge '4'
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = DiscordTextLight)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(DiscordRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("4", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = DiscordTextWhite, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Orbs",
                                color = DiscordTextWhite,
                                fontSize = 18.sp,
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

                            Spacer(modifier = Modifier.width(6.dp))

                            // Heart Favorites button
                            IconButton(
                                onClick = { showFavoritesOnly = !showFavoritesOnly },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favoris",
                                    tint = if (showFavoritesOnly) DiscordRed else DiscordTextLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Daily Wheel of Fortune button
                            IconButton(
                                onClick = { showWheelDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Roue de la chance",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Hero Banner matching Screenshot 1 with Floating 3D Crystal Orb
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF2E1065),
                                        Color(0xFF1E1035),
                                        DiscordDarkPrimary
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DISCORD ORBS",
                                    color = DiscordTextWhite,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Earn through Quests. Spend in the Shop.",
                                    color = DiscordTextLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showQuestsSheet = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkCard),
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DiscordDarkBorder),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Obtiens des Orbs", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = { showTryOnStudio = !showTryOnStudio },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (showTryOnStudio) DiscordBlurple else DiscordDarkInput),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Studio Essayage", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            // 3D Faceted Crystal Gem in Canvas (matching screenshot 1 right side)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable { showWheelDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    val r = size.width * 0.42f

                                    // Faceted 3D diamond path
                                    val topPath = Path().apply {
                                        moveTo(c.x, c.y - r)
                                        lineTo(c.x + r * 0.85f, c.y - r * 0.2f)
                                        lineTo(c.x, c.y)
                                        close()
                                    }
                                    drawPath(topPath, color = Color(0xFFC084FC))

                                    val leftPath = Path().apply {
                                        moveTo(c.x, c.y - r)
                                        lineTo(c.x - r * 0.85f, c.y - r * 0.2f)
                                        lineTo(c.x, c.y)
                                        close()
                                    }
                                    drawPath(leftPath, color = Color(0xFF818CF8))

                                    val bottomPath = Path().apply {
                                        moveTo(c.x - r * 0.85f, c.y - r * 0.2f)
                                        lineTo(c.x, c.y + r)
                                        lineTo(c.x + r * 0.85f, c.y - r * 0.2f)
                                        lineTo(c.x, c.y)
                                        close()
                                    }
                                    drawPath(bottomPath, color = Color(0xFFE879F9))

                                    // Starburst center glint
                                    drawCircle(color = Color.White, radius = 4f, center = Offset(c.x - r * 0.2f, c.y - r * 0.3f))
                                }
                            }
                        }
                    }

                    // Interactive Try-On Studio Card (Live Preview on User Avatar)
                    AnimatedVisibility(visible = showTryOnStudio) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = DiscordDarkCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "STUDIO D'ESSAYAGE EN DIRECT",
                                            color = DiscordTextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(onClick = { showTryOnStudio = false }, contentPadding = PaddingValues(0.dp)) {
                                        Text("Fermer", color = DiscordTextMuted, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // User Avatar Preview with tryOnDeco
                                    Box(
                                        modifier = Modifier.size(60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(user.avatarColor)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!user.avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = user.avatarUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            }
                                        }
                                        AvatarDecorationOverlay(
                                            decorationKey = tryOnDeco,
                                            size = 48.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.name, color = DiscordTextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            text = "Aperçu : ${tryOnDeco?.replace("_", " ")?.uppercase() ?: "Aucune décoration"}",
                                            color = DiscordTextMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            equippedDeco = tryOnDeco
                                            onEquipDecoration(tryOnDeco)
                                            SoundFxHelper.playChime()
                                            showConfetti = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Appliquer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick try-on selector chips
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val decorKeys = listOf(
                                        null to "Sans effet",
                                        "phoenix_flames" to "Phénix Feu 🔥",
                                        "lightning_storm" to "Foudre Tempête ⚡",
                                        "sakura_blossom" to "Sakura Céleste 🌸",
                                        "cyber_mecha" to "Casque Mecha 🤖",
                                        "astral_dragon" to "Dragon Astral 🐉",
                                        "black_hole" to "Trou Noir 🌌",
                                        "glitch_matrix" to "Glitch Matrix 👾",
                                        "rgb_headset" to "Casque Gamer 🎧",
                                        "archangel_wings" to "Ailes d'Archange 🪽",
                                        "royal_crown" to "Couronne Royale 👑",
                                        "aurora_frost" to "Givre Boréal ❄️",
                                        "spectral_soul" to "Âme Émeraude 👻",
                                        "magic_mist" to "Brouillard Magique ✨",
                                        "infinity_swirl" to "Tourbillon Infini 🌀",
                                        "raven_wings" to "Ailes du Corbeau 🦅",
                                        "cyber_halo" to "Couronne Cyber 🌐"
                                    )
                                    items(decorKeys) { (key, label) ->
                                        val isChosen = tryOnDeco == key
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                            border = if (isChosen) androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple) else null,
                                            modifier = Modifier
                                                .clickable { tryOnDeco = key }
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isChosen) Color.White else DiscordTextLight,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
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
                    val filteredItems = remember(shopItems, selectedCategory, showFavoritesOnly) {
                        var list = if (selectedCategory == ShopCategory.ALL) shopItems else shopItems.filter { it.category == selectedCategory }
                        if (showFavoritesOnly) list = list.filter { it.isFavorite }
                        list
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
                                userAvatarColor = user.avatarColor,
                                onFavoriteToggle = {
                                    shopItems = shopItems.map {
                                        if (it.id == item.id) it.copy(isFavorite = !it.isFavorite) else it
                                    }
                                },
                                onClick = {
                                    selectedItemDetail = item
                                    val isDeco = item.category in listOf(
                                        ShopCategory.AVATAR_DECORATION,
                                        ShopCategory.ELEMENTAL,
                                        ShopCategory.ANIME_SAKURA,
                                        ShopCategory.CYBER_MECHA,
                                        ShopCategory.DIVINE_WINGS
                                    )
                                    if (isDeco) {
                                        tryOnDeco = item.decorationKey
                                        showTryOnStudio = true
                                    }
                                },
                                onActionClick = {
                                    val isDeco = item.category in listOf(
                                        ShopCategory.AVATAR_DECORATION,
                                        ShopCategory.ELEMENTAL,
                                        ShopCategory.ANIME_SAKURA,
                                        ShopCategory.CYBER_MECHA,
                                        ShopCategory.DIVINE_WINGS
                                    )
                                    if (item.isPurchased) {
                                        // Toggle equip
                                        val newEquipped = !item.isEquipped
                                        shopItems = shopItems.map {
                                            if (it.id == item.id) it.copy(isEquipped = newEquipped)
                                            else if (it.category == item.category && newEquipped) it.copy(isEquipped = false)
                                            else it
                                        }
                                        if (isDeco) {
                                            val active = if (newEquipped) item.decorationKey else null
                                            equippedDeco = active
                                            onEquipDecoration(active)
                                        } else if (item.category == ShopCategory.PROFILE_EFFECT) {
                                            val active = if (newEquipped) item.decorationKey else null
                                            onEquipProfileEffect(active)
                                        }
                                        SoundFxHelper.playChime()
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
                                            if (isDeco) {
                                                equippedDeco = item.decorationKey
                                                onEquipDecoration(item.decorationKey)
                                            } else if (item.category == ShopCategory.PROFILE_EFFECT) {
                                                onEquipProfileEffect(item.decorationKey)
                                            } else if (item.category == ShopCategory.BADGES) {
                                                onUnlockBadge("ORBS_NEOPHYTE")
                                            }
                                            SoundFxHelper.playChime()
                                            showConfetti = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Confetti particle burst overlay
                ConfettiEffect(
                    trigger = showConfetti,
                    onAnimationEnd = { showConfetti = false }
                )
            }
        }
    }

    // Daily Lucky Wheel of Fortune Dialog
    if (showWheelDialog) {
        DailyOrbsWheelDialog(
            onDismiss = { showWheelDialog = false },
            onWinOrbs = { reward ->
                userOrbs += reward
                onUpdateOrbsBalance(userOrbs)
                showConfetti = true
            }
        )
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
                    Column {
                        Text(
                            text = "Quêtes & Défis Orbs",
                            color = DiscordTextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Complétez les actions pour remporter des Orbs instantanément.",
                            color = DiscordTextMuted,
                            fontSize = 11.sp
                        )
                    }

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

                Spacer(modifier = Modifier.height(12.dp))

                // Daily Streak Banner Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DiscordBlurple.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Série quotidienne : Jour $dailyStreak",
                                    color = DiscordTextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (streakClaimedToday) "Bonus d'aujourd'hui déjà réclamé !" else "Réclamez votre bonus quotidien (+Orbs & +30 XP)",
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val gained = onClaimDailyStreak()
                                if (gained > 0) showConfetti = true
                            },
                            enabled = !streakClaimedToday,
                            colors = ButtonDefaults.buttonColors(containerColor = if (streakClaimedToday) DiscordDarkInput else DiscordAmber),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("claim_daily_streak_button")
                        ) {
                            Text(
                                text = if (streakClaimedToday) "Réclamé" else "Réclamer",
                                color = if (streakClaimedToday) DiscordTextMuted else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                                    progress = { quest.currentProgress.toFloat() / quest.maxProgress.coerceAtLeast(1) },
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
                                        val gained = onClaimQuest(quest.id)
                                        if (gained > 0) showConfetti = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp).testTag("claim_quest_${quest.id}")
                                ) {
                                    Text("Réclamer +${quest.rewardOrbs}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (quest.isClaimed) {
                                Text("Réclamé", color = DiscordTextDark, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Button(
                                    onClick = { onCompleteQuestAction(quest.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("complete_quest_${quest.id}")
                                ) {
                                    Text(quest.actionLabel, color = Color.White, fontSize = 10.sp)
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
    userAvatarColor: Long,
    onFavoriteToggle: () -> Unit,
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

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (item.isFavorite) DiscordRed else DiscordTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Specialized Visual Preview based on item type (matching screenshot 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Profile Banner Effect Live Animated Preview
                    item.category == ShopCategory.PROFILE_EFFECT -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(72.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1F22)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                ProfileEffectOverlay(
                                    effectKey = item.decorationKey,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Effet de bannière",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Chat Wallpaper Theme Preview (Matching Screenshot 1 Card 4 & 6)
                    item.category == ShopCategory.CHAT_THEME -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(72.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (item.id.contains("infinity")) Color(0xFF132F4C) else Color(0xFF28133F)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Background cosmic wave gradient
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val bgBrush = Brush.linearGradient(
                                        listOf(
                                            if (item.id.contains("infinity")) Color(0xFF0284C7) else Color(0xFF9333EA),
                                            if (item.id.contains("infinity")) Color(0xFFEC4899) else Color(0xFF06B6D4)
                                        )
                                    )
                                    drawRect(brush = bgBrush, alpha = 0.55f)
                                }

                                // Mockup chat message elements
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.8f))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(44.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color.White.copy(alpha = 0.6f))
                                        )
                                    }

                                    // Chat bubble mockup
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(DiscordBlurple)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(55.dp)
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.White.copy(alpha = 0.7f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3D Nitro Credits Coin (Matching Screenshot 1 Card 1)
                    item.decorationKey == "nitro_credits" -> {
                        Canvas(modifier = Modifier.size(54.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.width * 0.4f

                            // Glowing magenta aura
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFEC4899).copy(alpha = 0.6f), Color.Transparent),
                                    center = c,
                                    radius = r * 1.5f
                                ),
                                radius = r * 1.5f,
                                center = c
                            )

                            // 3D coin stack base
                            for (step in 3 downTo 0) {
                                val yOff = step * 3.5f
                                drawOval(
                                    color = if (step == 0) Color(0xFF6366F1) else Color(0xFF3730A3),
                                    topLeft = Offset(c.x - r, c.y - r * 0.65f + yOff),
                                    size = Size(r * 2f, r * 1.3f)
                                )
                            }

                            // Nitro logo symbol 'N' / sparkle
                            drawCircle(color = Color(0xFFF472B6), radius = r * 0.4f, center = Offset(c.x, c.y - 2f))
                            drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(c.x, c.y - 2f))
                        }
                    }

                    // 3D Prismatic Star Badge (Matching Screenshot 1 Card 2)
                    item.decorationKey == "badge_orbs" -> {
                        Canvas(modifier = Modifier.size(54.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val r = size.width * 0.45f

                            // Outer 4-point star facets
                            val starPath = Path().apply {
                                moveTo(c.x, c.y - r)
                                lineTo(c.x + r * 0.35f, c.y - r * 0.35f)
                                lineTo(c.x + r, c.y)
                                lineTo(c.x + r * 0.35f, c.y + r * 0.35f)
                                lineTo(c.x, c.y + r)
                                lineTo(c.x - r * 0.35f, c.y + r * 0.35f)
                                lineTo(c.x - r, c.y)
                                lineTo(c.x - r * 0.35f, c.y - r * 0.35f)
                                close()
                            }
                            drawPath(
                                starPath,
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFFEC4899), Color(0xFF38BDF8))
                                )
                            )

                            // Inner core star
                            drawCircle(color = Color(0xFF0F172A), radius = r * 0.42f, center = c)
                            drawCircle(color = Color.White, radius = r * 0.22f, center = c)
                        }
                    }

                    // Pack Collector (Matching Screenshot 1 Card 3)
                    item.id == "bundle_swirl_pack" -> {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(64.dp)) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(
                                    brush = Brush.radialGradient(listOf(Color(0xFFEC4899).copy(alpha = 0.5f), Color.Transparent)),
                                    radius = size.width * 0.48f,
                                    center = c
                                )
                            }
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(34.dp))
                        }
                    }

                    // Avatar Decoration Preview (Matching Screenshot 1 Card 5 & 7)
                    else -> {
                        Box(contentAlignment = Alignment.Center) {
                            // Clyde / Raven Base Avatar
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2B2D31)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = DiscordTextLight,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Dynamic Animated Canvas Decoration Overlay!
                            AvatarDecorationOverlay(
                                decorationKey = item.decorationKey,
                                size = 46.dp
                            )
                        }
                    }
                }
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
                text = item.subtitle.ifBlank { item.description },
                color = DiscordTextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
                modifier = Modifier.padding(vertical = 1.dp)
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
