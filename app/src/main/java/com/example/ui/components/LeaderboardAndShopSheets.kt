package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.model.Server
import com.example.model.ServerShopItem
import com.example.model.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLeaderboardSheet(
    serverName: String,
    rankedMembers: List<Pair<User, Int>>,
    currentUserId: String,
    onMemberClick: (User) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("server_leaderboard_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 640.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Classement • $serverName", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Podium hebdomadaire XP, Boosts & Activité", color = palette.textMuted, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Top 3 Podium
            if (rankedMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val second = rankedMembers.getOrNull(1)
                    val first = rankedMembers.getOrNull(0)
                    val third = rankedMembers.getOrNull(2)

                    if (second != null) {
                        PodiumColumn(
                            rank = 2,
                            user = second.first,
                            score = second.second,
                            heightDp = 82,
                            medalColor = Color(0xFFC0C0C0),
                            modifier = Modifier.weight(1f),
                            onClick = { onMemberClick(second.first) }
                        )
                    }
                    if (first != null) {
                        PodiumColumn(
                            rank = 1,
                            user = first.first,
                            score = first.second,
                            heightDp = 104,
                            medalColor = Color(0xFFFACC15),
                            modifier = Modifier.weight(1.1f),
                            onClick = { onMemberClick(first.first) }
                        )
                    }
                    if (third != null) {
                        PodiumColumn(
                            rank = 3,
                            user = third.first,
                            score = third.second,
                            heightDp = 68,
                            medalColor = Color(0xFFCD7F32),
                            modifier = Modifier.weight(1f),
                            onClick = { onMemberClick(third.first) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("TOUS LES MEMBRES CLASSÉS", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(rankedMembers, key = { _, pair -> pair.first.id }) { idx, (member, score) ->
                    val isMe = member.id == currentUserId || member.id == "user_me"
                    Surface(
                        color = if (isMe) palette.accent.copy(alpha = 0.16f) else palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isMe) palette.accent else palette.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMemberClick(member) }
                            .testTag("leaderboard_row_${member.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                color = if (idx == 0) Color(0xFFFACC15) else palette.textMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(member.avatarColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(member.name, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (!member.clanTag.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = palette.inputBg, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = member.clanTag,
                                                color = palette.accent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (idx == 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = Color(0xFFFACC15).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "MVP",
                                                color = Color(0xFFFACC15),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text("Niv. ${member.level} • ${member.xp} XP", color = palette.textMuted, fontSize = 11.sp)
                            }
                            Text(
                                text = "$score pts",
                                color = palette.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    rank: Int,
    user: User,
    score: Int,
    heightDp: Int,
    medalColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = ravenPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        if (rank == 1) {
            Icon(Icons.Default.WorkspacePremium, contentDescription = "MVP", tint = medalColor, modifier = Modifier.size(22.dp))
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(user.avatarColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(user.name, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = medalColor.copy(alpha = 0.22f),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            border = BorderStroke(1.5.dp, medalColor),
            modifier = Modifier.fillMaxWidth().height(heightDp.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("#$rank", color = medalColor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("$score pts", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerShopSheet(
    server: Server,
    currentUser: User,
    onPurchaseItem: (String) -> Boolean,
    onCreateShopItem: (title: String, desc: String, priceOrbs: Int, primaryHex: Long, secondaryHex: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var showCreateForm by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("120") }
    var feedback by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("server_shop_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 650.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = DiscordAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Boutique de ${server.name}", color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Vos Orbs : ${currentUser.orbsBalance} Orbs", color = DiscordAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                FilledTonalButton(
                    onClick = { showCreateForm = !showCreateForm },
                    modifier = Modifier.testTag("server_shop_toggle_create")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Créer", fontSize = 12.sp)
                }
            }

            if (feedback != null) {
                Surface(color = palette.accent.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(feedback!!, color = palette.textPrimary, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }

            if (showCreateForm) {
                Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NOUVEL ARTICLE VIP DU SERVEUR", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Nom du rôle / titre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("shop_create_title")
                        )
                        OutlinedTextField(
                            value = newDesc,
                            onValueChange = { newDesc = it },
                            label = { Text("Description") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newPrice,
                                onValueChange = { newPrice = it.filter { c -> c.isDigit() } },
                                label = { Text("Prix Orbs") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val price = newPrice.toIntOrNull() ?: 120
                                    if (newTitle.isNotBlank()) {
                                        onCreateShopItem(newTitle, newDesc, price, 0xFFEB459E, 0xFF5865F2)
                                        newTitle = ""
                                        newDesc = ""
                                        showCreateForm = false
                                        feedback = "Article ajouté à la boutique du serveur !"
                                    }
                                },
                                enabled = newTitle.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                modifier = Modifier.testTag("shop_create_submit")
                            ) {
                                Text("Publier", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (server.shopItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Aucun article VIP dans cette boutique pour le moment.", color = palette.textMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(server.shopItems, key = { it.id }) { item ->
                        val owned = currentUser.id in item.purchasedByUserIds
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (owned) DiscordGreen else Color(item.primaryColorHex).copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth().testTag("server_shop_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(item.primaryColorHex), Color(item.secondaryColorHex))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(item.description, color = palette.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val ok = onPurchaseItem(item.id)
                                        feedback = if (ok) "Rôle « ${item.roleName} » activé !" else "Solde d'Orbs insuffisant."
                                    },
                                    enabled = !owned,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (owned) DiscordGreen else DiscordAmber
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("server_shop_buy_${item.id}")
                                ) {
                                    Text(
                                        text = if (owned) "Équipé" else "${item.priceOrbs} Orbs",
                                        color = if (owned) Color.White else Color.Black,
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
