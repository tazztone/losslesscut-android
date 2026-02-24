package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.ClipManagementUseCase
import java.util.UUID

/**
 * Handles clip and segment mutations.
 */
class ClipController(
    private val clipManagementUseCase: ClipManagementUseCase
) {
    companion object {
        const val MIN_SEGMENT_DURATION_MS = 100L
    }

    fun splitSegment(clip: MediaClip, positionMs: Long): MediaClip? {
        val segment = clip.segments.find { positionMs in it.startMs..it.endMs } ?: return null
        
        // Business guard: ensure split doesn't create tiny segments
        val isTiny = positionMs - segment.startMs < MIN_SEGMENT_DURATION_MS || 
                segment.endMs - positionMs < MIN_SEGMENT_DURATION_MS
        
        return if (isTiny) {
            null
        } else {
            clipManagementUseCase.splitSegment(clip, positionMs, MIN_SEGMENT_DURATION_MS)
        }
    }

    fun markSegmentDiscarded(clip: MediaClip, segmentId: UUID): MediaClip? {
        return clipManagementUseCase.markSegmentDiscarded(clip, segmentId)
    }

    fun reorderClips(clips: List<MediaClip>, from: Int, to: Int): List<MediaClip> {
        return clipManagementUseCase.reorderClips(clips, from, to)
    }

    fun updateSegmentBounds(clip: MediaClip, id: UUID, start: Long, end: Long): MediaClip {
        // Business guard: ensure segment duration is at least MIN_SEGMENT_DURATION_MS
        val coercedEnd = if (end - start < MIN_SEGMENT_DURATION_MS) start + MIN_SEGMENT_DURATION_MS else end
        return clipManagementUseCase.updateSegmentBounds(clip, id, start, coercedEnd)
    }
}
