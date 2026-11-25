package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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

    fun isPlaceholderOrBlank(value: String?): Boolean {
        val clean = value?.trim().orEmpty()
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
        val clean = raw?.trim().orEmpty()
        return clean.takeIf { !isPlaceholderOrBlank(it) }
    }

    private fun readStringResource(context: Context, name: String): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0) return null
        return runCatching { context.getString(resId) }
            .getOrNull()
            ?.trim()
            ?.takeIf { !isPlaceholderOrBlank(it) }
    }

    fun hasCustomFirebaseVariant(): Boolean {
        return validBuildConfigOrNull(BuildConfig.FIREBASE_PROJECT_ID) != null &&
            validBuildConfigOrNull(BuildConfig.FIREBASE_APP_ID) != null &&
            validBuildConfigOrNull(BuildConfig.FIREBASE_API_KEY) != null
    }

    fun resolveFirestoreDatabaseId(context: Context): String? {
        validBuildConfigOrNull(BuildConfig.FIREBASE_DATABASE_ID)?.let { return it }
        return runCatching { context.getString(R.string.firestore_database_id) }
            .getOrNull()
            ?.trim()
            ?.takeIf { !isPlaceholderOrBlank(it) }
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

                val optionsBuilder = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID.trim())
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID.trim())
                    .setApiKey(BuildConfig.FIREBASE_API_KEY.trim())

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

    fun getFirestoreInstance(context: Context, explicitDatabaseId: String? = null): FirebaseFirestore? {
        val dbId = explicitDatabaseId?.trim()?.takeIf { !isPlaceholderOrBlank(it) }
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
        val envDbId = validBuildConfigOrNull(BuildConfig.FIREBASE_DATABASE_ID)
        val xmlDbId = runCatching { context.getString(R.string.firestore_database_id) }
            .getOrNull()?.trim()?.takeIf { !isPlaceholderOrBlank(it) }
        val resolvedDbId = envDbId ?: xmlDbId.orEmpty()
        val dbSource = when {
            envDbId != null -> "Secrets (.env / BuildConfig)"
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

        val projectId = resolveProjectId(context).orEmpty()
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
