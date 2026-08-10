package com.tazztone.losslesscut.domain.model

import java.util.UUID

public object SegmentBounds {

    @Suppress("LongParameterList")
    public fun coerce(
        segments: List<TrimSegment>,
        segmentId: UUID,
        startMs: Long,
        endMs: Long,
        clipDurationMs: Long,
        minDurationMs: Long
    ): Pair<Long, Long>? {
        val index = segments.indexOfFirst { it.id == segmentId }
        if (index < 0) return null

        val minimum = minDurationMs.coerceAtLeast(0L)
        val lowerBound = segments.getOrNull(index - 1)?.endMs ?: 0L
        val upperBound = segments.getOrNull(index + 1)?.startMs ?: clipDurationMs
        if (lowerBound < 0L || upperBound > clipDurationMs || upperBound - lowerBound < minimum) {
            return null
        }

        val safeStart = startMs.coerceIn(lowerBound, upperBound - minimum)
        val safeEnd = endMs.coerceIn(safeStart + minimum, upperBound)
        return safeStart to safeEnd
    }
}
