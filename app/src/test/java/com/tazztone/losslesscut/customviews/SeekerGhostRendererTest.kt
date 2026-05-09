package com.tazztone.losslesscut.customviews

import android.graphics.Canvas
import android.graphics.Paint
import com.tazztone.losslesscut.domain.usecase.SilenceDetectionUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeekerGhostRendererTest {

    private lateinit var customSeeker: CustomVideoSeeker
    private lateinit var renderer: SeekerRenderer
    private lateinit var ghostRenderer: SeekerGhostRenderer
    private lateinit var canvas: Canvas

    @Before
    fun setUp() {
        customSeeker = mockk(relaxed = true)
        renderer = mockk(relaxed = true)
        canvas = mockk(relaxed = true)

        // Setup paints in mocked renderer
        every { renderer.thresholdMaskPaint } returns mockk(relaxed = true)
        every { renderer.droppedSilencePaint } returns mockk(relaxed = true)
        every { renderer.bridgedNoisePaint } returns mockk(relaxed = true)

        every { customSeeker.height } returns 100

        // Setup timeToX stub
        every { customSeeker.timeToX(any()) } answers {
            (firstArg<Long>() / 10f) // simple mock mapping: 1000L -> 100f
        }

        ghostRenderer = SeekerGhostRenderer(customSeeker, renderer)
    }

    @Test
    fun `test drawSpecialSilenceOverlay with NONE mode`() {
        every { customSeeker.activeSilenceVisualMode } returns CustomVideoSeeker.SilenceVisualMode.NONE

        val result = SilenceDetectionUseCase.DetectionResult(
            rawRanges = listOf(),
            noiseMergedRanges = listOf(),
            durationFilteredRanges = listOf(),
            finalRanges = listOf()
        )

        ghostRenderer.drawSpecialSilenceOverlay(canvas, result)

        // Nothing should be drawn
        verify(exactly = 0) { renderer.drawSimpleSilencePreviews(any(), any()) }
        verify(exactly = 0) { canvas.drawRect(any(), any(), any(), any(), any<Paint>()) }
    }

    @Test
    fun `test drawSpecialSilenceOverlay with PADDING mode`() {
        every { customSeeker.activeSilenceVisualMode } returns CustomVideoSeeker.SilenceVisualMode.PADDING

        val origRanges = listOf(1000L..3000L, 5000L..7000L)
        // With padding keepers, the silence ranges shrink
        val paddedRanges = listOf(1200L..2800L, 5200L..6800L)

        val result = SilenceDetectionUseCase.DetectionResult(
            rawRanges = listOf(),
            noiseMergedRanges = listOf(),
            durationFilteredRanges = origRanges,
            finalRanges = paddedRanges
        )

        ghostRenderer.drawSpecialSilenceOverlay(canvas, result)

        // Should draw simple previews for original ranges
        verify { renderer.drawSimpleSilencePreviews(canvas, origRanges) }

        // Padded extensions drawn as rects
        // Range 1 padding
        verify { canvas.drawRect(100f, 0f, 120f, 100f, any()) } // Pre-padding
        verify { canvas.drawRect(280f, 0f, 300f, 100f, any()) } // Post-padding

        // Range 2 padding
        verify { canvas.drawRect(500f, 0f, 520f, 100f, any()) } // Pre-padding
        verify { canvas.drawRect(680f, 0f, 700f, 100f, any()) } // Post-padding
    }

    @Test
    fun `test drawSpecialSilenceOverlay with MIN_SILENCE mode`() {
        every { customSeeker.activeSilenceVisualMode } returns CustomVideoSeeker.SilenceVisualMode.MIN_SILENCE

        val noiseMerged = listOf(1000L..2000L, 4000L..4050L, 6000L..7000L) // 4000L..4050L is short
        val filtered = listOf(1000L..2000L, 6000L..7000L) // short range dropped

        val result = SilenceDetectionUseCase.DetectionResult(
            rawRanges = listOf(),
            noiseMergedRanges = noiseMerged,
            durationFilteredRanges = filtered,
            finalRanges = filtered
        )

        ghostRenderer.drawSpecialSilenceOverlay(canvas, result)

        // Should draw simple previews for final ranges
        verify { renderer.drawSimpleSilencePreviews(canvas, filtered) }

        // Should draw rect for the dropped silence (4000L..4050L)
        verify(exactly = 1) { canvas.drawRect(400f, 0f, 405f, 100f, any()) }
    }

    @Test
    fun `test drawSpecialSilenceOverlay with MIN_SEGMENT mode`() {
        every { customSeeker.activeSilenceVisualMode } returns CustomVideoSeeker.SilenceVisualMode.MIN_SEGMENT

        val rawRanges = listOf(1000L..2000L, 2050L..3000L) // 50ms gap between silences
        val noiseMerged = listOf(1000L..3000L) // Gap bridged because it's too short

        val result = SilenceDetectionUseCase.DetectionResult(
            rawRanges = rawRanges,
            noiseMergedRanges = noiseMerged,
            durationFilteredRanges = noiseMerged,
            finalRanges = noiseMerged
        )

        ghostRenderer.drawSpecialSilenceOverlay(canvas, result)

        // Should draw simple previews for noise merged ranges
        verify { renderer.drawSimpleSilencePreviews(canvas, noiseMerged) }

        // Should draw rect for the bridged noise (the 50ms gap: 2000L to 2050L)
        verify(exactly = 1) { canvas.drawRect(200f, 0f, 205f, 100f, any()) }
    }
}
