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
        assertEquals(1500L..1500L, result[1])
    }

    @Test
    public fun `filter respects minSegmentMs`() {
        val frames = listOf(
            FrameAnalysis(0, 10.0, 1000.0, null, null),
            FrameAnalysis(500, 12.0, 1000.0, null, null),
            FrameAnalysis(1000, 30.0, 1000.0, null, null)
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 600
        )
        
        assertEquals(0, result.size)
    }

    @Test
    public fun `filter groups scene changes correctly`() {
        // Scene change uses > threshold
        val frames = listOf(
            FrameAnalysis(0, 100.0, 1000.0, null, null),     // No distance
            FrameAnalysis(500, 100.0, 1000.0, 15, null),    // Change!
            FrameAnalysis(1000, 100.0, 1000.0, 2, null),     // No change
            FrameAnalysis(1500, 100.0, 1000.0, 20, null)    // Change!
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
        // Freeze uses < threshold
        val frames = listOf(
            FrameAnalysis(0, 100.0, 1000.0, null, null),
            FrameAnalysis(500, 100.0, 1000.0, null, 1.0),   // Freeze!
            FrameAnalysis(1000, 100.0, 1000.0, null, 10.0),  // Moving
            FrameAnalysis(1500, 100.0, 1000.0, null, 0.5)   // Freeze!
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.FREEZE_FRAME,
            threshold = 2f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(500L..500L, result[0])
        assertEquals(1500L..1500L, result[1])
    }

    @Test
    public fun `filter groups blur frames correctly`() {
        // Blur uses < threshold (low variance = blurry)
        val frames = listOf(
            FrameAnalysis(0, 100.0, 10.0, null, null),    // Blurry
            FrameAnalysis(500, 100.0, 5.0, null, null),     // Blurry
            FrameAnalysis(1000, 100.0, 500.0, null, null),  // Sharp
            FrameAnalysis(1500, 100.0, 20.0, null, null)    // Blurry
        )
        
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLUR_QUALITY,
            threshold = 50f,
            minSegmentMs = 0
        )
        
        assertEquals(2, result.size)
        assertEquals(0L..500L, result[0])
        assertEquals(1500L..1500L, result[1])
    }
}
