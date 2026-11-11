package com.example.ui.components

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import coil.compose.AsyncImage
import com.example.R
import com.example.model.User
import com.example.model.UserRole
import com.example.model.UserStatus
import com.example.ui.theme.*
import com.example.util.CloudinaryHelper
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDialog(
    user: User,
    isCurrentUser: Boolean = true,
    onDismiss: () -> Unit,
    onUpdateStatus: (UserStatus, String) -> Unit = { _, _ -> },
    onSaveFullProfile: (
        name: String,
        pronouns: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        bannerUrl: String?,
        bannerColor: Long,
        customStatus: String,
        customStatusEmoji: String,
        activity: String,
        email: String,
        phone: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onStartDirectMessage: ((User) -> Unit)? = null,
    onToggleBadge: (String) -> Unit = {},
    onOpenOrbsShop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Profil, 1: Compte & Sécurité
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSuperBadgesDialog by remember { mutableStateOf(false) }
    var showCloudinaryDialog by remember { mutableStateOf(false) }
    var showStatusPicker by remember { mutableStateOf(false) }

    var authUser by remember {
        mutableStateOf(try { Firebase.auth.currentUser } catch (e: Exception) { null })
    }
    var isAuthLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DiscordDarkSidebar,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 36.dp, start = 16.dp, end = 16.dp)
        ) {
            // Discord Profile Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DiscordDarkCard,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    // Profile Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(Color(user.bannerColor))
                    ) {
                        if (!user.bannerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = user.bannerUrl,
                                contentDescription = "Bannière",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Top Badges Row (Super Badges - Clickable to open showcase)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x99000000))
                                .clickable { showSuperBadgesDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val equippedBadges = com.example.model.SuperBadgesCatalog.allBadges.filter { user.badges.contains(it.id) }
                            if (equippedBadges.isNotEmpty()) {
                                equippedBadges.forEach { badge ->
                                    SuperBadgeMedallion(
                                        iconKey = badge.iconKey,
                                        rarity = badge.rarity,
                                        size = 22.dp,
                                        iconSize = 13.dp
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = "Super Badges",
                                    tint = DiscordTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Avatar + Profile Identification
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Avatar overlapping banner
                            Box(modifier = Modifier.offset(y = (-30).dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .border(4.dp, DiscordDarkCard, CircleShape)
                                        .clip(CircleShape)
                                        .background(Color(user.avatarColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!user.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = "Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = user.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Status badge
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = (-2).dp, y = (-2).dp)
                                        .border(3.dp, DiscordDarkCard, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            when (user.status) {
                                                UserStatus.ONLINE -> DiscordGreen
                                                UserStatus.IDLE -> DiscordAmber
                                                UserStatus.DND -> DiscordRed
                                                UserStatus.OFFLINE -> DiscordTextDark
                                            }
                                        )
                                )
                                AvatarDecorationOverlay(
                                    decorationKey = user.avatarDecoration,
                                    size = 80.dp
                                )
                            }

                            // Quick Action Buttons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrentUser) {
                                    // Orbs shop quick button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DiscordDarkInput,
                                        modifier = Modifier
                                            .clickable {
                                                onOpenOrbsShop()
                                                onDismiss()
                                            }
                                            .padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Diamond, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${user.orbsBalance}", color = DiscordTextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = { showEditProfileDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("edit_profile_open_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Modifier", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                } else if (onStartDirectMessage != null) {
                                Button(
                                    onClick = {
                                        onStartDirectMessage(user)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Message", fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    // Name & Identity Info
                    Column(modifier = Modifier.offset(y = (-14).dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.name,
                                    color = DiscordTextWhite,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (user.pronouns.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = DiscordDarkInput,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = user.pronouns,
                                            color = DiscordTextMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "${user.name.lowercase().replace(" ", "")}${user.tag}",
                                color = DiscordTextMuted,
                                fontSize = 13.sp
                            )

                            // Custom Status pill
                            if (user.customStatus.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = DiscordDarkInput,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppStatusIcon(iconKey = user.customStatusEmoji, size = 16.dp, tint = DiscordBlurple)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = user.customStatus,
                                            color = DiscordTextLight,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation Tabs (Profil vs Compte & Paramètres)
            if (isCurrentUser) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = DiscordBlurple,
                    divider = { HorizontalDivider(color = DiscordDarkBorder) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Profil & Infos",
                                color = if (selectedTab == 0) DiscordTextWhite else DiscordTextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Compte & Sécurité",
                                color = if (selectedTab == 1) DiscordTextWhite else DiscordTextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (selectedTab == 0) {
                // Section: À propos de moi (Bio)
                ProfileSectionCard(title = "À PROPOS DE MOI") {
                    Text(
                        text = user.bio.ifBlank { "Aucune description fournie pour le moment." },
                        color = DiscordTextLight,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section: Activité en cours
                if (user.activity.isNotBlank()) {
                    ProfileSectionCard(title = "ACTIVITÉ ACTUELLE") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = DiscordGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user.activity,
                                    color = DiscordTextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Depuis 45 minutes",
                                    color = DiscordTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Section: Rôles
                ProfileSectionCard(title = "RÔLES SUR CE SERVEUR") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoleChip(
                            title = user.role.title,
                            color = when (user.role) {
                                UserRole.ADMIN -> DiscordRed
                                UserRole.MODERATOR -> DiscordBlurple
                                else -> DiscordTextMuted
                            }
                        )
                        RoleChip(title = "Membre Certifié", color = DiscordGreen)
                        RoleChip(title = "Raven", color = DiscordDarkBorder)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section: Super Badges
                ProfileSectionCard(title = "SUPER BADGES DISCORD") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val equippedList = com.example.model.SuperBadgesCatalog.allBadges.filter { user.badges.contains(it.id) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                equippedList.forEach { badge ->
                                    SuperBadgeMedallion(
                                        iconKey = badge.iconKey,
                                        rarity = badge.rarity,
                                        size = 32.dp,
                                        iconSize = 18.dp
                                    )
                                }
                            }

                            TextButton(
                                onClick = { showSuperBadgesDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Ouvrir la vitrine", color = DiscordBlurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section: Membre depuis
                ProfileSectionCard(title = "MEMBRE DEPUIS") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Raven", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = user.memberSince, color = DiscordTextWhite, fontSize = 13.sp)
                        }
                        Column {
                            Text(text = "Serveur actuel", color = DiscordTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = user.memberSince, color = DiscordTextWhite, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Section: Compte & Coordonnées
                ProfileSectionCard(title = "INFORMATIONS DU COMPTE") {
                    AccountInfoRow(label = "Nom d'utilisateur", value = "${user.name}${user.tag}")
                    HorizontalDivider(color = DiscordDarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    AccountInfoRow(label = "E-mail", value = user.email ?: "Non renseigné", isVerified = user.email != null)
                    HorizontalDivider(color = DiscordDarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                    AccountInfoRow(label = "Téléphone", value = user.phone ?: "Non renseigné", isVerified = user.phone != null)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section: Firebase Cloud Sync & Google Auth
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DiscordDarkCard
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = if (authUser != null) DiscordGreen else DiscordTextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (authUser != null) "Compte Google Connecté" else "Synchronisation Firebase",
                                        color = DiscordTextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (authUser != null) (authUser?.email ?: "Connecté") else "Sauvegarder profil & messages en cloud",
                                        color = DiscordTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (authUser == null) {
                                Button(
                                    onClick = {
                                        isAuthLoading = true
                                        coroutineScope.launch {
                                            try {
                                                val clientId = context.getString(R.string.default_web_client_id)
                                                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                                                val result = credentialManager.getCredential(context as Activity, request)
                                                val credential = result.credential
                                                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                                    val authCredential = GoogleAuthProvider.getCredential(token, null)
                                                    Firebase.auth.signInWithCredential(authCredential).await()
                                                    authUser = Firebase.auth.currentUser
                                                }
                                            } catch (e: GetCredentialCancellationException) {
                                                Log.w("Auth", "User cancelled Google sign in", e)
                                            } catch (e: Exception) {
                                                Log.e("Auth", "Sign in error", e)
                                            } finally {
                                                isAuthLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isAuthLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(if (isAuthLoading) "..." else "Se connecter", fontSize = 12.sp, color = Color.White)
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        try {
                                            Firebase.auth.signOut()
                                        } catch (e: Exception) {
                                            Log.w("Auth", "Sign out error", e)
                                        }
                                        authUser = null
                                    }
                                ) {
                                    Text("Déconnexion", color = DiscordRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Presence status quick switcher
                ProfileSectionCard(title = "STATUT DE PRÉSENCE") {
                    StatusOptionTile(
                        title = "En ligne",
                        color = DiscordGreen,
                        isSelected = user.status == UserStatus.ONLINE,
                        onClick = { onUpdateStatus(UserStatus.ONLINE, user.customStatus) }
                    )
                    StatusOptionTile(
                        title = "Inactif",
                        color = DiscordAmber,
                        isSelected = user.status == UserStatus.IDLE,
                        onClick = { onUpdateStatus(UserStatus.IDLE, user.customStatus) }
                    )
                    StatusOptionTile(
                        title = "Ne pas déranger",
                        color = DiscordRed,
                        isSelected = user.status == UserStatus.DND,
                        onClick = { onUpdateStatus(UserStatus.DND, user.customStatus) }
                    )
                    StatusOptionTile(
                        title = "Invisible",
                        color = DiscordTextDark,
                        isSelected = user.status == UserStatus.OFFLINE,
                        onClick = { onUpdateStatus(UserStatus.OFFLINE, user.customStatus) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cloudinary Config Option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCloudinaryDialog = true },
                    color = DiscordDarkCard
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = DiscordBlurple)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Hébergeur Cloudinary", color = DiscordTextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Cloud: ${CloudinaryHelper.cloudName}", color = DiscordTextMuted, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DiscordTextMuted)
                    }
                }
            }
        }
    }

    // Modal Edit Profile
    if (showEditProfileDialog) {
        EditProfileDialog(
            user = user,
            onDismiss = { showEditProfileDialog = false },
            onSaveProfile = { name, pronouns, bio, avUrl, avCol, banUrl, banCol, statusMsg, emoji, activity, email, phone ->
                onSaveFullProfile(
                    name, pronouns, bio, avUrl, avCol, banUrl, banCol, statusMsg, emoji, activity, email, phone
                )
                showEditProfileDialog = false
            }
        )
    }

    // Modal Super Badges Showcase
    if (showSuperBadgesDialog) {
        SuperBadgesDialog(
            equippedBadgeIds = user.badges,
            onDismiss = { showSuperBadgesDialog = false },
            onToggleEquipBadge = { badgeId ->
                onToggleBadge(badgeId)
            }
        )
    }

    // Cloudinary Dialog
    if (showCloudinaryDialog) {
        var cloudNameInput by remember { mutableStateOf(CloudinaryHelper.cloudName) }
        var presetInput by remember { mutableStateOf(CloudinaryHelper.uploadPreset) }

        AlertDialog(
            onDismissRequest = { showCloudinaryDialog = false },
            containerColor = DiscordDarkCard,
            title = { Text("Configuration Cloudinary", color = DiscordTextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Entrez vos paramètres Cloudinary pour héberger photos d'avatar, bannières et pièces jointes :",
                        color = DiscordTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cloudNameInput,
                        onValueChange = { cloudNameInput = it },
                        label = { Text("Cloud Name", color = DiscordTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DiscordDarkInput,
                            unfocusedContainerColor = DiscordDarkInput,
                            focusedBorderColor = DiscordBlurple,
                            focusedTextColor = DiscordTextWhite,
                            unfocusedTextColor = DiscordTextLight
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = presetInput,
                        onValueChange = { presetInput = it },
                        label = { Text("Upload Preset (unsigned)", color = DiscordTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DiscordDarkInput,
                            unfocusedContainerColor = DiscordDarkInput,
                            focusedBorderColor = DiscordBlurple,
                            focusedTextColor = DiscordTextWhite,
                            unfocusedTextColor = DiscordTextLight
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        CloudinaryHelper.cloudName = cloudNameInput.trim()
                        CloudinaryHelper.uploadPreset = presetInput.trim()
                        showCloudinaryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple)
                ) {
                    Text("Enregistrer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudinaryDialog = false }) {
                    Text("Annuler", color = DiscordTextMuted)
                }
            }
        )
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DiscordDarkCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = DiscordTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun BadgeItem(
    icon: ImageVector,
    tint: Color,
    tooltip: String
) {
    Surface(
        shape = CircleShape,
        color = Color(0x33FFFFFF),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun RoleChip(
    title: String,
    color: Color
) {
    Surface(
        color = DiscordDarkInput,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = title, color = DiscordTextLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    isVerified: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = DiscordTextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, color = DiscordTextWhite, fontSize = 13.sp)
        }
        if (isVerified) {
            Surface(
                color = DiscordGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Vérifié",
                    color = DiscordGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusOptionTile(
    title: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = if (isSelected) DiscordTextWhite else DiscordTextLight,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = DiscordBlurple, modifier = Modifier.size(16.dp))
        }
    }
}
