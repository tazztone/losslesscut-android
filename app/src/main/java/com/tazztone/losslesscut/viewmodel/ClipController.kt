package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.usecase.ClipManagementUseCase
import java.util.UUID

/**
 * Handles clip and segment mutations.
 */
class ClipController(
    private val clipManagementUseCase: ClipManagementUseCase,
    private val minSegmentDurationMs: Long
) {
    fun splitSegment(clip: MediaClip, positionMs: Long): MediaClip? {
        return clipManagementUseCase.splitSegment(clip, positionMs, minSegmentDurationMs)
    }

    fun markSegmentDiscarded(clip: MediaClip, segmentId: UUID): MediaClip? {
        return clipManagementUseCase.markSegmentDiscarded(clip, segmentId)
    }

    fun reorderClips(clips: List<MediaClip>, from: Int, to: Int): List<MediaClip> {
        return clipManagementUseCase.reorderClips(clips, from, to)
    }

    fun updateSegmentBounds(clip: MediaClip, id: UUID, start: Long, end: Long): MediaClip {
        return clipManagementUseCase.updateSegmentBounds(clip, id, start, end)
    }
}
