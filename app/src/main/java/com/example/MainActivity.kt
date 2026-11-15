package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.ChatRepository
import com.example.ui.RavenApp
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CloudinaryHelper
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private val chatRepository by lazy {
        val dbId = try {
            getString(R.string.firestore_database_id)
        } catch (e: Exception) {
            null
        }
        ChatRepository(
            scope = lifecycleScope,
            databaseId = dbId,
            appContext = applicationContext
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CloudinaryHelper.init(applicationContext)
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    chatRepository.updateFcmToken(token)
                }
            }
        } catch (_: Exception) {
        }
        enableEdgeToEdge()
        setContent {
            val appSettings by chatRepository.appSettings.collectAsState()
            MyApplicationTheme(
                themeMode = appSettings.themeMode,
                accentColorHex = appSettings.accentColorHex
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    RavenApp(repository = chatRepository)
                }
            }
        }
    }

    override fun onDestroy() {
        chatRepository.releaseResources()
        super.onDestroy()
    }
}

