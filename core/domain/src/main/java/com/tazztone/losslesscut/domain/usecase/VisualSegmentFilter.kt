package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.model.FrameAnalysis
import com.tazztone.losslesscut.domain.model.VisualStrategy

public object VisualSegmentFilter {
    private const val MIN_VISIBLE_STAMP_DURATION_MS = 100L
    
    public fun filter(
        frames: List<FrameAnalysis>,
        strategy: VisualStrategy,
        threshold: Float,
        minSegmentMs: Long,
        clipDurationMs: Long? = null
    ): List<LongRange> {
        if (frames.isEmpty()) return emptyList()

        val resultRanges = groupMatchingRanges(frames, strategy, threshold)

        // Fix: Visual Regression. Expand single-frame matches to have a duration
        // so they are visible as mask rects in the seeker.
        // We use minSegmentMs or a fixed fallback if minSegmentMs is 0.
        val displayPadding = if (minSegmentMs > 0) minSegmentMs else MIN_VISIBLE_STAMP_DURATION_MS
        val clipEnd = clipDurationMs?.coerceAtLeast(0L)
        
        val expandedRanges = resultRanges.mapNotNull { range ->
            if (clipEnd != null && (range.last < 0L || range.first > clipEnd)) {
                null
            } else if (range.first == range.last) {
                var start = range.first - displayPadding / 2
                var end = start + displayPadding
                if (clipEnd != null && end > clipEnd) {
                    end = clipEnd
                    start = (end - displayPadding).coerceAtLeast(0)
                }
                if (start < 0) {
                    start = 0
                    end = displayPadding
                }
                start..end
            } else {
                range
            }
        }.mapNotNull { range ->
            if (clipEnd == null) {
                range
            } else {
                if (range.last < 0L || range.first > clipEnd) {
                    null
                } else {
                    val start = range.first.coerceIn(0, clipEnd)
                    val end = range.last.coerceIn(0, clipEnd)
                    if (start < end) start..end else null
                }
            }
        }

        val filteredRanges = expandedRanges.filter { (it.last - it.first) >= minSegmentMs }

        return mergeOverlappingRanges(filteredRanges)
    }

    private fun mergeOverlappingRanges(ranges: List<LongRange>): List<LongRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        var currentStart = sorted[0].first
        var currentEnd = sorted[0].last

        for (i in 1 until sorted.size) {
            val range = sorted[i]
            if (range.first <= currentEnd) {
                currentEnd = maxOf(currentEnd, range.last)
            } else {
                merged.add(currentStart..currentEnd)
                currentStart = range.first
                currentEnd = range.last
            }
        }
        merged.add(currentStart..currentEnd)
        return merged
    }

    private fun groupMatchingRanges(
        frames: List<FrameAnalysis>,
        strategy: VisualStrategy,
        threshold: Float
    ): List<LongRange> {
        val resultRanges = mutableListOf<LongRange>()
        var currentRangeStart: Long? = null
        var currentRangeEnd: Long? = null

        for (i in frames.indices) {
            val frame = frames[i]
            if (isMatch(frame, strategy, threshold)) {
                if (currentRangeStart == null) {
                    currentRangeStart = getRangeStart(frames, i, strategy)
                }
                currentRangeEnd = frame.timeMs
            } else if (currentRangeStart != null && currentRangeEnd != null) {
                resultRanges.add(currentRangeStart..currentRangeEnd)
                currentRangeStart = null
                currentRangeEnd = null
            }
        }
        if (currentRangeStart != null && currentRangeEnd != null) {
            resultRanges.add(currentRangeStart..currentRangeEnd)
        }
        return resultRanges
    }

    private fun getRangeStart(frames: List<FrameAnalysis>, index: Int, strategy: VisualStrategy): Long {
        return if (strategy == VisualStrategy.FREEZE_FRAME && index > 0) {
            frames[index - 1].timeMs
        } else {
            frames[index].timeMs
        }
    }

    private fun isMatch(frame: FrameAnalysis, strategy: VisualStrategy, threshold: Float): Boolean {
        return when (strategy) {
            VisualStrategy.BLACK_FRAMES -> frame.meanLuma < threshold
            VisualStrategy.BLUR_QUALITY -> frame.blurVariance < threshold
            VisualStrategy.FREEZE_FRAME -> frame.freezeDiff != null && frame.freezeDiff < threshold
            VisualStrategy.SCENE_CHANGE -> frame.sceneDistance != null && frame.sceneDistance > threshold
        }
    }
}
