package com.tazztone.losslesscut

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun formatDuration_underOneMinute() {
        assertEquals("00:45.000", TimeUtils.formatDuration(45000))
    }

    @Test
    fun formatDuration_overOneMinute() {
        assertEquals("01:15.000", TimeUtils.formatDuration(75000))
    }

    @Test
    fun formatDuration_overOneHour() {
        assertEquals("01:01:05.000", TimeUtils.formatDuration(3665000))
    }

    @Test
    fun formatDuration_exactlyOneHour() {
        assertEquals("01:00:00.000", TimeUtils.formatDuration(3600000))
    }

    @Test
    fun formatDuration_zero() {
        assertEquals("00:00.000", TimeUtils.formatDuration(0))
    }
}
