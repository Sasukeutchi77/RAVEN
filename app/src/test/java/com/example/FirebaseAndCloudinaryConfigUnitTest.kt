package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.util.CloudinaryHelper
import com.example.util.FirebaseConfigManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAndCloudinaryConfigUnitTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() {
        CloudinaryHelper.resetToBuildConfig(context)
    }

    @Test
    fun `placeholder detector rejects default and blank tokens while accepting valid values`() {
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank(null))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank(""))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("  "))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("CLOUDINARY_CLOUD_NAME_PLACEHOLDER"))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("FIREBASE_DATABASE_ID_PLACEHOLDER"))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("YOUR_PROJECT_ID"))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("CHANGE_ME_KEY"))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("demo"))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("unsigned_preset"))

        assertFalse(FirebaseConfigManager.isPlaceholderOrBlank("raven-prod-cloud"))
        assertFalse(FirebaseConfigManager.isPlaceholderOrBlank("ai-studio-android-raven-27cb5ccd"))
    }

    @Test
    fun `firebase config manager resolves provisioned database id and inspects status`() {
        val dbId = FirebaseConfigManager.resolveFirestoreDatabaseId(context)
        assertNotNull(dbId)
        assertTrue(dbId!!.isNotBlank())
        assertFalse(FirebaseConfigManager.isPlaceholderOrBlank(dbId))

        val status = FirebaseConfigManager.inspectStatus(context)
        assertEquals(context.packageName, status.packageName)
        assertEquals(dbId, status.databaseId)
        assertEquals("firebase_applet_config.xml", status.databaseSource)
    }

    @Test
    fun `cloudinary config validates tokens folders and supports save plus reset to buildconfig`() {
        assertFalse(CloudinaryHelper.isValidConfigToken("demo"))
        assertFalse(CloudinaryHelper.isValidConfigToken("CLOUDINARY_CLOUD_NAME_PLACEHOLDER"))
        assertFalse(CloudinaryHelper.isValidConfigToken("bad space!"))
        assertTrue(CloudinaryHelper.isValidConfigToken("raven_cloud_prod"))
        assertTrue(CloudinaryHelper.isValidConfigToken("preset-v2"))

        assertTrue(CloudinaryHelper.isValidFolderToken("raven_media"))
        assertTrue(CloudinaryHelper.isValidFolderToken("raven/uploads"))
        assertFalse(CloudinaryHelper.isValidFolderToken("../escape"))
        assertFalse(CloudinaryHelper.isValidFolderToken("/leading_slash"))
        assertFalse(CloudinaryHelper.isValidFolderToken("trailing_slash/"))

        assertFalse(CloudinaryHelper.isConfigured())
        assertEquals("Mode Local (non configuré)", CloudinaryHelper.configSourceLabel(context))

        val saved = CloudinaryHelper.saveConfig(
            context = context,
            name = "my_raven_cloud",
            preset = "raven_unsigned_v1",
            folder = "raven/prod_media"
        )
        assertTrue(saved)
        assertTrue(CloudinaryHelper.isConfigured())
        assertTrue(CloudinaryHelper.isManualOverride(context))
        assertEquals("my_raven_cloud", CloudinaryHelper.cloudName)
        assertEquals("raven_unsigned_v1", CloudinaryHelper.uploadPreset)
        assertEquals("raven/prod_media", CloudinaryHelper.uploadFolder)
        assertEquals("Configuration manuelle", CloudinaryHelper.configSourceLabel(context))

        // Re-initializing keeps persisted manual override
        CloudinaryHelper.init(context)
        assertEquals("my_raven_cloud", CloudinaryHelper.cloudName)
        assertEquals("raven/prod_media", CloudinaryHelper.uploadFolder)

        // Invalid token must fail save and keep prior valid state
        assertFalse(CloudinaryHelper.saveConfig(context, "bad/name!", "valid_preset", "raven_media"))
        assertEquals("my_raven_cloud", CloudinaryHelper.cloudName)

        // Reset restores BuildConfig / local default
        CloudinaryHelper.resetToBuildConfig(context)
        assertFalse(CloudinaryHelper.isManualOverride(context))
        assertFalse(CloudinaryHelper.isConfigured())
        assertEquals(CloudinaryHelper.DEFAULT_UPLOAD_FOLDER, CloudinaryHelper.uploadFolder)
    }

    @Test
    fun `uploadAudioFile gracefully handles blank or local fallback when unconfigured`() = runTest {
        assertEquals("", CloudinaryHelper.uploadAudioFile("   "))
        assertEquals(
            "https://res.cloudinary.com/demo/video/upload/note.m4a",
            CloudinaryHelper.uploadAudioFile("https://res.cloudinary.com/demo/video/upload/note.m4a")
        )
        assertEquals(
            "/tmp/local_voice_note.m4a",
            CloudinaryHelper.uploadAudioFile("/tmp/local_voice_note.m4a")
        )
    }

    @Test
    fun `sanitizeSyncMediaUrl blocks unsafe schemes path traversal and oversize urls`() {
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl(null))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("   "))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("content://media/external/images/media/42"))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("javascript:alert(1)"))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("vbscript:msgbox(1)"))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("data:image/png;base64,AAAA"))
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl("https://res.cloudinary.com/cloud/../secret"))
        val tooLongUrl = "https://res.cloudinary.com/cloud/image/upload/" + "a".repeat(800)
        assertNull(FirebaseConfigManager.sanitizeSyncMediaUrl(tooLongUrl, maxLen = 800))

        val validUrl = "  https://res.cloudinary.com/ravencloud/image/upload/v1/avatar.jpg  "
        assertEquals(
            "https://res.cloudinary.com/ravencloud/image/upload/v1/avatar.jpg",
            FirebaseConfigManager.sanitizeSyncMediaUrl(validUrl, maxLen = 800)
        )
    }

    @Test
    fun `quoted env values are trimmed cleanly by FirebaseConfigManager and CloudinaryHelper`() {
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("\"YOUR_PROJECT_ID\""))
        assertTrue(FirebaseConfigManager.isPlaceholderOrBlank("'demo'"))
        assertFalse(FirebaseConfigManager.isPlaceholderOrBlank("\"raven-prod-cloud\""))

        assertTrue(CloudinaryHelper.isValidConfigToken("\"my_cloud_prod\""))
        assertTrue(CloudinaryHelper.isValidConfigToken("'preset-v3'"))
        assertTrue(CloudinaryHelper.isValidFolderToken("\"raven/prod_media\""))
        assertFalse(CloudinaryHelper.isValidFolderToken("\"../escape\""))
    }
}
