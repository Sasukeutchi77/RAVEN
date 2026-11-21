package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.PlatformLayoutOverride

enum class RavenPlatformKind(val label: String) {
    ANDROID_MOBILE("Android Mobile"),
    ANDROID_TABLET("Tablette Android"),
    PC_DESKTOP("PC / Ordinateur")
}

enum class RavenWindowSizeClass(val label: String) {
    COMPACT("Compact (< 600 dp)"),
    MEDIUM("Moyen (600–839 dp)"),
    EXPANDED("Étendu (840–1179 dp)"),
    LARGE_DESKTOP("Grand écran PC (≥ 1180 dp)")
}

enum class ActivePointerMode(val label: String) {
    TOUCH("Tactile"),
    MOUSE("Souris / Trackpad"),
    STYLUS("Stylet")
}

/**
 * Pure, deterministic platform signals collected from Android OS / InputManager / Window metrics.
 * Contains no privacy-sensitive hardware identifiers (no IMEI, MAC, serial, or ANDROID_ID).
 */
data class PlatformSignals(
    val hasPcFeature: Boolean = false,
    val isChromeOs: Boolean = false,
    val isWindowsPlayGamesPc: Boolean = false,
    val isDesktopUiMode: Boolean = false,
    val hasHardwareKeyboard: Boolean = false,
    val hasMouseOrTrackpad: Boolean = false,
    val hasTouchscreen: Boolean = true,
    val smallestScreenWidthDp: Int = 360,
    val windowWidthDp: Int = 360,
    val windowHeightDp: Int = 780,
    val activePointerMode: ActivePointerMode = ActivePointerMode.TOUCH,
    val androidRelease: String = "14"
)

data class RavenPlatformProfile(
    val detectedPlatform: RavenPlatformKind,
    val effectivePlatform: RavenPlatformKind,
    val layoutOverride: PlatformLayoutOverride,
    val windowSizeClass: RavenWindowSizeClass,
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val isPc: Boolean,
    val isTablet: Boolean,
    val isMobile: Boolean,
    val isDetectedPc: Boolean,
    val useDesktopLayout: Boolean,
    val useSplitPaneDm: Boolean,
    val dockRightMemberPanel: Boolean,
    val supportsEnterToSend: Boolean,
    val isChromeOs: Boolean,
    val isWindowsPlayGamesPc: Boolean,
    val isDesktopUiMode: Boolean,
    val hasHardwareKeyboard: Boolean,
    val hasMouseOrTrackpad: Boolean,
    val hasTouchscreen: Boolean,
    val activePointerMode: ActivePointerMode,
    val platformBadgeLabel: String,
    val sessionPlatformLabel: String,
    val detectionReasons: List<String>
)

object PlatformEnvironmentDetector {
    private const val FEATURE_ARC = "org.chromium.arc"
    private const val FEATURE_ARC_DEVICE_MGMT = "org.chromium.arc.device_management"
    private const val FEATURE_HPE_EXPERIENCE = "com.google.android.play.feature.HPE_EXPERIENCE"

    fun classifyWindowSize(widthDp: Int): RavenWindowSizeClass = when {
        widthDp >= 1180 -> RavenWindowSizeClass.LARGE_DESKTOP
        widthDp >= 840 -> RavenWindowSizeClass.EXPANDED
        widthDp >= 600 -> RavenWindowSizeClass.MEDIUM
        else -> RavenWindowSizeClass.COMPACT
    }

    fun evaluate(
        signals: PlatformSignals,
        override: PlatformLayoutOverride = PlatformLayoutOverride.AUTO
    ): RavenPlatformProfile {
        val reasons = mutableListOf<String>()
        if (signals.hasPcFeature) reasons += "SystemFeature PC"
        if (signals.isChromeOs) reasons += "ChromeOS / ARC++"
        if (signals.isWindowsPlayGamesPc) reasons += "PC Windows (Play Games)"
        if (signals.isDesktopUiMode) reasons += "UI Mode Desktop"
        if (signals.hasHardwareKeyboard) reasons += "Clavier physique"
        if (signals.hasMouseOrTrackpad) reasons += "Souris / Trackpad"
        if (!signals.hasTouchscreen) reasons += "Sans écran tactile"
        if (signals.activePointerMode == ActivePointerMode.MOUSE) reasons += "Pointeur souris actif"

        val hasDesktopPeripherals = signals.hasHardwareKeyboard &&
            (signals.hasMouseOrTrackpad || signals.activePointerMode == ActivePointerMode.MOUSE)

        val isPcHardwareOrOs = signals.hasPcFeature ||
            signals.isChromeOs ||
            signals.isWindowsPlayGamesPc ||
            signals.isDesktopUiMode ||
            (!signals.hasTouchscreen && (signals.hasHardwareKeyboard || signals.hasMouseOrTrackpad)) ||
            (hasDesktopPeripherals && (signals.smallestScreenWidthDp >= 600 || signals.windowWidthDp >= 840))

        val detectedPlatform = when {
            isPcHardwareOrOs -> RavenPlatformKind.PC_DESKTOP
            signals.smallestScreenWidthDp >= 600 || signals.windowWidthDp >= 840 -> RavenPlatformKind.ANDROID_TABLET
            else -> RavenPlatformKind.ANDROID_MOBILE
        }

        val effectivePlatform = when (override) {
            PlatformLayoutOverride.AUTO -> detectedPlatform
            PlatformLayoutOverride.FORCE_DESKTOP -> RavenPlatformKind.PC_DESKTOP
            PlatformLayoutOverride.FORCE_MOBILE -> RavenPlatformKind.ANDROID_MOBILE
        }

        val sizeClass = classifyWindowSize(signals.windowWidthDp)

        val useDesktopLayout = when (override) {
            PlatformLayoutOverride.FORCE_DESKTOP -> true
            PlatformLayoutOverride.FORCE_MOBILE -> false
            PlatformLayoutOverride.AUTO ->
                sizeClass == RavenWindowSizeClass.EXPANDED ||
                    sizeClass == RavenWindowSizeClass.LARGE_DESKTOP ||
                    (detectedPlatform == RavenPlatformKind.PC_DESKTOP && signals.windowWidthDp >= 640)
        }

        val useSplitPaneDm = when (override) {
            PlatformLayoutOverride.FORCE_DESKTOP -> signals.windowWidthDp >= 640
            PlatformLayoutOverride.FORCE_MOBILE -> false
            PlatformLayoutOverride.AUTO ->
                sizeClass == RavenWindowSizeClass.EXPANDED || sizeClass == RavenWindowSizeClass.LARGE_DESKTOP
        }

        val dockRightMemberPanel = when (override) {
            PlatformLayoutOverride.FORCE_MOBILE -> false
            PlatformLayoutOverride.FORCE_DESKTOP -> signals.windowWidthDp >= 980
            PlatformLayoutOverride.AUTO ->
                sizeClass == RavenWindowSizeClass.LARGE_DESKTOP ||
                    (effectivePlatform == RavenPlatformKind.PC_DESKTOP && signals.windowWidthDp >= 1060)
        }

        val supportsEnterToSend = effectivePlatform == RavenPlatformKind.PC_DESKTOP || signals.hasHardwareKeyboard

        val platformBadgeLabel = when {
            effectivePlatform == RavenPlatformKind.PC_DESKTOP && signals.isChromeOs -> "PC • ChromeOS"
            effectivePlatform == RavenPlatformKind.PC_DESKTOP && signals.isWindowsPlayGamesPc -> "PC • Windows"
            effectivePlatform == RavenPlatformKind.PC_DESKTOP && override == PlatformLayoutOverride.FORCE_DESKTOP &&
                detectedPlatform != RavenPlatformKind.PC_DESKTOP -> "Mode PC (Forcé)"
            effectivePlatform == RavenPlatformKind.PC_DESKTOP -> "PC • Bureau"
            effectivePlatform == RavenPlatformKind.ANDROID_TABLET -> "Tablette Android"
            override == PlatformLayoutOverride.FORCE_MOBILE &&
                detectedPlatform != RavenPlatformKind.ANDROID_MOBILE -> "Mode Mobile (Forcé)"
            else -> "Android Mobile"
        }

        val releaseClean = signals.androidRelease.ifBlank { "14" }.take(12)
        val sessionPlatformLabel = when {
            detectedPlatform == RavenPlatformKind.PC_DESKTOP && signals.isChromeOs -> "PC / ChromeOS"
            detectedPlatform == RavenPlatformKind.PC_DESKTOP && signals.isWindowsPlayGamesPc -> "PC / Windows"
            detectedPlatform == RavenPlatformKind.PC_DESKTOP -> "PC / Bureau"
            detectedPlatform == RavenPlatformKind.ANDROID_TABLET -> "Tablette Android"
            else -> "Android $releaseClean"
        }.take(32)

        return RavenPlatformProfile(
            detectedPlatform = detectedPlatform,
            effectivePlatform = effectivePlatform,
            layoutOverride = override,
            windowSizeClass = sizeClass,
            windowWidthDp = signals.windowWidthDp,
            windowHeightDp = signals.windowHeightDp,
            isPc = effectivePlatform == RavenPlatformKind.PC_DESKTOP,
            isTablet = effectivePlatform == RavenPlatformKind.ANDROID_TABLET,
            isMobile = effectivePlatform == RavenPlatformKind.ANDROID_MOBILE,
            isDetectedPc = detectedPlatform == RavenPlatformKind.PC_DESKTOP,
            useDesktopLayout = useDesktopLayout,
            useSplitPaneDm = useSplitPaneDm,
            dockRightMemberPanel = dockRightMemberPanel,
            supportsEnterToSend = supportsEnterToSend,
            isChromeOs = signals.isChromeOs,
            isWindowsPlayGamesPc = signals.isWindowsPlayGamesPc,
            isDesktopUiMode = signals.isDesktopUiMode,
            hasHardwareKeyboard = signals.hasHardwareKeyboard,
            hasMouseOrTrackpad = signals.hasMouseOrTrackpad,
            hasTouchscreen = signals.hasTouchscreen,
            activePointerMode = signals.activePointerMode,
            platformBadgeLabel = platformBadgeLabel,
            sessionPlatformLabel = sessionPlatformLabel,
            detectionReasons = reasons.distinct()
        )
    }

    fun collectSignals(
        context: Context,
        windowWidthDp: Int = context.resources.configuration.screenWidthDp.coerceAtLeast(320),
        windowHeightDp: Int = context.resources.configuration.screenHeightDp.coerceAtLeast(480),
        activePointerMode: ActivePointerMode = ActivePointerMode.TOUCH
    ): PlatformSignals {
        val pm = context.packageManager
        val config = context.resources.configuration

        val hasPcFeature = safeHasSystemFeature(pm, PackageManager.FEATURE_PC)
        val hardwareStr = (Build.HARDWARE ?: "").lowercase()
        val boardStr = (Build.BOARD ?: "").lowercase()
        val modelStr = (Build.MODEL ?: "").lowercase()
        val brandStr = (Build.BRAND ?: "").lowercase()
        val manufacturerStr = (Build.MANUFACTURER ?: "").lowercase()

        val isChromeOs = safeHasSystemFeature(pm, FEATURE_ARC) ||
            safeHasSystemFeature(pm, FEATURE_ARC_DEVICE_MGMT) ||
            hardwareStr.contains("cheets") ||
            boardStr.contains("cheets")

        val isWindowsPlayGamesPc = safeHasSystemFeature(pm, FEATURE_HPE_EXPERIENCE) ||
            (manufacturerStr.contains("google") && (modelStr.contains("windows") || boardStr.contains("hpe")))

        val isDesktopUiMode = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK

        val hasTouchscreenFeature = runCatching {
            pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        }.getOrDefault(config.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH)

        val configHardKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

        var inputKeyboardFound = false
        var inputMouseFound = false
        runCatching {
            val ids = InputDevice.getDeviceIds()
            for (id in ids) {
                val device = InputDevice.getDevice(id) ?: continue
                if (device.isVirtual) continue
                val isAlphaKeyboard = (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                val isMouseOrTrackpad = (device.sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                    (device.sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
                if (isAlphaKeyboard) inputKeyboardFound = true
                if (isMouseOrTrackpad) inputMouseFound = true
            }
        }

        val isEmulatorOrDesktopFallbackBrand = (brandStr.contains("chromium") || modelStr.contains("chromebook"))
        val smallestSw = if (config.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            config.smallestScreenWidthDp
        } else {
            minOf(windowWidthDp, windowHeightDp)
        }

        return PlatformSignals(
            hasPcFeature = hasPcFeature,
            isChromeOs = isChromeOs || isEmulatorOrDesktopFallbackBrand,
            isWindowsPlayGamesPc = isWindowsPlayGamesPc,
            isDesktopUiMode = isDesktopUiMode,
            hasHardwareKeyboard = configHardKeyboard || inputKeyboardFound,
            hasMouseOrTrackpad = inputMouseFound || activePointerMode == ActivePointerMode.MOUSE,
            hasTouchscreen = hasTouchscreenFeature && config.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH,
            smallestScreenWidthDp = smallestSw,
            windowWidthDp = windowWidthDp,
            windowHeightDp = windowHeightDp,
            activePointerMode = activePointerMode,
            androidRelease = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"
        )
    }

    fun detectSessionPlatformLabel(context: Context?): String {
        if (context == null) {
            val hw = (Build.HARDWARE ?: "").lowercase()
            val board = (Build.BOARD ?: "").lowercase()
            return when {
                hw.contains("cheets") || board.contains("cheets") -> "PC / ChromeOS"
                else -> "Android ${Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "14"}".take(32)
            }
        }
        val signals = collectSignals(context)
        return evaluate(signals, PlatformLayoutOverride.AUTO).sessionPlatformLabel
    }

    private fun safeHasSystemFeature(pm: PackageManager?, featureName: String): Boolean {
        if (pm == null) return false
        return runCatching { pm.hasSystemFeature(featureName) }.getOrDefault(false)
    }
}

val LocalRavenPlatform = compositionLocalOf {
    PlatformEnvironmentDetector.evaluate(PlatformSignals())
}

@Composable
fun rememberRavenPlatformProfile(
    windowWidthDp: Dp,
    windowHeightDp: Dp,
    layoutOverride: PlatformLayoutOverride,
    activePointerMode: ActivePointerMode
): RavenPlatformProfile {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var inputDeviceRevision by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val inputManager = runCatching {
            context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        }.getOrNull()
        val listener = if (inputManager != null) {
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    inputDeviceRevision++
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    inputDeviceRevision++
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    inputDeviceRevision++
                }
            }
        } else null

        if (inputManager != null && listener != null) {
            runCatching { inputManager.registerInputDeviceListener(listener, null) }
        }
        onDispose {
            if (inputManager != null && listener != null) {
                runCatching { inputManager.unregisterInputDeviceListener(listener) }
            }
        }
    }

    val widthInt = windowWidthDp.value.toInt().coerceAtLeast(1)
    val heightInt = windowHeightDp.value.toInt().coerceAtLeast(1)

    return remember(
        configuration,
        widthInt,
        heightInt,
        layoutOverride,
        activePointerMode,
        inputDeviceRevision
    ) {
        val signals = PlatformEnvironmentDetector.collectSignals(
            context = context,
            windowWidthDp = widthInt,
            windowHeightDp = heightInt,
            activePointerMode = activePointerMode
        )
        PlatformEnvironmentDetector.evaluate(signals, layoutOverride)
    }
}

fun Modifier.desktopHoverPointer(enabled: Boolean = true): Modifier =
    if (enabled) this.pointerHoverIcon(PointerIcon.Hand) else this

fun Modifier.onSecondaryMouseClick(
    enabled: Boolean = true,
    onSecondaryClick: () -> Unit
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(onSecondaryClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onSecondaryClick()
                }
            }
        }
    }
}

fun Modifier.desktopHoverHighlight(
    shape: Shape = RoundedCornerShape(8.dp),
    hoverColor: Color = Color.White.copy(alpha = 0.06f),
    enabled: Boolean = true
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        this
            .hoverable(interactionSource = interactionSource)
            .background(if (isHovered) hoverColor else Color.Transparent, shape)
    }
}
