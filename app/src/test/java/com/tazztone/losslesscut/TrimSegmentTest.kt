package com.tazztone.losslesscut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class TrimSegmentTest {

    @Test
    fun testSegmentDefaultAction() {
        val segment = TrimSegment(UUID.randomUUID(), 0, 1000)
        assertEquals(SegmentAction.KEEP, segment.action)
    }

    @Test
    fun testSegmentCopy() {
        val id = UUID.randomUUID()
        val original = TrimSegment(id, 100, 500, SegmentAction.KEEP)
        val copy = original.copy()
        
        assertEquals(original.id, copy.id)
        assertEquals(original.startMs, copy.startMs)
        assertEquals(original.endMs, copy.endMs)
        assertEquals(original.action, copy.action)
    }

    @Test
    fun testSegmentIdUniqueness() {
        val seg1 = TrimSegment(UUID.randomUUID(), 0, 1000)
        val seg2 = TrimSegment(UUID.randomUUID(), 0, 1000)
        assertNotEquals(seg1.id, seg2.id)
    }
}
