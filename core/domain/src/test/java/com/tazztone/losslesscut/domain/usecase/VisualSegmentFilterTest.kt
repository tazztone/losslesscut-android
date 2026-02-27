package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

public class VisualSegmentFilterTest {

    @Test
    public fun `filter groups contiguous black frames`() {
        val frames = listOf(
            FrameAnalysis(0, 10.0, 1000.0, null, null),
            FrameAnalysis(500, 12.0, 1000.0, null, null),
            FrameAnalysis(1000, 30.0, 1000.0, null, null), // Not black
            FrameAnalysis(1500, 5.0, 1000.0, null, null)
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(0L..500L, result[0])
        assertEquals(1450L..1550L, result[1])
    }

    @Test
    public fun `filter respects minSegmentMs`() {
        val frames = listOf(
            FrameAnalysis(0, 30.0, 1000.0, null, null),
            FrameAnalysis(500, 12.0, 1000.0, null, null), // Match (point)
            FrameAnalysis(1000, 30.0, 1000.0, null, null)
        )
        
        // minSegmentMs = 600, padding = 600
        // point 500 becomes 200..800 (duration 600) -> Should pass
        val resultPass = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 600
        )
        assertEquals(1, resultPass.size)
        assertEquals(200L..800L, resultPass[0])

        // minSegmentMs = 700, padding = 700
        // point 500 becomes 150..850 (duration 700) -> Should pass
        val resultPassExactly = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 700
        )
        assertEquals(1, resultPassExactly.size)

        // minSegmentMs = 800
        // point 500 becomes 100..900 (duration 800) -> Pass
        val resultPass800 = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 800
        )
        assertEquals(1, resultPass800.size)
    }

    @Test
    public fun `filter groups scene changes correctly`() {
        val frames = listOf(
            FrameAnalysis(500, 100.0, 1000.0, 15, null),
            FrameAnalysis(1000, 100.0, 1000.0, 2, null),     // No match
            FrameAnalysis(1500, 100.0, 1000.0, 20, null)
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.SCENE_CHANGE,
            threshold = 10f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(500L..500L, result[0])
        assertEquals(1500L..1500L, result[1])
    }

    @Test
    public fun `filter groups freeze frames correctly`() {
        val frames = listOf(
            FrameAnalysis(500, 100.0, 1000.0, null, 1.0),
            FrameAnalysis(1000, 100.0, 1000.0, null, 10.0), // No match
            FrameAnalysis(1500, 100.0, 1000.0, null, 0.5)
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.FREEZE_FRAME,
            threshold = 2f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(450L..550L, result[0])
        assertEquals(1450L..1550L, result[1])
    }

    @Test
    public fun `filter groups blur frames correctly`() {
        val frames = listOf(
            FrameAnalysis(0, 100.0, 10.0, null, null),
            FrameAnalysis(500, 100.0, 5.0, null, null),
            FrameAnalysis(1000, 100.0, 500.0, null, null),  // Sharp
            FrameAnalysis(1500, 100.0, 20.0, null, null)
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLUR_QUALITY,
            threshold = 50f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(0L..500L, result[0])
        assertEquals(1450L..1550L, result[1])
    }
}
