package com.example.util

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RavenMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            getSharedPreferences("raven_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply()

            val uid = Firebase.auth.currentUser?.uid ?: return
            val dbId = getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
                .collection("users")
                .document(uid)
                .set(mapOf("userId" to uid, "fcmToken" to token), SetOptions.merge())
        } catch (e: Exception) {
            Log.w("RavenFCM", "Unable to store FCM token", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Raven"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Nouveau message reçu"
        NotificationHelper.showNotification(applicationContext, title, body)
    }
}
