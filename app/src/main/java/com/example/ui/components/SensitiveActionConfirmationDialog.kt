package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.model.ConnectedDevice
import com.example.model.PendingSensitiveConfirmation
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

internal fun resolveDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
}

@Composable
fun SensitiveActionConfirmationDialog(
    pending: PendingSensitiveConfirmation,
    hasSecurityPinConfigured: Boolean,
    isFirebaseSignedIn: Boolean,
    failedAttempts: Int,
    errorMessage: String?,
    onConfirmWithPin: (String) -> Boolean,
    onConfirmWithGoogleReauth: (Boolean) -> Unit,
    onConfirmExplicitly: () -> Boolean,
    onDismiss: () -> Unit
) {
    val palette = ravenPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleWebClientId = remember(context) { resolveDefaultWebClientId(context) }
    var pinInput by remember(pending) { mutableStateOf("") }
    var isReauthLoading by remember(pending) { mutableStateOf(false) }
    var localReauthError by remember(pending) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("sensitive_confirm_dialog"),
        icon = {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = DiscordAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = pending.title,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pending.description,
                    color = palette.textSecondary,
                    fontSize = 13.sp
                )
                if (failedAttempts > 0) {
                    Surface(
                        color = DiscordRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Échecs récents : $failedAttempts / 3 (verrouillage automatique au 3e échec)",
                            color = DiscordRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                val displayedError = localReauthError ?: errorMessage
                if (!displayedError.isNullOrBlank()) {
                    Text(displayedError, color = DiscordRed, fontSize = 12.sp)
                }

                if (hasSecurityPinConfigured) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 12) pinInput = it },
                        label = { Text("Code de sécurité du compte") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sensitive_pin_input")
                    )
                    Button(
                        onClick = { onConfirmWithPin(pinInput) },
                        enabled = pinInput.length >= 4 && !isReauthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordBlurple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sensitive_confirm_pin_btn")
                    ) {
                        Text("Confirmer avec le code", color = Color.White)
                    }
                }

                if (isFirebaseSignedIn) {
                    OutlinedButton(
                        onClick = {
                            val clientId = googleWebClientId
                            if (clientId == null) {
                                localReauthError = "Ré-authentification Google indisponible : configuration Firebase (google-services.json) manquante."
                                return@OutlinedButton
                            }
                            localReauthError = null
                            isReauthLoading = true
                            scope.launch {
                                try {
                                    val cm = CredentialManager.create(context)
                                    val opt = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                    val req = GetCredentialRequest.Builder().addCredentialOption(opt).build()
                                    val activity = context as? Activity
                                    if (activity == null) {
                                        onConfirmWithGoogleReauth(false)
                                        return@launch
                                    }
                                    val res = cm.getCredential(activity, req)
                                    val cred = res.credential
                                    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                        val authCred = GoogleAuthProvider.getCredential(token, null)
                                        val currentAuthUser = Firebase.auth.currentUser
                                        if (currentAuthUser != null) {
                                            currentAuthUser.reauthenticate(authCred).await()
                                            onConfirmWithGoogleReauth(true)
                                        } else {
                                            onConfirmWithGoogleReauth(false)
                                        }
                                    } else {
                                        onConfirmWithGoogleReauth(false)
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.w("AccountSecurity", "Google re-auth cancelled", e)
                                    onConfirmWithGoogleReauth(false)
                                } catch (e: Exception) {
                                    Log.e("AccountSecurity", "Google re-auth failed", e)
                                    onConfirmWithGoogleReauth(false)
                                } finally {
                                    isReauthLoading = false
                                }
                            }
                        },
                        enabled = !isReauthLoading && googleWebClientId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sensitive_confirm_google_btn")
                    ) {
                        if (isReauthLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Ré-authentifier avec Google")
                    }
                    if (googleWebClientId == null) {
                        Text(
                            text = "Ré-authentification Google désactivée : configuration Firebase (google-services.json) absente.",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (!hasSecurityPinConfigured) {
                    Button(
                        onClick = { onConfirmExplicitly() },
                        enabled = !isReauthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = DiscordAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sensitive_confirm_explicit_btn")
                    ) {
                        Text("Confirmer cette action sensible", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
fun ConfigureSecurityPinDialog(
    hasExistingPin: Boolean,
    onSavePin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var localErr by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "Changer le code de sécurité" else "Définir un code de sécurité") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Ce code protège les révocations de session, changements critiques et configurations 2FA. Seule une empreinte PBKDF2 salée est conservée.",
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = pin1,
                    onValueChange = { if (it.length <= 12) pin1 = it; localErr = null },
                    label = { Text("Nouveau code (4 à 12 caractères)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("config_pin_field_1")
                )
                OutlinedTextField(
                    value = pin2,
                    onValueChange = { if (it.length <= 12) pin2 = it; localErr = null },
                    label = { Text("Confirmer le code") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("config_pin_field_2")
                )
                localErr?.let { Text(it, color = DiscordRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin1.trim().length !in 4..12 -> localErr = "4 à 12 caractères requis."
                        pin1.trim() != pin2.trim() -> localErr = "Les deux codes ne correspondent pas."
                        else -> {
                            onSavePin(pin1.trim())
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("config_pin_save_btn")
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun RenameDeviceDialog(
    device: ConnectedDevice,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(device) { mutableStateOf(device.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer l'appareil") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 48) name = it },
                label = { Text("Nom personnalisé") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name)
                    onDismiss()
                },
                enabled = name.trim().length >= 2
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun OneTimeBackupCodesDialog(
    codes: List<String>,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val palette = ravenPalette()
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sidebarBg,
        modifier = Modifier.testTag("backup_codes_dialog"),
        icon = { Icon(Icons.Default.Key, contentDescription = null, tint = DiscordGreen) },
        title = {
            Text(
                "Codes de secours 2FA (usage unique)",
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Notez ces codes maintenant : ils ne seront plus affichés en clair après fermeture (seules leurs empreintes SHA-256 sont stockées).",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                Surface(
                    color = palette.cardBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        codes.forEach { code ->
                            Text(
                                text = code,
                                color = palette.textPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(codes.joinToString("\n")))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copied) "Codes copiés !" else "Copier tous les codes")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("J'ai sauvegardé mes codes") }
        }
    )
}
