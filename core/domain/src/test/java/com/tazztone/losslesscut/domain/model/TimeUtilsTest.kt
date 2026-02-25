package com.tazztone.losslesscut.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

public class TimeUtilsTest {

    @Test
    public fun formatDuration_underOneMinute(): Unit {
        assertEquals("00:45.000", TimeUtils.formatDuration(45000))
    }

    @Test
    public fun formatDuration_overOneMinute(): Unit {
        assertEquals("01:15.000", TimeUtils.formatDuration(75000))
    }

    @Test
    public fun formatDuration_overOneHour(): Unit {
        assertEquals("01:01:05.000", TimeUtils.formatDuration(3665000))
    }

    @Test
    public fun formatDuration_exactlyOneHour(): Unit {
        assertEquals("01:00:00.000", TimeUtils.formatDuration(3600000))
    }

    @Test
    public fun formatDuration_zero(): Unit {
        assertEquals("00:00.000", TimeUtils.formatDuration(0))
    }

    @Test
    public fun testFormatFilenameDuration(): Unit {
        assertEquals("45s", TimeUtils.formatFilenameDuration(45000))
        assertEquals("01m15s", TimeUtils.formatFilenameDuration(75000))
        assertEquals("01h01m05s", TimeUtils.formatFilenameDuration(3665000))
        assertEquals("01h00m00s", TimeUtils.formatFilenameDuration(3600000))
        assertEquals("00s050ms", TimeUtils.formatFilenameDuration(50))
    }
}
