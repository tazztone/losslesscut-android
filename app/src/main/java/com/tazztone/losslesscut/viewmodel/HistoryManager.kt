package com.tazztone.losslesscut.viewmodel

import com.tazztone.losslesscut.domain.model.MediaClip

/**
 * Manages the undo/redo stack for media clips.
 * Note: Storing full snapshots can be memory intensive.
 * Consider using a command pattern for deltas in the future.
 */
class HistoryManager(private val limit: Int = 30) {
    private val history = mutableListOf<List<MediaClip>>()
    private val redoStack = mutableListOf<List<MediaClip>>()

    fun save(clips: List<MediaClip>) {
        // Deep copy of clips and their segments
        history.add(clips.map { it.copy(segments = it.segments.toList()) })
        if (history.size > limit) history.removeAt(0)
        redoStack.clear()
    }

    fun undo(currentClips: List<MediaClip>): List<MediaClip>? {
        if (history.isEmpty()) return null
        redoStack.add(currentClips.map { it.copy(segments = it.segments.toList()) })
        return history.removeAt(history.size - 1)
    }

    fun redo(currentClips: List<MediaClip>): List<MediaClip>? {
        if (redoStack.isEmpty()) return null
        history.add(currentClips.map { it.copy(segments = it.segments.toList()) })
        return redoStack.removeAt(redoStack.size - 1)
    }

    fun clear() {
        history.clear()
        redoStack.clear()
    }

    val canUndo: Boolean get() = history.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}
