package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

open class ClipManagementUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    open suspend fun createClips(uris: List<String>): Result<List<MediaClip>> = withContext(ioDispatcher) {
        try {
            val clips = uris.map { uri ->
                repository.createClipFromUri(uri).getOrThrow()
            }
            Result.success(clips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun splitSegment(clip: MediaClip, positionMs: Long, minDurationMs: Long): MediaClip? {
        val segment = clip.segments.find { positionMs in it.startMs..it.endMs } ?: return null
        
        if (positionMs - segment.startMs < minDurationMs || segment.endMs - positionMs < minDurationMs) {
            return null
        }

        val newSegments = clip.segments.toMutableList()
        val index = newSegments.indexOf(segment)
        newSegments.removeAt(index)
        newSegments.add(index, segment.copy(endMs = positionMs))
        newSegments.add(index + 1, segment.copy(id = UUID.randomUUID(), startMs = positionMs))
        
        return clip.copy(segments = newSegments)
    }

    fun markSegmentDiscarded(clip: MediaClip, id: UUID): MediaClip? {
        val segment = clip.segments.find { it.id == id } ?: return null
        
        if (segment.action == SegmentAction.KEEP && 
            clip.segments.count { it.action == SegmentAction.KEEP } <= 1) {
            return null
        }

        val newAction = if (segment.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP
        val newSegments = clip.segments.map { 
            if (it.id == id) it.copy(action = newAction) else it 
        }
        
        return clip.copy(segments = newSegments)
    }

    fun updateSegmentBounds(clip: MediaClip, id: UUID, start: Long, end: Long): MediaClip {
        val newSegments = clip.segments.map { 
            if (it.id == id) it.copy(startMs = start, endMs = end) else it 
        }
        return clip.copy(segments = newSegments)
    }

    fun reorderClips(clips: List<MediaClip>, from: Int, to: Int): List<MediaClip> {
        val list = clips.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }
}
