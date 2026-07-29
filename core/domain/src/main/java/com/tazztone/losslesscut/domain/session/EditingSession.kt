package com.tazztone.losslesscut.domain.session

import com.tazztone.losslesscut.domain.model.MediaClip
import com.tazztone.losslesscut.domain.model.SegmentAction
import com.tazztone.losslesscut.domain.model.TrimSegment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.collections.ArrayDeque

public data class EditingSessionState(
    public val clips: List<MediaClip> = emptyList(),
    public val selectedClipIndex: Int = 0,
    public val selectedSegmentId: UUID? = null,
    public val isDirty: Boolean = false,
    public val canUndo: Boolean = false,
    public val canRedo: Boolean = false
) {
    public val selectedClip: MediaClip?
        get() = clips.getOrNull(selectedClipIndex)

    public val selectedSegment: TrimSegment?
        get() = selectedClip?.segments?.find { it.id == selectedSegmentId }
}

public class EditingSession(
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT
) {
    private data class Snapshot(
        val clips: List<MediaClip>,
        val selectedClipIndex: Int,
        val selectedSegmentId: UUID?
    )

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var activeBoundsEditSegmentId: UUID? = null

    private val _state = MutableStateFlow(EditingSessionState())
    public val state: StateFlow<EditingSessionState> = _state.asStateFlow()

    public val currentSnapshot: EditingSessionState
        get() = _state.value

    public fun setClips(clips: List<MediaClip>, selectedIndex: Int = 0) {
        undoStack.clear()
        redoStack.clear()
        activeBoundsEditSegmentId = null
        val validIndex = if (clips.isEmpty()) 0 else selectedIndex.coerceIn(clips.indices)
        val firstSegmentId = clips.getOrNull(validIndex)?.segments?.firstOrNull()?.id

        _state.value = EditingSessionState(
            clips = clips,
            selectedClipIndex = validIndex,
            selectedSegmentId = firstSegmentId,
            isDirty = false,
            canUndo = false,
            canRedo = false
        )
    }

    public fun updateClipsList(clips: List<MediaClip>, selectedIndex: Int = _state.value.selectedClipIndex) {
        val current = _state.value
        pushHistory()
        val validIndex = if (clips.isEmpty()) 0 else selectedIndex.coerceIn(clips.indices)
        val newSelectedSegmentId = if (clips.getOrNull(validIndex)?.segments?.any { it.id == current.selectedSegmentId } == true) {
            current.selectedSegmentId
        } else {
            clips.getOrNull(validIndex)?.segments?.firstOrNull()?.id
        }
        _state.value = current.copy(
            clips = clips,
            selectedClipIndex = validIndex,
            selectedSegmentId = newSelectedSegmentId,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    public fun selectClip(index: Int) {
        val current = _state.value
        if (current.clips.isEmpty() || index !in current.clips.indices) return
        val newSegmentId = current.clips[index].segments.firstOrNull()?.id
        _state.value = current.copy(
            selectedClipIndex = index,
            selectedSegmentId = newSegmentId
        )
    }

    public fun selectSegment(segmentId: UUID?) {
        val current = _state.value
        val clip = current.selectedClip ?: return
        if (segmentId != null && clip.segments.none { it.id == segmentId }) return
        _state.value = current.copy(selectedSegmentId = segmentId)
    }

    public fun splitSegmentAt(positionMs: Long, minDurationMs: Long = MIN_SEGMENT_DURATION_MS): Boolean {
        val current = _state.value
        val clip = current.selectedClip ?: return false

        val targetSegment = clip.segments.find { positionMs in it.startMs..it.endMs } ?: return false
        val canSplit = positionMs - targetSegment.startMs >= minDurationMs &&
                targetSegment.endMs - positionMs >= minDurationMs

        if (!canSplit) return false

        pushHistory()

        val newSegmentId = UUID.randomUUID()
        val updatedSegments = clip.segments.toMutableList()
        val index = updatedSegments.indexOf(targetSegment)
        updatedSegments.removeAt(index)
        updatedSegments.add(index, targetSegment.copy(endMs = positionMs))
        updatedSegments.add(index + 1, targetSegment.copy(id = newSegmentId, startMs = positionMs))

        val updatedClip = clip.copy(segments = updatedSegments)
        val updatedClips = current.clips.toMutableList().apply {
            this[current.selectedClipIndex] = updatedClip
        }

        _state.value = current.copy(
            clips = updatedClips,
            selectedSegmentId = newSegmentId,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        return true
    }

    public fun toggleSegmentDiscard(segmentId: UUID): Boolean {
        val current = _state.value
        val clip = current.selectedClip ?: return false
        val segment = clip.segments.find { it.id == segmentId } ?: return false

        val canDiscard = segment.action != SegmentAction.KEEP ||
                clip.segments.count { it.action == SegmentAction.KEEP } > 1

        if (!canDiscard) return false

        pushHistory()

        val newAction = if (segment.action == SegmentAction.KEEP) SegmentAction.DISCARD else SegmentAction.KEEP
        val updatedSegments = clip.segments.map {
            if (it.id == segmentId) it.copy(action = newAction) else it
        }
        val updatedClip = clip.copy(segments = updatedSegments)
        val updatedClips = current.clips.toMutableList().apply {
            this[current.selectedClipIndex] = updatedClip
        }

        _state.value = current.copy(
            clips = updatedClips,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        return true
    }

    public fun resetClipSegments(): Boolean {
        val current = _state.value
        val clip = current.selectedClip ?: return false

        if (clip.segments.size == 1) {
            val single = clip.segments[0]
            if (single.action == SegmentAction.KEEP && single.startMs == 0L && single.endMs == clip.durationMs) {
                return false
            }
        }

        pushHistory()

        val newSegmentId = UUID.randomUUID()
        val resetSegment = TrimSegment(id = newSegmentId, startMs = 0L, endMs = clip.durationMs, action = SegmentAction.KEEP)
        val updatedClip = clip.copy(segments = listOf(resetSegment))
        val updatedClips = current.clips.toMutableList().apply {
            this[current.selectedClipIndex] = updatedClip
        }

        _state.value = current.copy(
            clips = updatedClips,
            selectedSegmentId = newSegmentId,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        return true
    }

    public fun updateSegmentBounds(
        segmentId: UUID,
        startMs: Long,
        endMs: Long,
        minDurationMs: Long = MIN_SEGMENT_DURATION_MS,
        coalesceHistory: Boolean = false
    ) {
        val current = _state.value
        val clip = current.selectedClip ?: return
        if (clip.segments.none { it.id == segmentId }) return

        if (!coalesceHistory || activeBoundsEditSegmentId != segmentId) {
            pushHistory()
            activeBoundsEditSegmentId = if (coalesceHistory) segmentId else null
        }

        val coercedEnd = if (endMs - startMs < minDurationMs) startMs + minDurationMs else endMs
        val updatedSegments = clip.segments.map {
            if (it.id == segmentId) it.copy(startMs = startMs, endMs = coercedEnd) else it
        }
        val updatedClip = clip.copy(segments = updatedSegments)
        val updatedClips = current.clips.toMutableList().apply {
            this[current.selectedClipIndex] = updatedClip
        }

        _state.value = current.copy(
            clips = updatedClips,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    public fun finishSegmentBoundsEdit() {
        activeBoundsEditSegmentId = null
    }

    public fun applySegments(updatedSegments: List<TrimSegment>) {
        val current = _state.value
        val clip = current.selectedClip ?: return
        if (updatedSegments == clip.segments) return

        pushHistory()

        val updatedClip = clip.copy(segments = updatedSegments)
        val updatedClips = current.clips.toMutableList().apply {
            this[current.selectedClipIndex] = updatedClip
        }

        val newSelectedSegmentId = if (updatedSegments.any { it.id == current.selectedSegmentId }) {
            current.selectedSegmentId
        } else {
            updatedSegments.firstOrNull()?.id
        }

        _state.value = current.copy(
            clips = updatedClips,
            selectedSegmentId = newSelectedSegmentId,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
    }

    public fun reorderClips(fromIndex: Int, toIndex: Int): Boolean {
        val current = _state.value
        if (fromIndex !in current.clips.indices || toIndex !in 0..current.clips.size || fromIndex == toIndex) {
            return false
        }

        pushHistory()

        val updatedList = current.clips.toMutableList()
        val item = updatedList.removeAt(fromIndex)
        updatedList.add(toIndex, item)

        val newSelectedClipIndex = when (current.selectedClipIndex) {
            fromIndex -> toIndex
            in (fromIndex + 1)..toIndex -> current.selectedClipIndex - 1
            in toIndex..<fromIndex -> current.selectedClipIndex + 1
            else -> current.selectedClipIndex
        }.coerceIn(updatedList.indices)

        _state.value = current.copy(
            clips = updatedList,
            selectedClipIndex = newSelectedClipIndex,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false
        )
        return true
    }

    public fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        activeBoundsEditSegmentId = null
        val current = _state.value
        redoStack.addLast(Snapshot(current.clips, current.selectedClipIndex, current.selectedSegmentId))

        val snapshot = undoStack.removeLast()
        _state.value = current.copy(
            clips = snapshot.clips,
            selectedClipIndex = snapshot.selectedClipIndex,
            selectedSegmentId = snapshot.selectedSegmentId,
            isDirty = true,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
        return true
    }

    public fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        activeBoundsEditSegmentId = null
        val current = _state.value
        undoStack.addLast(Snapshot(current.clips, current.selectedClipIndex, current.selectedSegmentId))

        val snapshot = redoStack.removeLast()
        _state.value = current.copy(
            clips = snapshot.clips,
            selectedClipIndex = snapshot.selectedClipIndex,
            selectedSegmentId = snapshot.selectedSegmentId,
            isDirty = true,
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
        return true
    }

    public fun clearDirty() {
        _state.value = _state.value.copy(isDirty = false)
    }

    public fun markDirty() {
        _state.value = _state.value.copy(isDirty = true)
    }

    private fun pushHistory() {
        activeBoundsEditSegmentId = null
        val current = _state.value
        if (undoStack.size >= historyLimit) {
            undoStack.removeFirst()
        }
        undoStack.addLast(Snapshot(current.clips, current.selectedClipIndex, current.selectedSegmentId))
        redoStack.clear()
    }

    public companion object {
        public const val DEFAULT_HISTORY_LIMIT: Int = 30
        public const val MIN_SEGMENT_DURATION_MS: Long = 100L
    }
}
