package com.tazztone.losslesscut.engine.muxing

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleTimeMapperTest {

    private val mapper = SampleTimeMapper()

    @Test
    fun `map calculates relative time correctly without offset`() {
        val result = mapper.map(sampleTimeUs = 1000L, effectiveStartUs = 200L)
        assertEquals(800L, result)
    }

    @Test
    fun `map calculates relative time correctly with offset`() {
        val result = mapper.map(sampleTimeUs = 1000L, effectiveStartUs = 200L, globalOffsetUs = 5000L)
        assertEquals(5800L, result)
    }

    @Test
    fun `map handles negative relative time by clamping to zero`() {
        val result = mapper.map(sampleTimeUs = 100L, effectiveStartUs = 200L)
        assertEquals(0L, result)
    }
}
