package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

data class FirebaseConfigStatus(
    val isFirebaseAppReady: Boolean,
    val usingCustomVariant: Boolean,
    val projectId: String,
    val databaseId: String,
    val databaseSource: String,
    val hasWebClientId: Boolean,
    val webClientIdSource: String,
    val hasFcmSender: Boolean,
    val packageName: String,
    val missingItems: List<String>
) {
    val isFullyConfigured: Boolean
        get() = isFirebaseAppReady &&
            projectId.isNotBlank() &&
            databaseId.isNotBlank() &&
            hasWebClientId &&
            missingItems.isEmpty()
}

/**
 * Centralise l'inspection, la validation et l'initialisation des configurations Firebase
 * (google-services.json, firebase_applet_config.xml et variantes Secrets/.env via BuildConfig).
 */
object FirebaseConfigManager {
    private const val TAG = "FirebaseConfig"
    private const val VARIANT_APP_NAME = "raven_variant"

    internal fun stripSurroundingQuotes(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (
            trimmed.length >= 2 &&
            ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'")))
        ) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }
    }

    fun isPlaceholderOrBlank(value: String?): Boolean {
        val clean = stripSurroundingQuotes(value)
        if (clean.isBlank()) return true
        val upper = clean.uppercase()
        return upper == "DEMO" ||
            upper == "UNSIGNED_PRESET" ||
            upper == "NULL" ||
            upper.endsWith("_PLACEHOLDER") ||
            upper.endsWith("_DEFAULT_VALUE") ||
            upper.startsWith("YOUR_") ||
            upper.startsWith("CHANGE_ME") ||
            upper.contains("REPLACE_ME")
    }

    internal fun validBuildConfigOrNull(raw: String?): String? {
        val clean = stripSurroundingQuotes(raw)
        return clean.takeIf { !isPlaceholderOrBlank(it) }
    }

    /**
     * Valide et assainit une URL média avant synchronisation Firestore afin de respecter
     * les règles isValidSafeMediaUrl() et la limite de taille des documents.
     */
    fun sanitizeSyncMediaUrl(url: String?, maxLen: Int = 800): String? {
        val clean = url?.trim().orEmpty()
        if (clean.isBlank() || clean.length > maxLen) return null
        val lower = clean.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("vbscript:") ||
            lower.startsWith("data:") ||
            lower.startsWith("content://") ||
            clean.contains("..")
        ) {
            return null
        }
        return clean
    }

    private fun readStringResource(context: Context, name: String): String? {
        var resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0 && context.packageName != "com.example") {
            resId = context.resources.getIdentifier(name, "string", "com.example")
        }
        if (resId == 0) return null
        return runCatching { context.getString(resId) }
            .getOrNull()
            ?.let(::stripSurroundingQuotes)
            ?.takeIf { !isPlaceholderOrBlank(it) }
    }

    fun hasCustomFirebaseVariant(): Boolean {
        return validBuildConfigOrNull(BuildConfig.FIREBASE_PROJECT_ID) != null &&
            validBuildConfigOrNull(BuildConfig.FIREBASE_APP_ID) != null &&
            validBuildConfigOrNull(BuildConfig.FIREBASE_API_KEY) != null
    }

    fun resolveFirestoreDatabaseId(context: Context): String? {
        validBuildConfigOrNull(BuildConfig.FIREBASE_DATABASE_ID)?.let { return it }
        val xmlDbId = runCatching { context.getString(R.string.firestore_database_id) }
            .getOrNull()
            ?.let(::stripSurroundingQuotes)
            ?.takeIf { !isPlaceholderOrBlank(it) }
        val activeProjectId = resolveProjectId(context).orEmpty()
        if (xmlDbId != null &&
            xmlDbId.startsWith("ai-studio-") &&
            activeProjectId.isNotBlank() &&
            activeProjectId != "gen-lang-client-0965686129"
        ) {
            return "(default)"
        }
        return xmlDbId
    }

    fun resolveWebClientId(context: Context): String? {
        validBuildConfigOrNull(BuildConfig.FIREBASE_WEB_CLIENT_ID)?.let { return it }
        return readStringResource(context, "default_web_client_id")
    }

    fun resolveProjectId(context: Context): String? {
        validBuildConfigOrNull(BuildConfig.FIREBASE_PROJECT_ID)?.let { return it }
        return readStringResource(context, "project_id")
    }

    fun ensureFirebaseInitialized(context: Context): Boolean {
        return try {
            if (hasCustomFirebaseVariant()) {
                val existingVariant = FirebaseApp.getApps(context)
                    .firstOrNull { it.name == VARIANT_APP_NAME }
                if (existingVariant != null) return true

                val projectId = validBuildConfigOrNull(BuildConfig.FIREBASE_PROJECT_ID) ?: return false
                val appId = validBuildConfigOrNull(BuildConfig.FIREBASE_APP_ID) ?: return false
                val apiKey = validBuildConfigOrNull(BuildConfig.FIREBASE_API_KEY) ?: return false

                val optionsBuilder = FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .setApiKey(apiKey)

                validBuildConfigOrNull(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)?.let {
                    optionsBuilder.setGcmSenderId(it)
                }
                validBuildConfigOrNull(BuildConfig.FIREBASE_STORAGE_BUCKET)?.let {
                    optionsBuilder.setStorageBucket(it)
                }

                FirebaseApp.initializeApp(context.applicationContext, optionsBuilder.build(), VARIANT_APP_NAME)
                true
            } else {
                val existingDefault = FirebaseApp.getApps(context)
                    .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                    ?: FirebaseApp.initializeApp(context.applicationContext)
                existingDefault != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization fallback skipped: ${e.message}")
            false
        }
    }

    fun getConfiguredApp(context: Context): FirebaseApp? {
        return try {
            ensureFirebaseInitialized(context)
            val apps = FirebaseApp.getApps(context)
            if (hasCustomFirebaseVariant()) {
                apps.firstOrNull { it.name == VARIANT_APP_NAME }
                    ?: apps.firstOrNull()
            } else {
                apps.firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                    ?: apps.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retourne l'instance FirebaseAuth alignée sur le même FirebaseApp que Firestore
     * (projet par défaut ou variante custom .env), évitant toute erreur PERMISSION_DENIED.
     */
    fun getAuthInstance(context: Context): FirebaseAuth? {
        return try {
            val app = getConfiguredApp(context)
            if (app != null) {
                FirebaseAuth.getInstance(app)
            } else {
                Firebase.auth
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to obtain FirebaseAuth: ${e.message}")
            try {
                Firebase.auth
            } catch (_: Exception) {
                null
            }
        }
    }

    fun getFirestoreInstance(context: Context, explicitDatabaseId: String? = null): FirebaseFirestore? {
        val dbId = explicitDatabaseId?.let(::stripSurroundingQuotes)?.takeIf { !isPlaceholderOrBlank(it) }
            ?: resolveFirestoreDatabaseId(context)
            ?: return null
        return try {
            val app = getConfiguredApp(context)
            if (app != null) {
                FirebaseFirestore.getInstance(app, dbId)
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to obtain FirebaseFirestore($dbId): ${e.message}")
            null
        }
    }

    fun inspectStatus(context: Context): FirebaseConfigStatus {
        val customVariant = hasCustomFirebaseVariant()
        val projectId = resolveProjectId(context).orEmpty()
        val envDbId = validBuildConfigOrNull(BuildConfig.FIREBASE_DATABASE_ID)
        val xmlDbId = runCatching { context.getString(R.string.firestore_database_id) }
            .getOrNull()?.let(::stripSurroundingQuotes)?.takeIf { !isPlaceholderOrBlank(it) }
        val useCustomDefaultDb = envDbId == null &&
            xmlDbId != null &&
            xmlDbId.startsWith("ai-studio-") &&
            projectId.isNotBlank() &&
            projectId != "gen-lang-client-0965686129"
        val resolvedDbId = when {
            envDbId != null -> envDbId
            useCustomDefaultDb -> "(default)"
            else -> xmlDbId.orEmpty()
        }
        val dbSource = when {
            envDbId != null -> "Secrets (.env / BuildConfig)"
            useCustomDefaultDb -> "Par défaut ((default))"
            xmlDbId != null -> "firebase_applet_config.xml"
            else -> "Non configuré"
        }

        val envWebClientId = validBuildConfigOrNull(BuildConfig.FIREBASE_WEB_CLIENT_ID)
        val resWebClientId = readStringResource(context, "default_web_client_id")
        val hasWebClient = envWebClientId != null || resWebClientId != null
        val webSource = when {
            envWebClientId != null -> "Secrets (.env / BuildConfig)"
            resWebClientId != null -> "google-services.json"
            else -> "Absent"
        }

        val hasFcmSender = validBuildConfigOrNull(BuildConfig.FIREBASE_MESSAGING_SENDER_ID) != null ||
            readStringResource(context, "gcm_defaultSenderId") != null

        val isAppReady = runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || ensureFirebaseInitialized(context)
        }.getOrDefault(false)

        val missing = buildList {
            if (projectId.isBlank()) add("project_id")
            if (resolvedDbId.isBlank()) add("firestore_database_id")
            if (!hasWebClient) add("default_web_client_id")
            if (!isAppReady) add("FirebaseApp")
        }

        return FirebaseConfigStatus(
            isFirebaseAppReady = isAppReady,
            usingCustomVariant = customVariant,
            projectId = projectId,
            databaseId = resolvedDbId,
            databaseSource = dbSource,
            hasWebClientId = hasWebClient,
            webClientIdSource = webSource,
            hasFcmSender = hasFcmSender,
            packageName = context.packageName,
            missingItems = missing
        )
    }
}
