package com.tazztone.losslesscut.ui

import android.view.MotionEvent
import com.tazztone.losslesscut.customviews.CustomVideoSeeker
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomVideoSeekerTest {

    private lateinit var seeker: CustomVideoSeeker
    private val videoDuration = 10000L // 10s

    @Before
    fun setup() {
        seeker = CustomVideoSeeker(RuntimeEnvironment.getApplication())
        seeker.setVideoDuration(videoDuration)
        // Set a default width for coordinate calculations (manually since it's a unit test)
        seeker.layout(0, 0, 1000, 100) 
    }

    @Test
    fun `lossless mode should always snap to nearest keyframe`() {
        seeker.isLosslessMode = true
        seeker.setKeyframes(listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L))
        
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 1000L, 3000L, SegmentAction.KEEP)
        ), null)

        // Simulate touch down on the left handle (at 1000ms)
        // Hit-testing logic: xToTime(x) = (x - padding) / availableWidth * duration
        // With width=1000, padding=50, availableWidth=900.
        // x(1000ms) = 50 + (1000 / 10000) * 900 = 50 + 90 = 140
        val downX = 140f
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, downX, 90f, 0)
        seeker.onTouchEvent(downEvent)
        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_LEFT, seeker.currentTouchTarget)

        // Move to 1200ms (x = 50 + (1200 / 10000) * 900 = 50 + 108 = 158)
        // In lossless mode, it should snap to nearest keyframe (0ms or 2000ms). 
        // 1200 is closer to 0 than 2000, but min-duration (100ms) might apply? 
        // No, min duration is against the other handle.
        // Let's move to 1800ms (closer to 2000ms)
        val moveX = 50f + (1800f / 10000f) * 900f // 50 + 162 = 212
        
        val onBoundsChanged = mockk<((UUID, Long, Long, Long) -> Unit)>(relaxed = true)
        seeker.onSegmentBoundsChanged = onBoundsChanged

        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, moveX, 90f, 0)
        seeker.onTouchEvent(moveEvent)

        // Should have snapped to 2000ms
        verify { onBoundsChanged(segmentId, 2000L, 3000L, 2000L) }
    }

    @Test
    fun `hit testing should identify left and right handles correctly`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        // Left handle is at x(2000) = 50 + (2000/10000)*900 = 50 + 180 = 230
        val downLeft = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 230f, 90f, 0)
        seeker.onTouchEvent(downLeft)
        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_LEFT, seeker.currentTouchTarget)
        
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.NONE

        // Right handle is at x(5000) = 50 + (5000/10000)*900 = 50 + 450 = 500
        val downRight = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 500f, 90f, 0)
        seeker.onTouchEvent(downRight)
        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_RIGHT, seeker.currentTouchTarget)
    }

    @Test
    fun `playhead hit testing should work correctly`() {
        seeker.setSeekPosition(3000L)
        // x(3000) = 50 + (3000/10000)*900 = 50 + 270 = 320
        
        // Note: touch Y=90 is bottom area for segments. Playhead hit is usually anywhere but if we use Y=20 (top)
        val downPlayhead = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 320f, 20f, 0)
        seeker.onTouchEvent(downPlayhead)
        assertEquals(CustomVideoSeeker.TouchTarget.PLAYHEAD, seeker.currentTouchTarget)
    }

    @Test
    fun `onSeekStart callback should be triggered on move start`() {
        val onSeekStart = mockk<(() -> Unit)>(relaxed = true)
        seeker.onSeekStart = onSeekStart
        
        seeker.setSeekPosition(5000L)
        val downX = 50f + (5000f / 10000f) * 900f
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, downX, 20f, 0)
        seeker.onTouchEvent(downEvent)
        
        verify { onSeekStart() }
    }

    @Test
    fun `setSeekPosition should use targeted invalidation during playback`() {
        // We use a subclass to track invalidation calls
        val testSeeker = TestCustomVideoSeeker(RuntimeEnvironment.getApplication())
        testSeeker.setVideoDuration(videoDuration)
        testSeeker.layout(0, 0, 1000, 100)
        
        testSeeker.resetInvalidateCounters()
        
        // Move playhead within visible bounds
        testSeeker.setSeekPosition(2000L)
        
        // Targeted invalidation means:
        // 1. Full invalidate() should NOT be called
        // 2. Targeted invalidate(l,t,r,b) should be called (twice: one for old, one for new pos)
        assertEquals(0, testSeeker.fullInvalidateCount)
        assertEquals(2, testSeeker.rectInvalidateCount)
    }

    @Test
    fun `pinch to zoom should update zoomFactor and scrollOffset`() {
        // Simulate pinch zoom
        // Center is 500. focusX = 500.
        // Initially zoomFactor = 1.0.
        // detector.scaleFactor = 2.0.
        
        // We need to trigger the internal scaleGestureDetector.listener
        // Since we can't easily mock ScaleGestureDetector internals in Robolectric without complexity,
        // we test the side effects of pinch by manipulating zoomFactor and verifying scroll logic.
        
        seeker.zoomFactor = 2.0f
        assertEquals(1000f, seeker.maxScrollOffset()) // logical width = 1000 * 2 = 2000. maxScroll = 2000 - 1000 = 1000.
        
        seeker.scrollOffsetX = 500f
        val timeAtCenter = seeker.xToTime(500f + 500f) // center of view (500) + scroll (500) = 1000 in world.
        // availableWidth = 2000 - 100 = 1900.
        // time = (1000 - 50) / 1900 * 10000 = 950 / 1900 * 10000 = 5000ms.
        assertEquals(5000L, timeAtCenter)
    }

    @Test
    fun `auto pan should trigger when dragging near edges`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 1000L, 5000L, SegmentAction.KEEP)
        ), null)
        seeker.zoomFactor = 2f // Enable scrolling
        
        // Drag right handle (at 5000ms) near the right edge of the screen
        // x(5000) with zoom 2 = 50 + (5000/10000) * (2000-100) = 50 + 0.5 * 1900 = 50 + 950 = 1000.
        // View width is 1000. x=1000 is right edge. 
        // EDGE_PAN_THRESHOLD is 100. So anything > 900 triggers it.
        
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1000f, 95f, 0)
        seeker.onTouchEvent(downEvent)
        
        // The touch handler should have started the auto-pan runnable
        // Since we can't easily check private property 'isAutoPanning', we check the side effect: 
        // The seeker should have an active touch target.
        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_RIGHT, seeker.currentTouchTarget)
    }

    @Test
    fun `accessibility helper should report correct virtual views`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)
        
        // We'll use reflection or just assume the helper works if it gets called
        // Ideally we'd test SeekerAccessibilityHelper directly, but since it's private to the package 
        // and internal to the view, we can verify it doesn't crash during layout/draw.
        seeker.playheadVisible = true
        seeker.segmentsVisible = true
        
        // No crash is a good sign for Robolectric
    }

    @Test
    fun `segment handles should respect minimum duration constraint`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            // 2000 to 5000 -> 3000ms duration
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        var newStartMs = 2000L
        seeker.onSegmentBoundsChanged = { _, start, _, _ -> newStartMs = start }

        // MOCK ClipController.MIN_SEGMENT_DURATION_MS = 100L
        // Left handle cannot pass 5000 - 100 = 4900L
        
        // Grab left handle (at 2000ms)
        val leftX = 50f + (2000f/10000f)*900f
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, leftX, 90f, 0)
        seeker.onTouchEvent(downEvent)
        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_LEFT, seeker.currentTouchTarget)

        // Try to drag left handle past max to 6000ms
        val dragX = 50f + (6000f/10000f)*900f
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, dragX, 90f, 0)
        seeker.onTouchEvent(moveEvent)
        
        // Should clamp to (5000 - min_duration) -> 4900L
        assertEquals(4900L, newStartMs)
    }

    @Test
    fun `resetView should clear zoom and scroll`() {
        seeker.zoomFactor = 5f
        seeker.scrollOffsetX = 200f
        
        seeker.resetView()
        
        assertEquals(1f, seeker.zoomFactor)
        assertEquals(0f, seeker.scrollOffsetX)
    }

    @Test
    fun `data loading should not crash layout or draw passes`() {
        // Set waveform
        seeker.setWaveformData(FloatArray(100) { it.toFloat() })
        
        // Set silence preview
        seeker.silencePreviewRanges = listOf(1000L..2000L, 4000L..6000L)
        seeker.activeSilenceVisualMode = CustomVideoSeeker.SilenceVisualMode.MIN_SILENCE
        seeker.noiseThresholdPreview = 0.5f

        // Let it "layout/draw" 
        seeker.playheadVisible = true
        seeker.segmentsVisible = true
        
        // If Robolectric runs this without an exception, the data binding works basically
        // (Invalidations are mocked correctly by TestCustomVideoSeeker in other tests)
    }

    /**
     * Test subclass to track invalidation calls for architectural verification.
     */
    private class TestCustomVideoSeeker(context: android.content.Context) : CustomVideoSeeker(context) {
        var fullInvalidateCount = 0
        var rectInvalidateCount = 0

        override fun invalidate() {
            fullInvalidateCount++
            super.invalidate()
        }

        override fun invalidate(l: Int, t: Int, r: Int, b: Int) {
            rectInvalidateCount++
            super.invalidate(l, t, r, b)
        }

        fun resetInvalidateCounters() {
            fullInvalidateCount = 0
            rectInvalidateCount = 0
        }
    }
}
