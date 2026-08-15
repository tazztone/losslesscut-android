package com.tazztone.losslesscut.ui.compose.loading

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.compose.theme.CyanAccent
import com.tazztone.losslesscut.ui.compose.theme.DarkGray
import com.tazztone.losslesscut.ui.compose.theme.DeepDark
import com.tazztone.losslesscut.ui.compose.theme.GreenAccent
import com.tazztone.losslesscut.ui.compose.theme.OnSurfaceVariant
import com.tazztone.losslesscut.ui.compose.theme.PurpleAccent
import com.tazztone.losslesscut.ui.compose.theme.SurfaceVariant
import com.tazztone.losslesscut.ui.compose.theme.TextColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern Laser Waveform & Keyframe Slicer loading overlay for LosslessCut.
 *
 * Replaces legacy clapperboard Lottie animation with an ultra-sleek procedural
 * canvas featuring pulsating audio/video waveforms, keyframe markers, an electric
 * laser beam, and kinetic particle sparks.
 *
 * @param progress Current progress percentage (0..100). If <= 0, operates in indeterminate mode.
 * @param message Dynamic status text describing the current processing step.
 * @param isVisible Controls overlay visibility with smooth enter/exit transitions and zero-CPU idle.
 */
@Composable
fun LoadingOverlay(
    progress: Int,
    message: String?,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        // Prevent accidental dismiss or navigation during critical media processing
        BackHandler(enabled = isVisible) {}

        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepDark.copy(alpha = 0.94f))
                .clickable(interactionSource = interactionSource, indication = null) {
                    // Consume touch events to prevent clicks from falling through to underlying editor
                },
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val isLandscape = maxWidth > maxHeight

                val animatedProgress by animateIntAsState(
                    targetValue = progress.coerceIn(0, 100),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "animatedProgress"
                )

                val displayMessage = message?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.loading)

                if (isLandscape) {
                    LandscapeLayout(
                        progress = animatedProgress,
                        isDeterminate = progress > 0,
                        message = displayMessage
                    )
                } else {
                    PortraitLayout(
                        progress = animatedProgress,
                        isDeterminate = progress > 0,
                        message = displayMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitLayout(
    progress: Int,
    isDeterminate: Boolean,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App / Processing Tag
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            color = CyanAccent.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Visual: Laser Waveform Canvas
        LaserWaveformAnimation(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Telemetry & Progress
        if (isDeterminate) {
            Text(
                text = "$progress%",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextColor,
                letterSpacing = (-1).sp,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$progress percent complete"
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Neon Gradient Progress Bar
            NeonProgressBar(
                progressFraction = progress / 100f,
                modifier = Modifier
                    .width(220.dp)
                    .height(5.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Status Message Ticker with Smooth Vertical Slide
        StatusMessageTicker(
            message = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun LandscapeLayout(
    progress: Int,
    isDeterminate: Boolean,
    message: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Left Column: Hero Laser Waveform Visual
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            LaserWaveformAnimation(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }

        Spacer(modifier = Modifier.width(36.dp))

        // Right Column: Minimalist Telemetry & Status
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp,
                color = CyanAccent.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isDeterminate) {
                Text(
                    text = "$progress%",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextColor,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "$progress percent complete"
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                NeonProgressBar(
                    progressFraction = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            StatusMessageTicker(
                message = message,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}

@Composable
private fun NeonProgressBar(
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(CyanAccent, PurpleAccent, GreenAccent)
                    )
                )
        )
    }
}

@Composable
private fun StatusMessageTicker(
    message: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center
) {
    AnimatedContent(
        targetState = message,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(150)))
        },
        label = "statusMessageTicker",
        modifier = modifier
    ) { targetMessage ->
        Text(
            text = targetMessage,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceVariant,
            textAlign = textAlign,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Procedural Laser Waveform & Keyframe Slicer Canvas.
 *
 * Renders an oscillating multi-harmonic audio/video waveform with glowing gradients,
 * timeline baseline, keyframe diamonds, sweeping electric laser cut-line,
 * ambient radial backlighting, and dynamic particle sparks.
 */
@Composable
fun LaserWaveformAnimation(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laserWaveformTransition")

    // Laser sweep back and forth
    val laserPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPhase"
    )

    // Waveform oscillation phase
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Ambient glow pulse
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    // Spark particles rotation/expansion cycle
    val sparkCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkCycle"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val centerY = height * 0.5f
        val laserX = width * (0.12f + 0.76f * laserPhase)

        // 1. Ambient Radial Glow behind the laser position
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyanAccent.copy(alpha = 0.25f * ambientPulse),
                    PurpleAccent.copy(alpha = 0.10f * ambientPulse),
                    Color.Transparent
                ),
                center = Offset(laserX, centerY),
                radius = height * 1.1f
            ),
            radius = height * 1.1f,
            center = Offset(laserX, centerY)
        )

        // 2. Timeline Baseline Track
        drawLine(
            color = SurfaceVariant,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.5.dp.toPx()
        )

        // 3. Procedural Audio/Video Waveform Amplitude Bars
        val barCount = 30
        val barSpacing = width / barCount
        val barWidth = barSpacing * 0.45f

        val waveformGradient = Brush.verticalGradient(
            colors = listOf(
                PurpleAccent.copy(alpha = 0.9f),
                CyanAccent,
                CyanAccent,
                PurpleAccent.copy(alpha = 0.9f)
            ),
            startY = centerY - height * 0.45f,
            endY = centerY + height * 0.45f
        )

        for (i in 0 until barCount) {
            val barCenterX = (i + 0.5f) * barSpacing

            // Multi-harmonic sine envelope for organic audio waveform feel
            val normX = i.toFloat() / barCount
            val windowEnvelope = sin(normX * PI.toFloat()) // Bell-curve envelope
            val waveHarmonic = sin(wavePhase + i * 0.45f) * 0.4f +
                    sin(wavePhase * 1.6f + i * 0.8f) * 0.3f +
                    0.5f

            val amplitude = (height * 0.42f) * windowEnvelope * waveHarmonic.coerceIn(0.15f, 1f)

            // Proximity boost when laser cuts through this bar
            val distToLaser = kotlin.math.abs(barCenterX - laserX)
            val laserProximity = (1f - (distToLaser / (width * 0.15f)).coerceIn(0f, 1f))
            val boostedAmplitude = amplitude * (1f + 0.35f * laserProximity)

            drawRoundRect(
                brush = if (laserProximity > 0.4f) {
                    Brush.verticalGradient(
                        colors = listOf(Color.White, CyanAccent, CyanAccent, Color.White),
                        startY = centerY - boostedAmplitude,
                        endY = centerY + boostedAmplitude
                    )
                } else waveformGradient,
                topLeft = Offset(barCenterX - barWidth * 0.5f, centerY - boostedAmplitude),
                size = Size(barWidth, boostedAmplitude * 2f),
                cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
            )
        }

        // 4. Keyframe Diamond Nodes
        val keyframeNodeCount = 5
        for (k in 0 until keyframeNodeCount) {
            val kx = width * (0.15f + k * (0.70f / (keyframeNodeCount - 1)))
            val diamondRadius = 4.5.dp.toPx()

            val diamondPath = Path().apply {
                moveTo(kx, centerY - diamondRadius)
                lineTo(kx + diamondRadius, centerY)
                lineTo(kx, centerY + diamondRadius)
                lineTo(kx - diamondRadius, centerY)
                close()
            }

            drawPath(
                path = diamondPath,
                color = if (kotlin.math.abs(kx - laserX) < 16.dp.toPx()) Color.White else CyanAccent.copy(alpha = 0.7f)
            )
            drawPath(
                path = diamondPath,
                color = DarkGray,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 5. Electric Cutting Laser Beam
        // Soft outer neon glow
        drawLine(
            color = CyanAccent.copy(alpha = 0.25f),
            start = Offset(laserX, 0f),
            end = Offset(laserX, height),
            strokeWidth = 10.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Mid-intensity glow
        drawLine(
            color = CyanAccent.copy(alpha = 0.75f),
            start = Offset(laserX, 0f),
            end = Offset(laserX, height),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Ultra-hot laser core
        drawLine(
            color = Color.White,
            start = Offset(laserX, 4.dp.toPx()),
            end = Offset(laserX, height - 4.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Laser top and bottom optic nodes
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(laserX, 4.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(laserX, height - 4.dp.toPx())
        )

        // 6. Kinetic Particle Sparks emitted at laser cut point
        drawLaserSparks(
            originX = laserX,
            originY = centerY,
            sparkCycle = sparkCycle,
            maxRadius = 36.dp.toPx()
        )
    }
}

private fun DrawScope.drawLaserSparks(
    originX: Float,
    originY: Float,
    sparkCycle: Float,
    maxRadius: Float
) {
    val sparkCount = 14
    for (s in 0 until sparkCount) {
        val angle = (s * (2 * PI / sparkCount) + sparkCycle * 1.5f).toFloat()
        val phaseOffset = (sparkCycle + s * (1f / sparkCount)) % 1f
        val distance = maxRadius * phaseOffset
        val alpha = (1f - phaseOffset).coerceIn(0f, 1f)

        val px = originX + cos(angle) * distance
        val py = originY + sin(angle) * (distance * 0.7f) // slight vertical squash

        val sparkRadius = (2.2.dp.toPx() * (1f - phaseOffset * 0.5f)).coerceAtLeast(0.8f)

        drawCircle(
            color = if (s % 2 == 0) Color.White.copy(alpha = alpha) else CyanAccent.copy(alpha = alpha),
            radius = sparkRadius,
            center = Offset(px, py)
        )
    }
}

@Preview(name = "Portrait Indeterminate", widthDp = 360, heightDp = 640)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewPortraitIndeterminate() {
    LoadingOverlay(
        progress = 0,
        message = "Importing media & analyzing keyframes…",
        isVisible = true
    )
}

@Preview(name = "Portrait Determinate", widthDp = 360, heightDp = 640)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewPortraitDeterminate() {
    LoadingOverlay(
        progress = 68,
        message = "Saving segment 2 of 4…",
        isVisible = true
    )
}

@Preview(name = "Landscape Determinate", widthDp = 720, heightDp = 360)
@Composable
@Suppress("UnusedPrivateMember")
private fun PreviewLandscapeDeterminate() {
    LoadingOverlay(
        progress = 84,
        message = "Finalizing lossless stream muxing…",
        isVisible = true
    )
}
