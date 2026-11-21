package com.example.util

import com.example.data.AccountSecurityCrypto
import com.example.model.PlatformLayoutOverride
import org.junit.Assert.*
import org.junit.Test

class PlatformEnvironmentUnitTest {

    @Test
    fun `detects standard Android handheld phone correctly`() {
        val profile = PlatformEnvironmentDetector.evaluate(
            PlatformSignals(
                hasTouchscreen = true,
                smallestScreenWidthDp = 360,
                windowWidthDp = 390,
                windowHeightDp = 844,
                androidRelease = "15"
            )
        )

        assertEquals(RavenPlatformKind.ANDROID_MOBILE, profile.detectedPlatform)
        assertEquals(RavenPlatformKind.ANDROID_MOBILE, profile.effectivePlatform)
        assertEquals(RavenWindowSizeClass.COMPACT, profile.windowSizeClass)
        assertFalse(profile.isPc)
        assertFalse(profile.useDesktopLayout)
        assertFalse(profile.useSplitPaneDm)
        assertFalse(profile.dockRightMemberPanel)
        assertFalse(profile.supportsEnterToSend)
        assertEquals("Android 15", profile.sessionPlatformLabel)
        assertTrue(profile.sessionPlatformLabel.length <= 32)
    }

    @Test
    fun `detects Android tablet with split-pane DM and sidebar layout`() {
        val profile = PlatformEnvironmentDetector.evaluate(
            PlatformSignals(
                hasTouchscreen = true,
                smallestScreenWidthDp = 768,
                windowWidthDp = 1024,
                windowHeightDp = 768,
                androidRelease = "14"
            )
        )

        assertEquals(RavenPlatformKind.ANDROID_TABLET, profile.detectedPlatform)
        assertTrue(profile.isTablet)
        assertFalse(profile.isPc)
        assertTrue(profile.useDesktopLayout)
        assertTrue(profile.useSplitPaneDm)
        assertFalse(profile.dockRightMemberPanel)
        assertEquals("Tablette Android", profile.sessionPlatformLabel)
        assertTrue(profile.sessionPlatformLabel.length <= 32)
    }

    @Test
    fun `detects ChromeOS PC with full desktop multi-pane and keyboard support`() {
        val profile = PlatformEnvironmentDetector.evaluate(
            PlatformSignals(
                hasPcFeature = true,
                isChromeOs = true,
                hasHardwareKeyboard = true,
                hasMouseOrTrackpad = true,
                hasTouchscreen = true,
                smallestScreenWidthDp = 800,
                windowWidthDp = 1366,
                windowHeightDp = 768,
                activePointerMode = ActivePointerMode.MOUSE
            )
        )

        assertEquals(RavenPlatformKind.PC_DESKTOP, profile.detectedPlatform)
        assertTrue(profile.isPc)
        assertTrue(profile.isDetectedPc)
        assertEquals(RavenWindowSizeClass.LARGE_DESKTOP, profile.windowSizeClass)
        assertTrue(profile.useDesktopLayout)
        assertTrue(profile.useSplitPaneDm)
        assertTrue(profile.dockRightMemberPanel)
        assertTrue(profile.supportsEnterToSend)
        assertEquals("PC / ChromeOS", profile.sessionPlatformLabel)
        assertTrue(profile.sessionPlatformLabel.length <= 32)
    }

    @Test
    fun `detects Windows Play Games PC accurately`() {
        val profile = PlatformEnvironmentDetector.evaluate(
            PlatformSignals(
                hasPcFeature = true,
                isWindowsPlayGamesPc = true,
                hasHardwareKeyboard = true,
                hasMouseOrTrackpad = true,
                hasTouchscreen = false,
                smallestScreenWidthDp = 1080,
                windowWidthDp = 1600,
                windowHeightDp = 900,
                activePointerMode = ActivePointerMode.MOUSE
            )
        )

        assertEquals(RavenPlatformKind.PC_DESKTOP, profile.detectedPlatform)
        assertEquals("PC • Windows", profile.platformBadgeLabel)
        assertEquals("PC / Windows", profile.sessionPlatformLabel)
        assertTrue(profile.sessionPlatformLabel.length <= 32)
        assertTrue(profile.dockRightMemberPanel)
    }

    @Test
    fun `detects external desktop DeX or keyboard+mouse mode even without ChromeOS`() {
        val profile = PlatformEnvironmentDetector.evaluate(
            PlatformSignals(
                isDesktopUiMode = true,
                hasHardwareKeyboard = true,
                hasMouseOrTrackpad = true,
                smallestScreenWidthDp = 900,
                windowWidthDp = 1280,
                windowHeightDp = 720,
                activePointerMode = ActivePointerMode.MOUSE
            )
        )

        assertEquals(RavenPlatformKind.PC_DESKTOP, profile.detectedPlatform)
        assertEquals("PC / Bureau", profile.sessionPlatformLabel)
        assertTrue(profile.useDesktopLayout)
        assertTrue(profile.dockRightMemberPanel)
        assertTrue(profile.supportsEnterToSend)
    }

    @Test
    fun `layout override preserves detected hardware while toggling UI mode`() {
        val compactPhoneSignals = PlatformSignals(
            hasTouchscreen = true,
            smallestScreenWidthDp = 360,
            windowWidthDp = 390,
            windowHeightDp = 844
        )
        val forcedDesktop = PlatformEnvironmentDetector.evaluate(
            compactPhoneSignals,
            PlatformLayoutOverride.FORCE_DESKTOP
        )
        assertEquals(RavenPlatformKind.ANDROID_MOBILE, forcedDesktop.detectedPlatform)
        assertEquals(RavenPlatformKind.PC_DESKTOP, forcedDesktop.effectivePlatform)
        assertTrue(forcedDesktop.useDesktopLayout)
        assertTrue(forcedDesktop.isPc)

        val pcSignals = PlatformSignals(
            hasPcFeature = true,
            hasHardwareKeyboard = true,
            hasMouseOrTrackpad = true,
            windowWidthDp = 1440,
            windowHeightDp = 900
        )
        val forcedMobile = PlatformEnvironmentDetector.evaluate(
            pcSignals,
            PlatformLayoutOverride.FORCE_MOBILE
        )
        assertEquals(RavenPlatformKind.PC_DESKTOP, forcedMobile.detectedPlatform)
        assertEquals(RavenPlatformKind.ANDROID_MOBILE, forcedMobile.effectivePlatform)
        assertFalse(forcedMobile.useDesktopLayout)
        assertFalse(forcedMobile.useSplitPaneDm)
        assertFalse(forcedMobile.dockRightMemberPanel)
    }

    @Test
    fun `account security session platform label satisfies firestore rule max length`() {
        val label = AccountSecurityCrypto.currentPlatformLabel(null)
        assertTrue(label.isNotBlank())
        assertTrue(label.length <= 32)
    }
}
