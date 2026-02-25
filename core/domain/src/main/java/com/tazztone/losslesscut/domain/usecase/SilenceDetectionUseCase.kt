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
        config: DetectionUtils.SilenceDetectionConfig
    ): List<LongRange> = withContext(ioDispatcher) {
        DetectionUtils.findSilence(
            waveform = waveformResult.rawAmplitudes,
            totalDurationMs = waveformResult.durationUs / 1000,
            config = config
        )
    }

    public fun applySilenceDetection(
        clip: MediaClip,
        silenceRanges: List<LongRange>,
        minSegmentDurationMs: Long
    ): MediaClip {
        val newSegments = buildSegmentsFromSilence(
            clipEnd = clip.durationMs,
            silenceRanges = silenceRanges.sortedBy { it.first },
            minSegmentDurationMs = minSegmentDurationMs
        )
        return clip.copy(segments = newSegments)
    }

    private fun buildSegmentsFromSilence(
        clipEnd: Long,
        silenceRanges: List<LongRange>,
        minSegmentDurationMs: Long
    ): List<TrimSegment> {
        val rawSegments = mutableListOf<TrimSegment>()
        var cursor = 0L

        // Phase 1: Create alternating KEEP/DISCARD segments covering the whole clip
        for (silence in silenceRanges) {
            val silStart = silence.first.coerceIn(0L, clipEnd)
            val silEnd = silence.last.coerceIn(0L, clipEnd)
            if (silEnd <= cursor) continue

            if (silStart > cursor) {
                rawSegments.add(TrimSegment(UUID.randomUUID(), cursor, silStart, SegmentAction.KEEP))
            }
            if (silEnd > silStart) {
                rawSegments.add(TrimSegment(UUID.randomUUID(), silStart, silEnd, SegmentAction.DISCARD))
            }
            cursor = silEnd
        }
        if (cursor < clipEnd) {
            rawSegments.add(TrimSegment(UUID.randomUUID(), cursor, clipEnd, SegmentAction.KEEP))
        }

        if (rawSegments.isEmpty()) return emptyList()

        return consolidateSegments(rawSegments, minSegmentDurationMs)
    }

    private fun consolidateSegments(
        segments: List<TrimSegment>,
        minSegmentDurationMs: Long
    ): List<TrimSegment> {
        if (segments.isEmpty()) return emptyList()

        val merged = ArrayList<TrimSegment>(segments.size)
        merged.add(segments[0])

        for (i in 1 until segments.size) {
            val cur = segments[i]
            val last = merged.last()
            val tooShort = (cur.endMs - cur.startMs) < minSegmentDurationMs

            if (cur.action == last.action || tooShort) {
                // Absorb cur into previous — previous action wins
                merged[merged.lastIndex] = last.copy(endMs = cur.endMs)
            } else {
                merged.add(cur)
            }
        }

        // Leading segment too short → absorb into the second
        if (merged.size > 1 && (merged[0].endMs - merged[0].startMs) < minSegmentDurationMs) {
            merged[1] = merged[1].copy(startMs = 0L)
            merged.removeAt(0)
        }

        return merged
    }
}
