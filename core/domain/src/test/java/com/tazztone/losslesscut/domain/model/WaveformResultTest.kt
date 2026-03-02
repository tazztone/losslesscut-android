package com.tazztone.losslesscut.domain.model

import org.junit.Assert.*
import org.junit.Test

public class WaveformResultTest {

    @Test
    public fun testWaveformResultEquality(): Unit {
        val raw1 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val raw2 = floatArrayOf(0.1f, 0.2f, 0.3f)
        val raw3 = floatArrayOf(0.1f, 0.2f, 0.4f)

        val res1 = WaveformResult(raw1, 0.3f, 1000L)
        val res2 = WaveformResult(raw2, 0.3f, 1000L)
        val res3 = WaveformResult(raw3, 0.3f, 1000L)
        val res4 = WaveformResult(raw1, 0.4f, 1000L)
        val res5 = WaveformResult(raw1, 0.3f, 2000L)

        assertEquals(res1, res2)
        assertNotEquals(res1, res3)
        assertNotEquals(res1, res4)
        assertNotEquals(res1, res5)
        
        assertEquals(res1.hashCode(), res2.hashCode())
        assertNotEquals(res1.hashCode(), res3.hashCode())
    }

    @Test
    public fun testWaveformResultToString(): Unit {
        val raw = floatArrayOf(0.1f, 0.2f)
        val res = WaveformResult(raw, 0.2f, 500L)
        val str = res.toString()
        
        assertTrue(str.contains("rawAmplitudesSize=2"))
        assertTrue(str.contains("maxAmplitude=0.2"))
        assertTrue(str.contains("durationUs=500"))
    }
}
