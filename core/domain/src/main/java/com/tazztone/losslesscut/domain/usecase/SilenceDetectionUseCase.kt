package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.model.DetectionUtils
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class SilenceDetectionUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun findSilence(
        waveform: FloatArray,
        threshold: Float,
        minSilenceMs: Long,
        totalDurationMs: Long,
        paddingMs: Long,
        minSegmentMs: Long
    ): List<LongRange> = withContext(ioDispatcher) {
        DetectionUtils.findSilence(
            waveform = waveform,
            threshold = threshold,
            minSilenceMs = minSilenceMs,
            totalDurationMs = totalDurationMs,
            paddingMs = paddingMs,
            minSegmentMs = minSegmentMs
        )
    }

    fun applySilenceDetection(
        clip: MediaClip,
        silenceRanges: List<LongRange>,
        minSegmentDurationMs: Long
    ): MediaClip {
        val newSegments = clip.segments.flatMap { seg ->
            if (seg.action == SegmentAction.DISCARD) {
                listOf(seg)
            } else {
                splitSegmentBySilence(seg, silenceRanges, minSegmentDurationMs)
            }
        }
        
        return clip.copy(segments = newSegments.sortedBy { it.startMs })
    }

    private fun splitSegmentBySilence(
        seg: TrimSegment,
        silenceRanges: List<LongRange>,
        minSegmentDurationMs: Long
    ): List<TrimSegment> {
        var segRanges = listOf(seg.startMs..seg.endMs)
        
        silenceRanges.forEach { silenceRange ->
            segRanges = segRanges.flatMap { curr ->
                val overlapStart = maxOf(curr.first, silenceRange.first)
                val overlapEnd = minOf(curr.last, silenceRange.last)
                
                if (overlapStart < overlapEnd && (overlapEnd - overlapStart) >= minSegmentDurationMs) {
                    val result = mutableListOf<LongRange>()
                    if (overlapStart > curr.first + minSegmentDurationMs) {
                        result.add(curr.first..overlapStart)
                    }
                    if (overlapEnd < curr.last - minSegmentDurationMs) {
                        result.add(overlapEnd..curr.last)
                    }
                    result
                } else {
                    listOf(curr)
                }
            }
        }
        
        val resultSegments = mutableListOf<TrimSegment>()
        var currentPos = seg.startMs
        segRanges.forEach { keepRange ->
            if (keepRange.first > currentPos + minSegmentDurationMs) {
                resultSegments.add(seg.copy(
                    id = UUID.randomUUID(), 
                    startMs = currentPos, 
                    endMs = keepRange.first, 
                    action = SegmentAction.DISCARD
                ))
            }
            resultSegments.add(seg.copy(
                id = UUID.randomUUID(), 
                startMs = keepRange.first, 
                endMs = keepRange.last, 
                action = SegmentAction.KEEP
            ))
            currentPos = keepRange.last
        }
        if (currentPos < seg.endMs - minSegmentDurationMs) {
            resultSegments.add(seg.copy(
                id = UUID.randomUUID(), 
                startMs = currentPos, 
                endMs = seg.endMs, 
                action = SegmentAction.DISCARD
            ))
        }
        return resultSegments
    }
}
