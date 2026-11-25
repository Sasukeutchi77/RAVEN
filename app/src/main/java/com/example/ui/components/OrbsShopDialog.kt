package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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

private enum class ShopSortMode(val label: String) {
    FEATURED("En vedette"),
    NEWEST("Nouveautés"),
    PRICE_ASC("Prix ↑"),
    PRICE_DESC("Prix ↓"),
    RARITY("Rareté"),
    OWNED("Mes achats")
}

private enum class StudioTab(val label: String) {
    DECORATIONS("Décorations"),
    EFFECTS("Effets Profil"),
    THEMES("Thèmes Chat")
}

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
    onEquipProfileEffect: (String?) -> Unit = {},
    onEquipChatTheme: (String?) -> Unit = {},
    onPurchaseShopItem: (ShopItem) -> Boolean = { false },
    onToggleFavoriteItem: (String) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(ShopCategory.ALL) }
    var sortMode by remember { mutableStateOf(ShopSortMode.FEATURED) }
    var searchQuery by remember { mutableStateOf("") }
    var userOrbs by remember(user.orbsBalance) { mutableIntStateOf(user.orbsBalance) }
    var equippedDeco by remember(user.avatarDecoration) { mutableStateOf(user.avatarDecoration) }
    var equippedEffect by remember(user.profileEffect) { mutableStateOf(user.profileEffect) }
    var equippedTheme by remember(user.equippedChatThemeId) { mutableStateOf(user.equippedChatThemeId) }

    var tryOnDeco by remember { mutableStateOf(user.avatarDecoration) }
    var tryOnEffect by remember { mutableStateOf(user.profileEffect) }
    var tryOnTheme by remember { mutableStateOf(user.equippedChatThemeId ?: "nebula") }
    var showTryOnStudio by remember { mutableStateOf(false) }
    var studioTab by remember { mutableStateOf(StudioTab.DECORATIONS) }

    var localPurchasedIds by remember(user.purchasedShopItemIds) {
        mutableStateOf(user.purchasedShopItemIds.toSet())
    }
    var localFavoriteIds by remember(user.favoriteShopItemIds) {
        mutableStateOf(user.favoriteShopItemIds.toSet())
    }

    val shopItems = remember(
        localPurchasedIds,
        localFavoriteIds,
        equippedDeco,
        equippedEffect,
        equippedTheme,
        user.badges
    ) {
        ShopCatalog.initialShopItems.map { item ->
            val isOwned = item.isPurchased || localPurchasedIds.contains(item.id)
            val isDeco = item.category in ShopCatalog.avatarDecorationCategories
            val isEquippedNow = when {
                isDeco -> item.decorationKey == equippedDeco
                item.category == ShopCategory.PROFILE_EFFECT -> item.decorationKey == equippedEffect
                item.category == ShopCategory.CHAT_THEME -> item.decorationKey == equippedTheme
                item.badgeId != null && item.category == ShopCategory.BADGES -> user.badges.contains(item.badgeId)
                else -> isOwned && item.bundleItemIds.isNotEmpty() &&
                    item.bundleItemIds.all { bId ->
                        val child = ShopCatalog.initialShopItems.find { it.id == bId }
                        child != null && (
                            (child.category in ShopCatalog.avatarDecorationCategories && child.decorationKey == equippedDeco) ||
                                (child.category == ShopCategory.PROFILE_EFFECT && child.decorationKey == equippedEffect) ||
                                (child.category == ShopCategory.CHAT_THEME && child.decorationKey == equippedTheme) ||
                                (child.badgeId != null && user.badges.contains(child.badgeId))
                            )
                    }
            }
            item.copy(
                isPurchased = isOwned,
                isEquipped = isEquippedNow,
                isFavorite = localFavoriteIds.contains(item.id)
            )
        }
    }

    var showQuestsSheet by remember { mutableStateOf(false) }
    var showWheelDialog by remember { mutableStateOf(false) }
    var selectedItemDetail by remember { mutableStateOf<ShopItem?>(null) }
    var showConfetti by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val unclaimedRewardsCount = remember(quests, streakClaimedToday) {
        quests.count { it.isCompleted && !it.isClaimed } + if (!streakClaimedToday) 1 else 0
    }

    val heroTransition = rememberInfiniteTransition(label = "hero_orb_float")
    val orbFloatY by heroTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbFloatY"
    )
    val orbPulse by heroTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    fun handleItemAction(item: ShopItem) {
        val isDeco = item.category in ShopCatalog.avatarDecorationCategories
        if (item.isPurchased) {
            when {
                isDeco -> {
                    val active = if (item.isEquipped) null else item.decorationKey
                    equippedDeco = active
                    onEquipDecoration(active)
                }
                item.category == ShopCategory.PROFILE_EFFECT -> {
                    val active = if (item.isEquipped) null else item.decorationKey
                    equippedEffect = active
                    onEquipProfileEffect(active)
                }
                item.category == ShopCategory.CHAT_THEME -> {
                    val active = if (item.isEquipped) null else item.decorationKey
                    equippedTheme = active
                    onEquipChatTheme(active)
                }
                item.category == ShopCategory.BADGES && item.badgeId != null -> {
                    onUnlockBadge(item.badgeId)
                }
                item.bundleItemIds.isNotEmpty() -> {
                    val decoChild = item.bundleItemIds.firstNotNullOfOrNull { bId ->
                        ShopCatalog.initialShopItems.find { it.id == bId && it.category in ShopCatalog.avatarDecorationCategories }
                    }
                    val effChild = item.bundleItemIds.firstNotNullOfOrNull { bId ->
                        ShopCatalog.initialShopItems.find { it.id == bId && it.category == ShopCategory.PROFILE_EFFECT }
                    }
                    val themeChild = item.bundleItemIds.firstNotNullOfOrNull { bId ->
                        ShopCatalog.initialShopItems.find { it.id == bId && it.category == ShopCategory.CHAT_THEME }
                    }
                    decoChild?.let { equippedDeco = it.decorationKey; onEquipDecoration(it.decorationKey) }
                    effChild?.let { equippedEffect = it.decorationKey; onEquipProfileEffect(it.decorationKey) }
                    themeChild?.let { equippedTheme = it.decorationKey; onEquipChatTheme(it.decorationKey) }
                }
            }
            SoundFxHelper.playChime()
            feedbackMessage = if (item.isEquipped) "${item.title} retiré" else "${item.title} équipé !"
        } else if (userOrbs >= item.priceOrbs) {
            val handledByRepo = onPurchaseShopItem(item)
            if (!handledByRepo) {
                userOrbs -= item.priceOrbs
                onUpdateOrbsBalance(userOrbs)
                if (isDeco) {
                    equippedDeco = item.decorationKey
                    onEquipDecoration(item.decorationKey)
                } else if (item.category == ShopCategory.PROFILE_EFFECT) {
                    equippedEffect = item.decorationKey
                    onEquipProfileEffect(item.decorationKey)
                } else if (item.category == ShopCategory.CHAT_THEME) {
                    equippedTheme = item.decorationKey
                    onEquipChatTheme(item.decorationKey)
                } else if (item.badgeId != null && !user.badges.contains(item.badgeId)) {
                    onUnlockBadge(item.badgeId)
                }
            } else {
                userOrbs = (userOrbs - item.priceOrbs).coerceAtLeast(0)
                if (isDeco) equippedDeco = item.decorationKey
                if (item.category == ShopCategory.PROFILE_EFFECT) equippedEffect = item.decorationKey
                if (item.category == ShopCategory.CHAT_THEME) equippedTheme = item.decorationKey
            }
            localPurchasedIds = localPurchasedIds + item.id + item.bundleItemIds
            SoundFxHelper.playJackpot()
            showConfetti = true
            feedbackMessage = "Achat réussi : ${item.title} débloqué !"
        } else {
            val missing = item.priceOrbs - userOrbs
            feedbackMessage = "Il vous manque $missing Orbs — tentez la Roue ou les Quêtes !"
        }
    }

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
                    // Top Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DiscordDarkSidebar)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(48.dp).testTag("close_orbs_shop_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer la boutique", tint = DiscordTextLight)
                                }
                                if (unclaimedRewardsCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .clip(CircleShape)
                                            .background(DiscordRed),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$unclaimedRewardsCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Boutique Orbs",
                                    color = DiscordTextWhite,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${shopItems.size} créations exclusives",
                                    color = DiscordTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = DiscordDarkInput,
                                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .clickable { showQuestsSheet = true }
                                    .testTag("orbs_balance_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Diamond,
                                        contentDescription = "Solde Orbs",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "$userOrbs",
                                        color = DiscordTextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showFavoritesOnly = !showFavoritesOnly },
                                modifier = Modifier.size(48.dp).testTag("toggle_favorites_button")
                            ) {
                                Icon(
                                    imageVector = if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Filtrer les favoris",
                                    tint = if (showFavoritesOnly) DiscordRed else DiscordTextLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showWheelDialog = true },
                                modifier = Modifier.size(48.dp).testTag("open_wheel_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Roue de la fortune Orbs",
                                    tint = Color(0xFFFDE047),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Luxury Hero Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF3B0764),
                                        Color(0xFF1E1035),
                                        DiscordDarkPrimary
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFEC4899).copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, Color(0xFFF472B6).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "COLLECTION ÉLITE",
                                            color = Color(0xFFFBCFE8),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${localPurchasedIds.size} possédés",
                                        color = Color(0xFFA78BFA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "RAVEN ORBS STUDIO",
                                    color = DiscordTextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Nouveaux modèles animés, packs collector & roue (max 500 Orbs).",
                                    color = DiscordTextLight,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        Button(
                                            onClick = { showQuestsSheet = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = DiscordDarkCard),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, DiscordDarkBorder),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp).testTag("open_quests_button")
                                        ) {
                                            Icon(Icons.Default.Bolt, contentDescription = null, tint = DiscordAmber, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (unclaimedRewardsCount > 0) "Quêtes ($unclaimedRewardsCount)" else "Quêtes",
                                                color = DiscordTextWhite,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    item {
                                        Button(
                                            onClick = { showTryOnStudio = !showTryOnStudio },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (showTryOnStudio) DiscordBlurple else DiscordDarkInput
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp).testTag("toggle_studio_button")
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Studio Essayage", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    item {
                                        Button(
                                            onClick = { showWheelDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF78350F)),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFDE047).copy(alpha = 0.65f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFDE047), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Roue 500 💎", color = Color(0xFFFEF08A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Floating Animated 3D Faceted Crystal Orb
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .offset(y = orbFloatY.dp)
                                    .clickable { showWheelDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    val r = size.width * 0.42f * orbPulse

                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(Color(0xFFC084FC).copy(alpha = 0.55f), Color.Transparent),
                                            center = c,
                                            radius = size.width * 0.5f
                                        ),
                                        radius = size.width * 0.5f,
                                        center = c
                                    )

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

                                    drawCircle(color = Color.White, radius = 4.2f, center = Offset(c.x - r * 0.22f, c.y - r * 0.3f))
                                }
                            }
                        }
                    }

                    // Feedback Banner if present
                    AnimatedVisibility(visible = feedbackMessage != null) {
                        Surface(
                            color = DiscordBlurple.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clickable { feedbackMessage = null }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = feedbackMessage.orEmpty(),
                                    color = DiscordTextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.Close, contentDescription = "Fermer le message", tint = DiscordTextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Interactive Try-On Studio Card (Avatar Deco + Banner Effect + Chat Theme together!)
                    AnimatedVisibility(visible = showTryOnStudio) {
                        val previewThemeObj = remember(tryOnTheme) {
                            ShopCatalog.initialChatThemes.find { it.id == tryOnTheme } ?: ShopCatalog.initialChatThemes.first()
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = DiscordDarkCard,
                            border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.75f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "STUDIO D'ESSAYAGE COMPLET",
                                            color = DiscordTextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    TextButton(onClick = { showTryOnStudio = false }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                        Text("Masquer", color = DiscordTextMuted, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Live Mini Profile + Chat Theme Preview Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(previewThemeObj.primaryColor),
                                                    Color(previewThemeObj.secondaryColor ?: previewThemeObj.primaryColor)
                                                )
                                            )
                                        )
                                ) {
                                    ProfileEffectOverlay(
                                        effectKey = tryOnEffect,
                                        modifier = Modifier.matchParentSize()
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(64.dp),
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

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                text = "Déco : ${tryOnDeco?.replace("_", " ") ?: "Aucune"}",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Bannière : ${tryOnEffect?.removePrefix("eff_")?.replace("_", " ") ?: "Standard"} • Fond : ${previewThemeObj.name}",
                                                color = Color.White.copy(alpha = 0.72f),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        val matchingItem = when (studioTab) {
                                            StudioTab.DECORATIONS -> shopItems.find {
                                                it.category in ShopCatalog.avatarDecorationCategories && it.decorationKey == tryOnDeco
                                            }
                                            StudioTab.EFFECTS -> shopItems.find {
                                                it.category == ShopCategory.PROFILE_EFFECT && it.decorationKey == tryOnEffect
                                            }
                                            StudioTab.THEMES -> shopItems.find {
                                                it.category == ShopCategory.CHAT_THEME && it.decorationKey == tryOnTheme
                                            }
                                        }
                                        val isFreeNone = (studioTab == StudioTab.DECORATIONS && tryOnDeco == null) ||
                                            (studioTab == StudioTab.EFFECTS && tryOnEffect == null)

                                        Button(
                                            onClick = {
                                                if (isFreeNone) {
                                                    if (studioTab == StudioTab.DECORATIONS) {
                                                        equippedDeco = null
                                                        onEquipDecoration(null)
                                                    } else {
                                                        equippedEffect = null
                                                        onEquipProfileEffect(null)
                                                    }
                                                } else if (matchingItem != null) {
                                                    handleItemAction(matchingItem)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (matchingItem?.isPurchased == false) Color(0xFFF59E0B) else DiscordBlurple
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp).testTag("studio_apply_button")
                                        ) {
                                            Text(
                                                text = when {
                                                    isFreeNone -> "Retirer"
                                                    matchingItem == null -> "Appliquer"
                                                    matchingItem.isPurchased -> "Équiper"
                                                    else -> "${matchingItem.priceOrbs} 💎"
                                                },
                                                color = if (matchingItem?.isPurchased == false) Color.Black else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Studio Sub-Tabs
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StudioTab.entries.forEach { tab ->
                                        val active = studioTab == tab
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (active) DiscordBlurple else DiscordDarkInput,
                                            modifier = Modifier.clickable { studioTab = tab }
                                        ) {
                                            Text(
                                                text = tab.label,
                                                color = if (active) Color.White else DiscordTextLight,
                                                fontSize = 10.sp,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Dynamic Try-On Selector Chips from ShopCatalog
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    when (studioTab) {
                                        StudioTab.DECORATIONS -> {
                                            item {
                                                val isChosen = tryOnDeco == null
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                                    border = if (isChosen) BorderStroke(1.dp, DiscordBlurple) else null,
                                                    modifier = Modifier.clickable { tryOnDeco = null }
                                                ) {
                                                    Text(
                                                        text = "Aucune",
                                                        color = if (isChosen) Color.White else DiscordTextLight,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                            val decoItems = shopItems.filter { it.category in ShopCatalog.avatarDecorationCategories }
                                            items(decoItems, key = { "try_${it.id}" }) { decoItem ->
                                                val isChosen = tryOnDeco == decoItem.decorationKey
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                                    border = if (isChosen) BorderStroke(1.dp, DiscordBlurple) else null,
                                                    modifier = Modifier.clickable { tryOnDeco = decoItem.decorationKey }
                                                ) {
                                                    Text(
                                                        text = if (decoItem.isNew) "✦ ${decoItem.title}" else decoItem.title,
                                                        color = if (isChosen) Color.White else DiscordTextLight,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                        StudioTab.EFFECTS -> {
                                            item {
                                                val isChosen = tryOnEffect == null
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                                    border = if (isChosen) BorderStroke(1.dp, DiscordBlurple) else null,
                                                    modifier = Modifier.clickable { tryOnEffect = null }
                                                ) {
                                                    Text(
                                                        text = "Aucun effet",
                                                        color = if (isChosen) Color.White else DiscordTextLight,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                            val effItems = shopItems.filter { it.category == ShopCategory.PROFILE_EFFECT }
                                            items(effItems, key = { "try_eff_${it.id}" }) { effItem ->
                                                val isChosen = tryOnEffect == effItem.decorationKey
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                                    border = if (isChosen) BorderStroke(1.dp, DiscordBlurple) else null,
                                                    modifier = Modifier.clickable { tryOnEffect = effItem.decorationKey }
                                                ) {
                                                    Text(
                                                        text = if (effItem.isNew) "✦ ${effItem.title}" else effItem.title,
                                                        color = if (isChosen) Color.White else DiscordTextLight,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                        StudioTab.THEMES -> {
                                            val themeItems = shopItems.filter { it.category == ShopCategory.CHAT_THEME }
                                            items(themeItems, key = { "try_thm_${it.id}" }) { thmItem ->
                                                val isChosen = tryOnTheme == thmItem.decorationKey
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isChosen) DiscordBlurple.copy(alpha = 0.35f) else DiscordDarkInput,
                                                    border = if (isChosen) BorderStroke(1.dp, DiscordBlurple) else null,
                                                    modifier = Modifier.clickable { tryOnTheme = thmItem.decorationKey }
                                                ) {
                                                    Text(
                                                        text = thmItem.title,
                                                        color = if (isChosen) Color.White else DiscordTextLight,
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search + Sort Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Rechercher un modèle, pack...", fontSize = 12.sp, color = DiscordTextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DiscordTextMuted, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Effacer la recherche", tint = DiscordTextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DiscordDarkInput,
                                unfocusedContainerColor = DiscordDarkInput,
                                focusedBorderColor = DiscordBlurple,
                                unfocusedBorderColor = DiscordDarkBorder,
                                focusedTextColor = DiscordTextWhite,
                                unfocusedTextColor = DiscordTextWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("shop_search_input")
                        )

                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DiscordDarkInput,
                                border = BorderStroke(1.dp, DiscordDarkBorder),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { showSortMenu = true }
                                    .testTag("shop_sort_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Trier", tint = Color(0xFF818CF8), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(sortMode.label, color = DiscordTextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                containerColor = DiscordDarkCard
                            ) {
                                ShopSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = mode.label,
                                                color = if (sortMode == mode) Color(0xFF818CF8) else DiscordTextWhite,
                                                fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Categories Row
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ShopCategory.values()) { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) DiscordBlurple else DiscordDarkInput,
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF818CF8)) else null,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedCategory = category }
                                    .testTag("shop_category_${category.name.lowercase()}")
                            ) {
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.White else DiscordTextLight,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Shop Items Grid
                    val filteredItems = remember(shopItems, selectedCategory, showFavoritesOnly, searchQuery, sortMode) {
                        var list = if (selectedCategory == ShopCategory.ALL) shopItems else shopItems.filter { it.category == selectedCategory }
                        if (showFavoritesOnly) list = list.filter { it.isFavorite }
                        if (searchQuery.isNotBlank()) {
                            val q = searchQuery.trim().lowercase()
                            list = list.filter {
                                it.title.lowercase().contains(q) ||
                                    it.subtitle.lowercase().contains(q) ||
                                    it.description.lowercase().contains(q) ||
                                    it.tierTag.lowercase().contains(q)
                            }
                        }
                        when (sortMode) {
                            ShopSortMode.FEATURED -> list
                            ShopSortMode.NEWEST -> list.sortedWith(compareByDescending<ShopItem> { it.isNew }.thenByDescending { it.isLimited })
                            ShopSortMode.PRICE_ASC -> list.sortedBy { it.priceOrbs }
                            ShopSortMode.PRICE_DESC -> list.sortedByDescending { it.priceOrbs }
                            ShopSortMode.RARITY -> list.sortedByDescending { it.rarity.ordinal }
                            ShopSortMode.OWNED -> list.filter { it.isPurchased }
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = DiscordTextMuted, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Aucun article trouvé", color = DiscordTextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    text = "Essayez un autre mot-clé ou réinitialisez les filtres.",
                                    color = DiscordTextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp)
                        ) {
                            items(filteredItems, key = { it.id }) { item ->
                                ShopItemCard(
                                    item = item,
                                    userOrbs = userOrbs,
                                    userAvatarColor = user.avatarColor,
                                    onFavoriteToggle = {
                                        localFavoriteIds = if (localFavoriteIds.contains(item.id)) {
                                            localFavoriteIds - item.id
                                        } else {
                                            localFavoriteIds + item.id
                                        }
                                        onToggleFavoriteItem(item.id)
                                    },
                                    onClick = {
                                        selectedItemDetail = item
                                        val isDeco = item.category in ShopCatalog.avatarDecorationCategories
                                        if (isDeco) {
                                            tryOnDeco = item.decorationKey
                                            studioTab = StudioTab.DECORATIONS
                                        } else if (item.category == ShopCategory.PROFILE_EFFECT) {
                                            tryOnEffect = item.decorationKey
                                            studioTab = StudioTab.EFFECTS
                                        } else if (item.category == ShopCategory.CHAT_THEME) {
                                            tryOnTheme = item.decorationKey
                                            studioTab = StudioTab.THEMES
                                        }
                                    },
                                    onActionClick = { handleItemAction(item) }
                                )
                            }
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

    // Interactive Detail Preview Dialog
    selectedItemDetail?.let { detailItemRaw ->
        val detailItem = shopItems.find { it.id == detailItemRaw.id } ?: detailItemRaw
        ShopItemDetailDialog(
            item = detailItem,
            user = user,
            userOrbs = userOrbs,
            onDismiss = { selectedItemDetail = null },
            onTryInStudio = {
                when {
                    detailItem.category in ShopCatalog.avatarDecorationCategories -> {
                        tryOnDeco = detailItem.decorationKey
                        studioTab = StudioTab.DECORATIONS
                    }
                    detailItem.category == ShopCategory.PROFILE_EFFECT -> {
                        tryOnEffect = detailItem.decorationKey
                        studioTab = StudioTab.EFFECTS
                    }
                    detailItem.category == ShopCategory.CHAT_THEME -> {
                        tryOnTheme = detailItem.decorationKey
                        studioTab = StudioTab.THEMES
                    }
                }
                showTryOnStudio = true
                selectedItemDetail = null
            },
            onOpenWheel = {
                selectedItemDetail = null
                showWheelDialog = true
            },
            onActionClick = {
                handleItemAction(detailItem)
                selectedItemDetail = null
            }
        )
    }

    // Daily Lucky Wheel of Fortune Dialog
    if (showWheelDialog) {
        DailyOrbsWheelDialog(
            onDismiss = { showWheelDialog = false },
            onWinOrbs = { reward ->
                userOrbs += reward
                onUpdateOrbsBalance(userOrbs)
                showConfetti = true
                feedbackMessage = "+$reward Orbs ajoutés depuis la Roue !"
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

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DiscordBlurple.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, DiscordBlurple.copy(alpha = 0.55f)),
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
                            modifier = Modifier.height(34.dp).testTag("claim_daily_streak_button")
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
    userOrbs: Int,
    userAvatarColor: Long,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val rarityColor = Color(item.rarity.color)
    val canAfford = item.isPurchased || userOrbs >= item.priceOrbs
    val borderBrush = when {
        item.isEquipped -> Brush.linearGradient(listOf(DiscordBlurple, Color(0xFF00F0FF)))
        item.rarity == BadgeRarity.LEGENDARY -> Brush.linearGradient(listOf(Color(0xFFFDE047).copy(alpha = 0.7f), Color(0xFFEC4899).copy(alpha = 0.6f)))
        item.rarity == BadgeRarity.EPIC -> Brush.linearGradient(listOf(Color(0xFFEB459E).copy(alpha = 0.65f), Color(0xFF818CF8).copy(alpha = 0.55f)))
        else -> Brush.linearGradient(listOf(DiscordDarkBorder, DiscordDarkBorder))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (item.isEquipped) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("shop_item_card_${item.id}"),
        color = DiscordDarkCard
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Card Top Row: Price / Tag + Favorite icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DiscordDarkInput
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${item.priceOrbs}",
                                color = if (canAfford) DiscordTextWhite else Color(0xFFFCA5A5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (item.isNew || item.isLimited) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.isLimited) Color(0xFFDC2626).copy(alpha = 0.25f) else Color(0xFF10B981).copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = if (item.isLimited) "LIMITÉ" else "NEW",
                                color = if (item.isLimited) Color(0xFFFCA5A5) else Color(0xFF34D399),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (item.isFavorite) DiscordRed else DiscordTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Visual Showcase Preview
            ShopItemVisualPreview(
                item = item,
                userAvatarColor = userAvatarColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Rarity + Tier Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(rarityColor)
                )
                Text(
                    text = if (item.tierTag.isNotBlank()) "${item.rarity.label} • ${item.tierTag}" else item.rarity.label,
                    color = rarityColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

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

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        item.isEquipped -> DiscordDarkInput
                        item.isPurchased -> DiscordBlurple
                        canAfford -> DiscordBlurple
                        else -> Color(0xFF3F3F46)
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("shop_action_${item.id}")
            ) {
                Text(
                    text = when {
                        item.isEquipped -> "Équipé ✓"
                        item.isPurchased -> "Équiper"
                        canAfford -> "Acheter"
                        else -> "${item.priceOrbs} 💎"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ShopItemVisualPreview(
    item: ShopItem,
    userAvatarColor: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            item.category == ShopCategory.PROFILE_EFFECT -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(74.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF14151B),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
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
                            shape = RoundedCornerShape(5.dp),
                            color = Color.Black.copy(alpha = 0.58f)
                        ) {
                            Text(
                                text = "Effet de bannière",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            item.category == ShopCategory.CHAT_THEME -> {
                val themeObj = remember(item.decorationKey) {
                    ShopCatalog.initialChatThemes.find { it.id == item.decorationKey }
                }
                val c1 = themeObj?.primaryColor?.let { Color(it) } ?: Color(0xFF28133F)
                val c2 = themeObj?.secondaryColor?.let { Color(it) } ?: Color(0xFF06B6D4)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .height(74.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = c1
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(brush = Brush.linearGradient(listOf(c1, c2)))
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(7.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.85f))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .width(48.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color.White.copy(alpha = 0.65f))
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(DiscordBlurple)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.78f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item.decorationKey == "nitro_credits" -> {
                Canvas(modifier = Modifier.size(58.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width * 0.4f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFFEC4899).copy(alpha = 0.65f), Color.Transparent),
                            center = c,
                            radius = r * 1.6f
                        ),
                        radius = r * 1.6f,
                        center = c
                    )
                    for (step in 3 downTo 0) {
                        val yOff = step * 3.5f
                        drawOval(
                            color = if (step == 0) Color(0xFF6366F1) else Color(0xFF3730A3),
                            topLeft = Offset(c.x - r, c.y - r * 0.65f + yOff),
                            size = Size(r * 2f, r * 1.3f)
                        )
                    }
                    drawCircle(color = Color(0xFFF472B6), radius = r * 0.42f, center = Offset(c.x, c.y - 2f))
                    drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(c.x, c.y - 2f))
                }
            }

            item.decorationKey == "badge_orbs" ||
                item.decorationKey == "badge_cyber_legend" ||
                item.decorationKey == "badge_void_monarch" -> {
                val cList = if (item.previewColors.size >= 2) {
                    item.previewColors.map { Color(it) }
                } else {
                    listOf(Color(0xFF38BDF8), Color(0xFFC084FC), Color(0xFFEC4899))
                }
                Canvas(modifier = Modifier.size(58.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width * 0.44f
                    drawCircle(
                        brush = Brush.radialGradient(listOf(cList.first().copy(alpha = 0.45f), Color.Transparent)),
                        radius = r * 1.45f,
                        center = c
                    )
                    val starPath = Path().apply {
                        moveTo(c.x, c.y - r)
                        lineTo(c.x + r * 0.34f, c.y - r * 0.34f)
                        lineTo(c.x + r, c.y)
                        lineTo(c.x + r * 0.34f, c.y + r * 0.34f)
                        lineTo(c.x, c.y + r)
                        lineTo(c.x - r * 0.34f, c.y + r * 0.34f)
                        lineTo(c.x - r, c.y)
                        lineTo(c.x - r * 0.34f, c.y - r * 0.34f)
                        close()
                    }
                    drawPath(starPath, brush = Brush.sweepGradient(cList + cList.first()))
                    drawCircle(color = Color(0xFF0F172A), radius = r * 0.4f, center = c)
                    drawCircle(color = Color.White, radius = r * 0.2f, center = c)
                }
            }

            item.bundleItemIds.isNotEmpty() -> {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(userAvatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    AvatarDecorationOverlay(
                        decorationKey = item.decorationKey,
                        size = 48.dp
                    )
                }
            }

            else -> {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(userAvatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = DiscordTextLight,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    AvatarDecorationOverlay(
                        decorationKey = item.decorationKey,
                        size = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopItemDetailDialog(
    item: ShopItem,
    user: User,
    userOrbs: Int,
    onDismiss: () -> Unit,
    onTryInStudio: () -> Unit,
    onOpenWheel: () -> Unit,
    onActionClick: () -> Unit
) {
    val rarityColor = Color(item.rarity.color)
    val canAfford = item.isPurchased || userOrbs >= item.priceOrbs
    val bundleChildren = remember(item.bundleItemIds) {
        item.bundleItemIds.mapNotNull { bId -> ShopCatalog.initialShopItems.find { it.id == bId } }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DiscordDarkCard,
            border = BorderStroke(1.dp, rarityColor.copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = rarityColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, rarityColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = item.rarity.label.uppercase(),
                            color = rarityColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer l'aperçu", tint = DiscordTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Large Animated Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DiscordDarkSidebar),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.category == ShopCategory.PROFILE_EFFECT) {
                        ProfileEffectOverlay(
                            effectKey = item.decorationKey,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (item.category in ShopCatalog.avatarDecorationCategories || item.bundleItemIds.isNotEmpty()) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(user.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                            }
                            AvatarDecorationOverlay(decorationKey = item.decorationKey, size = 72.dp)
                        }
                    } else {
                        ShopItemVisualPreview(
                            item = item,
                            userAvatarColor = user.avatarColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.title,
                    color = DiscordTextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.description,
                    color = DiscordTextLight,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                if (bundleChildren.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DiscordDarkInput,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Contenu inclus dans ce pack :",
                                color = DiscordTextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            bundleChildren.forEach { child ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${child.title} (${child.subtitle})", color = DiscordTextLight, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.category in ShopCatalog.avatarDecorationCategories ||
                        item.category == ShopCategory.PROFILE_EFFECT ||
                        item.category == ShopCategory.CHAT_THEME
                    ) {
                        OutlinedButton(
                            onClick = onTryInStudio,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DiscordBlurple),
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Text("Essayer", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!canAfford) {
                        Button(
                            onClick = onOpenWheel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.3f).height(46.dp)
                        ) {
                            Text("Gagner Orbs (${item.priceOrbs} 💎)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onActionClick,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                                .testTag("detail_buy_equip_button")
                        ) {
                            Text(
                                text = when {
                                    item.isEquipped -> "Retirer"
                                    item.isPurchased -> "Équiper maintenant"
                                    else -> "Acheter • ${item.priceOrbs} 💎"
                                },
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
