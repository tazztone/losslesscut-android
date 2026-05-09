package com.tazztone.losslesscut.customviews

import android.view.MotionEvent
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeekerTouchHandlerTest {

    private lateinit var seeker: CustomVideoSeeker
    private lateinit var touchHandler: SeekerTouchHandler
    private val videoDuration = 10000L // 10s

    @Before
    fun setup() {
        seeker = CustomVideoSeeker(RuntimeEnvironment.getApplication())
        seeker.setVideoDuration(videoDuration)
        seeker.layout(0, 0, 1000, 100) // 1000 width, 100 height. Padding is 50.
        touchHandler = SeekerTouchHandler(seeker)
    }

    @Test
    fun `handleActionDown on left handle sets target to HANDLE_LEFT`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        // Left handle is at x(2000) = 50 + (2000/10000)*900 = 50 + 180 = 230
        val downX = 230f
        val downY = 90f // Bottom area
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, downX, downY, 0)

        touchHandler.onTouchEvent(downEvent)

        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_LEFT, seeker.currentTouchTarget)
        assertEquals(segmentId, touchHandler.activeSegmentId)
    }

    @Test
    fun `handleActionDown on right handle sets target to HANDLE_RIGHT`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        // Right handle is at x(5000) = 50 + (5000/10000)*900 = 50 + 450 = 500
        val downX = 500f
        val downY = 90f // Bottom area
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, downX, downY, 0)

        touchHandler.onTouchEvent(downEvent)

        assertEquals(CustomVideoSeeker.TouchTarget.HANDLE_RIGHT, seeker.currentTouchTarget)
        assertEquals(segmentId, touchHandler.activeSegmentId)
    }

    @Test
    fun `handleActionDown on playhead sets target to PLAYHEAD`() {
        seeker.setSeekPosition(3000L)

        // Playhead is at x(3000) = 50 + (3000/10000)*900 = 50 + 270 = 320
        val downX = 320f
        val downY = 20f // Top area (avoids segment hit logic)
        val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, downX, downY, 0)

        touchHandler.onTouchEvent(downEvent)

        assertEquals(CustomVideoSeeker.TouchTarget.PLAYHEAD, seeker.currentTouchTarget)
    }

    @Test
    fun `handleActionMove drags left handle and calls onSegmentBoundsChanged`() {
        // By default isLosslessMode is true in CustomVideoSeeker.
        // It snaps to nearest keyframe or 0/duration.
        // For testing explicit values, we disable lossless mode so we get exact coords.
        seeker.isLosslessMode = false

        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        var boundsChangedCalled = false
        var capturedStartMs = -1L
        seeker.onSegmentBoundsChanged = { id, startMs, endMs, seekMs ->
            assertEquals(segmentId, id)
            capturedStartMs = startMs
            assertEquals(5000L, endMs)
            boundsChangedCalled = true
        }

        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_LEFT
        touchHandler.activeSegmentId = segmentId

        // Move left handle to 1000ms: x(1000) = 50 + (1000/10000)*900 = 140
        val moveX = 140f
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, moveX, 90f, 0)

        touchHandler.onTouchEvent(moveEvent)

        assertTrue(boundsChangedCalled)
        assertEquals(1000L, capturedStartMs)
    }

    @Test
    fun `handleActionMove drags right handle and calls onSegmentBoundsChanged`() {
        seeker.isLosslessMode = false

        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        var boundsChangedCalled = false
        var capturedEndMs = -1L
        seeker.onSegmentBoundsChanged = { id, startMs, endMs, seekMs ->
            assertEquals(segmentId, id)
            assertEquals(2000L, startMs)
            capturedEndMs = endMs
            boundsChangedCalled = true
        }

        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_RIGHT
        touchHandler.activeSegmentId = segmentId

        // Move right handle to 6000ms: x(6000) = 50 + (6000/10000)*900 = 590
        val moveX = 590f
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, moveX, 90f, 0)

        touchHandler.onTouchEvent(moveEvent)

        assertTrue(boundsChangedCalled)
        assertEquals(6000L, capturedEndMs)
    }

    @Test
    fun `handleActionMove drags playhead`() {
        seeker.setSeekPosition(3000L)
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.PLAYHEAD

        var seekListenerCalled = false
        var capturedMs = 0L
        seeker.onSeekListener = { ms ->
            capturedMs = ms
            seekListenerCalled = true
        }

        // Move playhead to 4000ms: x(4000) = 50 + (4000/10000)*900 = 410
        val moveX = 410f
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, moveX, 20f, 0)

        touchHandler.onTouchEvent(moveEvent)

        assertTrue(seekListenerCalled)
        assertEquals(4000L, capturedMs)
        assertEquals(4000L, seeker.seekPositionMs)
    }

    @Test
    fun `handleActionUp clears touch targets`() {
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_LEFT
        touchHandler.activeSegmentId = UUID.randomUUID()
        touchHandler.isAutoPanning = true

        var seekEndCalled = false
        seeker.onSeekEnd = { seekEndCalled = true }
        var dragEndCalled = false
        seeker.onSegmentBoundsDragEnd = { dragEndCalled = true }

        val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        touchHandler.onTouchEvent(upEvent)

        assertEquals(CustomVideoSeeker.TouchTarget.NONE, seeker.currentTouchTarget)
        assertEquals(null, touchHandler.activeSegmentId)
        assertFalse(touchHandler.isAutoPanning)
        assertTrue(seekEndCalled)
        assertTrue(dragEndCalled)
    }

    @Test
    fun `autoPan triggers when dragging near left edge`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        // Start dragging left handle
        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_LEFT
        touchHandler.activeSegmentId = segmentId

        // Move event near left edge (x < 100)
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 50f, 90f, 0)
        touchHandler.onTouchEvent(moveEvent)

        assertTrue(touchHandler.isAutoPanning)
    }

    @Test
    fun `autoPan triggers when dragging near right edge`() {
        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 5000L, SegmentAction.KEEP)
        ), null)

        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_RIGHT
        touchHandler.activeSegmentId = segmentId

        // Move event near right edge (x > 900)
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 950f, 90f, 0)
        touchHandler.onTouchEvent(moveEvent)

        assertTrue(touchHandler.isAutoPanning)
    }

    @Test
    fun `lossless mode snaps right handle to nearest keyframe`() {
        seeker.isLosslessMode = true
        seeker.setKeyframes(listOf(0L, 2000L, 4000L, 6000L, 8000L, 10000L))

        val segmentId = UUID.randomUUID()
        seeker.setSegments(listOf(
            TrimSegment(segmentId, 2000L, 4000L, SegmentAction.KEEP)
        ), null)

        seeker.currentTouchTarget = CustomVideoSeeker.TouchTarget.HANDLE_RIGHT
        touchHandler.activeSegmentId = segmentId

        var boundsChangedCalled = false
        var capturedEndMs = 0L
        seeker.onSegmentBoundsChanged = { _, _, endMs, _ ->
            capturedEndMs = endMs
            boundsChangedCalled = true
        }

        // Move near 5900ms. x(5900) = 50 + (5900/10000)*900 = 581
        val moveX = 581f
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, moveX, 90f, 0)

        touchHandler.onTouchEvent(moveEvent)

        assertTrue(boundsChangedCalled)
        assertEquals(6000L, capturedEndMs) // Snapped to 6000L
    }
}
