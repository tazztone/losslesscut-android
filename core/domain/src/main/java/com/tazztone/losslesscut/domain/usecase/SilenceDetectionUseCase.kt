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
        val result = mutableListOf<TrimSegment>()
        var cursor = 0L

        for (silence in silenceRanges) {
            val silStart = silence.first.coerceIn(0L, clipEnd)
            val silEnd = silence.last.coerceIn(0L, clipEnd)

            // KEEP the content before this silence
            if (silStart - cursor >= minSegmentDurationMs) {
                result.add(TrimSegment(UUID.randomUUID(), cursor, silStart, SegmentAction.KEEP))
            }
            // DISCARD the silent region
            if (silEnd > silStart) {
                result.add(TrimSegment(UUID.randomUUID(), silStart, silEnd, SegmentAction.DISCARD))
            }
            cursor = silEnd
        }

        // KEEP any remaining content after the last silence
        if (clipEnd - cursor >= minSegmentDurationMs) {
            result.add(TrimSegment(UUID.randomUUID(), cursor, clipEnd, SegmentAction.KEEP))
        }

        return result
    }
}
