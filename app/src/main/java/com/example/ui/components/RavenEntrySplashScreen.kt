package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.DiscordGreen
import com.example.ui.theme.RavenBodyFontFamily
import com.example.ui.theme.RavenDisplayFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private data class DriftingFeather(
    val xStartFraction: Float,
    val yStartFraction: Float,
    val lengthDp: Float,
    val fallSpeed: Float,
    val swayAmplitudeDp: Float,
    val tumbleSpeed: Float,
    val baseRotationDeg: Float,
    val phaseOffset: Float,
    val depthAlpha: Float,
    val isForeground: Boolean
)

private data class AtmosphericDustMote(
    val xFraction: Float,
    val yFraction: Float,
    val radiusDp: Float,
    val driftSpeed: Float,
    val phaseOffset: Float,
    val isIceBlue: Boolean
)

private val CinemaEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val CinemaEaseInOut = CubicBezierEasing(0.45f, 0f, 0.25f, 1f)

@Composable
fun RavenEntrySplashScreen(
    durationMs: Long = 3200L,
    onFinished: () -> Unit
) {
    // Cinematic camera dolly + 3D perspective settle
    val heroScale = remember { Animatable(1.24f) }
    val heroAlpha = remember { Animatable(0f) }
    val heroPitchX = remember { Animatable(9f) }
    val heroYawY = remember { Animatable(-7f) }

    // Studio key-light, eye gleam & anamorphic feather sweep
    val keyLightIntensity = remember { Animatable(0f) }
    val eyeGleamIntensity = remember { Animatable(0f) }
    val lightSweepProgress = remember { Animatable(-0.6f) }
    val horizonExpand = remember { Animatable(0f) }
    val titleCondense = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    var showTitle by remember { mutableStateOf(false) }
    var showStatusPill by remember { mutableStateOf(false) }
    var bootStageIndex by remember { mutableIntStateOf(0) }

    val feathers = remember {
        List(11) { idx ->
            DriftingFeather(
                xStartFraction = ((idx * 67 + 13) % 92 + 4) / 100f,
                yStartFraction = ((idx * 53 + 7) % 100) / 100f,
                lengthDp = 18f + (idx % 4) * 7f,
                fallSpeed = 0.28f + (idx % 3) * 0.14f,
                swayAmplitudeDp = 12f + (idx % 4) * 6f,
                tumbleSpeed = 1.1f + (idx % 3) * 0.45f,
                baseRotationDeg = -32f + (idx % 5) * 16f,
                phaseOffset = idx * 0.72f,
                depthAlpha = if (idx % 3 == 0) 0.62f else 0.34f,
                isForeground = idx % 3 == 0
            )
        }
    }

    val dustMotes = remember {
        List(26) { idx ->
            AtmosphericDustMote(
                xFraction = ((idx * 79 + 17) % 94 + 3) / 100f,
                yFraction = ((idx * 43 + 23) % 92 + 4) / 100f,
                radiusDp = 1.0f + (idx % 3) * 0.85f,
                driftSpeed = 0.08f + (idx % 4) * 0.03f,
                phaseOffset = idx * 0.55f,
                isIceBlue = idx % 2 == 0
            )
        }
    }

    val bootLabels = remember {
        listOf(
            "Éveil du réseau Raven...",
            "Chiffrement Gmail & session sécurisée",
            "Espace prêt"
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "raven_cinema_loop")
    val continuousCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "continuous_cycle"
    )
    val mistBreath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mist_breath"
    )
    val featherHover by infiniteTransition.animateFloat(
        initialValue = 0.992f,
        targetValue = 1.024f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "raven_breathing"
    )

    LaunchedEffect(Unit) {
        // Act 1: Dramatic chiaroscuro studio light & camera settle onto perched Raven
        launch {
            keyLightIntensity.animateTo(1f, tween(durationMillis = 1050, easing = CinemaEaseOut))
        }
        launch {
            delay(80L)
            heroAlpha.animateTo(1f, tween(durationMillis = 820, easing = CinemaEaseOut))
        }
        launch {
            heroScale.animateTo(1f, tween(durationMillis = 1350, easing = CinemaEaseOut))
        }
        launch {
            heroPitchX.animateTo(0f, tween(durationMillis = 1300, easing = CinemaEaseOut))
        }
        launch {
            heroYawY.animateTo(0f, tween(durationMillis = 1300, easing = CinemaEaseOut))
        }

        // Act 2: Eye gleam ignition + anamorphic plumage sweep + editorial title
        launch {
            delay(540L)
            eyeGleamIntensity.animateTo(1f, tween(durationMillis = 420, easing = CinemaEaseOut))
            delay(460L)
            eyeGleamIntensity.animateTo(0.45f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
        launch {
            delay(620L)
            lightSweepProgress.animateTo(1.6f, tween(durationMillis = 1100, easing = CinemaEaseInOut))
        }
        launch {
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (durationMs - 360L).coerceAtLeast(900L).toInt(),
                    easing = CinemaEaseInOut
                )
            )
        }

        delay(420L)
        showTitle = true
        launch {
            titleCondense.animateTo(1f, tween(durationMillis = 820, easing = CinemaEaseOut))
        }
        launch {
            delay(120L)
            horizonExpand.animateTo(1f, tween(durationMillis = 740, easing = CinemaEaseOut))
        }

        delay(380L)
        showStatusPill = true
        delay(680L)
        bootStageIndex = 1
        delay(800L)
        bootStageIndex = 2

        val remaining = (durationMs - 420L - 380L - 680L - 800L - 300L).coerceAtLeast(220L)
        delay(remaining)
        launch {
            exitAlpha.animateTo(0f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        }
        exitScale.animateTo(1.045f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020409))
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .drawBehind {
                val w = size.width
                val h = size.height
                val minDim = size.minDimension
                val maxDim = size.maxDimension
                val stageCenter = Offset(w * 0.5f, h * 0.39f)
                val light = keyLightIntensity.value

                // Pure pitch obsidian base + cool midnight ambient depth
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D1730).copy(alpha = 0.72f * light),
                            Color(0xFF060B18).copy(alpha = 0.90f),
                            Color(0xFF020409)
                        ),
                        center = stageCenter,
                        radius = maxDim * 0.66f
                    )
                )

                // Volumetric diagonal studio moonlight beam from upper-left
                val beamSwirlX = cos(mistBreath) * minDim * 0.045f
                val beamSwirlY = sin(mistBreath) * minDim * 0.025f
                val beamPath = Path().apply {
                    moveTo(w * 0.02f + beamSwirlX, -h * 0.05f)
                    lineTo(w * 0.44f + beamSwirlX, -h * 0.05f)
                    lineTo(w * 0.88f + beamSwirlX * 0.5f, h * 0.72f + beamSwirlY)
                    lineTo(w * 0.16f + beamSwirlX * 0.5f, h * 0.72f + beamSwirlY)
                    close()
                }
                drawPath(
                    path = beamPath,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF93C5FD).copy(alpha = 0.12f * light),
                            Color(0xFF38BDF8).copy(alpha = 0.055f * light),
                            Color.Transparent
                        ),
                        start = Offset(w * 0.18f, 0f),
                        end = Offset(w * 0.65f, h * 0.65f)
                    )
                )

                // Drifting volumetric cold mist clouds (parallax layer 1)
                val mistLeftCenter = Offset(
                    x = w * 0.28f + cos(mistBreath * 0.7f) * w * 0.08f,
                    y = h * 0.43f + sin(mistBreath * 0.5f) * h * 0.03f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E3A5F).copy(alpha = 0.22f * light),
                            Color.Transparent
                        ),
                        center = mistLeftCenter,
                        radius = minDim * 0.62f
                    ),
                    radius = minDim * 0.62f,
                    center = mistLeftCenter
                )

                val mistRightCenter = Offset(
                    x = w * 0.72f - cos(mistBreath * 0.6f) * w * 0.07f,
                    y = h * 0.34f - sin(mistBreath * 0.8f) * h * 0.025f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF312E81).copy(alpha = 0.18f * light),
                            Color.Transparent
                        ),
                        center = mistRightCenter,
                        radius = minDim * 0.54f
                    ),
                    radius = minDim * 0.54f,
                    center = mistRightCenter
                )

                // Back-plane drifting feathers & luminous airborne dust
                drawDustMotes(dustMotes, continuousCycle, mistBreath, light)
                drawDriftingFeathers(
                    feathers = feathers.filter { !it.isForeground },
                    cycle = continuousCycle,
                    mistBreath = mistBreath,
                    alphaMultiplier = light * 0.9f
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .testTag("raven_entry_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        val heroBoxSize = if (maxWidth < 360.dp) 232.dp else 274.dp

        // Foreground drifting feathers passing lightly in front of the scene for real depth
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDriftingFeathers(
                feathers = feathers.filter { it.isForeground },
                cycle = continuousCycle,
                mistBreath = mistBreath,
                alphaMultiplier = keyLightIntensity.value * 0.95f
            )
        }

        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Photorealistic Raven Portrait with Cinema Dolly, Seamless Vignette, Eye Gleam & Specular Sweep
            Box(
                modifier = Modifier
                    .size(heroBoxSize)
                    .graphicsLayer {
                        val combinedScale = heroScale.value * featherHover
                        scaleX = combinedScale
                        scaleY = combinedScale
                        rotationX = heroPitchX.value
                        rotationY = heroYawY.value
                        cameraDistance = 16f * density
                        alpha = heroAlpha.value
                    },
                contentAlignment = Alignment.Center
            ) {
                // Soft rim aura directly behind the raven silhouette
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width * 0.5f, size.height * 0.48f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.24f * keyLightIntensity.value),
                                Color(0xFF3730A3).copy(alpha = 0.16f * keyLightIntensity.value),
                                Color.Transparent
                            ),
                            center = c,
                            radius = size.minDimension * 0.50f
                        ),
                        radius = size.minDimension * 0.50f,
                        center = c
                    )
                }

                // Hyper-realistic 3D Raven artwork blended into void
                Image(
                    painter = painterResource(id = R.drawable.img_raven_splash_hero),
                    contentDescription = stringResource(id = R.string.splash_raven_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )

                // Seamless shadow vignette + specular feather light sweep + icy eye anamorphic flare
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w * 0.5f, h * 0.5f)

                    // Seamless radial edge vignette merging hero into #020409 pitch obsidian
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.58f to Color.Transparent,
                                0.80f to Color(0xFF020409).copy(alpha = 0.68f),
                                0.96f to Color(0xFF020409),
                                1.0f to Color(0xFF020409)
                            ),
                            center = center,
                            radius = w * 0.50f
                        ),
                        radius = w * 0.50f,
                        center = center
                    )

                    // Sweeping diagonal studio light across iridescent feathers
                    val sweep = lightSweepProgress.value
                    if (sweep in -0.55f..1.55f) {
                        val sweepX = w * sweep
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFBAE6FD).copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                start = Offset(sweepX - w * 0.22f, 0f),
                                end = Offset(sweepX + w * 0.18f, h)
                            ),
                            radius = w * 0.45f,
                            center = center,
                            blendMode = BlendMode.Screen
                        )
                    }

                    // Realistic icy-sapphire gleam aligned precisely with the Raven's eye (0.495, 0.215)
                    val eyeAlpha = eyeGleamIntensity.value
                    if (eyeAlpha > 0.02f) {
                        val eyePos = Offset(w * 0.495f, h * 0.215f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE0F2FE).copy(alpha = 0.72f * eyeAlpha),
                                    Color(0xFF38BDF8).copy(alpha = 0.34f * eyeAlpha),
                                    Color.Transparent
                                ),
                                center = eyePos,
                                radius = w * 0.095f
                            ),
                            radius = w * 0.095f,
                            center = eyePos,
                            blendMode = BlendMode.Screen
                        )
                        // Subtle anamorphic horizontal lens streak across the eye
                        val flareHalfW = w * (0.05f + 0.09f * eyeAlpha)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFBAE6FD).copy(alpha = 0.75f * eyeAlpha),
                                    Color.Transparent
                                ),
                                startX = eyePos.x - flareHalfW,
                                endX = eyePos.x + flareHalfW
                            ),
                            start = Offset(eyePos.x - flareHalfW, eyePos.y),
                            end = Offset(eyePos.x + flareHalfW, eyePos.y),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            blendMode = BlendMode.Screen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtle cinema horizon specular hairline beneath the Raven's perch
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(6.dp)
            ) {
                val expand = horizonExpand.value.coerceIn(0f, 1f)
                if (expand > 0.02f) {
                    val halfSpan = (size.width * 0.5f) * expand
                    val midX = size.width * 0.5f
                    val midY = size.height * 0.5f
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF38BDF8).copy(alpha = 0.55f),
                                Color(0xFFE0F2FE).copy(alpha = 0.90f),
                                Color(0xFF38BDF8).copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            startX = midX - halfSpan,
                            endX = midX + halfSpan
                        ),
                        start = Offset(midX - halfSpan, midY),
                        end = Offset(midX + halfSpan, midY),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(520, easing = CinemaEaseOut)) + slideInVertically(
                    animationSpec = tween(640, easing = CinemaEaseOut),
                    initialOffsetY = { it / 3 }
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "RAVEN",
                        style = TextStyle(
                            fontFamily = RavenDisplayFontFamily,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (18f - 10f * titleCondense.value).sp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White,
                                    Color(0xFFE2E8F0),
                                    Color(0xFF94A3B8)
                                )
                            )
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Messagerie chiffrée & salons vocaux HD",
                        fontFamily = RavenBodyFontFamily,
                        color = Color(0xFF94A3B8),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showStatusPill,
                enter = fadeIn(tween(420, easing = CinemaEaseOut)) + slideInVertically(
                    animationSpec = tween(420, easing = CinemaEaseOut),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC090E1C),
                    border = BorderStroke(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF7DD3FC).copy(alpha = 0.36f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    )
                ) {
                    AnimatedContent(
                        targetState = bootStageIndex,
                        transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                        label = "splash_boot_stage"
                    ) { stageIdx ->
                        val label = bootLabels[stageIdx.coerceIn(0, bootLabels.lastIndex)]
                        val isReadyStage = stageIdx == bootLabels.lastIndex
                        val dotColor = if (isReadyStage) DiscordGreen else Color(0xFF38BDF8)
                        val pulseScale = 0.85f + 0.25f * ((sin(mistBreath * 2f) + 1f) * 0.5f)
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .clip(CircleShape)
                                    .background(dotColor.copy(alpha = 0.28f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = label,
                                fontFamily = RavenBodyFontFamily,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Razor-thin liquid silver/sapphire progress hairline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(6.dp)
                    .alpha(0.95f)
            ) {
                val trackH = 2.5.dp.toPx()
                val topY = (size.height - trackH) / 2f
                val corner = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = Color(0xFF141B2D),
                    topLeft = Offset(0f, topY),
                    size = Size(size.width, trackH),
                    cornerRadius = corner
                )
                val fillW = (size.width * progressAnim.value).coerceAtLeast(0f)
                if (fillW > 2f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E3A8A),
                                Color(0xFF38BDF8),
                                Color(0xFFE0F2FE)
                            )
                        ),
                        topLeft = Offset(0f, topY),
                        size = Size(fillW, trackH),
                        cornerRadius = corner
                    )
                    drawCircle(
                        color = Color(0xFF7DD3FC).copy(alpha = 0.42f),
                        radius = 5.5.dp.toPx(),
                        center = Offset(fillW, size.height / 2f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.9.dp.toPx(),
                        center = Offset(fillW, size.height / 2f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawDustMotes(
    motes: List<AtmosphericDustMote>,
    cycle: Float,
    mistBreath: Float,
    alphaMultiplier: Float
) {
    val w = size.width
    val h = size.height
    motes.forEach { mote ->
        val progress = (mote.yFraction - cycle * mote.driftSpeed * 3.5f + 2f) % 1f
        val xDrift = sin(mistBreath + mote.phaseOffset) * 14.dp.toPx()
        val yPos = progress * h
        val edgeFade = (1f - abs(progress - 0.5f) * 1.9f).coerceIn(0f, 1f)
        val twinkle = 0.35f + 0.65f * ((sin(mistBreath * 1.6f + mote.phaseOffset) + 1f) * 0.5f)
        val color = if (mote.isIceBlue) Color(0xFFBAE6FD) else Color(0xFFCBD5E1)
        drawCircle(
            color = color.copy(alpha = alphaMultiplier * edgeFade * twinkle * 0.45f),
            radius = mote.radiusDp.dp.toPx(),
            center = Offset(w * mote.xFraction + xDrift, yPos)
        )
    }
}

private fun DrawScope.drawDriftingFeathers(
    feathers: List<DriftingFeather>,
    cycle: Float,
    mistBreath: Float,
    alphaMultiplier: Float
) {
    if (alphaMultiplier <= 0.01f) return
    val w = size.width
    val h = size.height

    feathers.forEach { feather ->
        val yProg = (feather.yStartFraction + cycle * feather.fallSpeed * 2.2f) % 1.16f - 0.08f
        val edgeFade = when {
            yProg < 0.12f -> (yProg + 0.08f) / 0.20f
            yProg > 0.88f -> (1.08f - yProg) / 0.20f
            else -> 1f
        }.coerceIn(0f, 1f)

        val swayPx = sin(mistBreath * 1.1f + feather.phaseOffset) * feather.swayAmplitudeDp.dp.toPx()
        val cx = w * feather.xStartFraction + swayPx
        val cy = h * yProg
        val len = feather.lengthDp.dp.toPx()
        val width = len * 0.27f
        val tumbleCos = cos(mistBreath * feather.tumbleSpeed + feather.phaseOffset)
        val tumbleScaleX = (0.30f + 0.70f * abs(tumbleCos)) * if (tumbleCos < 0f) -1f else 1f
        val rotDeg = feather.baseRotationDeg + sin(mistBreath + feather.phaseOffset) * 18f
        val finalAlpha = feather.depthAlpha * edgeFade * alphaMultiplier

        if (finalAlpha > 0.02f) {
            rotate(degrees = rotDeg, pivot = Offset(cx, cy)) {
                scale(scaleX = tumbleScaleX, scaleY = 1f, pivot = Offset(cx, cy)) {
                    val vanePath = Path().apply {
                        moveTo(cx, cy - len * 0.52f)
                        cubicTo(
                            cx + width * 1.05f, cy - len * 0.20f,
                            cx + width * 0.90f, cy + len * 0.22f,
                            cx + width * 0.15f, cy + len * 0.42f
                        )
                        lineTo(cx, cy + len * 0.56f)
                        lineTo(cx - width * 0.10f, cy + len * 0.40f)
                        cubicTo(
                            cx - width * 0.82f, cy + len * 0.16f,
                            cx - width * 0.88f, cy - len * 0.24f,
                            cx, cy - len * 0.52f
                        )
                        close()
                    }
                    drawPath(
                        path = vanePath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF475569).copy(alpha = finalAlpha),
                                Color(0xFF1E293B).copy(alpha = finalAlpha * 0.95f),
                                Color(0xFF0F172A).copy(alpha = finalAlpha)
                            ),
                            start = Offset(cx - width, cy - len * 0.5f),
                            end = Offset(cx + width, cy + len * 0.5f)
                        )
                    )
                    // Subtle moonlight sheen on one edge of the feather
                    drawPath(
                        path = vanePath,
                        color = Color(0xFF93C5FD).copy(alpha = finalAlpha * 0.42f),
                        style = Stroke(width = 0.7.dp.toPx())
                    )
                    // Curved central rachis (quill shaft)
                    val quillPath = Path().apply {
                        moveTo(cx, cy - len * 0.50f)
                        quadraticTo(cx + width * 0.12f, cy, cx, cy + len * 0.62f)
                    }
                    drawPath(
                        path = quillPath,
                        color = Color(0xFFCBD5E1).copy(alpha = finalAlpha * 0.55f),
                        style = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
