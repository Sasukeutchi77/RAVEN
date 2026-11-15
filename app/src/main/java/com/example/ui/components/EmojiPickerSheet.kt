package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class EmojiEntry(
    val symbol: String,
    val keywords: String
)

data class EmojiCategoryGroup(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val emojis: List<EmojiEntry>
)

object EmojiCatalog {
    val quickReactions = listOf("👍", "❤️", "😂", "🔥", "🎉", "🚀", "👀")

    val categories: List<EmojiCategoryGroup> = listOf(
        EmojiCategoryGroup(
            id = "popular",
            title = "Populaires",
            iconEmoji = "🔥",
            emojis = listOf(
                EmojiEntry("👍", "pouce bien ok oui valide like thumbs up"),
                EmojiEntry("❤️", "coeur amour love heart"),
                EmojiEntry("😂", "rire mdr lol pleure joy laugh"),
                EmojiEntry("🔥", "feu flamme incroyable fire hot lit"),
                EmojiEntry("🎉", "fete bravo celebration party"),
                EmojiEntry("🚀", "fusee envoi lune rocket space"),
                EmojiEntry("👀", "yeux regarde curieux eyes look"),
                EmojiEntry("💯", "cent parfait top 100"),
                EmojiEntry("😭", "pleure triste mdr cry sob"),
                EmojiEntry("😍", "amour yeux coeur love heart eyes"),
                EmojiEntry("🥳", "fete anniversaire celebration party"),
                EmojiEntry("🙌", "bravo mains ciel praise hands"),
                EmojiEntry("🙏", "merci priere s il vous plait pray thanks"),
                EmojiEntry("💀", "mort crane mdr skull dead"),
                EmojiEntry("✨", "etoiles magie brille sparkles"),
                EmojiEntry("😎", "cool lunettes style sunglasses")
            )
        ),
        EmojiCategoryGroup(
            id = "faces",
            title = "Visages",
            iconEmoji = "😀",
            emojis = listOf(
                EmojiEntry("😀", "sourire heureux smile happy"),
                EmojiEntry("😃", "sourire joie happy grin"),
                EmojiEntry("😄", "content rire smile"),
                EmojiEntry("😁", "dents sourire grin"),
                EmojiEntry("😅", "sueur gene rire sweat smile"),
                EmojiEntry("🤣", "rouler rire mdr rofl"),
                EmojiEntry("😊", "timide sourire content blush"),
                EmojiEntry("😇", "ange aureole innocent angel"),
                EmojiEntry("😉", "clin oeil wink"),
                EmojiEntry("😌", "soulage calme relieved"),
                EmojiEntry("🥰", "amour coeurs love"),
                EmojiEntry("😘", "bisou amour kiss"),
                EmojiEntry("😋", "miam delicieux yum"),
                EmojiEntry("😜", "fou langue wink tongue"),
                EmojiEntry("🤔", "penser reflexion hmm think"),
                EmojiEntry("🤨", "doute sourcil eyebrow"),
                EmojiEntry("😐", "neutre bof meh"),
                EmojiEntry("😏", "malin sourire smirk"),
                EmojiEntry("😴", "dodo sommeil dormir sleep"),
                EmojiEntry("🤯", "choc tete explose mind blown"),
                EmojiEntry("🥺", "supplie mignon yeux pleading"),
                EmojiEntry("😳", "choque rouge gene flushed"),
                EmojiEntry("😱", "peur cri choc scream"),
                EmojiEntry("😡", "colere enerve rage angry"),
                EmojiEntry("🤡", "clown drole"),
                EmojiEntry("🤖", "robot ia mecha"),
                EmojiEntry("👻", "fantome halloween ghost"),
                EmojiEntry("👾", "alien jeu retro arcade")
            )
        ),
        EmojiCategoryGroup(
            id = "gestures",
            title = "Gestes & Coeurs",
            iconEmoji = "🫶",
            emojis = listOf(
                EmojiEntry("🫶", "coeur mains amour heart hands"),
                EmojiEntry("👏", "applaudir bravo clap"),
                EmojiEntry("🤝", "accord poignée main handshake"),
                EmojiEntry("👌", "ok parfait super"),
                EmojiEntry("✌️", "paix victoire deux peace"),
                EmojiEntry("🤞", "croise doigts chance fingers crossed"),
                EmojiEntry("💪", "force muscle courage strong"),
                EmojiEntry("👋", "salut coucou bonjour wave"),
                EmojiEntry("🤙", "appel cool shaka call me"),
                EmojiEntry("👎", "pouce bas non dislike"),
                EmojiEntry("💖", "coeur brillant rose sparkle heart"),
                EmojiEntry("💜", "coeur violet purple heart"),
                EmojiEntry("💙", "coeur bleu blue heart"),
                EmojiEntry("💚", "coeur vert green heart"),
                EmojiEntry("🖤", "coeur noir black heart"),
                EmojiEntry("💔", "coeur brise triste broken heart")
            )
        ),
        EmojiCategoryGroup(
            id = "gaming",
            title = "Gaming & Fête",
            iconEmoji = "🎮",
            emojis = listOf(
                EmojiEntry("🎮", "manette jeu video gaming"),
                EmojiEntry("🏆", "trophee victoire champion gagnant trophy"),
                EmojiEntry("🥇", "medaille or premier gold"),
                EmojiEntry("🎧", "casque musique audio headset"),
                EmojiEntry("🎤", "micro chant vocal mic"),
                EmojiEntry("⚡", "eclair foudre energie vitesse bolt lightning"),
                EmojiEntry("💎", "diamant gemme orbs rare"),
                EmojiEntry("👑", "couronne roi reine crown royal"),
                EmojiEntry("🎯", "cible objectif precis target"),
                EmojiEntry("🎲", "de jeu hasard dice"),
                EmojiEntry("🎨", "art peinture dessin palette"),
                EmojiEntry("📸", "photo camera image"),
                EmojiEntry("🎵", "musique note chanson music"),
                EmojiEntry("🎁", "cadeau surprise gift"),
                EmojiEntry("🍿", "popcorn cinema film"),
                EmojiEntry("☕", "cafe pause matin coffee")
            )
        ),
        EmojiCategoryGroup(
            id = "nature",
            title = "Nature & Espace",
            iconEmoji = "🌸",
            emojis = listOf(
                EmojiEntry("🦅", "aigle corbeau raven oiseau"),
                EmojiEntry("🐉", "dragon astral feu"),
                EmojiEntry("🦊", "renard fox"),
                EmojiEntry("🐱", "chat cat"),
                EmojiEntry("🌸", "sakura fleur cerisier printemps"),
                EmojiEntry("🌹", "rose fleur amour"),
                EmojiEntry("🌊", "vague mer ocean wave"),
                EmojiEntry("❄️", "neige flocons froid hiver"),
                EmojiEntry("🌈", "arc en ciel couleur rainbow"),
                EmojiEntry("☀️", "soleil jour ete sun"),
                EmojiEntry("🌙", "lune nuit dormir moon"),
                EmojiEntry("⭐", "etoile star"),
                EmojiEntry("💫", "etoile filante cosmique"),
                EmojiEntry("🪐", "planete saturne espace"),
                EmojiEntry("🌋", "volcan lave feu"),
                EmojiEntry("🍀", "trefle chance vert")
            )
        ),
        EmojiCategoryGroup(
            id = "symbols",
            title = "Symboles",
            iconEmoji = "✅",
            emojis = listOf(
                EmojiEntry("✅", "valide coche ok check"),
                EmojiEntry("❌", "croix non erreur annuler"),
                EmojiEntry("❓", "question interrogation"),
                EmojiEntry("❗", "exclamation attention important"),
                EmojiEntry("💡", "idee ampoule astuce idea"),
                EmojiEntry("🔔", "cloche notification alerte"),
                EmojiEntry("📌", "epingle pin important"),
                EmojiEntry("🔒", "cadenas prive securite lock"),
                EmojiEntry("💬", "bulle message chat"),
                EmojiEntry("🔊", "son volume haut parleur"),
                EmojiEntry("♻️", "recyclage boucle"),
                EmojiEntry("🚩", "drapeau signaler flag")
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerModal(
    title: String = "Ajouter une réaction",
    onSelectEmoji: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(EmojiCatalog.categories.first().id) }

    val displayedEmojis = remember(searchQuery, selectedCategoryId) {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            EmojiCatalog.categories
                .flatMap { it.emojis }
                .distinctBy { it.symbol }
                .filter { it.keywords.contains(q) || it.symbol.contains(q) }
        } else {
            EmojiCatalog.categories
                .firstOrNull { it.id == selectedCategoryId }
                ?.emojis ?: emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DiscordDarkBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 460.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = DiscordTextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer le sélecteur d'emojis",
                        tint = DiscordTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Rechercher un emoji (ex: feu, rire, coeur)...",
                        color = DiscordTextMuted,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = DiscordTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Effacer la recherche",
                                tint = DiscordTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emoji_picker_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DiscordDarkInput,
                    unfocusedContainerColor = DiscordDarkInput,
                    focusedBorderColor = DiscordBlurple,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = DiscordTextWhite,
                    unfocusedTextColor = DiscordTextLight
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category selector tabs
            if (searchQuery.isBlank()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EmojiCatalog.categories, key = { it.id }) { category ->
                        val isSelected = category.id == selectedCategoryId
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DiscordBlurple else DiscordDarkInput,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedCategoryId = category.id }
                                .testTag("emoji_category_${category.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(text = category.iconEmoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.title,
                                    color = if (isSelected) Color.White else DiscordTextLight,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (displayedEmojis.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentDissatisfied,
                        contentDescription = null,
                        tint = DiscordTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aucun emoji trouvé pour \"$searchQuery\"",
                        color = DiscordTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayedEmojis, key = { it.symbol }) { entry ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DiscordDarkInput.copy(alpha = 0.65f))
                                .clickable { onSelectEmoji(entry.symbol) }
                                .testTag("emoji_picker_item_${entry.symbol}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.symbol,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
