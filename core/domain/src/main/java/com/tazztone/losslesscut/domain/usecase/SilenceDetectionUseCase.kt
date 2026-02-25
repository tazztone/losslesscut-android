package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.DetectionUtils
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.WaveformResult
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

public class SilenceDetectionUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    public suspend fun findSilence(
        waveformResult: WaveformResult,
        config: DetectionUtils.SilenceDetectionConfig,
        minSegmentMs: Long
    ): List<LongRange> = withContext(ioDispatcher) {
        val totalDurationMs = waveformResult.durationUs / US_PER_MS
        
        // 1. Find RAW silence (threshold only, NO duration filtering yet)
        val rawRanges = DetectionUtils.findSilence(
            waveform = waveformResult.rawAmplitudes,
            totalDurationMs = totalDurationMs,
            threshold = config.threshold
        )
        
        // 2. Merge close silences (Drop short noises/Keepers)
        // This surgically identifies the true discard/keep blocks.
        val noiseMergedRanges = if (minSegmentMs > 0) {
            DetectionUtils.mergeCloseSilences(rawRanges, minSegmentMs)
        } else {
            rawRanges
        }

        // 3. Filter short silences (Drop short Discards)
        // Discard any remaining silences that are too short to bothered cutting out.
        val durationFilteredRanges = DetectionUtils.filterShortSilences(
            ranges = noiseMergedRanges,
            minSilenceMs = config.minSilenceMs,
            totalDurationMs = totalDurationMs
        )
        
        // 4. Apply padding cosmeticly AFTER structural merging
        DetectionUtils.applyPaddingAndFilter(
            ranges = durationFilteredRanges,
            paddingStartMs = config.paddingStartMs,
            paddingEndMs = config.paddingEndMs,
            totalDurationMs = totalDurationMs
        )
    }

    public fun applySilenceDetection(
        clip: MediaClip,
        silenceRanges: List<LongRange>,
        @Suppress("UNUSED_PARAMETER") minSegmentDurationMs: Long
    ): MediaClip {
        val segments = buildSegmentsFromSilence(
            clipEnd = clip.durationMs,
            silenceRanges = silenceRanges
        )
        
        val finalSegments = if (segments.none { it.action == SegmentAction.KEEP }) {
            listOf(TrimSegment(UUID.randomUUID(), 0L, clip.durationMs, SegmentAction.KEEP))
        } else {
            segments
        }
        
        return clip.copy(segments = finalSegments)
    }

    private fun buildSegmentsFromSilence(
        clipEnd: Long,
        silenceRanges: List<LongRange>
    ): List<TrimSegment> {
        val rawSegments = mutableListOf<TrimSegment>()
        var cursor = 0L

        // Boundary clamping threshold (10ms resolution)
        val boundaryThresholdMs = BOUNDARY_THRESHOLD_MS

        for (silence in silenceRanges) {
            val silStart = silence.first.coerceIn(0L, clipEnd)
            val silEnd = silence.last.coerceIn(0L, clipEnd)
            
            if (silEnd <= cursor) continue

            // Fix boundary tiny gaps: if silence starts almost at 0, pull it to 0
            val effectiveStart = if (silStart < boundaryThresholdMs) 0L else silStart
            val effectiveEnd = if (clipEnd - silEnd < boundaryThresholdMs) clipEnd else silEnd

            if (effectiveStart > cursor) {
                rawSegments.add(TrimSegment(UUID.randomUUID(), cursor, effectiveStart, SegmentAction.KEEP))
            }
            if (effectiveEnd > effectiveStart) {
                rawSegments.add(TrimSegment(UUID.randomUUID(), effectiveStart, effectiveEnd, SegmentAction.DISCARD))
            }
            cursor = effectiveEnd
        }

        if (cursor < clipEnd) {
            if (clipEnd - cursor < boundaryThresholdMs) {
                // Too small gap at the end? Merge into DISCARD if previous was DISCARD, or just skip
                if (rawSegments.isNotEmpty() && rawSegments.last().action == SegmentAction.DISCARD) {
                    val last = rawSegments.removeAt(rawSegments.size - 1)
                    rawSegments.add(last.copy(endMs = clipEnd))
                } else {
                    rawSegments.add(TrimSegment(UUID.randomUUID(), cursor, clipEnd, SegmentAction.KEEP))
                }
            } else {
                rawSegments.add(TrimSegment(UUID.randomUUID(), cursor, clipEnd, SegmentAction.KEEP))
            }
        }

        return mergeAdjacentSameAction(rawSegments)
    }

    private fun mergeAdjacentSameAction(segments: List<TrimSegment>): List<TrimSegment> {
        if (segments.isEmpty()) return emptyList()
        val result = mutableListOf<TrimSegment>()
        var current = segments[0]
        for (i in 1 until segments.size) {
            val next = segments[i]
            if (next.action == current.action) {
                current = current.copy(endMs = next.endMs)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private companion object {
        private const val BOUNDARY_THRESHOLD_MS = 10L
        private const val US_PER_MS = 1000L
    }
}
