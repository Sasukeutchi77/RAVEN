package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.AccountSecurityProfile
import com.example.model.UserStatus
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AuthOnboardingPhase {
    CHECKING_ACCOUNT,
    SIGN_IN_GMAIL,
    CREATE_PROFILE,
    VERIFY_CODE,
    RESET_CODE,
    READY
}

enum class AuthEntryIntent {
    SIGN_IN,
    CREATE_ACCOUNT
}

enum class AccessCodeStrength {
    TOO_SHORT,
    WEAK,
    MEDIUM,
    STRONG
}

data class RememberedGmailAccount(
    val uid: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastUsedAtMs: Long = 0L
)

data class AuthGateUiState(
    val phase: AuthOnboardingPhase = AuthOnboardingPhase.SIGN_IN_GMAIL,
    val authIntent: AuthEntryIntent = AuthEntryIntent.SIGN_IN,
    val gmailInput: String = "",
    val signedInUid: String? = null,
    val verifiedEmail: String? = null,
    val suggestedName: String = "",
    val suggestedTag: String = "",
    val suggestedAvatarUrl: String? = null,
    val hasConfiguredPin: Boolean = false,
    val failedCodeAttempts: Int = 0,
    val cooldownUntilMs: Long = 0L,
    val lastCodeFailedNonce: Int = 0,
    val skipSilentAutoSignIn: Boolean = false,
    val rememberedAccounts: List<RememberedGmailAccount> = emptyList(),
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class AuthGateRepository(
    private val chatRepo: ChatRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val _state = MutableStateFlow(
        AuthGateUiState(rememberedAccounts = readRememberedAccounts())
    )
    val state: StateFlow<AuthGateUiState> = _state.asStateFlow()

    private var cachedPinHash: String? = null
    private var cachedPinSalt: String? = null
    private var cachedSecurityProfile: AccountSecurityProfile? = null

    companion object {
        private const val PREFS_NAME = "raven_auth_gate_cache"
        private const val KEY_REMEMBERED_ACCOUNTS = "remembered_gmail_accounts_v1"
        private val GMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@(gmail\\.com|googlemail\\.com)$", RegexOption.IGNORE_CASE)

        fun isValidGmailAddress(email: String?): Boolean {
            val clean = email?.trim().orEmpty()
            return clean.isNotBlank() && GMAIL_REGEX.matches(clean)
        }

        fun isValidAccessCode(code: String): Boolean {
            val clean = code.trim()
            return clean.length in 4..8 && clean.all { it.isDigit() }
        }

        fun evaluateAccessCodeStrength(code: String): AccessCodeStrength {
            val clean = code.trim()
            if (clean.length < 4 || !clean.all { it.isDigit() }) {
                return AccessCodeStrength.TOO_SHORT
            }
            val uniqueDigits = clean.toSet().size
            val isAllSame = uniqueDigits <= 1
            val isSequentialAsc = clean.zipWithNext().all { (a, b) -> b - a == 1 }
            val isSequentialDesc = clean.zipWithNext().all { (a, b) -> a - b == 1 }
            val isRepeatedPair = clean.length >= 4 && clean.chunked(2).distinct().size == 1

            if (isAllSame || isSequentialAsc || isSequentialDesc || isRepeatedPair) {
                return AccessCodeStrength.WEAK
            }
            return if (clean.length >= 6 && uniqueDigits >= 4) {
                AccessCodeStrength.STRONG
            } else {
                AccessCodeStrength.MEDIUM
            }
        }

        fun generateDefaultTag(uid: String): String {
            val hash = (uid.hashCode() and 0x7FFFFFFF) % 9000 + 1000
            return "#$hash"
        }

        fun generateRandomTag(): String {
            val num = (1000..9999).random()
            return "#$num"
        }
    }

    private fun prefs() = chatRepo.appContextOrNull()?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readRememberedAccounts(): List<RememberedGmailAccount> {
        val raw = prefs()?.getString(KEY_REMEMBERED_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uid = obj.optString("uid", "").trim()
                    val email = obj.optString("email", "").trim()
                    if (uid.isNotBlank() && isValidGmailAddress(email)) {
                        add(
                            RememberedGmailAccount(
                                uid = uid,
                                email = email,
                                displayName = obj.optString("displayName", email.substringBefore("@")),
                                avatarUrl = obj.optString("avatarUrl", "").takeIf { it.isNotBlank() },
                                lastUsedAtMs = obj.optLong("lastUsedAtMs", 0L)
                            )
                        )
                    }
                }
            }.sortedByDescending { it.lastUsedAtMs }.take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun upsertRememberedAccount(
        uid: String,
        email: String,
        displayName: String?,
        avatarUrl: String?
    ): List<RememberedGmailAccount> {
        if (uid.isBlank() || !isValidGmailAddress(email)) {
            return _state.value.rememberedAccounts
        }
        val cleanName = displayName?.trim()?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val updatedEntry = RememberedGmailAccount(
            uid = uid,
            email = email.trim(),
            displayName = cleanName.take(40),
            avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
            lastUsedAtMs = nowProvider()
        )
        val merged = (listOf(updatedEntry) + _state.value.rememberedAccounts
            .filterNot { it.uid == uid || it.email.equals(email.trim(), ignoreCase = true) })
            .sortedByDescending { it.lastUsedAtMs }
            .take(4)
        persistRememberedAccounts(merged)
        return merged
    }

    private fun persistRememberedAccounts(list: List<RememberedGmailAccount>) {
        try {
            val arr = JSONArray()
            list.forEach { acc ->
                arr.put(
                    JSONObject().apply {
                        put("uid", acc.uid)
                        put("email", acc.email)
                        put("displayName", acc.displayName)
                        put("avatarUrl", acc.avatarUrl ?: "")
                        put("lastUsedAtMs", acc.lastUsedAtMs)
                    }
                )
            }
            prefs()?.edit()?.putString(KEY_REMEMBERED_ACCOUNTS, arr.toString())?.apply()
        } catch (_: Exception) {
        }
    }

    fun selectRememberedAccount(account: RememberedGmailAccount) {
        _state.value = _state.value.copy(
            authIntent = AuthEntryIntent.SIGN_IN,
            gmailInput = account.email,
            suggestedName = account.displayName,
            suggestedAvatarUrl = account.avatarUrl,
            errorMessage = null,
            infoMessage = "Compte ${account.email} sélectionné. Confirmez via Google pour continuer."
        )
    }

    fun removeRememberedAccount(uid: String) {
        val next = _state.value.rememberedAccounts.filterNot { it.uid == uid }
        persistRememberedAccounts(next)
        _state.value = _state.value.copy(
            rememberedAccounts = next,
            gmailInput = if (_state.value.rememberedAccounts.firstOrNull { it.uid == uid }?.email.equals(_state.value.gmailInput, ignoreCase = true)) {
                ""
            } else {
                _state.value.gmailInput
            }
        )
    }

    private fun saveLocalAuthCache(
        uid: String,
        onboardingCompleted: Boolean,
        pinHash: String?,
        pinSalt: String?,
        name: String?,
        avatarUrl: String?,
        email: String?
    ) {
        try {
            prefs()?.edit()?.apply {
                putBoolean("${uid}_onboarding", onboardingCompleted)
                if (!pinHash.isNullOrBlank()) putString("${uid}_pin_hash", pinHash)
                if (!pinSalt.isNullOrBlank()) putString("${uid}_pin_salt", pinSalt)
                if (!name.isNullOrBlank()) putString("${uid}_name", name)
                if (!avatarUrl.isNullOrBlank()) putString("${uid}_avatar", avatarUrl)
                if (!email.isNullOrBlank()) putString("${uid}_email", email)
                apply()
            }
        } catch (_: Exception) {
        }
    }

    private data class LocalAuthCache(
        val onboardingCompleted: Boolean,
        val pinHash: String?,
        val pinSalt: String?,
        val name: String?,
        val avatarUrl: String?
    )

    private fun readLocalAuthCache(uid: String): LocalAuthCache? {
        val p = prefs() ?: return null
        return try {
            LocalAuthCache(
                onboardingCompleted = p.getBoolean("${uid}_onboarding", false),
                pinHash = p.getString("${uid}_pin_hash", null),
                pinSalt = p.getString("${uid}_pin_salt", null),
                name = p.getString("${uid}_name", null),
                avatarUrl = p.getString("${uid}_avatar", null)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun selectAuthIntent(intent: AuthEntryIntent) {
        _state.value = _state.value.copy(
            authIntent = intent,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun updateGmailInput(email: String) {
        _state.value = _state.value.copy(
            gmailInput = email.take(120),
            errorMessage = null
        )
    }

    fun setAuthError(message: String?) {
        _state.value = _state.value.copy(
            isBusy = false,
            errorMessage = message
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }

    fun clearLockoutIfExpired() {
        val st = _state.value
        if (st.cooldownUntilMs > 0L && nowProvider() >= st.cooldownUntilMs) {
            _state.value = st.copy(
                cooldownUntilMs = 0L,
                failedCodeAttempts = 0,
                errorMessage = null,
                infoMessage = "Vous pouvez réessayer votre code personnel."
            )
        }
    }

    fun evaluateStartupSession() {
        val remembered = readRememberedAccounts()
        val authUser = chatRepo.authInstance()?.currentUser
        if (authUser == null) {
            cachedPinHash = null
            cachedPinSalt = null
            cachedSecurityProfile = null
            _state.value = AuthGateUiState(
                phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
                skipSilentAutoSignIn = _state.value.skipSilentAutoSignIn,
                rememberedAccounts = remembered
            )
            return
        }
        val email = authUser.email
        if (!isValidGmailAddress(email)) {
            chatRepo.repoScope.launch {
                chatRepo.signOutCleanly()
                _state.value = AuthGateUiState(
                    phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
                    rememberedAccounts = remembered,
                    errorMessage = "Seules les adresses @gmail.com sont autorisées sur Raven."
                )
            }
            return
        }
        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.CHECKING_ACCOUNT,
            signedInUid = authUser.uid,
            verifiedEmail = email,
            rememberedAccounts = remembered,
            isBusy = true,
            errorMessage = null
        )
        chatRepo.repoScope.launch {
            resolveAccountState(
                uid = authUser.uid,
                email = email.orEmpty(),
                displayName = authUser.displayName,
                photoUrl = authUser.photoUrl?.toString(),
                isInteractiveSignIn = false,
                prefilledCode = ""
            )
        }
    }

    suspend fun onGmailCredentialSignedIn(
        prefilledCode: String = "",
        isSilentAutoSignIn: Boolean = false
    ) {
        val authUser = chatRepo.authInstance()?.currentUser
        if (authUser == null) {
            _state.value = _state.value.copy(
                phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
                isBusy = false,
                errorMessage = if (isSilentAutoSignIn) null else "Session Gmail introuvable après l'authentification."
            )
            return
        }
        val email = authUser.email
        if (!isValidGmailAddress(email)) {
            chatRepo.signOutCleanly()
            _state.value = AuthGateUiState(
                phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
                gmailInput = _state.value.gmailInput,
                rememberedAccounts = _state.value.rememberedAccounts,
                errorMessage = "Compte refusé (${email ?: "inconnu"}) : connexion autorisée uniquement avec une adresse @gmail.com."
            )
            return
        }
        val typedHint = _state.value.gmailInput.trim()
        if (!isSilentAutoSignIn && typedHint.isNotBlank() && !typedHint.equals(email.orEmpty(), ignoreCase = true)) {
            chatRepo.signOutCleanly()
            _state.value = AuthGateUiState(
                phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
                gmailInput = typedHint,
                rememberedAccounts = _state.value.rememberedAccounts,
                errorMessage = "L'adresse Gmail sélectionnée ($email) ne correspond pas à $typedHint."
            )
            return
        }

        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.CHECKING_ACCOUNT,
            signedInUid = authUser.uid,
            verifiedEmail = email,
            skipSilentAutoSignIn = false,
            isBusy = true,
            errorMessage = null
        )
        resolveAccountState(
            uid = authUser.uid,
            email = email.orEmpty(),
            displayName = authUser.displayName,
            photoUrl = authUser.photoUrl?.toString(),
            isInteractiveSignIn = !isSilentAutoSignIn,
            prefilledCode = prefilledCode
        )
    }

    internal suspend fun resolveAccountState(
        uid: String,
        email: String,
        displayName: String?,
        photoUrl: String?,
        isInteractiveSignIn: Boolean,
        prefilledCode: String
    ) {
        val db = chatRepo.firestoreDb()
        val local = readLocalAuthCache(uid)
        var remoteOnboardingDone = false
        var remoteName: String? = null
        var remoteAvatar: String? = null
        var remoteTag: String? = null
        var requireCodeOnLaunch = chatRepo.appSettings.value.requireCodeOnAppLaunch
        var fetchedProfile: AccountSecurityProfile? = null

        if (db != null) {
            try {
                withContext(Dispatchers.IO) {
                    val userSnap = Tasks.await(db.collection("users").document(uid).get())
                    if (userSnap.exists()) {
                        remoteOnboardingDone = userSnap.getBoolean("onboardingCompleted") == true
                        remoteName = userSnap.getString("name")
                        remoteAvatar = userSnap.getString("avatarUrl")
                        remoteTag = userSnap.getString("tag")
                        userSnap.getBoolean("requireCodeOnAppLaunch")?.let {
                            requireCodeOnLaunch = it
                        }
                    }
                    val secSnap = Tasks.await(
                        db.collection("users").document(uid)
                            .collection("security_profile").document("account")
                            .get()
                    )
                    if (secSnap.exists()) {
                        fetchedProfile = AccountSecurityFirestoreSync.securityProfileFromDoc(secSnap)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AuthGateRepo", "Unable to inspect remote onboarding/security profile, checking local cache", e)
            }
        }

        val resolvedPinHash = fetchedProfile?.pinHash ?: local?.pinHash
        val resolvedPinSalt = fetchedProfile?.pinSalt ?: local?.pinSalt
        val isOnboarded = (remoteOnboardingDone || local?.onboardingCompleted == true) &&
            !resolvedPinHash.isNullOrBlank() &&
            !resolvedPinSalt.isNullOrBlank()

        cachedPinHash = resolvedPinHash
        cachedPinSalt = resolvedPinSalt
        cachedSecurityProfile = fetchedProfile

        if (!isOnboarded) {
            val currentIntent = _state.value.authIntent
            chatRepo.markOnboardingCompleted(false)
            _state.value = _state.value.copy(
                phase = AuthOnboardingPhase.CREATE_PROFILE,
                authIntent = AuthEntryIntent.CREATE_ACCOUNT,
                signedInUid = uid,
                verifiedEmail = email,
                suggestedName = (remoteName ?: local?.name ?: displayName ?: email.substringBefore("@")).take(40),
                suggestedTag = remoteTag ?: generateDefaultTag(uid),
                suggestedAvatarUrl = remoteAvatar ?: local?.avatarUrl ?: photoUrl,
                hasConfiguredPin = false,
                isBusy = false,
                errorMessage = null,
                infoMessage = if (currentIntent == AuthEntryIntent.SIGN_IN) {
                    "Aucun compte Raven trouvé pour $email. Finalisez votre nouveau compte ci-dessous !"
                } else {
                    "Gmail vérifié ! Renseignez votre profil et votre code personnel pour créer le compte."
                }
            )
            return
        }

        val resolvedName = remoteName ?: local?.name ?: displayName
        val resolvedAvatar = remoteAvatar ?: local?.avatarUrl ?: photoUrl

        saveLocalAuthCache(
            uid = uid,
            onboardingCompleted = true,
            pinHash = resolvedPinHash,
            pinSalt = resolvedPinSalt,
            name = resolvedName,
            avatarUrl = resolvedAvatar,
            email = email
        )
        val updatedRemembered = upsertRememberedAccount(
            uid = uid,
            email = email,
            displayName = resolvedName,
            avatarUrl = resolvedAvatar
        )
        chatRepo.markOnboardingCompleted(true)

        val shouldPromptForCode = isInteractiveSignIn || requireCodeOnLaunch
        if (!shouldPromptForCode) {
            if (chatRepo.authInstance()?.currentUser != null) {
                chatRepo.syncFirebaseUser()
            }
            _state.value = _state.value.copy(
                phase = AuthOnboardingPhase.READY,
                signedInUid = uid,
                verifiedEmail = email,
                suggestedName = resolvedName ?: "Membre Raven",
                suggestedAvatarUrl = resolvedAvatar,
                rememberedAccounts = updatedRemembered,
                hasConfiguredPin = true,
                isBusy = false,
                errorMessage = null
            )
            return
        }

        val trimmedPrefill = prefilledCode.trim()
        if (trimmedPrefill.isNotBlank() && isValidAccessCode(trimmedPrefill) &&
            AccountSecurityCrypto.verifyPinPbkdf2(trimmedPrefill, resolvedPinHash, resolvedPinSalt)
        ) {
            if (chatRepo.authInstance()?.currentUser != null) {
                chatRepo.syncFirebaseUser()
            }
            _state.value = _state.value.copy(
                phase = AuthOnboardingPhase.READY,
                signedInUid = uid,
                verifiedEmail = email,
                rememberedAccounts = updatedRemembered,
                hasConfiguredPin = true,
                failedCodeAttempts = 0,
                isBusy = false,
                errorMessage = null
            )
        } else {
            val switchedFromCreate = _state.value.authIntent == AuthEntryIntent.CREATE_ACCOUNT
            _state.value = _state.value.copy(
                phase = AuthOnboardingPhase.VERIFY_CODE,
                authIntent = AuthEntryIntent.SIGN_IN,
                signedInUid = uid,
                verifiedEmail = email,
                suggestedName = resolvedName ?: "Membre Raven",
                suggestedAvatarUrl = resolvedAvatar,
                rememberedAccounts = updatedRemembered,
                hasConfiguredPin = true,
                isBusy = false,
                errorMessage = if (trimmedPrefill.isNotBlank()) "Code incorrect. Vérifiez votre code à 4–8 chiffres." else null,
                infoMessage = when {
                    trimmedPrefill.isNotBlank() -> null
                    switchedFromCreate -> "Ce compte Gmail existe déjà sur Raven ! Saisissez votre code pour vous connecter."
                    else -> "Compte reconnu. Saisissez votre code personnel pour vous connecter."
                }
            )
        }
    }

    suspend fun completeAccountCreation(
        name: String,
        tag: String,
        bio: String,
        avatarUrl: String?,
        avatarColor: Long,
        status: UserStatus,
        accessCode: String,
        confirmCode: String,
        requireCodeOnAppLaunch: Boolean = false
    ): Result<Unit> {
        val cleanName = name.trim()
        val cleanCode = accessCode.trim()
        val cleanConfirm = confirmCode.trim()

        if (cleanName.length !in 2..40) {
            val err = "Le nom doit contenir entre 2 et 40 caractères."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }
        if (!isValidAccessCode(cleanCode)) {
            val err = "Le code de connexion doit contenir entre 4 et 8 chiffres."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }
        if (cleanCode != cleanConfirm) {
            val err = "Les deux codes saisis ne correspondent pas."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }

        val uid = _state.value.signedInUid ?: chatRepo.authInstance()?.currentUser?.uid
        val email = _state.value.verifiedEmail ?: chatRepo.authInstance()?.currentUser?.email
        if (uid.isNullOrBlank() || !isValidGmailAddress(email)) {
            val err = "Authentification Gmail requise avant la création du compte."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalStateException(err))
        }

        _state.value = _state.value.copy(isBusy = true, errorMessage = null)

        val salt = AccountSecurityCrypto.generateSaltHex(16)
        val hash = AccountSecurityCrypto.hashPinPbkdf2(cleanCode, salt)
        val now = nowProvider()

        val secProfile = (cachedSecurityProfile ?: AccountSecurityProfile(userId = uid)).copy(
            userId = uid,
            pinHash = hash,
            pinSalt = salt,
            lastSensitiveVerifiedAtMs = now,
            failedChallengeCount = 0,
            updatedAtMs = now
        )
        cachedPinHash = hash
        cachedPinSalt = salt
        cachedSecurityProfile = secProfile

        val db = chatRepo.firestoreDb()
        if (db != null) {
            try {
                withContext(Dispatchers.IO) {
                    val payload = AccountSecurityFirestoreSync.securityProfileToMap(secProfile)
                        .filterValues { it != null }
                    Tasks.await(
                        db.collection("users").document(uid)
                            .collection("security_profile").document("account")
                            .set(payload)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AuthGateRepo", "Unable to write security profile immediately during onboarding", e)
            }
        }

        val cleanTag = tag.trim().ifBlank { generateDefaultTag(uid) }
        saveLocalAuthCache(
            uid = uid,
            onboardingCompleted = true,
            pinHash = hash,
            pinSalt = salt,
            name = cleanName,
            avatarUrl = avatarUrl,
            email = email
        )
        val updatedRemembered = upsertRememberedAccount(
            uid = uid,
            email = email.orEmpty(),
            displayName = cleanName,
            avatarUrl = avatarUrl
        )

        chatRepo.applyInitialAccountProfile(
            uid = uid,
            email = email,
            name = cleanName,
            tag = cleanTag,
            bio = bio.trim().ifBlank { "Membre Raven vérifié par Gmail" },
            avatarUrl = avatarUrl,
            avatarColor = avatarColor,
            status = status,
            requireCodeOnAppLaunch = requireCodeOnAppLaunch
        )
        if (chatRepo.authInstance()?.currentUser != null) {
            chatRepo.syncFirebaseUser(reloadFromCloud = false)
        }

        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.READY,
            signedInUid = uid,
            verifiedEmail = email,
            suggestedName = cleanName,
            suggestedTag = cleanTag,
            suggestedAvatarUrl = avatarUrl,
            rememberedAccounts = updatedRemembered,
            hasConfiguredPin = true,
            failedCodeAttempts = 0,
            skipSilentAutoSignIn = false,
            isBusy = false,
            errorMessage = null,
            infoMessage = null
        )
        return Result.success(Unit)
    }

    fun verifyAccessCode(candidateCode: String): Boolean {
        val now = nowProvider()
        val st = _state.value
        if (st.cooldownUntilMs > now) {
            val secLeft = ((st.cooldownUntilMs - now) / 1000L).coerceAtLeast(1L)
            _state.value = st.copy(
                lastCodeFailedNonce = st.lastCodeFailedNonce + 1,
                errorMessage = "Trop d'essais incorrects. Réessayez dans ${secLeft}s."
            )
            return false
        }

        val clean = candidateCode.trim()
        if (!isValidAccessCode(clean)) {
            _state.value = st.copy(
                lastCodeFailedNonce = st.lastCodeFailedNonce + 1,
                errorMessage = "Le code doit contenir entre 4 et 8 chiffres."
            )
            return false
        }

        val ok = AccountSecurityCrypto.verifyPinPbkdf2(clean, cachedPinHash, cachedPinSalt)
        if (!ok) {
            val nextFails = st.failedCodeAttempts + 1
            val nextCooldown = if (nextFails >= 5) now + 30_000L else 0L
            _state.value = st.copy(
                failedCodeAttempts = nextFails,
                cooldownUntilMs = nextCooldown,
                lastCodeFailedNonce = st.lastCodeFailedNonce + 1,
                errorMessage = if (nextCooldown > 0L) {
                    "5 tentatives échouées. Pause de sécurité de 30 secondes."
                } else {
                    "Code incorrect ($nextFails/5). Réessayez ou réinitialisez via Gmail."
                }
            )
            return false
        }

        val uid = st.signedInUid
        val email = st.verifiedEmail
        val updatedRemembered = if (!uid.isNullOrBlank() && !email.isNullOrBlank()) {
            upsertRememberedAccount(uid, email, st.suggestedName, st.suggestedAvatarUrl)
        } else {
            st.rememberedAccounts
        }

        chatRepo.markOnboardingCompleted(true)
        if (chatRepo.authInstance()?.currentUser != null) {
            chatRepo.syncFirebaseUser()
        }
        _state.value = st.copy(
            phase = AuthOnboardingPhase.READY,
            failedCodeAttempts = 0,
            cooldownUntilMs = 0L,
            rememberedAccounts = updatedRemembered,
            skipSilentAutoSignIn = false,
            isBusy = false,
            errorMessage = null,
            infoMessage = null
        )
        return true
    }

    fun unlockWithBiometrics() {
        val st = _state.value
        val uid = st.signedInUid
        val email = st.verifiedEmail
        val updatedRemembered = if (!uid.isNullOrBlank() && !email.isNullOrBlank()) {
            upsertRememberedAccount(uid, email, st.suggestedName, st.suggestedAvatarUrl)
        } else {
            st.rememberedAccounts
        }
        chatRepo.markOnboardingCompleted(true)
        if (chatRepo.authInstance()?.currentUser != null) {
            chatRepo.syncFirebaseUser()
        }
        _state.value = st.copy(
            phase = AuthOnboardingPhase.READY,
            failedCodeAttempts = 0,
            cooldownUntilMs = 0L,
            rememberedAccounts = updatedRemembered,
            skipSilentAutoSignIn = false,
            isBusy = false,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun enterCodeResetAfterGmailReauth() {
        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.RESET_CODE,
            errorMessage = null,
            infoMessage = "Identité Gmail confirmée. Définissez votre nouveau code à 4–8 chiffres."
        )
    }

    fun cancelCodeReset() {
        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.VERIFY_CODE,
            errorMessage = null,
            infoMessage = null
        )
    }

    suspend fun applyResetAccessCode(newCode: String, confirmCode: String): Result<Unit> {
        val clean = newCode.trim()
        val confirm = confirmCode.trim()
        if (!isValidAccessCode(clean)) {
            val err = "Le code doit contenir entre 4 et 8 chiffres."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }
        if (clean != confirm) {
            val err = "Les codes ne correspondent pas."
            _state.value = _state.value.copy(errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }

        val uid = _state.value.signedInUid ?: chatRepo.authInstance()?.currentUser?.uid
        if (uid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Utilisateur Gmail non authentifié."))
        }

        _state.value = _state.value.copy(isBusy = true, errorMessage = null)
        val salt = AccountSecurityCrypto.generateSaltHex(16)
        val hash = AccountSecurityCrypto.hashPinPbkdf2(clean, salt)
        val now = nowProvider()

        val updatedProfile = (cachedSecurityProfile ?: AccountSecurityProfile(userId = uid)).copy(
            userId = uid,
            pinHash = hash,
            pinSalt = salt,
            lastSensitiveVerifiedAtMs = now,
            failedChallengeCount = 0,
            updatedAtMs = now
        )
        cachedPinHash = hash
        cachedPinSalt = salt
        cachedSecurityProfile = updatedProfile

        val db = chatRepo.firestoreDb()
        if (db != null) {
            try {
                withContext(Dispatchers.IO) {
                    Tasks.await(
                        db.collection("users").document(uid)
                            .collection("security_profile").document("account")
                            .set(AccountSecurityFirestoreSync.securityProfileToMap(updatedProfile).filterValues { it != null })
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AuthGateRepo", "Could not push reset code to cloud", e)
            }
        }

        saveLocalAuthCache(
            uid = uid,
            onboardingCompleted = true,
            pinHash = hash,
            pinSalt = salt,
            name = _state.value.suggestedName,
            avatarUrl = _state.value.suggestedAvatarUrl,
            email = _state.value.verifiedEmail
        )
        chatRepo.markOnboardingCompleted(true)
        if (chatRepo.authInstance()?.currentUser != null) {
            chatRepo.syncFirebaseUser()
        }

        _state.value = _state.value.copy(
            phase = AuthOnboardingPhase.READY,
            failedCodeAttempts = 0,
            cooldownUntilMs = 0L,
            skipSilentAutoSignIn = false,
            isBusy = false,
            errorMessage = null,
            infoMessage = null
        )
        return Result.success(Unit)
    }

    suspend fun signOut() {
        cachedPinHash = null
        cachedPinSalt = null
        cachedSecurityProfile = null
        val remembered = readRememberedAccounts().ifEmpty { _state.value.rememberedAccounts }
        chatRepo.signOutCleanly()
        _state.value = AuthGateUiState(
            phase = AuthOnboardingPhase.SIGN_IN_GMAIL,
            skipSilentAutoSignIn = true,
            rememberedAccounts = remembered,
            infoMessage = "Vous êtes déconnecté. Choisissez un compte Gmail ou reconnectez-vous."
        )
    }

    fun hasAccessCodeConfigured(): Boolean {
        if (!cachedPinHash.isNullOrBlank() && !cachedPinSalt.isNullOrBlank()) return true
        val uid = _state.value.signedInUid ?: chatRepo.authInstance()?.currentUser?.uid ?: return false
        val local = readLocalAuthCache(uid)
        return !local?.pinHash.isNullOrBlank() && !local?.pinSalt.isNullOrBlank()
    }

    fun verifyAccessCodeForLock(candidatePin: String): Boolean {
        val clean = candidatePin.trim()
        if (clean.isBlank()) return false
        val uid = _state.value.signedInUid ?: chatRepo.authInstance()?.currentUser?.uid
        val local = uid?.let { readLocalAuthCache(it) }
        val hash = cachedPinHash ?: local?.pinHash
        val salt = cachedPinSalt ?: local?.pinSalt
        return AccountSecurityCrypto.verifyPinPbkdf2(clean, hash, salt)
    }

    internal fun seedResolvedPinForTest(uid: String, email: String, pin: String) {
        val salt = AccountSecurityCrypto.generateSaltHex(16)
        val hash = AccountSecurityCrypto.hashPinPbkdf2(pin, salt)
        cachedPinHash = hash
        cachedPinSalt = salt
        val updatedRemembered = upsertRememberedAccount(uid, email, email.substringBefore("@"), null)
        _state.value = AuthGateUiState(
            phase = AuthOnboardingPhase.VERIFY_CODE,
            signedInUid = uid,
            verifiedEmail = email,
            rememberedAccounts = updatedRemembered,
            hasConfiguredPin = true
        )
    }
}
