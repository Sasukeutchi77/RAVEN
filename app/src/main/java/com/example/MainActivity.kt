package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ui.RavenApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RavenAppViewModel
import com.example.util.CloudinaryHelper
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private val appViewModel: RavenAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chatRepository = appViewModel.repository

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                chatRepository.onAppBackgroundStateChanged(isBackground = false)
            }

            override fun onStop(owner: LifecycleOwner) {
                // Ignore transient onStop during screen rotation
                if (!isChangingConfigurations) {
                    chatRepository.onAppBackgroundStateChanged(isBackground = true)
                }
            }
        })

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
            val isViewingVoiceRoom by appViewModel.isViewingFullVoiceRoom.collectAsState()
            MyApplicationTheme(
                themeMode = appSettings.themeMode,
                accentColorHex = appSettings.accentColorHex
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    RavenApp(
                        repository = chatRepository,
                        isViewingFullVoiceRoom = isViewingVoiceRoom,
                        onViewingFullVoiceRoomChange = { appViewModel.setViewingFullVoiceRoom(it) }
                    )
                }
            }
        }
    }
}
