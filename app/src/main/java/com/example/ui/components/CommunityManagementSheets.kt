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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CommunityPermissions
import com.example.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityInvitesSheet(
    server: Server,
    canManageInvites: Boolean,
    onCreateInvite: (maxUses: Int, expiresInHours: Int) -> Unit,
    onRevokeInvite: (code: String) -> Unit,
    onDeleteInvite: (code: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val clipboard = LocalClipboardManager.current
    var maxUses by remember { mutableIntStateOf(0) }
    var expiresHours by remember { mutableIntStateOf(24) }
    var copiedCode by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("community_invites_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 640.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Gestion des invitations • ${server.name}",
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Default quick share card
            Surface(
                color = palette.cardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Code principal", color = palette.textMuted, fontSize = 11.sp)
                        Text(server.inviteCode, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    FilledTonalButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(server.inviteCode))
                            copiedCode = server.inviteCode
                        },
                        modifier = Modifier.testTag("copy_main_invite_btn")
                    ) {
                        Text(if (copiedCode == server.inviteCode) "Copié !" else "Copier")
                    }
                }
            }

            if (canManageInvites) {
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NOUVELLE INVITATION PERSONNALISÉE", color = palette.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Limite d'utilisations :", color = palette.textMuted, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(0 to "Illimité", 1 to "1 utilisation", 5 to "5 max", 25 to "25 max")) { (valUses, label) ->
                                FilterChip(
                                    selected = maxUses == valUses,
                                    onClick = { maxUses = valUses },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Text("Expiration :", color = palette.textMuted, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(0 to "Jamais", 1 to "1 heure", 24 to "24 heures", 168 to "7 jours")) { (hrs, label) ->
                                FilterChip(
                                    selected = expiresHours == hrs,
                                    onClick = { expiresHours = hrs },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                        Button(
                            onClick = { onCreateInvite(maxUses, expiresHours) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create_community_invite_btn")
                        ) {
                            Icon(Icons.Default.AddLink, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Générer une invitation", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(server.invites, key = { it.code }) { inv ->
                    val usable = inv.isUsable()
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (usable) palette.border else DiscordRed.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = inv.code,
                                        color = if (usable) palette.textPrimary else palette.textMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = (if (usable) DiscordGreen else DiscordRed).copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                inv.isRevoked -> "Révoquée"
                                                inv.isExpired() -> "Expirée"
                                                inv.isExhausted() -> "Épuisée"
                                                else -> "Active"
                                            },
                                            color = if (usable) DiscordGreen else DiscordRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val limitLabel = if (inv.maxUses == 0) "illimité" else "${inv.maxUses}"
                                Text(
                                    text = "Utilisations : ${inv.usesCount}/$limitLabel • Par ${inv.createdByName}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(inv.code))
                                copiedCode = inv.code
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier le code", tint = palette.accent)
                            }
                            if (canManageInvites && !inv.isRevoked) {
                                IconButton(
                                    onClick = { onRevokeInvite(inv.code) },
                                    modifier = Modifier.testTag("revoke_invite_${inv.code}")
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = "Révoquer l'invitation", tint = DiscordAmber)
                                }
                            }
                            if (canManageInvites) {
                                IconButton(
                                    onClick = { onDeleteInvite(inv.code) },
                                    modifier = Modifier.testTag("delete_invite_${inv.code}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer l'invitation", tint = DiscordRed)
                                }
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
fun CommunityRolesMatrixSheet(
    server: Server,
    canManageRoles: Boolean,
    onSaveRole: (name: String, colorHex: Long, secondaryHex: Long?, perms: Set<RolePermission>) -> Unit,
    onAssignMemberRole: (uid: String, role: UserRole, customRoleId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var roleName by remember { mutableStateOf("") }
    var colorHex by remember { mutableLongStateOf(0xFF5865F2L) }
    var selectedPerms by remember {
        mutableStateOf(setOf(RolePermission.SEND_MESSAGES, RolePermission.CONNECT_VOICE, RolePermission.POST_ANNOUNCEMENTS))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("community_roles_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 660.dp)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Rôles & Matrice de Permissions", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Définissez les permissions vérifiées côté application et Firestore.", color = palette.textMuted, fontSize = 12.sp)
            }

            if (canManageRoles) {
                item {
                    Surface(
                        color = palette.cardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = roleName,
                                onValueChange = { roleName = it },
                                label = { Text("Nom du rôle") },
                                placeholder = { Text("ex. Responsable Events") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("role_matrix_name_input")
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(0xFF5865F2L, 0xFFEB459EL, 0xFF23A55AL, 0xFFF0B232L, 0xFF00A8FCL, 0xFFED4245L)) { hex ->
                                    Box(
                                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(hex)).clickable { colorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (colorHex == hex) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            RolePermission.entries.filter { it != RolePermission.DELETE_COMMUNITY }.forEach { perm ->
                                val enabled = perm in selectedPerms
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(perm.label, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(perm.description, color = palette.textMuted, fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            selectedPerms = if (checked) selectedPerms + perm else selectedPerms - perm
                                        },
                                        modifier = Modifier.testTag("perm_switch_${perm.name}")
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onSaveRole(roleName, colorHex, null, selectedPerms)
                                    roleName = ""
                                },
                                enabled = roleName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("role_matrix_submit_btn")
                            ) {
                                Text("Créer ce rôle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Text("RÔLES ACTIFS (${server.customRoles.size})", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            items(server.customRoles, key = { it.id }) { role ->
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(role.colorHex)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(role.name, color = Color(role.colorHex), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = role.permissions.joinToString(" • ") { it.label },
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (canManageRoles) {
                item {
                    Text("ATTRIBUTION DES RÔLES", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                items(server.members, key = { "assign_${it.id}" }) { member ->
                    var expanded by remember { mutableStateOf(false) }
                    val isOwner = CommunityPermissions.isOwner(server, member.id)
                    val curRole = CommunityPermissions.effectiveRole(server, member.id)
                    Surface(color = palette.cardBg, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, palette.border)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.name, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(if (isOwner) "Propriétaire" else curRole.title, color = palette.textMuted, fontSize = 11.sp)
                            }
                            if (!isOwner) {
                                Box {
                                    TextButton(onClick = { expanded = true }) {
                                        Text("Changer", color = palette.accent, fontWeight = FontWeight.Bold)
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf(UserRole.ADMIN, UserRole.MODERATOR, UserRole.MEMBER).forEach { r ->
                                            DropdownMenuItem(
                                                text = { Text(r.title) },
                                                onClick = {
                                                    onAssignMemberRole(member.id, r, null)
                                                    expanded = false
                                                }
                                            )
                                        }
                                        server.customRoles.forEach { cr ->
                                            DropdownMenuItem(
                                                text = { Text("Personnalisé : ${cr.name}", color = Color(cr.colorHex)) },
                                                onClick = {
                                                    val mapped = when {
                                                        cr.isAdmin -> UserRole.ADMIN
                                                        cr.canKickBan -> UserRole.MODERATOR
                                                        else -> UserRole.MEMBER
                                                    }
                                                    onAssignMemberRole(member.id, mapped, cr.id)
                                                    expanded = false
                                                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityCategoriesSheet(
    server: Server,
    canManageChannels: Boolean,
    onCreateCategory: (name: String, isPrivate: Boolean) -> Unit,
    onDeleteCategory: (name: String) -> Unit,
    onCreateChannel: (name: String, type: ChannelType, categoryName: String, isPrivate: Boolean) -> Unit,
    onDeleteChannel: (channelId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    var catName by remember { mutableStateOf("") }
    var chName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ChannelType.TEXT) }
    val allCatNames = remember(server.categories, server.channels) {
        (server.categories.map { it.name } + server.channels.map { it.category }).distinct().ifEmpty { listOf("SALONS TEXTUELS", "SALONS VOCAUX") }
    }
    var selectedCategory by remember(allCatNames) { mutableStateOf(allCatNames.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("community_categories_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 660.dp)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Catégories & Salons", color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (canManageChannels) {
                item {
                    Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("NOUVELLE CATÉGORIE", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = catName,
                                    onValueChange = { catName = it },
                                    placeholder = { Text("ex. PROJETS") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("new_category_input")
                                )
                                Button(
                                    onClick = {
                                        onCreateCategory(catName, false)
                                        selectedCategory = catName.trim().uppercase()
                                        catName = ""
                                    },
                                    enabled = catName.isNotBlank(),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("create_category_submit")
                                ) {
                                    Text("Ajouter")
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("NOUVEAU SALON", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = chName,
                                onValueChange = { chName = it },
                                label = { Text("Nom du salon") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("new_channel_in_cat_input")
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf(ChannelType.TEXT to "Textuel", ChannelType.VOICE to "Vocal", ChannelType.ANNOUNCEMENT to "Annonce", ChannelType.STAGE to "Scène")) { (t, lbl) ->
                                    FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(lbl, fontSize = 11.sp) })
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(allCatNames) { cName ->
                                    FilterChip(selected = selectedCategory == cName, onClick = { selectedCategory = cName }, label = { Text(cName, fontSize = 11.sp) })
                                }
                            }
                            Button(
                                onClick = {
                                    onCreateChannel(chName, selectedType, selectedCategory, false)
                                    chName = ""
                                },
                                enabled = chName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create_channel_in_cat_submit")
                            ) {
                                Text("Créer le salon dans $selectedCategory", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(allCatNames, key = { "cat_mgr_$it" }) { categoryName ->
                val chs = server.channels.filter { it.category.equals(categoryName, ignoreCase = true) }
                Surface(color = palette.cardBg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, palette.border)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(categoryName, color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (canManageChannels && allCatNames.size > 1) {
                                IconButton(onClick = { onDeleteCategory(categoryName) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Supprimer la catégorie", tint = DiscordRed)
                                }
                            }
                        }
                        chs.forEach { ch ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (ch.type == ChannelType.VOICE) Icons.Default.VolumeUp else Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = palette.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(ch.name, color = palette.textPrimary, fontSize = 13.sp)
                                }
                                if (canManageChannels && server.channels.size > 1) {
                                    IconButton(onClick = { onDeleteChannel(ch.id) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Supprimer le salon", tint = DiscordRed, modifier = Modifier.size(18.dp))
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
