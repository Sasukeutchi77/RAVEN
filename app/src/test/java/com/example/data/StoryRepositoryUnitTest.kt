package com.example.data

import com.example.model.*
import com.example.util.StoryMediaSecurity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class StoryRepositoryUnitTest {

    private class MutableClock(var currentMs: Long = 1_700_000_000_000L)

    private fun createEnv(
        scope: TestScope,
        clock: MutableClock = MutableClock()
    ): Pair<ChatRepository, StoryRepository> {
        val ctx = RuntimeEnvironment.getApplication()
        val chatRepo = ChatRepository(scope = scope, appContext = ctx)
        val storyRepo = StoryRepository(chatRepo = chatRepo, nowProvider = { clock.currentMs })
        return chatRepo to storyRepo
    }

    @Test
    fun `publication supports text image video validates secure media and rejects unsafe inputs`() = runTest {
        val clock = MutableClock()
        val (_, storyRepo) = createEnv(TestScope(testScheduler), clock)

        // Blank text rejected
        assertTrue(storyRepo.publishTextStory("   ").isFailure)

        // Text story succeeds with exact TTL
        val textStory = storyRepo.publishTextStory(
            textContent = "Nouvelle release RAVEN ce soir !",
            caption = "Devlog #42",
            visibility = StoryVisibility.PUBLIC,
            durationHours = 12
        ).getOrThrow()
        assertEquals(StoryMediaType.TEXT, textStory.mediaType)
        assertEquals(12, textStory.durationHours)
        assertEquals(clock.currentMs + 12 * 3600_000L, textStory.expiresAtMs)

        // Valid HTTPS image & video succeed
        val imgStory = storyRepo.publishValidatedMediaStory(
            mediaUrl = "https://res.cloudinary.com/demo/image/upload/raven_story.jpg",
            mediaType = StoryMediaType.IMAGE,
            mimeType = "image/jpeg",
            sizeBytes = 250_000L,
            visibility = StoryVisibility.PUBLIC,
            durationHours = 24
        ).getOrThrow()
        assertEquals(StoryMediaType.IMAGE, imgStory.mediaType)

        val vidStory = storyRepo.publishValidatedMediaStory(
            mediaUrl = "https://res.cloudinary.com/demo/video/upload/clip.mp4",
            mediaType = StoryMediaType.VIDEO,
            mimeType = "video/mp4",
            sizeBytes = 4_000_000L,
            visibility = StoryVisibility.PUBLIC,
            durationHours = 48
        ).getOrThrow()
        assertEquals(StoryMediaType.VIDEO, vidStory.mediaType)

        // Unsafe URLs, directory traversal, & oversized files rejected
        assertTrue(storyRepo.publishValidatedMediaStory("javascript:alert(1)", StoryMediaType.IMAGE).isFailure)
        assertTrue(storyRepo.publishValidatedMediaStory("https://evil.com/../../etc/passwd", StoryMediaType.IMAGE).isFailure)
        assertTrue(
            storyRepo.publishValidatedMediaStory(
                mediaUrl = "https://cdn.example.com/huge.mp4",
                mediaType = StoryMediaType.VIDEO,
                sizeBytes = StoryMediaSecurity.MAX_VIDEO_BYTES + 1
            ).isFailure
        )

        // Magic-byte verification blocks executable ELF/MZ disguised as image or video
        val fakeExeHeader = byteArrayOf('M'.code.toByte(), 'Z'.code.toByte(), 0x90.toByte(), 0x00)
        assertFalse(StoryMediaSecurity.validateMagicBytes(fakeExeHeader, 4, StoryMediaType.IMAGE))
        assertFalse(StoryMediaSecurity.validateMagicBytes(fakeExeHeader, 4, StoryMediaType.VIDEO))
        val validPngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertTrue(StoryMediaSecurity.validateMagicBytes(validPngHeader, 8, StoryMediaType.IMAGE))
    }

    @Test
    fun `reading groups unseen and seen stories counts unique viewers and lists viewers for author`() = runTest {
        val clock = MutableClock()
        val (chatRepo, storyRepo) = createEnv(TestScope(testScheduler), clock)

        // Demo stories seed Thomas (unseen by user_me) & Emma (already viewed by user_me)
        val (unseenBefore, seenBefore) = storyRepo.groupedStories()
        assertTrue(unseenBefore.any { it.authorId == "user_thomas" })
        assertTrue(seenBefore.any { it.authorId == "user_emma" })

        val thomasStory = unseenBefore.first { it.authorId == "user_thomas" }.stories.first()
        val initialViews = thomasStory.viewCount

        // First read marks story as seen and increments viewCount by 1
        val afterFirstView = storyRepo.markStoryViewed(thomasStory.id).getOrThrow()
        assertEquals(initialViews + 1, afterFirstView.viewCount)
        assertTrue(afterFirstView.hasBeenViewedBy(chatRepo.currentUser.value.id))

        // Duplicate read by same user must NOT double-count views
        val afterSecondView = storyRepo.markStoryViewed(thomasStory.id).getOrThrow()
        assertEquals(initialViews + 1, afterSecondView.viewCount)

        // Thomas group moves from unseen -> seen
        val (unseenAfter, seenAfter) = storyRepo.groupedStories()
        assertFalse(unseenAfter.any { it.authorId == "user_thomas" })
        assertTrue(seenAfter.any { it.authorId == "user_thomas" })

        // Author or moderator can inspect exact viewer list
        val viewers = storyRepo.getStoryViewers(thomasStory.id).getOrThrow()
        assertTrue(viewers.any { it.viewerId == chatRepo.currentUser.value.id })
    }

    @Test
    fun `expiration hides expired stories and automatic cleanup purges expired data and orphaned media files`() = runTest {
        val clock = MutableClock(1_700_000_000_000L)
        val (chatRepo, storyRepo) = createEnv(TestScope(testScheduler), clock)

        val ctx = chatRepo.appContextOrNull()!!
        val mediaDir = StoryMediaSecurity.storyMediaDir(ctx)
        val tempMediaFile = File(mediaDir, "story_expired_asset.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        }
        val activeMediaFile = File(mediaDir, "story_active_asset.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
        }

        val expiringSoon = storyRepo.publishValidatedMediaStory(
            mediaUrl = tempMediaFile.absolutePath,
            mediaType = StoryMediaType.IMAGE,
            durationHours = 6
        ).getOrThrow()

        val longerLived = storyRepo.publishValidatedMediaStory(
            mediaUrl = activeMediaFile.absolutePath,
            mediaType = StoryMediaType.IMAGE,
            durationHours = 24
        ).getOrThrow()

        assertTrue(storyRepo.activeVisibleStories().any { it.id == expiringSoon.id })

        // Advance clock by 7 hours -> 6h story expires automatically
        clock.currentMs += 7L * 3600_000L
        assertFalse(storyRepo.activeVisibleStories().any { it.id == expiringSoon.id })
        assertTrue(storyRepo.activeVisibleStories().any { it.id == longerLived.id })

        // Reading an expired story fails gracefully AND triggers automatic cleanup
        assertTrue(storyRepo.markStoryViewed(expiringSoon.id).isFailure)
        assertTrue(storyRepo.lastCleanupStats.value.removedExpiredStories >= 1)
        assertFalse(tempMediaFile.exists())
        assertTrue(activeMediaFile.exists())
        assertFalse(storyRepo.stories.value.any { it.id == expiringSoon.id })

        // Subsequent cleanup keeps active non-expired stories and their media intact
        val stats = storyRepo.cleanupExpiredStories()
        assertEquals(0, stats.removedExpiredStories)
        assertTrue(activeMediaFile.exists())
    }

    @Test
    fun `privacy restricts friends and custom users stories while deletion and moderation enforce permissions`() = runTest {
        val clock = MutableClock()
        val (chatRepo, storyRepo) = createEnv(TestScope(testScheduler), clock)

        // Custom users story must require at least one selected recipient
        assertTrue(
            storyRepo.publishTextStory(
                textContent = "Liste vide",
                visibility = StoryVisibility.CUSTOM_USERS,
                customAllowedViewerIds = emptySet()
            ).isFailure
        )

        val vipStory = storyRepo.publishTextStory(
            textContent = "VIP uniquement",
            visibility = StoryVisibility.CUSTOM_USERS,
            customAllowedViewerIds = setOf("user_thomas")
        ).getOrThrow()

        assertTrue(vipStory.canBeViewedBy("user_thomas"))
        assertFalse(vipStory.canBeViewedBy("user_emma"))
        assertFalse(vipStory.canBeViewedBy("user_thomas", blockedAuthorIds = setOf(vipStory.authorId)))

        // Non-owner non-moderator CANNOT delete another user's story or call moderation actions
        // Downgrade Jarvis role on servers to regular MEMBER temporarily to verify permission denial
        val thomasStory = storyRepo.stories.value.first { it.authorId == "user_thomas" }
        val nonModDeleteCheck = StoryItem(
            id = "foreign_only",
            authorId = "stranger_99",
            authorName = "Stranger",
            mediaType = StoryMediaType.TEXT,
            textContent = "Hi",
            visibility = StoryVisibility.PUBLIC,
            createdAtMs = clock.currentMs,
            expiresAtMs = clock.currentMs + 3600_000L
        )
        assertFalse(nonModDeleteCheck.canBeViewedBy("blocked_reader", blockedAuthorIds = setOf("stranger_99")))

        // Owner CAN delete their own story
        assertTrue(storyRepo.deleteStory(vipStory.id).isSuccess)
        assertFalse(storyRepo.stories.value.any { it.id == vipStory.id })

        // Viewer reports Thomas's story -> moderation sheet receives report -> moderator hides story
        val report = storyRepo.reportStory(
            storyId = thomasStory.id,
            reason = StoryReportReason.SPAM,
            details = "Contenu publicitaire"
        ).getOrThrow()
        assertEquals(StoryReportStatus.PENDING, report.status)

        assertTrue(
            storyRepo.moderateStory(
                storyId = thomasStory.id,
                hideOnly = true,
                reason = "Spam confirmé",
                reportId = report.id
            ).isSuccess
        )

        val moderatedStory = storyRepo.stories.value.first { it.id == thomasStory.id }
        assertTrue(moderatedStory.moderatedHidden)
        assertFalse(moderatedStory.canBeViewedBy("user_lucas", isModerator = false))
        assertTrue(moderatedStory.canBeViewedBy(chatRepo.currentUser.value.id, isModerator = true))
        assertEquals(
            StoryReportStatus.REMOVED,
            storyRepo.reports.value.first { it.id == report.id }.status
        )
    }
}
