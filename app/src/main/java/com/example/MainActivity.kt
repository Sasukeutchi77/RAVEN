package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.ChatRepository
import com.example.ui.RavenApp
import com.example.ui.theme.DiscordDarkChat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val chatRepository by lazy {
        val dbId = try {
            getString(R.string.firestore_database_id)
        } catch (e: Exception) {
            null
        }
        ChatRepository(lifecycleScope, dbId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DiscordDarkChat
                ) {
                    RavenApp(repository = chatRepository)
                }
            }
        }
    }
}
