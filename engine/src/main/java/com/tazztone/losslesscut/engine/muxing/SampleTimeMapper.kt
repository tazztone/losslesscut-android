package com.tazztone.losslesscut.engine.muxing

import kotlin.math.max
import javax.inject.Inject

/**
 * Handles presentation time mapping for lossless cutting and merging.
 */
class SampleTimeMapper @Inject constructor() {

    /**
     * Maps original sample time to relative presentation time.
     * @param sampleTimeUs Original timestamp from extractor.
     * @param effectiveStartUs The start timestamp of the segment.
     * @param globalOffsetUs Offset to add (used for merging multiple segments).
     * @return Mapped presentation time in microseconds.
     */
    fun map(sampleTimeUs: Long, effectiveStartUs: Long, globalOffsetUs: Long = 0L): Long {
        val relativeTime = sampleTimeUs - effectiveStartUs
        return globalOffsetUs + max(0L, relativeTime)
    }
}
