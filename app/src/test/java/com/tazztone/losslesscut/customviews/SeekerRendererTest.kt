package com.tazztone.losslesscut.customviews

import android.content.Context
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.VisualStrategy
import io.mockk.mockk
import io.mockk.verify
import com.tazztone.losslesscut.domain.model.TrimSegment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SeekerRendererTest {

    private lateinit var context: Context
    private lateinit var customSeeker: CustomVideoSeeker
    private lateinit var renderer: SeekerRenderer
    private lateinit var canvas: Canvas

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight)
        customSeeker = CustomVideoSeeker(context)
        renderer = SeekerRenderer(customSeeker)
        canvas = mockk(relaxed = true)

        // Setup base layout to avoid zero width/height issues
        customSeeker.layout(0, 0, 1000, 200)
        customSeeker.setVideoDuration(10000L) // 10 seconds
    }

    @Test
    fun `test drawZoomHint`() {
        renderer.drawZoomHint(canvas)
        // Verify text is drawn (the hint text)
        verify { canvas.drawText(any(), any(), any(), any()) }
        // Verify finger circles are drawn (2 fingers = 2 circles)
        verify(exactly = 2) { canvas.drawCircle(any(), any(), any(), any()) }
        // Verify background rounded rect
        verify { canvas.drawRoundRect(any(), any(), any(), any()) }
    }

    @Test
    fun `test drawHandleArrow left and right`() {
        // Draw left arrow
        renderer.drawHandleArrow(canvas, 100f, 100f, true)
        // Draw right arrow
        renderer.drawHandleArrow(canvas, 200f, 100f, false)

        // Should draw path twice
        verify(exactly = 2) { canvas.drawPath(any(), any()) }
    }

    @Test
    fun `test drawPlayhead`() {
        customSeeker.setSeekPosition(5000L)
        renderer.drawPlayhead(canvas)

        // Verify main vertical line
        verify { canvas.drawLine(any(), any(), any(), any(), any()) }
        // Verify top handle rect
        verify { canvas.drawRoundRect(any(), any(), any(), any()) }
        // Verify triangle tip path
        verify { canvas.drawPath(any(), any()) }
    }

    @Test
    fun `test drawTimeLabels`() {
        // 10000ms duration, drawing from 0 to 10000
        renderer.drawTimeLabels(canvas, 0L, 10000L)

        // Should draw some ticks and texts
        verify(atLeast = 1) { canvas.drawLine(any(), any(), any(), any(), any()) }
        verify(atLeast = 1) { canvas.drawText(any(), any(), any(), any()) }
    }

    @Test
    fun `test drawSegments`() {
        val segmentId1 = UUID.randomUUID()
        val segmentId2 = UUID.randomUUID()

        customSeeker.setSegments(listOf(
            TrimSegment(segmentId1, 1000L, 3000L, SegmentAction.KEEP),
            TrimSegment(segmentId2, 5000L, 8000L, SegmentAction.KEEP)
        ), null)

        renderer.drawSegments(canvas)

        // 2 segments, each has a rect, 2 lines (handles), 2 circles (handles)
        verify(exactly = 2) { canvas.drawRect(any<android.graphics.RectF>(), any()) }
        verify(exactly = 4) { canvas.drawLine(any(), any(), any(), any(), any()) }
        verify(exactly = 4) { canvas.drawCircle(any(), any(), any(), any()) }
    }

    @Test
    fun `test drawSegments with selected segment`() {
        val segmentId1 = UUID.randomUUID()
        customSeeker.setSegments(listOf(
            TrimSegment(segmentId1, 1000L, 3000L, SegmentAction.KEEP)
        ), segmentId1)

        renderer.drawSegments(canvas)

        // 1 normal rect, 1 selected border rect
        verify(exactly = 2) { canvas.drawRect(any<android.graphics.RectF>(), any()) }
    }

    @Test
    fun `test drawSegments ignores DISCARD segments`() {
        val segmentId1 = UUID.randomUUID()
        customSeeker.setSegments(listOf(
            TrimSegment(segmentId1, 1000L, 3000L, SegmentAction.DISCARD)
        ), null)

        renderer.drawSegments(canvas)

        // Discarded segments are not drawn
        verify(exactly = 0) { canvas.drawRect(any<android.graphics.RectF>(), any()) }
    }

    @Test
    fun `test drawSimpleSilencePreviews with rects`() {
        customSeeker.visualStrategy = VisualStrategy.BLACK_FRAMES
        val ranges = listOf(1000L..2000L, 4000L..5000L)

        renderer.drawSimpleSilencePreviews(canvas, ranges)

        // 2 ranges = 2 rects
        verify(exactly = 2) { canvas.drawRect(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `test drawSimpleSilencePreviews with split markers`() {
        customSeeker.visualStrategy = VisualStrategy.SCENE_CHANGE
        val ranges = listOf(1000L..2000L, 4000L..5000L)

        renderer.drawSimpleSilencePreviews(canvas, ranges)

        // Scene change draws lines, not rects
        verify(exactly = 0) { canvas.drawRect(any(), any(), any(), any(), any()) }
        verify(exactly = 2) { canvas.drawLine(any(), any(), any(), any(), any()) }
    }
}
