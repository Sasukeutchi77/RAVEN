package com.example.ui.components

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import coil.compose.AsyncImage
import com.example.data.AccessCodeStrength
import com.example.data.AuthEntryIntent
import com.example.data.AuthGateRepository
import com.example.data.AuthGateUiState
import com.example.data.AuthOnboardingPhase
import com.example.data.RememberedGmailAccount
import com.example.model.UserStatus
import com.example.ui.theme.DiscordAmber
import com.example.ui.theme.DiscordBlurple
import com.example.ui.theme.DiscordGreen
import com.example.ui.theme.DiscordRed
import com.example.ui.theme.RavenBodyFontFamily
import com.example.ui.theme.RavenDisplayFontFamily
import com.example.ui.theme.ravenPalette
import com.example.util.CloudinaryHelper
import com.example.util.FirebaseConfigManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

@Composable
fun RavenAuthAndOnboardingScreen(
    uiState: AuthGateUiState,
    onAuthIntentChange: (AuthEntryIntent) -> Unit,
    onGmailInputChange: (String) -> Unit,
    onSelectRememberedAccount: (RememberedGmailAccount) -> Unit,
    onRemoveRememberedAccount: (String) -> Unit,
    onAuthError: (String?) -> Unit,
    onClearLockoutIfExpired: () -> Unit,
    onGmailCredentialSignedIn: (prefilledCode: String, isSilentAutoSignIn: Boolean) -> Unit,
    onVerifyAccessCode: (String) -> Unit,
    onGmailReauthConfirmedForReset: () -> Unit,
    onCancelCodeReset: () -> Unit,
    onApplyResetCode: (newCode: String, confirmCode: String) -> Unit,
    onCompleteAccountCreation: (
        name: String,
        tag: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        status: UserStatus,
        accessCode: String,
        confirmCode: String,
        requireCodeOnAppLaunch: Boolean
    ) -> Unit,
    biometricUnlockEnabled: Boolean = false,
    onBiometricUnlocked: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val googleWebClientId = remember(context) { resolveDefaultWebClientId(context) }
    var isCredentialBusy by remember { mutableStateOf(false) }
    var attemptedSilentAutoSignIn by remember { mutableStateOf(false) }
    var prefillCode by rememberSaveable { mutableStateOf("") }

    // Silent startup Credential Manager attempt (separate single-option request for API 36 safety)
    LaunchedEffect(uiState.phase, uiState.skipSilentAutoSignIn, googleWebClientId) {
        val firebaseAuth = FirebaseConfigManager.getAuthInstance(context)
        if (uiState.phase == AuthOnboardingPhase.SIGN_IN_GMAIL &&
            !uiState.skipSilentAutoSignIn &&
            !attemptedSilentAutoSignIn &&
            googleWebClientId != null &&
            firebaseAuth != null &&
            firebaseAuth.currentUser == null
        ) {
            attemptedSilentAutoSignIn = true
            val activity = context as? Activity ?: return@LaunchedEffect
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(googleWebClientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val silentReq = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val res = credentialManager.getCredential(activity, silentReq)
                val cred = res.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                    firebaseAuth.signInWithCredential(firebaseCred).await()
                    onGmailCredentialSignedIn(prefillCode, true)
                }
            } catch (e: Exception) {
                Log.d("Auth", "Silent auto-sign-in skipped or unavailable: ${e.message}")
            }
        }
    }

    val launchGmailSignIn: (String?) -> Unit = { explicitGmail ->
        val typedGmail = (explicitGmail ?: uiState.gmailInput).trim()
        if (explicitGmail != null) {
            onGmailInputChange(explicitGmail)
        }
        if (typedGmail.isNotBlank() && !AuthGateRepository.isValidGmailAddress(typedGmail)) {
            onAuthError("Adresse invalide : utilisez uniquement une adresse @gmail.com.")
        } else if (googleWebClientId == null) {
            onAuthError("Connexion Gmail indisponible : ajoutez app/google-services.json ou FIREBASE_WEB_CLIENT_ID dans Secrets/.env.")
        } else {
            onAuthError(null)
            isCredentialBusy = true
            scope.launch {
                try {
                    val activity = context as? Activity
                    val firebaseAuth = FirebaseConfigManager.getAuthInstance(context)
                    if (activity == null || firebaseAuth == null) {
                        onAuthError("Impossible d'ouvrir la fenêtre de connexion Gmail.")
                        return@launch
                    }
                    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = googleWebClientId).build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()
                    val result = credentialManager.getCredential(activity, request)
                    val cred = result.credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                        firebaseAuth.signInWithCredential(firebaseCred).await()
                        onGmailCredentialSignedIn(prefillCode, false)
                    } else {
                        onAuthError("Identifiant Google non reconnu.")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("Auth", "Gmail sign in error", e)
                    onAuthError("Échec de connexion Gmail : ${e.localizedMessage ?: "erreur inconnue"}")
                } finally {
                    isCredentialBusy = false
                }
            }
        }
    }

    val launchGmailReauthForCodeReset: () -> Unit = {
        val clientId = googleWebClientId
        if (clientId == null) {
            onAuthError("Ré-authentification Gmail indisponible : ajoutez app/google-services.json ou FIREBASE_WEB_CLIENT_ID.")
        } else {
            onAuthError(null)
            isCredentialBusy = true
            scope.launch {
                try {
                    val activity = context as? Activity
                    val user = FirebaseConfigManager.getAuthInstance(context)?.currentUser
                    if (activity == null || user == null) {
                        onAuthError("Session Gmail introuvable.")
                        return@launch
                    }
                    val opt = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                    val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
                    val res = credentialManager.getCredential(activity, req)
                    val cred = res.credential
                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                        user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
                        onGmailReauthConfirmedForReset()
                    } else {
                        onAuthError("Impossible de confirmer le compte Gmail.")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("Auth", "Gmail reauth cancelled: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("Auth", "Gmail reauth error", e)
                    onAuthError("Vérification Gmail échouée : ${e.localizedMessage ?: "erreur"}")
                } finally {
                    isCredentialBusy = false
                }
            }
        }
    }

    val handleSwitchAccount: () -> Unit = {
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
            }
            prefillCode = ""
            onSignOut()
        }
    }

    val effectiveBusy = uiState.isBusy || isCredentialBusy

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070912))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF312E81).copy(alpha = 0.44f),
                            Color(0xFF101428),
                            Color(0xFF060811)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.maxDimension * 0.78f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0EA5E9).copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.82f),
                        radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f,
                    center = Offset(size.width * 0.85f, size.height * 0.82f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("raven_auth_gate"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = uiState.phase,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "auth_phase_transition"
            ) { phase ->
                when (phase) {
                    AuthOnboardingPhase.CHECKING_ACCOUNT, AuthOnboardingPhase.READY -> {
                        CheckingAccountCard()
                    }

                    AuthOnboardingPhase.SIGN_IN_GMAIL -> {
                        SignInGmailCard(
                            authIntent = uiState.authIntent,
                            gmailInput = uiState.gmailInput,
                            prefillCode = prefillCode,
                            rememberedAccounts = uiState.rememberedAccounts,
                            isBusy = effectiveBusy,
                            googleConfigured = googleWebClientId != null,
                            errorMessage = uiState.errorMessage,
                            infoMessage = uiState.infoMessage,
                            onAuthIntentChange = { intent ->
                                if (intent == AuthEntryIntent.CREATE_ACCOUNT) {
                                    prefillCode = ""
                                }
                                onAuthIntentChange(intent)
                            },
                            onGmailChange = onGmailInputChange,
                            onPrefillCodeChange = { raw ->
                                if (raw.length <= 8 && raw.all { it.isDigit() }) {
                                    prefillCode = raw
                                }
                            },
                            onSelectRememberedAndContinue = { account ->
                                onSelectRememberedAccount(account)
                                launchGmailSignIn(account.email)
                            },
                            onRemoveRemembered = onRemoveRememberedAccount,
                            onContinueWithGmail = { launchGmailSignIn(null) }
                        )
                    }

                    AuthOnboardingPhase.VERIFY_CODE -> {
                        VerifyCodeCard(
                            verifiedEmail = uiState.verifiedEmail.orEmpty(),
                            displayName = uiState.suggestedName,
                            avatarUrl = uiState.suggestedAvatarUrl,
                            failedAttempts = uiState.failedCodeAttempts,
                            cooldownUntilMs = uiState.cooldownUntilMs,
                            failedNonce = uiState.lastCodeFailedNonce,
                            isBusy = effectiveBusy,
                            errorMessage = uiState.errorMessage,
                            infoMessage = uiState.infoMessage,
                            biometricEnabled = biometricUnlockEnabled,
                            onClearLockoutIfExpired = onClearLockoutIfExpired,
                            onVerifyCode = onVerifyAccessCode,
                            onBiometricUnlock = onBiometricUnlocked,
                            onAuthError = onAuthError,
                            onForgotCode = launchGmailReauthForCodeReset,
                            onSwitchAccount = handleSwitchAccount
                        )
                    }

                    AuthOnboardingPhase.RESET_CODE -> {
                        ResetCodeCard(
                            verifiedEmail = uiState.verifiedEmail.orEmpty(),
                            isBusy = effectiveBusy,
                            errorMessage = uiState.errorMessage,
                            infoMessage = uiState.infoMessage,
                            onSubmitReset = onApplyResetCode,
                            onCancel = onCancelCodeReset
                        )
                    }

                    AuthOnboardingPhase.CREATE_PROFILE -> {
                        CreateAccountProfileCard(
                            verifiedEmail = uiState.verifiedEmail.orEmpty(),
                            initialName = uiState.suggestedName,
                            initialTag = uiState.suggestedTag,
                            initialAvatarUrl = uiState.suggestedAvatarUrl,
                            isBusy = effectiveBusy,
                            errorMessage = uiState.errorMessage,
                            infoMessage = uiState.infoMessage,
                            onSubmit = onCompleteAccountCreation,
                            onSwitchAccount = handleSwitchAccount
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Raven • Accès protégé Gmail + Code chiffré PBKDF2-SHA256",
                fontFamily = RavenBodyFontFamily,
                color = palette.textMuted.copy(alpha = 0.72f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CheckingAccountCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF111629).copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Color(0xFF2B355C))
    ) {
        Column(
            modifier = Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF38BDF8))
            Text(
                text = "Vérification de votre session Gmail...",
                fontFamily = RavenDisplayFontFamily,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Chargement du profil chiffré et du code de sécurité",
                fontFamily = RavenBodyFontFamily,
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SignInGmailCard(
    authIntent: AuthEntryIntent,
    gmailInput: String,
    prefillCode: String,
    rememberedAccounts: List<RememberedGmailAccount>,
    isBusy: Boolean,
    googleConfigured: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onAuthIntentChange: (AuthEntryIntent) -> Unit,
    onGmailChange: (String) -> Unit,
    onPrefillCodeChange: (String) -> Unit,
    onSelectRememberedAndContinue: (RememberedGmailAccount) -> Unit,
    onRemoveRemembered: (String) -> Unit,
    onContinueWithGmail: () -> Unit
) {
    var codeVisible by remember { mutableStateOf(false) }
    val typedValidOrEmpty = gmailInput.isBlank() || AuthGateRepository.isValidGmailAddress(gmailInput)
    val isCreateMode = authIntent == AuthEntryIntent.CREATE_ACCOUNT

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF111628).copy(alpha = 0.95f),
        border = BorderStroke(
            1.dp,
            if (isCreateMode) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF2D365E)
        ),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode Chooser: Se connecter vs Créer un nouveau compte
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0A0E1B),
                border = BorderStroke(1.dp, Color(0xFF232B4A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AuthIntentTabButton(
                        label = "Se connecter",
                        icon = Icons.AutoMirrored.Filled.Login,
                        selected = !isCreateMode,
                        accentColor = DiscordBlurple,
                        enabled = !isBusy,
                        onClick = { onAuthIntentChange(AuthEntryIntent.SIGN_IN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_sign_in_tab")
                    )
                    AuthIntentTabButton(
                        label = "Nouveau compte",
                        icon = Icons.Default.PersonAdd,
                        selected = isCreateMode,
                        accentColor = DiscordGreen,
                        enabled = !isBusy,
                        onClick = { onAuthIntentChange(AuthEntryIntent.CREATE_ACCOUNT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_mode_create_account_tab")
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (isCreateMode) DiscordGreen.copy(alpha = 0.16f) else Color(0xFF1E264A),
                    border = BorderStroke(
                        1.dp,
                        if (isCreateMode) DiscordGreen.copy(alpha = 0.55f) else Color(0xFF6366F1).copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCreateMode) Icons.Default.PersonAdd else Icons.Default.MailOutline,
                            contentDescription = if (isCreateMode) "Créer un compte Gmail" else "Connexion Gmail",
                            tint = if (isCreateMode) DiscordGreen else Color(0xFF7DD3FC),
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isCreateMode) "Créer un nouveau compte" else "Se connecter à Raven",
                        fontFamily = RavenDisplayFontFamily,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCreateMode) {
                            "Inscription rapide avec votre adresse Gmail"
                        } else {
                            "Déjà inscrit ? Connectez-vous simplement"
                        },
                        fontFamily = RavenBodyFontFamily,
                        color = if (isCreateMode) Color(0xFF6EE7B7) else Color(0xFF7DD3FC),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!infoMessage.isNullOrBlank()) AuthBanner(text = infoMessage, isError = false)
            if (!errorMessage.isNullOrBlank()) AuthBanner(text = errorMessage, isError = true)

            if (!isCreateMode && rememberedAccounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Connexion rapide (comptes récents)",
                        fontFamily = RavenBodyFontFamily,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(rememberedAccounts, key = { it.uid }) { account ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF171E38),
                                border = BorderStroke(
                                    1.dp,
                                    if (gmailInput.equals(account.email, ignoreCase = true)) {
                                        Color(0xFF38BDF8)
                                    } else {
                                        Color(0xFF2B355C)
                                    }
                                ),
                                modifier = Modifier
                                    .widthIn(min = 210.dp, max = 260.dp)
                                    .clickable(enabled = !isBusy) {
                                        onSelectRememberedAndContinue(account)
                                    }
                                    .testTag("remembered_gmail_${account.uid}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(DiscordBlurple),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!account.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = account.avatarUrl,
                                                contentDescription = account.displayName,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = account.displayName.take(1).uppercase().ifBlank { "R" },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.displayName,
                                            fontFamily = RavenBodyFontFamily,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = account.email,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveRemembered(account.uid) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Retirer ce compte mémorisé",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = gmailInput,
                onValueChange = onGmailChange,
                label = {
                    Text(if (isCreateMode) "Adresse Gmail pour le compte (@gmail.com)" else "Adresse Gmail (@gmail.com)")
                },
                placeholder = { Text("prenom.nom@gmail.com") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                isError = !typedValidOrEmpty,
                supportingText = if (!typedValidOrEmpty) {
                    { Text("Uniquement une adresse @gmail.com", color = DiscordRed) }
                } else {
                    { Text("Optionnel : laissez vide pour choisir dans votre compte Google", color = Color(0xFF94A3B8)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_gmail_input")
            )

            if (!isCreateMode) {
                OutlinedTextField(
                    value = prefillCode,
                    onValueChange = onPrefillCodeChange,
                    label = { Text("Code personnel (optionnel ici)") },
                    placeholder = { Text("4 à 8 chiffres pour entrer directement") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = { codeVisible = !codeVisible },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (codeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (codeVisible) "Masquer le code" else "Afficher le code"
                            )
                        }
                    },
                    visualTransformation = if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = authTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_prefill_code_input")
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0C1020),
                    border = BorderStroke(1.dp, Color(0xFF202847))
                ) {
                    Column(
                        modifier = Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Votre inscription en 2 étapes simples :",
                            fontFamily = RavenBodyFontFamily,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "1. Validez votre adresse @gmail.com via Google",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "2. Ajoutez votre nom, photo de profil et code secret",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Button(
                onClick = onContinueWithGmail,
                enabled = !isBusy && googleConfigured && typedValidOrEmpty,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCreateMode) DiscordGreen else DiscordBlurple
                ),
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(vertical = 15.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag(if (isCreateMode) "auth_gmail_create_button" else "auth_gmail_continue_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Icon(
                        imageVector = if (isCreateMode) Icons.Default.PersonAdd else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = when {
                        isBusy -> "Authentification Gmail..."
                        isCreateMode -> "Créer un nouveau compte"
                        else -> "Se connecter avec Gmail"
                    },
                    fontFamily = RavenBodyFontFamily,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!googleConfigured) {
                AuthBanner(
                    text = "Configuration Google Sign-In absente : ajoutez app/google-services.json (OAuth Web) ou FIREBASE_WEB_CLIENT_ID dans Secrets/.env.",
                    isError = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCreateMode) "Vous avez déjà un compte ?" else "Pas encore de compte ?",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = {
                        onAuthIntentChange(
                            if (isCreateMode) AuthEntryIntent.SIGN_IN else AuthEntryIntent.CREATE_ACCOUNT
                        )
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(if (isCreateMode) "auth_switch_to_signin_button" else "auth_switch_to_create_button")
                ) {
                    Text(
                        text = if (isCreateMode) "Se connecter" else "Créer un compte",
                        color = if (isCreateMode) Color(0xFF7DD3FC) else Color(0xFF6EE7B7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthIntentTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentColor.copy(alpha = 0.24f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = RavenBodyFontFamily,
                color = if (selected) Color.White else Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VerifyCodeCard(
    verifiedEmail: String,
    displayName: String,
    avatarUrl: String?,
    failedAttempts: Int,
    cooldownUntilMs: Long,
    failedNonce: Int,
    isBusy: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    biometricEnabled: Boolean,
    onClearLockoutIfExpired: () -> Unit,
    onVerifyCode: (String) -> Unit,
    onBiometricUnlock: () -> Unit,
    onAuthError: (String?) -> Unit,
    onForgotCode: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val context = LocalContext.current
    val biometricSupported = remember(context) { com.example.util.BiometricAuthHelper.isBiometricAvailable(context) }
    var code by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(cooldownUntilMs) {
        if (cooldownUntilMs > 0L) {
            while (true) {
                nowMs = System.currentTimeMillis()
                if (nowMs >= cooldownUntilMs) {
                    onClearLockoutIfExpired()
                    break
                }
                delay(1000L)
            }
        }
    }

    LaunchedEffect(failedNonce) {
        if (failedNonce > 0) {
            code = ""
            listOf(-18f, 18f, -12f, 12f, -6f, 6f, 0f).forEach { target ->
                shakeOffset.animateTo(target, tween(durationMillis = 45, easing = FastOutSlowInEasing))
            }
        }
    }

    val cooldownSecondsLeft = ((cooldownUntilMs - nowMs) / 1000L).coerceAtLeast(0L)
    val isLockedOut = cooldownUntilMs > nowMs && cooldownSecondsLeft > 0L

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF111628).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, if (isLockedOut) DiscordRed.copy(alpha = 0.55f) else Color(0xFF2D365E))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DiscordBlurple)
                    .border(2.dp, Color(0xFF38BDF8).copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Photo de profil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase().ifBlank { "R" },
                        fontFamily = RavenDisplayFontFamily,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayName.ifBlank { "Compte Raven" },
                    fontFamily = RavenDisplayFontFamily,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                VerifiedGmailBadge(email = verifiedEmail)
            }

            if (isLockedOut) {
                AuthBanner(
                    text = "Sécurité activée : nouvel essai autorisé dans ${cooldownSecondsLeft}s.",
                    isError = true
                )
            } else {
                if (!infoMessage.isNullOrBlank()) AuthBanner(text = infoMessage, isError = false)
                if (!errorMessage.isNullOrBlank()) AuthBanner(text = errorMessage, isError = true)
            }

            // Visual PIN Slot Indicators
            CodeSlotsBar(
                code = code,
                visible = visible,
                slotCount = maxOf(6, code.length),
                isError = !errorMessage.isNullOrBlank() || isLockedOut
            )

            // Keyboard-accessible synchronized field + show/hide toggle
            OutlinedTextField(
                value = code,
                onValueChange = { raw ->
                    if (!isLockedOut && raw.length <= 8 && raw.all { it.isDigit() }) {
                        code = raw
                    }
                },
                enabled = !isBusy && !isLockedOut,
                label = { Text("Code personnel (4 à 8 chiffres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { visible = !visible },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Masquer" else "Afficher"
                        )
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_verify_code_input")
            )

            // Tactile Numeric Keypad
            TactileNumericKeypad(
                enabled = !isBusy && !isLockedOut,
                onDigit = { d -> if (code.length < 8) code += d },
                onBackspace = { if (code.isNotEmpty()) code = code.dropLast(1) },
                onClear = { code = "" }
            )

            if (failedAttempts > 0 && !isLockedOut) {
                Text(
                    text = "Essais échoués : $failedAttempts / 5",
                    color = DiscordAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = { onVerifyCode(code) },
                enabled = !isBusy && !isLockedOut && code.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("auth_verify_code_submit_button")
            ) {
                Text(
                    text = if (isLockedOut) "Patienter (${cooldownSecondsLeft}s)" else "Déverrouiller et entrer",
                    fontFamily = RavenBodyFontFamily,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (biometricEnabled && biometricSupported && !isLockedOut) {
                OutlinedButton(
                    onClick = {
                        com.example.util.BiometricAuthHelper.authenticate(
                            context = context,
                            title = "Déverrouiller Raven",
                            subtitle = "Utilisez votre empreinte ou reconnaissance faciale",
                            onSuccess = onBiometricUnlock,
                            onError = { err -> onAuthError(err) }
                        )
                    },
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("auth_biometric_unlock_button")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Déverrouiller par biométrie", color = Color(0xFF7DD3FC), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onForgotCode,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("auth_forgot_code_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Code oublié ?", fontSize = 12.sp)
                }

                TextButton(
                    onClick = onSwitchAccount,
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("auth_switch_gmail_button")
                ) {
                    Text("Changer de Gmail", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun CodeSlotsBar(
    code: String,
    visible: Boolean,
    slotCount: Int,
    isError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(slotCount.coerceIn(6, 8)) { idx ->
            val filled = idx < code.length
            val isCurrent = idx == code.length
            val borderColor by animateColorAsState(
                targetValue = when {
                    isError -> DiscordRed
                    isCurrent -> Color(0xFF38BDF8)
                    filled -> Color(0xFF818CF8)
                    else -> Color(0xFF2B355C)
                },
                label = "pin_slot_border"
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (filled) Color(0xFF1A2242) else Color(0xFF0C1020),
                border = BorderStroke(if (isCurrent || filled) 1.5.dp else 1.dp, borderColor),
                modifier = Modifier.size(width = 40.dp, height = 46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (filled) {
                        if (visible) {
                            Text(
                                text = code[idx].toString(),
                                fontFamily = RavenDisplayFontFamily,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7DD3FC))
                            )
                        }
                    } else if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 2.dp)
                                .background(Color(0xFF38BDF8))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileNumericKeypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = remember {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "DEL")
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when (key) {
                            "CLR", "DEL" -> Color(0xFF131932)
                            else -> Color(0xFF1A2242)
                        },
                        border = BorderStroke(1.dp, Color(0xFF2B3663)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = enabled) {
                                when (key) {
                                    "CLR" -> onClear()
                                    "DEL" -> onBackspace()
                                    else -> onDigit(key)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (key) {
                                "DEL" -> Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Effacer un chiffre",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(19.dp)
                                )
                                "CLR" -> Text(
                                    text = "Effacer",
                                    fontFamily = RavenBodyFontFamily,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                else -> Text(
                                    text = key,
                                    fontFamily = RavenDisplayFontFamily,
                                    color = Color.White,
                                    fontSize = 19.sp,
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

@Composable
private fun ResetCodeCard(
    verifiedEmail: String,
    isBusy: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onSubmitReset: (newCode: String, confirmCode: String) -> Unit,
    onCancel: () -> Unit
) {
    var newCode by rememberSaveable { mutableStateOf("") }
    var confirmCode by rememberSaveable { mutableStateOf("") }
    var showCodes by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF111628).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF2D365E))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Nouveau code de connexion",
                fontFamily = RavenDisplayFontFamily,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            VerifiedGmailBadge(email = verifiedEmail)

            if (!infoMessage.isNullOrBlank()) AuthBanner(text = infoMessage, isError = false)
            if (!errorMessage.isNullOrBlank()) AuthBanner(text = errorMessage, isError = true)

            OutlinedTextField(
                value = newCode,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) newCode = it },
                label = { Text("Nouveau code (4 à 8 chiffres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { showCodes = !showCodes },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (showCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showCodes) "Masquer" else "Afficher"
                        )
                    }
                },
                visualTransformation = if (showCodes) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_reset_code_input")
            )

            CodeStrengthMeter(code = newCode)

            OutlinedTextField(
                value = confirmCode,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmCode = it },
                label = { Text("Confirmer le nouveau code") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                visualTransformation = if (showCodes) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                colors = authTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_reset_code_confirm_input")
            )

            Button(
                onClick = { onSubmitReset(newCode, confirmCode) },
                enabled = !isBusy && newCode.length >= 4 && confirmCode.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("auth_reset_code_submit_button")
            ) {
                Text("Enregistrer le code", color = Color.White, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onCancel,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("auth_reset_code_cancel_button")
            ) {
                Text("Annuler", color = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun CreateAccountProfileCard(
    verifiedEmail: String,
    initialName: String,
    initialTag: String,
    initialAvatarUrl: String?,
    isBusy: Boolean,
    errorMessage: String?,
    infoMessage: String?,
    onSubmit: (
        name: String,
        tag: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        status: UserStatus,
        accessCode: String,
        confirmCode: String,
        requireCodeOnAppLaunch: Boolean
    ) -> Unit,
    onSwitchAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var wizardStep by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var tag by rememberSaveable(initialTag) { mutableStateOf(initialTag.ifBlank { "#1001" }) }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatarUrl by rememberSaveable(initialAvatarUrl) { mutableStateOf(initialAvatarUrl) }
    var avatarColor by rememberSaveable { mutableLongStateOf(0xFF5865F2L) }
    var selectedStatus by rememberSaveable { mutableStateOf(UserStatus.ONLINE) }
    var accessCode by rememberSaveable { mutableStateOf("") }
    var confirmCode by rememberSaveable { mutableStateOf("") }
    var requireCodeOnLaunch by rememberSaveable { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showCodes by remember { mutableStateOf(false) }

    val avatarColors = remember {
        listOf(
            0xFF5865F2L,
            0xFF10B981L,
            0xFFEC4899L,
            0xFFF59E0BL,
            0xFF06B6D4L,
            0xFF8B5CF6L,
            0xFFEF4444L,
            0xFF14B8A6L
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                val persisted = CloudinaryHelper.persistImage(context, uri, subDir = "profiles")
                avatarUrl = persisted
                isUploadingPhoto = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF111628).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF2D365E))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Créer votre compte",
                        fontFamily = RavenDisplayFontFamily,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VerifiedGmailBadge(email = verifiedEmail)
                }
                TextButton(
                    onClick = onSwitchAccount,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Changer", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }

            // Step indicator pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnboardingStepChip(
                    stepLabel = "1. Profil & Photo",
                    active = wizardStep == 0,
                    completed = wizardStep > 0 && name.trim().length >= 2,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { wizardStep = 0 }
                )
                OnboardingStepChip(
                    stepLabel = "2. Code d'accès",
                    active = wizardStep == 1,
                    completed = accessCode.length >= 4 && accessCode == confirmCode,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = name.trim().length >= 2) { wizardStep = 1 }
                )
            }

            if (!infoMessage.isNullOrBlank()) AuthBanner(text = infoMessage, isError = false)
            if (!errorMessage.isNullOrBlank()) AuthBanner(text = errorMessage, isError = true)

            // Live Profile Preview Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0C1020),
                border = BorderStroke(1.dp, Color(0xFF253055)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(46.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(avatarColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Aperçu avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = name.trim().take(1).uppercase().ifBlank { "R" },
                                    fontFamily = RavenDisplayFontFamily,
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(statusIndicatorColor(selectedStatus))
                                .border(2.dp, Color(0xFF0C1020), CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name.trim().ifBlank { "Votre pseudo" },
                                fontFamily = RavenBodyFontFamily,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tag.ifBlank { "#1001" },
                                color = Color(0xFF7DD3FC),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = bio.trim().ifBlank { selectedStatus.label },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (wizardStep == 0) {
                // Step 1: Photo + Color + Name/Tag/Bio/Status
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(avatarColor))
                            .border(2.5.dp, Color(0xFF38BDF8), CircleShape)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("onboarding_pick_photo_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Photo de profil choisie",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = name.trim().take(1).uppercase().ifBlank { "R" },
                                fontFamily = RavenDisplayFontFamily,
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0F172A),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isUploadingPhoto) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Choisir une photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Touchez pour importer votre photo",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        if (!avatarUrl.isNullOrBlank()) {
                            TextButton(
                                onClick = { avatarUrl = null },
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("Retirer", fontSize = 11.sp, color = DiscordRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(avatarColors) { colorHex ->
                            val selected = avatarColor == colorHex
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorHex))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .clickable { avatarColor = colorHex }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Nom / Pseudo *") },
                    placeholder = { Text("Ex. Alex Martin") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    colors = authTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { raw -> if (raw.length <= 10) tag = raw },
                        label = { Text("Tag") },
                        placeholder = { Text("#1234") },
                        trailingIcon = {
                            IconButton(
                                onClick = { tag = AuthGateRepository.generateRandomTag() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Générer un tag",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        colors = authTextFieldColors(),
                        modifier = Modifier
                            .weight(0.48f)
                            .testTag("onboarding_tag_input")
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { if (it.length <= 160) bio = it },
                        label = { Text("Bio (optionnel)") },
                        placeholder = { Text("Sur Raven !") },
                        singleLine = true,
                        colors = authTextFieldColors(),
                        modifier = Modifier
                            .weight(0.52f)
                            .testTag("onboarding_bio_input")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Statut initial",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(UserStatus.ONLINE, UserStatus.IDLE, UserStatus.DND)) { st ->
                            val active = selectedStatus == st
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) DiscordBlurple.copy(alpha = 0.25f) else Color(0xFF0E111D),
                                border = BorderStroke(
                                    1.dp,
                                    if (active) DiscordBlurple else Color(0xFF283052)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { selectedStatus = st }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(statusIndicatorColor(st))
                                    )
                                    Text(
                                        text = st.label,
                                        color = if (active) Color.White else Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { wizardStep = 1 },
                    enabled = !isBusy && !isUploadingPhoto && name.trim().length >= 2,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("onboarding_next_step_button")
                ) {
                    Text(
                        text = "Suivant : Code de sécurité",
                        fontFamily = RavenBodyFontFamily,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // Step 2: Access Code + Strength + Launch Lock Option
                OutlinedTextField(
                    value = accessCode,
                    onValueChange = { raw ->
                        if (raw.length <= 8 && raw.all { it.isDigit() }) accessCode = raw
                    },
                    label = { Text("Créer votre code d'accès (4–8 chiffres) *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = { showCodes = !showCodes },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showCodes) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showCodes) "Masquer" else "Afficher"
                            )
                        }
                    },
                    visualTransformation = if (showCodes) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = authTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_code_input")
                )

                CodeStrengthMeter(code = accessCode)

                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = { raw ->
                        if (raw.length <= 8 && raw.all { it.isDigit() }) confirmCode = raw
                    },
                    label = { Text("Confirmer le code d'accès *") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    visualTransformation = if (showCodes) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = authTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_code_confirm_input")
                )

                if (confirmCode.isNotEmpty()) {
                    val matched = accessCode == confirmCode && accessCode.length >= 4
                    Text(
                        text = if (matched) "Les deux codes correspondent." else "Les codes saisis ne sont pas identiques.",
                        color = if (matched) DiscordGreen else DiscordAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0C1020),
                    border = BorderStroke(1.dp, Color(0xFF232C4F))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exiger le code à chaque ouverture",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Verrouille l'application même après fermeture",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = requireCodeOnLaunch,
                            onCheckedChange = { requireCodeOnLaunch = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DiscordBlurple
                            ),
                            modifier = Modifier.testTag("onboarding_require_code_switch")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { wizardStep = 0 },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.36f)
                            .heightIn(min = 52.dp)
                            .testTag("onboarding_back_step_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Profil", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            onSubmit(
                                name,
                                tag,
                                bio,
                                avatarUrl,
                                avatarColor,
                                selectedStatus,
                                accessCode,
                                confirmCode,
                                requireCodeOnLaunch
                            )
                        },
                        enabled = !isBusy && !isUploadingPhoto &&
                            name.trim().length >= 2 &&
                            accessCode.length >= 4 &&
                            confirmCode.length >= 4,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(0.64f)
                            .heightIn(min = 52.dp)
                            .testTag("onboarding_submit_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isBusy) "Finalisation..." else "Créer et entrer",
                            fontFamily = RavenBodyFontFamily,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepChip(
    stepLabel: String,
    active: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            active -> Color(0xFF1F2A56)
            completed -> DiscordGreen.copy(alpha = 0.14f)
            else -> Color(0xFF0C1020)
        },
        border = BorderStroke(
            1.dp,
            when {
                active -> Color(0xFF6366F1)
                completed -> DiscordGreen.copy(alpha = 0.55f)
                else -> Color(0xFF232C4F)
            }
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepLabel,
                fontFamily = RavenBodyFontFamily,
                color = if (active || completed) Color.White else Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CodeStrengthMeter(code: String) {
    val strength = remember(code) { AuthGateRepository.evaluateAccessCodeStrength(code) }
    val (label, color, fraction) = when (strength) {
        AccessCodeStrength.TOO_SHORT -> Triple("4 à 8 chiffres requis", Color(0xFF64748B), 0.2f)
        AccessCodeStrength.WEAK -> Triple("Faible (évitez les suites comme 1234 ou 0000)", DiscordAmber, 0.45f)
        AccessCodeStrength.MEDIUM -> Triple("Sécurité correcte (6+ chiffres conseillés)", Color(0xFF38BDF8), 0.72f)
        AccessCodeStrength.STRONG -> Triple("Code robuste", DiscordGreen, 1f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A2038))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun statusIndicatorColor(status: UserStatus): Color = when (status) {
    UserStatus.ONLINE -> DiscordGreen
    UserStatus.IDLE -> DiscordAmber
    UserStatus.DND -> DiscordRed
    UserStatus.OFFLINE -> Color(0xFF64748B)
}

@Composable
private fun VerifiedGmailBadge(email: String) {
    Surface(
        shape = CircleShape,
        color = DiscordGreen.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, DiscordGreen.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Gmail vérifié",
                tint = DiscordGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = email.ifBlank { "Gmail vérifié" },
                color = Color(0xFFDCFCE7),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AuthBanner(text: String, isError: Boolean) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (isError) DiscordRed.copy(alpha = 0.15f) else DiscordBlurple.copy(alpha = 0.16f),
        border = BorderStroke(
            1.dp,
            if (isError) DiscordRed.copy(alpha = 0.55f) else DiscordBlurple.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = if (isError) DiscordRed else Color(0xFF7DD3FC),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF6366F1),
    unfocusedBorderColor = Color(0xFF2B355C),
    focusedLabelColor = Color(0xFFA5B4FC),
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedLeadingIconColor = Color(0xFF7DD3FC),
    unfocusedLeadingIconColor = Color(0xFF94A3B8),
    focusedTrailingIconColor = Color(0xFF7DD3FC),
    unfocusedTrailingIconColor = Color(0xFF94A3B8),
    focusedContainerColor = Color(0xFF0C1020),
    unfocusedContainerColor = Color(0xFF0C1020),
    cursorColor = Color(0xFF7DD3FC)
)
