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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumChannelView(
    channel: Channel,
    posts: List<ForumPost>,
    currentUserId: String,
    canModerateForum: Boolean,
    showNavigationDrawerButton: Boolean,
    onToggleDrawer: () -> Unit,
    onCreatePost: (title: String, content: String, tags: List<String>, status: ForumPostStatus) -> Unit,
    onToggleUpvote: (postId: String) -> Unit,
    onAddReply: (postId: String, content: String) -> Unit,
    onUpdateStatus: (postId: String, status: ForumPostStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = ravenPalette()
    var filterStatus by remember { mutableStateOf<ForumPostStatus?>(null) }
    var showNewPostDialog by remember { mutableStateOf(false) }
    var activePostId by remember { mutableStateOf<String?>(null) }

    val filteredPosts = remember(posts, filterStatus) {
        val base = if (filterStatus == null) posts else posts.filter { it.status == filterStatus }
        base.sortedByDescending { it.upvotedByIds.size }
    }
    val activePost = posts.find { it.id == activePostId }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("forum_channel_view"),
        containerColor = palette.chatBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = DiscordBlurple)
                        Column {
                            Text("#${channel.name}", color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Salon Forum & Entraide • ${posts.size} sujet(s)", color = palette.textMuted, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    if (showNavigationDrawerButton) {
                        IconButton(onClick = onToggleDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Ouvrir le menu", tint = palette.textPrimary)
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { showNewPostDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("forum_new_post_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sujet", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.sidebarBg)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filterStatus == null,
                        onClick = { filterStatus = null },
                        label = { Text("Tous (${posts.size})", fontSize = 12.sp) }
                    )
                }
                items(ForumPostStatus.entries) { st ->
                    FilterChip(
                        selected = filterStatus == st,
                        onClick = { filterStatus = if (filterStatus == st) null else st },
                        label = { Text(st.label, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredPosts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Forum, contentDescription = null, tint = DiscordTextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aucun sujet pour ce filtre", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                        Text("Lancez la première question ou annonce du salon !", color = palette.textMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredPosts, key = { it.id }) { post ->
                        val hasUpvoted = currentUserId in post.upvotedByIds
                        Surface(
                            color = palette.cardBg,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { activePostId = post.id }
                                .testTag("forum_post_card_${post.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = Color(post.status.colorHex).copy(alpha = 0.2f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = post.status.label,
                                            color = Color(post.status.colorHex),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(post.timestamp, color = palette.textMuted, fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(post.title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = post.content,
                                    color = palette.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        post.tags.take(3).forEach { tag ->
                                            Surface(color = palette.inputBg, shape = RoundedCornerShape(6.dp)) {
                                                Text(tag, color = palette.textMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        Text("• ${post.authorName}", color = palette.textMuted, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(14.dp))
                                            Text("${post.replies.size}", color = palette.textMuted, fontSize = 12.sp)
                                        }
                                        Surface(
                                            color = if (hasUpvoted) DiscordBlurple.copy(alpha = 0.25f) else palette.inputBg,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onToggleUpvote(post.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ThumbUp,
                                                    contentDescription = "Voter",
                                                    tint = if (hasUpvoted) DiscordBlurple else palette.textMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text("${post.upvotedByIds.size}", color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    if (showNewPostDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var tagText by remember { mutableStateOf("Entraide") }
        var selectedStatus by remember { mutableStateOf(ForumPostStatus.OPEN) }

        AlertDialog(
            onDismissRequest = { showNewPostDialog = false },
            containerColor = DiscordDarkCard,
            title = { Text("Nouveau sujet Forum", color = DiscordTextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titre du sujet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("forum_title_input")
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Détails / question") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("forum_body_input")
                    )
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        label = { Text("Étiquettes (séparées par virgule)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ForumPostStatus.entries.forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tags = tagText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onCreatePost(title, body, tags, selectedStatus)
                        showNewPostDialog = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                ) {
                    Text("Publier", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPostDialog = false }) { Text("Annuler", color = DiscordTextMuted) }
            }
        )
    }

    if (activePost != null) {
        var replyInput by remember { mutableStateOf("") }
        val canChangeStatus = canModerateForum || activePost.authorId == currentUserId

        ModalBottomSheet(
            onDismissRequest = { activePostId = null },
            containerColor = DiscordDarkCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(activePost.title, color = DiscordTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(activePost.content, color = DiscordTextLight, fontSize = 13.sp)

                if (canChangeStatus) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ForumPostStatus.entries.forEach { st ->
                            FilterChip(
                                selected = activePost.status == st,
                                onClick = { onUpdateStatus(activePost.id, st) },
                                label = { Text(st.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = DiscordDarkBorder)
                Text("RÉPONSES (${activePost.replies.size})", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(activePost.replies, key = { it.id }) { rep ->
                        Surface(color = DiscordDarkInput, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(rep.authorName, color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(rep.timestamp, color = DiscordTextMuted, fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(rep.content, color = DiscordTextWhite, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = replyInput,
                        onValueChange = { replyInput = it },
                        placeholder = { Text("Répondre au sujet...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (replyInput.isNotBlank()) {
                                onAddReply(activePost.id, replyInput)
                                replyInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer réponse", tint = DiscordBlurple)
                    }
                }
            }
        }
    }
}
