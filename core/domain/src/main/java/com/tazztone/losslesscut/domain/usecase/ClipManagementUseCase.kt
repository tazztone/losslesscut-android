package com.tazztone.losslesscut.domain.usecase

import com.tazztone.losslesscut.domain.di.IoDispatcher
import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import com.tazztone.losslesscut.domain.repository.IVideoEditingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.util.UUID
import javax.inject.Inject

public open class ClipManagementUseCase @Inject constructor(
    private val repository: IVideoEditingRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    public open suspend fun createClips(uris: List<String>): Result<List<MediaClip>> = withContext(ioDispatcher) {
        try {
            val clips = uris.map { uri ->
                repository.createClipFromUri(uri).getOrThrow()
            }
            Result.success(clips)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    public fun splitSegment(clip: MediaClip, positionMs: Long, minDurationMs: Long): MediaClip? {
        val segment = clip.segments.find { positionMs in it.startMs..it.endMs }
        
        val canSplit = segment != null && 
            positionMs - segment.startMs >= minDurationMs && 
            segment.endMs - positionMs >= minDurationMs

        return if (canSplit && segment != null) {
            val newSegments = clip.segments.toMutableList()
            val index = newSegments.indexOf(segment)
            newSegments.removeAt(index)
            newSegments.add(index, segment.copy(endMs = positionMs))
            newSegments.add(index + 1, segment.copy(id = UUID.randomUUID(), startMs = positionMs))
            clip.copy(segments = newSegments)
        } else {
            null
        }
    }

    public fun markSegmentDiscarded(clip: MediaClip, id: UUID): MediaClip? {
        val segment = clip.segments.find { it.id == id } ?: return null
        
        val canDiscard = segment.action != SegmentAction.KEEP || 
            clip.segments.count { it.action == SegmentAction.KEEP } > 1

        return if (canDiscard) {
            val newAction = if (segment.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP
            val newSegments = clip.segments.map { 
                if (it.id == id) it.copy(action = newAction) else it 
            }
            clip.copy(segments = newSegments)
        } else {
            null
        }
    }

    public fun updateSegmentBounds(clip: MediaClip, id: UUID, start: Long, end: Long): MediaClip {
        val newSegments = clip.segments.map { 
            if (it.id == id) it.copy(startMs = start, endMs = end) else it 
        }
        return clip.copy(segments = newSegments)
    }

    public fun reorderClips(clips: List<MediaClip>, from: Int, to: Int): List<MediaClip> {
        val list = clips.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }
}
