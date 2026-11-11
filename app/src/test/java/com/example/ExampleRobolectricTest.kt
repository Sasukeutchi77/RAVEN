package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Raven", appName)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository sends message and joins voice`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        assertEquals("server_main", repo.selectedServerId.value)

        // Send message
        repo.sendMessage("c_general", "Test Discord message")
        val messages = repo.messages.value["c_general"]
        assertNotNull(messages)
        assertTrue(messages!!.any { it.text == "Test Discord message" })

        // Join Voice Channel
        val voiceChannel = repo.servers.value.first { it.id == "server_main" }
            .channels.first { it.type == com.example.model.ChannelType.VOICE }
        repo.joinVoiceChannel(voiceChannel)
        assertNotNull(repo.connectedVoiceChannel.value)
        assertEquals(voiceChannel.id, repo.connectedVoiceChannel.value?.id)

        // Leave Voice
        repo.leaveVoiceChannel()
        assertNull(repo.connectedVoiceChannel.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `chat repository updates full user profile`() = runTest {
        val testScope = TestScope(testScheduler)
        val repo = ChatRepository(testScope)

        repo.updateUserProfile(
            name = "Alexandre",
            pronouns = "iel",
            bio = "Nouvelle bio Discord",
            customStatus = "En réunion",
            customStatusEmoji = "code",
            activity = "Développe une app"
        )

        val updated = repo.currentUser.value
        assertEquals("Alexandre", updated.name)
        assertEquals("iel", updated.pronouns)
        assertEquals("Nouvelle bio Discord", updated.bio)
        assertEquals("En réunion", updated.customStatus)
        assertEquals("code", updated.customStatusEmoji)
        assertEquals("Développe une app", updated.activity)

        // Test toggle Super Badge
        val initialBadges = updated.badges
        assertTrue(initialBadges.contains("ACTIVE_DEV"))
        repo.toggleUserBadge("ACTIVE_DEV")
        assertFalse(repo.currentUser.value.badges.contains("ACTIVE_DEV"))
        repo.toggleUserBadge("ACTIVE_DEV")
        assertTrue(repo.currentUser.value.badges.contains("ACTIVE_DEV"))
    }
}
