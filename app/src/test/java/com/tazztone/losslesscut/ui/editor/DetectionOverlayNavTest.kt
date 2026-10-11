package com.tazztone.losslesscut.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionOverlayNavTest {

    private val ranges = listOf(1000L..2000L, 5000L..6000L)

    @Test
    fun findMatchTarget_forwardPicksNextRange() {
        assertEquals(5000L, findMatchTarget(ranges, 1500L, 1))
    }

    @Test
    fun findMatchTarget_backwardPicksPreviousRange() {
        assertEquals(1000L, findMatchTarget(ranges, 3000L, -1))
    }

    @Test
    fun findMatchTarget_forwardWrapsToFirst() {
        assertEquals(1000L, findMatchTarget(ranges, 6500L, 1))
    }

    @Test
    fun findMatchTarget_backwardWrapsToLast() {
        assertEquals(5000L, findMatchTarget(ranges, 0L, -1))
    }

    @Test
    fun stepSliderValue_zeroStepSizeFallsBackToOne() {
        assertEquals(6f, stepSliderValue(5f, 0f, 10f, 0f, 1))
    }

    @Test
    fun stepSliderValue_positiveStepSizeSnapsToStep() {
        assertEquals(8f, stepSliderValue(5f, 0f, 10f, 2f, 1))
    }

    @Test
    fun stepSliderValue_clampsAtBounds() {
        assertEquals(10f, stepSliderValue(9f, 0f, 10f, 2f, 1))
        assertEquals(0f, stepSliderValue(1f, 0f, 10f, 2f, -1))
    }
}
