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
        assertEquals(450L..550L, result[0])
        assertEquals(1450L..1550L, result[1])
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
        assertEquals(450L..550L, sceneResult[0])

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
            FrameAnalysis(0, 20.0, 50.0, 10, 2.0),   // At boundary for all
            FrameAnalysis(500, 19.9, 49.9, 11, 1.9)  // Match all
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
        assertEquals(450L..550L, sceneResult[0])

        // BLUR_QUALITY: blurVariance < 50
        val blurResult = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLUR_QUALITY,
            threshold = 50f,
            minSegmentMs = 0
        )
        assertEquals(1, blurResult.size)
        assertEquals(450L..550L, blurResult[0])

        // FREEZE_FRAME: freezeDiff < 2.0
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
    public fun `filter coerces start time to zero when expanding single frame match near start`() {
        val frames = listOf(
            FrameAnalysis(20, 10.0, 1000.0, null, null) // Match near start
        )

        // minSegmentMs = 0, meaning padding defaults to 100
        // half-padding = 50
        // start = 20 - 50 = -30 -> coerced to 0
        // end = 20 + 50 = 70
        // Because minSegmentMs = 0, duration 70 >= 0 is true and it's kept.
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 0
        )

        assertEquals(1, result.size)
        assertEquals(0L..100L, result[0])
    }

    @Test
    public fun `filter removes multi-frame segment if duration is less than minSegmentMs`() {
        val frames = listOf(
            FrameAnalysis(0, 10.0, 1000.0, null, null), // Match
            FrameAnalysis(50, 10.0, 1000.0, null, null) // Match
        )

        // Duration = 50 - 0 = 50
        // minSegmentMs = 100
        // 50 < 100 -> removed
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 100
        )

        assertEquals(0, result.size)
    }

    @Test
    public fun `filter keeps multi-frame segment if duration is at least minSegmentMs`() {
        val frames = listOf(
            FrameAnalysis(0, 10.0, 1000.0, null, null), // Match
            FrameAnalysis(150, 10.0, 1000.0, null, null) // Match
        )

        // Duration = 150 - 0 = 150
        // minSegmentMs = 100
        // 150 >= 100 -> kept
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = 100
        )

        assertEquals(1, result.size)
        assertEquals(0L..150L, result[0])
    }

    @Test
    public fun `filter handles negative minSegmentMs`() {
        val frames = listOf(
            FrameAnalysis(500, 10.0, 1000.0, null, null) // Single frame match
        )

        // minSegmentMs = -10 (which is <= 0)
        // Fallback MIN_VISIBLE_STAMP_DURATION_MS = 100 is used for padding
        // start = 500 - 50 = 450
        // end = 500 + 50 = 550
        // duration = 100
        // filter check: duration (100) >= minSegmentMs (-10) -> true
        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.BLACK_FRAMES,
            threshold = 20f,
            minSegmentMs = -10
        )

        assertEquals(1, result.size)
        assertEquals(450L..550L, result[0])
    }

    @Test
    public fun `filter_singleFrameNearStart_maintainsDuration`() {
        val frames = listOf(
            FrameAnalysis(0, 100.0, 1000.0, 15, null) // Single frame at time 0
        )

        val minSegmentMs = 101L

        val result = VisualSegmentFilter.filter(
            frames = frames,
            strategy = VisualStrategy.SCENE_CHANGE,
            threshold = 10f,
            minSegmentMs = minSegmentMs
        )

        assertEquals(1, result.size)
        // Expected duration is exactly 101: start should be 0, end should be 101
        assertEquals(0L..101L, result[0])
    }
}
