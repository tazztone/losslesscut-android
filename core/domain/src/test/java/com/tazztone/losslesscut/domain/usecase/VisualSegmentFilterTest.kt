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

    @Test
    public fun `filter handles empty frame list`() {
        val result = VisualSegmentFilter.filter(
            frames = emptyList(),
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )
        assertEquals(0, result.size)
    }

    @Test
    public fun `filter handles no matches`() {
        val frames = listOf(
            FrameAnalysis(0, 30.0, 1000.0, null, null),
            FrameAnalysis(500, 40.0, 1000.0, null, null)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )
        assertEquals(0, result.size)
    }

    @Test
    public fun `filter handles all matches as single range`() {
        val frames = listOf(
            FrameAnalysis(0, 5.0, 1000.0, null, null),
            FrameAnalysis(500, 5.0, 1000.0, null, null),
            FrameAnalysis(1000, 5.0, 1000.0, null, null)
        )
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )
        assertEquals(1, result.size)
        assertEquals(0L..1000L, result[0])
    }

    @Test
    public fun `filter ignores null fields in scene change and freeze`() {
        val frames = listOf(
            FrameAnalysis(0, 100.0, 1000.0, null, null), // Both null
            FrameAnalysis(500, 100.0, 1000.0, 15, 0.5)   // Both match
        )
        
        val sceneResult = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.SCENE_CHANGE,
            threshold = 10f,
            minSegmentMs = 0
        )
        assertEquals(1, sceneResult.size)
        assertEquals(500L..500L, sceneResult[0])

        val freezeResult = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.FREEZE_FRAME,
            threshold = 2f,
            minSegmentMs = 0
        )
        assertEquals(1, freezeResult.size)
        assertEquals(450L..550L, freezeResult[0])
    }

    @Test
    public fun `filter respects strict threshold boundaries`() {
        val frames = listOf(
            FrameAnalysis(0, 20.0, 1000.0, 10, null),   // At boundary for both
            FrameAnalysis(500, 19.9, 1000.0, 11, null)  // Match both
        )

        // BLACK_FRAMES: luma < 20
        val blackResult = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )
        assertEquals(1, blackResult.size)
        assertEquals(450L..550L, blackResult[0])

        // SCENE_CHANGE: distance > 10
        val sceneResult = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.SCENE_CHANGE,
            threshold = 10f,
            minSegmentMs = 0
        )
        assertEquals(1, sceneResult.size)
        assertEquals(500L..500L, sceneResult[0])
    }
}
