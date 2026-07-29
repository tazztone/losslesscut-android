package com.tazztone.losslesscut.customviews

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class TimelineViewportTest {

    @Test
    public fun `timeToPixel and pixelToTime are inverse operations`() {
        val viewport = TimelineViewport(
            durationMs = 10000L,
            viewWidthPx = 1000,
            zoomFactor = 2.0f,
            scrollXPx = 100f
        )

        val timestamp = 5000L
        val pixelX = viewport.timeToPixel(timestamp)
        val recalculatedTime = viewport.pixelToTime(pixelX)

        assertEquals(timestamp.toDouble(), recalculatedTime.toDouble(), 10.0)
    }

    @Test
    public fun `zoomAt clamps zoom factor between MIN_ZOOM and MAX_ZOOM`() {
        val viewport = TimelineViewport(durationMs = 10000L, viewWidthPx = 1000)

        val zoomedMin = viewport.zoomAt(500f, 0.5f)
        assertEquals(TimelineViewport.MIN_ZOOM, zoomedMin.zoomFactor, 0.001f)

        val zoomedMax = viewport.zoomAt(500f, 50.0f)
        assertEquals(TimelineViewport.MAX_ZOOM, zoomedMax.zoomFactor, 0.001f)
    }

    @Test
    public fun `visibleTileRange calculates correct tile indices`() {
        val viewport = TimelineViewport(
            durationMs = 60000L,
            viewWidthPx = 1000,
            zoomFactor = 5.0f,
            scrollXPx = 3000f,
            tileWidthPx = 2048
        )

        val range = viewport.visibleTileRange()
        assertTrue(range.first >= 1)
        assertTrue(range.last >= range.first)
    }
}
