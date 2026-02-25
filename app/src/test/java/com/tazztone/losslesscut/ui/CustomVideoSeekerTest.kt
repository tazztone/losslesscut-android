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
}
