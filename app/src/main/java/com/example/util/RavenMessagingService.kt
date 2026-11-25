package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RavenMessagingService : FirebaseMessagingService() {

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val cleanToken = token.trim()
        if (cleanToken.length !in 20..512 || cleanToken.contains("..")) return
        try {
            getSharedPreferences("raven_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("fcm_token", cleanToken)
                .apply()

            val uid = Firebase.auth.currentUser?.uid ?: return
            val db = FirebaseConfigManager.getFirestoreInstance(applicationContext) ?: return
            db.collection("users")
                .document(uid)
                .set(mapOf("userId" to uid, "fcmToken" to cleanToken), SetOptions.merge())
        } catch (e: Exception) {
            Log.w("RavenFCM", "Unable to store FCM token", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val rawTitle = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Raven"
        val rawBody = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Nouveau message reçu"
        val safeTitle = sanitizeNotificationField(rawTitle, 100).ifBlank { "Raven" }
        val safeBody = sanitizeNotificationField(rawBody, 300).ifBlank { "Nouveau message reçu" }
        NotificationHelper.showNotification(applicationContext, safeTitle, safeBody)
    }

    companion object {
        internal fun sanitizeNotificationField(value: String, maxLen: Int): String {
            return value
                .replace(Regex("[\\p{Cntrl}&&[^\n]]"), " ")
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(maxLen)
        }
    }
}
