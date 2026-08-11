package com.tazztone.losslesscut.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentActionPopupPositionTest {

    private val visibleFrame = SegmentActionPopupPosition.Bounds(0, 0, 1000, 600)

    @Test
    fun `centers popup on long press`() {
        val position = SegmentActionPopupPosition.calculate(
            anchorX = 500,
            anchorY = 300,
            popupWidth = 48,
            popupHeight = 96,
            visibleFrame = visibleFrame
        )

        assertEquals(476, position.left)
        assertEquals(252, position.top)
    }

    @Test
    fun `clamps popup to visible frame edges`() {
        val leftTop = SegmentActionPopupPosition.calculate(
            anchorX = 4,
            anchorY = 8,
            popupWidth = 48,
            popupHeight = 96,
            visibleFrame = visibleFrame
        )
        val rightBottom = SegmentActionPopupPosition.calculate(
            anchorX = 998,
            anchorY = 598,
            popupWidth = 48,
            popupHeight = 96,
            visibleFrame = visibleFrame
        )

        assertEquals(0, leftTop.left)
        assertEquals(0, leftTop.top)
        assertEquals(952, rightBottom.left)
        assertEquals(504, rightBottom.top)
    }

}
