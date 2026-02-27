package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

public class VisualSegmentFilterTest {

    @Test
    public fun `filter groups contiguous black frames correctly`() {
        // Arrange
        val frames = listOf(
            createFrame(timeMs = 0, meanLuma = 5.0),   // Match
            createFrame(timeMs = 100, meanLuma = 5.0), // Match
            createFrame(timeMs = 200, meanLuma = 20.0), // No match
            createFrame(timeMs = 300, meanLuma = 5.0), // Match
            createFrame(timeMs = 400, meanLuma = 5.0)  // Match
        )
        val threshold = 10f
        val minSegmentMs = 0L

        // Act
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = threshold,
            minSegmentMs = minSegmentMs
        )

        // Assert
        assertEquals(2, result.size)
        assertEquals(0L..100L, result[0])
        assertEquals(300L..400L, result[1])
    }

    @Test
    public fun `filter respects minimum segment duration`() {
        // Arrange
        val frames = listOf(
            createFrame(timeMs = 0, meanLuma = 5.0),
            createFrame(timeMs = 100, meanLuma = 5.0), // Duration 100ms
            createFrame(timeMs = 200, meanLuma = 20.0),
            createFrame(timeMs = 300, meanLuma = 5.0),
            createFrame(timeMs = 400, meanLuma = 5.0),
            createFrame(timeMs = 500, meanLuma = 5.0)  // Duration 200ms
        )
        val threshold = 10f
        val minSegmentMs = 150L

        // Act
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = threshold,
            minSegmentMs = minSegmentMs
        )

        // Assert
        assertEquals(1, result.size)
        assertEquals(300L..500L, result[0])
    }

    @Test
    public fun `filter handles empty input`() {
        val result = VisualSegmentFilter.filter(
            frames = emptyList(),
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 10f,
            minSegmentMs = 0L
        )
        assertEquals(0, result.size)
    }

    @Test
    public fun `filter handles no matches`() {
        val frames = listOf(
            createFrame(timeMs = 0, meanLuma = 20.0),
            createFrame(timeMs = 100, meanLuma = 20.0)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 10f,
            minSegmentMs = 0L
        )
        assertEquals(0, result.size)
    }

    @Test
    public fun `filter handles scene changes (greater than threshold)`() {
        val frames = listOf(
            createFrame(timeMs = 0, sceneDistance = 5), // No match (<= 10)
            createFrame(timeMs = 100, sceneDistance = 15), // Match (> 10)
            createFrame(timeMs = 200, sceneDistance = 15)  // Match (> 10)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.SCENE_CHANGE,
            threshold = 10f,
            minSegmentMs = 0L
        )
        assertEquals(1, result.size)
        assertEquals(100L..200L, result[0])
    }

    @Test
    public fun `filter handles blur quality (less than threshold)`() {
        val frames = listOf(
            createFrame(timeMs = 0, blurVariance = 50.0), // Match (< 100)
            createFrame(timeMs = 100, blurVariance = 150.0) // No match (>= 100)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLUR_QUALITY,
            threshold = 100f,
            minSegmentMs = 0L
        )
        assertEquals(1, result.size)
        assertEquals(0L..0L, result[0])
    }

    @Test
    public fun `filter handles freeze frame (less than threshold)`() {
        val frames = listOf(
            createFrame(timeMs = 0, freezeDiff = 2.0), // Match (< 5)
            createFrame(timeMs = 100, freezeDiff = 10.0) // No match (>= 5)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.FREEZE_FRAME,
            threshold = 5f,
            minSegmentMs = 0L
        )
        assertEquals(1, result.size)
        assertEquals(0L..0L, result[0])
    }

    private fun createFrame(
        timeMs: Long,
        meanLuma: Double = 0.0,
        blurVariance: Double = 0.0,
        sceneDistance: Int? = null,
        freezeDiff: Double? = null
    ): FrameAnalysis {
        return FrameAnalysis(
            timeMs = timeMs,
            meanLuma = meanLuma,
            blurVariance = blurVariance,
            sceneDistance = sceneDistance,
            freezeDiff = freezeDiff
        )
    }
}
